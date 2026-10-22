package app.nagmedar.ui.streams

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.nagmedar.ui.R
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagTextField
import app.nagmedar.ui.components.SectionCaption
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.input.KeyChord
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.music.AddToPlaylistDialog
import app.nagmedar.ui.music.MediaLibraryPlaylists
import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.util.getFromMl
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager

/** A stream played before, as the medialibrary's network history keeps it: its name and its address. */
data class SavedStream(val id: Long, val name: String, val address: String, val media: MediaWrapper?)

/** Whether a text is an address libVLC can open as a stream, and the address made of it. */
object StreamAddress {
    private val SCHEME = Regex("[A-Za-z][A-Za-z0-9+.-]*")

    /** The address in [text] (spaces around it dropped); null when it has no scheme and host part ("rtsp://…"). */
    fun parse(text: String): String? {
        val address = text.trim()
        if (address.isEmpty() || address.any { it.isWhitespace() }) return null
        val scheme = address.substringBefore("://", missingDelimiterValue = "")
        if (!scheme.matches(SCHEME)) return null
        return address.takeIf { it.length > scheme.length + 3 }
    }
}

/**
 * The asks the Streams page answers from elsewhere (Ctrl+N, the palette): its address field takes
 * the keyboard. The page clears the ask once it has done it.
 */
object StreamsCenter {
    private val focus = MutableStateFlow(false)

    val focusRequested: StateFlow<Boolean> = focus

    fun requestFocus() {
        focus.value = true
    }

    internal fun focused() {
        focus.value = false
    }
}

/** The medialibrary's network history: the streams played, renamed and removed here. */
class StreamHistory(context: Context) {
    private val context = context.applicationContext

    suspend fun list(): List<SavedStream> = context.getFromMl {
        history(Medialibrary.HISTORY_TYPE_NETWORK).map { media ->
            SavedStream(media.id, media.title?.takeIf { it.isNotBlank() } ?: media.location, media.location, media)
        }
    }

    suspend fun rename(stream: SavedStream, name: String) {
        context.getFromMl { stream.media?.rename(name) }
    }

    /** Takes [stream] out of the history; the file or the address itself is not touched. */
    suspend fun remove(stream: SavedStream): Boolean = context.getFromMl { stream.id > 0 && removeExternalMedia(stream.id) }

    /** Puts [stream] back in the history (an undone removal). */
    suspend fun restore(stream: SavedStream): Boolean = context.getFromMl { addToHistory(stream.address, stream.name) }

    /** The medialibrary's id of [address], added as a stream when it has none, for a playlist; 0 when it cannot be. */
    suspend fun idOf(address: String, name: String): Long = context.getFromMl {
        getMedia(address)?.id?.takeIf { it > 0 } ?: addStream(address, name)?.id ?: 0L
    }
}

/** A stream to play: the playback service treats it as one (no parsing as a local file). */
private fun streamMedia(address: String, name: String? = null): MediaWrapper =
    MLServiceLocator.getAbstractMediaWrapper(Uri.parse(address)).apply {
        type = MediaWrapper.TYPE_STREAM
        if (!name.isNullOrBlank() && name != address) title = name
    }

/**
 * Sources › Streams: an address typed or pasted plays or joins the queue (Enter plays); the
 * streams played before are listed by name and address from the medialibrary's network history,
 * each with its menu: play, add to the queue or to a playlist, rename, copy the address, remove
 * (undone from the bar). Ctrl+N brings the keyboard to the address field from anywhere.
 */
