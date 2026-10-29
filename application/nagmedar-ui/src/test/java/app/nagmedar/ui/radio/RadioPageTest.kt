package app.nagmedar.ui.radio

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/** S-1105: the radio screen's search page: stations listed and played with a click or Enter, the filters, the menu, and its states. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
class RadioPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val directory = FakeDirectory()
    private val library = FakeLibrary()
    private val sink = RecordingSink()
    private var now = 1_760_000_000_000L
    private val copied = mutableListOf<Station>()
    private var searchHandled = 0
    private lateinit var inputModes: InputModeManager

    private fun show(searchRequested: Boolean = false, width: WidthClass = WidthClass.Large): RadioController {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val controller = RadioController(directory, library, RadioPlayer(directory, library, sink, { now }, clickScope = inlineClicks()), scope, { now }, debounceMs = 0)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalWidthClass provides width) {
                    inputModes = LocalInputModeManager.current
                    RadioPage(
                        controller,
                        searchRequested = searchRequested,
                        onSearchHandled = { searchHandled++ },
                        onCopyAddress = { copied += it },
                        now = { now },
                    )
                }
            }
        }
        compose.waitForIdle()
        return controller
    }

    @Test
    fun theBestStationsAreListedWithWhatIsKnownOfThem() {
        directory.onSearch = { RadioSamples.all }
        show()
        compose.onNodeWithTag("station-a1-caz").assertExists()
        compose.onNodeWithText("Gece Cazı").assertExists()
        compose.onNodeWithText("Haber Hattı").assertExists()
        // place, codec and bitrate, votes, tags
        compose.onNodeWithText("Almanya · Almanca").assertExists()
        compose.onNodeWithText("MP3 · 128 kbps").assertExists()
        compose.onNodeWithText("18.402 oy").assertExists()
        compose.onNodeWithText("jazz · gece · enstrümantal").assertExists()
    }

    @Test
    fun aClickPlaysTheStation() {
        directory.onSearch = { RadioSamples.all }
        directory.onClick = { StationClick("https://cdn.example/live", null) }
        show()
        compose.onNodeWithTag("station-b2-haber").performClick()
        compose.waitForIdle()
        assertEquals("b2-haber", sink.played.single().station.uuid)
        assertEquals(listOf("b2-haber"), directory.clicks.toList())
    }

    @Test
    fun enterPlaysTheStationThatHasTheKeyboard() {
        directory.onSearch = { RadioSamples.all }
        show()
        // a focus ring needs the keyboard: the touch mode does not give it to a row
        compose.runOnIdle { inputModes.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
        compose.onNodeWithTag("station-c3-klasik").apply {
            requestFocus()
            performKeyInput { pressKey(Key.Enter) }
        }
        compose.waitForIdle()
        assertEquals("c3-klasik", sink.played.single().station.uuid)
    }

    @Test
    fun theMenuVotesAndCopiesTheAddress() {
        directory.onSearch = { listOf(RadioSamples.jazz) }
        show()
        compose.onAllNodesWithContentDescription("Gece Cazı için diğer işlemler")[0].performClick()
        compose.onNodeWithText("Akış adresini kopyala").performClick()
        compose.waitForIdle()
        assertEquals(listOf(RadioSamples.jazz.streamUrl), copied.map { it.streamUrl })
        compose.onAllNodesWithContentDescription("Gece Cazı için diğer işlemler")[0].performClick()
        compose.onNodeWithText("Bu radyoya oy ver").performClick()
        compose.waitForIdle()
        assertEquals(listOf("a1-caz"), directory.votes.toList())
        assertEquals("Gece Cazı için oy verdiniz", ShadowToast.getTextOfLatestToast())
        // the vote is counted in the row, and the menu says it was cast
        compose.onNodeWithText("18.403 oy").assertExists()
        compose.onAllNodesWithContentDescription("Gece Cazı için diğer işlemler")[0].performClick()
        compose.onNodeWithText("Son bir günde oy verildi").assertExists()
    }

    @Test
    fun theSearchShortcutPutsTheKeyboardInTheBox() {
        show(searchRequested = true)
        compose.onNodeWithTag("radio-search-field").assertIsFocused()
        assertEquals(1, searchHandled)
    }

    @Test
    fun whatIsTypedIsSearched() {
        val controller = show()
        compose.onNodeWithTag("radio-search-field").performTextInput("gece")
        compose.waitForIdle()
        assertEquals("gece", directory.searches.last().text)
        assertEquals("gece", controller.search.text)
    }

    @Test
    fun aFilterIsPickedFromTheDirectorysList() {
        directory.countriesList = listOf(DirectoryCountry("Germany", "DE", 600), DirectoryCountry("Turkey", "TR", 800))
        val controller = show()
        compose.onNodeWithTag("radio-filter-country").performClick()
        compose.onNodeWithText("Almanya").performClick()
        compose.waitForIdle()
        assertEquals("DE", directory.searches.last().filters.countryCode)
        assertEquals(1, controller.search.filters.count)
        compose.onNodeWithTag("radio-filters-clear").performClick()
        compose.waitForIdle()
        assertEquals(0, directory.searches.last().filters.count)
    }

    @Test
    fun aCodecAndABitrateAreFilters() {
        show()
        compose.onNodeWithTag("radio-filter-codec").performClick()
        compose.onNodeWithText("FLAC").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("radio-filter-bitrate").performClick()
        compose.onNodeWithText("En az 192 kbps").performClick()
        compose.waitForIdle()
        val filters = directory.searches.last().filters
        assertEquals("FLAC", filters.codec)
        assertEquals(192, filters.minBitrate)
    }

    @Test
    fun theOrderIsChosen() {
        show()
        compose.onNodeWithTag("radio-sort").performClick()
        compose.onNodeWithText("En yüksek bit hızı").performClick()
        compose.waitForIdle()
        assertEquals(StationOrder.Bitrate, directory.searches.last().order)
    }

    @Test
    fun withoutAConnectionItSaysSoAndTriesAgain() {
        directory.onSearch = { throw RadioFailure.Offline() }
        show()
        compose.onNodeWithTag("radio-offline").assertExists()
        compose.onNodeWithText("Bağlantı yok").assertExists()
        directory.onSearch = { RadioSamples.all }
        compose.onNodeWithText("Yeniden dene").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("station-a1-caz").assertExists()
        compose.onNodeWithTag("radio-offline").assertDoesNotExist()
    }

    @Test
    fun aBusyDirectoryAndAnErrorEachSayWhat() {
        directory.onSearch = { throw RadioFailure.Busy(60_000) }
        val controller = show()
        compose.onNodeWithTag("radio-busy").assertExists()
        directory.onSearch = { throw RadioFailure.Server(503) }
        controller.search.retry()
        compose.waitForIdle()
        compose.onNodeWithTag("radio-error").assertExists()
        compose.onNodeWithText("Sunucu hata verdi (503).").assertExists()
    }

    @Test
    fun nothingFoundSaysItAndOffersToClearTheFilters() {
        directory.onSearch = { emptyList() }
        val controller = show()
        compose.onNodeWithTag("radio-none").assertExists()
        compose.onNodeWithText("Radyo bulunamadı").assertExists()
        controller.search.filter(StationFilters(tag = "yok"))
        compose.waitForIdle()
        compose.onNodeWithTag("radio-none").assertExists()
        // the filters can be cleared from the bar
        compose.onNodeWithTag("radio-filters-clear").performClick()
        compose.waitForIdle()
        assertEquals(0, directory.searches.last().filters.count)
    }

    @Test
    fun whileItSearchesItSaysSo() {
        val never = CompletableDeferred<List<Station>>()
        directory.onSearch = { never.await() }
        show()
        compose.onNodeWithTag("radio-loading").assertExists()
        compose.onNodeWithText("Radyo rehberinde aranıyor…").assertExists()
        never.complete(emptyList())
        compose.waitForIdle()
        assertTrue(directory.searches.isNotEmpty())
    }

    @Test
    fun aFullPageOffersMore() {
        directory.onSearch = { query ->
            (query.offset until query.offset + query.limit).map { Station("u$it", "Radyo $it", "https://yayin.example/$it") }
        }
        show()
        compose.onNodeWithTag("radio-list").performScrollToNode(hasTestTag("radio-more"))
        compose.onNodeWithTag("radio-more").performClick()
        compose.waitForIdle()
        assertEquals(listOf(0, 50), directory.searches.map { it.offset })
        compose.onNodeWithTag("radio-list").performScrollToNode(hasTestTag("station-u99"))
        compose.onNodeWithTag("station-u99").assertExists()
    }
}
