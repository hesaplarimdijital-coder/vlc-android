package app.nagmedar.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import app.nagmedar.ui.cast.CastButton
import app.nagmedar.ui.cast.CastDevice
import app.nagmedar.ui.cast.Casting
import app.nagmedar.ui.cast.CastingStrip
import app.nagmedar.ui.cast.rememberCastDevice
import app.nagmedar.ui.cast.rememberCastingEnabled
import app.nagmedar.ui.components.ArtistAlbumLine
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.RatingStars
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.covers.rememberCoverMenu
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.lyrics.PlayingLyrics
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.ChapterStrip
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.playback.ProgressTimeline
import app.nagmedar.ui.playback.QueueEntry
import app.nagmedar.ui.playback.playbackRate
import app.nagmedar.ui.playback.rememberChapterMarks
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.playback.rememberPlayQueue
import app.nagmedar.ui.playback.rememberPlaybackProgress
import app.nagmedar.ui.playback.rememberPlayingChapters
import app.nagmedar.ui.podcast.Chapter
import app.nagmedar.ui.podcast.ChapterSteps
import app.nagmedar.ui.rememberNagmedarPreferences
import app.nagmedar.ui.stats.TrackStatsIndex
import app.nagmedar.ui.stats.statsKey
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.visual.PlayingVisualizer
import kotlin.math.min
import org.videolan.medialibrary.interfaces.media.MediaWrapper

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
    /** S-1104: the chapters of the podcast episode that plays; empty for any other media. */
    val chapters: List<Chapter> = emptyList(),
    /** The playing speed: the timeline moves at it between the service's readings. */
    val rate: Float = 1f,
)

/**
 * S-1001: the right pane of the desktop music view: the artwork, what plays, how it plays, its
 * progress over its waveform (a click or a drag seeks), and what comes next (a click plays it).
 * The artist and the album are links to their tracks in the music view ([onOpenArtist],
 * [onOpenAlbum]). [info] null: nothing plays.
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
    /** S-1004: the track's lyrics, in place of the artwork and the queue while [lyricsShown]; the header's button turns them on and off. */
    lyrics: (@Composable (Modifier) -> Unit)? = null,
    lyricsShown: Boolean = false,
    onLyricsShown: ((Boolean) -> Unit)? = null,
    /** S-1302: VLC's casting setting is on: the header has the cast button, which [onCast] answers. */
    canCast: Boolean = false,
    /** S-1302: the device a renderer plays the track on; null while it plays here. */
    casting: CastDevice? = null,
    onCast: () -> Unit = {},
    onStopCasting: () -> Unit = {},
    /** S-611: the visualiser, above the waveform while [visualizerShown]; the header's button turns it on and off. */
    visualizer: (@Composable (Modifier) -> Unit)? = null,
    visualizerShown: Boolean = false,
    onVisualizerShown: ((Boolean) -> Unit)? = null,
    /** S-1104: the chapter strip's buttons: to the next chapter (true) or the previous (false), and to a chosen chapter's start. */
    onStepChapter: (Boolean) -> Unit = {},
    onSeekToMs: (Long) -> Unit = {},
    onOpenArtist: (() -> Unit)? = null,
    onOpenAlbum: (() -> Unit)? = null,
    /** Desktop Kit 1.0: more under what plays, in the same scroll (the inspector's tags of the track). */
    below: (@Composable () -> Unit)? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val links = Links(onOpenArtist, onOpenAlbum)
    Column(modifier.fillMaxSize().padding(dimens.gapL), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nag_now_playing), style = NagTheme.type.labelSmall, color = colors.textTertiary, modifier = Modifier.weight(1f))
            if (info != null && visualizer != null && onVisualizerShown != null && !(lyrics != null && lyricsShown)) {
                NagIconButton(
                    NagIcons.Visualizer,
                    stringResource(R.string.nag_vis_title),
                    { onVisualizerShown(!visualizerShown) },
                    selected = visualizerShown,
                    size = 24.dp,
                    iconSize = 16.dp,
                    modifier = Modifier.testTag("now-playing-visualizer"),
                )
            }
            if (info != null && lyrics != null && onLyricsShown != null) {
                NagIconButton(
                    NagIcons.Lyrics,
                    stringResource(R.string.nag_lyrics_title),
                    { onLyricsShown(!lyricsShown) },
                    selected = lyricsShown,
                    size = 24.dp,
                    iconSize = 16.dp,
                    modifier = Modifier.testTag("now-playing-lyrics"),
                )
            }
            // S-1302
            if (info != null && (canCast || casting != null)) CastButton(casting, onCast, size = 24.dp, iconSize = 16.dp)
        }
        if (info != null && casting != null) CastingStrip(casting, onStopCasting)
        when {
            info == null -> Text(stringResource(R.string.nag_music_nothing_playing), style = NagTheme.type.body, color = colors.textSecondary)
            lyrics != null && lyricsShown -> {
                PlayingLine(info, artwork, links)
                lyrics(Modifier.weight(1f).fillMaxWidth())
                ProgressTimeline(
                    progress, info.lengthMs, onSeek,
                    playing = info.playing, rate = info.rate, waveform = waveform,
                    marks = rememberChapterMarks(info.chapters, info.lengthMs), label = stringResource(R.string.nag_music_waveform),
                )
                ChapterStrip(info.chapters, info.lengthMs, progress, onStepChapter, onSeekToMs)
            }
            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                Playing(info, progress, waveform, upNext, onSeek, onPlayUpNext, artwork, onRate, visualizer.takeIf { visualizerShown }, onStepChapter, onSeekToMs, links)
                below?.invoke()
            }
        }
    }
}

