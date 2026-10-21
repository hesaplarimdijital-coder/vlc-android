package app.nagmedar.ui.player.subtitles

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.audio.FolderFiles
import app.nagmedar.ui.R
import app.nagmedar.ui.input.DropKind
import app.nagmedar.ui.input.DropPlan
import app.nagmedar.ui.input.DroppedFile
import app.nagmedar.ui.input.planDrop
import app.nagmedar.ui.player.subtitles.tools.DocumentSubtitleToolsIo
import app.nagmedar.ui.player.subtitles.tools.SubtitleFormat
import app.nagmedar.ui.player.subtitles.tools.SubtitleToolsController
import app.nagmedar.ui.player.subtitles.tools.ToolsFile
import app.nagmedar.ui.player.subtitles.tools.ToolsPlayback
import app.nagmedar.ui.player.subtitles.tools.ToolsTab
import app.nagmedar.ui.player.subtitles.translate.SubtitleTranslateController
import app.nagmedar.ui.player.subtitles.translate.WorkTranslationService
import app.nagmedar.ui.player.subtitles.whisper.WhisperController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.tools.KEY_SUBTITLES_AUTOLOAD
import org.videolan.tools.Settings
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.repository.SlaveRepository
import java.io.File
import java.util.Locale

/**
 * The playing film's subtitle files (S-701): added with the system's file picker (Ctrl+Shift+A,
 * the subtitles' menu), dropped on the video, or found in its folder as it starts. One found is
 * loaded; more than one are offered ([found]), the user's language first. What is added stays
 * with the film, as the classic player's picker keeps it, and its document's access with the app.
 * Each is read in its own character set (S-702): VLC is told the film's as it starts, a file in
 * another one is read into UTF-8 first; "Subtitle encoding…" chooses one for the film ([encoding]).
 */
@Stable
internal class PlayerSubtitles(private val activity: VideoPlayerActivity, private val scope: CoroutineScope) {

    /** Subtitles found in the film's folder, more than one: offered until one is chosen or the card is closed. */
    var found by mutableStateOf<List<SubtitleCandidate>>(emptyList())
        private set

    /** Files are carried over the video: what a drop does shows. */
    var dragging by mutableStateOf(false)

    /** Opens the system's file picker: the composition's launcher. */
    var pick: () -> Unit = {}

    /** Where the film's last subtitles came from, for the subtitles' tab (S-703): "Film.tr.srt · matched from the folder". */
    var note by mutableStateOf<String?>(null)
        private set

    /** "Subtitle encoding…" is open on the film's first subtitle file (S-702). */
    var encoding by mutableStateOf<EncodingSample?>(null)
        private set

    /**
     * The subtitle tools (S-708), over the video: the film's subtitle files cleaned, converted and
     * edited into new files, or translated offline (S-710) and shown with the original.
     */
    val tools = SubtitleToolsController(
        DocumentSubtitleToolsIo(activity.applicationContext),
        scope,
        activity.getString(R.string.nag_subtools_edited_word),
        translation = SubtitleTranslateController(WorkTranslationService(activity.applicationContext), scope, Locale.getDefault().language),
    )

    /** "Generate subtitles…" (S-709): the film's speech transcribed on the device. */
    val whisper = WhisperController(activity.applicationContext, scope)

    /** S-703: the film's two subtitle tracks, which a translation (S-710) shows as the secondary one. */
    var dual: DualSubtitlesController? = null

    /** The film whose folder was read: once a film. */
    private var lookedAt: String? = null

    /** A film started or went on: its folder is read once for its subtitles, unless it has some. */
    fun onMediaStarted() {
        val service = activity.service ?: return
        val uri = service.currentMediaWrapper?.uri ?: return
        val key = uri.toString()
        if (key == lookedAt) return
        lookedAt = key
        found = emptyList()
        note = null
        // VLC's own "Auto load subtitles" says no
        if (!Settings.getInstance(activity).getBoolean(KEY_SUBTITLES_AUTOLOAD, true)) return
        if (!service.isVideoPlaying) return
        val context = activity.applicationContext
        scope.launch {
            val candidates = withContext(Dispatchers.IO) {
                val name = displayName(context.contentResolver, uri) ?: return@withContext emptyList()
                matchSubtitles(name, siblingFiles(context, uri), prefer = Locale.getDefault().language)
            }
            // another film meanwhile, or subtitles of its own: VLC found them beside a local file, or they were kept with it
            if (lookedAt != key || candidates.isEmpty() || hasSubtitleFiles()) return@launch
            if (candidates.size == 1) {
                val only = candidates.single()
                if (load(Uri.parse(only.uri), only.name, select = true)) {
                    say(activity.getString(R.string.nag_sub_added_from_folder, only.name))
                    note = activity.getString(R.string.nag_subs_found_note, only.name)
                }
            } else {
                found = candidates
            }
        }
    }

