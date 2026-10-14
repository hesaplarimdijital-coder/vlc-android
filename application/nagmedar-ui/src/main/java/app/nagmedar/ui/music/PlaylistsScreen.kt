package app.nagmedar.ui.music

import android.content.res.Resources
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.IconMenuButton
import app.nagmedar.ui.components.LocalClickModifiers
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MultiSelection
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SelectionBar
import app.nagmedar.ui.components.SelectionCommands
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.rememberMultiSelection
import app.nagmedar.ui.components.rememberReorderState
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.library.MediaRepository
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.library.rememberScanState
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.stats.TrackStatsIndex
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.launch

/** The order of the formats in the export menus: UTF-8's M3U first, the old M3U last. */
private val EXPORT_FORMATS = listOf(PlaylistFormat.M3U8, PlaylistFormat.XSPF, PlaylistFormat.PLS, PlaylistFormat.M3U)

/**
 * S-804: the playlists of the medialibrary, a tab each: the open one's lines in the dense table,
 * dragged by their handles or moved with Alt+↑/↓, taken out with Delete (the undo bar brings them
 * back); a playlist made here, renamed, deleted, read from an M3U, M3U8, PLS or XSPF file or
 * written to one through the system's picker.
 */
@Composable
fun PlaylistsScreen(repository: MediaRepository, undo: UndoBarState?, onBrowseMusic: () -> Unit) {
    val context = LocalContext.current
    val res = context.resources
    val store = remember(context) { MediaLibraryPlaylists(context.applicationContext) }
    DisposableEffect(store) {
        val stop = store.listen()
        onDispose { stop() }
    }
    val scope = rememberCoroutineScope()
    val state = remember(store, scope) { PlaylistsState(store, scope) }
    val changes by store.changes.collectAsState()
    LaunchedEffect(changes) { state.reload() }
    // S-805: the smart lists, and the library, the ratings and the plays they read
    val smartStore = remember(context) { DatabaseSmartLists(context.applicationContext) }
    val smart = remember(smartStore, scope) { SmartListsState(smartStore, scope) }
    LaunchedEffect(smart) { smart.follow() }
    val scan = rememberScanState()
    val media = rememberMusicMedia(repository, scan.active)
    val tech by TrackTechIndex.tech.collectAsState()
    val library = remember(media, tech) { media?.map { item -> item.toTrack(item.uri?.toString()?.let { tech[CueUri.file(it)] }) } }
    val byUri = remember(media) { media.orEmpty().associateBy { it.uri?.toString().orEmpty() } }
    val stats by TrackStatsIndex.stats.collectAsState()
    LaunchedEffect(Unit) { TrackStatsIndex.follow(context) }
    // S-808: "Puan ver…" from a line's menu
    var rating by remember { mutableStateOf<List<Track>?>(null) }
    val actions = remember(context, store, byUri) {
        ServiceMusicActions(context, lookup = { uri -> store.media[uri] ?: byUri[uri] }, onAskRating = { tracks -> rating = tracks })
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = store.import(uri, state.playlists.orEmpty().map { it.name })
            if (imported == null) {
                Toast.makeText(context, res.getString(R.string.nag_pl_import_failed), Toast.LENGTH_LONG).show()
                return@launch
            }
            state.reload(prefer = imported.id)
            val text = listOfNotNull(
                res.getQuantityString(R.plurals.nag_pl_imported, imported.found, imported.found, imported.name),
                if (imported.missing > 0) res.getQuantityString(R.plurals.nag_pl_import_missing, imported.missing, imported.missing) else null,
            ).joinToString("; ")
            // taken back, the new playlist goes
            undo?.show(text, res.getString(R.string.nag_sc_undo), ShortcutRegistry.chordLabel(ShortcutId.Undo, res)) { state.delete(imported.id) { } }
                ?: Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
    // which playlist (or, S-805, which smart list) the file being made is for, kept through the picker
    var exportingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var exportingSmart by rememberSaveable { mutableStateOf(false) }
    val exporters = EXPORT_FORMATS.associateWith { format ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType)) { uri ->
            val id = exportingId
            val ofSmart = exportingSmart
            exportingId = null
            exportingSmart = false
            if (uri == null || id == null) return@rememberLauncherForActivityResult
            scope.launch {
                val (name, saved) = if (ofSmart) {
                    // the smart list as it is now
                    val list = smart.lists?.firstOrNull { it.id == id } ?: return@launch
                    val tracks = library?.let { list.select(it, stats, System.currentTimeMillis(), res.locale()) }.orEmpty()
                    list.name to store.exportTracks(tracks, list.name, uri, format)
                } else {
                    val playlist = state.playlists?.firstOrNull { it.id == id } ?: return@launch
                    playlist.name to store.export(playlist.id, playlist.name, uri, format)
                }
                val text = if (saved) res.getString(R.string.nag_pl_exported, name, format.name) else res.getString(R.string.nag_pl_export_failed)
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }
        }
    }
    PlaylistsBrowser(
        state = state,
        actions = actions,
        undo = undo,
        onImport = { importer.launch(arrayOf("*/*")) },
        onExport = { playlist, format ->
            exportingId = playlist.id
            exportingSmart = false
            exporters.getValue(format).launch("${playlist.name}.${format.extension}")
        },
        onBrowseMusic = onBrowseMusic,
        smart = smart,
        library = library,
        stats = stats,
        onExportSmart = { list, format ->
            exportingId = list.id
            exportingSmart = true
            exporters.getValue(format).launch("${list.name}.${format.extension}")
        },
    )
    rating?.let { tracks -> RatingDialog(tracks, stats, onRate = { stars -> actions.rate(tracks, stars) }, onDismiss = { rating = null }) }
}

