package app.nagmedar.ui.remoteaccess

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-1304: Settings › Remote access: the switches reach the settings, the security text is said, the pages open. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h1400dp-mdpi")
class RemoteAccessSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val control = RecordingControl()

    private fun show(): RemoteAccessSettings {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("ra-section-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = RemoteAccessSettings(prefs, control)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) { RemoteAccessSection(settings) }
        }
        return settings
    }

    @Test
    fun theSwitchStartsAndStopsIt() {
        val settings = show()
        compose.onNodeWithTag("ra-enable").assertIsOff()
        compose.onNodeWithTag("ra-enable").performClick()
        compose.onNodeWithTag("ra-enable").assertIsOn()
        assertTrue(settings.enabled)
        assertEquals(listOf("start", "how"), control.calls)
        compose.onNodeWithTag("ra-enable").performClick()
        assertFalse(settings.enabled)
        assertEquals(listOf("start", "how", "stop"), control.calls)
    }

    @Test
    fun theDefaultsAreShown() {
        show()
        listOf("Audio", "Playlists", "Search").forEach { compose.onNodeWithTag("ra-part-$it").assertIsOn() }
        // 1.0 plays no video: its part is not offered
        compose.onNodeWithTag("ra-part-Video").assertDoesNotExist()
        compose.onNodeWithTag("ra-files").assertIsOff()
        compose.onNodeWithTag("ra-network").assertIsOff()
        compose.onNodeWithTag("ra-history").assertIsOff()
        compose.onNodeWithTag("ra-playback").assertIsOn()
        compose.onNodeWithTag("ra-logs").assertIsOff()
    }

    @Test
    fun aLibraryPartIsTakenOut() {
        val settings = show()
        compose.onNodeWithTag("ra-part-Playlists").performClick()
        compose.onNodeWithTag("ra-part-Playlists").assertIsOff()
        assertEquals(setOf(LibraryPart.Video, LibraryPart.Audio, LibraryPart.Search), settings.library)
    }

    @Test
    fun theBrowsersAndTheLogsAreSwitched() {
        val settings = show()
        compose.onNodeWithTag("ra-files").performClick()
        compose.onNodeWithTag("ra-history").performClick()
        compose.onNodeWithTag("ra-playback").performClick()
        compose.onNodeWithTag("ra-logs").performClick()
        assertTrue(settings.files && settings.history && settings.logs)
        assertFalse(settings.playback)
        // the network browser is the one that restarts the server
        compose.onNodeWithTag("ra-network").performClick()
        assertTrue(settings.network)
        assertEquals(listOf("restart"), control.calls)
    }

    @Test
    fun theSecurityIsSaidNotSet() {
        show()
        compose.onNodeWithTag("ra-security").assertExists()
        compose.onNodeWithText("HTTPS", substring = true).assertExists()
        compose.onNodeWithText("tek seferlik", substring = true).assertExists()
    }

    @Test
    fun theAddressesNeedItOn() {
        show()
        compose.onNodeWithTag("ra-addresses").assertIsNotEnabled()
        compose.onNodeWithTag("ra-enable").performClick()
        control.calls.clear()
        compose.onNodeWithTag("ra-addresses").performClick()
        compose.onNodeWithTag("ra-how").performClick()
        assertEquals(listOf("addresses", "how"), control.calls)
    }
}
