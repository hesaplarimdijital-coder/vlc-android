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
import app.nagmedar.ui.components.TooltipDelay
import app.nagmedar.ui.screens.InputSection
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-404: Settings > Giriş with the density and the tooltip delay; in DeX, on a tablet and a phone. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InputSettingsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, changed: Boolean) {
        val prefs = NagmedarPreferences(compose.activity.getSharedPreferences("input-shot-$name", Context.MODE_PRIVATE))
        if (changed) {
            prefs.updateTooltipDelay(TooltipDelay.Short)
        }
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(top = 16.dp)) { InputSection(prefs) }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h520dp-mdpi-keysexposed-qwerty")
    fun dex() = capture("settings-input-dex-dark", ThemeMode.Dark, InputMode.Pointer, changed = true)

    @Test @Config(qualifiers = "tr-w800dp-h560dp-mdpi")
    fun tablet() = capture("settings-input-tablet-light", ThemeMode.Light, InputMode.Touch, changed = false)

    @Test @Config(qualifiers = "tr-w390dp-h860dp-hdpi")
    fun phone() = capture("settings-input-phone-dark", ThemeMode.Dark, InputMode.Touch, changed = false)

    /** S-1501: in English. */
    @Test @Config(qualifiers = "en-w800dp-h560dp-mdpi")
    fun tabletEnglish() = capture("settings-input-tablet-light-en", ThemeMode.Light, InputMode.Touch, changed = false)
}
