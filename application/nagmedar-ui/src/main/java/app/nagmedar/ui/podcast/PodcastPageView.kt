package app.nagmedar.ui.podcast

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.LoadingState
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagPlayButton
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.covers.CoverTile
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.library.MediaFile
import app.nagmedar.ui.library.fileMenuEntries
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.NagTheme
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/**
 * S-1101: a podcast's page: its cover, who makes it, how many episodes are new, links to its web
 * page and to where to support it, "Abonelikten çık", then the episodes, the newest first, each
 * with its date, its length and whether it was listened to. A click or Enter opens the episode's page
 * (S-205), its play button plays it; its menu plays, downloads (S-1103), marks it listened to or not and copies its address.
 * [downloads] are the downloads running now; [selected] is the episode whose page is open beside the list.
 */
@Composable
internal fun PodcastPageView(
    page: PodcastPage,
    fullScreen: Boolean,
    actions: PodcastActions,
    downloads: Map<Long, DownloadProgress> = emptyMap(),
    selected: Long? = null,
) {
    val res = LocalContext.current.resources
    val dimens = NagTheme.dimens
    val feed = page.summary.feed
    val dates = remember(res.configuration) { DateFormat.getDateInstance(DateFormat.MEDIUM, res.locale()) }
    LazyColumn(
        Modifier.fillMaxSize().background(NagTheme.colors.background).testTag("podcast-page"),
        contentPadding = PaddingValues(bottom = dimens.gapL),
    ) {
        item(key = "top") { PageTop(page, fullScreen, actions) }
        val episodes = page.episodes
        if (episodes == null) {
            item(key = "loading") { LoadingState(stringResource(R.string.nag_pod_episodes_loading), Modifier.padding(dimens.pagePadding), posters = 0) }
        } else {
            items(episodes, key = { it.id }) { episode -> EpisodeRow(feed, episode, dates, actions, DownloadUi.of(episode, downloads[episode.id]), episode.id == selected) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageTop(page: PodcastPage, fullScreen: Boolean, actions: PodcastActions) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val feed = page.summary.feed
    val cover = rememberPodcastCover(feed.imageUrl, with(LocalDensity.current) { PAGE_COVER.roundToPx() })
    var expanded by rememberSaveable(feed.id) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapM)) {
        NagIconButton(
            if (fullScreen) NagIcons.ChevronLeft else NagIcons.Close,
            stringResource(if (fullScreen) R.string.nag_pod_back else R.string.nag_pod_close),
            actions.onClosePage,
            modifier = Modifier.testTag("podcast-page-close"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
            CoverTile(cover, PAGE_COVER, corner = dimens.radiusControl, placeholder = NagIcons.Podcasts)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(feed.title, style = NagTheme.type.title, color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                feed.author?.let { Text(it, style = NagTheme.type.body, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(
                    res.getQuantityString(R.plurals.nag_pod_episodes, page.summary.episodeCount, page.summary.episodeCount),
                    style = NagTheme.type.labelSmall,
                    color = colors.textSecondary,
                )
                if (page.summary.newCount > 0) {
                    ToneBadge(pluralStringResource(R.plurals.nag_pod_new, page.summary.newCount, page.summary.newCount), StateTone.Info, Modifier.padding(top = 4.dp))
                }
            }
        }
        if (currentWidthClass() == WidthClass.Compact) {
            // a phone: small chips, as on an episode's page
            FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                feed.link?.let { link -> ActionChip(NagIcons.Network, stringResource(R.string.nag_pod_web_page), { actions.onOpenLink(link) }) }
                feed.fundingUrl?.let { url -> ActionChip(NagIcons.Star, stringResource(R.string.nag_pod_support), { actions.onOpenLink(url) }) }
                ActionChip(NagIcons.Settings, stringResource(R.string.nag_pod_show_settings), { actions.onShowSettings(feed.id) }, Modifier.testTag("podcast-settings"))
                ActionChip(null, stringResource(R.string.nag_pod_unsubscribe), { actions.onUnsubscribe(feed.id) }, Modifier.testTag("podcast-unsubscribe"))
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                feed.link?.let { link -> NagButton(stringResource(R.string.nag_pod_web_page), { actions.onOpenLink(link) }, icon = NagIcons.Network) }
                feed.fundingUrl?.let { url -> NagButton(stringResource(R.string.nag_pod_support), { actions.onOpenLink(url) }, icon = NagIcons.Star) }
                NagButton(stringResource(R.string.nag_pod_show_settings), { actions.onShowSettings(feed.id) }, icon = NagIcons.Settings, modifier = Modifier.testTag("podcast-settings"))
                NagButton(stringResource(R.string.nag_pod_unsubscribe), { actions.onUnsubscribe(feed.id) }, modifier = Modifier.testTag("podcast-unsubscribe"))
            }
        }
        feed.description?.let { description ->
            val interaction = remember { MutableInteractionSource() }
            val state = interaction.collectState()
            val shape = RoundedCornerShape(dimens.radiusSmall)
            Text(
                description,
                style = NagTheme.type.body,
                color = colors.textSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .focusRing(state, shape)
                    .nagClickable(
                        interaction,
                        { expanded = !expanded },
                        role = Role.Button,
                        onClickLabel = res.getString(if (expanded) R.string.nag_pod_less else R.string.nag_pod_more_text),
                    )
                    .testTag("podcast-description"),
            )
        }
    }
}

@Composable
private fun EpisodeRow(feed: PodcastFeed, episode: PodcastEpisode, dates: DateFormat, actions: PodcastActions, download: DownloadUi, selected: Boolean) {
    val context = LocalContext.current
    val res = context.resources
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val played = episode.played
    val fresh = !played && episode.addedAt > feed.subscribedAt
    // the episode page's rule: "Devam et · 0:43" where it was left, else "Oynat" (a row starts it, never pauses it)
    val playWord = playLabel(res, PlaybackRules.playButton(episode.positionMs, played, now = null))
    val meta = listOfNotNull(
        episode.publishedAt.takeIf { it > 0 }?.let { dates.format(Date(it)) },
        episode.durationMs.takeIf { it > 0 }?.let { formatDuration(it) },
    ).joinToString(" · ")
    ContextMenuArea(
        entries = {
            listOf(
                MenuEntry.Item(playWord, { actions.onPlay(feed, episode) }, icon = NagIcons.Play),
                MenuEntry.Item(res.getString(R.string.nag_pod_open_episode), { actions.onOpenEpisode(episode.id) }, icon = NagIcons.Info),
                downloadEntry(res, episode, download, actions),
                MenuEntry.Item(
                    res.getString(if (played) R.string.nag_pod_mark_unplayed else R.string.nag_pod_mark_played),
                    { actions.onSetPlayed(episode.id, !played) },
                    icon = NagIcons.Check,
                ),
                MenuEntry.Item(res.getString(R.string.nag_pod_copy_episode_address), { actions.onCopy(episode.audioUrl) }),
            ) + downloadedFileEntries(context, episode, download)
        },
        accessibilityLabel = res.getString(R.string.nag_pod_actions),
        modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.gapS),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .focusRing(state, shape)
                .background(stateBackground(state, selected), shape)
                .nagClickable(interaction, { actions.onOpenEpisode(episode.id) }, onClickLabel = res.getString(R.string.nag_pod_open_episode))
                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
                .testTag("podcast-episode"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (played) {
                    Icon(NagIcons.Check, contentDescription = stringResource(R.string.nag_pod_played), tint = colors.textTertiary, modifier = Modifier.size(16.dp))
                } else {
                    val unplayed = stringResource(R.string.nag_pod_unplayed)
                    Box(Modifier.size(8.dp).background(colors.accent, CircleShape).semantics { contentDescription = unplayed })
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    episode.title,
                    style = if (played) NagTheme.type.body else NagTheme.type.bodyStrong,
                    color = if (played) colors.textSecondary else colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotEmpty()) Text(meta, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (fresh) ToneBadge(stringResource(R.string.nag_pod_new_badge), StateTone.Info, Modifier.testTag("podcast-episode-new"))
            DownloadMark(download)
            NagPlayButton(playWord, { actions.onPlay(feed, episode) }, modifier = Modifier.testTag("podcast-play"))
        }
    }
}

/** The menu line of an episode's download: download, stop it, or delete the file, by where it stands. */
private fun downloadEntry(res: android.content.res.Resources, episode: PodcastEpisode, download: DownloadUi, actions: PodcastActions): MenuEntry.Item = when (download) {
    DownloadUi.None, DownloadUi.Failed -> MenuEntry.Item(res.getString(R.string.nag_pod_download), { actions.onDownload(episode.id) }, icon = NagIcons.Downloads)
    DownloadUi.Queued, is DownloadUi.Running -> MenuEntry.Item(res.getString(R.string.nag_pod_download_stop), { actions.onCancelDownload(episode.id) }, icon = NagIcons.Close)
    is DownloadUi.Done -> MenuEntry.Item(res.getString(R.string.nag_pod_download_delete), { actions.onDeleteDownload(episode.id) }, icon = NagIcons.Delete, destructive = true)
}

/** A downloaded episode's file: shared ("Paylaş…") or shown in its folder ("Dosyada göster"); nothing before it is down. */
private fun downloadedFileEntries(context: Context, episode: PodcastEpisode, download: DownloadUi): List<MenuEntry> {
    val location = episode.downloadPath?.takeIf { download is DownloadUi.Done } ?: return emptyList()
    val file = MediaFile(DownloadFiles.uriOf(location), video = DownloadFiles.mimeOf(episode).startsWith("video/"), name = episode.title)
    val entries = fileMenuEntries(context, file)
    return if (entries.isEmpty()) entries else listOf(MenuEntry.Separator) + entries
}

/** What a row says of its episode's download: nothing before it, a few words while it waits or runs, a mark once it is on the device or failed. */
@Composable
private fun DownloadMark(download: DownloadUi) {
    val colors = NagTheme.colors
    when (download) {
        DownloadUi.None -> Unit
        DownloadUi.Queued -> Text(stringResource(R.string.nag_pod_download_queued_short), style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("podcast-download-mark"))
        is DownloadUi.Running -> {
            val percent = download.progress?.fraction?.let { NumberFormat.getPercentInstance(LocalContext.current.resources.locale()).format(it.toDouble()) }
            Text(percent ?: stringResource(R.string.nag_pod_download_starting), style = NagTheme.type.labelSmall, color = colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("podcast-download-mark"))
        }
        is DownloadUi.Done -> Icon(
            NagIcons.Downloads,
            contentDescription = stringResource(R.string.nag_pod_download_done_short),
            tint = toneColors(StateTone.Success).icon,
            modifier = Modifier.size(16.dp).testTag("podcast-download-mark"),
        )
        DownloadUi.Failed -> Icon(
            NagIcons.Warning,
            contentDescription = stringResource(R.string.nag_pod_download_failed_short),
            tint = toneColors(StateTone.Warning).icon,
            modifier = Modifier.size(16.dp).testTag("podcast-download-mark"),
        )
    }
}

/** The page's cover, in dp. */
private val PAGE_COVER = 104.dp

/** A description is cut after this many lines until it is asked for in full. */
private const val DESCRIPTION_LINES = 4
