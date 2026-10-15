package app.nagmedar.ui.convert

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.RemuxJobEntry
import app.nagmedar.data.RemuxJobError
import app.nagmedar.data.RemuxJobKind
import app.nagmedar.data.RemuxJobState
import app.nagmedar.ui.tvlab.remux.DeviceCondition
import app.nagmedar.ui.tvlab.remux.FakeRemuxEngine
import app.nagmedar.ui.tvlab.remux.MemoryRemuxJobDao
import app.nagmedar.ui.tvlab.remux.RemuxEvent
import app.nagmedar.ui.tvlab.remux.RemuxJobView
import app.nagmedar.ui.tvlab.remux.RemuxRunner
import app.nagmedar.ui.tvlab.remux.Thermal
import app.nagmedar.ui.tvlab.remux.sampleJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

/**
 * S-1303: a conversion in the media jobs' queue: it waits its turn after a remux and runs with
 * the stream output's chain; the encoding then the copy into the document make its progress;
 * a cancel, the battery, a stopped worker and libVLC's failure leave the row as the interface
 * expects, and never a file behind. Under Robolectric for the real Uri and JSON.
 */
@RunWith(AndroidJUnit4::class)
class ConvertRunnerTest {

    private val dao = MemoryRemuxJobDao()
    private var condition = DeviceCondition.FINE
    private var clock = 1_000L
    private val dir: File = Files.createTempDirectory("convert").toFile()
    private val files = FakeConvertFiles(dir)
    private val engine = FakeSoutEngine()
    private val updates = mutableListOf<String>()

    private fun runner() = ConvertRunner(engine, files, dao, { condition }, { clock += 1_000; clock }) { updates += it.state }

    private val profile = ConvertProfiles.presets.single { it.id == -2L }

    private fun queued(at: Long = 1): RemuxJobEntry = dao.get(
        dao.insert(
            RemuxJobEntry(
                inputUri = "content://docs/kuzey.mkv", inputName = "Kuzey Işığı.mkv", inputSize = 1, outputUri = "content://docs/out.mp4",
                outputName = "Kuzey Işığı.mp4", plan = profile.toJson(), createdAt = at, kind = RemuxJobKind.CONVERT,
                // as the queue hands it over: the empty document is the job's
                ownsOutput = true,
            ),
        ),
    )!!

    @Test
    fun aConversionEncodesThenSaves() {
        val job = queued()
        val done = runner().run(job) { null }
        assertEquals(RemuxJobState.DONE, done.state)
        assertNull(done.error)
        assertEquals(1f, done.progress)
        assertEquals(FakeSoutEngine.BYTES.toLong(), done.bytesOut)
        // the chain is the profile's, into the job's file in the app's storage
        val temp = File(dir, "job-${job.id}.mp4")
        assertEquals(SoutChain.convert(profile, temp.path), engine.options)
        assertEquals(Uri.parse("content://docs/kuzey.mkv"), engine.input)
        // saved into the document, and gone from the app's storage
        assertEquals(listOf("content://docs/out.mp4"), files.saved)
        assertFalse(temp.exists())
        assertTrue(files.removed.isEmpty())
        assertEquals(listOf(RemuxJobState.RUNNING, RemuxJobState.DONE), updates.distinct())
        // the view reads it as a conversion with its profile
        val view = RemuxJobView.of(dao.get(job.id)!!)
        assertTrue(view.converts)
        assertEquals(profile.copy(id = 0, nameRes = null), view.profile)
        assertNull(view.plan)
    }

    @Test
    fun aCancelStopsItAndLeavesNothing() {
        val job = queued()
        engine.onStep = { i -> if (i == 2) dao.requestCancel(job.id) }
        val done = runner().run(job) { null }
        assertEquals(RemuxJobState.CANCELLED, done.state)
        assertEquals(RemuxJobError.CANCELLED, done.error)
        assertEquals(listOf("content://docs/out.mp4"), files.removed)
        assertFalse(File(dir, "job-${job.id}.mp4").exists())
        assertFalse(done.cancelRequested)
    }

    @Test
    fun aLowBatteryPausesThenItGoesOn() {
        val job = queued()
        engine.onStep = { asked ->
            condition = when (asked) {
                1 -> DeviceCondition(12, charging = false, thermal = Thermal.None)
                4 -> DeviceCondition(80, charging = true, thermal = Thermal.None)
                else -> condition
            }
        }
        val done = runner().run(job) { null }
        assertEquals(RemuxJobState.DONE, done.state)
        assertTrue(engine.paused > 0)
        assertEquals(listOf(RemuxEvent.STARTED, RemuxEvent.PAUSED, RemuxEvent.RESUMED), RemuxEvent.parse(done.events).map { it.kind })
        assertTrue(RemuxJobState.PAUSED in updates)
    }

    @Test
    fun aStoppedWorkerSendsItBackToTheQueue() {
        val job = queued()
        var steps = 0
        val back = runner().run(job) { if (++steps > 2) RemuxEvent.WORKER_STOPPED else null }
        assertEquals(RemuxJobState.QUEUED, back.state)
        assertEquals(RemuxEvent.WORKER_STOPPED, RemuxEvent.parse(back.events).last().detail)
        assertFalse(File(dir, "job-${job.id}.mp4").exists())
        assertTrue(files.removed.isEmpty())
        // the document emptied for the next attempt, which writes only into an empty one
        assertEquals(listOf("content://docs/out.mp4"), files.emptied)
    }

