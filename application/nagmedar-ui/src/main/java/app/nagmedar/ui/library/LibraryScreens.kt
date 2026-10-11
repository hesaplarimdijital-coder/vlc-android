package app.nagmedar.ui.library

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.ProduceStateScope
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.NativeLab
import app.nagmedar.ui.R
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
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
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.TAG_ITEM
import org.videolan.vlc.gui.InfoActivity
import org.videolan.vlc.media.MediaUtils
import kotlinx.coroutines.delay

private enum class ViewMode { Grid, Table }

/**
 * Context menu of a media item; the same entries serve right click, long press and the Menu key.
 * [info] shows the details in this interface (videos, S-802); without it the classic media info opens.
 */
fun mediaMenu(context: Context, media: MediaWrapper, play: () -> Unit, info: (() -> Unit)? = null): List<MenuEntry> {
    val res = context.resources
    return listOf(
        MenuEntry.Item(res.getString(R.string.nag_play), play, shortcut = "Enter", icon = NagIcons.Play),
        MenuEntry.Item(res.getString(R.string.nag_play_next), { MediaUtils.insertNext(context, media) }, icon = NagIcons.Next),
        MenuEntry.Item(res.getString(R.string.nag_add_to_queue), { MediaUtils.appendMedia(context, media) }, icon = NagIcons.PlaylistAdd),
        MenuEntry.Separator,
        MenuEntry.Item(
            res.getString(R.string.nag_sc_media_info),
            info ?: { context.startActivity(Intent(context, InfoActivity::class.java).putExtra(TAG_ITEM, media)) },
            shortcut = ShortcutRegistry.chordLabel(ShortcutId.MediaInfo, res),
            icon = NagIcons.Info,
        ),
    )
}

// ---------------------------------------------------------------- Home

/** While the medialibrary scans, the screens reload this often so new media appear. */
private const val RELOAD_WHILE_SCANNING_MS = 4_000L

/** Loads with [load] now and, while a scan runs, again every few seconds. */
private suspend fun <T> ProduceStateScope<T?>.loadWhileScanning(scanning: Boolean, load: suspend () -> T) {
    while (true) {
        value = load()
        if (!scanning) break
        delay(RELOAD_WHILE_SCANNING_MS)
    }
}

private class HomeContent(val videos: List<MediaWrapper>, val tracks: List<MediaWrapper>, val libraryEmpty: Boolean)

@Composable
fun HomeScreen(repository: MediaRepository, onAddFolder: () -> Unit, onBrowse: (Destination) -> Unit) {
    val context = LocalContext.current
    val dimens = NagTheme.dimens
    val scan = rememberScanState()
    val access = LocalLibraryAccess.current
    val home by produceState<HomeContent?>(null, scan.active, access?.media, access?.folders) {
        loadWhileScanning(scan.active) {
            val counts = repository.counts()
            HomeContent(repository.recentVideos(), repository.recentAudio(), counts.videos + counts.tracks == 0)
        }
    }
    val loaded = home
    Column(Modifier.fillMaxSize()) {
        PageHeader(title = stringResource(R.string.nag_nav_home))
        LibraryContent(
            loading = loaded == null,
            empty = loaded?.libraryEmpty == true,
            scan = scan,
            emptyTitle = stringResource(R.string.nag_state_empty_title),
            emptyDescription = stringResource(R.string.nag_state_empty_desc),
            onAddFolder = onAddFolder,
        ) {
            val videoItems = remember(loaded) { loaded?.videos.orEmpty().map { it.toVideoItem() } }
            val tech by rememberTech(videoItems)
            val tracks = loaded?.tracks.orEmpty()
            if (videoItems.isEmpty() && tracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    StatePanel(
                        tone = StateTone.Neutral,
                        icon = NagIcons.Play,
                        title = stringResource(R.string.nag_home_nothing_title),
                        description = stringResource(R.string.nag_home_nothing_desc),
                        primary = StateAction(stringResource(R.string.nag_home_browse_movies)) { onBrowse(Destination.Movies) },
                        secondary = StateAction(stringResource(R.string.nag_home_browse_music)) { onBrowse(Destination.Music) },
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dimens.gapXl)) {
                    if (videoItems.isNotEmpty()) {
                        item { ShelfTitle(stringResource(R.string.nag_home_continue)) }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = dimens.pagePadding), horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                                itemsIndexed(videoItems, key = { _, item -> item.key }) { _, item ->
                                    VideoCard(item, tech[item.key], Modifier.width(240.dp), onClick = { item.media?.let { PlaybackActions.playVideo(context, it) } })
                                }
                            }
                        }
                    }
                    if (tracks.isNotEmpty()) {
                        item { ShelfTitle(stringResource(R.string.nag_home_recent_music)) }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = dimens.pagePadding), horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
                                itemsIndexed(tracks, key = { _, m -> m.id }) { index, media ->
                                    TrackCard(media, Modifier.width(150.dp)) { PlaybackActions.playList(context, tracks, index) }
                                }
                            }
                        }
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

