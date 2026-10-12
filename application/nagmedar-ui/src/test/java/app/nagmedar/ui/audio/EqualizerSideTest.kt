package app.nagmedar.ui.audio

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.audio.AbSide
import app.nagmedar.audio.EngineState
import app.nagmedar.audio.ReplayGainMode
import app.nagmedar.audio.SignalPath
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

/** S-910 and S-912: the signal path as the panel tells it, and the A/B test as the listener runs it. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w420dp-h2400dp-mdpi")
class EqualizerSideTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val sent = mutableListOf<AbSide>()

    private fun show(path: SignalPath?, ab: AbTestState = AbTestState(engine = { sent += it }, random = Random(3))): AbTestState {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EqualizerSide(path = path, outputDevice = "Topping D50s", ab = ab)
                }
            }
        }
        compose.waitForIdle()
        return ab
    }

    @Test
    fun everyStageWithItsFormat() {
        show(samplePath())
        listOf(
            "FLAC · 24 bit · 96 kHz · stereo",
            "FLAC → 64 bit kayan nokta",
            "Albüme göre · −6,2 dB",
            "−1,5 dB",
            "20 bant",
            "Sennheiser HD 600 · 5 bant",
            "−1 dBFS · 5 ms önden bakış",
            "USB DAC · Topping D50s · 24 bit · 96 kHz · stereo · doğrudan",
            "Bit-perfect değil",
            "Neden: ReplayGain",
        ).forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun aBitPerfectPath() {
        show(samplePath().copy(replayGain = samplePath().replayGain.copy(mode = ReplayGainMode.Off, gainDb = 0.0), dsp = emptyList(), dspActive = false, bitPerfect = true, reason = SignalPath.Reason.None))
        compose.onNodeWithText("Bit-perfect").assertExists()
        compose.onNodeWithText("Bit-perfect değil").assertDoesNotExist()
        compose.onNodeWithText("Albüme göre · −6,2 dB").assertDoesNotExist()
        // nothing to compare without a chain
        compose.onNodeWithText("Zincir şu an sesi değiştirmiyor: karşılaştırılacak bir şey yok.").assertExists()
        compose.onNode(hasText("Kör testi başlat") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun withoutTheEngine() {
        show(null)
        compose.onNodeWithText("Müzik motoru çalarken dolar.").assertExists()
        compose.onNodeWithText("Test, müzik motoru çalarken çalışır.").assertExists()
    }

    @Test
    fun sideBInTheOpen() {
        val ab = AbTestState(engine = { sent += it }, random = Random(3))
        ab.listen(AbChoice.B)
        show(samplePath(abSide = AbSide.B), ab)
        compose.onNodeWithText("A/B testinin B tarafı, −3,2 dB").assertExists()
        compose.onNodeWithText("B zincirin düzeyinde: −3,2 dB").assertExists()
        compose.onNodeWithText("20 bant").assertDoesNotExist()
    }

    @Test
    fun theBlindTestKeepsTheSideQuiet() {
        val ab = AbTestState(engine = { sent += it }, random = Random(3))
        ab.startBlind(10)
        show(samplePath(abSide = ab.side), ab)
        // the chain's own stages, whatever side plays
        compose.onNodeWithText("A/B testinin B tarafı, −3,2 dB").assertDoesNotExist()
        compose.onNodeWithText("20 bant").assertExists()
        compose.onNodeWithText("Kör test sürerken çalan taraf gösterilmez.").assertExists()
        compose.onNodeWithText("Tur 1 / 10").assertExists()
        compose.onNodeWithText("Alt+B sırayla A'ya, B'ye ve X'e geçer.").assertExists()
    }

    @Test
    fun theOpenTest() {
        show(samplePath())
        compose.onNodeWithText("B zincirin düzeyinde: −3,2 dB").assertExists()
        compose.onNodeWithText("B · zincirsiz").performClick()
        compose.waitForIdle()
        assertEquals(listOf(AbSide.B), sent)
        compose.onNodeWithText("B çalıyor: A'yı seçene dek zincir atlanır.").assertExists()
        compose.onNodeWithText("Alt+B, A ile B arasında geçiş yapar.").assertExists()
        compose.onNodeWithText("A · zincirle").performClick()
        compose.waitForIdle()
        assertEquals(listOf(AbSide.B, AbSide.A), sent)
        compose.onNodeWithText("B çalıyor: A'yı seçene dek zincir atlanır.").assertDoesNotExist()
    }

    @Test
    fun theBlindTestShowsTheAnswersAtTheEnd() {
        val ab = show(samplePath())
        compose.onNodeWithText("10").performClick()
        compose.onNode(hasText("Kör testi başlat") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
        val xs = ab.rounds.map { it.x }
        assertEquals(10, xs.size)
        // X plays first
        assertEquals(xs[0], sent.last())
        repeat(10) { round ->
            compose.onNodeWithText("Tur ${round + 1} / 10").assertExists()
            // nothing tells what X was before the end
            compose.onNodeWithText("Cevabınız").assertDoesNotExist()
            compose.onNode(hasText("A'dır") and hasClickAction()).performScrollTo().performClick()
            compose.waitForIdle()
        }
        val right = xs.count { it == AbSide.A }
        compose.onNodeWithText("10 turda $right doğru").assertExists()
        compose.onNodeWithText("Cevabınız").assertExists()
        // the chain plays again
        assertEquals(AbSide.A, sent.last())
        compose.onNode(hasText("Kapat") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("A · zincirle").assertExists()
    }

    private fun samplePath(abSide: AbSide = AbSide.A) = SignalPath(
        state = EngineState.Playing,
        source = SignalPath.Source(1, "file:///music/a.flac", "flac", "flac", 96_000, 2, 24, false, 300_000, 0, 0, false),
        output = SignalPath.Output("usb", 96_000, 2, "s24", 24, exclusive = true),
        volume = 1.0,
        crossfadeMs = 0,
        replayGain = SignalPath.ReplayGainStage(ReplayGainMode.Album, 0.0, -6.2, tagged = true, clippingPrevented = false),
        tempo = 1.0,
        pitch = 0.0,
        balance = 0.0,
        mono = false,
        dsp = listOf(
            SignalPath.DspStage("preamp", null, -1.5, null, null, null),
            SignalPath.DspStage("graphic_eq", 20, null, null, null, null),
            SignalPath.DspStage("profile", 5, null, "Sennheiser HD 600", null, null),
            SignalPath.DspStage("limiter", null, -1.0, null, 5.0, 100.0),
        ),
        dspActive = true,
        dspLevelDb = -3.2,
        abSide = abSide,
        abGainDb = if (abSide == AbSide.B) -3.2 else 0.0,
        bitPerfect = false,
        reason = SignalPath.Reason.ReplayGain,
    )
}
