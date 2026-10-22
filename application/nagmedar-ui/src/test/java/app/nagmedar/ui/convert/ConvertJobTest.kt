package app.nagmedar.ui.convert

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import app.nagmedar.ui.backup.BackupSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * S-1010: a job and its summary kept in the app's storage and read back; the choices kept for
 * the next time (the folder out of a backup); what the music library shows from WorkManager.
 */
@RunWith(AndroidJUnit4::class)
class ConvertJobTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val items = listOf(
        ConvertItem("file:///m/03.flac", "Gece Yolcusu — Çığlık", listOf("Şebnem Ağaoğlu", "Işıklı Dönüş"), "03 Gece Yolcusu — Çığlık.opus"),
        ConvertItem("file:///m/album.flac#cue=2", "İkinci", listOf("Çeşitli", "Albüm"), "02 İkinci.opus", mapOf("TITLE" to "İkinci", "TRACKNUMBER" to "2")),
        ConvertItem("file:///m/x.flac", "x", emptyList(), "x.opus"),
    )

    private val job = ConvertJob(
        "a1",
        1_791_837_600_000L,
        ConvertOptions(ConvertPreset.Aac256, ConvertRate.Hz48000, ConvertNaming.Flat, skipExisting = false, folder = "content://tree/primary%3AM%C3%BCzik"),
        items,
    )

    @Test
    fun aJobReadBack() {
        assertEquals(job, ConvertJob.parse(job.toJson()))
        assertNull(ConvertJob.parse("{}"))
        assertNull(ConvertJob.parse("not json"))
    }

    @Test
    fun aSummaryReadBack() {
        val summary = ConvertSummary("a1", 3, emptyList())
            .with(ItemOutcome.Converted(items[0], 4_200_000))
            .with(ItemOutcome.Skipped(items[1]))
            .with(ItemOutcome.Failed(items[2], ConvertReason.Engine, "decoding: Invalid data \"x\""))
            .copy(finished = true)
        val back = ConvertSummary.parse(summary.toJson(), job)
        assertEquals(summary, back)
        assertEquals(1, back!!.converted)
        assertEquals(1, back.skipped)
        assertFalse(back.cancelled)
        // outcomes of files the job does not have are passed over
        val other = job.copy(items = items.take(1))
        assertEquals(1, ConvertSummary.parse(summary.toJson(), other)!!.outcomes.size)
    }

    @Test
    fun theStoresFolderIsAskedForAtTheFirstReadNotWhenItIsMade() {
        // the app's folder (filesDir) looks at the disk: the store is made on the main thread, read off it
        var asked = 0
        val dir = folder.newFolder("later")
        val store = ConvertStore { asked++; dir }
        assertEquals(0, asked)
        assertFalse(store.has("a1"))
        store.save(job)
        assertTrue(store.has("a1"))
        assertEquals(1, asked)
    }

    @Test
    fun theStoreKeepsAJobUntilItIsPutAway() {
        val store = ConvertStore(folder.newFolder("convert"))
        store.save(job)
        assertEquals(job, store.job("a1"))
        assertNull(store.summary(job))
        val summary = ConvertSummary("a1", 3, listOf(ItemOutcome.Converted(items[0], 10)))
        store.saveSummary(summary)
        assertEquals(summary, store.summary(job))
        assertFalse(store.isCancelled("a1"))
        store.markCancelled("a1")
        assertTrue(store.isCancelled("a1"))
        assertTrue(store.has("a1"))
        store.delete("a1")
        assertNull(store.job("a1"))
        assertFalse(store.has("a1"))
        assertFalse(store.isCancelled("a1"))
        // a new job puts the finished ones away, not those under way
        store.save(job)
        store.saveSummary(summary.copy(finished = true))
        store.save(job.copy(id = "b2"))
        store.saveSummary(ConvertSummary("b2", 3, emptyList()))
        // the folder of a job under way keeps its permission
        assertEquals(setOf(job.options.folder), store.activeFolders())
        store.prune(System.currentTimeMillis())
        assertFalse(store.has("a1"))
        assertTrue(store.has("b2"))
        // the temporary file being written, kept until it is done
        assertNull(store.partial("b2"))
        store.savePartial("b2", "content://tree/document/.03.opus.part")
        assertEquals("content://tree/document/.03.opus.part", store.partial("b2"))
        store.savePartial("b2", null)
        assertNull(store.partial("b2"))
    }

    @Test
    fun theChoicesForTheNextTime() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("convert-test", Context.MODE_PRIVATE)
        assertEquals(ConvertOptions(), ConvertOptions.read(prefs))
        job.options.write(prefs.edit()).apply()
        assertEquals(job.options, ConvertOptions.read(prefs))
        // a folder permission is this device's: a backup leaves it out
        assertTrue(ConvertOptions.KEY_FOLDER in BackupSettings.leftOut())
    }

    private fun work(state: WorkInfo.State, progress: Data = Data.EMPTY, output: Data = Data.EMPTY, job: String = "", attempts: Int = 0) =
        ConvertQueue.WorkState(state, progress, output, job, attempts)

    @Test
    fun whatTheLibraryShows() {
        val progress = ConvertProgress(2, 12, "Çığlık", 0.5, converted = 2, skipped = 0, failed = 0)
        val running = ConvertQueue.status(
            listOf(work(WorkInfo.State.RUNNING, ConvertQueue.progressData("a1", progress)), work(WorkInfo.State.BLOCKED)),
        ) { true }
        assertEquals(ConvertStatus.Running("a1", progress, 1), running)
        // waiting for the battery
        val waiting = ConvertQueue.status(listOf(work(WorkInfo.State.RUNNING, ConvertQueue.progressData("a1", progress.copy(waiting = ConvertReason.Battery))))) { true }
        assertEquals(ConvertReason.Battery, (waiting as ConvertStatus.Running).progress?.waiting)
        // the newest finished job whose summary is kept
        val done = ConvertSummary("b2", 3, listOf(ItemOutcome.Converted(items[0], 1), ItemOutcome.Skipped(items[1]), ItemOutcome.Failed(items[2], ConvertReason.Input)))
        val older = ConvertSummary("a1", 1, listOf(ItemOutcome.Converted(items[0], 1)))
        val works = listOf(
            work(WorkInfo.State.SUCCEEDED, output = ConvertQueue.outputData(done, 2_000)),
            work(WorkInfo.State.SUCCEEDED, output = ConvertQueue.outputData(older, 1_000)),
        )
        assertEquals(ConvertStatus.Finished("b2", 1, 1, 1, cancelled = false), ConvertQueue.status(works) { true })
        assertEquals(ConvertStatus.Finished("a1", 1, 0, 0, cancelled = false), ConvertQueue.status(works) { it == "a1" })
        assertNull(ConvertQueue.status(works) { false })
        assertNull(ConvertQueue.status(emptyList()) { true })
        // cancelled files are not counted as failed
        val stopped = ConvertSummary("c3", 2, listOf(ItemOutcome.Converted(items[0], 1), ItemOutcome.Failed(items[1], ConvertReason.Cancelled)))
        assertEquals(
            ConvertStatus.Finished("c3", 1, 0, 0, cancelled = true),
            ConvertQueue.status(listOf(work(WorkInfo.State.SUCCEEDED, output = ConvertQueue.outputData(stopped, 3_000)))) { true },
        )
        // a job queued, none running yet: it is about to start
        assertEquals(ConvertStatus.Running("", null, 0), ConvertQueue.status(listOf(work(WorkInfo.State.ENQUEUED))) { true })
        // one Android stopped (its time limit) waits to go on from where it was, and can be cancelled
        assertEquals(
            ConvertStatus.Running("a1", null, 1, resumes = true),
            ConvertQueue.status(listOf(work(WorkInfo.State.ENQUEUED, job = "a1", attempts = 1), work(WorkInfo.State.BLOCKED, job = "b2"))) { true },
        )
        // running, before its first progress: its id from its tag
        assertEquals(ConvertStatus.Running("c3", null, 0), ConvertQueue.status(listOf(work(WorkInfo.State.RUNNING, job = "c3"))) { true })
        // progress before the first file: no counts yet
        assertEquals(ConvertStatus.Running("a1", null, 0), ConvertQueue.status(listOf(work(WorkInfo.State.RUNNING, workDataOf("job" to "a1")))) { true })
    }
}
