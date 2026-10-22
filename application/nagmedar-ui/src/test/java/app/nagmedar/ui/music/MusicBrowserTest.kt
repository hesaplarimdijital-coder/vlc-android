package app.nagmedar.ui.music

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/** S-803, S-1001: the ways through the music library, its filter, its table's columns. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w420dp-h900dp-mdpi")
class MusicBrowserTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val played = mutableListOf<Pair<List<String>, Int>>()

    private val actions = object : MusicActions {
        override fun play(tracks: List<Track>, index: Int) {
            played += tracks.map { it.title } to index
        }

        override fun queue(tracks: List<Track>) = Unit

        override fun menu(track: Track, play: () -> Unit, select: (() -> Unit)?): List<MenuEntry> = emptyList()
    }

    private val library = MusicLibrary(MusicSamples.tracks, Locale.forLanguageTag("tr"))

    private fun show(width: WidthClass, input: InputMode, state: MusicBrowserState = MusicBrowserState()): MusicBrowserState {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                CompositionLocalProvider(LocalWidthClass provides width) {
                    MusicBrowser(library, state, actions, Modifier)
                }
            }
        }
        compose.waitForIdle()
        return state
    }

    @Test
    fun aPhoneOpensAnAlbumAndComesBack() {
        val state = show(WidthClass.Compact, InputMode.Touch)
        // a phone chooses the way in from a menu
        compose.onNodeWithTag("music-views").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Albümler").performClick()
        compose.waitForIdle()
        assertEquals(MusicView.Albums, state.view)
        // the list of albums, then one album's tracks
        compose.onNodeWithText("Itrî'nin İzinde").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Neva Kâr").assertExists()
        compose.onNode(hasContentDescription("Listeye dön")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Neva Kâr").assertDoesNotExist()
        compose.onNodeWithText("Gece Yolcusu").assertExists()
    }

    @Test
    fun aTapPlaysTheAlbumFromThatTrack() {
        val state = show(WidthClass.Compact, InputMode.Touch, MusicBrowserState(view = MusicView.Albums, group = library.groups(MusicView.Albums).first { it.title == "Itrî'nin İzinde" }.key))
        compose.onNodeWithText("Neva Kâr").performClick()
        compose.waitForIdle()
        assertEquals(listOf(listOf("Segâh Tekbîr", "Neva Kâr", "Nihâvend Saz Semâîsi") to 1), played)
        assertEquals(MusicView.Albums, state.view)
    }

    @Test
    fun theDepthFilter() {
        val state = show(WidthClass.Compact, InputMode.Touch)
        compose.onNode(hasContentDescription("Filtrele")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("24 bit").performClick()
        compose.waitForIdle()
        assertEquals(setOf(Depth.Bits24), state.filter.depths)
        compose.onNodeWithText("Neva Kâr").assertExists()
        compose.onNodeWithText("Yağmur").assertDoesNotExist()
        compose.onNodeWithText("Filtreleri temizle").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Yağmur").assertExists()
    }

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
    fun aDesktopShowsTheGroupsBesideTheTableAndItsColumns() {
        val state = show(WidthClass.Large, InputMode.Pointer, MusicBrowserState(view = MusicView.Genres))
        // the first genre is open beside the list: its name in the list and over the table
        assertEquals(2, compose.onAllNodesWithText("Caz").fetchSemanticsNodes().size)
        compose.onNodeWithText("Ada").assertExists()
        // the columns: the composer added from the menu
        compose.onNode(hasText("Sütunlar") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Besteci").performClick()
        compose.waitForIdle()
        assertTrue(MusicColumn.Composer in state.columns)
        assertEquals(MusicColumn.Title, state.columns.first())
    }

    @Test
    fun columnsAreKeptAsText() {
        assertEquals(MusicColumn.DEFAULT, MusicColumn.parse(null))
        assertEquals(listOf(MusicColumn.Title, MusicColumn.Rate, MusicColumn.Depth), MusicColumn.parse("rate,depth,nope"))
        assertEquals(listOf(MusicColumn.Title, MusicColumn.Artist), MusicColumn.parse("artist,title"))
        assertEquals("title,artist,format", MusicColumn.format(listOf(MusicColumn.Title, MusicColumn.Artist, MusicColumn.Format)))
    }

    @Test
    fun formatsAndSorts() {
        val tracks = MusicSamples.tracks
        assertEquals(listOf("FLAC 24/96", "FLAC 16/44.1", "MP3 320 kb/s", "ALAC 24/192", "DSD64", "Opus 160 kb/s"),
            listOf(0, 3, 5, 6, 8, 9).map { formatLabel(tracks[it].tech!!) })
        // S-911: a tracker module by its kind, as the engine names it
        assertEquals("IT", formatLabel(TrackTech("tracker", "it", 48000, 0, 2, true)))
        assertEquals("Tracker", formatLabel(TrackTech("tracker", "", 48000, 0, 2, true)))
        val tr = Locale.forLanguageTag("tr")
        // by depth and rate, the unprobed last both ways
        val up = sortTracks(tracks, MusicSort(MusicColumn.Rate, true), tr).map { it.title }
        assertEquals("İsimsiz", up.last())
        val down = sortTracks(tracks, MusicSort(MusicColumn.Rate, false), tr).map { it.title }
        assertEquals("Ada", down.first())
        assertEquals("İsimsiz", down.last())
        // Turkish order: "Çağla" after "Cemre"
        val byArtist = sortTracks(tracks, MusicSort(MusicColumn.Artist, true), tr).map { it.artist }.distinct()
        assertEquals(listOf("Cemre Kaya", "Çağla Akın", "Deniz Su", "Ece Duru", "Kadri Erdem"), byArtist)
    }
}
