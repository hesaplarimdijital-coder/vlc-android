package app.nagmedar.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
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
 * S-311: tracks found the Turkish way; network shares only when asked; nothing found said so.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h1200dp-mdpi")
class SearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val played = mutableListOf<String>()
    private var query by mutableStateOf("")

    private fun page() {
        val data = SearchSamples.data
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                SearchContent(
                    data = data,
                    query = query,
                    onQueryChange = { query = it },
                    onPlayTrack = { track, _ -> played += "track:${track.title}" },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun scrollTo(text: String) = compose.onNodeWithTag("search").performScrollToNode(hasText(text, substring = true))

    @Test
    fun withoutAQueryTheSyntaxIsShown() {
        page()
        compose.onNodeWithText("Arama sözdizimi").assertExists()
        compose.onNodeWithText("Parça, sanatçı ya da albüm ara").assertExists()
    }

    @Test
    fun tracksAreFoundTheTurkishWay() {
        page()
        query = "kuzey"
        compose.waitForIdle()
        compose.onNodeWithText("“kuzey” için", substring = true).assertExists()
        compose.onNodeWithText("MÜZİK · 1").assertExists()
        compose.onNode(hasText("Kuzeyden Esen", substring = true) and hasClickAction()).performClick()
        assertEquals(listOf("track:Kuzeyden Esen"), played)
        // "isik" finds "Işık"
        query = "isik"
        compose.waitForIdle()
        assertTrue(shown("Işıklar") >= 1)
    }

    @Test
    fun networkSharesOnlyWhenAsked() {
        page()
        query = "fener"
        compose.waitForIdle()
        // the track on the device is found, the copy on the NAS is counted
        scrollTo("Ağda 1 dosya daha")
        compose.onNodeWithText("Ağda 1 dosya daha").assertExists()
        compose.onNode(hasText("Ağdakileri de göster") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ağda 1 dosya daha").assertDoesNotExist()
        assertTrue(shown("Fener (NAS)") >= 1)
    }

    /** S-1501: in English the syntax's examples are English, and the groups' titles English capitals. */
    @Test @Config(qualifiers = "en-w1280dp-h1200dp-mdpi")
    fun inEnglishTheSyntaxAndTheTitlesAreEnglish() {
        page()
        compose.onNodeWithText("\"night blue\"").assertExists()
        compose.onNodeWithText("-live").assertExists()
        query = "type:music kuzey"
        compose.waitForIdle()
        assertEquals(1, shown("MUSIC ·"))
    }

    @Test
    fun nothingFoundSaysSo() {
        page()
        query = "zzzz"
        compose.waitForIdle()
        compose.onNodeWithText("“zzzz” için sonuç yok").assertExists()
    }
}

/** Four tracks, one of them on a NAS. */
object SearchSamples {
    val data: SearchData by lazy {
        SearchData(
            tracks = listOf(
                TrackItem("file:///Music/Ada Karan/Sabaha Kadar.flac", "Sabaha Kadar", "Ada Karan", "Gece Mavisi", 258_000),
                TrackItem("file:///Music/Ada Karan/Kuzeyden Esen.flac", "Kuzeyden Esen", "Ada Karan", "Gece Mavisi", 231_000),
                TrackItem("file:///Music/Deniz Aral/Fener.flac", "Fener", "Deniz Aral", "Liman Işıkları", 302_000),
                TrackItem("smb://nas/Muzik/Deniz Aral/Fener (NAS).flac", "Fener (NAS)", "Deniz Aral", "Liman Işıkları", 302_000),
            ),
        )
    }
}
