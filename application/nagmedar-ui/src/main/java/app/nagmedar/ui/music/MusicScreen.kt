package app.nagmedar.ui.music

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.kiloHertz
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.ChipRow
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.FilterChip
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.SelectionBar
import app.nagmedar.ui.components.SelectionCommands
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.TableSort
import app.nagmedar.ui.components.VerticalRule
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.rememberMultiSelection
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.LocalClickModifiers
import app.nagmedar.ui.input.fileDragSource
import app.nagmedar.ui.input.onMiddleClick
import app.nagmedar.ui.library.LibraryContent
import app.nagmedar.ui.library.MediaRepository
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.library.loadWhileScanning
import app.nagmedar.ui.library.mediaMenu
import app.nagmedar.ui.library.rememberScanState
import app.nagmedar.ui.library.LocalLibraryAccess
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.stats.TrackStatsIndex
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.media.MediaUtils
import org.videolan.tools.NAGMEDAR_MUSIC_COLUMNS
import org.videolan.tools.NAGMEDAR_MUSIC_VIEW
import org.videolan.tools.Settings

/**
 * S-803: where the music library is: the way in (tracks, artists, albums, genres, composers,
 * years, folders), the group open in it, the depth and rate filter, and the table's sort and
 * columns (S-1001). The way in and the columns are kept in the settings through [keep].
 */
@Stable
class MusicBrowserState(
    view: MusicView = MusicView.Tracks,
    columns: List<MusicColumn> = MusicColumn.DEFAULT,
    group: String? = null,
    filter: TechFilter = TechFilter(),
    sort: MusicSort? = null,
    private val keep: (MusicView, List<MusicColumn>) -> Unit = { _, _ -> },
) {
    var view by mutableStateOf(view)
        private set

    /** The group open in [view]; null for none (the first one on a wide window). */
    var group by mutableStateOf(group)

    var filter by mutableStateOf(filter)

    var sort by mutableStateOf(sort)

    var columns by mutableStateOf(columns)
        private set

    fun open(next: MusicView) {
        if (next == view) return
        view = next
        group = null
        sort = null
        keep(view, columns)
    }

    /** Shows or hides [column]; the title stays. */
    fun toggle(column: MusicColumn) {
        if (column == MusicColumn.Title) return
        columns = if (column in columns) columns - column else MusicColumn.entries.filter { it in columns || it == column }
        if (sort?.column == column && column !in columns) sort = null
        keep(view, columns)
    }

    companion object {
        fun saver(keep: (MusicView, List<MusicColumn>) -> Unit) = Saver<MusicBrowserState, List<String>>(
            save = {
                listOf(
                    it.view.name, MusicColumn.format(it.columns), it.group.orEmpty(),
                    it.filter.depths.joinToString(",") { d -> d.name }, it.filter.rates.joinToString(",") { r -> r.name },
                    it.sort?.let { s -> "${s.column.name}:${s.ascending}" }.orEmpty(),
                )
            },
            restore = { saved ->
                fun <E : Enum<E>> names(text: String, all: Array<E>): Set<E> = text.split(',').mapNotNull { n -> all.firstOrNull { it.name == n } }.toSet()
                MusicBrowserState(
                    view = MusicView.entries.firstOrNull { it.name == saved[0] } ?: MusicView.Tracks,
                    columns = MusicColumn.parse(saved[1]),
                    group = saved[2].ifEmpty { null },
                    filter = TechFilter(names(saved[3], Depth.entries.toTypedArray()), names(saved[4], RateBand.entries.toTypedArray())),
                    sort = saved[5].takeIf { it.isNotEmpty() }?.let { s ->
                        MusicColumn.entries.firstOrNull { it.name == s.substringBefore(':') }?.let { MusicSort(it, s.substringAfter(':') == "true") }
                    },
                    keep = keep,
                )
            },
        )
    }
}

/** The state of the app's music library: the way in and the columns from the settings. */
@Composable
fun rememberMusicBrowserState(): MusicBrowserState {
    val context = LocalContext.current
    val settings = remember(context) { Settings.getInstance(context) }
    val keep: (MusicView, List<MusicColumn>) -> Unit = remember(settings) {
        { view, columns ->
            settings.edit().putString(NAGMEDAR_MUSIC_VIEW, view.name.lowercase()).putString(NAGMEDAR_MUSIC_COLUMNS, MusicColumn.format(columns)).apply()
        }
    }
    return rememberSaveable(saver = MusicBrowserState.saver(keep)) {
        MusicBrowserState(
            view = MusicView.entries.firstOrNull { it.name.lowercase() == settings.getString(NAGMEDAR_MUSIC_VIEW, null) } ?: MusicView.Tracks,
            columns = MusicColumn.parse(settings.getString(NAGMEDAR_MUSIC_COLUMNS, null)),
            keep = keep,
        )
    }
}

