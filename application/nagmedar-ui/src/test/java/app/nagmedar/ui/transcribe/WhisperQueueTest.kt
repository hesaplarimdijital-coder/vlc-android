package app.nagmedar.ui.transcribe

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Podcast transcripts: the Whisper queue's order, its one job per episode, its failures, and what a job needs to start. */
@RunWith(AndroidJUnit4::class)
class WhisperQueueTest {

    private fun hand(id: Long) = WhisperRequest(id, "Bölüm $id")

    private fun auto(id: Long) = WhisperRequest(id, "Bölüm $id", auto = true)

    private fun WhisperQueue.ids() = requests.map { it.episodeId }

    @Test
    fun theOnesAskedByHandComeBeforeTheAutomaticOnes() {
        val queue = WhisperQueue().add(auto(1)).add(hand(2)).add(hand(3)).add(auto(4))
        assertEquals(listOf(2L, 3L, 1L, 4L), queue.ids())
        assertEquals(2L, queue.head?.episodeId)
        assertEquals(listOf(false, false, true, true), queue.requests.map { it.auto })
    }

    @Test
    fun nothingIsPutBeforeTheJobUnderWay() {
        val queue = WhisperQueue().add(auto(1)).begin(1).add(hand(2))
        assertEquals(listOf(1L, 2L), queue.ids())
        // the next job is the first after the one under way
        assertEquals(2L, queue.next?.episodeId)
        assertEquals(1L, queue.paused(1).next?.episodeId)
        assertNull(queue.paused(1).running)
    }

    @Test
    fun anEpisodeIsQueuedOnce() {
        val twice = WhisperQueue().add(hand(1)).add(hand(1))
        assertEquals(listOf(1L), twice.ids())
        // an automatic ask of one asked by hand changes nothing
        assertFalse(twice.add(auto(1)).head!!.auto)
        // a hand's ask moves an automatic one up among those asked by hand
        val moved = WhisperQueue().add(hand(1)).add(auto(2)).add(auto(3)).add(hand(3))
        assertEquals(listOf(1L, 3L, 2L), moved.ids())
        assertFalse(moved.requests[1].auto)
        // the automatic one under way stays where it is, now as asked by hand
        val running = WhisperQueue().add(auto(1)).begin(1).add(hand(1))
        assertEquals(listOf(1L), running.ids())
        assertFalse(running.head!!.auto)
    }

    @Test
    fun aFailureIsKeptForItsPageUntilTheEpisodeIsAskedAgain() {
        val failed = WhisperQueue().add(hand(1)).add(hand(2)).begin(1).failed(1, WhisperFailure.Fetch)
        assertEquals(listOf(2L), failed.ids())
        assertNull(failed.running)
        assertEquals(WhisperFailure.Fetch, failed.failures[1])
        val again = failed.add(hand(1))
        assertNull(again.failures[1])
        assertTrue(again.has(1))
        // only the latest failures are kept
        var many = WhisperQueue()
        for (id in 1L..30L) many = many.failed(id, WhisperFailure.Empty)
        assertEquals(WhisperQueue.MAX_FAILURES, many.failures.size)
        assertEquals((11L..30L).toSet(), many.failures.keys)
    }

    @Test
    fun aRemovedJobLeavesTheQueue() {
        val queue = WhisperQueue().add(hand(1)).add(hand(2)).begin(1).remove(1)
        assertEquals(listOf(2L), queue.ids())
        assertNull(queue.running)
        assertEquals(queue, queue.remove(9))
    }

    @Test
    fun theQueueIsKeptWithoutTheJobUnderWay() {
        val queue = WhisperQueue().add(hand(1)).add(WhisperRequest(2, "Gece · 2", WhisperModel.Turbo, auto = true)).begin(1).failed(3, WhisperFailure.Timeout)
        val read = WhisperQueue.parse(queue.toJson())
        assertEquals(queue.requests, read.requests)
        assertEquals(queue.failures, read.failures)
        // a job taken up after a restart takes its request again
        assertNull(read.running)
        assertEquals(WhisperQueue(), WhisperQueue.parse(null))
        assertEquals(WhisperQueue(), WhisperQueue.parse("{bozuk"))
    }

    @Test
    fun aJobNeedsANetworkOnlyForWhatItDownloads() {
        assertEquals(JobNeeds(), JobNeeds.of(hand(1), modelReady = true, downloaded = true, wifiOnly = true))
        assertEquals(NetworkNeed.Any, JobNeeds.of(hand(1), modelReady = true, downloaded = false, wifiOnly = false).network)
        assertEquals(NetworkNeed.Any, JobNeeds.of(hand(1), modelReady = false, downloaded = true, wifiOnly = false).network)
        assertEquals(NetworkNeed.Unmetered, JobNeeds.of(hand(1), modelReady = true, downloaded = false, wifiOnly = true).network)
        // an automatic one waits for the charger and an idle device, and for Wi-Fi when it downloads
        assertEquals(JobNeeds(NetworkNeed.None, charging = true, idle = true), JobNeeds.of(auto(1), modelReady = true, downloaded = true, wifiOnly = false))
        assertEquals(JobNeeds(NetworkNeed.Unmetered, charging = true, idle = true), JobNeeds.of(auto(1), modelReady = false, downloaded = true, wifiOnly = false))
    }

