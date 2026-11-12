package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A probe, not a test to keep: what the pattern of 1.0.5's swipeToDismiss (an Animatable moved with
 * `launch { snapTo() }`, animated from onDragStopped) does, step by step, when the last move and the
 * lift are handed over in one go and the coroutines wait in a queue. Every scenario fails with its
 * trace as the message.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-mdpi")
class SwipeTraceProbe {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(effectContext = StandardTestDispatcher())

    private val log = mutableListOf<String>()
    private var deltas = 0

    private fun now() = compose.mainClock.currentTime

    @Composable
    private fun Probe() {
        val swipe = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        Box(
            Modifier
                .testTag("probe")
                .size(360.dp, 48.dp)
                .graphicsLayer { translationX = swipe.value }
                .background(Color.Gray)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val k = ++deltas
                        log += "t${now()} delta#$k $delta (value ${swipe.value})"
                        scope.launch {
                            val to = swipe.value + delta
                            log += "t${now()}   snap#$k starts: value ${swipe.value}, to $to"
                            try {
                                swipe.snapTo(to)
                                log += "t${now()}   snap#$k done: value ${swipe.value}"
                            } catch (e: CancellationException) {
                                log += "t${now()}   snap#$k CANCELLED (value ${swipe.value}): ${e.message}"
                                throw e
                            }
                        }
                    },
                    onDragStopped = { velocity ->
                        log += "t${now()} onDragStopped: velocity $velocity, value ${swipe.value}"
                        try {
                            swipe.animateTo(0f, spring(dampingRatio = 1f, stiffness = 400f)) { log += "t${now()}   settle frame: $value" }
                            log += "t${now()} settled at ${swipe.value}"
                        } catch (e: CancellationException) {
                            log += "t${now()} settle CANCELLED at ${swipe.value}: ${e.message}"
                            throw e
                        }
                    },
                ),
        )
    }

    private fun probe() {
        compose.setContent { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Probe() } }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun framesAfter(count: Int, at: () -> Float) {
        repeat(count) { i ->
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            log += "t${now()} after frame ${i + 1}: at ${at()}"
        }
    }

    @Test
    fun aShortSwipeInOneGo() {
        probe()
        val node = compose.onNodeWithTag("probe")
        val rest = node.getBoundsInRoot().left.value
        log += "t${now()} the gesture: down, 8 moves of -6 px, the lift with the last"
        node.performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(-6f, 0f)) }
            up()
        }
        log += "t${now()} the gesture was handed over: at ${node.getBoundsInRoot().left.value - rest}"
        framesAfter(14) { node.getBoundsInRoot().left.value - rest }
        fail(log.joinToString("\n"))
    }

    @Test
    fun aShortSwipeTheLiftAFrameLater() {
        probe()
        val node = compose.onNodeWithTag("probe")
        val rest = node.getBoundsInRoot().left.value
        log += "t${now()} the gesture: down, 8 moves of -6 px, the lift 16 ms after the last"
        node.performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(-6f, 0f)) }
            advanceEventTime(16)
            up()
        }
        log += "t${now()} the gesture was handed over: at ${node.getBoundsInRoot().left.value - rest}"
        framesAfter(14) { node.getBoundsInRoot().left.value - rest }
        fail(log.joinToString("\n"))
    }

    @Test
    fun aFlingInOneGo() {
        probe()
        val node = compose.onNodeWithTag("probe")
        val rest = node.getBoundsInRoot().left.value
        log += "t${now()} the gesture: down, 5 moves of -40 px, the lift with the last"
        node.performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(-40f, 0f)) }
            up()
        }
        log += "t${now()} the gesture was handed over: at ${node.getBoundsInRoot().left.value - rest}"
        framesAfter(14) { node.getBoundsInRoot().left.value - rest }
        fail(log.joinToString("\n"))
    }

    /** 1.0.5's own undo bar under the same short swipe: where it is, frame by frame. */
    @Test
    fun theUndoBarOf105UnderTheShortSwipe() {
        val state = UndoBarState()
        val text = "Uzun bir iletinin tamamı: on iki parça sıradan kaldırıldı"
        state.show(text, "Geri al") {}
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides MotionTier.Timed) {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                        UndoBarHost(state, Modifier.width(360.dp))
                    }
                }
            }
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val node = compose.onNodeWithText(text)
        val rest = node.getBoundsInRoot().left.value
        node.performTouchInput { down(center) }
        log += "t${now()} down: at ${node.getBoundsInRoot().left.value - rest}"
        repeat(7) { i ->
            node.performTouchInput { moveBy(Offset(-6f, 0f)) }
            log += "t${now()} move ${i + 1} handed over: at ${node.getBoundsInRoot().left.value - rest}"
        }
        node.performTouchInput {
            moveBy(Offset(-6f, 0f))
            up()
        }
        log += "t${now()} move 8 and the lift handed over in one go: at ${node.getBoundsInRoot().left.value - rest}"
        framesAfter(14) { node.getBoundsInRoot().left.value - rest }
        fail(log.joinToString("\n"))
    }
}
