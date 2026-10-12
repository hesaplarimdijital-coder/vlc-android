package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.player.subtitles.SecondaryPosition
import app.nagmedar.ui.player.subtitles.SubtitlesPanel
import app.nagmedar.ui.player.subtitles.sampleDual
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-703: the player with its subtitles' tab, after the mockups' player: Turkish at the bottom,
 * English above, as VLC draws them over the film (drawn here by hand); "TR + EN" on the bar; in
 * English; under the video in half a DeX window; on a phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerSubtitlesTabScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, input: InputMode = InputMode.Pointer, controls: Boolean = true) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                PlayerWithPanel(PanelTab.Subtitles, controls, overVideo = { Lines() }, secondarySubtitles = "4") { columns ->
                    SubtitlesPanel(
                        state = sampleDual,
                        note = "Kuzey Işığı.tr.srt · klasörden eşlendi",
                        layoutSupported = false,
                        position = SecondaryPosition.Top,
                        onPrimary = {},
                        onSecondary = {},
                        onSecondaryOn = {},
                        onPosition = {},
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

    /** The two subtitles as VLC 4 draws them: the secondary at the top, the primary at the bottom. */
    @Composable
    private fun BoxScope.Lines() {
        val style = NagTheme.type.title.copy(color = Color.White, shadow = Shadow(Color.Black, blurRadius = 6f), textAlign = TextAlign.Center)
        Text("When the lights go out, the stars show the way.", style = style.copy(fontSize = 17.sp), modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp, start = 24.dp, end = 24.dp))
        Text("Işıklar söndüğünde, yolu yıldızlar gösterir.", style = style.copy(fontSize = 22.sp), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 132.dp, start = 24.dp, end = 24.dp))
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dex() = capture("player-subtitles-tab-dex")

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun dexEnglish() = capture("player-subtitles-tab-dex-en")

    @Test @Config(qualifiers = "tr-w728dp-h880dp-mdpi")
    fun halfWindow() = capture("player-subtitles-tab-half")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("player-subtitles-tab-phone", input = InputMode.Touch, controls = false)
}
