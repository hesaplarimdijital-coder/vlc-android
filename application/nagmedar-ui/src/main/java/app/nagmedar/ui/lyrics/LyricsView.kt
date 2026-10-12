package app.nagmedar.ui.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.playback.rememberPlaybackTime
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/** "+0,3", "−1,2", "0": a delay in seconds as the subtitles' is written, its sign first. */
internal fun delayNumber(ms: Long, locale: Locale): String {
    if (ms == 0L) return "0"
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return (if (ms > 0) "+" else "−") + format.format(abs(ms) / 1_000.0)
}

/** Where the lyrics came from, under them. */
@Composable
internal fun lyricsOriginLabel(origin: LyricsOrigin): String = stringResource(
    when (origin) {
        LyricsOrigin.User -> R.string.nag_lyrics_from_user
        LyricsOrigin.Embedded -> R.string.nag_lyrics_from_tags
        LyricsOrigin.File -> R.string.nag_lyrics_from_lrc
        LyricsOrigin.Online -> R.string.nag_lyrics_from_lrclib
    },
)

/**
 * S-1004: a track's lyrics as they are sung, or what is said when there are none. Synced lines:
 * the one sung lit and kept a third of the way down (after a scroll of the user's, from four
 * seconds on), a click or a tap on a line goes there, word-timed lines light word by word. Plain
 * lyrics read as text. Under them the delay, in tenths of a second (positive: the lines come
 * later), and where they came from. [large]: the full-screen view's type.
 */
@Composable
fun LyricsPane(
    load: LyricsLoad,
    timeMs: () -> Long,
    onSeek: ((Long) -> Unit)?,
    onDelay: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    onlineAllowed: Boolean = true,
    onLookAgain: (() -> Unit)? = null,
    onFullScreen: (() -> Unit)? = null,
) {
    val colors = NagTheme.colors
    Column(modifier.testTag("lyrics-pane")) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (load) {
                is LyricsLoad.Found -> when {
                    load.lyrics.instrumental -> Centered {
                        StatePanel(
                            tone = StateTone.Neutral,
                            icon = NagIcons.Lyrics,
                            title = stringResource(R.string.nag_lyrics_instrumental),
                            description = stringResource(R.string.nag_lyrics_instrumental_desc),
                            dense = !large,
                        )
                    }
                    load.lyrics.synced -> SyncedLines(load.lyrics, timeMs, onSeek, large)
                    else -> PlainLines(load.lyrics, large)
                }
                is LyricsLoad.Missing -> Centered {
                    StatePanel(
                        tone = StateTone.Neutral,
                        icon = NagIcons.Lyrics,
                        title = stringResource(R.string.nag_lyrics_missing),
                        description = stringResource(if (onlineAllowed) R.string.nag_lyrics_missing_desc else R.string.nag_lyrics_missing_offline),
                        primary = onLookAgain?.let { StateAction(stringResource(R.string.nag_lyrics_look_again), it) },
                        dense = !large,
                        modifier = Modifier.testTag("lyrics-missing"),
                    )
                }
                is LyricsLoad.Looking -> Centered {
                    Text(stringResource(R.string.nag_lyrics_looking), style = NagTheme.type.body, color = colors.textTertiary)
                }
                LyricsLoad.Idle -> Centered {
                    Text(stringResource(R.string.nag_music_nothing_playing), style = NagTheme.type.body, color = colors.textTertiary)
                }
            }
        }
        val lyrics = (load as? LyricsLoad.Found)?.lyrics
        if (lyrics != null || onFullScreen != null) LyricsFooter(lyrics, onDelay, onFullScreen, large)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** The lines' type: the pane's headline, larger over the whole window. */
@Composable
private fun lineStyle(large: Boolean): TextStyle =
    if (large) NagTheme.type.display.copy(fontSize = 32.sp, lineHeight = 42.sp) else NagTheme.type.headline

