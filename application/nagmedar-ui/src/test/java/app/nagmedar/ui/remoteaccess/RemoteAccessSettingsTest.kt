package app.nagmedar.ui.remoteaccess

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.resources.REMOTE_ACCESS_ONBOARDING
import org.videolan.tools.KEY_ENABLE_REMOTE_ACCESS
import org.videolan.tools.KEY_REMOTE_ACCESS_LAST_STATE_STOPPED
import org.videolan.tools.KEY_REMOTE_ACCESS_ML_CONTENT
import org.videolan.tools.REMOTE_ACCESS_FILE_BROWSER_CONTENT
import org.videolan.tools.REMOTE_ACCESS_HISTORY_CONTENT
import org.videolan.tools.REMOTE_ACCESS_LOGS
import org.videolan.tools.REMOTE_ACCESS_NETWORK_BROWSER_CONTENT
import org.videolan.tools.REMOTE_ACCESS_PLAYBACK_CONTROL

/** What the settings ask of the server, in order. */
internal class RecordingControl : RemoteAccessControl {
    val calls = mutableListOf<String>()

    override fun start() {
        calls += "start"
    }

    override fun stop() {
        calls += "stop"
    }

    override fun restart() {
        calls += "restart"
    }

    override fun showAddresses() {
        calls += "addresses"
    }

    override fun showHowItWorks() {
        calls += "how"
    }
}

/**
 * S-1304: the remote access settings write the preferences the classic screen and the server read
 * (same keys, same defaults), start and stop the service, and follow the notification's "Disable".
 */
@RunWith(AndroidJUnit4::class)
class RemoteAccessSettingsTest {

    private lateinit var prefs: SharedPreferences
    private val control = RecordingControl()

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("ra-settings-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    private fun settings() = RemoteAccessSettings(prefs, control)

    @Test
    fun theDefaultsAreTheClassicScreens() {
        val settings = settings()
        assertFalse(settings.enabled)
        assertEquals(LibraryPart.All, settings.library)
        assertFalse(settings.files)
        assertFalse(settings.network)
        assertFalse(settings.history)
        assertTrue(settings.playback)
        assertFalse(settings.logs)
    }

    @Test
    fun turningItOnStartsTheService() {
        val settings = settings()
        prefs.edit().putBoolean(KEY_REMOTE_ACCESS_LAST_STATE_STOPPED, true).commit()
        settings.enable(true)
        assertTrue(settings.enabled)
        assertTrue(prefs.getBoolean(KEY_ENABLE_REMOTE_ACCESS, false))
        // asked for on purpose, so it runs although it was stopped before
        assertFalse(prefs.getBoolean(KEY_REMOTE_ACCESS_LAST_STATE_STOPPED, true))
        assertEquals(listOf("start", "how"), control.calls)
    }

    @Test
    fun turningItOffStopsTheService() {
        val settings = settings()
        settings.enable(true)
        control.calls.clear()
        settings.enable(false)
        assertFalse(settings.enabled)
        assertFalse(prefs.getBoolean(KEY_ENABLE_REMOTE_ACCESS, true))
        assertEquals(listOf("stop"), control.calls)
    }

    @Test
    fun theHowItWorksPagesComeOnlyTheFirstTime() {
        val settings = settings()
        settings.enable(true)
        settings.enable(false)
        control.calls.clear()
        settings.enable(true)
        assertTrue(prefs.getBoolean(REMOTE_ACCESS_ONBOARDING, false))
        assertEquals(listOf("start"), control.calls)
    }

    @Test
    fun theSameStateAsksForNothing() {
        val settings = settings()
        settings.enable(false)
        assertTrue(control.calls.isEmpty())
        assertFalse(prefs.contains(KEY_ENABLE_REMOTE_ACCESS))
    }

    @Test
    fun theLibraryPartsAreStoredAsTheClassicScreenStoresThem() {
        val settings = settings()
        settings.showLibraryPart(LibraryPart.Playlists, false)
        settings.showLibraryPart(LibraryPart.Search, false)
        assertEquals(setOf(LibraryPart.Video, LibraryPart.Audio), settings.library)
        assertEquals(setOf("0", "1"), prefs.getStringSet(KEY_REMOTE_ACCESS_ML_CONTENT, null))
        settings.showLibraryPart(LibraryPart.Search, true)
        assertEquals(setOf("0", "1", "3"), prefs.getStringSet(KEY_REMOTE_ACCESS_ML_CONTENT, null))
        assertEquals(setOf(LibraryPart.Video, LibraryPart.Audio, LibraryPart.Search), settings().library)
    }

    @Test
    fun aValueNoPartHasIsPassedOver() {
        prefs.edit().putStringSet(KEY_REMOTE_ACCESS_ML_CONTENT, setOf("2", "9")).commit()
        assertEquals(setOf(LibraryPart.Playlists), settings().library)
    }

    @Test
    fun theSwitchesWriteTheirKeys() {
        val settings = settings()
        settings.showFiles(true)
        settings.showHistory(true)
        settings.controlPlayback(false)
        settings.shareLogs(true)
        assertTrue(prefs.getBoolean(REMOTE_ACCESS_FILE_BROWSER_CONTENT, false))
        assertTrue(prefs.getBoolean(REMOTE_ACCESS_HISTORY_CONTENT, false))
        assertFalse(prefs.getBoolean(REMOTE_ACCESS_PLAYBACK_CONTROL, true))
        assertTrue(prefs.getBoolean(REMOTE_ACCESS_LOGS, false))
        assertTrue(control.calls.isEmpty())
        val again = settings()
        assertTrue(again.files && again.history && again.logs)
        assertFalse(again.playback)
    }

    @Test
    fun theNetworkBrowserNeedsARestart() {
        val settings = settings()
        settings.showNetwork(true)
        assertTrue(prefs.getBoolean(REMOTE_ACCESS_NETWORK_BROWSER_CONTENT, false))
        assertEquals(listOf("restart"), control.calls)
    }

    @Test
    fun theNotificationsDisableIsFollowed() {
        val settings = settings()
        settings.enable(true)
        settings.attach()
        prefs.edit().putBoolean(KEY_ENABLE_REMOTE_ACCESS, false).commit()
        assertFalse(settings.enabled)
        settings.detach()
        prefs.edit().putBoolean(KEY_ENABLE_REMOTE_ACCESS, true).commit()
        assertFalse(settings.enabled)
    }

    @Test
    fun thePagesAreAskedFor() {
        val settings = settings()
        settings.showAddresses()
        settings.showHowItWorks()
        assertEquals(listOf("addresses", "how"), control.calls)
    }
}
