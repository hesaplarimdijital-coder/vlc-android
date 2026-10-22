package app.nagmedar.ui.playback

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.Waveform
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HoverTooltip
import app.nagmedar.ui.components.MarkReach
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SliderMark
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.nearest
import app.nagmedar.ui.components.playingAt
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where a [ProgressTimeline] sits: it sets the waveform's height, the thumb and where the times go. */
enum class TimelineStyle {
    /** The desktop music view's now playing pane: a soft waveform, the times under it. */
    Panel,

    /** The phone's player: a taller waveform for the thumb, the times under it. */
    Phone,

    /** The transport bar: a plain track between the elapsed and the remaining time. */
    Bar,
}

/**
 * The player's progress, as the music apps draw it: a rounded track, the part played in the accent, over the track's
 * [waveform] drawn soft (its loudness, bar by bar); the elapsed and the remaining time in even figures. Between the
 * service's readings ([progress], twice a second) the position moves on at the speed of [rate], a pixel at a time, so
 * it glides instead of stepping (with "Remove animations", with the readings alone). The thumb grows under the
 * pointer, under a finger and with the focus; dragging shows the time (and the chapter) in a bubble above it and
 * seeks where it is let go, with a light tick of the phone each time it crosses a chapter's start. Left and Right
 * step five seconds, Home and End go to the ends; screen readers read and set it as a seek bar. [marks] are the
 * chapters' starts (S-1104), drawn as ticks and named under the pointer, in the bubble and with the focus.
 */