/** A playlist's name being asked for: a new one, or one renamed. */
private sealed interface Naming {
    data object New : Naming

    data class Rename(val playlist: PlaylistSummary) : Naming
}

/** S-805: a smart list being made, or changed. */
private sealed interface SmartEditing {
    data object New : SmartEditing

    data class Edit(val list: SmartList) : SmartEditing
}

/** The playlists below their header; [state] in the app is the medialibrary's, in tests a list's. */
@Composable
fun PlaylistsBrowser(
    state: PlaylistsState,
    actions: MusicActions,
    modifier: Modifier = Modifier,
    undo: UndoBarState? = null,
    onImport: () -> Unit = {},
    onExport: (PlaylistSummary, PlaylistFormat) -> Unit = { _, _ -> },
    onBrowseMusic: () -> Unit = {},
    /** S-805: the smart lists; null where there are none. */
    smart: SmartListsState? = null,
    /** S-805: the music library the smart lists take their tracks from; null while it is read. */
    library: List<Track>? = null,
    stats: Map<String, TrackStats> = emptyMap(),
    /** The smart lists' clock ("in the last 30 days"). */
    now: () -> Long = System::currentTimeMillis,
    onExportSmart: (SmartList, PlaylistFormat) -> Unit = { _, _ -> },
) {
    val res = LocalContext.current.resources
    val locale = res.locale()
    var naming by remember { mutableStateOf<Naming?>(null) }
    var editing by remember { mutableStateOf<SmartEditing?>(null) }
    var openSmart by rememberSaveable { mutableStateOf<Long?>(null) }
    val playlists = state.playlists
    val smartLists = smart?.lists.orEmpty()
    // read again as the library, the ratings and the plays change
    val clock = remember(library, stats) { now() }
    val smartTracks = remember(smartLists, library, stats, clock, locale) {
        smartLists.associate { list -> list.id to library?.let { list.select(it, stats, clock, locale) } }
    }
    // a smart list open, or the first one when no playlist is
    val shownSmart = smartLists.firstOrNull { it.id == openSmart } ?: smartLists.firstOrNull().takeIf { state.open == null }
    val deleteSmart = { list: SmartList ->
        openSmart = null
        smart?.delete(list) { restore ->
            undo?.show(res.getString(R.string.nag_pl_deleted, list.name), res.getString(R.string.nag_sc_undo), ShortcutRegistry.chordLabel(ShortcutId.Undo, res), restore)
        }
    }
    val smartMenu = { list: SmartList -> smartListMenu(res, list, onEdit = { editing = SmartEditing.Edit(list) }, onExport = onExportSmart, onDelete = { deleteSmart(it) }) }
    val delete = { playlist: PlaylistSummary ->
        state.delete(playlist.id) { restore ->
            undo?.show(res.getString(R.string.nag_pl_deleted, playlist.name), res.getString(R.string.nag_sc_undo), ShortcutRegistry.chordLabel(ShortcutId.Undo, res), restore)
        }
    }
    val menu = { playlist: PlaylistSummary -> playlistMenu(res, playlist, onRename = { naming = Naming.Rename(playlist) }, onExport = onExport, onDelete = delete) }
    Column(modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.nag_nav_playlists),
            subtitle = playlists?.let { pluralStringResource(R.plurals.nag_pl_count, it.size, it.size) },
        ) {
            NagButton(stringResource(R.string.nag_sc_import), onImport, icon = NagIcons.Folder)
            if (smart != null) {
                Spacer(Modifier.width(NagTheme.dimens.gapS))
                NagButton(stringResource(R.string.nag_smart_new), { editing = SmartEditing.New }, icon = NagIcons.SmartList, modifier = Modifier.testTag("smart-new"))
            }
            Spacer(Modifier.width(NagTheme.dimens.gapS))
            NagButton(stringResource(R.string.nag_pl_new), { naming = Naming.New }, icon = NagIcons.Plus)
        }
        when {
            playlists == null -> Unit
            playlists.isEmpty() && smartLists.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StatePanel(
                    tone = StateTone.Neutral,
                    icon = NagIcons.PlaylistAdd,
                    title = stringResource(R.string.nag_pl_empty_title),
                    description = stringResource(R.string.nag_pl_empty_desc),
                    primary = StateAction(stringResource(R.string.nag_pl_new)) { naming = Naming.New },
                    secondary = StateAction(stringResource(R.string.nag_sc_import), onImport),
                )
            }
            else -> {
                PlaylistTabs(
                    playlists = playlists,
                    openId = state.openId.takeIf { shownSmart == null },
                    onOpen = { id ->
                        openSmart = null
                        state.openList(id)
                    },
                    menu = menu,
                    smartLists = smartLists,
                    smartCounts = smartTracks.mapValues { it.value?.size },
                    openSmart = shownSmart?.id,
                    onOpenSmart = { id -> openSmart = id },
                    smartMenu = smartMenu,
                )
                HorizontalRule()
                val open = state.open
                when {
                    shownSmart != null -> SmartPane(shownSmart, smartTracks[shownSmart.id], stats, actions, onEdit = { editing = SmartEditing.Edit(shownSmart) }, menu = { smartMenu(shownSmart) })
                    open != null -> PlaylistPane(open, state, actions, undo, { menu(open) }, onBrowseMusic)
                }
            }
        }
    }
    when (val asking = editing) {
        null -> Unit
        SmartEditing.New -> SmartListDialog(null, library, stats, clock, onSave = { list -> smart?.save(list) { id -> openSmart = id } }, onDismiss = { editing = null })
        is SmartEditing.Edit -> SmartListDialog(asking.list, library, stats, clock, onSave = { list -> smart?.save(list) }, onDismiss = { editing = null })
    }
    when (val asking = naming) {
        null -> Unit
        Naming.New -> PlaylistNameDialog(
            title = stringResource(R.string.nag_pl_new),
            initial = PlaylistsState.uniqueName(stringResource(R.string.nag_pl_new), playlists.orEmpty().map { it.name }),
            confirm = stringResource(R.string.nag_pl_create),
            onConfirm = { name -> state.create(name) },
            onDismiss = { naming = null },
        )
        is Naming.Rename -> PlaylistNameDialog(
            title = stringResource(R.string.nag_pl_rename_title),
            initial = asking.playlist.name,
            confirm = stringResource(R.string.nag_bm_save),
            onConfirm = { name -> state.rename(asking.playlist.id, name) },
            onDismiss = { naming = null },
        )
    }
}

