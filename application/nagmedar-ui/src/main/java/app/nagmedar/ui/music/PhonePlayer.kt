package app.nagmedar.ui.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.nagmedar.audio.Waveform
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.rememberSignalPath
import app.nagmedar.ui.cast.CastButton
import app.nagmedar.ui.cast.CastDevice
import app.nagmedar.ui.cast.Casting
import app.nagmedar.ui.cast.CastingStrip
import app.nagmedar.ui.cast.rememberCastDevice
import app.nagmedar.ui.cast.rememberCastingEnabled
import app.nagmedar.ui.components.HoverTooltip
import app.nagmedar.ui.components.IconMenuButton
import app.nagmedar.ui.components.LocalPlayerSheet
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.covers.rememberCoverMenu
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.lyrics.PlayingLyricLine
import app.nagmedar.ui.lyrics.PlayingLyrics
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.ChapterStrip
import app.nagmedar.ui.playback.PlayFrom
import app.nagmedar.ui.playback.PlaySource
import app.nagmedar.ui.playback.PlaySources
import app.nagmedar.ui.playback.PlayerBarActions
import app.nagmedar.ui.playback.ProgressTimeline
import app.nagmedar.ui.playback.ServicePlayerBarActions
import app.nagmedar.ui.playback.TimelineStyle
import app.nagmedar.ui.playback.TransportControls
import app.nagmedar.ui.playback.TransportSize
import app.nagmedar.ui.playback.playbackRate
import app.nagmedar.ui.playback.rememberChapterMarks
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.playback.rememberPlayQueue
import app.nagmedar.ui.playback.rememberPlaybackProgress
import app.nagmedar.ui.playback.rememberPlayingChapters
import app.nagmedar.ui.rememberNagmedarPreferences
import app.nagmedar.ui.sleep.SleepTimerButton
import app.nagmedar.ui.sleep.SleepTimerDialog
import app.nagmedar.ui.stats.TrackStatsIndex
import app.nagmedar.ui.stats.statsKey
import app.nagmedar.ui.theme.ArtworkAccent
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.visual.PlayingVisualizer
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.Notices
import org.videolan.vlc.media.PlaylistManager

