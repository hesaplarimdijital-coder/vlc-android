package app.nagmedar.ui.input

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.UndoBarHost
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.1: keycaps and shortcut labels show with a hardware keyboard only (the S24 by touch showed
 * "Ctrl+Z" on its undo bars); with one attached (DeX, a keyboard cover) they stay.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardHintsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun config(keyboard: Int, hidden: Int) = Configuration().apply {
        this.keyboard = keyboard
        hardKeyboardHidden = hidden
    }

    @Test
    fun aKeyboardIsOneThatIsQwertyAndNotHidden() {
        assertTrue(hasHardwareKeyboard(config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_NO)))
        assertTrue(hasHardwareKeyboard(config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_UNDEFINED)))
        // a keyboard cover folded back
        assertFalse(hasHardwareKeyboard(config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_YES)))
        // a touch phone
        assertFalse(hasHardwareKeyboard(config(Configuration.KEYBOARD_NOKEYS, Configuration.HARDKEYBOARDHIDDEN_YES)))
        assertFalse(hasHardwareKeyboard(config(Configuration.KEYBOARD_12KEY, Configuration.HARDKEYBOARDHIDDEN_NO)))
    }

    @Test
    fun theKeysShowWithAKeyboardOnly() {
        assertEquals("Ctrl+Z", keyHint("Ctrl+Z", keyboard = true))
        assertNull(keyHint("Ctrl+Z", keyboard = false))
        assertNull(keyHint(null, keyboard = true))
        // a shortcut taken away leaves no empty keycap
        assertNull(keyHint(" ", keyboard = true))
    }

    private fun undoBar(keyboard: Boolean) {
        val undo = UndoBarState()
        undo.show("Fener sıradan kaldırıldı", "Geri al", "Ctrl+Z") {}
        compose.setContent {
            // as the window's configuration would say it (a qualifier would stay on for the tests after these)
            CompositionLocalProvider(LocalHardwareKeyboard provides keyboard) {
                NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) { UndoBarHost(undo) }
            }
        }
        compose.waitForIdle()
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-mdpi")
    fun theUndoBarOfATouchPhoneHasNoKeycap() {
        undoBar(keyboard = false)
        compose.onNodeWithText("Geri al").assertExists()
        compose.onNodeWithText("Ctrl+Z").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
    fun theUndoBarWithAKeyboardKeepsItsKeycap() {
        undoBar(keyboard = true)
        compose.onNodeWithText("Ctrl+Z").assertExists()
    }
}
