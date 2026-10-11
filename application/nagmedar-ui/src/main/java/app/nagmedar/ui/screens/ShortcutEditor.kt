package app.nagmedar.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.KeyChord
import app.nagmedar.ui.input.PhysicalKeys
import app.nagmedar.ui.input.Shortcut
import app.nagmedar.ui.input.ShortcutFile
import app.nagmedar.ui.input.ShortcutGroup
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutPreset
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.input.reservedReason
import app.nagmedar.ui.theme.NagTheme
import android.view.KeyEvent as AndroidKeyEvent

/** [overrides] with [id] bound to [chord]; a chord equal to the default is no override. */
internal fun Map<ShortcutId, KeyChord?>.with(id: ShortcutId, chord: KeyChord?): Map<ShortcutId, KeyChord?> =
    if (ShortcutRegistry.default(id).chord == chord) this - id else this + (id to chord)

/** The table [overrides] make of the defaults. */
internal fun tableOf(overrides: Map<ShortcutId, KeyChord?>): List<Shortcut> =
    ShortcutRegistry.defaults.map { if (it.id in overrides) it.copy(chord = overrides[it.id]) else it }

/** Keys pressed for [id] that are [other]'s: assigned only when the user says so. */
private class Taken(val id: ShortcutId, val chord: KeyChord, val other: Shortcut)