    /** The one chosen from those found: it shows. */
    fun choose(candidate: SubtitleCandidate) {
        found = emptyList()
        scope.launch {
            if (load(Uri.parse(candidate.uri), candidate.name, select = true)) {
                say(activity.getString(R.string.nag_sub_added, candidate.name))
                note = activity.getString(R.string.nag_subs_found_note, candidate.name)
            }
        }
    }

    fun dismiss() {
        found = emptyList()
    }

    /** Files picked or dropped: the subtitles among them are added, the first one shows; a word on what was not. */
    fun add(files: List<Pair<Uri, String?>>) {
        val (subtitles, others) = files.partition { (uri, name) -> isSubtitleFile(name ?: uri.lastPathSegment.orEmpty()) }
        if (subtitles.isEmpty()) {
            others.firstOrNull()?.let { (uri, name) -> say(activity.getString(R.string.nag_sub_not_subtitle, name ?: uri.lastPathSegment.orEmpty())) }
            return
        }
        found = emptyList()
        scope.launch {
            val added = subtitles.filterIndexed { index, (uri, name) -> load(uri, name ?: uri.lastPathSegment.orEmpty(), select = index == 0) }
            added.firstOrNull()?.let { (uri, name) -> note = activity.getString(R.string.nag_subs_added_note, name ?: uri.lastPathSegment.orEmpty()) }
            when {
                added.isEmpty() -> say(activity.getString(R.string.nag_sub_failed, subtitles.first().let { (uri, name) -> name ?: uri.lastPathSegment.orEmpty() }))
                added.size == 1 -> say(activity.getString(R.string.nag_sub_added, added.single().let { (uri, name) -> name ?: uri.lastPathSegment.orEmpty() }))
                else -> say(activity.resources.getQuantityString(R.plurals.nag_drop_subtitles_loaded, added.size, added.size))
            }
        }
    }

    /** Files dropped on the video: subtitles join the film; a video or music plays instead, as on the library's window (S-406). */
    fun dropped(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = activity.contentResolver
        scope.launch {
            val files = withContext(Dispatchers.IO) { uris.map { DroppedFile(it.toString(), displayName(resolver, it), runCatching { resolver.getType(it) }.getOrNull()) } }
            when (val plan = planDrop(files, videoPlaying = true)) {
                is DropPlan.Play -> MediaUtils.openList(activity, plan.uris.map { MLServiceLocator.getAbstractMediaWrapper(Uri.parse(it)) }, 0)
                is DropPlan.LoadSubtitles -> add(files.filter { it.kind == DropKind.Subtitle }.map { Uri.parse(it.uri) to it.name })
                else -> files.firstOrNull()?.let { say(activity.getString(R.string.nag_sub_not_subtitle, it.name ?: it.uri)) }
            }
        }
    }

