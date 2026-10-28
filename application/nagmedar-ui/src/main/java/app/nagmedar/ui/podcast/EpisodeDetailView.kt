package app.nagmedar.ui.podcast

import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.IconMenuButton
import app.nagmedar.ui.components.LoadingState
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.NagSlider
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.covers.CoverTile
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.playback.PlayButton
import app.nagmedar.ui.playback.PlaybackRules
import app.nagmedar.ui.playback.ProgressTimeline
import app.nagmedar.ui.playback.TimelineStyle
import app.nagmedar.ui.playback.rememberChapterMarks
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** The widest the episode page's text runs: lines longer than this are hard to follow. */
internal val DetailMaxWidth = 720.dp

/** S-205: the three tabs of an episode's page. */
internal enum class DetailTab { Notes, Chapters, Transcript }

/**
 * S-205: the pane an open episode takes: its page once it is read, a loading line before. In a phone window it fills
 * the screen and Back returns to the podcast; beside the episodes (DeX, tablet) it is the right-hand pane.
 * [listening] is read where it is drawn, so that only the page follows the playing position.
 */
@Composable
internal fun EpisodeDetailPane(model: PodcastScreenModel, fullScreen: Boolean, actions: PodcastActions, listening: () -> Listening? = { model.episode?.listening }) {
    val ui = model.episode
    if (ui == null) {
        val dimens = NagTheme.dimens
        Column(Modifier.fillMaxSize().background(NagTheme.colors.background).testTag("episode-loading")) {
            Box(Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapS)) { CloseButton(fullScreen, actions.onCloseEpisode) }
            LoadingState(stringResource(R.string.nag_pod_ep_loading), Modifier.padding(dimens.pagePadding), posters = 0)
        }
    } else {
        EpisodeDetailView(ui, fullScreen, actions, listening)
    }
}

@Composable
private fun CloseButton(fullScreen: Boolean, onClick: () -> Unit) {
    NagIconButton(
        if (fullScreen) NagIcons.ChevronLeft else NagIcons.Close,
        stringResource(if (fullScreen) R.string.nag_pod_back else R.string.nag_pod_close),
        onClick,
        modifier = Modifier.testTag("episode-close"),
    )
}

/** One row of the page: a column no wider than [DetailMaxWidth] with the page's side padding; [tight], less room above and below (a phone's head of the page). */
@Composable
internal fun Frame(modifier: Modifier = Modifier, tight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val dimens = NagTheme.dimens
    Column(
        modifier.fillMaxWidth().widthIn(max = DetailMaxWidth).padding(horizontal = dimens.pagePadding, vertical = if (tight) dimens.gapXs else dimens.gapS),
        content = content,
    )
}

/**
 * S-205: an episode's page: its cover, show, title, date, length and whether it was played; play or resume, download or
 * delete, mark played, share; the download's state; while the player has the episode, its timeline with the chapters'
 * marks, skip buttons and chapter buttons; then three tabs: the show notes (sanitised, links open outside, pictures on
 * request), the chapters (a tap plays from there) and the transcript (searchable, followed while it plays, a tap seeks).
 * A phone's page is tighter: play and download in one row, marking and sharing as small chips under them, the timeline
 * with its skip buttons right after, the download's state under them; the tabs come up on the first screen.
 */
