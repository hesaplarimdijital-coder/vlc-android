package app.nagmedar.ui.playback

import android.content.Context
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSlider
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.library.audioFormatLabel
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.library.mainAudio
import app.nagmedar.ui.library.rememberTechOf
import app.nagmedar.ui.library.techBadge
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.sleep.SleepTimerButton
import app.nagmedar.ui.theme.NagTheme
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.ThumbnailsProvider
import kotlin.math.roundToInt

/**
 * Cover or video thumbnail of [media], loaded through the classic thumbnail cache. Without one, a
 * film or a note stands in, by [video].
 */
@Composable
fun Artwork(
    media: MediaWrapper?,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 6.dp,
    video: Boolean = media?.type == MediaWrapper.TYPE_VIDEO,
) {
    val colors = NagTheme.colors
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, media?.id, media?.artworkMrl, px) {
        value = media?.let { ThumbnailsProvider.obtainBitmap(it, px)?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(corner)
    val placeholder = bitmap == null
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(colors.surfaceRaised)
            // without artwork the tile needs an edge: on the light theme it is as light as the bar
            .then(if (placeholder) Modifier.border(1.dp, colors.border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                if (video) NagIcons.Movies else NagIcons.Music,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(size / 2.5f),
            )
        }
    }
}

/** What the player bars show (S-304): from the playback service, or made up in tests and previews. */
@Immutable
data class PlayerBarInfo(
    val title: String,
    /** The artist, or "Şimdi çalıyor". */
    val subtitle: String,
    val isVideo: Boolean,
    val playing: Boolean,
    val lengthMs: Long,
    /** "FLAC 24/96", "4K DV"; null until the file has been probed. */
    val format: String? = null,
    val shuffling: Boolean = false,
    val repeating: Boolean = false,
    /** 0–100 as the system's media volume goes; the service boosts up to 200. */
    val volume: Int = 100,
)

/** What the bars ask of playback: the playback service in the app, a recorder in tests. */
interface PlayerBarActions {
    fun openPlayer()

    fun togglePlayPause()

    fun previous()

    fun next()

    fun seekTo(fraction: Float)

    fun stop()

    fun shuffle()

    fun cycleRepeat()

    fun setVolume(volume: Int)

    /** "Sıradaki" opens or closes (S-310). */
    fun openQueue() = Unit
}

/**
 * [PlayerBarActions] on the existing playback service (ADR-05: one service, one session). With
 * [onOpenAudio], music opens in the new interface (S-1002: the phone player, the music view).
 */
class ServicePlayerBarActions(
    private val context: Context,
    private val onQueue: () -> Unit = {},
    private val onOpenAudio: (() -> Unit)? = null,
) : PlayerBarActions {
    override fun openPlayer() {
        val audio = PlaybackService.instance?.currentMediaWrapper?.type == MediaWrapper.TYPE_AUDIO
        val open = onOpenAudio
        if (audio && open != null) open() else PlaybackActions.openPlayer(context)
    }

    override fun openQueue() = onQueue()

    override fun togglePlayPause() = PlaybackActions.togglePlayPause()

    override fun previous() = PlaybackActions.previous()

    override fun next() = PlaybackActions.next()

    override fun seekTo(fraction: Float) = PlaybackActions.seekTo(fraction)

    override fun stop() {
        PlaybackService.instance?.stop()
    }

    override fun shuffle() {
        PlaybackService.instance?.shuffle()
    }

    override fun cycleRepeat() {
        val service = PlaybackService.instance ?: return
        service.repeatType = when (service.repeatType) {
            PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
            PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
            else -> PlaybackStateCompat.REPEAT_MODE_NONE
        }
    }

    override fun setVolume(volume: Int) {
        PlaybackService.instance?.setVolume(volume.coerceIn(0, 200))
    }
}

