package app.nagmedar.ui

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.input.KeyChord
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.ThemeMode
import androidx.compose.ui.input.key.Key
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * S-307: Ctrl+Shift+N opens another window of the library on the same screen, a task of its own
 * (beside the first in split screen); the system offers new windows too; what one window changes
 * in the settings, the others follow.
 */
@RunWith(AndroidJUnit4::class)
class WindowsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun reset() {
        ShortcutRegistry.overrides = emptyMap()
    }

    @Test
    fun aNewWindowIsATaskOfItsOwnOnTheSameScreen() {
        val intent = newWindowIntent(context, "movies", adjacent = false)
        assertEquals(NagmedarActivity::class.java.name, intent.component?.className)
        val separate = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        assertEquals(separate, intent.flags and separate)
        assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        assertEquals("movies", intent.getStringExtra(NAGMEDAR_EXTRA_DESTINATION))
        // split screen: beside this one
        val beside = newWindowIntent(context, null, adjacent = true)
        assertTrue(beside.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT != 0)
        assertNull(beside.getStringExtra(NAGMEDAR_EXTRA_DESTINATION))
    }

    private fun activity(manifest: Element, name: String): Element? {
        val activities = manifest.getElementsByTagName("activity")
        return (0 until activities.length).map { activities.item(it) as Element }.firstOrNull { it.getAttribute("android:name") == name }
    }

    @Test
    fun theManifestSaysSo() {
        // the module's manifest, read as written (tests run in the module's directory)
        val file = File("src/main/AndroidManifest.xml")
        val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val shell = activity(manifest, "app.nagmedar.ui.NagmedarActivity")!!
        val properties = shell.getElementsByTagName("property")
        val multiInstance = (0 until properties.length).map { properties.item(it) as Element }
            .firstOrNull { it.getAttribute("android:name") == "android.window.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI" }
        assertEquals("true", multiInstance?.getAttribute("android:value"))
        assertFalse("standard launch: many windows", shell.hasAttribute("android:launchMode"))
    }

    @Test
    fun whatOneWindowChangesTheOthersFollow() {
        val shared = context.getSharedPreferences("windows-test", Context.MODE_PRIVATE)
        val first = NagmedarPreferences(shared)
        val second = NagmedarPreferences(shared)
        second.follow()
        first.updateThemeMode(ThemeMode.Light)
        first.updateAccent(Accent.Coral)
        first.updateSidebarCollapsed(true)
        first.updateShortcutOverrides(mapOf(ShortcutId.Stop to KeyChord(Key.Q)))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ThemeMode.Light, second.themeMode)
        assertEquals(Accent.Coral, second.accent)
        assertTrue(second.sidebarCollapsed)
        assertEquals(mapOf(ShortcutId.Stop to KeyChord(Key.Q)), second.shortcutOverrides)
        // a window that closed follows no more
        second.stopFollowing()
        first.updateThemeMode(ThemeMode.Dark)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ThemeMode.Light, second.themeMode)
    }
}
