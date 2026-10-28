package app.nagmedar.ui.podcast

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextEquals
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
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import app.nagmedar.ui.transcribe.WhisperFailure
import app.nagmedar.ui.transcribe.WhisperModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Podcast transcripts by Whisper on the episode's page: with no transcript the "Döküm" tab offers Whisper, with the
 * model's size; a job under way shows how far it is and what it runs on, and stops; the transcript made is shown as a
 * feed's is (timed, a tap plays from there), marked as Whisper's; the episode's menu makes it again or removes it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
class EpisodeTranscriptTabTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()

    private val actions = PodcastActions(
        onTranscribe = { calls += "transcribe:${it.id}" },
        onCancelTranscribe = { calls += "cancel:$it" },
        onDeleteTranscript = { calls += "delete:$it" },
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
    fun noTranscriptOffersWhisperWithTheModelsSize() {
        show(PodcastSamples.episodePage().copy(transcribe = TranscribeUi.Idle(WhisperModel.Small, downloadMb = 181)), "episode-transcript-none")
        compose.onNodeWithTag("episode-transcribe-model", useUnmergedTree = true).assertTextEquals("181 MB'lık small modeli önce bir kez Hugging Face'ten indirilir.")
        // not downloaded: its audio comes into the cache for the job
        compose.onNodeWithText("Bölüm indirilmediği için", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Whisper ile döküm oluştur").performClick()
        assertEquals(listOf("transcribe:10"), calls)
    }

    @Test
    fun aFailedTryIsSaidAndCanBeAskedAgain() {
        val ui = PodcastSamples.episodePage(download = DownloadUi.Done(58_000_000)).copy(transcribe = TranscribeUi.Idle(failure = WhisperFailure.Empty))
        show(ui, "episode-transcript-none")
        compose.onNodeWithTag("episode-transcribe-failed", useUnmergedTree = true).assertTextEquals("Son deneme: Bölümde konuşma duyulmadı.")
        compose.onNodeWithText("Bölüm indirilmediği için", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Whisper ile döküm oluştur").performClick()
        assertEquals(listOf("transcribe:10"), calls)
    }

    @Test
    fun aJobUnderWayShowsHowFarItIsAndStops() {
        show(PodcastSamples.episodePage().copy(transcribe = TranscribeUi.Running(PodcastSamples.transcribing)), "episode-transcribe-running")
        compose.onNodeWithTag("episode-transcribe-engine", useUnmergedTree = true).assertTextEquals("GPU (Vulkan) · gerçek zamanın 4,2 katı")
        compose.onNodeWithText("yaklaşık 8 dk kaldı", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("episode-transcribe-running-action").performClick()
        assertEquals(listOf("cancel:10"), calls)
    }

    @Test
    fun aWaitingJobSaysWhatItWaitsFor() {
        show(PodcastSamples.episodePage().copy(transcribe = TranscribeUi.Queued(auto = true)), "episode-transcribe-queued")
        compose.onNodeWithText("Cihaz şarj olurken ve boştayken başlar.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theTranscriptMadeIsShownAsAFeedsIs() {
        val ui = PodcastSamples.episodePage().copy(transcript = ExtraUi.Ready(PodcastSamples.whisperTranscript), transcribe = TranscribeUi.Idle())
        show(ui, "episode-transcript-whisper")
        compose.onNodeWithTag("episode-transcript-whisper", useUnmergedTree = true).assertTextEquals("Whisper ile oluşturuldu · small")
        compose.onNodeWithTag("episode-detail").performScrollToNode(hasText("Konuğumuz Ayşe Demir; hoş geldiniz."))
        compose.onNodeWithText("Konuğumuz Ayşe Demir; hoş geldiniz.").performClick()
        assertEquals(listOf("play:10:9800"), calls)
    }

    @Test
    fun theMenuMakesItAgainOrRemovesIt() {
        val res = compose.activity.resources
        fun labels(ui: EpisodeDetailUi) = transcribeEntries(res, ui, actions).map { (it as MenuEntry.Item).label }
        val page = PodcastSamples.episodePage()
        assertEquals(listOf("Whisper ile döküm oluştur"), labels(page.copy(transcribe = TranscribeUi.Idle())))
        val made = page.copy(transcript = ExtraUi.Ready(PodcastSamples.whisperTranscript), transcribe = TranscribeUi.Idle())
        assertEquals(listOf("Whisper ile yeniden oluştur", "Whisper dökümünü sil"), labels(made))
        assertEquals(listOf("Döküm işini iptal et"), labels(page.copy(transcribe = TranscribeUi.Running(PodcastSamples.transcribing))))
        assertEquals(emptyList<String>(), labels(page))
        transcribeEntries(res, made, actions).forEach { (it as MenuEntry.Item).onClick() }
        assertEquals(listOf("transcribe:10", "delete:10"), calls)
    }
}