/** The bar of the shell (S-304), for every screen: the transport bar with a sidebar, the mini player otherwise. */
@Composable
fun PlayerBar(
    navKind: NavKind,
    nowPlaying: NowPlaying,
    modifier: Modifier = Modifier,
    /** Whether "Sıradaki" is open, and its button (S-310). */
    queueOpen: Boolean = false,
    onQueue: () -> Unit = {},
    /** Music opened from the bar (S-1002); null: the classic audio player. */
    onOpenAudio: (() -> Unit)? = null,
) {
    val media = nowPlaying.media ?: return
    val info = rememberPlayerBarInfo(media, nowPlaying.playing)
    val progress by rememberPlaybackProgress(nowPlaying)
    val context = LocalContext.current
    val currentOnQueue by rememberUpdatedState(onQueue)
    val currentOnOpenAudio by rememberUpdatedState(onOpenAudio)
    val actions = remember(context) {
        ServicePlayerBarActions(context, { currentOnQueue() }, { currentOnOpenAudio?.invoke() ?: PlaybackActions.openPlayer(context) })
    }
    val artwork: @Composable (Dp) -> Unit = { size -> Artwork(media, size, video = info.isVideo) }
    if (navKind == NavKind.Sidebar) {
        TransportBar(info, { progress }, actions, artwork, modifier, queueOpen)
    } else {
        MiniPlayer(info, { progress }, actions, artwork, modifier, floating = navKind == NavKind.BottomBar, queueOpen = queueOpen)
    }
}

@Composable
private fun rememberPlayerBarInfo(media: MediaWrapper, playing: Boolean): PlayerBarInfo {
    val shuffling by PlaylistManager.shuffling.collectAsState()
    val repeating by PlaylistManager.repeating.collectAsState()
    val tech = rememberTechOf(media.uri?.toString())
    val video = media.type == MediaWrapper.TYPE_VIDEO
    val artist = media.artistName
    return PlayerBarInfo(
        title = media.nowPlaying ?: media.title ?: "",
        subtitle = if (artist.isNullOrBlank()) stringResource(R.string.nag_now_playing) else artist,
        isVideo = video,
        playing = playing,
        lengthMs = media.length,
        format = tech?.let { if (video) techBadge(it, media.width, media.height) else it.mainAudio()?.let(::audioFormatLabel) },
        shuffling = shuffling,
        repeating = repeating != PlaybackStateCompat.REPEAT_MODE_NONE,
        volume = PlaybackService.instance?.volume ?: 100,
    )
}

/**
 * Compact, medium and expanded windows: what is playing, above the navigation of every screen
 * (S-304). A tap on the artwork or the words opens the full player. On a phone it is a card over
 * the content's edge. With a pointer, hovering it (or tabbing into it) brings out the previous and
 * stop buttons and a seek bar in place of the second line.
 */
