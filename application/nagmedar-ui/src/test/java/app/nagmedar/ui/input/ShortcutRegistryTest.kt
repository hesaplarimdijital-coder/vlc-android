package app.nagmedar.ui.input

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-401: one table, no chord bound twice, the same label everywhere, events map back to shortcuts. */
@RunWith(AndroidJUnit4::class)
class ShortcutRegistryTest {

    @Test
    fun noChordIsBoundTwice() {
        val conflicts = ShortcutRegistry.conflicts()
        assertTrue(conflicts.joinToString { (a, b) -> "${a.id}/${b.id}: ${a.chord.label()}" }, conflicts.isEmpty())
    }

    @Test
    fun conflictsAreDetected() {
        val doubled = ShortcutRegistry.defaults + ShortcutRegistry[ShortcutId.Mute].copy(id = ShortcutId.Stop)
        assertEquals(1, ShortcutRegistry.conflicts(doubled).size)
    }

    @Test
    fun everyIdHasExactlyOneShortcut() {
        val ids = ShortcutRegistry.defaults.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ShortcutId.entries.toSet(), ids.toSet())
    }

    @Test
    fun everyMenuGroupHasEntries() {
        for (group in ShortcutGroup.entries) {
            assertTrue("$group is empty", ShortcutRegistry.defaults.any { it.group == group })
        }
    }

    @Test
    fun labels() {
        assertEquals("Ctrl+K", KeyChord(Key.K, ctrl = true).label())
        assertEquals("Ctrl+Shift+N", KeyChord(Key.N, ctrl = true, shift = true).label())
        assertEquals("Alt+→", KeyChord(Key.DirectionRight, alt = true).label())
        assertEquals("Space", KeyChord(Key.Spacebar).label())
        assertEquals("Ctrl+,", KeyChord(Key.Comma, ctrl = true).label())
    }

    @Test
    fun vlcDefaults() {
        assertEquals(KeyChord(Key.Spacebar), ShortcutRegistry[ShortcutId.PlayPause].chord)
        assertEquals(KeyChord(Key.M), ShortcutRegistry[ShortcutId.Mute].chord)
        assertEquals(KeyChord(Key.G), ShortcutRegistry[ShortcutId.SubtitleDelayDown].chord)
        assertEquals(KeyChord(Key.H), ShortcutRegistry[ShortcutId.SubtitleDelayUp].chord)
        assertEquals(KeyChord(Key.J), ShortcutRegistry[ShortcutId.AudioDelayDown].chord)
        assertEquals(KeyChord(Key.K), ShortcutRegistry[ShortcutId.AudioDelayUp].chord)
    }

    @Test
    fun eventsMatchShortcuts() {
        val ctrlK = KeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_K, 0, AndroidKeyEvent.META_CTRL_ON or AndroidKeyEvent.META_CTRL_LEFT_ON))
        assertEquals(ShortcutId.CommandPalette, ShortcutRegistry.match(ctrlK)?.id)
        val space = KeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_SPACE))
        assertEquals(ShortcutId.PlayPause, ShortcutRegistry.match(space)?.id)
        // G is a player shortcut: not matched in the global scope
        val g = KeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_G))
        assertNull(ShortcutRegistry.match(g))
        assertEquals(ShortcutId.SubtitleDelayDown, ShortcutRegistry.match(g, setOf(ShortcutScope.Global, ShortcutScope.Player))?.id)
    }

    @Test
    fun systemShortcutListCoversEveryShortcut() {
        val resources = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val groups = ShortcutRegistry.keyboardShortcutGroups(resources)
        assertEquals(ShortcutGroup.entries.size, groups.size)
        assertEquals(ShortcutRegistry.defaults.size, groups.sumOf { it.items.size })
    }
}
