package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import app.nagmedar.ui.perf.PerfView
import app.nagmedar.ui.perf.PerformanceCard
import app.nagmedar.ui.perf.samplePerfView
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
 * S-1404: Settings › Performance with what a Tab S11 Ultra measured (seven start-ups) in DeX,
 * on a tablet in English and on a phone; and before anything was measured.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PerformanceScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, view: PerfView) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(top = 16.dp)) {
                    SettingsSection(stringResource(R.string.nag_perf_title)) { PerformanceCard(view, {}, {}) }
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h720dp-mdpi")
    fun dex() = capture("settings-performance-dex-dark", ThemeMode.Dark, InputMode.Pointer, samplePerfView())

    @Test @Config(qualifiers = "en-w800dp-h760dp-mdpi")
    fun tabletEnglish() = capture("settings-performance-tablet-light-en", ThemeMode.Light, InputMode.Touch, samplePerfView())

    @Test @Config(qualifiers = "tr-w390dp-h1000dp-hdpi")
    fun phone() = capture("settings-performance-phone-dark", ThemeMode.Dark, InputMode.Touch, samplePerfView())

    @Test @Config(qualifiers = "tr-w1280dp-h480dp-mdpi")
    fun empty() = capture("settings-performance-empty-dex-light", ThemeMode.Light, InputMode.Pointer, PerfView(emptyList()))
}
