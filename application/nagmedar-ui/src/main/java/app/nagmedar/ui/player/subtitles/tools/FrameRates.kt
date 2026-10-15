package app.nagmedar.ui.player.subtitles.tools

import androidx.compose.runtime.Immutable

/**
 * The frame rates subtitles are timed for (S-708), as exact fractions: 23.976 is 24000/1001. A
 * subtitle timed for a film at one rate shows early or late on the same film at another rate (a
 * 23.976 fps release against a 25 fps one) until its times are scaled by the rates' ratio.
 */
enum class FrameRate(val numerator: Long, val denominator: Long) {
    Fps23976(24_000, 1_001),
    Fps24(24, 1),
    Fps25(25, 1),
    Fps2997(30_000, 1_001),
    Fps30(30, 1),
    ;

    val fps: Double get() = numerator.toDouble() / denominator
}

/** The result of [FrameRateConversion.apply]: the cues, and how many were dropped for ending before the start of the film. */
@Immutable
data class Rescaled(val cues: List<TimedCue>, val dropped: Int)

/** Scaling the times of subtitles from one frame rate to another, and shifting them. */
object FrameRateConversion {

    /**
     * [ms] timed for a film at [from] as it is on a film at [to]: the same frame is at the same
     * number, so its time is scaled by from/to. Rounded to the nearest millisecond; there and
     * back again it is within 1 ms.
     */
    fun convert(ms: Long, from: FrameRate, to: FrameRate): Long {
        if (from == to) return ms
        val numerator = ms * from.numerator * to.denominator
        val denominator = from.denominator * to.numerator
        return if (numerator >= 0) (numerator + denominator / 2) / denominator else -((-numerator + denominator / 2) / denominator)
    }

    /** The scale as a number: 0.959 from 23.976 to 25. */
    fun factor(from: FrameRate, to: FrameRate): Double = (from.fps / to.fps)

    /**
     * [cues] scaled from [from] to [to], then shifted by [shiftMs] (negative: earlier). A cue that
     * ends at or before zero goes; one that starts before it starts at zero.
     */
    fun apply(cues: List<TimedCue>, from: FrameRate, to: FrameRate, shiftMs: Long): Rescaled {
        var dropped = 0
        val moved = ArrayList<TimedCue>(cues.size)
        for (cue in cues) {
            val start = convert(cue.startMs, from, to) + shiftMs
            val end = convert(cue.endMs, from, to) + shiftMs
            if (end <= 0) {
                dropped++
                continue
            }
            moved += cue.copy(startMs = start.coerceAtLeast(0), endMs = end)
        }
        return Rescaled(moved, dropped)
    }
}