/**
 * The shortcut editor (S-402), the settings' keyboard section: every shortcut by menu with its
 * keys. A click (or Enter) on the keys records new ones; Esc gives up and Backspace takes the keys
 * away. Keys that are another shortcut's are assigned only after a warning, the system's and the
 * window's own never. The VLC and mpv presets, a reset and the import come with an undo; the table
 * is exported to and imported from a JSON file. Keys go by their place (S-410): pressed on Türkçe Q
 * or on a US keyboard, the same key is the same shortcut; the keyboard map shows where they are.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutEditor(
    overrides: Map<ShortcutId, KeyChord?>,
    onChange: (Map<ShortcutId, KeyChord?>) -> Unit,
    undo: UndoBarState? = null,
) {
    val context = LocalContext.current
    val res = context.resources
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val table = remember(overrides) { tableOf(overrides) }
    val preset = remember(overrides) { ShortcutPreset.of(overrides) }
    val conflicts = remember(table) { ShortcutRegistry.conflicts(table) }
    var recording by remember { mutableStateOf<ShortcutId?>(null) }
    var taken by remember { mutableStateOf<Taken?>(null) }
    var refused by remember { mutableStateOf<Pair<ShortcutId, Int>?>(null) }
    var mapShown by rememberSaveable { mutableStateOf(false) }

    // while keys are recorded they are not shortcuts
    val isRecording = recording != null
    val owner = remember { Any() }
    DisposableEffect(isRecording) {
        ShortcutRegistry.pause(owner, isRecording)
        onDispose { ShortcutRegistry.pause(owner, false) }
    }

    /** Applies [next]; [message] offers to take it back. */
    fun change(next: Map<ShortcutId, KeyChord?>, message: String? = null) {
        val previous = overrides
        onChange(next)
        if (message != null) {
            val label = res.getString(R.string.nag_sc_undo)
            if (undo != null) {
                undo.show(message, label, ShortcutRegistry.chordLabel(ShortcutId.Undo, res)) { onChange(previous) }
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pressed(id: ShortcutId, chord: KeyChord) {
        recording = null
        val reason = reservedReason(chord)
        val other = table.firstOrNull { it.id != id && it.chord == chord }
        when {
            reason != null -> refused = id to reason
            other != null -> taken = Taken(id, chord, other)
            else -> change(overrides.with(id, chord))
        }
    }

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(ShortcutFile.export(table).toByteArray()) } != null
        }.getOrDefault(false)
        Toast.makeText(context, if (written) R.string.nag_sc_exported else R.string.nag_sc_export_failed, Toast.LENGTH_SHORT).show()
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
            ShortcutFile.import(text)
        }.getOrNull()
        if (imported == null) {
            Toast.makeText(context, R.string.nag_sc_import_failed, Toast.LENGTH_LONG).show()
        } else {
            val message = res.getQuantityString(R.plurals.nag_sc_imported, imported.applied, imported.applied) +
                if (imported.skipped.isEmpty()) "" else " · " + res.getQuantityString(R.plurals.nag_sc_import_skipped, imported.skipped.size, imported.skipped.size)
            change(imported.overrides, message)
        }
    }

    SettingsSection(stringResource(R.string.nag_settings_shortcuts)) {
        SettingRow(
            stringResource(R.string.nag_sc_preset),
            subtitle = stringResource(if (preset == null) R.string.nag_sc_custom else R.string.nag_sc_preset_hint),
        ) {
            SegmentedChoice<ShortcutPreset?>(
                options = ShortcutPreset.entries.map { it to stringResource(it.label) },
                selected = preset,
                onSelect = { chosen ->
                    if (chosen != null && chosen != preset) {
                        change(chosen.overrides, res.getString(R.string.nag_sc_preset_done, res.getString(chosen.label)))
                    }
                },
            )
        }
        FlowRow(
            Modifier.padding(vertical = dimens.gapS),
            horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
            verticalArrangement = Arrangement.spacedBy(dimens.gapS),
        ) {
            NagButton(stringResource(R.string.nag_sc_export), { exportFile.launch(ShortcutFile.NAME) })
            NagButton(stringResource(R.string.nag_sc_import), { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
            NagButton(
                stringResource(R.string.nag_sc_reset_all),
                { change(emptyMap(), res.getString(R.string.nag_sc_reset_done)) },
                enabled = overrides.isNotEmpty(),
            )
            // S-410: where the shortcuts are on the keyboard in use
            NagButton(
                stringResource(if (mapShown) R.string.nag_kb_hide else R.string.nag_kb_show),
                { mapShown = !mapShown },
                icon = NagIcons.Keyboard,
            )
        }
        if (mapShown) KeyboardMap(table, Modifier.padding(vertical = dimens.gapS))
        if (conflicts.isNotEmpty()) {
            Notice(
                pluralStringResource(R.plurals.nag_sc_conflicts, conflicts.size, conflicts.size) + "\n" +
                    conflicts.joinToString("\n") { (a, b) ->
                        res.getString(R.string.nag_sc_conflict_pair, a.chord?.label(res).orEmpty(), res.getString(a.label), res.getString(b.label))
                    },
                Modifier.testTag("shortcut-conflicts"),
            )
        }
        ShortcutGroup.entries.forEach { group ->
            val shortcuts = table.filter { it.group == group }
            if (shortcuts.isEmpty()) return@forEach
            Text(
                stringResource(group.title),
                style = NagTheme.type.label,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = dimens.gapM, bottom = 4.dp),
            )
            shortcuts.forEach { shortcut ->
                val id = shortcut.id
                ShortcutRow(
                    shortcut = shortcut,
                    default = ShortcutRegistry.default(id).chord,
                    recording = recording == id,
                    onRecord = {
                        taken = null
                        refused = null
                        recording = if (recording == id) null else id
                    },
                    onKeys = { pressed(id, it) },
                    onCancel = { if (recording == id) recording = null },
                    onRemove = {
                        if (recording == id) recording = null
                        change(overrides.with(id, null))
                    },
                    onRestore = { change(overrides - id) },
                )
                val conflict = taken?.takeIf { it.id == id }
                if (conflict != null) {
                    val keys = conflict.chord.label(res)
                    val other = res.getString(conflict.other.label)
                    Notice(stringResource(R.string.nag_sc_conflict, keys, other)) {
                        NagButton(stringResource(R.string.nag_sc_assign_anyway), {
                            taken = null
                            change(overrides.with(conflict.other.id, null).with(id, conflict.chord))
                        }, primary = true)
                        NagButton(stringResource(R.string.nag_sc_cancel), { taken = null })
                    }
                }
                val reason = refused?.takeIf { it.first == id }?.second
                if (reason != null) {
                    Notice(stringResource(reason)) {
                        NagButton(stringResource(R.string.nag_sc_close), { refused = null })
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    shortcut: Shortcut,
    default: KeyChord?,
    recording: Boolean,
    onRecord: () -> Unit,
    onKeys: (KeyChord) -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRestore: () -> Unit,
) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val changed = shortcut.chord != default
    val label = stringResource(shortcut.label)
    Column(Modifier.fillMaxWidth().testTag("shortcut-${shortcut.id.name}")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(label, style = NagTheme.type.body, color = colors.text)
                val notes = listOfNotNull(
                    stringResource(R.string.nag_shortcuts_player_only).takeIf { shortcut.scope == ShortcutScope.Player },
                    stringResource(R.string.nag_sc_default, default?.label(res) ?: stringResource(R.string.nag_sc_none)).takeIf { changed },
                )
                if (notes.isNotEmpty()) {
                    Text(notes.joinToString(" · "), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                }
            }
            ChordButton(shortcut.chord, recording, label, onRecord, onKeys, onCancel, onRemove, Modifier.testTag("keys-${shortcut.id.name}"))
            // fixed slots, so the keys stay in one column
            Box(Modifier.size(dimens.minTarget.coerceAtLeast(32.dp)), contentAlignment = Alignment.Center) {
                if (changed) NagIconButton(NagIcons.History, stringResource(R.string.nag_sc_restore_label, label), onRestore)
            }
            Box(Modifier.size(dimens.minTarget.coerceAtLeast(32.dp)), contentAlignment = Alignment.Center) {
                if (shortcut.chord != null) NagIconButton(NagIcons.Close, stringResource(R.string.nag_sc_remove_label, label), onRemove)
            }
        }
        if (recording) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.nag_sc_press_hint), style = NagTheme.type.labelSmall, color = colors.accentText, modifier = Modifier.weight(1f))
                NagButton(stringResource(R.string.nag_sc_cancel), onCancel)
            }
        }
    }
}

