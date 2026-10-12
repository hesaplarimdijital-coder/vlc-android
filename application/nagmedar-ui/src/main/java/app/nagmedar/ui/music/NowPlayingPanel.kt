package app.nagmedar.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.SignalPath
import app.nagmedar.audio.Waveform
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.kiloHertz
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.audio.rememberSignalPath
import app.nagmedar.ui.audio.sinkLabel
import app.nagmedar.ui.components.RatingStars
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.playback.QueueEntry
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.playback.rememberPlayQueue
import app.nagmedar.ui.playback.rememberPlaybackProgress
import app.nagmedar.ui.stats.TrackStatsIndex
import app.nagmedar.ui.stats.statsKey
import app.nagmedar.ui.theme.NagTheme
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import kotlin.math.max
import kotlin.math.min

/** What the now playing panel shows (S-1001): from the playback service, or made up in tests. */
@Immutable
data class NowPlayingInfo(
    val title: String,
    val artist: String?,
    val album: String?,
    /** "FLAC · 24 bit · 96 kHz · USB DAC, bit-perfect"; null until known. */
    val format: String?,
    val lengthMs: Long,
    val playing: Boolean,
    /** S-808: its stars (0 none); null for what is not rated (a stream). */
    val rating: Int? = null,
)

/**
 * S-1001: the right pane of the desktop music view: the artwork, what plays, how it plays, its
 * waveform as the timeline (a click seeks), and what comes next (a click plays it). [info] null:
 * nothing plays.
 */
@Composable
fun NowPlayingPanel(
    info: NowPlayingInfo?,
    progress: () -> Float,
    waveform: Waveform?,
    upNext: List<QueueEntry>,
    onSeek: (Float) -> Unit,
    onPlayUpNext: (Int) -> Unit,
    modifier: Modifier = Modifier,
    artwork: @Composable (Dp) -> Unit = {},
    /** S-808: sets [NowPlayingInfo.rating]. */
    onRate: ((Int) -> Unit)? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dimens.gapL),
        verticalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        Text(stringResource(R.string.nag_now_playing), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        if (info == null) {
            Text(stringResource(R.string.nag_music_nothing_playing), style = NagTheme.type.body, color = colors.textSecondary)
        } else {
            Playing(info, progress, waveform, upNext, onSeek, onPlayUpNext, artwork, onRate)
        }
    }
}

@Composable
private fun Playing(
    info: NowPlayingInfo,
    progress: () -> Float,
    waveform: Waveform?,
    upNext: List<QueueEntry>,
    onSeek: (Float) -> Unit,
    onPlayUpNext: (Int) -> Unit,
    artwork: @Composable (Dp) -> Unit,
    onRate: ((Int) -> Unit)?,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val side = min(maxWidth.value, 288f).dp
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { artwork(side) }
        }
        Spacer(Modifier.height(dimens.gapXs))
        Text(info.title, style = NagTheme.type.title, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val line = listOfNotNull(info.artist?.takeIf { it.isNotBlank() }, info.album?.takeIf { it.isNotBlank() }).joinToString(" · ")
        if (line.isNotEmpty()) Text(line, style = NagTheme.type.body, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (info.format != null) Text(info.format, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 2)
        info.rating?.let { stars -> RatingStars(stars, onRate, starSize = 16.dp, target = 22.dp, focusable = onRate != null, modifier = Modifier.testTag("now-playing-rating")) }
        Spacer(Modifier.height(dimens.gapXs))
        WaveformTimeline(waveform, progress, info.lengthMs, onSeek)
        if (upNext.isNotEmpty()) {
            Spacer(Modifier.height(dimens.gapS))
            Text(stringResource(R.string.nag_queue_title), style = NagTheme.type.labelSmall, color = colors.textTertiary)
            upNext.take(UP_NEXT).forEachIndexed { i, entry -> UpNextRow(entry) { onPlayUpNext(i) } }
        }
    }
}

/** How many of the next tracks the panel lists. */
private const val UP_NEXT = 6

@Composable
private fun UpNextRow(entry: QueueEntry, onPlay: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTarget.coerceAtLeast(40.dp))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onPlay)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = NagTheme.type.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.subtitle.isNotBlank()) {
                Text(entry.subtitle, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(formatDuration(entry.lengthMs), style = NagTheme.type.mono, color = colors.textTertiary)
    }
}

/**
 * S-1001: the track as its waveform, the part played lit: each bar a stretch of the track, its
 * peak drawn faint and its loudness (RMS) strong. A click or a drag seeks; Left and Right step
 * five seconds, Home and End go to the ends; TalkBack reads and sets it as a seek bar. Without a
 * waveform (no engine, a stream) the bars are a plain line.
 */