@Composable
fun MiniPlayer(
    info: PlayerBarInfo,
    progress: () -> Float,
    actions: PlayerBarActions,
    artwork: @Composable (Dp) -> Unit,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
    queueOpen: Boolean = false,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val density = LocalDensity.current
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var focusInside by remember { mutableStateOf(false) }
    val extras = pointer && (hovered || focusInside)
    val open = remember { MutableInteractionSource() }
    val openState = open.collectState()
    var titleHeight by remember { mutableIntStateOf(0) }
    val shape = if (floating) RoundedCornerShape(dimens.radiusCard) else RectangleShape
    val artSize = dimens.miniPlayerHeight - if (floating) 28.dp else 20.dp
    Box(modifier.fillMaxWidth().then(if (floating) Modifier.padding(horizontal = 8.dp, vertical = 6.dp) else Modifier)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (floating) colors.surfaceRaised else colors.chrome)
                .then(if (floating) Modifier.border(1.dp, colors.border, shape) else Modifier)
                .hoverable(hover)
                .onFocusChanged { focusInside = it.hasFocus }
                .testTag("mini-player"),
        ) {
            if (!floating) {
                HorizontalRule()
                ProgressLine(progress)
            }
            Row(
                Modifier.fillMaxWidth().height(dimens.miniPlayerHeight - if (floating) 8.dp else 0.dp).padding(horizontal = if (floating) 8.dp else dimens.gapM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                            .focusRing(openState, RoundedCornerShape(dimens.radiusControl))
                            .background(if (openState.hovered) colors.hover else Color.Transparent, RoundedCornerShape(dimens.radiusControl))
                            .nagClickable(open, actions::openPlayer, onClickLabel = stringResource(R.string.nag_open_player)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        artwork(artSize)
                        Spacer(Modifier.width(dimens.gapM))
                        Column(Modifier.weight(1f)) {
                            Text(info.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.onSizeChanged { titleHeight = it.height })
                            if (extras) {
                                Spacer(Modifier.height(SeekRowHeight))
                            } else {
                                Text(
                                    listOfNotNull(info.subtitle, info.format).joinToString(" · "),
                                    style = NagTheme.type.labelSmall,
                                    color = colors.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (extras) {
                        // where the second line was, beside the click target rather than in it
                        SeekRow(
                            info.lengthMs,
                            progress,
                            actions::seekTo,
                            Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = artSize + dimens.gapM, top = with(density) { titleHeight.toDp() })
                                .fillMaxWidth(),
                        )
                    }
                }
                if (extras) NagIconButton(NagIcons.Previous, stringResource(R.string.nag_previous), actions::previous, shortcut = ShortcutId.Previous)
                NagIconButton(
                    if (info.playing) NagIcons.Pause else NagIcons.Play,
                    stringResource(if (info.playing) R.string.nag_pause else R.string.nag_play),
                    actions::togglePlayPause,
                    tint = colors.text,
                    shortcut = ShortcutId.PlayPause,
                )
                NagIconButton(NagIcons.Next, stringResource(R.string.nag_next), actions::next, shortcut = ShortcutId.Next)
                NagIconButton(NagIcons.Queue, stringResource(R.string.nag_queue_title), actions::openQueue, selected = queueOpen, shortcut = ShortcutId.PlayQueue)
                if (extras) NagIconButton(NagIcons.Close, stringResource(R.string.nag_stop), actions::stop, shortcut = ShortcutId.Stop)
            }
            if (floating) ProgressLine(progress)
        }
    }
}

private val SeekRowHeight = 24.dp

/** The played part as a hairline; read as the position by screen readers. */
@Composable
private fun ProgressLine(progress: () -> Float) {
    val colors = NagTheme.colors
    val label = stringResource(R.string.nag_position)
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(colors.border)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        Box(Modifier.fillMaxWidth(progress().coerceIn(0f, 1f)).fillMaxHeight().background(colors.accent))
    }
}

