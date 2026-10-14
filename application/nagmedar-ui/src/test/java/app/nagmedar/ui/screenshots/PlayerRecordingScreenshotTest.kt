package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.ChromeVisibility
import app.nagmedar.ui.player.DesktopControls
import app.nagmedar.ui.player.FakePlayerActions
import app.nagmedar.ui.player.FakePreviews
import app.nagmedar.ui.player.record.RecordingCard
import app.nagmedar.ui.player.record.RecordingFailure
import app.nagmedar.ui.player.record.RecordingIndicator
import app.nagmedar.ui.player.record.RecordingOutcome
import app.nagmedar.ui.player.record.RecordingStatus
import app.nagmedar.ui.player.samplePlayerState
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-607: a recording over the video: its mark at the top right while it is made (under the title
 * while the controls show), the card it leaves once kept (DeX, in English, a tablet, a phone);
 * the copy into its folder going on beside the card of a folder that was gone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerRecordingScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, status: RecordingStatus, controls: Boolean = true, input: InputMode = InputMode.Pointer, card: @Composable BoxScope.() -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        val visibility = ChromeVisibility(active = true).apply {
            onMouseMoved()
            overBars = true
        }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    SampleVideo(Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                    if (controls) DesktopControls(samplePlayerState.copy(recording = status is RecordingStatus.On), FakePlayerActions(), visibility, FakePreviews())
                    RecordingIndicator(status, onStop = {}, modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = if (controls) 64.dp else 16.dp))
                    card()
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Composable
    private fun BoxScope.Card(outcome: RecordingOutcome, bottom: Int = 120) {
        RecordingCard(
            outcome = outcome,
            onPlay = {},
            onOpenFolder = {},
            onChooseFolder = {},
            onRetry = {},
            onDismiss = {},
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = bottom.dp),
            stays = true,
        )
    }

    private val recording = RecordingStatus.On(elapsedMs = 754_000, bytes = 248_300_000)

    private fun saved(forSpace: Boolean = false) = RecordingOutcome.Saved(
        uri = "content://media/external/video/media/42",
        fileName = "Kuzey Işığı 2026-10-15 21-04-33.ts",
        folder = "Movies/Nağmedar",
        tree = null,
        mime = "video/mp2t",
        bytes = 1_240_000_000,
        durationMs = 1_865_000,
        stoppedForSpace = forSpace,
    )

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dex() = capture("player-recording-dex", recording) { Card(saved()) }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun dexEnglish() = capture("player-recording-dex-en", recording) { Card(saved(forSpace = true)) }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tablet() = capture("player-recording-tablet", RecordingStatus.Starting, controls = false) { Card(saved(), bottom = 24) }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("player-recording-phone", recording, controls = false, input = InputMode.Touch) {
        Card(
            RecordingOutcome.Saved(
                uri = "content://media/external/audio/media/7",
                fileName = "TRT Radyo 1 2026-10-15 21-04-33.mp3",
                folder = "Music/Nağmedar",
                tree = null,
                mime = "audio/mpeg",
                bytes = 14_200_000,
                durationMs = 912_000,
            ),
            bottom = 24,
        )
    }

    @Test @Config(qualifiers = "tr-w728dp-h880dp-mdpi")
    fun folderGone() = capture("player-recording-failed-half", RecordingStatus.Saving(copied = 620_000_000, total = 1_240_000_000)) {
        Card(RecordingOutcome.Failed(RecordingFailure.Folder, canRetry = true))
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun notSupported() = capture("player-recording-unsupported-dex", RecordingStatus.Off) { Card(RecordingOutcome.Failed(RecordingFailure.NotSupported)) }
}
