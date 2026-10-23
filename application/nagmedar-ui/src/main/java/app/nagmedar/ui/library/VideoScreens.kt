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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.nagmedar.ui.components.MultiSelection
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SelectionBar
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.ColumnSort
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.rememberMultiSelection
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ContextMenuArea
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import app.nagmedar.data.FilmKind
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.library.metadata.CountBadge
import app.nagmedar.ui.library.metadata.EpisodeAbout
import app.nagmedar.ui.library.metadata.EpisodeMeta
import app.nagmedar.ui.library.metadata.FilmAbout
import app.nagmedar.ui.library.metadata.FilmHero
import app.nagmedar.ui.library.metadata.FilmImage
import app.nagmedar.ui.library.metadata.FilmImageSize
import app.nagmedar.ui.library.metadata.FilmInfo
import app.nagmedar.ui.library.metadata.FilmQuery
import app.nagmedar.ui.library.metadata.FilmSearchDialog
import app.nagmedar.ui.library.metadata.LibraryViews
import app.nagmedar.ui.library.metadata.PosterCard
import app.nagmedar.ui.library.metadata.PosterThumb
import app.nagmedar.ui.library.metadata.SeriesView
import app.nagmedar.ui.library.metadata.WatchMark
import app.nagmedar.ui.library.metadata.WatchMarks
import app.nagmedar.ui.library.metadata.WatchState
import app.nagmedar.ui.library.metadata.WatchedBadge
import app.nagmedar.ui.library.metadata.WatchedBar
import app.nagmedar.ui.library.metadata.factsLine
import app.nagmedar.ui.library.metadata.filmMenuEntries
import app.nagmedar.ui.library.metadata.filmQuery
import app.nagmedar.ui.library.metadata.heroOf
import app.nagmedar.ui.library.metadata.ratingNumber
import app.nagmedar.ui.library.metadata.ratingsLine
import app.nagmedar.ui.library.metadata.rememberEpisodeMeta
import app.nagmedar.ui.library.metadata.rememberFilmActions
import app.nagmedar.ui.library.metadata.rememberFilmInfo
import app.nagmedar.ui.library.metadata.rememberFilmKeys
import app.nagmedar.ui.library.metadata.watchState
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.LocalNavKind
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme

/** How the films are shown: cards, the dense list, their folders, or posters (film information). */
enum class MoviesView { Grid, Table, Folders, Poster }

// ---------------------------------------------------------------- Films (S-801)

