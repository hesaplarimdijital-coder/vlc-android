package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.screens.AboutFacts
import app.nagmedar.ui.screens.AboutScreen
import app.nagmedar.ui.screens.sampleThirdParties
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

/**
 * S-209, for approval: the about page in a DeX window (beside each other: who and what, and the
 * libraries with FFmpeg's licence open), on a tablet, and on a phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val facts = AboutFacts("3.7.0-nagmedar.1", "4.0.0-eap20", "0.13.13-rc14")

    private fun capture(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode, open: String? = null) {
        val libraries = sampleThirdParties()
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    TestShell(widthClass, startDestination = Destination.About) { _, _ ->
                        AboutScreen(libraries, onOpenLink = {}, facts = facts)
                    }
                }
            }
        }
        compose.waitForIdle()
        if (open != null) {
            compose.onNode(hasText(open) and hasClickAction()).performClick()
            compose.waitForIdle()
        }
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun dex() = capture("about-dex-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer, open = "FFmpeg")

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tablet() = capture("about-tablet-light", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("about-phone-dark", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch)

    /** S-1501: in English. */
    @Test @Config(qualifiers = "en-w411dp-h891dp-hdpi")
    fun phoneEnglish() = capture("about-phone-dark-en", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch)
}
