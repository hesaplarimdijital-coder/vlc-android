package app.nagmedar.ui.music

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.kiloHertz
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.LocalClickModifiers
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
import app.nagmedar.ui.convert.ConvertHost
import app.nagmedar.ui.covers.CoverMenuButton
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.covers.rememberCover
import app.nagmedar.ui.covers.rememberCoverMenu
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.input.fileDragSource
import app.nagmedar.ui.input.onMiddleClick
import app.nagmedar.ui.library.LibraryContent
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.library.LocalLibraryAccess
import app.nagmedar.ui.library.MediaRepository
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.library.loadWhileScanning
import app.nagmedar.ui.library.mediaMenu
import app.nagmedar.ui.library.rememberScanState
import app.nagmedar.ui.playback.PlayFrom
import app.nagmedar.ui.playback.PlaySource
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.stats.TrackStatsIndex
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.NAGMEDAR_MUSIC_COLUMNS
import org.videolan.tools.NAGMEDAR_MUSIC_VIEW
import org.videolan.tools.Settings
import org.videolan.vlc.media.MediaUtils
import org.videolan.tools.Notices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    /** A group to scroll the list to once (a player's artist or album link); null for none. */
    var reveal by mutableStateOf<String?>(null)

    /** A player's link: [view] with the group [key] open, the list scrolled to it, no filter hiding its tracks. */
    fun show(view: MusicView, key: String, clearFilter: Boolean) {
        open(view)
        if (clearFilter) filter = TechFilter()
        group = key
        reveal = key
    }

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

    /** [tracks] played as [from]'s (an album, an artist, a playlist), which the phone's player names; null: as [play]. */
    fun play(tracks: List<Track>, index: Int, from: PlaySource?) = play(tracks, index)

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

    /** S-1010: [tracks] converted to another format, in a window; null where there is no converter. */
    val convert: ((List<Track>) -> Unit)? get() = null
}

/** These actions with their plays coming from [source] (a group of the music view, a playlist); themselves for none. */
internal fun MusicActions.playingFrom(source: PlaySource?): MusicActions = if (source == null) this else SourcedMusicActions(this, source)

/** [base] whose plays say they come from [source]; everything else is [base]'s. */
private class SourcedMusicActions(private val base: MusicActions, private val source: PlaySource) : MusicActions by base {
    override fun play(tracks: List<Track>, index: Int) = base.play(tracks, index, source)

    override fun play(tracks: List<Track>, index: Int, from: PlaySource?) = base.play(tracks, index, from ?: source)
}

/** What the phone's player names for a group's tracks: an album or an artist by its name; none for the other views or a group without one. */
internal fun groupSource(view: MusicView, group: MusicGroup?): PlaySource? {
    val name = group?.title ?: return null
    return when (view) {
        MusicView.Albums -> PlaySource(PlayFrom.Album, name)
        MusicView.Artists -> PlaySource(PlayFrom.Artist, name)
        else -> null
    }
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
        // S-808: a CUE sheet's track has its file's technical side
        media?.let { list -> MusicLibrary(list.map { item -> item.toTrack(item.uri?.toString()?.let { tech[CueUri.file(it)] }) }, locale) }
    }
    val byUri = remember(media) { media.orEmpty().associateBy { it.uri?.toString().orEmpty() } }
    // S-808: the ratings and counts
    val stats by TrackStatsIndex.stats.collectAsState()
    LaunchedEffect(Unit) { TrackStatsIndex.follow(context) }
    // S-804: the tracks being put in a playlist, while its window is open; S-808: being rated
    var adding by remember { mutableStateOf<List<Track>?>(null) }
    var rating by remember { mutableStateOf<List<Track>?>(null) }
    // S-1010: the tracks being converted, while the converter's window is open
    var converting by remember { mutableStateOf<List<Track>?>(null) }
    val scope = rememberCoroutineScope()
    val actions = remember(context, byUri) {
        ServiceMusicActions(
            context, byUri,
            onAddToPlaylist = { tracks -> adding = tracks },
            onAskRating = { tracks -> rating = tracks },
            onConvert = { tracks -> converting = tracks },
        )
    }
    val state = rememberMusicBrowserState()
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.nag_nav_music),
            subtitle = library?.let { pluralStringResource(R.plurals.nag_tracks_count, it.tracks.size, it.tracks.size) },
        )
        // S-1010: the converter's job under way, or what the last one came to
        ConvertHost(
            converting,
            onConverted = { converting = null },
            modifier = Modifier.padding(horizontal = NagTheme.dimens.pagePadding, vertical = NagTheme.dimens.gapXs),
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
    // the music of the section's folders (Settings › Library)
    val media by produceState<List<MediaWrapper>?>(null, scanning, access?.media, access?.folders, access?.scopes) {
        loadWhileScanning(scanning) { repository.tracks(LibrarySection.Music) }
    }
    LaunchedEffect(media) {
        // S-808: a CUE sheet's tracks share their file, probed once
        media?.let { list -> runCatching { TrackTechIndex.update(context, list.mapNotNull { it.uri?.toString()?.let(CueUri::file) }.distinct()) } }
    }
    return media
}