@Composable
fun MoviesScreen(repository: MediaRepository, onAddFolder: () -> Unit) {
    val context = LocalContext.current
    val scan = rememberScanState()
    val access = LocalLibraryAccess.current
    // a film marked watched or unwatched is read again
    val marks by WatchMarks.changes.collectAsState()
    // the films of the section's folders (Settings › Library)
    val items by rememberRead<List<VideoItem>>(repository.lastRead, "movies", scan.active, access?.media, access?.folders, access?.scopes, marks) {
        loadWhileScanning(scan.active) { repository.videoItems(LibrarySection.Movies) }
    }
    val library = remember(items) { items?.let { repository.lastRead.derived("movies", it) { list -> buildVideoLibrary(list, moviesOnly = true) } } }
    val tech by rememberTech(items)
    val queries = remember(library) { library?.movies?.mapNotNull { it.filmQuery() } }
    val info by rememberFilmInfo(queries)
    val scope = rememberCoroutineScope()
    var fixing by remember { mutableStateOf<FilmQuery?>(null) }
    fixing?.let { query -> FilmSearchDialog(query) { fixing = null } }
    val pointer = NagTheme.inputMode == InputMode.Pointer
    // the view is kept for the section (Izgara, Afiş, Liste, Klasörler)
    var view by rememberSaveable {
        mutableStateOf(LibraryViews.read(context, LibraryViews.FILMS, MoviesView.entries) ?: if (pointer) MoviesView.Table else MoviesView.Grid)
    }
    val changeView: (MoviesView) -> Unit = {
        view = it
        LibraryViews.save(context, LibraryViews.FILMS, it)
    }
    var filters by rememberSaveable { mutableStateOf(emptySet<MovieFilter>()) }
    var order by rememberSaveable { mutableStateOf(MovieOrder.Added) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    // by its column: the columns a window shows change with its width
    var sort by rememberSaveable(stateSaver = ColumnSort.StateSaver) { mutableStateOf<ColumnSort?>(null) }
    // one selected film has its details; several have the selection bar (S-405)
    val selection = rememberMultiSelection()
    val selected = selection.keys.singleOrNull() as? String
    var detailsRequested by rememberSaveable { mutableStateOf(false) }
    val play: (VideoItem) -> Unit = { item -> item.media?.let { PlaybackActions.playVideo(context, it) } }
    BackHandler(enabled = folder != null) { folder = null }
    // selecting by touch (S-310): Back lets go first
    BackHandler(enabled = selection.selecting) { selection.clear() }
    // the details stand beside the header and the list, the full height of the screen (S-303)
    VideoDetailsPanes(
        selected = library?.movies?.firstOrNull { it.key == selected },
        detailsRequested = detailsRequested,
        tech = tech,
        onPlay = play,
        onCloseDetails = {
            selection.clear()
            detailsRequested = false
        },
        info = { item -> item.filmQuery()?.let { info[it.key] } },
        about = { item ->
            val query = item.filmQuery()
            FilmAbout(query?.let { info[it.key] }, FilmKind.MOVIE, rememberFilmActions(query), showTitle = false)
        },
    ) {
        // the macrobenchmarks scroll its list by this tag (S-1404)
        Column(Modifier.fillMaxSize().testTag("films")) {
            MoviesHeader(library, view, changeView, order, { order = it }, onAddFolder)
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
                    // the selection took the click already
                    onSelect = {},
                    onShowDetails = {
                        selection.click(it.key, listOf(it.key))
                        detailsRequested = true
                    },
                    onPlay = play,
                    menu = { item, playItem, details ->
                        // by touch the menu starts selecting (S-310)
                        val select = if (pointer) null else ({ selection.startSelecting(item.key) })
                        val extra = WatchMarks.menuEntries(context, listOf(item)) + filmMenuEntries(context, item.filmQuery(), scope) { fixing = it }
                        item.media?.let { mediaMenu(context, it, playItem, details, select) + listOf(MenuEntry.Separator) + extra }.orEmpty()
                    },
                    selection = selection,
                    onPlayAll = { films -> PlaybackActions.playList(context, films.mapNotNull { it.media }, 0) },
                    onQueueAll = { films -> PlaybackActions.appendList(context, films.mapNotNull { it.media }) },
                    onShareAll = { films -> MediaSharing.share(context, films.mapNotNull { it.media }.map { MediaFile.of(it) }) },
                    info = info,
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
            ViewButtons(
                options = listOf(
                    Triple(MoviesView.Grid, NagIcons.Grid, stringResource(R.string.nag_view_grid)),
                    Triple(MoviesView.Poster, NagIcons.Posters, stringResource(R.string.nag_view_poster)),
                    Triple(MoviesView.Table, NagIcons.ListView, stringResource(R.string.nag_view_list)),
                    Triple(MoviesView.Folders, NagIcons.Folder, stringResource(R.string.nag_view_folders)),
                ),
                selected = view,
                onSelect = onViewChange,
                compact = compact,
            )
            if (compact) {
                IconMenuButton(NagIcons.Sort, sortLabel, sortEntries)
            } else {
                Spacer(Modifier.width(NagTheme.dimens.gapXs))
                MenuButton(sortLabel, entries = sortEntries)
            }
        }
    }
}

/**
 * The views of a section (film information: Izgara, Afiş, Liste…) as icon buttons, the chosen one
 * selected; on a phone one button with the views in its menu.
 */
@Composable
internal fun <T> ViewButtons(options: List<Triple<T, androidx.compose.ui.graphics.vector.ImageVector, String>>, selected: T, onSelect: (T) -> Unit, compact: Boolean) {
    if (compact) {
        val current = options.firstOrNull { it.first == selected } ?: options.first()
        IconMenuButton(current.second, stringResource(R.string.nag_view_menu, current.third), entries = {
            options.map { (value, icon, label) -> MenuEntry.Item(label, { onSelect(value) }, icon = icon, checked = value == selected) }
        })
    } else {
        options.forEach { (value, icon, label) -> NagIconButton(icon, label, { onSelect(value) }, selected = value == selected) }
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
 * one, as cards with the "İzlemeye devam et" shelf on top, or as the dense table.
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
    sort: ColumnSort?,
    onSortChange: (ColumnSort) -> Unit,
    selected: String?,
    onSelect: (VideoItem) -> Unit,
    onShowDetails: (VideoItem) -> Unit,
    onPlay: (VideoItem) -> Unit,
    menu: (VideoItem, play: () -> Unit, info: () -> Unit) -> List<MenuEntry>,
    /** Several films selected (S-405), with the bar's actions on them. */
    selection: MultiSelection? = null,
    onPlayAll: (List<VideoItem>) -> Unit = {},
    onQueueAll: (List<VideoItem>) -> Unit = {},
    /** The selected films' files to another app, with the system's share sheet. */
    onShareAll: (List<VideoItem>) -> Unit = {},
    /** Film information by [FilmQuery.key]: posters, the hero, years and ratings. */
    info: Map<String, FilmInfo> = emptyMap(),
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
        if (selection != null) {
            // a filter or a scan takes away what is no longer listed
            LaunchedEffect(shown) { selection.retain(shown.map { it.key }) }
            if (selection.count > 1 || selection.selecting) {
                val chosen = shown.filter { it.key in selection }
                SelectionBar(
                    text = pluralStringResource(R.plurals.nag_selected_films, chosen.size, chosen.size),
                    onClear = selection::clear,
                    clearLabel = stringResource(R.string.nag_selection_clear),
                    modifier = Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs),
                ) {
                    NagButton(stringResource(R.string.nag_selection_play), { onPlayAll(chosen) }, primary = true)
                    NagButton(stringResource(R.string.nag_add_to_queue), { onQueueAll(chosen) })
                    NagButton(stringResource(R.string.nag_media_share), { onShareAll(chosen) })
                    val toWatched = chosen.any { it.watchState != WatchState.Watched }
                    NagButton(stringResource(if (toWatched) R.string.nag_mark_watched else R.string.nag_mark_unwatched), { WatchMarks.mark(chosen, toWatched) })
                }
            }
        }
        val shelf = if (filters.isEmpty() && openFolder == null) library.continueMovies else emptyList()
        // the "Afiş" view opens with a film's backdrop, as the streaming apps do
        val hero = if (view == MoviesView.Poster && filters.isEmpty() && openFolder == null) {
            // the films' names are read once for the library, not again with each change of the film information
            val keys = rememberFilmKeys(library.movies)
            remember(library, keys, info) { heroOf(library.movies, keys, info) }
        } else {
            null
        }
        val top: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.gapL)) {
                if (hero != null) FilmHero(hero.second, hero.first, onPlay = { onPlay(hero.first) }, onDetails = { onShowDetails(hero.first) })
                if (shelf.isNotEmpty()) ContinueShelf(shelf, tech, onPlay) { item -> menu(item, { onPlay(item) }, { onShowDetails(item) }) }
            }
        }
        val shelfHeader = top.takeIf { hero != null || shelf.isNotEmpty() }
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
                poster = view == MoviesView.Poster,
                info = info,
                sort = sort,
                onSortChange = onSortChange,
                selected = selected,
                onSelect = onSelect,
                onShowDetails = onShowDetails,
                onPlay = onPlay,
                menu = menu,
                gridHeader = shelfHeader,
                selection = selection,
            )
        }
    }
}