// ---------------------------------------------------------------- Movies

@Composable
fun MoviesScreen(repository: MediaRepository, onAddFolder: () -> Unit) {
    val context = LocalContext.current
    val scan = rememberScanState()
    val access = LocalLibraryAccess.current
    val videos by produceState<List<MediaWrapper>?>(null, scan.active, access?.media, access?.folders) {
        loadWhileScanning(scan.active) { repository.videos() }
    }
    val items = remember(videos) { videos?.map { it.toVideoItem() } }
    val tech by rememberTech(items)
    val pointer = NagTheme.inputMode == InputMode.Pointer
    var mode by rememberSaveable { mutableStateOf(if (pointer) ViewMode.Table else ViewMode.Grid) }
    var sort by rememberSaveable(stateSaver = TableSortSaver) { mutableStateOf<TableSort?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsRequested by rememberSaveable { mutableStateOf(false) }
    val list = items
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.nag_nav_movies),
            subtitle = list?.let { stringResource(R.string.nag_videos_count, it.size) },
        ) {
            if (!list.isNullOrEmpty()) {
                NagIconButton(NagIcons.Grid, stringResource(R.string.nag_view_grid), { mode = ViewMode.Grid }, selected = mode == ViewMode.Grid)
                NagIconButton(NagIcons.ListView, stringResource(R.string.nag_view_list), { mode = ViewMode.Table }, selected = mode == ViewMode.Table)
            }
        }
        LibraryContent(
            loading = list == null,
            empty = list?.isEmpty() == true,
            scan = scan,
            emptyTitle = stringResource(R.string.nag_state_no_videos_title),
            emptyDescription = stringResource(R.string.nag_state_no_videos_desc),
            onAddFolder = onAddFolder,
        ) {
            MoviesContent(
                items = list.orEmpty(),
                tech = tech,
                techState = { item -> techStateOf(item, tech) },
                grid = mode == ViewMode.Grid,
                sort = sort,
                onSortChange = { sort = it },
                selected = selected,
                onSelect = { selected = it.key },
                detailsRequested = detailsRequested,
                onShowDetails = {
                    selected = it.key
                    detailsRequested = true
                },
                onCloseDetails = {
                    selected = null
                    detailsRequested = false
                },
                onPlay = { item -> item.media?.let { PlaybackActions.playVideo(context, it) } },
                menu = { item, play, info -> item.media?.let { mediaMenu(context, it, play, info) }.orEmpty() },
            )
        }
    }
}

/** Probed, still probing, or never: the native probe is missing from this build. */
fun techStateOf(item: VideoItem, tech: Map<String, MediaTech>): TechState = when {
    tech.containsKey(item.key) -> TechState.Ready
    NativeLab.available -> TechState.Probing
    else -> TechState.Unavailable
}

/** Width from which the details panel sits beside the list instead of over it. */
private val DetailsBesideMinWidth = 900.dp

/**
 * The movies as a grid of cards or a dense table, with the details of the selected one (S-802).
 * With a pointer a click selects and a double click plays; by touch a tap plays and the details
 * open from the item's menu. In a wide window the details sit beside the list; in a narrow one
 * they open over it when asked for.
 */
