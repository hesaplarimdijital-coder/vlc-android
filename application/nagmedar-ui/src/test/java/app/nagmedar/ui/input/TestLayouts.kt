package app.nagmedar.ui.input

import android.view.KeyEvent as AndroidKeyEvent

/** Keyboard layouts as Android's keyboard files have them, for the tests of S-410. */
internal object TestLayouts {

    /** A layout from the key code each US place gives (the rest keep theirs) and the keycap of each key code. */
    private class Table(override val name: String?, private val codes: Map<Int, Int>, private val labels: Map<Int, Char>) : KeyLayout {
        override fun codeAt(place: Int): Int = codes[place] ?: place

        override fun label(keyCode: Int): Char? = labels[keyCode]
    }

    private val letterAndDigitLabels: Map<Int, Char> =
        (AndroidKeyEvent.KEYCODE_A..AndroidKeyEvent.KEYCODE_Z).associateWith { 'A' + (it - AndroidKeyEvent.KEYCODE_A) } +
            (AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9).associateWith { '0' + (it - AndroidKeyEvent.KEYCODE_0) }

    private val usLabels: Map<Int, Char> = letterAndDigitLabels + mapOf(
        AndroidKeyEvent.KEYCODE_GRAVE to '`', AndroidKeyEvent.KEYCODE_MINUS to '-', AndroidKeyEvent.KEYCODE_EQUALS to '=',
        AndroidKeyEvent.KEYCODE_LEFT_BRACKET to '[', AndroidKeyEvent.KEYCODE_RIGHT_BRACKET to ']', AndroidKeyEvent.KEYCODE_BACKSLASH to '\\',
        AndroidKeyEvent.KEYCODE_SEMICOLON to ';', AndroidKeyEvent.KEYCODE_APOSTROPHE to '\'', AndroidKeyEvent.KEYCODE_COMMA to ',',
        AndroidKeyEvent.KEYCODE_PERIOD to '.', AndroidKeyEvent.KEYCODE_SLASH to '/',
    )

    /** keyboard_layout_turkish.kcm's keycaps, by the key code the key gives. */
    private val turkishLabels: Map<Int, Char> = letterAndDigitLabels + mapOf(
        AndroidKeyEvent.KEYCODE_GRAVE to '"', AndroidKeyEvent.KEYCODE_SLASH to '*', AndroidKeyEvent.KEYCODE_MINUS to '-',
        AndroidKeyEvent.KEYCODE_LEFT_BRACKET to 'Ğ', AndroidKeyEvent.KEYCODE_RIGHT_BRACKET to 'Ü', AndroidKeyEvent.KEYCODE_COMMA to ',',
        AndroidKeyEvent.KEYCODE_SEMICOLON to 'Ş', AndroidKeyEvent.KEYCODE_APOSTROPHE to 'İ',
        AndroidKeyEvent.KEYCODE_EQUALS to 'Ö', AndroidKeyEvent.KEYCODE_BACKSLASH to 'Ç', AndroidKeyEvent.KEYCODE_PERIOD to '.',
        AndroidKeyEvent.KEYCODE_PLUS to '<',
    )

    /** A US keyboard: every key where it is, printed as it is. */
    val us: KeyLayout = Table("US Keyboard", emptyMap(), usLabels)

    /** Türkçe Q: "Ğ" on "[", the key at the US "," gives "=" with "Ö" on it, and so on. */
    val turkishQ: KeyLayout = Table("Türkçe Q", KnownLayouts.turkishQ, turkishLabels)

    /** QWERTZ's letters: the key at the US Y gives Z, Z on it, and the other way round. */
    val qwertz: KeyLayout = Table(
        "QWERTZ",
        mapOf(AndroidKeyEvent.KEYCODE_Y to AndroidKeyEvent.KEYCODE_Z, AndroidKeyEvent.KEYCODE_Z to AndroidKeyEvent.KEYCODE_Y),
        usLabels,
    )

    /** AZERTY's M and comma: M at the US ";", "," at the US M, ";" at the US ",". */
    val azerty: KeyLayout = Table(
        "AZERTY",
        mapOf(
            AndroidKeyEvent.KEYCODE_SEMICOLON to AndroidKeyEvent.KEYCODE_M,
            AndroidKeyEvent.KEYCODE_M to AndroidKeyEvent.KEYCODE_COMMA,
            AndroidKeyEvent.KEYCODE_COMMA to AndroidKeyEvent.KEYCODE_SEMICOLON,
        ),
        usLabels,
    )

    /** The keyboards by device id; the keycaps are those of [keyboard] (none when null). */
    fun use(vararg keyboards: Pair<Int, KeyLayout>, keyboard: Int? = keyboards.firstOrNull()?.first) {
        val byId = keyboards.toMap()
        PhysicalKeys.layoutOf = { id -> byId[id] }
        PhysicalKeys.keyboardId = { keyboard }
        PhysicalKeys.forget()
    }

    /** The keycaps the label function of Android's keyboard files gives, for [KnownLayouts]. */
    fun turkishKeycap(keyCode: Int): Char? = turkishLabels[keyCode]

    fun usKeycap(keyCode: Int): Char? = usLabels[keyCode]
}
