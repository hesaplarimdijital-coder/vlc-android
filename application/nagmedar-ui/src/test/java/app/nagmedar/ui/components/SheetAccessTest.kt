package app.nagmedar.ui.components

import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * 1.0.8 (Desktop Kit 2.8.0, the design audit's high finding 6): a sheet closes without a gesture (TalkBack, Switch
 * Access, a keyboard: the handle is its close button, the dimmed screen says what a tap on it does), sheets never pile
 * up (one opened over another closes that one), and the keyboard's focus stays in a sheet while it is up.
 */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-mdpi-keysexposed-qwerty")
class SheetAccessTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var first by mutableStateOf(false)
    private var second by mutableStateOf(false)
    private lateinit var view: View
    private lateinit var inputModes: InputModeManager

    /** The real shell on a phone with a button on its screen and two sheets. */
    private fun shell() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            inputModes = LocalInputModeManager.current
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, screen = { _, _ ->
                    Column {
                        NagButton("Arkada", {}, Modifier.testTag("behind"))
                        Text("ekran")
                    }
                    GlassBottomSheet(visible = first, onDismiss = { first = false }, label = "Birinci") {
                        NagButton("Bir", {})
                        NagButton("İki", {})
                    }
                    GlassBottomSheet(visible = second, onDismiss = { second = false }, label = "İkinci") { Text("ikinci-içerik") }
                })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theHandleClosesTheSheet() {
        shell()
        first = true
        compose.waitForIdle()
        compose.onNodeWithText("Bir").assertExists()
        compose.onNodeWithTag("sheet-handle")
            .assertContentDescriptionEquals("Sayfayı kapat")
            .assertHasClickAction()
            .performClick()
        compose.waitForIdle()
        assertFalse(first)
        compose.onNodeWithText("Bir").assertDoesNotExist()
    }

    /** TalkBack finds two ways out: the handle and the dimmed screen, which says what a tap on it does. */
    @Test
    fun theDimmedScreenSaysItClosesTheSheet() {
        shell()
        first = true
        compose.waitForIdle()
        val closers = compose.onAllNodesWithContentDescription("Sayfayı kapat")
        closers.assertCountEquals(2)
        closers.onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertFalse(first)
        compose.onNodeWithText("Bir").assertDoesNotExist()
    }

    @Test
    fun aSheetOpenedOverAnotherClosesIt() {
        shell()
        first = true
        compose.waitForIdle()
        second = true
        compose.waitForIdle()
        assertFalse("the first sheet closed", first)
        compose.onNodeWithText("ikinci-içerik").assertExists()
        compose.onNodeWithText("Bir").assertDoesNotExist()
    }

    /**
     * A sheet opened from another's content (a row's "⋯" in the music inspector's sheet on a phone) leaves that one open:
     * closing it would take the new one away with its content (the independent review of 1.0.8).
     */
    @Test
    fun aSheetOpenedFromAnothersContentLeavesItOpen() {
        var inner by mutableStateOf(false)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, screen = { _, _ ->
                    Text("ekran")
                    GlassBottomSheet(visible = first, onDismiss = { first = false }, label = "Denetçi") {
                        NagButton("Menüyü aç", { inner = true })
                        GlassBottomSheet(visible = inner, onDismiss = { inner = false }, label = "Menü") { Text("menü-içeriği") }
                    }
                    GlassBottomSheet(visible = second, onDismiss = { second = false }, label = "İkinci") { Text("ikinci-içerik") }
                })
            }
        }
        first = true
        compose.waitForIdle()
        compose.onNodeWithText("Menüyü aç").performClick()
        compose.waitForIdle()
        assertTrue("the sheet it was opened from stays", first)
        compose.onNodeWithText("menü-içeriği").assertExists()
        // closing the inner one leaves the outer one as it was
        inner = false
        compose.waitForIdle()
        assertTrue(first)
        compose.onNodeWithText("Menüyü aç").assertExists()
        // a sheet opened from the screen still closes it
        second = true
        compose.waitForIdle()
        assertFalse(first)
        compose.onNodeWithText("ikinci-içerik").assertExists()
    }

    /** Desktop Kit 2.8.0 (the audit's high finding 2): Tab and Shift+Tab do not reach the screen behind an open sheet. */
    @Test
    fun aSheetKeepsTheKeyboardsFocus() {
        shell()
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
        compose.waitForIdle()
        compose.onNodeWithTag("behind").requestFocus()
        compose.onNodeWithTag("behind").assertIsFocused()
        first = true
        compose.waitForIdle()
        // at once, from the sheet itself (it takes the focus as it opens), and then from its controls
        press(KeyEvent.KEYCODE_TAB, shift = true)
        assertTrue("Shift+Tab stays in the sheet", focusInSheet())
        repeat(6) {
            press(KeyEvent.KEYCODE_TAB)
            assertTrue("Tab stays in the sheet", focusInSheet())
        }
        repeat(6) {
            press(KeyEvent.KEYCODE_TAB, shift = true)
            assertTrue("Shift+Tab stays in the sheet", focusInSheet())
        }
        // the handle is one of its stops: Enter on it closes the sheet, and the focus goes back where it was
        compose.onNodeWithTag("sheet-handle").requestFocus()
        press(KeyEvent.KEYCODE_ENTER)
        assertFalse(first)
        compose.onNodeWithTag("behind").assertIsFocused()
    }

    private fun press(keyCode: Int, shift: Boolean = false) {
        val meta = if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        compose.runOnUiThread {
            view.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            view.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta))
        }
        compose.waitForIdle()
    }

    /** Whether the keyboard's focus is on the sheet (tagged "sheet") or on something in it. */
    private fun focusInSheet(): Boolean =
        compose.onAllNodes(isFocused() and (hasTestTag("sheet") or hasAnyAncestor(hasTestTag("sheet"))), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
}