/** "İzlemeye devam et": the films in progress, in a row that scrolls with the grid. */
@Composable
fun ContinueShelf(items: List<VideoItem>, tech: Map<String, MediaTech>, onPlay: (VideoItem) -> Unit, menu: (VideoItem) -> List<MenuEntry>) {
    val dimens = NagTheme.dimens
    Column(Modifier.fillMaxWidth().testTag("continue")) {
        Text(
            stringResource(R.string.nag_continue_watching),
            style = NagTheme.type.title,
            color = NagTheme.colors.text,
            modifier = Modifier.padding(bottom = dimens.gapS).semantics { heading() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapM)) {
            items(items, key = { it.key }) { item ->
                ContextMenuArea(entries = { menu(item) }, accessibilityLabel = stringResource(R.string.nag_more_actions)) {
                    VideoCard(item, tech[item.key], Modifier.width(240.dp), onClick = { onPlay(item) })
                }
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
    // an episode marked watched or unwatched is read again
    val marks by WatchMarks.changes.collectAsState()
    // the episodes of the section's folders (Settings › Library)
    val items by rememberRead<List<VideoItem>>(repository.lastRead, "series", scan.active, access?.media, access?.folders, access?.scopes, marks) {
        loadWhileScanning(scan.active) { repository.videoItems(LibrarySection.Series) }
    }
    // the episodes' names are parsed once for a list: not again in the first frame of a screen that comes back
    val library = remember(items) { items?.let { repository.lastRead.derived("series", it, ::buildVideoLibrary) } }
    val tech by rememberTech(items)
    val shows = library?.shows
    val queries = remember(shows) { shows?.map { it.filmQuery() } }
    val info by rememberFilmInfo(queries)
    val scope = rememberCoroutineScope()
    var fixing by remember { mutableStateOf<FilmQuery?>(null) }
    fixing?.let { query -> FilmSearchDialog(query) { fixing = null } }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var season by rememberSaveable { mutableStateOf<Int?>(null) }
    // the view is kept for the section (Izgara, Afiş, Liste)
    var view by rememberSaveable { mutableStateOf(LibraryViews.read(context, LibraryViews.SERIES, SeriesView.entries) ?: SeriesView.Grid) }
    BackHandler(enabled = open != null) { open = null }
    val compact = LocalNavKind.current == NavKind.BottomBar
    val openShow: (String) -> Unit = {
        open = it
        season = null
    }
    Column(Modifier.fillMaxSize()) {
        if (open == null) {
            PageHeader(
                title = stringResource(R.string.nag_nav_series),
                subtitle = shows?.let {
                    pluralStringResource(R.plurals.nag_series_count, it.size, it.size) + " · " +
                        pluralStringResource(R.plurals.nag_episodes_count, it.sumOf { s -> s.episodes.size }, it.sumOf { s -> s.episodes.size })
                },
            ) {
                if (!shows.isNullOrEmpty()) {
                    ViewButtons(
                        options = listOf(
                            Triple(SeriesView.Grid, NagIcons.Grid, stringResource(R.string.nag_view_grid)),
                            Triple(SeriesView.Poster, NagIcons.Posters, stringResource(R.string.nag_view_poster)),
                            Triple(SeriesView.List, NagIcons.ListView, stringResource(R.string.nag_view_list)),
                        ),
                        selected = view,
                        onSelect = {
                            view = it
                            LibraryViews.save(context, LibraryViews.SERIES, it)
                        },
                        compact = compact,
                    )
                }
            }
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
                onOpen = openShow,
                onClose = { open = null },
                season = season,
                onSeasonChange = { season = it },
                onPlay = { item -> item.media?.let { PlaybackActions.playVideo(context, it) } },
                menu = { item, play, details ->
                    item.media?.let { mediaMenu(context, it, play, details) + listOf(MenuEntry.Separator) + WatchMarks.menuEntries(context, listOf(item)) }.orEmpty()
                },
                view = view,
                info = info,
                showMenu = { show ->
                    showMarkEntries(context, show.episodes.map { it.item }) + filmMenuEntries(context, show.filmQuery(), scope) { fixing = it }
                },
                onFix = { fixing = it },
            )
        }
    }
}

/** "Hepsini izlendi olarak işaretle", "Hepsini izlenmedi olarak işaretle" for a series' or a season's [items]. */
private fun showMarkEntries(context: android.content.Context, items: List<VideoItem>, season: Int? = null): List<MenuEntry> {
    val res = context.resources
    val anyUnwatched = items.any { it.watchState != WatchState.Watched }
    val anyStarted = items.any { it.watchState != WatchState.Unwatched }
    return listOfNotNull(
        if (anyUnwatched) {
            MenuEntry.Item(
                if (season != null) res.getString(R.string.nag_mark_season_watched, season) else res.getString(R.string.nag_mark_all_watched),
                { WatchMarks.mark(items, true) },
                icon = NagIcons.Check,
            )
        } else {
            null
        },
        if (anyStarted) {
            MenuEntry.Item(
                if (season != null) res.getString(R.string.nag_mark_season_unwatched, season) else res.getString(R.string.nag_mark_all_unwatched),
                { WatchMarks.mark(items, false) },
                icon = NagIcons.Resume,
            )
        } else {
            null
        },
    )
}

/** Episodes not watched to the end yet. */
private val Show.unwatched: Int get() = episodes.size - watched

/**
 * The shows as cards with what comes next, as posters, or as the dense list; or one show's seasons
 * and episodes (S-801) with the details of the selected episode beside or over them (S-303).
 * Film information gives the posters, backdrops, overviews and the episodes' titles.
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
    view: SeriesView = SeriesView.Grid,
    info: Map<String, FilmInfo> = emptyMap(),
    /** A show's menu: mark it, refresh it, search for it ("Aç" comes first where it is listed). */
    showMenu: (Show) -> List<MenuEntry> = { emptyList() },
    onFix: (FilmQuery) -> Unit = {},
) {
    val show = shows.firstOrNull { it.key == open }
    if (show != null) {
        ShowDetail(
            show = show,
            info = info[show.filmQuery().key],
            tech = tech,
            season = season ?: show.next?.info?.season ?: show.seasons.first().number,
            onSeasonChange = onSeasonChange,
            onClose = onClose,
            onPlay = onPlay,
            menu = menu,
            showMenu = { showMenu(show) },
            onFix = onFix,
        )
    } else {
        when (view) {
            SeriesView.Grid -> ShowGrid(shows, info, onOpen, showMenu)
            SeriesView.Poster -> ShowPosterGrid(shows, info, onOpen, showMenu)
            SeriesView.List -> ShowTable(shows, info, onOpen, showMenu)
        }
    }
}

/** The shows being watched: an episode in progress, or some watched and some not. */
private fun goingOn(shows: List<Show>): List<Show> = shows.filter { s -> s.episodes.any { it.item.progress > 0f } || (s.watched in 1 until s.episodes.size) }

@Composable
private fun ShowGrid(shows: List<Show>, info: Map<String, FilmInfo>, onOpen: (String) -> Unit, showMenu: (Show) -> List<MenuEntry>) {
    val dimens = NagTheme.dimens
    val pointer = NagTheme.inputMode == InputMode.Pointer
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (pointer) 220.dp else 260.dp),
        contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapL),
        verticalArrangement = Arrangement.spacedBy(dimens.gapL),
        modifier = Modifier.fillMaxSize(),
    ) {
        val going = goingOn(shows)
        if (going.isNotEmpty()) {
            item(key = "continue", span = { GridItemSpan(maxLineSpan) }) { SeriesHeading(stringResource(R.string.nag_continue_watching)) }
            items(going, key = { "continue-" + it.key }) { s -> ShowMenuArea(s, showMenu, onOpen) { ShowCard(s, info[s.filmQuery().key]) { onOpen(s.key) } } }
            item(key = "all", span = { GridItemSpan(maxLineSpan) }) { SeriesHeading(stringResource(R.string.nag_series_all), Modifier.padding(top = dimens.gapS)) }
        }
        items(shows, key = { it.key }) { s -> ShowMenuArea(s, showMenu, onOpen) { ShowCard(s, info[s.filmQuery().key]) { onOpen(s.key) } } }
    }
}

