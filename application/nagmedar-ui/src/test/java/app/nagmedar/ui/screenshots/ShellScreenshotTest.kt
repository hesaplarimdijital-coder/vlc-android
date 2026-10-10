package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * S-1402: the shell in the five width classes, light and dark, in Turkish. The PNGs land in
 * build/outputs/screenshots; CI compares them with src/test/screenshots and reports the
 * differences (buildsystem/ci/screenshot_report.py).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode, start: Destination = Destination.Movies) {
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    TestShell(widthClass, startDestination = start)
                }
            }
        }
        compose.waitForIdle()
        save(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun compactDark() = capture("compact-dark", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun compactLight() = capture("compact-light", WidthClass.Compact, ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w700dp-h1000dp-mdpi")
    fun mediumDark() = capture("medium-dark", WidthClass.Medium, ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w700dp-h1000dp-mdpi")
    fun mediumLight() = capture("medium-light", WidthClass.Medium, ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun expandedDark() = capture("expanded-dark", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun expandedLight() = capture("expanded-light", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun largeDark() = capture("large-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun largeLight() = capture("large-light", WidthClass.Large, ThemeMode.Light, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun extraLargeDark() = capture("xlarge-dark", WidthClass.ExtraLarge, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun extraLargeLight() = capture("xlarge-light", WidthClass.ExtraLarge, ThemeMode.Light, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun extraLargeOled() = capture("xlarge-oled-home", WidthClass.ExtraLarge, ThemeMode.Oled, InputMode.Pointer, Destination.Home)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun placeholderLarge() = capture("large-dark-tvlab", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer, Destination.TvLab)

    companion object {
        private val dir = File(System.getProperty("nagmedar.screenshotDir") ?: "build/outputs/screenshots")

        fun save(name: String, bitmap: Bitmap) {
            dir.mkdirs()
            FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