/** "1:42 ──●──── 4:18": seeking from a bar. */
@Composable
private fun SeekRow(lengthMs: Long, progress: () -> Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val label = stringResource(R.string.nag_position)
    val fraction = progress()
    Row(modifier.height(SeekRowHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(clock((lengthMs * fraction).toLong()), style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1)
        Spacer(Modifier.width(dimens.gapS))
        NagSlider(
            value = fraction,
            onValueChange = onSeek,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            hoverLabel = { clock((lengthMs * it).toLong()) },
        )
        Spacer(Modifier.width(dimens.gapS))
        Text(clock(lengthMs), style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1)
    }
}

/** "0:00", "1:42", "1:02:03". */
private fun clock(millis: Long): String = if (millis <= 0) "0:00" else formatDuration(millis)

/**
 * Large windows: the transport bar of the mockups across the bottom of every screen (S-304). The
 * artwork and words open the full player; in the middle the transport and the seek bar, which
 * shows the time under the pointer; at the end the format, the volume and the player.
 */
@Composable
fun TransportBar(
    info: PlayerBarInfo,
    progress: () -> Float,
    actions: PlayerBarActions,
    artwork: @Composable (Dp) -> Unit,
    modifier: Modifier = Modifier,
    queueOpen: Boolean = false,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val open = remember { MutableInteractionSource() }
    val openState = open.collectState()
    val openLabel = stringResource(R.string.nag_open_player)
    Column(modifier.fillMaxWidth().background(colors.chrome).testTag("transport-bar")) {
        HorizontalRule()
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = dimens.gapL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .focusRing(openState, RoundedCornerShape(dimens.radiusControl))
                    .background(if (openState.hovered) colors.hover else Color.Transparent, RoundedCornerShape(dimens.radiusControl))
                    .nagClickable(open, actions::openPlayer, onClickLabel = openLabel)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artwork(48.dp)
                Spacer(Modifier.width(dimens.gapM))
                Column(Modifier.weight(1f)) {
                    Text(info.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(info.subtitle, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(dimens.gapL))
            Column(Modifier.widthIn(min = 360.dp, max = 640.dp).weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NagIconButton(NagIcons.Shuffle, stringResource(R.string.nag_shuffle), actions::shuffle, selected = info.shuffling, shortcut = ShortcutId.Shuffle)
                    NagIconButton(NagIcons.Previous, stringResource(R.string.nag_previous), actions::previous, shortcut = ShortcutId.Previous)
                    PlayPauseButton(info.playing, actions::togglePlayPause)
                    NagIconButton(NagIcons.Next, stringResource(R.string.nag_next), actions::next, shortcut = ShortcutId.Next)
                    NagIconButton(NagIcons.Repeat, stringResource(R.string.nag_repeat), actions::cycleRepeat, selected = info.repeating, shortcut = ShortcutId.Repeat)
                }
                SeekRow(info.lengthMs, progress, actions::seekTo, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(dimens.gapL))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                info.format?.let { FormatChip(it) }
                Spacer(Modifier.width(dimens.gapS))
                VolumeControl(info.volume, actions::setVolume)
                // S-1007: while the sleep timer runs, what is left
                SleepTimerButton(onlyWhileRunning = true)
                NagIconButton(NagIcons.Queue, stringResource(R.string.nag_queue_title), actions::openQueue, selected = queueOpen, shortcut = ShortcutId.PlayQueue)
                NagIconButton(NagIcons.OpenWindow, openLabel, actions::openPlayer)
            }
        }
    }
}

/** "FLAC 24/96" in the bar's end, as the mockup shows the format. */
@Composable
private fun FormatChip(text: String) {
    val colors = NagTheme.colors
    Text(
        text,
        style = NagTheme.type.mono,
        color = colors.textSecondary,
        maxLines = 1,
        modifier = Modifier
            .border(1.dp, colors.borderStrong, RoundedCornerShape(NagTheme.dimens.radiusSmall))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** The speaker mutes and brings the sound back; the slider sets the volume. */
@Composable
private fun VolumeControl(volume: Int, onVolume: (Int) -> Unit) {
    var level by remember(volume) { mutableIntStateOf(volume) }
    var beforeMute by remember { mutableIntStateOf(volume.takeIf { it > 0 } ?: 100) }
    val label = stringResource(R.string.nag_volume)
    val muted = level == 0
    NagIconButton(
        NagIcons.Volume,
        stringResource(if (muted) R.string.nag_unmute else R.string.nag_mute),
        {
            if (muted) {
                level = beforeMute
            } else {
                beforeMute = level
                level = 0
            }
            onVolume(level)
        },
        selected = muted,
        shortcut = ShortcutId.Mute,
    )
    NagSlider(
        value = level.coerceIn(0, 100) / 100f,
        onValueChange = { fraction ->
            level = (fraction * 100).roundToInt()
            onVolume(level)
        },
        modifier = Modifier.width(96.dp).semantics { contentDescription = label },
    )
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val size = NagTheme.dimens.minTarget.coerceAtLeast(36.dp)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size - 4.dp).clip(CircleShape).background(colors.accent))
        NagIconButton(
            icon = if (playing) NagIcons.Pause else NagIcons.Play,
            contentDescription = stringResource(if (playing) R.string.nag_pause else R.string.nag_play),
            onClick = onClick,
            size = size,
            tint = colors.onAccent,
            shortcut = ShortcutId.PlayPause,
        )
    }
}
