package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.InputMode as ComposeInputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * 1.0.3, the user's DeX findings: what a layer over the whole window covers takes no keyboard. A
 * hardware keyboard's Tab does not go into the page hidden behind the open player, the focus that
 * was in it leaves it, and Ctrl+A (the screen's select all) does not reach the list under it.
 * Uncovered again, Tab goes through the page as before.
 */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
class CoveredFocusTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var covered by mutableStateOf(false)
    private val commands = ScreenCommands()
    private lateinit var focus: FocusManager
    private lateinit var inputModes: InputModeManager
    private var selectedAll = 0

    /** A page of three focusable rows under [coveredByLayer], a player's two buttons beside it. */
    private fun window() {
        commands.selectAllHandler = {
            selectedAll++
            true
        }
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            focus = LocalFocusManager.current
            inputModes = LocalInputModeManager.current
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalScreenCommands provides commands) {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxWidth().coveredByLayer(covered)) {
                            (1..3).forEach { Text("Satır $it", Modifier.height(40.dp).testTag("row-$it").focusable()) }
                        }
                        Box(Modifier.fillMaxWidth().height(40.dp).testTag("player-1").focusable())
                        Box(Modifier.fillMaxWidth().height(40.dp).testTag("player-2").focusable())
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
        compose.waitForIdle()
    }

    private fun tab(): Boolean {
        val moved = compose.runOnIdle { focus.moveFocus(FocusDirection.Next) }
        compose.waitForIdle()
        return moved
    }

    @Test
    fun tabStaysOutOfTheCoveredPage() {
        window()
        covered = true
        compose.waitForIdle()
        compose.onNodeWithTag("player-1").requestFocus()
        compose.onNodeWithTag("player-1").assertIsFocused()
        // round and round the player's two buttons, never into the rows under it
        repeat(6) {
            tab()
            (1..3).forEach { compose.onNodeWithTag("row-$it").assertIsNotFocused() }
        }
    }

    @Test
    fun theFocusInThePageLeavesItWhenItIsCovered() {
        window()
        compose.onNodeWithTag("row-2").requestFocus()
        compose.onNodeWithTag("row-2").assertIsFocused()
        covered = true
        compose.waitForIdle()
        compose.onNodeWithTag("row-2").assertIsNotFocused()
    }

    @Test
    fun selectAllDoesNotReachTheCoveredScreen() {
        window()
        assertTrue(compose.runOnIdle { commands.selectAll() })
        covered = true
        compose.waitForIdle()
        assertFalse(compose.runOnIdle { commands.selectAll() })
        covered = false
        compose.waitForIdle()
        assertTrue(compose.runOnIdle { commands.selectAll() })
        assertEquals(2, selectedAll)
    }

    @Test
    fun uncoveredTabGoesThroughThePageAgain() {
        window()
        covered = true
        compose.waitForIdle()
        covered = false
        compose.waitForIdle()
        compose.onNodeWithTag("row-1").requestFocus()
        tab()
        compose.onNodeWithTag("row-2").assertIsFocused()
        tab()
        compose.onNodeWithTag("row-3").assertIsFocused()
        tab()
        compose.onNodeWithTag("player-1").assertIsFocused()
    }
}
