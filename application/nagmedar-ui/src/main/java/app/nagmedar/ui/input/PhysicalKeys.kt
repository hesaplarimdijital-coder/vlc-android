package app.nagmedar.ui.input

import android.os.Build
import android.view.InputDevice
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import android.view.KeyEvent as AndroidKeyEvent

/**
 * A keyboard's layout as the shortcuts see it (S-410): the key code the key at each place of a US
 * keyboard gives, and what is printed on the key that gives a key code.
 */
interface KeyLayout {
    /** The keyboard's name, as it calls itself. */
    val name: String? get() = null

    /** The key code the key at US [place] gives; the place itself where the layout keeps it. */
    fun codeAt(place: Int): Int

    /** What is printed on the key that gives [keyCode]; null when nothing is. */
    fun label(keyCode: Int): Char?
}

/**
 * Shortcuts by the place of the key (S-410). A layout other than the US one (Türkçe Q, QWERTZ,
 * AZERTY) gives some keys other key codes and prints other characters on them: Türkçe Q gives the
 * key where a US keyboard has "," the code of "=" and prints "Ö" on it. The shortcut table names
 * keys by their US place, and here a key pressed finds its place again:
 *
 * - letters are the letter the key gives, which Android makes the letter printed on it, so Ctrl+Z
 *   is the key with Z on it on QWERTZ too;
 * - the other keys (digits, punctuation) are the key at the US place, whatever it prints: Ctrl+,
 *   is the key at the US "," on Türkçe Q, "Ö" on its keycap, as the same key on a US keyboard.
 *
 * Keycaps, menus and the system's list (Meta+/) show what the keyboard in use prints on the key.
 */
object PhysicalKeys {

    private val letters = AndroidKeyEvent.KEYCODE_A..AndroidKeyEvent.KEYCODE_Z

    /** The US places that are not letters: what is printed on them changes from layout to layout. */
    internal val places: Set<Int> = buildSet {
        addAll(AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9)
        addAll(
            listOf(
                AndroidKeyEvent.KEYCODE_GRAVE, AndroidKeyEvent.KEYCODE_MINUS, AndroidKeyEvent.KEYCODE_EQUALS,
                AndroidKeyEvent.KEYCODE_LEFT_BRACKET, AndroidKeyEvent.KEYCODE_RIGHT_BRACKET, AndroidKeyEvent.KEYCODE_BACKSLASH,
                AndroidKeyEvent.KEYCODE_SEMICOLON, AndroidKeyEvent.KEYCODE_APOSTROPHE, AndroidKeyEvent.KEYCODE_COMMA,
                AndroidKeyEvent.KEYCODE_PERIOD, AndroidKeyEvent.KEYCODE_SLASH,
            ),
        )
    }

    /** Keys with something printed on them: the letters, the [places] and the key between Shift and Z of ISO keyboards. */
    private fun isPrinted(key: Int) = key in letters || key in places || key == AndroidKeyEvent.KEYCODE_PLUS

    /**
     * Changes when a keyboard comes, goes or changes its layout: labels read it, so keycaps follow.
     * Made in the global snapshot: the object is first touched inside a window's composition.
     */
    var version by Snapshot.global { mutableIntStateOf(0) }
        private set

    /** The layout of the keyboard with this device id, null for none. */
    @VisibleForTesting
    internal var layoutOf: (deviceId: Int) -> KeyLayout? = ::deviceLayout

    /** The keyboard whose keycaps the labels show: the hardware keyboard, null without one. */
    @VisibleForTesting
    internal var keyboardId: () -> Int? = ::hardwareKeyboard

    private class Learnt(val layout: KeyLayout) {
        /** Each key code the layout gives at another US place than its own, to that place. */
        val placeOf: Map<Int, Int> = buildMap {
            for (place in letters + places) {
                val code = layout.codeAt(place)
                if (code != place && code != AndroidKeyEvent.KEYCODE_UNKNOWN) put(code, place)
            }
        }
    }

    private val learnt = HashMap<Int, Learnt?>()
    private var keyboard: Int? = null

    /** The [version] [keyboard] was looked for at. */
    private var keyboardAt = -1

    private fun learnt(deviceId: Int): Learnt? = synchronized(learnt) {
        // a device without a layout is remembered too: a key event is no time to ask again
        if (deviceId in learnt) return learnt[deviceId]
        runCatching { layoutOf(deviceId) }.getOrNull()?.let(::Learnt).also { learnt[deviceId] = it }
    }

    /** A keyboard came, went or changed its layout: what was learnt goes. */
    fun forget() {
        synchronized(learnt) { learnt.clear() }
        version++
    }

    /** The US place of the key that gave [keyCode] on [deviceId]'s keyboard: where the key map draws it. */
    fun placeOf(keyCode: Int, deviceId: Int): Int = learnt(deviceId)?.placeOf?.get(keyCode) ?: keyCode

    /**
     * The key of the shortcut table [keyCode] from [deviceId] is: a letter as it is, another key by
     * its US place; [AndroidKeyEvent.KEYCODE_UNKNOWN] for a key at a letter's place that gives no
     * letter (AZERTY's ","), which no shortcut means.
     */
    fun keyOf(keyCode: Int, deviceId: Int): Int {
        if (keyCode in letters) return keyCode
        val place = placeOf(keyCode, deviceId)
        return if (place in letters) AndroidKeyEvent.KEYCODE_UNKNOWN else place
    }

    fun keyOf(event: KeyEvent): Key = Key(keyOf(event.nativeKeyEvent.keyCode, event.nativeKeyEvent.deviceId))