@Composable
private fun SeriesHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = NagTheme.type.title, color = NagTheme.colors.text, modifier = modifier.semantics { heading() })
}

@Composable
private fun ShowMenuArea(show: Show, showMenu: (Show) -> List<MenuEntry>, onOpen: (String) -> Unit, content: @Composable () -> Unit) {
    val open = stringResource(R.string.nag_series_open)
    ContextMenuArea(
        entries = { listOf(MenuEntry.Item(open, { onOpen(show.key) }, shortcut = "Enter", icon = NagIcons.ChevronRight)) + showMenu(show) },
        accessibilityLabel = stringResource(R.string.nag_more_actions),
    ) { content() }
}

/** The "Afiş" view of the series: posters with the unwatched episodes in the corner. */
@Composable
private fun ShowPosterGrid(shows: List<Show>, info: Map<String, FilmInfo>, onOpen: (String) -> Unit, showMenu: (Show) -> List<MenuEntry>) {
    val dimens = NagTheme.dimens
    val pointer = NagTheme.inputMode == InputMode.Pointer
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (pointer) 150.dp else 160.dp),
        contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapM),
        verticalArrangement = Arrangement.spacedBy(dimens.gapM),
        modifier = Modifier.fillMaxSize().testTag("series-posters"),
    ) {
        val going = goingOn(shows)
        val poster: @Composable (Show) -> Unit = { s ->
            val film = info[s.filmQuery().key]
            val next = s.next
            ShowMenuArea(s, showMenu, onOpen) {
                PosterCard(
                    title = film?.title ?: s.title,
                    subtitle = if (next != null) stringResource(R.string.nag_next_episode, next.code) else stringResource(R.string.nag_all_watched),
                    posterPath = film?.posterPath,
                    media = (next ?: s.episodes.first()).item.media,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpen(s.key) },
                    progress = (s.watched.toFloat() / s.episodes.size).takeIf { it < 1f } ?: 0f,
                    watched = s.unwatched == 0,
                    unwatched = s.unwatched,
                )
            }
        }
        if (going.isNotEmpty()) {
            item(key = "continue", span = { GridItemSpan(maxLineSpan) }) { SeriesHeading(stringResource(R.string.nag_continue_watching)) }
            items(going, key = { "continue-" + it.key }) { poster(it) }
            item(key = "all", span = { GridItemSpan(maxLineSpan) }) { SeriesHeading(stringResource(R.string.nag_series_all), Modifier.padding(top = dimens.gapS)) }
        }
        items(shows, key = { it.key }) { poster(it) }
    }
}