@Composable
fun StreamsScreen(undo: UndoBarState? = null) {
    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()
    val history = remember(context) { StreamHistory(context) }
    var reload by remember { mutableIntStateOf(0) }
    // what plays changes the history: a stream played here comes to its top
    val playing by PlaylistManager.currentPlayedMedia.observeAsState()
    val streams by produceState<List<SavedStream>?>(null, reload, playing) { value = runCatching { history.list() }.getOrDefault(emptyList()) }
    LifecycleResumeEffect(history) {
        reload++
        onPauseOrDispose {}
    }
    var address by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedStream?>(null) }
    var toPlaylist by remember { mutableStateOf<SavedStream?>(null) }
    val field = remember { FocusRequester() }
    val focusAsked by StreamsCenter.focusRequested.collectAsState()
    LaunchedEffect(focusAsked) {
        if (focusAsked) {
            runCatching { field.requestFocus() }
            StreamsCenter.focused()
        }
    }
    val open = { enqueue: Boolean ->
        val parsed = StreamAddress.parse(address)
        if (parsed == null) {
            invalid = true
        } else {
            invalid = false
            if (enqueue) {
                MediaUtils.appendMedia(context, streamMedia(parsed))
                Toast.makeText(context, res.getQuantityString(R.plurals.nag_drop_queued, 1, 1), Toast.LENGTH_SHORT).show()
            } else {
                MediaUtils.openMedia(context, streamMedia(parsed))
            }
            address = ""
        }
    }
    val actions = StreamRowActions(
        play = { MediaUtils.openMedia(context, streamMedia(it.address, it.name)) },
        queue = {
            MediaUtils.appendMedia(context, streamMedia(it.address, it.name))
            Toast.makeText(context, res.getQuantityString(R.plurals.nag_drop_queued, 1, 1), Toast.LENGTH_SHORT).show()
        },
        addToPlaylist = { toPlaylist = it },
        rename = { renaming = it },
        copy = { copyAddress(context, it.address) },
        remove = { stream -> removeStream(scope, history, stream, res, undo) { reload++ } },
    )
    Column(Modifier.fillMaxSize()) {
        val count = streams?.size
        PageHeader(
            title = stringResource(R.string.nag_nav_streams),
            subtitle = count?.let { pluralStringResource(R.plurals.nag_streams_count, it, it) },
        )
        HorizontalRule()
        OpenBar(
            address = address,
            onAddress = {
                address = it
                invalid = false
            },
            invalid = invalid,
            field = field,
            onPaste = {
                val pasted = clipboardText(context)
                if (pasted.isNullOrBlank()) {
                    Toast.makeText(context, R.string.nag_streams_clipboard_empty, Toast.LENGTH_SHORT).show()
                } else {
                    address = pasted.trim()
                    invalid = false
                }
            },
            onPlay = { open(false) },
            onQueue = { open(true) },
        )
        HorizontalRule()
        val list = streams
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                list == null -> Unit
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    StatePanel(
                        tone = StateTone.Neutral,
                        icon = NagIcons.Streams,
                        title = stringResource(R.string.nag_streams_empty_title),
                        description = stringResource(R.string.nag_streams_empty_desc),
                        modifier = Modifier.testTag("streams-empty"),
                    )
                }
                else -> StreamTable(list, actions)
            }
        }
    }
    renaming?.let { stream ->
        RenameStreamDialog(
            stream = stream,
            onRename = { name ->
                scope.launch {
                    history.rename(stream, name)
                    reload++
                }
            },
            onDismiss = { renaming = null },
        )
    }
    toPlaylist?.let { stream -> StreamToPlaylist(stream, history, scope) { toPlaylist = null } }
}

/** What a saved stream's row and menu do. */
private class StreamRowActions(
    val play: (SavedStream) -> Unit,
    val queue: (SavedStream) -> Unit,
    val addToPlaylist: (SavedStream) -> Unit,
    val rename: (SavedStream) -> Unit,
    val copy: (SavedStream) -> Unit,
    val remove: (SavedStream) -> Unit,
)

