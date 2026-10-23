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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.data.MediaTechDao
import app.nagmedar.data.MediaTechEntry
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.lab.MediaProbe
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.NativeLab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
class TechRepository internal constructor(
    private val context: Context,
    private val dao: MediaTechDao = NagmedarDatabase.get(context).mediaTech(),
) {
    private val scope = CoroutineScope(SupervisorJob() + probeThread)
    private val queued: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    /**
     * Every current probe by media URI, read and parsed once for every screen that shows them and kept: a screen
     * that comes back starts with what was read last instead of an empty map, and is told when it changes.
     */
    val all: StateFlow<Map<String, MediaTech>> = dao.all()
        .map { rows ->
            rows.asSequence()
                .filter { it.probeVersion == MediaProbe.VERSION }
                .mapNotNull { row -> MediaProbe.parse(row.json)?.let { row.mediaUri to it } }
                .toMap()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.WhileSubscribed(KEPT_FOLLOWING_MS), emptyMap())

    /**
     * The current probe of one file ([key], a media URI) when it has one and the file is still the
     * one probed (its size and modification time): the player asks it (S-606).
     */
    suspend fun cached(key: String): MediaTech? = withContext(Dispatchers.IO) { current(key, stamp(Uri.parse(key))) }

    /** Probes the [keys] (media URIs) that have no current result, in the background. */
    fun probeMissing(keys: List<String>) {
        // the screens ask on the main thread: the lab's library is loaded on the probe's
        if (NativeLab.loaded == false) return
        for (key in keys) {
            if (!queued.add(key)) continue
            scope.launch {
                try {
                    if (NativeLab.available) probeIfStale(key)
                } finally {
                    queued.remove(key)
                }
            }
        }
    }

    /**
     * The current probe of one file, made now when there is none or the file changed since it was
     * probed (S-507: before a film starts; a film replaced in place has other tracks); null when it
     * cannot be.
     */
    suspend fun probeNow(key: String): MediaTech? = withContext(Dispatchers.IO) {
        val stamp = stamp(Uri.parse(key))
        current(key, stamp) ?: run {
            if (NativeLab.available && stamp != null) probe(key, stamp)
            current(key, stamp)
        }
    }

    private fun probeIfStale(key: String) {
        val stamp = stamp(Uri.parse(key)) ?: return
        if (dao.get(key)?.describes(stamp) == true) return
        probe(key, stamp)
    }

    private fun probe(key: String, stamp: Pair<Long, Long>) {
        val json = MediaProbe.probe(context, Uri.parse(key)) ?: return
        // an unreadable file is not cached: it may be on a drive that comes back
        if (MediaProbe.parse(json) == null) return
        dao.save(MediaTechEntry(key, stamp.first, stamp.second, MediaProbe.VERSION, json, System.currentTimeMillis()))
    }

    /** [key]'s cached probe when it [describes] the file as [stamp] says it is now. */
    private fun current(key: String, stamp: Pair<Long, Long>?): MediaTech? =
        dao.get(key)?.takeIf { it.describes(stamp) }?.let { MediaProbe.parse(it.json) }

    /** Size and modification time, which say whether a cached result still describes the file. */
    private fun stamp(uri: Uri): Pair<Long, Long>? = runCatching {
        when (uri.scheme) {
            null, "file" -> File(uri.path ?: return null).takeIf { it.isFile }?.let { it.length() to it.lastModified() }
            // one column per query: a provider may refuse a column it does not have
            "content" -> (column(uri, OpenableColumns.SIZE) ?: return null) to (column(uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0L)
            else -> null
        }
    }.getOrNull()

    /**
     * Whether this cached probe is the current probe's and of the file as it is: the same size and
     * modification time ([stamp]; null when the file does not say now, which keeps what was probed).
     */
    private fun MediaTechEntry.describes(stamp: Pair<Long, Long>?): Boolean =
        probeVersion == MediaProbe.VERSION && (stamp == null || (fileSize == stamp.first && modifiedAt == stamp.second))

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

        /** How long [all] goes on following the database once no screen shows it. */
        private const val KEPT_FOLLOWING_MS = 60_000L

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
    val flow = remember(repository) { repository?.all ?: MutableStateFlow(emptyMap()) }
    return flow.collectAsState()
}

/** The cached probe of one file ([key], a media URI), probing it when it has none; null meanwhile. */
@Composable
fun rememberTechOf(key: String?): MediaTech? {
    val context = LocalContext.current
    val repository = remember(context) { runCatching { TechRepository.get(context) }.getOrNull() }
    LaunchedEffect(repository, key) {
        if (repository != null && key != null) repository.probeMissing(listOf(key))
    }
    val flow = remember(repository) { repository?.all ?: MutableStateFlow(emptyMap()) }
    val all by flow.collectAsState()
    return key?.let { all[it] }
}
