package app.nagmedar.ui.player.record

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext

/** S-607: the recordings' states: started, made, stopped, kept; and what goes wrong on the way. */
class RecorderTest {

    private val base: File = Files.createTempDirectory("recordings").toFile()

    @After
    fun clean() {
        base.deleteRecursively()
    }

    private class FakeEngine : RecordingEngine {
        override var available = true
        override var playing = true
        var takes = true
        val calls = mutableListOf<String>()

        override fun title(): String = "Kuzey Işığı: Final"

        override fun start(directory: String): Boolean {
            calls += "start"
            return takes
        }

        override fun stop(): Boolean {
            calls += "stop"
            return true
        }
    }

    private inner class FakeStorage : RecordingStorage {
        var free = 10L * 1024 * 1024 * 1024
        var failWith: RecordingFailure? = null
        val kept = mutableListOf<String>()
        val progress = mutableListOf<Long>()
        var folders = 0

        override fun newFolder(): File = File(base, "r${folders++}").apply { mkdirs() }

        override fun freeBytes(folder: File): Long = free

        var measures = 0

        override fun bytes(folder: File): Long {
            measures++
            return folder.listFiles()?.sumOf { it.length() } ?: 0L
        }

        override fun written(folder: File, path: String?): File? =
            path?.let(::File)?.takeIf { it.isFile } ?: folder.listFiles()?.maxByOrNull { it.length() }

        override suspend fun keep(file: File, name: String, durationMs: Long, progress: (Long, Long) -> Unit): KeptRecording {
            failWith?.let { throw RecordingException(it) }
            progress(file.length() / 2, file.length())
            this.progress += file.length() / 2
            kept += name
            return KeptRecording("content://kept/$name", "Movies/Nağmedar", null, recordingMime(file.extension))
        }

        override fun discard(folder: File) {
            folder.deleteRecursively()
        }
    }

    private val engine = FakeEngine()
    private val storage = FakeStorage()
    private val outcomes = mutableListOf<RecordingOutcome>()
    // the storage's work done in place: the test runs on one thread
    private val recorder = Recorder(engine, storage, CoroutineScope(Dispatchers.Unconfined), "Nağmedar", io = Dispatchers.Unconfined) { outcomes += it }

    /** 15 Ekim 2026 21:04:33 UTC. */
    private val wall = 1_792_098_273_000L

    private fun write(bytes: Int, name: String = "vlc-record.ts"): File {
        val folder = base.listFiles()!!.single { it.isDirectory }
        return File(folder, name).apply { writeBytes(ByteArray(bytes)) }
    }

    @Test
    fun recordsKeepsAndNamesIt() {
        recorder.toggle(nowMs = 0, wallMs = wall)
        assertEquals(RecordingStatus.Starting, recorder.status)
        assertTrue(recorder.recording)
        recorder.onRecordChanged(recording = true, path = null, nowMs = 100)
        assertEquals(RecordingStatus.On(0, 0), recorder.status)

        val file = write(4_000)
        recorder.tick(1_100)
        engine.playing = false
        // a pause does not count
        recorder.tick(5_100)
        engine.playing = true
        recorder.tick(6_100)
        assertEquals(RecordingStatus.On(2_000, 4_000), recorder.status)

        recorder.toggle(nowMs = 6_200, wallMs = wall)
        assertEquals(listOf("start", "stop"), engine.calls)
        assertTrue(recorder.status is RecordingStatus.Saving)
        assertFalse(recorder.recording)
        recorder.onRecordChanged(recording = false, path = file.path, nowMs = 6_300)

        val saved = recorder.outcome as RecordingOutcome.Saved
        assertTrue(saved.fileName, saved.fileName.startsWith("Kuzey Işığı_ Final 2026-10-"))
        assertTrue(saved.fileName, saved.fileName.endsWith(".ts"))
        assertEquals("video/mp2t", saved.mime)
        assertEquals(4_000L, saved.bytes)
        assertEquals(2_000L, saved.durationMs)
        assertFalse(saved.stoppedForSpace)
        assertEquals(listOf(saved.fileName), storage.kept)
        assertEquals(RecordingStatus.Off, recorder.status)
        // the app's copy went
        assertTrue(base.listFiles()!!.none { it.isDirectory })
        assertEquals(listOf<RecordingOutcome>(saved), outcomes)
    }