/** The address field and what is done with it. */
@Composable
private fun OpenBar(
    address: String,
    onAddress: (String) -> Unit,
    invalid: Boolean,
    field: FocusRequester,
    onPaste: () -> Unit,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
) {
    val dimens = NagTheme.dimens
    val compact = currentWidthClass() == WidthClass.Compact
    Column(
        Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapM),
        verticalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        Text(stringResource(R.string.nag_streams_open_title), style = NagTheme.type.title, color = NagTheme.colors.text)
        val buttons = @Composable {
            NagButton(stringResource(R.string.nag_streams_paste), onPaste, modifier = Modifier.testTag("streams-paste"))
            NagButton(stringResource(R.string.nag_add_to_queue), onQueue, icon = NagIcons.Queue, enabled = address.isNotBlank())
            NagButton(stringResource(R.string.nag_play), onPlay, icon = NagIcons.Play, primary = true, enabled = address.isNotBlank(),
                modifier = Modifier.testTag("streams-play"))
        }
        val input = @Composable { modifier: Modifier ->
            Box(modifier) {
                NagTextField(
                    value = address,
                    onValueChange = onAddress,
                    label = stringResource(R.string.nag_streams_address),
                    placeholder = stringResource(R.string.nag_streams_address_hint),
                    onDone = onPlay,
                    modifier = Modifier.focusRequester(field).testTag("streams-address"),
                )
            }
        }
        if (compact) {
            input(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) { buttons() }
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                input(Modifier.weight(1f).widthIn(max = 720.dp))
                buttons()
            }
        }
        if (invalid) {
            Text(
                stringResource(R.string.nag_streams_invalid),
                style = NagTheme.type.labelSmall,
                color = NagTheme.colors.textSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("streams-invalid"),
            )
        } else {
            Text(stringResource(R.string.nag_streams_open_hint), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
        }
    }
}

/** The streams played before: a click selects and a double click or Enter plays (a tap plays by touch). */
@Composable
private fun StreamTable(streams: List<SavedStream>, actions: StreamRowActions) {
    val res = LocalContext.current.resources
    val dimens = NagTheme.dimens
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val compact = currentWidthClass() == WidthClass.Compact
    var selected by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        SectionCaption(stringResource(R.string.nag_streams_recent), Modifier.padding(start = dimens.pagePadding, end = dimens.pagePadding, top = dimens.gapM))
        val name = TableColumn<SavedStream>(
            title = stringResource(R.string.nag_streams_column_name),
            width = ColumnWidth.Weight(1f),
            sortable = false,
            content = { stream, isSelected -> StreamName(stream, isSelected, showAddress = compact) },
            value = { it.name },
        )
        val address = TableColumn<SavedStream>(
            title = stringResource(R.string.nag_streams_column_address),
            width = ColumnWidth.Weight(1.2f),
            mono = true,
            sortable = false,
            value = { it.address },
        )
        DenseTable(
            columns = if (compact) listOf(name) else listOf(name, address),
            rows = streams,
            key = { "${it.id}-${it.address}" },
            selectedKey = selected,
            onRowClick = { stream, _ -> if (pointer) selected = "${stream.id}-${stream.address}" else actions.play(stream) },
            onRowDoubleClick = if (pointer) { stream, _ -> actions.play(stream) } else null,
            rowMenu = { stream, _ -> streamMenu(res, stream, actions) },
            rowMenuLabel = stringResource(R.string.nag_more_actions),
            modifier = Modifier.testTag("streams-table"),
        )
    }
}

