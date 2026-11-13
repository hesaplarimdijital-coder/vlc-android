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
 * window the last move and the lift together (the moves that waited for the next frame are handed
 * over just before the lift), and nothing of the app runs between the two. Up to 1.0.5 the bar
 * stayed where the finger left it, part faded, until its time was up: each move was applied in a
 * coroutine of its own (`launch { snapTo() }`), Compose's draggable starts `onDragStopped` before
 * anything that waits in the queue has run, and the last move's coroutine then stopped the animation
 * the release had just begun ("Mutation interrupted"). The owner's notice of 13 Nov; seen again on
 * the Android 17 emulator with a finger's raw events (the walk's notice-swipe pass).
 *
 * The coroutines of these tests wait in a queue, as they do on a device's main thread
 * ([StandardTestDispatcher]). The rule this module's tests are built on runs each coroutine the
 * moment it is launched, an order no device has (Compose 1.12 deprecates that rule for one that
 * queues): that is why the swipes of [ComponentsBehaviourTest] never showed this.
 *
 * Every swipe is made twice, with one move more the second time. Under these tests a bar that
 * moves under the finger takes only every other move of it after the first two (seen with a probe
 * that printed each move the bar was given), and 1.0.5's bar stayed aside only when the last move
 * was one it took: with one count or the other it is. Compose gives a node no move whose place
 * within the node has not changed (HitPathTracker, read in androidx's main branch); a bar that has
 * followed the finger exactly, under the even steps of a test, is there again at every second move.
 * A person's finger does not move in even steps, and on the emulator's touch screen, moved by raw
 * touch events, the bar followed every move.
 *
 * Each swipe also says that the finger had moved the bar when it let go: without that, a bar that
 * never left its place would pass for one that came back.
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

    /** What the test itself changed is told to the composition first; then [count] frames. */
    private fun frames(count: Int) {
        compose.waitForIdle()
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

    /** What a flung bar did: "gone", or where it still is. */
    private fun flung(text: String, moves: Int, step: Float, gone: () -> Boolean): String {
        val rest = left(text)
        // 40 px a frame: 2500 px/s, twice what a fling asks for
        compose.onNodeWithText(text).performTouchInput { letGoInMotion(step, moves) }
        // no frame has passed since the lift: the bar is where the finger had it
        val under = left(text) - rest
        if (abs(under) < 40f) return "the finger had not moved it ($under px from its place at the lift, $moves moves)"
        val ran = framesUntil(90, gone)
        return if (gone()) "gone" else "still there after $ran frames, ${left(text) - rest} px from its place ($moves moves)"
    }

    @Test
    fun onAPhoneAnUndoBarFlungAwayLeaves() {
        val state = UndoBarState()
        var undone = 0
        state.show(LONG, "Geri al") { undone++ }
        undoBar(MotionTier.Gesture, state)
        val first = flung(LONG, moves = 5, step = -40f) { state.messages.none { it.text == LONG } }
        compose.runOnUiThread { state.show(OTHER, "Geri al") { undone++ } }
        frames(3)
        val second = flung(OTHER, moves = 6, step = -40f) { state.messages.none { it.text == OTHER } }
        assertEquals("a flung bar leaves, whichever move the lift comes with", "gone, gone", "$first, $second")
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

    /** A short, slow swipe (6 px a frame: 375 px/s, some 30 px in all) let go in motion: the bar is back at its place. */
    private fun comesBack(tier: MotionTier) {
        val state = UndoBarState()
        state.show(LONG, "Geri al") {}
        undoBar(tier, state)
        val rest = left(LONG)
        for (moves in intArrayOf(8, 7)) {
            compose.onNodeWithText(LONG).performTouchInput { letGoInMotion(step = -6f, moves = moves) }
            // no frame has passed since the lift: the bar is where the finger had it
            val under = left(LONG) - rest
            assertTrue("the finger had moved the bar when it let go ($under px from its place, $tier, $moves moves)", under < -6f)
            frames(1)
            assertEquals("a short swipe leaves the bar", 1, state.messages.size)
            val ran = framesUntil(120) { abs(left(LONG) - rest) < 0.5f }
            assertEquals("the bar is back at its place after $ran frames ($tier, $moves moves)", rest, left(LONG), 0.5f)
        }
        assertEquals(1, state.messages.size)
    }

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
        // long ones: they stay seven seconds, and the frames below are three seconds at most
        Notices.show(compose.activity, NOTICE, long = true)
        frames(3)
        compose.mainClock.autoAdvance = false
        val shown = { text: String -> Notices.shown.value.any { it.text == text } }
        val first = flung(NOTICE, moves = 5, step = 40f) { !shown(NOTICE) }
        Notices.show(compose.activity, OTHER, long = true)
        frames(3)
        val second = flung(OTHER, moves = 6, step = 40f) { !shown(OTHER) }
        assertEquals("a flung notice leaves, whichever move the lift comes with", "gone, gone", "$first, $second")
        assertTrue(Notices.shown.value.isEmpty())
    }

    private companion object {
        const val LONG = "Uzun bir iletinin tamamı: on iki parça sıradan kaldırıldı"
        const val OTHER = "Bir ileti daha: dört parça çalma listesinden çıkarıldı"
        const val NOTICE = "Kütüphane yeniden taranıyor; liste bu arada olduğu gibi kalır"
    }
}
