package app.nagmedar.ui.e2e

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-1403: the main flows of a DeX window with the mouse alone: a screen from the sidebar, the
 * menu bar's menus and the command palette opened by clicking. No key is pressed.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
class MouseOnlyFlowsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val window = DexWindow()

    @Before
    fun show() {
        ShortcutRegistry.resume()
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) { window.Content() }
        }
        compose.waitForIdle()
    }

    /** A left click with the mouse, in the middle of what says [text]. */
    private fun click(text: String, substring: Boolean = false) = click(compose.onNode(hasText(text, substring = substring) and hasClickAction()))

    private fun click(node: SemanticsNodeInteraction) {
        node.performMouseInput { click(center) }
        compose.waitForIdle()
    }

    @Test
    fun aScreenFromTheSidebar() {
        click("İndirmeler")
        assertEquals(Destination.Downloads, window.destination)
    }

    @Test
    fun theMenuBarsMenus() {
        click("Araçlar")
        // the menu's item, with its keys
        click(compose.onNode(hasText("Radyo rehberinde ara") and hasText("Ctrl+Shift+R") and hasClickAction()))
        assertEquals(Destination.Radio, window.destination)
        click("Yardım")
        click("Kısayol kartı")
        assertEquals(Destination.ShortcutCard, window.destination)
    }

    @Test
    fun theCommandPaletteByClicking() {
        click("Ctrl+K", substring = true)
        compose.onNodeWithTag("palette").assertExists()
        // its screens, then the network among them: the row says its section
        click("Ekranlar")
        click(compose.onNode(hasText("Ağ") and hasText("Kaynaklar") and hasClickAction()))
        assertEquals(Destination.Network, window.destination)
        compose.onNodeWithTag("palette").assertDoesNotExist()
    }
}
