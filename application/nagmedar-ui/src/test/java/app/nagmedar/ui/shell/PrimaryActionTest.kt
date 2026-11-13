package app.nagmedar.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.8 (Desktop Kit 2.8.0, DeskMail's request 5, the user's choice for "Yeni liste"): the screen's main action floats on
 * a phone with its label at the list's top and the icon alone once scrolled; on the rail it is an icon at the top, in the
 * sidebar a button with its label.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class PrimaryActionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var made = 0

    private fun shell(widthClass: WidthClass, inputMode: InputMode = InputMode.Touch) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = inputMode) {
                TestShell(widthClass, screen = { _, _ ->
                    ShellPrimaryAction("Yeni liste", NagIcons.Plus) { made++ }
                    val padding = rememberShellContentPadding()
                    LazyColumn(Modifier.fillMaxSize().testTag("list"), contentPadding = padding) {
                        items((1..80).toList()) { Text("Liste $it") }
                    }
                })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aPhoneFloatsItWithItsLabelAtTheTop() {
        shell(WidthClass.Compact)
        compose.onNodeWithText("Yeni liste").assertIsDisplayed().performClick()
        assertEquals(1, made)
        // scrolled: the icon alone, still named for TalkBack
        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = bottom - 400f, endY = top + 300f, durationMillis = 600) }
        compose.waitForIdle()
        compose.onNodeWithText("Yeni liste").assertDoesNotExist()
        compose.onNodeWithContentDescription("Yeni liste").assertExists()
    }

    @Test
    @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun theRailHasItAtItsTop() {
        shell(WidthClass.Expanded)
        compose.onNodeWithContentDescription("Yeni liste").performClick()
        assertEquals(1, made)
    }

    @Test
    @Config(qualifiers = "tr-w1400dp-h900dp-mdpi")
    fun theSidebarHasItsButton() {
        shell(WidthClass.Large, InputMode.Pointer)
        compose.onNodeWithText("Yeni liste").performClick()
        assertEquals(1, made)
    }
}
