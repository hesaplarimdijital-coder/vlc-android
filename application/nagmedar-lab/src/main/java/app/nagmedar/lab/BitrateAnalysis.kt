package app.nagmedar.lab

import org.json.JSONArray
import org.json.JSONObject

/** One stream's share of the file (S-1202). */
data class StreamBitrate(
    val index: Int,
    /** "video", "audio", "subtitle", "attachment", "other". */
    val type: String,
    val codec: String,
    val bytes: Long,
    val averageKbps: Int,
    val peakSecondKbps: Int,
)

/**
 * S-1202: a file's bitrate over time, as the native analysis read it from the packets: every
 * second's kilobits for all streams together ([total]) and for the video alone ([video]), the
 * means and the peaks (the highest second, the highest five seconds, which a player's buffer
 * must take), and each stream's share. [complete] is false when the reading was stopped.
 */
data class BitrateAnalysis(
    val complete: Boolean,
    val durationMs: Long,
    val sizeBytes: Long,
    val total: IntArray,
    val video: IntArray,
    val averageKbps: Int,
    val peakSecondKbps: Int,
    val peakFiveSecondsKbps: Int,
    val videoAverageKbps: Int,
    val videoPeakSecondKbps: Int,
    val videoPeakFiveSecondsKbps: Int,
    val streams: List<StreamBitrate>,
) {
    val seconds: Int get() = total.size

    /** The seconds whose rate is over [kbps]: what a slower link could not feed in time. */
    fun secondsOver(kbps: Int): Int = total.count { it > kbps }

    /** The highest mean of [window] consecutive seconds of [series], as the native side does for five. */
    fun peakOf(series: IntArray, window: Int): Int {
        if (series.isEmpty() || window <= 0) return 0
        val size = minOf(window, series.size)
        var sum = 0L
        var best = 0L
        for (i in series.indices) {
            sum += series[i]
            if (i >= size) sum -= series[i - size]
            if (i >= size - 1 && sum > best) best = sum
        }
        return (best / size).toInt()
    }

    override fun equals(other: Any?): Boolean = other is BitrateAnalysis && other.toString() == toString()

    override fun hashCode(): Int = toString().hashCode()

    override fun toString(): String =
        "BitrateAnalysis(complete=$complete, durationMs=$durationMs, sizeBytes=$sizeBytes, total=${total.toList()}, video=${video.toList()}, " +
            "averageKbps=$averageKbps, peakSecondKbps=$peakSecondKbps, peakFiveSecondsKbps=$peakFiveSecondsKbps, " +
            "videoAverageKbps=$videoAverageKbps, videoPeakSecondKbps=$videoPeakSecondKbps, videoPeakFiveSecondsKbps=$videoPeakFiveSecondsKbps, streams=$streams)"

    companion object {
        /** The native JSON version (BITRATE_VERSION in bitrate.h). */
        const val VERSION = 1

        /**
         * What a USB 2.0 port feeds in practice, in kilobits per second: about 30 MB/s of the
         * 480 Mbit/s nominal, after the bus's overhead; a film whose seconds pass it stutters
         * from a USB 2.0 drive (an LG B4's ports).
         */
        const val USB2_KBPS = 240_000

        /** The analysis' JSON as [BitrateAnalysis]; null when it is an error or not an analysis. */
        fun parse(json: String): BitrateAnalysis? = runCatching {
            val o = JSONObject(json)
            if (o.optString("error").isNotEmpty() || o.optInt("version") != VERSION) return null
            BitrateAnalysis(
                complete = o.optBoolean("complete", true),
                durationMs = o.optLong("duration_ms"),
                sizeBytes = o.optLong("size"),
                total = o.optJSONArray("total").ints(),
                video = o.optJSONArray("video").ints(),
                averageKbps = o.optInt("average_kbps"),
                peakSecondKbps = o.optInt("peak_1s_kbps"),
                peakFiveSecondsKbps = o.optInt("peak_5s_kbps"),
                videoAverageKbps = o.optInt("video_average_kbps"),
                videoPeakSecondKbps = o.optInt("video_peak_1s_kbps"),
                videoPeakFiveSecondsKbps = o.optInt("video_peak_5s_kbps"),
                streams = o.optJSONArray("streams").let { streams ->
                    if (streams == null) emptyList() else (0 until streams.length()).mapNotNull { streams.optJSONObject(it) }.map(::stream)
                },
            )
        }.getOrNull()

        /** The error the native side gave, or null for an analysis. */
        fun error(json: String): String? = runCatching { JSONObject(json).optString("error").takeIf { it.isNotEmpty() } }.getOrNull()

        private fun JSONArray?.ints(): IntArray = if (this == null) IntArray(0) else IntArray(length()) { optInt(it) }

        private fun stream(s: JSONObject) = StreamBitrate(
            index = s.optInt("index"),
            type = s.optString("type"),
            codec = s.optString("codec"),
            bytes = s.optLong("bytes"),
            averageKbps = s.optInt("average_kbps"),
            peakSecondKbps = s.optInt("peak_1s_kbps"),
        )
    }
}