@Composable
internal fun EpisodeDetailView(ui: EpisodeDetailUi, fullScreen: Boolean, actions: PodcastActions, listening: () -> Listening? = { ui.listening }) {
    val dimens = NagTheme.dimens
    val now = listening()
    var tab by rememberSaveable(ui.episode.id) { mutableStateOf(DetailTab.Notes) }
    var query by rememberSaveable(ui.episode.id) { mutableStateOf("") }
    var images by rememberSaveable(ui.episode.id) { mutableStateOf(false) }
    val list = rememberLazyListState()
    val chapters = (ui.chapters as? ExtraUi.Ready)?.value.orEmpty()
    val transcript = (ui.transcript as? ExtraUi.Ready)?.value
    // the passage being said, while the transcript is on show, has times and is not narrowed by a search
    val cue = if (tab == DetailTab.Transcript && transcript != null && transcript.timed && now != null && query.isBlank()) transcript.indexAt(now.positionMs) else -1
    if (cue >= 0) FollowCue(list, leadingItems(ui, now != null) + cue)
    // a phone: the tighter page (wide windows keep theirs)
    val compact = currentWidthClass() == WidthClass.Compact
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().background(NagTheme.colors.background).testTag("episode-detail"),
        contentPadding = PaddingValues(bottom = if (compact) dimens.gapXl else dimens.gapL),
    ) {
        item(key = "top") { Frame(tight = compact) { TopBar(ui, fullScreen, actions) } }
        item(key = "header") { Frame(tight = compact) { Header(ui, now, compact) } }
        if (compact) {
            // the controls together: play and download, marking and sharing, then the timeline and its skips
            item(key = "actions") { Frame(tight = true) { CompactActions(ui, now, actions) } }
            if (now != null) item(key = "timeline") { Frame(tight = true) { CompactTimeline(ui, now, chapters, actions) } }
            if (ui.download != DownloadUi.None) item(key = "download") { Frame { DownloadStatus(ui, actions) } }
            if (ui.offline) item(key = "offline") { Frame { OfflineNotice(actions.onRetryExtras) } }
        } else {
            item(key = "actions") { Frame { Actions(ui, now, actions) } }
            if (ui.download != DownloadUi.None) item(key = "download") { Frame { DownloadStatus(ui, actions) } }
            if (ui.offline) item(key = "offline") { Frame { OfflineNotice(actions.onRetryExtras) } }
            if (now != null) item(key = "timeline") { Frame { Timeline(ui, now, chapters, actions) } }
        }
        item(key = "tabs") { Frame { Tabs(tab, ui) { tab = it } } }
        when (tab) {
            DetailTab.Notes -> notesItems(ui, images, { images = it }, actions)
            DetailTab.Chapters -> chapterItems(ui, now, actions)
            DetailTab.Transcript -> transcriptItems(ui, query, { query = it }, cue, actions)
        }
    }
}

/** How many rows come before the first passage of the transcript: the page's own rows, the tabs and the search. */
private fun leadingItems(ui: EpisodeDetailUi, timeline: Boolean): Int =
    3 + (if (ui.download != DownloadUi.None) 1 else 0) + (if (ui.offline) 1 else 0) + (if (timeline) 1 else 0) + 1 + 1

/**
 * The page follows the passage being said: it scrolls to it, but not for a while after the person scrolled it
 * themselves (the same as the lyrics do).
 */
@Composable
private fun FollowCue(list: LazyListState, target: Int) {
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
    LaunchedEffect(target, following) {
        if (!following) return@LaunchedEffect
        autoScrolling = true
        try {
            if (reduced) list.scrollToItem(target) else list.animateScrollToItem(target)
        } finally {
            autoScrolling = false
        }
    }
}

private const val FOLLOW_AGAIN_MS = 4_000L

// ---------------------------------------------------------------- top, header, actions

@Composable
private fun TopBar(ui: EpisodeDetailUi, fullScreen: Boolean, actions: PodcastActions) {
    val res = LocalContext.current.resources
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CloseButton(fullScreen, actions.onCloseEpisode)
        Box(Modifier.weight(1f))
        IconMenuButton(
            NagIcons.MoreVertical,
            stringResource(R.string.nag_pod_more),
            entries = {
                listOf(
                    MenuEntry.Item(res.getString(R.string.nag_pod_show_settings), { actions.onShowSettings(ui.feed.id) }, icon = NagIcons.Settings),
                    MenuEntry.Item(res.getString(R.string.nag_pod_copy_episode_address), { actions.onCopy(ui.episode.audioUrl) }),
                ) + transcribeEntries(res, ui, actions)
            },
            modifier = Modifier.testTag("episode-more"),
        )
    }
}

