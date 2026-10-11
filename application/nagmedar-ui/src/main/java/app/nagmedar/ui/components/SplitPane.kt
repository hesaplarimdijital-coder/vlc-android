package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
 * Two panes side by side with a draggable divider (player | panel). The start pane keeps the
 * width it is dragged to, within [minStart] and the room left for [minEnd].
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
    BoxWithConstraints(modifier.fillMaxSize()) {
        val handle = paneHandleWidth()
        val maxStart = (maxWidth - handle - minEnd).coerceAtLeast(minStart)
        val startWidth = state.startWidthDp.dp.coerceIn(minStart, maxStart)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(startWidth).fillMaxHeight()) { start() }
            PaneDivider(
                widthDp = startWidth.value,
                range = minStart.value..maxStart.value,
                // several moves can arrive in one frame: add to the state, not to this composition's width
                onResize = { towardsEnd ->
                    state.startWidthDp = (state.startWidthDp.coerceIn(minStart.value, maxStart.value) + towardsEnd).coerceIn(minStart.value, maxStart.value)
                },
                onSet = { state.startWidthDp = it.coerceIn(minStart.value, maxStart.value) },
                label = dividerLabel,
                modifier = Modifier.width(handle),
            )
            Box(Modifier.weight(1f).fillMaxHeight()) { end() }
        }
    }
}

/** Width of a divider's grab area: wider for fingers than for a mouse. */
@Composable
@ReadOnlyComposable
fun paneHandleWidth(): Dp = if (NagTheme.dimens.minTarget > 24.dp) 16.dp else 8.dp

/**
 * The draggable edge between two panes (S-303): the resize cursor, a hairline that becomes an
 * accent bar while hovered, focused or held, dragging with mouse or finger, the arrow keys when
 * focused, and a slider for screen readers that reports [widthDp] within [range]. Movements
 * arrive in [onResize] in dp towards the layout's end edge (to the left in right-to-left
 * layouts); [onResizeEnd] follows each drag, key press and accessibility change, for saving.
 * [line] places the hairline in the grab area.
 */
@Composable
fun PaneDivider(
    widthDp: Float,
    range: ClosedFloatingPointRange<Float>,
    onResize: (towardsEnd: Float) -> Unit,
    onSet: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onResizeEnd: () -> Unit = {},
    line: Alignment = Alignment.Center,
) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val visual = interaction.collectState()
    // pressed while a pointer is down on the divider: the drag detector reports no presses
    var held by remember { mutableStateOf(false) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val resize by rememberUpdatedState(onResize)
    val resizeEnd by rememberUpdatedState(onResizeEnd)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW))
            .hoverable(interaction)
            .focusable(interactionSource = interaction)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(widthDp.coerceIn(range), range)
                setProgress { target ->
                    onSet(target)
                    resizeEnd()
                    true
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = when (event.key) {
                    Key.DirectionLeft -> -KEY_STEP_DP
                    Key.DirectionRight -> KEY_STEP_DP
                    else -> return@onKeyEvent false
                }
                resize(if (rtl) -step else step)
                resizeEnd()
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
            .pointerInput(rtl) {
                detectHorizontalDragGestures(onDragEnd = { resizeEnd() }, onDragCancel = { resizeEnd() }) { change, dragAmount ->
                    change.consume()
                    val dp = dragAmount.toDp().value
                    resize(if (rtl) -dp else dp)
                }
            },
        contentAlignment = line,
    ) {
        val active = visual.hovered || visual.focused || held
        Box(
            Modifier
                .width(if (active) 3.dp else 1.dp)
                .fillMaxHeight()
                .background(if (active) colors.accent else colors.border),
        )
    }
}

private const val KEY_STEP_DP = 16f

/** Convenience for screens that only need a fixed-ratio row without a draggable divider. */
@Composable
fun TwoPane(start: @Composable () -> Unit, end: @Composable () -> Unit, startWidth: Dp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        Box(Modifier.width(startWidth).fillMaxHeight()) { start() }
        VerticalRule(Modifier.fillMaxHeight())
        Box(Modifier.weight(1f).fillMaxHeight()) { end() }
    }
}
