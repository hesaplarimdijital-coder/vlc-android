package app.nagmedar.lab

import org.json.JSONObject

/**
 * S-1204: a drive's sequential read, as the native test measured it: [bytes] in [ms], the wait
 * for the first block ([firstMs]), and whether it ran to its end.
 */
data class DiskSpeed(val bytes: Long, val ms: Long, val megabytesPerSecond: Double, val firstMs: Long, val complete: Boolean) {
    /** The rate in kbps, to set beside a film's bitrate. */
    val kbps: Long get() = (megabytesPerSecond * 8_000).toLong()

    companion object {
        const val VERSION = 1

        fun parse(json: String): DiskSpeed? = runCatching {
            val o = JSONObject(json)
            if (o.optString("error").isNotEmpty() || o.optInt("version") != VERSION) return null
            DiskSpeed(
                bytes = o.optLong("bytes"),
                ms = o.optLong("ms"),
                megabytesPerSecond = o.optDouble("mb_per_s", 0.0),
                firstMs = o.optLong("first_ms"),
                complete = o.optBoolean("complete", true),
            )
        }.getOrNull()

        fun error(json: String): String? = runCatching { JSONObject(json).optString("error").takeIf { it.isNotEmpty() } }.getOrNull()
    }
}
