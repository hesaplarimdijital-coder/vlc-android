package app.nagmedar.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-801: filter chips, folders and the "Devam et" shelf of the films; shows, seasons and episodes. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class VideoScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val played = mutableListOf<String>()

    private fun films(view: MoviesView = MoviesView.Grid) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                var filters by remember { mutableStateOf(emptySet<MovieFilter>()) }
                var folder by remember { mutableStateOf<String?>(null) }
                MoviesLibrary(
                    library = MovieSamples.library,
                    tech = MovieSamples.tech,
                    view = view,
                    filters = filters,
                    onFiltersChange = { filters = it },
                    order = MovieOrder.Added,
                    folder = folder,
                    onOpenFolder = { folder = it },
                    sort = null,
                    onSortChange = {},
                    selected = null,
                    onSelect = {},
                    detailsRequested = false,
                    onShowDetails = {},
                    onCloseDetails = {},
                    onPlay = { played += it.title },
                    menu = { _, _, _ -> emptyList() },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun shown(title: String) = compose.onAllNodesWithText(title).fetchSemanticsNodes().size

    @Test
    fun episodesAreNotFilms() {
        films()
        assertEquals(0, shown("S02E02 Fener"))
        compose.onNodeWithText("Demir Vadi").assertExists()
    }

    @Test
    fun theShelfShowsFilmsInProgress() {
        films()
        compose.onNodeWithTag("continue").assertExists()
        // in the shelf and in the grid
        assertEquals(2, shown("Kuzey Işığı"))
    }

    @Test
    fun chipsNarrowAndClear() {
        films()
        compose.onNodeWithText("Dolby Vision").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("continue").assertDoesNotExist()
        assertEquals(1, shown("Kuzey Işığı"))
        assertEquals(1, shown("Demir Vadi"))
        assertEquals(0, shown("Sessiz Liman"))
        assertEquals(0, shown("Mavi Kanal"))
        compose.onNodeWithText("Türkçe altyazılı").performClick()
        compose.waitForIdle()
        // Dolby Vision and Turkish subtitles: only Kuzey Işığı has both
        assertEquals(1, shown("Kuzey Işığı"))
        assertEquals(0, shown("Demir Vadi"))
        compose.onNodeWithText("Atmos").performClick()
        compose.onNodeWithText("İzlenmedi").performClick()
        compose.onNodeWithText("4K").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tümü").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("continue").assertExists()
        compose.onNodeWithText("Mavi Kanal").assertExists()
    }

    @Test
    fun noMatchOffersToClear() {
        films()
        compose.onNodeWithText("Türkçe altyazılı").performClick()
        compose.onNodeWithText("İzlenmedi").performClick()
        compose.waitForIdle()
        // Kuzey Işığı was watched; Sessiz Liman has a Turkish subtitle file
        assertEquals(1, shown("Sessiz Liman"))
        assertEquals(0, shown("Kuzey Işığı"))
        compose.onNodeWithText("Dolby Vision").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bu filtrelere uyan film yok").assertExists()
        compose.onNodeWithText("Filtreleri temizle").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Mavi Kanal").assertExists()
    }

    @Test
    fun foldersOpenAndGoBack() {
        films(MoviesView.Folders)
        compose.onNodeWithText("Bilim Kurgu").assertExists()
        compose.onNodeWithText("3 film").assertExists()
        compose.onNodeWithText("Bilim Kurgu").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Cam Şehir").assertExists()
        assertEquals(0, shown("Sessiz Liman"))
        compose.onNodeWithContentDescription("Klasörlere dön").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Dram").assertExists()
    }

    private fun series() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                var open by remember { mutableStateOf<String?>(null) }
                var season by remember { mutableStateOf<Int?>(null) }
                SeriesContent(
                    shows = MovieSamples.library.shows,
                    tech = emptyMap(),
                    open = open,
                    onOpen = { open = it },
                    onClose = { open = null },
                    season = season,
                    onSeasonChange = { season = it },
                    onPlay = { played += it.title },
                    menu = { _, _ -> emptyList() },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun showsSayWhatComesNext() {
        series()
        // Kuzey Rüzgârı is in progress: in "Devam et" and in "Tüm diziler"
        assertEquals(2, shown("Kuzey Rüzgârı"))
        assertEquals(2, shown("Sıradaki: S02E02"))
        compose.onNodeWithText("Sıradaki: S01E01").assertExists()
        assertEquals(2, shown("2 sezon · 11 bölüm"))
        compose.onNodeWithText("1 sezon · 3 bölüm").assertExists()
    }

    @Test
    fun aShowOpensOnItsCurrentSeason() {
        series()
        compose.onAllNodesWithText("Kuzey Rüzgârı")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("show").assertExists()
        compose.onNodeWithText("Fener").assertExists()
        compose.onNodeWithText("7 / 11 izlendi", substring = true).assertExists()
        compose.onNodeWithText("Devam et · S02E02").performClick()
        assertEquals(listOf("S02E02 Fener"), played)
        compose.onNodeWithText("Sezon 1").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Başlangıç").assertExists()
        compose.onNodeWithText("Fener").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dizilere dön").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("show").assertDoesNotExist()
    }
}