/** S-1006: an album's cover in its header; a click (a right click, a long press) opens its menu. */
@Composable
private fun AlbumCover(group: MusicGroup, modifier: Modifier = Modifier) {
    val track = remember(group.key) { group.tracks.firstOrNull()?.let(CoverTrack::of) }
    val side = 48.dp
    val px = with(LocalDensity.current) { side.roundToPx() }
    CoverMenuButton(rememberCover(track, px), side, stringResource(R.string.nag_cover_menu), rememberCoverMenu(track), modifier)
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
    private val onConvert: ((List<Track>) -> Unit)? = null,
) : MusicActions {

    constructor(
        context: Context,
        byUri: Map<String, MediaWrapper>,
        onAddToPlaylist: ((List<Track>) -> Unit)? = null,
        onAskRating: ((List<Track>) -> Unit)? = null,
        onConvert: ((List<Track>) -> Unit)? = null,
    ) : this(context, { uri: String -> byUri[uri] }, onAddToPlaylist, onAskRating, onConvert)

    override val addToPlaylist: ((List<Track>) -> Unit)? get() = onAddToPlaylist

    override val askRating: ((List<Track>) -> Unit)? get() = onAskRating

    override val convert: ((List<Track>) -> Unit)? get() = onConvert

    override fun rate(tracks: List<Track>, rating: Int) = tracks.forEach { TrackStatsIndex.rate(context, it.statsKey, rating) }

    private fun media(tracks: List<Track>) = tracks.mapNotNull { lookup(it.uri) }

    override fun play(tracks: List<Track>, index: Int) = play(tracks, index, null)

    override fun play(tracks: List<Track>, index: Int, from: PlaySource?) {
        val list = media(tracks)
        if (list.isNotEmpty()) PlaybackActions.playList(context, list, index.coerceIn(0, list.lastIndex), from)
    }

    override fun queue(tracks: List<Track>) = PlaybackActions.appendList(context, media(tracks))

    override fun playNext(tracks: List<Track>) = MediaUtils.insertNext(context, media(tracks).toTypedArray())

    override fun menu(track: Track, play: () -> Unit, select: (() -> Unit)?): List<MenuEntry> {
        val entries = lookup(track.uri)?.let { mediaMenu(context, it, play, select = select) }.orEmpty()
        // after "Sıraya ekle": "Listeye ekle…", "Puan ver…", "Dönüştür…"
        val added = listOfNotNull(
            onAddToPlaylist?.let { add -> MenuEntry.Item(context.getString(R.string.nag_pl_add), { add(listOf(track)) }, icon = NagIcons.PlaylistAdd) },
            onAskRating?.let { ask -> MenuEntry.Item(context.getString(R.string.nag_rate), { ask(listOf(track)) }, icon = NagIcons.Star) },
            onConvert?.let { convert -> MenuEntry.Item(context.getString(R.string.nag_convert), { convert(listOf(track)) }, icon = NagIcons.Convert) },
        )
        if (added.isEmpty()) return entries
        val queueLabel = context.getString(R.string.nag_add_to_queue)
        val at = entries.indexOfFirst { it is MenuEntry.Item && it.label == queueLabel } + 1
        return if (at > 0) entries.subList(0, at) + added + entries.subList(at, entries.size) else entries + added
    }

    override fun rowModifier(track: Track, others: () -> List<Track>): Modifier =
        Modifier.onMiddleClick {
            queue(listOf(track))
            Notices.show(context, context.resources.getQuantityString(R.plurals.nag_drop_queued, 1, 1))
        }.fileDragSource(
            label = {
                val many = others()
                if (many.size > 1) context.resources.getQuantityString(R.plurals.nag_drag_tracks, many.size, many.size) else track.title
            },
            // S-808: a CUE sheet's track is dragged as its file
            files = { others().map { Uri.parse(CueUri.file(it.uri)) } },
        )
}