@Composable
fun ProgressTimeline(
    progress: () -> Float,
    lengthMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    rate: Float = 1f,
    waveform: Waveform? = null,
    marks: List<SliderMark> = emptyList(),
    style: TimelineStyle = TimelineStyle.Panel,
    label: String = stringResource(R.string.nag_position),
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val motion = NagTheme.motion
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val smooth = remember { SmoothProgress() }
    val latestProgress = rememberUpdatedState(progress)
    val latestLength by rememberUpdatedState(lengthMs)
    var widthPx by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(smooth) { snapshotFlow { latestProgress.value() }.collect { smooth.report(it.coerceIn(0f, 1f), latestLength) } }
    LaunchedEffect(playing, lengthMs, rate, widthPx, motion.reduced) {
        if (playing && !motion.reduced) smooth.glide(lengthMs, rate, widthPx)
    }
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var hoverAt by remember { mutableStateOf<Float?>(null) }
    val latestSeek by rememberUpdatedState(onSeek)
    val latestMarks by rememberUpdatedState(marks)
    val shown: () -> Float = { if (dragging) dragValue else smooth.fraction(playing, lengthMs, rate) }
    val seek: (Float) -> Unit = { target ->
        val to = target.coerceIn(0f, 1f)
        smooth.jumpTo(to)
        latestSeek(to)
    }
    val latestSeekTo by rememberUpdatedState(seek)

    // the times change once a second, not on every frame
    val elapsedSeconds by remember(lengthMs, playing, rate) { derivedStateOf { if (lengthMs > 0L) (shown() * lengthMs / 1000f).toLong() else 0L } }
    val elapsedMs = elapsedSeconds * 1000L
    val position = stringResource(R.string.nag_music_position, clock(elapsedMs), clock(lengthMs))
    val playingMark = if (marks.isEmpty() || lengthMs <= 0L) null else marks.playingAt(elapsedMs.toFloat() / lengthMs)

    val active = dragging || state.hovered || state.focused
    val bar = style == TimelineStyle.Bar
    val thumbTarget = when {
        dragging -> if (pointer) 7.dp else 10.dp
        active -> if (pointer) 6.dp else 8.dp
        pointer -> 0.dp
        else -> if (bar) 5.dp else 6.dp
    }
    val thumb by animateFloatAsState(with(density) { thumbTarget.toPx() }, motion.standard(NagMotion.SHORT), label = "thumb")
    val trackTarget = if (active) 6.dp else if (bar) 3.dp else 4.dp
    val track by animateFloatAsState(with(density) { trackTarget.toPx() }, motion.standard(NagMotion.SHORT), label = "track")
    val waveHeight = when (style) {
        TimelineStyle.Panel -> 36.dp
        TimelineStyle.Phone -> 48.dp
        TimelineStyle.Bar -> 0.dp
    }
    val height = max(waveHeight.value, dimens.minTarget.coerceAtLeast(24.dp).value).dp
    val barWidth = with(density) { 2.dp.toPx() }
    val barGap = with(density) { 2.dp.toPx() }
    val count = max(1, ((widthPx + barGap) / (barWidth + barGap)).toInt())
    val levels = remember(waveform, count) { waveform?.takeIf { it.size > 0 }?.let { softLevels(it, count) } }
    val reach = with(density) { MarkReach.toPx() } / widthPx
    val step = if (lengthMs > 0L) SEEK_STEP_MS.toFloat() / lengthMs else 0.02f

    val played = colors.accent
    val rest = colors.borderStrong
    val waveRest = colors.textTertiary.copy(alpha = 0.22f)
    val wavePlayed = colors.accent.copy(alpha = 0.34f)
    val waveAhead = colors.accent.copy(alpha = 0.18f)
    val tick = colors.text.copy(alpha = 0.55f)
    val tickNear = colors.text
    val thumbColor = colors.text
    val halo = colors.accent.copy(alpha = 0.22f)

    val timeline: @Composable (Modifier) -> Unit = { boxModifier ->
        Box(boxModifier.height(height)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .testTag("progress-timeline")
                    .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .hoverable(interaction)
                    .focusable(interactionSource = interaction)
                    .focusRing(state, RoundedCornerShape(dimens.radiusSmall))
                    .semantics {
                        contentDescription = label
                        stateDescription = playingMark?.let { "$position · ${it.label}" } ?: position
                        progressBarRangeInfo = ProgressBarRangeInfo(if (lengthMs > 0L) (elapsedMs.toFloat() / lengthMs).coerceIn(0f, 1f) else 0f, 0f..1f)
                        setProgress { target -> latestSeekTo(target); true }
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val now = shown()
                        val target = when (event.key) {
                            Key.DirectionLeft -> now - step
                            Key.DirectionRight -> now + step
                            Key.MoveHome -> 0f
                            Key.MoveEnd -> 1f
                            else -> return@onKeyEvent false
                        }
                        latestSeekTo(target)
                        true
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            var last = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                val at = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        // a right or a middle click is the menu's, not a seek
                                        if (change.type == PointerType.Mouse && !event.buttons.isPrimaryPressed) continue
                                        dragging = true
                                        dragValue = at
                                        last = at
                                        change.consume()
                                    }
                                    PointerEventType.Move -> if (dragging && change.pressed) {
                                        // a light tick as the finger crosses a chapter's start
                                        val low = min(last, at)
                                        val high = max(last, at)
                                        if (latestMarks.any { it.fraction > 0f && it.fraction > low && it.fraction <= high }) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        last = at
                                        dragValue = at
                                        change.consume()
                                    } else if (!change.pressed) {
                                        hoverAt = at
                                    }
                                    PointerEventType.Release -> if (dragging) {
                                        dragging = false
                                        latestSeekTo(dragValue)
                                        change.consume()
                                    }
                                    PointerEventType.Enter -> if (!change.pressed) hoverAt = at
                                    PointerEventType.Exit -> hoverAt = null
                                    else -> Unit
                                }
                            }
                        }
                    },
            ) {
                val fraction = shown()
                val w = size.width
                val middle = size.height / 2
                val playedX = w * fraction
                val hover = hoverAt?.takeIf { pointer && !dragging }
                val hoverX = hover?.let { w * it }
                // the waveform, soft, both ways from the track
                if (levels != null) {
                    val room = size.height - track
                    for (i in levels.indices) {
                        val x = i * (barWidth + barGap)
                        if (x > w) break
                        val h = track + levels[i] * room
                        val centre = x + barWidth / 2
                        val color = when {
                            centre <= playedX -> wavePlayed
                            hoverX != null && centre <= hoverX -> waveAhead
                            else -> waveRest
                        }
                        drawRoundRect(color, Offset(x, middle - h / 2), Size(barWidth, h), CornerRadius(barWidth / 2))
                    }
                }
                // the track, what was played, and under the pointer what a click would skip
                val top = middle - track / 2
                val radius = CornerRadius(track / 2)
                drawRoundRect(rest, Offset(0f, top), Size(w, track), radius)
                if (hoverX != null && hoverX > playedX) drawRoundRect(played.copy(alpha = 0.35f), Offset(0f, top), Size(hoverX, track), radius)
                if (playedX > 0f) drawRoundRect(played, Offset(0f, top), Size(max(playedX, track), track), radius)
                // the chapters' starts
                val tickWidth = 2.dp.toPx()
                val tickHeight = track + 8.dp.toPx()
                val apart = 3.dp.toPx()
                var lastTick = -apart
                val near = (hover ?: if (dragging) dragValue else null)?.let { latestMarks.nearest(it, reach) }
                for (mark in latestMarks) {
                    if (mark.fraction <= 0f) continue
                    val x = w * mark.fraction.coerceIn(0f, 1f)
                    if (x - lastTick < apart) continue
                    lastTick = x
                    drawRoundRect(
                        if (mark == near) tickNear else tick,
                        Offset(x - tickWidth / 2, middle - tickHeight / 2),
                        Size(tickWidth, tickHeight),
                        CornerRadius(tickWidth / 2),
                    )
                }
                // the thumb, with a halo while it is held
                if (thumb > 0.5f) {
                    val x = playedX.coerceIn(thumb, (w - thumb).coerceAtLeast(thumb))
                    if (dragging) drawCircle(halo, thumb * 2f, Offset(x, middle))
                    drawCircle(thumbColor, thumb, Offset(x, middle))
                }
            }
            // over the track: the time held (dragging) or pointed at, and its chapter; with the focus, the chapter playing
            val anchor = when {
                dragging -> dragValue
                hoverAt != null && pointer -> hoverAt
                state.focused && playingMark != null -> elapsedMs.toFloat() / lengthMs.coerceAtLeast(1L)
                else -> null
            }
            if (anchor != null && lengthMs > 0L) {
                val near = if (!dragging && hoverAt != null) marks.nearest(anchor, reach) else null
                val at = near?.fraction ?: anchor
                val chapter = near ?: if (marks.isEmpty()) null else marks.playingAt(at)
                val text = listOfNotNull(clock((latestLength * at).toLong()), chapter?.label).joinToString(" · ")
                TimeBubble(text, widthPx * at, widthPx)
            }
        }
    }

    val timeStyle = (if (style == TimelineStyle.Phone) NagTheme.type.labelSmall else NagTheme.type.caption).tabular()
    val elapsedColor = if (dragging) colors.accentText else colors.textSecondary
    val elapsedText = clock(elapsedMs)
    val remainingText = if (lengthMs > 0L) stringResource(R.string.nag_time_remaining, clock((lengthMs - elapsedMs).coerceAtLeast(0L))) else ""
    if (bar) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(elapsedText, style = timeStyle, color = elapsedColor, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = TIME_WIDTH))
            Spacer(Modifier.width(dimens.gapS))
            timeline(Modifier.weight(1f))
            Spacer(Modifier.width(dimens.gapS))
            Text(remainingText, style = timeStyle, color = colors.textSecondary, maxLines = 1, modifier = Modifier.widthIn(min = TIME_WIDTH))
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            timeline(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(elapsedText, style = timeStyle, color = elapsedColor, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(remainingText, style = timeStyle, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

/** Room for "1:02:03" beside the bar's track, so that it does not move as the figures change. */
private val TIME_WIDTH = 44.dp

/** A key step of the timeline. */
private const val SEEK_STEP_MS = 5_000L

/** "0:00", "1:42", "1:02:03". */
private fun clock(millis: Long): String = if (millis <= 0L) "0:00" else formatDuration(millis)

/** Figures of even width, so that a running time does not shift. */
private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** The time (and the chapter) over the timeline, centred on [anchorPx], kept within [widthPx]. */
@Composable
private fun BoxScope.TimeBubble(text: String, anchorPx: Float, widthPx: Float) {
    val colors = NagTheme.colors
    var bubble by remember { mutableStateOf(IntSize.Zero) }
    Text(
        text,
        style = NagTheme.type.label.tabular(),
        color = colors.onInverseSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(Alignment.TopStart)
            .widthIn(max = 320.dp)
            .onSizeChanged { bubble = it }
            .offset {
                val x = (anchorPx - bubble.width / 2f).roundToInt()
                IntOffset(x.coerceIn(0, (widthPx.roundToInt() - bubble.width).coerceAtLeast(0)), -bubble.height - 4.dp.roundToPx())
            }
            .background(colors.inverseSurface, RoundedCornerShape(NagTheme.dimens.radiusPill))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("progress-bubble"),
    )
}

/**
 * Bar heights 0..1 for [count] bars from the waveform's buckets: the loudness (RMS) with a share of the peak, each
 * bar averaged with its neighbours, so that the outline is soft.
 */
private fun softLevels(waveform: Waveform, count: Int): FloatArray {
    val raw = FloatArray(count) { index ->
        val from = index * waveform.size / count
        val to = max(from + 1, (index + 1) * waveform.size / count).coerceAtMost(waveform.size)
        var peak = 0f
        var rms = 0f
        for (b in from until to) {
            peak = max(peak, waveform.peak(b))
            rms += waveform.rms(b)
        }
        (0.65f * min(1f, rms / (to - from) * RMS_SCALE) + 0.35f * peak).coerceIn(0f, 1f)
    }
    return FloatArray(count) { i ->
        val before = raw[(i - 1).coerceAtLeast(0)]
        val after = raw[(i + 1).coerceAtMost(count - 1)]
        (before + 2 * raw[i] + after) / 4f
    }
}

/** RMS is a few times smaller than the peak: drawn larger to read. */
private const val RMS_SCALE = 2.2f

/**
 * The position between the service's readings: the last reading, carried on by the time since at the playing speed,
 * at most [AHEAD_MS] ahead (a stream that stalls stops there). A seek holds its target until the service reads near
 * it, so that the thumb does not jump back to where the track was.
 */
@Stable
internal class SmoothProgress {
    private var anchor by mutableFloatStateOf(0f)
    private var anchorNanos by mutableLongStateOf(0L)

    /** The frame the position was last carried on to ([glide]). */
    var frameNanos by mutableLongStateOf(0L)
        private set
    private var held = -1f
    private var heldUntil = 0L

    /**
     * Carries the position on while it plays: a pixel of a timeline [widthPx] wide at a time, as often as [rate]
     * moves it one ([glideStepMs]), each step on a frame, so that none is drawn while the window is out of sight.
     * Returns at once when a reading moves it less than a pixel: the readings alone do then.
     */
    suspend fun glide(lengthMs: Long, rate: Float, widthPx: Float) {
        val step = glideStepMs(lengthMs, rate, widthPx) ?: return
        while (true) {
            delay(step)
            withFrameNanos { frameNanos = it }
        }
    }

    fun fraction(playing: Boolean, lengthMs: Long, rate: Float): Float {
        val base = anchor
        if (!playing || lengthMs <= 0L) return base
        val ahead = ((frameNanos - anchorNanos) / 1_000_000L).coerceIn(0L, AHEAD_MS)
        return (base + ahead * rate / lengthMs).coerceIn(0f, 1f)
    }

    fun report(value: Float, lengthMs: Long) {
        val now = System.nanoTime()
        // a reading from before a seek the service has not yet made
        if (held >= 0f && now < heldUntil && abs(value - held) * lengthMs > STALE_MS) return
        held = -1f
        anchor = value
        anchorNanos = now
    }

    fun jumpTo(value: Float) {
        val now = System.nanoTime()
        anchor = value
        anchorNanos = now
        held = value
        heldUntil = now + HOLD_NANOS
    }

    private companion object {
        const val AHEAD_MS = 1_500L
        const val STALE_MS = 1_500f
        const val HOLD_NANOS = 1_200_000_000L
    }
}

/**
 * How often a timeline [widthPx] wide moves on between the readings of a media [lengthMs] long playing at [rate]:
 * once a pixel, at most every [GLIDE_MIN_STEP_MS]; null when a pixel takes longer than the readings' interval
 * ([PROGRESS_READING_MS]), the readings alone moving it then (a track on a phone's bar, a film anywhere).
 */
internal fun glideStepMs(lengthMs: Long, rate: Float, widthPx: Float): Long? {
    if (lengthMs <= 0L || rate <= 0f || widthPx < 1f) return null
    val pixelMs = lengthMs / (rate * widthPx)
    if (pixelMs >= PROGRESS_READING_MS) return null
    return max(pixelMs.toLong(), GLIDE_MIN_STEP_MS)
}

/** The shortest step of a glide, some fifty a second: more would not show on a timeline. */
internal const val GLIDE_MIN_STEP_MS = 20L

/** The played part of what plays as a hairline (the compact bars), gliding as [ProgressTimeline] does; read as the position. */
@Composable
fun ProgressHairline(progress: () -> Float, lengthMs: Long, playing: Boolean, modifier: Modifier = Modifier, rate: Float = 1f) {
    val colors = NagTheme.colors
    val reduced = NagTheme.motion.reduced
    val label = stringResource(R.string.nag_position)
    val smooth = remember { SmoothProgress() }
    val latestProgress = rememberUpdatedState(progress)
    val latestLength by rememberUpdatedState(lengthMs)
    var widthPx by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(smooth) { snapshotFlow { latestProgress.value() }.collect { smooth.report(it.coerceIn(0f, 1f), latestLength) } }
    LaunchedEffect(playing, lengthMs, rate, widthPx, reduced) {
        if (playing && !reduced) smooth.glide(lengthMs, rate, widthPx)
    }
    val percent by remember(lengthMs, playing, rate) { derivedStateOf { (smooth.fraction(playing, lengthMs, rate) * 100).toInt() } }
    val rest = colors.border
    val fill = colors.accent
    Canvas(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
            },
    ) {
        drawRect(rest)
        val x = size.width * smooth.fraction(playing, lengthMs, rate)
        if (x > 0f) drawRoundRect(fill, Offset.Zero, Size(x, size.height), CornerRadius(size.height / 2))
    }
}

// --------------------------------------------------------------- the transport

/** How large the transport is drawn: the bars', the desktop pane's, the phone's. */
enum class TransportSize(val button: Dp, val icon: Dp, val play: Dp, val playIcon: Dp) {
    Bar(36.dp, 16.dp, 40.dp, 18.dp),
    Phone(56.dp, 24.dp, 76.dp, 32.dp),
}

/**
 * Shuffle, previous, play or pause as a filled circle, next and repeat, as the music apps lay them out. Every button
 * shrinks a little under the press and springs back; shuffle and repeat are lit with a dot under them while on, repeat
 * one with a "1" on it. Each has its label (and its keys in the tooltip).
 */
@Composable
fun TransportControls(
    playing: Boolean,
    shuffling: Boolean,
    repeating: Boolean,
    actions: PlayerBarActions,
    modifier: Modifier = Modifier,
    size: TransportSize = TransportSize.Bar,
    repeatOne: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
) {
    val bar = size == TransportSize.Bar
    val button = if (bar) size.button.coerceAtLeast(NagTheme.dimens.minTarget) else size.button
    val play = if (bar) size.play.coerceAtLeast(NagTheme.dimens.minTarget) else size.play
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = horizontalArrangement) {
        TransportButton(NagIcons.Shuffle, stringResource(R.string.nag_shuffle), actions::shuffle, button, size.icon, selected = shuffling, toggle = true, shortcut = ShortcutId.Shuffle)
        TransportButton(NagIcons.Previous, stringResource(R.string.nag_previous), actions::previous, button, size.icon * 1.15f, shortcut = ShortcutId.Previous, strong = true)
        PlayPauseCircle(playing, actions::togglePlayPause, play, size.playIcon)
        TransportButton(NagIcons.Next, stringResource(R.string.nag_next), actions::next, button, size.icon * 1.15f, shortcut = ShortcutId.Next, strong = true)
        TransportButton(
            NagIcons.Repeat,
            stringResource(
                when {
                    repeatOne -> R.string.nag_player_repeat_one
                    repeating -> R.string.nag_player_repeat_all
                    else -> R.string.nag_repeat
                },
            ),
            actions::cycleRepeat,
            button,
            size.icon,
            selected = repeating || repeatOne,
            toggle = true,
            badge = if (repeatOne) "1" else null,
            shortcut = ShortcutId.Repeat,
        )
    }
}

/** How far a transport button shrinks under the press. */
private const val PRESSED_SCALE = 0.86f

/**
 * One round transport button: hover and press tint a circle behind the icon, the press shrinks it; [selected] lights
 * the icon in the accent with a dot under it; [badge] is a small mark on it (repeat one's "1"). [strong]: the icon in
 * the text colour, as previous and next are.
 */
@Composable
fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /** Shuffle and repeat: read as on or off. */
    toggle: Boolean = false,
    strong: Boolean = false,
    badge: String? = null,
    shortcut: ShortcutId? = null,
) {
    val colors = NagTheme.colors
    val motion = NagTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val scale by animateFloatAsState(if (state.pressed) PRESSED_SCALE else 1f, motion.standard(NagMotion.SHORT), label = "press")
    val keys = shortcut?.let { ShortcutRegistry.chordLabel(it, LocalContext.current.resources) }
    val tint = when {
        selected -> colors.accentText
        strong -> colors.text
        else -> colors.textSecondary
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRing(state, CircleShape)
            .background(
                when {
                    state.pressed -> colors.pressed
                    state.hovered -> colors.hover
                    else -> Color.Transparent
                },
                CircleShape,
            )
            .nagClickable(interaction, onClick, onClickLabel = contentDescription)
            .then(if (toggle) Modifier.semantics { this.selected = selected } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
        if (badge != null) {
            Text(
                badge,
                style = NagTheme.type.caption,
                color = colors.accentText,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = size / 6, end = size / 6),
            )
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = size / 9)
                    .size(4.dp)
                    .background(colors.accentText, CircleShape),
            )
        }
        HoverTooltip(state.hovered && !state.pressed, contentDescription, keys)
    }
}

