package app.nagmedar.ui.music

import app.nagmedar.ui.library.ScanStrip
import app.nagmedar.ui.shell.LocalWindowRootInsets
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.CompositionLocalProvider
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.kiloHertz
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.ClearSelectionOnBack
import app.nagmedar.ui.components.CollapsingTop
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.HorizontalSplitPane
import app.nagmedar.ui.components.InspectorCommands
import app.nagmedar.ui.components.InspectorState
import app.nagmedar.ui.components.InspectorToggleButton
import app.nagmedar.ui.components.LocalClickModifiers
import app.nagmedar.ui.components.rememberClickPlays
import app.nagmedar.ui.components.trackClick
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MoreButton
import app.nagmedar.ui.components.MultiSelection
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.SelectionBar
import app.nagmedar.ui.components.SelectionCommands
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.TableSort
import app.nagmedar.ui.components.WorkstationLayout
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.listAppear
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.rememberCollapsingTopState
import app.nagmedar.ui.components.rememberInspectorState
import app.nagmedar.ui.components.rememberListAppear
import app.nagmedar.ui.components.rememberMultiSelection
import app.nagmedar.ui.components.rememberSplitState
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.toActionGroups
import app.nagmedar.ui.convert.ConvertHost
import app.nagmedar.ui.covers.CoverMenuButton
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.covers.rememberCover
import app.nagmedar.ui.covers.rememberCoverMenu
import app.nagmedar.ui.device.FileReveal
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.input.fileDragSource
import app.nagmedar.ui.input.onMiddleClick
import app.nagmedar.ui.library.LibraryContent
import app.nagmedar.ui.library.LocalLibraryAccess
import app.nagmedar.ui.library.MediaFile
import app.nagmedar.ui.library.MediaRepository
import app.nagmedar.ui.library.MediaSharing
import app.nagmedar.ui.library.MusicRead
import app.nagmedar.ui.library.MusicSnapshot
import app.nagmedar.ui.library.RecentKept
import app.nagmedar.ui.library.ScanState
import app.nagmedar.ui.library.ShelfTitle
import app.nagmedar.ui.library.TrackCard
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.library.mediaMenu
import app.nagmedar.ui.library.rememberRead
import app.nagmedar.ui.library.rememberScanState
import app.nagmedar.ui.perf.StartupPerf
import app.nagmedar.ui.playback.PlayFrom
import app.nagmedar.ui.playback.PlaySource
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.DetailTransition
import app.nagmedar.ui.shell.HeightClass
import app.nagmedar.ui.shell.LocalHeightClass
import app.nagmedar.ui.shell.LocalShellBottomPadding
import app.nagmedar.ui.shell.MUSIC_GROUPS_PANE
import app.nagmedar.ui.shell.MovableDetails
import app.nagmedar.ui.shell.OnReselect
import app.nagmedar.ui.shell.PlayerAside
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.shell.plus
import app.nagmedar.ui.shell.rememberMovableDetails
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.stats.TrackStatsIndex
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.NAGMEDAR_MUSIC_COLUMNS
import org.videolan.tools.NAGMEDAR_MUSIC_LAYOUT
import org.videolan.tools.NAGMEDAR_MUSIC_VIEW
import org.videolan.tools.Notices
import org.videolan.tools.Settings
import org.videolan.vlc.media.MediaUtils

/**
 * S-803: where the music library is: the way in (tracks, artists, albums, genres, composers,
 * years, folders), the group open in it, the depth and rate filter, and the table's sort and
 * columns (S-1001). The way in and the columns are kept in the settings through [keep]; Desktop
 * Kit 1.0: each way in has columns of its own, in the order the user put them, read with
 * [columnsOf] when it opens.
 */
