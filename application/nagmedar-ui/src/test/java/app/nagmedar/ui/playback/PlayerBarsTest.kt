package app.nagmedar.ui.playback

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.SamplePlayerBar
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.samplePlayerInfo
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * S-304: the bar is on every screen; a tap on it opens the full player; with a pointer, hovering
 * the mini player brings out more controls; the transport bar shuffles, repeats, mutes and sets
 * the volume.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerBarsTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recorder : PlayerBarActions {
        val calls = mutableListOf<String>()
        val seeks = mutableListOf<Float>()
        val volumes = mutableListOf<Int>()

        override fun openPlayer() {
            calls += "open"
        }

        override fun togglePlayPause() {
            calls += "playPause"
        }

        override fun previous() {
            calls += "previous"
        }

        override fun next() {
            calls += "next"
        }

        override fun seekTo(fraction: Float) {
            seeks += fraction
        }

        override fun stop() {
            calls += "stop"
        }

        override fun shuffle() {
            calls += "shuffle"
        }

        override fun cycleRepeat() {
            calls += "repeat"
        }

        override fun setVolume(volume: Int) {
            volumes += volume
        }
    }

    private val actions = Recorder()
    private lateinit var inputModeManager: InputModeManager
    private lateinit var focusManager: FocusManager

    private fun bar(kind: NavKind, input: InputMode) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                inputModeManager = LocalInputModeManager.current
                focusManager = LocalFocusManager.current
                SamplePlayerBar(kind, actions = actions)
            }
        }
        compose.waitForIdle()
    }

    private val seekBar = hasContentDescription("Parça konumu") and SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun aTapOpensThePlayerAndTheButtonsControlPlayback() {
        bar(NavKind.BottomBar, InputMode.Touch)
        compose.onNodeWithText("Sabaha Kadar").performClick()
        compose.onNodeWithContentDescription("Duraklat").performClick()
        compose.onNodeWithContentDescription("Sonraki parça").performClick()
        assertEquals(listOf("open", "playPause", "next"), actions.calls)
        // the second line: artist and format
        compose.onNodeWithText("Ada Karan · Gece Mavisi · FLAC 24/96").assertExists()
        // by touch there is nothing to hover
        compose.onNodeWithContentDescription("Önceki parça").assertDoesNotExist()
        compose.onNodeWithContentDescription("Durdur").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "tr-w700dp-h900dp-mdpi")
    fun hoveringTheMiniPlayerBringsOutItsControls() {
        bar(NavKind.Rail, InputMode.Pointer)
        compose.onNodeWithContentDescription("Önceki parça").assertDoesNotExist()
        compose.onNodeWithTag("mini-player").performMouseInput { moveTo(center) }
        compose.waitForIdle()
        // clicked with the mouse, which stays over the bar
        compose.onNodeWithContentDescription("Önceki parça").performMouseInput { click() }
        compose.onNodeWithContentDescription("Durdur").performMouseInput { click() }
        assertEquals(listOf("previous", "stop"), actions.calls)
        // the seek bar takes the second line and tells the time under the pointer, in a bubble over it
        compose.onNode(seekBar).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(listOf(0.5f), actions.seeks)
        compose.onNode(seekBar).performMouseInput { moveTo(center) }
        compose.waitForIdle()
        compose.onNodeWithTag("progress-bubble").assertTextEquals("2:09")
        // sought to the middle of 4:18: the time played and the time left either side
        compose.onNode(hasText("2:09") and hasTestTag("progress-bubble").not()).assertExists()
        compose.onNodeWithText("−2:09").assertExists()
        // away from the bar (the window wraps it: outside the window) and without the focus, the controls go
        compose.runOnIdle { focusManager.clearFocus() }
        compose.onRoot().performMouseInput { moveTo(Offset(-40f, -40f)) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Durdur").assertDoesNotExist()
        compose.onNodeWithText("Ada Karan · Gece Mavisi · FLAC 24/96").assertExists()
    }

    @Test
    @Config(qualifiers = "tr-w700dp-h900dp-mdpi")
    fun theKeyboardBringsOutTheControlsToo() {
        bar(NavKind.Rail, InputMode.Pointer)
        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
        compose.onNodeWithContentDescription("Sonraki parça").requestFocus()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Durdur").assertExists()
        compose.onNode(seekBar).assertExists()
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun theTransportBarShufflesRepeatsAndSetsTheVolume() {
        bar(NavKind.Sidebar, InputMode.Pointer)
        compose.onNodeWithText("Sabaha Kadar").performClick()
        compose.onNodeWithContentDescription("Karıştır").performClick()
        compose.onNodeWithContentDescription("Tekrarla").performClick()
        compose.onNodeWithContentDescription("Oynatıcıyı aç").performClick()
        assertEquals(listOf("open", "shuffle", "repeat", "open"), actions.calls)
        compose.onNodeWithText("FLAC 24/96").assertExists()
        // mute, then the sound comes back at its level
        compose.onNodeWithContentDescription("Sesi kapat").performClick()
        compose.onNodeWithContentDescription("Sesi aç").performClick()
        assertEquals(listOf(0, 70), actions.volumes)
        compose.onNode(hasContentDescription("Ses düzeyi")).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(50, actions.volumes.last())
        compose.onNode(seekBar).performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }
        assertEquals(listOf(0.25f), actions.seeks)
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun theBarIsOnEveryScreenOfAPhone() = everyScreen(WidthClass.Compact, InputMode.Touch, "mini-player")

    @Test
    @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun theBarIsOnEveryScreenOfADesktop() = everyScreen(WidthClass.Large, InputMode.Pointer, "transport-bar")

    private fun everyScreen(widthClass: WidthClass, input: InputMode, tag: String) {
        var start by mutableStateOf(Destination.Home)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                TestShell(widthClass, startDestination = start, screen = { destination, _ -> Text(destination.name) })
            }
        }
        for (destination in Destination.entries) {
            start = destination
            compose.waitForIdle()
            compose.onNodeWithText(destination.name).assertExists()
            compose.onNodeWithTag(tag).assertExists()
        }
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun aVideoSaysSo() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                SamplePlayerBar(NavKind.BottomBar, samplePlayerInfo.copy(title = "Kuzey Işığı", subtitle = "Şimdi çalıyor", isVideo = true, format = "4K DV"))
            }
        }
        compose.onNodeWithText("Şimdi çalıyor · 4K DV").assertExists()
    }
}