/**
 * Whisper in the episode's menu: a transcript made (again, when the episode has one, the feed's or its own), its job
 * cancelled while it waits or runs, and the episode's own transcript removed.
 */
internal fun transcribeEntries(res: Resources, ui: EpisodeDetailUi, actions: PodcastActions): List<MenuEntry> {
    val shown = (ui.transcript as? ExtraUi.Ready)?.value
    val job = when (ui.transcribe) {
        is TranscribeUi.Idle -> MenuEntry.Item(
            res.getString(if (shown != null) R.string.nag_pod_whisper_again else R.string.nag_pod_whisper_create),
            { actions.onTranscribe(ui.episode) },
            icon = NagIcons.Subtitle,
        )
        is TranscribeUi.Queued, is TranscribeUi.Running -> MenuEntry.Item(res.getString(R.string.nag_pod_whisper_cancel), { actions.onCancelTranscribe(ui.episode.id) }, icon = NagIcons.Close)
        TranscribeUi.Unavailable -> null
    }
    val delete = shown?.whisperModel?.let { MenuEntry.Item(res.getString(R.string.nag_pod_whisper_delete), { actions.onDeleteTranscript(ui.episode.id) }, icon = NagIcons.Delete, destructive = true) }
    return listOfNotNull(job, delete)
}

@Composable
private fun Header(ui: EpisodeDetailUi, now: Listening?, compact: Boolean = false) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val episode = ui.episode
    val cover = rememberPodcastCover(episode.imageUrl ?: ui.feed.imageUrl, with(LocalDensity.current) { COVER.roundToPx() })
    val dates = remember(res.configuration) { DateFormat.getDateInstance(DateFormat.LONG, res.locale()) }
    val season = episode.season
    val number = episode.episodeNumber
    val numbering = when {
        season != null && number != null -> stringResource(R.string.nag_pod_ep_season_episode, season, number)
        number != null -> stringResource(R.string.nag_pod_ep_number, number)
        else -> null
    }
    val meta = listOfNotNull(
        episode.publishedAt.takeIf { it > 0 }?.let { dates.format(Date(it)) },
        episode.durationMs.takeIf { it > 0 }?.let { formatDuration(it) },
        numbering,
    ).joinToString(" · ")
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
        CoverTile(cover, COVER, corner = dimens.radiusControl, placeholder = NagIcons.Podcasts)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ui.feed.title, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("episode-show"))
            Text(episode.title, style = NagTheme.type.title, color = colors.text, maxLines = if (compact) 3 else 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("episode-title"))
            if (meta.isNotEmpty()) {
                Text(meta, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = if (compact) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("episode-meta"))
            }
            val state = playState(ui, now)
            // the badge's place is kept while it has nothing to say yet (the player's length comes a moment after the
            // page): what is under it does not move when it comes
            Box(Modifier.padding(top = 4.dp)) {
                if (state != null) {
                    ToneBadge(state.first, state.second, Modifier.testTag("episode-state"))
                } else {
                    ToneBadge("", StateTone.Neutral, Modifier.alpha(0f).clearAndSetSemantics {})
                }
            }
        }
    }
}

/** The badge under the title: played, how much is left, or new; nothing for an episode not touched and not new. */
@Composable
private fun playState(ui: EpisodeDetailUi, now: Listening?): Pair<String, StateTone>? {
    val episode = ui.episode
    val position = PlaybackRules.shownPositionMs(episode.positionMs, episode.played, now, ui.starting, episode.durationMs)
    val length = now?.lengthMs?.takeIf { it > 0 } ?: episode.durationMs
    return when {
        episode.played -> stringResource(R.string.nag_pod_played) to StateTone.Success
        position >= PlaybackRules.RESUME_MIN_MS && length > position -> stringResource(R.string.nag_pod_ep_left, formatDuration(length - position)) to StateTone.Info
        episode.addedAt > ui.feed.subscribedAt -> stringResource(R.string.nag_pod_new_badge) to StateTone.Info
        else -> null
    }
}

