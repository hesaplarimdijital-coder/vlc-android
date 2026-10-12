package app.nagmedar.ui.player

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.input.KeyChord
import app.nagmedar.ui.input.PhysicalKeys
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutPreset
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.TestLayouts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-602: the video player's keys are VLC's by default, every one of them doing its work there
 * (the table below), and follow the user's keys; a held key acts once unless it seeks, steps or
 * turns something; keys in no table stay the classic player's.
 */
@RunWith(AndroidJUnit4::class)
class PlayerKeysTest {

    /** VLC's player keys: what S-602 asks for, key by key. */
    private val vlcKeys: List<Triple<String, KeyChord, ShortcutId>> = listOf(
        Triple("Space", KeyChord(Key.Spacebar), ShortcutId.PlayPause),
        Triple("F", KeyChord(Key.F), ShortcutId.Fullscreen),
        Triple("M", KeyChord(Key.M), ShortcutId.Mute),
        Triple("G", KeyChord(Key.G), ShortcutId.SubtitleDelayDown),
        Triple("H", KeyChord(Key.H), ShortcutId.SubtitleDelayUp),
        Triple("J", KeyChord(Key.J), ShortcutId.AudioDelayDown),
        Triple("K", KeyChord(Key.K), ShortcutId.AudioDelayUp),
        Triple("E", KeyChord(Key.E), ShortcutId.NextFrame),
        Triple("A", KeyChord(Key.A), ShortcutId.AspectRatio),
        Triple("C", KeyChord(Key.C), ShortcutId.Crop),
        Triple("Z", KeyChord(Key.Z), ShortcutId.Zoom),
        Triple("T", KeyChord(Key.T), ShortcutId.ShowTime),
        Triple("V", KeyChord(Key.V), ShortcutId.NextSubtitle),
        Triple("B", KeyChord(Key.B), ShortcutId.NextAudioTrack),
        Triple("Ctrl+T", KeyChord(Key.T, ctrl = true), ShortcutId.JumpToTime),
        Triple("Shift+S", KeyChord(Key.S, shift = true), ShortcutId.Snapshot),
    )

    private val performed = mutableListOf<ShortcutId>()
    private val target = PlayerKeyTarget { id ->
        performed += id
        true
    }

    @Before
    fun usKeyboard() {
        TestLayouts.use(-1 to TestLayouts.us)
    }

    @After
    fun reset() {
        ShortcutRegistry.overrides = emptyMap()
        PhysicalKeys.reset()
    }

    private fun press(chord: KeyChord, repeat: Int = 0): KeyEvent {
        var meta = 0
        if (chord.ctrl) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (chord.shift) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (chord.alt) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        return KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, chord.key.nativeKeyCode, repeat, meta)
    }

    @Test
    fun vlcsKeysAreTheDefaults() {
        for ((name, chord, id) in vlcKeys) {
            assertEquals(name, chord, ShortcutRegistry.default(id).chord)
            assertEquals(name, chord.label(), ShortcutRegistry.chordLabel(id))
        }
    }

    @Test
    fun eachKeyDoesItsWorkInThePlayer() {
        for ((name, chord, id) in vlcKeys) {
            performed.clear()
            assertTrue(name, PlayerKeys.handle(press(chord), target))
            assertEquals(name, listOf(id), performed)
        }
    }

    @Test
    fun theUsersKeysWin() {
        ShortcutRegistry.overrides = ShortcutPreset.Mpv.overrides + (ShortcutId.Mute to KeyChord(Key.M, ctrl = true))
        PlayerKeys.handle(press(KeyChord(Key.Z)), target)
        PlayerKeys.handle(press(KeyChord(Key.O)), target)
        PlayerKeys.handle(press(KeyChord(Key.M, ctrl = true)), target)
        assertEquals(listOf(ShortcutId.SubtitleDelayDown, ShortcutId.ShowTime, ShortcutId.Mute), performed)
        // M alone was VLC's: given away, it is not the classic player's either
        assertTrue(PlayerKeys.handle(press(KeyChord(Key.M)), target))
        assertEquals(3, performed.size)
    }

    @Test
    fun aHeldKeyActsOnceUnlessItGoesOn() {
        assertTrue(PlayerKeys.handle(press(KeyChord(Key.Spacebar), repeat = 3), target))
        assertTrue(PlayerKeys.handle(press(KeyChord(Key.F), repeat = 1), target))
        assertEquals(emptyList<ShortcutId>(), performed)
        PlayerKeys.handle(press(KeyChord(Key.J), repeat = 2), target)
        PlayerKeys.handle(press(KeyChord(Key.DirectionRight, alt = true), repeat = 5), target)
        assertEquals(listOf(ShortcutId.AudioDelayDown, ShortcutId.JumpForward), performed)
    }

    @Test
    fun otherKeysStayTheClassicPlayers() {
        assertFalse("the arrows alone", PlayerKeys.handle(press(KeyChord(Key.DirectionLeft)), target))
        assertFalse("O, the classic options", PlayerKeys.handle(press(KeyChord(Key.O)), target))
        assertFalse(PlayerKeys.handle(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD), target))
        assertFalse("a key's release", PlayerKeys.handle(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE), target))
        assertEquals(emptyList<ShortcutId>(), performed)
    }

    @Test
    fun whileTheEditorRecordsKeysTheyAreNotShortcuts() {
        ShortcutRegistry.pause(this, true)
        try {
            assertFalse(PlayerKeys.handle(press(KeyChord(Key.Spacebar)), target))
        } finally {
            ShortcutRegistry.pause(this, false)
        }
        assertTrue(performed.isEmpty())
    }
}
