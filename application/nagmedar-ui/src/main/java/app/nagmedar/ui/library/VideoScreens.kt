package app.nagmedar.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.nagmedar.lab.MediaTech
import app.nagmedar.ui.R
import app.nagmedar.ui.components.ChipRow
import app.nagmedar.ui.components.FilterChip
import app.nagmedar.ui.components.IconMenuButton
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.TableSort
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.LocalNavKind
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme

/** How the films are shown: cards, the dense table, or their folders. */
enum class MoviesView { Grid, Table, Folders }

// ---------------------------------------------------------------- Films (S-801)

@Composable
fun MoviesScreen(repository: MediaRepository, onAddFolder: () -> Unit) {
    val context = LocalContext.current
    val scan = rememberScanState()
    val access = LocalLibraryAccess.current
    val items by produceState<List<VideoItem>?>(null, scan.active, access?.media, access?.folders) {
        loadWhileScanning(scan.active) { repository.videoItems() }
    }
    val library = remember(items) { items?.let(::buildVideoLibrary) }
    val tech by rememberTech(items)
    val pointer = NagTheme.inputMode == InputMode.Pointer
    var view by rememberSaveable { mutableStateOf(if (pointer) MoviesView.Table else MoviesView.Grid) }
    var filters by rememberSaveable { mutableStateOf(emptySet<MovieFilter>()) }
    var order by rememberSaveable { mutableStateOf(MovieOrder.Added) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable(stateSaver = TableSortSaver) { mutableStateOf<TableSort?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsRequested by rememberSaveable { mutableStateOf(false) }
    val play: (VideoItem) -> Unit = { item -> item.media?.let { PlaybackActions.playVideo(context, it) } }
    BackHandler(enabled = folder != null) { folder = null }
    // the details stand beside the header and the list, the full height of the screen (S-303)
    VideoDetailsPanes(
        selected = library?.movies?.firstOrNull { it.key == selected },
        detailsRequested = detailsRequested,
        tech = tech,
        onPlay = play,
        onCloseDetails = {
            selected = null
            detailsRequested = false
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            MoviesHeader(library, view, { view = it }, order, { order = it }, onAddFolder)
            LibraryContent(
                loading = library == null,
                empty = library?.movies?.isEmpty() == true,
                scan = scan,
                emptyTitle = stringResource(R.string.nag_state_no_videos_title),
                emptyDescription = stringResource(R.string.nag_state_no_videos_desc),
                onAddFolder = onAddFolder,
            ) {
                MoviesLibrary(
                    library = library ?: VideoLibrary.Empty,
                    tech = tech,
                    view = view,
                    filters = filters,
                    onFiltersChange = { filters = it },
                    order = order,
                    folder = folder,
                    onOpenFolder = { folder = it },
                    sort = sort,
                    onSortChange = { sort = it },
                    selected = selected,
                    onSelect = { selected = it.key },
                    onShowDetails = {
                        selected = it.key
                        detailsRequested = true
                    },
                    onPlay = play,
                    menu = { item, playItem, info -> item.media?.let { mediaMenu(context, it, playItem, info) }.orEmpty() },
                )
            }
        }
    }
}

@Composable
internal fun MoviesHeader(
    library: VideoLibrary?,
    view: MoviesView,
    onViewChange: (MoviesView) -> Unit,
    order: MovieOrder,
    onOrderChange: (MovieOrder) -> Unit,
    onAddFolder: () -> Unit,
) {
    val subtitle = library?.let {
        pluralStringResource(R.plurals.nag_films_count, it.movies.size, it.movies.size) + " · " +
            pluralStringResource(R.plurals.nag_folders_count, it.folders.size, it.folders.size)
    }
    val orderLabels = orderLabels()
    val sortLabel = stringResource(R.string.nag_sort_by, orderLabels.getValue(order))
    val sortEntries = { MovieOrder.entries.map { MenuEntry.Item(orderLabels.getValue(it), { onOrderChange(it) }, checked = it == order) } }
    // a phone has room for icons only
    val compact = LocalNavKind.current == NavKind.BottomBar
    PageHeader(title = stringResource(R.string.nag_nav_movies), subtitle = subtitle) {
        if (!library?.movies.isNullOrEmpty()) {
            if (compact) {
                NagIconButton(NagIcons.Plus, stringResource(R.string.nag_state_add_folder), onAddFolder)
            } else {
                NagButton(stringResource(R.string.nag_state_add_folder), onAddFolder, icon = NagIcons.Plus)
                Spacer(Modifier.width(NagTheme.dimens.gapXs))
            }
            NagIconButton(NagIcons.Grid, stringResource(R.string.nag_view_grid), { onViewChange(MoviesView.Grid) }, selected = view == MoviesView.Grid)
            NagIconButton(NagIcons.ListView, stringResource(R.string.nag_view_list), { onViewChange(MoviesView.Table) }, selected = view == MoviesView.Table)
            NagIconButton(NagIcons.Folder, stringResource(R.string.nag_view_folders), { onViewChange(MoviesView.Folders) }, selected = view == MoviesView.Folders)
            if (compact) {
                IconMenuButton(NagIcons.Sort, sortLabel, sortEntries)
            } else {
                Spacer(Modifier.width(NagTheme.dimens.gapXs))
                MenuButton(sortLabel, entries = sortEntries)
            }
        }
    }
}

@Composable
private fun orderLabels(): Map<MovieOrder, String> = mapOf(
    MovieOrder.Added to stringResource(R.string.nag_order_added),
    MovieOrder.Title to stringResource(R.string.nag_order_title),
    MovieOrder.Duration to stringResource(R.string.nag_order_duration),
    MovieOrder.Resolution to stringResource(R.string.nag_order_resolution),
)

@Composable
private fun filterLabel(filter: MovieFilter): String = stringResource(
    when (filter) {
        MovieFilter.UHD -> R.string.nag_filter_uhd
        MovieFilter.HDR -> R.string.nag_filter_hdr
        MovieFilter.DolbyVision -> R.string.nag_filter_dv
        MovieFilter.Atmos -> R.string.nag_filter_atmos
        MovieFilter.TurkishSubtitles -> R.string.nag_filter_turkish_subtitles
        MovieFilter.Unwatched -> R.string.nag_filter_unwatched
    },
)

/**
 * The films below the header (S-801): the filter chips ("Tümü", "4K", "HDR", "Dolby Vision",
 * "Atmos", "Türkçe altyazılı", "İzlenmedi"), then the folders, or the films of every folder or of
 * one, as cards with the "Devam et" shelf on top, or as the dense table.
 */
@Composable
fun MoviesLibrary(
    library: VideoLibrary,
    tech: Map<String, MediaTech>,
    view: MoviesView,
    filters: Set<MovieFilter>,
    onFiltersChange: (Set<MovieFilter>) -> Unit,
    order: MovieOrder,
    folder: String?,
    onOpenFolder: (String?) -> Unit,
    sort: TableSort?,
    onSortChange: (TableSort) -> Unit,
    selected: String?,
    onSelect: (VideoItem) -> Unit,
    onShowDetails: (VideoItem) -> Unit,
    onPlay: (VideoItem) -> Unit,
    menu: (VideoItem, play: () -> Unit, info: () -> Unit) -> List<MenuEntry>,
) {
    val dimens = NagTheme.dimens
    val openFolder = library.folders.firstOrNull { it.path == folder }
    Column(Modifier.fillMaxSize()) {
        ChipRow(contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapXs)) {
            FilterChip(stringResource(R.string.nag_filter_all), filters.isEmpty(), { onFiltersChange(emptySet()) })
            MovieFilter.entries.forEach { filter ->
                FilterChip(filterLabel(filter), filter in filters, { on -> onFiltersChange(if (on) filters + filter else filters - filter) })
            }
        }
        if (openFolder != null) {
            FolderTrail(openFolder) { onOpenFolder(null) }
        }
        val source = openFolder?.items ?: library.movies
        val shown = remember(source, filters, order, tech) { source.filtered(filters, tech).ordered(order, tech) }
        val shelf = if (filters.isEmpty() && openFolder == null) library.continueMovies else emptyList()
        val shelfHeader: @Composable () -> Unit = { ContinueShelf(shelf, tech, onPlay) }
        when {
            view == MoviesView.Folders && openFolder == null -> FolderGrid(library.folders, tech, filters, onOpenFolder)
            shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NoResultsState(
                    title = stringResource(R.string.nag_no_matching_films),
                    suggestions = listOf(StateAction(stringResource(R.string.nag_clear_filters)) { onFiltersChange(emptySet()) }),
                )
            }
            else -> MoviesContent(
                items = shown,
                tech = tech,
                grid = view != MoviesView.Table,
                sort = sort,
                onSortChange = onSortChange,
                selected = selected,
                onSelect = onSelect,
                onShowDetails = onShowDetails,
                onPlay = onPlay,
                menu = menu,
                gridHeader = shelfHeader.takeIf { shelf.isNotEmpty() },
            )
        }
    }
}

