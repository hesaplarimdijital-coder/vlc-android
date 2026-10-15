package app.nagmedar.ui.input

import androidx.compose.ui.input.key.Key
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-1107, S-207: the two keys of radio recording are their own and sit with the tools. */
@RunWith(AndroidJUnit4::class)
class RadioRecordingShortcutsTest {

    @Test
    fun recordTheStationThatPlaysAndOpenTheRecordings() {
        assertEquals(KeyChord(Key.R, alt = true), ShortcutRegistry[ShortcutId.RadioRecord].chord)
        assertEquals(KeyChord(Key.R, alt = true, shift = true), ShortcutRegistry[ShortcutId.RadioRecordings].chord)
        assertEquals(ShortcutGroup.Tools, ShortcutRegistry[ShortcutId.RadioRecord].group)
        assertEquals(ShortcutGroup.Tools, ShortcutRegistry[ShortcutId.RadioRecordings].group)
    }

    @Test
    fun noOtherShortcutTakesThem() {
        val taken = ShortcutRegistry.defaults.filter { it.chord == KeyChord(Key.R, alt = true) || it.chord == KeyChord(Key.R, alt = true, shift = true) }
        assertEquals(listOf(ShortcutId.RadioRecord, ShortcutId.RadioRecordings), taken.map { it.id })
        assertTrue(ShortcutRegistry.conflicts().isEmpty())
    }

    @Test
    fun theVideoRecorderKeepsItsOwnKey() {
        assertEquals(KeyChord(Key.R, shift = true), ShortcutRegistry[ShortcutId.Record].chord)
    }
}