/**
 * Play or pause: the accent's filled circle, the icon crossfading between the two; it shrinks a little under the press
 * and lightens under the pointer.
 */
@Composable
fun PlayPauseCircle(playing: Boolean, onClick: () -> Unit, size: Dp, iconSize: Dp, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val motion = NagTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val scale by animateFloatAsState(if (state.pressed) 0.92f else 1f, motion.standard(NagMotion.SHORT), label = "press")
    val label = stringResource(if (playing) R.string.nag_pause else R.string.nag_play)
    val keys = ShortcutRegistry.chordLabel(ShortcutId.PlayPause, LocalContext.current.resources)
    val fill = when {
        state.pressed -> lerp(colors.accent, colors.onAccent, 0.2f)
        state.hovered -> lerp(colors.accent, colors.onAccent, 0.1f)
        else -> colors.accent
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRing(state, CircleShape)
            .padding(2.dp)
            .background(fill, CircleShape)
            .nagClickable(interaction, onClick, onClickLabel = label)
            .testTag("play-pause"),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(playing, animationSpec = motion.standard(NagMotion.SHORT), label = "play-pause") { isPlaying ->
            Icon(
                if (isPlaying) NagIcons.Pause else NagIcons.Play,
                contentDescription = label,
                tint = colors.onAccent,
                // the triangle's weight is left of its box: moved right a little to look centred
                modifier = Modifier.size(iconSize).offset(x = if (isPlaying) 0.dp else iconSize / 16),
            )
        }
        HoverTooltip(state.hovered && !state.pressed, label, keys)
    }
}
