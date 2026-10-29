package app.nagmedar.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.Waveform
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.AbTest
import app.nagmedar.ui.audio.AbTestState
import app.nagmedar.ui.audio.MusicEngineAccess
import app.nagmedar.ui.audio.SignalPathCard
import app.nagmedar.ui.audio.channelsText
import app.nagmedar.ui.audio.latency.LatencyCenter
import app.nagmedar.ui.audio.rememberSignalPath
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.InspectorPane
import app.nagmedar.ui.components.InspectorState
import app.nagmedar.ui.components.InspectorTab
import app.nagmedar.ui.components.RatingStars
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.covers.CoverMenuButton
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.covers.rememberCover
import app.nagmedar.ui.covers.rememberCoverMenu
import app.nagmedar.ui.lyrics.PlayingLyrics
import app.nagmedar.ui.playback.QueuePanel
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.playback.rememberPlayQueue
import app.nagmedar.ui.playback.softLevels
import app.nagmedar.ui.shell.LocalShellBottomPadding
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.stats.TrackStatsIndex
import app.nagmedar.ui.theme.NagTheme
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/** Desktop Kit 1.0: the music view's inspector tabs, by their stable ids. */
internal object InspectorTabs {
    const val TAGS = "tags"
    const val SIGNAL = "signal"
    const val LYRICS = "lyrics"
    const val QUEUE = "queue"
}

/**
 * What the music view's inspector is about (Desktop Kit 1.0): the [track] selected in the table
 * (the last one clicked when [selected] are), or none, and then what plays.
 */
@Immutable
data class InspectorSubject(val track: Track?, val selected: Int = if (track == null) 0 else 1)

/**
 * What the music view's inspector shows in each tab (Desktop Kit 1.0): the playback service's in
 * the app ([rememberMusicInspector]), samples in the tests.
 */
@Immutable
class MusicInspectorContent(
    /** "Etiketler ve spektrum": the subject's tags and technical line, its waveform; what plays with its visualiser. */
    val tags: @Composable (InspectorSubject) -> Unit,
    /** "Sinyal yolu": the playing track's signal path. */
    val signal: @Composable (InspectorSubject) -> Unit,
    /** "Sözler": the playing track's lyrics. */
    val lyrics: @Composable (InspectorSubject) -> Unit,
    /** "Sıra": the play queue. */
    val queue: @Composable () -> Unit,
)

/** The inspector of the music view: its four tabs over [content], about [subject]; the page header's button hides it. */
@Composable
internal fun MusicInspectorPane(state: InspectorState, content: MusicInspectorContent, subject: InspectorSubject) {
    val tabs = listOf(
        InspectorTab(InspectorTabs.TAGS, stringResource(R.string.nag_inspector_tags)),
        InspectorTab(InspectorTabs.SIGNAL, stringResource(R.string.nag_sp_title)),
        InspectorTab(InspectorTabs.LYRICS, stringResource(R.string.nag_lyrics_title)),
        InspectorTab(InspectorTabs.QUEUE, stringResource(R.string.nag_inspector_queue)),
    )
    // the last lines rest clear of a floating player bar
    val bottom = LocalShellBottomPadding.current
    InspectorPane(tabs, state.selectedTab, { state.selectedTab = it }) { tab ->
        Box(Modifier.fillMaxSize().padding(bottom)) {
            when (tab.id) {
                InspectorTabs.TAGS -> content.tags(subject)
                InspectorTabs.SIGNAL -> content.signal(subject)
                InspectorTabs.LYRICS -> content.lyrics(subject)
                else -> content.queue()
            }
        }
    }
}

/** A tab's line on whose it is: "Seçili parça", or a word that the selection is not what plays. */
@Composable
fun InspectorNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary, modifier = modifier.testTag("inspector-note"))
}

/**
 * The tags tab for a track selected in the table that is not what plays: whose it is ([selected]
 * of them selected), its cover, title, artist and album, its stars ([onRate] sets them), its
 * technical line and [waveform], then every tag the library and the engine's probe know.
 */