/** S-805: a smart list's menu: its tab's and its pane's. */
private fun smartListMenu(
    res: Resources,
    list: SmartList,
    onEdit: () -> Unit,
    onExport: (SmartList, PlaylistFormat) -> Unit,
    onDelete: (SmartList) -> Unit,
): List<MenuEntry> = buildList {
    add(MenuEntry.Item(res.getString(R.string.nag_smart_edit), onEdit, icon = NagIcons.SmartList))
    add(MenuEntry.Separator)
    EXPORT_FORMATS.forEach { format -> add(MenuEntry.Item(res.getString(R.string.nag_pl_export_as, format.name), { onExport(list, format) })) }
    add(MenuEntry.Separator)
    add(MenuEntry.Item(res.getString(R.string.nag_smart_delete), { onDelete(list) }, icon = NagIcons.Delete, destructive = true))
}

/** A playlist's menu: its tab's and its pane's. */
private fun playlistMenu(
    res: Resources,
    playlist: PlaylistSummary,
    onRename: () -> Unit,
    onExport: (PlaylistSummary, PlaylistFormat) -> Unit,
    onDelete: (PlaylistSummary) -> Unit,
): List<MenuEntry> = buildList {
    add(MenuEntry.Item(res.getString(R.string.nag_pl_rename), onRename))
    add(MenuEntry.Separator)
    EXPORT_FORMATS.forEach { format -> add(MenuEntry.Item(res.getString(R.string.nag_pl_export_as, format.name), { onExport(playlist, format) })) }
    add(MenuEntry.Separator)
    add(MenuEntry.Item(res.getString(R.string.nag_pl_delete), { onDelete(playlist) }, icon = NagIcons.Delete, destructive = true))
}