/**
 * The keys of a shortcut, a button: chosen, it records the next keys pressed with their modifiers
 * until one that is not a modifier comes; Esc gives up and Backspace takes the keys away.
 */
@Composable
private fun ChordButton(
    chord: KeyChord?,
    recording: Boolean,
    label: String,
    onClick: () -> Unit,
    onKeys: (KeyChord) -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(recording) { if (recording) runCatching { focus.requestFocus() } }
    Box(
        modifier
            .widthIn(min = 84.dp)
            .heightIn(min = dimens.controlHeight)
            .focusRing(state, shape)
            .background(if (recording) colors.selection else stateBackground(state), shape)
            .border(1.dp, if (recording) colors.accent else colors.border, shape)
            .focusRequester(focus)
            .onFocusChanged { if (recording && !it.hasFocus) onCancel() }
            .onPreviewKeyEvent { event ->
                if (!recording) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    val code = event.nativeKeyEvent.keyCode
                    val plain = !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed
                    // the key by its place (S-410): the same keys on Türkçe Q and on a US keyboard
                    val key = PhysicalKeys.keyOf(event)
                    when {
                        AndroidKeyEvent.isModifierKey(code) -> Unit
                        plain && code == AndroidKeyEvent.KEYCODE_ESCAPE -> onCancel()
                        plain && code == AndroidKeyEvent.KEYCODE_DEL -> onRemove()
                        key.nativeKeyCode == AndroidKeyEvent.KEYCODE_UNKNOWN -> Unit
                        else -> onKeys(KeyChord(key, ctrl = event.isCtrlPressed, shift = event.isShiftPressed, alt = event.isAltPressed, meta = event.isMetaPressed))
                    }
                }
                // nothing reaches the window while keys are recorded
                true
            }
            // a keyboard records, so the button takes the focus in touch mode too
            .focusProperties { canFocus = true }
            .nagClickable(interaction, onClick, onClickLabel = stringResource(R.string.nag_sc_change_label, label))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            recording -> Text(stringResource(R.string.nag_sc_press), style = NagTheme.type.labelSmall, color = colors.onSelection, maxLines = 1)
            chord != null -> Text(chord.label(res), style = NagTheme.type.mono, color = colors.text, maxLines = 1)
            else -> Text(stringResource(R.string.nag_sc_none), style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1)
        }
    }
}

/** A warning under a row or above the table, with its choices. */
@Composable
private fun Notice(text: String, modifier: Modifier = Modifier, actions: (@Composable () -> Unit)? = null) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.warning, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NagIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(NagTheme.dimens.icon))
        Spacer(Modifier.width(10.dp))
        Text(text, style = NagTheme.type.body, color = colors.text, modifier = Modifier.weight(1f))
        if (actions != null) {
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions() }
        }
    }
}