/** The columns of the series' "Liste" view, which its sort is kept by. */
private enum class ShowColumnId { Title, Year, Seasons, Episodes, Watched, Imdb }

/** The "Liste" view of the series: poster, name, year, seasons, episodes, watched, ratings; sortable. */
@Composable
private fun ShowTable(shows: List<Show>, info: Map<String, FilmInfo>, onOpen: (String) -> Unit, showMenu: (Show) -> List<MenuEntry>) {
    val width = currentWidthClass()
    val locale = currentLocale()
    val compact = width < WidthClass.Medium
    val thumb = if (NagTheme.inputMode == InputMode.Pointer) 22.dp else 30.dp
    val films = remember(shows, info) { shows.associate { it.key to info[it.filmQuery().key] } }
    val title = stringResource(R.string.nag_column_title)
    val year = stringResource(R.string.nag_column_year)
    val seasons = stringResource(R.string.nag_column_seasons)
    val episodes = stringResource(R.string.nag_column_episodes)
    val watched = stringResource(R.string.nag_column_watched)
    val imdb = stringResource(R.string.nag_column_imdb)
    val openLabel = stringResource(R.string.nag_series_open)
    // read with the locale: a change of language recomposes
    val res = LocalContext.current.resources
    // by its column: the columns a window shows change with its width
    var sort by rememberSaveable(stateSaver = ColumnSort.StateSaver) { mutableStateOf<ColumnSort?>(null) }
    val columns = remember(films, compact, width, locale, thumb, res) {
        listOfNotNull(
            ShowColumnId.Title to TableColumn<Show>(title, ColumnWidth.Weight(1f), content = { show, selected -> ShowTitleCell(show, films[show.key], thumb, selected, compact) }) {
                films[it.key]?.title ?: it.title
            },
            if (compact) null else ShowColumnId.Year to TableColumn<Show>(year, ColumnWidth.Fixed(64.dp), mono = true) { films[it.key]?.year?.toString() ?: "–" },
            if (compact) null else ShowColumnId.Seasons to TableColumn<Show>(seasons, ColumnWidth.Fixed(72.dp), alignEnd = true, mono = true) { it.seasons.size.toString() },
            if (width < WidthClass.Expanded) null else ShowColumnId.Episodes to TableColumn<Show>(episodes, ColumnWidth.Fixed(72.dp), alignEnd = true, mono = true) { it.episodes.size.toString() },
            ShowColumnId.Watched to TableColumn<Show>(watched, ColumnWidth.Fixed(if (compact) 64.dp else 84.dp), alignEnd = true, mono = true) { "${it.watched}/${it.episodes.size}" },
            if (compact) null else ShowColumnId.Imdb to TableColumn<Show>(imdb, ColumnWidth.Fixed(64.dp), alignEnd = true, mono = true) { s ->
                films[s.key]?.imdbRating?.let { ratingNumber(it, locale) } ?: "–"
            },
        )
    }
    val ids = remember(columns) { columns.map { it.first.name } }
    val shownSort = sort?.among(ids)
    val sorted = remember(shows, shownSort, columns, films) {
        val s = shownSort ?: return@remember shows
        val comparator: Comparator<Show> = when (columns[s.column].first) {
            ShowColumnId.Year -> compareBy { films[it.key]?.year ?: 0 }
            ShowColumnId.Seasons -> compareBy { it.seasons.size }
            ShowColumnId.Episodes -> compareBy { it.episodes.size }
            ShowColumnId.Watched -> compareBy { it.watched.toFloat() / it.episodes.size }
            ShowColumnId.Imdb -> compareBy { films[it.key]?.imdbRating ?: -1.0 }
            ShowColumnId.Title -> compareBy(String.CASE_INSENSITIVE_ORDER) { films[it.key]?.title ?: it.title }
        }
        if (s.ascending) shows.sortedWith(comparator) else shows.sortedWith(comparator.reversed())
    }
    DenseTable(
        columns = columns.map { it.second },
        rows = sorted,
        key = { it.key },
        sort = shownSort,
        onSortChange = { s -> sort = ColumnSort.of(s, ids) },
        onRowClick = { show, _ -> onOpen(show.key) },
        rowMenu = { show, _ -> listOf(MenuEntry.Item(openLabel, { onOpen(show.key) }, shortcut = "Enter", icon = NagIcons.ChevronRight)) + showMenu(show) },
        rowMenuLabel = stringResource(R.string.nag_more_actions),
        modifier = Modifier.testTag("series-list"),
    )
}

