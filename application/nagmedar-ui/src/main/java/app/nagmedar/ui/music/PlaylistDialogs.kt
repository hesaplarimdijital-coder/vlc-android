package app.nagmedar.ui.music

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * S-804: a playlist's name asked in a small window: a new list's, or one renamed. The name comes
 * selected; Enter keeps it, Escape leaves.
 */
@Composable
fun PlaylistNameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) { PlaylistNameSurface(title, initial, confirm, onConfirm, onDismiss) }
    }
}

@Composable
fun PlaylistNameSurface(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = NagTheme.colors
    val focusManager = LocalFocusManager.current
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val save = {
        if (value.text.isNotBlank()) {
            onConfirm(value.text.trim())
            onDismiss()
        }
    }
    DialogCard(title, Modifier.testTag("playlist-name-dialog")) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val label = stringResource(R.string.nag_pl_name)
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = NagTheme.type.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.accent, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .focusRequester(focus)
                .semantics { contentDescription = label }
                .testTag("playlist-name-field")
                .tabMovesFocus(focusManager)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            save()
                            true
                        }
                        Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                },
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
            NagButton(confirm, { save() }, primary = true, enabled = value.text.isNotBlank(), modifier = Modifier.testTag("playlist-name-save"))
        }
    }
}

/**
 * S-804: tracks of the library put in a playlist: one of [playlists], or a new one named in the
 * same window. [count] tracks are asked for.
 */
@Composable
fun AddToPlaylistDialog(
    count: Int,
    playlists: List<PlaylistSummary>,
    onChoose: (PlaylistSummary) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) { AddToPlaylistSurface(count, playlists, onChoose, onCreate, onDismiss) }
    }
}

@Composable
fun AddToPlaylistSurface(
    count: Int,
    playlists: List<PlaylistSummary>,
    onChoose: (PlaylistSummary) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NagTheme.colors
    val newName = stringResource(R.string.nag_pl_new)
    var naming by remember { mutableStateOf(playlists.isEmpty()) }
    if (naming) {
        PlaylistNameSurface(
            title = stringResource(R.string.nag_pl_new),
            initial = PlaylistsState.uniqueName(newName, playlists.map { it.name }),
            confirm = stringResource(R.string.nag_pl_create),
            onConfirm = onCreate,
            onDismiss = onDismiss,
        )
        return
    }
    DialogCard(stringResource(R.string.nag_pl_add_title), Modifier.testTag("playlist-add-dialog")) {
        Text(pluralStringResource(R.plurals.nag_selected_tracks, count, count), style = NagTheme.type.label, color = colors.textSecondary)
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            playlists.forEach { playlist ->
                PlaylistChoice(playlist) {
                    onChoose(playlist)
                    onDismiss()
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            NagButton(stringResource(R.string.nag_pl_new), { naming = true })
            NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
        }
    }
}

@Composable
private fun PlaylistChoice(playlist: PlaylistSummary, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTarget.coerceAtLeast(40.dp))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(playlist.name, style = NagTheme.type.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            listOfNotNull(pluralStringResource(R.plurals.nag_tracks_count, playlist.count, playlist.count), playlist.durationMs.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" · "),
            style = NagTheme.type.labelSmall,
            color = colors.textTertiary,
            maxLines = 1,
        )
    }
}

/**
 * S-804: "Listeye ekle…" of the music library on the medialibrary's playlists: [tracks] that are
 * in the medialibrary go to the playlist chosen or made; the work runs in [scope], which outlives
 * the window.
 */
@Composable
internal fun AddToPlaylistHost(tracks: List<Track>, scope: CoroutineScope, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val res = context.resources
    val store = remember(context) { MediaLibraryPlaylists(context.applicationContext) }
    val playlists by produceState<List<PlaylistSummary>?>(null, store) { value = store.playlists() }
    // picked folders' files are not the medialibrary's: they cannot go in its playlists
    val ids = remember(tracks) { tracks.map { it.id }.filter { it > 0 } }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) {
            Toast.makeText(context, res.getString(R.string.nag_pl_add_none), Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }
    val list = playlists
    if (list == null || ids.isEmpty()) return
    val added = { name: String -> Toast.makeText(context, res.getQuantityString(R.plurals.nag_pl_added, ids.size, ids.size, name), Toast.LENGTH_SHORT).show() }
    AddToPlaylistDialog(
        count = ids.size,
        playlists = list,
        onChoose = { playlist ->
            scope.launch {
                store.append(playlist.id, ids)
                added(playlist.name)
            }
        },
        onCreate = { name ->
            scope.launch {
                if (store.create(name, ids) != null) added(name)
            }
        },
        onDismiss = onDismiss,
    )
}