/** The playlists' tabs, then the smart lists' (S-805), scrolled sideways when they do not fit; each has its list's menu. */
@Composable
private fun PlaylistTabs(
    playlists: List<PlaylistSummary>,
    openId: Long?,
    onOpen: (Long) -> Unit,
    menu: (PlaylistSummary) -> List<MenuEntry>,
    smartLists: List<SmartList> = emptyList(),
    smartCounts: Map<Long, Int?> = emptyMap(),
    openSmart: Long? = null,
    onOpenSmart: (Long) -> Unit = {},
    smartMenu: (SmartList) -> List<MenuEntry> = { emptyList() },
) {
    val dimens = NagTheme.dimens
    val moreActions = stringResource(R.string.nag_more_actions)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = dimens.pagePadding - 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        playlists.forEach { playlist ->
            ContextMenuArea(entries = { menu(playlist) }, accessibilityLabel = moreActions) {
                PlaylistTab(playlist.name, playlist.count, selected = playlist.id == openId) { onOpen(playlist.id) }
            }
        }
        smartLists.forEach { list ->
            ContextMenuArea(entries = { smartMenu(list) }, accessibilityLabel = moreActions) {
                PlaylistTab(list.name, smartCounts[list.id], selected = list.id == openSmart, icon = NagIcons.SmartList) { onOpenSmart(list.id) }
            }
        }
    }
}

