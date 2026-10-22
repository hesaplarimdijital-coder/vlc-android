package app.nagmedar.ui.tvlab.remux

import android.net.Uri
import app.nagmedar.data.RemuxJobError
import app.nagmedar.data.RemuxJobState
import app.nagmedar.lab.DolbyVisionMode
import app.nagmedar.ui.convert.ConvertPolicy
import app.nagmedar.ui.convert.DeviceState
import app.nagmedar.ui.convert.Heat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * S-1206, S-1209: the queue's loop. A job runs, is verified and ends done with its hash; a
 * verification that does not match fails it and removes the output; a cancel, a stopped worker,
 * a dead process, the battery and the heat each leave the row as the interface expects. Under
 * Robolectric for the real Uri and JSON.
 */
@RunWith(AndroidJUnit4::class)
class RemuxRunnerTest {

    private val dao = MemoryRemuxJobDao()
    private val engine = FakeRemuxEngine()
    private var condition = DeviceState.FINE
    private var clock = 1_000L
    private val updates = mutableListOf<String>()

    private fun runner(interruption: () -> String? = { null }): Pair<RemuxRunner, () -> String?> =
        RemuxRunner(engine, dao, { condition }, { clock += 1_000; clock }) { updates += it.state } to interruption

    private fun queued(id: Long = 1) = dao.insert(sampleJob(id = id)).let { dao.get(it)!! }

    @Test
    fun aJobRunsIsVerifiedAndEndsDoneWithItsHash() {
        val job = queued()
        val (runner, active) = runner()
        val done = runner.run(job, active)
        assertEquals(RemuxJobState.DONE, done.state)
        assertEquals(SAMPLE_HASH, done.videoHash)
        assertNull(done.error)
        assertEquals(1, done.attempts)
        assertNotNull(done.startedAt)
        assertNotNull(done.finishedAt)
        assertEquals(1f, done.progress)
        assertEquals(sampleResultJson, done.result)
        assertEquals(sampleVerificationJson, done.verification)
        // the verification is asked in the remux's scope and for the plan's Dolby Vision profile
        assertEquals(listOf("remux:7", "verify:base_layer_nal_units:189720:8"), engine.calls)
        assertTrue(engine.removed.isEmpty())
        assertEquals(listOf(RemuxJobState.RUNNING, RemuxJobState.VERIFYING, RemuxJobState.DONE), updates.distinct())
        assertEquals(listOf(RemuxEvent.STARTED), RemuxEvent.parse(done.events).map { it.kind })
        assertEquals(done, dao.get(1))
        assertTrue("the empty document became the job's", done.ownsOutput)
    }

    @Test
    fun aPlanWithoutConversionIsVerifiedWithoutDolbyVision() {
        val plan = samplePlan.copy(dolbyVision = DolbyVisionMode.Keep)
        engine.resultJson = sampleResultJson.replace("base_layer_nal_units", "nal_units")
        engine.verificationJson = sampleVerificationJson.replace("base_layer_nal_units", "nal_units")
        val id = dao.insert(sampleJob(plan = plan))
        val (runner, active) = runner()
        assertEquals(RemuxJobState.DONE, runner.run(dao.get(id)!!, active).state)
        assertEquals("verify:nal_units:189720:0", engine.calls[1])
    }

    @Test
    fun aMismatchFailsTheJobAndRemovesTheOutput() {
        engine.verificationJson = sampleBadVerificationJson
        val (runner, active) = runner()
        val failed = runner.run(queued(), active)
        assertEquals(RemuxJobState.FAILED, failed.state)
        assertEquals(RemuxJobError.VERIFY, failed.error)
        assertEquals(sampleBadVerificationJson, failed.verification)
        assertNull(failed.videoHash)
        assertEquals(listOf(Uri.parse(SAMPLE_OUTPUT)), engine.removed)
    }

    @Test
    fun theEnginesErrorFailsTheJob() {
        engine.resultJson = """{"error":"write: No space left on device","cancelled":false}"""
        val (runner, active) = runner()
        val failed = runner.run(queued(), active)
        assertEquals(RemuxJobState.FAILED, failed.state)
        assertEquals(RemuxJobError.ENGINE + "write: No space left on device", failed.error)
        assertEquals(1, engine.removed.size)
        assertEquals(listOf("remux:7"), engine.calls)
    }

    @Test
    fun filesThatDoNotOpenAreToldApart() {
        engine.openInput = false
        val (runner, active) = runner()
        assertEquals(RemuxJobError.OPEN_INPUT, runner.run(queued(1), active).error)
        // the picker made the output document: an empty file must not stay either
        assertEquals(1, engine.removed.size)
        engine.openInput = true
        engine.openOutput = false
        assertEquals(RemuxJobError.OPEN_OUTPUT, runner.run(queued(2), active).error)
        assertEquals(2, engine.removed.size)
    }

