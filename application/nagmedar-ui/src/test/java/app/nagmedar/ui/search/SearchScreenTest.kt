package app.nagmedar.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * S-311: tracks found the Turkish way; network shares only when asked; nothing found said so.
 * 1.0.7: the artists and the albums are found too (an album by its artist as well) and open.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h1200dp-mdpi-keysexposed-qwerty")
class SearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val played = mutableListOf<String>()
    private val opened = mutableListOf<GroupItem>()
    private var query by mutableStateOf("")

    private fun page() {
        val data = SearchSamples.data
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                SearchContent(
                    data = data,
                    query = query,
                    onQueryChange = { query = it },
                    onPlayTrack = { track, _ -> played += "track:${track.title}" },
                    onOpenGroup = { opened += it },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun scrollTo(text: String) = compose.onNodeWithTag("search").performScrollToNode(hasText(text, substring = true))

    @Test
    fun withoutAQueryTheSyntaxIsShown() {
        page()
        compose.onNodeWithText("Arama sözdizimi").assertExists()
        compose.onNodeWithText("Parça, sanatçı ya da albüm ara").assertExists()
    }

    @Test
    fun tracksAreFoundTheTurkishWay() {
        page()
        query = "kuzey"
        compose.waitForIdle()
        compose.onNodeWithText("“kuzey” için", substring = true).assertExists()
        compose.onNodeWithText("PARÇALAR · 1").assertExists()
        // no artist and no album is called so
        compose.onNodeWithText("SANATÇILAR", substring = true).assertDoesNotExist()
        compose.onNodeWithText("ALBÜMLER", substring = true).assertDoesNotExist()
        compose.onNode(hasText("Kuzeyden Esen", substring = true) and hasClickAction()).performClick()
        assertEquals(listOf("track:Kuzeyden Esen"), played)
        // "isik" finds "Işık"
        query = "isik"
        compose.waitForIdle()
        assertTrue(shown("Işıklar") >= 1)
    }

    /** 1.0.7: the artist by the name, the album by its artist's, then the artist's tracks; each of the two opens. */
    @Test
    fun artistsAndAlbumsAreFoundAndOpen() {
        page()
        query = "ada"
        compose.waitForIdle()
        compose.onNodeWithText("“ada” için 4 sonuç", substring = true).assertExists()
        compose.onNodeWithText("SANATÇILAR · 1").assertExists()
        compose.onNodeWithText("ALBÜMLER · 1").assertExists()
        compose.onNodeWithText("PARÇALAR · 2").assertExists()
        // "1 albüm · 2 parça" under the artist, "Ada Karan · 2 parça" under the album
        compose.onNode(hasText("1 albüm · 2 parça") and hasClickAction()).performClick()
        compose.onNode(hasText("Ada Karan · 2 parça") and hasClickAction()).performClick()
        assertEquals(listOf(MusicView.Artists to "Ada Karan", MusicView.Albums to "Gece Mavisi"), opened.map { it.view to it.title })
        assertTrue(played.isEmpty())
        // the album by its own name
        query = "liman"
        compose.waitForIdle()
        compose.onNodeWithText("ALBÜMLER · 1").assertExists()
        compose.onNodeWithText("SANATÇILAR", substring = true).assertDoesNotExist()
    }

    /** 1.0.7: a library read again that has not changed equals the one that is kept, so it stays; a changed one does not. */
    @Test
    fun aLibraryReadAgainIsTheOneKeptUntilItChanges() {
        val kept = SearchSamples.data
        fun again(tracks: List<TrackItem> = kept.tracks.map { it.copy() }, groups: List<GroupItem> = kept.groups.map { it.copy() }) = SearchData(tracks, groups)
        assertEquals(kept, again())
        assertEquals(kept.hashCode(), again().hashCode())
        assertNotEquals(kept, again(tracks = kept.tracks.dropLast(1)))
        assertNotEquals(kept, again(tracks = kept.tracks.map { if (it.title == "Fener") it.copy(title = "Fener (Canlı)") else it }))
        assertNotEquals(kept, again(groups = kept.groups.map { if (it.view == MusicView.Artists) it.copy(tracks = it.tracks + 1) else it }))
    }

    /** 1.0.7: the palette's media items are made once of a read, in a language: of that very read, not of one that equals it. */
    @Test
    fun thePalettesMediaAreMadeOnceOfAReadInALanguage() {
        val read = SearchSamples.data
        val turkish = Locale.forLanguageTag("tr")
        val media = PaletteMedia(read, turkish, emptyList())
        assertTrue(media.madeOf(read, turkish))
        assertFalse(media.madeOf(read, Locale.ENGLISH))
        assertFalse(media.madeOf(SearchData(read.tracks, read.groups), turkish))
    }

    @Test
    fun networkSharesOnlyWhenAsked() {
        page()
        query = "fener"
        compose.waitForIdle()
        // the track on the device is found, the copy on the NAS is counted
        scrollTo("Ağda 1 dosya daha")
        compose.onNodeWithText("Ağda 1 dosya daha").assertExists()
        compose.onNode(hasText("Ağdakileri de göster") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ağda 1 dosya daha").assertDoesNotExist()
        assertTrue(shown("Fener (NAS)") >= 1)
    }

    /** S-1501: in English the syntax's examples are English, and the groups' titles English capitals. */
    @Test @Config(qualifiers = "en-w1280dp-h1200dp-mdpi-keysexposed-qwerty")
    fun inEnglishTheSyntaxAndTheTitlesAreEnglish() {
        page()
        compose.onNodeWithText("\"night blue\"").assertExists()
        compose.onNodeWithText("-live").assertExists()
        query = "type:music kuzey"
        compose.waitForIdle()
        assertEquals(1, shown("TRACKS ·"))
    }

    @Test
    fun nothingFoundSaysSo() {
        page()
        query = "zzzz"
        compose.waitForIdle()
        compose.onNodeWithText("“zzzz” için sonuç yok").assertExists()
    }
}

/** Four tracks, one of them on a NAS; 1.0.7: their two artists and two albums. */
object SearchSamples {
    val data: SearchData by lazy {
        SearchData(
            tracks = listOf(
                TrackItem("file:///Music/Ada Karan/Sabaha Kadar.flac", "Sabaha Kadar", "Ada Karan", "Gece Mavisi", 258_000),
                TrackItem("file:///Music/Ada Karan/Kuzeyden Esen.flac", "Kuzeyden Esen", "Ada Karan", "Gece Mavisi", 231_000),
                TrackItem("file:///Music/Deniz Aral/Fener.flac", "Fener", "Deniz Aral", "Liman Işıkları", 302_000),
                TrackItem("smb://nas/Muzik/Deniz Aral/Fener (NAS).flac", "Fener (NAS)", "Deniz Aral", "Liman Işıkları", 302_000),
            ),
            groups = listOf(
                GroupItem("artist:ada karan", MusicView.Artists, "ada karan", "Ada Karan", albums = 1, tracks = 2),
                GroupItem("artist:deniz aral", MusicView.Artists, "deniz aral", "Deniz Aral", albums = 1, tracks = 2),
                GroupItem("album:ada karan/gece mavisi", MusicView.Albums, "album:ada karan\u0000gece mavisi", "Gece Mavisi", artist = "Ada Karan", tracks = 2),
                GroupItem("album:deniz aral/liman isiklari", MusicView.Albums, "album:deniz aral\u0000liman ışıkları", "Liman Işıkları", artist = "Deniz Aral", tracks = 2),
            ),
        )
    }
}
