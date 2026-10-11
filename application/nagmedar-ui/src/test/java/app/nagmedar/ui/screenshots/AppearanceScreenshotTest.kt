package app.nagmedar.ui.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.screens.AppearanceSection
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-204: Settings > Appearance with the wallpaper accent, on a phone, a tablet and in a DeX window. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, accent: Accent, input: InputMode) {
        val prefs = NagmedarPreferences(compose.activity.getSharedPreferences("appearance-shot-$name", Context.MODE_PRIVATE))
        prefs.updateThemeMode(theme)
        prefs.updateAccent(accent)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = prefs.themeMode, accent = prefs.accent, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(top = 16.dp)) { AppearanceSection(prefs) }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w390dp-h420dp-hdpi")
    fun phoneDarkWallpaper() = capture("appearance-phone-dark", ThemeMode.Dark, Accent.System, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w800dp-h360dp-mdpi")
    fun tabletLight() = capture("appearance-tablet-light", ThemeMode.Light, Accent.Coral, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1280dp-h360dp-mdpi")
    fun dexOledWallpaper() = capture("appearance-dex-oled", ThemeMode.Oled, Accent.System, InputMode.Pointer)
}
