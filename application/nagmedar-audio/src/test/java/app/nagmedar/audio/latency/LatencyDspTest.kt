package app.nagmedar.audio.latency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * S-613: the measurement's signal processing over synthetic rooms: a known delay comes back
 * within a millisecond through noise, echoes, a drifting clock and clipping; silence and a lone
 * stray are told apart from a result.
 */
class LatencyDspTest {

    private val signal = LatencyDsp.testSignal()
    private val rate = signal.rate

    /** The signal's frame 0 lands this far into the recording. */
    private val origin = rate / 5

    /** An echo: [delayMs] after the direct sound at [gain] of it. */
    private data class Echo(val delayMs: Double, val gain: Float)

    /**
     * A room's recording of [signal]: the signal [delayMs] late at [gain], its [echoes] after it,
     * noise of [noiseRms], the recorder's clock [drift] faster (1,001: a tenth of a percent), and
     * the samples clipped at [clipAt].
     */
    private fun record(
        delayMs: Double,
        gain: Float = 0.3f,
        noiseRms: Float = 0f,
        echoes: List<Echo> = emptyList(),
        drift: Double = 1.0,
        clipAt: Float = 1f,
        seed: Int = 1,
    ): FloatArray {
        val out = FloatArray(origin + signal.samples.size + rate * 2)
        val arrivals = listOf(Echo(0.0, 1f)) + echoes
        for (i in out.indices) {
            // the recorder's sample i was taken at i / drift of the player's samples
            val playerTime = i / drift - origin
            var value = 0.0
            for (arrival in arrivals) {
                val source = playerTime - (delayMs + arrival.delayMs) * rate / 1000.0
                value += arrival.gain * sampleAt(signal.samples, source)
            }
            out[i] = (value * gain).toFloat()
        }
        if (noiseRms > 0f) {
            val random = Random(seed)
            for (i in out.indices) out[i] += (gaussian(random) * noiseRms).toFloat()
        }
        if (clipAt < 1f) for (i in out.indices) out[i] = out[i].coerceIn(-clipAt, clipAt)
        return out
    }

    /** [x] between its samples. */
    private fun sampleAt(x: FloatArray, at: Double): Double {
        val i = kotlin.math.floor(at).toInt()
        if (i < 0 || i + 1 >= x.size) return 0.0
        val f = at - i
        return x[i] * (1 - f) + x[i + 1] * f
    }

    private fun gaussian(random: Random): Double {
        val u = 1 - random.nextDouble()
        val v = random.nextDouble()
        return sqrt(-2 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)
    }

    private fun measure(recorded: FloatArray): Measurement? {
        val hits = LatencyDsp.findBursts(recorded, signal, origin.toDouble())
        return LatencyDsp.combine(LatencyDsp.delaysMs(hits, signal, origin.toDouble()), signal.bursts)
    }

    private fun assertDelay(expected: Double, measurement: Measurement?) {
        assertNotNull("no measurement", measurement)
        assertEquals("delay", expected, measurement!!.medianMs, 1.0)
        assertTrue("spread ${measurement.spreadMs}", measurement.spreadMs <= 1.0)
    }

    @Test
    fun theSignalIsAChirpTrain() {
        assertEquals(LatencyDsp.BURSTS, signal.bursts)
        assertEquals(rate * LatencyDsp.LEAD_MS / 1000, signal.burstStarts[0])
        assertEquals(rate * LatencyDsp.GAP_MS / 1000, signal.burstStarts[1] - signal.burstStarts[0])
        assertEquals(rate * LatencyDsp.CHIRP_MS / 1000, signal.burst.size)
        // the chirps start and end in silence and never pass −6 dBFS
        assertEquals(0f, signal.burst.first(), 1e-6f)
        assertEquals(0f, signal.burst.last(), 1e-6f)
        assertTrue(signal.burst.all { abs(it) <= 0.5f })
        assertTrue("about ${LatencyDsp.BURSTS * LatencyDsp.GAP_MS / 1000} s", signal.durationMs in 7_000..8_000)
    }

    @Test
    fun aCleanDelayComesBackExactly() {
        assertDelay(123.45, measure(record(123.45)))
        assertDelay(7.3, measure(record(7.3)))
        assertDelay(480.0, measure(record(480.0)))
        // a clock that runs ahead: the sound before the clock says so
        assertDelay(-12.5, measure(record(-12.5)))
    }

