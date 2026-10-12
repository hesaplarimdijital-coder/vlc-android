package app.nagmedar.ui.input

import androidx.annotation.StringRes
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import app.nagmedar.ui.R
import org.json.JSONException
import org.json.JSONObject
import android.view.KeyEvent as AndroidKeyEvent

/*
 * The shortcut editor's model (S-402): the text form of a chord used in exported files and in the
 * preferences ("Ctrl+Shift+N"), the VLC and mpv presets, keys the system or the shell keep for
 * themselves, and the exported file.
 */

/** English key names of the text form by Android key code: the same in every language and layout. */
private val keyNames: Map<Int, String> = buildMap {
    for (c in 'A'..'Z') put(AndroidKeyEvent.KEYCODE_A + (c - 'A'), c.toString())
    for (d in 0..9) put(AndroidKeyEvent.KEYCODE_0 + d, d.toString())
    for (f in 1..12) put(AndroidKeyEvent.KEYCODE_F1 + f - 1, "F$f")
    for (d in 0..9) put(AndroidKeyEvent.KEYCODE_NUMPAD_0 + d, "NumPad$d")
    put(AndroidKeyEvent.KEYCODE_SPACE, "Space")
    put(AndroidKeyEvent.KEYCODE_ENTER, "Enter")
    put(AndroidKeyEvent.KEYCODE_ESCAPE, "Escape")
    put(AndroidKeyEvent.KEYCODE_TAB, "Tab")
    put(AndroidKeyEvent.KEYCODE_DEL, "Backspace")
    put(AndroidKeyEvent.KEYCODE_FORWARD_DEL, "Delete")
    put(AndroidKeyEvent.KEYCODE_INSERT, "Insert")
    put(AndroidKeyEvent.KEYCODE_MOVE_HOME, "Home")
    put(AndroidKeyEvent.KEYCODE_MOVE_END, "End")
    put(AndroidKeyEvent.KEYCODE_PAGE_UP, "PageUp")
    put(AndroidKeyEvent.KEYCODE_PAGE_DOWN, "PageDown")
    put(AndroidKeyEvent.KEYCODE_DPAD_LEFT, "Left")
    put(AndroidKeyEvent.KEYCODE_DPAD_RIGHT, "Right")
    put(AndroidKeyEvent.KEYCODE_DPAD_UP, "Up")
    put(AndroidKeyEvent.KEYCODE_DPAD_DOWN, "Down")
    put(AndroidKeyEvent.KEYCODE_COMMA, "Comma")
    put(AndroidKeyEvent.KEYCODE_PERIOD, "Period")
    put(AndroidKeyEvent.KEYCODE_SLASH, "Slash")
    put(AndroidKeyEvent.KEYCODE_BACKSLASH, "Backslash")
    put(AndroidKeyEvent.KEYCODE_SEMICOLON, "Semicolon")
    put(AndroidKeyEvent.KEYCODE_APOSTROPHE, "Apostrophe")
    put(AndroidKeyEvent.KEYCODE_GRAVE, "Grave")
    put(AndroidKeyEvent.KEYCODE_MINUS, "Minus")
    put(AndroidKeyEvent.KEYCODE_EQUALS, "Equals")
    put(AndroidKeyEvent.KEYCODE_PLUS, "Plus")
    put(AndroidKeyEvent.KEYCODE_LEFT_BRACKET, "LeftBracket")
    put(AndroidKeyEvent.KEYCODE_RIGHT_BRACKET, "RightBracket")
    put(AndroidKeyEvent.KEYCODE_NUMPAD_ADD, "NumPadAdd")
    put(AndroidKeyEvent.KEYCODE_NUMPAD_SUBTRACT, "NumPadSubtract")
    put(AndroidKeyEvent.KEYCODE_NUMPAD_MULTIPLY, "NumPadMultiply")
    put(AndroidKeyEvent.KEYCODE_NUMPAD_DIVIDE, "NumPadDivide")
    put(AndroidKeyEvent.KEYCODE_NUMPAD_DOT, "NumPadDot")
    put(AndroidKeyEvent.KEYCODE_NUMPAD_ENTER, "NumPadEnter")
    put(AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "MediaPlayPause")
    put(AndroidKeyEvent.KEYCODE_MEDIA_STOP, "MediaStop")
    put(AndroidKeyEvent.KEYCODE_MEDIA_NEXT, "MediaNext")
    put(AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS, "MediaPrevious")
    put(AndroidKeyEvent.KEYCODE_MEDIA_REWIND, "MediaRewind")
    put(AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "MediaFastForward")
}

