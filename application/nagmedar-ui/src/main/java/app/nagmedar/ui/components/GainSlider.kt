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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A vertical gain slider centred on 0 dB, for the equaliser's bands (S-906): [value] runs from
 * -[range] to +[range] in [step] steps; the fill goes from the 0 dB line to the value.
 *
 * - drag or press anywhere on the track; the wheel moves it a step a notch;
 * - Up/Down a step, Page Up/Down six, Home and End to the ends;
 * - a double click or tap, Delete and Backspace put it back to 0 dB;
 * - TalkBack reads [label] and [valueText] and can set it.
 */
@Composable
fun GainSlider(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    range: Double = 12.0,
    step: Double = 0.5,
    enabled: Boolean = true,
) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    var heightPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var lastPress by remember { mutableLongStateOf(0L) }
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)

    fun snap(db: Double): Double = (Math.round(db.coerceIn(-range, range) / step) * step) + 0.0 // + 0.0: no −0.0
    fun fromY(y: Float): Double = snap(range - (y / heightPx).coerceIn(0f, 1f) * 2 * range)
    fun set(db: Double) {
        val snapped = snap(db)
        if (snapped != latestValue) latestOnChange(snapped)
    }

    val fraction = ((value.coerceIn(-range, range) + range) / (2 * range)).toFloat()
    val emphasised = state.hovered || state.focused || dragging
    BoxWithConstraints(
        modifier = modifier
            .width(NagTheme.dimens.minTarget.coerceAtLeast(28.dp))
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction, enabled)
            .focusable(enabled, interaction)
            .semantics {
                contentDescription = label
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) setProgress { target -> set(target.coerceIn(0f, 1f) * 2 * range - range); true }
            }
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val target = when (event.key) {
                    Key.DirectionUp -> value + step
                    Key.DirectionDown -> value - step
                    Key.PageUp -> value + step * 6
                    Key.PageDown -> value - step * 6
                    Key.MoveHome -> range
                    Key.MoveEnd -> -range
                    Key.Delete, Key.Backspace -> 0.0
                    else -> return@onKeyEvent false
                }
                set(target)
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
                                val delta = change.scrollDelta.y
                                if (delta != 0f) {
                                    set(latestValue - (if (delta > 0) step else -step))
                                    change.consume()
                                }
                            }
                            PointerEventType.Press -> {
                                val now = change.uptimeMillis
                                if (now - lastPress < DOUBLE_PRESS_MS) {
                                    // a double click or tap: back to 0 dB
                                    set(0.0)
                                    lastPress = 0L
                                } else {
                                    lastPress = now
                                    dragging = true
                                    set(fromY(change.position.y))
                                }
                                change.consume()
                            }
                            PointerEventType.Move -> if (dragging && change.pressed) {
                                set(fromY(change.position.y))
                                change.consume()
                            }
                            PointerEventType.Release -> if (dragging) {
                                dragging = false
                                change.consume()
                            }
                            else -> Unit
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val track = if (emphasised) 5.dp else 3.dp
        val zeroY = maxHeight / 2
        val valueY = maxHeight * (1f - fraction)
        // the track
        Box(Modifier.width(track).fillMaxHeight().background(colors.borderStrong, RoundedCornerShape(50)))
        // the 0 dB line, across
        Box(Modifier.offset(y = zeroY - 0.5.dp).width(14.dp).height(1.dp).background(colors.textTertiary))
        // the fill from 0 dB to the value
        val top = if (valueY < zeroY) valueY else zeroY
        val length = abs((valueY - zeroY).value).dp
        if (length.value > 0f) {
            Box(
                Modifier
                    .offset(y = top)
                    .width(track)
                    .height(length)
                    .background(if (enabled) colors.accent else colors.textTertiary, RoundedCornerShape(50)),
            )
        }
        val thumb = if (emphasised) 14.dp else 10.dp
        Box(
            Modifier
                .offset(y = valueY - thumb / 2)
                .size(thumb)
                .background(if (enabled) colors.text else colors.textTertiary, CircleShape),
        )
        if (state.focused) {
            Box(Modifier.fillMaxWidth().fillMaxHeight().focusRing(state, RoundedCornerShape(NagTheme.dimens.radiusSmall)))
        }
    }
}

/** Two presses within this many milliseconds are a double press. */
private const val DOUBLE_PRESS_MS = 350L

/** [db] with its sign, to the half decibel: "+3", "−2,5", "0". */
fun gainText(db: Double, decimalSeparator: Char): String {
    val tenths = (abs(db) * 10).roundToInt()
    val whole = tenths / 10
    val fraction = tenths % 10
    val number = if (fraction == 0) "$whole" else "$whole$decimalSeparator$fraction"
    return when {
        tenths == 0 -> "0"
        db > 0 -> "+$number"
        else -> "−$number"
    }
}
