package app.nagmedar.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/**
 * S-802: cards carry the technical badge and summary; with a pointer a click selects and shows
 * the details beside the list, a double click plays; a tap plays.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class MoviesContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val played = mutableListOf<String>()

    private fun render(input: InputMode, grid: Boolean = true) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                var selected by remember { mutableStateOf<String?>(null) }
                var requested by remember { mutableStateOf(false) }
                MoviesContent(
                    items = MovieSamples.items,
                    tech = MovieSamples.tech,
                    techState = MovieSamples::state,
                    grid = grid,
                    sort = null,
                    onSortChange = {},
                    selected = selected,
                    onSelect = { selected = it.key },
                    detailsRequested = requested,
                    onShowDetails = {
                        selected = it.key
                        requested = true
                    },
                    onCloseDetails = {
                        selected = null
                        requested = false
                    },
                    onPlay = { played += it.title },
                    menu = { _, _, _ -> emptyList() },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun cardsCarryTheBadgeAndTheSummary() {
        render(InputMode.Pointer)
        assertEquals(3, compose.onAllNodesWithText("4K DV").fetchSemanticsNodes().size)
        compose.onNodeWithText("2160p · DV P7 · Atmos").assertExists()
        compose.onNodeWithText("2160p · HDR10+ · E-AC-3").assertExists()
        // not probed yet: the medialibrary's size and the duration
        compose.onAllNodesWithText("HD").fetchSemanticsNodes().let { assertTrue("HD badges", it.size >= 2) }
        compose.onNodeWithText("1:44:00").assertExists()
    }

    @Test
    fun aPointerClickSelectsAndShowsTheDetails() {
        render(InputMode.Pointer)
        compose.onNodeWithTag("details").assertDoesNotExist()
        compose.onNodeWithText("Kuzey Işığı").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("details").assertExists()
        compose.onNodeWithContentDescription("Video: HEVC Main 10 · 3840×2160").assertExists()
        compose.onNodeWithContentDescription("HDR: Dolby Vision P7 FEL · HDR10").assertExists()
        compose.onNodeWithContentDescription("Ses 1: TrueHD 7.1 Atmos · İng").assertExists()
        compose.onNodeWithContentDescription("Dosya: 58,4 GB · MKV").assertExists()
        compose.onNodeWithText("Devam et").assertExists()
        assertTrue(played.isEmpty())
        compose.onNodeWithContentDescription("Ayrıntılar panelini kapat").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("details").assertDoesNotExist()
    }

    @Test
    fun aDoubleClickPlays() {
        render(InputMode.Pointer)
        compose.onNodeWithText("Demir Vadi").performTouchInput { doubleClick() }
        compose.waitForIdle()
        assertEquals(listOf("Demir Vadi"), played)
    }

    @Test
    fun aTapPlays() {
        render(InputMode.Touch)
        compose.onNodeWithText("Demir Vadi").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Demir Vadi"), played)
        compose.onNodeWithTag("details").assertDoesNotExist()
    }

    @Test
    fun anUnprobedFileSaysItIsBeingRead() {
        render(InputMode.Pointer)
        compose.onNodeWithText("Yedinci Kat").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Dosya okunuyor… ayrıntılar birazdan görünür.").assertExists()
    }

    @Test
    fun theTableHasTechnicalColumns() {
        render(InputMode.Pointer, grid = false)
        compose.onNodeWithText("2160p · DV P7").assertExists()
        assertEquals(2, compose.onAllNodesWithText("TrueHD 7.1 Atmos").fetchSemanticsNodes().size)
        compose.onNodeWithText("TrueHD 7.1").assertExists()
        compose.onNodeWithText("2160p · HDR10+").assertExists()
    }
}