    /** The picker's files, with the names their documents give. */
    fun picked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = activity.contentResolver
        scope.launch {
            val named = withContext(Dispatchers.IO) { uris.map { it to displayName(resolver, it) } }
            add(named)
        }
    }

    /**
     * Loads [uri] (named [name]) into the playing film, read in its own character set; [select],
     * it shows. The file itself is kept with the film: as the film starts again VLC is told its
     * character set (S-702).
     */
    private suspend fun load(uri: Uri, name: String, select: Boolean): Boolean {
        val service = activity.service ?: return false
        val media = service.currentMediaWrapper ?: return false
        // a document picked or dropped stays readable when the film plays again
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching { activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        val context = activity.applicationContext
        val readable = withContext(Dispatchers.IO) { SubtitleEncodings.readable(context, media.uri.toString(), uri, name) }
        val done = runCatching { service.addSubtitleTrack(readable, select) }.getOrDefault(false)
        if (done) SlaveRepository.getInstance(activity).saveSlave(media.location, IMedia.Slave.Type.Subtitle, KEPT_PRIORITY, uri.toString())
        return done
    }

    /**
     * The online search's film (S-706): the playing one, its file's fingerprint, its folder for a
     * download (else the app's), which then shows; null without a film.
     */
    fun onlineFilm(): OnlineFilm? {
        val service = activity.service ?: return null
        val media = service.currentMediaWrapper ?: return null
        val video = media.uri ?: return null
        val context = activity.applicationContext
        val filmName = displayName(context.contentResolver, video) ?: media.title ?: return null
        return object : OnlineFilm {
            override val name: String = filmName

            override suspend fun hash(): String? = movieHash(context.contentResolver, video)

            override suspend fun save(fileName: String, bytes: ByteArray): SavedSubtitle? =
                withContext(Dispatchers.IO) { saveSubtitle(context, video, fileName, bytes) }

            override suspend fun load(saved: SavedSubtitle): Boolean {
                if (!this@PlayerSubtitles.load(Uri.parse(saved.uri), saved.name, select = true)) return false
                note = activity.getString(R.string.nag_online_note, saved.name)
                say(activity.getString(if (saved.beside) R.string.nag_online_saved_beside else R.string.nag_online_saved_app, saved.name))
                return true
            }
        }
    }

    /**
     * "Subtitle tools…" (Alt+E, S-708): the film's subtitle files (those VLC loaded and those
     * beside the film, SRT, WebVTT and ASS/SSA) in the tools' window, with the film itself at hand
     * for the editor: its position, going to a line, and the saved file shown at once.
     */
    fun openTools(tab: ToolsTab = ToolsTab.Clean) {
        val service = activity.service ?: return
        val video = service.currentMediaWrapper?.uri ?: return
        val context = activity.applicationContext
        val loaded = subtitleFiles()
        scope.launch {
            val files = withContext(Dispatchers.IO) { toolsFiles(context, video, loaded) }
            tools.open(files, playback(), tab)
        }
    }

    /** "Translate subtitles…" (S-710): the tools on their translation tab. */
    fun openTranslation() = openTools(ToolsTab.Translate)

    /** The film as the editor drives it: its clock is the subtitles' (the film's less their delay). */
    private fun playback() = object : ToolsPlayback {
        private fun delayMs(): Long = (activity.service?.spuDelay ?: 0L) / US_PER_MS

        override fun positionMs(): Long = ((activity.service?.getTime() ?: 0L) - delayMs()).coerceAtLeast(0)

        override fun seekTo(ms: Long) {
            activity.service?.seek((ms + delayMs()).coerceAtLeast(0), fromUser = true)
        }

        override suspend fun load(saved: SavedSubtitle): Boolean {
            if (!this@PlayerSubtitles.load(Uri.parse(saved.uri), saved.name, select = true)) return false
            note = activity.getString(R.string.nag_subtools_note, saved.name)
            return true
        }

        override suspend fun showTranslation(original: ToolsFile, saved: SavedSubtitle): Boolean {
            val dual = dual ?: return load(saved)
            dual.refresh()
            // no subtitles show: the original first, as the primary ones
            if (dual.state.primary == null) {
                if (!this@PlayerSubtitles.load(Uri.parse(original.uri), original.name, select = true)) return false
                if (!waitFor { dual.refresh(); dual.state.primary != null }) return false
            }
            val before = dual.state.tracks.map { it.id }.toSet()
            if (!this@PlayerSubtitles.load(Uri.parse(saved.uri), saved.name, select = false)) return false
            note = activity.getString(R.string.nag_subtools_note, saved.name)
            // the new track comes a moment after the file is added
            var added: String? = null
            if (!waitFor { dual.refresh(); added = dual.state.tracks.firstOrNull { it.id !in before }?.id; added != null }) return false
            dual.setSecondary(added)
            return dual.state.secondaryOn
        }

        override fun swapSubtitles(): Boolean = dual?.swap() ?: false
    }

    /** [ready] asked again every [WAIT_STEP_MS] for a few seconds; whether it came true. */
    private suspend fun waitFor(ready: () -> Boolean): Boolean {
        repeat(WAIT_STEPS) {
            if (ready()) return true
            delay(WAIT_STEP_MS)
        }
        return ready()
    }

    /**
     * "Generate subtitles…" (Alt+W, S-709): the dialog on the playing film, the audio track
     * playing chosen (its place among the film's audio tracks).
     */
    fun openWhisper() {
        val service = activity.service ?: return
        val media = service.currentMediaWrapper ?: return
        val video = media.uri ?: return
        val tracks = service.audioTracks.orEmpty().filter { it.getId() != NO_TRACK }
        val playing = tracks.indexOfFirst { it.getId() == service.audioTrack }
        whisper.open(video, media.fileName?.takeIf { it.isNotBlank() }, playing)
    }

    /** Subtitles a job made for the playing film (S-709), shown at once; a word when they could not be. */
    fun loadGenerated(saved: SavedSubtitle) {
        scope.launch {
            if (load(Uri.parse(saved.uri), saved.name, select = true)) {
                note = activity.getString(R.string.nag_whisper_note, saved.name)
                say(activity.getString(R.string.nag_sub_added, saved.name))
            } else {
                say(activity.getString(R.string.nag_sub_failed, saved.name))
            }
        }
    }

    /** "Subtitle encoding…": the film's first subtitle file, its head read; a word when it has none. */
    fun openEncoding() {
        val service = activity.service ?: return
        val media = service.currentMediaWrapper?.uri ?: return
        val context = activity.applicationContext
        val files = subtitleFiles()
        scope.launch {
            val sample = withContext(Dispatchers.IO) {
                files.firstNotNullOfOrNull { uri ->
                    val name = displayName(context.contentResolver, uri) ?: return@firstNotNullOfOrNull null
                    val head = SubtitleEncodings.head(context, uri) ?: return@firstNotNullOfOrNull null
                    if (!isTextSubtitle(name, head)) return@firstNotNullOfOrNull null
                    EncodingSample(name, head, detectEncoding(head), SubtitleEncodings.chosen(context, media))
                }
            }
            if (sample == null) say(activity.getString(R.string.nag_enc_none)) else encoding = sample
        }
    }

    fun closeEncoding() {
        encoding = null
    }

    /**
     * The character set chosen for the film (null: detected), kept for it: its subtitle files are
     * read in it at once, the first one shows; as the film starts again VLC is told it.
     */
    fun applyEncoding(chosen: SubtitleEncoding?) {
        val service = activity.service ?: return
        val media = service.currentMediaWrapper?.uri ?: return
        val context = activity.applicationContext
        val files = subtitleFiles()
        scope.launch {
            SubtitleEncodings.choose(context, media, chosen)
            val readable = withContext(Dispatchers.IO) {
                files.mapNotNull { uri ->
                    val name = displayName(context.contentResolver, uri) ?: return@mapNotNull null
                    val head = SubtitleEncodings.head(context, uri) ?: return@mapNotNull null
                    if (!isTextSubtitle(name, head)) return@mapNotNull null
                    SubtitleEncodings.readable(context, media.toString(), uri, name, chosen ?: detectEncoding(head))
                }
            }
            readable.forEachIndexed { index, uri -> runCatching { service.addSubtitleTrack(uri, index == 0) } }
            if (readable.isNotEmpty()) {
                say(if (chosen == null) activity.getString(R.string.nag_enc_applied_auto) else activity.getString(R.string.nag_enc_applied, activity.getString(chosen.label)))
            }
        }
    }

    /** The film's subtitle files as VLC loaded them, not the copies read into UTF-8. */
    private fun subtitleFiles(): List<Uri> {
        val media = activity.service?.mediaplayer?.media ?: return emptyList()
        val slaves = try {
            media.slaves?.filter { it.type == IMedia.Slave.Type.Subtitle }?.map { it.uri }.orEmpty()
        } finally {
            media.release()
        }
        return slaves.filterNot { SubtitleEncodings.isCopy(activity, it) }.distinct().map(Uri::parse)
    }

    /** Whether the playing film has subtitle files already: found by VLC beside a local file, or kept with it. */
    private fun hasSubtitleFiles(): Boolean {
        val media = activity.service?.mediaplayer?.media ?: return false
        return try {
            media.slaves?.any { it.type == IMedia.Slave.Type.Subtitle } == true
        } finally {
            media.release()
        }
    }

    private fun say(text: String) = activity.overlayDelegate.showInfo(text, SAID_MS)

    private companion object {
        /** The priority the classic player's picker keeps a subtitle with: the user's choice. */
        const val KEPT_PRIORITY = 2
        const val SAID_MS = 2_000

        /** The audio tracks' "off". */
        const val NO_TRACK = "-1"
        const val US_PER_MS = 1_000L
        const val WAIT_STEPS = 20
        const val WAIT_STEP_MS = 150L
    }
}