@Stable
class MusicBrowserState(
    view: MusicView = MusicView.Tracks,
    columns: List<MusicColumn> = MusicColumn.defaultFor(view),
    group: String? = null,
    filter: TechFilter = TechFilter(),
    sort: MusicSort? = null,
    private val keep: (MusicView, List<MusicColumn>) -> Unit = { _, _ -> },
    private val columnsOf: ((MusicView) -> List<MusicColumn>)? = null,
    layout: MusicLayout = MusicLayout.Grid,
    quick: QuickFilter = QuickFilter.All,
    private val keepLayout: (MusicLayout) -> Unit = {},
) {
    var view by mutableStateOf(view)
        private set

    /** 1.1.0 (Ebru): the groups as covers or as rows ([keepLayout] keeps it in the settings). */
    var layout by mutableStateOf(layout)
        private set

    /** 1.1.0 (Ebru): the quick filter over the groups; not kept. */
    var quick by mutableStateOf(quick)

    /** 1.1.1 (the design's "A–Z" button): the groups' order; null for the way in's own ([GroupSort.default]). */
    var groupSort by mutableStateOf<GroupSort?>(null)

    fun changeLayout(next: MusicLayout) {
        if (next == layout) return
        layout = next
        keepLayout(next)
    }

    /** The group open in [view]; null for none (the first one on a wide window). */
    var group by mutableStateOf(group)

    var filter by mutableStateOf(filter)

    var sort by mutableStateOf(sort)

    var columns by mutableStateOf(columns)
        private set

    /** A group to scroll the list to once (a player's artist or album link); null for none. */
    var reveal by mutableStateOf<String?>(null)

    /**
     * A player's link: [view] with the group [key] open, the list scrolled to it, no filter hiding its tracks
     * ([clearFilter]) nor the group (1.1.0: [clearQuick], the quick filter).
     */
    fun show(view: MusicView, key: String, clearFilter: Boolean, clearQuick: Boolean = false) {
        open(view)
        if (clearFilter) filter = TechFilter()
        if (clearQuick) quick = QuickFilter.All
        group = key
        reveal = key
    }

    fun open(next: MusicView) {
        if (next == view) return
        view = next
        group = null
        sort = null
        groupSort = null
        columnsOf?.invoke(next)?.let { columns = it }
        keep(view, columns)
    }

    /** Shows or hides [column]; the title stays. */
    fun toggle(column: MusicColumn) {
        if (column == MusicColumn.Title) return
        columns = MusicColumns.toggle(columns, column)
        if (sort?.column == column && column !in columns) sort = null
        keep(view, columns)
    }

    /** Desktop Kit 1.0: [column] moved [delta] places, towards the start when negative. */
    fun move(column: MusicColumn, delta: Int) {
        columns = MusicColumns.move(columns, column, delta)
        keep(view, columns)
    }

    /** The table's own columns again (1.1.2: an album's own, the design's). */
    fun resetColumns() {
        columns = MusicColumn.defaultFor(view)
        if (sort?.column !in columns) sort = null
        keep(view, columns)
    }

    companion object {
        fun saver(
            keep: (MusicView, List<MusicColumn>) -> Unit,
            columnsOf: ((MusicView) -> List<MusicColumn>)? = null,
            keepLayout: (MusicLayout) -> Unit = {},
        ) = Saver<MusicBrowserState, List<String>>(
            save = {
                listOf(
                    it.view.name, MusicColumn.format(it.columns), it.group.orEmpty(),
                    it.filter.depths.joinToString(",") { d -> d.name }, it.filter.rates.joinToString(",") { r -> r.name },
                    it.sort?.let { s -> "${s.column.name}:${s.ascending}" }.orEmpty(),
                    it.layout.name, it.quick.name,
                    it.groupSort?.let { g -> "${g.order.name}:${g.ascending}" }.orEmpty(),
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
                    columnsOf = columnsOf,
                    layout = MusicLayout.entries.firstOrNull { it.name == saved.getOrNull(6) } ?: MusicLayout.Grid,
                    quick = QuickFilter.entries.firstOrNull { it.name == saved.getOrNull(7) } ?: QuickFilter.All,
                    keepLayout = keepLayout,
                ).also { state ->
                    state.groupSort = saved.getOrNull(8)?.takeIf { it.isNotEmpty() }?.let { g ->
                        GroupOrder.entries.firstOrNull { it.name == g.substringBefore(':') }?.let { GroupSort(it, g.substringAfter(':') == "true") }
                    }
                }
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
            settings.edit().putString(NAGMEDAR_MUSIC_VIEW, view.name.lowercase()).putString(columnsKey(view), MusicColumn.format(columns)).apply()
        }
    }
    // a way in whose columns were never chosen takes those kept before there was one set per way in
    // 1.1.2 (the design's tablet page): an album's own are No, Ad, Süre, Çalınma, Biçim
    val columnsOf: (MusicView) -> List<MusicColumn> = remember(settings) {
        { view ->
            (settings.getString(columnsKey(view), null) ?: settings.getString(NAGMEDAR_MUSIC_COLUMNS, null))?.let { MusicColumn.parse(it) }
                ?: MusicColumn.defaultFor(view)
        }
    }
    val keepLayout: (MusicLayout) -> Unit = remember(settings) {
        { layout -> settings.edit().putString(NAGMEDAR_MUSIC_LAYOUT, layout.name.lowercase()).apply() }
    }
    return rememberSaveable(saver = MusicBrowserState.saver(keep, columnsOf, keepLayout)) {
        // 1.1.1 (the design): the albums until another way in was chosen
        val view = MusicView.entries.firstOrNull { it.name.lowercase() == settings.getString(NAGMEDAR_MUSIC_VIEW, null) } ?: MusicView.Albums
        // 1.1.0 (Ebru): the covers unless the rows were chosen
        val layout = if (settings.getString(NAGMEDAR_MUSIC_LAYOUT, null) == MusicLayout.List.name.lowercase()) MusicLayout.List else MusicLayout.Grid
        MusicBrowserState(view = view, columns = columnsOf(view), keep = keep, columnsOf = columnsOf, layout = layout, keepLayout = keepLayout)
    }
}

/** Desktop Kit 1.0: where a way in's columns are kept ("nagmedar_music_columns_albums"). */
internal fun columnsKey(view: MusicView): String = NAGMEDAR_MUSIC_COLUMNS + "_" + view.name.lowercase(Locale.ROOT)

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

    /**
     * A track dragged out with the mouse, with [others] when it is among those selected (S-406). 1.0.8: where [atOnce]
     * says so at the press (a selected row) a mouse drags without resting first.
     */
    fun rowModifier(track: Track, atOnce: () -> Boolean = { false }, others: () -> List<Track>): Modifier = Modifier

    /** S-804: [tracks] to a playlist, chosen or made in a window; null where there is none. */
    val addToPlaylist: ((List<Track>) -> Unit)? get() = null

    /** S-808: [tracks]' rating, 0 (none) to 5 stars. */
    fun rate(tracks: List<Track>, rating: Int) = Unit

    /** S-808: the rating window for [tracks]; null where ratings are not kept. */
    val askRating: ((List<Track>) -> Unit)? get() = null

    /** S-1010: [tracks] converted to another format, in a window; null where there is no converter. */
    val convert: ((List<Track>) -> Unit)? get() = null

    /** [tracks]' files to another app, with the system's share sheet; null where they cannot be. */
    val share: ((List<Track>) -> Unit)? get() = null

    /** [track]'s file in its folder ("Dosyada göster"); null where it cannot be. */
    val reveal: ((Track) -> Unit)? get() = null
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

/**
 * S-803: the music library, from the medialibrary (or the picked folders) and the engine's probe. Desktop Kit 1.0: on
 * tablet and DeX windows [inspector]'s tabs stand on the right of the table, shown and hidden from the header. 1.1.1
 * (the design's phone page): on a phone the page is the design's ([PhoneMusicPage]), [onSearch] its search chip.
 */
@Composable
fun MusicScreen(repository: MediaRepository, onAddFolder: () -> Unit, inspector: MusicInspectorContent? = null, onSearch: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scan = rememberScanState()
    val read = rememberMusicRead(repository)
    val media = read?.media
    // 1.0: the tracks played last (the playback history, newest first): the shelf on a phone, the Recent view on a wide
    // window. Read on its own, off the main thread, again whenever the history changes while the screen is open; never
    // part of the library, which a new history would otherwise make again
    val access = LocalLibraryAccess.current
    // 1.0.3: with the kept list on screen in the first frames, the history's read would come after the rows: the tracks
    // played last are kept too ([RecentKept]) and found in the kept list, so the shelf is there with it from the start
    val recent = rememberRead<List<MediaWrapper>>(
        repository.lastRead, "recent", access?.media, access?.folders, access?.scopes,
        kept = { RecentKept.cached()?.let { ids -> MusicSnapshot.cached(repository.musicScope())?.let { RecentKept.tracks(ids, it.media) } } },
    ) {
        // the first frame came before the kept files were read: the kept shelf as soon as they are
        if (value == null) repository.keptRecent()?.let { value = it }
        suspend fun load() {
            val fresh = repository.recentAudio()
            value = fresh
            repository.keepRecent(fresh)
        }
        load()
        repository.historyChanges().collect { load() }
    }
    val compact = currentWidthClass() == WidthClass.Compact
    // on a phone the shelf is the list's first item, scrolling away with it; it alone reads (and follows) the history.
    // 1.1.0 (Ebru): the band over it, in the inks of what plays (the bozkır's while nothing does); across the page's top on
    // a wide window
    val inks = rememberEbruInks(rememberNowPlaying().media)
    // 1.1.1 (the design's phone page): the recently played are the phone's last tab too
    val recentKeys = recent.value.orEmpty().let { list -> remember(list) { list.mapNotNull { it.uri?.toString() } } }
    val tech by TrackTechIndex.tech.collectAsState()
    val probing by TrackTechIndex.remaining.collectAsState()
    val locale = context.resources.locale()
    val library = remember(read, tech, locale) {
        // S-808: a CUE sheet's track has its file's technical side; made again only for new tracks or a new probe,
        // not in the first frame of a screen that comes back (1.0.3: compared by what the tracks show, their tags too)
        read?.let { r ->
            repository.lastRead.derived("music", listOf(r, tech, locale)) {
                MusicLibrary(r.media.map { item -> item.toTrack(item.uri?.toString()?.let { tech[CueUri.file(it)] }) }, locale)
            }
        }
    }
    val byUri = remember(read) { media.orEmpty().associateBy { it.uri?.toString().orEmpty() } }
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
    // 1.1.2 (the design's tablet page): the panel 376 dp wide until it is dragged
    val inspectorState = rememberInspectorState(defaultWidth = MUSIC_PANEL_WIDTH)
    // 1.1.0 (Ebru): the counts under the masthead: tracks and albums, as the Albums view has them (tracks without an
    // album's name are no album); the albums once they are grouped, off the main thread the first time. 1.1.1 (the
    // design): the artists too
    val groupCounts by produceState(
        library?.let { known -> known.madeGroups(MusicView.Albums)?.let { a -> known.madeGroups(MusicView.Artists)?.let { r -> named(a) to named(r) } } },
        library,
    ) {
        val known = library ?: return@produceState
        value = withContext(Dispatchers.Default) { named(known.groups(MusicView.Albums)) to named(known.groups(MusicView.Artists)) }
    }
    val counts = library?.let { known ->
        val tracks = pluralStringResource(R.plurals.nag_tracks_count, known.tracks.size, known.tracks.size)
        groupCounts?.let { (albums, artists) ->
            listOf(
                tracks,
                pluralStringResource(R.plurals.nag_music_albums, albums, albums),
                pluralStringResource(R.plurals.nag_music_artists_count, artists, artists),
            ).joinToString(" · ")
        } ?: tracks
    }
    if (compact) {
        PhoneMusicPage(library, scan, state, actions, inks, counts, probing, stats, recentKeys, converting, onConverted = { converting = null }, onAddFolder, onSearch)
        adding?.let { tracks -> AddToPlaylistHost(tracks, scope) { adding = null } }
        rating?.let { tracks ->
            RatingDialog(tracks, stats, onRate = { stars -> actions.rate(tracks, stars) }, onDismiss = { rating = null })
        }
        return
    }
    // 1.1.2 (the design's tablet page): the page as the design draws it on a tablet's and a DeX window
    WideMusicPage(
        library, scan, state, actions, inks, counts, probing, stats, recentKeys, converting, onConverted = { converting = null },
        onAddFolder, onSearch, inspector, inspectorState,
    )
    adding?.let { tracks -> AddToPlaylistHost(tracks, scope) { adding = null } }
    rating?.let { tracks ->
        RatingDialog(tracks, stats, onRate = { stars -> actions.rate(tracks, stars) }, onDismiss = { rating = null })
    }
}

/**
 * 1.1.2 (the design's tablet page, Tablet.dc.html): the Music page of a tablet's or a DeX window. The band across its
 * top, the design's tall one on a tablet's window (none on a window as short as a phone on its side), the search field
 * over it ([onSearch]; with a pointer the menu bar has its own); the masthead with [counts] and how long the library
 * plays, Oynat and Karıştır; the tabs, the toolbar and the covers or the rows under it, [inspector]'s panel beside it all.
 * Until there is a list, the band and the masthead over the loading or empty state.
 */
@Composable
internal fun WideMusicPage(
    library: MusicLibrary?,
    scan: ScanState,
    state: MusicBrowserState,
    actions: MusicActions,
    inks: EbruInks,
    counts: String?,
    probing: Int,
    stats: Map<String, TrackStats>,
    recent: List<String>,
    converting: List<Track>?,
    onConverted: () -> Unit,
    onAddFolder: () -> Unit,
    onSearch: (() -> Unit)?,
    inspector: MusicInspectorContent?,
    inspectorState: InspectorState,
) {
    val short = LocalHeightClass.current == HeightClass.Compact
    val band = wideBandHeight(LocalConfiguration.current.screenHeightDp, short)
    val bandSearch = onSearch.takeIf { NagTheme.inputMode != InputMode.Pointer }
    val length = library?.let { known -> remember(known) { known.tracks.sumOf { it.durationMs.coerceAtLeast(0) } } }
    val wideCounts = listOfNotNull(counts, length?.let { libraryLength(it) }).joinToString(" · ").ifEmpty { null }
    val listed = library?.tracks?.isNotEmpty() == true
    val header: @Composable (play: (() -> Unit)?, shuffle: (() -> Unit)?) -> Unit = { play, shuffle ->
        WideMusicTop(
            inks, band, bandSearch, wideCounts, play, shuffle,
            // the panel's button while it is hidden (its own menu hides it; Ctrl+I both); below the docking width it
            // comes up as a sheet (1.0.8). 1.1.3: in a window too short for the band (a phone on its side) the band's
            // search field's place before it
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (band == 0.dp && bandSearch != null) {
                        NagIconButton(NagIcons.Search, stringResource(R.string.nag_search), bandSearch, modifier = Modifier.testTag("music-search"))
                    }
                    if (inspector != null && inspectorState.hidden) InspectorToggleButton(inspectorState)
                }
            },
            below = {
                // a scan going on; S-1010: the converter's job under way, or what the last one came to
                if (scan.active && listed) ScanStrip(scan, Modifier.padding(horizontal = WIDE_MARGIN, vertical = 4.dp))
                ConvertHost(converting, onConverted = onConverted, modifier = Modifier.padding(horizontal = WIDE_MARGIN, vertical = NagTheme.dimens.gapXs))
            },
        )
    }
    if (library == null || !listed) {
        Column(Modifier.fillMaxSize().testTag("music-page")) {
            header(null, null)
            Box(Modifier.weight(1f)) { MusicContent(library, scan, onAddFolder) {} }
        }
    } else {
        MusicBrowser(
            library, state, actions, Modifier.fillMaxSize(), probing = probing, inspector = inspector, inspectorState = inspectorState,
            stats = stats, recent = recent, wideHeader = header,
        )
    }
}