@Composable
private fun ShowTitleCell(show: Show, info: FilmInfo?, thumb: androidx.compose.ui.unit.Dp, selected: Boolean, compact: Boolean) {
    val colors = NagTheme.colors
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        PosterThumb(info?.posterPath, (show.next ?: show.episodes.first()).item.media, thumb)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(info?.title ?: show.title, style = NagTheme.type.bodyStrong, color = if (selected) colors.onSelection else colors.text, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            val next = show.next
            val line = listOfNotNull(
                info?.year?.takeIf { compact }?.toString(),
                if (next != null) stringResource(R.string.nag_next_episode, next.code) else stringResource(R.string.nag_all_watched),
            ).joinToString(" · ")
            Text(line, style = NagTheme.type.labelSmall, color = if (selected) colors.onSelection else colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (show.unwatched > 0) {
            Spacer(Modifier.width(6.dp))
            CountBadge(show.unwatched)
        }
    }
}

@Composable
private fun ShowCard(show: Show, info: FilmInfo?, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusCard)
    val next = show.next
    val cover = (next ?: show.episodes.first()).item
    val unwatchedText = pluralStringResource(R.plurals.nag_unwatched_episodes, show.unwatched, show.unwatched)
    val px = with(LocalDensity.current) { 320.dp.roundToPx() }
    Column(
        Modifier
            .fillMaxWidth()
            .focusRing(state, shape)
            .background(stateBackground(state, rest = colors.background), shape)
            .semantics { if (show.unwatched > 0) stateDescription = unwatchedText }
            .nagClickable(interaction, onClick, onClickLabel = show.title)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(dimens.radiusControl))) {
            FilmImage(info?.backdropPath, FilmImageSize.Backdrop, px, Modifier.fillMaxSize()) {
                Artwork(cover.media, size = 320.dp, modifier = Modifier.fillMaxSize(), corner = 0.dp, video = true)
            }
            val fraction = show.watched.toFloat() / show.episodes.size
            if (fraction > 0f) WatchedBar(fraction, Modifier.align(Alignment.BottomStart))
            if (next != null) TechBadge(next.code, Modifier.align(Alignment.TopStart).padding(6.dp))
            if (show.unwatched > 0) CountBadge(show.unwatched, Modifier.align(Alignment.TopEnd).padding(6.dp)) else WatchedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(info?.title ?: show.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(
                info?.year?.toString(),
                pluralStringResource(R.plurals.nag_seasons_count, show.seasons.size, show.seasons.size),
                pluralStringResource(R.plurals.nag_episodes_count, show.episodes.size, show.episodes.size),
            ).joinToString(" · "),
            style = NagTheme.type.labelSmall,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (next != null) stringResource(R.string.nag_next_episode, next.code) else stringResource(R.string.nag_all_watched),
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShowDetail(
    show: Show,
    info: FilmInfo?,
    tech: Map<String, MediaTech>,
    season: Int,
    onSeasonChange: (Int) -> Unit,
    onClose: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    menu: (VideoItem, play: () -> Unit, info: () -> Unit) -> List<MenuEntry>,
    showMenu: () -> List<MenuEntry>,
    onFix: (FilmQuery) -> Unit,
) {
    val pointer = NagTheme.inputMode == InputMode.Pointer
    var selected by rememberSaveable(show.key) { mutableStateOf<String?>(null) }
    var requested by rememberSaveable(show.key) { mutableStateOf(false) }
    val episodes = rememberEpisodeMeta(info?.tmdbId)
    val metaOf: (VideoItem) -> EpisodeMeta? = { item ->
        show.episodes.firstOrNull { it.item.key == item.key }?.let { episodes[it.info.season to it.info.episode] }
    }
    VideoDetailsPanes(
        selected = show.episodes.firstOrNull { it.item.key == selected }?.item,
        detailsRequested = requested,
        tech = tech,
        onPlay = onPlay,
        onCloseDetails = {
            selected = null
            requested = false
        },
        info = { info },
        episode = metaOf,
        about = { item -> metaOf(item)?.let { EpisodeAbout(it) } },
        list = {
            ShowEpisodes(
                show = show,
                info = info,
                episodes = episodes,
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
                showMenu = showMenu,
                onFix = onFix,
            )
        },
    )
}

@Composable
private fun ShowEpisodes(
    show: Show,
    info: FilmInfo?,
    episodes: Map<Pair<Int, Int>, EpisodeMeta>,
    tech: Map<String, MediaTech>,
    season: Int,
    onSeasonChange: (Int) -> Unit,
    onClose: () -> Unit,
    selected: String?,
    onClick: (VideoItem) -> Unit,
    onDoubleClick: ((VideoItem) -> Unit)?,
    onPlay: (VideoItem) -> Unit,
    menu: (VideoItem) -> List<MenuEntry>,
    showMenu: () -> List<MenuEntry>,
    onFix: (FilmQuery) -> Unit,
) {
    val context = LocalContext.current
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val next = show.next
    val current = show.seasons.firstOrNull { it.number == season } ?: show.seasons.first()
    val compact = LocalNavKind.current == NavKind.BottomBar
    val wide = currentWidthClass() >= WidthClass.Medium
    Column(Modifier.fillMaxSize().testTag("show")) {
        Row(
            Modifier.fillMaxWidth().padding(start = dimens.pagePadding - 8.dp, end = dimens.pagePadding, top = dimens.gapS, bottom = dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_series_back), onClose)
            Spacer(Modifier.width(dimens.gapXs))
            Column(Modifier.weight(1f)) {
                Text(info?.title ?: show.title, style = NagTheme.type.display, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                Spacer(Modifier.width(dimens.gapXs))
            }
            IconMenuButton(NagIcons.MoreHorizontal, stringResource(R.string.nag_series_actions), showMenu)
        }
        // a phone gives the title the whole row and the button its own
        if (next != null && compact) {
            Box(Modifier.padding(start = dimens.pagePadding, end = dimens.pagePadding, bottom = dimens.gapS)) {
                NagButton(stringResource(R.string.nag_continue_episode, next.code), { onPlay(next.item) }, icon = NagIcons.Play, primary = true)
            }
        }
        // film information: the series' poster, facts, ratings and overview
        if (info != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs), verticalAlignment = Alignment.Top) {
                PosterThumb(info.posterPath, null, if (wide) 72.dp else 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val facts = factsLine(info, genres = 3)
                    if (facts.isNotEmpty()) Text(facts, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val ratings = ratingsLine(info)
                    if (ratings.isNotEmpty()) Text(ratings, style = NagTheme.type.mono, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    info.overview?.let {
                        Text(it, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = if (wide) 3 else 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (show.seasons.size > 1) {
                SegmentedChoice(
                    options = show.seasons.map { s ->
                        s.number to stringResource(R.string.nag_season_progress, s.number, s.episodes.count { it.item.watched }, s.episodes.size)
                    },
                    selected = current.number,
                    onSelect = onSeasonChange,
                )
                Spacer(Modifier.width(dimens.gapS))
            }
            IconMenuButton(NagIcons.Check, stringResource(R.string.nag_season_actions, current.number), entries = {
                showMarkEntries(context, current.episodes.map { it.item }, current.number)
            })
        }
        // the season's progress: "3 / 10 izlendi"
        val seasonWatched = current.episodes.count { it.item.watched }
        val seasonLabel = stringResource(R.string.nag_watched_count, seasonWatched, current.episodes.size)
        Row(Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs), verticalAlignment = Alignment.CenterVertically) {
            Text(seasonLabel, style = NagTheme.type.labelSmall, color = colors.textSecondary)
            Spacer(Modifier.width(dimens.gapS))
            Box(Modifier.width(160.dp)) { NagProgressBar(seasonWatched.toFloat() / current.episodes.size, label = seasonLabel) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = dimens.pagePadding - 8.dp, vertical = dimens.gapS)) {
            items(current.episodes, key = { it.item.key }) { episode ->
                ContextMenuArea(entries = { menu(episode.item) }, accessibilityLabel = stringResource(R.string.nag_more_actions)) {
                    EpisodeRow(
                        episode = episode,
                        meta = episodes[episode.info.season to episode.info.episode],
                        tech = tech[episode.item.key],
                        next = episode == next,
                        selected = episode.item.key == selected,
                        wide = wide,
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
    meta: EpisodeMeta?,
    tech: MediaTech?,
    next: Boolean,
    selected: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)?,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val item = episode.item
    val px = with(LocalDensity.current) { 160.dp.roundToPx() }
    // "Bölüm 3" when neither the file nor TMDB names it
    val number = stringResource(R.string.nag_episode_number, episode.info.episode)
    val name = episode.info.title.ifEmpty { meta?.title ?: number }
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
            FilmImage(meta?.stillPath, FilmImageSize.Still, px, Modifier.fillMaxSize()) {
                Artwork(item.media, size = 160.dp, modifier = Modifier.fillMaxSize(), corner = 0.dp, video = true)
            }
            if (item.progress > 0f) WatchedBar(item.progress, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(episode.code, style = NagTheme.type.mono, color = if (selected) colors.onSelection else colors.accentText)
                Spacer(Modifier.width(8.dp))
                Text(name, style = NagTheme.type.bodyStrong,
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
                overflow = TextOverflow.Ellipsis,
            )
            if (wide) {
                meta?.overview?.let {
                    Text(it, style = NagTheme.type.labelSmall, color = if (selected) colors.onSelection else colors.textSecondary, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        WatchMark(item.watchState, item.progress, selected = selected)
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
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .border(1.dp, colors.accent, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
