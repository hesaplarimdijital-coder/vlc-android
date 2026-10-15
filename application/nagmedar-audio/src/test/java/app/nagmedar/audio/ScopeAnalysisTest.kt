package app.nagmedar.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * S-611: the Kotlin analysis reads a sine as the engine's (test-audio.sh holds native/audio's to
 * the same figures): the bin, its level in dBFS, each channel's peak and RMS, the waveform from a
 * rising zero crossing; and the native array's layout comes back as it went.
 */
class ScopeAnalysisTest {

    private fun sine(hz: Double, amplitude: Double, rate: Int, frames: Int) =
        FloatArray(frames) { i -> (amplitude * sin(2.0 * PI * hz * i / rate)).toFloat() }

    @Test
    fun theFftFollowsTheRate() {
        assertEquals(1024, ScopeAnalysis.fftSize(22_050))
        assertEquals(2048, ScopeAnalysis.fftSize(44_100))
        assertEquals(2048, ScopeAnalysis.fftSize(48_000))
        assertEquals(4096, ScopeAnalysis.fftSize(96_000))
        assertEquals(8192, ScopeAnalysis.fftSize(192_000))
        assertEquals(16384, ScopeAnalysis.fftSize(384_000))
        assertEquals(16384, ScopeAnalysis.fftSize(768_000))
    }

    @Test
    fun aSineIsItsBinAtItsLevel() {
        // 1500 Hz is bin 64 of 2048 at 48 kHz: no scalloping, no leakage past the next bins
        val s = sine(1500.0, 0.5, 48_000, 4096)
        val frame = ScopeAnalysis.analyse(s, s, 48_000)
        assertEquals(2048, frame.fftSize)
        assertEquals(23.4375f, frame.binHz, 1e-4f)
        val top = frame.spectrumDb.indices.maxBy { frame.spectrumDb[it] }
        assertEquals(64, top)
        assertEquals(-6.0206f, frame.spectrumDb[top], 0.01f)
        val away = frame.spectrumDb.indices.filter { it !in 56..72 }.maxOf { frame.spectrumDb[it] }
        assertTrue("leakage $away dB", away < -90f)
        assertEquals(-6.02f, frame.peakDb[0], 0.01f)
        assertEquals(-9.03f, frame.rmsDb[0], 0.01f)
    }

    @Test
    fun eachChannelHasItsLevels() {
        val l = sine(1500.0, 0.5, 48_000, 2048)
        val r = sine(1500.0, 0.1, 48_000, 2048)
        val frame = ScopeAnalysis.analyse(l, r, 48_000)
        assertEquals(-6.02f, frame.peakDb[0], 0.01f)
        assertEquals(-20.0f, frame.peakDb[1], 0.01f)
        assertEquals(-9.03f, frame.rmsDb[0], 0.01f)
        assertEquals(-23.01f, frame.rmsDb[1], 0.01f)
        // the bin holds both channels' mean power: sqrt((0.25 + 0.01) / 2)
        assertEquals(-8.86f, frame.spectrumDb[64], 0.01f)
    }

    @Test
    fun theWaveStartsOnARisingZeroCrossing() {
        val s = sine(440.0, 0.8, 48_000, 2048)
        val frame = ScopeAnalysis.analyse(s, s, 48_000, wavePoints = 128)
        assertEquals(128, frame.waveLeft.size)
        assertTrue(frame.waveLeft[0] >= 0f && frame.waveLeft[0] < 0.06f)
        assertTrue(frame.waveLeft[1] > frame.waveLeft[0])
    }

    @Test
    fun silenceIsTheFloor() {
        val frame = ScopeAnalysis.analyse(FloatArray(100), FloatArray(100), 48_000)
        assertTrue(frame.spectrumDb.all { it == ScopeFrame.FLOOR_DB })
        assertEquals(ScopeFrame.FLOOR_DB, frame.rmsDb[0])
        assertEquals(ScopeFrame.FLOOR_DB, frame.peakDb[1])
    }

    @Test
    fun theNativeLayoutComesBack() {
        val buffer = ScopeFrame.buffer()
        val header = floatArrayOf(48_000f, 2048f, -1f, -2f, -3f, -4f, 3f, 0f)
        header.copyInto(buffer)
        for (k in 0 until 1024) buffer[ScopeFrame.HEADER + k] = -k.toFloat()
        floatArrayOf(0.1f, 0.2f, 0.3f, -0.1f, -0.2f, -0.3f).copyInto(buffer, ScopeFrame.HEADER + 1024)
        val frame = ScopeFrame.decode(buffer, 1234L)
        assertEquals(48_000, frame.rate)
        assertEquals(1024, frame.spectrumDb.size)
        assertEquals(-1023f, frame.spectrumDb[1023])
        assertArrayEquals(floatArrayOf(-1f, -2f), frame.peakDb, 0f)
        assertArrayEquals(floatArrayOf(-3f, -4f), frame.rmsDb, 0f)
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f), frame.waveLeft, 0f)
        assertArrayEquals(floatArrayOf(-0.1f, -0.2f, -0.3f), frame.waveRight, 0f)
        assertEquals(1234L, frame.frames)
    }
}
