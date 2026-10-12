package app.nagmedar.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How long a drag waits between two seeks: the player seeks as the mouse goes, not at every pixel. */
private const val SEEK_EVERY_MS = 120L

/**
 * The player's timeline (S-601): what has played, the chapters' starts as ticks, the A-B loop's
 * marks with its stretch lit; under the mouse a frame of that moment with its time and chapter.
 * A click seeks there; a drag seeks as it goes.
 */
@Composable
fun PlayerTimeline(
    state: PlayerChromeState,
    onSeek: (Long) -> Unit,
    previews: FramePreviews?,
    modifier: Modifier = Modifier,
    /** The mouse's place in the timeline, for tests and screenshots: 0..1, null when it is elsewhere. */
    initialHover: Float? = null,
) {
    val colors = NagTheme.colors
    val length = state.lengthMs
    val label = stringResource(R.string.nag_player_position)
    var widthPx by remember { mutableFloatStateOf(1f) }
    var hover by remember { mutableStateOf(initialHover) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var lastSeek by remember { mutableLongStateOf(0L) }
    var lastSought by remember { mutableFloatStateOf(-1f) }
    val latestSeek by rememberUpdatedState(onSeek)
    val latestLength by rememberUpdatedState(length)
    val played = drag ?: if (length > 0) (state.timeMs.toFloat() / length).coerceIn(0f, 1f) else 0f
    val loop = colors.warning
    val tick = Color.White.copy(alpha = 0.55f)
    val track = Color.White.copy(alpha = 0.28f)
    val emphasised = hover != null || drag != null

    fun seek(fraction: Float, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSeek < SEEK_EVERY_MS) return
        lastSeek = now
        lastSought = fraction
        if (latestLength > 0) latestSeek((latestLength * fraction.coerceIn(0f, 1f)).toLong())
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(TimelineHeight)
            .testTag("player-timeline")
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(played, 0f..1f)
                setProgress { target ->
                    seek(target, force = true)
                    true
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val at = (change.position.x / widthPx).coerceIn(0f, 1f)
                        when (event.type) {
                            PointerEventType.Press -> {
                                drag = at
                                seek(at, force = true)
                                change.consume()
                            }
                            PointerEventType.Move -> {
                                hover = at
                                if (drag != null && change.pressed) {
                                    drag = at
                                    seek(at, force = false)
                                    change.consume()
                                }
                            }
                            PointerEventType.Release -> if (drag != null) {
                                // where the drag ended, unless it was sought there already (a click)
                                if (at != lastSought) seek(at, force = true)
                                drag = null
                                change.consume()
                            }
                            PointerEventType.Enter -> hover = at
                            PointerEventType.Exit -> hover = null
                        }
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        Canvas(Modifier.fillMaxWidth().height(TimelineHeight)) {
            val y = size.height - TrackFromBottom.toPx()
            val thickness = (if (emphasised) 6.dp else 4.dp).toPx()
            val radius = CornerRadius(thickness / 2, thickness / 2)
            fun x(ms: Long) = if (length > 0) size.width * (ms.toFloat() / length).coerceIn(0f, 1f) else 0f
            drawRoundRect(track, Offset(0f, y - thickness / 2), Size(size.width, thickness), radius)
            drawRoundRect(colors.accent, Offset(0f, y - thickness / 2), Size(size.width * played, thickness), radius)
            // the A-B loop: its stretch and its two marks, "A" and "B" written above them
            if (state.abStartMs >= 0) {
                val a = x(state.abStartMs)
                val b = if (state.abStopMs >= 0) x(state.abStopMs) else a
                if (b > a) drawRect(loop.copy(alpha = 0.55f), Offset(a, y - thickness / 2), Size(b - a, thickness))
                for (mark in listOfNotNull(a, b.takeIf { state.abStopMs >= 0 })) {
                    drawRect(loop, Offset(mark - 1.dp.toPx(), y - 7.dp.toPx()), Size(2.dp.toPx(), 14.dp.toPx()))
                }
            }
            // the chapters' starts
            for (chapter in state.chapters) {
                if (chapter.startMs <= 0) continue
                val at = x(chapter.startMs)
                drawRect(tick, Offset(at - 1.dp.toPx(), y - 6.dp.toPx()), Size(2.dp.toPx(), 4.dp.toPx()))
            }
            val thumb = (if (emphasised) 7.dp else 6.dp).toPx()
            drawCircle(Color.Black.copy(alpha = 0.35f), thumb + 1.dp.toPx(), Offset(size.width * played, y + 1.dp.toPx()))
            drawCircle(Color.White, thumb, Offset(size.width * played, y))
        }
        if (state.abStartMs >= 0) {
            AbLetter("A", state.abStartMs, length, widthPx)
            if (state.abStopMs >= 0) AbLetter("B", state.abStopMs, length, widthPx)
        }
        val at = hover
        if (at != null && length > 0) {
            HoverPreview(
                state = state,
                ms = (length * at).toLong(),
                x = with(density) { (widthPx * at).toDp() },
                width = maxWidth,
                previews = previews,
            )
        }
    }
}

private val TimelineHeight = 34.dp
private val TrackFromBottom = 10.dp
private val PreviewWidth = 192.dp
private val PreviewHeight = 108.dp
private val PreviewGap = 6.dp

@Composable
private fun AbLetter(letter: String, ms: Long, length: Long, widthPx: Float) {
    if (length <= 0) return
    val x = widthPx * (ms.toFloat() / length).coerceIn(0f, 1f)
    Text(
        letter,
        style = NagTheme.type.caption,
        color = NagTheme.colors.warning,
        modifier = Modifier.offset { IntOffset((x - 4.dp.toPx()).roundToInt(), 0) },
    )
}

/**
 * Above the mouse: the frame of that moment (when the file gives one), its time and its chapter.
 * Centred on the mouse and kept inside the timeline; the label may be wider than the frame.
 */
@Composable
private fun HoverPreview(
    state: PlayerChromeState,
    ms: Long,
    x: Dp,
    width: Dp,
    previews: FramePreviews?,
) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val bucket = ms / FramePreviews.STEP_MS
    LaunchedEffect(previews, bucket) {
        if (previews == null) {
            frame = null
            return@LaunchedEffect
        }
        // the mouse rests a moment before a frame is read
        delay(80)
        frame = previews.frameAt(ms)
    }
    val chapter = state.chapterAt(ms)?.let { index ->
        val name = state.chapters[index].name
        if (name.isBlank()) stringResource(R.string.nag_player_chapter, index + 1) else stringResource(R.string.nag_player_chapter_named, index + 1, name)
    }
    val text = listOfNotNull(playerClock(ms), chapter).joinToString(" · ")
    val image = frame
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier
            .testTag("player-preview")
            .layout { measurable, _ ->
                val room = width.roundToPx()
                val placeable = measurable.measure(Constraints(maxWidth = room))
                val left = (x.roundToPx() - placeable.width / 2).coerceIn(0, (room - placeable.width).coerceAtLeast(0))
                // above the timeline, taking no room in it
                layout(0, 0) { placeable.place(left, -placeable.height - PreviewGap.roundToPx()) }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (image != null) {
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(PreviewWidth, PreviewHeight)
                    .clip(shape)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), shape),
            )
        }
        Box(
            Modifier
                .padding(top = 4.dp)
                .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(text, style = NagTheme.type.mono, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
