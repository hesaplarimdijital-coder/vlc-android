package app.nagmedar.ui.audio.latency

import app.nagmedar.audio.latency.LatencyDsp
import app.nagmedar.audio.latency.TestSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-613: a measurement from the explanation to its result, over a room of the test's making. */
class LatencyMeasureSessionTest {

    private val signal = LatencyDsp.testSignal()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /** A room: the signal [delayMs] late at [gain], every tone, or only the ones in [heard]; the clocks [exact] or not. */
    private class Room(
        private val delayMs: Double,
        private val gain: Float = 0.3f,
        private val exact: Boolean = true,
        private val heard: Set<Int>? = null,
        private val jitterMs: List<Double> = emptyList(),
    ) : MeasureIo {
        var progress = ArrayList<Float>()

        override suspend fun playAndRecord(signal: TestSignal, tailMs: Int, onProgress: (Float) -> Unit): Recording {
            val rate = signal.rate
            val origin = rate / 4
            val out = FloatArray(origin + signal.samples.size + rate * tailMs / 1000)
            signal.burstStarts.forEachIndexed { index, start ->
                if (heard != null && index !in heard) return@forEachIndexed
                val late = delayMs + (jitterMs.getOrNull(index) ?: 0.0)
                val at = origin + start + (late * rate / 1000).toInt()
                for (i in signal.burst.indices) if (at + i < out.size) out[at + i] += signal.burst[i] * gain
            }
            onProgress(0.5f).also { progress += 0.5f }
            onProgress(1f).also { progress += 1f }
            return Recording(out, rate, origin.toDouble(), exact)
        }
    }

    private fun session(io: MeasureIo, referenceMs: Int? = null) = LatencyMeasureSession(io, scope, referenceMs, signal, cpu = Dispatchers.Unconfined)

    @Test
    fun aMeasurementEndsInItsResult() {
        val room = Room(183.0)
        val session = session(room)
        assertEquals(LatencyMeasureSession.State.Intro, session.state)
        session.start()
        val done = session.state as LatencyMeasureSession.State.Done
        assertEquals(183, done.result.rawMs)
        assertEquals(180, done.result.delayMs)
        assertEquals(5, done.result.valid)
        assertEquals(5, done.result.total)
        assertEquals(0, done.result.spreadMs)
        assertTrue(done.result.exact)
        assertFalse(done.result.uncertain)
        assertEquals(listOf(0.5f, 1f), room.progress)
        // back to the start for another go
        session.reset()
        assertEquals(LatencyMeasureSession.State.Intro, session.state)
    }

    @Test
    fun theSpeakersShareComesOffAnotherDevice() {
        val session = session(Room(195.0), referenceMs = 12)
        session.start()
        val result = (session.state as LatencyMeasureSession.State.Done).result
        assertEquals(195, result.rawMs)
        assertEquals(12, result.referenceMs)
        assertEquals(180, result.delayMs)
    }

    @Test
    fun aDeviceWithoutTimestampsGivesAFigureThatIsNotSaved() {
        val session = session(Room(100.0, exact = false))
        session.start()
        val result = (session.state as LatencyMeasureSession.State.Done).result
        assertFalse(result.exact)
        assertFalse(result.saveable)
        assertTrue(session(Room(100.0)).also { it.start() }.state.let { (it as LatencyMeasureSession.State.Done).result.saveable })
    }

    @Test
    fun tonesThatDisagreeAreUncertainAndAStrayIsDropped() {
        val session = session(Room(100.0, jitterMs = listOf(0.0, 30.0, -30.0, 0.0, 25.0)))
        session.start()
        val result = (session.state as LatencyMeasureSession.State.Done).result
        assertTrue("spread ${result.spreadMs}", result.spreadMs > LatencyResult.TOLERANCE_MS)
        assertTrue(result.uncertain)
        // one tone far off the others is thrown out and the rest agree
        val stray = session(Room(100.0, jitterMs = listOf(0.0, 0.0, 400.0, 0.0, 0.0)))
        stray.start()
        val kept = (stray.state as LatencyMeasureSession.State.Done).result
        assertEquals(100, kept.rawMs)
        assertEquals(4, kept.valid)
        assertFalse(kept.uncertain)
    }

    @Test
    fun silenceAndTooFewTonesFail() {
        val quiet = session(Room(100.0, gain = 0f))
        quiet.start()
        assertEquals(LatencyMeasureSession.State.Failed(MeasureFailure.NotHeard), quiet.state)
        val few = session(Room(100.0, heard = setOf(2)))
        few.start()
        assertEquals(LatencyMeasureSession.State.Failed(MeasureFailure.Inconsistent), few.state)
        // three of five heard is enough
        val three = session(Room(100.0, heard = setOf(0, 2, 4)))
        three.start()
        assertEquals(3, (three.state as LatencyMeasureSession.State.Done).result.valid)
    }

    @Test
    fun aStreamThatWillNotOpenFails() {
        val broken = object : MeasureIo {
            override suspend fun playAndRecord(signal: TestSignal, tailMs: Int, onProgress: (Float) -> Unit): Recording = throw IllegalStateException("busy")
        }
        val session = session(broken)
        session.start()
        assertEquals(LatencyMeasureSession.State.Failed(MeasureFailure.Device), session.state)
    }
}
