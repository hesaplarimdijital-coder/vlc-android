package app.nagmedar.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.8 (Desktop Kit 2.8.0): the page top bar's back arrow slides in or out before the title, and the title moves
 * along with it; the title fades only when its words change. Until then the whole bar faded out and in when the
 * arrow alone came or went.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class PageTopBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theSameTitleDoesNotFadeWhenTheBackArrowComes() {
        var back by mutableStateOf(false)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides MotionTier.Gesture) {
                    PageTopBar(title = "Ayarlar", onSearch = null, onBack = if (back) ({}) else null)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        back = true
        repeat(20) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            // a fade would compose the outgoing and the incoming title together
            compose.onAllNodesWithText("Ayarlar").assertCountEquals(1)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("top-bar-back").assertIsDisplayed()
        compose.onNodeWithText("Ayarlar").assertIsDisplayed()
    }
}