@Composable
private fun SyncedLines(lyrics: Lyrics, timeMs: () -> Long, onSeek: ((Long) -> Unit)?, large: Boolean) {
    val list = rememberLazyListState()
    val current by remember(lyrics) { derivedStateOf { lyrics.lineAt(timeMs()) } }
    // the list follows the song, but not for a while after the user scrolled it
    var following by remember { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }
    var userScrolls by remember { mutableIntStateOf(0) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { scrolling ->
            if (autoScrolling) return@collect
            if (scrolling) following = false else if (!following) userScrolls++
        }
    }
    LaunchedEffect(userScrolls) {
        if (userScrolls == 0) return@LaunchedEffect
        delay(FOLLOW_AGAIN_MS)
        following = true
    }
    val reduced = NagTheme.motion.reduced
    LaunchedEffect(current, following) {
        if (!following) return@LaunchedEffect
        autoScrolling = true
        try {
            val target = current.coerceAtLeast(0)
            if (reduced) list.scrollToItem(target) else list.animateScrollToItem(target)
        } finally {
            autoScrolling = false
        }
    }
    val seekLabel = stringResource(R.string.nag_lyrics_seek)
    val style = lineStyle(large)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = maxHeight * 0.3f, bottom = maxHeight * 0.6f),
            modifier = Modifier.fillMaxSize().fadingEdges(FADE, NagTheme.colors.background).testTag("lyrics-lines"),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                LyricRow(
                    line = line,
                    index = index,
                    sung = index == current,
                    // the time as the lyrics count it: the delay taken off
                    at = { timeMs() - lyrics.offsetMs },
                    style = style,
                    seekLabel = seekLabel,
                    onClick = onSeek?.let { seek -> { seek(line.startMs + lyrics.offsetMs) } },
                )
            }
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    index: Int,
    sung: Boolean,
    at: () -> Long,
    style: TextStyle,
    seekLabel: String,
    onClick: (() -> Unit)?,
) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    val text: AnnotatedString = when {
        line.text.isEmpty() -> AnnotatedString(INTERLUDE)
        sung && line.words.isNotEmpty() -> {
            // the words sung so far lit, the rest waiting
            val now = at()
            buildAnnotatedString {
                for (word in line.words) {
                    withStyle(SpanStyle(color = if (word.startMs <= now) colors.accent else colors.text)) { append(word.text) }
                }
            }
        }
        else -> AnnotatedString(line.text)
    }
    Text(
        text,
        style = if (sung) style.copy(fontWeight = FontWeight.Bold) else style,
        color = if (sung) colors.accent else colors.textTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .then(if (onClick != null) Modifier.nagClickable(interaction, onClick, onClickLabel = seekLabel) else Modifier)
            .semantics { selected = sung }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("lyrics-line-$index"),
    )
}

@Composable
private fun PlainLines(lyrics: Lyrics, large: Boolean) {
    val colors = NagTheme.colors
    val style = if (large) lineStyle(true).copy(fontWeight = FontWeight.Normal) else NagTheme.type.body
    LazyColumn(Modifier.fillMaxSize().fadingEdges(FADE, colors.background).testTag("lyrics-lines"), contentPadding = PaddingValues(vertical = NagTheme.dimens.gapS)) {
        item {
            Text(
                stringResource(R.string.nag_lyrics_plain),
                style = NagTheme.type.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = NagTheme.dimens.gapS),
            )
        }
        itemsIndexed(lyrics.lines) { index, line ->
            if (line.text.isEmpty()) {
                Spacer(Modifier.height(NagTheme.dimens.gapM))
            } else {
                Text(line.text, style = style, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).testTag("lyrics-line-$index"))
            }
        }
    }
}

/** The delay's buttons (synced lyrics); where the lyrics came from and the full-screen button. */
@Composable
private fun LyricsFooter(lyrics: Lyrics?, onDelay: ((Long) -> Unit)?, onFullScreen: (() -> Unit)?, large: Boolean) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val locale = currentLocale()
    Column(Modifier.fillMaxWidth().padding(top = dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
        if (lyrics != null && lyrics.synced && onDelay != null) {
            val step = LyricsSession.STEP_MS
            val stepText = stringResource(R.string.nag_lyrics_delay_value, delayNumber(step, locale).removePrefix("+"))
            val earlier = stringResource(R.string.nag_lyrics_earlier, stepText)
            val later = stringResource(R.string.nag_lyrics_later, stepText)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapXs)) {
                Text(stringResource(R.string.nag_lyrics_delay), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                NagButton(
                    delayNumber(-step, locale),
                    { onDelay(lyrics.offsetMs - step) },
                    modifier = Modifier.semantics { contentDescription = earlier }.testTag("lyrics-earlier"),
                )
                Text(
                    stringResource(R.string.nag_lyrics_delay_value, delayNumber(lyrics.offsetMs, locale)),
                    style = NagTheme.type.mono,
                    color = if (lyrics.offsetMs == 0L) colors.textTertiary else colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 56.dp).testTag("lyrics-delay"),
                )
                NagButton(
                    delayNumber(step, locale),
                    { onDelay(lyrics.offsetMs + step) },
                    modifier = Modifier.semantics { contentDescription = later }.testTag("lyrics-later"),
                )
                if (lyrics.offsetMs != 0L) {
                    NagIconButton(NagIcons.Close, stringResource(R.string.nag_lyrics_delay_reset), { onDelay(0) }, size = 28.dp, iconSize = 14.dp, modifier = Modifier.testTag("lyrics-delay-reset"))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lyrics?.let { lyricsOriginLabel(it.origin) }.orEmpty(),
                style = NagTheme.type.labelSmall,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onFullScreen != null && !large) {
                NagIconButton(NagIcons.Fullscreen, stringResource(R.string.nag_sc_lyrics), onFullScreen, shortcut = ShortcutId.Lyrics, modifier = Modifier.testTag("lyrics-full-screen-button"))
            }
        }
    }
}

/**
 * S-1004: the lyrics over the whole window (Ctrl+Shift+L, or the panes' full-screen button), in
 * large type: what plays above them, the delay under them; Esc, Back or the close button leaves.
 */
