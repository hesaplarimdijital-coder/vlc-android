package app.nagmedar.ui.library

import androidx.activity.compose.BackHandler
import android.net.Uri
import android.widget.Toast
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.NativeLab
import app.nagmedar.ui.R
import app.nagmedar.ui.cast.CastPayload
import app.nagmedar.ui.cast.Casting
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.LocalClickModifiers
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MultiSelection
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SelectionBar
import app.nagmedar.ui.components.SelectionCommands
import app.nagmedar.ui.components.SelectionMark
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.components.TableSort
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.rememberMultiSelection
import app.nagmedar.ui.components.rememberRubberBand
import app.nagmedar.ui.components.rubberBandOverlay
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.input.DropKind
import app.nagmedar.ui.input.dropTarget
import app.nagmedar.ui.input.fileDragSource
import app.nagmedar.ui.input.mayBeSubtitle
import app.nagmedar.ui.input.onMiddleClick
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.ListDetailPanes
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.player.subtitles.tools.SubtitleToolsLauncher
import app.nagmedar.ui.player.subtitles.whisper.WhisperLauncher
import app.nagmedar.ui.tvlab.openInTvLab
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.TAG_ITEM
import org.videolan.vlc.gui.InfoActivity
import org.videolan.vlc.media.MediaUtils
import kotlinx.coroutines.delay
import org.videolan.vlc.repository.SlaveRepository
import app.nagmedar.ui.transcode.TranscodeMedia
import app.nagmedar.ui.transcode.TranscodeRequests

/**
 * Context menu of a media item; the same entries serve right click, long press and the Menu key.
 * "Play on …" opens the device picker for it (S-1302).
 * [info] shows the details in this interface (videos, S-802); without it the classic media info opens.
 * [select] starts selecting by touch (S-310): the menu's "Seç".
 */
fun mediaMenu(context: Context, media: MediaWrapper, play: () -> Unit, info: (() -> Unit)? = null, select: (() -> Unit)? = null): List<MenuEntry> {
    val res = context.resources
    return listOfNotNull(
        MenuEntry.Item(res.getString(R.string.nag_play), play, shortcut = "Enter", icon = NagIcons.Play),
        MenuEntry.Item(res.getString(R.string.nag_play_next), { MediaUtils.insertNext(context, media) }, icon = NagIcons.Next),
        MenuEntry.Item(res.getString(R.string.nag_add_to_queue), { MediaUtils.appendMedia(context, media) }, icon = NagIcons.PlaylistAdd),
        // S-1302: on a Chromecast or a DLNA renderer, chosen in the device picker; a tracker module plays here alone
        if (Casting.enabled(context) && Casting.offered(media.uri)) {
            MenuEntry.Item(res.getString(R.string.nag_cast_play_on), { Casting.openPicker(CastPayload(listOf(media), 0)) }, icon = NagIcons.Cast)
        } else {
            null
        },
        select?.let { MenuEntry.Item(res.getString(R.string.nag_select), it, icon = NagIcons.Check) },
        MenuEntry.Separator,
        MenuEntry.Item(
            res.getString(R.string.nag_sc_media_info),
            info ?: { context.startActivity(Intent(context, InfoActivity::class.java).putExtra(TAG_ITEM, media)) },
            shortcut = ShortcutRegistry.chordLabel(ShortcutId.MediaInfo, res),
            icon = NagIcons.Info,
        ),
        // S-1201: the film checked against the target device
        if (media.type == MediaWrapper.TYPE_VIDEO) MenuEntry.Item(res.getString(R.string.nag_tvlab_analyse), { openInTvLab(context, media.uri) }, icon = NagIcons.TvLab) else null,
        // S-708: the film's subtitle files cleaned, converted and edited into new files
        if (media.type == MediaWrapper.TYPE_VIDEO) MenuEntry.Item(res.getString(R.string.nag_subtools_menu), { SubtitleToolsLauncher.open(media.uri, media.fileName) }, icon = NagIcons.Subtitle) else null,
        // S-709: subtitles from the film's speech, made on the device
        if (media.type == MediaWrapper.TYPE_VIDEO) MenuEntry.Item(res.getString(R.string.nag_whisper_menu), { WhisperLauncher.open(media.uri, media.fileName) }, icon = NagIcons.Subtitle) else null,
        // S-1303: into another format, or on the network
        MenuEntry.Item(
            res.getString(R.string.nag_conv_menu),
            { TranscodeRequests.open(context, TranscodeRequests.Kind.Convert, TranscodeMedia(media.uri, media.fileName ?: media.title.orEmpty())) },
            shortcut = ShortcutRegistry.chordLabel(ShortcutId.ConvertSave, res),
            icon = NagIcons.Convert,
        ),
        MenuEntry.Item(
            res.getString(R.string.nag_hls_start_menu),
            { TranscodeRequests.open(context, TranscodeRequests.Kind.Stream, TranscodeMedia(media.uri, media.title ?: media.fileName.orEmpty())) },
            shortcut = ShortcutRegistry.chordLabel(ShortcutId.StreamHls, res),
            icon = NagIcons.Streams,
        ),
    )
}

