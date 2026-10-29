package app.nagmedar.macrobenchmark

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-1404, Nağmedar 1.0: a track of the Music's table played as the listener plays it (a tap on its
 * row): the playback session says it plays within a few seconds, and still does after
 * nagmedar.seconds. Needs music on the device; the Baseline Profile's run takes the same path.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackTest {

    private val device = uiDevice()

    @Before
    fun firstRun() = device.pastFirstRun()

    @Test
    fun trackPlays() {
        device.pressHome()
        device.executeShellCommand("am start -W -n $TARGET/$SHELL_ACTIVITY --es $EXTRA_DESTINATION music")
        val list = waitFor { device.findObject(By.res("music"))?.findObject(By.scrollable(true)) }
        assumeTrue("no music to play: the Music needs music on the device", list != null)
        val track = waitFor { list!!.findObject(By.clickable(true)) }
        assumeTrue("the Music's table has no track", track != null)
        track!!.click()
        assertTrue("the track did not start within ${START_MS / 1000} s", waitPlaying(START_MS))
        SystemClock.sleep(Arguments.playSeconds * 1_000L)
        assertTrue("the track stopped before ${Arguments.playSeconds} s", playing())
        device.pressHome()
    }

    /** Whether the app's playback session says it plays (PlaybackState.STATE_PLAYING). */
    private fun playing(): Boolean =
        device.executeShellCommand("dumpsys media_session").split("package=").drop(1)
            .any { it.startsWith(TARGET) && "state=PlaybackState {state=3," in it }

    private fun waitPlaying(timeoutMs: Long): Boolean {
        val until = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < until) {
            if (playing()) return true
            SystemClock.sleep(250)
        }
        return false
    }

    private companion object {
        /** A tapped track plays within this. */
        const val START_MS = 10_000L
    }
}
