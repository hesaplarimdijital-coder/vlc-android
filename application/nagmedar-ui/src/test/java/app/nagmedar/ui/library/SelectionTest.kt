package app.nagmedar.ui.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.LocalScreenCommands
import app.nagmedar.ui.components.MultiSelection
import app.nagmedar.ui.components.ScreenCommands
import app.nagmedar.ui.components.TrackClickModifiers
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
 * S-405: Ctrl+click and Shift+click select several films in the grid and the table, Ctrl+A takes
 * them all and Esc lets go, a rubber band from the space between the cards takes what it touches.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class SelectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val selection = MultiSelection()
    private val commands = ScreenCommands()
    private val played = mutableListOf<String>()

    private fun render(grid: Boolean) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalScreenCommands provides commands) {
                    TrackClickModifiers {
                        MoviesContent(
                            items = MovieSamples.items,
                            tech = MovieSamples.tech,
                            grid = grid,
                            sort = null,
                            onSortChange = {},
                            selected = null,
                            onSelect = {},
                            onShowDetails = {},
                            onPlay = { played += it.title },
                            menu = { _, _, _ -> emptyList() },
                            selection = selection,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun key(title: String) = MovieSamples.items.first { it.title == title }.key

    private fun item(title: String) = compose.onNode(hasText(title) and hasClickAction())

    private fun clickOn(title: String, vararg held: Key) {
        item(title).performMultiModalInput {
            key { held.forEach { keyDown(it) } }
            mouse { click(center) }
            key { held.forEach { keyUp(it) } }
        }
        compose.waitForIdle()
    }

    private fun checkSeveral() {
        clickOn("Kuzey Işığı")
        assertEquals(setOf(key("Kuzey Işığı")), selection.keys)
        clickOn("Demir Vadi", Key.CtrlLeft)
        assertEquals(setOf(key("Kuzey Işığı"), key("Demir Vadi")), selection.keys)
        // Shift: from the last item clicked, in the order shown
        clickOn("Kırık Pusula", Key.ShiftLeft)
        assertEquals(setOf(key("Demir Vadi"), key("Mavi Kanal"), key("Kırık Pusula")), selection.keys)
        item("Mavi Kanal").assertIsSelected()
        item("Kuzey Işığı").assertIsNotSelected()
        // Ctrl on a selected one takes it out
        clickOn("Mavi Kanal", Key.CtrlLeft)
        assertEquals(setOf(key("Demir Vadi"), key("Kırık Pusula")), selection.keys)
        assertTrue(played.isEmpty())
    }

    @Test
    fun ctrlAndShiftSelectSeveralCards() {
        render(grid = true)
        checkSeveral()
    }

    @Test
    fun ctrlAndShiftSelectSeveralRows() {
        render(grid = false)
        checkSeveral()
    }

    @Test
    fun ctrlATakesAllAndEscLetsGo() {
        render(grid = false)
        compose.runOnIdle { assertTrue(commands.selectAll()) }
        assertEquals(MovieSamples.items.map { it.key }.toSet(), selection.keys)
        compose.runOnIdle { assertTrue(commands.escape()) }
        assertTrue(selection.keys.isEmpty())
        // nothing to let go of: Esc is for something else
        compose.runOnIdle { assertFalse(commands.escape()) }
    }

    @Test
    fun aBandFromBetweenTheCardsTakesWhatItTouches() {
        render(grid = true)
        val first = item("Kuzey Işığı").fetchSemanticsNode().boundsInRoot
        val second = item("Sessiz Liman").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performMouseInput {
            // from the space above the first card to the middle of the second
            moveTo(Offset(first.left + 2f, first.top - 4f))
            press()
            moveTo(Offset(first.center.x, first.center.y))
            moveTo(second.center)
            release()
        }
        compose.waitForIdle()
        assertEquals(setOf(key("Kuzey Işığı"), key("Sessiz Liman")), selection.keys)
        assertTrue(played.isEmpty())
        // a click between the cards lets go of them
        compose.onRoot().performMouseInput { click(Offset(first.left + 2f, first.top - 4f)) }
        compose.waitForIdle()
        assertTrue(selection.keys.isEmpty())
    }
}
