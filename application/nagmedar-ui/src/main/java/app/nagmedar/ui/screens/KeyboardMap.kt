package app.nagmedar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.ChipRow
import app.nagmedar.ui.components.FilterChip
import app.nagmedar.ui.components.HoverTooltip
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.input.KeyChord
import app.nagmedar.ui.input.PhysicalKeys
import app.nagmedar.ui.input.Shortcut
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.input.reservedReason
import app.nagmedar.ui.input.usKeyName
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import android.view.KeyEvent as AndroidKeyEvent

/** The modifiers of a layer of the key map. */
internal enum class Mod { Ctrl, Shift, Alt }

/** A layer of the key map: the shortcuts shown are those with these modifiers. */
@Immutable
internal data class KeyLayer(val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false) {
    fun has(mod: Mod): Boolean = when (mod) {
        Mod.Ctrl -> ctrl
        Mod.Shift -> shift
        Mod.Alt -> alt
    }

    fun with(mod: Mod, on: Boolean): KeyLayer = when (mod) {
        Mod.Ctrl -> copy(ctrl = on)
        Mod.Shift -> copy(shift = on)
        Mod.Alt -> copy(alt = on)
    }

    /** The table's [key] with this layer's modifiers. */
    fun chord(key: Int): KeyChord = KeyChord(Key(key), ctrl = ctrl, shift = shift, alt = alt)

    /** "Ctrl+Shift+": the modifiers as a chord's label writes them. */
    val prefix: String
        get() = buildString {
            if (ctrl) append("Ctrl+")
            if (alt) append("Alt+")
            if (shift) append("Shift+")
        }
}

/** A key of the drawn keyboard: its US place (null for the space between keys) and its width in keys. */
@Immutable
private class MapKey(val place: Int?, val width: Float = 1f) {
    val mod: Mod?
        get() = when (place) {
            AndroidKeyEvent.KEYCODE_CTRL_LEFT, AndroidKeyEvent.KEYCODE_CTRL_RIGHT -> Mod.Ctrl
            AndroidKeyEvent.KEYCODE_SHIFT_LEFT, AndroidKeyEvent.KEYCODE_SHIFT_RIGHT -> Mod.Shift
            AndroidKeyEvent.KEYCODE_ALT_LEFT, AndroidKeyEvent.KEYCODE_ALT_RIGHT -> Mod.Alt
            else -> null
        }
}

private fun key(place: Int, width: Float = 1f) = MapKey(place, width)

private fun gap(width: Float) = MapKey(null, width)

private fun letters(row: String) = row.map { key(AndroidKeyEvent.KEYCODE_A + (it - 'A')) }

private fun functionKeys(from: Int) = (from until from + 4).map { key(AndroidKeyEvent.KEYCODE_F1 + it) }

/** Esc and the function keys. */
private val functionRow: List<MapKey> =
    listOf(key(AndroidKeyEvent.KEYCODE_ESCAPE), gap(1f)) + functionKeys(0) + gap(0.5f) + functionKeys(4) + gap(0.5f) + functionKeys(8)

/** The main block of a US keyboard, 15 keys wide. */
private val mainRows: List<List<MapKey>> = listOf(
    listOf(key(AndroidKeyEvent.KEYCODE_GRAVE)) + (AndroidKeyEvent.KEYCODE_1..AndroidKeyEvent.KEYCODE_9).map { key(it) } +
        listOf(key(AndroidKeyEvent.KEYCODE_0), key(AndroidKeyEvent.KEYCODE_MINUS), key(AndroidKeyEvent.KEYCODE_EQUALS), key(AndroidKeyEvent.KEYCODE_DEL, 2f)),
    listOf(key(AndroidKeyEvent.KEYCODE_TAB, 1.5f)) + letters("QWERTYUIOP") +
        listOf(key(AndroidKeyEvent.KEYCODE_LEFT_BRACKET), key(AndroidKeyEvent.KEYCODE_RIGHT_BRACKET), key(AndroidKeyEvent.KEYCODE_BACKSLASH, 1.5f)),
    listOf(key(AndroidKeyEvent.KEYCODE_CAPS_LOCK, 1.75f)) + letters("ASDFGHJKL") +
        listOf(key(AndroidKeyEvent.KEYCODE_SEMICOLON), key(AndroidKeyEvent.KEYCODE_APOSTROPHE), key(AndroidKeyEvent.KEYCODE_ENTER, 2.25f)),
    listOf(key(AndroidKeyEvent.KEYCODE_SHIFT_LEFT, 2.25f)) + letters("ZXCVBNM") +
        listOf(key(AndroidKeyEvent.KEYCODE_COMMA), key(AndroidKeyEvent.KEYCODE_PERIOD), key(AndroidKeyEvent.KEYCODE_SLASH), key(AndroidKeyEvent.KEYCODE_SHIFT_RIGHT, 2.75f)),
    listOf(
        key(AndroidKeyEvent.KEYCODE_CTRL_LEFT, 1.5f), key(AndroidKeyEvent.KEYCODE_META_LEFT, 1.25f), key(AndroidKeyEvent.KEYCODE_ALT_LEFT, 1.5f),
        key(AndroidKeyEvent.KEYCODE_SPACE, 6.5f),
        key(AndroidKeyEvent.KEYCODE_ALT_RIGHT, 1.5f), key(AndroidKeyEvent.KEYCODE_META_RIGHT, 1.25f), key(AndroidKeyEvent.KEYCODE_CTRL_RIGHT, 1.5f),
    ),
)