/** How many of [groups] have a name (tracks without an album's name are no album). */
private fun named(groups: List<MusicGroup>): Int = groups.count { it.title != null }

/**
 * 1.1.1 (the design's phone page, Main.dc.html): the Music page of a phone. Its list starts at the window's top: the
 * header ([PhoneMusicHeader]: the band under the status bar, the masthead with [counts], the tabs, the toolbar) is its
 * first item; the chips stay over it ([PhoneMusicChips]: the brand, or the way back from a group's page; the search;
 * the options with the quick filters, the filter and the folders). Until there is a list, the band and the masthead
 * over the loading or empty state.
 */
@Composable
internal fun PhoneMusicPage(
    library: MusicLibrary?,
    scan: ScanState,
    state: MusicBrowserState,
    actions: MusicActions,
    inks: EbruInks,
    counts: String?,
    probing: Int,
    stats: Map<String, TrackStats>,
    recent: List<String>,
    converting: List<Track>?,
    onConverted: () -> Unit,
    onAddFolder: () -> Unit,
    onSearch: (() -> Unit)?,
) {
    val phoneTop = remember { PhoneTop() }
    val density = LocalDensity.current
    val statusTop = with(density) { (LocalWindowRootInsets.current?.top?.getTop(this) ?: 0).toDp() }
    val chipsRoom = statusTop + PHONE_CHIPS_ROOM
    val res = LocalContext.current.resources
    var filterOpen by remember { mutableStateOf(false) }
    val filtered = filteredWords(state)
    val clearFilters = remember(state) {
        {
            state.filter = TechFilter()
            state.quick = QuickFilter.All
        }
    }
    // the header reads what changes through these, so that the lists that hold it are not made again for them
    val latestInks = rememberUpdatedState(inks)
    val latestCounts = rememberUpdatedState(counts)
    val latestFiltered = rememberUpdatedState(filtered)
    val latestConverting = rememberUpdatedState(converting)
    val latestConverted = rememberUpdatedState(onConverted)
    val header: @Composable (shuffle: (() -> Unit)?) -> Unit = remember(state, statusTop, scan) {
        { shuffle ->
            PhoneMusicHeader(
                latestInks.value, statusTop, latestCounts.value, state, shuffle, latestFiltered.value, clearFilters,
            ) {
                // a scan going on, the converter's job: under the toolbar
                if (scan.active) ScanStrip(scan)
                ConvertHost(
                    latestConverting.value,
                    onConverted = { latestConverted.value() },
                    modifier = Modifier.padding(horizontal = NagTheme.dimens.pagePadding, vertical = NagTheme.dimens.gapXs),
                )
            }
        }
    }
    val options: () -> List<MenuEntry> = {
        buildList {
            // 1.1.0's quick filters over the groups, as radio items
            if (state.view.grouped) {
                QuickFilter.entries.forEach { quick -> add(MenuEntry.Item(res.getString(quick.label), { state.quick = quick }, checked = state.quick == quick)) }
                add(MenuEntry.Separator)
            }
            add(MenuEntry.Item(res.getString(R.string.nag_music_filter), { filterOpen = true }, icon = NagIcons.Filter, checked = state.filter.active.takeIf { it }))
            add(MenuEntry.Item(res.getString(R.string.nag_state_add_folder), onAddFolder, icon = NagIcons.FolderAdd))
        }
    }
    val open = state.view.grouped && state.group != null
    Box(Modifier.fillMaxSize().onGloballyPositioned { phoneTop.pageTop = it.positionInWindow().y }.testTag("music-page")) {
        CompositionLocalProvider(LocalPhoneTop provides phoneTop, LocalPhoneBand provides remember(inks, statusTop) { PhoneBand(inks, statusTop) }) {
            if (library == null || library.tracks.isEmpty()) {
                Column(Modifier.fillMaxSize()) {
                    PhoneMusicTopOnly(inks, statusTop, counts)
                    Box(Modifier.weight(1f)) { MusicContent(library, scan, onAddFolder) {} }
                }
            } else {
                MusicBrowser(
                    library, state, actions, Modifier.fillMaxSize(), probing = probing, stats = stats, recent = recent,
                    phoneHeader = header, chipsRoom = chipsRoom,
                )
            }
            PhoneMusicChips(
                statusTop = statusTop,
                inks = inks,
                onBack = if (open) ({ state.group = null }) else null,
                onSearch = onSearch,
                options = options,
                marked = filtered != null,
                // 1.1.3: the way in shown, once the band has gone from under the chips
                section = stringResource(viewLabel(state.view)),
                optionsAnchor = { if (filterOpen) FilterPanel(state, probing, onDismiss = { filterOpen = false }) },
            )
        }
    }
}