@Composable
private fun PlaylistTab(name: String, count: Int?, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(topStart = dimens.radiusControl, topEnd = dimens.radiusControl)
    Column(
        Modifier
            .width(IntrinsicSize.Max)
            .heightIn(min = dimens.minTarget.coerceAtLeast(36.dp))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .nagClickable(interaction, onClick, role = Role.Tab),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (selected) colors.accent else colors.textTertiary, modifier = Modifier.size(NagTheme.dimens.icon))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                name,
                style = if (selected) NagTheme.type.bodyStrong else NagTheme.type.body,
                color = if (selected) colors.text else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (count != null) {
                Spacer(Modifier.width(6.dp))
                Text("$count", style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) colors.accent else Color.Transparent))
    }
}

/** The open playlist: its name, play and shuffle, its menu, the selection's commands, its lines. */
@Composable
private fun PlaylistPane(
    open: PlaylistSummary,
    state: PlaylistsState,
    actions: MusicActions,
    undo: UndoBarState?,
    menu: () -> List<MenuEntry>,
    onBrowseMusic: () -> Unit,
) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val compact = currentWidthClass() == WidthClass.Compact
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val lines = state.lines
    val tracks = remember(lines) { lines.map { it.track } }
    val keys = remember(lines) { lines.map { it.key } }
    val selection = rememberMultiSelection()
    LaunchedEffect(open.id) { selection.clear() }
    LaunchedEffect(keys) { selection.retain(keys) }
    SelectionCommands(selection, keys)
    BackHandler(enabled = selection.selecting) { selection.clear() }
    val undoLabel = stringResource(R.string.nag_sc_undo)
    val remove = { taken: Set<Any> ->
        val playlist = state.openId
        val gone = state.remove(taken)
        if (playlist != null && gone.isNotEmpty()) {
            selection.clear()
            undo?.show(
                res.getQuantityString(R.plurals.nag_pl_removed, gone.size, gone.size, open.name),
                undoLabel,
                ShortcutRegistry.chordLabel(ShortcutId.Undo, res),
            ) { state.restore(gone, playlist) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
        ) {
            Column(Modifier.weight(1f)) {
                Text(open.name, style = NagTheme.type.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val length = tracks.sumOf { it.durationMs.coerceAtLeast(0) }
                Text(
                    listOfNotNull(pluralStringResource(R.plurals.nag_tracks_count, tracks.size, tracks.size), length.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" · "),
                    style = NagTheme.type.labelSmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                )
            }
            NagButton(stringResource(R.string.nag_play), { actions.play(tracks, 0) }, icon = NagIcons.Play, primary = true, enabled = tracks.isNotEmpty())
            if (!compact) NagButton(stringResource(R.string.nag_shuffle), { actions.play(tracks.shuffled(), 0) }, icon = NagIcons.Shuffle, enabled = tracks.size > 1)
            IconMenuButton(NagIcons.MoreHorizontal, stringResource(R.string.nag_more_actions), menu)
        }
        if (selection.count > 1 || selection.selecting) {
            val chosen = lines.filter { it.key in selection }
            SelectionBar(
                text = pluralStringResource(R.plurals.nag_selected_tracks, chosen.size, chosen.size),
                onClear = selection::clear,
                clearLabel = stringResource(R.string.nag_selection_clear),
                modifier = Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs),
            ) {
                NagButton(stringResource(R.string.nag_selection_play), { actions.play(chosen.map { it.track }, 0) }, primary = true)
                if (!compact) NagButton(stringResource(R.string.nag_play_next), { actions.playNext(chosen.map { it.track }) })
                NagButton(stringResource(R.string.nag_pl_remove), { remove(chosen.map { it.key }.toSet()) })
            }
        }
        HorizontalRule()
        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StatePanel(
                    tone = StateTone.Neutral,
                    icon = NagIcons.Music,
                    title = stringResource(R.string.nag_pl_list_empty_title),
                    description = stringResource(R.string.nag_pl_list_empty_desc),
                    primary = StateAction(stringResource(R.string.nag_pl_browse_music), onBrowseMusic),
                )
            }
        } else {
            PlaylistTable(state, actions, selection, compact, pointer, remove)
        }
    }
}

