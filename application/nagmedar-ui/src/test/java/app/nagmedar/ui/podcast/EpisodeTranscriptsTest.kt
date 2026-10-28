package app.nagmedar.ui.podcast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.DownloadState
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.ui.transcribe.SpeechSegment
import app.nagmedar.ui.transcribe.WhisperFailure
import app.nagmedar.ui.transcribe.WhisperLines
import app.nagmedar.ui.transcribe.WhisperModel
import app.nagmedar.ui.transcribe.WhisperQueue
import app.nagmedar.ui.transcribe.WhisperRequest
import app.nagmedar.ui.transcribe.WhisperStage
import app.nagmedar.ui.transcribe.WhisperStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Podcast transcripts by Whisper: what Whisper heard written as WebVTT and read back by the transcripts' reader with its
 * times, the files in the app's storage, the downloads transcribed by themselves, and what the episode's page offers.
 */
@RunWith(AndroidJUnit4::class)
class EpisodeTranscriptsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val heard = listOf(
        SpeechSegment(0, 2_400, " Merhaba, gece yayınına hoş geldiniz."),
        SpeechSegment(2_400, 2_450, "[BLANK_AUDIO]"),
        SpeechSegment(
            3_000,
            11_000,
            "Bu akşam Itrî'nin bestelerini, yeni çıkan kayıtlarını ve onları kaydeden ustaların neler anlattığını konuşacağız.",
        ),
        SpeechSegment(65_000, 66_500, "Teşekkürler --> hepinize."),
    )

    private fun episode(id: Long = 7, model: String? = "small", transcript: String? = null, state: Int = DownloadState.DONE) = PodcastEpisode(
        id = id, feedId = 1, guid = "g$id", title = "Bölüm $id", audioUrl = "https://example.org/$id.mp3",
        downloadState = state, transcriptUrl = transcript, whisperModel = model,
    )

    @Test
    fun whatWhisperHeardReadsBackAsATimedTranscript() {
        val cues = WhisperLines.cues(heard)
        val bytes = WhisperLines.vtt(cues, EpisodeTranscripts.note(WhisperModel.Small, "tr"))
        val text = String(bytes, Charsets.UTF_8)
        assertTrue(text.startsWith("WEBVTT\n\nNOTE Nagmedar · whisper.cpp small · tr\n\n"))
        val transcript = EpisodeTranscripts.parse(text, "small")!!
        assertTrue(transcript.timed)
        assertEquals("small", transcript.whisperModel)
        // the note is no passage; every passage keeps its times and words, its rows joined again
        assertEquals(cues.map { it.startMs to it.endMs }, transcript.cues.map { it.startMs to it.endMs })
        assertEquals(cues.map { it.text.replace('\n', ' ').replace("-->", "->") }, transcript.cues.map { it.text })
        assertTrue(transcript.cues.none { "BLANK" in it.text })
        // a long line is shared out over passages, each its part of the time
        assertTrue(transcript.cues.size > 3)
        assertEquals(1, transcript.indexAt(3_500))
        assertEquals(65_000L, transcript.cues.last().startMs)
        // WebVTT allows no arrow in a passage
        assertEquals("Teşekkürler -> hepinize.", transcript.cues.last().text)
    }

    @Test
    fun anEpisodesTranscriptIsAFileOfTheAppsOwn() {
        val bytes = WhisperLines.vtt(WhisperLines.cues(heard), EpisodeTranscripts.note(WhisperModel.Base, ""))
        assertTrue(EpisodeTranscripts.write(context, 7, bytes))
        assertTrue(EpisodeTranscripts.file(context, 7).path.startsWith(context.filesDir.path))
        assertEquals("base", EpisodeTranscripts.read(context, episode(model = "base"))?.whisperModel)
        // no model in its row: it has none, whatever the folder holds
        assertNull(EpisodeTranscripts.read(context, episode(model = null)))
        // written again: the new one
        assertTrue(EpisodeTranscripts.write(context, 7, WhisperLines.vtt(WhisperLines.cues(heard.take(1)), "")))
        assertEquals(1, EpisodeTranscripts.read(context, episode())?.cues?.size)
        // the files of episodes gone go, but not one a job has just written
        assertTrue(EpisodeTranscripts.write(context, 8, bytes))
        EpisodeTranscripts.tidy(context, kept = setOf(7))
        assertTrue(EpisodeTranscripts.file(context, 8).isFile)
        EpisodeTranscripts.tidy(context, kept = setOf(7), now = System.currentTimeMillis() + 3_600_000)
        assertFalse(EpisodeTranscripts.file(context, 8).exists())
        assertTrue(EpisodeTranscripts.file(context, 7).isFile)
        assertTrue(EpisodeTranscripts.delete(context, 7))
        assertNull(EpisodeTranscripts.read(context, episode()))
    }

    @Test
    fun onlyANewDownloadWithoutATranscriptIsTranscribedByItself() {
        val fresh = episode(model = null)
        assertTrue(AutoTranscribe.wants(on = true, fresh, modelReady = true))
        assertFalse("off", AutoTranscribe.wants(on = false, fresh, modelReady = true))
        assertFalse("no model: its size is the person's to see", AutoTranscribe.wants(on = true, fresh, modelReady = false))
        assertFalse("the feed has one", AutoTranscribe.wants(on = true, episode(model = null, transcript = "https://example.org/7.vtt"), modelReady = true))
        assertFalse("it has its own", AutoTranscribe.wants(on = true, episode(), modelReady = true))
        assertFalse("not downloaded", AutoTranscribe.wants(on = true, episode(model = null, state = DownloadState.FAILED), modelReady = true))
    }

    @Test
    fun thePageOffersWhatTheQueueSays() {
        val running = WhisperStatus.Running("w", WhisperRequest(7, "Bölüm 7"), WhisperStage.Transcribing, doneSeconds = 30.0, totalSeconds = 60.0)
        val queue = WhisperQueue().add(WhisperRequest(7, "Bölüm 7")).add(WhisperRequest(8, "Bölüm 8", auto = true)).failed(9, WhisperFailure.Fetch)
        val state = TranscribeState(queue, running, WhisperModel.Small, downloadMb = null)
        assertEquals(TranscribeUi.Running(running), TranscribeUi.of(7, state))
        assertEquals(TranscribeUi.Queued(auto = true), TranscribeUi.of(8, state))
        assertEquals(TranscribeUi.Idle(WhisperModel.Small, null, WhisperFailure.Fetch), TranscribeUi.of(9, state))
        assertEquals(TranscribeUi.Idle(WhisperModel.Small, 181, null), TranscribeUi.of(10, state.copy(downloadMb = 181)))
        // before the first look, and in a build without Whisper
        assertEquals(TranscribeUi.Unavailable, TranscribeUi.of(7, null))
        assertEquals(TranscribeUi.Unavailable, TranscribeUi.of(7, state.copy(native = false)))
    }
}
