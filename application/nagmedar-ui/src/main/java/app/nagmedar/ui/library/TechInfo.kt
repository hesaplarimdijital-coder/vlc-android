package app.nagmedar.ui.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.data.MediaTechEntry
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.lab.MediaProbe
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.NativeLab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** A video as the library screens show it; [media] is the medialibrary's item (null in tests). */
@Immutable
data class VideoItem(
    /** The media URI: the key of the probe cache and of the screens' lists. */
    val key: String,
    val title: String,
    val durationMs: Long,
    /** Watched fraction of a video in progress, 0 otherwise. */
    val progress: Float,
    val width: Int,
    val height: Int,
    val media: MediaWrapper? = null,
    /** The file's path, for episode and folder names (S-801). */
    val path: String = "",
    /** When the medialibrary added it, for "Sırala: Eklenme". */
    val addedAt: Long = 0,
    /** How often it was watched to the end. */
    val playCount: Long = 0,
    /** Languages of the subtitle files next to it (ISO 639-2, "und" when the name has none). */
    val sidecarSubtitles: Set<String> = emptySet(),
) {
    /** Where playback resumes, for "Kaldığı yer". */
    val resumeMs: Long get() = (durationMs * progress).toLong()

    /** The folder it is in. */
    val folder: String get() = path.substringBeforeLast('/', "")

    val watched: Boolean get() = playCount > 0
}

fun MediaWrapper.toVideoItem(sidecars: Set<String> = emptySet()) = VideoItem(
    key = uri.toString(),
    title = title ?: uri.lastPathSegment.orEmpty(),
    durationMs = length,
    progress = watchProgress(),
    width = width,
    height = height,
    media = this,
    path = uri.path.orEmpty(),
    addedAt = insertionDate,
    playCount = seen,
    sidecarSubtitles = sidecars,
)

/**
 * S-802: the media probe's results for the library, cached in Room per file. Cached results are
 * shown at once; files without one, or changed since (size, modification time) or probed by an
 * older probe, are probed one at a time on a low-priority thread, and each result appears as
 * soon as it is saved. Without the native library nothing is probed and the screens fall back
 * to what the medialibrary knows (the resolution).
 */
class TechRepository private constructor(private val context: Context) {

    private val dao = NagmedarDatabase.get(context).mediaTech()
    private val scope = CoroutineScope(SupervisorJob() + probeThread)
    private val queued: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    /** Every current probe by media URI. */
    val all: Flow<Map<String, MediaTech>> = dao.all()
        .map { rows ->
            rows.asSequence()
                .filter { it.probeVersion == MediaProbe.VERSION }
                .mapNotNull { row -> MediaProbe.parse(row.json)?.let { row.mediaUri to it } }
                .toMap()
        }
        .flowOn(Dispatchers.Default)

    /** Probes the [keys] (media URIs) that have no current result, in the background. */
    fun probeMissing(keys: List<String>) {
        if (!NativeLab.available) return
        for (key in keys) {
            if (!queued.add(key)) continue
            scope.launch {
                try {
                    probeIfStale(key)
                } finally {
                    queued.remove(key)
                }
            }
        }
    }

    private fun probeIfStale(key: String) {
        val uri = Uri.parse(key)
        val (size, modified) = stamp(uri) ?: return
        val cached = dao.get(key)
        if (cached != null && cached.probeVersion == MediaProbe.VERSION && cached.fileSize == size && cached.modifiedAt == modified) return
        val json = MediaProbe.probe(context, uri) ?: return
        // an unreadable file is not cached: it may be on a drive that comes back
        if (MediaProbe.parse(json) == null) return
        dao.save(MediaTechEntry(key, size, modified, MediaProbe.VERSION, json, System.currentTimeMillis()))
    }

    /** Size and modification time, which say whether a cached result still describes the file. */
    private fun stamp(uri: Uri): Pair<Long, Long>? = runCatching {
        when (uri.scheme) {
            null, "file" -> File(uri.path ?: return null).takeIf { it.isFile }?.let { it.length() to it.lastModified() }
            // one column per query: a provider may refuse a column it does not have
            "content" -> (column(uri, OpenableColumns.SIZE) ?: return null) to (column(uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0L)
            else -> null
        }
    }.getOrNull()

    private fun column(uri: Uri, name: String): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(name), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    companion object {
        /** One background thread for every probe: the library stays responsive while a drive is read. */
        private val probeThread = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "nagmedar-probe").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }.asCoroutineDispatcher()

        @Volatile
        private var instance: TechRepository? = null

        fun get(context: Context): TechRepository = instance ?: synchronized(this) {
            instance ?: TechRepository(context.applicationContext).also { instance = it }
        }
    }
}

/** The cached probes, probing [items] that lack one; empty in previews and tests. */
@Composable
fun rememberTech(items: List<VideoItem>?): State<Map<String, MediaTech>> {
    val context = LocalContext.current
    val repository = remember(context) { runCatching { TechRepository.get(context) }.getOrNull() }
    LaunchedEffect(repository, items) {
        if (repository != null && items != null) repository.probeMissing(items.map { it.key })
    }
    val flow = remember(repository) { repository?.all ?: flowOf(emptyMap()) }
    return flow.collectAsState(initial = emptyMap())
}
