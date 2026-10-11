package app.nagmedar.ui.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MultiSelection
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

/**
 * S-310: by touch a long press opens the film's menu, whose "Seç" starts selecting; taps then
 * take films in and out instead of playing them, the selection bar counts them and plays them,
 * and its close button lets go.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
class TouchSelectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val selection = MultiSelection()
    private val played = mutableListOf<String>()
    private var playedAll = emptyList<String>()

    private fun films() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                MoviesLibrary(
                    library = MovieSamples.library,
                    tech = MovieSamples.tech,
                    view = MoviesView.Grid,
                    filters = emptySet(),
                    onFiltersChange = {},
                    order = MovieOrder.Added,
                    folder = null,
                    onOpenFolder = {},
                    sort = null,
                    onSortChange = {},
                    selected = null,
                    onSelect = {},
                    onShowDetails = {},
                    onPlay = { played += it.title },
                    // as the films screen builds it by touch
                    menu = { item, play, _ -> listOf(MenuEntry.Item("Oynat", play), MenuEntry.Item("Seç", { selection.startSelecting(item.key) })) },
                    selection = selection,
                    onPlayAll = { films -> playedAll = films.map { it.title } },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun card(title: String) = compose.onNode(hasText(title) and hasClickAction())

    private fun key(title: String) = MovieSamples.items.first { it.title == title }.key

    @Test
    fun theMenusSelectStartsSelectingAndTapsTakeFilmsInAndOut() {
        films()
        // a tap plays, as always by touch
        card("Mavi Kanal").performClick()
        assertEquals(listOf("Mavi Kanal"), played)

        card("Demir Vadi").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Seç").performClick()
        compose.waitForIdle()
        assertTrue(selection.selecting)
        assertEquals(setOf<Any>(key("Demir Vadi")), selection.keys)
        compose.onNodeWithText("1 film seçili").assertExists()
        card("Demir Vadi").assertIsSelected()

        card("Mavi Kanal").performClick()
        card("Sessiz Liman").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Mavi Kanal"), played)
        compose.onNodeWithText("3 film seçili").assertExists()
        card("Demir Vadi").performClick()
        compose.waitForIdle()
        assertEquals(setOf<Any>(key("Mavi Kanal"), key("Sessiz Liman")), selection.keys)

        // the bar plays them in the list's order
        compose.onNode(hasText("Oynat") and hasClickAction()).performClick()
        assertEquals(listOf("Sessiz Liman", "Mavi Kanal"), playedAll)

        // its close button lets go: a tap plays again
        compose.onNodeWithContentDescription("Seçimi kaldır").performClick()
        compose.waitForIdle()
        assertFalse(selection.selecting)
        assertTrue(selection.keys.isEmpty())
        card("Demir Vadi").performClick()
        assertEquals(listOf("Mavi Kanal", "Demir Vadi"), played)
    }

    @Test
    fun takingTheLastOneOutEndsSelecting() {
        films()
        card("Demir Vadi").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Seç").performClick()
        compose.waitForIdle()
        card("Demir Vadi").performClick()
        compose.waitForIdle()
        assertFalse(selection.selecting)
        compose.onNodeWithText("1 film seçili").assertDoesNotExist()
    }
}