    /** The key code to press for the table's [key] on [deviceId]'s keyboard (the hardware keyboard without one). */
    fun codeFor(key: Int, deviceId: Int? = null): Int {
        if (key in letters || key !in places) return key
        val id = deviceId ?: currentKeyboard() ?: return key
        val code = learnt(id)?.layout?.codeAt(key) ?: return key
        return if (code == AndroidKeyEvent.KEYCODE_UNKNOWN || code in letters) key else code
    }

    /**
     * What the keyboard in use prints on the key of the table's [key]: "Ğ" for "[" and "Ö" for ","
     * on Türkçe Q. Null without a hardware keyboard, for keys without a character (Enter, the
     * arrows) and for a place a letter took; the US name stands then.
     */
    fun printed(key: Int): String? {
        if (!isPrinted(key)) return null
        val id = currentKeyboard() ?: return null
        val layout = learnt(id)?.layout ?: return null
        val code = if (key in places) layout.codeAt(key).takeIf { it != AndroidKeyEvent.KEYCODE_UNKNOWN } ?: key else key
        if (key !in letters && code in letters) return null
        return layout.label(code)?.takeIf { it != '\u0000' && !it.isWhitespace() }?.toString()
    }

    /** What is printed on the key at US [place] of the keyboard in use, for the key map; null for the US name. */
    fun printedAtPlace(place: Int): String? {
        if (!isPrinted(place)) return null
        val layout = currentKeyboard()?.let(::learnt)?.layout ?: return null
        val code = layout.codeAt(place).takeIf { it != AndroidKeyEvent.KEYCODE_UNKNOWN } ?: place
        return layout.label(code)?.takeIf { it != '\u0000' && !it.isWhitespace() }?.toString()
    }

    /** The table's key the key at US [place] of the keyboard in use is. */
    fun keyAtPlace(place: Int): Int {
        val id = currentKeyboard() ?: return place
        val code = learnt(id)?.layout?.codeAt(place)?.takeIf { it != AndroidKeyEvent.KEYCODE_UNKNOWN } ?: place
        return keyOf(code, id)
    }

    /**
     * The hardware keyboard's id, looked for once until [forget]. It reads [version]: keycaps
     * composed with it follow a keyboard that comes, goes or changes its layout.
     */
    fun currentKeyboard(): Int? {
        val now = version
        synchronized(learnt) {
            if (keyboardAt != now) {
                keyboard = runCatching { keyboardId() }.getOrNull()
                keyboardAt = now
            }
            return keyboard
        }
    }

    /** The name of the hardware keyboard, for the key map; null without one. */
    fun keyboardName(): String? {
        val id = currentKeyboard() ?: return null
        return learnt(id)?.layout?.name
    }

    @VisibleForTesting
    internal fun reset() {
        layoutOf = ::deviceLayout
        keyboardId = ::hardwareKeyboard
        forget()
    }

    private fun hardwareKeyboard(): Int? = InputDevice.getDeviceIds().firstOrNull { id ->
        val device = InputDevice.getDevice(id) ?: return@firstOrNull false
        !device.isVirtual && device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
    }

    private fun deviceLayout(deviceId: Int): KeyLayout? {
        val device = InputDevice.getDevice(deviceId) ?: return null
        if (device.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC) return null
        return DeviceLayout(device)
    }
}

/** A hardware keyboard's layout, as Android has it. */
private class DeviceLayout(private val device: InputDevice) : KeyLayout {
    private val characters = runCatching { device.keyCharacterMap }.getOrNull()

    override val name: String? get() = device.name

    /** Before Android 13 the places cannot be asked for: a layout known by its keycaps gives them. */
    private val known: Map<Int, Int>? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) KnownLayouts.codesOf(::label) else null

    override fun codeAt(place: Int): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { device.getKeyCodeForKeyLocation(place) }.getOrDefault(place)
    } else {
        known?.get(place) ?: place
    }

    override fun label(keyCode: Int): Char? = characters?.let { map -> runCatching { map.getDisplayLabel(keyCode) }.getOrNull() }
        ?.takeIf { it != '\u0000' }
}

/** Layouts whose places are known without asking Android, which cannot tell before Android 13. */
internal object KnownLayouts {

    /**
     * Türkçe Q, as Android's keyboard_layout_turkish.kcm maps it: the key at each US place to the
     * key code it gives (the keys not listed keep theirs).
     */
    val turkishQ: Map<Int, Int> = mapOf(
        AndroidKeyEvent.KEYCODE_MINUS to AndroidKeyEvent.KEYCODE_SLASH,
        AndroidKeyEvent.KEYCODE_EQUALS to AndroidKeyEvent.KEYCODE_MINUS,
        AndroidKeyEvent.KEYCODE_BACKSLASH to AndroidKeyEvent.KEYCODE_COMMA,
        AndroidKeyEvent.KEYCODE_COMMA to AndroidKeyEvent.KEYCODE_EQUALS,
        AndroidKeyEvent.KEYCODE_PERIOD to AndroidKeyEvent.KEYCODE_BACKSLASH,
        AndroidKeyEvent.KEYCODE_SLASH to AndroidKeyEvent.KEYCODE_PERIOD,
    )

    /** The places of the layout whose keycaps [label] reads, when it is one of these; null otherwise. */
    fun codesOf(label: (Int) -> Char?): Map<Int, Int>? = when {
        // Türkçe Q with its keys moved: "Ğ" on "[" and "Ö" on the key giving "="
        label(AndroidKeyEvent.KEYCODE_LEFT_BRACKET) == 'Ğ' && label(AndroidKeyEvent.KEYCODE_EQUALS) == 'Ö' -> turkishQ
        else -> null
    }
}