    @Test
    fun withoutTheNativeToolsTheJobFailsAtOnce() {
        val (runner, active) = RemuxRunner(FakeRemuxEngine(available = false), dao, { condition }) to { null }
        assertEquals(RemuxJobError.NO_TOOLS, runner.run(queued(1), active).error)
    }

    @Test
    fun anOutputThatIsTheSourceIsRefused() {
        val id = dao.insert(sampleJob(id = 7).copy(outputUri = SAMPLE_INPUT))
        val (runner, active) = runner()
        val failed = runner.run(dao.get(id)!!, active)
        assertEquals(RemuxJobState.FAILED, failed.state)
        assertEquals(RemuxJobError.SAME_FILE, failed.error)
        assertTrue("the source is never opened, let alone removed", engine.calls.isEmpty() && engine.removed.isEmpty() && engine.emptied.isEmpty())
        assertEquals(false, failed.ownsOutput)
    }

    /** The source behind another name (a file URI in, the picker's "replace" out): a film holds bytes, so it is refused unread. */
    @Test
    fun anOutputThatHoldsSomethingIsRefusedUntouched() {
        engine.outputsEmpty = false
        val (runner, active) = runner()
        val failed = runner.run(queued(), active)
        assertEquals(RemuxJobError.OUTPUT_NOT_EMPTY, failed.error)
        assertTrue(engine.calls.isEmpty() && engine.removed.isEmpty() && engine.emptied.isEmpty())
        assertEquals(false, failed.ownsOutput)
        // an output that cannot be read at all is not the job's either
        engine.outputsEmpty = null
        assertEquals(RemuxJobError.OPEN_OUTPUT, runner.run(queued(2), active).error)
        assertTrue(engine.removed.isEmpty())
    }

    /** The engine sees one file behind two descriptors (device and inode, or the path): refused before a byte, left alone. */
    @Test
    fun theEnginesRefusalIsNeverARemoval() {
        engine.refused = "same_file"
        val (runner, active) = runner()
        val failed = runner.run(queued(), active)
        assertEquals(RemuxJobError.SAME_FILE, failed.error)
        assertEquals(false, failed.ownsOutput)
        assertTrue(engine.removed.isEmpty() && engine.emptied.isEmpty())
        engine.refused = "output_not_empty"
        assertEquals(RemuxJobError.OUTPUT_NOT_EMPTY, runner.run(queued(2), active).error)
        assertTrue(engine.removed.isEmpty())
    }

    @Test
    fun aJobWaitingForTheAppIsMarkedSo() {
        val job = queued()
        val (runner, _) = runner()
        runner.waitForApp(job)
        assertEquals(RemuxJobError.WAITING_APP, dao.get(1)!!.error)
        assertEquals(RemuxJobState.QUEUED, dao.get(1)!!.state)
        assertTrue(RemuxJobView.of(dao.get(1)!!).waitingForApp)
        // a job that runs clears the mark
        val (again, active) = runner()
        val done = again.run(dao.next()!!, active)
        assertNull(done.error)
        assertEquals(RemuxJobState.DONE, done.state)
        // and a job already taken up is not marked
        dao.insert(sampleJob(id = 2, state = RemuxJobState.RUNNING))
        runner.waitForApp(dao.get(2)!!)
        assertNull(dao.get(2)!!.error)
    }

    @Test
    fun aCancelAskedWhileRunningEndsCancelledWithoutAnOutput() {
        engine.onStep = { phase, i -> if (phase == "remux" && i == 2) dao.requestCancel(1) }
        val (runner, active) = runner()
        val cancelled = runner.run(queued(), active)
        assertEquals(RemuxJobState.CANCELLED, cancelled.state)
        assertEquals(RemuxJobError.CANCELLED, cancelled.error)
        assertEquals(false, cancelled.cancelRequested)
        assertEquals(1, engine.removed.size)
        assertEquals(listOf("remux:7"), engine.calls)
    }

    @Test
    fun aCancelDuringTheVerificationCountsToo() {
        engine.onStep = { phase, i -> if (phase == "verify" && i == 1) dao.requestCancel(1) }
        val (runner, active) = runner()
        val cancelled = runner.run(queued(), active)
        assertEquals(RemuxJobState.CANCELLED, cancelled.state)
        assertEquals(1, engine.removed.size)
    }

