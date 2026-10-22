package app.nagmedar.ui.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.IconMenuButton
import app.nagmedar.ui.components.LocalClickModifiers
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MultiSelection
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SelectionBar
import app.nagmedar.ui.components.SelectionCommands
import app.nagmedar.ui.components.SelectionMark
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.rememberMultiSelection
import app.nagmedar.ui.components.rememberRubberBand
import app.nagmedar.ui.components.rubberBandOverlay
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.LocalNavKind
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import java.text.DateFormat
import java.util.Date

/**
 * The Videos page: the videos that are neither films nor episodes, those of the camera, screen
 * recordings and messengers, or exactly the folders chosen for it (Settings › Library). Cards
 * with the duration and the resolution, the date taken and the size; the ones in progress first.
 * As in the films' grid, a pointer's click selects and a double click plays, a tap plays; Ctrl,
 * Shift, Ctrl+A and a rubber band select several, a drag takes them out. Nothing here deletes.
 */
@Composable
fun VideosScreen(repository: MediaRepository, onAddFolder: () -> Unit) {
    val context = LocalContext.current
    val scan = rememberScanState()
    val access = LocalLibraryAccess.current
    val clips by produceState<List<VideoClip>?>(null, scan.active, access?.media, access?.folders, access?.scopes) {
        loadWhileScanning(scan.active) { repository.clips() }
    }
    val pointer = NagTheme.inputMode == InputMode.Pointer
    var order by rememberSaveable { mutableStateOf(ClipOrder.Date) }
    val selection = rememberMultiSelection()
    val selected = selection.keys.singleOrNull() as? String
    var detailsRequested by rememberSaveable { mutableStateOf(false) }
    val play: (VideoItem) -> Unit = { item -> item.media?.let { PlaybackActions.playVideo(context, it) } }
    BackHandler(enabled = selection.selecting) { selection.clear() }
    val shown = remember(clips, order) { clips.orEmpty().ordered(order) }
    val selectedItem = shown.firstOrNull { it.item.key == selected }?.item
    // only the clip whose details are asked for is probed, not the whole camera roll
    val selectedTech = rememberTechOf(selectedItem?.key)
    val tech = remember(selectedItem, selectedTech) {
        if (selectedItem != null && selectedTech != null) mapOf(selectedItem.key to selectedTech) else emptyMap()
    }
    VideoDetailsPanes(
        selected = selectedItem,
        detailsRequested = detailsRequested,
        tech = tech,
        onPlay = play,
        onCloseDetails = {
            selection.clear()
            detailsRequested = false
        },
    ) {
        Column(Modifier.fillMaxSize().testTag("videos")) {
            VideosHeader(clips, order, { order = it }, onAddFolder)
            LibraryContent(
                loading = clips == null,
                empty = clips?.isEmpty() == true,
                scan = scan,
                emptyTitle = stringResource(R.string.nag_state_no_videos_title),
                emptyDescription = stringResource(R.string.nag_lib_no_clips_desc),
                onAddFolder = onAddFolder,
            ) {
                ClipsContent(
                    clips = shown,
                    selection = selection,
                    onPlay = play,
                    onShowDetails = { item ->
                        selection.click(item.key, listOf(item.key))
                        detailsRequested = true
                    },
                    menu = { item, playItem, info ->
                        // by touch the menu starts selecting (S-310)
                        val select = if (pointer) null else ({ selection.startSelecting(item.key) })
                        item.media?.let { mediaMenu(context, it, playItem, info, select) }.orEmpty()
                    },
                    onPlayAll = { list -> PlaybackActions.playList(context, list.mapNotNull { it.media }, 0) },
                    onQueueAll = { list -> PlaybackActions.appendList(context, list.mapNotNull { it.media }) },
                )
            }
        }
    }
}

@Composable
private fun VideosHeader(clips: List<VideoClip>?, order: ClipOrder, onOrderChange: (ClipOrder) -> Unit, onAddFolder: () -> Unit) {
    val labels = mapOf(
        ClipOrder.Date to stringResource(R.string.nag_order_date),
        ClipOrder.Name to stringResource(R.string.nag_order_title),
        ClipOrder.Size to stringResource(R.string.nag_order_size),
        ClipOrder.Duration to stringResource(R.string.nag_order_duration),
    )
    val sortLabel = stringResource(R.string.nag_sort_by, labels.getValue(order))
    val sortEntries = { ClipOrder.entries.map { MenuEntry.Item(labels.getValue(it), { onOrderChange(it) }, checked = it == order) } }
    val compact = LocalNavKind.current == NavKind.BottomBar
    PageHeader(
        title = stringResource(R.string.nag_nav_videos),
        subtitle = clips?.let { pluralStringResource(R.plurals.nag_lib_videos_count, it.size, it.size) },
    ) {
        if (!clips.isNullOrEmpty()) {
            if (compact) {
                NagIconButton(NagIcons.FolderAdd, stringResource(R.string.nag_lib_sources), onAddFolder)
                IconMenuButton(NagIcons.Sort, sortLabel, sortEntries)
            } else {
                NagButton(stringResource(R.string.nag_lib_sources), onAddFolder, icon = NagIcons.FolderAdd)
                Spacer(Modifier.width(NagTheme.dimens.gapXs))
                MenuButton(sortLabel, entries = sortEntries)
            }
        }
    }
}