@Composable
fun WaveformTimeline(
    waveform: Waveform?,
    progress: () -> Float,
    lengthMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    var widthPx by remember { mutableFloatStateOf(1f) }
    val latestSeek by rememberUpdatedState(onSeek)
    val fraction = progress().coerceIn(0f, 1f)
    val step = if (lengthMs > 0) (SEEK_STEP_MS.toFloat() / lengthMs) else 0.02f
    val description = stringResource(R.string.nag_music_waveform)
    val position = stringResource(R.string.nag_music_position, formatDuration((lengthMs * fraction).toLong()), formatDuration(lengthMs))
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(interaction)
                .focusable(interactionSource = interaction)
                .focusRing(state, RoundedCornerShape(4.dp))
                .semantics {
                    contentDescription = description
                    stateDescription = position
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { target -> latestSeek(target.coerceIn(0f, 1f)); true }
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val target = when (event.key) {
                        Key.DirectionLeft -> fraction - step
                        Key.DirectionRight -> fraction + step
                        Key.MoveHome -> 0f
                        Key.MoveEnd -> 1f
                        else -> return@onKeyEvent false
                    }
                    latestSeek(target.coerceIn(0f, 1f))
                    true
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            when (event.type) {
                                PointerEventType.Press -> {
                                    dragging = true
                                    latestSeek((change.position.x / widthPx).coerceIn(0f, 1f))
                                    change.consume()
                                }
                                PointerEventType.Move -> if (dragging && change.pressed) {
                                    latestSeek((change.position.x / widthPx).coerceIn(0f, 1f))
                                    change.consume()
                                }
                                PointerEventType.Release -> dragging = false
                                else -> Unit
                            }
                        }
                    }
                },
        ) {
            val bar = 2.dp.toPx()
            val gap = 1.dp.toPx()
            val count = max(1, ((size.width + gap) / (bar + gap)).toInt())
            val middle = size.height / 2
            val played = size.width * fraction
            for (i in 0 until count) {
                val x = i * (bar + gap)
                val (peak, rms) = if (waveform != null && waveform.size > 0) bucket(waveform, i, count) else FLAT
                val lit = x + bar / 2 <= played
                val peakHeight = max(bar, peak * size.height)
                val rmsHeight = max(bar, min(size.height, rms * RMS_SCALE * size.height))
                drawRoundRect(
                    if (lit) colors.accent.copy(alpha = 0.45f) else colors.borderStrong,
                    topLeft = Offset(x, middle - peakHeight / 2),
                    size = Size(bar, peakHeight),
                    cornerRadius = CornerRadius(bar / 2),
                )
                drawRoundRect(
                    if (lit) colors.accent else colors.textTertiary,
                    topLeft = Offset(x, middle - rmsHeight / 2),
                    size = Size(bar, rmsHeight),
                    cornerRadius = CornerRadius(bar / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(formatDuration((lengthMs * fraction).toLong()), style = NagTheme.type.mono, color = colors.textTertiary)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(lengthMs), style = NagTheme.type.mono, color = colors.textTertiary)
        }
    }
}

/** A key step of the timeline. */
private const val SEEK_STEP_MS = 5_000L

/** RMS is a few times smaller than the peak: drawn larger to read. */
private const val RMS_SCALE = 2.2f

/** A bar without a waveform: a thin line. */
private val FLAT = 0f to 0f

/** The peak (largest) and RMS (mean) of the buckets under bar [index] of [count]. */
private fun bucket(waveform: Waveform, index: Int, count: Int): Pair<Float, Float> {
    val from = index * waveform.size / count
    val to = max(from + 1, (index + 1) * waveform.size / count).coerceAtMost(waveform.size)
    var peak = 0f
    var rms = 0f
    for (b in from until to) {
        peak = max(peak, waveform.peak(b))
        rms += waveform.rms(b)
    }
    return peak to rms / (to - from)
}

// --------------------------------------------------------------- the app's panel

/** The panel on the playback service: what plays, its waveform, the queue after it. */
@Composable
fun MusicNowPlaying() {
    val context = LocalContext.current
    val nowPlaying = rememberNowPlaying()
    val media = nowPlaying.media?.takeIf { it.type == MediaWrapper.TYPE_AUDIO }
    // S-808: its stars
    val stats by TrackStatsIndex.stats.collectAsState()
    LaunchedEffect(Unit) { TrackStatsIndex.follow(context) }
    val key = media?.let(::statsKey)
    val progress by rememberPlaybackProgress(nowPlaying)
    val waveform = rememberWaveform(media?.uri?.toString())
    val queue = rememberPlayQueue()
    val path = rememberSignalPath()
    val upNext = if (queue.current >= 0) queue.entries.drop(queue.current + 1) else emptyList()
    val info = media?.let {
        NowPlayingInfo(
            title = it.nowPlaying ?: it.title ?: "",
            artist = it.artistName,
            album = it.albumName,
            format = path?.let { p -> formatLine(p) },
            lengthMs = it.length,
            playing = nowPlaying.playing,
            rating = key?.let { k -> stats[k]?.rating ?: 0 },
        )
    }
    NowPlayingPanel(
        info = info,
        progress = { progress },
        waveform = waveform,
        upNext = upNext,
        onSeek = PlaybackActions::seekTo,
        onPlayUpNext = { i -> queue.play(queue.current + 1 + i) },
        artwork = { size -> Artwork(media, size, corner = 8.dp, video = false) },
        onRate = key?.let { k -> { stars -> TrackStatsIndex.rate(context, k, stars) } },
    )
}

/** "FLAC 24/96 · USB DAC · Bit-perfect": the source and the output as the engine plays them. */
@Composable
internal fun formatLine(path: SignalPath): String? {
    val source = path.source ?: return null
    val locale = LocalContext.current.resources.locale()
    val tech = TrackTech(source.codec, source.container, source.rate, source.bits, source.channels, source.isFloat, source.dsdRate, source.bitrate)
    val output = path.output?.let { out -> sinkLabel(out.sink) + " · " + kiloHertz(out.rate, locale) }
    val perfect = if (path.bitPerfect) stringResource(R.string.nag_sp_bit_perfect) else null
    return listOfNotNull(formatLabel(tech), output, perfect).joinToString(" · ")
}
