package app.nagmedar.ui.cast

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-1302: the video player while casting: where it plays, the transport on the renderer, the picker, casting stopped. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class CastingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recorder : CastingActions {
        val done = mutableListOf<String>()

        override fun togglePlayPause() {
            done += "play/pause"
        }

        override fun previous() {
            done += "previous"
        }

        override fun next() {
            done += "next"
        }

        override fun seekTo(ms: Long) {
            done += "seek $ms"
        }

        override fun seekBy(deltaMs: Int) {
            done += "by $deltaMs"
        }

        override fun choose() {
            done += "picker"
        }

        override fun stopCasting() {
            done += "stop casting"
        }

        override fun close() {
            done += "close"
        }
    }

    @Test
    fun theControlsActThroughThePlaybackService() {
        val actions = Recorder()
        val state = CastingScreenState(
            device = CastDevice.of("Salon TV", "chromecast"),
            title = "Kuzey Işığı",
            playing = true,
            timeMs = 60_000,
            lengthMs = 600_000,
            hasPrevious = false,
            hasNext = true,
        )
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) { CastingScreen(state, actions) }
        }
        compose.onNodeWithText("Salon TV cihazına yayınlanıyor").assertExists()
        compose.onNodeWithContentDescription("Duraklat").performClick()
        compose.onNodeWithContentDescription("10 sn ileri").performClick()
        compose.onNodeWithContentDescription("Sonraki").performClick()
        // the queue's first item: nothing before it
        compose.onNodeWithContentDescription("Önceki").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Salon TV cihazına yayınlanıyor").performClick()
        compose.onNodeWithTag("casting-stop").performClick()
        assertEquals(listOf("play/pause", "by 10000", "next", "picker", "stop casting"), actions.done)
    }
}