@Composable
fun MoviesContent(
    items: List<VideoItem>,
    tech: Map<String, MediaTech>,
    techState: (VideoItem) -> TechState,
    grid: Boolean,
    sort: TableSort?,
    onSortChange: (TableSort) -> Unit,
    selected: String?,
    onSelect: (VideoItem) -> Unit,
    detailsRequested: Boolean,
    onShowDetails: (VideoItem) -> Unit,
    onCloseDetails: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    menu: (VideoItem, play: () -> Unit, info: () -> Unit) -> List<MenuEntry>,
) {
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val selectedItem = items.firstOrNull { it.key == selected }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val beside = maxWidth >= DetailsBesideMinWidth
        val panel = selectedItem != null && (beside && (pointer || detailsRequested))
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val click: (VideoItem) -> Unit = { item -> if (pointer) onSelect(item) else onPlay(item) }
                val doubleClick: ((VideoItem) -> Unit)? = if (pointer) onPlay else null
                if (grid) {
                    VideoGrid(items, tech, selected, click, doubleClick, menu, onPlay, onShowDetails)
                } else {
                    VideoTable(items, tech, sort, onSortChange, selected, click, doubleClick, menu, onPlay, onShowDetails)
                }
            }
            if (panel && selectedItem != null) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(NagTheme.colors.border))
                MediaDetailsPanel(
                    item = selectedItem,
                    tech = tech[selectedItem.key],
                    state = techState(selectedItem),
                    onPlay = { onPlay(selectedItem) },
                    onClose = onCloseDetails,
                    modifier = Modifier.width(336.dp),
                )
            }
        }
        if (!beside && detailsRequested && selectedItem != null) {
            Dialog(onDismissRequest = onCloseDetails, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                MediaDetailsPanel(
                    item = selectedItem,
                    tech = tech[selectedItem.key],
                    state = techState(selectedItem),
                    onPlay = { onPlay(selectedItem) },
                    onClose = onCloseDetails,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun VideoGrid(
    items: List<VideoItem>,
    tech: Map<String, MediaTech>,
    selected: String?,
    onClick: (VideoItem) -> Unit,
    onDoubleClick: ((VideoItem) -> Unit)?,
    menu: (VideoItem, () -> Unit, () -> Unit) -> List<MenuEntry>,
    onPlay: (VideoItem) -> Unit,
    onShowDetails: (VideoItem) -> Unit,
) {
    val dimens = NagTheme.dimens
    val pointer = NagTheme.inputMode == InputMode.Pointer
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (pointer) 190.dp else 220.dp),
        contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapL),
        verticalArrangement = Arrangement.spacedBy(dimens.gapL),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, item -> item.key }) { _, item ->
            ContextMenuArea(
                entries = { menu(item, { onPlay(item) }, { onShowDetails(item) }) },
                accessibilityLabel = stringResource(R.string.nag_more_actions),
            ) {
                VideoCard(
                    item,
                    tech[item.key],
                    Modifier.fillMaxWidth(),
                    selected = item.key == selected,
                    onClick = { onClick(item) },
                    onDoubleClick = onDoubleClick?.let { open -> { open(item) } },
                )
            }
        }
    }
}

@Composable
private fun VideoTable(
    items: List<VideoItem>,
    tech: Map<String, MediaTech>,
    sort: TableSort?,
    onSortChange: (TableSort) -> Unit,
    selected: String?,
    onClick: (VideoItem) -> Unit,
    onDoubleClick: ((VideoItem) -> Unit)?,
    menu: (VideoItem, () -> Unit, () -> Unit) -> List<MenuEntry>,
    onPlay: (VideoItem) -> Unit,
    onShowDetails: (VideoItem) -> Unit,
) {
    val sorted = remember(items, sort, tech) { sortVideos(items, sort, tech) }
    DenseTable(
        columns = videoColumns(tech),
        rows = sorted,
        key = { it.key },
        selectedKey = selected,
        sort = sort,
        onSortChange = onSortChange,
        onRowClick = { item, _ -> onClick(item) },
        onRowDoubleClick = onDoubleClick?.let { open -> { item, _ -> open(item) } },
        rowMenu = { item, _ -> menu(item, { onPlay(item) }, { onShowDetails(item) }) },
        rowMenuLabel = stringResource(R.string.nag_more_actions),
    )
}

@Composable
private fun videoColumns(tech: Map<String, MediaTech>): List<TableColumn<VideoItem>> {
    val title = stringResource(R.string.nag_column_title)
    val video = stringResource(R.string.nag_column_video)
    val audio = stringResource(R.string.nag_column_audio)
    val duration = stringResource(R.string.nag_column_duration)
    return remember(title, video, audio, duration, tech) {
        listOf(
            TableColumn<VideoItem>(title, ColumnWidth.Weight(1f)) { it.title },
            TableColumn<VideoItem>(video, ColumnWidth.Fixed(170.dp), mono = true) { item ->
                tech[item.key]?.video?.let { "${it.resolution.lines} · ${rangeLabel(it)}" }
                    ?: resolutionOf(item.width, item.height)?.lines ?: "–"
            },
            TableColumn<VideoItem>(audio, ColumnWidth.Fixed(150.dp), mono = true) { item ->
                tech[item.key]?.mainAudio()?.let { "${audioCodecLabel(it)} ${channelLabel(it)}" + if (it.atmos) " Atmos" else "" } ?: "–"
            },
            TableColumn<VideoItem>(duration, ColumnWidth.Fixed(100.dp), alignEnd = true, mono = true) { formatDuration(it.durationMs) },
        )
    }
}

