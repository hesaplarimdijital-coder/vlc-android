package app.nagmedar.ui.scrobble

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-1008: what a play that counts becomes: a scrobble queued for each service that is on, and the job that sends the queue. */
@RunWith(AndroidJUnit4::class)
class ScrobbleHubTest {

    private val store = FakeScrobbleStore()
    private val lastFm = FakeScrobbleService(ScrobbleServiceId.LastFm)
    private val brainz = FakeScrobbleService(ScrobbleServiceId.ListenBrainz)
    private val scheduler = FakeScrobbleScheduler()
    private val settings = ScrobbleSettings(freshPrefs(), FakeSecretBox())
    private var now = 1_800_000_000_000L
    private val hub = ScrobbleHub(settings, store, listOf(lastFm, brainz), scheduler, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) { now }

    private val track = ScrobbleTrack("file:///m/a.flac", "Ada Karan", "Sabaha Kadar", "Gece Mavisi", "Ada Karan", 180)

    /** [track] played for [seconds] from its start, a sample a second. */
    private fun play(seconds: Int, from: Int = 0) {
        for (second in from..seconds) {
            hub.sample({ track }, second * 1_000L, 180_000, true)
            now += 1_000
        }
    }

    @Test
    fun aPlayThatCountsIsQueuedForEveryServiceThatIsOn() {
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "k"))
        settings.signIn(ScrobbleServiceId.ListenBrainz, ScrobbleAccount("ada", "t"))
        settings.setEnabled(ScrobbleServiceId.ListenBrainz, false)
        play(100)
        assertEquals(1, store.count(ScrobbleServiceId.LastFm))
        assertEquals(0, store.count(ScrobbleServiceId.ListenBrainz))
        val queued = store.rows.single()
        assertEquals("Sabaha Kadar", queued.track)
        assertEquals("Ada Karan", queued.albumArtist)
        assertEquals(180, queued.durationSec)
        assertEquals(1_800_000_000L, queued.listenedAt)
        assertEquals(listOf(false), scheduler.requests)
    }

    @Test
    fun theStartIsToldToTheServicesThatSendAndNotToAHeldOne() {
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "k"))
        settings.signIn(ScrobbleServiceId.ListenBrainz, ScrobbleAccount("ada", "t"))
        settings.setHold(ScrobbleServiceId.ListenBrainz, ScrobbleHold("code 9"))
        play(5)
        assertEquals(listOf(track), lastFm.playing)
        assertTrue(brainz.playing.isEmpty())
        // a held service still queues: nothing is lost
        play(100, from = 6)
        assertEquals(1, store.count(ScrobbleServiceId.ListenBrainz))
    }

    @Test
    fun withNoServiceOnTheTrackIsNotEvenLookedAt() {
        var asked = 0
        repeat(100) { hub.sample({ asked++; track }, it * 1_000L, 180_000, true) }
        assertEquals(0, asked)
        assertTrue(store.rows.isEmpty())
        assertTrue(scheduler.requests.isEmpty())
    }

    @Test
    fun aTrackNotScrobbledIsLeftAlone() {
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "k"))
        repeat(100) { hub.sample({ null }, it * 1_000L, 180_000, true) }
        assertTrue(store.rows.isEmpty())
        assertTrue(lastFm.playing.isEmpty())
    }

    @Test
    fun anOldQueueIsSentWhenTheServiceStarts() {
        hub.resume()
        assertTrue(scheduler.requests.isEmpty())
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "k"))
        store.add(entry())
        hub.resume()
        assertEquals(listOf(false), scheduler.requests)
    }
}
