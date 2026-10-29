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
import app.nagmedar.ui.radio.RadioFailure
import app.nagmedar.ui.radio.RadioPage
import app.nagmedar.ui.radio.RadioPlayer
import app.nagmedar.ui.radio.RadioSamples
import app.nagmedar.ui.radio.RecordingSink
import app.nagmedar.ui.radio.Station
import app.nagmedar.ui.radio.StationFilters
import app.nagmedar.ui.radio.StationOrder
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-1105, for approval: the radio directory's search page: the stations with their monograms
 * (the logos are fetched only in the app) in DeX, on a phone in English and on a tablet with
 * filters on; and the offline, empty and loading states.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadioScreenshotTest {

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

    private val more = listOf(
        Station("e5-sabah", "Sabah Seansı", "https://yayin.example/sabah", countryCode = "TR", country = "Turkey", language = "turkish", tags = listOf("pop", "sabah", "yerli"), codec = "AAC+", bitrate = 96, votes = 640),
        Station("f6-nostalji", "Nostalji FM", "https://yayin.example/nostalji", countryCode = "TR", country = "Turkey", language = "turkish", tags = listOf("oldies", "türkü"), codec = "MP3", bitrate = 192, votes = 412),
        Station("g7-rock", "Kıyı Rock", "https://yayin.example/rock", countryCode = "TR", country = "Turkey", language = "turkish", tags = listOf("rock", "alternative"), codec = "MP3", bitrate = 128, votes = 255),
    )

    @Composable
    private fun Radio(
        directory: FakeDirectory = FakeDirectory().apply { onSearch = { RadioSamples.all + more } },
        filters: StationFilters? = null,
        order: StationOrder? = null,
    ) {
        val scope = rememberCoroutineScope()
        val controller = remember {
            val library = FakeLibrary()
            RadioController(directory, library, RadioPlayer(directory, library, RecordingSink(), clickScope = inlineClicks()), scope, debounceMs = 0).also {
                if (filters != null) it.search.filter(filters)
                if (order != null) it.search.sort(order)
            }
        }
        RadioPage(controller, now = { 1_760_000_000_000L })
    }

    @Test @Config(qualifiers = "tr-w1440dp-h900dp-mdpi-keysexposed-qwerty")
    fun dex() {
        capture("radio-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
            TestShell(WidthClass.Large, startDestination = Destination.Radio) { _, _ -> Radio() }
        }
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun phoneEnglish() {
        capture("radio-phone-light-en", ThemeMode.Light, InputMode.Touch) {
            TestShell(WidthClass.Compact, startDestination = Destination.Radio) { _, _ -> Radio() }
        }
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletFiltered() {
        val directory = FakeDirectory().apply { onSearch = { listOf(RadioSamples.classical) } }
        capture("radio-tablet-light-filtered", ThemeMode.Light, InputMode.Touch) {
            TestShell(WidthClass.Expanded, startDestination = Destination.Radio) { _, _ ->
                Radio(directory, StationFilters(countryCode = "DE", codec = "OGG", minBitrate = 192), StationOrder.Bitrate)
            }
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun phoneOffline() {
        val directory = FakeDirectory().apply { onSearch = { throw RadioFailure.Offline() } }
        capture("radio-phone-dark-offline", ThemeMode.Dark, InputMode.Touch) {
            TestShell(WidthClass.Compact, startDestination = Destination.Radio) { _, _ -> Radio(directory) }
        }
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletNothingFound() {
        val directory = FakeDirectory().apply { onSearch = { emptyList() } }
        capture("radio-tablet-dark-empty", ThemeMode.Dark, InputMode.Touch) {
            TestShell(WidthClass.Expanded, startDestination = Destination.Radio) { _, _ ->
                Radio(directory, StationFilters(tag = "gregoryen", minBitrate = 320))
            }
        }
    }

    @Test @Config(qualifiers = "en-w1440dp-h900dp-mdpi-keysexposed-qwerty")
    fun dexLoadingEnglish() {
        val never = CompletableDeferred<List<Station>>()
        val directory = FakeDirectory().apply { onSearch = { never.await() } }
        capture("radio-dex-light-loading-en", ThemeMode.Light, InputMode.Pointer) {
            TestShell(WidthClass.Large, startDestination = Destination.Radio) { _, _ -> Radio(directory) }
        }
    }
}
