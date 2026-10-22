package app.nagmedar.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the touch layout read from the player and asked of it beyond the controls' actions. */
internal class FakeTouchActions : TouchActions {
    val calls = mutableListOf<String>()

    override val gestures = TouchGestureSettings()

    override fun jump(deltaMs: Int) {
        calls += "jump:$deltaMs"
    }

    override fun timeMs(): Long = 60_000L

    override fun lengthMs(): Long = 600_000L

    override fun playing(): Boolean = true

    override fun rate(): Float = 1f

    override fun volumePercent(): Int = 50

    override fun maxVolumePercent(): Int = 100

    override fun brightness(): Float = 0.5f

    override fun setBrightness(level: Float) {
        calls += "brightness"
    }

    override fun zoom(fill: Boolean) {
        calls += "zoom:$fill"
    }

    override fun openSheet(sheet: PlayerSheet) {
        calls += "sheet:$sheet"
    }

    override fun openPanel(tab: PanelTab) {
        calls += "panel:$tab"
    }

    override fun lock() {
        calls += "lock"
    }

    override fun unlock() {
        calls += "unlock"
    }
}

/**
 * S-601's touch gestures end with the touch layout: when the layout goes while a finger is still
 * on the video (a mouse moved, picture in picture began), the long press's fast play and a
 * swipe's line on the OSD do not stay behind.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w891dp-h411dp-xhdpi")
class TouchControlsTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = FakePlayerActions()
    private val touch = FakeTouchActions()
    private val osd = PlayerOsd()
    private val visibility = ChromeVisibility(touch = true)
    private var shown by mutableStateOf(true)

    @Before
    fun show() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                if (shown) TouchControls(PlayerChromeState(playing = true, lengthMs = 600_000L), actions, touch, visibility, osd, previews = null)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theLayoutGoingMidPressEndsTheFastPlay() {
        compose.onNodeWithTag("player-gestures").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(LONG_PRESS_MS)
        compose.waitForIdle()
        assertEquals(listOf("rate:2.0"), actions.calls)
        assertTrue("the fast play is said while held", osd.shown is OsdItem.Line)
        // a mouse moved: the desktop layout takes over while the finger is still down
        shown = false
        compose.waitForIdle()
        assertEquals(listOf("rate:2.0", "rate:1.0"), actions.calls)
        assertNull(osd.shown)
    }

    @Test
    fun pictureInPictureEndsTheFastPlay() {
        compose.onNodeWithTag("player-gestures").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(LONG_PRESS_MS)
        compose.waitForIdle()
        visibility.onPictureInPicture(true)
        compose.waitForIdle()
        assertEquals(listOf("rate:2.0", "rate:1.0"), actions.calls)
        assertNull(osd.shown)
        // the release that comes later changes nothing more
        compose.onNodeWithTag("player-gestures").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf("rate:2.0", "rate:1.0"), actions.calls)
    }

    @Test
    fun theLayoutGoingMidSwipeLeavesNoLineAndDoesNotSeek() {
        compose.onNodeWithTag("player-gestures").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(120f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(120f, 0f))
        }
        compose.waitForIdle()
        assertTrue("the swipe says where it seeks while held", osd.shown is OsdItem.Line)
        shown = false
        compose.waitForIdle()
        assertNull(osd.shown)
        assertTrue(actions.calls.none { it.startsWith("seekTo") })
    }

    private companion object {
        /** Past the long press's timeout. */
        const val LONG_PRESS_MS = 1_000L
    }
}