/** A document's or a file's name as the file manager shows it. */
internal fun displayName(resolver: ContentResolver, uri: Uri): String? {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.let { return it }
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}


/** The files beside a video, their names and where they are ([FolderFiles]: a folder, or a picked tree). */
internal fun siblingFiles(context: Context, video: Uri): List<Pair<String, String>> = FolderFiles.siblings(context, video)

/**
 * A downloaded subtitle file (S-706) beside [video]: in a local file's folder, or in the folder
 * chosen with the system's picker, through its document tree; where the app may not write, in
 * its own folder. Null when neither takes it.
 */
internal fun saveSubtitle(context: Context, video: Uri, fileName: String, bytes: ByteArray): SavedSubtitle? {
    saveBeside(context, video, fileName, bytes)?.let { uri ->
        return SavedSubtitle(uri.toString(), displayName(context.contentResolver, uri) ?: fileName, beside = true)
    }
    val folder = File(context.getExternalFilesDir(null) ?: context.filesDir, APP_SUBTITLES)
    return runCatching {
        folder.mkdirs()
        val file = writeNewFile(folder, fileName, bytes, source = null) ?: return@runCatching null
        SavedSubtitle(Uri.fromFile(file).toString(), file.name, beside = false)
    }.getOrNull()
}

/** [bytes] as a new file beside [video] (a local file's folder, or its picked tree); null where the app may not write. Never replaces a file. */
internal fun saveBeside(context: Context, video: Uri, fileName: String, bytes: ByteArray): Uri? = when (video.scheme) {
    null, ContentResolver.SCHEME_FILE -> runCatching {
        val folder = File(requireNotNull(video.path)).parentFile ?: error("no folder")
        val file = writeNewFile(folder, fileName, bytes, source = File(requireNotNull(video.path))) ?: return@runCatching null
        Uri.fromFile(file)
    }.getOrNull()
    ContentResolver.SCHEME_CONTENT -> treeSave(context, video, fileName, bytes)
    else -> null
}