/** "Devam et": the films in progress, in a row that scrolls with the grid. */
@Composable
fun ContinueShelf(items: List<VideoItem>, tech: Map<String, MediaTech>, onPlay: (VideoItem) -> Unit) {
    val dimens = NagTheme.dimens
    Column(Modifier.fillMaxWidth().testTag("continue")) {
        Text(
            stringResource(R.string.nag_video_continue),
            style = NagTheme.type.title,
            color = NagTheme.colors.text,
            modifier = Modifier.padding(bottom = dimens.gapS).semantics { heading() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
            items(items, key = { it.key }) { item ->
                VideoCard(item, tech[item.key], Modifier.width(240.dp), onClick = { onPlay(item) })
            }
        }
    }
}

@Composable
private fun FolderTrail(folder: VideoFolder, onBack: () -> Unit) {
    val dimens = NagTheme.dimens
    Row(
        Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding - 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_folders_back), onBack)
        Text(stringResource(R.string.nag_view_folders), style = NagTheme.type.body, color = NagTheme.colors.textSecondary)
        Text("  /  ", style = NagTheme.type.body, color = NagTheme.colors.textTertiary)
        Text(folder.name, style = NagTheme.type.bodyStrong, color = NagTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun FolderGrid(folders: List<VideoFolder>, tech: Map<String, MediaTech>, filters: Set<MovieFilter>, onOpen: (String) -> Unit) {
    val dimens = NagTheme.dimens
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (NagTheme.inputMode == InputMode.Pointer) 220.dp else 260.dp),
        contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapM),
        verticalArrangement = Arrangement.spacedBy(dimens.gapM),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(folders, key = { it.path }) { folder ->
            val count = remember(folder, filters, tech) { folder.items.filtered(filters, tech).size }
            FolderTile(folder, count) { onOpen(folder.path) }
        }
    }
}

