package app.nagmedar.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.0.1: Settings on a touch phone leaves out what only a keyboard or a mouse uses (Input, the
 * keyboard shortcuts) unless it was changed or asked for, never on DeX; the developer tools wait
 * at the end behind their row.
 */
class SettingsSectionsTest {

    private fun shown(
        item: SettingsItem,
        keyboard: Boolean = false,
        pointer: Boolean = false,
        inputChanged: Boolean = false,
        shortcutsChanged: Boolean = false,
        developerOpen: Boolean = false,
        revealed: Set<SettingsItem> = emptySet(),
    ) = settingsShown(item, keyboard, pointer, inputChanged, shortcutsChanged, developerOpen, revealed)

    @Test
    fun aTouchPhoneLeavesOutTheKeyboardsAndTheMousesSections() {
        assertFalse(shown(SettingsItem.Input))
        assertFalse(shown(SettingsItem.Shortcuts))
        // the user's own settings stay in sight everywhere
        listOf(SettingsItem.Appearance, SettingsItem.Library, SettingsItem.Music, SettingsItem.Backup, SettingsItem.About, SettingsItem.Developer)
            .forEach { assertTrue(it.name, shown(it)) }
    }

    @Test
    fun aKeyboardOrAMouseBringsThemBack() {
        for (item in listOf(SettingsItem.Input, SettingsItem.Shortcuts)) {
            assertTrue(shown(item, keyboard = true))
            assertTrue(shown(item, pointer = true))
            // DeX: both
            assertTrue(shown(item, keyboard = true, pointer = true))
        }
    }

    @Test
    fun whatTheUserChangedStaysInSight() {
        assertTrue(shown(SettingsItem.Input, inputChanged = true))
        assertFalse(shown(SettingsItem.Shortcuts, inputChanged = true))
        assertTrue(shown(SettingsItem.Shortcuts, shortcutsChanged = true))
    }

    @Test
    fun aLinkShowsTheSectionItAskedFor() {
        assertTrue(shown(SettingsItem.Shortcuts, revealed = setOf(SettingsItem.Shortcuts)))
        assertFalse(shown(SettingsItem.Input, revealed = setOf(SettingsItem.Shortcuts)))
        val reveal = SettingsReveal()
        reveal.reveal(SettingsItem.Input)
        reveal.reveal(SettingsItem.Input)
        assertEquals(setOf(SettingsItem.Input), reveal.items)
    }

    @Test
    fun theDeveloperToolsWaitBehindTheirRowAtTheEnd() {
        assertFalse(shown(SettingsItem.Lab))
        assertFalse(shown(SettingsItem.Performance, keyboard = true, pointer = true))
        assertTrue(shown(SettingsItem.Lab, developerOpen = true))
        assertTrue(shown(SettingsItem.Performance, developerOpen = true))
        val entries = SettingsItem.entries
        assertEquals(listOf(SettingsItem.Developer, SettingsItem.Lab, SettingsItem.Performance), entries.takeLast(3))
    }
}