private val keyCodes: Map<String, Int> = keyNames.entries.associate { (code, name) -> name.lowercase() to code }

/** "Ctrl+Shift+N": the chord's text form in files and preferences; [KeyChord.label] is what people read. */
fun KeyChord.toText(): String = buildString {
    if (ctrl) append("Ctrl+")
    if (alt) append("Alt+")
    if (shift) append("Shift+")
    if (meta) append("Meta+")
    val code = key.nativeKeyCode
    append(keyNames[code] ?: "Key$code")
}

/** The chord written as [text] ("Ctrl+K", "shift+left", "Key123"); null when it is not one. */
fun parseChord(text: String): KeyChord? {
    val parts = text.trim().split('+').map { it.trim().lowercase() }
    if (parts.any { it.isEmpty() }) return null
    var ctrl = false
    var alt = false
    var shift = false
    var meta = false
    for (modifier in parts.dropLast(1)) {
        when (modifier) {
            "ctrl", "control" -> ctrl = true
            "alt", "option" -> alt = true
            "shift" -> shift = true
            "meta", "cmd", "win", "super" -> meta = true
            else -> return null
        }
    }
    val name = parts.last()
    val code = keyCodes[name] ?: name.removePrefix("key").toIntOrNull()?.takeIf { name.startsWith("key") && it > 0 } ?: return null
    if (AndroidKeyEvent.isModifierKey(code)) return null
    return KeyChord(Key(code), ctrl = ctrl, shift = shift, alt = alt, meta = meta)
}

/**
 * Why [chord] cannot be a shortcut, or null when it can: Meta's chords, Alt+Tab and Alt+F4 are the
 * system's, F10 opens the menu bar, and plain Tab, Enter and Escape move and choose in the window.
 */
@StringRes
fun reservedReason(chord: KeyChord): Int? {
    val plain = !chord.ctrl && !chord.alt && !chord.meta
    return when {
        chord.meta -> R.string.nag_sc_reserved_system
        chord.alt && !chord.ctrl && (chord.key == Key.Tab || chord.key == Key.F4) -> R.string.nag_sc_reserved_system
        chord.key == Key.F10 && plain && !chord.shift -> R.string.nag_sc_reserved_menu
        plain && (chord.key == Key.Tab || chord.key == Key.Enter || chord.key == Key.Escape) -> R.string.nag_sc_reserved_navigation
        else -> null
    }
}

/** Starting points of the editor: VLC's keys (the defaults) and mpv's. */
enum class ShortcutPreset(@StringRes val label: Int) {
    Vlc(R.string.nag_sc_preset_vlc),
    Mpv(R.string.nag_sc_preset_mpv),
    ;

    /** Every shortcut's chord under this preset. */
    val chords: Map<ShortcutId, KeyChord?>
        get() {
            val vlc = ShortcutRegistry.defaults.associate { it.id to it.chord }
            return if (this == Vlc) vlc else vlc + mpvKeys
        }

    /** The preset as overrides of the defaults. */
    val overrides: Map<ShortcutId, KeyChord?>
        get() = chords.filter { (id, chord) -> ShortcutRegistry.default(id).chord != chord }

    companion object {
        /** The preset [overrides] amount to, null for the user's own. */
        fun of(overrides: Map<ShortcutId, KeyChord?>): ShortcutPreset? = entries.firstOrNull { it.overrides == overrides }
    }
}

