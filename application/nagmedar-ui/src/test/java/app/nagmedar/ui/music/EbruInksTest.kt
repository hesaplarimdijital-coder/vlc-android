package app.nagmedar.ui.music

import android.net.Uri
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
 * started is unknown meanwhile, and its band fades in over the bozkır's.
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
        val seen = mutableListOf<EbruInks>()
        compose.mainClock.autoAdvance = false
        compose.setContent { seen += rememberEbruInks(playing) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(EbruInks.Unknown, seen.first())
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(EbruInks.Default, seen.last())
        // known from now on: the next page has them at once
        assertEquals(EbruInks.Default, Ebru.keptInks(CoverTrack.of(playing)!!.key))
    }

    @Test
    fun nothingPlayingIsTheBozkirsAtOnce() {
        val seen = mutableListOf<EbruInks>()
        compose.setContent { seen += rememberEbruInks(null) }
        compose.waitForIdle()
        assertTrue(seen.all { it == EbruInks.Default })
    }
}
