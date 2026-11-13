package app.nagmedar.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.9 (Desktop Kit 2.9.0's full-screen tasks): full-screen lyrics, the visualiser and the lyrics editor are pages of
 * the screen's overlay, which on a phone lies between the top bar and the bottom bar unless the expanded player is
 * under it. Opened with a shortcut over the mini player (a keyboard on a phone-wide window), they lay between the bars,
 * the lyrics editor's buttons above a bottom bar whose tabs could be tapped half-way through. Now they take the whole
 * window on a phone, the bars aside under them, as Sıradaki does; on a tablet nothing changes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneWindowPageTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(false)

    /** The shell at [width] with a task in the screen's overlay, coming in as the app's do ([animated]) or at once. */
    private fun shell(width: WidthClass, animated: Boolean = true) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(width, overlay = {
                    if (animated) {
                        AnimatedVisibility(open) {
                            PhoneWindowPage(this)
                            Box(Modifier.fillMaxSize().testTag("task"))
                        }
                    } else if (open) {
                        PhoneWindowPage(null)
                        Box(Modifier.fillMaxSize().testTag("task"))
                    }
                })
            }
        }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-mdpi")
    fun onAPhoneTheTaskHasTheWholeWindow() {
        shell(WidthClass.Compact)
        compose.onNodeWithText("Radyo").assertExists()
        open = true
        compose.waitForIdle()
        assertEquals(compose.onRoot().getBoundsInRoot(), compose.onNodeWithTag("task").getBoundsInRoot())
        // the bottom bar under it is neither drawn nor read out, its tabs not tapped
        compose.onNodeWithText("Radyo").assertDoesNotExist()
        open = false
        compose.waitForIdle()
        compose.onNodeWithTag("task").assertDoesNotExist()
        compose.onNodeWithText("Radyo").assertExists()
    }

    /** The lyrics editor comes without an animation: the whole window from its first frame. */
    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-mdpi")
    fun theEditorHasItAtOnce() {
        shell(WidthClass.Compact, animated = false)
        open = true
        compose.waitForIdle()
        assertEquals(compose.onRoot().getBoundsInRoot(), compose.onNodeWithTag("task").getBoundsInRoot())
        compose.onNodeWithText("Radyo").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "tr-w700dp-h1000dp-mdpi")
    fun onATabletNothingChanges() {
        shell(WidthClass.Medium)
        open = true
        compose.waitForIdle()
        // where the screen's overlay lies on a tablet, as before: beside the rail, not over the whole window
        assertNotEquals(compose.onRoot().getBoundsInRoot(), compose.onNodeWithTag("task").getBoundsInRoot())
    }
}
