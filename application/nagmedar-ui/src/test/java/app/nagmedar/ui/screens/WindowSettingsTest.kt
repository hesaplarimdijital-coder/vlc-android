package app.nagmedar.ui.screens

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.tools.NAGMEDAR_WINDOW_BOUNDS

/** S-409: a window's place reads back. */
@RunWith(AndroidJUnit4::class)
class WindowSettingsTest {

    private val shared = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("window-test", Context.MODE_PRIVATE)

    @Test
    fun aWindowsPlaceReadsBack() {
        val bounds = Rect(120, 80, 1400, 880)
        shared.edit().putString(NAGMEDAR_WINDOW_BOUNDS, bounds.flattenToString()).commit()
        assertEquals(bounds, Rect.unflattenFromString(shared.getString(NAGMEDAR_WINDOW_BOUNDS, null)))
    }
}
