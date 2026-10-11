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
import app.nagmedar.ui.library.MovieSamples
import app.nagmedar.ui.library.VideoItem
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
 * S-311: films, episodes, tracks and subtitle lines found together, the Turkish way; chips for
 * each kind; network shares only when asked; a line plays its film from its time.
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
                    loading = false,
                    tech = MovieSamples.tech,
                    query = query,
                    onQueryChange = { query = it },
                    onPlayVideo = { played += "video:${it.title}" },
                    onPlayTrack = { track, _ -> played += "track:${track.title}" },
                    onPlayCue = { video, ms -> played += "cue:${video.title}@$ms" },
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
        compose.onNodeWithText("Film, dizi, parça ya da replik ara").assertExists()
    }

    @Test
    fun everyKindIsFoundTheTurkishWay() {
        page()
        query = "kuzey"
        compose.waitForIdle()
        compose.onNodeWithText("“kuzey” için", substring = true).assertExists()
        compose.onNodeWithText("FİLMLER · 1").assertExists()
        compose.onNodeWithText("DİZİLER", substring = true).assertExists()
        compose.onNodeWithText("MÜZİK · 1").assertExists()
        scrollTo("SÖYLENENLERDE")
        compose.onNodeWithText("SÖYLENENLERDE · 1").assertExists()
        // "isik" finds "Işık"
        query = "isik"
        compose.waitForIdle()
        assertTrue(shown("Kuzey Işığı") >= 1)
    }

    @Test
    fun episodesComeWithTheirShowInItsOrder() {
        val data = SearchSamples.data
        val episodes = searchLibrary(data, "kuzey", includeNetwork = false).episodes.map { data.episodeLabel(it.key) }
        assertEquals(
            listOf("Kuzey Rüzgârı · S01E01 · Başlangıç", "Kuzey Rüzgârı · S01E02", "Kuzey Rüzgârı · S01E03"),
            episodes.take(3),
        )
        // the number finds an episode too
        assertEquals(listOf("Kuzey Rüzgârı · S02E02 · Fener"), searchLibrary(data, "s02e02", includeNetwork = false).episodes.map { data.episodeLabel(it.key) })
    }

    @Test
    fun chipsKeepOneKind() {
        page()
        query = "kuzey"
        compose.waitForIdle()
        compose.onNode(hasText("Müzik 1") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("MÜZİK · 1").assertExists()
        compose.onNodeWithText("FİLMLER · 1").assertDoesNotExist()
        compose.onNode(hasText("Kuzeyden Esen", substring = true) and hasClickAction()).performClick()
        assertEquals(listOf("track:Kuzeyden Esen"), played)
    }

    @Test
    fun networkSharesOnlyWhenAsked() {
        page()
        query = "fener"
        compose.waitForIdle()
        // the episode on the device is found, the copy on the NAS is counted
        scrollTo("Ağda 1 dosya daha")
        compose.onNodeWithText("Ağda 1 dosya daha").assertExists()
        compose.onNode(hasText("Ağdakileri de göster") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ağda 1 dosya daha").assertDoesNotExist()
        assertTrue(shown("Fener (NAS)") >= 1)
    }

    @Test
    fun aLinePlaysItsFilmFromItsTime() {
        page()
        query = "yildizlar"
        compose.waitForIdle()
        compose.onNodeWithText("SÖYLENENLERDE · 1").assertExists()
        compose.onNode(hasText("Işıklar söndüğünde", substring = true) and hasClickAction()).performClick()
        assertEquals(listOf("cue:Kuzey Işığı@62345"), played)
    }

    /** S-1501: in English the syntax's examples are English, and the groups' titles English capitals (FILMS, not FİLMS). */
    @Test @Config(qualifiers = "en-w1280dp-h1200dp-mdpi")
    fun inEnglishTheSyntaxAndTheTitlesAreEnglish() {
        page()
        compose.onNodeWithText("type:film north").assertExists()
        compose.onNodeWithText("\"northern lights\"").assertExists()
        compose.onNodeWithText("-trailer").assertExists()
        query = "type:films kuzey"
        compose.waitForIdle()
        assertEquals(1, shown("FILMS ·"))
        assertEquals(0, shown("FİLMS"))
    }

    @Test
    fun nothingFoundSaysSo() {
        page()
        query = "zzzz"
        compose.waitForIdle()
        compose.onNodeWithText("“zzzz” için sonuç yok").assertExists()
    }
}

/** The films and episodes of [MovieSamples], two tracks, a subtitle file, and an episode on a NAS. */
object SearchSamples {
    private val nas = VideoItem(
        "smb://nas/Diziler/Kuzey Rüzgârı/Sezon 2/S02E02 Fener (NAS).mkv", "S02E02 Fener (NAS)", 52 * 60_000L, 0f, 3840, 2160,
        path = "/Diziler/Kuzey Rüzgârı/Sezon 2/S02E02 Fener (NAS).mkv",
    )

    private val srt = "1\n00:01:02,345 --> 00:01:04,000\nIşıklar söndüğünde, yolu yıldızlar gösterir.\n\n2\n01:11:45,000 --> 01:11:47,000\nBu Kuzey gecesi.\n"

    val data: SearchData by lazy {
        SearchData(
            videos = MovieSamples.items + MovieSamples.episodes + nas,
            tracks = listOf(
                TrackItem("file:///Music/Ada Karan/Sabaha Kadar.flac", "Sabaha Kadar", "Ada Karan", "Gece Mavisi", 258_000),
                TrackItem("file:///Music/Ada Karan/Kuzeyden Esen.flac", "Kuzeyden Esen", "Ada Karan", "Gece Mavisi", 231_000),
            ),
            subtitles = listOf(Triple(MovieSamples.kuzeyIsigi, "Kuzey Işığı.tr.srt", SubtitleParser.parse("Kuzey Işığı.tr.srt", srt.toByteArray()))),
        )
    }
}
