package app.nagmedar.ui.music

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.covers.CoverTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * 1.1.3: the band is made before the eyes only when the music changes. A page shown again (another tab) has the playing
 * album's inks from its first frame, as they were worked out once; an album whose cover has not been read since the app
 * started is unknown meanwhile, and its band fades in over the bozkır's. 1.1.5: a station's and an episode's too (their
 * logo, their picture), and what has no picture has the bozkır's in a drawing of its own.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EbruInksTest {

    @get:Rule
    val compose = createComposeRule()

    private val night = EbruInks(listOf(0xFF1F2A5A.toInt(), 0xFFE7A33E.toInt(), 0xFF2E9C9A.toInt()), 0xFFEFE3CC.toInt(), 101L)
    private val dawn = EbruInks(listOf(0xFFB4412F.toInt(), 0xFFF2C14E.toInt(), 0xFF5C3A2E.toInt()), 0xFFF4EBDD.toInt(), 202L)

    private fun track(id: Long, album: String): MediaWrapper {
        val path = "/storage/emulated/0/Music/$album/$id.flac"
        return MLServiceLocator.getAbstractMediaWrapper(
            id, Uri.fromFile(java.io.File(path)).toString(), 0L, 0f, 241_000L, MediaWrapper.TYPE_AUDIO, "Parça $id", "$id.flac",
            7L, 7L, "Cemre Kaya", "Caz", 11L, album, "Cemre Kaya", 0, 0, null, -2, -2, 3, 1, 1_760_000_000L, 0L, false, false, 2026, true,
            1_760_000_100L,
        )
    }

    @Test
    fun onlyAMusicChangeMakesTheBandBeforeTheEyes() {
        // another album
        assertTrue(marblesBetween(night, dawn))
        // to the bozkır's (an album without a cover) and back
        assertTrue(marblesBetween(night, EbruInks.Default))
        assertTrue(marblesBetween(EbruInks.Default, dawn))
        // the same album (another of its tracks)
        assertFalse(marblesBetween(night, night))
        // the inks of what plays coming to be known: a page shown, the app started
        assertFalse(marblesBetween(EbruInks.Unknown, night))
        assertFalse(marblesBetween(EbruInks.Unknown, EbruInks.Default))
        assertFalse(marblesBetween(night, EbruInks.Unknown))
        // the stand-in is drawn as the bozkır's
        assertEquals(EbruInks.Default, EbruInks.Unknown.drawn)
    }

    @Test
    fun aPageShownAgainHasItsAlbumsInksFromItsFirstFrame() {
        val playing = track(1, "Kıyı")
        Ebru.keepInks(CoverTrack.of(playing)!!.key, night)
        val seen = mutableListOf<EbruInks>()
        compose.setContent { seen += rememberEbruInks(playing) }
        compose.waitForIdle()
        assertEquals(night, seen.first())
        assertTrue(seen.all { it == night })
    }

    @Test
    fun anAlbumNotReadYetIsUnknownThenTheBozkirsWithoutACover() {
        val playing = track(2, "Hiç Kapaksız")
        val key = CoverTrack.of(playing)!!.key
        val seen = mutableListOf<EbruInks>()
        compose.mainClock.autoAdvance = false
        compose.setContent { seen += rememberEbruInks(playing) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(EbruInks.Unknown, seen.first())
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // 1.1.5: the bozkır's in a drawing of the album's own
        assertEquals(EbruInks.bozkirOf(key), seen.last())
        assertEquals(EbruInks.Default.inks, seen.last().inks)
        assertTrue(marblesBetween(EbruInks.Default, seen.last()))
        // known from now on: the next page has them at once
        assertEquals(EbruInks.bozkirOf(key), Ebru.keptInks(key))
    }

    private fun station(address: String): MediaWrapper = MLServiceLocator.getAbstractMediaWrapper(Uri.parse(address)).apply {
        type = MediaWrapper.TYPE_STREAM
        title = "İstasyon"
    }

    /** 1.1.5: Radio's band changes with the station: one without a logo has a band of its own too. */
    @Test
    fun aStationWithoutALogoHasABandOfItsOwn() {
        var playing by mutableStateOf(station("https://radyo.example/gece"))
        val seen = mutableListOf<EbruInks>()
        compose.setContent { seen += rememberEbruInks(playing) }
        compose.waitForIdle()
        val first = seen.last()
        assertEquals(EbruInks.bozkirOf("stream|https://radyo.example/gece"), first)
        assertEquals(first, Ebru.keptInks("stream|https://radyo.example/gece"))
        // another station: another band, made before the eyes
        compose.runOnIdle { playing = station("https://radyo.example/sabah") }
        compose.waitForIdle()
        assertEquals(EbruInks.bozkirOf("stream|https://radyo.example/sabah"), seen.last())
        assertTrue(marblesBetween(first, seen.last()))
    }

    /** 1.1.5: an episode's inks are its picture's (its show's), kept under the picture: its show's other episodes share them. */
    @Test
    fun anEpisodesInksAreKeptUnderItsPicture() {
        val episode = MLServiceLocator.getAbstractMediaWrapper(Uri.parse("file:///storage/emulated/0/Podcasts/bolum-12.mp3")).apply {
            type = MediaWrapper.TYPE_AUDIO
            setArtist("Tarih Sohbetleri")
            setArtworkURL("file:///storage/emulated/0/Podcasts/tarih.png")
        }
        val seen = mutableListOf<EbruInks>()
        compose.setContent { seen += rememberEbruInks(episode) }
        // read off the main thread
        compose.waitUntil(5_000) { seen.last() != EbruInks.Unknown }
        // the picture does not read here: the bozkır's in the show's own drawing
        assertEquals(EbruInks.bozkirOf("art|file:///storage/emulated/0/Podcasts/tarih.png"), seen.last())
        assertEquals(seen.last(), Ebru.keptInks("art|file:///storage/emulated/0/Podcasts/tarih.png"))
    }

    @Test
    fun aVideoLeavesTheBozkirs() {
        val video = MLServiceLocator.getAbstractMediaWrapper(Uri.parse("file:///storage/emulated/0/Movies/yol.mp4")).apply { type = MediaWrapper.TYPE_VIDEO }
        val seen = mutableListOf<EbruInks>()
        compose.setContent { seen += rememberEbruInks(video) }
        compose.waitForIdle()
        assertTrue(seen.all { it == EbruInks.Default })
    }

    @Test
    fun nothingPlayingIsTheBozkirsAtOnce() {
        val seen = mutableListOf<EbruInks>()
        compose.setContent { seen += rememberEbruInks(null) }
        compose.waitForIdle()
        assertTrue(seen.all { it == EbruInks.Default })
    }
}