/** The open playlist's lines: by its handle a line is dragged, by keys and its menu moved and taken out. */
@Composable
private fun PlaylistTable(
    state: PlaylistsState,
    actions: MusicActions,
    selection: MultiSelection,
    compact: Boolean,
    pointer: Boolean,
    remove: (Set<Any>) -> Unit,
) {
    val res = LocalContext.current.resources
    val modifiers = LocalClickModifiers.current
    val lines = state.lines
    val tracks = remember(lines) { lines.map { it.track } }
    val keys = remember(lines) { lines.map { it.key } }
    val musicColumns = rememberMusicColumns(
        if (compact) listOf(MusicColumn.Title, MusicColumn.Artist, MusicColumn.Duration)
        else listOf(MusicColumn.Title, MusicColumn.Artist, MusicColumn.Album, MusicColumn.Format, MusicColumn.Duration),
    )
    val columns = remember(musicColumns) {
        musicColumns.map { column ->
            TableColumn<PlaylistLine>(column.title, column.width, alignEnd = column.alignEnd, mono = column.mono, sortable = false) { line -> column.value(line.track) }
        }
    }
    val list = rememberLazyListState()
    val reorder = rememberReorderState(list) { from, to -> state.move(from, to) }
    val up = stringResource(R.string.nag_queue_move_up)
    val down = stringResource(R.string.nag_queue_move_down)
    val takeOut = stringResource(R.string.nag_pl_remove)
    // a line's index, for its keys and menu
    val indices = remember(lines) { lines.withIndex().associate { it.value.key to it.index } }
    // Delete takes the selection out when the line is in it, else the line
    fun taken(line: PlaylistLine): Set<Any> = if (line.key in selection && selection.count > 1) selection.keys else setOf(line.key)
    DenseTable(
        columns = columns,
        rows = lines,
        key = { it.key },
        state = list,
        reorder = reorder,
        handleLabel = { line -> res.getString(R.string.nag_queue_handle, line.track.title) },
        selectedKeys = selection.keys,
        onRowClick = { line, index ->
            when {
                selection.selecting -> selection.toggle(line.key)
                pointer -> {
                    val (ctrl, shift) = modifiers.take()
                    selection.click(line.key, keys, ctrl, shift)
                }
                else -> actions.play(tracks, index)
            }
        },
        onRowDoubleClick = if (pointer && !selection.selecting) ({ _, index -> actions.play(tracks, index) }) else null,
        rowMenu = { line, index ->
            actions.menu(line.track, play = { actions.play(tracks, index) }, select = if (pointer) null else ({ selection.startSelecting(line.key) })) +
                listOf(
                    MenuEntry.Separator,
                    MenuEntry.Item(up, { state.move(index, index - 1) }, shortcut = "Alt+↑", enabled = index > 0),
                    MenuEntry.Item(down, { state.move(index, index + 1) }, shortcut = "Alt+↓", enabled = index < lines.lastIndex),
                    MenuEntry.Item(takeOut, { remove(taken(line)) }, shortcut = "Del", icon = NagIcons.Delete, destructive = true),
                )
        },
        rowMenuLabel = stringResource(R.string.nag_more_actions),
        rowModifier = { line ->
            val index = indices[line.key] ?: 0
            Modifier
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when {
                        event.key == Key.Delete || event.key == Key.Backspace -> remove(taken(line))
                        event.isAltPressed && !event.isCtrlPressed && event.key == Key.DirectionUp -> state.move(index, index - 1)
                        event.isAltPressed && !event.isCtrlPressed && event.key == Key.DirectionDown -> state.move(index, index + 1)
                        else -> return@onKeyEvent false
                    }
                    true
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(up) { state.move(index, index - 1); true },
                        CustomAccessibilityAction(down) { state.move(index, index + 1); true },
                        CustomAccessibilityAction(takeOut) { remove(setOf(line.key)); true },
                    )
                }
                .then(actions.rowModifier(line.track) { listOf(line.track) })
        },
    )
}