@Composable
fun LyricsFullScreen(
    title: String?,
    subtitle: String?,
    load: LyricsLoad,
    timeMs: () -> Long,
    onSeek: ((Long) -> Unit)?,
    onDelay: ((Long) -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onlineAllowed: Boolean = true,
    onLookAgain: (() -> Unit)? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    BackHandler(onBack = onClose)
    Column(
        modifier
            .fillMaxSize()
            // over the window: nothing under it takes a click
            .pointerInput(Unit) { detectTapGestures { } }
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = dimens.gapL, vertical = dimens.gapM)
            .testTag("lyrics-full-screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.widthIn(max = MAX_WIDTH).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (title != null) Text(title, style = NagTheme.type.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = NagTheme.type.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NagIconButton(NagIcons.Close, stringResource(R.string.nag_lyrics_close), onClose, modifier = Modifier.testTag("lyrics-close"))
        }
        LyricsPane(
            load = load,
            timeMs = timeMs,
            onSeek = onSeek,
            onDelay = onDelay,
            modifier = Modifier.weight(1f).widthIn(max = MAX_WIDTH).fillMaxWidth(),
            large = true,
            onlineAllowed = onlineAllowed,
            onLookAgain = onLookAgain,
        )
    }
}

// --------------------------------------------------------------- on the playback service

/** S-1004: the playing track's lyrics in a pane, from the app's session; [onFullScreen] opens them over the window. */
@Composable
fun PlayingLyrics(modifier: Modifier = Modifier, onFullScreen: (() -> Unit)? = null) {
    val playing = rememberPlayingLyrics()
    LyricsPane(
        load = playing.load,
        timeMs = playing.timeMs,
        onSeek = playing.onSeek,
        onDelay = playing.onDelay,
        modifier = modifier,
        onlineAllowed = playing.onlineAllowed,
        onLookAgain = playing.onLookAgain,
        onFullScreen = onFullScreen,
    )
}

/** S-1004: the playing track's lyrics over the whole window, from the app's session. */
@Composable
fun PlayingLyricsFullScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val playing = rememberPlayingLyrics()
    LyricsFullScreen(
        title = playing.media?.let { it.nowPlaying ?: it.title },
        subtitle = playing.media?.let { listOfNotNull(it.artistName?.takeIf(String::isNotBlank), it.albumName?.takeIf(String::isNotBlank)).joinToString(" · ") },
        load = playing.load,
        timeMs = playing.timeMs,
        onSeek = playing.onSeek,
        onDelay = playing.onDelay,
        onClose = onClose,
        modifier = modifier,
        onlineAllowed = playing.onlineAllowed,
        onLookAgain = playing.onLookAgain,
    )
}

/** What the views of the playing track's lyrics are given. */
private class PlayingLyricsState(
    val media: MediaWrapper?,
    val load: LyricsLoad,
    val timeMs: () -> Long,
    val onSeek: (Long) -> Unit,
    val onDelay: (Long) -> Unit,
    val onlineAllowed: Boolean,
    val onLookAgain: () -> Unit,
)

@Composable
private fun rememberPlayingLyrics(): PlayingLyricsState {
    val context = LocalContext.current
    val session = remember(context) { LyricsSession.get(context) }
    val nowPlaying = rememberNowPlaying()
    val media = nowPlaying.media?.takeIf { it.type == MediaWrapper.TYPE_AUDIO }
    val track = remember(media) { media?.let(LyricsTrack::of) }
    LaunchedEffect(track) { session.show(track) }
    val time = rememberPlaybackTime(nowPlaying)
    val load = session.load
    return PlayingLyricsState(
        media = media,
        // the session may still hold the track before: this one is being looked for
        load = when {
            track == null -> LyricsLoad.Idle
            load.track == track -> load
            else -> LyricsLoad.Looking(track)
        },
        timeMs = { time.ms },
        onSeek = time::seekTo,
        onDelay = { session.setDelay(it) },
        onlineAllowed = remember(track) { LyricsCenter.onlineAllowed(context) },
        onLookAgain = session::again,
    )
}

/**
 * The lists' ends fade into [color] over [edge]: the lines come and go rather than being cut. A
 * veil of the background over the lines, which every renderer draws alike.
 */
private fun Modifier.fadingEdges(edge: Dp, color: Color): Modifier = drawWithContent {
    drawContent()
    val height = edge.toPx()
    if (height * 2 < size.height) {
        drawRect(Brush.verticalGradient(0f to color, 1f to color.copy(alpha = 0f), startY = 0f, endY = height), size = Size(size.width, height))
        drawRect(
            Brush.verticalGradient(0f to color.copy(alpha = 0f), 1f to color, startY = size.height - height, endY = size.height),
            topLeft = Offset(0f, size.height - height),
            size = Size(size.width, height),
        )
    }
}

/** How far the lists' ends fade. */
private val FADE = 24.dp

/** A synced line without words: a pause in the singing. */
private const val INTERLUDE = "♪"

/** After a scroll of the user's, the lines follow the song again this much later. */
private const val FOLLOW_AGAIN_MS = 4_000L

/** The full-screen view's lines read no wider than this. */
private val MAX_WIDTH = 880.dp