/** mpv's default keys (input.conf) where mpv differs from VLC; the rest stay as they are. */
private val mpvKeys: Map<ShortcutId, KeyChord?> = mapOf(
    ShortcutId.MediaInfo to KeyChord(Key.I),
    ShortcutId.Stop to KeyChord(Key.Q),
    ShortcutId.Previous to KeyChord(Key.Comma, shift = true),
    ShortcutId.Next to KeyChord(Key.Period, shift = true),
    ShortcutId.JumpBack to KeyChord(Key.DirectionLeft),
    ShortcutId.JumpForward to KeyChord(Key.DirectionRight),
    ShortcutId.JumpBackLong to KeyChord(Key.DirectionDown),
    ShortcutId.JumpForwardLong to KeyChord(Key.DirectionUp),
    ShortcutId.NextFrame to KeyChord(Key.Period),
    ShortcutId.PreviousFrame to KeyChord(Key.Comma),
    ShortcutId.NormalSpeed to KeyChord(Key.Backspace),
    ShortcutId.Repeat to KeyChord(Key.L, shift = true),
    ShortcutId.VolumeUp to KeyChord(Key.Zero),
    ShortcutId.VolumeDown to KeyChord(Key.Nine),
    ShortcutId.AudioDelayDown to KeyChord(Key.Minus, ctrl = true),
    ShortcutId.AudioDelayUp to KeyChord(Key.Equals, ctrl = true),
    ShortcutId.NextAudioTrack to KeyChord(Key.Three, shift = true),
    ShortcutId.Snapshot to KeyChord(Key.S),
    ShortcutId.NextSubtitle to KeyChord(Key.J),
    ShortcutId.SubtitleDelayDown to KeyChord(Key.Z),
    ShortcutId.SubtitleDelayUp to KeyChord(Key.Z, shift = true),
    // mpv shows the time with O and has no zoom of its own on a key: Z is the subtitles' there
    ShortcutId.ShowTime to KeyChord(Key.O),
    ShortcutId.Zoom to null,
)

/** The overrides as the preferences keep them: "Id=Chord" lines, "Id=" for no keys. */
object ShortcutOverrides {
    fun format(overrides: Map<ShortcutId, KeyChord?>): String =
        overrides.entries.joinToString("\n") { (id, chord) -> "${id.name}=${chord?.toText().orEmpty()}" }

    /** Lines of unknown shortcuts or keys are left out. */
    fun parse(text: String?): Map<ShortcutId, KeyChord?> {
        if (text.isNullOrBlank()) return emptyMap()
        val ids = ShortcutId.entries.associateBy { it.name }
        return buildMap {
            for (line in text.lineSequence()) {
                val id = ids[line.substringBefore('=', "").trim()] ?: continue
                val value = line.substringAfter('=').trim()
                if (value.isEmpty()) put(id, null) else parseChord(value)?.let { put(id, it) }
            }
        }
    }
}

/** The exported shortcut file (S-402): every shortcut's keys, by name, in JSON. */
object ShortcutFile {
    private const val TYPE = "nagmedar-shortcuts"
    private const val VERSION = 1

    /** The file name offered when exporting. */
    const val NAME = "nagmedar-kisayollar.json"

    fun export(shortcuts: List<Shortcut> = ShortcutRegistry.shortcuts): String {
        val bindings = JSONObject()
        shortcuts.forEach { bindings.put(it.id.name, it.chord?.toText().orEmpty()) }
        return JSONObject().put("type", TYPE).put("version", VERSION).put("bindings", bindings).toString(2)
    }

    /** What an imported file holds: the overrides it amounts to, how many keys it set, and what was left out. */
    class Imported(val overrides: Map<ShortcutId, KeyChord?>, val applied: Int, val skipped: List<String>)

    /** Reads an exported file; shortcuts it leaves out keep their defaults. Throws [IllegalArgumentException] for another file. */
    fun import(text: String): Imported {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("not a shortcut file", e)
        }
        require(root.optString("type") == TYPE) { "not a shortcut file" }
        val bindings = root.optJSONObject("bindings") ?: throw IllegalArgumentException("no bindings")
        val ids = ShortcutId.entries.associateBy { it.name }
        val chords = ShortcutRegistry.defaults.associate { it.id to it.chord }.toMutableMap()
        var applied = 0
        val skipped = mutableListOf<String>()
        for (name in bindings.keys()) {
            val id = ids[name]
            val value = bindings.optString(name).trim()
            val chord = if (value.isEmpty()) null else parseChord(value)
            if (id == null || (value.isNotEmpty() && (chord == null || reservedReason(chord) != null))) {
                skipped += name
                continue
            }
            chords[id] = chord
            applied++
        }
        val overrides = chords.filter { (id, chord) -> ShortcutRegistry.default(id).chord != chord }
        return Imported(overrides, applied, skipped)
    }
}
