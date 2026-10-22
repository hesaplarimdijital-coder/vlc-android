package app.nagmedar.ui.player

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.input.ShortcutId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * The player's equaliser (Ctrl+Shift+E, its menu, the effects tab's button) is what is heard:
 * VLC's while libVLC plays the film, the radio or a stream; the music engine's DSP chain while
 * the engine plays.
 */
@RunWith(AndroidJUnit4::class)
class PlayerAppWindowsTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).get()

    @Test
    fun vlcsEqualizerWhileLibVlcPlays() {
        val windows = PlayerAppWindows(musicEngine = { false })
        assertTrue(windows.perform(activity, ShortcutId.Equalizer))
        assertEquals(PlayerAppWindow.Equalizer, windows.open)
    }

    @Test
    fun theDspChainWhileTheMusicEnginePlays() {
        val windows = PlayerAppWindows(musicEngine = { true })
        assertTrue(windows.perform(activity, ShortcutId.Equalizer))
        assertEquals(PlayerAppWindow.MusicEqualizer, windows.open)
        windows.close()
        assertEquals(null, windows.open)
    }
}