/** The play button's word, as [PlaybackRules.playButton] has it: "Oynat", "Devam et · 0:43" or "Duraklat". */
internal fun playLabel(res: Resources, button: PlayButton): String = when (button) {
    PlayButton.Play -> res.getString(R.string.nag_pod_play)
    is PlayButton.Resume -> res.getString(R.string.nag_pod_ep_resume, formatDuration(button.positionMs))
    PlayButton.Pause, PlayButton.Starting -> res.getString(R.string.nag_pod_ep_pause)
}

/** The play button's rule ([PlaybackRules.playButton]), word and icon; [PlayButton.Starting] waits, not pressable. */
@Composable
private fun playChoice(ui: EpisodeDetailUi, now: Listening?): Pair<PlayButton, Pair<String, ImageVector>> {
    val episode = ui.episode
    val button = PlaybackRules.playButton(episode.positionMs, episode.played, now, ui.starting, episode.durationMs)
    val icon = when (button) {
        PlayButton.Play -> NagIcons.Play
        is PlayButton.Resume -> NagIcons.Resume
        PlayButton.Pause, PlayButton.Starting -> NagIcons.Pause
    }
    return button to (playLabel(LocalContext.current.resources, button) to icon)
}

/** The download button: download, try again, stop, or delete the file. */
@Composable
private fun DownloadButton(ui: EpisodeDetailUi, actions: PodcastActions, modifier: Modifier = Modifier) {
    val episode = ui.episode
    val tagged = modifier.testTag("episode-download")
    when (ui.download) {
        DownloadUi.None -> NagButton(stringResource(R.string.nag_pod_download), { actions.onDownload(episode.id) }, icon = NagIcons.Downloads, modifier = tagged)
        DownloadUi.Failed -> NagButton(stringResource(R.string.nag_pod_download_retry), { actions.onDownload(episode.id) }, icon = NagIcons.Downloads, modifier = tagged)
        DownloadUi.Queued, is DownloadUi.Running -> NagButton(stringResource(R.string.nag_pod_download_stop), { actions.onCancelDownload(episode.id) }, icon = NagIcons.Close, modifier = tagged)
        is DownloadUi.Done -> NagButton(stringResource(R.string.nag_pod_download_delete), { actions.onDeleteDownload(episode.id) }, icon = NagIcons.Delete, modifier = tagged)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(ui: EpisodeDetailUi, now: Listening?, actions: PodcastActions) {
    val dimens = NagTheme.dimens
    val episode = ui.episode
    val (button, play) = playChoice(ui, now)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
        NagButton(play.first, { actions.onTogglePlay(ui.feed, episode) }, icon = play.second, primary = true, enabled = button != PlayButton.Starting, modifier = Modifier.testTag("episode-play"))
        DownloadButton(ui, actions)
        NagButton(
            stringResource(if (episode.played) R.string.nag_pod_mark_unplayed else R.string.nag_pod_mark_played),
            { actions.onSetPlayed(episode.id, !episode.played) },
            icon = NagIcons.Check,
            modifier = Modifier.testTag("episode-played"),
        )
        NagButton(stringResource(R.string.nag_pod_share), { actions.onShare(ui.feed, episode) }, modifier = Modifier.testTag("episode-share"))
    }
}

/**
 * A phone's: play (the room left) and download (its own width) side by side in one row, a downloaded file's delete
 * button by its icon; marking played and sharing as small chips under them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactActions(ui: EpisodeDetailUi, now: Listening?, actions: PodcastActions) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val episode = ui.episode
    val (button, play) = playChoice(ui, now)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            NagButton(
                play.first,
                { actions.onTogglePlay(ui.feed, episode) },
                icon = play.second,
                primary = true,
                enabled = button != PlayButton.Starting,
                modifier = Modifier.weight(1f).testTag("episode-play"),
            )
            if (ui.download is DownloadUi.Done) {
                // downloaded (its card says so): the file's delete button by its icon, its words its name
                NagIconButton(
                    NagIcons.Delete,
                    stringResource(R.string.nag_pod_download_delete),
                    { actions.onDeleteDownload(episode.id) },
                    modifier = Modifier.border(1.dp, colors.borderStrong, RoundedCornerShape(dimens.radiusControl)).testTag("episode-download"),
                )
            } else {
                DownloadButton(ui, actions)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            ActionChip(
                NagIcons.Check,
                stringResource(if (episode.played) R.string.nag_pod_mark_unplayed else R.string.nag_pod_mark_played),
                { actions.onSetPlayed(episode.id, !episode.played) },
                Modifier.testTag("episode-played"),
            )
            ActionChip(NagIcons.Share, stringResource(R.string.nag_pod_share), { actions.onShare(ui.feed, episode) }, Modifier.testTag("episode-share"))
        }
    }
}

/** A small outlined action of a phone's podcast pages: an icon (if any) and its word in a pill, in a finger's target. */
@Composable
internal fun ActionChip(icon: ImageVector?, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusPill)
    Box(
        modifier
            .heightIn(min = dimens.minTarget)
            .nagClickable(interaction, onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(dimens.controlHeight - dimens.gapS)
                .focusRing(state, shape)
                .background(stateBackground(state), shape)
                .border(1.dp, colors.borderStrong, shape)
                .padding(horizontal = dimens.gapM),
            horizontalArrangement = Arrangement.spacedBy(dimens.gapXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(dimens.icon))
            Text(label, style = NagTheme.type.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- the download's state

@Composable
private fun DownloadStatus(ui: EpisodeDetailUi, actions: PodcastActions) {
    val locale = LocalContext.current.resources.locale()
    val episode = ui.episode
    when (val download = ui.download) {
        DownloadUi.None -> Unit
        DownloadUi.Queued -> StatusCard(StateTone.Neutral, NagIcons.Downloads, stringResource(R.string.nag_pod_dl_queued_title), stringResource(R.string.nag_pod_dl_queued_text), "episode-download-queued")
        is DownloadUi.Running -> {
            val progress = download.progress
            val fraction = progress?.fraction
            val percent = fraction?.let { NumberFormat.getPercentInstance(locale).format(it.toDouble()) }
            val sizes = when {
                progress == null -> null
                progress.total > 0 -> stringResource(R.string.nag_pod_dl_progress, sizeLabel(progress.done, locale), sizeLabel(progress.total, locale))
                else -> stringResource(R.string.nag_pod_dl_progress_unknown, sizeLabel(progress.done, locale))
            }
            val title = stringResource(R.string.nag_pod_dl_running_title)
            StatusCard(StateTone.Info, NagIcons.Downloads, title, listOfNotNull(percent, sizes).joinToString(" · ").ifEmpty { stringResource(R.string.nag_pod_download_starting) }, "episode-download-running") {
                NagProgressBar(fraction, title, Modifier.padding(top = 6.dp))
            }
        }
        is DownloadUi.Done -> StatusCard(
            StateTone.Success,
            NagIcons.SuccessCircle,
            stringResource(R.string.nag_pod_dl_done_title),
            stringResource(R.string.nag_pod_dl_done_text, sizeLabel(download.bytes.takeIf { it > 0 } ?: episode.sizeBytes, locale)),
            "episode-download-done",
        )
        DownloadUi.Failed -> StatusCard(
            StateTone.Warning,
            NagIcons.Warning,
            stringResource(R.string.nag_pod_dl_failed_title),
            stringResource(R.string.nag_pod_dl_failed_text),
            "episode-download-failed",
            action = StateAction(stringResource(R.string.nag_pod_retry)) { actions.onDownload(episode.id) },
        )
    }
}

/** A line of the page with a tone: its icon, a title, what it says, and what can be done about it. */
@Composable
internal fun StatusCard(
    tone: StateTone,
    icon: ImageVector,
    title: String,
    text: String,
    tag: String,
    action: StateAction? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val c = toneColors(tone)
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        Modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.border, shape).padding(dimens.gapM).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapM),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = c.icon, modifier = Modifier.size(dimens.icon).padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = NagTheme.type.bodyStrong, color = colors.text)
            Text(text, style = NagTheme.type.labelSmall, color = colors.textSecondary)
            extra?.invoke(this)
        }
        if (action != null) NagButton(action.label, action.onClick, modifier = Modifier.testTag("$tag-action"))
    }
}

