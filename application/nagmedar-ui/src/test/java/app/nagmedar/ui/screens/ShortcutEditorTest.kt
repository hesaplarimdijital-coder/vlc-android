package app.nagmedar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.input.KeyChord
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutPreset
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-402: keys pressed on a shortcut become its keys; another shortcut's keys are assigned only
 * after a warning, the window's never; Esc gives up, Backspace takes the keys away; the presets
 * and the reset can be undone; conflicts in the table are shown.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h3200dp-mdpi")
class ShortcutEditorTest {

    @get:Rule
    val compose = createComposeRule()

    private var overrides by mutableStateOf<Map<ShortcutId, KeyChord?>>(emptyMap())
    private val undo = UndoBarState()

    @After
    fun reset() {
        ShortcutRegistry.overrides = emptyMap()
        ShortcutRegistry.resume()
    }

    private fun editor() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Column { ShortcutEditor(overrides, { overrides = it }, undo) }
            }
        }
        compose.waitForIdle()
    }

    private val stop get() = compose.onNodeWithTag("keys-Stop")

    private fun record(key: Key) {
        stop.performClick()
        compose.waitForIdle()
        stop.performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun pressedKeysBecomeTheShortcut() {
        editor()
        stop.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tuşlara basın…").assertExists()
        // while keys are recorded they are not shortcuts
        assertTrue(ShortcutRegistry.recording)
        stop.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Q) } }
        compose.waitForIdle()
        assertEquals(mapOf(ShortcutId.Stop to KeyChord(Key.Q, ctrl = true)), overrides)
        assertFalse(ShortcutRegistry.recording)
        stop.assert(hasText("Ctrl+Q"))
        compose.onNodeWithText("Varsayılan: S").assertExists()
        // and back to the default
        compose.onNodeWithContentDescription("Durdur: varsayılana döndür").performClick()
        compose.waitForIdle()
        assertTrue(overrides.isEmpty())
        stop.assert(hasText("S"))
    }

    @Test
    fun anotherShortcutsKeysAskFirst() {
        editor()
        record(Key.M)
        // M is Sessiz's: nothing changes before the answer
        compose.onNodeWithText("M şu an “Sessiz” kısayolunda", substring = true).assertExists()
        assertTrue(overrides.isEmpty())
        compose.onNodeWithText("Yine de ata").performClick()
        compose.waitForIdle()
        assertEquals(mapOf(ShortcutId.Stop to KeyChord(Key.M), ShortcutId.Mute to null), overrides)
        compose.onNodeWithText("Yine de ata").assertDoesNotExist()
        compose.onNodeWithTag("keys-Mute").assert(hasText("Tuş yok"))
    }

    @Test
    fun theWindowsKeysAreRefusedEscGivesUpBackspaceRemoves() {
        editor()
        record(Key.F10)
        compose.onNodeWithText("F10 menü çubuğunu açar; kısayol olamaz.").assertExists()
        assertTrue(overrides.isEmpty())
        compose.onNodeWithText("Tamam").performClick()
        compose.waitForIdle()

        record(Key.Escape)
        assertTrue(overrides.isEmpty())
        compose.onNodeWithText("Tuşlara basın…").assertDoesNotExist()

        record(Key.Backspace)
        assertEquals(mapOf<ShortcutId, KeyChord?>(ShortcutId.Stop to null), overrides)
        stop.assert(hasText("Tuş yok"))
    }

    @Test
    fun presetsAndTheResetCanBeTakenBack() {
        editor()
        compose.onNode(hasText("mpv") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(ShortcutPreset.Mpv.overrides, overrides)
        stop.assert(hasText("Q"))
        assertEquals(1, undo.messages.size)

        compose.onNodeWithText("Tümünü sıfırla").performClick()
        compose.waitForIdle()
        assertTrue(overrides.isEmpty())
        // Ctrl+Z (the undo bar) takes the reset back
        compose.runOnIdle { assertTrue(undo.undoLatest()) }
        compose.waitForIdle()
        assertEquals(ShortcutPreset.Mpv.overrides, overrides)
    }

    @Test
    fun conflictsInTheTableAreShown() {
        overrides = mapOf(ShortcutId.Stop to KeyChord(Key.M))
        editor()
        compose.onNodeWithTag("shortcut-conflicts").assertExists()
        compose.onNodeWithText("M: “Durdur” ve “Sessiz”", substring = true).assertExists()
    }
}
