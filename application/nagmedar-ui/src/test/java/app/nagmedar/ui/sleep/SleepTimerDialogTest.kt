package app.nagmedar.ui.sleep

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-1007: the sleep timer's window sets the timer, then says what is left, adds to it and turns it off. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
class SleepTimerDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private class Player : SleepTarget {
        override var playing = true
        override val volume: Int get() = 100
        override val trackLeftMs: Long? = null
        var stopAfterTrack = false

        override fun setVolume(volume: Int) = Unit

        override fun stop() {
            playing = false
        }

        override fun stopAtTrackEnd(): Boolean {
            stopAfterTrack = true
            return true
        }

        override fun goOnAfterTrack() {
            stopAfterTrack = false
        }
    }

    private val clock = 1_000_000L
    private val player = Player()

    /** The test looks at the window, not the clock: the timer's coroutines never run. */
    private val timer = SleepTimer(target = { player }, scope = CoroutineScope(Job().apply { cancel() }), now = { clock })
    private var dismissed = 0

    private fun render() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                SleepTimerSurface(timer) { dismissed++ }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aTimeThenTheTrackPlayedOut() {
        render()
        compose.onNodeWithText("45 dk").performClick()
        compose.onNodeWithText("Çalan parça bitsin").performClick()
        compose.onNodeWithTag("sleep-start").performClick()
        val state = timer.state.value!!
        assertEquals(clock + 45 * 60_000L, state.endsAt)
        assertTrue(state.atTrackEnd)
        assertTrue("the fade is on by default", state.fade)
        assertEquals(1, dismissed)
        // the window again: what is left, then more of it, then off
        compose.onNodeWithText("45:00 kaldı").assertExists()
        compose.onNodeWithText("Süre bitince çalan parça sonuna kadar çalar; son 30 saniyesinde ses yavaşça kısılır.").assertExists()
        compose.onNodeWithText("+15 dk").performClick()
        assertEquals(60 * 60_000L, timer.remainingMs())
        compose.onNodeWithText("Zamanlayıcıyı kapat").performClick()
        assertNull(timer.state.value)
        assertEquals(2, dismissed)
    }

    @Test
    fun theTracksEndAlone() {
        render()
        compose.onNodeWithText("Parça sonu").performClick()
        compose.onNodeWithText("Çalan parça bitince oynatma durur.").assertExists()
        // the fade off: the track ends as it is
        compose.onNodeWithText("Son 30 saniyede sesi yavaşça kıs").performClick()
        compose.onNodeWithTag("sleep-start").performClick()
        assertTrue(player.stopAfterTrack)
        assertTrue(timer.state.value!!.waitingForTrackEnd)
        assertFalse(timer.state.value!!.fade)
        compose.onNodeWithText("Bu parça bitince durur").assertExists()
        compose.onNodeWithText("+15 dk").assertDoesNotExist()
        compose.onNodeWithText("Zamanlayıcıyı kapat").performClick()
        assertFalse("the track goes on to the next", player.stopAfterTrack)
    }
}