/** What holds a phone's list now: the quick filter and the format's filters, in words; null for nothing. */
@Composable
private fun filteredWords(state: MusicBrowserState): String? {
    val locale = LocalContext.current.resources.locale()
    val parts = buildList {
        if (state.view.grouped && state.quick != QuickFilter.All) add(stringResource(state.quick.label))
        state.filter.depths.forEach { add(depthLabel(it)) }
        state.filter.rates.forEach { add(rateLabel(it, locale)) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/**
 * The Music page under its header: the loading state while no list is known yet (the first start,
 * before its first read), the empty state, or [browser] with the list. 1.0.3: a known list (this
 * window's, or the one kept from the last start) is shown whatever the medialibrary does; a scan
 * the user asked for adds its strip above it.
 */
@Composable
internal fun MusicContent(library: MusicLibrary?, scan: ScanState, onAddFolder: () -> Unit, browser: @Composable (MusicLibrary) -> Unit) {
    LibraryContent(
        loading = library == null,
        empty = library?.tracks?.isEmpty() == true,
        scan = scan,
        emptyTitle = stringResource(R.string.nag_state_no_music_title),
        emptyDescription = stringResource(R.string.nag_state_no_music_desc),
        onAddFolder = onAddFolder,
    ) {
        if (library != null) browser(library)
    }
}

/**
 * The medialibrary's music (or the picked folders'), the engine's probe started on what is new: the music screen's,
 * and the smart lists' (S-805). 1.0.3: what was shown last is on screen from the first frame (this window's, else the
 * list kept on the device from the last start: [MusicSnapshot]); the read behind it replaces it only when the music
 * changed, and is kept for the next start. Read again whenever the medialibrary reports a change (a scan or the
 * start's look found, changed or lost files) or a picked folder's listing changed, never every few seconds, and the
 * tracks' probe runs only when the list changed.
 */
@Composable
fun rememberMusicRead(repository: MediaRepository): MusicRead? {
    val context = LocalContext.current
    val access = LocalLibraryAccess.current
    val scope = remember(access?.media, access?.folders, access?.scopes) { repository.musicScope() }
    // the music of the section's folders (Settings › Library)
    val read by rememberRead<MusicRead>(repository.lastRead, "music", scope, kept = { MusicSnapshot.cached(scope) }) {
        if (value == null) repository.keptMusic(scope)?.let { value = it }
        var keptAt = 0L
        suspend fun load() {
            val fresh = repository.musicRead()
            if (fresh != value) value = fresh
            // kept for the next start; while a scan adds files, twice a minute at most
            val now = SystemClock.elapsedRealtime()
            if (!Medialibrary.getInstance().isWorking || now - keptAt >= MUSIC_KEEP_WHILE_WORKING_MS) {
                keptAt = now
                repository.keepMusic(scope, fresh)
            }
        }
        load()
        repository.musicChanges().collect {
            // a burst of reports while a scan parses is read once in a while, not for every file
            delay(if (Medialibrary.getInstance().isWorking) MUSIC_CHANGE_WORKING_MS else MUSIC_CHANGE_SETTLE_MS)
            load()
        }
    }
    LaunchedEffect(read) {
        // 1.0.3: when the list first stood on screen in this process, for the log (the walk's restart check reads it)
        read?.let { StartupPerf.musicListShown(it.media.size, it.kept) }
        // S-808: a CUE sheet's tracks share their file, probed once
        read?.media?.let { list -> runCatching { TrackTechIndex.update(context, list.mapNotNull { it.uri?.toString()?.let(CueUri::file) }.distinct()) } }
    }
    return read
}

/** 1.0.3: how long the Music waits after a change the medialibrary reported before it reads its list again. */
private const val MUSIC_CHANGE_SETTLE_MS = 1_000L

/** 1.0.3: the same while the medialibrary still works (a scan adds files one after another): the list follows it at this pace. */
private const val MUSIC_CHANGE_WORKING_MS = 4_000L

/** 1.0.3: how often the list is kept for the next start while a scan still adds files (its end keeps it at once). */
private const val MUSIC_KEEP_WHILE_WORKING_MS = 30_000L

/** S-1006: an album's cover in its header; a click (a right click, a long press) opens its menu. */
@Composable
internal fun AlbumCover(group: MusicGroup, modifier: Modifier = Modifier, side: Dp = 48.dp) {
    val track = remember(group.key) { group.tracks.firstOrNull()?.let(CoverTrack::of) }
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
        year = releaseYearOf(releaseYear),
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

    override val share: (List<Track>) -> Unit get() = { tracks -> MediaSharing.share(context, tracks.map { fileOf(it) }) }

    override val reveal: (Track) -> Unit get() = { track -> FileReveal.show(context, fileOf(track).uri) }

    /** A track's file: a CUE sheet's track is its whole file. */
    private fun fileOf(track: Track) = MediaFile(CueUri.file(Uri.parse(track.uri)), video = false, name = track.title)

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

    override fun rowModifier(track: Track, atOnce: () -> Boolean, others: () -> List<Track>): Modifier =
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
            atOnce = atOnce,
        )
}

// ------------------------------------------------------------------ the browser

/**
 * The groups the browser shows. Made at once when nothing is on screen yet or the view or the
 * filter changed, so what was asked for shows; when only the library changed (the engine's probe
 * added to it, a scan found more) the new grouping is made off the main thread while the last one
 * stays, so the probe of a large library does not stall the window again and again.
 */
@Composable
private fun rememberGroups(library: MusicLibrary, view: MusicView, filter: TechFilter): List<MusicGroup> {
    val held = remember { HeldGroups() }
    library.madeGroups(view, filter)?.let { return held.keep(view, filter, it) }
    val last = held.groups
    if (last == null || held.view != view || held.filter != filter) return held.keep(view, filter, library.groups(view, filter))
    val made by produceState<List<MusicGroup>?>(null, library, view, filter) {
        value = withContext(Dispatchers.Default) { library.groups(view, filter) }
    }
    return made?.let { held.keep(view, filter, it) } ?: last
}

/**
 * A table's order: which group of which view under which filter (a group keeps its key when the filter takes tracks
 * out of it, and "" is both the Tracks view's one group and another view's group without a name), how, in which
 * language, with which statistics (compared as the same map).
 */
private class OrderKey(
    val view: MusicView,
    val filter: TechFilter,
    val group: String,
    val sort: MusicSort?,
    val locale: Locale,
    val stats: Map<String, TrackStats>,
    /** The Recent view's history (its one group's tracks change with it); empty for the other views. */
    val recent: List<String> = emptyList(),
) {
    override fun equals(other: Any?): Boolean =
        other is OrderKey && other.view == view && other.filter == filter && other.group == group && other.sort == sort &&
            other.locale == locale && other.stats === stats && other.recent == recent

    override fun hashCode(): Int =
        (((((view.hashCode() * 31 + filter.hashCode()) * 31 + group.hashCode()) * 31 + sort.hashCode()) * 31 + locale.hashCode()) * 31 +
            System.identityHashCode(stats)) * 31 + recent.hashCode()
}

/**
 * The open group's tracks in the table's order, as [rememberGroups] makes the groups: at once when nothing is on
 * screen yet or another group or order was asked for, off the main thread when only the library changed.
 */
@Composable
private fun rememberOrdered(
    library: MusicLibrary,
    view: MusicView,
    filter: TechFilter,
    open: MusicGroup?,
    sort: MusicSort?,
    locale: Locale,
    stats: Map<String, TrackStats>,
    recent: List<String> = emptyList(),
): List<Track> {
    if (open == null) return emptyList()
    val key = OrderKey(view, filter, open.key, sort, locale, stats, if (view == MusicView.Recent) recent else emptyList())
    val held = remember { HeldOrder() }
    library.madeOrder(key)?.let { return held.keep(key, it) }
    val last = held.tracks
    if (last == null || held.key != key) return held.keep(key, library.ordered(key) { sortTracks(open.tracks, sort, locale, stats) })
    val made by produceState<List<Track>?>(null, library, key) {
        value = withContext(Dispatchers.Default) { library.ordered(key) { sortTracks(open.tracks, sort, locale, stats) } }
    }
    return made?.let { held.keep(key, it) } ?: last
}

/** The order on screen and what it is of; plain, as [HeldGroups]. */
private class HeldOrder {
    var key: OrderKey? = null
    var tracks: List<Track>? = null

    fun keep(key: OrderKey, tracks: List<Track>): List<Track> {
        this.key = key
        this.tracks = tracks
        return tracks
    }
}

/** The grouping on screen and what it is of; plain, so keeping it never recomposes anything. */
private class HeldGroups {
    var view: MusicView? = null
    var filter: TechFilter? = null
    var groups: List<MusicGroup>? = null

    fun keep(view: MusicView, filter: TechFilter, groups: List<MusicGroup>): List<MusicGroup> {
        this.view = view
        this.filter = filter
        this.groups = groups
        return groups
    }
}

/**
 * S-803 and S-1001: the music library below its header. The toolbar on top (the ways in, Play,
 * Shuffle, the columns and the filter); then, by the window's width: a phone shows the groups, then the tracks of the
 * one opened (Back returns); a tablet and a desktop window the groups beside the track table.
 *
 * Desktop Kit 1.0 ("MusicBee"): on tablet and DeX windows (Expanded and wider) [inspector]'s tabs stand on the right
 * ([WorkstationLayout]), about the track selected in the table or, with none, what plays; [inspectorState] keeps the
 * pane's width and whether it is hidden (Ctrl+I). Beside it a tablet's window (narrower than Large) shows the groups,
 * then the tracks of the one opened, as a narrow window does.
 *
 * 1.0: the Recent view lists [recent] (the uris played last, newest first), which is not part of [library]; on a phone
 * [shelf] (the recently played) is the first item of the list while no group is open, and scrolls away with it.
 * 1.0.3: [shelfPresent] says whether it has tracks to show now (it may get them after the rows are on screen).
 */
@Composable
fun MusicBrowser(
    library: MusicLibrary,
    state: MusicBrowserState,
    actions: MusicActions,
    modifier: Modifier = Modifier,
    probing: Int = 0,
    inspector: MusicInspectorContent? = null,
    inspectorState: InspectorState = rememberInspectorState(defaultWidth = MUSIC_PANEL_WIDTH),
    /** S-808: the ratings and counts, by track uri. */
    stats: Map<String, TrackStats> = emptyMap(),
    recent: List<String> = emptyList(),
    shelf: (@Composable () -> Unit)? = null,
    shelfPresent: (() -> Boolean)? = null,
    /**
     * 1.1.1 (the design's phone page): a phone's header (the band, the masthead, the tabs, the toolbar), the first item of
     * every list and grid, scrolling away with it; the toolbar then is none of the browser's. [chipsRoom]: what the
     * page's chips cover at the lists' top.
     */
    phoneHeader: (@Composable (shuffle: (() -> Unit)?) -> Unit)? = null,
    chipsRoom: Dp = 0.dp,
    /**
     * 1.1.2 (the design's tablet page): a wide window's top (the band, the masthead with Oynat and Karıştır: the shown
     * tracks) over the tabs and the toolbar, beside the panel.
     */
    wideHeader: (@Composable (play: (() -> Unit)?, shuffle: (() -> Unit)?) -> Unit)? = null,
) {
    val dimens = NagTheme.dimens
    val width = currentWidthClass()
    val all = if (state.view == MusicView.Recent) {
        remember(library, recent, state.filter) { library.recentGroups(recent, state.filter) }
    } else {
        rememberOrderedGroups(library, state.view, state.filter, state.groupSort, rememberGroups(library, state.view, state.filter))
    }
    // 1.1.0 (Ebru): the quick filter over a way in's groups
    val quick = state.quick.takeIf { state.view.grouped } ?: QuickFilter.All
    val groups = remember(all, quick, stats) { all.quick(quick, stats, currentYear()) }
    // 1.1.0: the covers of the groups, where they are groups
    val grid = state.view.grouped && state.layout == MusicLayout.Grid
    val playingMedia = rememberNowPlaying().media
    val playingUri = playingMedia?.uri?.toString()
    // 1.1.5: what the panel beside the page shows as what plays: music, an episode (nothing: it says so); not a station
    val panelShowsPlaying = playingMedia == null || playingMedia.type == MediaWrapper.TYPE_AUDIO
    // the phone's only: over the groups, or over the whole library's tracks. 1.1.1: the design's header in its place, its
    // Karıştır the shown groups' tracks (the whole library's in the Tracks view)
    val phone = width == WidthClass.Compact && phoneHeader != null
    val shuffleFrom = rememberUpdatedState(groups)
    val shuffleAll: () -> Unit = remember(actions) { { actions.play(shuffleFrom.value.flatMap { it.tracks }.shuffled(), 0) } }
    val canShuffle = groups.any { it.tracks.isNotEmpty() }
    val latestShuffle = rememberUpdatedState(shuffleAll.takeIf { canShuffle })
    val header: (@Composable () -> Unit)? = phoneHeader?.let { h -> remember(h) { @Composable { h(latestShuffle.value) } } }
    val phoneShelf = if (width != WidthClass.Compact) null else header ?: shelf
    // 1.1.2 (the design's tablet page): a wide window's tabs and toolbar over the groups, the quick filters in it
    val wideTop = width != WidthClass.Compact
    // 1.1.0 (Ebru): the quick filters over a way in's groups, the first thing of their list or grid (after a phone's
    // shelf), so the tracks beside them never move for them; in the narrow pane beside the tracks on one line. 1.1.1: on
    // a phone with the design's header they are in its options' chip
    val quickOverGrid: (@Composable () -> Unit)? = if (wideTop) null else remember(phoneShelf, state, phone) {
        @Composable {
            Column {
                phoneShelf?.invoke()
                if (!phone) QuickFilterRow(state)
            }
        }
    }
    val quickInPane: (@Composable () -> Unit)? = if (wideTop) null else remember(state) { @Composable { QuickFilterRow(state, wrap = false) } }
    val grouped = state.view.grouped
    // the recently played are the shelf on a phone, not a view (1.1.1: with the design's header, its last tab)
    LaunchedEffect(width, state.view, phone) {
        if (width == WidthClass.Compact && !phone && state.view == MusicView.Recent) state.open(MusicView.Tracks)
    }
    val workstation = inspector != null && width >= WidthClass.Expanded
    // a tablet's window with the inspector shown has the room of a narrow one for the groups and their tracks (Desktop Kit 1.0)
    val wide = width >= WidthClass.Expanded && !(workstation && !inspectorState.collapsed && width < WidthClass.Large)
    // a wide window always shows a group; a narrow one shows the list until one is opened. 1.1.0: the open group is
    // looked for past the quick filter, which goes by the play counts: an unplayed album played stays open
    val open = if (!grouped) {
        groups.firstOrNull()
    } else {
        state.group?.let { key -> all.firstOrNull { it.key == key } } ?: groups.firstOrNull().takeIf { wide && !grid }
    }
    // (a phone's group closes with its transition's Back, as the grid's does)
    if (!wide && !grid && grouped && state.group != null && width != WidthClass.Compact) BackHandler { state.group = null }
    // the toolbar's Play and Shuffle take the open group's tracks as the table shows them
    val locale = LocalContext.current.resources.locale()
    val sort = effectiveSort(state)
    val sortStats = if (sort?.column in STATS_COLUMNS) stats else emptyMap()
    val shown = rememberOrdered(library, state.view, state.filter, open, sort, locale, sortStats, recent)
    // a player's artist or album link: its group, wherever the library was
    val context = LocalContext.current
    val link = MusicLinks.pending
    LaunchedEffect(link, library) {
        val asked = link ?: return@LaunchedEffect
        val filter = state.filter
        val quickNow = state.quick
        val counts = stats
        // the groups are sorted with the reader's collator: off the main thread for a large library
        val found = withContext(Dispatchers.Default) {
            val all = library.groups(asked.view)
            val key = asked.keys.firstOrNull { k -> all.any { it.key == k } } ?: return@withContext null
            val hidden = filter.active && library.groups(asked.view, filter).none { it.key == key }
            // 1.1.0: nor the quick filter, over the groups the tech filter leaves (all of them once it is cleared)
            val shown = if (hidden || !filter.active) all else library.groups(asked.view, filter)
            val unquick = quickNow != QuickFilter.All && asked.view.grouped && shown.quick(quickNow, counts, currentYear()).none { it.key == key }
            Triple(key, hidden, unquick)
        }
        MusicLinks.taken(asked)
        if (found == null) {
            Notices.show(context, R.string.nag_music_link_missing)
            return@LaunchedEffect
        }
        state.show(asked.view, found.first, clearFilter = found.second, clearQuick = found.third)
    }
    // the open group's tracks play from it: an album's or an artist's, as the phone's player names it
    val openActions = remember(actions, state.view, open?.title) { actions.playingFrom(groupSource(state.view, open)) }
    // the table's selection, which the inspector follows (S-405, Desktop Kit 1.0)
    val selection = rememberMultiSelection()
    // 1.1.5: the player asked for on a tablet's or a DeX window: the panel up on what plays (no big player there)
    val panelAsked = MusicPanelRequest.pending
    LaunchedEffect(panelAsked, inspector != null) {
        if (!panelAsked || inspector == null) return@LaunchedEffect
        MusicPanelRequest.taken()
        selection.clear()
        inspectorState.selectedTab = 0
        if (inspectorState.collapsed) inspectorState.updateCollapsed(false)
        // once laid out: beside the page, or as its sheet in a window too narrow for it
        withFrameNanos { }
        if (!inspectorState.docked) inspectorState.sheetOpen = true
    }
    // a phone's group closing keeps the order its tracks were shown in
    val closing = remember { ClosingOrder() }
    if (inspector != null) InspectorCommands(inspectorState::toggle)
    // 1.0.8 (Desktop Kit 2.8.0's list and detail): the groups are one list in every layout, so a window crossing a width
    // (resized on DeX, a tablet or a phone turned, the inspector shown) keeps their place, as does a group's tracks
    // covering them; each group's tracks move the same way, one pane per group ([MovableDetails])
    val currentGroups by rememberUpdatedState(groups)
    val currentActions by rememberUpdatedState(actions)
    val savedGroups = rememberSaveableStateHolder()
    val currentStats by rememberUpdatedState(stats)
    val currentPlaying by rememberUpdatedState(playingUri)
    // 1.1.1: the groups go under their letters while they are in order by a name (the design's A–Z)
    val order = state.groupSort ?: GroupSort.default(state.view)
    val named = order.order == GroupOrder.Name || order.order == GroupOrder.Artist
    val gridWords = remember(state.view, order) {
        val view = state.view
        GroupGridWords(
            title = { groupTitle(it, view) },
            // 1.1.1 (the design): an album's cover has its artist and year under its name, a row its tracks too
            line = { if (view == MusicView.Albums) listOfNotNull(it.artist, it.year?.toString()).joinToString(" · ") else groupLine(it, view) },
            letters = named && (view == MusicView.Albums || view == MusicView.Artists || view == MusicView.Genres || view == MusicView.Composers),
            // the albums are in order by their names (1.1.1; by their artists before), or by their artists when asked
            sortName = if (view == MusicView.Albums && order.order == GroupOrder.Artist) { group -> group.artist } else { group -> group.title },
            nameless = {
                stringResource(
                    when (view) {
                        MusicView.Genres -> R.string.nag_music_unknown_genre
                        MusicView.Composers -> R.string.nag_music_unknown_composer
                        else -> R.string.nag_unknown_artist
                    },
                )
            },
            count = { n ->
                pluralStringResource(
                    when (view) {
                        MusicView.Albums -> R.plurals.nag_music_albums
                        MusicView.Genres -> R.plurals.nag_music_genres_count
                        MusicView.Composers -> R.plurals.nag_music_composers_count
                        else -> R.plurals.nag_music_artists_count
                    },
                    n, n,
                )
            },
        )
    }
    val currentWords by rememberUpdatedState(gridWords)
    val groupList = remember(state) {
        movableContentOf { openKey: String?, listModifier: Modifier, rowPadding: Dp, leading: (@Composable () -> Unit)? ->
            savedGroups.SaveableStateProvider(GROUP_LIST) {
                GroupList(currentGroups, state, currentActions, openKey, listModifier, rowPadding, leading, currentStats, currentPlaying, currentWords, chipsRoom)
            }
        }
    }
    val groupGrid: @Composable (Modifier, (@Composable () -> Unit)?) -> Unit = { gridModifier, leading ->
        savedGroups.SaveableStateProvider(GROUP_GRID) {
            val resources = LocalContext.current.resources
            GroupGrid(
                groups, gridWords, stats, playingUri,
                onOpen = { state.group = it.key },
                menu = { group -> groupMenu(resources, group, actions.playingFrom(groupSource(state.view, group))) },
                modifier = gridModifier,
                leading = leading,
                reveal = state.reveal,
                onRevealed = { state.reveal = null },
                chipsRoom = chipsRoom,
                // 1.1.2 (the design's tablet page): a cover's play button
                onPlay = if (width == WidthClass.Compact) null else ({ group -> actions.playingFrom(groupSource(state.view, group)).play(group.tracks, 0) }),
            )
        }
    }
    val trackPanes = rememberMovableDetails<String, TrackPaneArgs>(open?.key) { args ->
        TrackPane(args.group, args.tracks, state, args.actions, args.showBack, stats, selection, args.leading, args.leadingPresent, chipsRoom, playingUri)
    }
    // the groups' width beside their tracks, dragged and kept
    // 1.1.2 (the design): the margin, the rows and their letters' wave
    val groupsSplit = rememberSplitState(if (wideTop) 328.dp else 260.dp, MUSIC_GROUPS_PANE)
    val instant = LocalMotionTier.current == MotionTier.Reduced || NagTheme.motion.reduced
    val panes: @Composable () -> Unit = {
        when {
            // nothing played yet (no filter hiding what was)
            state.view == MusicView.Recent && open?.tracks.isNullOrEmpty() && !state.filter.active -> UnderPhoneHeader(header.takeIf { phone }) {
                StatePanel(
                    tone = StateTone.Neutral,
                    icon = NagIcons.Play,
                    title = stringResource(R.string.nag_music_recent_none_title),
                    description = stringResource(R.string.nag_music_recent_none_desc),
                )
            }
            // (an open group stays: the quick filter may have let it go as it played)
            (groups.isEmpty() && open == null) || (open != null && open.tracks.isEmpty()) -> UnderPhoneHeader(header.takeIf { phone }) {
                NoResultsState(
                    title = stringResource(R.string.nag_music_no_match),
                    // the files still being read may yet fit
                    detail = if (probing > 0 && state.filter.active) pluralStringResource(R.plurals.nag_music_probing, probing, probing) else null,
                    // 1.1.0: the quick filter is one of them
                    suggestions = listOf(StateAction(stringResource(R.string.nag_clear_filters)) {
                        state.filter = TechFilter()
                        state.quick = QuickFilter.All
                    }),
                )
            }
            // 1.1.0 (Ebru): the covers, in every window; a group opens over them, as a phone's does, and Back returns
            grid -> DetailTransition(
                detail = open,
                onBack = { state.group = null },
                detailKey = { it.key },
                list = { groupGrid(Modifier.fillMaxSize(), quickOverGrid) },
            ) { group ->
                val tracks = closing.of(group.key, if (group.key == open?.key) shown else null) ?: group.tracks
                val groupActions = remember(actions, state.view, group.title) { actions.playingFrom(groupSource(state.view, group)) }
                trackPanes.Show(group.key, TrackPaneArgs(group, tracks, groupActions, showBack = true))
            }
            // the groups and the open group's tracks side by side, the divider between them dragged and kept (1.0.8)
            wide && grouped -> HorizontalSplitPane(
                state = groupsSplit,
                // 1.1.2 (the design): the groups from the page's margin
                start = { groupList(open?.key, Modifier.fillMaxSize().padding(start = if (wideTop) WIDE_MARGIN else 0.dp), 0.dp, quickInPane) },
                end = {
                    // 1.0.8 (Desktop Kit 2.8.0): another group chosen, its tracks come once the last ones have gone
                    AnimatedContent(
                        targetState = open,
                        contentKey = { it?.key },
                        transitionSpec = {
                            if (instant) {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            } else {
                                fadeIn(tween(NagMotion.MEDIUM, delayMillis = NagMotion.SHORT)) togetherWith fadeOut(tween(NagMotion.SHORT))
                            }
                        },
                        label = "groupTracks",
                        modifier = Modifier.fillMaxSize(),
                    ) { group ->
                        if (group != null) {
                            // the one going keeps what it showed
                            val current = open?.takeIf { it.key == group.key }
                            trackPanes.Show(group.key, current?.let { TrackPaneArgs(it, shown, openActions, showBack = false) })
                        }
                    }
                },
                minStart = 200.dp,
                minEnd = 360.dp,
                dividerLabel = stringResource(R.string.nag_list_divider),
            )
            // 1.0.3 (Desktop Kit 2.3.0): a phone opens a group's tracks in depth over the groups, which stay where they
            // were; Back is the predictive gesture
            width == WidthClass.Compact && grouped -> DetailTransition(
                detail = open,
                onBack = { state.group = null },
                detailKey = { it.key },
                list = { groupList(null, Modifier.fillMaxSize(), dimens.pagePadding - 12.dp, quickOverGrid) },
            ) { group ->
                val tracks = closing.of(group.key, if (group.key == open?.key) shown else null) ?: group.tracks
                val groupActions = remember(actions, state.view, group.title) { actions.playingFrom(groupSource(state.view, group)) }
                trackPanes.Show(group.key, TrackPaneArgs(group, tracks, groupActions, showBack = true))
            }
            !grouped || open != null -> if (open != null) {
                trackPanes.Show(
                    open.key,
                    TrackPaneArgs(
                        open, shown, openActions, showBack = grouped,
                        leading = phoneShelf.takeIf { !grouped }, leadingPresent = shelfPresent.takeIf { !grouped && phoneShelf != null },
                    ),
                )
            }
            else -> groupList(null, Modifier.fillMaxSize(), dimens.pagePadding - 12.dp, if (grouped) quickOverGrid else phoneShelf)
        }
    }
    // 1.1.2 (the design's tablet page): the masthead's Oynat and Karıştır: the shown groups' tracks in their order (the
    // table's, for the library's tracks)
    val playFrom = rememberUpdatedState(if (grouped) null else shown)
    val playShown: () -> Unit = remember(actions) { { actions.play(playFrom.value ?: shuffleFrom.value.flatMap { it.tracks }, 0) } }
    val shuffleShown: () -> Unit = remember(actions) { { actions.play((playFrom.value ?: shuffleFrom.value.flatMap { it.tracks }).shuffled(), 0) } }
    val covers = grid && open == null
    // 1.1.3: a wide window's band and masthead fold away as its covers, groups and tracks scroll, the tabs and the
    // toolbar staying at the top, so that the lists have the window's height; back at a list's top they come down again
    val top = rememberCollapsingTopState()
    val folded by remember(top) { derivedStateOf { top.fraction > 0.5f } }
    // (the panes in the same place in every width: a window crossing one keeps their lists where they were)
    val main: @Composable () -> Unit = {
        CollapsingTop(
            top,
            header = { if (wideTop) wideHeader?.invoke(playShown.takeIf { canShuffle }, shuffleShown.takeIf { canShuffle }) },
            pinned = {
                if (wideTop) {
                    Column(Modifier.fillMaxWidth()) {
                        WideTabs(state)
                        WideToolbar(
                            state, probing,
                            // the order of the covers; the table's columns where there is a table (its titles sort it)
                            orderShown = covers || (!grouped && state.view != MusicView.Recent),
                            columnsShown = !covers,
                            // the panel's button folds away with the masthead: here meanwhile
                            trailing = { if (folded && inspector != null && inspectorState.hidden) InspectorToggleButton(inspectorState) },
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                } else if (!phone) {
                    // 1.1.1: a phone's toolbar is in its header
                    MusicToolbar(state, openActions, shown, showColumns = false, probing = probing)
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) { panes() }
        }
    }
    // "music": what the scroll benchmark (S-803) looks for
    Column(modifier.fillMaxSize().testTag("music")) {
        if (inspector == null) {
            main()
        } else {
            // the selected track (the last clicked of several), else what plays
            val chosen = selection.keys
            val key = selection.anchor?.takeIf { it in chosen } ?: chosen.firstOrNull()
            val subject = InspectorSubject(key?.let { k -> shown.firstOrNull { it.uri == k } }, chosen.size)
            // 1.1.2 (the design's tablet page): the panel beside the page shows the transport of what plays: the shell's
            // floating player stands aside meanwhile. 1.1.5 (the owner, 16 Nov: "Tablet ve Dex modunda altta oynatıcı
            // çıkmasın"): whatever the panel shows (a track selected with Ctrl, its lyrics, the queue), the player is the
            // panel's on a tablet's and a DeX window, not a bar that comes and goes as the panel changes; a station, which
            // the panel does not show, keeps the bar
            PlayerAside(inspectorState.docked && !inspectorState.collapsed && panelShowsPlaying)
            WorkstationLayout(
                state = inspectorState,
                main = main,
                inspector = { MusicInspectorPane(inspectorState, inspector, subject) },
                // the table keeps the room for its title beside the fixed columns (and the groups beside it): the inspector gives way
                minMain = if (wide) 760.dp else 560.dp,
                sheetTitle = stringResource(R.string.nag_inspector),
            )
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
    MusicView.Recent -> R.string.nag_music_view_recent
}

/** A group's name, or what stands for it when the tag is missing. */
@Composable
internal fun groupTitle(group: MusicGroup, view: MusicView): String = group.title ?: stringResource(
    when (view) {
        MusicView.Artists -> R.string.nag_unknown_artist
        MusicView.Albums -> R.string.nag_music_unknown_album
        MusicView.Genres -> R.string.nag_music_unknown_genre
        MusicView.Composers -> R.string.nag_music_unknown_composer
        MusicView.Years -> R.string.nag_music_unknown_year
        MusicView.Folders, MusicView.Tracks, MusicView.Recent -> R.string.nag_music_unknown_folder
    },
)

/** "Ada Karan · 1977 · 9 parça", "4 albüm · 38 parça", "12 parça · 52:10". */
@Composable
internal fun groupLine(group: MusicGroup, view: MusicView): String {
    val tracks = pluralStringResource(R.plurals.nag_tracks_count, group.tracks.size, group.tracks.size)
    val parts = when (view) {
        MusicView.Albums -> listOfNotNull(group.artist, group.year?.toString(), tracks)
        MusicView.Artists -> listOf(pluralStringResource(R.plurals.nag_music_albums, group.albumCount, group.albumCount), tracks)
        MusicView.Folders -> listOfNotNull(group.title?.let { path -> path.substringBeforeLast('/', "").ifEmpty { null } }, tracks)
        else -> listOf(tracks, formatDuration(group.durationMs))
    }
    return parts.joinToString(" · ")
}

/** The groups of a view; [leading] (a phone's shelf) over them, scrolling with them; each row inset by [rowPadding]. */
@Composable
private fun GroupList(
    groups: List<MusicGroup>,
    state: MusicBrowserState,
    actions: MusicActions,
    openKey: String?,
    modifier: Modifier,
    rowPadding: Dp = 0.dp,
    leading: (@Composable () -> Unit)? = null,
    stats: Map<String, TrackStats> = emptyMap(),
    playing: String? = null,
    words: GroupGridWords? = null,
    chipsRoom: Dp = 0.dp,
) {
    val listState = rememberLazyListState()
    val res = LocalContext.current.resources
    val reduced = NagTheme.motion.reduced
    val compact = currentWidthClass() == WidthClass.Compact
    // 1.1.1 (the design's phone page): a phone's rows under their letters, the wave down their end; the same list's state,
    // so the groups keep their place as the window crosses the width
    if (compact && words != null) {
        PhoneGroupList(groups, state, actions, listState, modifier, leading, stats, playing, words, chipsRoom)
        return
    }
    // 1.1.2 (the design's tablet page): a wide window's rows beside the open group's tracks
    if (words != null && leading == null) {
        WideGroupList(groups, state, actions, listState, openKey, modifier, stats, playing, words)
        return
    }
    // 1.0.8 (Desktop Kit 2.8.0): Müzik's entry tapped again: the groups go to their top
    OnReselect { if (reduced) listState.scrollToItem(0) else listState.animateScrollToItem(0) }
    // 1.0.3: on a phone the first groups arrive in turn, again for another way in, not when a group's tracks close
    val appear = rememberListAppear(state.view)
    LaunchedEffect(state.reveal, groups) {
        val key = state.reveal ?: return@LaunchedEffect
        val index = groups.indexOfFirst { it.key == key }
        if (index >= 0) {
            val item = index + if (leading != null) 1 else 0
            if (reduced) listState.scrollToItem(item) else listState.animateScrollToItem(item)
            state.reveal = null
        }
    }
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(vertical = 4.dp) + LocalShellBottomPadding.current) {
        if (leading != null) item(key = LEADING_ITEM, contentType = LEADING_ITEM) { leading() }
        itemsIndexed(groups, key = { _, group -> group.key }) { index, group ->
            // the group shown beside, the first one on a wide window until another is opened
            // the menu plays the group's tracks, as the table's rows do one track
            val menu = { groupMenu(res, group, actions.playingFrom(groupSource(state.view, group))) }
            ContextMenuArea(
                entries = menu,
                modifier = Modifier.listAppear(appear, index).padding(horizontal = rowPadding),
                accessibilityLabel = stringResource(R.string.nag_more_actions),
            ) {
                // a phone's album, artist or folder ends with "⋯": the same menu (Desktop Kit 1.0)
                GroupRow(group, state.view, selected = group.key == openKey, menu = menu.takeIf { compact }, stats = stats, playing = playing) { state.group = group.key }
            }
        }
    }
}

/** What a group's [TrackPane] shows; 1.0.8: one movable pane per group ([MovableDetails]). */
private class TrackPaneArgs(
    val group: MusicGroup,
    val tracks: List<Track>,
    val actions: MusicActions,
    val showBack: Boolean,
    val leading: (@Composable () -> Unit)? = null,
    val leadingPresent: (() -> Boolean)? = null,
)

/** The groups' saved state while they are out of sight (a narrow window's open group covering them). */
private const val GROUP_LIST = "groups"

/** 1.1.0: the grid's, as [GROUP_LIST]. */
private const val GROUP_GRID = "group-grid"


/** 1.1.0: an album row's listening trace, under its line. */
private val TRACE_WIDTH = 120.dp

/** The order a phone's open group showed its tracks in, kept while it closes; plain, so keeping it never redraws anything. */
private class ClosingOrder {
    private var key: String? = null
    private var tracks: List<Track>? = null

    /** [shown] kept as [key]'s when there is one; else what was kept for [key], if anything. */
    fun of(key: String, shown: List<Track>?): List<Track>? {
        if (shown != null) {
            this.key = key
            tracks = shown
            return shown
        }
        return tracks.takeIf { this.key == key }
    }
}

/** The key of a group list's first item that is not a group (the phone's shelf); no group's key holds a NUL. */
internal const val LEADING_ITEM = "\u0000leading"

/**
 * An album's, an artist's or a folder's menu (right click, long press): play, queue, convert; its
 * tracks' files shared together, the first one shown in its folder.
 */
internal fun groupMenu(res: android.content.res.Resources, group: MusicGroup, actions: MusicActions): List<MenuEntry> =
    tracksMenu(res, group.tracks, actions)

/**
 * [tracks]' menu: a group's, or (Desktop Kit 1.0) the table's selection's, opened on one of the selected rows, with
 * their rating too ([rate]); the selection bar's actions as a menu.
 */
internal fun tracksMenu(res: android.content.res.Resources, tracks: List<Track>, actions: MusicActions, rate: Boolean = false): List<MenuEntry> {
    val share = actions.share
    val reveal = actions.reveal
    val first = tracks.firstOrNull()
    return listOfNotNull(
        MenuEntry.Item(res.getString(R.string.nag_play), { actions.play(tracks, 0) }, icon = NagIcons.Play),
        MenuEntry.Item(res.getString(R.string.nag_play_next), { actions.playNext(tracks) }, icon = NagIcons.Next),
        MenuEntry.Item(res.getString(R.string.nag_add_to_queue), { actions.queue(tracks) }, icon = NagIcons.PlaylistAdd),
        actions.addToPlaylist?.let { add -> MenuEntry.Item(res.getString(R.string.nag_pl_add), { add(tracks) }, icon = NagIcons.PlaylistAdd) },
        if (rate) actions.askRating?.let { ask -> MenuEntry.Item(res.getString(R.string.nag_rate), { ask(tracks) }, icon = NagIcons.Star) } else null,
        actions.convert?.let { convert -> MenuEntry.Item(res.getString(R.string.nag_convert), { convert(tracks) }, icon = NagIcons.Convert) },
        if (first != null && (share != null || reveal != null)) MenuEntry.Separator else null,
        if (first != null) share?.let { MenuEntry.Item(res.getString(R.string.nag_media_share), { it(tracks) }, icon = NagIcons.Share) } else null,
        if (first != null) reveal?.let { MenuEntry.Item(res.getString(R.string.nag_media_reveal), { it(first) }, icon = NagIcons.Folder) } else null,
    )
}

/**
 * A group's row. 1.1.0 (Ebru): an album's and an artist's start with the cover (52 dp by touch, 36 with a pointer); an
 * album's line is followed by its [ListeningTrace].
 */
@Composable
private fun GroupRow(
    group: MusicGroup,
    view: MusicView,
    selected: Boolean,
    menu: (() -> List<MenuEntry>)? = null,
    stats: Map<String, TrackStats> = emptyMap(),
    playing: String? = null,
    onOpen: () -> Unit,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    // a folder by its last name; its path in the second line
    val title = if (view == MusicView.Folders) group.title?.substringAfterLast('/') ?: groupTitle(group, view) else groupTitle(group, view)
    val covered = view == MusicView.Albums || view == MusicView.Artists
    val touch = dimens.minTarget >= 48.dp
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = dimens.minTarget.coerceAtLeast(48.dp))
                .focusRing(state, shape)
                .background(stateBackground(state, selected), shape)
                .semantics { this.selected = selected }
                .nagClickable(interaction, onOpen)
                .padding(horizontal = if (covered) 6.dp else 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (covered) {
                GroupCover(group, Modifier.size(if (touch) 52.dp else 36.dp), corner = if (touch) 6.dp else 4.dp, read = 64.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
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
                if (view == MusicView.Albums) ListeningTrace(group.tracks, stats, playing, Modifier.padding(top = 4.dp).width(TRACE_WIDTH))
                }
        }
        if (menu != null) MoreButton(groups = { menu().toActionGroups() }, title = title)
    }
}

/**
 * A group's tracks, [shown] in the order the table has: its name and the way back, then the table. [selection] is the
 * browser's, which the inspector follows.
 */
@Composable
private fun TrackPane(
    group: MusicGroup,
    shown: List<Track>,
    state: MusicBrowserState,
    actions: MusicActions,
    showBack: Boolean,
    stats: Map<String, TrackStats>,
    selection: MultiSelection,
    /** A phone's shelf over the rows, scrolling away with them. */
    leading: (@Composable () -> Unit)? = null,
    /** 1.0.3: whether [leading] has something to show now ([DenseTable]'s). */
    leadingPresent: (() -> Boolean)? = null,
    /** 1.1.1: what a phone's chips cover at the list's top. */
    chipsRoom: Dp = 0.dp,
    playing: String? = null,
) {
    val dimens = NagTheme.dimens
    val compact = currentWidthClass() == WidthClass.Compact
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val sort = effectiveSort(state)
    val columns = if (compact) listOf(MusicColumn.Title, MusicColumn.Artist, MusicColumn.Duration) else state.columns
    val tableColumns = rememberMusicColumns(
        columns, stats, onRate = if (actions.askRating != null) ({ track, stars -> actions.rate(listOf(track), stars) }) else null,
        // 1.1.2 (the design's tablet page): an album's tracks as the design draws them
        designed = !compact, playing = playing?.takeIf { uri -> shown.any { it.uri == uri } },
    )
    val labels = MusicColumn.entries.associateWith { stringResource(it.label) }
    val words = rememberColumnMenuWords()
    val res = LocalContext.current.resources
    val modifiers = LocalClickModifiers.current
    val plays = rememberClickPlays()
    val keys = remember(shown) { shown.map { it.uri } }
    LaunchedEffect(keys) { selection.retain(keys) }
    SelectionCommands(selection, keys)
    // 1.0.3 (Desktop Kit 2.3.0): on a phone the first rows arrive in turn when what the pane shows changes under it
    // (another way in); 1.0.8: each group has a pane of its own, which appears with its rows (another group chosen
    // beside the groups comes with the panes' fade, as the kit's list and detail does)
    val appear = rememberListAppear(state.view, group.key)
    // 1.0.3 (Desktop Kit 2.2): Back clears a selection being made: any by touch or in "Seç", several with a mouse
    ClearSelectionOnBack(selection, touchLayout = !pointer)
    // 1.0.8 (Desktop Kit 2.8.0): Müzik's entry tapped again: the tracks go to their top
    val tableState = rememberLazyListState()
    // 1.1.1 (the design's phone page): a phone's rows (a group's own page, or the library's tracks under the header); the
    // same list's state as the table's, so the rows keep their place as the window crosses the width
    if (compact) {
        PhoneTrackList(group, shown, state, actions, stats, selection, tableState, leading, chipsRoom, playing, page = state.view.grouped, appear = appear)
        return
    }
    // 1.1.2 (the design's tablet page): beside the groups the pane runs from 16 dp after their divider to the page's
    // margin; over the covers, between the margins
    Column(Modifier.fillMaxSize().padding(start = if (showBack) WIDE_MARGIN - 8.dp else 16.dp, end = WIDE_MARGIN)) {
        // a group's name, line and trace, the way back over the covers, its play button and its conversion; S-1006: an
        // album's cover changed from the header's menu (a right click, a long press)
        if (state.view.grouped) {
            val coverMenu = rememberCoverMenu(remember(group.key) { group.tracks.firstOrNull()?.takeIf { state.view == MusicView.Albums }?.let(CoverTrack::of) })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (showBack) NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_music_back), { state.group = null }, Modifier.testTag("music-back"))
                ContextMenuArea(entries = coverMenu, modifier = Modifier.weight(1f), accessibilityLabel = stringResource(R.string.nag_cover_menu)) {
                    WideGroupHeader(
                        group, state.view, stats, playing,
                        onPlay = if (shown.isEmpty()) null else ({ actions.play(shown, 0) }),
                    ) {
                        // S-1010: an album's, an artist's, a folder's tracks converted
                        actions.convert?.let { convert ->
                            NagIconButton(NagIcons.Convert, stringResource(R.string.nag_convert), { convert(shown) }, modifier = Modifier.testTag("music-convert"))
                        }
                    }
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
                actions.share?.let { share -> NagButton(stringResource(R.string.nag_media_share), { share(chosen) }) }
            }
        }
        // 1.1.2 (the design): the column titles' rule under the header, none over it
        if (!state.view.grouped) HorizontalRule()
        val columnsLabel = stringResource(R.string.nag_music_columns)
        val reducedMotion = NagTheme.motion.reduced
        OnReselect { if (reducedMotion) tableState.scrollToItem(0) else tableState.animateScrollToItem(0) }
        DenseTable(
            columns = tableColumns,
            rows = shown,
            key = { it.uri },
            state = tableState,
            selectedKeys = selection.keys,
            sort = sort?.let { s -> columns.indexOf(s.column).takeIf { it >= 0 }?.let { TableSort(it, s.ascending) } },
            onSortChange = { s -> state.sort = MusicSort(columns[s.column], s.ascending) },
            // 1.1.5: a click plays as a tap does (Ctrl and Shift select; 1.0.3-1.1.4: a mouse's click selected), and the
            // panel beside the table shows it; Enter plays the focused row, a double click's next click starts nothing
            onRowClick = { track, index ->
                selection.trackClick(track.uri, keys, modifiers, touchLayout = !pointer, plays) { actions.play(shown, index) }
            },
            onRowDoubleClick = if (pointer && !selection.selecting) ({ track, index -> if (plays.fresh(track.uri)) actions.play(shown, index) }) else null,
            rowMenu = { track, index ->
                // on one of several selected rows, the menu is the selection's (Desktop Kit 1.0)
                if (track.uri in selection && selection.count > 1) {
                    tracksMenu(res, shown.filter { it.uri in selection }, actions, rate = true)
                } else {
                    actions.menu(track, play = { actions.play(shown, index) }, select = if (pointer) null else ({ selection.startSelecting(track.uri) }))
                }
            },
            rowMenuLabel = stringResource(R.string.nag_more_actions),
            // 1.0.8 (Desktop Kit 2.8.0): a mouse drags a selected row at once; from another row it draws a selection frame
            rowModifier = { track ->
                actions.rowModifier(track, atOnce = { track.uri in selection }) {
                    if (track.uri in selection && selection.count > 1) shown.filter { it.uri in selection } else listOf(track)
                }
            },
            selection = selection,
            // a title's menu: the columns shown, the one clicked moved (Desktop Kit 1.0)
            headerMenu = if (compact) null else ({ index -> columnMenu(state, labels, words, columns.getOrNull(index)) }),
            headerMenuLabel = columnsLabel,
            // a phone's rows end with "⋯": the same menu as the long press, as a sheet (Desktop Kit 1.0)
            rowMoreButton = compact,
            rowMoreTitle = { it.title },
            // the last rows rest clear of the phone's bars (Desktop Kit 1.0)
            contentPadding = PaddingValues(bottom = 8.dp) + LocalShellBottomPadding.current,
            // tablet and DeX: the rows pass under a glass header; Shift+↑ and Shift+↓ grow the selection
            glassHeader = !compact,
            onExtendSelection = if (compact) null else ({ track, delta -> selection.extend(track.uri, keys, delta) }),
            leading = leading,
            leadingPresent = leadingPresent,
            rowAppear = appear,
            // 1.1.2 (the design): the track that plays in bold
            rowEmphasis = playing?.let { uri -> { track: Track -> track.uri == uri } },
        )
    }
}

/** The table's sort: the whole library by artist when nothing else is asked; a group plays in album order. */
internal fun effectiveSort(state: MusicBrowserState): MusicSort? =
    state.sort ?: if (state.view == MusicView.Tracks) MusicSort(MusicColumn.Artist, true) else null

/** S-808: the columns whose order the statistics decide; sorted by one, the order follows the ratings and counts. */
private val STATS_COLUMNS = setOf(MusicColumn.Rating, MusicColumn.Plays, MusicColumn.Skips, MusicColumn.LastPlayed)

/** What the column chooser's moves and reset say (Desktop Kit 1.0). */
internal class ColumnMenuWords(val moveLeft: String, val moveRight: String, val reset: String)

@Composable
internal fun rememberColumnMenuWords(): ColumnMenuWords {
    val left = stringResource(R.string.nag_columns_move_left)
    val right = stringResource(R.string.nag_columns_move_right)
    val reset = stringResource(R.string.nag_columns_reset)
    return remember(left, right, reset) { ColumnMenuWords(left, right, reset) }
}

/**
 * The columns to show, each with its check; the title always. With [words] (Desktop Kit 1.0) the
 * [clicked] title's column moves left or right, and "Sütunları sıfırla" brings the table's own back.
 */
internal fun columnMenu(
    state: MusicBrowserState,
    labels: Map<MusicColumn, String>,
    words: ColumnMenuWords? = null,
    clicked: MusicColumn? = null,
): List<MenuEntry> {
    val toggles = MusicColumn.entries.filter { it != MusicColumn.Title }.map { column ->
        MenuEntry.Item(labels.getValue(column), { state.toggle(column) }, checked = column in state.columns)
    }
    if (words == null) return toggles
    val at = clicked?.let { state.columns.indexOf(it) } ?: -1
    val moves = if (clicked == null || at < 0) emptyList() else listOf(
        MenuEntry.Separator,
        MenuEntry.Item(words.moveLeft, { state.move(clicked, -1) }, icon = NagIcons.ChevronLeft, enabled = at > 0),
        MenuEntry.Item(words.moveRight, { state.move(clicked, 1) }, icon = NagIcons.ChevronRight, enabled = at < state.columns.lastIndex),
    )
    return toggles + moves + listOf(MenuEntry.Separator, MenuEntry.Item(words.reset, state::resetColumns, enabled = state.columns != MusicColumn.DEFAULT))
}