    @Test
    fun aJobStartedWithMoreRunsWhatNeedsLess() {
        val auto = JobNeeds(NetworkNeed.Unmetered, charging = true, idle = true)
        assertTrue(auto.covers(JobNeeds(NetworkNeed.Any)))
        assertTrue(JobNeeds(NetworkNeed.Any).covers(JobNeeds()))
        assertFalse(JobNeeds().covers(JobNeeds(NetworkNeed.Any)))
        assertFalse(JobNeeds(NetworkNeed.Unmetered).covers(auto))
    }

    @Test
    fun aHeldRequestWaitsForTheAppAndGoesOnAsAskedByHand() {
        val queue = WhisperQueue().add(auto(1)).add(auto(2)).begin(1).hold(1)
        assertTrue(queue.waitsForApp)
        assertNull("not under way", queue.running)
        assertEquals("it keeps its place", listOf(1L, 2L), queue.ids())
        val released = queue.release()
        assertFalse(released.waitsForApp)
        // the charger and an idle device cannot come while the app is used
        assertEquals(listOf(false, true), released.requests.map { it.auto })
        assertEquals(listOf(1L, 2L), released.ids())
    }

    @Test
    fun aHeldRequestAskedAgainByHandOrRemovedWaitsNoMore() {
        val held = WhisperQueue().add(auto(1)).hold(1)
        assertFalse(held.add(hand(1)).waitsForApp)
        assertTrue("an automatic ask again changes nothing", held.add(auto(1)).waitsForApp)
        assertTrue(held.remove(1).held.isEmpty())
        assertTrue(held.failed(1, WhisperFailure.Engine).held.isEmpty())
        // one no longer queued is not held
        assertTrue(WhisperQueue().hold(5).held.isEmpty())
    }

    @Test
    fun theHoldsAreKeptWithTheQueue() {
        val queue = WhisperQueue().add(auto(1)).add(hand(2)).hold(1)
        assertEquals(setOf(1L), WhisperQueue.parse(queue.toJson()).held)
        // a hold of a request gone is dropped
        val json = queue.toJson().replace("\"held\":[1]", "\"held\":[1,9]")
        assertEquals(setOf(1L), WhisperQueue.parse(json).held)
    }

    private fun stop(
        foregroundTimeLimit: Boolean = false,
        queued: Boolean = true,
        foreground: Boolean = true,
        jobTimeLimit: Boolean = false,
        attempt: Int = 0,
        appInFront: Boolean = false,
    ) = WhisperStop.of(foregroundTimeLimit, queued, foreground, jobTimeLimit, attempt, appInFront)

    @Test
    fun aStoppedJobFailsGoesOnWaitsOrIsTriedAgain() {
        assertEquals("Android 15's foreground time", WhisperStop.Fail, stop(foregroundTimeLimit = true))
        assertEquals("cancelled by the listener", WhisperStop.Next, stop(queued = false))
        assertEquals("the system stopped a foreground job", WhisperStop.Retry, stop(attempt = 3))
        // a plain job (no foreground) stopped at about ten minutes: once more, then it waits for the app
        assertEquals(WhisperStop.Retry, stop(foreground = false, jobTimeLimit = true))
        assertEquals(WhisperStop.Hold, stop(foreground = false, jobTimeLimit = true, attempt = 1))
        assertEquals(WhisperStop.Hold, stop(foreground = false, attempt = 1))
        assertEquals(WhisperStop.Hold, stop(jobTimeLimit = true, attempt = 2))
        // the app is in front: started again, it gets its foreground
        assertEquals(WhisperStop.Retry, stop(foreground = false, jobTimeLimit = true, attempt = 1, appInFront = true))
    }

    @Test
    fun aCheckpointGoesOnAfterItsLastPassage() {
        val checkpoint = WhisperCheckpoint(
            "small", "tr", null,
            listOf(SpeechSegment(0, 4_000, "Merhaba"), SpeechSegment(4_000, 9_500, "ve hoş geldiniz")),
        )
        assertEquals(9_500L, checkpoint.resumeAtMs)
        assertEquals(checkpoint, WhisperCheckpoint.parse(checkpoint.toJson()))
        assertEquals("en", WhisperCheckpoint.parse(checkpoint.copy(heard = "en").toJson())?.heard)
        assertNull(WhisperCheckpoint.parse("{bozuk"))
        assertEquals(0L, checkpoint.copy(segments = emptyList()).resumeAtMs)
    }
}
