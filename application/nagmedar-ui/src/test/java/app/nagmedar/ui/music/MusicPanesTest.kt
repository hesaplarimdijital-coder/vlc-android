package app.nagmedar.ui.music

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.shell.LocalPaneWidths
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.MUSIC_GROUPS_PANE
import app.nagmedar.ui.shell.PaneWidths
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.math.abs

/**
 * 1.0.8 (Desktop Kit 2.8.0's list and detail, the design audit's high finding 1): the music view's groups and an open
 * group's tracks keep their place as the window crosses a width (a window resized on DeX, a tablet or a phone turned);
 * an album open beside the albums stays open, alone, once the window narrows; the divider between them is dragged
 * and kept.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
class MusicPanesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val actions = object : MusicActions {
        override fun play(tracks: List<Track>, index: Int) = Unit

        override fun queue(tracks: List<Track>) = Unit
    }

    /** Sixty albums of forty tracks each: more than a window shows of either. */
    private val library = MusicLibrary(
        (1..60).flatMap { album ->
            (1..40).map { number ->
                val a = "%02d".format(album)
                val n = "%02d".format(number)
                Track(
                    id = album * 100L + number,
                    uri = "file:///storage/emulated/0/Music/Sanatçı/Albüm $a/$n.flac",
                    title = "Parça $n",
                    artist = "Sanatçı",
                    albumArtist = null,
                    album = "Albüm $a",
                    genre = "Türk Sanat Müziği",
                    year = 2000 + album,
                    trackNumber = number,
                    discNumber = 1,
                    durationMs = 180_000,
                    folder = "/storage/emulated/0/Music/Sanatçı/Albüm $a",
                )
            }
        },
        Locale.forLanguageTag("tr"),
    )

    private var width by mutableStateOf(WidthClass.Compact)

    private class Store : PaneWidths {
        val saved = mutableMapOf<String, Float>()

        override fun paneWidth(key: String): Float? = saved[key]

        override fun savePaneWidth(key: String, widthDp: Float) {
            saved[key] = widthDp
        }
    }

    /** An inspector as the app always has one: the music view then lies in the workstation's layout. */
    private val inspector = MusicInspectorContent(
        tags = { _ -> Text("etiketler") },
        signal = { _ -> Text("sinyal") },
        lyrics = { _ -> Text("sözler") },
        queue = { Text("sıra") },
    )

    private fun show(state: MusicBrowserState, store: PaneWidths = Store(), withInspector: Boolean = false) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalWidthClass provides width, LocalPaneWidths provides store) {
                    MusicBrowser(library, state, actions, Modifier, inspector = inspector.takeIf { withInspector })
                }
            }
        }
        compose.waitForIdle()
    }

    /** 1.1.0: the albums' list holds the quick filters over the 60 albums. */
    private val ALBUM_ROWS = 61

    /**
     * The scrolling list of [rows] rows (the albums are 61 with the quick filters, an album's tracks 40); 1.1.1: a phone's
     * has one more, the albums' letter (the design's) or the album's own header over its tracks.
     */
    private fun listWith(rows: Int) = compose.onNode(
        hasScrollToNodeAction() and SemanticsMatcher("$rows rows") { it.config.getOrNull(SemanticsProperties.CollectionInfo)?.rowCount in rows..rows + 1 },
    )

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** Whether [text] is on the screen now: a list's row scrolled away, or a list covered, is not. */
    private fun onScreen(text: String): Boolean {
        val window = compose.onAllNodes(isRoot())[0].fetchSemanticsNode().boundsInRoot
        return compose.onAllNodesWithText(text).fetchSemanticsNodes().any { node ->
            node.layoutInfo.isPlaced && node.boundsInRoot.let { it.width > 0f && it.height > 0f && it.overlaps(window) }
        }
    }

    /** The number of the first of [prefix] 01, 02… on the screen now. */
    private fun topOnScreen(prefix: String, count: Int): Int = (1..count).first { onScreen("$prefix %02d".format(it)) }

    /**
     * The list is where it was: its first row on the screen is the one before, or the next when the rows' heights in
     * the other layout leave only the edge of it (the scroll keeps the first row and how far into it it is).
     */
    private fun assertStillAt(top: Int, now: Int) = assertTrue("the list's first row was $top, is $now", now == top || now == top + 1)

    @Test
    fun theAlbumsKeepTheirPlaceAsTheWindowWidensAndNarrows() {
        show(MusicBrowserState(view = MusicView.Albums, layout = MusicLayout.List))
        listWith(ALBUM_ROWS).performScrollToNode(hasText("Albüm 45"))
        compose.waitForIdle()
        val top = topOnScreen("Albüm", 60)
        assertFalse(onScreen("Albüm 01"))
        // a tablet's width: the albums beside the first one's tracks, where they were (the open album's header says
        // "Albüm 01": the list's own rows are looked at)
        width = WidthClass.Expanded
        compose.waitForIdle()
        assertTrue(onScreen("Albüm %02d".format(top + 1)))
        assertFalse("the albums are not back at their top", onScreen("Albüm 02"))
        assertTrue(onScreen("Parça 01"))
        // and back
        width = WidthClass.Compact
        compose.waitForIdle()
        assertStillAt(top, topOnScreen("Albüm", 60))
    }

    /**
     * As in the app, with the inspector (the independent review of 1.0.8): the music view lies in the workstation's
     * layout, and a large window docks the inspector beside the albums and their tracks.
     */
    @Test
    fun theAlbumsKeepTheirPlaceBesideTheInspector() {
        show(MusicBrowserState(view = MusicView.Albums, layout = MusicLayout.List), withInspector = true)
        listWith(ALBUM_ROWS).performScrollToNode(hasText("Albüm 45"))
        compose.waitForIdle()
        val top = topOnScreen("Albüm", 60)
        width = WidthClass.Large
        compose.waitForIdle()
        assertTrue(onScreen("Albüm %02d".format(top + 1)))
        assertFalse("the albums are not back at their top", onScreen("Albüm 02"))
        assertTrue(onScreen("Parça 01"))
        // the inspector docked beside them
        assertTrue(onScreen("etiketler"))
        compose.onNodeWithText("Albüm %02d".format(top + 2)).performClick()
        compose.waitForIdle()
        listWith(40).performScrollToNode(hasText("Parça 40"))
        compose.waitForIdle()
        val tracksTop = topOnScreen("Parça", 40)
        width = WidthClass.Compact
        compose.waitForIdle()
        assertStillAt(tracksTop, topOnScreen("Parça", 40))
        // 1.1.1: a phone's way back is the page's chip (not part of the browser) and Back
        back()
        assertStillAt(top, topOnScreen("Albüm", 60))
    }

    /** The numbers of the albums whose covers are on the screen now (the grid's). */
    private fun coversOnScreen(): List<Int> = (1..60).filter { onScreen("Albüm %02d".format(it)) }

    /**
     * 1.1.0 (Ebru): the albums' covers, the default, keep their place as the window widens (the inspector docking beside
     * them, a row holding another number of covers) and narrows again: the grid keeps its first cover in sight, the one
     * whose title may be scrolled off above, and starts the row the new number of covers puts it in, so the first title
     * on the screen moves by up to a row of each layout (7 covers to a row here narrow, 4 wide); never back at the top.
     */
    @Test
    fun theCoversKeepTheirPlaceAsTheWindowWidensAndNarrows() {
        show(MusicBrowserState(view = MusicView.Albums), withInspector = true)
        compose.onNodeWithTag("music-grid").performScrollToNode(hasText("Albüm 45"))
        compose.waitForIdle()
        val first = coversOnScreen().min()
        assertTrue(coversOnScreen().toString(), 45 in coversOnScreen() && first > 1)
        width = WidthClass.Large
        compose.waitForIdle()
        val wide = coversOnScreen()
        assertTrue("wide: $wide, first $first", wide.isNotEmpty() && 1 !in wide && abs(wide.min() - first) <= 12)
        assertTrue(onScreen("etiketler"))
        width = WidthClass.Compact
        compose.waitForIdle()
        val narrow = coversOnScreen()
        assertTrue("narrow again: $narrow, first $first", narrow.isNotEmpty() && 1 !in narrow && abs(narrow.min() - first) <= 12)
    }

    @Test
    fun anAlbumOpenBesideTheAlbumsStaysOpenWithItsPlaceOnceTheWindowNarrows() {
        width = WidthClass.Expanded
        val state = MusicBrowserState(view = MusicView.Albums, layout = MusicLayout.List)
        show(state)
        compose.onNodeWithText("Albüm 03").performClick()
        compose.waitForIdle()
        listWith(40).performScrollToNode(hasText("Parça 40"))
        compose.waitForIdle()
        val top = topOnScreen("Parça", 40)
        assertFalse(onScreen("Parça 01"))
        // a phone's width: the album alone, its tracks where they were
        width = WidthClass.Compact
        compose.waitForIdle()
        assertEquals(library.groups(MusicView.Albums).first { it.title == "Albüm 03" }.key, state.group)
        // alone: its tracks, the albums under them out of sight (1.1.1: a phone's way back is the page's chip, and Back)
        compose.onNodeWithTag("music-tracks").assertIsDisplayed()
        assertStillAt(top, topOnScreen("Parça", 40))
        // the medium width shows it alone as well, still where it was
        width = WidthClass.Medium
        compose.waitForIdle()
        assertStillAt(top, topOnScreen("Parça", 40))
    }

    @Test
    fun theDividerBetweenTheAlbumsAndTheirTracksIsKept() {
        width = WidthClass.Expanded
        val store = Store()
        show(MusicBrowserState(view = MusicView.Albums, layout = MusicLayout.List), store)
        val divider = compose.onNodeWithContentDescription("Listenin genişliği")
        // where the music view always had it
        assertTrue(abs(divider.fetchSemanticsNode().boundsInRoot.left - 260f) < 1f)
        divider.performSemanticsAction(SemanticsActions.SetProgress) { it(340f) }
        compose.waitForIdle()
        assertEquals(340f, store.saved[MUSIC_GROUPS_PANE])
        assertTrue(abs(divider.fetchSemanticsNode().boundsInRoot.left - 340f) < 1f)
    }
}