@Composable
fun TrackInspector(
    track: Track,
    selected: Int,
    waveform: Waveform?,
    rating: Int,
    onRate: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    stats: Map<String, TrackStats> = emptyMap(),
    cover: @Composable (Dp) -> Unit = {},
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dimens.gapL).testTag("track-inspector"),
        verticalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        InspectorNote(
            if (selected > 1) pluralStringResource(R.plurals.nag_inspector_selected_of, selected, selected) else stringResource(R.string.nag_inspector_selected),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
            cover(72.dp)
            Column(Modifier.weight(1f)) {
                Text(track.title, style = NagTheme.type.title, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val line = listOfNotNull(track.artist?.takeIf { it.isNotBlank() }, track.album?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (line.isNotEmpty()) Text(line, style = NagTheme.type.body, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        track.tech?.let { tech ->
            val parts = listOfNotNull(formatLabel(tech), tech.channels.takeIf { it > 0 }?.let { channelsText(it) })
            Text(parts.joinToString(" · "), style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 2)
        }
        RatingStars(rating, onRate, starSize = 16.dp, target = 22.dp, focusable = onRate != null)
        if (waveform != null) {
            WaveformStrip(waveform, Modifier.fillMaxWidth().height(40.dp).padding(vertical = 4.dp), stringResource(R.string.nag_music_waveform))
        }
        HorizontalRule()
        TrackTags(track, stats)
    }
}

/** The tags the table can show, in the order the tags tab lists them; the stars stand above them. */
private val TAG_COLUMNS = listOf(
    MusicColumn.Title, MusicColumn.Artist, MusicColumn.AlbumArtist, MusicColumn.Album, MusicColumn.Number, MusicColumn.Year,
    MusicColumn.Genre, MusicColumn.Composer, MusicColumn.Duration, MusicColumn.Format, MusicColumn.Depth, MusicColumn.Rate,
    MusicColumn.Bitrate, MusicColumn.Plays, MusicColumn.Skips, MusicColumn.LastPlayed, MusicColumn.Added,
)

/** [track]'s tags as the table words them, one under the other, the empty ones left out; its channels and folder. */
@Composable
fun TrackTags(track: Track, stats: Map<String, TrackStats> = emptyMap(), modifier: Modifier = Modifier) {
    val columns = rememberMusicColumns(TAG_COLUMNS, stats)
    Column(modifier.fillMaxWidth().testTag("track-tags"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        columns.forEach { column -> TagRow(column.title, column.value(track), mono = column.mono) }
        track.tech?.channels?.takeIf { it > 0 }?.let { TagRow(stringResource(R.string.nag_inspector_channels), channelsText(it)) }
        TagRow(stringResource(R.string.nag_inspector_folder), track.folder)
    }
}

@Composable
private fun TagRow(label: String, value: String, mono: Boolean = false) {
    if (value.isBlank()) return
    val colors = NagTheme.colors
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 2, modifier = Modifier.width(104.dp).padding(top = 2.dp, end = 8.dp))
        Text(value, style = if (mono) NagTheme.type.mono else NagTheme.type.body, color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** A track's waveform at rest, as the timeline draws it soft: its loudness bar by bar. */
@Composable
fun WaveformStrip(waveform: Waveform, modifier: Modifier = Modifier, label: String = "") {
    val tint = NagTheme.colors.textTertiary.copy(alpha = 0.55f)
    val density = LocalDensity.current
    val bar = with(density) { 2.dp.toPx() }
    val gap = with(density) { 1.dp.toPx() }
    Canvas(modifier.semantics { if (label.isNotEmpty()) contentDescription = label }) {
        val count = (size.width / (bar + gap)).toInt().coerceAtLeast(1)
        val levels = softLevels(waveform, count)
        val middle = size.height / 2f
        levels.forEachIndexed { i, level ->
            val half = (level * middle).coerceAtLeast(bar / 2f)
            drawRoundRect(tint, Offset(i * (bar + gap), middle - half), Size(bar, 2 * half), CornerRadius(bar / 2f))
        }
    }
}

// ------------------------------------------------------------------ the app's tabs

/**
 * The inspector's tabs on the playback service and the library (Desktop Kit 1.0): the full-screen
 * lyrics and visualiser are [onFullScreenLyrics] and [onFullScreenVisualizer]; [undo] brings back
 * what the queue tab took out.
 */
@Composable
fun rememberMusicInspector(onFullScreenLyrics: () -> Unit, onFullScreenVisualizer: () -> Unit, undo: UndoBarState?): MusicInspectorContent {
    val lyrics by rememberUpdatedState(onFullScreenLyrics)
    val visualizer by rememberUpdatedState(onFullScreenVisualizer)
    return remember(undo) {
        MusicInspectorContent(
            tags = { subject -> TagsTab(subject) { visualizer() } },
            signal = { subject -> SignalTab(subject) },
            lyrics = { subject -> LyricsTab(subject) { lyrics() } },
            queue = { QueueTab(undo) },
        )
    }
}

/** The uri of the music that plays; null when nothing does (or a stream, a video). */
@Composable
private fun rememberPlayingUri(): String? = rememberNowPlaying().media?.takeIf { it.type == MediaWrapper.TYPE_AUDIO }?.uri?.toString()

/** Whether the subject is a selected track other than what plays. */
private fun InspectorSubject.notPlaying(playing: String?): Boolean = track != null && track.uri != playing

/**
 * The tags tab: with nothing selected, or the selection being what plays, the now playing pane
 * (artwork, title, links, format, stars, visualiser, waveform timeline, chapters) and its tags under it; else the
 * selected track's.
 */
@Composable
private fun TagsTab(subject: InspectorSubject, onFullScreenVisualizer: () -> Unit) {
    val context = LocalContext.current
    val playing = rememberPlayingUri()
    val stats by TrackStatsIndex.stats.collectAsState()
    LaunchedEffect(Unit) { TrackStatsIndex.follow(context) }
    val track = subject.track
    if (track == null || !subject.notPlaying(playing)) {
        MusicNowPlaying(onFullScreenVisualizer = onFullScreenVisualizer, withQueue = false, withLyrics = false, below = { PlayingTags(stats) })
        return
    }
    val coverTrack = remember(track) { CoverTrack.of(track) }
    val side = 72.dp
    val px = with(LocalDensity.current) { side.roundToPx() }
    val coverLabel = stringResource(R.string.nag_cover_menu)
    val image = rememberCover(coverTrack, px)
    val coverMenu = rememberCoverMenu(coverTrack)
    TrackInspector(
        track = track,
        selected = subject.selected,
        waveform = rememberWaveform(track.uri),
        rating = stats[track.statsKey]?.rating ?: 0,
        onRate = { stars -> TrackStatsIndex.rate(context, track.statsKey, stars) },
        stats = stats,
        cover = { CoverMenuButton(image, side, coverLabel, coverMenu) },
    )
}

/** What plays, as the library knows it: its tags under the now playing pane. */
@Composable
private fun PlayingTags(stats: Map<String, TrackStats>) {
    val media = rememberNowPlaying().media?.takeIf { it.type == MediaWrapper.TYPE_AUDIO } ?: return
    val tech by TrackTechIndex.tech.collectAsState()
    val track = remember(media, tech) { media.toTrack(media.uri?.toString()?.let { tech[CueUri.file(it)] }) }
    Spacer(Modifier.height(NagTheme.dimens.gapS))
    HorizontalRule()
    Spacer(Modifier.height(NagTheme.dimens.gapS))
    TrackTags(track, stats)
}

/** The signal path of what plays; a word when the selection is another track. */
@Composable
private fun SignalTab(subject: InspectorSubject) {
    val dimens = NagTheme.dimens
    val playing = rememberPlayingUri()
    val path = rememberSignalPath()
    // S-613: the output's latency profile, on the path's last stage, as the equaliser's side has it
    val applied by LatencyCenter.applied.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dimens.gapL), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
        if (subject.notPlaying(playing)) InspectorNote(stringResource(R.string.nag_inspector_not_playing))
        SignalPathCard(
            path,
            MusicEngineAccess.controller?.outputDevice,
            blind = AbTest.state.phase == AbTestState.Phase.Blind,
            outputLatencyMs = applied?.delayMs ?: 0,
        )
    }
}

/** The lyrics of what plays (word by word where the file times them); a word when the selection is another track. */
@Composable
private fun LyricsTab(subject: InspectorSubject, onFullScreen: () -> Unit) {
    val dimens = NagTheme.dimens
    val playing = rememberPlayingUri()
    Column(Modifier.fillMaxSize().padding(dimens.gapL), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
        if (subject.notPlaying(playing)) InspectorNote(stringResource(R.string.nag_inspector_not_playing))
        PlayingLyrics(Modifier.weight(1f).fillMaxWidth(), onFullScreen = onFullScreen)
    }
}

/** The play queue, as "Sıradaki" has it, without its close button. */
@Composable
private fun QueueTab(undo: UndoBarState?) {
    val queue = rememberPlayQueue()
    QueuePanel(queue.entries, queue.current, queue, onClose = null, modifier = Modifier.fillMaxSize(), undo = undo, background = NagTheme.colors.surface)
}