@Composable
private fun FolderTile(folder: VideoFolder, count: Int, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusCard)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .focusRing(state, shape)
            .background(stateBackground(state, rest = colors.surface), shape)
            .border(1.dp, colors.border, shape)
            .nagClickable(interaction, onClick, onClickLabel = folder.name)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(colors.surfaceRaised, RoundedCornerShape(dimens.radiusControl)), contentAlignment = Alignment.Center) {
            Icon(NagIcons.Folder, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.nag_films_count, count, count), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        }
    }
}

// ---------------------------------------------------------------- Series (S-801)

@Composable
fun SeriesScreen(repository: MediaRepository, onAddFolder: () -> Unit) {
    val context = LocalContext.current
    val scan = rememberScanState()
    val access = LocalLibraryAccess.current
    val items by produceState<List<VideoItem>?>(null, scan.active, access?.media, access?.folders) {
        loadWhileScanning(scan.active) { repository.videoItems() }
    }
    val library = remember(items) { items?.let(::buildVideoLibrary) }
    val tech by rememberTech(items)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var season by rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler(enabled = open != null) { open = null }
    val shows = library?.shows
    Column(Modifier.fillMaxSize()) {
        if (open == null) {
            PageHeader(
                title = stringResource(R.string.nag_nav_series),
                subtitle = shows?.let {
                    pluralStringResource(R.plurals.nag_series_count, it.size, it.size) + " · " +
                        pluralStringResource(R.plurals.nag_episodes_count, it.sumOf { s -> s.episodes.size }, it.sumOf { s -> s.episodes.size })
                },
            )
        }
        LibraryContent(
            loading = library == null,
            empty = shows?.isEmpty() == true,
            scan = scan,
            emptyTitle = stringResource(R.string.nag_no_series_title),
            emptyDescription = stringResource(R.string.nag_no_series_desc),
            onAddFolder = onAddFolder,
        ) {
            SeriesContent(
                shows = shows.orEmpty(),
                tech = tech,
                open = open,
                onOpen = {
                    open = it
                    season = null
                },
                onClose = { open = null },
                season = season,
                onSeasonChange = { season = it },
                onPlay = { item -> item.media?.let { PlaybackActions.playVideo(context, it) } },
                menu = { item, play, info -> item.media?.let { mediaMenu(context, it, play, info) }.orEmpty() },
            )
        }
    }
}

/**
 * The shows as cards with what comes next, or one show's seasons and episodes (S-801) with the
 * details of the selected episode beside or over them (S-303).
 */
