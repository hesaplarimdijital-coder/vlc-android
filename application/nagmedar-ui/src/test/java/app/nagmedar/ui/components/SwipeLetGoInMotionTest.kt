package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.videolan.tools.Notices
import kotlin.math.abs

/**
 * 1.0.6: a notice or an undo bar let go while the finger is still moving. A phone then gives the
 * window the last move and the lift together (the batch of moves that waited for the next frame is
 * handed over just before the lift), and nothing of the app runs between the two. Up to 1.0.5 the
 * bar stayed where the finger left it, half faded, until its time was up: each move was applied in a
 * coroutine of its own, the last of them ran after the release had begun to carry the bar out (or
 * back) and stopped that.
 *
 * The coroutines of these tests wait in a queue, as they do on a device's main thread
 * ([StandardTestDispatcher]). The default of Compose's tests runs each coroutine the moment it is
 * launched, an order no device has: that is why the swipes of [ComponentsBehaviourTest] never showed
 * this.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-mdpi")
class SwipeLetGoInMotionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(effectContext = StandardTestDispatcher())

    @After
    fun noNoticesLeft() {
        Notices.shown.value.forEach { Notices.dismiss(it.id) }
    }

    private fun frames(count: Int) {
        repeat(count) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
    }

    private fun undoBar(tier: MotionTier, state: UndoBarState) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides tier) {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                        UndoBarHost(state, Modifier.width(360.dp))
                    }
                }
            }
        }
        frames(3)
        compose.mainClock.autoAdvance = false
    }

    /**
     * A finger down on the bar, [moves] moves of [step] px a frame apart, and the lift with the last
     * of them: no time passes between the two, so they are handed to the window in one go.
     */
    private fun TouchInjectionScope.letGoInMotion(step: Float, moves: Int) {
        down(center)
        repeat(moves) { moveBy(Offset(step, 0f)) }
        up()
    }

    private fun left(text: String): Float = compose.onNodeWithText(text).getBoundsInRoot().left.value

    /** Runs frames until [done] or [most] of them have passed; how many it ran. */
    private fun framesUntil(most: Int, done: () -> Boolean): Int {
        var ran = 0
        while (ran < most && !done()) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            ran++
        }
        return ran
    }

    @Test
    fun onAPhoneAnUndoBarFlungAwayLeaves() {
        val state = UndoBarState()
        var undone = 0
        state.show(LONG, "Geri al") { undone++ }
        undoBar(MotionTier.Gesture, state)
        val rest = left(LONG)
        // 40 px a frame: 2500 px/s, twice what a fling asks for
        compose.onNodeWithText(LONG).performTouchInput { letGoInMotion(step = -40f, moves = 5) }
        val ran = framesUntil(90) { state.messages.isEmpty() }
        assertTrue(
            "the bar left once it was flung (after $ran frames it is ${stillAt(state, LONG, rest)})",
            state.messages.isEmpty(),
        )
        assertEquals("a swipe does not take the change back", 0, undone)
    }

    @Test
    fun onAPhoneAnUndoBarLetGoShortOfTheWayComesBack() {
        comesBack(MotionTier.Gesture)
    }

    @Test
    fun widerUpAnUndoBarLetGoShortOfTheWayComesBack() {
        comesBack(MotionTier.Timed)
    }

    @Test
    fun withReducedMotionAnUndoBarLetGoShortOfTheWayIsBack() {
        comesBack(MotionTier.Reduced)
    }

    /** A short, slow swipe (6 px a frame: 375 px/s, 40 px in all) let go in motion: the bar is back at its place. */
    private fun comesBack(tier: MotionTier) {
        val state = UndoBarState()
        state.show(LONG, "Geri al") {}
        undoBar(tier, state)
        val rest = left(LONG)
        compose.onNodeWithText(LONG).performTouchInput { letGoInMotion(step = -6f, moves = 8) }
        frames(1)
        assertEquals("a short swipe leaves the bar", 1, state.messages.size)
        val ran = framesUntil(120) { abs(left(LONG) - rest) < 0.5f }
        assertEquals("the bar is back at its place after $ran frames ($tier)", rest, left(LONG), 0.5f)
        assertEquals(1, state.messages.size)
    }

    private fun stillAt(state: UndoBarState, text: String, rest: Float): String =
        if (state.messages.isEmpty()) "gone" else "still there, ${left(text) - rest} px from its place"

    @Test
    fun onAPhoneANoticeFlungAwayLeaves() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides MotionTier.Gesture) {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) { NoticeHost() }
                }
            }
        }
        frames(3)
        // a long one: it stays seven seconds, and the frames below are a second and a half
        Notices.show(compose.activity, NOTICE, long = true)
        frames(3)
        compose.mainClock.autoAdvance = false
        val rest = left(NOTICE)
        compose.onNodeWithText(NOTICE).performTouchInput { letGoInMotion(step = 40f, moves = 5) }
        val ran = framesUntil(90) { Notices.shown.value.isEmpty() }
        val seen = if (Notices.shown.value.isEmpty()) "gone" else "still there, ${left(NOTICE) - rest} px from its place"
        assertTrue("the notice left once it was flung (after $ran frames it is $seen)", Notices.shown.value.isEmpty())
    }

    private companion object {
        const val LONG = "Uzun bir iletinin tamamı: on iki parça sıradan kaldırıldı"
        const val NOTICE = "Kütüphane yeniden taranıyor; liste bu arada olduğu gibi kalır"
    }
}
