package app.nagmedar.ui.perf

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/*
 * S-1404: what Nağmedar measures of itself on the device, without a computer: how long a cold
 * start-up took to its first frame, and each playback's shown and dropped frames and the battery
 * it used. The last ten of each are kept for the Performance card; each one is also written to
 * the log as a "NagmedarPerf" line, which the macrobenchmarks (application/benchmark) read.
 */

/** A cold start-up: [ms] from the launch to the first frame, [atMs] when it was (the wall clock). */
@Immutable
data class StartupRecord(val atMs: Long, val ms: Long)

/**
 * A playback: the film's [name] and picture ([width]×[height], 0 when not known; [range] as the
 * library says it, "HDR10", "DV P8", or empty), the [seconds] it played, the frames it [shown]
 * and [dropped] and the audio buffers it lost; the battery it used in an hour, null while it
 * charged or when it was too short to tell.
 */
@Immutable
data class PlaybackRecord(
    val atMs: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val range: String,
    val seconds: Int,
    val shown: Long,
    val dropped: Long,
    val audioLost: Long,
    val batteryPerHour: Double?,
) {
    /** The dropped frames, in percent of the frames due. */
    val droppedPercent: Double get() = if (shown + dropped > 0) dropped * 100.0 / (shown + dropped) else 0.0
}

/** What the Performance card and the macrobenchmarks hold Nağmedar to. */
object PerfTargets {
    /** A cold start-up shows its first frame within a second. */
    const val STARTUP_MS = 1_000L

    /** At most one frame in a thousand is dropped. */
    const val DROPPED_PERCENT = 0.1

    fun startupOk(ms: Long) = ms <= STARTUP_MS

    fun playbackOk(record: PlaybackRecord) = record.droppedPercent <= DROPPED_PERCENT
}

/**
 * The log's lines, one a record: "startup cold_ms=640"; "playback file="Film.mkv" width=3840
 * height=2160 range=HDR10 seconds=10 shown=240 dropped=0 dropped_pct=0.000 audio_lost=0
 * battery_pct_h=-". Numbers as Locale.ROOT writes them, for the scripts that read them.
 */
object PerfLog {
    const val TAG = "NagmedarPerf"

    fun line(record: StartupRecord) = "startup cold_ms=${record.ms}"

    fun line(record: PlaybackRecord): String = buildString {
        append("playback file=\"").append(record.name.replace('"', '\'')).append('"')
        append(" width=").append(record.width)
        append(" height=").append(record.height)
        append(" range=").append(record.range.ifEmpty { "-" }.replace(' ', '_'))
        append(" seconds=").append(record.seconds)
        append(" shown=").append(record.shown)
        append(" dropped=").append(record.dropped)
        append(" dropped_pct=").append(String.format(Locale.ROOT, "%.3f", record.droppedPercent))
        append(" audio_lost=").append(record.audioLost)
        append(" battery_pct_h=").append(record.batteryPerHour?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "-")
    }
}

/** The records kept, the newest first. */
interface PerfStore {
    fun startups(): List<StartupRecord>

    fun playbacks(): List<PlaybackRecord>

    fun add(record: StartupRecord)

    fun add(record: PlaybackRecord)

    fun clear()
}

/** A record kept and written to the log. */
fun PerfStore.keep(record: StartupRecord) {
    Log.i(PerfLog.TAG, PerfLog.line(record))
    add(record)
}

fun PerfStore.keep(record: PlaybackRecord) {
    Log.i(PerfLog.TAG, PerfLog.line(record))
    add(record)
}

/** The records as JSON arrays, the newest first; what cannot be read is left out. */
object PerfCodec {
    const val KEEP = 10

    fun startups(json: String?): List<StartupRecord> = read(json) { StartupRecord(it.getLong("at"), it.getLong("ms")) }

    fun playbacks(json: String?): List<PlaybackRecord> = read(json) {
        PlaybackRecord(
            atMs = it.getLong("at"),
            name = it.getString("name"),
            width = it.optInt("width"),
            height = it.optInt("height"),
            range = it.optString("range"),
            seconds = it.getInt("seconds"),
            shown = it.getLong("shown"),
            dropped = it.getLong("dropped"),
            audioLost = it.optLong("audio_lost"),
            batteryPerHour = if (it.has("battery")) it.getDouble("battery") else null,
        )
    }

    fun encodeStartups(records: List<StartupRecord>): String =
        JSONArray(records.take(KEEP).map { JSONObject().put("at", it.atMs).put("ms", it.ms) }).toString()

    fun encodePlaybacks(records: List<PlaybackRecord>): String = JSONArray(
        records.take(KEEP).map {
            JSONObject()
                .put("at", it.atMs)
                .put("name", it.name)
                .put("width", it.width)
                .put("height", it.height)
                .put("range", it.range)
                .put("seconds", it.seconds)
                .put("shown", it.shown)
                .put("dropped", it.dropped)
                .put("audio_lost", it.audioLost)
                .apply { it.batteryPerHour?.let { battery -> put("battery", battery) } }
        },
    ).toString()

    private fun <T> read(json: String?, record: (JSONObject) -> T): List<T> {
        val array = json?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { runCatching { record(it) }.getOrNull() } }
    }
}

/** The records in the app's preferences ("nagmedar_perf"). */
class PrefsPerfStore(private val prefs: SharedPreferences) : PerfStore {

    override fun startups(): List<StartupRecord> = runCatching { PerfCodec.startups(prefs.getString(KEY_STARTUPS, null)) }.getOrDefault(emptyList())

    override fun playbacks(): List<PlaybackRecord> = runCatching { PerfCodec.playbacks(prefs.getString(KEY_PLAYBACKS, null)) }.getOrDefault(emptyList())

    override fun add(record: StartupRecord) {
        prefs.edit().putString(KEY_STARTUPS, PerfCodec.encodeStartups(listOf(record) + startups())).apply()
    }

    override fun add(record: PlaybackRecord) {
        prefs.edit().putString(KEY_PLAYBACKS, PerfCodec.encodePlaybacks(listOf(record) + playbacks())).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_STARTUPS).remove(KEY_PLAYBACKS).apply()
    }

    /** [onChange] whenever a record comes or they are cleared; the returned function stops it. */
    fun listen(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == null || key == KEY_STARTUPS || key == KEY_PLAYBACKS) onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val FILE = "nagmedar_perf"
        private const val KEY_STARTUPS = "startups"
        private const val KEY_PLAYBACKS = "playbacks"

        fun get(context: Context) = PrefsPerfStore(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
