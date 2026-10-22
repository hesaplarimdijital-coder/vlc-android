package app.nagmedar.ui.tvlab.remux

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.RemuxJobError
import app.nagmedar.data.RemuxJobState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-1206: the queue's own guards. An output that is the source, or that already holds
 * something, is refused at the door; an empty one is the job's from then on. A cancel of a
 * waiting job removes only an output that is the job's own (one it was queued with only while
 * it is still empty), and a job the worker took up meanwhile is asked to stop instead.
 */
@RunWith(AndroidJUnit4::class)
class RemuxQueueTest {

    private val dao = MemoryRemuxJobDao()
    private val engine = FakeRemuxEngine()
    private val queue = RemuxQueue(ApplicationProvider.getApplicationContext<Context>(), dao, engine)

    @Test
    fun theSourceOrATakenFileIsRefusedAsOutput() = runBlocking {
        val input = Uri.parse(SAMPLE_INPUT)
        assertEquals(RemuxQueue.REFUSED_SAME_FILE, queue.enqueue(input, "a.mkv", 1, input, "a.mkv", samplePlan))
        engine.outputsEmpty = false
        assertEquals(RemuxQueue.REFUSED_NOT_EMPTY, queue.enqueue(input, "a.mkv", 1, Uri.parse(SAMPLE_OUTPUT), "b.mkv", samplePlan))
        assertTrue(dao.all().isEmpty())
        assertTrue(engine.removed.isEmpty() && engine.emptied.isEmpty())
    }

    @Test
    fun aCancelledWaitingJobKeepsAnOutputThatIsNotItsOwn() = runBlocking {
        dao.insert(sampleJob(id = 1))
        dao.insert(sampleJob(id = 2, ownsOutput = true, attempts = 1))
        queue.cancel(1)
        assertEquals(RemuxJobState.CANCELLED, dao.get(1)!!.state)
        assertEquals(RemuxJobError.CANCELLED, dao.get(1)!!.error)
        assertTrue("never written by the job: left alone", engine.removed.isEmpty())
        queue.cancel(2)
        assertEquals(RemuxJobState.CANCELLED, dao.get(2)!!.state)
        assertEquals(listOf(Uri.parse(SAMPLE_OUTPUT)), engine.removed)
    }

    /** The document the picker or the batch made for a job, cancelled before it ran: removed while it is still empty. */
    @Test
    fun aJobCancelledBeforeItRanTakesItsEmptyDocumentAlong() = runBlocking {
        val input = Uri.parse(SAMPLE_INPUT)
        val id = queue.enqueue(input, "a.mkv", 1, Uri.parse(SAMPLE_OUTPUT), "a.remux.mkv", samplePlan, start = false)
        assertTrue("the empty document is the job's from the start", dao.get(id)!!.ownsOutput)
        queue.cancel(id)
        assertEquals(RemuxJobState.CANCELLED, dao.get(id)!!.state)
        assertEquals(listOf(Uri.parse(SAMPLE_OUTPUT)), engine.removed)
        assertFalse(dao.get(id)!!.ownsOutput)
        // something else wrote into the next one's document meanwhile: not the job's, left as it is
        val other = queue.enqueue(input, "b.mkv", 1, Uri.parse(SAMPLE_OUTPUT + "-2"), "b.remux.mkv", samplePlan, start = false)
        engine.outputsEmpty = false
        queue.cancel(other)
        assertEquals(1, engine.removed.size)
        assertFalse(dao.get(other)!!.ownsOutput)
        // forgotten, the row goes and the file stays
        queue.remove(other)
        assertNull(dao.get(other))
        assertEquals(1, engine.removed.size)
    }

    /** One that does not open at the cancel is kept to remove: forgetting the job removes it once it opens, still empty. */
    @Test
    fun anOutputThatDoesNotOpenAtTheCancelGoesWhenTheJobIsForgotten() = runBlocking {
        val id = queue.enqueue(Uri.parse(SAMPLE_INPUT), "a.mkv", 1, Uri.parse(SAMPLE_OUTPUT), "a.remux.mkv", samplePlan, start = false)
        engine.outputsEmpty = null
        queue.cancel(id)
        assertTrue(engine.removed.isEmpty())
        assertTrue("still the job's, to remove later", dao.get(id)!!.ownsOutput)
        queue.remove(id)
        assertNotNull("the row stays while its file cannot be told", dao.get(id))
        engine.outputsEmpty = true
        queue.remove(id)
        assertNull(dao.get(id))
        assertEquals(listOf(Uri.parse(SAMPLE_OUTPUT)), engine.removed)
    }

    @Test
    fun aJobTakenUpMeanwhileIsAskedToStop() = runBlocking {
        dao.insert(sampleJob(id = 1, state = RemuxJobState.RUNNING, ownsOutput = true))
        queue.cancel(1)
        assertEquals(RemuxJobState.RUNNING, dao.get(1)!!.state)
        assertTrue(dao.get(1)!!.cancelRequested)
        assertTrue("the worker removes it when the engine returns", engine.removed.isEmpty())
        // a finished job is left as it is
        dao.insert(sampleJob(id = 2, state = RemuxJobState.DONE, ownsOutput = true))
        queue.cancel(2)
        assertEquals(RemuxJobState.DONE, dao.get(2)!!.state)
        queue.remove(2)
        assertTrue(dao.get(2) == null)
    }
}