    @Test
    fun aCancelBetweenTheRemuxAndTheVerificationIsNotLost() {
        engine.onStep = { phase, _ -> if (phase == "remux-done") dao.requestCancel(1) }
        val (runner, active) = runner()
        val cancelled = runner.run(queued(), active)
        assertEquals(RemuxJobState.CANCELLED, cancelled.state)
        assertEquals(1, engine.removed.size)
        // the verification was asked, and stopped at its first step
        assertEquals(2, engine.calls.size)
    }

    @Test
    fun aStoppedWorkerPutsTheJobBackInTheQueue() {
        var active = true
        engine.onStep = { phase, i -> if (phase == "remux" && i == 1) active = false }
        val (runner, _) = runner()
        val back = runner.run(queued()) { if (active) null else RemuxEvent.WORKER_STOPPED }
        assertEquals(RemuxJobState.QUEUED, back.state)
        assertEquals(0f, back.progress)
        assertNull(back.error)
        assertTrue("the document stays for the next attempt, emptied", engine.removed.isEmpty() && engine.emptied.size == 1)
        assertEquals(listOf(RemuxEvent.STARTED, RemuxEvent.INTERRUPTED), RemuxEvent.parse(back.events).map { it.kind })
        assertEquals(RemuxEvent.WORKER_STOPPED, RemuxJobView.of(back).interruption)
        // the next worker takes it again, from the beginning, as a second attempt
        active = true
        engine.onStep = { _, _ -> }
        val done = runner.run(dao.next()!!) { if (active) null else RemuxEvent.WORKER_STOPPED }
        assertEquals(RemuxJobState.DONE, done.state)
        assertEquals(2, done.attempts)
        assertEquals(listOf(RemuxEvent.STARTED, RemuxEvent.INTERRUPTED, RemuxEvent.RESTARTED), RemuxEvent.parse(done.events).map { it.kind })
        assertTrue(RemuxJobView.of(done).restarted)
    }

    @Test
    fun aForegroundTimeoutIsRecordedForTheUser() {
        var timedOut = false
        engine.onStep = { phase, i -> if (phase == "verify" && i == 2) timedOut = true }
        val (runner, _) = runner()
        val back = runner.run(queued()) { if (timedOut) RemuxEvent.FOREGROUND_TIMEOUT else null }
        assertEquals(RemuxJobState.QUEUED, back.state)
        assertEquals(RemuxEvent.FOREGROUND_TIMEOUT, RemuxJobView.of(back).interruption)
        assertEquals(1, engine.emptied.size)
        // a cancel while it waits for the app removes what is left
        assertEquals(RemuxJobState.QUEUED, dao.next()!!.state)
    }

    @Test
    fun aVerificationOfNoPacketsIsNoVerification() {
        engine.resultJson = sampleResultJson.replace("\"video_packets\":189720", "\"video_packets\":0")
        engine.verificationJson = sampleVerificationJson.replace("\"expected_packets\":189720,\"packets\":189720", "\"expected_packets\":0,\"packets\":0")
        val (runner, active) = runner()
        val failed = runner.run(queued(), active)
        assertEquals(RemuxJobState.FAILED, failed.state)
        assertEquals(RemuxJobError.VERIFY, failed.error)
        assertNull(failed.videoHash)
    }

    @Test
    fun jobsLeftByADeadProcessAreQueuedAgain() {
        dao.insert(sampleJob(id = 1, state = RemuxJobState.RUNNING, progress = 0.6f, mediaSeconds = 400.0, bytesOut = 5, attempts = 1, ownsOutput = true))
        dao.insert(sampleJob(id = 2, state = RemuxJobState.VERIFYING, attempts = 1, ownsOutput = true))
        dao.insert(sampleJob(id = 3, state = RemuxJobState.DONE, attempts = 1, videoHash = SAMPLE_HASH, ownsOutput = true))
        dao.insert(sampleJob(id = 4, state = RemuxJobState.PAUSED, error = RemuxJobError.BATTERY, attempts = 1, ownsOutput = true))
        val (runner, _) = runner()
        runner.recoverInterrupted()
        assertEquals(listOf(RemuxJobState.QUEUED, RemuxJobState.QUEUED, RemuxJobState.DONE, RemuxJobState.QUEUED), (1L..4L).map { dao.get(it)!!.state })
        assertEquals("the partial outputs are emptied", 3, engine.emptied.size)
        assertEquals(RemuxEvent.PROCESS_DIED, RemuxJobView.of(dao.get(1)!!).interruption)
        dao.get(1)!!.let {
            assertEquals(0f, it.progress)
            assertEquals(0.0, it.mediaSeconds, 0.0)
            assertEquals(listOf(RemuxEvent.INTERRUPTED), RemuxEvent.parse(it.events).map { e -> e.kind })
        }
        assertNull(dao.get(4)!!.error)
        // the oldest first
        assertEquals(1L, dao.next()!!.id)
    }

