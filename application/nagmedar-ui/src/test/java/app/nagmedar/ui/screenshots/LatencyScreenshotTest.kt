package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.audio.latency.AppliedLatency
import app.nagmedar.audio.latency.LatencyProfile
import app.nagmedar.audio.latency.LatencyProfiles
import app.nagmedar.audio.latency.OutputDeviceKey
import app.nagmedar.ui.audio.latency.LatencyMeasureSession
import app.nagmedar.ui.audio.latency.LatencyResult
import app.nagmedar.ui.audio.latency.LatencySection
import app.nagmedar.ui.audio.latency.MeasureFailure
import app.nagmedar.ui.audio.latency.MeasureSurface
import app.nagmedar.ui.audio.latency.MemoryLatencyStore
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-613, for approval: Settings › Latency profiles in DeX with three devices and the buds in
 * use, on an English phone; the measurement's window at each step: the explanation on a tablet,
 * the tones under way, the result with the speaker's share taken off, and the tones not heard.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LatencyScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val speaker = OutputDeviceKey(LatencyProfiles.TYPE_BUILTIN_SPEAKER, "SM-X936B")
    private val buds = OutputDeviceKey(LatencyProfiles.TYPE_BLUETOOTH_A2DP, "Galaxy Buds2 Pro", "AA:BB:CC:DD:EE:FF")
    private val receiver = OutputDeviceKey(LatencyProfiles.TYPE_HDMI_EARC, "Denon AVR-X2800H")

    private val profiles = listOf(
        LatencyProfile(speaker, 0, measured = true, spreadMs = 2, calibrationMs = 10),
        LatencyProfile(buds, 180, measured = true, spreadMs = 4),
        LatencyProfile(receiver, 90),
    )

    private fun capture(name: String, theme: ThemeMode, input: InputMode, content: @Composable () -> Unit) {
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
    }

    @Composable
    private fun Section() {
        LatencySection(store = MemoryLatencyStore(profiles), active = buds, applied = flowOf(AppliedLatency(buds, 180, profiles[1])))
    }

    @Composable
    private fun Measure(state: LatencyMeasureSession.State) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MeasureSurface(device = buds, state = state, denied = false, onStart = {}, onRetry = {}, onSave = {}, onDismiss = {})
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
    fun settingsDex() = capture("settings-latency-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
        Box(Modifier.padding(top = 16.dp)) { Section() }
    }

    @Test @Config(qualifiers = "en-w390dp-h844dp-hdpi")
    fun settingsPhoneEnglish() = capture("settings-latency-phone-light-en", ThemeMode.Light, InputMode.Touch) {
        Box(Modifier.padding(top = 16.dp)) { Section() }
    }

    @Test @Config(qualifiers = "tr-w728dp-h880dp-mdpi")
    fun introTablet() = capture("latency-measure-intro-tablet-dark", ThemeMode.Dark, InputMode.Touch) {
        Measure(LatencyMeasureSession.State.Intro)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
    fun measuringDex() = capture("latency-measure-running-dex-light", ThemeMode.Light, InputMode.Pointer) {
        Measure(LatencyMeasureSession.State.Measuring(0.6f))
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun resultPhone() = capture("latency-measure-result-phone-dark", ThemeMode.Dark, InputMode.Touch) {
        Measure(LatencyMeasureSession.State.Done(LatencyResult(rawMs = 193, spreadMs = 4, valid = 5, total = 5, exact = true, referenceMs = 10)))
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-hdpi")
    fun notHeardPhoneEnglish() = capture("latency-measure-failed-phone-light-en", ThemeMode.Light, InputMode.Touch) {
        Measure(LatencyMeasureSession.State.Failed(MeasureFailure.NotHeard))
    }
}
