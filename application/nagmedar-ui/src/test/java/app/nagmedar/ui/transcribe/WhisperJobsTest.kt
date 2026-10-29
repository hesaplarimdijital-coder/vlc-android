package app.nagmedar.ui.transcribe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Podcast transcripts: a Whisper job started with what its request needs, none while the first waits for the app, cancelling; the checkpoints. */
@RunWith(AndroidJUnit4::class)
class WhisperJobsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        WhisperQueueStore.update(context) { WhisperQueue() }
    }

    private fun jobs(): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WhisperJobs.WORK).get()

    private val charger = JobNeeds(NetworkNeed.Unmetered, charging = true, idle = true)

    @Test
    fun aJobIsStartedWithWhatItsRequestNeeds() = runBlocking {
        WhisperQueueStore.update(context) { it.add(WhisperRequest(7, "Bölüm 7", auto = true)) }
        WhisperJobs.start(context, after = false, needs = charger)
        val job = jobs().single()
        // it waits for the charger and an idle device
        assertEquals(WorkInfo.State.ENQUEUED, job.state)
        assertTrue(job.constraints.requiresCharging())
        assertTrue(job.constraints.requiresDeviceIdle())
    }

    @Test
    fun noJobStartsWhileTheFirstWaitsForTheApp() = runBlocking {
        WhisperQueueStore.update(context) { it.add(WhisperRequest(7, "Bölüm 7", auto = true)).hold(7) }
        WhisperJobs.start(context, after = false, needs = charger)
        assertTrue(jobs().isEmpty())
    }

    @Test
    fun cancellingTakesTheRequestOutAndStopsItsJob() = runBlocking {
        WhisperQueueStore.update(context) { it.add(WhisperRequest(7, "Bölüm 7", auto = true)).add(WhisperRequest(8, "Bölüm 8", auto = true)) }
        WhisperJobs.start(context, after = false, needs = charger)
        WhisperJobs.cancel(context, 7)
        val queue = WhisperQueueStore.state(context).value
        assertFalse(queue.has(7))
        assertEquals(8L, queue.head?.episodeId)
        assertTrue(jobs().any { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun cancellingOneThatWaitsLeavesTheJobUnderWay() = runBlocking {
        WhisperQueueStore.update(context) { it.add(WhisperRequest(7, "Bölüm 7", auto = true)).add(WhisperRequest(8, "Bölüm 8", auto = true)) }
        WhisperJobs.start(context, after = false, needs = charger)
        WhisperJobs.cancel(context, 8)
        assertEquals(listOf(7L), WhisperQueueStore.state(context).value.requests.map { it.episodeId })
        assertEquals(WorkInfo.State.ENQUEUED, jobs().single().state)
    }

    @Test
    fun aCheckpointIsReadForItsModelAndLanguageOnly() {
        val passages = listOf(SpeechSegment(0, 3_000, "Merhaba"))
        assertTrue(WhisperProgress.write(context, 3, WhisperCheckpoint("small", "tr", null, passages)))
        assertEquals(passages, WhisperProgress.read(context, 3, WhisperModel.Small, "tr")?.segments)
        assertNull("another language starts afresh", WhisperProgress.read(context, 3, WhisperModel.Small, "en"))
        // those no longer asked for are removed
        WhisperProgress.tidy(context, kept = setOf(4))
        assertNull(WhisperProgress.read(context, 3, WhisperModel.Small, "tr"))
    }
}
