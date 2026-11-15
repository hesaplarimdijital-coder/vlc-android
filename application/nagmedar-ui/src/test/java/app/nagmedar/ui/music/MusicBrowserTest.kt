package app.nagmedar.ui.music

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.LocalScreenCommands
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.ScreenCommands
import app.nagmedar.ui.shell.LocalPaneWidths
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.PaneWidths
import app.nagmedar.ui.shell.WORKSTATION_PANE
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * S-803, S-1001: the ways through the music library, its filter, its table's columns; Desktop Kit
 * 1.0: the inspector following the selection, its tabs and its keeping, Shift+arrows and the
 * selection's menu.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w420dp-h900dp-mdpi")
class MusicBrowserTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val played = mutableListOf<Pair<List<String>, Int>>()
    private val queued = mutableListOf<List<String>>()

    private val actions = object : MusicActions {
        override fun play(tracks: List<Track>, index: Int) {
            played += tracks.map { it.title } to index
        }

        override fun queue(tracks: List<Track>) {
            queued += tracks.map { it.title }
        }

        override fun menu(track: Track, play: () -> Unit, select: (() -> Unit)?): List<MenuEntry> = emptyList()
    }

    private val library = MusicLibrary(MusicSamples.tracks, Locale.forLanguageTag("tr"))

    private fun show(width: WidthClass, input: InputMode, state: MusicBrowserState = MusicBrowserState(), shown: MusicLibrary = library): MusicBrowserState {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                CompositionLocalProvider(LocalWidthClass provides width) {
                    MusicBrowser(shown, state, actions, Modifier)
                }
            }
        }
        compose.waitForIdle()
        return state
    }

    /**
     * 1.1.0: the genres' covers with a genre that starts with a digit (first, under "#") and tracks without one (last,
     * under "Türü yok"), all on one screen: each heading its own key in the grid (two "#"s with one key ended the grid).
     */
    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
    fun theGridTakesADigitFirstAndTheNamelessTogether() {
        val tracks = listOf(
            Track(1, "file:///m/1.flac", "Bir", "Ada", null, "Seksenler", "80s", 1985, 1, 1, 200_000, "/m"),
            Track(2, "file:///m/2.flac", "İki", "Ada", null, "Caz", "Caz", 1990, 1, 1, 200_000, "/m"),
            Track(3, "file:///m/3.flac", "Üç", "Ada", null, "Demo", null, 2000, 1, 1, 200_000, "/m"),
        )
        show(WidthClass.Large, InputMode.Pointer, MusicBrowserState(view = MusicView.Genres), MusicLibrary(tracks, Locale.forLanguageTag("tr")))
        compose.onNodeWithTag("music-grid").assertExists()
        compose.onNodeWithText("80s").assertExists()
        compose.onNodeWithText("Türü yok").assertExists()
        // the headings, as TalkBack hears them
        compose.onNode(hasContentDescription("#, 1 tür")).assertExists()
        compose.onNode(hasContentDescription("C, 1 tür")).assertExists()
        compose.onNode(hasContentDescription("Türü yok, 1 tür")).assertExists()
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
        // the albums (1.1.0: their covers, under their artists' letters), then one album's tracks
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Itrî'nin İzinde"))
        compose.onNodeWithText("Itrî'nin İzinde").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Neva Kâr").assertExists()
        compose.onNode(hasContentDescription("Listeye dön")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Neva Kâr").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Gece Yolcusu"))
        compose.onNodeWithText("Gece Yolcusu").assertExists()
    }

    @Test
    fun theAlbumsAsCoversAndAsRows() {
        val state = show(WidthClass.Compact, InputMode.Touch, MusicBrowserState(view = MusicView.Albums))
        // 1.1.0 (Ebru): the covers first
        compose.onNodeWithTag("music-grid").performScrollToNode(hasText("Itrî'nin İzinde"))
        compose.onNodeWithText("Itrî'nin İzinde").assertExists()
        compose.onNode(hasContentDescription("Liste görünümü")).performClick()
        compose.waitForIdle()
        assertEquals(MusicLayout.List, state.layout)
        compose.onNodeWithTag("music-grid").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Itrî'nin İzinde"))
        compose.onNodeWithText("Itrî'nin İzinde").assertExists()
        compose.onNode(hasContentDescription("Izgara görünümü")).performClick()
        compose.waitForIdle()
        assertEquals(MusicLayout.Grid, state.layout)
    }

    @Test
    fun theQuickFiltersNarrowTheAlbums() {
        val state = show(WidthClass.Compact, InputMode.Touch, MusicBrowserState(view = MusicView.Albums))
        // nothing of the samples was played: every album is unheard, none heard in part
        compose.onNodeWithText("Hiç çalınmayanlar").performClick()
        compose.waitForIdle()
        assertEquals(QuickFilter.Unheard, state.quick)
        compose.onNodeWithTag("music-grid").performScrollToNode(hasText("Itrî'nin İzinde"))
        // the chips are the grid's first item
        compose.onNodeWithTag("music-grid").performScrollToIndex(0)
        compose.onNodeWithText("Yarım kalanlar").performClick()
        compose.waitForIdle()
        assertEquals(QuickFilter.Unfinished, state.quick)
        compose.onNodeWithText("Itrî'nin İzinde").assertDoesNotExist()
        compose.onNodeWithText("Filtreleri temizle").performClick()
        compose.waitForIdle()
        assertEquals(QuickFilter.All, state.quick)
        compose.onNodeWithTag("music-grid").performScrollToNode(hasText("Itrî'nin İzinde"))
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

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun aDesktopShowsTheGroupsBesideTheTableAndItsColumns() {
        val state = show(WidthClass.Large, InputMode.Pointer, MusicBrowserState(view = MusicView.Genres, layout = MusicLayout.List))
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
        // Desktop Kit 1.0: the order the user put them in; the title among them, first when it was missing
        assertEquals(listOf(MusicColumn.Artist, MusicColumn.Title), MusicColumn.parse("artist,title"))
        assertEquals("nagmedar_music_columns_albums", columnsKey(MusicView.Albums))
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

    // ------------------------------------------------------------ Desktop Kit 1.0: the workstation

    /** Panes kept in a map, as the preferences keep them. */
    private class MapPaneWidths : PaneWidths {
        val widths = mutableMapOf<String, Float>()
        val hidden = mutableMapOf<String, Boolean>()

        override fun paneWidth(key: String): Float? = widths[key]

        override fun savePaneWidth(key: String, widthDp: Float) {
            widths[key] = widthDp
        }

        override fun paneCollapsed(key: String): Boolean = hidden[key] ?: false

        override fun savePaneCollapsed(key: String, collapsed: Boolean) {
            hidden[key] = collapsed
        }
    }

    /** Each tab says what it is about: "etiketler Neva Kâr 1", "etiketler çalan 0". */
    private val tabs = MusicInspectorContent(
        tags = { subject -> Text("etiketler ${subject.track?.title ?: "çalan"} ${subject.selected}") },
        signal = { _ -> Text("sinyal") },
        lyrics = { _ -> Text("sözler") },
        queue = { Text("sıra") },
    )

    private lateinit var inputModes: InputModeManager

    /** The window's commands: Ctrl+I goes to the music view through them. */
    private val commands = ScreenCommands()

    private fun workstation(store: PaneWidths) {
        val itri = library.groups(MusicView.Albums).first { it.title == "Itrî'nin İzinde" }.key
        val state = MusicBrowserState(view = MusicView.Albums, group = itri, columns = listOf(MusicColumn.Title, MusicColumn.Duration))
        compose.setContent {
            inputModes = LocalInputModeManager.current
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalWidthClass provides WidthClass.Large, LocalPaneWidths provides store, LocalScreenCommands provides commands) {
                    MusicBrowser(library, state, actions, Modifier, inspector = tabs)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun row(title: String) = compose.onNode(hasText(title) and hasClickAction())

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun theInspectorFollowsTheSelectionAndItsTabs() {
        val store = MapPaneWidths()
        workstation(store)
        // nothing selected: what plays
        compose.onNodeWithText("etiketler çalan 0").assertExists()
        row("Neva Kâr").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("etiketler Neva Kâr 1").assertExists()
        // a tab clicked, the next one with the arrow key
        compose.onNodeWithTag("inspector-tab-lyrics").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("sözler").assertExists()
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
        compose.onNodeWithTag("inspector-tab-lyrics").requestFocus()
        compose.onNodeWithTag("inspector-tab-lyrics").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        compose.onNodeWithText("sıra").assertExists()
        // Ctrl+I hides it, and it is kept so
        assertEquals(true, compose.runOnIdle { commands.toggleInspector() })
        compose.waitForIdle()
        compose.onNodeWithTag("inspector").assertDoesNotExist()
        assertEquals(true, store.hidden[WORKSTATION_PANE])
    }

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun aHiddenInspectorStaysHidden() {
        workstation(MapPaneWidths().apply { hidden[WORKSTATION_PANE] = true })
        compose.onNodeWithTag("inspector").assertDoesNotExist()
        compose.onNodeWithText("etiketler çalan 0").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun theInspectorComesBackAsWide() {
        workstation(MapPaneWidths().apply { widths[WORKSTATION_PANE] = 420f })
        compose.onNodeWithTag("inspector").assertWidthIsEqualTo(420.dp)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun shiftArrowsGrowTheSelectionAndItsMenuTakesThemAll() {
        workstation(MapPaneWidths())
        row("Segâh Tekbîr").performClick()
        compose.waitForIdle()
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
        row("Segâh Tekbîr").requestFocus()
        row("Segâh Tekbîr").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        compose.waitForIdle()
        // the anchor's track, one of the two selected
        compose.onNodeWithText("etiketler Segâh Tekbîr 2").assertExists()
        // a right click on a selected row: the menu is the selection's
        row("Neva Kâr").performMouseInput { rightClick(center) }
        compose.waitForIdle()
        // the selection bar's button and the menu's item
        val queue = compose.onAllNodesWithText("Sıraya ekle")
        queue.assertCountEquals(2)
        queue[1].performClick()
        compose.waitForIdle()
        assertEquals(listOf(listOf("Segâh Tekbîr", "Neva Kâr")), queued)
    }
}