/** What the library asks of playback: the playback service in the app, a recorder in tests. */
interface MusicActions {
    fun play(tracks: List<Track>, index: Int)

    fun queue(tracks: List<Track>)

    /** S-806: right after what plays, the rest of the queue as it was. */
    fun playNext(tracks: List<Track>) = Unit

    /** A track's context menu; [select] starts selecting by touch. */
    fun menu(track: Track, play: () -> Unit, select: (() -> Unit)?): List<MenuEntry> = emptyList()

    /** A track dragged out with the mouse, with [others] when it is among those selected (S-406). */
    fun rowModifier(track: Track, others: () -> List<Track>): Modifier = Modifier

    /** S-804: [tracks] to a playlist, chosen or made in a window; null where there is none. */
    val addToPlaylist: ((List<Track>) -> Unit)? get() = null

    /** S-808: [tracks]' rating, 0 (none) to 5 stars. */
    fun rate(tracks: List<Track>, rating: Int) = Unit

    /** S-808: the rating window for [tracks]; null where ratings are not kept. */
    val askRating: ((List<Track>) -> Unit)? get() = null
}

// ------------------------------------------------------------------- the screen

/** S-803: the music library, from the medialibrary (or the picked folders) and the engine's probe. */
@Composable
fun MusicScreen(repository: MediaRepository, onAddFolder: () -> Unit, nowPlaying: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    val scan = rememberScanState()
    val media = rememberMusicMedia(repository, scan.active)
    val tech by TrackTechIndex.tech.collectAsState()
    val probing by TrackTechIndex.remaining.collectAsState()
    val locale = context.resources.locale()
    val library = remember(media, tech, locale) {
        media?.let { list -> MusicLibrary(list.map { item -> item.toTrack(item.uri?.toString()?.let { tech[it] }) }, locale) }
    }
    val byUri = remember(media) { media.orEmpty().associateBy { it.uri?.toString().orEmpty() } }
    // S-808: the ratings and counts
    val stats by TrackStatsIndex.stats.collectAsState()
    LaunchedEffect(Unit) { TrackStatsIndex.follow(context) }
    // S-804: the tracks being put in a playlist, while its window is open; S-808: being rated
    var adding by remember { mutableStateOf<List<Track>?>(null) }
    var rating by remember { mutableStateOf<List<Track>?>(null) }
    val scope = rememberCoroutineScope()
    val actions = remember(context, byUri) {
        ServiceMusicActions(context, byUri, onAddToPlaylist = { tracks -> adding = tracks }, onAskRating = { tracks -> rating = tracks })
    }
    val state = rememberMusicBrowserState()
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.nag_nav_music),
            subtitle = library?.let { pluralStringResource(R.plurals.nag_tracks_count, it.tracks.size, it.tracks.size) },
        )
        LibraryContent(
            loading = library == null,
            empty = library?.tracks?.isEmpty() == true,
            scan = scan,
            emptyTitle = stringResource(R.string.nag_state_no_music_title),
            emptyDescription = stringResource(R.string.nag_state_no_music_desc),
            onAddFolder = onAddFolder,
        ) {
            if (library != null) MusicBrowser(library, state, actions, probing = probing, nowPlaying = nowPlaying, stats = stats)
        }
    }
    adding?.let { tracks -> AddToPlaylistHost(tracks, scope) { adding = null } }
    rating?.let { tracks ->
        RatingDialog(tracks, stats, onRate = { stars -> actions.rate(tracks, stars) }, onDismiss = { rating = null })
    }
}

/**
 * The medialibrary's music (or the picked folders'), read again while a scan finds more, the
 * engine's probe started on what is new: the music screen's, and the smart lists' (S-805).
 */
@Composable
fun rememberMusicMedia(repository: MediaRepository, scanning: Boolean): List<MediaWrapper>? {
    val context = LocalContext.current
    val access = LocalLibraryAccess.current
    val media by produceState<List<MediaWrapper>?>(null, scanning, access?.media, access?.folders) {
        loadWhileScanning(scanning) { repository.tracks() }
    }
    LaunchedEffect(media) {
        media?.let { list -> runCatching { TrackTechIndex.update(context, list.mapNotNull { it.uri?.toString() }) } }
    }
    return media
}

