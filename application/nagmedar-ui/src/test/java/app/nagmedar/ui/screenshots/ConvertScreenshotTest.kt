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
import app.nagmedar.ui.convert.ConvertItem
import app.nagmedar.ui.convert.ConvertNaming
import app.nagmedar.ui.convert.ConvertOptions
import app.nagmedar.ui.convert.ConvertPreset
import app.nagmedar.ui.convert.ConvertProgress
import app.nagmedar.ui.convert.ConvertRate
import app.nagmedar.ui.convert.ConvertReason
import app.nagmedar.ui.convert.ConvertStatus
import app.nagmedar.ui.convert.ConvertStatusBar
import app.nagmedar.ui.convert.ConvertSummary
import app.nagmedar.ui.convert.ConvertSummarySurface
import app.nagmedar.ui.convert.ConvertSurface
import app.nagmedar.ui.convert.ItemOutcome
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.music.TrackTech
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
 * S-1010, for approval: the converter's window in DeX (Opus, a folder chosen), on a tablet (AAC
 * at 48 kHz, one folder) and on an English phone (MP3 V0, no folder yet, lossy tracks among
 * them); its line above the library while a job runs and once it is done; the summary.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConvertScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val folder = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FTa%C5%9F%C4%B1nabilir"

    private val album = List(12) { index ->
        Track(
            id = index.toLong(), uri = "file:///m/${index + 1}.flac", title = TITLES[index], artist = "Şebnem Ağaoğlu", albumArtist = null,
            album = "Gece Yolcusu", genre = "Türk Halk Müziği", year = 1997, trackNumber = index + 1, discNumber = 1, durationMs = 241_000,
            folder = "/m", tech = TrackTech("flac", "flac", 96_000, 24, 2, isFloat = false),
        )
    }

    private val mixed = album.take(5) + album.drop(5).take(3).map { it.copy(tech = TrackTech("mp3", "mp3", 44_100, 0, 2, isFloat = true)) }

    private fun item(index: Int) = ConvertItem("file:///m/${index + 1}.flac", TITLES[index], listOf("Şebnem Ağaoğlu", "Gece Yolcusu"), "%02d %s.opus".format(index + 1, TITLES[index]))

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
    private fun Window(tracks: List<Track>, options: ConvertOptions, engine: Boolean = true) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ConvertSurface(tracks, options, onOptions = {}, engineAvailable = engine, onPickFolder = {}, onStart = {}, onDismiss = {})
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dialogDex() = capture("convert-dialog-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
        Window(album, ConvertOptions(ConvertPreset.Opus160, folder = folder))
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun dialogTablet() = capture("convert-dialog-tablet-light", ThemeMode.Light, InputMode.Touch) {
        Window(album.take(1), ConvertOptions(ConvertPreset.Aac256, ConvertRate.Hz48000, ConvertNaming.Flat, skipExisting = false, folder = folder))
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun dialogPhoneEnglish() = capture("convert-dialog-phone-light-en", ThemeMode.Light, InputMode.Touch) {
        Window(mixed, ConvertOptions(ConvertPreset.Mp3V0))
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun progressTablet() = capture("convert-progress-tablet-dark", ThemeMode.Dark, InputMode.Touch) {
        Box(Modifier.padding(24.dp)) {
            ConvertStatusBar(
                ConvertStatus.Running("a1", ConvertProgress(4, 12, TITLES[4], 0.4, converted = 3, skipped = 1, failed = 0), queued = 1),
                onCancel = {}, onDetails = {}, onDismiss = {},
            )
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun waitingPhone() = capture("convert-progress-phone-light", ThemeMode.Light, InputMode.Touch) {
        Box(Modifier.padding(16.dp)) {
            ConvertStatusBar(
                ConvertStatus.Running("a1", ConvertProgress(7, 12, TITLES[7], 0.1, 6, 1, 0, waiting = ConvertReason.Battery), queued = 0),
                onCancel = {}, onDetails = {}, onDismiss = {},
            )
        }
    }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun doneDexEnglish() = capture("convert-done-dex-light-en", ThemeMode.Light, InputMode.Pointer) {
        Box(Modifier.padding(24.dp)) {
            ConvertStatusBar(ConvertStatus.Finished("a1", converted = 9, skipped = 1, failed = 2, cancelled = false), onCancel = {}, onDetails = {}, onDismiss = {})
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun summaryPhone() = capture("convert-summary-phone-dark", ThemeMode.Dark, InputMode.Touch) {
        val outcomes = List(12) { index ->
            when (index) {
                3 -> ItemOutcome.Skipped(item(index))
                6 -> ItemOutcome.Failed(item(index), ConvertReason.Engine, "decoding: Invalid data found when processing input")
                9 -> ItemOutcome.Failed(item(index), ConvertReason.Input)
                else -> ItemOutcome.Converted(item(index), 4_200_000)
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ConvertSummarySurface(ConvertSummary("a1", 12, outcomes, finished = true), onDismiss = {})
        }
    }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun stoppedDexEnglish() = capture("convert-summary-dex-dark-en", ThemeMode.Dark, InputMode.Pointer) {
        val outcomes = List(6) { index ->
            if (index < 4) ItemOutcome.Converted(item(index), 4_200_000) else ItemOutcome.Failed(item(index), ConvertReason.Cancelled)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ConvertSummarySurface(ConvertSummary("a1", 6, outcomes, finished = true), onDismiss = {})
        }
    }

    private companion object {
        val TITLES = listOf(
            "Uzun İnce Bir Yoldayım", "Gece Yolcusu", "Çığlık", "Işıklı Sabah", "Dönüş", "Ağıt",
            "Şafak", "Göç", "Üç Kuruş", "Yağmur", "Öğle Sıcağı", "Son Söz",
        )
    }
}
