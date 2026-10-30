package app.nagmedar.ui.playback

import android.content.Context
import android.support.v4.media.session.PlaybackStateCompat
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.cast.CastButton
import app.nagmedar.ui.cast.CastDevice
import app.nagmedar.ui.cast.Casting
import app.nagmedar.ui.cast.rememberCastDevice
import app.nagmedar.ui.cast.rememberCastingEnabled
import app.nagmedar.ui.components.ArtistAlbumLine
import app.nagmedar.ui.components.ExpandablePlayer
import app.nagmedar.ui.components.ExpandablePlayerState
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSlider
import app.nagmedar.ui.components.SliderMark
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.covers.CoverCenter
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.library.audioFormatLabel
import app.nagmedar.ui.library.mainAudio
import app.nagmedar.ui.library.rememberTechOf
import app.nagmedar.ui.music.MusicLinks
import app.nagmedar.ui.music.rememberArtworkColor
import app.nagmedar.ui.podcast.Chapter
import app.nagmedar.ui.podcast.ChapterSteps
import app.nagmedar.ui.shell.LocalFloatingPlayer
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.floatingPlayerCard
import app.nagmedar.ui.sleep.SleepTimerButton
import app.nagmedar.ui.theme.ArtworkAccent
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.animatedAccent
import kotlin.math.roundToInt
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.ThumbnailsProvider

/**
 * Cover or video thumbnail of [media], loaded through the classic thumbnail cache. Without one, a
 * film or a note stands in, by [video]. With [crossfade] (the phone's player) another picture fades
 * in over the one before, never through the tile's background; the same picture does not move.
 */