/** The medialibrary's item as the library shows it. */
fun MediaWrapper.toTrack(tech: TrackTech?): Track {
    val location = uri?.toString().orEmpty()
    val path = uri?.path.orEmpty()
    return Track(
        id = id,
        uri = location,
        title = title?.takeIf { it.isNotBlank() } ?: uri?.lastPathSegment.orEmpty(),
        artist = artistName,
        albumArtist = albumArtistName,
        album = albumName,
        genre = genre,
        year = releaseYear.takeIf { it > 0 },
        trackNumber = trackNumber,
        discNumber = discNumber,
        durationMs = length,
        folder = path.substringBeforeLast('/', ""),
        addedAt = insertionDate,
        tech = tech,
    )
}

/**
 * [MusicActions] on the playback service, through the medialibrary's items; with [onAddToPlaylist]
 * the tracks' menu has "Listeye ekle…", with [onAskRating] "Puan ver…" (S-808).
 */
internal class ServiceMusicActions(
    private val context: Context,
    /** The medialibrary's item of a track's uri. */
    private val lookup: (String) -> MediaWrapper?,
    private val onAddToPlaylist: ((List<Track>) -> Unit)? = null,
    private val onAskRating: ((List<Track>) -> Unit)? = null,
) : MusicActions {

    constructor(
        context: Context,
        byUri: Map<String, MediaWrapper>,
        onAddToPlaylist: ((List<Track>) -> Unit)? = null,
        onAskRating: ((List<Track>) -> Unit)? = null,
    ) : this(context, { uri: String -> byUri[uri] }, onAddToPlaylist, onAskRating)

    override val addToPlaylist: ((List<Track>) -> Unit)? get() = onAddToPlaylist

    override val askRating: ((List<Track>) -> Unit)? get() = onAskRating

    override fun rate(tracks: List<Track>, rating: Int) = tracks.forEach { TrackStatsIndex.rate(context, it.statsKey, rating) }

    private fun media(tracks: List<Track>) = tracks.mapNotNull { lookup(it.uri) }

    override fun play(tracks: List<Track>, index: Int) {
        val list = media(tracks)
        if (list.isNotEmpty()) PlaybackActions.playList(context, list, index.coerceIn(0, list.lastIndex))
    }

    override fun queue(tracks: List<Track>) = PlaybackActions.appendList(context, media(tracks))

    override fun playNext(tracks: List<Track>) = MediaUtils.insertNext(context, media(tracks).toTypedArray())

    override fun menu(track: Track, play: () -> Unit, select: (() -> Unit)?): List<MenuEntry> {
        val entries = lookup(track.uri)?.let { mediaMenu(context, it, play, select = select) }.orEmpty()
        // after "Sıraya ekle": "Listeye ekle…", "Puan ver…"
        val added = listOfNotNull(
            onAddToPlaylist?.let { add -> MenuEntry.Item(context.getString(R.string.nag_pl_add), { add(listOf(track)) }, icon = NagIcons.PlaylistAdd) },
            onAskRating?.let { ask -> MenuEntry.Item(context.getString(R.string.nag_rate), { ask(listOf(track)) }, icon = NagIcons.Star) },
        )
        if (added.isEmpty()) return entries
        val queueLabel = context.getString(R.string.nag_add_to_queue)
        val at = entries.indexOfFirst { it is MenuEntry.Item && it.label == queueLabel } + 1
        return if (at > 0) entries.subList(0, at) + added + entries.subList(at, entries.size) else entries + added
    }

    override fun rowModifier(track: Track, others: () -> List<Track>): Modifier =
        Modifier.onMiddleClick {
            queue(listOf(track))
            Toast.makeText(context, context.resources.getQuantityString(R.plurals.nag_drop_queued, 1, 1), Toast.LENGTH_SHORT).show()
        }.fileDragSource(
            label = {
                val many = others()
                if (many.size > 1) context.resources.getQuantityString(R.plurals.nag_drag_tracks, many.size, many.size) else track.title
            },
            files = { others().map { Uri.parse(it.uri) } },
        )
}

// ------------------------------------------------------------------ the browser

/**
 * S-803 and S-1001: the music library below its header. The ways in and the depth and rate
 * filter on top; then, by the window's width: a phone shows the groups, then the tracks of the
 * one opened (Back returns); a tablet the groups beside the tracks; a desktop window the groups,
 * the track table and [nowPlaying] side by side.
 */