    @Test
    fun noiseOfTheSignalsOwnPowerDoesNotMoveIt() {
        // the chirp at 0,3 and white noise at 0,3: 0 dB SNR within the chirp, far below outside it
        assertDelay(210.0, measure(record(210.0, gain = 0.3f, noiseRms = 0.3f)))
    }

    @Test
    fun theDirectSoundWinsOverTheRoomsEchoes() {
        val echoes = listOf(Echo(8.0, 0.7f), Echo(25.0, 0.5f), Echo(60.0, 0.3f))
        assertDelay(150.0, measure(record(150.0, echoes = echoes, noiseRms = 0.05f)))
        // an echo louder than the direct sound, a wall behind the phone
        assertDelay(150.0, measure(record(150.0, echoes = listOf(Echo(12.0, 1.3f)), noiseRms = 0.05f)))
    }

    @Test
    fun aDriftingRecorderClockStaysWithinAMillisecond() {
        // 50 ppm, two crystals apart; over the eight seconds that is under half a millisecond
        val measurement = measure(record(300.0, drift = 1.00005, noiseRms = 0.05f))
        assertDelay(300.0, measurement)
        // a tenth of a percent: every burst is still found, each where the slow clock put it
        val recorded = record(300.0, drift = 1.001, noiseRms = 0.05f)
        val hits = LatencyDsp.findBursts(recorded, signal, origin.toDouble())
        assertTrue(hits.all { it.at != null })
        val delays = LatencyDsp.delaysMs(hits, signal, origin.toDouble())
        signal.burstStarts.forEachIndexed { i, start ->
            val heardAt = origin + start + 300.0 * rate / 1000
            assertEquals(300.0 + heardAt * 0.001 * 1000 / rate, delays[i], 0.2)
        }
    }

    @Test
    fun clippingDoesNotMoveIt() {
        assertDelay(95.0, measure(record(95.0, gain = 2f, clipAt = 0.5f, noiseRms = 0.1f)))
    }

    @Test
    fun silenceGivesNothing() {
        val quiet = record(100.0, gain = 0f, noiseRms = 0.1f)
        val hits = LatencyDsp.findBursts(quiet, signal, origin.toDouble())
        assertTrue(hits.all { it.at == null })
        assertNull(measure(quiet))
    }

    @Test
    fun aStrayDelayIsThrownOut() {
        val combined = LatencyDsp.combine(listOf(100.2, 100.4, 100.1, 450.0, 100.3), total = 5)!!
        assertEquals(100.3, combined.medianMs, 0.15)
        assertEquals(1, combined.rejected)
        assertEquals(0.3, combined.spreadMs, 0.01)
        // delays all over the place agree on nothing: the spread says so, the screen warns
        val scattered = LatencyDsp.combine(listOf(100.0, 100.0, 300.0, 500.0, 700.0), total = 5)!!
        assertEquals(0, scattered.rejected)
        assertEquals(600.0, scattered.spreadMs, 0.01)
        // one alone, or too few of the bursts, is not a measurement
        assertNull(LatencyDsp.combine(listOf(100.0), total = 5))
        assertNull(LatencyDsp.combine(listOf(100.0, 100.1), total = 5))
        // three of five that agree are
        assertEquals(100.0, LatencyDsp.combine(listOf(100.0, 100.1, 99.9), total = 5)!!.medianMs, 0.01)
    }

    @Test
    fun theSearchWindowIsBounded() {
        // a delay beyond the window is not found rather than mistaken for another burst
        val hits = LatencyDsp.findBursts(record(1_250.0), signal, origin.toDouble())
        assertTrue(hits.all { it.at == null })
        // a recording cut short before the last burst still gives the others
        val cut = record(100.0).copyOf(origin + signal.burstStarts[3] + rate / 2)
        val short = LatencyDsp.findBursts(cut, signal, origin.toDouble())
        assertEquals(listOf(true, true, true, true, false), short.map { it.at != null })
        assertDelay(100.0, LatencyDsp.combine(LatencyDsp.delaysMs(short, signal, origin.toDouble()), signal.bursts))
    }
}