@Composable
fun Artwork(
    media: MediaWrapper?,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 6.dp,
    video: Boolean = media?.type == MediaWrapper.TYPE_VIDEO,
    crossfade: Boolean = false,
) {
    val colors = NagTheme.colors
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    // S-1006: a track's album cover (its tags', its folder's, Cover Art Archive's, or one chosen); else the medialibrary's
    val changes by CoverCenter.changes.collectAsState()
    // shown a moment ago: there at once (a screen that comes back has its pictures in its first frame), and read again behind it
    val shown = media?.let { item ->
        (if (item.type == MediaWrapper.TYPE_AUDIO) CoverTrack.of(item)?.let { CoverCenter.shown(it, px) } else null) ?: ShownArtwork.get(item, px)
    }
    val bitmap by produceState(shown, media?.id, media?.artworkMrl, px, changes) {
        value = media?.let { item ->
            val track = if (item.type == MediaWrapper.TYPE_AUDIO) CoverTrack.of(item) else null
            track?.let { runCatching { CoverCenter.image(context, it, px) }.getOrNull() }
                ?: ThumbnailsProvider.obtainBitmap(item, px)?.asImageBitmap()?.also { ShownArtwork.put(item, px, it) }
        }
    }
    // with crossfade: the picture drawn, and the one before under it while the new one fades in
    val motion = NagTheme.motion
    var drawn by remember { mutableStateOf(bitmap) }
    var under by remember { mutableStateOf<ImageBitmap?>(null) }
    val coming = remember { Animatable(1f) }
    if (crossfade) {
        LaunchedEffect(bitmap) {
            val before = drawn
            if (before == null || bitmap == null || before === bitmap || motion.reduced) {
                drawn = bitmap
                return@LaunchedEffect
            }
            under = before
            drawn = bitmap
            coming.snapTo(0f)
            try {
                coming.animateTo(1f, motion.standard(NagMotion.MEDIUM))
            } finally {
                under = null
            }
        }
    }
    val picture = if (crossfade) drawn else bitmap
    val shape = RoundedCornerShape(corner)
    val placeholder = picture == null
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(colors.surfaceRaised)
            // without artwork the tile needs an edge: on the light theme it is as light as the bar
            .then(if (placeholder) Modifier.border(1.dp, colors.border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val image = picture
        if (image != null) {
            val before = under.takeIf { crossfade }
            if (before != null) Image(before, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = if (before != null) Modifier.fillMaxSize().graphicsLayer { alpha = coming.value } else Modifier.fillMaxSize(),
            )
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

/** The medialibrary's thumbnails last shown, by media and size, while there is room (a few megabytes). */
private object ShownArtwork {
    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun key(media: MediaWrapper, px: Int) = "${media.id}|${media.uri}|${media.artworkMrl}|$px"

    fun get(media: MediaWrapper, px: Int): ImageBitmap? = cache.get(key(media, px))

    fun put(media: MediaWrapper, px: Int, image: ImageBitmap) {
        cache.put(key(media, px), image)
    }

    private const val MAX_BYTES = 12 * 1024 * 1024
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
    /** S-1302: VLC's casting setting is on: the bars have the cast button. */
    val canCast: Boolean = false,
    /** S-1302: the device a renderer plays it on; null while it plays here. */
    val casting: CastDevice? = null,
    /** S-1104: the chapters of the podcast episode that plays; empty for any other media. */
    val chapters: List<Chapter> = emptyList(),
    /** The album of the library's music (null for anything else); with [linked] it opens its tracks. */
    val album: String? = null,
    /** The artist ([subtitle]) and the album are links to their tracks in the music view (music of the library). */
    val linked: Boolean = false,
    /** Repeat one: the repeat button has its "1". */
    val repeatOne: Boolean = false,
    /** The playing speed: the timelines move at it between the service's readings. */
    val rate: Float = 1f,
    /** 1.0.2: a stream without a length (a station on the air): the bars show it live instead of a seek bar. */
    val live: Boolean = false,
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

    /** S-1302: the device picker. */
    fun cast() = Unit

    /** S-1104: to the next chapter of the playing episode, or for [forward] false to the start of this one (the one before). */
    fun stepChapter(forward: Boolean) = Unit

    /** S-1104: to [ms] of the playing media (a chapter chosen from the list). */
    fun seekToMs(ms: Long) = Unit

    /** The artist's tracks of what plays, in the music view. */
    fun openArtist() = Unit

    /** The album's tracks of what plays, in the music view. */
    fun openAlbum() = Unit
}

/**
 * [PlayerBarActions] on the existing playback service (ADR-05: one service, one session). With
 * [onOpenAudio], what is not a video opens in the new interface (S-1002: the phone player, the music
 * view; the radio, podcasts and streams pages).
 */
class ServicePlayerBarActions(
    private val context: Context,
    private val onQueue: () -> Unit = {},
    private val onOpenAudio: (() -> Unit)? = null,
) : PlayerBarActions {
    override fun openPlayer() {
        // music, radio, podcasts and streams open in this interface
        val open = onOpenAudio
        if (open != null) open() else PlaybackActions.openPlayer(context)
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

    /** The user's volume: kept for the next start (never the sleep timer's fade nor the alarm's rise, which go round it). */
    override fun setVolume(volume: Int) = PlaybackActions.setVolume(volume.coerceIn(0, 200))

    override fun cast() = Casting.openPicker()

    override fun stepChapter(forward: Boolean) {
        ChapterSteps.step(forward)
    }

    override fun seekToMs(ms: Long) = PlaybackActions.seekToMs(ms)

    override fun openArtist() {
        MusicLinks.artistOf(PlaybackService.instance?.currentMediaWrapper)?.let(MusicLinks::open)
    }

    override fun openAlbum() {
        MusicLinks.albumOf(PlaybackService.instance?.currentMediaWrapper)?.let(MusicLinks::open)
    }
}

/**
 * The bar of the shell (S-304), for every screen: the transport bar with a sidebar, the mini player
 * with a rail, and on a phone the transport bar laid out for it (1.0.2, [CompactTransportBar]).
 * On a phone, given [phonePlayer] and its [fullPlayer], the bar grows into the full player when
 * dragged up or tapped (Desktop Kit 1.0's ExpandablePlayer) while music plays, and a sideways swipe
 * on it (not on its seek bar) skips to the previous or next track; a station's or an episode's opens
 * its page as before ([onOpenAudio]) and does not skip (the transport has no seek steps for them).
 */
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
    phonePlayer: ExpandablePlayerState? = null,
    fullPlayer: (@Composable () -> Unit)? = null,
) {
    // Nağmedar 1.0 plays everything as audio: the bars show whatever plays
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
    // Desktop Kit 1.0: in the shell's floating bar (tablet, DeX) the bars draw no surface of their own
    val transparent = LocalFloatingPlayer.current
    // Desktop Kit 1.0: while music plays the bar takes its artwork's accent, cross-fading from one track to the next
    val music = media.type == MediaWrapper.TYPE_AUDIO
    ArtworkAccent(rememberArtworkColor(media.takeIf { music })) {
        when {
            navKind == NavKind.Sidebar -> TransportBar(info, { progress }, actions, artwork, modifier, queueOpen, transparent = transparent)
            navKind == NavKind.BottomBar && phonePlayer != null && fullPlayer != null -> ExpandablePlayer(
                state = phonePlayer,
                mini = { CompactTransportBar(info, { progress }, actions, artwork) },
                full = fullPlayer,
                modifier = modifier,
                expandable = music,
                onPrevious = if (music) actions::previous else null,
                onNext = if (music) actions::next else null,
            )
            navKind == NavKind.BottomBar -> CompactTransportBar(info, { progress }, actions, artwork, modifier)
            else -> MiniPlayer(info, { progress }, actions, artwork, modifier, queueOpen = queueOpen, transparent = transparent)
        }
    }
}

@Composable
private fun rememberPlayerBarInfo(media: MediaWrapper, playing: Boolean): PlayerBarInfo {
    val shuffling by PlaylistManager.shuffling.collectAsState()
    val repeating by PlaylistManager.repeating.collectAsState()
    val tech = rememberTechOf(media.uri?.toString())
    val video = media.type == MediaWrapper.TYPE_VIDEO
    val artist = media.artistName
    val links = remember(media) { MusicLinks.artistOf(media) to MusicLinks.albumOf(media) }
    return PlayerBarInfo(
        title = media.nowPlaying ?: media.title ?: "",
        subtitle = if (artist.isNullOrBlank()) stringResource(R.string.nag_now_playing) else artist,
        isVideo = video,
        playing = playing,
        lengthMs = media.length,
        format = tech?.mainAudio()?.let(::audioFormatLabel),
        shuffling = shuffling,
        repeating = repeating != PlaybackStateCompat.REPEAT_MODE_NONE,
        // the listener's own volume: a podcast show's boost (S-1104) plays over it and is never kept as theirs
        volume = PlaybackService.instance?.userVolume ?: 100,
        canCast = rememberCastingEnabled(),
        casting = rememberCastDevice(),
        chapters = rememberPlayingChapters(),
        album = if (links.second != null) media.albumName else null,
        linked = links.first != null && !artist.isNullOrBlank(),
        repeatOne = repeating == PlaybackStateCompat.REPEAT_MODE_ONE,
        rate = playbackRate(),
        live = media.length <= 0L && isStream(media.uri?.scheme),
    )
}

/** An address played from the network (a station, a stream), not a file of this device. */
internal fun isStream(scheme: String?): Boolean = scheme != null && scheme.lowercase() !in LocalSchemes

private val LocalSchemes = setOf("file", "content", "android.resource")

/**
 * Medium and expanded windows: what is playing, beside the rail of every screen (S-304). A tap on
 * the artwork or the words opens the full player. With a pointer, hovering it (or tabbing into it)
 * brings out the previous and stop buttons and a seek bar in place of the second line.
 * [transparent]: it lies in the shell's floating bar of a tablet (Desktop Kit 1.0), its progress
 * along the bottom. (1.0.2: a phone has [CompactTransportBar].)
 */
@Composable
fun MiniPlayer(
    info: PlayerBarInfo,
    progress: () -> Float,
    actions: PlayerBarActions,
    artwork: @Composable (Dp) -> Unit,
    modifier: Modifier = Modifier,
    queueOpen: Boolean = false,
    transparent: Boolean = false,
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
    val artSize = dimens.miniPlayerHeight - 20.dp
    val marks = rememberChapterMarks(info.chapters, info.lengthMs)
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (transparent) Modifier else Modifier.background(colors.chrome))
                .hoverable(hover)
                .onFocusChanged { focusInside = it.hasFocus }
                .testTag("mini-player"),
        ) {
            if (!transparent) {
                HorizontalRule()
                ProgressHairline(progress, info.lengthMs, info.playing, rate = info.rate)
            }
            Row(
                Modifier.fillMaxWidth().height(dimens.miniPlayerHeight).padding(horizontal = dimens.gapM),
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
                            } else if (info.casting != null) {
                                // S-1302: where it plays
                                CastingLine(info.casting)
                            } else {
                                SubtitleLine(info, progress, withFormat = true)
                            }
                        }
                    }
                    if (extras) {
                        // where the second line was, beside the click target rather than in it
                        SeekRow(
                            info,
                            progress,
                            actions::seekTo,
                            marks = marks,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = artSize + dimens.gapM, top = with(density) { titleHeight.toDp() })
                                .fillMaxWidth(),
                        )
                    }
                }
                val button = dimens.minTarget.coerceAtLeast(dimens.controlHeight)
                if (extras) TransportButton(NagIcons.Previous, stringResource(R.string.nag_previous), actions::previous, button, dimens.icon, strong = true, shortcut = ShortcutId.Previous)
                // S-1104: the chapters of a podcast episode
                if (extras && info.chapters.isNotEmpty()) ChapterButtons(actions::stepChapter)
                TransportButton(
                    if (info.playing) NagIcons.Pause else NagIcons.Play,
                    stringResource(if (info.playing) R.string.nag_pause else R.string.nag_play),
                    actions::togglePlayPause,
                    button,
                    dimens.icon,
                    strong = true,
                    shortcut = ShortcutId.PlayPause,
                )
                TransportButton(NagIcons.Next, stringResource(R.string.nag_next), actions::next, button, dimens.icon, strong = true, shortcut = ShortcutId.Next)
                // S-1302: lit while casting; with a pointer over the bar, to cast
                if (info.casting != null || (extras && info.canCast)) CastButton(info.casting, actions::cast)
                NagIconButton(NagIcons.Queue, stringResource(R.string.nag_queue_title), actions::openQueue, selected = queueOpen, shortcut = ShortcutId.PlayQueue)
                if (extras) NagIconButton(NagIcons.Close, stringResource(R.string.nag_stop), actions::stop, shortcut = ShortcutId.Stop)
            }
            if (transparent) ProgressHairline(progress, info.lengthMs, info.playing, rate = info.rate)
        }
    }
}