@Composable
private fun OfflineNotice(onRetry: () -> Unit) {
    StatusCard(
        StateTone.Neutral,
        NagIcons.NetworkOff,
        stringResource(R.string.nag_pod_search_offline_title),
        stringResource(R.string.nag_pod_ep_offline_text),
        "episode-offline",
        action = StateAction(stringResource(R.string.nag_pod_retry), onRetry),
    )
}

// ---------------------------------------------------------------- the timeline

/** "0:00", "12:03", "1:02:03": a time on a clock, zero included (the lists' [formatDuration] writes a dash for zero). */
internal fun clock(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds) else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Timeline(ui: EpisodeDetailUi, now: Listening, chapters: List<Chapter>, actions: PodcastActions) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val length = now.lengthMs.takeIf { it > 0 } ?: ui.episode.durationMs
    val at = PlaybackRules.shownPositionMs(ui.episode.positionMs, ui.episode.played, now, ui.starting, ui.episode.durationMs)
    val fraction = if (length > 0) (at.toFloat() / length).coerceIn(0f, 1f) else 0f
    val current = Chapters.indexAt(chapters, at)
    Column(verticalArrangement = Arrangement.spacedBy(dimens.gapXs), modifier = Modifier.testTag("episode-timeline")) {
        Box(Modifier.fillMaxWidth()) {
            NagSlider(
                value = fraction,
                onValueChange = { target -> if (length > 0) actions.onSeekTo((target * length).toLong()) },
                modifier = Modifier.fillMaxWidth().testTag("episode-seek"),
                enabled = length > 0,
                hoverLabel = { f -> clock((f * length).toLong()) },
            )
            if (length > 0 && chapters.size > 1) ChapterMarks(chapters, length, Modifier.matchParentSize())
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(clock(at), style = NagTheme.type.mono, color = colors.textSecondary, modifier = Modifier.testTag("episode-position"))
            Box(Modifier.weight(1f))
            if (length > 0) Text(clock(length), style = NagTheme.type.mono, color = colors.textSecondary)
        }
        if (current != null) {
            val title = chapters[current].title.ifBlank { stringResource(R.string.nag_pod_chapter_n, current + 1) }
            Text(title, style = NagTheme.type.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("episode-chapter-now"))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            NagButton(
                stringResource(R.string.nag_pod_skip_back_label, ui.skipBackSeconds),
                { actions.onSkip(-ui.skipBackSeconds * 1000L) },
                icon = NagIcons.Resume,
                modifier = Modifier.testTag("episode-skip-back"),
            )
            NagIconButton(NagIcons.Previous, stringResource(R.string.nag_pod_chapter_previous), { actions.onStepChapter(false) }, enabled = chapters.isNotEmpty(), modifier = Modifier.testTag("episode-chapter-previous"))
            NagIconButton(NagIcons.Next, stringResource(R.string.nag_pod_chapter_next), { actions.onStepChapter(true) }, enabled = chapters.isNotEmpty(), modifier = Modifier.testTag("episode-chapter-next"))
            NagButton(
                stringResource(R.string.nag_pod_skip_forward_label, ui.skipForwardSeconds),
                { actions.onSkip(ui.skipForwardSeconds * 1000L) },
                icon = NagIcons.Forward,
                modifier = Modifier.testTag("episode-skip-forward"),
            )
        }
    }
}

