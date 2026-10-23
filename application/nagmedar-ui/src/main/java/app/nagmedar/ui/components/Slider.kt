package app.nagmedar.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme
import kotlin.math.roundToInt

/**
 * A mark on a seek bar (S-1104: a chapter's start): where it is, 0..1 of the track, and what it is called. Marks are drawn
 * as ticks; under the pointer or at the thumb of the focused bar the bar names the mark.
 */
@Immutable
data class SliderMark(val fraction: Float, val label: String)

/** The mark nearest to [at] (0..1) within [reach] of it, or null. */
fun List<SliderMark>.nearest(at: Float, reach: Float): SliderMark? =
    minByOrNull { kotlin.math.abs(it.fraction - at) }?.takeIf { kotlin.math.abs(it.fraction - at) <= reach }

/** The last mark at or before [at] (0..1): the chapter playing there; null before the first. */
fun List<SliderMark>.playingAt(at: Float): SliderMark? = lastOrNull { it.fraction <= at + MARK_EPSILON }

private const val MARK_EPSILON = 0.0005f

/** How far from a mark the pointer still means it, on the track. */
internal val MarkReach = 8.dp

/**
 * The label that floats over a timeline: [text] centred on [anchorPx], kept within [widthPx], above the track. A long
 * name is cut short.
 */
@Composable
internal fun BoxScope.TimelineLabel(text: String, anchorPx: Float, widthPx: Float) {
    val colors = NagTheme.colors
    var labelSize by remember { mutableStateOf(IntSize.Zero) }
    Text(
        text,
        style = NagTheme.type.mono,
        color = colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(Alignment.TopStart)
            .widthIn(max = 320.dp)
            .onSizeChanged { labelSize = it }
            .offset {
                val x = (anchorPx - labelSize.width / 2f).roundToInt()
                IntOffset(x.coerceIn(0, (widthPx.roundToInt() - labelSize.width).coerceAtLeast(0)), -labelSize.height - 2.dp.roundToPx())
            }
            .background(colors.surfaceRaised, RoundedCornerShape(4.dp))
            .border(1.dp, colors.borderStrong, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Horizontal slider for 0..1 values (seek bar, volume, equaliser bands).
 *
 * - drag or click anywhere on the track;
 * - mouse wheel and two-finger touchpad scroll move it by [wheelStep] per notch;
 * - arrow keys move it by [keyStep] when focused (Home/End jump to the ends);
 * - exposes progress semantics so TalkBack can read and set it;
 * - [marks] (chapters) are ticks on the track; the pointer near one, or the focus on the bar, names it.
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
    /** With a pointer, what the track means under it ("2:09" on a seek bar), shown above it. */
    hoverLabel: ((Float) -> String)? = null,
    /** S-1104: the chapters' starts; the bar names the one under the pointer, or the one playing while it has the focus. */
    marks: List<SliderMark> = emptyList(),
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value) }
    var hoverAt by remember { mutableStateOf<Float?>(null) }
    val shown = if (dragging) dragValue else value.coerceIn(0f, 1f)
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val emphasised = state.hovered || state.focused || dragging
    val touchHeight = dimens.minTarget.coerceAtLeast(24.dp)
    val reach = with(LocalDensity.current) { MarkReach.toPx() } / widthPx
    val playingMark = if (marks.isEmpty()) null else marks.playingAt(shown)

    BoxWithConstraints(
        modifier = modifier
            .height(touchHeight)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction, enabled)
            .focusable(enabled, interaction)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                playingMark?.let { stateDescription = it.label }
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
                            } else if (!change.pressed) {
                                hoverAt = (change.position.x / widthPx).coerceIn(0f, 1f)
                            }
                            PointerEventType.Enter -> hoverAt = (change.position.x / widthPx).coerceIn(0f, 1f)
                            PointerEventType.Exit -> hoverAt = null
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
        val motion = NagTheme.motion
        val trackHeight by animateDpAsState(if (emphasised) 5.dp else 3.dp, motion.fastSpatial(), label = "trackHeight")
        // the thumb springs in on hover, focus or touch and swells a little while dragged (Desktop Kit 0.2.0)
        val thumbScale = animateFloatAsState(
            when {
                !emphasised || !enabled -> 0f
                dragging -> 1.25f
                else -> 1f
            },
            motion.fastSpatial(),
            label = "thumbScale",
        )
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
        if (marks.isNotEmpty()) {
            val tick = colors.text.copy(alpha = 0.6f)
            Canvas(Modifier.fillMaxWidth().height(touchHeight)) {
                val width = 2.dp.toPx()
                val height = (if (emphasised) 11.dp else 9.dp).toPx()
                val apart = 3.dp.toPx()
                var last = -apart
                for (mark in marks) {
                    // a chapter at 0 is the start of the track, not a mark on it
                    if (mark.fraction <= 0f) continue
                    val x = size.width * mark.fraction.coerceIn(0f, 1f)
                    if (x - last < apart) continue
                    last = x
                    drawRect(tick, Offset(x - width / 2, (size.height - height) / 2), Size(width, height))
                }
            }
        }
        if (thumbScale.value > 0.01f) {
            Box(
                Modifier
                    .offset(x = maxWidth * shown - thumb / 2)
                    .size(thumb)
                    .graphicsLayer {
                        scaleX = thumbScale.value
                        scaleY = thumbScale.value
                    }
                    .background(colors.text, CircleShape),
            )
        }
        if (state.focused) {
            Box(Modifier.fillMaxWidth().height(touchHeight).focusRing(state, RoundedCornerShape(dimens.radiusSmall)))
        }
        val at = hoverAt
        if (enabled && !dragging) {
            // under the pointer: its time, and the mark when it is near one; the focused bar names the chapter playing
            val mark = if (at != null) marks.nearest(at, reach) else if (state.focused) playingMark else null
            val anchor = if (at != null) mark?.fraction ?: at else if (mark != null) shown else null
            if (anchor != null) {
                val text = listOfNotNull(hoverLabel?.invoke(anchor), mark?.label).joinToString(" · ")
                if (text.isNotEmpty()) TimelineLabel(text, widthPx * anchor, widthPx)
            }
        }
    }
}
