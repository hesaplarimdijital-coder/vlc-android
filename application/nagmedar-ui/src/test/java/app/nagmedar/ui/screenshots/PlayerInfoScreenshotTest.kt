package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import app.nagmedar.ui.library.TechSamples
import app.nagmedar.ui.player.info.InfoPanel
import app.nagmedar.ui.player.info.InfoRow
import app.nagmedar.ui.player.info.LiveStats
import app.nagmedar.ui.player.info.StatsOverlay
import app.nagmedar.ui.player.info.StatsSample
import app.nagmedar.ui.player.info.VideoDecoding
import app.nagmedar.ui.player.info.sampleFile
import app.nagmedar.ui.player.info.sampleTracks
import app.nagmedar.ui.player.info.streamRows
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/**
 * S-605: the player with its Info tab, after the mockups' "Panel · Info", and the statistics over
 * the video: Kuzey Işığı's streams from the media probe, its decoding, a minute of its input
 * bitrate; in English; under the video in half a DeX window; on a phone, for a finger.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerInfoScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, input: InputMode = InputMode.Pointer, controls: Boolean = true) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            val live = remember {
                LiveStats().apply {
                    for (i in 0 until 60) add(StatsSample((64 + 14 * sin(i / 5.2) + 9 * sin(i / 1.9)).toFloat(), 103_216L + i * 24, 0))
                }
            }
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                PlayerWithPanel(
                    PanelTab.Info,
                    controls,
                    overVideo = { StatsOverlay(live.last, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 64.dp)) },
                ) { columns ->
                    InfoPanel(
                        file = sampleFile,
                        streams = streamRows(TechSamples.kuzeyIsigi, sampleTracks, VideoDecoding.Hardware),
                        decoding = listOf(
                            InfoRow(stringResource(R.string.nag_info_video_decoder), stringResource(VideoDecoding.Hardware.label)),
                            InfoRow(stringResource(R.string.nag_info_video_output), null),
                            InfoRow(stringResource(R.string.nag_info_audio_decoder), null),
                            InfoRow(stringResource(R.string.nag_info_audio_output), "AAudio"),
                        ),
                        live = live,
                        overlay = true,
                        onOverlay = {},
                        report = { "" },
                        columns = columns,
                    )
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dex() = capture("player-info-dex")

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun dexEnglish() = capture("player-info-dex-en")

    @Test @Config(qualifiers = "tr-w728dp-h880dp-mdpi")
    fun halfWindow() = capture("player-info-half")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("player-info-phone", input = InputMode.Touch, controls = false)
}
