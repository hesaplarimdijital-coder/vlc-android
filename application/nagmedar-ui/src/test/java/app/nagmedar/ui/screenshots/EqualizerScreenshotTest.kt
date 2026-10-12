package app.nagmedar.ui.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.audio.DspMeter
import app.nagmedar.audio.DspSettings
import app.nagmedar.audio.EqBand
import app.nagmedar.audio.EqType
import app.nagmedar.audio.GraphicPresets
import app.nagmedar.audio.HeadphoneProfile
import app.nagmedar.audio.withProfile
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.audio.EqualizerScreen
import app.nagmedar.ui.audio.rememberEqualizerState
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
import org.videolan.tools.NAGMEDAR_DSP
import org.videolan.tools.NAGMEDAR_DSP_PROFILES

/**
 * S-906, for approval: the equaliser with VLC's Rock preset on its twenty bands, two
 * parametric bands, a headphone profile and the limiter, in DeX with the clip meter running,
 * on a phone in English, and bypassed on a tablet.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EqualizerScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val hd600 = HeadphoneProfile(
        "Sennheiser HD 600", -6.4,
        listOf(
            EqBand(105.0, 4.5, 0.7, EqType.LowShelf),
            EqBand(2_200.0, -2.1, 1.6),
            EqBand(3_300.0, 3.2, 2.5),
            EqBand(6_000.0, -4.2, 3.0),
            EqBand(10_000.0, 2.0, 0.7, EqType.HighShelf),
        ),
    )

    private val chain = DspSettings(
        preampDb = -1.5,
        autoPreamp = true,
        graphicEnabled = true,
        graphicDb = GraphicPresets.bands("rock")!!,
        parametricEnabled = true,
        parametric = listOf(EqBand(60.0, 3.0, 0.9, EqType.LowShelf), EqBand(4_500.0, -2.5, 4.0)),
        limiterEnabled = true,
    ).withProfile(hd600)

    private fun capture(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode, dsp: DspSettings, meter: DspMeter?) {
        val settings = compose.activity.getSharedPreferences("eq-shot-$name", Context.MODE_PRIVATE)
        settings.edit().clear()
            .putString(NAGMEDAR_DSP, dsp.toJson())
            .putString(NAGMEDAR_DSP_PROFILES, HeadphoneProfile.listToJson(listOf(hd600)))
            .apply()
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    TestShell(widthClass, startDestination = Destination.Equalizer) { _, _ ->
                        EqualizerScreen(state = rememberEqualizerState(settings), meter = meter)
                    }
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h1700dp-mdpi")
    fun dex() = capture(
        "equalizer-dex-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer, chain,
        DspMeter(frames = 48_000L * 95, clipped = 0, peak = 1.31, reductionDb = 1.9),
    )

    @Test @Config(qualifiers = "en-w411dp-h2600dp-hdpi")
    fun phoneEnglish() = capture(
        "equalizer-phone-light-en", WidthClass.Compact, ThemeMode.Light, InputMode.Touch, chain.copy(limiterEnabled = false),
        DspMeter(frames = 48_000L * 20, clipped = 37, peak = 1.12, reductionDb = 0.0),
    )

    @Test @Config(qualifiers = "tr-w1000dp-h1700dp-mdpi")
    fun tabletBypassed() = capture("equalizer-tablet-dark-bypassed", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch, chain.copy(bypass = true), null)
}
