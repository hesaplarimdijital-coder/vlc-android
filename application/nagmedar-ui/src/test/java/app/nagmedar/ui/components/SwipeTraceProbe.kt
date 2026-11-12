package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A probe, not a test to keep. The first probe showed two things of 1.0.5's pattern: the last move's
 * coroutine stops the release's animation ("Mutation interrupted"), and of the moves that follow the
 * first two every other one gives the draggable no delta (8 moves of 6 px moved the box 20 px). This
 * one asks where the missing deltas go: the offset moved at once (as 1.0.6 does), with the draggable
 * inside the box's own moving layer and outside it.
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
    private fun Probe(draggableInside: Boolean) {
        val swipe = remember { SwipeAway() }
        val drag = Modifier.draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                val k = ++deltas
                swipe.dragBy(delta)
                log += "t${now()} delta#$k $delta -> offset ${swipe.offset}"
            },
            onDragStopped = { velocity ->
                log += "t${now()} onDragStopped: velocity $velocity, offset ${swipe.offset}"
                try {
                    swipe.settle(0f, spring(dampingRatio = 1f, stiffness = 400f), 0f)
                    log += "t${now()} settled at ${swipe.offset}"
                } catch (e: CancellationException) {
                    log += "t${now()} settle CANCELLED at ${swipe.offset}: ${e.message}"
                    throw e
                }
            },
        )
        val layer = Modifier.graphicsLayer { translationX = swipe.offset }
        Box(
            Modifier
                .testTag("probe")
                .size(360.dp, 48.dp)
                .then(if (draggableInside) layer.then(drag) else drag.then(layer))
                .background(Color.Gray),
        )
    }

    private fun trace(draggableInside: Boolean, step: Float, moves: Int) {
        compose.setContent { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Probe(draggableInside) } }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val node = compose.onNodeWithTag("probe")
        val rest = node.getBoundsInRoot().left.value
        log += "t${now()} draggable ${if (draggableInside) "inside" else "outside"} the moving layer: down, $moves moves of $step px, the lift with the last"
        node.performTouchInput {
            down(center)
            repeat(moves) { moveBy(Offset(step, 0f)) }
            up()
        }
        log += "t${now()} handed over: drawn at ${node.getBoundsInRoot().left.value - rest}"
        repeat(4) { i ->
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            log += "t${now()} after frame ${i + 1}: drawn at ${node.getBoundsInRoot().left.value - rest}"
        }
        fail(log.joinToString("\n"))
    }

    @Test
    fun movedAtOnceInsideItsLayerAShortSwipe() = trace(draggableInside = true, step = -6f, moves = 8)

    @Test
    fun movedAtOnceOutsideItsLayerAShortSwipe() = trace(draggableInside = false, step = -6f, moves = 8)

    @Test
    fun movedAtOnceInsideItsLayerAFling() = trace(draggableInside = true, step = -40f, moves = 5)

    @Test
    fun movedAtOnceOutsideItsLayerAFling() = trace(draggableInside = false, step = -40f, moves = 5)
}