    @Test
    fun notStartingWithinTheTimeIsNotSupported() {
        recorder.start(nowMs = 0, wallMs = wall)
        recorder.tick(Recorder.START_TIMEOUT_MS - 1)
        assertEquals(RecordingStatus.Starting, recorder.status)
        recorder.tick(Recorder.START_TIMEOUT_MS)
        assertEquals(RecordingOutcome.Failed(RecordingFailure.NotSupported), recorder.outcome)
        assertEquals(listOf("start", "stop"), engine.calls)
        assertEquals(RecordingStatus.Off, recorder.status)
        assertTrue(base.listFiles()!!.isEmpty())
    }

    @Test
    fun theMusicEngineIsNotRecorded() {
        engine.available = false
        recorder.toggle(0, wall)
        assertEquals(RecordingOutcome.Failed(RecordingFailure.NotSupported), recorder.outcome)
        assertTrue(engine.calls.isEmpty())
        assertFalse(recorder.busy)
    }

    @Test
    fun libVlcRefusingIsNotSupported() {
        engine.takes = false
        recorder.toggle(0, wall)
        assertEquals(RecordingOutcome.Failed(RecordingFailure.NotSupported), recorder.outcome)
        assertTrue(base.listFiles()!!.isEmpty())
    }

    @Test
    fun aFullStorageStartsNothing() {
        storage.free = Recorder.MIN_FREE_BYTES - 1
        recorder.toggle(0, wall)
        assertEquals(RecordingOutcome.Failed(RecordingFailure.NoSpace), recorder.outcome)
        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun aStorageRunningLowStopsAndKeeps() {
        recorder.start(0, wall)
        recorder.onRecordChanged(true, null, 0)
        val file = write(1_000)
        storage.free = Recorder.LOW_FREE_BYTES - 1
        recorder.tick(500)
        assertEquals(listOf("start", "stop"), engine.calls)
        recorder.onRecordChanged(false, file.path, 600)
        val saved = recorder.outcome as RecordingOutcome.Saved
        assertTrue(saved.stoppedForSpace)
    }

    @Test
    fun theMediaEndingStopsIt() {
        recorder.start(0, wall)
        recorder.onRecordChanged(true, null, 0)
        write(2_000)
        recorder.onPlaybackEnded(1_000)
        // libVLC never said where: the folder's file is taken after a while
        recorder.tick(1_000 + Recorder.FINISH_TIMEOUT_MS - 1)
        assertNull(recorder.outcome)
        recorder.tick(1_000 + Recorder.FINISH_TIMEOUT_MS)
        assertEquals(2_000L, (recorder.outcome as RecordingOutcome.Saved).bytes)
    }

    @Test
    fun theMediaEndingBeforeItStartsIsNotSupported() {
        recorder.start(0, wall)
        recorder.onPlaybackEnded(100)
        assertEquals(RecordingOutcome.Failed(RecordingFailure.NotSupported), recorder.outcome)
    }

    @Test
    fun nothingWrittenIsEmpty() {
        recorder.start(0, wall)
        recorder.onRecordChanged(true, null, 0)
        recorder.stop(1_000)
        recorder.onRecordChanged(false, null, 1_100)
        assertEquals(RecordingOutcome.Failed(RecordingFailure.Empty), recorder.outcome)
        assertTrue(base.listFiles()!!.isEmpty())
    }

    @Test
    fun aFolderGoneKeepsItInTheAppUntilAnotherIsChosen() {
        recorder.start(0, wall)
        recorder.onRecordChanged(true, null, 0)
        val file = write(3_000, "vlc-record.mp3")
        recorder.stop(1_000)
        storage.failWith = RecordingFailure.Folder
        recorder.onRecordChanged(false, file.path, 1_100)
        assertEquals(RecordingOutcome.Failed(RecordingFailure.Folder, canRetry = true), recorder.outcome)
        assertTrue(file.isFile)

        storage.failWith = null
        recorder.retry()
        val saved = recorder.outcome as RecordingOutcome.Saved
        assertEquals("audio/mpeg", saved.mime)
        assertTrue(saved.fileName.endsWith(".mp3"))
        assertFalse(file.exists())
        assertEquals(listOf(1_500L), storage.progress)
    }

    @Test
    fun stoppingBeforeItStartsLeavesNothing() {
        recorder.toggle(0, wall)
        recorder.toggle(100, wall)
        assertEquals(RecordingStatus.Off, recorder.status)
        assertNull(recorder.outcome)
        assertTrue(base.listFiles()!!.isEmpty())
    }

    @Test
    fun aSecondRecordingWaitsForTheFirstToBeKept() {
        recorder.start(0, wall)
        recorder.onRecordChanged(true, null, 0)
        write(10)
        recorder.stop(100)
        // Finishing: Shift+R does nothing yet
        recorder.toggle(200, wall)
        assertEquals(listOf("start", "stop"), engine.calls)
    }

    @Test
    fun dismissingTheCardForgetsTheOutcome() {
        engine.available = false
        recorder.toggle(0, wall)
        recorder.dismiss()
        assertNull(recorder.outcome)
    }

    /** The storage's work, held until the test lets it run: what the main thread does meanwhile. */
    private class HeldWork : CoroutineDispatcher() {
        private val held = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            held.addLast(block)
        }

        fun runAll() {
            while (held.isNotEmpty()) held.removeFirst().run()
        }
    }

