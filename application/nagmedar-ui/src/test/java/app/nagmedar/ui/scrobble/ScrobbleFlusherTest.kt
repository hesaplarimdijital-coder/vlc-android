package app.nagmedar.ui.scrobble

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S-1008: the queue is sent in batches, kept when it cannot be sent, held when the service refuses the account. */
@RunWith(AndroidJUnit4::class)
class ScrobbleFlusherTest {

    private val store = FakeScrobbleStore()
    private val lastFm = FakeScrobbleService(ScrobbleServiceId.LastFm)
    private val brainz = FakeScrobbleService(ScrobbleServiceId.ListenBrainz)
    private val settings = ScrobbleSettings(freshPrefs(), FakeSecretBox())
    private var now = 1_800_000_000_000L
    private val flusher = ScrobbleFlusher(store, settings, listOf(lastFm, brainz)) { now }

    @Before
    fun signedIn() {
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "lastfm-key"))
        settings.signIn(ScrobbleServiceId.ListenBrainz, ScrobbleAccount("ada", "brainz-token"))
    }

    private fun queue(service: ScrobbleServiceId, count: Int) {
        repeat(count) { store.add(entry(service, "Parça $it", at = 1_800_000_000L + it)) }
    }

    @Test
    fun theQueueGoesInBatchesOfFiftyOldestFirst() {
        queue(ScrobbleServiceId.LastFm, 120)
        val result = runBlocking { flusher.flush() }
        assertEquals(FlushResult.Done, result)
        assertEquals(listOf(50, 50, 20), lastFm.batches.map { it.size })
        assertEquals("Parça 0", lastFm.batches.first().first().track)
        assertEquals("Parça 119", lastFm.batches.last().last().track)
        assertEquals(0, store.count(ScrobbleServiceId.LastFm))
        assertEquals(setOf("lastfm-key"), lastFm.secrets.toSet())
    }

    @Test
    fun eachServiceSendsItsOwnQueue() {
        queue(ScrobbleServiceId.LastFm, 2)
        queue(ScrobbleServiceId.ListenBrainz, 3)
        runBlocking { flusher.flush() }
        assertEquals(listOf(2), lastFm.batches.map { it.size })
        assertEquals(listOf(3), brainz.batches.map { it.size })
        assertEquals(listOf("brainz-token"), brainz.secrets.distinct())
    }

    @Test
    fun aServiceThatIsOffOrNotSignedInSendsNothingAndKeepsItsQueue() {
        queue(ScrobbleServiceId.LastFm, 2)
        queue(ScrobbleServiceId.ListenBrainz, 2)
        settings.setEnabled(ScrobbleServiceId.LastFm, false)
        settings.signOut(ScrobbleServiceId.ListenBrainz)
        assertEquals(FlushResult.Done, runBlocking { flusher.flush() })
        assertTrue(lastFm.batches.isEmpty() && brainz.batches.isEmpty())
        assertEquals(2, store.count(ScrobbleServiceId.LastFm))
        assertEquals(2, store.count(ScrobbleServiceId.ListenBrainz))
    }

    @Test
    fun noNetworkKeepsTheBatchCountsTheFailureAndAsksForAnotherTry() {
        queue(ScrobbleServiceId.LastFm, 3)
        queue(ScrobbleServiceId.ListenBrainz, 1)
        lastFm.outcomes = listOf(SubmitOutcome.Retry("offline"))
        val result = runBlocking { flusher.flush() }
        assertEquals(FlushResult.Retry, result)
        assertEquals(3, store.count(ScrobbleServiceId.LastFm))
        assertEquals(listOf(1, 1, 1), store.pending(ScrobbleServiceId.LastFm, 50).map { it.attempts })
        // the other service went on
        assertEquals(0, store.count(ScrobbleServiceId.ListenBrainz))
        // later the network is back: all of it goes, once
        lastFm.outcomes = listOf(SubmitOutcome.Accepted)
        lastFm.batches.clear()
        assertEquals(FlushResult.Done, runBlocking { flusher.flush() })
        assertEquals(1, lastFm.batches.size)
        assertEquals(3, lastFm.batches.single().size)
        assertEquals(0, store.count(ScrobbleServiceId.LastFm))
    }

    @Test
    fun aRefusedAccountHoldsTheQueueUntilTheUserSignsInAgain() {
        queue(ScrobbleServiceId.LastFm, 4)
        lastFm.outcomes = listOf(SubmitOutcome.AuthFailed("code 9"))
        assertEquals(FlushResult.Done, runBlocking { flusher.flush() })
        assertEquals("code 9", settings.hold(ScrobbleServiceId.LastFm)?.reason)
        assertEquals(4, store.count(ScrobbleServiceId.LastFm))
        // held: not asked again
        lastFm.batches.clear()
        runBlocking { flusher.flush() }
        assertTrue(lastFm.batches.isEmpty())
        // signed in again: the hold goes and the queue is sent
        lastFm.outcomes = listOf(SubmitOutcome.Accepted)
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "new-key"))
        assertNull(settings.hold(ScrobbleServiceId.LastFm))
        runBlocking { flusher.flush() }
        assertEquals(listOf(4), lastFm.batches.map { it.size })
        assertEquals("new-key", lastFm.secrets.last())
        assertEquals(0, store.count(ScrobbleServiceId.LastFm))
    }

    @Test
    fun aBatchTheServiceRefusesIsSentOneByOneAndOnlyTheBadOnesAreGivenUp() {
        queue(ScrobbleServiceId.ListenBrainz, 5)
        brainz.rule = { entries -> if (entries.size > 1 || entries.single().track == "Parça 2") SubmitOutcome.Rejected("status 400") else SubmitOutcome.Accepted }
        assertEquals(FlushResult.Done, runBlocking { flusher.flush() })
        assertEquals(0, store.count(ScrobbleServiceId.ListenBrainz))
        // the batch, then each of the five
        assertEquals(listOf(5, 1, 1, 1, 1, 1), brainz.batches.map { it.size })
        assertEquals(listOf("Parça 0", "Parça 1", "Parça 2", "Parça 3", "Parça 4"), brainz.batches.drop(1).map { it.single().track })
    }

    @Test
    fun aFailureInTheMiddleOfAnOneByOneSendKeepsTheRest() {
        queue(ScrobbleServiceId.ListenBrainz, 4)
        brainz.rule = { entries ->
            when {
                entries.size > 1 -> SubmitOutcome.Rejected()
                entries.single().track == "Parça 2" -> SubmitOutcome.Retry("offline")
                else -> SubmitOutcome.Accepted
            }
        }
        assertEquals(FlushResult.Retry, runBlocking { flusher.flush() })
        // the two sent before it are gone, it and the one after wait
        assertEquals(listOf("Parça 2", "Parça 3"), store.pending(ScrobbleServiceId.ListenBrainz, 50).map { it.track })
    }

    @Test
    fun whatLastFmWouldIgnoreForAgeIsDroppedBeforeSending() {
        val old = FakeScrobbleService(ScrobbleServiceId.LastFm, maxAgeSec = 14L * 24 * 3600)
        val aged = ScrobbleFlusher(store, settings, listOf(old)) { now }
        store.add(entry(ScrobbleServiceId.LastFm, "Eski", at = now / 1000 - 15L * 24 * 3600))
        store.add(entry(ScrobbleServiceId.LastFm, "Yeni", at = now / 1000 - 3600))
        runBlocking { aged.flush() }
        assertEquals(listOf("Yeni"), old.batches.single().map { it.track })
        assertEquals(0, store.count(ScrobbleServiceId.LastFm))
    }

    @Test
    fun aServiceThisBuildCannotUseIsLeftAlone() {
        val noKey = FakeScrobbleService(ScrobbleServiceId.LastFm, available = false)
        queue(ScrobbleServiceId.LastFm, 1)
        assertEquals(FlushResult.Done, runBlocking { ScrobbleFlusher(store, settings, listOf(noKey)).flush() })
        assertTrue(noKey.batches.isEmpty())
        assertEquals(1, store.count(ScrobbleServiceId.LastFm))
    }

    @Test
    fun anAccountWhoseSecretCannotBeOpenedIsHeld() {
        val prefs = freshPrefs("other-device")
        // another device's preferences: the name is there, the secret is sealed by a key that is not here
        prefs.edit().putString("nagmedar_scrobble_lastfm_account", "ada").putString("nagmedar_scrobble_lastfm_secret", "not-ours").putBoolean("nagmedar_scrobble_lastfm_on", true).commit()
        val copied = ScrobbleSettings(prefs, FakeSecretBox())
        queue(ScrobbleServiceId.LastFm, 1)
        runBlocking { ScrobbleFlusher(store, copied, listOf(lastFm)).flush() }
        assertNotNull(copied.hold(ScrobbleServiceId.LastFm))
        assertTrue(lastFm.batches.isEmpty())
        assertEquals(1, store.count(ScrobbleServiceId.LastFm))
    }
}