/** The editing keys and the arrows, beside the main block's rows. */
private val sideRows: List<List<MapKey>> = listOf(
    listOf(key(AndroidKeyEvent.KEYCODE_INSERT), key(AndroidKeyEvent.KEYCODE_MOVE_HOME), key(AndroidKeyEvent.KEYCODE_PAGE_UP)),
    listOf(key(AndroidKeyEvent.KEYCODE_FORWARD_DEL), key(AndroidKeyEvent.KEYCODE_MOVE_END), key(AndroidKeyEvent.KEYCODE_PAGE_DOWN)),
    emptyList(),
    listOf(gap(1f), key(AndroidKeyEvent.KEYCODE_DPAD_UP), gap(1f)),
    listOf(key(AndroidKeyEvent.KEYCODE_DPAD_LEFT), key(AndroidKeyEvent.KEYCODE_DPAD_DOWN), key(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)),
)

/** Keys wide: the main block, a half key, the editing keys and the arrows. */
private const val MAP_WIDTH = 18.5f

/** What a key on the map is in a layer. */
private enum class KeyKind { Free, Bound, Conflict, Reserved, ModifierOn }

/**
 * The key chosen: where it is and which key of the table it is; pressed on the keyboard, with the
 * modifiers held with it ([layer]), clicked, in the layer shown.
 */
@Immutable
private data class Chosen(val place: Int, val key: Int, val layer: KeyLayer? = null)

