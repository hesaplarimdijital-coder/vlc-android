package app.nagmedar.ui.player.subtitles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Turkish at the bottom, English above it. */
internal val sampleDual = DualSubtitles(sampleSubtitleTracks, primary = "spu/2", secondary = "spu/4", secondaryOn = true)

/**
 * S-703: the subtitles' tab shows the primary and the secondary track, where the subtitles came
 * from and the key of the secondary; the tracks are chosen from their lists, the secondary turned
 * off with its switch; its place waits for the native side.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class SubtitlesPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun panel(state: DualSubtitles = sampleDual, columns: Int = 1, sync: (@Composable () -> Unit)? = null) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                SubtitlesPanel(
                    state = state,
                    note = "Kuzey Işığı.tr.srt · klasörden eşlendi",
                    layoutSupported = false,
                    position = SecondaryPosition.Top,
                    onPrimary = { calls += "primary:$it" },
                    onSecondary = { calls += "secondary:$it" },
                    onSecondaryOn = { calls += "on:$it" },
                    onPosition = { calls += "position:$it" },
                    columns = columns,
                    sync = sync,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun bothTracksTheirSourceAndTheKey() {
        panel()
        compose.onNodeWithText("İZLER").assertExists()
        compose.onNodeWithText("Türkçe · SRT").assertExists()
        compose.onNodeWithText("English · PGS").assertExists()
        compose.onNodeWithTag("subtitles-note").assertExists()
        compose.onNodeWithText("Alt+V").assertExists()
        compose.onNodeWithTag("subtitles-secondary-switch").assertIsOn()
        compose.onNodeWithTag("subtitles-pending").assertExists()
    }

    @Test
    fun theTracksAreChosenAndTheSecondaryTurnedOff() {
        panel()
        compose.onNodeWithText("Türkçe · SRT").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Türkçe · zorunlu · SRT") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("English · PGS").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Yok") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("subtitles-secondary-switch").performClick()
        assertEquals(listOf("primary:spu/3", "secondary:null", "on:false"), calls)
    }

    @Test
    fun aSingleTrackHasNoSecondary() {
        panel(DualSubtitles(sampleSubtitleTracks.take(1), primary = "spu/2"))
        compose.onNodeWithTag("subtitles-secondary-switch").assertIsOff()
        compose.onNodeWithText("Yok").assertExists()
    }

    private val slot = @Composable { Box(Modifier.fillMaxWidth().height(40.dp).testTag("slot")) }

    /** S-704: the delay and the sync assistant after the tracks, before the secondary's place. */
    @Test
    fun theDelayComesAfterTheTracks() {
        panel(sync = slot)
        val tracks = compose.onNodeWithText("İZLER").getBoundsInRoot()
        val delay = compose.onNodeWithTag("slot").getBoundsInRoot()
        val secondary = compose.onNodeWithText("İKİNCİL ALTYAZI").getBoundsInRoot()
        assertTrue(tracks.top < delay.top && delay.bottom <= secondary.top)
    }

    /** Under the video: the tracks and the secondary on the left, the delay on the right. */
    @Test
    fun underTheVideoTheDelayIsBesideTheTracks() {
        panel(columns = 2, sync = slot)
        val delay = compose.onNodeWithTag("slot").getBoundsInRoot()
        val tracks = compose.onNodeWithText("İZLER").getBoundsInRoot()
        val secondary = compose.onNodeWithText("İKİNCİL ALTYAZI").getBoundsInRoot()
        assertTrue(delay.left > tracks.right && secondary.left < delay.left && secondary.top > tracks.top)
    }
}
