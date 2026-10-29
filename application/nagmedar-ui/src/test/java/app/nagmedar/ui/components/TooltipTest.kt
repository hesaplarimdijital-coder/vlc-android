package app.nagmedar.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.input.ShortcutId
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

/**
 * S-404: a mouse resting on an icon button shows its name and keys after the window's delay and
 * leaving takes them away; touch never shows them; the delay can be turned off and is kept; 1.0.1:
 * the keys only with a keyboard.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w600dp-h400dp-mdpi-keysexposed-qwerty")
class TooltipTest {

    @get:Rule
    val compose = createComposeRule()

    private var delay by mutableStateOf<Long?>(TooltipDelay.Normal.millis)

    private fun button() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalTooltipDelay provides delay) {
                    Box(Modifier.padding(40.dp)) {
                        NagIconButton(NagIcons.Play, "Oynat", {}, shortcut = ShortcutId.PlayPause)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private val playButton get() = compose.onNodeWithContentDescription("Oynat")

    private fun tooltipShown() = compose.onAllNodesWithText("Boşluk").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aRestingMouseShowsTheNameAndTheKeys() {
        button()
        playButton.performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeBy(700)
        assertTrue("shown after the delay", tooltipShown())
        compose.onNodeWithText("Oynat").assertExists()
        // the mouse leaves the window (which wraps the button)
        playButton.performMouseInput { moveTo(Offset(-200f, -200f)) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertFalse("gone when the mouse leaves", tooltipShown())
    }

    @Test
    fun itWaitsForTheDelay() {
        delay = 5_000
        button()
        playButton.performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeBy(1_000)
        assertFalse("not before the delay", tooltipShown())
        compose.mainClock.advanceTimeBy(5_000)
        assertTrue("shown after the delay", tooltipShown())
    }

    @Test
    fun touchShowsNone() {
        button()
        playButton.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1500)
        assertFalse("touch shows none", tooltipShown())
        playButton.performTouchInput { up() }
    }

    /** 1.0.1: a mouse without a keyboard: the name alone, no keys it could not press. */
    @Test @Config(qualifiers = "tr-w600dp-h400dp-mdpi")
    fun withoutAKeyboardTheNameAlone() {
        button()
        playButton.performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText("Oynat").assertExists()
        assertFalse("no keys without a keyboard", tooltipShown())
    }

    @Test
    fun tooltipsCanBeTurnedOff() {
        delay = TooltipDelay.Off.millis
        button()
        playButton.performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeBy(2000)
        assertFalse("off shows none", tooltipShown())
    }

    @Test
    fun theDelayIsKept() {
        val shared = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("tooltip-test", Context.MODE_PRIVATE)
        NagmedarPreferences(shared).updateTooltipDelay(TooltipDelay.Long)
        assertEquals(TooltipDelay.Long, NagmedarPreferences(shared).tooltipDelay)
        assertEquals(1000L, TooltipDelay.Long.millis)
    }
}