@Composable
fun MusicBrowser(
    library: MusicLibrary,
    state: MusicBrowserState,
    actions: MusicActions,
    modifier: Modifier = Modifier,
    probing: Int = 0,
    nowPlaying: (@Composable () -> Unit)? = null,
    /** S-808: the ratings and counts, by track uri. */
    stats: Map<String, TrackStats> = emptyMap(),
) {
    val dimens = NagTheme.dimens
    val width = currentWidthClass()
    val groups = remember(library, state.view, state.filter) { library.groups(state.view, state.filter) }
    val grouped = state.view != MusicView.Tracks
    val wide = width >= WidthClass.Expanded
    // a wide window always shows a group; a narrow one shows the list until one is opened
    val open = if (!grouped) groups.firstOrNull() else groups.firstOrNull { it.key == state.group } ?: groups.firstOrNull().takeIf { wide }
    if (!wide && grouped && state.group != null) BackHandler { state.group = null }
    // "music": what the scroll benchmark (S-803) looks for
    Column(modifier.fillMaxSize().testTag("music")) {
        ViewChips(state)
        FilterRow(state, probing)
        when {
            groups.isEmpty() || (open != null && open.tracks.isEmpty()) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NoResultsState(
                    title = stringResource(R.string.nag_music_no_match),
                    suggestions = listOf(StateAction(stringResource(R.string.nag_clear_filters)) { state.filter = TechFilter() }),
                )
            }
            width >= WidthClass.Large -> Row(Modifier.fillMaxSize()) {
                if (grouped) {
                    GroupList(groups, state, open?.key, Modifier.width(260.dp).fillMaxHeight())
                    VerticalRule()
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { if (open != null) TrackPane(open, state, actions, showBack = false, stats) }
                if (nowPlaying != null) {
                    VerticalRule()
                    Box(Modifier.width(300.dp).fillMaxHeight()) { nowPlaying() }
                }
            }
            wide && grouped -> Row(Modifier.fillMaxSize()) {
                GroupList(groups, state, open?.key, Modifier.width(260.dp).fillMaxHeight())
                VerticalRule()
                Box(Modifier.weight(1f).fillMaxHeight()) { if (open != null) TrackPane(open, state, actions, showBack = false, stats) }
            }
            !grouped || open != null -> if (open != null) TrackPane(open, state, actions, showBack = grouped, stats)
            else -> GroupList(groups, state, null, Modifier.fillMaxSize().padding(horizontal = dimens.pagePadding - 12.dp))
        }
    }
}

/** The ways in, one at a time. */
@Composable
private fun ViewChips(state: MusicBrowserState) {
    val dimens = NagTheme.dimens
    ChipRow(contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapXs)) {
        MusicView.entries.forEach { view ->
            FilterChip(stringResource(viewLabel(view)), view == state.view, { state.open(view) })
        }
    }
}

/** The depths and the rates: none chosen lets every track through. */
@Composable
private fun FilterRow(state: MusicBrowserState, probing: Int) {
    val dimens = NagTheme.dimens
    val locale = LocalContext.current.resources.locale()
    val filter = state.filter
    ChipRow(contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapXs)) {
        FilterChip(stringResource(R.string.nag_filter_all), !filter.active, { state.filter = TechFilter() })
        Depth.entries.forEach { depth ->
            FilterChip(depthLabel(depth), depth in filter.depths, { on ->
                state.filter = filter.copy(depths = if (on) filter.depths + depth else filter.depths - depth)
            })
        }
        RateBand.entries.forEach { band ->
            FilterChip(rateLabel(band, locale), band in filter.rates, { on ->
                state.filter = filter.copy(rates = if (on) filter.rates + band else filter.rates - band)
            })
        }
    }
    if (probing > 0 && filter.active) {
        Text(
            pluralStringResource(R.plurals.nag_music_probing, probing, probing),
            style = NagTheme.type.labelSmall,
            color = NagTheme.colors.textTertiary,
            modifier = Modifier.padding(horizontal = dimens.pagePadding),
        )
    }
}

@Composable
internal fun depthLabel(depth: Depth): String = when (depth) {
    Depth.Bits16 -> stringResource(R.string.nag_sp_bits, 16)
    Depth.Bits24 -> stringResource(R.string.nag_sp_bits, 24)
    Depth.Bits32 -> stringResource(R.string.nag_sp_bits, 32)
    Depth.Lossy -> stringResource(R.string.nag_music_lossy)
    Depth.Dsd -> stringResource(R.string.nag_settings_dsd)
}

