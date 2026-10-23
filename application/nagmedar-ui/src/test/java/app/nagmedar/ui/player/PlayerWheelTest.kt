package app.nagmedar.ui.player

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.panel.PanelDock
import app.nagmedar.ui.player.panel.PanelHost
import app.nagmedar.ui.player.panel.PanelState
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.player.panel.PlayerPanel
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
import org.videolan.tools.NAGMEDAR_MIDDLE_PLAY_PAUSE
import org.videolan.vlc.gui.video.PlayerMouse

/**
 * S-408: the wheel sets the volume over the video alone. Over the panel beside it the wheel
 * scrolls the panel's tab (the subtitles' tab with its Whisper section, on a tablet in DeX), over
 * the control bar it is the bar's: its volume slider takes it, nothing else does. The player's
 * views are made as the chrome makes them, and each turn goes as the player's window sends it:
 * to the mouse's handling first, then to the views.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class PlayerWheelTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val visibility = ChromeVisibility()
    private val panel = PanelState()
    private val tab = ScrollState(0)
    private val actions = FakePlayerActions()

    /** What the wheel did to the player: the volume's notches, the seeks. */
    private val volume = mutableListOf<Int>()
    private val seeks = mutableListOf<Int>()

    private val player = object : PlayerMouse.Target {
        override val wheelSeeks = false
        override val middleClick = NAGMEDAR_MIDDLE_PLAY_PAUSE

        // as DesktopPlayerChrome answers it
        override fun isOverControls(x: Float, y: Float) = visibility.surfaces.contains(x, y)

        override fun wheelVolume(notches: Int) {
            volume += notches
        }

        override fun wheelSeek(deltaMs: Int) {
            seeks += deltaMs
        }

        override fun playPause() = Unit

        override fun toggleMute() = Unit
    }

    /** The video's controls over it and the panel docked beside it, on its subtitles' tab, long enough to scroll. */
    private fun player() {
        compose.runOnUiThread {
            val activity = compose.activity
            activity.actionBar?.hide()
            val window = FrameLayout(activity)
            val root = FrameLayout(activity)
            window.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            // not in an apply: there "visibility" would be the view's own
            val controls = ComposeView(activity)
            controls.setContent {
                NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                    DesktopControls(samplePlayerState, actions, visibility, previews = null)
                }
            }
            root.addView(controls, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            activity.setContentView(window)
            PanelHost(activity, root, panel, visibility.surfaces) {
                NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                    PlayerPanel(panel, listOf(PanelTab.Subtitles), PanelDock.Side, Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().verticalScroll(tab)) {
                            repeat(60) { line -> Text("Altyazı satırı ${line + 1}", Modifier.padding(12.dp)) }
                        }
                    }
                }
            }.attach()
            panel.show(PanelTab.Subtitles)
        }
        compose.waitForIdle()
    }

    private fun centreOf(tag: String): Offset = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow.center

    /** A notch of the wheel ([notches] up, below zero down) at the window's point [at], as the player's window has it. */
    private fun turn(at: Offset, notches: Float) {
        compose.runOnUiThread {
            val properties = arrayOf(
                MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_MOUSE
                },
            )
            val coords = arrayOf(
                MotionEvent.PointerCoords().apply {
                    x = at.x
                    y = at.y
                    setAxisValue(MotionEvent.AXIS_VSCROLL, notches)
                },
            )
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
            // VideoPlayerActivity.dispatchGenericMotionEvent: the mouse's handling, then the views
            if (!PlayerMouse.handle(player, event)) toTheViews(event)
            event.recycle()
        }
        compose.waitForIdle()
    }

    /**
     * The views' part of the window's dispatch: the turn goes to the Compose view under its point,
     * in that view's coordinates. Done here rather than through the activity's views: Robolectric's
     * API 34 views look for the view under a pointer's generic event at (x, x) (that release's
     * ViewGroup.dispatchGenericPointerEvent reads x twice), or under a mouse's cursor, which an event
     * made here does not have; a device's views give it to the view under the pointer.
     */
    private fun toTheViews(event: MotionEvent) {
        val target = composeViewUnder(compose.activity.window.decorView, event.x, event.y) ?: return
        val at = IntArray(2)
        target.getLocationInWindow(at)
        event.offsetLocation(-at[0].toFloat(), -at[1].toFloat())
        target.dispatchGenericMotionEvent(event)
    }

    /** The Compose view under the window's point ([x], [y]), as the views look for it: the last shown child that holds it, down. */
    private fun composeViewUnder(view: View, x: Float, y: Float): View? {
        if (view.visibility != View.VISIBLE) return null
        val at = IntArray(2)
        view.getLocationInWindow(at)
        if (x < at[0] || y < at[1] || x >= at[0] + view.width || y >= at[1] + view.height) return null
        if (view is ViewRootForTest) return view
        if (view !is ViewGroup) return null
        for (index in view.childCount - 1 downTo 0) {
            composeViewUnder(view.getChildAt(index), x, y)?.let { return it }
        }
        return null
    }

    @Test
    fun theWheelScrollsThePanelAndSetsTheVolumeOverTheVideo() {
        player()
        val panelBounds = compose.onNodeWithTag("player-panel").fetchSemanticsNode().boundsInWindow
        assertTrue("the panel shows beside the video", panelBounds.width > 0f && panelBounds.height > 0f)
        // over the panel's tab: it scrolls, the volume stays
        turn(panelBounds.center, -1f)
        assertTrue("the tab scrolled", tab.value > 0)
        assertEquals(emptyList<Int>(), volume)
        val scrolled = tab.value
        // over the video: the volume, and the panel stays where it was
        val video = centreOf("player-chrome")
        assertFalse(panelBounds.contains(video))
        turn(video, 1f)
        assertEquals(listOf(1), volume)
        assertEquals(scrolled, tab.value)
        assertEquals(emptyList<Int>(), seeks)
    }

    @Test
    fun overTheBarTheWheelIsTheBars() {
        player()
        compose.runOnUiThread {
            visibility.onMouseMoved()
            visibility.overBars = true
        }
        compose.waitForIdle()
        // the time's text: a part of the bar that does nothing with the wheel
        turn(centreOf("player-time"), 1f)
        assertEquals(emptyList<Int>(), volume)
        assertEquals(emptyList<String>(), actions.calls)
        // the bar's own volume slider takes it
        turn(centreOf("player-volume"), 1f)
        assertEquals(emptyList<Int>(), volume)
        assertTrue(actions.calls.toString(), actions.calls.single().startsWith("volume:"))
        // the bars gone, the video under them has the wheel again
        val bar = centreOf("player-time")
        compose.runOnUiThread { visibility.hide() }
        compose.waitForIdle()
        turn(bar, -1f)
        assertEquals(listOf(-1), volume)
    }
}