// ------------------------------------------------------------------ the browser

/**
 * S-803 and S-1001: the music library below its header. The toolbar on top (the ways in, Play,
 * Shuffle, the columns and the filter); then, by the window's width: a phone shows the groups, then the tracks of the
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
    // the toolbar's Play and Shuffle take the open group's tracks as the table shows them
    val locale = LocalContext.current.resources.locale()
    val sort = effectiveSort(state)
    val sortStats = if (sort?.column in STATS_COLUMNS) stats else emptyMap()
    val shown = remember(open, sort, locale, sortStats) { open?.let { sortTracks(it.tracks, sort, locale, sortStats) }.orEmpty() }
    // a player's artist or album link: its group, wherever the library was
    val context = LocalContext.current
    val link = MusicLinks.pending
    LaunchedEffect(link, library) {
        val asked = link ?: return@LaunchedEffect
        val filter = state.filter
        // the groups are sorted with the reader's collator: off the main thread for a large library
        val (key, hidden) = withContext(Dispatchers.Default) {
            val all = library.groups(asked.view)
            val found = asked.keys.firstOrNull { k -> all.any { it.key == k } }
            found to (found != null && filter.active && library.groups(asked.view, filter).none { it.key == found })
        }
        MusicLinks.taken(asked)
        if (key == null) {
            Notices.show(context, R.string.nag_music_link_missing)
            return@LaunchedEffect
        }
        state.show(asked.view, key, clearFilter = hidden)
    }
    // the open group's tracks play from it: an album's or an artist's, as the phone's player names it
    val openActions = remember(actions, state.view, open?.title) { actions.playingFrom(groupSource(state.view, open)) }
    // "music": what the scroll benchmark (S-803) looks for
    Column(modifier.fillMaxSize().testTag("music")) {
        MusicToolbar(state, openActions, shown, showColumns = width != WidthClass.Compact, probing = probing)
        when {
            groups.isEmpty() || (open != null && open.tracks.isEmpty()) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NoResultsState(
                    title = stringResource(R.string.nag_music_no_match),
                    // the files still being read may yet fit
                    detail = if (probing > 0 && state.filter.active) pluralStringResource(R.plurals.nag_music_probing, probing, probing) else null,
                    suggestions = listOf(StateAction(stringResource(R.string.nag_clear_filters)) { state.filter = TechFilter() }),
                )
            }
            width >= WidthClass.Large -> Row(Modifier.fillMaxSize()) {
                if (grouped) {
                    GroupList(groups, state, actions, open?.key, Modifier.width(260.dp).fillMaxHeight())
                    VerticalRule()
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { if (open != null) TrackPane(open, shown, state, openActions, showBack = false, stats) }
                if (nowPlaying != null) {
                    VerticalRule()
                    Box(Modifier.width(300.dp).fillMaxHeight()) { nowPlaying() }
                }
            }
            wide && grouped -> Row(Modifier.fillMaxSize()) {
                GroupList(groups, state, actions, open?.key, Modifier.width(260.dp).fillMaxHeight())
                VerticalRule()
                Box(Modifier.weight(1f).fillMaxHeight()) { if (open != null) TrackPane(open, shown, state, openActions, showBack = false, stats) }
            }
            !grouped || open != null -> if (open != null) TrackPane(open, shown, state, openActions, showBack = grouped, stats)
            else -> GroupList(groups, state, actions, null, Modifier.fillMaxSize().padding(horizontal = dimens.pagePadding - 12.dp))
        }
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
internal fun rateLabel(band: RateBand, locale: java.util.Locale): String =
    if (band == RateBand.R192) stringResource(R.string.nag_music_rate_and_up, kiloHertz(192_000, locale)) else kiloHertz(band.maxHz, locale)

internal fun viewLabel(view: MusicView): Int = when (view) {
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
private fun GroupList(groups: List<MusicGroup>, state: MusicBrowserState, actions: MusicActions, openKey: String?, modifier: Modifier) {
    val listState = rememberLazyListState()
    val res = LocalContext.current.resources
    LaunchedEffect(state.reveal, groups) {
        val key = state.reveal ?: return@LaunchedEffect
        val index = groups.indexOfFirst { it.key == key }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            state.reveal = null
        }
    }
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
        items(groups, key = { it.key }) { group ->
            // the group shown beside, the first one on a wide window until another is opened
            // the menu plays the group's tracks, as the table's rows do one track
            ContextMenuArea(entries = { groupMenu(res, group, actions.playingFrom(groupSource(state.view, group))) }, accessibilityLabel = stringResource(R.string.nag_more_actions)) {
                GroupRow(group, state.view, selected = group.key == openKey) { state.group = group.key }
            }
        }
    }
}

/** An album's, an artist's or a folder's menu (right click, long press): play, queue, convert. */
private fun groupMenu(res: android.content.res.Resources, group: MusicGroup, actions: MusicActions): List<MenuEntry> = listOfNotNull(
    MenuEntry.Item(res.getString(R.string.nag_play), { actions.play(group.tracks, 0) }, icon = NagIcons.Play),
    MenuEntry.Item(res.getString(R.string.nag_play_next), { actions.playNext(group.tracks) }, icon = NagIcons.Next),
    MenuEntry.Item(res.getString(R.string.nag_add_to_queue), { actions.queue(group.tracks) }, icon = NagIcons.PlaylistAdd),
    actions.addToPlaylist?.let { add -> MenuEntry.Item(res.getString(R.string.nag_pl_add), { add(group.tracks) }, icon = NagIcons.PlaylistAdd) },
    actions.convert?.let { convert -> MenuEntry.Item(res.getString(R.string.nag_convert), { convert(group.tracks) }, icon = NagIcons.Convert) },
)

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

