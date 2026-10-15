package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import app.nagmedar.ui.tvlab.NoTvLabActions
import app.nagmedar.ui.tvlab.TvLabContent
import app.nagmedar.ui.tvlab.remux.RemuxCard
import app.nagmedar.ui.tvlab.remux.sampleDoneJob
import app.nagmedar.ui.tvlab.remux.sampleFailedJob
import app.nagmedar.ui.tvlab.remux.samplePausedJob
import app.nagmedar.ui.tvlab.remux.sampleRunningJob
import app.nagmedar.ui.tvlab.sampleTvLabView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * S-1206, S-1209, for approval: the TV Lab with the sample film's remux queue (one running, one
 * done and verified with its badge, one failed at the verification) in a DeX window, on a tablet
 * and on a phone (the whole page); the queue's card alone in English, with a job paused for the
 * battery.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemuxScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = TimeZone.getDefault()

    private val jobs = listOf(sampleRunningJob(), sampleDoneJob(), sampleFailedJob())

    private fun capture(name: String, theme: ThemeMode, input: InputMode, content: @Composable () -> Unit) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"))
        try {
            compose.runOnUiThread { compose.activity.actionBar?.hide() }
            compose.setContent {
                NagmedarTheme(themeMode = theme, inputMode = input) {
                    Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
                }
            }
            compose.waitForIdle()
            val window = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { window.draw(Canvas(bitmap)) }
            ShellScreenshotTest.save(name, bitmap)
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    private fun lab(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode) = capture(name, theme, input) {
        TestShell(widthClass, startDestination = Destination.TvLab) { _, _ -> TvLabContent(sampleTvLabView().copy(jobs = jobs), NoTvLabActions) }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h1100dp-mdpi")
    fun dex() = lab("tvlab-remux-dex-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1000dp-h1300dp-mdpi")
    fun tablet() = lab("tvlab-remux-tablet-light", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w411dp-h3000dp-hdpi")
    fun phoneWholePage() = lab("tvlab-remux-phone-dark-tall", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "en-w600dp-h700dp-mdpi")
    fun cardEnglish() = capture("tvlab-remux-card-light-en", ThemeMode.Light, InputMode.Pointer) {
        Box(Modifier.padding(16.dp)) { RemuxCard(listOf(samplePausedJob(), sampleDoneJob()), onCancel = {}, onRemove = {}) }
    }
}
