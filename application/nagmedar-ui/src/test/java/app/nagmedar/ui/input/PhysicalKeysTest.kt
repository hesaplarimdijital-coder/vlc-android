package app.nagmedar.ui.input

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import android.view.KeyEvent as AndroidKeyEvent

/**
 * S-410: a shortcut is the key at its place on Türkçe Q as on a US keyboard, letters are the
 * letter on the key, and keycaps, menus and the system's list show what the keyboard prints.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalKeysTest {

    private val turkish = 1
    private val us = 2

    @After
    fun reset() {
        PhysicalKeys.reset()
        ShortcutRegistry.overrides = emptyMap()
    }

    private fun press(code: Int, device: Int, ctrl: Boolean = false): KeyEvent {
        val meta = if (ctrl) AndroidKeyEvent.META_CTRL_ON or AndroidKeyEvent.META_CTRL_LEFT_ON else 0
        return KeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN, code, 0, meta, device, 0))
    }

    private fun shortcutOf(code: Int, device: Int, ctrl: Boolean = false) = ShortcutRegistry.match(press(code, device, ctrl))?.id

    @Test
    fun theSameKeyIsTheSameShortcutOnTurkishQAndAUsKeyboard() {
        TestLayouts.use(turkish to TestLayouts.turkishQ, us to TestLayouts.us)
        // the key at the US ",": "=" on Türkçe Q, "," on a US keyboard
        assertEquals(ShortcutId.Settings, shortcutOf(AndroidKeyEvent.KEYCODE_EQUALS, turkish, ctrl = true))
        assertEquals(ShortcutId.Settings, shortcutOf(AndroidKeyEvent.KEYCODE_COMMA, us, ctrl = true))
        // the key at the US "=": "-" on Türkçe Q
        assertEquals(ShortcutId.NormalSpeed, shortcutOf(AndroidKeyEvent.KEYCODE_MINUS, turkish))
        assertEquals(ShortcutId.NormalSpeed, shortcutOf(AndroidKeyEvent.KEYCODE_EQUALS, us))
        // the key at the US "/": "." on Türkçe Q
        assertEquals(ShortcutId.ShortcutList, shortcutOf(AndroidKeyEvent.KEYCODE_PERIOD, turkish, ctrl = true))
        assertEquals(ShortcutId.ShortcutList, shortcutOf(AndroidKeyEvent.KEYCODE_SLASH, us, ctrl = true))
        // "[" keeps its code, "Ğ" on it; the letters are the letters
        assertEquals(ShortcutId.Slower, shortcutOf(AndroidKeyEvent.KEYCODE_LEFT_BRACKET, turkish))
        assertEquals(ShortcutId.Stop, shortcutOf(AndroidKeyEvent.KEYCODE_S, turkish))
        assertEquals(ShortcutId.Stop, shortcutOf(AndroidKeyEvent.KEYCODE_S, us))
        // Türkçe Q's "," is at the US "\": not Ctrl+,
        assertEquals(AndroidKeyEvent.KEYCODE_BACKSLASH, PhysicalKeys.keyOf(AndroidKeyEvent.KEYCODE_COMMA, turkish))
        assertNull(shortcutOf(AndroidKeyEvent.KEYCODE_COMMA, turkish, ctrl = true))
        // a keyboard Android has no layout for, as it is
        assertEquals(ShortcutId.NormalSpeed, shortcutOf(AndroidKeyEvent.KEYCODE_EQUALS, 7))
    }

    @Test
    fun keycapsShowWhatTheKeyboardPrints() {
        TestLayouts.use(turkish to TestLayouts.turkishQ)
        assertEquals("Ctrl+Ö", ShortcutRegistry.chordLabel(ShortcutId.Settings))
        assertEquals("Ğ", ShortcutRegistry.chordLabel(ShortcutId.Slower))
        assertEquals("Ü", ShortcutRegistry.chordLabel(ShortcutId.Faster))
        assertEquals("-", ShortcutRegistry.chordLabel(ShortcutId.NormalSpeed))
        assertEquals("Ctrl+.", ShortcutRegistry.chordLabel(ShortcutId.ShortcutList))
        assertEquals("S", ShortcutRegistry.chordLabel(ShortcutId.Stop))
        assertEquals("Alt+←", ShortcutRegistry.chordLabel(ShortcutId.JumpBack))
        assertEquals("Space", ShortcutRegistry.chordLabel(ShortcutId.PlayPause))
        // and the map's keys
        assertEquals("Ö", PhysicalKeys.printedAtPlace(AndroidKeyEvent.KEYCODE_COMMA))
        assertEquals(AndroidKeyEvent.KEYCODE_COMMA, PhysicalKeys.keyAtPlace(AndroidKeyEvent.KEYCODE_COMMA))
        assertEquals(",", PhysicalKeys.printedAtPlace(AndroidKeyEvent.KEYCODE_BACKSLASH))
        assertEquals("Türkçe Q", PhysicalKeys.keyboardName())

        // a US keyboard, and no keyboard: the US names
        TestLayouts.use(us to TestLayouts.us)
        assertEquals("Ctrl+,", ShortcutRegistry.chordLabel(ShortcutId.Settings))
        assertEquals("[", ShortcutRegistry.chordLabel(ShortcutId.Slower))
        TestLayouts.use(turkish to TestLayouts.turkishQ, keyboard = null)
        assertEquals("Ctrl+,", ShortcutRegistry.chordLabel(ShortcutId.Settings))
        assertNull(PhysicalKeys.keyboardName())
    }

    @Test
    fun aNewLayoutBringsNewKeycaps() {
        TestLayouts.use(turkish to TestLayouts.us)
        val before = PhysicalKeys.version
        assertEquals("[", ShortcutRegistry.chordLabel(ShortcutId.Slower))
        // the keyboard is set to Türkçe Q: Android says the device changed
        TestLayouts.use(turkish to TestLayouts.turkishQ)
        assertNotEquals(before, PhysicalKeys.version)
        assertEquals("Ğ", ShortcutRegistry.chordLabel(ShortcutId.Slower))
    }

    @Test
    fun lettersAreTheLetterOnTheKey() {
        TestLayouts.use(turkish to TestLayouts.qwertz)
        // the key with Z on it (at the US Y) is Ctrl+Z
        assertEquals(ShortcutId.Undo, shortcutOf(AndroidKeyEvent.KEYCODE_Z, turkish, ctrl = true))
        assertEquals("Ctrl+Z", ShortcutRegistry.chordLabel(ShortcutId.Undo))
        // and the map draws it at the US Y
        assertEquals(AndroidKeyEvent.KEYCODE_Y, PhysicalKeys.placeOf(AndroidKeyEvent.KEYCODE_Z, turkish))
        assertEquals(AndroidKeyEvent.KEYCODE_Z, PhysicalKeys.keyAtPlace(AndroidKeyEvent.KEYCODE_Y))
        assertEquals("Z", PhysicalKeys.printedAtPlace(AndroidKeyEvent.KEYCODE_Y))
    }

    @Test
    fun aPlaceALetterTookIsNoShortcut() {
        TestLayouts.use(turkish to TestLayouts.azerty)
        // M is the key with M on it, at the US ";"
        assertEquals(ShortcutId.Mute, shortcutOf(AndroidKeyEvent.KEYCODE_M, turkish))
        // "," comes from the US M, a letter's place: no shortcut's
        assertEquals(AndroidKeyEvent.KEYCODE_UNKNOWN, PhysicalKeys.keyOf(AndroidKeyEvent.KEYCODE_COMMA, turkish))
        assertNull(shortcutOf(AndroidKeyEvent.KEYCODE_COMMA, turkish, ctrl = true))
        // the key at the US "," (";" on it) is Ctrl+,
        assertEquals(ShortcutId.Settings, shortcutOf(AndroidKeyEvent.KEYCODE_SEMICOLON, turkish, ctrl = true))
        // the US ";" is M's now: the keycap of ";" is the US one
        assertEquals("M", PhysicalKeys.printedAtPlace(AndroidKeyEvent.KEYCODE_SEMICOLON))
        assertNull(PhysicalKeys.printed(AndroidKeyEvent.KEYCODE_SEMICOLON))
        assertEquals(AndroidKeyEvent.KEYCODE_UNKNOWN, PhysicalKeys.keyAtPlace(AndroidKeyEvent.KEYCODE_M))
    }

    @Test
    fun theSystemsListShowsTheKeysToPress() {
        TestLayouts.use(turkish to TestLayouts.turkishQ)
        val res = ApplicationProvider.getApplicationContext<Context>().resources
        val items = ShortcutRegistry.keyboardShortcutGroups(res, turkish).flatMap { it.items }
        fun codeOf(label: Int) = items.first { it.label == res.getString(label) }.keycode
        assertEquals(AndroidKeyEvent.KEYCODE_EQUALS, codeOf(R.string.nag_nav_settings))
        assertEquals(AndroidKeyEvent.KEYCODE_MINUS, codeOf(R.string.nag_sc_normal_speed))
        assertEquals(AndroidKeyEvent.KEYCODE_LEFT_BRACKET, codeOf(R.string.nag_sc_slower))
        assertEquals(AndroidKeyEvent.KEYCODE_S, codeOf(R.string.nag_sc_stop))
        // the screen asked, not a keyboard: the hardware keyboard's
        val asked = ShortcutRegistry.keyboardShortcutGroups(res).flatMap { it.items }
        assertEquals(AndroidKeyEvent.KEYCODE_EQUALS, asked.first { it.label == res.getString(R.string.nag_nav_settings) }.keycode)
    }

    @Test
    fun turkishQIsKnownByItsKeycapsBeforeAndroid13() {
        assertEquals(KnownLayouts.turkishQ, KnownLayouts.codesOf(TestLayouts::turkishKeycap))
        assertNull(KnownLayouts.codesOf(TestLayouts::usKeycap))
        // Türkçe Q's places, read back
        TestLayouts.use(turkish to TestLayouts.turkishQ)
        val places = PhysicalKeys.places.associateWith { place -> PhysicalKeys.keyOf(TestLayouts.turkishQ.codeAt(place), turkish) }
        places.forEach { (place, key) -> assertEquals(AndroidKeyEvent.keyCodeToString(place), place, key) }
    }

    @Test
    fun keysPressedAreRecordedByTheirPlace() {
        TestLayouts.use(turkish to TestLayouts.turkishQ)
        assertEquals(Key.Comma, PhysicalKeys.keyOf(press(AndroidKeyEvent.KEYCODE_EQUALS, turkish, ctrl = true)))
        assertEquals(Key.Equals, PhysicalKeys.keyOf(press(AndroidKeyEvent.KEYCODE_MINUS, turkish)))
    }
}