/** A group's tracks, [shown] in the order the table has: its name and the way back, then the table. */
@Composable
private fun TrackPane(group: MusicGroup, shown: List<Track>, state: MusicBrowserState, actions: MusicActions, showBack: Boolean, stats: Map<String, TrackStats>) {
    val dimens = NagTheme.dimens
    val compact = currentWidthClass() == WidthClass.Compact
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val sort = effectiveSort(state)
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
        // a group's name and line; the way back on a phone; an album's cover; the group's conversion
        if (state.view != MusicView.Tracks) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.pagePadding - 4.dp, vertical = dimens.gapXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBack) NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_music_back), { state.group = null })
                // S-1006: an album's cover, its menu to change it
                if (state.view == MusicView.Albums) AlbumCover(group, Modifier.padding(start = 4.dp, end = 4.dp))
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(groupTitle(group, state.view), style = NagTheme.type.title, color = NagTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(groupLine(group, state.view), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // S-1010: an album's, an artist's, a folder's tracks converted
                actions.convert?.let { convert ->
                    NagIconButton(NagIcons.Convert, stringResource(R.string.nag_convert), { convert(shown) }, modifier = Modifier.testTag("music-convert"))
                }
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
                actions.convert?.let { convert -> NagButton(stringResource(R.string.nag_convert), { convert(chosen) }) }
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

/** The table's sort: the whole library by artist when nothing else is asked; a group plays in album order. */
private fun effectiveSort(state: MusicBrowserState): MusicSort? =
    state.sort ?: if (state.view == MusicView.Tracks) MusicSort(MusicColumn.Artist, true) else null

/** S-808: the columns whose order the statistics decide; sorted by one, the order follows the ratings and counts. */
private val STATS_COLUMNS = setOf(MusicColumn.Rating, MusicColumn.Plays, MusicColumn.Skips, MusicColumn.LastPlayed)

/** The columns to show, each with its check; the title always. */
internal fun columnMenu(state: MusicBrowserState, labels: Map<MusicColumn, String>): List<MenuEntry> =
    MusicColumn.entries.filter { it != MusicColumn.Title }.map { column ->
        MenuEntry.Item(labels.getValue(column), { state.toggle(column) }, checked = column in state.columns)
    }
