package app.nagmedar.ui.radio

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.playback.RadioMetadata
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/** S-1106: the radio screen's tabs (favourites, recent, search, imported), the now-playing strip, and what the lists say while empty. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
class RadioTabsPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val directory = FakeDirectory()
    private val library = FakeLibrary()
    private val sink = RecordingSink()
    private val onAir = MutableStateFlow<RadioNow?>(null)
    private var now = 1_760_000_000_000L
    private var imports = 0
    private var toggles = 0

    private fun show(): RadioController {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val controller = RadioController(directory, library, RadioPlayer(directory, library, sink, { now }, clickScope = inlineClicks()), scope, { now }, debounceMs = 0, playingNow = onAir, work = Dispatchers.Unconfined)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalWidthClass provides WidthClass.Large) {
                    RadioPage(controller, onImport = { imports++ }, onTogglePlay = { toggles++ }, now = { now })
                }
            }
        }
        compose.waitForIdle()
        return controller
    }

    private fun tab(name: String) {
        compose.onNodeWithTag("radio-tab-$name").performClick()
        compose.waitForIdle()
    }

    @Test
    fun theTabsAreThereAndSearchIsFirst() {
        show()
        listOf("favourites", "recent", "search", "imported").forEach { compose.onNodeWithTag("radio-tab-$it").assertExists() }
        compose.onNodeWithTag("radio-search-field").assertExists()
    }

    @Test
    fun emptyListsSayWhatTheyAreFor() {
        show()
        tab("favourites")
        compose.onNodeWithTag("radio-empty-favourites").assertExists()
        compose.onNodeWithText("Henüz favori radyo yok").assertExists()
        tab("recent")
        compose.onNodeWithText("Henüz bir şey çalınmadı").assertExists()
        tab("imported")
        compose.onNodeWithText("İçe aktarılan radyo yok").assertExists()
    }

    @Test
    fun theEmptyListsOfferTheSearchAndTheImport() {
        show()
        tab("favourites")
        compose.onNodeWithText("Radyo ara").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("radio-search-field").assertExists()
        tab("imported")
        compose.onNodeWithText("Dosya içe aktar…").performClick()
        assertEquals(1, imports)
    }

    @Test
    fun theStarPutsAStationInFavourites() {
        directory.onSearch = { listOf(RadioSamples.jazz) }
        show()
        compose.onNodeWithTag("favourite-a1-caz").performClick()
        compose.waitForIdle()
        tab("favourites")
        compose.onNodeWithTag("station-a1-caz").assertExists()
        // and takes it out again
        compose.onNodeWithTag("favourite-a1-caz").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("radio-empty-favourites").assertExists()
    }

    @Test
    fun theRecentTabListsWhatPlayedAndTheMenuForgetsIt() {
        directory.onSearch = { listOf(RadioSamples.jazz) }
        show()
        compose.onNodeWithTag("station-a1-caz").performClick()
        compose.waitForIdle()
        tab("recent")
        compose.onNodeWithTag("station-a1-caz").assertExists()
        compose.onAllNodesWithContentDescription("Diğer işlemler")[0].performClick()
        compose.onNodeWithText("Son çalınanlardan kaldır").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("radio-empty-recent").assertExists()
        assertEquals("“Gece Cazı” kaldırıldı", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun importedStationsAreListedWithTheImportButtonAndRemovedFromTheMenu() {
        val controller = show()
        controller.import(javaClass.getResourceAsStream("/radio/stations.m3u8")!!.use { it.readBytes() }, "stations.m3u8")
        compose.waitForIdle()
        compose.onNodeWithTag("radio-import").assertExists()
        compose.onNodeWithText("Nağmedar Test FM (MP3)").assertExists()
        assertEquals("2 radyo içe aktarıldı", ShadowToast.getTextOfLatestToast())
        compose.onNodeWithTag("radio-import").performClick()
        assertEquals(1, imports)
        // an imported station has no directory id: there is no vote in its menu
        compose.onAllNodesWithContentDescription("Diğer işlemler")[0].performClick()
        compose.onNodeWithText("Radyoyu kaldır").assertExists()
        compose.onNodeWithText("Bu radyoya oy ver").assertDoesNotExist()
        compose.onNodeWithText("Radyoyu kaldır").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Nağmedar Test FM (AAC)").assertDoesNotExist()
        compose.onNodeWithText("Nağmedar Test FM (MP3)").assertExists()
    }

    @Test
    fun anImportOfKnownStationsSaysTheyWereAlreadyThere() {
        val controller = show()
        val file = javaClass.getResourceAsStream("/radio/stations.m3u8")!!.use { it.readBytes() }
        controller.import(file, "stations.m3u8")
        compose.waitForIdle()
        controller.import(file, "stations.m3u8")
        compose.waitForIdle()
        assertEquals("2 radyo zaten kayıtlıydı", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun thereIsNoStripUntilSomethingPlays() {
        show()
        compose.onNodeWithTag("radio-now").assertDoesNotExist()
    }

    @Test
    fun theStripShowsTheStationAsLiveWhenTheStreamSendsNoTitle() {
        show()
        onAir.value = RadioNow(RadioSamples.jazz, RadioMetadata.display("Gece Cazı", null), playing = true)
        compose.waitForIdle()
        compose.onNodeWithTag("radio-now-title").assertTextEquals("Gece Cazı")
        compose.onNodeWithTag("radio-now-line").assertTextEquals("Canlı yayın")
    }

    @Test
    fun theStripFollowsTheTitleTheStreamSends() {
        show()
        onAir.value = RadioNow(RadioSamples.jazz, RadioMetadata.display("Gece Cazı", "Miles Davis - So What"), playing = true)
        compose.waitForIdle()
        compose.onNodeWithTag("radio-now-title").assertTextEquals("So What")
        compose.onNodeWithTag("radio-now-line").assertTextEquals("Miles Davis · Gece Cazı")
        onAir.value = RadioNow(RadioSamples.jazz, RadioMetadata.display("Gece Cazı", "Bill Evans - Waltz for Debby"), playing = true)
        compose.waitForIdle()
        compose.onNodeWithTag("radio-now-title").assertTextEquals("Waltz for Debby")
    }

    @Test
    fun theStripPausesAndEndsWithTheStream() {
        show()
        onAir.value = RadioNow(RadioSamples.jazz, RadioMetadata.display("Gece Cazı", null), playing = true)
        compose.waitForIdle()
        compose.onNodeWithTag("radio-now-toggle").performClick()
        assertEquals(1, toggles)
        onAir.value = null
        compose.waitForIdle()
        compose.onNodeWithTag("radio-now").assertDoesNotExist()
    }
}