    @Test
    fun aStopAndAStartWhileTheFolderIsMadeStartOnce() {
        val work = HeldWork()
        val slow = Recorder(engine, storage, CoroutineScope(Dispatchers.Unconfined), "Nağmedar", io = work) { outcomes += it }
        slow.start(0, wall)
        assertEquals(RecordingStatus.Starting, slow.status)
        slow.stop(10)
        slow.start(20, wall)
        work.runAll()
        // the first start's folder goes, the second one records
        assertEquals(listOf("start"), engine.calls)
        assertEquals(1, base.listFiles()!!.count { it.isDirectory })
        assertTrue(slow.recording)
    }

    @Test
    fun ticksDoNotPileUpWhileTheStorageIsSlow() {
        val work = HeldWork()
        val slow = Recorder(engine, storage, CoroutineScope(Dispatchers.Unconfined), "Nağmedar", io = work) { outcomes += it }
        slow.start(0, wall)
        work.runAll()
        slow.onRecordChanged(true, null, 0)
        slow.tick(500)
        slow.tick(1_000)
        slow.tick(1_500)
        work.runAll()
        assertEquals(1, storage.measures)
        // the next tick measures again
        slow.tick(2_000)
        work.runAll()
        assertEquals(2, storage.measures)
        assertEquals(RecordingStatus.On(2_000, 0), slow.status)
    }

    @Test
    fun leftoversAreThoseOfAnEarlierRun() {
        val old = File(base, "1000").apply { mkdirs() }
        File(old, "vlc-record.ts").writeBytes(ByteArray(10))
        val now = File(base, "5000").apply { mkdirs() }
        File(now, "vlc-record.ts").writeBytes(ByteArray(10))
        val leftovers = object : RecordingStorage by storage {
            override fun leftovers(): List<File> = listOf(old, now)
        }
        Recorder(engine, leftovers, CoroutineScope(Dispatchers.Unconfined), "Nağmedar", io = Dispatchers.Unconfined).recoverLeftovers(beforeMs = 5_000)
        assertEquals(listOf("vlc-record.ts"), storage.kept)
        assertFalse(old.exists())
        // made by this run: left alone
        assertTrue(now.exists())
    }
}