/**
 * S-1002: the phone's music player, over the whole window, on a backdrop tinted from the top with the artwork's colour
 * ([tint]). On top, closing it, what it plays from ([from]: an album, an artist, a playlist, the queue) and its menu
 * (the lyrics, the visualiser, the sleep timer, the equaliser, the rating); the artwork large, settling back a little
 * while paused; the line of the lyrics being sung; the title with the artist and the album, and the button that puts
 * the track in a playlist; a slim timeline over a faint band of the waveform; the controls low, where one thumb reaches
 * them; under them where it plays (casting, the format and the output), sharing and the queue. Nothing in it is a word
 * that could wrap. The artwork swiped left plays the next track and right the previous; swiped down, or Back, closes
 * the player: in the phone's expanding sheet ([LocalPlayerSheet]) the sheet's own drag and Back do, so that the
 * player goes down with the finger in one piece (1.0); standing alone (a tablet's overlay) its own, from anywhere on
 * it ([drop]). Held sideways, the artwork is on the left and the rest beside it. The artist and the album open their
 * tracks in the music view ([onOpenArtist], [onOpenAlbum]). The same artwork, words and controls as the lock screen's,
 * from the same media session.
 *
 * 1.0.2, the big player of a tablet and a desktop window (both sides at least [BIG_MIN]): wider than high, the artwork
 * as large as the height allows on the left and the words and the controls in a column of a reading width beside it,
 * the pair centred under the top row; upright, the phone's composition scaled up. Larger words and controls, for a
 * screen seen from further away.
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
    /** S-808: "Puan ver…" in the menu, which rates what plays; null for what is not rated (a stream). */
    onRate: (() -> Unit)? = null,
    /** S-1004: the track's lyrics, in place of the artwork while [lyricsShown]; the menu turns them on and off. */
    lyrics: (@Composable (Modifier) -> Unit)? = null,
    lyricsShown: Boolean = false,
    onLyricsShown: ((Boolean) -> Unit)? = null,
    /** S-1302: VLC's casting setting is on: the bottom row has the cast button. */
    canCast: Boolean = false,
    /** S-1302: the device a renderer plays the track on; null while it plays here. */
    casting: CastDevice? = null,
    onStopCasting: () -> Unit = {},
    /** S-611: the visualiser, in place of the artwork while [visualizerShown] (the lyrics come first); the menu turns it on and off. */
    visualizer: (@Composable (Modifier) -> Unit)? = null,
    visualizerShown: Boolean = false,
    onVisualizerShown: ((Boolean) -> Unit)? = null,
    repeatOne: Boolean = false,
    onOpenArtist: (() -> Unit)? = null,
    onOpenAlbum: (() -> Unit)? = null,
    /** The artwork's colour ([rememberArtworkColor]); null: the plain background. */
    tint: Color? = null,
    /** Where what plays comes from; null: "Şimdi çalıyor". */
    from: PlaySource? = null,
    /** S-1004: the line of the synced lyrics being sung, under the artwork (a tap opens the lyrics); null for none. */
    lyricLine: (@Composable (Modifier) -> Unit)? = null,
    /** S-804: the round button beside the title, which puts what plays in a playlist. */
    onAddToPlaylist: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    /** The equaliser's page, from the menu. */
    onEqualizer: (() -> Unit)? = null,
    /** The player has the whole window: its backdrop goes on under the system's bars. */
    edgeToEdge: Boolean = false,
    /** Standing alone, how far it is pulled down to close; given by a window that watches it (1.0.2). */
    drop: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
    /** 1.0.2: a desktop window's caption bar is this high above the player: its backdrop goes on under it. */
    captionAbove: Dp = 0.dp,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val motion = NagTheme.motion
    val res = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    // in the phone's sheet, a drag down and Back are the sheet's (1.0); standing alone, the player's own
    val hosted = LocalPlayerSheet.current != null
    if (!hosted) BackHandler(onBack = onClose)
    val latestClose by rememberUpdatedState(onClose)
    // S-1007: the sleep timer's window, from the menu
    var sleepOpen by rememberSaveable { mutableStateOf(false) }
    val top = remember(colors, tint) { tint?.let { backdropTop(colors, it) } }
    val insets = WindowInsets.safeDrawing
    val menu: () -> List<MenuEntry> = {
        listOfNotNull(
            // S-1004
            if (lyrics != null && onLyricsShown != null) {
                MenuEntry.Item(res.getString(R.string.nag_lyrics_title), { onLyricsShown(!lyricsShown) }, icon = NagIcons.Lyrics, checked = lyricsShown)
            } else {
                null
            },
            // S-611: the lyrics come first; turned on, the visualiser takes their place
            if (visualizer != null && onVisualizerShown != null) {
                MenuEntry.Item(
                    res.getString(R.string.nag_vis_title),
                    {
                        onVisualizerShown(!visualizerShown || lyricsShown)
                        if (lyricsShown) onLyricsShown?.invoke(false)
                    },
                    icon = NagIcons.Visualizer,
                    checked = visualizerShown && !lyricsShown,
                )
            } else {
                null
            },
            MenuEntry.Item(res.getString(R.string.nag_sc_sleep_timer), { sleepOpen = true }, icon = NagIcons.Sleep),
            onEqualizer?.let { MenuEntry.Item(res.getString(R.string.nag_sc_equalizer), it, icon = NagIcons.Equalizer) },
            // S-808
            onRate?.let { MenuEntry.Item(res.getString(R.string.nag_rate), it, icon = NagIcons.Star) },
        )
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .offset { IntOffset(0, drop.value.roundToInt()) }
            // standing alone, pulled down from anywhere (what in it moves by a drag of its own keeps it) it closes
            .then(if (hosted) Modifier else Modifier.pointerInput(drop) { pullDown(drop, scope, motion.spatial()) { latestClose() } })
            .drawBehind {
                // with the whole window, under the system's bars too: the backdrop has no seam; a desktop window's
                // caption bar over it has it too
                val above = (if (edgeToEdge) insets.getTop(this).toFloat() else 0f) + captionAbove.toPx()
                val below = if (edgeToEdge) insets.getBottom(this).toFloat() else 0f
                val left = if (edgeToEdge) insets.getLeft(this, layoutDirection).toFloat() else 0f
                val right = if (edgeToEdge) insets.getRight(this, layoutDirection).toFloat() else 0f
                val origin = Offset(-left, -above)
                val area = Size(size.width + left + right, size.height + above + below)
                if (top == null) {
                    drawRect(colors.background, origin, area)
                } else {
                    drawRect(Brush.verticalGradient(0f to top, FADE to colors.background, startY = -above, endY = size.height + below), origin, area)
                }
            }
            // over the window: nothing under it takes a touch
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("phone-player"),
    ) {
        val sideways = maxWidth > maxHeight && maxHeight < SIDEWAYS_MAX_HEIGHT && maxWidth >= SIDEWAYS_MIN_WIDTH
        // 1.0.2: a tablet's and a desktop's window, wider than high or upright
        val big = minOf(maxWidth, maxHeight) >= BIG_MIN
        val wide = big && maxWidth > maxHeight
        val narrow = maxWidth < NARROW_WIDTH
        val gutter = when {
            big -> BIG_GUTTER
            narrow -> dimens.gapL
            else -> dimens.gapXl
        }
        val transport = when {
            big -> TransportSize.Large
            narrow || sideways -> TransportSize.Narrow
            else -> TransportSize.Phone
        }
        val parts = PlayerParts(
            info = info,
            actions = actions,
            onClose = onClose,
            drop = drop,
            hosted = hosted,
            artwork = artwork,
            stage = when {
                lyrics != null && lyricsShown -> lyrics
                visualizer != null && visualizerShown -> visualizer
                else -> null
            },
            lyricLine = lyricLine.takeIf { !(lyrics != null && lyricsShown) },
            links = Links(onOpenArtist, onOpenAlbum),
            onAddToPlaylist = onAddToPlaylist,
            big = big,
            artworkMax = when {
                wide -> WIDE_ARTWORK_MAX
                big -> BIG_ARTWORK_MAX
                else -> ARTWORK_MAX
            },
        )
        val topRow: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().padding(vertical = dimens.gapXs), verticalAlignment = Alignment.CenterVertically) {
                NagIconButton(NagIcons.ChevronDown, stringResource(R.string.nag_player_close), onClose, modifier = Modifier.testTag("phone-player-close"))
                Overline(from, Modifier.weight(1f).padding(horizontal = dimens.gapXs))
                IconMenuButton(NagIcons.MoreVertical, stringResource(R.string.nag_more_actions), menu, modifier = Modifier.testTag("phone-player-more"))
            }
            // S-1302
            if (casting != null) CastingStrip(casting, onStopCasting, Modifier.padding(bottom = dimens.gapS))
        }
        val controls: @Composable ColumnScope.() -> Unit = {
            ProgressTimeline(
                progress, info.lengthMs, actions::seekTo,
                playing = info.playing, rate = info.rate, waveform = waveform,
                marks = rememberChapterMarks(info.chapters, info.lengthMs), style = TimelineStyle.Phone,
                label = stringResource(R.string.nag_music_waveform),
            )
            // S-1104: the chapters of a podcast episode
            ChapterStrip(info.chapters, info.lengthMs, progress, actions::stepChapter, actions::seekToMs)
            // the controls, low: the thumb's
            TransportControls(
                playing = info.playing,
                shuffling = shuffling,
                repeating = repeating,
                actions = actions,
                modifier = Modifier.fillMaxWidth().padding(vertical = dimens.gapS),
                size = transport,
                repeatOne = repeatOne,
                horizontalArrangement = Arrangement.SpaceBetween,
            )
            BottomRow(info.format, actions, canCast || casting != null, casting, onShare)
        }
        if (wide) {
            // 1.0.2: the top row across the window; under it the artwork as large as the height allows and the words
            // and the controls beside it at a reading width, the pair in the middle
            Column(Modifier.fillMaxSize().padding(horizontal = gutter)) {
                topRow()
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(bottom = dimens.gapXl), contentAlignment = Alignment.Center) {
                    val column = (maxWidth * WIDE_CONTROLS_SHARE).coerceIn(WIDE_CONTROLS_MIN, WIDE_CONTROLS_MAX)
                    val gap = (maxWidth * WIDE_GAP_SHARE).coerceIn(dimens.gapXl, WIDE_GAP_MAX)
                    val side = minOf(maxHeight, maxWidth - column - gap, WIDE_ARTWORK_MAX).coerceAtLeast(0.dp)
                    val high = maxHeight
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                        Stage(parts, Modifier.size(side))
                        // a window too low for all of it scrolls
                        Column(Modifier.width(column).heightIn(max = high).verticalScroll(rememberScrollState()).testTag("player-controls")) {
                            parts.lyricLine?.invoke(Modifier.fillMaxWidth())
                            TitleRow(parts)
                            Spacer(Modifier.height(dimens.gapL))
                            controls()
                        }
                    }
                }
            }
        } else if (sideways) {
            // held sideways: the artwork on the left, as high as the window, the rest beside it, keeping its width (at
            // most a reading width: the pair in the middle of a wide and low desktop window)
            val high = maxHeight - dimens.gapS * 2
            val side = minOf(high, maxWidth - gutter * 2 - dimens.gapXl - SIDEWAYS_CONTROLS_WIDTH).coerceAtLeast(0.dp)
            Row(
                Modifier.fillMaxSize().padding(horizontal = gutter, vertical = dimens.gapS),
                horizontalArrangement = Arrangement.spacedBy(dimens.gapXl, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stage(parts, Modifier.size(side))
                // spread over the height; a window too low for it scrolls
                Column(
                    Modifier.weight(1f, fill = false).widthIn(max = WIDE_CONTROLS_MAX).fillMaxHeight().verticalScroll(rememberScrollState()).heightIn(min = high),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    topRow()
                    Column {
                        parts.lyricLine?.invoke(Modifier.fillMaxWidth())
                        TitleRow(parts)
                    }
                    Column(content = controls)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                topRow()
                Stage(parts, Modifier.fillMaxWidth().weight(1f).padding(vertical = dimens.gapS))
                Column(Modifier.widthIn(max = if (big) BIG_ARTWORK_MAX else CONTENT_MAX_WIDTH).fillMaxWidth().padding(bottom = if (big) dimens.gapXl else dimens.gapS)) {
                    parts.lyricLine?.invoke(Modifier.fillMaxWidth())
                    TitleRow(parts)
                    controls()
                }
            }
        }
    }
    if (sleepOpen) SleepTimerDialog { sleepOpen = false }
}

