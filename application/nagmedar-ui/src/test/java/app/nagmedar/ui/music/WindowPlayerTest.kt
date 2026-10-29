package app.nagmedar.ui.music

import androidx.activity.ComponentActivity
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.BigPlayerWindow
import app.nagmedar.ui.shell.CaptionBar
import app.nagmedar.ui.shell.WidthClass
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
 * 1.0.2: on a tablet and in DeX the bar's "open the player" (its button, its artwork and words) opens
 * the big player over the whole window, the bars standing aside, as the phone's full player; Back,
 * Esc, a pull down from anywhere on it and its ⌄ close it; a desktop window's caption bar stays the
 * window's.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WindowPlayerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun window(widthClass: WidthClass, input: InputMode, caption: CaptionBar? = null): BigPlayerWindow {
        val window = BigPlayerWindow(widthClass, caption)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = input) { window.Content() }
        }
        compose.waitForIdle()
        return window
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** The big player is up over the whole window (below [top]), the bar under it gone. */
    private fun assertOverTheWindow(top: Float = 0f) {
        compose.onNodeWithTag("phone-player").assertIsDisplayed()
        val root = compose.onRoot().getBoundsInRoot()
        val player = compose.onNodeWithTag("phone-player").getBoundsInRoot()
        assertEquals(root.left.value, player.left.value, 0.5f)
        assertEquals(root.right.value, player.right.value, 0.5f)
        assertEquals(root.bottom.value, player.bottom.value, 0.5f)
        assertEquals(top, player.top.value, 0.5f)
        compose.onNodeWithTag("transport-bar").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "tr-w1392dp-h870dp-mdpi")
    fun theBarsButtonOpensTheBigPlayerOnATabletAndBackClosesIt() {
        val window = window(WidthClass.Large, InputMode.Touch)
        compose.onNodeWithTag("phone-player").assertDoesNotExist()
        compose.onNodeWithTag("transport-open-player").performClick()
        compose.waitForIdle()
        assertTrue(window.open)
        assertOverTheWindow()
        // the phone's player, wide: the artwork beside the words and the controls
        val artwork = compose.onNodeWithTag("phone-artwork").getBoundsInRoot()
        val controls = compose.onNodeWithTag("player-controls").getBoundsInRoot()
        assertTrue("the controls beside the artwork: $artwork, $controls", controls.left >= artwork.right)
        assertTrue("the artwork large: $artwork", artwork.bottom - artwork.top >= 560.dp)
        back()
        assertFalse(window.open)
        compose.onNodeWithTag("phone-player").assertDoesNotExist()
        compose.onNodeWithTag("transport-bar").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "tr-w1392dp-h870dp-mdpi")
    fun theArtworkAndTheWordsOpenItTooAndAPullDownAnywhereClosesIt() {
        val window = window(WidthClass.Large, InputMode.Touch)
        // the bar's, not the sample table's row of the same track
        compose.onNode(hasText("Sabaha Kadar") and hasAnyAncestor(hasTestTag("transport-bar"))).performClick()
        compose.waitForIdle()
        assertTrue(window.open)
        assertOverTheWindow()
        // pulled down from above the words, where nothing of its own moves
        compose.onNodeWithTag("phone-player").performTouchInput {
            swipe(Offset(width * 0.8f, height * 0.12f), Offset(width * 0.8f, height * 0.9f), durationMillis = 300)
        }
        compose.waitForIdle()
        assertFalse(window.open)
        compose.onNodeWithTag("phone-player").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "tr-w1392dp-h870dp-mdpi")
    fun aShortPullSpringsBack() {
        val window = window(WidthClass.Large, InputMode.Touch)
        compose.onNodeWithTag("transport-open-player").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("phone-player").performTouchInput {
            swipe(Offset(width * 0.8f, height * 0.12f), Offset(width * 0.8f, height * 0.12f + 40.dp.toPx()), durationMillis = 800)
        }
        compose.waitForIdle()
        assertTrue(window.open)
        assertOverTheWindow()
    }

    @Test
    @Config(qualifiers = "tr-w870dp-h1392dp-mdpi")
    fun anUprightTabletsMiniPlayerOpensItAsTheRailDid() {
        val window = window(WidthClass.Expanded, InputMode.Touch)
        compose.onNode(hasText("Sabaha Kadar") and hasAnyAncestor(hasTestTag("mini-player"))).performClick()
        compose.waitForIdle()
        assertTrue(window.open)
        compose.onNodeWithTag("phone-player").assertIsDisplayed()
        // upright: the phone's composition scaled up, the artwork over the words
        val artwork = compose.onNodeWithTag("phone-artwork").getBoundsInRoot()
        val title = compose.onNodeWithTag("phone-title").getBoundsInRoot()
        assertTrue("the words under the artwork: $artwork, $title", title.top >= artwork.bottom)
        assertTrue("the artwork large: $artwork", artwork.right - artwork.left >= 600.dp)
        compose.onNodeWithTag("phone-player-close").performClick()
        compose.waitForIdle()
        assertFalse(window.open)
    }

    @Test
    @Config(qualifiers = "tr-w1440dp-h900dp-mdpi-keysexposed-qwerty")
    fun escClosesItOnADesktop() {
        val window = window(WidthClass.Large, InputMode.Pointer)
        // a hardware keyboard's first key takes the window out of touch mode; injected keys do not
        compose.runOnIdle { window.inputModes.requestInputMode(ComposeInputMode.Keyboard) }
        compose.onNodeWithTag("transport-open-player").performClick()
        compose.waitForIdle()
        assertOverTheWindow()
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertFalse(window.open)
        compose.onNodeWithTag("phone-player").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "tr-w1440dp-h900dp-mdpi-keysexposed-qwerty")
    fun aDesktopWindowsCaptionBarStaysTheWindows() {
        val window = window(WidthClass.Large, InputMode.Pointer, caption = CaptionBar(40.dp, right = 138.dp))
        compose.onNodeWithTag("transport-open-player").performClick()
        compose.waitForIdle()
        assertTrue(window.open)
        // under the caption bar, where the system's buttons are and the window is moved
        assertOverTheWindow(top = 40f)
        back()
        assertFalse(window.open)
    }
}
