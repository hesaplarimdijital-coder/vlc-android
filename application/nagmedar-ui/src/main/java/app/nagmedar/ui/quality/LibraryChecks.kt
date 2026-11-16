package app.nagmedar.ui.quality

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.tags.ContentRef
import app.nagmedar.tags.LibraryFile
import app.nagmedar.tags.artwork.LibraryCoverScanner
import app.nagmedar.tags.artwork.LibraryCoverState
import app.nagmedar.tags.files.FileCoverCheckStore
import app.nagmedar.tags.files.FileQualityStore
import app.nagmedar.tags.files.LocalAudioFiles
import app.nagmedar.tags.quality.CheckLosslessUseCase
import app.nagmedar.tags.quality.LibraryQualityScanner
import app.nagmedar.tags.quality.LibraryQualityState
import app.nagmedar.tags.quality.LosslessCheckResult
import app.nagmedar.tags.quality.LosslessVerdict
import app.nagmedar.tags.quality.TrackQuality
import app.nagmedar.ui.tags.TagCenter
import app.nagmedar.ui.tags.TagSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.AppScope

/**
 * 1.4.0, Metamatik's checks of the library, in the background and once for each version of a file, what they found kept
 * in the app's own storage:
 * - every FLAC and WAV file's audio, for whether it has been through a lossy encoder on its way ("Sahte FLAC");
 * - every file's tags: whether it holds a cover ("Kapaksızlar"), which of the tags a song is filed by it lacks
 *   ("Düzeltilecekler"), whether the text it is listed by looks garbled ("Bozuk yazılılar").
 *
 * The Music page hands over its files ([submit]); Settings › Etiket düzenleyici turns the checks off. Nothing leaves the
 * device.
 */
object LibraryChecks {

    private class Scanners(val quality: LibraryQualityScanner, val tags: LibraryCoverScanner, val check: CheckLosslessUseCase)

    @Volatile
    private var built: Scanners? = null

    private val _quality = MutableStateFlow(LibraryQualityState())

    /** What the lossless check found, by URI, and how far it has got. */
    val quality: StateFlow<LibraryQualityState> = _quality.asStateFlow()

    private val _tags = MutableStateFlow(LibraryCoverState())

    /** What the read of the files' tags found, by URI, and how far it has got. */
    val tags: StateFlow<LibraryCoverState> = _tags.asStateFlow()

    private fun scanners(context: Context): Scanners = built ?: synchronized(this) {
        built ?: build(context.applicationContext).also { built = it }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun build(context: Context): Scanners {
        val folder = File(context.filesDir, "library-checks")
        val check = CheckLosslessUseCase(LocalAudioFiles(context))
        // one file at a time, after the work that matters more: the probe of the formats, a batch being saved
        val lowPriority = Dispatchers.Default.limitedParallelism(1)
        val quality = LibraryQualityScanner(check, FileQualityStore(File(folder, "lossless.lines")), AppScope, default = lowPriority)
        val batch = TagCenter.files(context).batch
        val tags = LibraryCoverScanner(
            TagCenter.files(context).reader,
            FileCoverCheckStore(File(folder, "tags.lines")),
            AppScope,
            busy = batch.progress.map { it?.isRunning == true },
            default = lowPriority,
        )
        AppScope.launch { quality.state.collect { _quality.value = it } }
        AppScope.launch { tags.state.collect { _tags.value = it } }
        return Scanners(quality, tags, check)
    }

    /** What the lossless check knows of [uri]'s file now. */
    fun qualityOf(uri: String): TrackQuality = _quality.value.of(CueUri.file(uri))

    /** Whether Settings lets the library be checked. */
    fun enabled(context: Context): Boolean = TagSettings.libraryChecks(context)

    /** The files last handed over, for the checks turned on again. */
    @Volatile
    private var last: List<String> = emptyList()

    /**
     * Settings' switch: off, the checks stop after the file they are at (what they found is kept, and its lists are
     * empty until they are on again); on, they go on with the library's files.
     */
    fun setEnabled(context: Context, on: Boolean) {
        TagSettings.updateLibraryChecks(context, on)
        val app = context.applicationContext
        AppScope.launch {
            if (on) {
                submit(app, last)
            } else {
                built?.let { scanners ->
                    scanners.quality.submit(emptyList())
                    scanners.tags.submit(emptyList())
                }
            }
        }
    }

    /**
     * The Music page's files, as it has them now: each checked in the background once for each version of it. A CUE
     * sheet's tracks share their file, checked once. Nothing while Settings has the checks off.
     */
    suspend fun submit(context: Context, uris: List<String>) {
        last = uris
        if (!enabled(context)) return
        val files = withContext(Dispatchers.IO) {
            uris.asSequence().map(CueUri::file).distinct().map { uri -> libraryFile(context, uri) }.toList()
        }
        val scanners = scanners(context)
        scanners.quality.submit(files)
        scanners.tags.submit(files)
    }

    /** The check of one file, for the editor: the same as the library's. */
    fun lossless(context: Context): suspend (ContentRef) -> LosslessCheckResult {
        val check = scanners(context).check
        return { ref -> check(ref) }
    }

    /** The editor's verdict on [ref]'s file, which the library then has too, without checking it again. */
    fun record(context: Context, ref: ContentRef, verdict: LosslessVerdict?) {
        val app = context.applicationContext
        AppScope.launch {
            val file = withContext(Dispatchers.IO) { libraryFile(app, ref.uri) }
            scanners(app).quality.record(file, verdict)
        }
    }

    /** [uri] as the checks see it: its name, its size and when it was last written, which say whether it changed. */
    internal fun libraryFile(context: Context, uri: String): LibraryFile {
        val parsed = Uri.parse(uri)
        val name = parsed.lastPathSegment?.substringAfterLast('/') ?: uri
        val stamp = runCatching { stamp(context, parsed) }.getOrNull()
        return LibraryFile(ContentRef(uri), name, sizeBytes = stamp?.first ?: 0L, dateModifiedEpochSeconds = (stamp?.second ?: 0L) / 1000)
    }

    /** A file's size and its modification time in milliseconds, as the probe of the formats reads them. */
    private fun stamp(context: Context, uri: Uri): Pair<Long, Long>? = when (uri.scheme) {
        null, "file" -> File(uri.path ?: return null).takeIf { it.isFile }?.let { it.length() to it.lastModified() }
        "content" -> (column(context, uri, OpenableColumns.SIZE) ?: return null) to (column(context, uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0L)
        else -> null
    }

    private fun column(context: Context, uri: Uri, name: String): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(name), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()
}