/** What the player's parts share, whichever way the phone is held. */
private class PlayerParts(
    val info: NowPlayingInfo,
    val actions: PlayerBarActions,
    val onClose: () -> Unit,
    val drop: Animatable<Float, AnimationVector1D>,
    /** In the phone's expanding sheet: a vertical drag on the artwork is the sheet's, not [drop]'s. */
    val hosted: Boolean,
    val artwork: @Composable (Dp) -> Unit,
    /** The lyrics or the visualiser in the artwork's place; null: the artwork. */
    val stage: (@Composable (Modifier) -> Unit)?,
    val lyricLine: (@Composable (Modifier) -> Unit)?,
    val links: Links,
    val onAddToPlaylist: (() -> Unit)?,
    /** A tablet's or a desktop's big player (1.0.2): larger words. */
    val big: Boolean,
    /** The artwork grows no larger than this. */
    val artworkMax: Dp,
)

/** "Albümden çalınıyor" over the album's name; "Şimdi çalıyor" alone when the queue does not say where from. */
@Composable
private fun Overline(from: PlaySource?, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val label = when (from?.from) {
        PlayFrom.Album -> R.string.nag_player_from_album
        PlayFrom.Artist -> R.string.nag_player_from_artist
        PlayFrom.Playlist -> R.string.nag_player_from_playlist
        PlayFrom.Queue -> R.string.nag_player_from_queue
        null -> R.string.nag_now_playing
    }
    val name = from?.name?.takeIf { it.isNotBlank() }
    Column(modifier.semantics(mergeDescendants = true) {}.testTag("phone-player-from"), horizontalAlignment = Alignment.CenterHorizontally) {
        if (name == null) {
            Text(stringResource(label), style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        } else {
            Text(stringResource(label), style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(name, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/**
 * The artwork, as large as the room allows (it comes forward while playing, settles back paused): swiped sideways it
 * changes the track, down it closes the player; or the lyrics or the visualiser in its place, which take their own
 * touches.
 */
@Composable
private fun Stage(parts: PlayerParts, modifier: Modifier) {
    val stage = parts.stage
    if (stage != null) {
        stage(modifier)
        return
    }
    val dimens = NagTheme.dimens
    val motion = NagTheme.motion
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val swipe = remember { Animatable(0f) }
    val drop = parts.drop
    // a swipe that changed the track: the artwork as it was when let go stays there and fades over the next one, which
    // is in its place at once, so the page never shows its background between them (the picture is kept while swiping)
    val picture = rememberGraphicsLayer()
    var handing by remember { mutableStateOf(false) }
    val leaving = remember { Animatable(1f) }
    var handover by remember { mutableStateOf<Job?>(null) }
    val latestActions by rememberUpdatedState(parts.actions)
    val latestClose by rememberUpdatedState(parts.onClose)
    val next = stringResource(R.string.nag_next)
    val previous = stringResource(R.string.nag_previous)
    val close = stringResource(R.string.nag_player_close)
    val settle = motion.spatial<Float>()
    BoxWithConstraints(
        modifier
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(next) { latestActions.next(); true },
                    CustomAccessibilityAction(previous) { latestActions.previous(); true },
                    CustomAccessibilityAction(close) { latestClose(); true },
                )
            }
            .pointerInput(parts.hosted) {
                val sideways = with(density) { SWIPE.toPx() }
                val down = with(density) { CLOSE.toPx() }
                var dx = 0f
                var dy = 0f
                val start: (Offset) -> Unit = {
                    dx = 0f
                    dy = 0f
                    // a swipe again before the last one's picture has gone: it goes now
                    if (handover?.isActive == true) {
                        handover?.cancel()
                        scope.launch { swipe.snapTo(0f) }
                    }
                }
                val end: () -> Unit = {
                    val horizontal = abs(dx) > abs(dy)
                    val change: (() -> Unit)? = when {
                        horizontal && dx <= -sideways -> latestActions::next
                        horizontal && dx >= sideways -> latestActions::previous
                        else -> null
                    }
                    if (change != null) {
                        handover = scope.launch {
                            handing = true
                            leaving.snapTo(1f)
                            change()
                            launch { drop.animateTo(0f, settle) }
                            try {
                                leaving.animateTo(0f, motion.exit(NagMotion.MEDIUM))
                            } finally {
                                handing = false
                            }
                            swipe.snapTo(0f)
                        }
                    } else {
                        scope.launch {
                            // closing, it goes down from where it was let go
                            if (!horizontal && dy >= down) {
                                latestClose()
                                return@launch
                            }
                            // back to rest on the spatial spring, both at once
                            launch { swipe.animateTo(0f, settle) }
                            drop.animateTo(0f, settle)
                        }
                    }
                }
                val cancel: () -> Unit = {
                    scope.launch {
                        launch { swipe.animateTo(0f, settle) }
                        drop.animateTo(0f, settle)
                    }
                }
                if (parts.hosted) {
                    // in the sheet only the sideways swipe is the artwork's: a drag down moves the sheet itself
                    detectHorizontalDragGestures(onDragStart = start, onDragEnd = end, onDragCancel = cancel) { change, amount ->
                        change.consume()
                        dx += amount
                        scope.launch { swipe.snapTo(dx) }
                    }
                } else {
                    detectDragGestures(onDragStart = start, onDragEnd = end, onDragCancel = cancel) { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                        scope.launch {
                            if (abs(dx) > abs(dy)) swipe.snapTo(dx) else drop.snapTo(dy.coerceAtLeast(0f))
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight, parts.artworkMax)
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        // playing, the artwork comes forward; paused, it settles back a little
        val scale by animateFloatAsState(if (parts.info.playing) 1f else PAUSED_SCALE, motion.emphasized(), label = "artwork")
        val lift by animateDpAsState(if (parts.info.playing) dimens.gapXl else dimens.gapXs, motion.emphasized(), label = "lift")
        Box(
            Modifier
                .offset { IntOffset(if (handing) 0 else swipe.value.roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // swiped aside, it fades as it goes
                    alpha = if (handing) 1f else 1f - (abs(swipe.value) / widthPx).coerceIn(0f, SWIPE_FADE)
                    rotationZ = if (handing) 0f else swipe.value / widthPx * 4f
                }
                .shadow(lift, RoundedCornerShape(dimens.radiusCard), clip = false)
                .drawWithContent {
                    if (!handing && swipe.value != 0f) {
                        picture.record { this@drawWithContent.drawContent() }
                        drawLayer(picture)
                    } else {
                        drawContent()
                    }
                }
                .testTag("phone-artwork"),
        ) { parts.artwork(side) }
        if (handing) {
            Box(
                Modifier
                    .size(side)
                    .offset { IntOffset(swipe.value.roundToInt(), 0) }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = (1f - (abs(swipe.value) / widthPx).coerceIn(0f, SWIPE_FADE)) * leaving.value
                        rotationZ = swipe.value / widthPx * 4f
                    }
                    .drawBehind { drawLayer(picture) },
            )
        }
    }
}

/** The title on one line with the artist and the album under it (links to their tracks), and the round "Listeye ekle…". */
@Composable
private fun TitleRow(parts: PlayerParts) {
    val colors = NagTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // another track's words cross-fade in, as its artwork does
        Crossfade(
            Triple(parts.info.title, parts.info.artist, parts.info.album),
            Modifier.weight(1f),
            animationSpec = NagTheme.motion.standard(NagMotion.MEDIUM),
            label = "title",
        ) { (title, artist, album) ->
            Column {
                Text(
                    title,
                    style = if (parts.big) NagTheme.type.display else NagTheme.type.headline,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("phone-title"),
                )
                PlayingArtistAlbum(parts.info.copy(artist = artist, album = album), parts.links, if (parts.big) NagTheme.type.title.copy(fontWeight = FontWeight.Normal) else NagTheme.type.body)
            }
        }
        parts.onAddToPlaylist?.let { add ->
            RoundIconButton(NagIcons.PlaylistAdd, stringResource(R.string.nag_pl_add), add, Modifier.testTag("phone-add-to-playlist"))
        }
    }
}

/** An icon in a ring, in a finger's target: the title's "Listeye ekle…". */
@Composable
private fun RoundIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    Box(
        modifier
            .size(dimens.minTarget.coerceAtLeast(dimens.controlHeight))
            .nagClickable(interaction, onClick, onClickLabel = contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(dimens.iconLarge + dimens.gapM)
                .focusRing(state, CircleShape)
                .background(stateBackground(state), CircleShape)
                .border(RING, colors.textSecondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = colors.text, modifier = Modifier.size(dimens.icon))
        }
        HoverTooltip(state.hovered && !state.pressed, contentDescription, null)
    }
}

/**
 * Where it plays and what else: the cast button (S-1302) and the format and the output on the left; the sleep timer's
 * time while it runs (S-1007), sharing and the queue on the right.
 */
@Composable
private fun BottomRow(format: String?, actions: PlayerBarActions, cast: Boolean, casting: CastDevice?, onShare: (() -> Unit)?) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (cast) CastButton(casting, actions::cast)
        // 1.0.1: on one line where it fits; on a phone's width the source over the output, each whole, instead of
        // "FLAC 16/44.1 · AAudi…"
        BoxWithConstraints(Modifier.weight(1f).padding(horizontal = dimens.gapXs)) {
            val text = format.orEmpty()
            val style = NagTheme.type.mono
            val measurer = rememberTextMeasurer()
            val width = constraints.maxWidth
            val fits = remember(text, style, width, measurer) {
                measurer.measure(text, style, softWrap = false, maxLines = 1).size.width <= width
            }
            Text(
                if (fits) text else formatOnTwoLines(text),
                style = style,
                color = colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("phone-format"),
            )
        }
        SleepTimerButton(onlyWhileRunning = true)
        if (onShare != null) NagIconButton(NagIcons.Share, stringResource(R.string.nag_player_share), onShare, modifier = Modifier.testTag("phone-share"))
        NagIconButton(NagIcons.Queue, stringResource(R.string.nag_queue_title), actions::openQueue, shortcut = ShortcutId.PlayQueue)
    }
}

/**
 * The player's format line on two lines (1.0.1): the source on the first, how it is played on the
 * second ("FLAC 16/44.1" over "AAudio · 48 kHz · Bit-perfect"); a line without parts stays as it is.
 */
internal fun formatOnTwoLines(format: String): String = format.replaceFirst(FORMAT_SEPARATOR, "\n")

private const val FORMAT_SEPARATOR = " · "

/** How small the artwork settles while paused. */
private const val PAUSED_SCALE = 0.88f

/** How far the artwork fades as it is swiped aside. */
private const val SWIPE_FADE = 0.6f

/** A sideways swipe this long changes the track. */
private val SWIPE = 80.dp

/** A swipe down this long closes the player; or this fast. */
private val CLOSE = 140.dp
private val CLOSE_FLING = 1_000.dp

/**
 * The big player standing alone (1.0.2) pulled down from anywhere on it: it follows the finger ([drop]) and closes
 * ([close]) past [CLOSE] or flung down; let go before, it springs back on [settle]. What moves by a drag of its own (the
 * seek bar, the lyrics, the artwork) keeps it: a drag it took is not this one's.
 */
private suspend fun PointerInputScope.pullDown(
    drop: Animatable<Float, AnimationVector1D>,
    scope: CoroutineScope,
    settle: AnimationSpec<Float>,
    close: () -> Unit,
) {
    val tracker = VelocityTracker()
    // the player moves with the finger: the way it went, not where it is on the player, gives the speed
    var pulled = 0f
    detectVerticalDragGestures(
        onDragStart = {
            tracker.resetTracking()
            pulled = drop.value
        },
        onDragEnd = {
            val velocity = tracker.calculateVelocity().y
            if (drop.value >= CLOSE.toPx() || (velocity >= CLOSE_FLING.toPx() && drop.value > 0f)) {
                close()
            } else {
                scope.launch { drop.animateTo(0f, settle, initialVelocity = velocity) }
            }
        },
        onDragCancel = { scope.launch { drop.animateTo(0f, settle) } },
    ) { change, amount ->
        change.consume()
        pulled = (pulled + amount).coerceAtLeast(0f)
        tracker.addPosition(change.uptimeMillis, Offset(0f, pulled))
        scope.launch { drop.snapTo(pulled) }
    }
}

/** The artwork and the rows under it grow no larger than this (a phone, a small tablet held upright). */
private val ARTWORK_MAX = 560.dp
private val CONTENT_MAX_WIDTH = 560.dp

/** 1.0.2: a window at least this wide and high has the big player (a tablet's, a desktop's), its gutters this wide. */
private val BIG_MIN = 600.dp
private val BIG_GUTTER = 48.dp

/** The big player upright: the artwork and the rows under it grow no larger than this. */
private val BIG_ARTWORK_MAX = 680.dp

/** The big player wider than high: the artwork's largest side; the column beside it, its share of the width between these; the gap between them. */
private val WIDE_ARTWORK_MAX = 760.dp
private const val WIDE_CONTROLS_SHARE = 0.38f
private val WIDE_CONTROLS_MIN = 360.dp
private val WIDE_CONTROLS_MAX = 520.dp
private const val WIDE_GAP_SHARE = 0.05f
private val WIDE_GAP_MAX = 96.dp

/** Narrower than this, the gutters and the controls are smaller. */
private val NARROW_WIDTH = 360.dp

/** Wider than high, lower than this and at least this wide: a phone held sideways; the controls keep this much width beside the artwork. */
private val SIDEWAYS_MAX_HEIGHT = 600.dp
private val SIDEWAYS_MIN_WIDTH = 560.dp
private val SIDEWAYS_CONTROLS_WIDTH = 320.dp

/** How far down the artwork's colour fades into the background. */
private const val FADE = 0.75f

/** The ring of the round button. */
private val RING = 1.5.dp

/**
 * The phone player on the playback service; [onQueue] opens "Sıradaki" over it, [onFullScreenLyrics] the lyrics over
 * the whole window (S-1004; the line under the artwork too), [onFullScreenVisualizer] the visualiser (S-611),
 * [onEqualizer] the equaliser's page; [fullWindow]: the player has the whole window (a phone's). Nothing without music.
 * 1.0.2: also the big player of a tablet and a desktop window, which watches how far it is pulled down ([drop]) and
 * has a desktop window's caption bar above it ([captionAbove]).
 */
@Composable
fun PhonePlayerHost(
    onClose: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onFullScreenLyrics: (() -> Unit)? = null,
    onFullScreenVisualizer: (() -> Unit)? = null,
    onEqualizer: (() -> Unit)? = null,
    fullWindow: Boolean = false,
    drop: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
    captionAbove: Dp = 0.dp,
) {
    val context = LocalContext.current
    val prefs = rememberNagmedarPreferences()
    val nowPlaying = rememberNowPlaying()
    val media = nowPlaying.media?.takeIf { it.type == MediaWrapper.TYPE_AUDIO }
    if (media == null) {
        // the music stopped, or a video took over: nothing to show
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val progress by rememberPlaybackProgress(nowPlaying)
    // the phone's player, composed while it waits: the waveform is measured for a track once the player is out
    val sheet = LocalPlayerSheet.current
    val waveform = rememberWaveform(media.uri?.toString()) { sheet?.shown != false }
    val path = rememberSignalPath()
    val shuffling by PlaylistManager.shuffling.collectAsState()
    val repeating by PlaylistManager.repeating.collectAsState()
    // S-808: its stars
    val stats by TrackStatsIndex.stats.collectAsState()
    LaunchedEffect(Unit) { TrackStatsIndex.follow(context) }
    val key = statsKey(media)
    val latestQueue by rememberUpdatedState(onQueue)
    // S-1004: the lyrics in place of the artwork, until turned off
    var lyricsShown by rememberSaveable { mutableStateOf(false) }
    // S-804, S-808: the playlists' and the rating's windows
    var adding by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val track = remember(media) { media.toTrack(null) }
    val coverMenu = rememberCoverMenu(remember(media) { CoverTrack.of(media) })
    val coverLabel = stringResource(R.string.nag_cover_menu)
    val actions = remember(context) { ServicePlayerBarActions(context, { latestQueue() }) }
    val artistLink = remember(media) { MusicLinks.artistOf(media) }
    val albumLink = remember(media) { MusicLinks.albumOf(media) }
    // where it plays from: the album, artist or playlist the queue was started from, while it plays one of theirs
    val queue = rememberPlayQueue()
    val started = PlaySources.started
    val from = remember(started, media, queue.entries) {
        PlaySources.playingFrom(started, PlaySources.locationOf(media), queue.entries.mapNotNull { entry -> entry.media?.let(PlaySources::locationOf) })
    }
    val tint = rememberArtworkColor(media)
    // Desktop Kit 1.0: the player takes the artwork's accent too; in the phone's sheet it has it from the mini player's
    // (PlayerBar), standing alone (a tablet's overlay) from its own
    AccentUnless(sheet != null, tint) {
        PhonePlayer(
            info = NowPlayingInfo(
                title = media.nowPlaying ?: media.title ?: "",
                artist = media.artistName,
                album = media.albumName,
                format = path?.let { formatLine(it) },
                lengthMs = media.length,
                playing = nowPlaying.playing,
                rating = key?.let { stats[it]?.rating ?: 0 },
                chapters = rememberPlayingChapters(),
                rate = playbackRate(),
            ),
            progress = { progress },
            waveform = waveform,
            actions = actions,
            onClose = onClose,
            modifier = modifier,
            shuffling = shuffling,
            repeating = repeating != PlaybackStateCompat.REPEAT_MODE_NONE,
            repeatOne = repeating == PlaybackStateCompat.REPEAT_MODE_ONE,
            // S-1006: the album's cover changed from its menu (a long press)
            artwork = { size ->
                ContextMenuArea(coverMenu, accessibilityLabel = coverLabel) { Artwork(media, size, corner = NagTheme.dimens.radiusCard, video = false, crossfade = true) }
            },
            onRate = key?.let { { rating = true } },
            lyrics = { lyricsModifier -> PlayingLyrics(lyricsModifier, onFullScreen = onFullScreenLyrics) },
            lyricsShown = lyricsShown,
            onLyricsShown = { lyricsShown = it },
            canCast = rememberCastingEnabled(),
            casting = rememberCastDevice(),
            onStopCasting = Casting::stop,
            // S-611: on and off here as in Settings › Visualiser
            visualizer = { visualizerModifier -> PlayingVisualizer(visualizerModifier, onFullScreen = onFullScreenVisualizer) },
            visualizerShown = prefs.visualizer.music,
            onVisualizerShown = { prefs.updateVisualizer(prefs.visualizer.copy(music = it)) },
            // the music view opens the artist's or the album's tracks; the player closes for it
            onOpenArtist = artistLink?.let { link -> { MusicLinks.open(link) } },
            onOpenAlbum = albumLink?.let { link -> { MusicLinks.open(link) } },
            tint = tint,
            from = from,
            // the line sung opens the lyrics over the window, or in the artwork's place
            lyricLine = { lineModifier -> PlayingLyricLine(onClick = { onFullScreenLyrics?.invoke() ?: run { lyricsShown = true } }, modifier = lineModifier) },
            onAddToPlaylist = { adding = true },
            onShare = { share(context, scope, media) },
            onEqualizer = onEqualizer,
            edgeToEdge = fullWindow,
            drop = drop,
            captionAbove = captionAbove,
        )
    }
    if (adding) AddToPlaylistHost(listOf(track), scope) { adding = false }
    if (rating && key != null) RatingDialog(listOf(track), stats, onRate = { stars -> TrackStatsIndex.rate(context, key, stars) }, onDismiss = { rating = false })
}

/** [content] with the artwork's accent of [seed] ([ArtworkAccent]), unless [given]: the caller's already is. */
@Composable
private fun AccentUnless(given: Boolean, seed: Color?, content: @Composable () -> Unit) {
    if (given) content() else ArtworkAccent(seed, content)
}

/** What plays shared with the system's sheet: the file itself where there is one, its address otherwise. */
private fun share(context: Context, scope: CoroutineScope, media: MediaWrapper) {
    val uri = media.uri ?: return
    val title = media.nowPlaying ?: media.title ?: media.fileName.orEmpty()
    scope.launch {
        // a file outside the provider's folders cannot be shared as a file
        val send = withContext(Dispatchers.IO) { runCatching { shareIntent(context, uri) }.getOrNull() }
        if (send == null) {
            Notices.show(context, R.string.nag_player_share_missing)
            return@launch
        }
        runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.nag_player_share_title, title))) }
    }
}

private fun shareIntent(context: Context, uri: Uri): Intent? = when (uri.scheme) {
    "content" -> Intent(Intent.ACTION_SEND).setType(AUDIO_TYPE).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    "file", null -> uri.path?.let(::File)?.takeIf { it.isFile }?.let { file ->
        val shared = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        Intent(Intent.ACTION_SEND).setType(AUDIO_TYPE).putExtra(Intent.EXTRA_STREAM, shared).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    else -> Intent(Intent.ACTION_SEND).setType(TEXT_TYPE).putExtra(Intent.EXTRA_TEXT, uri.toString())
}

private const val AUDIO_TYPE = "audio/*"
private const val TEXT_TYPE = "text/plain"
