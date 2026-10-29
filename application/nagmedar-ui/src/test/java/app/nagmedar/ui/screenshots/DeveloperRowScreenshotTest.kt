package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.screens.DeveloperRow
import app.nagmedar.ui.screens.SettingsSection
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
 * 1.0.1: the end of Settings: the last section of the user's, then the developer tools (Lab,
 * Performance) closed behind their one row, on a phone and in DeX.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeveloperRowScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Column(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(top = 16.dp)) {
                    SettingsSection(stringResource(R.string.nag_settings_start)) {
                        NagButton(stringResource(R.string.nag_settings_first_run_again), {})
                    }
                    var open by remember { mutableStateOf(false) }
                    DeveloperRow(open) { open = !open }
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w384dp-h400dp-xhdpi")
    fun phone() = capture("settings-developer-phone-light", ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "en-w1280dp-h360dp-mdpi")
    fun dexEnglish() = capture("settings-developer-dex-dark-en", ThemeMode.Dark, InputMode.Pointer)
}