/**
 * A phone's timeline: the players' slim one with its times (a drag seeks, the chapters' starts marked), the chapter
 * playing, and right under them the skip back and forward buttons with the chapter buttons between.
 */
@Composable
private fun CompactTimeline(ui: EpisodeDetailUi, now: Listening, chapters: List<Chapter>, actions: PodcastActions) {
    val colors = NagTheme.colors
    val length = now.lengthMs.takeIf { it > 0 } ?: ui.episode.durationMs
    // the saved place while the player has the episode but has not started it: the timeline starts there
    val at = PlaybackRules.shownPositionMs(ui.episode.positionMs, ui.episode.played, now, ui.starting, ui.episode.durationMs)
    val current = Chapters.indexAt(chapters, at)
    val latest by rememberUpdatedState(at)
    Column(Modifier.testTag("episode-timeline")) {
        ProgressTimeline(
            progress = { if (length > 0) (latest.toFloat() / length).coerceIn(0f, 1f) else 0f },
            lengthMs = length,
            onSeek = { fraction -> if (length > 0) actions.onSeekTo((fraction * length).toLong()) },
            modifier = Modifier.testTag("episode-seek"),
            playing = now.playing,
            marks = rememberChapterMarks(chapters, length),
            style = TimelineStyle.Phone,
        )
        if (current != null) {
            val title = chapters[current].title.ifBlank { stringResource(R.string.nag_pod_chapter_n, current + 1) }
            Text(title, style = NagTheme.type.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("episode-chapter-now"))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionChip(
                NagIcons.Resume,
                stringResource(R.string.nag_pod_skip_back_label, ui.skipBackSeconds),
                { actions.onSkip(-ui.skipBackSeconds * 1000L) },
                Modifier.testTag("episode-skip-back"),
            )
            Spacer(Modifier.weight(1f))
            if (chapters.isNotEmpty()) {
                NagIconButton(NagIcons.Previous, stringResource(R.string.nag_pod_chapter_previous), { actions.onStepChapter(false) }, modifier = Modifier.testTag("episode-chapter-previous"))
                NagIconButton(NagIcons.Next, stringResource(R.string.nag_pod_chapter_next), { actions.onStepChapter(true) }, modifier = Modifier.testTag("episode-chapter-next"))
                Spacer(Modifier.weight(1f))
            }
            ActionChip(
                NagIcons.Forward,
                stringResource(R.string.nag_pod_skip_forward_label, ui.skipForwardSeconds),
                { actions.onSkip(ui.skipForwardSeconds * 1000L) },
                Modifier.testTag("episode-skip-forward"),
            )
        }
    }
}

