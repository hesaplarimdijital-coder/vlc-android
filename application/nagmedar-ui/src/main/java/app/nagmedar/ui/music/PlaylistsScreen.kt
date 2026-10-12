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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
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
import app.nagmedar.ui.R
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
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
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
fun PlaylistsScreen(undo: UndoBarState?, onBrowseMusic: () -> Unit) {
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
    val actions = remember(context, store) { ServiceMusicActions(context, store.media) }
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
    // which playlist the file being made is for, kept through the picker
    var exportingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val exporters = EXPORT_FORMATS.associateWith { format ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType)) { uri ->
            val playlist = state.playlists?.firstOrNull { it.id == exportingId }
            exportingId = null
            if (uri == null || playlist == null) return@rememberLauncherForActivityResult
            scope.launch {
                val saved = store.export(playlist.id, playlist.name, uri, format)
                val text = if (saved) res.getString(R.string.nag_pl_exported, playlist.name, format.name) else res.getString(R.string.nag_pl_export_failed)
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
            exporters.getValue(format).launch("${playlist.name}.${format.extension}")
        },
        onBrowseMusic = onBrowseMusic,
    )
}

/** A playlist's name being asked for: a new one, or one renamed. */
private sealed interface Naming {
    data object New : Naming

    data class Rename(val playlist: PlaylistSummary) : Naming
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
) {
    val res = LocalContext.current.resources
    var naming by remember { mutableStateOf<Naming?>(null) }
    val playlists = state.playlists
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
            Spacer(Modifier.width(NagTheme.dimens.gapS))
            NagButton(stringResource(R.string.nag_pl_new), { naming = Naming.New }, icon = NagIcons.Plus)
        }
        when {
            playlists == null -> Unit
            playlists.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                PlaylistTabs(playlists, state.openId, state::openList, menu)
                HorizontalRule()
                val open = state.open
                if (open != null) PlaylistPane(open, state, actions, undo, { menu(open) }, onBrowseMusic)
            }
        }
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

/** The playlists' tabs, scrolled sideways when they do not fit; each has the playlist's menu. */
@Composable
private fun PlaylistTabs(playlists: List<PlaylistSummary>, openId: Long?, onOpen: (Long) -> Unit, menu: (PlaylistSummary) -> List<MenuEntry>) {
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
                PlaylistTab(playlist, selected = playlist.id == openId) { onOpen(playlist.id) }
            }
        }
    }
}

@Composable
private fun PlaylistTab(playlist: PlaylistSummary, selected: Boolean, onClick: () -> Unit) {
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
            Text(
                playlist.name,
                style = if (selected) NagTheme.type.bodyStrong else NagTheme.type.body,
                color = if (selected) colors.text else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Text("${playlist.count}", style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1)
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
