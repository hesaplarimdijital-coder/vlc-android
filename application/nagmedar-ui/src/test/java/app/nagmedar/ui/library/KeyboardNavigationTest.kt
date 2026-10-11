package app.nagmedar.ui.library

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.MultiSelection
import app.nagmedar.ui.components.TrackClickModifiers
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * S-407: from the keyboard Space selects a film and Enter plays it, in the table and the grid; the
 * arrows move through the grid's rows and columns.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class KeyboardNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val selection = MultiSelection()
    private val played = mutableListOf<String>()
    private lateinit var inputModes: InputModeManager

    private fun render(grid: Boolean) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                inputModes = LocalInputModeManager.current
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
        compose.waitForIdle()
        // a hardware key takes the window out of touch mode; injected keys do not
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
    }

    private fun item(title: String) = compose.onNode(hasText(title) and hasClickAction())

    private fun key(title: String) = MovieSamples.items.first { it.title == title }.key

    private fun spaceSelectsEnterPlays() {
        item("Demir Vadi").requestFocus()
        item("Demir Vadi").assertIsFocused()
        item("Demir Vadi").performKeyInput { pressKey(Key.Spacebar) }
        compose.waitForIdle()
        assertEquals(setOf(key("Demir Vadi")), selection.keys)
        assertTrue(played.isEmpty())
        item("Demir Vadi").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(listOf("Demir Vadi"), played)
    }

    @Test
    fun spaceSelectsEnterPlaysInTheTable() {
        render(grid = false)
        spaceSelectsEnterPlays()
    }

    @Test
    fun spaceSelectsEnterPlaysInTheGrid() {
        render(grid = true)
        spaceSelectsEnterPlays()
    }

    @Test
    fun theArrowsMoveThroughTheGrid() {
        render(grid = true)
        item("Kuzey Işığı").requestFocus()
        item("Kuzey Işığı").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        item("Sessiz Liman").assertIsFocused()
        // Down goes to the card under the first one, however many columns the window has room for
        val tops = MovieSamples.items.map { item(it.title).fetchSemanticsNode().boundsInRoot.top }
        val columns = tops.count { it == tops.first() }
        val below = MovieSamples.items.getOrNull(columns) ?: return
        item("Sessiz Liman").performKeyInput { pressKey(Key.DirectionLeft) }
        item("Kuzey Işığı").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        item(below.title).assertIsFocused()
    }
}