    @Test
    fun drainRunsEveryWaitingJobInOrder() {
        queued(1)
        // each job into its own new file: a file another job wrote is never written again
        dao.insert(sampleJob(id = 2).copy(outputUri = SAMPLE_OUTPUT + "-2"))
        dao.insert(sampleJob(id = 3, state = RemuxJobState.CANCELLED))
        val (runner, active) = runner()
        runner.drain(active)
        assertEquals(RemuxJobState.DONE, dao.get(1)!!.state)
        assertEquals(RemuxJobState.DONE, dao.get(2)!!.state)
        assertEquals(RemuxJobState.CANCELLED, dao.get(3)!!.state)
        assertEquals(4, engine.calls.size)
        assertNull(dao.next())
        // a worker told to stop leaves the rest waiting
        dao.insert(sampleJob(id = 4).copy(outputUri = SAMPLE_OUTPUT + "-4"))
        runner.drain { RemuxEvent.WORKER_STOPPED }
        assertEquals(RemuxJobState.QUEUED, dao.get(4)!!.state)
    }

    @Test
    fun aLowBatteryPausesTheJobAndChargingResumesIt() {
        var step = 0
        engine.onStep = { phase, i ->
            if (phase == "remux" && i == 1) condition = DeviceState(12, charging = false, heat = Heat.None)
            if (phase == "remux-wait" && ++step == 3) condition = DeviceState(12, charging = true, heat = Heat.None)
        }
        val (runner, active) = runner()
        val done = runner.run(queued(), active)
        assertEquals(RemuxJobState.DONE, done.state)
        assertEquals(List(3) { ConvertPolicy.PAUSE_WAIT_MS.toInt() }, engine.waits)
        val kinds = RemuxEvent.parse(done.events)
        assertEquals(listOf(RemuxEvent.STARTED, RemuxEvent.PAUSED, RemuxEvent.RESUMED), kinds.map { it.kind })
        assertEquals(RemuxJobError.BATTERY, kinds[1].detail)
        // while it waited the row said so
        assertTrue(dao.updates.any { it.state == RemuxJobState.PAUSED && it.error == RemuxJobError.BATTERY })
        assertNull(done.error)
    }

    @Test
    fun aDrainedBatteryStopsTheJob() {
        engine.onStep = { phase, i -> if (phase == "remux" && i == 2) condition = DeviceState(3, charging = false, heat = Heat.None) }
        val (runner, active) = runner()
        val failed = runner.run(queued(), active)
        assertEquals(RemuxJobState.FAILED, failed.state)
        assertEquals(RemuxJobError.BATTERY, failed.error)
        assertEquals(1, engine.removed.size)
        assertEquals(RemuxEvent.STOPPED, RemuxEvent.parse(failed.events).last().kind)
    }

    @Test
    fun heatSlowsThenPausesTheJob() {
        var waits = 0
        engine.onStep = { phase, i ->
            if (phase == "remux" && i == 1) condition = DeviceState(90, charging = true, heat = Heat.Moderate)
            if (phase == "verify" && i == 0) condition = DeviceState(90, charging = true, heat = Heat.Severe)
            if (phase == "verify-wait" && ++waits == 2) condition = DeviceState.FINE
        }
        val (runner, active) = runner()
        val done = runner.run(queued(), active)
        assertEquals(RemuxJobState.DONE, done.state)
        // a step of a slowed job waits once and goes on; a paused one waits until the device cools
        assertEquals(listOf(ConvertPolicy.SLOW_WAIT_MS.toInt(), ConvertPolicy.SLOW_WAIT_MS.toInt(), ConvertPolicy.SLOW_WAIT_MS.toInt(), ConvertPolicy.PAUSE_WAIT_MS.toInt(), ConvertPolicy.PAUSE_WAIT_MS.toInt()), engine.waits)
        assertEquals(listOf(RemuxEvent.STARTED, RemuxEvent.SLOWED, RemuxEvent.PAUSED, RemuxEvent.RESUMED), RemuxEvent.parse(done.events).map { it.kind })
        assertTrue(RemuxJobView.of(done).slowed)
    }

    @Test
    fun theViewReadsTheRow() {
        val view = sampleDoneJob()
        assertTrue(view.verified)
        assertTrue(view.convertsDolbyVision)
        assertEquals(189_720L, view.verification!!.packets)
        assertEquals(7, view.plan!!.streams.size)
        val failed = sampleFailedJob()
        assertEquals(false, failed.verified)
        assertTrue(failed.restarted)
        assertEquals(RemuxJobError.BATTERY, samplePausedJob().pauseReason)
        assertNull(sampleRunningJob().pauseReason)
    }
}
