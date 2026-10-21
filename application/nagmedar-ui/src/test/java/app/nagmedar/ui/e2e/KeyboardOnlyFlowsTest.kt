package app.nagmedar.ui.e2e

import androidx.activity.ComponentActivity
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.theme.InputMode as NagInputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * S-1403: the main flows of a DeX window with the keyboard alone, through the window's own keys:
 * the command palette; the menu bar from Alt; Esc going back. Nothing is clicked.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
class KeyboardOnlyFlowsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val window = DexWindow()

    @Before
    fun show() {
        ShortcutRegistry.resume()
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = NagInputMode.Pointer) { window.Content() }
        }
        compose.waitForIdle()
        // a hardware keyboard's first key takes the window out of touch mode; injected keys do not
        compose.runOnIdle { window.inputModes.requestInputMode(InputMode.Keyboard) }
        compose.waitForIdle()
    }

    /** Keys to the window on top: a menu or a dialog when one is open. */
    private fun keys(block: KeyInjectionScope.() -> Unit) {
        compose.runOnIdle { dialogsOutOfTouchMode() }
        compose.onAllNodes(isRoot()).onLast().performKeyInput(block)
        compose.waitForIdle()
    }

    /**
     * A hardware key takes the whole screen out of touch mode, so a dialog opened after it has
     * the keyboard from the start (its buttons and chips take the focus); the injected keys do
     * not, so the dialogs leave touch mode here, as the main window did in [show].
     */
    private fun dialogsOutOfTouchMode() {
        ShadowDialog.getShownDialogs().filter { it.isShowing }.forEach { dialog ->
            val view = dialog.currentFocus ?: dialog.window?.decorView ?: return@forEach
            if (view.isInTouchMode) view.requestFocusFromTouch()
        }
    }

    private fun texts(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText)?.text)

    private fun focused(): List<String> = compose.onAllNodes(isFocused()).fetchSemanticsNodes().flatMap(::texts)

    @Test
    fun theCommandPaletteFindsAScreen() {
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
        compose.onNode(hasSetTextAction()).performTextInput("radyo")
        compose.waitForIdle()
        keys { pressKey(Key.Enter) }
        assertEquals(Destination.Radio, window.destination)
    }

    @Test
    fun theMenuBarFromAlt() {
        // Alt alone: the menu bar has the keyboard on its first title
        keys { pressKey(Key.AltLeft) }
        // to Araçlar, the sixth; Down opens it on its first item
        repeat(5) { keys { pressKey(Key.DirectionRight) } }
        keys { pressKey(Key.DirectionDown) }
        assertTrue(focused().toString(), focused().contains("Ayarlar"))
        // Senkron is the player's: Down skips it to the radio directory's search
        keys { pressKey(Key.DirectionDown) }
        assertTrue(focused().toString(), focused().contains("Radyo rehberinde ara"))
        keys { pressKey(Key.Enter) }
        assertEquals(Destination.Radio, window.destination)
    }

    @Test
    fun escapeGoesBackNeverOut() {
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.Comma) } }
        assertEquals(Destination.Settings, window.destination)
        keys { pressKey(Key.Escape) }
        assertEquals(Destination.Home, window.destination)
        // on the first screen Esc stays
        keys { pressKey(Key.Escape) }
        assertEquals(Destination.Home, window.destination)
    }

    @Test
    fun settingsFromTheirShortcut() {
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.Comma) } }
        assertEquals(Destination.Settings, window.destination)
    }
}
