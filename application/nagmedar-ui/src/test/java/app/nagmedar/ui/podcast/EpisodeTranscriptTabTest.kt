package app.nagmedar.ui.podcast

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The episode's "Döküm" tab with the transcripts the feeds give (1.0.2: the only ones): with none it says so and
 * offers nothing; the feed's is shown timed, and a tap plays the episode from that passage.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
class EpisodeTranscriptTabTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()

    private val actions = PodcastActions(
        onPlayAt = { _: PodcastFeed, episode: PodcastEpisode, ms: Long -> calls += "play:${episode.id}:$ms" },
    )

    /** The episode's page on its "Döküm" tab, scrolled to [tag]. */
    private fun show(ui: EpisodeDetailUi, tag: String) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) { EpisodeDetailView(ui, fullScreen = true, actions) }
        }
        compose.onNode(hasText("Döküm") and hasClickAction()).performClick()
        compose.onNodeWithTag("episode-detail").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun noTranscriptSaysSoAndOffersNothing() {
        show(PodcastSamples.episodePage(), "episode-transcript-none")
        compose.onNodeWithText("Döküm yok", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Program bu bölümün dökümünü vermiyor.", useUnmergedTree = true).assertExists()
        // no button in the panel: nothing to make, nothing promised
        compose.onNodeWithText("oluştur", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theFeedsTranscriptIsTimedAndATapPlaysFromThere() {
        show(PodcastSamples.episodePage().copy(transcript = ExtraUi.Ready(PodcastSamples.feedTranscript)), "episode-transcript-search")
        compose.onNodeWithTag("episode-detail").performScrollToNode(hasText("Konuğumuz Ayşe Demir; hoş geldiniz."))
        compose.onNodeWithText("Konuğumuz Ayşe Demir; hoş geldiniz.").performClick()
        assertEquals(listOf("play:10:9800"), calls)
    }
}