private val SeekRowHeight = 24.dp

/** "1:42 ━━━●──── −2:36": seeking from a bar, the times either side in even figures; [height] the row's. */
@Composable
private fun SeekRow(
    info: PlayerBarInfo,
    progress: () -> Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    marks: List<SliderMark> = emptyList(),
    height: Dp = SeekRowHeight,
) {
    ProgressTimeline(
        progress,
        info.lengthMs,
        onSeek,
        modifier.height(height),
        playing = info.playing,
        rate = info.rate,
        marks = marks,
        style = TimelineStyle.Bar,
    )
}

/**
 * The second line of a bar: the artist (and the format), or for a podcast episode with chapters the chapter playing
 * (S-1104). Reads the progress itself, so that only this line changes as the episode goes on. With [actions] (the
 * transport bar's line, without the format), the library's music has its artist and album as links to their tracks.
 */
@Composable
private fun SubtitleLine(info: PlayerBarInfo, progress: () -> Float, withFormat: Boolean, actions: PlayerBarActions? = null) {
    val chapter = if (info.chapters.isEmpty()) null else chapterNameAt(info.chapters, info.lengthMs, progress())
    if (actions != null && info.linked && chapter == null && !withFormat) {
        val colors = NagTheme.colors
        val style = NagTheme.type.labelSmall
        Row(Modifier.testTag("bar-subtitle"), verticalAlignment = Alignment.CenterVertically) {
            ArtistAlbumLine(
                info.subtitle,
                info.album,
                style,
                colors.textTertiary,
                Modifier.weight(1f, fill = false),
                onArtist = actions::openArtist,
                onAlbum = actions::openAlbum,
                artistLabel = stringResource(R.string.nag_music_open_artist),
                albumLabel = stringResource(R.string.nag_music_open_album),
            )
        }
        return
    }
    Text(
        listOfNotNull(info.subtitle, chapter ?: info.format.takeIf { withFormat }).joinToString(" · "),
        style = NagTheme.type.labelSmall,
        color = NagTheme.colors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("bar-subtitle"),
    )
}

