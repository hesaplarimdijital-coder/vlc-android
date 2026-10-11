package app.nagmedar.ui.input

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.screens.tableOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.view.KeyEvent as AndroidKeyEvent

/**
 * S-402: chords written and read back, keys the system and the window keep, the VLC and mpv
 * presets without conflicts, the user's keys in the table, the menus and the exported file.
 */
@RunWith(AndroidJUnit4::class)
class ShortcutBindingsTest {

    @After
    fun reset() {
        ShortcutRegistry.overrides = emptyMap()
    }

    @Test
    fun everyChordReadsBackFromItsText() {
        val chords = ShortcutPreset.entries.flatMap { it.chords.values }.filterNotNull()
        chords.forEach { assertEquals(it.toText(), it, parseChord(it.toText())) }
        assertEquals("Ctrl+Shift+N", KeyChord(Key.N, ctrl = true, shift = true).toText())
        assertEquals("Alt+Left", KeyChord(Key.DirectionLeft, alt = true).toText())
        assertEquals("Ctrl+Comma", KeyChord(Key.Comma, ctrl = true).toText())
    }

    @Test
    fun chordsAreReadLeniently() {
        assertEquals(KeyChord(Key.K, ctrl = true), parseChord(" control + k "))
        assertEquals(KeyChord(Key.DirectionLeft, shift = true), parseChord("shift+LEFT"))
        assertEquals(KeyChord(Key.Spacebar), parseChord("Space"))
        assertEquals(KeyChord(Key.F5, alt = true), parseChord("Alt+F5"))
        assertNull(parseChord(""))
        assertNull(parseChord("Ctrl+"))
        assertNull(parseChord("Hyper+K"))
        assertNull(parseChord("Ctrl+Shift"))
    }

    @Test
    fun theSystemsAndTheWindowsKeysAreKept() {
        assertEquals(R.string.nag_sc_reserved_system, reservedReason(KeyChord(Key.D, meta = true)))
        assertEquals(R.string.nag_sc_reserved_system, reservedReason(KeyChord(Key.Tab, alt = true)))
        assertEquals(R.string.nag_sc_reserved_system, reservedReason(KeyChord(Key.F4, alt = true)))
        assertEquals(R.string.nag_sc_reserved_menu, reservedReason(KeyChord(Key.F10)))
        assertEquals(R.string.nag_sc_reserved_navigation, reservedReason(KeyChord(Key.Escape)))
        assertEquals(R.string.nag_sc_reserved_navigation, reservedReason(KeyChord(Key.Enter)))
        assertNull(reservedReason(KeyChord(Key.Enter, alt = true)))
        assertNull(reservedReason(KeyChord(Key.K, ctrl = true)))
        // the presets keep clear of them
        ShortcutPreset.entries.forEach { preset ->
            preset.chords.values.filterNotNull().forEach { assertNull("$preset ${it.toText()}", reservedReason(it)) }
        }
    }

    @Test
    fun thePresetsHaveNoConflicts() {
        assertTrue(ShortcutPreset.Vlc.overrides.isEmpty())
        assertEquals(ShortcutPreset.Vlc, ShortcutPreset.of(emptyMap()))
        val mpv = ShortcutPreset.Mpv.overrides
        assertEquals(KeyChord(Key.Q), mpv[ShortcutId.Stop])
        assertEquals(KeyChord(Key.J), mpv[ShortcutId.NextSubtitle])
        assertEquals(ShortcutPreset.Mpv, ShortcutPreset.of(mpv))
        assertNull(ShortcutPreset.of(mapOf(ShortcutId.Stop to null)))
        ShortcutPreset.entries.forEach { preset ->
            val conflicts = ShortcutRegistry.conflicts(tableOf(preset.overrides))
            assertTrue("$preset: " + conflicts.joinToString { (a, b) -> "${a.id}/${b.id}" }, conflicts.isEmpty())
        }
    }

    @Test
    fun overridesKeepTheirTextForm() {
        val overrides = mapOf(ShortcutId.Stop to KeyChord(Key.Q), ShortcutId.Mute to null)
        assertEquals(overrides, ShortcutOverrides.parse(ShortcutOverrides.format(overrides)))
        // unknown shortcuts and keys are left out
        assertEquals(mapOf(ShortcutId.Stop to KeyChord(Key.Q)), ShortcutOverrides.parse("Stop=Q\nTeleport=Ctrl+K\nMute=Hyper+M"))
        assertTrue(ShortcutOverrides.parse(null).isEmpty())
    }

    private fun down(code: Int, meta: Int = 0) = KeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN, code, 0, meta))

    @Test
    fun theUsersKeysAreTheTables() {
        ShortcutRegistry.overrides = mapOf(ShortcutId.Stop to KeyChord(Key.Q), ShortcutId.Mute to null)
        assertEquals(KeyChord(Key.Q), ShortcutRegistry[ShortcutId.Stop].chord)
        assertNull(ShortcutRegistry[ShortcutId.Mute].chord)
        assertEquals(KeyChord(Key.S), ShortcutRegistry.default(ShortcutId.Stop).chord)
        assertEquals(ShortcutId.Stop, ShortcutRegistry.match(down(AndroidKeyEvent.KEYCODE_Q))?.id)
        assertNull(ShortcutRegistry.match(down(AndroidKeyEvent.KEYCODE_M)))
        assertNull(ShortcutRegistry.match(down(AndroidKeyEvent.KEYCODE_S)))
        // the menus and the system's list show them
        val res = ApplicationProvider.getApplicationContext<Context>().resources
        val specs = menuBarSpecs(res, {}, { true })
        val playback = specs[ShortcutGroup.Playback.ordinal].entries().filterIsInstance<MenuEntry.Item>()
        assertEquals("Q", playback.first { it.label == res.getString(R.string.nag_sc_stop) }.shortcut)
        val audio = specs[ShortcutGroup.Audio.ordinal].entries().filterIsInstance<MenuEntry.Item>()
        assertNull(audio.first { it.label == res.getString(R.string.nag_sc_mute) }.shortcut)
        assertEquals(ShortcutRegistry.defaults.size - 1, ShortcutRegistry.keyboardShortcutGroups(res).sumOf { it.items.size })
    }

    @Test
    fun theTableGoesOutAndComesBackAsAFile() {
        val overrides = ShortcutPreset.Mpv.overrides + (ShortcutId.Mute to null)
        val back = ShortcutFile.import(ShortcutFile.export(tableOf(overrides)))
        assertEquals(overrides, back.overrides)
        assertEquals(ShortcutId.entries.size, back.applied)
        assertTrue(back.skipped.isEmpty())
        // unknown names and the system's keys are left out; what the file leaves out keeps its default
        val partial = ShortcutFile.import(
            """{"type": "nagmedar-shortcuts", "version": 1, "bindings": {"Stop": "Ctrl+Q", "Teleport": "T", "Mute": "Meta+M"}}""",
        )
        assertEquals(mapOf(ShortcutId.Stop to KeyChord(Key.Q, ctrl = true)), partial.overrides)
        assertEquals(1, partial.applied)
        assertEquals(listOf("Teleport", "Mute"), partial.skipped)
        assertThrows(IllegalArgumentException::class.java) { ShortcutFile.import("{}") }
        assertThrows(IllegalArgumentException::class.java) { ShortcutFile.import("bir altyazı dosyası") }
    }
}