// ---------------------------------------------------------------- Home

/** While the medialibrary scans, the screens reload this often so new media appear. */
private const val RELOAD_WHILE_SCANNING_MS = 4_000L

/** Loads with [load] now and, while a scan runs, again every few seconds. */
internal suspend fun <T> ProduceStateScope<T?>.loadWhileScanning(scanning: Boolean, load: suspend () -> T) {
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
                        item { ShelfTitle(stringResource(R.string.nag_continue_watching)) }
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

/** Probed, still probing, or never: the native probe is missing from this build. */
fun techStateOf(item: VideoItem, tech: Map<String, MediaTech>): TechState = when {
    tech.containsKey(item.key) -> TechState.Ready
    NativeLab.available -> TechState.Probing
    else -> TechState.Unavailable
}

/**
 * A video list with the details of the selected video (S-802): beside the list in expanded
 * windows and wider, over it in narrower ones (S-303). Where there is room a pointer's selection
 * shows them at once; otherwise they open when asked for ([detailsRequested], the item's menu).
 */
@Composable
fun VideoDetailsPanes(
    selected: VideoItem?,
    detailsRequested: Boolean,
    tech: Map<String, MediaTech>,
    onPlay: (VideoItem) -> Unit,
    onCloseDetails: () -> Unit,
    modifier: Modifier = Modifier,
    techState: (VideoItem) -> TechState = { techStateOf(it, tech) },
    list: @Composable () -> Unit,
) {
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val side = currentWidthClass() >= WidthClass.Expanded
    ListDetailPanes(
        detail = selected?.takeIf { detailsRequested || (pointer && side) },
        onCloseDetail = onCloseDetails,
        list = list,
        detailPane = { item, fullScreen ->
            MediaDetailsPanel(
                item = item,
                tech = tech[item.key],
                state = techState(item),
                onPlay = { onPlay(item) },
                onClose = onCloseDetails,
                modifier = Modifier.fillMaxSize(),
                fullScreen = fullScreen,
            )
        },
        modifier = modifier,
    )
}

/**
 * The movies as a grid of cards or a dense table (S-802). With a pointer a click selects and a
 * double click plays; by touch a tap plays; the menu's "Medya bilgisi" asks for the details.
 * With a [selection] (S-405), Ctrl+click, Shift+click, Ctrl+A and a rubber band over the grid
 * select several.
 */
@Composable
fun MoviesContent(
    items: List<VideoItem>,
    tech: Map<String, MediaTech>,
    grid: Boolean,
    sort: TableSort?,
    onSortChange: (TableSort) -> Unit,
    selected: String?,
    onSelect: (VideoItem) -> Unit,
    onShowDetails: (VideoItem) -> Unit,
    onPlay: (VideoItem) -> Unit,
    menu: (VideoItem, play: () -> Unit, info: () -> Unit) -> List<MenuEntry>,
    /** Scrolls with the grid above the cards (the "İzlemeye devam et" shelf); the table has none. */
    gridHeader: (@Composable () -> Unit)? = null,
    selection: MultiSelection? = null,
) {
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val modifiers = LocalClickModifiers.current
    // selecting by touch (S-310): a tap takes the film in or out
    val selecting = selection?.selecting == true
    val click: (VideoItem, List<VideoItem>) -> Unit = { item, shown ->
        when {
            selecting -> selection?.toggle(item.key)
            pointer -> {
                val (ctrl, shift) = modifiers.take()
                selection?.click(item.key, shown.map { it.key }, ctrl, shift)
                onSelect(item)
            }
            else -> onPlay(item)
        }
    }
    val doubleClick: ((VideoItem) -> Unit)? = if (pointer && !selecting) onPlay else null
    val isSelected: (VideoItem) -> Boolean = { item -> selection?.contains(item.key) ?: (item.key == selected) }
    if (grid) {
        VideoGrid(items, tech, isSelected, click, doubleClick, menu, onPlay, onShowDetails, gridHeader, selection)
    } else {
        VideoTable(items, tech, sort, onSortChange, selected, click, doubleClick, menu, onPlay, onShowDetails, selection)
    }
}

@Composable
private fun VideoGrid(
    items: List<VideoItem>,
    tech: Map<String, MediaTech>,
    isSelected: (VideoItem) -> Boolean,
    onClick: (VideoItem, List<VideoItem>) -> Unit,
    onDoubleClick: ((VideoItem) -> Unit)?,
    menu: (VideoItem, () -> Unit, () -> Unit) -> List<MenuEntry>,
    onPlay: (VideoItem) -> Unit,
    onShowDetails: (VideoItem) -> Unit,
    header: (@Composable () -> Unit)?,
    selection: MultiSelection?,
) {
    val dimens = NagTheme.dimens
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val band = rememberRubberBand()
    if (selection != null) SelectionCommands(selection, items.map { it.key })
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (pointer) 190.dp else 220.dp),
        contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapL),
        verticalArrangement = Arrangement.spacedBy(dimens.gapL),
        modifier = Modifier
            .fillMaxSize()
            .then(if (selection != null && pointer) band.area(selection).rubberBandOverlay(band) else Modifier),
    ) {
        if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { Box(band.keepOut("header")) { header() } }
        itemsIndexed(items, key = { _, item -> item.key }) { _, item ->
            ContextMenuArea(
                entries = { menu(item, { onPlay(item) }, { onShowDetails(item) }) },
                accessibilityLabel = stringResource(R.string.nag_more_actions),
            ) {
                VideoCard(
                    item,
                    tech[item.key],
                    Modifier
                        .fillMaxWidth()
                        .then(band.item(item.key))
                        .filmDrags(item, items, selection, RoundedCornerShape(NagTheme.dimens.radiusCard)),
                    selected = isSelected(item),
                    onClick = { onClick(item, items) },
                    onDoubleClick = onDoubleClick?.let { open -> { open(item) } },
                    selecting = selection?.selecting == true,
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
    onClick: (VideoItem, List<VideoItem>) -> Unit,
    onDoubleClick: ((VideoItem) -> Unit)?,
    menu: (VideoItem, () -> Unit, () -> Unit) -> List<MenuEntry>,
    onPlay: (VideoItem) -> Unit,
    onShowDetails: (VideoItem) -> Unit,
    selection: MultiSelection?,
) {
    val sorted = remember(items, sort, tech) { sortVideos(items, sort, tech) }
    val rowShape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    if (selection != null) SelectionCommands(selection, sorted.map { it.key })
    DenseTable(
        columns = videoColumns(tech),
        rows = sorted,
        key = { it.key },
        selectedKey = selected,
        selectedKeys = selection?.keys,
        sort = sort,
        onSortChange = onSortChange,
        onRowClick = { item, _ -> onClick(item, sorted) },
        onRowDoubleClick = onDoubleClick?.let { open -> { item, _ -> open(item) } },
        rowMenu = { item, _ -> menu(item, { onPlay(item) }, { onShowDetails(item) }) },
        rowMenuLabel = stringResource(R.string.nag_more_actions),
        rowModifier = { item -> Modifier.filmDrags(item, sorted, selection, rowShape) },
    )
}

/**
 * A film dragged out with the mouse (S-406), with the others selected when it is one of them, and a
 * subtitle dropped on it kept with it: the player loads it next time the film plays.
 */
private fun Modifier.filmDrags(item: VideoItem, shown: List<VideoItem>, selection: MultiSelection?, shape: Shape): Modifier = composed {
    val context = LocalContext.current
    val res = context.resources
    this
        .fileDragSource(
            label = {
                val many = dragged(item, shown, selection)
                if (many.size > 1) res.getQuantityString(R.plurals.nag_drag_films, many.size, many.size) else item.title
            },
            files = { dragged(item, shown, selection).map { Uri.parse(it.key) } },
        )
        // the middle button queues the film (S-408)
        .onMiddleClick {
            item.media?.let { media ->
                PlaybackActions.appendList(context, listOf(media))
                Toast.makeText(context, res.getQuantityString(R.plurals.nag_drop_queued, 1, 1), Toast.LENGTH_SHORT).show()
            }
        }
        .dropTarget("film-${item.key}", ::mayBeSubtitle, shape) { files ->
            val subtitles = files.filter { it.kind == DropKind.Subtitle }
            subtitles.forEach { file ->
                SlaveRepository.getInstance(context).saveSlave(item.media?.location ?: item.key, IMedia.Slave.Type.Subtitle, 2, file.uri)
            }
            if (subtitles.isNotEmpty()) Toast.makeText(context, res.getString(R.string.nag_drop_subtitle_for, item.title), Toast.LENGTH_SHORT).show()
            subtitles.isNotEmpty()
        }
}

/** What a drag from [item] carries: the selection when the item is in it, else the item. */
private fun dragged(item: VideoItem, shown: List<VideoItem>, selection: MultiSelection?): List<VideoItem> =
    if (selection != null && item.key in selection && selection.count > 1) shown.filter { it.key in selection } else listOf(item)

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
internal val TableSortSaver = Saver<TableSort?, Int>(
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
    /** Selecting by touch (S-310): the card shows whether it is in. */
    selecting: Boolean = false,
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
            Artwork(item.media, size = 320.dp, modifier = Modifier.fillMaxSize(), corner = 0.dp, video = true)
            if (item.progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(colors.scrim)) {
                    Box(Modifier.fillMaxWidth(item.progress).fillMaxHeight().background(colors.accent))
                }
            }
            if (badge != null) TechBadge(badge, Modifier.align(Alignment.TopStart).padding(6.dp))
            if (selecting) SelectionMark(selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
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