@Composable
private fun ClipsContent(
    clips: List<VideoClip>,
    selection: MultiSelection,
    onPlay: (VideoItem) -> Unit,
    onShowDetails: (VideoItem) -> Unit,
    menu: (VideoItem, play: () -> Unit, info: () -> Unit) -> List<MenuEntry>,
    onPlayAll: (List<VideoItem>) -> Unit,
    onQueueAll: (List<VideoItem>) -> Unit,
) {
    val dimens = NagTheme.dimens
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val modifiers = LocalClickModifiers.current
    val items = remember(clips) { clips.map { it.item } }
    val keys = remember(items) { items.map { it.key } }
    // a scan takes away what is no longer listed
    LaunchedEffect(keys) { selection.retain(keys) }
    SelectionCommands(selection, keys)
    val selecting = selection.selecting
    val click: (VideoItem) -> Unit = { item ->
        when {
            selecting -> selection.toggle(item.key)
            pointer -> {
                val (ctrl, shift) = modifiers.take()
                selection.click(item.key, keys, ctrl, shift)
            }
            else -> onPlay(item)
        }
    }
    val going = remember(clips) { clips.filter { it.item.progress > 0f } }
    val band = rememberRubberBand()
    Column(Modifier.fillMaxSize()) {
        if (selection.count > 1 || selecting) {
            val chosen = items.filter { it.key in selection }
            SelectionBar(
                text = pluralStringResource(R.plurals.nag_lib_selected_videos, chosen.size, chosen.size),
                onClear = selection::clear,
                clearLabel = stringResource(R.string.nag_selection_clear),
                modifier = Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs),
            ) {
                NagButton(stringResource(R.string.nag_selection_play), { onPlayAll(chosen) }, primary = true)
                NagButton(stringResource(R.string.nag_add_to_queue), { onQueueAll(chosen) })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (pointer) 180.dp else 160.dp),
            contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapS),
            horizontalArrangement = Arrangement.spacedBy(dimens.gapM),
            verticalArrangement = Arrangement.spacedBy(dimens.gapM),
            modifier = Modifier
                .fillMaxSize()
                .then(if (pointer) band.area(selection).rubberBandOverlay(band) else Modifier),
        ) {
            if (going.isNotEmpty()) {
                item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                    Box(band.keepOut("continue")) { ClipShelf(going, onPlay) }
                }
            }
            items(clips, key = { it.item.key }) { video ->
                val item = video.item
                ContextMenuArea(
                    entries = { menu(item, { onPlay(item) }, { onShowDetails(item) }) },
                    accessibilityLabel = stringResource(R.string.nag_more_actions),
                ) {
                    ClipCard(
                        video,
                        Modifier
                            .fillMaxWidth()
                            .then(band.item(item.key))
                            .filmDrags(item, items, selection, RoundedCornerShape(dimens.radiusCard), R.plurals.nag_lib_drag_videos),
                        selected = item.key in selection,
                        selecting = selecting,
                        onClick = { click(item) },
                        onDoubleClick = if (pointer && !selecting) ({ onPlay(item) }) else null,
                    )
                }
            }
        }
    }
}

/** "İzlemeye devam et": the videos in progress, in a row above the grid. */
@Composable
private fun ClipShelf(clips: List<VideoClip>, onPlay: (VideoItem) -> Unit) {
    val dimens = NagTheme.dimens
    Column(Modifier.fillMaxWidth().padding(bottom = dimens.gapS)) {
        Text(
            stringResource(R.string.nag_continue_watching),
            style = NagTheme.type.title,
            color = NagTheme.colors.text,
            modifier = Modifier.padding(bottom = dimens.gapS).semantics { heading() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
            items(clips, key = { "continue-" + it.item.key }) { video ->
                ClipCard(video, Modifier.width(200.dp), selected = false, selecting = false, onClick = { onPlay(video.item) }, onDoubleClick = null)
            }
        }
    }
}

/** A video's card: its picture with the resolution and the duration on it, the part watched, the title, the date taken and the size. */
@Composable
private fun ClipCard(
    video: VideoClip,
    modifier: Modifier,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)?,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val locale = LocalContext.current.resources.locale()
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusCard)
    val item = video.item
    val resolution = techBadge(null, item.width, item.height)
    val details = remember(video, locale) {
        listOfNotNull(
            video.takenAt.takeIf { it > 0 }?.let { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(it)) },
            video.sizeBytes.takeIf { it > 0 }?.let { sizeLabel(it, locale) },
        ).joinToString(" · ")
    }
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
            Artwork(item.media, size = 240.dp, modifier = Modifier.fillMaxSize(), corner = 0.dp, video = true)
            if (item.progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(colors.scrim)) {
                    Box(Modifier.fillMaxWidth(item.progress).fillMaxHeight().background(colors.accent))
                }
            }
            if (resolution != null) TechBadge(resolution, Modifier.align(Alignment.TopStart).padding(6.dp))
            if (item.durationMs > 0) TechBadge(formatDuration(item.durationMs), Modifier.align(Alignment.BottomEnd).padding(6.dp))
            if (selecting) SelectionMark(selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = NagTheme.type.bodyStrong, color = if (selected) colors.onSelection else colors.text, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        if (details.isNotEmpty()) {
            Text(details, style = NagTheme.type.labelSmall, color = if (selected) colors.onSelection else colors.textTertiary, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
