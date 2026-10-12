package app.nagmedar.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.Waveform
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.rememberSignalPath
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.PlayerBarActions
import app.nagmedar.ui.playback.ServicePlayerBarActions
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.playback.rememberPlaybackProgress
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.media.PlaylistManager
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * S-1002: the phone's music player, over the whole window: the artwork, what plays and how, the
 * waveform as the timeline, and the controls low, where one thumb reaches them. The artwork swiped
 * left plays the next track and right the previous; swiped down, or Back, closes the player. The
 * same artwork, words and controls as the lock screen's, from the same media session.
 */
@Composable
fun PhonePlayer(
    info: NowPlayingInfo,
    progress: () -> Float,
    waveform: Waveform?,
    actions: PlayerBarActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    shuffling: Boolean = false,
    repeating: Boolean = false,
    artwork: @Composable (Dp) -> Unit = {},
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val swipe = remember { Animatable(0f) }
    val drop = remember { Animatable(0f) }
    val latestActions by rememberUpdatedState(actions)
    val latestClose by rememberUpdatedState(onClose)
    BackHandler(onBack = onClose)
    val next = stringResource(R.string.nag_next)
    val previous = stringResource(R.string.nag_previous)
    val close = stringResource(R.string.nag_player_close)
    Column(
        modifier
            .fillMaxSize()
            .offset { IntOffset(0, drop.value.roundToInt()) }
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = dimens.gapL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = dimens.gapXs), verticalAlignment = Alignment.CenterVertically) {
            NagIconButton(NagIcons.ChevronDown, close, onClose)
            Text(
                stringResource(R.string.nag_now_playing),
                style = NagTheme.type.label,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            NagIconButton(NagIcons.Queue, stringResource(R.string.nag_queue_title), actions::openQueue, shortcut = ShortcutId.PlayQueue)
        }
        // the artwork: swiped sideways it changes the track, down it closes the player
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(next) { latestActions.next(); true },
                        CustomAccessibilityAction(previous) { latestActions.previous(); true },
                        CustomAccessibilityAction(close) { latestClose(); true },
                    )
                }
                .pointerInput(Unit) {
                    val sideways = with(density) { SWIPE.toPx() }
                    val down = with(density) { CLOSE.toPx() }
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = {
                            dx = 0f
                            dy = 0f
                        },
                        onDragEnd = {
                            val horizontal = abs(dx) > abs(dy)
                            scope.launch {
                                when {
                                    horizontal && dx <= -sideways -> latestActions.next()
                                    horizontal && dx >= sideways -> latestActions.previous()
                                    !horizontal && dy >= down -> latestClose()
                                }
                                swipe.animateTo(0f)
                                drop.animateTo(0f)
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                swipe.animateTo(0f)
                                drop.animateTo(0f)
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                        scope.launch {
                            if (abs(dx) > abs(dy)) swipe.snapTo(dx) else drop.snapTo(dy.coerceAtLeast(0f))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight, 420.dp)
            Box(Modifier.offset { IntOffset(swipe.value.roundToInt(), 0) }) { artwork(side) }
        }
        Spacer(Modifier.height(dimens.gapM))
        Column(Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(info.title, style = NagTheme.type.title, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(info.artist?.takeIf { it.isNotBlank() }, info.album?.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = NagTheme.type.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (info.format != null) Text(info.format, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(dimens.gapS))
            WaveformTimeline(waveform, progress, info.lengthMs, actions::seekTo, height = 56.dp)
        }
        Spacer(Modifier.height(dimens.gapM))
        // the controls, low: the thumb's
        Row(
            Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(bottom = dimens.gapL),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NagIconButton(NagIcons.Shuffle, stringResource(R.string.nag_shuffle), actions::shuffle, selected = shuffling, size = 56.dp)
            NagIconButton(NagIcons.Previous, previous, actions::previous, size = 64.dp, iconSize = 28.dp)
            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(colors.accent))
                NagIconButton(
                    if (info.playing) NagIcons.Pause else NagIcons.Play,
                    stringResource(if (info.playing) R.string.nag_pause else R.string.nag_play),
                    actions::togglePlayPause,
                    size = 80.dp,
                    iconSize = 36.dp,
                    tint = colors.onAccent,
                )
            }
            NagIconButton(NagIcons.Next, next, actions::next, size = 64.dp, iconSize = 28.dp)
            NagIconButton(NagIcons.Repeat, stringResource(R.string.nag_repeat), actions::cycleRepeat, selected = repeating, size = 56.dp)
        }
    }
}

/** A sideways swipe this long changes the track. */
private val SWIPE = 80.dp

/** A swipe down this long closes the player. */
private val CLOSE = 140.dp

/** The phone player on the playback service; [onQueue] opens "Sıradaki" over it. Nothing without music. */
@Composable
fun PhonePlayerHost(onClose: () -> Unit, onQueue: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val nowPlaying = rememberNowPlaying()
    val media = nowPlaying.media?.takeIf { it.type == MediaWrapper.TYPE_AUDIO }
    if (media == null) {
        // the music stopped, or a video took over: nothing to show
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val progress by rememberPlaybackProgress(nowPlaying)
    val waveform = rememberWaveform(media.uri?.toString())
    val path = rememberSignalPath()
    val shuffling by PlaylistManager.shuffling.collectAsState()
    val repeating by PlaylistManager.repeating.collectAsState()
    val latestQueue by rememberUpdatedState(onQueue)
    val actions = remember(context) { ServicePlayerBarActions(context, { latestQueue() }) }
    PhonePlayer(
        info = NowPlayingInfo(
            title = media.nowPlaying ?: media.title ?: "",
            artist = media.artistName,
            album = media.albumName,
            format = path?.let { formatLine(it) },
            lengthMs = media.length,
            playing = nowPlaying.playing,
        ),
        progress = { progress },
        waveform = waveform,
        actions = actions,
        onClose = onClose,
        modifier = modifier,
        shuffling = shuffling,
        repeating = repeating != PlaybackStateCompat.REPEAT_MODE_NONE,
        artwork = { size -> Artwork(media, size, corner = 12.dp, video = false) },
    )
}