/**
 * The keyboard map (S-410): a keyboard drawn as the user's keyboard prints its keys (Türkçe Q's
 * "Ğ" where a US keyboard has "["), with the keys that have a shortcut in the layer chosen with
 * Ctrl, Shift and Alt, two shortcuts on one key marked, the system's and the window's keys apart.
 * A key clicked, or pressed while the map has the focus, says what it does; holding Ctrl, Shift
 * or Alt shows their layer. While the map has the focus keys only show what they do.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardMap(table: List<Shortcut>, modifier: Modifier = Modifier) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val type = NagTheme.type
    var toggled by remember { mutableStateOf(KeyLayer()) }
    var held by remember { mutableStateOf<KeyLayer?>(null) }
    var chosen by remember { mutableStateOf<Chosen?>(null) }
    val layer = held ?: toggled
    val byChord = remember(table) { table.mapNotNull { shortcut -> shortcut.chord?.let { it to shortcut } }.groupBy({ it.first }, { it.second }) }

    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { ShortcutRegistry.pause(owner, false) } }
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val focus = remember { FocusRequester() }
    val spaceName = stringResource(R.string.nag_key_space)
    val pointer = NagTheme.inputMode == InputMode.Pointer

    fun nameAt(place: Int): String = when (place) {
        AndroidKeyEvent.KEYCODE_SPACE -> spaceName
        AndroidKeyEvent.KEYCODE_DEL -> "⌫"
        AndroidKeyEvent.KEYCODE_CAPS_LOCK -> "Caps"
        else -> PhysicalKeys.printedAtPlace(place) ?: usKeyName(place)
    }

    fun kindOf(mapKey: MapKey, key: Int): KeyKind {
        mapKey.mod?.let { return if (layer.has(it)) KeyKind.ModifierOn else KeyKind.Free }
        if (mapKey.place == AndroidKeyEvent.KEYCODE_META_LEFT || mapKey.place == AndroidKeyEvent.KEYCODE_META_RIGHT) return KeyKind.Reserved
        if (key == AndroidKeyEvent.KEYCODE_UNKNOWN) return KeyKind.Reserved
        val chord = layer.chord(key)
        val on = byChord[chord].orEmpty()
        return when {
            on.size > 1 -> KeyKind.Conflict
            on.size == 1 -> KeyKind.Bound
            reservedReason(chord) != null -> KeyKind.Reserved
            else -> KeyKind.Free
        }
    }

    /** "Ctrl+Ö · Ayarlar": the key with the modifiers of [mods] and what it does. */
    fun describe(place: Int, key: Int, mods: KeyLayer = layer): String {
        if (key == AndroidKeyEvent.KEYCODE_UNKNOWN) return res.getString(R.string.nag_kb_nothing, mods.prefix + nameAt(place))
        val chord = mods.chord(key)
        val name = chord.label(res)
        val on = byChord[chord].orEmpty()
        fun title(shortcut: Shortcut) = res.getString(shortcut.label) +
            if (shortcut.scope == ShortcutScope.Player) " (" + res.getString(R.string.nag_shortcuts_player_only).lowercase() + ")" else ""
        val reserved = reservedReason(chord)
        return when {
            on.size > 1 -> res.getString(R.string.nag_kb_conflict, name, on.joinToString(", ") { title(it) })
            on.size == 1 -> res.getString(R.string.nag_kb_one, name, title(on.single()))
            reserved != null -> res.getString(R.string.nag_kb_one, name, res.getString(reserved))
            else -> res.getString(R.string.nag_kb_free, name)
        }
    }

    /** A key pressed while the map has the focus: its place lights up; Ctrl, Shift and Alt held show their layer. */
    fun tryKey(event: KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        val code = native.keyCode
        val down = event.type == KeyEventType.KeyDown
        var now = KeyLayer(ctrl = event.isCtrlPressed, shift = event.isShiftPressed, alt = event.isAltPressed)
        when (code) {
            AndroidKeyEvent.KEYCODE_CTRL_LEFT, AndroidKeyEvent.KEYCODE_CTRL_RIGHT -> now = now.with(Mod.Ctrl, down)
            AndroidKeyEvent.KEYCODE_SHIFT_LEFT, AndroidKeyEvent.KEYCODE_SHIFT_RIGHT -> now = now.with(Mod.Shift, down)
            AndroidKeyEvent.KEYCODE_ALT_LEFT, AndroidKeyEvent.KEYCODE_ALT_RIGHT -> now = now.with(Mod.Alt, down)
        }
        held = now.takeIf { it != KeyLayer() }
        return when {
            AndroidKeyEvent.isModifierKey(code) -> true
            // Tab moves on, as everywhere
            code == AndroidKeyEvent.KEYCODE_TAB -> false
            // Esc lets go of the key chosen, then goes back, as everywhere
            code == AndroidKeyEvent.KEYCODE_ESCAPE -> if (chosen != null && now == KeyLayer()) {
                if (down) chosen = null
                true
            } else {
                false
            }
            down -> {
                chosen = Chosen(PhysicalKeys.placeOf(code, native.deviceId), PhysicalKeys.keyOf(code, native.deviceId), now)
                true
            }
            else -> true
        }
    }

    Column(modifier.fillMaxWidth()) {
        val keyboard = PhysicalKeys.currentKeyboard()
        val name = PhysicalKeys.keyboardName()
        Text(
            when {
                keyboard == null -> stringResource(R.string.nag_kb_no_keyboard)
                name.isNullOrBlank() -> stringResource(R.string.nag_kb_keyboard_unnamed)
                else -> stringResource(R.string.nag_kb_keyboard, name)
            },
            style = type.label,
            color = colors.text,
        )
        Text(
            stringResource(if (pointer) R.string.nag_kb_hint else R.string.nag_kb_hint_touch),
            style = type.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        // the layers; by touch the keys are too small to be targets, so these choose them
        ChipRow(Modifier.padding(bottom = 8.dp)) {
            Mod.entries.forEach { mod ->
                FilterChip(mod.name, toggled.has(mod), { on -> toggled = toggled.with(mod, on) })
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // the frame's padding and border take 16 dp
            val unit = ((maxWidth - 16.dp) / MAP_WIDTH).coerceIn(24.dp, 46.dp)
            val shape = RoundedCornerShape(NagTheme.dimens.radiusCard)
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Column(
                    Modifier
                        .testTag("keyboard-map")
                        .focusRequester(focus)
                        .onFocusChanged { focusState ->
                            // while the map has the focus, keys only show what they do
                            ShortcutRegistry.pause(owner, focusState.isFocused)
                            if (!focusState.isFocused) held = null
                        }
                        .onPreviewKeyEvent(::tryKey)
                        .focusable(interactionSource = interaction)
                        .focusRing(state, shape)
                        .background(colors.surfaceRaised, shape)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    @Composable
                    fun KeysRow(keys: List<MapKey>) {
                        keys.forEach { mapKey ->
                            val place = mapKey.place
                            if (place == null) {
                                Spacer(Modifier.width(unit * mapKey.width))
                            } else {
                                val tableKey = if (mapKey.mod != null) place else PhysicalKeys.keyAtPlace(place)
                                MapKeyCap(
                                    label = nameAt(place),
                                    named = place !in printedPlaces,
                                    width = unit * mapKey.width,
                                    unit = unit,
                                    kind = kindOf(mapKey, tableKey),
                                    selected = chosen?.place == place,
                                    description = if (mapKey.mod != null) nameAt(place) else describe(place, tableKey),
                                    // with a mouse a key says what it does; by touch the chips choose the layer
                                    onClick = if (!pointer) null else {
                                        {
                                            val mod = mapKey.mod
                                            if (mod != null) toggled = toggled.with(mod, !toggled.has(mod)) else chosen = Chosen(place, tableKey)
                                            // the keyboard goes on from the key clicked
                                            runCatching { focus.requestFocus() }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Row { KeysRow(functionRow) }
                    Spacer(Modifier.height(unit * 0.25f))
                    mainRows.forEachIndexed { index, row ->
                        Row {
                            KeysRow(row)
                            Spacer(Modifier.width(unit * 0.5f))
                            KeysRow(sideRows[index])
                        }
                    }
                }
            }
        }
        val current = chosen
        if (current != null || pointer) {
            Text(
                if (current == null) stringResource(R.string.nag_kb_choose) else describe(current.place, current.key, current.layer ?: layer),
                style = if (current == null) type.labelSmall else type.bodyStrong,
                color = if (current == null) colors.textTertiary else colors.text,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("keyboard-map-detail")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Legend(KeyKind.Bound, stringResource(R.string.nag_kb_legend_bound))
            Legend(KeyKind.Conflict, stringResource(R.string.nag_kb_legend_conflict))
            Legend(KeyKind.Reserved, stringResource(R.string.nag_kb_legend_reserved))
        }
    }
}

/** Places printed with a character: the rest (Tab, Enter, the arrows) are named. */
private val printedPlaces: Set<Int> = PhysicalKeys.places + (AndroidKeyEvent.KEYCODE_A..AndroidKeyEvent.KEYCODE_Z)

/** The fill, the edge and the text of a key of [kind]. */
@Composable
private fun keyColors(kind: KeyKind, hovered: Boolean): Triple<Color, Color, Color> {
    val colors = NagTheme.colors
    return when (kind) {
        KeyKind.Bound -> Triple(colors.selection, colors.selection, colors.onSelection)
        KeyKind.Conflict -> Triple(colors.warning.copy(alpha = 0.28f).compositeOver(colors.surface), colors.warning, colors.text)
        KeyKind.Reserved -> Triple(if (hovered) colors.hover else colors.surfaceRaised, colors.border, colors.textTertiary)
        KeyKind.ModifierOn -> Triple(colors.accent, colors.accent, colors.onAccent)
        KeyKind.Free -> Triple(if (hovered) colors.hover else colors.surface, colors.border, colors.textSecondary)
    }
}

@Composable
private fun MapKeyCap(
    label: String,
    named: Boolean,
    width: Dp,
    unit: Dp,
    kind: KeyKind,
    selected: Boolean,
    description: String,
    onClick: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(5.dp)
    val (fill, edge, text) = keyColors(kind, state.hovered)
    val gap = 3.dp
    Box(
        Modifier
            .padding(end = gap, bottom = gap)
            .size(width - gap, unit - gap)
            .background(fill, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) NagTheme.colors.focusRing else edge, shape)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    // the map is one stop for Tab; its keys are clicked, or pressed on the keyboard
                    Modifier.focusProperties { canFocus = false }.nagClickable(interaction, onClick, onClickLabel = description)
                },
            )
            .semantics {
                contentDescription = description
                this.selected = selected || kind == KeyKind.ModifierOn
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = when {
                named || unit < 30.dp -> NagTheme.type.caption
                else -> NagTheme.type.label
            },
            color = text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        HoverTooltip(state.hovered, description)
    }
}

@Composable
private fun Legend(kind: KeyKind, text: String) {
    val (fill, edge, _) = keyColors(kind, hovered = false)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).background(fill, RoundedCornerShape(3.dp)).border(1.dp, edge, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        Text(text, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
    }
}