@Composable
private fun StreamName(stream: SavedStream, selected: Boolean, showAddress: Boolean) {
    val colors = NagTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(NagIcons.Streams, contentDescription = null, tint = if (selected) colors.onSelection else colors.accentText, modifier = Modifier.size(NagTheme.dimens.icon))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(stream.name, style = NagTheme.type.body, color = if (selected) colors.onSelection else colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showAddress && stream.name != stream.address) {
                Text(stream.address, style = NagTheme.type.labelSmall, color = if (selected) colors.onSelection else colors.textTertiary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun streamMenu(res: Resources, stream: SavedStream, actions: StreamRowActions): List<MenuEntry> = listOf(
    MenuEntry.Item(res.getString(R.string.nag_play), { actions.play(stream) }, icon = NagIcons.Play, shortcut = KeyChord(Key.Enter).label(res)),
    MenuEntry.Item(res.getString(R.string.nag_add_to_queue), { actions.queue(stream) }, icon = NagIcons.Queue),
    MenuEntry.Item(res.getString(R.string.nag_pl_add), { actions.addToPlaylist(stream) }, icon = NagIcons.PlaylistAdd),
    MenuEntry.Separator,
    MenuEntry.Item(res.getString(R.string.nag_streams_rename), { actions.rename(stream) }, enabled = stream.media != null),
    MenuEntry.Item(res.getString(R.string.nag_streams_copy), { actions.copy(stream) }),
    MenuEntry.Separator,
    MenuEntry.Item(res.getString(R.string.nag_streams_remove), { actions.remove(stream) }, icon = NagIcons.Delete, destructive = true),
)

private fun removeStream(scope: CoroutineScope, history: StreamHistory, stream: SavedStream, res: Resources, undo: UndoBarState?, changed: () -> Unit) {
    scope.launch {
        if (!history.remove(stream)) return@launch
        changed()
        undo?.show(res.getString(R.string.nag_streams_removed, stream.name), res.getString(R.string.nag_sc_undo), ShortcutRegistry.chordLabel(ShortcutId.Undo, res)) {
            scope.launch {
                history.restore(stream)
                changed()
            }
        }
    }
}

private fun copyAddress(context: Context, address: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.nag_streams_address), address))
    Toast.makeText(context, R.string.nag_streams_copied, Toast.LENGTH_SHORT).show()
}

/** The clipboard's text, read only when the person asks for it (the Paste button). */
private fun clipboardText(context: Context): String? = runCatching {
    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()

/** A saved stream's name asked in a small window; Enter keeps it, Esc leaves. */
@Composable
private fun RenameStreamDialog(stream: SavedStream, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(stream) { mutableStateOf(stream.name) }
    val save = {
        if (name.isNotBlank()) {
            onRename(name.trim())
            onDismiss()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.nag_streams_rename_title), Modifier.testTag("streams-rename")) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                NagTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.nag_streams_column_name),
                    onDone = save,
                    modifier = Modifier.focusRequester(focus),
                )
                Text(stream.address, style = NagTheme.type.mono, color = NagTheme.colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
                    NagButton(stringResource(R.string.nag_streams_save), { save() }, primary = true, enabled = name.isNotBlank())
                }
            }
        }
    }
}

/** "Listeye ekle…" for a stream: the medialibrary's playlists, or a new one; the stream is the medialibrary's first. */
@Composable
private fun StreamToPlaylist(stream: SavedStream, history: StreamHistory, scope: CoroutineScope, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val res = context.resources
    val store = remember(context) { MediaLibraryPlaylists(context.applicationContext) }
    val playlists by produceState<List<PlaylistSummary>?>(null, store) { value = store.playlists() }
    val list = playlists ?: return
    val added = { name: String -> Toast.makeText(context, res.getQuantityString(R.plurals.nag_pl_added, 1, 1, name), Toast.LENGTH_SHORT).show() }
    fun add(name: String, into: suspend (Long) -> Boolean) {
        scope.launch {
            val id = stream.id.takeIf { it > 0 } ?: history.idOf(stream.address, stream.name)
            if (id > 0 && into(id)) added(name) else Toast.makeText(context, R.string.nag_streams_playlist_failed, Toast.LENGTH_LONG).show()
        }
    }
    AddToPlaylistDialog(
        count = 1,
        playlists = list,
        onChoose = { playlist ->
            add(playlist.name) { id ->
                store.append(playlist.id, listOf(id))
                true
            }
        },
        onCreate = { name -> add(name) { id -> store.create(name, listOf(id)) != null } },
        onDismiss = onDismiss,
    )
}