/**
 * Large windows: the transport bar of the mockups across the bottom of every screen (S-304). The
 * artwork and words open the full player (1.0.2: the big player over the window); in the middle the
 * transport and the seek bar, which shows the time under the pointer; at the end the format, the
 * volume and the player. [transparent]: it lies in the shell's floating bar (Desktop Kit 1.0),
 * without a surface or a rule.
 */
@Composable
fun TransportBar(
    info: PlayerBarInfo,
    progress: () -> Float,
    actions: PlayerBarActions,
    artwork: @Composable (Dp) -> Unit,
    modifier: Modifier = Modifier,
    queueOpen: Boolean = false,
    transparent: Boolean = false,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val openLabel = stringResource(R.string.nag_open_player)
    Column(modifier.fillMaxWidth().then(if (transparent) Modifier else Modifier.background(colors.chrome)).testTag("transport-bar")) {
        if (!transparent) HorizontalRule()
        // by touch the artist's and album's links take a finger's height (S-1407): the bar grows a little for them
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = dimens.gapL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NowPlayingWords(info, progress, actions, artwork, 48.dp, Modifier.weight(1f), withFormat = false, links = true)
            Spacer(Modifier.width(dimens.gapL))
            Column(Modifier.widthIn(min = 360.dp, max = 640.dp).weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                TransportControls(info.playing, info.shuffling, info.repeating, actions, repeatOne = info.repeatOne, horizontalArrangement = Arrangement.spacedBy(8.dp))
                BarTimeline(info, progress, actions, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(dimens.gapL))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                // S-1104: the chapters of a podcast episode
                if (info.chapters.isNotEmpty()) ChapterButtons(actions::stepChapter)
                info.format?.let { FormatChip(it) }
                Spacer(Modifier.width(dimens.gapS))
                // S-1302: a renderer's volume is the device's own
                if (info.casting == null) VolumeControl(info.volume, actions::setVolume)
                // S-1007: while the sleep timer runs, what is left
                SleepTimerButton(onlyWhileRunning = true)
                if (info.canCast || info.casting != null) CastButton(info.casting, actions::cast)
                NagIconButton(NagIcons.Queue, stringResource(R.string.nag_queue_title), actions::openQueue, selected = queueOpen, shortcut = ShortcutId.PlayQueue)
                NagIconButton(NagIcons.OpenWindow, openLabel, actions::openPlayer, modifier = Modifier.testTag("transport-open-player"))
            }
        }
    }
}

