package app.nagmedar.ui.screens

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NagmedarPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.tools.NAGMEDAR_MIDDLE_MUTE
import org.videolan.tools.NAGMEDAR_WINDOW_BOUNDS

/** S-408, S-409: the player's fullscreen request and the mouse settings are kept; a window's place reads back. */
@RunWith(AndroidJUnit4::class)
class WindowSettingsTest {

    private val shared = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("window-test", Context.MODE_PRIVATE)

    @Test
    fun thePlayersAndTheMousesSettingsAreKept() {
        val prefs = NagmedarPreferences(shared)
        assertTrue(prefs.playerFullscreen)
        assertFalse(prefs.wheelSeeks)
        prefs.updatePlayerFullscreen(false)
        prefs.updateWheelSeeks(true)
        prefs.updateMiddleClick(NAGMEDAR_MIDDLE_MUTE)
        val again = NagmedarPreferences(shared)
        assertFalse(again.playerFullscreen)
        assertTrue(again.wheelSeeks)
        assertEquals(NAGMEDAR_MIDDLE_MUTE, again.middleClick)
    }

    @Test
    fun aWindowsPlaceReadsBack() {
        val bounds = Rect(120, 80, 1400, 880)
        shared.edit().putString(NAGMEDAR_WINDOW_BOUNDS, bounds.flattenToString()).commit()
        assertEquals(bounds, Rect.unflattenFromString(shared.getString(NAGMEDAR_WINDOW_BOUNDS, null)))
    }
}