/** The artist's and the album's links of a player: each null where it does not open anything. */
@Immutable
internal data class Links(val artist: (() -> Unit)?, val album: (() -> Unit)?)

/** "Artist · Album" under a player's title, each a link to its tracks in the music view. */
@Composable
internal fun PlayingArtistAlbum(info: NowPlayingInfo, links: Links, style: TextStyle, modifier: Modifier = Modifier) {
    ArtistAlbumLine(
        info.artist,
        info.album,
        style,
        NagTheme.colors.textSecondary,
        modifier.testTag("now-playing-artist-album"),
        onArtist = links.artist,
        onAlbum = links.album,
        artistLabel = stringResource(R.string.nag_music_open_artist),
        albumLabel = stringResource(R.string.nag_music_open_album),
    )
}

/** S-1004: what plays, in a line above its lyrics: a small artwork, the title, the artist and album. */
@Composable
private fun PlayingLine(info: NowPlayingInfo, artwork: @Composable (Dp) -> Unit, links: Links) {
    val colors = NagTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS)) {
        artwork(48.dp)
        Column(Modifier.weight(1f)) {
            Text(info.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PlayingArtistAlbum(info, links, NagTheme.type.labelSmall)
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
    visualizer: (@Composable (Modifier) -> Unit)?,
    onStepChapter: (Boolean) -> Unit,
    onSeekToMs: (Long) -> Unit,
    links: Links,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val side = min(maxWidth.value, 288f).dp
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { artwork(side) }
        }
        Spacer(Modifier.height(dimens.gapXs))
        Column {
            Text(info.title, style = NagTheme.type.title, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            PlayingArtistAlbum(info, links, NagTheme.type.body)
        }
        if (info.format != null) Text(info.format, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 2)
        info.rating?.let { stars -> RatingStars(stars, onRate, starSize = 16.dp, target = 22.dp, focusable = onRate != null, modifier = Modifier.testTag("now-playing-rating")) }
        Spacer(Modifier.height(dimens.gapS))
        // S-611: what plays, drawn
        visualizer?.invoke(Modifier.fillMaxWidth().height(VISUALIZER_HEIGHT))
        ProgressTimeline(
            progress, info.lengthMs, onSeek,
            playing = info.playing, rate = info.rate, waveform = waveform,
            marks = rememberChapterMarks(info.chapters, info.lengthMs), label = stringResource(R.string.nag_music_waveform),
        )
        ChapterStrip(info.chapters, info.lengthMs, progress, onStepChapter, onSeekToMs)
        if (upNext.isNotEmpty()) {
            Spacer(Modifier.height(dimens.gapS))
            Text(stringResource(R.string.nag_queue_title), style = NagTheme.type.labelSmall, color = colors.textTertiary)
            upNext.take(UP_NEXT).forEachIndexed { i, entry -> UpNextRow(entry) { onPlayUpNext(i) } }
        }
    }
}

/** The visualiser's height in the panel: the bars, and the kind's choice under them. */
private val VISUALIZER_HEIGHT = 168.dp

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

// --------------------------------------------------------------- the app's panel

/**
 * The panel on the playback service: what plays, its progress over its waveform, the queue after it; or its lyrics
 * (S-1004), which [onFullScreenLyrics] opens over the window; its visualiser (S-611), which
 * [onFullScreenVisualizer] does. In the music view's inspector (Desktop Kit 1.0) the queue and the lyrics have tabs
 * of their own ([withQueue], [withLyrics] false) and [below] adds the track's tags.
 */
@Composable
fun MusicNowPlaying(
    onFullScreenLyrics: (() -> Unit)? = null,
    onFullScreenVisualizer: (() -> Unit)? = null,
    withQueue: Boolean = true,
    withLyrics: Boolean = true,
    below: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = rememberNagmedarPreferences()
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
    val upNext = if (withQueue && queue.current >= 0) queue.entries.drop(queue.current + 1) else emptyList()
    // S-1004: the lyrics in place of the artwork and the queue, until turned off
    var lyricsShown by rememberSaveable { mutableStateOf(false) }
    val coverMenu = rememberCoverMenu(remember(media) { media?.let(CoverTrack::of) })
    val coverLabel = stringResource(R.string.nag_cover_menu)
    val chapters = rememberPlayingChapters()
    val info = media?.let {
        NowPlayingInfo(
            title = it.nowPlaying ?: it.title ?: "",
            artist = it.artistName,
            album = it.albumName,
            format = path?.let { p -> formatLine(p) },
            lengthMs = it.length,
            playing = nowPlaying.playing,
            rating = key?.let { k -> stats[k]?.rating ?: 0 },
            chapters = chapters,
            rate = playbackRate(),
        )
    }
    val artistLink = remember(media) { MusicLinks.artistOf(media) }
    val albumLink = remember(media) { MusicLinks.albumOf(media) }
    NowPlayingPanel(
        info = info,
        progress = { progress },
        waveform = waveform,
        upNext = upNext,
        onSeek = PlaybackActions::seekTo,
        onPlayUpNext = { i -> queue.play(queue.current + 1 + i) },
        // S-1006: the album's cover changed from its menu (a right click, a long press)
        artwork = { size ->
            ContextMenuArea(coverMenu, accessibilityLabel = coverLabel) { Artwork(media, size, corner = 8.dp, video = false) }
        },
        onRate = key?.let { k -> { stars -> TrackStatsIndex.rate(context, k, stars) } },
        lyrics = if (withLyrics) ({ lyricsModifier -> PlayingLyrics(lyricsModifier, onFullScreen = onFullScreenLyrics) }) else null,
        lyricsShown = lyricsShown,
        onLyricsShown = { lyricsShown = it },
        canCast = rememberCastingEnabled(),
        casting = rememberCastDevice(),
        onCast = { Casting.openPicker() },
        onStopCasting = Casting::stop,
        // S-611: on and off here as in Settings › Visualiser
        visualizer = { visualizerModifier -> PlayingVisualizer(visualizerModifier, onFullScreen = onFullScreenVisualizer) },
        visualizerShown = prefs.visualizer.music,
        onVisualizerShown = { prefs.updateVisualizer(prefs.visualizer.copy(music = it)) },
        onStepChapter = { forward -> ChapterSteps.step(forward) },
        onSeekToMs = PlaybackActions::seekToMs,
        // the music view beside the pane opens the artist's or the album's tracks; the pane stays
        onOpenArtist = artistLink?.let { link -> { MusicLinks.open(link) } },
        onOpenAlbum = albumLink?.let { link -> { MusicLinks.open(link) } },
        below = below,
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
