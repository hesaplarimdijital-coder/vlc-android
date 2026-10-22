package app.nagmedar.ui.player

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * The player's sheets have the keyboard: the player's frame keeps the focus from its own views,
 * so a sheet is a window of its own. Opened by a key (Up opens the menu) its first item has the
 * focus, the arrows go from item to item, Enter acts, Esc closes it; opened by a finger, Down
 * goes to its first item.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class PlayerSheetsTest {

    @get:Rule
    val compose = createComposeRule()

    private val sheets = PlayerSheets()
    private val done = mutableListOf<String>()

    private fun item(label: String) = MenuEntry.Item(label, { done += label })

    private fun show(byKeys: Boolean) {
        sheets.show(PlayerSheet.More, byKeys)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                if (sheets.open != null) {
                    PlayerSheetView(
                        "Menü",
                        listOf(item("Zamana atla…"), item("Yer imleri"), MenuEntry.Separator, item("Ekranı kilitle")),
                        onDismiss = sheets::close,
                        focusFirst = sheets.byKeys,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun openedByAKeyItsFirstItemHasTheKeyboard() {
        show(byKeys = true)
        compose.onNodeWithText("Zamana atla…").assertIsFocused()
        compose.onNodeWithText("Zamana atla…").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Yer imleri").assertIsFocused()
        compose.onNodeWithText("Yer imleri").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(listOf("Yer imleri"), done)
        assertNull(sheets.open)
    }

    @Test
    fun openedByAFingerDownGoesToItsFirstItem() {
        show(byKeys = false)
        compose.onNodeWithText("Zamana atla…").assertIsNotFocused()
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Zamana atla…").assertIsFocused()
    }

    @Test
    fun escapeClosesIt() {
        show(byKeys = true)
        // as a keyboard's Esc reaches the sheet's window
        compose.runOnUiThread {
            val window = ShadowDialog.getLatestDialog().window!!
            window.callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))
            window.callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE))
        }
        compose.waitForIdle()
        assertNull(sheets.open)
        assertEquals(emptyList<String>(), done)
    }
}
