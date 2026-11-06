package app.nagmedar.ui.music

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.3: the tablet's big player is pulled down as the sheets are (rememberSheetDrag): its lyrics scroll back to their
 * top first, and once there the pull goes on into the player, which follows the finger and closes; a pull that the
 * lyrics take whole leaves it open.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1392dp-h870dp-mdpi")
class WindowPlayerPullTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theLyricsAtTheirTopPulledDownCloseIt() {
        var open by mutableStateOf(true)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                Box(Modifier.fillMaxSize()) {
                    WindowPlayer(open, onClose = { open = false }) { modifier ->
                        // the lyrics in the player: a list as tall as the window
                        LazyColumn(modifier.fillMaxSize().background(NagTheme.colors.background).testTag("lyrics")) {
                            items((0 until 80).toList()) { i -> Text("Satır $i", Modifier.fillMaxWidth().height(48.dp)) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // scrolled down, then pulled back part of the way: the lines scroll back, the player stays
        compose.onNodeWithTag("lyrics").performTouchInput { swipeUp(startY = centerY, endY = centerY - 300f, durationMillis = 600) }
        compose.waitForIdle()
        compose.onNodeWithTag("lyrics").performTouchInput { swipeDown(startY = centerY - 200f, endY = centerY, durationMillis = 600) }
        compose.waitForIdle()
        assertTrue("a pull the lyrics took whole leaves the player open", open)
        // the rest of the way to their top, and on: the player follows the finger and closes
        compose.onNodeWithTag("lyrics").performTouchInput { swipeDown(startY = top + 10f, endY = top + 10f + height * 0.6f, durationMillis = 300) }
        compose.waitForIdle()
        assertFalse("pulled down past their top, the player closed", open)
    }
}