    @Test
    fun aDocumentNotTheJobsIsNeverRemoved() {
        // the queue refused the output (it held something): a failure leaves it as it was
        val job = dao.get(dao.insert(queued().copy(id = 0, ownsOutput = false)))!!
        engine.outcome = SoutOutcome.Failed(LibVlcSoutEngine.ENCODE)
        assertEquals(RemuxJobState.FAILED, runner().run(job) { null }.state)
        assertTrue(files.removed.isEmpty())
        // a conversion into its own source is refused, the source untouched
        val same = dao.get(dao.insert(queued().copy(id = 0, outputUri = "content://docs/kuzey.mkv")))!!
        assertEquals(RemuxJobError.SAME_FILE, runner().run(same) { null }.error)
        assertTrue(files.removed.isEmpty())
        assertEquals(1, engine.calls)
    }

    @Test
    fun libVlcsFailureFailsTheJob() {
        val job = queued()
        engine.outcome = SoutOutcome.Failed(LibVlcSoutEngine.ENCODE)
        val failed = runner().run(job) { null }
        assertEquals(RemuxJobState.FAILED, failed.state)
        assertEquals(RemuxJobError.ENGINE + LibVlcSoutEngine.ENCODE, failed.error)
        assertEquals(listOf("content://docs/out.mp4"), files.removed)
        // a profile this build cannot encode is refused before libVLC is asked
        engine.calls = 0
        val h265 = dao.get(dao.insert(queued(2).copy(id = 0, plan = ConvertProfiles.presets.single { it.id == -3L }.toJson())))!!
        assertEquals(RemuxJobError.ENGINE + ConvertRunner.ENCODER, runner().run(h265) { null }.error)
        assertEquals(0, engine.calls)
    }

    @Test
    fun theQueueRunsConversionsAndRemuxesInTurn() {
        val remux = dao.insert(sampleJob(id = 1).copy(createdAt = 1))
        val convert = queued(at = 2).id
        val remuxEngine = FakeRemuxEngine()
        val queue = RemuxRunner(remuxEngine, dao, { condition }, { clock += 1_000; clock }, convert = runner())
        queue.drain()
        assertEquals(RemuxJobState.DONE, dao.get(remux)!!.state)
        assertEquals(RemuxJobState.DONE, dao.get(convert)!!.state)
        // the remux engine saw the remux only, libVLC the conversion only
        assertEquals(1, remuxEngine.calls.count { it.startsWith("remux") })
        assertEquals(1, engine.calls)
        // a conversion with no runner (a build without it) fails rather than being remuxed
        val alone = queued(at = 3)
        val done = RemuxRunner(remuxEngine, dao, { condition }).run(alone)
        assertEquals(RemuxJobState.FAILED, done.state)
        assertEquals(RemuxJobError.NO_TOOLS, done.error)
    }
}

/**
 * libVLC's stream output, faked: [STEPS] steps forward (a pause stays where it is), [onStep] told
 * the count of steer's questions; then [BYTES] bytes written into the destination its chain names.
 */
class FakeSoutEngine : SoutEngine {
    var input: Uri? = null
    var options: List<String> = emptyList()
    var outcome: SoutOutcome = SoutOutcome.Ended
    var onStep: (Int) -> Unit = {}
    var calls = 0
    var paused = 0

    override fun run(input: Uri, options: List<String>, steer: (SoutProgress) -> SoutSteer): SoutOutcome {
        calls++
        this.input = input
        this.options = options
        val destination = SoutChain.unquote(options.first().substringAfter("dst=").removeSuffix("}"))
        var i = 0
        var asked = 0
        while (i < STEPS) {
            onStep(asked++)
            when (steer(SoutProgress(i / STEPS.toFloat(), i * 10_000L, STEPS * 10_000L))) {
                SoutSteer.Stop -> return SoutOutcome.Stopped
                SoutSteer.Pause -> paused++
                SoutSteer.Go -> i++
            }
        }
        if (outcome == SoutOutcome.Ended) File(destination).writeBytes(ByteArray(BYTES))
        return outcome
    }

    companion object {
        const val STEPS = 5
        const val BYTES = 4096
    }
}

/** The app's storage in a folder of the test's, the documents remembered. */
class FakeConvertFiles(private val dir: File) : ConvertFiles {
    val saved = mutableListOf<String>()
    val removed = mutableListOf<String>()
    val emptied = mutableListOf<String>()

    override fun temp(id: Long, extension: String) = File(dir, "job-$id.$extension")

    override fun save(from: File, output: Uri, step: (Long) -> Boolean): Boolean {
        if (!step(from.length())) return false
        saved += output.toString()
        return true
    }

    override fun removeOutput(output: Uri) {
        removed += output.toString()
    }

    override fun emptyOutput(output: Uri) {
        emptied += output.toString()
    }
}
