package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.radio.FakeDirectory
import app.nagmedar.ui.radio.FakeLibrary
import app.nagmedar.ui.radio.RadioController
import app.nagmedar.ui.radio.inlineClicks
import app.nagmedar.ui.radio.RadioPage
import app.nagmedar.ui.radio.RadioPlayer
import app.nagmedar.ui.radio.RadioSamples
import app.nagmedar.ui.radio.RecordingSink
import app.nagmedar.ui.radio.Station
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import app.nagmedar.playback.RadioMetadata
import app.nagmedar.ui.radio.PlaylistStation
import app.nagmedar.ui.radio.RadioNow
import app.nagmedar.ui.radio.RadioTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-1106, for approval: the radio screen's tabs (favourites, recent, search, imported) and the
 * now-playing strip with the title the stream sends, in DeX, on a phone and on a tablet, in both
 * themes and languages; and the lists while empty.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadioListsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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

    private val at = 1_760_000_000_000L
    private val sabah = Station("e5-sabah", "Sabah Seansı", "https://yayin.example/sabah", countryCode = "TR", country = "Turkey", language = "turkish", tags = listOf("pop", "sabah", "yerli"), codec = "AAC+", bitrate = 96, votes = 640)
    private val imported = listOf(
        PlaylistStation("Nağmedar Test FM (AAC)", "https://example.org/stream/aac", "https://example.org/logo.png", "Test"),
        PlaylistStation("Gece Cazı - 24 saat", "https://example.org/stream/caz.mp3", "https://example.org/caz.png", "Caz, Blues"),
        PlaylistStation("Haber Hattı", "http://example.org:8000/haber", null, "Haber"),
        PlaylistStation("Yalnız Ad", "rtsp://example.org/yalniz", null, "Müzik"),
    )

    private fun air(station: Station, nowPlaying: String?) = RadioNow(station, RadioMetadata.display(station.name, nowPlaying), playing = true)

    @Composable
    private fun Radio(tab: RadioTab, nowOnAir: RadioNow? = null, fill: suspend (FakeLibrary) -> Unit = {}) {
        val scope = rememberCoroutineScope()
        val controller = remember {
            val directory = FakeDirectory().apply { onSearch = { RadioSamples.all + sabah } }
            val library = FakeLibrary().also { runBlocking { fill(it) } }
            RadioController(directory, library, RadioPlayer(directory, library, RecordingSink(), clickScope = inlineClicks()), scope, debounceMs = 0, playingNow = flowOf(nowOnAir))
                .also { it.select(tab) }
        }
        RadioPage(controller, now = { at })
    }

    private suspend fun favourites(library: FakeLibrary) {
        library.favourite(RadioSamples.jazz, true)
        library.favourite(RadioSamples.classical, true)
        library.favourite(sabah, true)
    }

    private suspend fun played(library: FakeLibrary) {
        library.played(RadioSamples.news, at - 3_600_000)
        library.played(RadioSamples.jazz, at - 60_000)
        library.played(sabah, at - 86_400_000)
    }

    @Test @Config(qualifiers = "tr-w1440dp-h900dp-mdpi-qwerty")
    fun dexSearchWithTheStrip() {
        capture("radio-dex-dark-strip", ThemeMode.Dark, InputMode.Pointer) {
            TestShell(WidthClass.Large, startDestination = Destination.Radio) { _, _ ->
                Radio(RadioTab.Search, air(RadioSamples.jazz, "Miles Davis - So What"))
            }
        }
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun phoneFavouritesEnglish() {
        capture("radio-phone-light-favourites-en", ThemeMode.Light, InputMode.Touch) {
            TestShell(WidthClass.Compact, startDestination = Destination.Radio) { _, _ ->
                Radio(RadioTab.Favourites, air(RadioSamples.jazz, "Bill Evans - Waltz for Debby")) { favourites(it) }
            }
        }
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletRecent() {
        capture("radio-tablet-dark-recent", ThemeMode.Dark, InputMode.Touch) {
            TestShell(WidthClass.Expanded, startDestination = Destination.Radio) { _, _ ->
                Radio(RadioTab.Recent, air(RadioSamples.jazz, null)) { played(it); it.favourite(RadioSamples.jazz, true) }
            }
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun phoneImported() {
        capture("radio-phone-dark-imported", ThemeMode.Dark, InputMode.Touch) {
            TestShell(WidthClass.Compact, startDestination = Destination.Radio) { _, _ ->
                Radio(RadioTab.Imported) { it.import(imported) }
            }
        }
    }

    @Test @Config(qualifiers = "en-w1440dp-h900dp-mdpi-qwerty")
    fun dexImportedEnglish() {
        capture("radio-dex-light-imported-en", ThemeMode.Light, InputMode.Pointer) {
            TestShell(WidthClass.Large, startDestination = Destination.Radio) { _, _ ->
                Radio(RadioTab.Imported) { it.import(imported) }
            }
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun phoneNoFavourites() {
        capture("radio-phone-dark-favourites-empty", ThemeMode.Dark, InputMode.Touch) {
            TestShell(WidthClass.Compact, startDestination = Destination.Radio) { _, _ -> Radio(RadioTab.Favourites) }
        }
    }

    @Test @Config(qualifiers = "en-w1000dp-h800dp-mdpi")
    fun tabletNothingImportedEnglish() {
        capture("radio-tablet-light-imported-empty-en", ThemeMode.Light, InputMode.Touch) {
            TestShell(WidthClass.Expanded, startDestination = Destination.Radio) { _, _ -> Radio(RadioTab.Imported) }
        }
    }
}
