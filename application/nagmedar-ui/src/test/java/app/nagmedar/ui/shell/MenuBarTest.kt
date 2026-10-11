package app.nagmedar.ui.shell

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.MenuBarMenus
import app.nagmedar.ui.components.MenuBarState
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuSpec
import app.nagmedar.ui.input.ShortcutGroup
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.menuBarSpecs
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
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * S-302: the menu bar has the eight menus, each item named and keyed from the shortcut table; it
 * shows only with a pointer; Alt gives it the keyboard without moving the window's focus, the
 * arrows move through titles and menus, Escape returns to the title and Alt gives the keyboard back.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class MenuBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val res get() = compose.activity.resources

    @Test
    fun theMenusComeFromTheShortcutTable() {
        val performed = mutableListOf<Any>()
        val specs = menuBarSpecs(res, { performed += it }, { true })
        assertEquals(listOf("Medya", "Oynatma", "Ses", "Video", "Altyazı", "Araçlar", "Görünüm", "Yardım"), specs.map { it.title })
        ShortcutGroup.entries.zip(specs).forEach { (group, spec) ->
            val shortcuts = ShortcutRegistry.defaults.filter { it.group == group }
            val items = spec.entries().filterIsInstance<MenuEntry.Item>()
            assertEquals(shortcuts.map { res.getString(it.label) }, items.map { it.label })
            assertEquals(shortcuts.map { it.chord?.label(res) }, items.map { it.shortcut })
            assertTrue(group.name, items.isNotEmpty())
        }
        specs[1].entries().filterIsInstance<MenuEntry.Item>().first().onClick()
        assertEquals(listOf(ShortcutRegistry.defaults.first { it.group == ShortcutGroup.Playback }.id), performed)
    }

    private fun shell(input: InputMode) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                TestShell(WidthClass.Large, startDestination = Destination.Music)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aPointerHasTheMenuBar() {
        shell(InputMode.Pointer)
        // the menu titles; "Araçlar" is a sidebar section too
        listOf("Medya", "Oynatma", "Ses", "Video", "Altyazı", "Araçlar", "Görünüm", "Yardım").forEach {
            compose.onNode(hasText(it) and hasClickAction()).assertExists()
        }
    }

    @Test
    fun touchHasNoMenuBarButKeepsTheSearch() {
        shell(InputMode.Touch)
        compose.onNodeWithText("Medya").assertDoesNotExist()
        compose.onNodeWithText("Yardım").assertDoesNotExist()
        compose.onNodeWithText("Ctrl+K").assertExists()
    }

    private val chosen = mutableListOf<String>()
    private lateinit var inputModeManager: InputModeManager

    private fun menus(state: MenuBarState) {
        val specs = listOf("Medya", "Oynatma", "Ses").map { title ->
            MenuSpec(title) {
                listOf(
                    MenuEntry.Item("$title 1", { chosen += "$title 1" }),
                    MenuEntry.Item("$title 2", { chosen += "$title 2" }),
                )
            }
        }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                inputModeManager = LocalInputModeManager.current
                Row { MenuBarMenus(specs, state) }
            }
        }
        compose.waitForIdle()
        // a hardware key takes the window out of touch mode; injected keys do not
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
    }

    /** A key of the window, as the activity passes it to the bar: pressed and released. */
    private fun MenuBarState.press(keyCode: Int, rtl: Boolean = false): Boolean {
        var used = false
        compose.runOnIdle {
            used = onKey(keyCode, down = true, rtl = rtl)
            onKey(keyCode, down = false, rtl = rtl)
        }
        compose.waitForIdle()
        return used
    }

    /** The focused nodes of the window (and of an open menu). */
    private fun focused() = compose.onAllNodes(isFocused()).fetchSemanticsNodes().map { it.id }

    @Test
    fun altAndTheArrowsWalkTheMenus() {
        val state = MenuBarState()
        menus(state)
        val before = focused()
        compose.runOnIdle { assertTrue(state.onAlt()) }
        compose.waitForIdle()
        // the first title has the keyboard; the window's focus stays where it was
        assertEquals(0, state.highlighted)
        assertEquals(before, focused())

        assertTrue(state.press(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(1, state.highlighted)

        // Down opens the menu with its first item focused
        assertTrue(state.press(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(1, state.open)
        compose.onNodeWithText("Oynatma 1").assertIsFocused()

        // Right goes on to the next menu, whose title takes the keyboard
        compose.onNodeWithText("Oynatma 1").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        assertEquals(2, state.open)
        assertEquals(2, state.highlighted)
        compose.onNodeWithText("Ses 1").assertIsFocused()

        // Down and Enter choose, and the bar gives the keyboard back
        compose.onNodeWithText("Ses 1").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithText("Ses 2").assertIsFocused()
        compose.onNodeWithText("Ses 2").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(listOf("Ses 2"), chosen)
        assertEquals(-1, state.open)
        assertEquals(-1, state.highlighted)
        assertEquals(before, focused())
    }

    @Test
    fun escapeReturnsToTheTitleAndAltLeaves() {
        val state = MenuBarState()
        menus(state)
        val before = focused()
        compose.runOnIdle { state.onAlt() }
        state.press(KeyEvent.KEYCODE_ENTER)
        compose.onNodeWithText("Medya 1").assertIsFocused()
        compose.onNodeWithText("Medya 1").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        // Left from the first menu goes round to the last
        assertEquals(2, state.open)
        compose.onNodeWithText("Ses 1").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertEquals(-1, state.open)
        assertEquals(2, state.highlighted)
        // Alt again gives the keyboard back to what had it
        compose.runOnIdle { assertTrue(state.onAlt()) }
        compose.waitForIdle()
        assertEquals(-1, state.highlighted)
        assertEquals(before, focused())
    }

    @Test
    fun theBarTakesOnlyItsKeys() {
        val state = MenuBarState()
        // no bar on screen (touch): Alt is not the bar's
        assertFalse(state.onAlt())
        menus(state)
        val before = focused()
        // without Alt the window's keys are not the bar's
        assertFalse(state.press(KeyEvent.KEYCODE_DPAD_RIGHT))
        compose.runOnIdle { state.onAlt() }
        // modifiers pass, so Alt can be released and Ctrl held
        compose.runOnIdle { assertFalse(state.onKey(KeyEvent.KEYCODE_SHIFT_LEFT, down = true)) }
        // Left from the first title goes round; right to left the arrows turn round
        assertTrue(state.press(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(2, state.highlighted)
        assertTrue(state.press(KeyEvent.KEYCODE_DPAD_LEFT, rtl = true))
        assertEquals(0, state.highlighted)
        // Escape leaves
        assertTrue(state.press(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(-1, state.highlighted)
        // any other key leaves and goes on to the window
        compose.runOnIdle { state.onAlt() }
        assertFalse(state.press(KeyEvent.KEYCODE_K))
        assertEquals(-1, state.highlighted)
        assertEquals(before, focused())
    }

    @Test
    fun theBarLeavesWhenItGoes() {
        val state = MenuBarState()
        var shown by mutableStateOf(true)
        val specs = listOf(MenuSpec("Medya") { listOf(MenuEntry.Item("Medya 1", {})) })
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Row { if (shown) MenuBarMenus(specs, state) }
            }
        }
        compose.runOnIdle { assertTrue(state.onAlt()) }
        // the window turned to touch: the bar is gone and gives the keyboard back
        shown = false
        compose.waitForIdle()
        assertEquals(-1, state.highlighted)
        assertFalse(state.onAlt())
    }
}
