package app.nagmedar.ui.playback

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.podcast.EpisodeAddresses
import app.nagmedar.ui.podcast.EpisodeMedia
import app.nagmedar.ui.podcast.PodcastSamples
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * 1.0.4, the owner's report of 12 Nov: "podcast çaldığında canlı yayın diyor müzik barı. Canlı yayın olmamalı". An
 * episode streamed from its enclosure is, to the player, what a station is: a stream without a length of its own.
 * 1.0.2's rule (a stream without a length is on the air) drew "● Canlı yayın" in its seek bar's place. The rule now
 * knows an episode: one the app starts from its first frame, one the player was left with as soon as Room has
 * answered; a station stays on the air.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayingLengthTest {

    @get:Rule
    val compose = createComposeRule()

    /** Room, pretended: the episodes' feed lengths by their addresses, and what was asked of it. */
    private val room = HashMap<String, Long>()
    private val asked = ArrayList<String>()
    private lateinit var source: (Context, String) -> Long?

    @Before
    fun pretendRoom() {
        EpisodeAddresses.forget()
        source = EpisodeAddresses.source
        EpisodeAddresses.source = { _, location ->
            synchronized(asked) { asked += location }
            room[location]
        }
    }

    @After
    fun giveRoomBack() {
        EpisodeAddresses.source = source
        EpisodeAddresses.forget()
    }

    private fun stream(address: String): MediaWrapper =
        MLServiceLocator.getAbstractMediaWrapper(Uri.parse(address)).apply { type = MediaWrapper.TYPE_AUDIO }

    /** What a bar makes of a media: "live" as every composition had it, the episode and the length as the last did. */
    private class Seen {
        val live = ArrayList<Boolean>()
        var episode: Long? = null
        var length = -1L
    }

    private fun bar(media: MediaWrapper): Seen {
        val seen = Seen()
        compose.setContent {
            val episode = rememberEpisodeLength(media)
            seen.episode = episode
            seen.live += PlayingLength.live(media.length, media.uri?.scheme, episode != null)
            seen.length = rememberPlayingLength(media, playing = false, episode = episode)
        }
        compose.waitForIdle()
        return seen
    }

    @Test
    fun aStationOnTheAirIsLiveAndAnEpisodeIsNot() {
        // a station: a stream, no length of its own, no episode
        assertTrue(PlayingLength.live(0L, "https", episode = false))
        assertTrue(PlayingLength.live(-1L, "HTTP", episode = false))
        // the owner's: an episode streamed from its enclosure, its length not found yet
        assertFalse(PlayingLength.live(0L, "https", episode = true))
        // a stream with a length of its own; a file of this device, whatever else is known of it
        assertFalse(PlayingLength.live(183_000L, "https", episode = false))
        assertFalse(PlayingLength.live(0L, "file", episode = false))
        assertFalse(PlayingLength.live(0L, "content", episode = false))
        assertFalse(PlayingLength.live(0L, null, episode = false))
    }

    @Test
    fun thePlayerIsAskedForAFileAndForAnEpisodeNotForAStation() {
        assertTrue(PlayingLength.asksPlayer(0L, "file", episode = false))
        assertTrue(PlayingLength.asksPlayer(0L, "content", episode = true))
        assertTrue(PlayingLength.asksPlayer(0L, "https", episode = true))
        assertFalse(PlayingLength.asksPlayer(0L, "https", episode = false))
        // a length of the media's own is taken as it is
        assertFalse(PlayingLength.asksPlayer(241_000L, "file", episode = false))
        assertFalse(PlayingLength.asksPlayer(241_000L, "https", episode = true))
    }

    @Test
    fun theLengthShownIsTheMediasThenThePlayersThenTheFeeds() {
        assertEquals(241_000L, PlayingLength.shown(own = 241_000L, player = 240_500L, feed = 239_000L))
        assertEquals(3_725_000L, PlayingLength.shown(own = 0L, player = 3_725_000L, feed = 3_723_000L))
        assertEquals(3_723_000L, PlayingLength.shown(own = 0L, player = 0L, feed = 3_723_000L))
        assertEquals(0L, PlayingLength.shown(own = 0L, player = 0L, feed = 0L))
        assertEquals(0L, PlayingLength.shown(own = 0L, player = 0L, feed = null))
        assertEquals(0L, PlayingLength.shown(own = -1L, player = -1L, feed = -5L))
    }

    @Test
    fun anEpisodeTheAppStartsIsNoStationFromItsFirstFrame() {
        val episode = PodcastSamples.episodes[0]
        assertNull(EpisodeAddresses.lengthOf(episode.audioUrl))
        val media = EpisodeMedia.of(PodcastSamples.gece, episode)
        // to the player it is what a station is, and 1.0.3 said so: a stream without a length of its own
        assertEquals(episode.audioUrl, media.location)
        assertTrue("1.0.3 took it for live", media.length <= 0L && isStream(media.uri?.scheme))
        assertEquals("told before it plays", episode.durationMs, EpisodeAddresses.lengthOf(media.location))
        val seen = bar(media)
        assertTrue(seen.live.isNotEmpty())
        assertFalse("never live, not for a frame: ${seen.live}", seen.live.any { it })
        // no player here: the feed's length stands in for it
        assertEquals(episode.durationMs, seen.length)
        assertTrue("Room is not asked for what it was told: $asked", asked.isEmpty())
    }

    @Test
    fun anEpisodeWhoseFeedGivesNoLengthIsNoStationEither() {
        val episode = PodcastSamples.episodes[1].copy(durationMs = 0)
        val seen = bar(EpisodeMedia.of(PodcastSamples.gece, episode))
        assertEquals(0L, seen.episode)
        assertFalse("never live: ${seen.live}", seen.live.any { it })
        assertEquals("no length yet: the time played alone", 0L, seen.length)
    }

    @Test
    fun anEpisodeThePlayerWasLeftWithIsKnownOnceRoomAnswers() {
        // the list restored at the next start: nobody here started it
        val address = "https://cdn.example.org/gece/77.mp3"
        room[address] = 2_700_000L
        val seen = bar(stream(address))
        compose.waitUntil(5_000) { seen.episode != null }
        compose.waitForIdle()
        assertEquals(2_700_000L, seen.episode)
        assertFalse("no station once Room has answered: ${seen.live}", seen.live.last())
        assertEquals(2_700_000L, seen.length)
        assertEquals("kept: the next timeline does not ask again", 2_700_000L, EpisodeAddresses.lengthOf(address))
        assertEquals(listOf(address), synchronized(asked) { asked.toList() })
    }

    @Test
    fun aStationStaysOnTheAir() {
        val address = "https://radyo.example.org/nagme.aac"
        val seen = bar(stream(address))
        compose.waitUntil(5_000) { synchronized(asked) { asked.isNotEmpty() } }
        compose.waitForIdle()
        assertNull(seen.episode)
        assertTrue("live in every frame: ${seen.live}", seen.live.all { it })
        assertEquals(0L, seen.length)
        assertNull(EpisodeAddresses.lengthOf(address))
    }

    @Test
    fun aFileIsNeverAskedAbout() {
        val seen = bar(stream("file:///storage/emulated/0/Music/Gece%20Yolcusu.mp3"))
        assertNull(seen.episode)
        assertFalse(seen.live.any { it })
        assertTrue("Room is asked about streams alone: $asked", synchronized(asked) { asked.isEmpty() })
    }

    @Test
    fun onlyTheLastAddressesAreKept() {
        for (i in 1..70) EpisodeAddresses.started("https://cdn.example.org/e/$i.mp3", i * 1_000L)
        assertNull("the oldest are let go", EpisodeAddresses.lengthOf("https://cdn.example.org/e/1.mp3"))
        assertEquals(70_000L, EpisodeAddresses.lengthOf("https://cdn.example.org/e/70.mp3"))
        // a feed's nonsense is no length
        EpisodeAddresses.started("https://cdn.example.org/e/x.mp3", -4L)
        assertEquals(0L, EpisodeAddresses.lengthOf("https://cdn.example.org/e/x.mp3"))
    }
}
