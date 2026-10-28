package app.nagmedar.ui.perf

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/*
 * S-1404: what Nağmedar measures of itself on the device, without a computer: how long a cold
 * start-up took to its first frame. The last ten are kept for the Performance card; each one is
 * also written to the log as a "NagmedarPerf" line, which the macrobenchmarks (application/benchmark) read.
 */

/** A cold start-up: [ms] from the launch to the first frame, [atMs] when it was (the wall clock). */
@Immutable
data class StartupRecord(val atMs: Long, val ms: Long)

/** What the Performance card and the macrobenchmarks hold Nağmedar to. */
object PerfTargets {
    /** A cold start-up shows its first frame within a second. */
    const val STARTUP_MS = 1_000L

    fun startupOk(ms: Long) = ms <= STARTUP_MS
}

/** The log's lines, one a record: "startup cold_ms=640", for the scripts that read them. */
object PerfLog {
    const val TAG = "NagmedarPerf"

    fun line(record: StartupRecord) = "startup cold_ms=${record.ms}"
}

/** The records kept, the newest first. */
interface PerfStore {
    fun startups(): List<StartupRecord>

    fun add(record: StartupRecord)

    fun clear()
}

/** A record kept and written to the log. */
fun PerfStore.keep(record: StartupRecord) {
    Log.i(PerfLog.TAG, PerfLog.line(record))
    add(record)
}

/** The records as JSON arrays, the newest first; what cannot be read is left out. */
object PerfCodec {
    const val KEEP = 10

    fun startups(json: String?): List<StartupRecord> = read(json) { StartupRecord(it.getLong("at"), it.getLong("ms")) }

    fun encodeStartups(records: List<StartupRecord>): String =
        JSONArray(records.take(KEEP).map { JSONObject().put("at", it.atMs).put("ms", it.ms) }).toString()

    private fun <T> read(json: String?, record: (JSONObject) -> T): List<T> {
        val array = json?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { runCatching { record(it) }.getOrNull() } }
    }
}

/** The records in the app's preferences ("nagmedar_perf"). */
class PrefsPerfStore(private val prefs: SharedPreferences) : PerfStore {

    override fun startups(): List<StartupRecord> = runCatching { PerfCodec.startups(prefs.getString(KEY_STARTUPS, null)) }.getOrDefault(emptyList())

    override fun add(record: StartupRecord) {
        prefs.edit().putString(KEY_STARTUPS, PerfCodec.encodeStartups(listOf(record) + startups())).apply()
    }

    override fun clear() {
        // and the film playbacks an earlier version kept
        prefs.edit().remove(KEY_STARTUPS).remove(KEY_PLAYBACKS).apply()
    }

    /** [onChange] whenever a record comes or they are cleared; the returned function stops it. */
    fun listen(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == null || key == KEY_STARTUPS) onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val FILE = "nagmedar_perf"
        private const val KEY_STARTUPS = "startups"

        /** 0.5's film playbacks: no more kept, cleared with the start-ups. */
        private const val KEY_PLAYBACKS = "playbacks"

        fun get(context: Context) = PrefsPerfStore(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
