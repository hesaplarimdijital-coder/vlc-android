package app.nagmedar.ui.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.audio.AbSide
import app.nagmedar.audio.DspMeter
import app.nagmedar.audio.DspSettings
import app.nagmedar.audio.EngineState
import app.nagmedar.audio.EqBand
import app.nagmedar.audio.EqType
import app.nagmedar.audio.GraphicPresets
import app.nagmedar.audio.HeadphoneProfile
import app.nagmedar.audio.ReplayGainMode
import app.nagmedar.audio.SignalPath
import app.nagmedar.audio.withProfile
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.audio.AbTestState
import app.nagmedar.ui.audio.EqualizerScreen
import app.nagmedar.ui.audio.EqualizerSide
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
import kotlin.random.Random

/**
 * S-906, S-910 and S-912, for approval: the equaliser with VLC's Rock preset on its twenty bands,
 * two parametric bands, a headphone profile and the limiter, in DeX with the clip meter running
 * and the signal path and the A/B test beside the chain; on a phone in English, a blind test in
 * its fourth round; bypassed on a tablet, bit-perfect to a USB DAC; and a blind test's results.
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

    private fun capture(
        name: String,
        widthClass: WidthClass,
        theme: ThemeMode,
        input: InputMode,
        dsp: DspSettings,
        meter: DspMeter?,
        side: (@Composable ColumnScope.() -> Unit)? = null,
    ) {
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
                        EqualizerScreen(state = rememberEqualizerState(settings), meter = meter, side = side)
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

    /** The engine's path for [chain]: a 24/96 FLAC to a USB DAC, or a 16/44.1 ALAC to AAudio. */
    private fun path(usb: Boolean, stages: List<SignalPath.DspStage>, abSide: AbSide = AbSide.A, replayGainDb: Double = 0.0) = SignalPath(
        state = EngineState.Playing,
        source = if (usb) {
            SignalPath.Source(1, "file:///music/a.flac", "flac", "flac", 96_000, 2, 24, false, 312_000, 0, 0, false)
        } else {
            SignalPath.Source(1, "file:///music/a.m4a", "alac", "mov,mp4,m4a,3gp,3g2,mj2", 44_100, 2, 16, false, 254_000, 0, 0, false)
        },
        output = if (usb) SignalPath.Output("usb", 96_000, 2, "s24", 24, true) else SignalPath.Output("aaudio", 48_000, 2, "f32", 32, false),
        volume = 1.0,
        crossfadeMs = 0,
        replayGain = SignalPath.ReplayGainStage(if (replayGainDb != 0.0) ReplayGainMode.Track else ReplayGainMode.Off, 0.0, replayGainDb, true, false),
        tempo = 1.0,
        pitch = 0.0,
        balance = 0.0,
        mono = false,
        dsp = stages,
        dspActive = stages.none { it.name == "bypass" },
        dspLevelDb = -2.4,
        abSide = abSide,
        abGainDb = if (abSide == AbSide.B) -2.4 else 0.0,
        bitPerfect = stages.any { it.name == "bypass" } && usb,
        reason = when {
            stages.any { it.name == "bypass" } -> if (usb) SignalPath.Reason.None else SignalPath.Reason.Output
            replayGainDb != 0.0 -> SignalPath.Reason.ReplayGain
            else -> SignalPath.Reason.Dsp
        },
    )

    private val stages = listOf(
        SignalPath.DspStage("preamp", null, -1.5, null, null, null),
        SignalPath.DspStage("graphic_eq", 20, null, null, null, null),
        SignalPath.DspStage("parametric_eq", 2, null, null, null, null),
        SignalPath.DspStage("profile", 5, null, "Sennheiser HD 600", null, null),
        SignalPath.DspStage("auto_preamp", null, -4.5, null, null, null),
        SignalPath.DspStage("limiter", null, -1.0, null, 5.0, 100.0),
    )

    /** A blind test of [rounds] rounds, [answered] of them answered: right but for every [wrongEvery]th. */
    private fun blindTest(rounds: Int, answered: Int, wrongEvery: Int = 4): AbTestState {
        val ab = AbTestState(engine = {}, random = Random(5))
        ab.startBlind(rounds)
        repeat(answered) { i ->
            val x = ab.rounds[ab.round].x
            ab.answer(if ((i + 1) % wrongEvery == 0) (if (x == AbSide.A) AbSide.B else AbSide.A) else x)
        }
        return ab
    }

    @Test @Config(qualifiers = "tr-w1280dp-h1700dp-mdpi")
    fun dex() = capture(
        "equalizer-dex-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer, chain,
        DspMeter(frames = 48_000L * 95, clipped = 0, peak = 1.31, reductionDb = 1.9),
    ) { EqualizerSide(path(usb = true, stages = stages), outputDevice = "Topping D50s", ab = AbTestState(engine = {})) }

    @Test @Config(qualifiers = "en-w411dp-h3600dp-hdpi")
    fun phoneEnglish() = capture(
        "equalizer-phone-light-en", WidthClass.Compact, ThemeMode.Light, InputMode.Touch, chain.copy(limiterEnabled = false),
        DspMeter(frames = 48_000L * 20, clipped = 37, peak = 1.12, reductionDb = 0.0),
    ) {
        val ab = blindTest(rounds = 16, answered = 3)
        EqualizerSide(path(usb = false, stages = stages.dropLast(1), abSide = ab.side, replayGainDb = -4.1), outputDevice = null, ab = ab)
    }

    @Test @Config(qualifiers = "tr-w1000dp-h2600dp-mdpi")
    fun tabletBypassed() = capture(
        "equalizer-tablet-dark-bypassed", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch, chain.copy(bypass = true), null,
    ) { EqualizerSide(path(usb = true, stages = listOf(SignalPath.DspStage("bypass", null, null, null, null, null))), outputDevice = "Topping D50s", ab = AbTestState(engine = {})) }

    /** S-912: a blind test of sixteen rounds, thirteen right: under 5 % by guessing. */
    @Test @Config(qualifiers = "tr-w420dp-h1900dp-mdpi")
    fun blindTestResults() {
        val ab = blindTest(rounds = 16, answered = 16, wrongEvery = 5)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EqualizerSide(path(usb = true, stages = stages), outputDevice = "Topping D50s", ab = ab)
                    }
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save("equalizer-ab-results-light", bitmap)
    }
}