@Composable
private fun rateLabel(band: RateBand, locale: java.util.Locale): String =
    if (band == RateBand.R192) stringResource(R.string.nag_music_rate_and_up, kiloHertz(192_000, locale)) else kiloHertz(band.maxHz, locale)

private fun viewLabel(view: MusicView): Int = when (view) {
    MusicView.Tracks -> R.string.nag_music_view_tracks
    MusicView.Artists -> R.string.nag_music_view_artists
    MusicView.Albums -> R.string.nag_music_view_albums
    MusicView.Genres -> R.string.nag_music_view_genres
    MusicView.Composers -> R.string.nag_music_view_composers
    MusicView.Years -> R.string.nag_music_view_years
    MusicView.Folders -> R.string.nag_music_view_folders
}

/** A group's name, or what stands for it when the tag is missing. */
@Composable
private fun groupTitle(group: MusicGroup, view: MusicView): String = group.title ?: stringResource(
    when (view) {
        MusicView.Artists -> R.string.nag_unknown_artist
        MusicView.Albums -> R.string.nag_music_unknown_album
        MusicView.Genres -> R.string.nag_music_unknown_genre
        MusicView.Composers -> R.string.nag_music_unknown_composer
        MusicView.Years -> R.string.nag_music_unknown_year
        MusicView.Folders, MusicView.Tracks -> R.string.nag_music_unknown_folder
    },
)

