package app.nagmedar.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * S-302: the menu bar has the eight menus, each item named and keyed from the shortcut table; it
 * shows only with a pointer; Alt takes the keyboard into it, the arrows move through titles and
 * menus, Escape returns to the title and Alt leaves.
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
            assertEquals(shortcuts.map { it.chord.label(res) }, items.map { it.shortcut })
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

    @Test
    fun altAndTheArrowsWalkTheMenus() {
        val state = MenuBarState()
        menus(state)
        compose.runOnIdle { state.onAlt() }
        compose.waitForIdle()
        compose.onNodeWithText("Medya").assertIsFocused()

        compose.onNodeWithText("Medya").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        compose.onNodeWithText("Oynatma").assertIsFocused()

        // Down opens the menu with its first item focused
        compose.onNodeWithText("Oynatma").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        assertEquals(1, state.open)
        compose.onNodeWithText("Oynatma 1").assertIsFocused()

        // Right goes on to the next menu
        compose.onNodeWithText("Oynatma 1").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        assertEquals(2, state.open)
        compose.onNodeWithText("Ses 1").assertIsFocused()

        // Down and Enter choose
        compose.onNodeWithText("Ses 1").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithText("Ses 2").assertIsFocused()
        compose.onNodeWithText("Ses 2").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(listOf("Ses 2"), chosen)
        assertEquals(-1, state.open)
    }

    @Test
    fun escapeReturnsToTheTitleAndAltLeaves() {
        val state = MenuBarState()
        menus(state)
        compose.runOnIdle { state.onAlt() }
        compose.waitForIdle()
        compose.onNodeWithText("Medya").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithText("Medya 1").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        // Left from the first menu goes round to the last
        assertEquals(2, state.open)
        compose.onNodeWithText("Ses 1").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertEquals(-1, state.open)
        compose.onNodeWithText("Ses").assertIsFocused()
        compose.runOnIdle { state.onAlt() }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(isFocused()).fetchSemanticsNodes().isEmpty())
    }
}