@Composable
fun SeriesContent(
    shows: List<Show>,
    tech: Map<String, MediaTech>,
    open: String?,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    season: Int?,
    onSeasonChange: (Int) -> Unit,
    onPlay: (VideoItem) -> Unit,
    menu: (VideoItem, play: () -> Unit, info: () -> Unit) -> List<MenuEntry>,
) {
    val show = shows.firstOrNull { it.key == open }
    if (show != null) {
        ShowDetail(show, tech, season ?: show.next?.info?.season ?: show.seasons.first().number, onSeasonChange, onClose, onPlay, menu)
    } else {
        ShowGrid(shows, onOpen)
    }
}

@Composable
private fun ShowGrid(shows: List<Show>, onOpen: (String) -> Unit) {
    val dimens = NagTheme.dimens
    val pointer = NagTheme.inputMode == InputMode.Pointer
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (pointer) 220.dp else 260.dp),
        contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapL),
        verticalArrangement = Arrangement.spacedBy(dimens.gapL),
        modifier = Modifier.fillMaxSize(),
    ) {
        val going = shows.filter { s -> s.episodes.any { it.item.progress > 0f } || (s.watched in 1 until s.episodes.size) }
        if (going.isNotEmpty()) {
            item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.nag_video_continue),
                    style = NagTheme.type.title,
                    color = NagTheme.colors.text,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(going, key = { "continue-" + it.key }) { s -> ShowCard(s) { onOpen(s.key) } }
            item(key = "all", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.nag_series_all),
                    style = NagTheme.type.title,
                    color = NagTheme.colors.text,
                    modifier = Modifier.padding(top = dimens.gapS).semantics { heading() },
                )
            }
        }
        items(shows, key = { it.key }) { s -> ShowCard(s) { onOpen(s.key) } }
    }
}

