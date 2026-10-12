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
import app.nagmedar.ui.player.samplePlayerState
import app.nagmedar.ui.player.subtitles.EncodingSample
import app.nagmedar.ui.player.subtitles.EncodingSurface
import app.nagmedar.ui.player.subtitles.OnlineProblem
import app.nagmedar.ui.player.subtitles.OnlineQuota
import app.nagmedar.ui.player.subtitles.OnlineSearchState
import app.nagmedar.ui.player.subtitles.OnlineSubtitlesSurface
import app.nagmedar.ui.player.subtitles.SubtitleChooser
import app.nagmedar.ui.player.subtitles.SubtitleDropOverlay
import app.nagmedar.ui.player.subtitles.SubtitleEncoding
import app.nagmedar.ui.player.subtitles.sampleEncoding
import app.nagmedar.ui.player.subtitles.sampleFound
import app.nagmedar.ui.player.subtitles.sampleOnline
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-701: the subtitles found in the film's folder, offered over the video above the control bar
 * (DeX, in English, on a phone); and files carried over the video, to be dropped. S-702: "Subtitle
 * encoding…" on a Turkish SRT in Windows-1254, detected; in English with Windows-1252 chosen, its
 * letters broken in the preview; on a phone. S-706: "Search OpenSubtitles", the subtitle timed for
 * the file first, the day's quota; in English; on a phone; and a build without the key.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerSubtitlesScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, input: InputMode = InputMode.Pointer, overVideo: @Composable BoxScope.() -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        val visibility = ChromeVisibility(active = true).apply {
            onMouseMoved()
            overBars = true
        }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    SampleVideo(Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                    DesktopControls(samplePlayerState, FakePlayerActions(), visibility, FakePreviews())
                    overVideo()
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
    private fun BoxScope.Found() {
        SubtitleChooser(sampleFound, onChoose = {}, onDismiss = {}, modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 120.dp))
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dex() = capture("player-subtitles-found-dex") { Found() }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun dexEnglish() = capture("player-subtitles-found-dex-en") { Found() }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("player-subtitles-found-phone", InputMode.Touch) { Found() }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun drop() = capture("player-subtitles-drop-dex") { SubtitleDropOverlay() }

    @Composable
    private fun Encoding(sample: EncodingSample = sampleEncoding) {
        // the dialog's dim, drawn here: the window holds only the activity
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        EncodingSurface(sample, onApply = {}, onDismiss = {})
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun encoding() = capture("player-encoding-dex") { Encoding() }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun encodingEnglish() = capture("player-encoding-dex-en") {
        Encoding(EncodingSample(sampleEncoding.name, sampleEncoding.head, sampleEncoding.detected, chosen = SubtitleEncoding.Windows1252))
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun encodingPhone() = capture("player-encoding-phone", InputMode.Touch) { Encoding() }

    @Composable
    private fun Online(state: OnlineSearchState = sampleSearch) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        OnlineSubtitlesSurface(
            state = state,
            quota = OnlineQuota(14, 20, null),
            nowMs = 0,
            onName = {},
            onLanguage = {},
            onByHash = {},
            onSearch = {},
            onDownload = {},
            onDismiss = {},
        )
    }

    private val sampleSearch = OnlineSearchState("Kuzey Işığı 2024", listOf("tr", "en"), listOf("tr", "en"), results = sampleOnline)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun online() = capture("player-online-dex") { Online() }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun onlineEnglish() = capture("player-online-dex-en") { Online(sampleSearch.copy(languages = listOf("en", "tr"), offered = listOf("en", "tr"))) }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun onlinePhone() = capture("player-online-phone", InputMode.Touch) { Online(sampleSearch.copy(downloading = sampleOnline[0].fileId)) }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun onlineWithoutKey() = capture("player-online-nokey-dex") { Online(sampleSearch.copy(results = null, problem = OnlineProblem.NoKey)) }
}