/** The chapters' starts as small ticks over the seek bar's track. */
@Composable
private fun ChapterMarks(chapters: List<Chapter>, length: Long, modifier: Modifier) {
    val color = NagTheme.colors.text.copy(alpha = 0.55f)
    Canvas(modifier) {
        val width = size.width
        val centre = size.height / 2
        val half = 5.dp.toPx()
        chapters.drop(1).forEach { chapter ->
            val x = width * (chapter.startMs.toFloat() / length).coerceIn(0f, 1f)
            drawLine(color, Offset(x, centre - half), Offset(x, centre + half), strokeWidth = 2.dp.toPx())
        }
    }
}

// ---------------------------------------------------------------- the tabs

@Composable
private fun Tabs(tab: DetailTab, ui: EpisodeDetailUi, onSelect: (DetailTab) -> Unit) {
    val chapters = (ui.chapters as? ExtraUi.Ready)?.value
    SegmentedChoice(
        options = listOf(
            DetailTab.Notes to stringResource(R.string.nag_pod_tab_notes),
            DetailTab.Chapters to if (chapters != null) pluralStringResource(R.plurals.nag_pod_tab_chapters_count, chapters.size, chapters.size) else stringResource(R.string.nag_pod_tab_chapters),
            DetailTab.Transcript to stringResource(R.string.nag_pod_tab_transcript),
        ),
        selected = tab,
        onSelect = onSelect,
        modifier = Modifier.testTag("episode-tabs"),
    )
}

/** The picture size of the page's cover, in dp. */
private val COVER = 96.dp
