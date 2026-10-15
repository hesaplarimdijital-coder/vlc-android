package app.nagmedar.ui.player.subtitles.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** S-708: the times of subtitles scaled from one frame rate to another, and shifted. */
class FrameRateConversionTest {

    @Test
    fun knownConversions() {
        // an hour timed for 23.976 fps is 3 596.4 s long at 24 fps (23.976 / 24 = 0.999)
        assertEquals(3_596_404L, FrameRateConversion.convert(3_600_000, FrameRate.Fps23976, FrameRate.Fps24))
        // 25 -> 23.976 stretches by 1.0427
        assertEquals(3_753_750L, FrameRateConversion.convert(3_600_000, FrameRate.Fps25, FrameRate.Fps23976))
        assertEquals(3_596_404L, FrameRateConversion.convert(3_600_000, FrameRate.Fps2997, FrameRate.Fps30))
        assertEquals(5_000L, FrameRateConversion.convert(5_000, FrameRate.Fps25, FrameRate.Fps25))
        assertEquals(0L, FrameRateConversion.convert(0, FrameRate.Fps24, FrameRate.Fps30))
        assertEquals(0.959, FrameRateConversion.factor(FrameRate.Fps23976, FrameRate.Fps25), 0.0005)
    }

    @Test
    fun thereAndBackIsWithinAMillisecond() {
        var worst = 0L
        for (from in FrameRate.entries) {
            for (to in FrameRate.entries) {
                var ms = 0L
                while (ms < 4 * 3_600_000L) {
                    val back = FrameRateConversion.convert(FrameRateConversion.convert(ms, from, to), to, from)
                    worst = maxOf(worst, abs(back - ms))
                    ms += 7_919
                }
            }
        }
        assertTrue("worst round trip $worst ms", worst <= 1)
    }

    @Test
    fun theCuesAreScaledAndShifted() {
        val cues = listOf(TimedCue(1, 1_000, 3_000, "a"), TimedCue(2, 3_600_000, 3_601_000, "b"))
        val result = FrameRateConversion.apply(cues, FrameRate.Fps25, FrameRate.Fps23976, 0)
        assertEquals(listOf(1_043L, 3_753_750L), result.cues.map { it.startMs })
        assertEquals(0, result.dropped)

        // a cue that ends at zero or before goes; the second is three seconds earlier
        val shifted = FrameRateConversion.apply(cues, FrameRate.Fps25, FrameRate.Fps25, -3_000)
        assertEquals(listOf(3_597_000L), shifted.cues.map { it.startMs })
        assertEquals(1, shifted.dropped)
        // one that starts before zero starts at zero
        val clamped = FrameRateConversion.apply(cues, FrameRate.Fps25, FrameRate.Fps25, -1_500)
        assertEquals(listOf(0L, 3_598_500L), clamped.cues.map { it.startMs })
        assertEquals(1_500L, clamped.cues[0].endMs)
        assertEquals(0, clamped.dropped)
    }

    @Test
    fun theRatesAreExact() {
        assertEquals(23.976, FrameRate.Fps23976.fps, 0.0005)
        assertEquals(29.97, FrameRate.Fps2997.fps, 0.0005)
        assertEquals(25.0, FrameRate.Fps25.fps, 0.0)
    }
}
