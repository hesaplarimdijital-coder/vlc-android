package app.nagmedar.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.8 (Desktop Kit 2.8.0, the design audit's finding 15): the menu bar shows only in the sidebar layout; on the rail (a
 * tablet, a DeX window under the sidebar's width) its menus are behind a "≡" in the top bar, for a pointer as the menu
 * bar's are; a finger's tablet keeps its bar as it was.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
class RailMenusTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shell(inputMode: InputMode) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = inputMode) {
                TestShell(WidthClass.Expanded)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theRailsTopBarHasTheMenus() {
        shell(InputMode.Pointer)
        compose.onNodeWithContentDescription("Menüler").performClick()
        compose.waitForIdle()
        // the menus' titles; a title opens its menu
        compose.onNodeWithText("Medya").performClick()
        compose.waitForIdle()
        // the list of titles closed, Medya's menu is up in its place
        compose.onNodeWithText("Oynatma").assertDoesNotExist()
    }

    @Test
    fun aFingersTabletKeepsItsBar() {
        shell(InputMode.Touch)
        compose.onNodeWithContentDescription("Menüler").assertDoesNotExist()
    }
}
