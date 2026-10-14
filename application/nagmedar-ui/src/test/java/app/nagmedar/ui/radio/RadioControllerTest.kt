package app.nagmedar.ui.radio

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-1105: a vote is cast once a day for a station, and says what became of it. */
@RunWith(AndroidJUnit4::class)
class RadioControllerTest {

    private val directory = FakeDirectory()
    private val library = FakeLibrary()
    private val sink = RecordingSink()
    private var now = 1_760_000_000_000L
    private val hour = 60 * 60_000L

    private fun controller(): RadioController {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val controller = RadioController(directory, library, RadioPlayer(directory, library, sink, { now }, clickScope = inlineClicks()), scope, { now }, debounceMs = 0)
        directory.onSearch = { listOf(RadioSamples.jazz) }
        controller.search.start()
        return controller
    }

    @Test
    fun theRule() {
        val day = VoteRule.DAY_MS
        assertEquals(86_400_000L, day)
        assertTrue(VoteRule.canVote(0, now))
        assertFalse(VoteRule.canVote(now - 1, now))
        assertFalse(VoteRule.canVote(now - day + 1, now))
        assertTrue(VoteRule.canVote(now - day, now))
        // a clock set back does not lock the vote for good
        assertTrue(VoteRule.canVote(now + 5 * day, now))
        assertEquals(0L, VoteRule.waitMs(0, now))
        assertEquals(day - hour, VoteRule.waitMs(now - hour, now))
        assertEquals(0L, VoteRule.waitMs(now - 2 * day, now))
    }

    @Test
    fun twoQuickTapsSendOneVote() = runBlocking {
        val controller = controller()
        val station = controller.search.stations.single()
        val release = CompletableDeferred<Unit>()
        directory.onVote = {
            release.await()
            VoteResult(true, "voted for station successfully")
        }
        controller.vote(station)
        controller.vote(station)
        release.complete(Unit)
        assertEquals(listOf("a1-caz"), directory.votes.toList())
        assertEquals(RadioNotice.Voted("Gece Cazı"), controller.notice)
        // once it is cast, the rule holds
        controller.vote(controller.search.stations.single())
        assertEquals(listOf("a1-caz"), directory.votes.toList())
    }

    @Test
    fun aStaleRowDoesNotSendASecondVote() {
        val controller = controller()
        val stale = controller.search.stations.single()
        controller.vote(stale)
        assertEquals(1, directory.votes.size)
        // the row in hand is from before the vote
        controller.vote(stale)
        assertEquals(1, directory.votes.size)
        assertEquals(RadioNotice.AlreadyVoted("Gece Cazı"), controller.notice)
    }

    @Test
    fun aVoteIsCountedAndRemembered() {
        val controller = controller()
        val voted = controller.search.stations.single()
        controller.vote(voted)
        assertEquals(listOf("a1-caz"), directory.votes.toList())
        assertEquals(RadioNotice.Voted("Gece Cazı"), controller.notice)
        val row = controller.search.stations.single()
        assertEquals(now, row.lastVotedAt)
        assertEquals(RadioSamples.jazz.votes + 1, row.votes)
        assertEquals(now, library.kept.single().lastVotedAt)
    }

    @Test
    fun theSameStationTwiceInADayIsVotedOnce() {
        val controller = controller()
        controller.vote(controller.search.stations.single())
        controller.noticed()
        assertNull(controller.notice)
        now += 3 * hour
        controller.vote(controller.search.stations.single())
        assertEquals(1, directory.votes.size)
        assertEquals(RadioNotice.AlreadyVoted("Gece Cazı"), controller.notice)
        // a day on, it may be voted for again
        now += 22 * hour
        controller.vote(controller.search.stations.single())
        assertEquals(2, directory.votes.size)
        assertEquals(RadioNotice.Voted("Gece Cazı"), controller.notice)
    }

    @Test
    fun otherStationsAreVotedForTheSameDay() {
        val controller = controller()
        directory.onSearch = { RadioSamples.all }
        controller.search.search()
        controller.search.stations.forEach { controller.vote(it) }
        assertEquals(RadioSamples.all.map { it.uuid }, directory.votes.toList())
    }

    @Test
    fun aVoteTheDirectoryRefusedDoesNotCount() {
        val controller = controller()
        directory.onVote = { VoteResult(false, "VoteError 'you are voting for the same station too often'") }
        controller.vote(controller.search.stations.single())
        assertEquals(RadioNotice.VoteRefused("Gece Cazı"), controller.notice)
        val row = controller.search.stations.single()
        assertEquals(0L, row.lastVotedAt)
        assertEquals(RadioSamples.jazz.votes, row.votes)
        // so it may be tried again at once
        directory.onVote = { VoteResult(true, "") }
        controller.vote(row)
        assertEquals(RadioNotice.Voted("Gece Cazı"), controller.notice)
    }

    @Test
    fun aVoteThatCouldNotBeSentIsNotRemembered() {
        val controller = controller()
        directory.onVote = { throw RadioFailure.Offline() }
        controller.vote(controller.search.stations.single())
        assertEquals(RadioNotice.VoteOffline, controller.notice)
        assertEquals(0L, controller.search.stations.single().lastVotedAt)
        assertTrue(library.kept.isEmpty())
    }

    @Test
    fun aStationOfTheUsersHasNoOneToVoteFor() {
        val controller = controller()
        controller.vote(Station(uuid = null, name = "Benim Radyom", streamUrl = "http://ev.example/s"))
        assertTrue(directory.votes.isEmpty())
        assertNull(controller.notice)
    }

    @Test
    fun playingStartsTheStationAndShowsItAsPlayed() {
        val controller = controller()
        directory.onClick = { StationClick("https://cdn.example/live", null) }
        controller.play(controller.search.stations.single())
        assertEquals("https://cdn.example/live", sink.played.single().url)
        assertEquals(listOf("a1-caz"), directory.clicks.toList())
        assertEquals(now, controller.search.stations.single().lastPlayedAt)
    }
}
