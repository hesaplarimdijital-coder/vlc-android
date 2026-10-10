package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme

/**
 * Horizontal slider for 0..1 values (seek bar, volume, equaliser bands).
 *
 * - drag or click anywhere on the track;
 * - mouse wheel and two-finger touchpad scroll move it by [wheelStep] per notch;
 * - arrow keys move it by [keyStep] when focused (Home/End jump to the ends);
 * - exposes progress semantics so TalkBack can read and set it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NagSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    wheelStep: Float = 0.02f,
    keyStep: Float = 0.01f,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) dragValue else value.coerceIn(0f, 1f)
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val emphasised = state.hovered || state.focused || dragging
    val touchHeight = dimens.minTarget.coerceAtLeast(24.dp)

    BoxWithConstraints(
        modifier = modifier
            .height(touchHeight)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction, enabled)
            .focusable(enabled, interaction)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                if (enabled) setProgress { target -> latestOnChange(target.coerceIn(0f, 1f)); true }
            }
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val target = when (event.key) {
                    Key.DirectionRight, Key.DirectionUp -> value + keyStep
                    Key.DirectionLeft, Key.DirectionDown -> value - keyStep
                    Key.PageUp -> value + keyStep * 10
                    Key.PageDown -> value - keyStep * 10
                    Key.MoveHome -> 0f
                    Key.MoveEnd -> 1f
                    else -> return@onKeyEvent false
                }
                latestOnChange(target.coerceIn(0f, 1f))
                true
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                val delta = change.scrollDelta.y.takeIf { it != 0f } ?: change.scrollDelta.x
                                if (delta != 0f) {
                                    latestOnChange((latestValue - delta * wheelStep).coerceIn(0f, 1f))
                                    change.consume()
                                }
                            }
                            PointerEventType.Press -> {
                                dragging = true
                                dragValue = (change.position.x / widthPx).coerceIn(0f, 1f)
                                latestOnChange(dragValue)
                                change.consume()
                            }
                            PointerEventType.Move -> if (dragging && change.pressed) {
                                dragValue = (change.position.x / widthPx).coerceIn(0f, 1f)
                                latestOnChange(dragValue)
                                change.consume()
                            }
                            PointerEventType.Release -> if (dragging) {
                                dragging = false
                                latestOnFinished?.invoke()
                                change.consume()
                            }
                            else -> Unit
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackHeight = if (emphasised) 5.dp else 3.dp
        val thumb = 12.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .background(colors.borderStrong, RoundedCornerShape(50)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .fillMaxHeight()
                    .background(if (enabled) colors.accent else colors.textTertiary, RoundedCornerShape(50)),
            )
        }
        if (emphasised && enabled) {
            Box(
                Modifier
                    .offset(x = maxWidth * shown - thumb / 2)
                    .size(thumb)
                    .background(colors.text, CircleShape),
            )
        }
        if (state.focused) {
            Box(Modifier.fillMaxWidth().height(touchHeight).focusRing(state, RoundedCornerShape(dimens.radiusSmall)))
        }
    }
}
