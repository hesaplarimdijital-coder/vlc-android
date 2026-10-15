package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.player.subtitles.SecondaryPosition
import app.nagmedar.ui.player.subtitles.SubtitlesPanel
import app.nagmedar.ui.player.subtitles.sampleDual
import app.nagmedar.ui.player.subtitles.tools.FAULTY_SRT
import app.nagmedar.ui.player.subtitles.tools.FakePlayback
import app.nagmedar.ui.player.subtitles.tools.SDH_SRT
import app.nagmedar.ui.player.subtitles.tools.SubtitleToolsSection
import app.nagmedar.ui.player.subtitles.tools.SubtitleToolsSurface
import app.nagmedar.ui.player.subtitles.tools.ToolsTab
import app.nagmedar.ui.player.subtitles.tools.toolsController
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
 * S-708: the subtitle tools. The subtitles' tab with its entry to them; the window over the
 * video (dark) or over the library (light) on a DeX window, a tablet and a phone, in English too:
 * the cleaning with its changed lines, the frame rate with the times before and after, and the
 * line timing editor on a file with a wrong time and two overlapping lines, the film at hand.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubtitleToolsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun snap(name: String) {
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    private fun capture(
        name: String,
        tab: ToolsTab,
        text: String = SDH_SRT,
        theme: ThemeMode = ThemeMode.Dark,
        input: InputMode = InputMode.Pointer,
        film: Boolean = false,
        prepare: (app.nagmedar.ui.player.subtitles.tools.SubtitleToolsController) -> Unit = {},
    ) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        val controller = toolsController(text = text, playback = FakePlayback(position = 6_200).takeIf { film })
        controller.setTab(tab)
        prepare(controller)
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Backdrop(dark = theme == ThemeMode.Dark) { SubtitleToolsSurface(controller, onOpenFile = {}, onDismiss = {}) }
            }
        }
        snap(name)
    }

    /** The video under the player's window, or the library under the library's; dimmed, as a dialog dims. */
    @Composable
    private fun Backdrop(dark: Boolean, window: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(if (dark) Color.Black else NagTheme.colors.background))
        if (dark) SampleVideo(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        window()
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun cleanDex() = capture("subtools-clean-dex", ToolsTab.Clean)

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun cleanDexEnglish() = capture("subtools-clean-dex-en", ToolsTab.Clean)

    @Test @Config(qualifiers = "tr-w728dp-h880dp-mdpi")
    fun cleanTabletLight() = capture("subtools-clean-tablet-light", ToolsTab.Clean, theme = ThemeMode.Light)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun cleanPhone() = capture("subtools-clean-phone", ToolsTab.Clean, input = InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun rateDex() = capture("subtools-rate-dex", ToolsTab.FrameRate) {
        it.setRates(app.nagmedar.ui.player.subtitles.tools.FrameRate.Fps23976, app.nagmedar.ui.player.subtitles.tools.FrameRate.Fps25)
        it.setShift(-250)
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun ratePhoneLight() = capture("subtools-rate-phone-light", ToolsTab.FrameRate, theme = ThemeMode.Light, input = InputMode.Touch) {
        it.setRates(app.nagmedar.ui.player.subtitles.tools.FrameRate.Fps25, app.nagmedar.ui.player.subtitles.tools.FrameRate.Fps24)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun linesDex() = capture("subtools-lines-dex", ToolsTab.Edit, FAULTY_SRT, film = true) { it.click(4) }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun linesDexEnglish() = capture("subtools-lines-dex-en", ToolsTab.Edit, FAULTY_SRT, film = true) { it.click(2) }

    @Test @Config(qualifiers = "tr-w728dp-h880dp-mdpi")
    fun linesTabletLight() = capture("subtools-lines-tablet-light", ToolsTab.Edit, SDH_SRT, theme = ThemeMode.Light) { it.click(3) }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun linesPhone() = capture("subtools-lines-phone", ToolsTab.Edit, FAULTY_SRT, input = InputMode.Touch) { it.click(4) }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun panelEntry() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                PlayerWithPanel(PanelTab.Subtitles, controls = true, secondarySubtitles = "4") { columns ->
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
                        tools = { SubtitleToolsSection(onOpen = {}) },
                    )
                }
            }
        }
        snap("subtools-panel-dex")
    }
}