@Composable
private fun ShowCard(show: Show, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusCard)
    val next = show.next
    val cover = (next ?: show.episodes.first()).item
    Column(
        Modifier
            .fillMaxWidth()
            .focusRing(state, shape)
            .background(stateBackground(state, rest = colors.background), shape)
            .nagClickable(interaction, onClick, onClickLabel = show.title)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(dimens.radiusControl))) {
            Artwork(cover.media, size = 320.dp, modifier = Modifier.fillMaxSize(), corner = 0.dp, video = true)
            val fraction = show.watched.toFloat() / show.episodes.size
            if (fraction > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(colors.scrim)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(colors.accent))
                }
            }
            if (next != null) TechBadge(next.code, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(show.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            pluralStringResource(R.plurals.nag_seasons_count, show.seasons.size, show.seasons.size) + " · " +
                pluralStringResource(R.plurals.nag_episodes_count, show.episodes.size, show.episodes.size),
            style = NagTheme.type.labelSmall,
            color = colors.textTertiary,
            maxLines = 1,
        )
        Text(
            if (next != null) stringResource(R.string.nag_next_episode, next.code) else stringResource(R.string.nag_all_watched),
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShowDetail(
    show: Show,
    tech: Map<String, MediaTech>,
    season: Int,
    onSeasonChange: (Int) -> Unit,
    onClose: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    menu: (VideoItem, play: () -> Unit, info: () -> Unit) -> List<MenuEntry>,
) {
    val pointer = NagTheme.inputMode == InputMode.Pointer
    var selected by rememberSaveable(show.key) { mutableStateOf<String?>(null) }
    var requested by rememberSaveable(show.key) { mutableStateOf(false) }
    VideoDetailsPanes(
        selected = show.episodes.firstOrNull { it.item.key == selected }?.item,
        detailsRequested = requested,
        tech = tech,
        onPlay = onPlay,
        onCloseDetails = {
            selected = null
            requested = false
        },
        list = {
            ShowEpisodes(
                show = show,
                tech = tech,
                season = season,
                onSeasonChange = onSeasonChange,
                onClose = onClose,
                selected = selected,
                onClick = { item ->
                    if (pointer) {
                        selected = item.key
                    } else {
                        onPlay(item)
                    }
                },
                onDoubleClick = if (pointer) onPlay else null,
                onPlay = onPlay,
                menu = { item ->
                    menu(item, { onPlay(item) }) {
                        selected = item.key
                        requested = true
                    }
                },
            )
        },
    )
}

@Composable
private fun ShowEpisodes(
    show: Show,
    tech: Map<String, MediaTech>,
    season: Int,
    onSeasonChange: (Int) -> Unit,
    onClose: () -> Unit,
    selected: String?,
    onClick: (VideoItem) -> Unit,
    onDoubleClick: ((VideoItem) -> Unit)?,
    onPlay: (VideoItem) -> Unit,
    menu: (VideoItem) -> List<MenuEntry>,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val next = show.next
    val episodes = show.seasons.firstOrNull { it.number == season }?.episodes ?: show.seasons.first().episodes
    val compact = LocalNavKind.current == NavKind.BottomBar
    Column(Modifier.fillMaxSize().testTag("show")) {
        Row(
            Modifier.fillMaxWidth().padding(start = dimens.pagePadding - 8.dp, end = dimens.pagePadding, top = dimens.gapS, bottom = dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_series_back), onClose)
            Spacer(Modifier.width(dimens.gapXs))
            Column(Modifier.weight(1f)) {
                Text(show.title, style = NagTheme.type.display, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
                Text(
                    pluralStringResource(R.plurals.nag_seasons_count, show.seasons.size, show.seasons.size) + " · " +
                        pluralStringResource(R.plurals.nag_episodes_count, show.episodes.size, show.episodes.size) + " · " +
                        stringResource(R.string.nag_watched_count, show.watched, show.episodes.size),
                    style = NagTheme.type.body,
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (next != null && !compact) {
                NagButton(stringResource(R.string.nag_continue_episode, next.code), { onPlay(next.item) }, icon = NagIcons.Play, primary = true)
            }
        }
        // a phone gives the title the whole row and the button its own
        if (next != null && compact) {
            Box(Modifier.padding(start = dimens.pagePadding, end = dimens.pagePadding, bottom = dimens.gapS)) {
                NagButton(stringResource(R.string.nag_continue_episode, next.code), { onPlay(next.item) }, icon = NagIcons.Play, primary = true)
            }
        }
        if (show.seasons.size > 1) {
            Box(Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs)) {
                SegmentedChoice(
                    options = show.seasons.map { it.number to stringResource(R.string.nag_season_label, it.number) },
                    selected = season,
                    onSelect = onSeasonChange,
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = dimens.pagePadding - 8.dp, vertical = dimens.gapS)) {
            items(episodes, key = { it.item.key }) { episode ->
                ContextMenuArea(entries = { menu(episode.item) }, accessibilityLabel = stringResource(R.string.nag_more_actions)) {
                    EpisodeRow(
                        episode = episode,
                        tech = tech[episode.item.key],
                        next = episode == next,
                        selected = episode.item.key == selected,
                        onClick = { onClick(episode.item) },
                        onDoubleClick = onDoubleClick?.let { play -> { play(episode.item) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    tech: MediaTech?,
    next: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)?,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val item = episode.item
    // "Bölüm 3" when the file names no title
    val number = stringResource(R.string.nag_episode_number, episode.info.episode)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTarget)
            .focusRing(state, shape)
            .background(stateBackground(state, selected = selected), shape)
            .semantics { this.selected = selected }
            .nagClickable(interaction, onClick, onClickLabel = item.title, onDoubleClick = onDoubleClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(dimens.radiusSmall))) {
            Artwork(item.media, size = 160.dp, modifier = Modifier.fillMaxSize(), corner = 0.dp, video = true)
            if (item.progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(colors.scrim)) {
                    Box(Modifier.fillMaxWidth(item.progress).fillMaxHeight().background(colors.accent))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(episode.code, style = NagTheme.type.mono, color = if (selected) colors.onSelection else colors.accentText)
                Spacer(Modifier.width(8.dp))
                Text(episode.info.title.ifEmpty { number }, style = NagTheme.type.bodyStrong,
                    color = if (selected) colors.onSelection else colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (next) {
                    Spacer(Modifier.width(8.dp))
                    NextTag()
                }
            }
            Text(
                listOfNotNull(formatDuration(item.durationMs), tech?.let(::techSummary)?.takeIf { it.isNotEmpty() }).joinToString(" · "),
                style = NagTheme.type.labelSmall,
                color = if (selected) colors.onSelection else colors.textTertiary,
                maxLines = 1,
            )
        }
        if (item.watched) {
            Spacer(Modifier.width(8.dp))
            Icon(NagIcons.Check, contentDescription = stringResource(R.string.nag_watched), tint = colors.success, modifier = Modifier.size(18.dp))
        }
    }
}

/** "Sıradaki": the episode the show goes on with. */
@Composable
private fun NextTag() {
    val colors = NagTheme.colors
    Text(
        stringResource(R.string.nag_next_tag),
        style = NagTheme.type.caption,
        color = colors.accentText,
        maxLines = 1,
        modifier = Modifier
            .border(1.dp, colors.accent, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
