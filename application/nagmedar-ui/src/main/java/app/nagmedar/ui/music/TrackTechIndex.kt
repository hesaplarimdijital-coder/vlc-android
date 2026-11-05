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
 * S-803: the engine's probe of every music file (depth, rate, DSD, codec, the composer; 1.0.3: its
 * ReplayGain tags), kept in Nağmedar's database and read again only when a file changes (its size
 * or modification time) or the probe reads more ([PROBE_VERSION]). [tech] and [gains] have what is
 * known; [update] probes the rest in the background, publishing as it goes. Without the engine
 * nothing is probed.
 */
object TrackTechIndex {

    /**
     * Bump when the probe reads more: every file is read again, once, in the background; until then
     * an earlier version's row gives what it read, so the table's columns stay filled, and a file
     * read again the same publishes no new [tech] (the music list is not made again for it). 2
     * (1.0.3): the ReplayGain tags.
     */
    const val PROBE_VERSION = 2

    /** The first version whose rows carry the ReplayGain tags; an earlier row's are not known yet. */
    private const val REPLAY_GAIN_SINCE = 2

    private const val TAG = "TrackTechIndex"

    /** Files probed between two saves and two publications. */
    private const val BATCH = 24

    private val _tech = MutableStateFlow<Map<String, TrackTech>>(emptyMap())

    /** What the probe read, by media URI. */
    val tech: StateFlow<Map<String, TrackTech>> = _tech.asStateFlow()

    private val _gains = MutableStateFlow<Map<String, ReplayGainTags>>(emptyMap())

    /**
     * 1.0.3: the ReplayGain tags the probe read, by media URI, for the inspector; a file not read for
     * them yet (a row of an earlier probe, a file the probe could not read) is not in it. Apart from
     * [tech], so that the tags read for the first time do not make the music list again.
     */
    val gains: StateFlow<Map<String, ReplayGainTags>> = _gains.asStateFlow()

    private val _remaining = MutableStateFlow(0)

    /** Files left to probe in the update under way. */
    val remaining: StateFlow<Int> = _remaining.asStateFlow()

    private val lock = Mutex()

    @Volatile
    private var known: MutableMap<String, AudioTechEntry>? = null

    /**
     * 1.0.3: what the probe has read, for a reader without the Music page (Android Auto's music): [tech] once an update
     * has loaded it, else the database's rows as they are. Nothing is probed or kept, and an update under way (which
     * holds its lock for as long as it probes) is never waited for.
     */
    suspend fun read(context: Context): Map<String, TrackTech> {
        if (known != null) return _tech.value
        return withContext(Dispatchers.IO) {
            runCatching { NagmedarDatabase.get(context).audioTech().all() }.getOrNull().orEmpty()
                .mapNotNull { row -> row.toTech()?.let { row.mediaUri to it } }.toMap()
        }
    }

    /** Loads what is known, then probes the [uris] that are new or changed. */
    suspend fun update(context: Context, uris: List<String>) = withContext(Dispatchers.IO) {
        lock.withLock {
            val dao = runCatching { NagmedarDatabase.get(context).audioTech() }.getOrNull() ?: return@withLock
            val cache = known ?: dao.all().associateByTo(HashMap()) { it.mediaUri }.also { rows ->
                // published before it is known: a reader that finds it known finds it published ([known])
                _tech.value = rows.values.mapNotNull { row -> row.toTech()?.let { row.mediaUri to it } }.toMap()
                // the tags read for the inspector meanwhile ([readGains]) are as new as these
                val stored = rows.values.mapNotNull { row -> row.gains()?.let { row.mediaUri to it } }.toMap()
                _gains.update { read -> stored + read }
                known = rows
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
                    // a file read again the same leaves the map equal: no new value, the list stays as it is
                    _tech.update { map -> map + rows.mapNotNull { row -> row.toTech()?.let { row.mediaUri to it } } }
                    _gains.update { map -> map + rows.mapNotNull { row -> row.gains()?.let { row.mediaUri to it } } }
                    _remaining.update { (it - batch.size).coerceAtLeast(0) }
                }
            } finally {
                _remaining.value = 0
            }
        }
    }

    /**
     * 1.0.3: the ReplayGain tags of the file [uri] for the inspector, read now when the probe has not
     * read them yet: after the probe's update a large library's files are read again one after the
     * other, and the track looked at should not wait for them. Published in [gains] only; the update
     * keeps them with the file's row when it comes to it. Reads the file: off the main thread.
     */
    suspend fun readGains(context: Context, uri: String) {
        if (uri in _gains.value) return
        val info = withContext(Dispatchers.IO) {
            if (!MusicEngine.available) return@withContext null
            runCatching { LoudnessScanner.probe(context, Uri.parse(uri)) }.onFailure { Log.w(TAG, "probe of $uri failed: $it") }.getOrNull()
        } ?: return
        _gains.update { it + (uri to ReplayGainTags(info.trackGainDb, info.trackPeak, info.albumGainDb, info.albumPeak)) }
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
            trackGainDb = info?.trackGainDb,
            trackPeak = info?.trackPeak,
            albumGainDb = info?.albumGainDb,
            albumPeak = info?.albumPeak,
        )
    }

    /** What a row says of its file; a row of an earlier probe too, which read all of it. */
    internal fun AudioTechEntry.toTech(): TrackTech? =
        if (probeVersion !in 1..PROBE_VERSION || rate <= 0) null else TrackTech(codec, container, rate, bits, channels, isFloat, dsdRate, bitrate, composer)

    /** The ReplayGain tags of a row whose probe read them; null for an earlier probe's row or a file it could not read. */
    internal fun AudioTechEntry.gains(): ReplayGainTags? =
        if (probeVersion !in REPLAY_GAIN_SINCE..PROBE_VERSION || rate <= 0) null else ReplayGainTags(trackGainDb, trackPeak, albumGainDb, albumPeak)

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
