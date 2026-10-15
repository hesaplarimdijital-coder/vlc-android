package app.nagmedar.ui.visual

import app.nagmedar.audio.ScopeAnalysis
import app.nagmedar.audio.ScopeFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * S-611: the meters move as meters do: the RMS's 65 ms rise and 300 ms fall, the peak's fall of
 * 11.8 dB a second, the held peak and the clip mark; the spectrum's bars and caps; and a sine
 * lands in its band.
 */
class BallisticsTest {

    /** [seconds] of reads every [step] s at [peak] and [rms]. */
    private fun LevelBallistics.feed(peak: Float?, rms: Float?, seconds: Float, step: Float = 1f / 60f) {
        var t = 0f
        while (t < seconds - 1e-6f) {
            update(peak, rms, step)
            t += step
        }
    }

    @Test
    fun theRmsRisesFasterThanItFalls() {
        val level = LevelBallistics()
        level.feed(-6f, -12f, 0.065f)
        // one time constant: 63 % of the way in power, from silence
        assertEquals(-12f + 10f * kotlin.math.log10(0.632f), level.rmsDb, 0.3f)
        level.feed(-6f, -12f, 1f)
        assertEquals(-12f, level.rmsDb, 0.01f)
        // falling, after 300 ms the power is down to 37 %
        level.feed(null, null, 0.3f)
        assertEquals(-12f + 10f * kotlin.math.log10(0.368f), level.rmsDb, 0.3f)
    }

    @Test
    fun thePeakJumpsAndFalls() {
        val level = LevelBallistics()
        level.update(-3f, -10f, 1f / 60f)
        assertEquals(-3f, level.peakDb, 0f)
        level.feed(-40f, -50f, 1f)
        assertEquals(-3f - LevelBallistics.PEAK_FALL_DB, level.peakDb, 0.3f)
    }

    @Test
    fun theHighestPeakIsHeldThenFalls() {
        val level = LevelBallistics()
        level.update(-2f, -10f, 0.01f)
        level.feed(-30f, -40f, 1.4f)
        assertEquals(-2f, level.holdDb, 0f)
        level.feed(-30f, -40f, 0.6f)
        // held 1.5 s, then 20 dB a second for the 0.5 s after
        assertEquals(-2f - 0.5f * LevelBallistics.HOLD_FALL_DB, level.holdDb, 0.5f)
    }

    @Test
    fun fullScaleLightsTheClipMarkForTwoSeconds() {
        val level = LevelBallistics()
        level.update(0f, -3f, 0.01f)
        assertTrue(level.clipped)
        level.feed(-20f, -30f, 1.9f)
        assertTrue(level.clipped)
        level.feed(-20f, -30f, 0.2f)
        assertFalse(level.clipped)
    }

    @Test
    fun silenceComesToRest() {
        val meter = LevelMeter()
        meter.update(frame(-3f, -9f), 0.02f)
        assertFalse(meter.atRest)
        var steps = 0
        while (!meter.atRest && steps < 60 * 30) {
            meter.update(null, 1f / 60f)
            steps++
        }
        assertTrue("at rest after ${steps / 60f} s", meter.atRest)
    }

    @Test
    fun barsFallAndCapsWait() {
        val spectrum = SpectrumBallistics(2)
        spectrum.update(floatArrayOf(-10f, -20f), 0.01f)
        spectrum.update(floatArrayOf(-60f, -60f), 0.25f)
        assertEquals(-20f, spectrum.bars[0], 0.01f)
        assertEquals(-10f, spectrum.caps[0], 0f)
        spectrum.update(floatArrayOf(-60f, -60f), 0.5f)
        // the cap held 0.5 s, then fell for the last 0.25 s
        assertEquals(-40f, spectrum.bars[0], 0.01f)
        assertTrue(spectrum.caps[0] < -10f && spectrum.caps[0] > -20f)
    }

    @Test
    fun aSineIsInItsBand() {
        val rate = 48_000
        val samples = FloatArray(4096) { i -> (0.5 * sin(2.0 * PI * 1000.0 * i / rate)).toFloat() }
        val frame = ScopeAnalysis.analyse(samples, samples, rate)
        val bands = SpectrumBands.bands(frame, VisualizerState.BANDS)
        val edges = SpectrumBands.edges(VisualizerState.BANDS, SpectrumBands.MIN_HZ, SpectrumBands.MAX_HZ)
        val band = (0 until VisualizerState.BANDS).single { edges[it] <= 1000f && 1000f < edges[it + 1] }
        assertEquals(band, bands.indices.maxBy { bands[it] })
        // the loudest bin of the band: within the Hann window's scalloping of the sine's -6 dB
        assertEquals(-6.02f, bands[band], 1.5f)
        assertTrue(bands.first() < -60f)
    }

    @Test
    fun narrowBandsReadBetweenBins() {
        // 23.4 Hz bins at 48 kHz: the bands under 100 Hz are narrower, they read the line between two bins
        val spectrum = FloatArray(1024) { k -> -k.toFloat() / 10f }
        val frame = ScopeFrame(48_000, 2048, spectrum, floatArrayOf(0f, 0f), floatArrayOf(0f, 0f), FloatArray(0), FloatArray(0), 1)
        val bands = SpectrumBands.bands(frame, VisualizerState.BANDS)
        val centre = SpectrumBands.centres(VisualizerState.BANDS)[0]
        assertEquals(-(centre / frame.binHz) / 10f, bands[0], 0.01f)
        assertTrue((1 until bands.size).all { bands[it - 1] >= bands[it] })
    }

    private fun frame(peak: Float, rms: Float) =
        ScopeFrame(48_000, 2048, FloatArray(1024) { ScopeFrame.FLOOR_DB }, floatArrayOf(peak, peak), floatArrayOf(rms, rms), FloatArray(0), FloatArray(0), 1)
}
