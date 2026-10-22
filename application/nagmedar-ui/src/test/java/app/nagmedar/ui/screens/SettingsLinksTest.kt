package app.nagmedar.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.prefs.NagmedarLink
import app.nagmedar.ui.prefs.PreferenceCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "All settings"' lines that Nağmedar's own settings have lead to the section with that key in
 * Settings' list: found by its key, not by a count that went stale when sections were added
 * (Remote access opened the podcasts, Export and Restore the remote access).
 */
@RunWith(AndroidJUnit4::class)
class SettingsLinksTest {

    /** Each line that leads into Settings, by its preference key, and the key of the section it must reach. */
    private val expected = mapOf(
        "directories" to "library",
        "app_theme" to "appearance",
        "remote_access_category" to "remote_access",
        "enable_remote_access" to "remote_access",
        "remote_access_status" to "remote_access",
        "export_settings" to "backup",
        "restore_settings" to "backup",
    )

    @Test
    fun everyLinkReachesTheSectionWithItsKey() {
        val keys = SettingsItem.entries.map { it.key }
        (PreferenceCatalog.links.keys + expected.keys).forEach { preference ->
            val link = requireNotNull(PreferenceCatalog.linkFor(preference)) { "$preference leads nowhere" }
            val item = link.settingsItem ?: return@forEach
            assertEquals(preference, expected[preference], keys[item.index])
        }
    }

    @Test
    fun theEqualiserAndAboutArePagesOfTheirOwn() {
        assertNull(NagmedarLink.Equalizer.settingsItem)
        assertNull(NagmedarLink.About.settingsItem)
    }

    @Test
    fun eachSectionHasItsOwnKeyAndPlace() {
        val entries = SettingsItem.entries
        assertEquals(entries.size, entries.map { it.key }.toSet().size)
        entries.forEachIndexed { place, item -> assertEquals(item.key, place, item.index) }
        // the shortcut card's "Edit shortcuts…" and the home page's "Klasör ekle"
        assertEquals("shortcuts", entries[SettingsItem.Shortcuts.index].key)
        assertEquals("library", entries[SettingsItem.Library.index].key)
    }
}