/** "Ada Karan · 1977 · 9 parça", "4 albüm · 38 parça", "12 parça · 52:10". */
@Composable
private fun groupLine(group: MusicGroup, view: MusicView): String {
    val tracks = pluralStringResource(R.plurals.nag_tracks_count, group.tracks.size, group.tracks.size)
    val parts = when (view) {
        MusicView.Albums -> listOfNotNull(group.artist, group.year?.toString(), tracks)
        MusicView.Artists -> listOf(pluralStringResource(R.plurals.nag_music_albums, group.albumCount, group.albumCount), tracks)
        MusicView.Folders -> listOfNotNull(group.title?.let { path -> path.substringBeforeLast('/', "").ifEmpty { null } }, tracks)
        else -> listOf(tracks, formatDuration(group.durationMs))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun GroupList(groups: List<MusicGroup>, state: MusicBrowserState, openKey: String?, modifier: Modifier) {
    val listState = rememberLazyListState()
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
        items(groups, key = { it.key }) { group ->
            // the group shown beside, the first one on a wide window until another is opened
            GroupRow(group, state.view, selected = group.key == openKey) { state.group = group.key }
        }
    }
}

@Composable
private fun GroupRow(group: MusicGroup, view: MusicView, selected: Boolean, onOpen: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    // a folder by its last name; its path in the second line
    val title = if (view == MusicView.Folders) group.title?.substringAfterLast('/') ?: groupTitle(group, view) else groupTitle(group, view)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .heightIn(min = dimens.minTarget.coerceAtLeast(48.dp))
            .focusRing(state, shape)
            .background(stateBackground(state, selected), shape)
            .semantics { this.selected = selected }
            .nagClickable(interaction, onOpen)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = NagTheme.type.bodyStrong,
            color = if (selected) colors.onSelection else if (group.title == null) colors.textSecondary else colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            groupLine(group, view),
            style = NagTheme.type.labelSmall,
            color = if (selected) colors.onSelection else colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A group's tracks: its name, play and shuffle, the columns, the table. */
@Composable
private fun TrackPane(group: MusicGroup, state: MusicBrowserState, actions: MusicActions, showBack: Boolean, stats: Map<String, TrackStats>) {
    val dimens = NagTheme.dimens
    val locale = LocalContext.current.resources.locale()
    val compact = currentWidthClass() == WidthClass.Compact
    val pointer = NagTheme.inputMode == InputMode.Pointer
    // the whole library sorts by artist when nothing else is asked; a group plays in album order
    val sort = state.sort ?: if (state.view == MusicView.Tracks) MusicSort(MusicColumn.Artist, true) else null
    // sorted by a rating or a count, the order follows them; else a play counted does not resort
    val sortStats = if (sort?.column in STATS_COLUMNS) stats else emptyMap()
    val shown = remember(group, sort, locale, sortStats) { sortTracks(group.tracks, sort, locale, sortStats) }
    val columns = if (compact) listOf(MusicColumn.Title, MusicColumn.Artist, MusicColumn.Duration) else state.columns
    val tableColumns = rememberMusicColumns(columns, stats, onRate = if (actions.askRating != null) ({ track, stars -> actions.rate(listOf(track), stars) }) else null)
    val labels = MusicColumn.entries.associateWith { stringResource(it.label) }
    val selection = rememberMultiSelection()
    val modifiers = LocalClickModifiers.current
    val keys = remember(shown) { shown.map { it.uri } }
    LaunchedEffect(keys) { selection.retain(keys) }
    SelectionCommands(selection, keys)
    BackHandler(enabled = selection.selecting) { selection.clear() }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.pagePadding - 4.dp, vertical = dimens.gapXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_music_back), { state.group = null })
            if (state.view != MusicView.Tracks) {
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(groupTitle(group, state.view), style = NagTheme.type.title, color = NagTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(groupLine(group, state.view), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            NagButton(stringResource(R.string.nag_play), { actions.play(shown, 0) }, icon = NagIcons.Play, primary = true)
            Spacer(Modifier.width(dimens.gapS))
            NagButton(stringResource(R.string.nag_shuffle), { actions.play(shown.shuffled(), 0) }, icon = NagIcons.Shuffle)
            if (!compact) {
                Spacer(Modifier.width(dimens.gapS))
                MenuButton(stringResource(R.string.nag_music_columns), { columnMenu(state, labels) })
            }
        }
        if (selection.count > 1 || selection.selecting) {
            val chosen = shown.filter { it.uri in selection }
            SelectionBar(
                text = pluralStringResource(R.plurals.nag_selected_tracks, chosen.size, chosen.size),
                onClear = selection::clear,
                clearLabel = stringResource(R.string.nag_selection_clear),
                modifier = Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs),
            ) {
                NagButton(stringResource(R.string.nag_selection_play), { actions.play(chosen, 0) }, primary = true)
                NagButton(stringResource(R.string.nag_play_next), { actions.playNext(chosen) })
                NagButton(stringResource(R.string.nag_add_to_queue), { actions.queue(chosen) })
                actions.addToPlaylist?.let { add -> NagButton(stringResource(R.string.nag_pl_add), { add(chosen) }) }
                actions.askRating?.let { ask -> NagButton(stringResource(R.string.nag_rate), { ask(chosen) }) }
            }
        }
        HorizontalRule()
        val columnsLabel = stringResource(R.string.nag_music_columns)
        DenseTable(
            columns = tableColumns,
            rows = shown,
            key = { it.uri },
            selectedKeys = selection.keys,
            sort = sort?.let { s -> columns.indexOf(s.column).takeIf { it >= 0 }?.let { TableSort(it, s.ascending) } },
            onSortChange = { s -> state.sort = MusicSort(columns[s.column], s.ascending) },
            onRowClick = { track, index ->
                when {
                    selection.selecting -> selection.toggle(track.uri)
                    pointer -> {
                        val (ctrl, shift) = modifiers.take()
                        selection.click(track.uri, keys, ctrl, shift)
                    }
                    else -> actions.play(shown, index)
                }
            },
            onRowDoubleClick = if (pointer && !selection.selecting) ({ _, index -> actions.play(shown, index) }) else null,
            rowMenu = { track, index ->
                actions.menu(track, play = { actions.play(shown, index) }, select = if (pointer) null else ({ selection.startSelecting(track.uri) }))
            },
            rowMenuLabel = stringResource(R.string.nag_more_actions),
            rowModifier = { track ->
                actions.rowModifier(track) {
                    if (track.uri in selection && selection.count > 1) shown.filter { it.uri in selection } else listOf(track)
                }
            },
            headerMenu = if (compact) null else ({ columnMenu(state, labels) }),
            headerMenuLabel = columnsLabel,
        )
    }
}

/** S-808: the columns whose order the statistics decide. */
private val STATS_COLUMNS = setOf(MusicColumn.Rating, MusicColumn.Plays, MusicColumn.Skips, MusicColumn.LastPlayed)

/** The columns to show, each with its check; the title always. */
private fun columnMenu(state: MusicBrowserState, labels: Map<MusicColumn, String>): List<MenuEntry> =
    MusicColumn.entries.filter { it != MusicColumn.Title }.map { column ->
        MenuEntry.Item(labels.getValue(column), { state.toggle(column) }, checked = column in state.columns)
    }
