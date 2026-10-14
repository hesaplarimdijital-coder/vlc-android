package app.nagmedar.ui.scrobble

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S-1008: the background job: one at a time, "send now" starts over, and it runs the flusher. */
@RunWith(AndroidJUnit4::class)
class ScrobbleWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun workManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun jobs() = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkScrobbleScheduler.NAME).get()

    @Test
    fun theJobRunsTheFlusherAndSucceedsWhenNothingIsLeftToDo() {
        val worker = TestListenableWorkerBuilder<ScrobbleWorker>(context).build()
        val result = runBlocking { worker.doWork() }
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun aScrobbleQueuedWhileAJobWaitsDoesNotMakeAnother() {
        val scheduler = WorkScrobbleScheduler(context)
        scheduler.schedule()
        scheduler.schedule()
        val waiting = jobs().filter { !it.state.isFinished }
        assertEquals(1, waiting.size)
        assertEquals(WorkInfo.State.ENQUEUED, waiting.single().state)
    }

    @Test
    fun sendNowStartsOverInsteadOfWaiting() {
        val scheduler = WorkScrobbleScheduler(context)
        scheduler.schedule()
        val first = jobs().single { !it.state.isFinished }.id
        scheduler.schedule(now = true)
        val waiting = jobs().filter { !it.state.isFinished }
        assertEquals(1, waiting.size)
        assertNotEquals(first, waiting.single().id)
    }
}