/**
 * 1.0.2: the phone's bar is the tablet's [TransportBar] laid out for a phone ("the same bar"): the
 * same floating card, the artwork and the words that open the player (on a phone the full player
 * grows from the bar), the transport, and under them the tablet's seek bar in the artwork's colour
 * between the time played and the time left, which a finger drags to seek with the time following
 * it; a station on the air is shown live instead ([BarTimeline]). Shuffle and repeat join the
 * transport where the window leaves the words room for them ([showsModes]); while casting the
 * second line names the device, as the tablet's does.
 */
@Composable
fun CompactTransportBar(
    info: PlayerBarInfo,
    progress: () -> Float,
    actions: PlayerBarActions,
    artwork: @Composable (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = NagTheme.dimens
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = COMPACT_MARGIN, vertical = 6.dp)) {
        val button = TransportSize.Compact.button.coerceAtLeast(dimens.minTarget)
        val modes = showsModes(maxWidth, button, dimens.gapM)
        Column(Modifier.fillMaxWidth().floatingPlayerCard().testTag("mini-player")) {
            Row(
                Modifier.fillMaxWidth().padding(start = COMPACT_INSET - 4.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NowPlayingWords(info, progress, actions, artwork, COMPACT_ARTWORK, Modifier.weight(1f), withFormat = true, links = false)
                TransportControls(
                    info.playing, info.shuffling, info.repeating, actions,
                    size = TransportSize.Compact,
                    repeatOne = info.repeatOne,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    modes = modes,
                )
            }
            // its own node: the times with the seek bar, apart from the card that opens the player; a finger's height
            BarTimeline(
                info, progress, actions,
                Modifier.fillMaxWidth().padding(horizontal = COMPACT_INSET).semantics(mergeDescendants = true) {}.testTag("mini-player-seek"),
                height = dimens.minTarget.coerceAtLeast(SeekRowHeight),
            )
        }
    }
}