private fun treeSave(context: Context, video: Uri, fileName: String, bytes: ByteArray): Uri? {
    val segments = video.pathSegments
    if (segments.size < 4 || segments[0] != "tree" || segments[2] != "document") return null
    val resolver = context.contentResolver
    val documentId = runCatching { DocumentsContract.getDocumentId(video) }.getOrNull() ?: return null
    val parentId = FolderFiles.parentDocument(resolver, video, documentId) ?: return null
    val folder = DocumentsContract.buildDocumentUriUsingTree(video, parentId)
    // the provider keeps the name as it is given, and numbers it when it is taken
    val created = runCatching { DocumentsContract.createDocument(resolver, folder, ANY_FILE, fileName) }.getOrNull() ?: return null
    val written = runCatching { resolver.openOutputStream(created)?.use { it.write(bytes) } != null }.getOrDefault(false)
    if (!written) {
        runCatching { DocumentsContract.deleteDocument(resolver, created) }
        return null
    }
    return created
}

private const val APP_SUBTITLES = "Subtitles"
private const val ANY_FILE = "application/octet-stream"

/** The folder a tree's document is in: the provider says so from Android 8; the external storage's ids are paths before. */

/**
 * The subtitle files the tools can work on for the film [video]: [loaded] (those VLC has) and
 * those beside the film by name, SRT, WebVTT, ASS and SSA only (pictures and other formats are not
 * text to edit); the ones in the user's language first.
 */
internal fun toolsFiles(context: Context, video: Uri, loaded: List<Uri>): List<ToolsFile> {
    val resolver = context.contentResolver
    val beside = displayName(resolver, video)?.let { name ->
        matchSubtitles(name, siblingFiles(context, video), prefer = Locale.getDefault().language).map { ToolsFile(it.uri, it.name) }
    }.orEmpty()
    val kept = loaded.mapNotNull { uri -> displayName(resolver, uri)?.let { ToolsFile(uri.toString(), it) } }
    return (beside + kept).filter { SubtitleFormat.ofName(it.name) != null }.distinctBy { it.uri }
}