private fun sortVideos(list: List<VideoItem>, sort: TableSort?, tech: Map<String, MediaTech>): List<VideoItem> {
    if (sort == null) return list
    val comparator: Comparator<VideoItem> = when (sort.column) {
        // by resolution, then by dynamic range: 4K Dolby Vision last
        1 -> compareBy<VideoItem> { tech[it.key]?.video?.resolution?.ordinal ?: resolutionOf(it.width, it.height)?.ordinal ?: -1 }
            .thenBy { tech[it.key]?.video?.dynamicRange?.ordinal ?: -1 }
        2 -> compareBy(String.CASE_INSENSITIVE_ORDER) { item -> tech[item.key]?.mainAudio()?.let(::audioShortLabel).orEmpty() }
        3 -> compareBy { it.durationMs }
        else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    }
    return if (sort.ascending) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
}

/** Saves a table sort as one Int: column * 2 + 1 when ascending, -1 for no sort. */
private val TableSortSaver = Saver<TableSort?, Int>(
    save = { sort -> sort?.let { it.column * 2 + if (it.ascending) 1 else 0 } ?: -1 },
    restore = { saved -> if (saved < 0) null else TableSort(saved / 2, saved % 2 == 1) },
)

/**
 * A video card (S-802): artwork with the technical badge ("4K DV") and the watched part, the
 * title, and the technical summary ("2160p · DV P8 · TrueHD") or the duration until the probe
 * has run.
 */
@Composable
fun VideoCard(
    item: VideoItem,
    tech: MediaTech?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusCard)
    val badge = techBadge(tech, item.width, item.height)
    val summary = tech?.let(::techSummary)?.takeIf { it.isNotEmpty() }
    Column(
        modifier = modifier
            .focusRing(state, shape)
            .background(if (state.hovered || state.pressed || selected) stateBackground(state, selected) else colors.background, shape)
            .then(if (selected) Modifier.border(2.dp, colors.accent, shape) else Modifier)
            .semantics { this.selected = selected }
            .nagClickable(interaction, onClick, onClickLabel = item.title, onDoubleClick = onDoubleClick)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(dimens.radiusControl))) {
            Artwork(item.media, size = 320.dp, modifier = Modifier.fillMaxSize(), corner = 0.dp)
            if (item.progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(colors.scrim)) {
                    Box(Modifier.fillMaxWidth(item.progress).fillMaxHeight().background(colors.accent))
                }
            }
            if (badge != null) TechBadge(badge, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = NagTheme.type.bodyStrong, color = if (selected) colors.onSelection else colors.text, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Text(
            summary ?: formatDuration(item.durationMs),
            style = NagTheme.type.mono,
            color = if (selected) colors.onSelection else colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
fun MusicScreen(repository: MediaRepository, onAddFolder: () -> Unit) {
    val context = LocalContext.current
    val scan = rememberScanState()
    val access = LocalLibraryAccess.current
    val tracks by produceState<List<MediaWrapper>?>(null, scan.active, access?.media, access?.folders) {
        loadWhileScanning(scan.active) { repository.tracks() }
    }
    var sort by rememberSaveable(stateSaver = TableSortSaver) { mutableStateOf<TableSort?>(null) }
    val list = tracks
    val unknown = stringResource(R.string.nag_unknown_artist)
    Column(Modifier.fillMaxSize()) {
        PageHeader(title = stringResource(R.string.nag_nav_music), subtitle = list?.let { stringResource(R.string.nag_tracks_count, it.size) })
        LibraryContent(
            loading = list == null,
            empty = list?.isEmpty() == true,
            scan = scan,
            emptyTitle = stringResource(R.string.nag_state_no_music_title),
            emptyDescription = stringResource(R.string.nag_state_no_music_desc),
            onAddFolder = onAddFolder,
        ) {
            val sorted = remember(list, sort) { sortTracks(list.orEmpty(), sort) }
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
