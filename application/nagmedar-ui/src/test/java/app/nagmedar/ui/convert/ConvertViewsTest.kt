package app.nagmedar.ui.convert

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.music.TrackTech
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-1010: the converter's window (the choices, the example, Dönüştür), its line above the library and its summary. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
class ConvertViewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val flac = Track(
        id = 1, uri = "file:///m/03.flac", title = "Çığlık", artist = "Şebnem Ağaoğlu", albumArtist = null, album = "Gece Yolcusu",
        genre = null, year = 1997, trackNumber = 3, discNumber = 1, durationMs = 241_000, folder = "/m",
        tech = TrackTech("flac", "flac", 44_100, 16, 2, isFloat = false),
    )
    private val mp3 = flac.copy(id = 2, uri = "file:///m/04.mp3", title = "Yol", trackNumber = 4, tech = TrackTech("mp3", "mp3", 44_100, 0, 2, isFloat = true))

    private fun window(
        tracks: List<Track>,
        start: ConvertOptions = ConvertOptions(),
        engine: Boolean = true,
        onStart: (ConvertOptions) -> Unit = {},
        onPick: () -> Unit = {},
    ) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                var options by remember { mutableStateOf(start) }
                ConvertSurface(tracks, options, { options = it }, engine, onPick, { onStart(options) }, onDismiss = {})
            }
        }
    }

    @Test
    fun aFolderIsNeededFirst() {
        var picked = 0
        window(listOf(flac), onPick = { picked++ })
        compose.onNodeWithTag("convert-start").assertIsNotEnabled()
        compose.onNodeWithText("Henüz seçilmedi").assertExists()
        compose.onNodeWithTag("convert-pick-folder").performScrollTo().performClick()
        assertEquals(1, picked)
    }

    @Test
    fun theChoicesGoToTheJob() {
        var started: ConvertOptions? = null
        window(listOf(flac, mp3), start = ConvertOptions(folder = "content://tree"), onStart = { started = it })
        compose.onNodeWithText("2 parça").assertExists()
        // Opus: always 48 kHz, its rate not offered
        compose.onNodeWithText("Opus her zaman 48 kHz'dir.").assertExists()
        compose.onNodeWithText("Örnek: Şebnem Ağaoğlu/Gece Yolcusu/03 Çığlık.opus").assertExists()
        // MP3: its qualities, the rate offered
        compose.onNodeWithText("MP3").performClick()
        compose.onNodeWithText("V0 (değişken)").assertExists()
        compose.onNodeWithText("320 kbps").performClick()
        compose.onNodeWithText("Örnek: Şebnem Ağaoğlu/Gece Yolcusu/03 Çığlık.mp3").assertExists()
        compose.onNode(hasText("Kaynaktaki gibi") and hasAnyAncestor(hasTestTag("convert-rate"))).assertIsEnabled()
        // MP3 is always stereo: the channels are not offered
        compose.onNode(hasText("Stereoya indir") and hasAnyAncestor(hasTestTag("convert-channels"))).assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("MP3 her zaman stereodur: ikiden çok kanal stereoya indirilir.").assertExists()
        compose.onNodeWithText("Tek klasör").performScrollTo().performClick()
        compose.onNodeWithText("Örnek: Şebnem Ağaoğlu - Çığlık.mp3").assertExists()
        compose.onNodeWithTag("convert-skip").performScrollTo().performClick()
        // a lossy source says what it costs
        compose.onNodeWithText("1 parça zaten kayıplı biçimde", substring = true).assertExists()
        compose.onNodeWithTag("convert-start").assertIsEnabled().performClick()
        assertEquals(ConvertOptions(ConvertPreset.Mp3Cbr320, ConvertRate.Source, ConvertNaming.Flat, skipExisting = false, folder = "content://tree"), started)
    }

    @Test
    fun aacKeepsTheRateAsked() {
        var started: ConvertOptions? = null
        window(listOf(flac), start = ConvertOptions(folder = "content://tree"), onStart = { started = it })
        compose.onNodeWithText("AAC").performClick()
        compose.onNodeWithText("256 kbps").assertExists()
        compose.onNodeWithText("48 kHz").performScrollTo().performClick()
        compose.onNodeWithTag("convert-start").performClick()
        assertEquals(ConvertPreset.Aac256, started?.preset)
        assertEquals(ConvertRate.Hz48000, started?.rate)
    }

    @Test
    fun withoutTheEngineNothingStarts() {
        window(listOf(flac), start = ConvertOptions(folder = "content://tree"), engine = false)
        compose.onNodeWithTag("convert-no-engine").assertExists()
        compose.onNodeWithTag("convert-start").assertIsNotEnabled()
    }

    /** S-1407 on a phone: every control of the window and of the line 48 dp at least, and named. */
    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun touchTargetsAndNames() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                androidx.compose.foundation.layout.Column {
                    ConvertStatusBar(ConvertStatus.Finished("a1", 3, 0, 1, cancelled = false), {}, {}, {})
                    ConvertSurface(listOf(flac), ConvertOptions(preset = ConvertPreset.Mp3V0, folder = "content://tree"), {}, true, {}, {}, {})
                }
            }
        }
        val density = compose.density
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(nodes.size >= 12)
        val problems = nodes.filter { node ->
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            with(density) { node.size.width.toDp() < 47.5.dp || node.size.height.toDp() < 47.5.dp } || label.isNullOrBlank()
        }
        assertTrue(problems.joinToString { it.config.toString() }, problems.isEmpty())
    }

    @Test
    fun theLineAboveTheLibrary() {
        val cancelled = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val dismissed = mutableListOf<String>()
        var status by mutableStateOf<ConvertStatus?>(ConvertStatus.Running("a1", ConvertProgress(2, 12, "Çığlık", 0.5, 2, 0, 0), queued = 1))
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                ConvertStatusBar(status, { cancelled += it }, { opened += it }, { dismissed += it })
            }
        }
        compose.onNodeWithText("Müzik dönüştürülüyor").assertExists()
        compose.onNodeWithText("3 / 12 · Çığlık · 1 iş daha bekliyor").assertExists()
        compose.onNodeWithTag("convert-cancel").performClick()
        assertEquals(listOf("a1"), cancelled)
        status = ConvertStatus.Running("a1", ConvertProgress(2, 12, "Çığlık", 0.5, 2, 0, 0, waiting = ConvertReason.Thermal), queued = 0)
        compose.onNodeWithText("Bekliyor: cihaz ısındı; soğuyunca sürecek.").assertExists()
        status = ConvertStatus.Finished("a1", converted = 11, skipped = 1, failed = 2, cancelled = false)
        compose.onNodeWithText("Dönüştürme bitti").assertExists()
        compose.onNodeWithText("11 dönüştürüldü · 1 atlandı · 2 başarısız").assertExists()
        compose.onNodeWithTag("convert-details").performClick()
        compose.onNodeWithTag("convert-dismiss").performClick()
        assertEquals(listOf("a1"), opened)
        assertEquals(listOf("a1"), dismissed)
        status = null
        compose.onNodeWithTag("convert-status").assertDoesNotExist()
    }

    @Test
    fun theSummary() {
        val one = ConvertItem("file:///m/03.flac", "Çığlık", listOf("Şebnem Ağaoğlu", "Gece Yolcusu"), "03 Çığlık.opus")
        val two = one.copy(uri = "file:///m/04.flac", title = "Yol", name = "04 Yol.opus")
        val three = one.copy(uri = "file:///m/05.flac", title = "Kuyu", name = "05 Kuyu.opus")
        val summary = ConvertSummary(
            "a1", 3,
            listOf(ItemOutcome.Skipped(one), ItemOutcome.Failed(two, ConvertReason.Engine, "decoding: Invalid data"), ItemOutcome.Converted(three, 1)),
            finished = true,
        )
        var closed = 0
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Pointer) { ConvertSummarySurface(summary) { closed++ } }
        }
        compose.onNodeWithTag("convert-summary-counts").assertExists()
        compose.onNodeWithText("1 dönüştürüldü · 1 atlandı · 1 başarısız").assertExists()
        compose.onNodeWithText("Dosya dönüştürülemedi").assertExists()
        compose.onNodeWithText("decoding: Invalid data").assertExists()
        compose.onNodeWithText("Klasörde zaten var").assertExists()
        compose.onNodeWithText("Şebnem Ağaoğlu/Gece Yolcusu/04 Yol.opus").assertExists()
        compose.onNodeWithTag("convert-summary-close").performClick()
        assertEquals(1, closed)
    }
}
