package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme

/** Position of a split, as the width of the start pane in dp; survives configuration changes. */
class SplitState(initialStartWidth: Float) {
    var startWidthDp by mutableFloatStateOf(initialStartWidth)

    companion object {
        val Saver: Saver<SplitState, Float> = Saver(save = { it.startWidthDp }, restore = { SplitState(it) })
    }
}

@Composable
fun rememberSplitState(initialStartWidth: Dp): SplitState =
    rememberSaveable(saver = SplitState.Saver) { SplitState(initialStartWidth.value) }

/**
 * Two panes side by side with a draggable divider (library list | details, player | panel).
 * The divider shows the resize cursor, can be dragged with mouse or finger, and moves with the
 * arrow keys when focused. Pane widths stay within [minStart] and [minEnd].
 */
@Composable
fun HorizontalSplitPane(
    state: SplitState,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    minStart: Dp = 240.dp,
    minEnd: Dp = 280.dp,
    dividerLabel: String = "",
) {
    val density = LocalDensity.current
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val visual = interaction.collectState()
    // pressed while a pointer is down on the divider: the drag detector reports no presses
    var held by remember { mutableStateOf(false) }
    val handleWidth = if (NagTheme.dimens.minTarget > 24.dp) 16.dp else 8.dp

    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Box(Modifier.layoutId("start")) { start() }
            Box(
                modifier = Modifier
                    .layoutId("divider")
                    .width(handleWidth)
                    .fillMaxHeight()
                    .pointerHoverIcon(PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW))
                    .hoverable(interaction)
                    .focusable(interactionSource = interaction)
                    .semantics {
                        contentDescription = dividerLabel
                        progressBarRangeInfo = ProgressBarRangeInfo(state.startWidthDp, 0f..10000f)
                        setProgress { target -> state.startWidthDp = target; true }
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> state.startWidthDp -= 16f
                            Key.DirectionRight -> state.startWidthDp += 16f
                            else -> return@onKeyEvent false
                        }
                        true
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            held = true
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                } while (event.changes.any { it.pressed })
                            } finally {
                                held = false
                            }
                        }
                    }
                    .pointerInput(state) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            state.startWidthDp += with(density) { dragAmount.toDp().value }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val active = visual.hovered || visual.focused || held
                Box(
                    Modifier
                        .width(if (active) 3.dp else 1.dp)
                        .fillMaxHeight()
                        .background(if (active) colors.accent else colors.border),
                )
            }
            Box(Modifier.layoutId("end")) { end() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val divider = measurables.first { it.layoutId == "divider" }.measure(Constraints.fixedHeight(height))
        val minStartPx = minStart.roundToPx()
        val minEndPx = minEnd.roundToPx()
        val wanted = state.startWidthDp.dp.roundToPx()
        val maxStart = (width - divider.width - minEndPx).coerceAtLeast(minStartPx)
        val startWidth = wanted.coerceIn(minStartPx, maxStart).coerceAtMost(width)
        val endWidth = (width - startWidth - divider.width).coerceAtLeast(0)
        val startPlaceable = measurables.first { it.layoutId == "start" }.measure(Constraints.fixed(startWidth, height))
        val endPlaceable = measurables.first { it.layoutId == "end" }.measure(Constraints.fixed(endWidth, height))
        layout(width, height) {
            startPlaceable.placeRelative(0, 0)
            divider.placeRelative(startWidth, 0)
            endPlaceable.placeRelative(startWidth + divider.width, 0)
        }
    }
}

/** Convenience for screens that only need a fixed-ratio row without a draggable divider. */
@Composable
fun TwoPane(start: @Composable () -> Unit, end: @Composable () -> Unit, startWidth: Dp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        Box(Modifier.width(startWidth).fillMaxHeight()) { start() }
        VerticalRule(Modifier.fillMaxHeight())
        Box(Modifier.weight(1f).fillMaxHeight()) { end() }
    }
}
