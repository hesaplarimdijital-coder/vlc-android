package app.nagmedar.ui.library

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.components.TableSort
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.TAG_ITEM
import org.videolan.vlc.gui.InfoActivity
import org.videolan.vlc.media.MediaUtils

private enum class ViewMode { Grid, Table }

/** Context menu of a media item; the same entries serve right click, long press and the Menu key. */
fun mediaMenu(context: Context, media: MediaWrapper, play: () -> Unit): List<MenuEntry> {
    val res = context.resources
    return listOf(
        MenuEntry.Item(res.getString(R.string.nag_play), play, shortcut = "Enter", icon = NagIcons.Play),
        MenuEntry.Item(res.getString(R.string.nag_play_next), { MediaUtils.insertNext(context, media) }, icon = NagIcons.Next),
        MenuEntry.Item(res.getString(R.string.nag_add_to_queue), { MediaUtils.appendMedia(context, media) }, icon = NagIcons.PlaylistAdd),
        MenuEntry.Separator,
        MenuEntry.Item(
            res.getString(R.string.nag_sc_media_info),
            { context.startActivity(Intent(context, InfoActivity::class.java).putExtra(TAG_ITEM, media)) },
            shortcut = ShortcutRegistry.chordLabel(ShortcutId.MediaInfo, res),
            icon = NagIcons.Info,
        ),
    )
}

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(repository: MediaRepository) {
    val context = LocalContext.current
    val dimens = NagTheme.dimens
    val videos by produceState<List<MediaWrapper>?>(null) { value = repository.recentVideos() }
    val tracks by produceState<List<MediaWrapper>?>(null) { value = repository.recentAudio() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dimens.gapXl)) {
        item { PageHeader(title = stringResource(R.string.nag_nav_home)) }
        val loadedVideos = videos
        val loadedTracks = tracks
        if (loadedVideos == null || loadedTracks == null) {
            item { CenteredMessage(stringResource(R.string.nag_library_loading)) }
            return@LazyColumn
        }
        if (loadedVideos.isEmpty() && loadedTracks.isEmpty()) {
            item { CenteredMessage(stringResource(R.string.nag_home_empty)) }
            return@LazyColumn
        }
        if (loadedVideos.isNotEmpty()) {
            item { ShelfTitle(stringResource(R.string.nag_home_continue)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = dimens.pagePadding), horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                    itemsIndexed(loadedVideos, key = { _, m -> m.id }) { _, media ->
                        VideoCard(media, Modifier.width(240.dp)) { PlaybackActions.playVideo(context, media) }
                    }
                }
            }
        }
        if (loadedTracks.isNotEmpty()) {
            item { ShelfTitle(stringResource(R.string.nag_home_recent_music)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = dimens.pagePadding), horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                    itemsIndexed(loadedTracks, key = { _, m -> m.id }) { index, media ->
                        TrackCard(media, Modifier.width(150.dp)) { PlaybackActions.playList(context, loadedTracks, index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfTitle(text: String) {
    Text(
        text,
        style = NagTheme.type.title,
        color = NagTheme.colors.text,
        modifier = Modifier
            .padding(start = NagTheme.dimens.pagePadding, top = NagTheme.dimens.gapL, bottom = NagTheme.dimens.gapS)
            .semantics { heading() },
    )
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = NagTheme.type.body, color = NagTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- Movies

@Composable
fun MoviesScreen(repository: MediaRepository) {
    val context = LocalContext.current
    val dimens = NagTheme.dimens
    val videos by produceState<List<MediaWrapper>?>(null) { value = repository.videos() }
    val pointer = NagTheme.inputMode == InputMode.Pointer
    var mode by rememberSaveable { mutableStateOf(if (pointer) ViewMode.Table else ViewMode.Grid) }
    var sort by rememberSaveable(stateSaver = TableSortSaver) { mutableStateOf<TableSort?>(null) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val list = videos
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.nag_nav_movies),
            subtitle = list?.let { stringResource(R.string.nag_videos_count, it.size) },
        ) {
            NagIconButton(NagIcons.Grid, stringResource(R.string.nag_view_grid), { mode = ViewMode.Grid }, selected = mode == ViewMode.Grid)
            NagIconButton(NagIcons.ListView, stringResource(R.string.nag_view_list), { mode = ViewMode.Table }, selected = mode == ViewMode.Table)
        }
        when {
            list == null -> CenteredMessage(stringResource(R.string.nag_library_loading))
            list.isEmpty() -> CenteredMessage(stringResource(R.string.nag_library_empty_video))
            mode == ViewMode.Grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(if (pointer) 190.dp else 220.dp),
                contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapS),
                horizontalArrangement = Arrangement.spacedBy(dimens.gapL),
                verticalArrangement = Arrangement.spacedBy(dimens.gapL),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(list, key = { _, m -> m.id }) { _, media ->
                    val play = { PlaybackActions.playVideo(context, media) }
                    ContextMenuArea(entries = { mediaMenu(context, media, play) }, accessibilityLabel = stringResource(R.string.nag_more_actions)) {
                        VideoCard(media, Modifier.fillMaxWidth(), onClick = play)
                    }
                }
            }
            else -> {
                val sorted = remember(list, sort) { sortVideos(list, sort) }
                DenseTable(
                    columns = videoColumns(),
                    rows = sorted,
                    key = { it.id },
                    selectedKey = selected,
                    sort = sort,
                    onSortChange = { sort = it },
                    onRowClick = { media, _ ->
                        selected = media.id
                        PlaybackActions.playVideo(context, media)
                    },
                    rowMenu = { media, _ -> mediaMenu(context, media) { PlaybackActions.playVideo(context, media) } },
                    rowMenuLabel = stringResource(R.string.nag_more_actions),
                )
            }
        }
    }
}

@Composable
private fun videoColumns(): List<TableColumn<MediaWrapper>> {
    val title = stringResource(R.string.nag_column_title)
    val resolution = stringResource(R.string.nag_column_resolution)
    val duration = stringResource(R.string.nag_column_duration)
    return remember(title, resolution, duration) {
        listOf(
            TableColumn<MediaWrapper>(title, ColumnWidth.Weight(1f)) { it.title ?: "" },
            TableColumn<MediaWrapper>(resolution, ColumnWidth.Fixed(110.dp), mono = true) { it.resolutionLabel() ?: "–" },
            TableColumn<MediaWrapper>(duration, ColumnWidth.Fixed(100.dp), alignEnd = true, mono = true) { formatDuration(it.length) },
        )
    }
}

private fun sortVideos(list: List<MediaWrapper>, sort: TableSort?): List<MediaWrapper> {
    if (sort == null) return list
    val comparator: Comparator<MediaWrapper> = when (sort.column) {
        1 -> compareBy { it.height.coerceAtMost(it.width) }
        2 -> compareBy { it.length }
        else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title ?: "" }
    }
    return if (sort.ascending) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
}

/** Saves a table sort as one Int: column * 2 + 1 when ascending, -1 for no sort. */
private val TableSortSaver = Saver<TableSort?, Int>(
    save = { sort -> sort?.let { it.column * 2 + if (it.ascending) 1 else 0 } ?: -1 },
    restore = { saved -> if (saved < 0) null else TableSort(saved / 2, saved % 2 == 1) },
)

@Composable
fun VideoCard(media: MediaWrapper, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusCard)
    Column(
        modifier = modifier
            .focusRing(state, shape)
            .background(if (state.hovered || state.pressed) stateBackground(state) else colors.background, shape)
            .nagClickable(interaction, onClick, onClickLabel = media.title)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(dimens.radiusControl))) {
            Artwork(media, size = 320.dp, modifier = Modifier.fillMaxSize(), corner = 0.dp)
            val progress = media.watchProgress()
            if (progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(colors.scrim)) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.accent))
                }
            }
            media.resolutionLabel()?.let { res ->
                Text(
                    res,
                    style = NagTheme.type.mono,
                    color = colors.text,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(colors.scrim, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(media.title ?: "", style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatDuration(media.length), style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1)
    }
}

@Composable
private fun TrackCard(media: MediaWrapper, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusCard)
    Column(
        modifier = modifier
            .focusRing(state, shape)
            .background(if (state.hovered || state.pressed) stateBackground(state) else colors.background, shape)
            .nagClickable(interaction, onClick, onClickLabel = media.title)
            .padding(6.dp),
    ) {
        Artwork(media, size = 138.dp, corner = dimens.radiusControl)
        Spacer(Modifier.height(8.dp))
        Text(media.title ?: "", style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(media.artistName ?: stringResource(R.string.nag_unknown_artist), style = NagTheme.type.labelSmall,
            color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- Music

@Composable
fun MusicScreen(repository: MediaRepository) {
    val context = LocalContext.current
    val tracks by produceState<List<MediaWrapper>?>(null) { value = repository.tracks() }
    var sort by rememberSaveable(stateSaver = TableSortSaver) { mutableStateOf<TableSort?>(null) }
    val list = tracks
    val unknown = stringResource(R.string.nag_unknown_artist)
    Column(Modifier.fillMaxSize()) {
        PageHeader(title = stringResource(R.string.nag_nav_music), subtitle = list?.let { stringResource(R.string.nag_tracks_count, it.size) })
        when {
            list == null -> CenteredMessage(stringResource(R.string.nag_library_loading))
            list.isEmpty() -> CenteredMessage(stringResource(R.string.nag_library_empty_music))
            else -> {
                val sorted = remember(list, sort) { sortTracks(list, sort) }
                val titleCol = stringResource(R.string.nag_column_title)
                val artistCol = stringResource(R.string.nag_column_artist)
                val albumCol = stringResource(R.string.nag_column_album)
                val durationCol = stringResource(R.string.nag_column_duration)
                val columns = remember(titleCol, artistCol, albumCol, durationCol, unknown) {
                    listOf(
                        TableColumn<MediaWrapper>(titleCol, ColumnWidth.Weight(1.4f)) { it.title ?: "" },
                        TableColumn<MediaWrapper>(artistCol, ColumnWidth.Weight(1f)) { it.artistName ?: unknown },
                        TableColumn<MediaWrapper>(albumCol, ColumnWidth.Weight(1f)) { it.albumName ?: "" },
                        TableColumn<MediaWrapper>(durationCol, ColumnWidth.Fixed(90.dp), alignEnd = true, mono = true) { formatDuration(it.length) },
                    )
                }
                DenseTable(
                    columns = columns,
                    rows = sorted,
                    key = { it.id },
                    sort = sort,
                    onSortChange = { sort = it },
                    onRowClick = { _, index -> PlaybackActions.playList(context, sorted, index) },
                    rowMenu = { media, index -> mediaMenu(context, media) { PlaybackActions.playList(context, sorted, index) } },
                    rowMenuLabel = stringResource(R.string.nag_more_actions),
                )
            }
        }
    }
}

private fun sortTracks(list: List<MediaWrapper>, sort: TableSort?): List<MediaWrapper> {
    if (sort == null) return list
    val comparator: Comparator<MediaWrapper> = when (sort.column) {
        1 -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.artistName ?: "" }
        2 -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.albumName ?: "" }
        3 -> compareBy { it.length }
        else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title ?: "" }
    }
    return if (sort.ascending) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
}
