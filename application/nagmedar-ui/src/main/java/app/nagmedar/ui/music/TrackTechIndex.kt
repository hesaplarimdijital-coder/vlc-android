package app.nagmedar.ui.music

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import app.nagmedar.audio.LoudnessScanner
import app.nagmedar.audio.MusicEngine
import app.nagmedar.data.AudioTechEntry
import app.nagmedar.data.NagmedarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * S-803: the engine's probe of every music file (depth, rate, DSD, codec, the composer), kept in
 * Nağmedar's database and read again only when a file changes (its size or modification time)
 * or the probe reads more ([PROBE_VERSION]). [tech] has what is known; [update] probes the rest
 * in the background, publishing as it goes. Without the engine nothing is probed.
 */
object TrackTechIndex {

    /** Bump when the probe reads more: every file is read again. */
    const val PROBE_VERSION = 1

    private const val TAG = "TrackTechIndex"

    /** Files probed between two saves and two publications. */
    private const val BATCH = 24

    private val _tech = MutableStateFlow<Map<String, TrackTech>>(emptyMap())

    /** What the probe read, by media URI. */
    val tech: StateFlow<Map<String, TrackTech>> = _tech.asStateFlow()

    private val _remaining = MutableStateFlow(0)

    /** Files left to probe in the update under way. */
    val remaining: StateFlow<Int> = _remaining.asStateFlow()

    private val lock = Mutex()
    private var known: MutableMap<String, AudioTechEntry>? = null

    /** Loads what is known, then probes the [uris] that are new or changed. */
    suspend fun update(context: Context, uris: List<String>) = withContext(Dispatchers.IO) {
        lock.withLock {
            val dao = runCatching { NagmedarDatabase.get(context).audioTech() }.getOrNull() ?: return@withLock
            val cache = known ?: dao.all().associateByTo(HashMap()) { it.mediaUri }.also { rows ->
                known = rows
                _tech.value = rows.values.mapNotNull { row -> row.toTech()?.let { row.mediaUri to it } }.toMap()
            }
            if (!MusicEngine.available) return@withLock
            val stale = uris.mapNotNull { uri ->
                val stamp = stamp(context, Uri.parse(uri)) ?: return@mapNotNull null
                val row = cache[uri]
                if (row != null && row.probeVersion == PROBE_VERSION && row.fileSize == stamp.first && row.modifiedAt == stamp.second) null else uri to stamp
            }
            _remaining.value = stale.size
            try {
                for (batch in stale.chunked(BATCH)) {
                    coroutineContext.ensureActive()
                    val rows = batch.map { (uri, stamp) -> probe(context, uri, stamp) }
                    dao.save(rows)
                    rows.forEach { cache[it.mediaUri] = it }
                    _tech.update { map -> map + rows.mapNotNull { row -> row.toTech()?.let { row.mediaUri to it } } }
                    _remaining.update { (it - batch.size).coerceAtLeast(0) }
                }
            } finally {
                _remaining.value = 0
            }
        }
    }

    /** A file the probe cannot read is kept too, empty, so it is not read again until it changes. */
    private fun probe(context: Context, uri: String, stamp: Pair<Long, Long>): AudioTechEntry {
        val info = runCatching { LoudnessScanner.probe(context, Uri.parse(uri)) }
            .onFailure { Log.w(TAG, "probe of $uri failed: $it") }
            .getOrNull()
        return AudioTechEntry(
            mediaUri = uri,
            fileSize = stamp.first,
            modifiedAt = stamp.second,
            probeVersion = PROBE_VERSION,
            codec = info?.codec.orEmpty(),
            container = info?.container.orEmpty(),
            rate = info?.rate ?: 0,
            channels = info?.channels ?: 0,
            bits = info?.bits ?: 0,
            isFloat = info?.isFloat ?: false,
            dsdRate = info?.dsdRate ?: 0,
            bitrate = info?.bitrate ?: 0,
            durationMs = info?.durationMs ?: 0,
            composer = info?.composer,
            probedAt = System.currentTimeMillis(),
        )
    }

    private fun AudioTechEntry.toTech(): TrackTech? =
        if (probeVersion != PROBE_VERSION || rate <= 0) null else TrackTech(codec, container, rate, bits, channels, isFloat, dsdRate, bitrate, composer)

    /** Size and modification time, which say whether what was probed still describes the file. */
    private fun stamp(context: Context, uri: Uri): Pair<Long, Long>? = runCatching {
        when (uri.scheme) {
            null, "file" -> File(uri.path ?: return null).takeIf { it.isFile }?.let { it.length() to it.lastModified() }
            "content" -> (column(context, uri, OpenableColumns.SIZE) ?: return null) to (column(context, uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0L)
            else -> null
        }
    }.getOrNull()

    private fun column(context: Context, uri: Uri, name: String): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(name), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()
}
