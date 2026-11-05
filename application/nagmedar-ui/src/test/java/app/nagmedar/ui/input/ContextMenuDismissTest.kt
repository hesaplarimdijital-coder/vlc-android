package app.nagmedar.ui.input

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowUiAutomation
import kotlin.math.roundToInt

/**
 * 1.0.3 (Desktop Kit 0.2.2), a row's context menu with a mouse, frame by frame: clicks go in the way
 * Android delivers them (to the topmost window that takes them: the focused menu takes every press
 * in the app's window), and every frame records where each menu window is. A click outside closes
 * the menu where it is and does nothing else; a right click outside opens the menu there in the same
 * click, and the old menu never moves there; Esc closes it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1000dp-h600dp-mdpi")
class ContextMenuDismissTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val names = listOf("Sabaha Kadar", "Gece Yolcusu", "Neva Kâr", "Yağmur", "Liman Işıkları")
    private val clicked = mutableListOf<String>()
    private val chosen = mutableListOf<String>()

    /** One record per menu window per frame. */
    private data class Frame(val menu: View, val at: IntOffset)

    private val frames = mutableListOf<Frame>()

    private fun rows() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Pointer) {
                Column(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    names.forEach { name ->
                        ContextMenuArea(entries = { listOf(MenuEntry.Item("Aç: $name", { chosen += name }), MenuEntry.Item("Sil", {})) }) {
                            val interaction = remember { MutableInteractionSource() }
                            Box(Modifier.fillMaxWidth().height(48.dp).nagClickable(interaction, { clicked += name })) { Text(name) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** A point on row [index], [x] pixels from the left (mdpi: 1 dp = 1 px). */
    private fun point(index: Int, x: Float): Offset =
        Offset(x, compose.onNodeWithText(names[index]).fetchSemanticsNode().boundsInWindow.center.y)

    private fun Offset.rounded() = IntOffset(x.roundToInt(), y.roundToInt())

    private val global by lazy { Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null) }

    @Suppress("UNCHECKED_CAST")
    private fun <T> globalList(name: String): List<T> =
        (global.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(global) as List<T>).toList()

    /** Every window of the app: its root view and its layout parameters (where the window is). */
    private fun windows(): List<Pair<View, WindowManager.LayoutParams>> = globalList<View>("mViews").zip(globalList("mParams"))

    /** The menu windows (every window but the activity's own) on screen now. */
    private fun menus(): List<View> {
        val decor = compose.activity.window.decorView
        return windows().filter { (view, _) -> view !== decor }.map { it.first }
    }

    private fun describeWindows() = windows().joinToString { (view, params) -> "${view.javaClass.simpleName} type=${params.type} at ${params.x},${params.y}" }

    private fun View.at(): IntOffset = windows().first { it.first === this }.second.let { IntOffset(it.x, it.y) }

    private fun record() {
        menus().forEach { frames += Frame(it, it.at()) }
    }

    private fun mouse(action: Int, at: Offset, buttons: Int, downTime: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = at.x; y = at.y; pressure = 1f; size = 1f })
        return MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1, properties, coords, 0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
    }

    /** A mouse click at [at] in window (= screen) coordinates, routed as Android's input dispatcher does. */
    private fun click(at: Offset, button: Int) {
        compose.runOnUiThread {
            val down = SystemClock.uptimeMillis()
            ShadowUiAutomation.injectInputEvent(mouse(MotionEvent.ACTION_DOWN, at, button, down))
            ShadowUiAutomation.injectInputEvent(mouse(MotionEvent.ACTION_UP, at, 0, down))
        }
    }

    private fun rightClick(at: Offset) = click(at, MotionEvent.BUTTON_SECONDARY)

    private fun leftClick(at: Offset) = click(at, MotionEvent.BUTTON_PRIMARY)

    /** Opens a row's menu with a right click at [at] and lets it settle; returns its window. */
    private fun openAt(at: Offset): View {
        rightClick(at)
        compose.waitForIdle()
        compose.onNodeWithText("Sil").assertExists()
        val menu = menus().singleOrNull() ?: error("expected one menu window: ${describeWindows()}")
        assertEquals(at.rounded(), menu.at())
        return menu
    }

    /** Runs [action], then frame by frame until every animation is over, recording each frame. */
    private fun framesOf(action: () -> Unit) {
        compose.mainClock.autoAdvance = false
        frames.clear()
        record()
        action()
        record()
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            record()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    /** Frames before a new window was measured and placed (still at the corner) say nothing. */
    private fun placed(of: List<Frame>) = of.filter { it.at != IntOffset.Zero }

    @Test
    fun aClickOutsideClosesTheMenuWhereItIsAndDoesNothingElse() {
        rows()
        val a = point(1, 100f)
        val b = point(4, 650f)
        val menu = openAt(a)

        framesOf { leftClick(b) }

        assertTrue("the menu should be closed", menus().isEmpty())
        assertTrue("frames were recorded", frames.isNotEmpty())
        frames.forEach { frame ->
            assertTrue("only the old menu may be on screen while it fades out: $frame", frame.menu === menu)
            assertEquals("the closing menu stays where it opened", a.rounded(), frame.at)
        }
        assertEquals("the click that closed the menu does not reach the row", emptyList<String>(), clicked)
    }

    @Test
    fun aRightClickOutsideOpensOneMenuThereAndTheOldOneNeverMovesThere() {
        rows()
        val a = point(1, 100f)
        val b = point(4, 650f)
        val old = openAt(a)

        framesOf { rightClick(b) }

        val now = menus().singleOrNull() ?: error("expected one menu window: ${describeWindows()}")
        assertNotSame(old, now)
        assertEquals(b.rounded(), now.at())
        compose.onNodeWithText("Aç: ${names[4]}").assertExists()
        frames.filter { it.menu === old }.forEach { assertEquals("the old menu closes where it was", a.rounded(), it.at) }
        placed(frames.filter { it.menu !== old }).forEach { assertEquals("the new menu opens at the click", b.rounded(), it.at) }
        assertEquals(emptyList<String>(), clicked)
    }

    @Test
    fun aRightClickElsewhereOnTheSameRowOpensItThere() {
        rows()
        val a = point(1, 100f)
        val b = point(1, 650f)
        val old = openAt(a)

        framesOf { rightClick(b) }

        val now = menus().singleOrNull() ?: error("expected one menu window: ${describeWindows()}")
        assertNotSame(old, now)
        assertEquals(b.rounded(), now.at())
        frames.filter { it.menu === old }.forEach { assertEquals(a.rounded(), it.at) }
        placed(frames.filter { it.menu !== old }).forEach { assertEquals(b.rounded(), it.at) }
    }

    @Test
    fun escapeClosesTheMenu() {
        rows()
        val menu = openAt(point(2, 100f))
        compose.runOnUiThread {
            deliverKey(menu, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))
            deliverKey(menu, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE))
        }
        compose.waitForIdle()
        assertTrue(menus().isEmpty())
    }

    @Test
    fun choosingAnItemClosesTheMenu() {
        rows()
        openAt(point(3, 100f))
        compose.onNodeWithText("Aç: ${names[3]}").performClick()
        compose.waitForIdle()
        assertEquals(listOf(names[3]), chosen)
        assertTrue(menus().isEmpty())
    }

    @Test
    fun aLongPressStillOpensTheMenu() {
        rows()
        compose.onNodeWithText(names[2]).performTouchInput { longClick(center) }
        compose.waitForIdle()
        assertEquals(describeWindows(), 1, menus().size)
        compose.onNodeWithText("Aç: ${names[2]}").assertExists()
        assertEquals(emptyList<String>(), clicked)
    }

    /**
     * A key to a window as Android's ViewRootImpl delivers it: to the views first, then, if none
     * handled it, to the unhandled-key listeners. With the mouse nothing in the menu may have the
     * focus, so Esc can take the second way.
     */
    private fun deliverKey(root: View, event: KeyEvent) {
        if (root.dispatchKeyEvent(event)) return
        View::class.java.getDeclaredMethod("dispatchUnhandledKeyEvent", KeyEvent::class.java)
            .apply { isAccessible = true }
            .invoke(root, event)
    }
}