/**
 * Whether a phone's bar whose card is [width] wide has room for shuffle and repeat beside previous, play and next ([button] each):
 * only if the artwork and the words keep [WORDS_MIN] after them; on a phone held upright they do not (1.0.2).
 */
internal fun showsModes(width: Dp, button: Dp, gap: Dp): Boolean =
    width - COMPACT_INSET - 4.dp - 8.dp - COMPACT_ARTWORK - gap - button * 5 >= WORDS_MIN

/** The phone's bar: its margin to the window's edges, the room inside its card, the artwork. */
private val COMPACT_MARGIN = 8.dp
private val COMPACT_INSET = 12.dp
private val COMPACT_ARTWORK = 40.dp

/** The least the words of a phone's bar keep when shuffle and repeat join the transport. */
private val WORDS_MIN = 160.dp

/**
 * The artwork with the title and the second line (the artist, or the device cast to), which open the player: the
 * transport bar's start and the phone's bar's. With [links] the library's artist and album open their tracks; with
 * [withFormat] the second line ends with the format.
 */
@Composable
private fun NowPlayingWords(
    info: PlayerBarInfo,
    progress: () -> Float,
    actions: PlayerBarActions,
    artwork: @Composable (Dp) -> Unit,
    artSize: Dp,
    modifier: Modifier,
    withFormat: Boolean,
    links: Boolean,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val open = remember { MutableInteractionSource() }
    val openState = open.collectState()
    Row(
        modifier = modifier
            .focusRing(openState, RoundedCornerShape(dimens.radiusControl))
            .background(if (openState.hovered) colors.hover else Color.Transparent, RoundedCornerShape(dimens.radiusControl))
            .nagClickable(open, actions::openPlayer, onClickLabel = stringResource(R.string.nag_open_player))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        artwork(artSize)
        Spacer(Modifier.width(dimens.gapM))
        Column(Modifier.weight(1f)) {
            Text(info.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (info.casting != null) {
                CastingLine(info.casting)
            } else {
                SubtitleLine(info, progress, withFormat = withFormat, actions = actions.takeIf { links })
            }
        }
    }
}

/**
 * The bars' seek bar with the times either side ([SeekRow]), [height] high; for a stream on the air, that it is live
 * ([LiveLine]).
 */
@Composable
private fun BarTimeline(info: PlayerBarInfo, progress: () -> Float, actions: PlayerBarActions, modifier: Modifier, height: Dp = SeekRowHeight) {
    if (info.live) {
        LiveLine(modifier, height)
    } else {
        SeekRow(info, progress, actions::seekTo, modifier, marks = rememberChapterMarks(info.chapters, info.lengthMs), height = height)
    }
}

/**
 * 1.0.2: a station on the air has nothing to seek: "● Canlı yayın" where the time played would be, and the track
 * whole in the accent, as tall as the seek bar it stands for ([height]) so that the bar does not change its height.
 */
@Composable
private fun LiveLine(modifier: Modifier, height: Dp) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val accent = animatedAccent(colors.accent, "liveAccent")
    Row(modifier.height(height), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).drawBehind { drawCircle(accent.value) })
        Spacer(Modifier.width(dimens.gapS))
        Text(
            stringResource(R.string.nag_radio_live),
            style = NagTheme.type.caption,
            color = colors.accentText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("bar-live"),
        )
        Spacer(Modifier.width(dimens.gapS))
        Box(
            Modifier
                .weight(1f)
                .height(3.dp)
                .drawBehind { drawRoundRect(accent.value.copy(alpha = 0.45f), cornerRadius = CornerRadius(size.height / 2)) },
        )
    }
}

/** S-1302: "Casting to Salon TV", in place of the artist while a renderer plays. */
@Composable
private fun CastingLine(device: CastDevice) {
    Text(
        stringResource(R.string.nag_cast_casting_to, device.name),
        style = NagTheme.type.labelSmall,
        color = NagTheme.colors.accentText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
        overflow = TextOverflow.Ellipsis,
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
