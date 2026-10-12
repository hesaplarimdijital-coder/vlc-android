package app.nagmedar.ui.player.subtitles

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.ui.R
import app.nagmedar.ui.input.DropKind
import app.nagmedar.ui.input.DropPlan
import app.nagmedar.ui.input.DroppedFile
import app.nagmedar.ui.input.planDrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
                if (load(Uri.parse(only.uri), select = true)) say(activity.getString(R.string.nag_sub_added_from_folder, only.name))
            } else {
                found = candidates
            }
        }
    }

    /** The one chosen from those found: it shows. */
    fun choose(candidate: SubtitleCandidate) {
        found = emptyList()
        if (load(Uri.parse(candidate.uri), select = true)) say(activity.getString(R.string.nag_sub_added, candidate.name))
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
        val added = subtitles.filterIndexed { index, (uri, _) -> load(uri, select = index == 0) }
        when {
            added.isEmpty() -> say(activity.getString(R.string.nag_sub_failed, subtitles.first().let { (uri, name) -> name ?: uri.lastPathSegment.orEmpty() }))
            added.size == 1 -> say(activity.getString(R.string.nag_sub_added, added.single().let { (uri, name) -> name ?: uri.lastPathSegment.orEmpty() }))
            else -> say(activity.resources.getQuantityString(R.plurals.nag_drop_subtitles_loaded, added.size, added.size))
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

    /** Loads [uri] into the playing film, kept with it; [select], it shows. */
    private fun load(uri: Uri, select: Boolean): Boolean {
        val service = activity.service ?: return false
        val media = service.currentMediaWrapper ?: return false
        // a document picked or dropped stays readable when the film plays again
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching { activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        val done = runCatching { service.addSubtitleTrack(uri, select) }.getOrDefault(false)
        if (done) SlaveRepository.getInstance(activity).saveSlave(media.location, IMedia.Slave.Type.Subtitle, KEPT_PRIORITY, uri.toString())
        return done
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

private val SIBLINGS = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
)

/**
 * The files beside a video, their names and where they are: a local file's folder, read
 * directly, or a folder chosen with the system's picker (S-309), read through its document tree.
 * A document another app shared alone, or a stream, has no folder to read.
 */
internal fun siblingFiles(context: Context, video: Uri): List<Pair<String, String>> = when (video.scheme) {
    null, ContentResolver.SCHEME_FILE -> {
        val folder = video.path?.let { File(it).parentFile }
        folder?.listFiles()?.filter { it.isFile }?.map { it.name to Uri.fromFile(it).toString() }.orEmpty()
    }
    ContentResolver.SCHEME_CONTENT -> treeSiblings(context, video)
    else -> emptyList()
}

private fun treeSiblings(context: Context, video: Uri): List<Pair<String, String>> {
    // content://…/tree/<root>/document/<document>: a folder's tree the app may read
    val segments = video.pathSegments
    if (segments.size < 4 || segments[0] != "tree" || segments[2] != "document") return emptyList()
    val resolver = context.contentResolver
    val documentId = runCatching { DocumentsContract.getDocumentId(video) }.getOrNull() ?: return emptyList()
    val parentId = parentDocument(resolver, video, documentId) ?: return emptyList()
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(video, parentId)
    val cursor = runCatching { resolver.query(children, SIBLINGS, null, null, null) }.getOrNull() ?: return emptyList()
    return cursor.use { c ->
        buildList {
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                add(name to DocumentsContract.buildDocumentUriUsingTree(video, id).toString())
            }
        }
    }
}

/** The folder a tree's document is in: the provider says so from Android 8; the external storage's ids are paths before. */
private fun parentDocument(resolver: ContentResolver, video: Uri, documentId: String): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val path = runCatching { DocumentsContract.findDocumentPath(resolver, video) }.getOrNull()?.path
        if (path != null && path.size >= 2) return path[path.size - 2]
    }
    // "primary:Movies/Film.mkv" is in "primary:Movies", "primary:Film.mkv" in "primary:"
    return when {
        '/' in documentId -> documentId.substringBeforeLast('/')
        ':' in documentId && !documentId.endsWith(':') -> documentId.substringBefore(':') + ":"
        else -> null
    }
}
