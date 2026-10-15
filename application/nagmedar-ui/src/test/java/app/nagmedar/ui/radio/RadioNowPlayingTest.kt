package app.nagmedar.ui.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** S-1106: the strip shows the station started here for as long as the service plays its stream, with the title the stream sends. */
@RunWith(AndroidJUnit4::class)
class RadioNowPlayingTest {

    private val stream = StationStream(RadioSamples.jazz, "https://cdn.example/live.mp3")

    @Test
    fun nothingStartedHereOrNothingPlayingIsNoStrip() {
        assertNull(RadioNowPlaying.of(null, PlayingSnapshot(stream.url, null, true, isStream = true)))
        assertNull(RadioNowPlaying.of(stream, null))
    }

    @Test
    fun anotherMediaTakingOverEndsTheStrip() {
        assertNull(RadioNowPlaying.of(stream, PlayingSnapshot("file:///music/a.mp3", null, true)))
    }

    @Test
    fun aPlaylistStationKeepsItsStripWhenLibvlcExpandsIt() {
        val playlist = StationStream(RadioSamples.news, "https://yayin.example/haber.pls")
        // the entry has another address, and the service says what the media was expanded from
        val viaEntry = RadioNowPlaying.of(playlist, PlayingSnapshot("http://cdn7.example:8000/haber", "A - B", true, entry = "https://yayin.example/haber.pls", isStream = false))
        assertEquals("B", viaEntry?.display?.title)
    }

    @Test
    fun aLocalFilePlayedAfterAPlaylistStationHidesTheStrip() {
        val playlist = StationStream(RadioSamples.news, "https://yayin.example/haber.pls")
        // the service has no expansion for a media it did not expand: the entry is null
        assertNull(RadioNowPlaying.of(playlist, PlayingSnapshot("file:///music/a.mp3", null, true, entry = null, isStream = false)))
        assertNull(RadioNowPlaying.of(playlist, PlayingSnapshot("https://yayin.example/dosya.mp3", null, true, entry = null, isStream = false)))
    }

    @Test
    fun anotherStationOfTheSameHostIsNotThisStation() {
        val playlist = StationStream(RadioSamples.news, "https://yayin.example/haber.pls")
        assertNull(RadioNowPlaying.of(playlist, PlayingSnapshot("https://yayin.example/spor.mp3", "A - B", true, isStream = true)))
        // and another playlist's entry is not this one's
        assertNull(RadioNowPlaying.of(playlist, PlayingSnapshot("http://cdn7.example:8000/spor", null, true, entry = "https://yayin.example/spor.pls", isStream = true)))
    }

    @Test
    fun aStreamThatIsNoStreamIsNotTheStation() {
        assertNull(RadioNowPlaying.of(stream, PlayingSnapshot(stream.url, null, true, isStream = false)))
    }

    @Test
    fun aStreamWithoutATitleShowsTheStationAsLive() {
        val now = RadioNowPlaying.of(stream, PlayingSnapshot(stream.url, null, true, isStream = true))!!
        assertEquals("Gece Cazı", now.display.title)
        assertFalse(now.display.hasTrack)
        assertTrue(now.playing)
    }

    @Test
    fun theTitleTheStreamSendsIsSplitIntoArtistAndSong() {
        val now = RadioNowPlaying.of(stream, PlayingSnapshot(stream.url, "Miles Davis - So What", false, isStream = true))!!
        assertEquals("So What", now.display.title)
        assertEquals("Miles Davis", now.display.artist)
        assertEquals("Gece Cazı", now.display.album)
        assertTrue(now.display.hasTrack)
        assertFalse(now.playing)
    }

    @Test
    fun aNewTitleReplacesTheOld() {
        val first = RadioNowPlaying.of(stream, PlayingSnapshot(stream.url, "A - B", true, isStream = true))!!
        val second = RadioNowPlaying.of(stream, PlayingSnapshot(stream.url, "C - D", true, isStream = true))!!
        assertEquals("B", first.display.title)
        assertEquals("D", second.display.title)
    }
}
