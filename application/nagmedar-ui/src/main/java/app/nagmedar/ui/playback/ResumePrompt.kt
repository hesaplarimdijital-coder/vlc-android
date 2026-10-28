package app.nagmedar.ui.playback

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.components.DialogCard
import app.nagmedar.ui.components.DialogFrame
import app.nagmedar.ui.podcast.PodcastPlaybackObserver
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.AppScope
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.ResumeStatus
import org.videolan.vlc.media.WaitConfirmation

/**
 * Classic UI: the question of VLC's "Resume playback: Ask" for what is not a video. The playlist
 * manager asks it (waitForConfirmationAudio) and plays nothing until it is answered; only VLC's
 * audio player answered it, so from Nağmedar the playback waited for good. A podcast episode is
 * not asked: its show's rule says where it starts (S-1104), so it starts from the beginning and the
 * episode's tracker takes it to its place. The video player keeps its own question.
 */
object ResumePrompts {
    // process-wide state: made in the global snapshot by ProcessState.warmUp, before any window composes
    private val asked = mutableStateOf<Question?>(null)

    /** The question waiting for an answer: the media's title and where it was left (0 when unknown). */
    class Question(val confirmation: WaitConfirmation, val leftAtMs: Long)

    val pending: Question? get() = asked.value

    /** The playlist manager asks about [confirmation]; main thread. */
    fun ask(context: Context, service: PlaybackService, confirmation: WaitConfirmation) {
        val media = service.playlistManager.getMedia(confirmation.index)
        val location = media?.location
        AppScope.launch {
            val dao = NagmedarDatabase.get(context.applicationContext).podcasts()
            val episode = location != null && withContext(Dispatchers.IO) { runCatching { PodcastPlaybackObserver.episodeAt(dao, location) != null }.getOrDefault(false) }
            if (episode) {
                play(service, confirmation, resume = false)
                return@launch
            }
            // what played before waits too
            service.pause()
            PlaybackService.waitConfirmation.postValue(confirmation.title)
            asked.value = Question(confirmation, media?.time ?: 0L)
        }
    }

    /** Answered elsewhere (the remote access page) or the playlist changed: nothing waits any more. */
    fun cleared() {
        asked.value = null
    }

    /** Resumes ([resume]) or starts again; [forList] does the same for the rest of the list without asking. */
    fun answer(resume: Boolean, forList: Boolean) {
        val question = asked.value ?: return
        asked.value = null
        val service = PlaybackService.instance ?: return
        if (forList) service.playlistManager.audioResumeStatus = if (resume) ResumeStatus.ALWAYS else ResumeStatus.NEVER
        play(service, question.confirmation, resume)
    }

    /** Closed without an answer: nothing plays, as VLC's question did. */
    fun cancel() {
        asked.value ?: return
        asked.value = null
        val service = PlaybackService.instance ?: return
        service.playlistManager.waitForConfirmationAudio.value = null
        PlaybackService.waitConfirmation.postValue(null)
        service.stop()
    }

    private fun play(service: PlaybackService, confirmation: WaitConfirmation, resume: Boolean) {
        val manager = service.playlistManager
        manager.waitForConfirmationAudio.value = null
        PlaybackService.waitConfirmation.postValue(null)
        AppScope.launch { manager.playIndex(confirmation.index, confirmation.flags, forceResume = resume, forceRestart = !resume) }
    }
}

/**
 * The question of [ResumePrompts] over the window: Resume (the keyboard's first) or Start over,
 * and whether the rest of the list goes the same way. Esc and Back close it and nothing plays.
 */
@Composable
fun ResumePromptHost() {
    val context = LocalContext.current
    val service by PlaybackService.serviceFlow.collectAsState()
    service?.let { playing ->
        val confirmation by playing.playlistManager.waitForConfirmationAudio.observeAsState()
        LaunchedEffect(confirmation) {
            val asked = confirmation
            when {
                asked == null -> ResumePrompts.cleared()
                // every window sees it: the first to take it asks
                !asked.used -> {
                    asked.used = true
                    ResumePrompts.ask(context, playing, asked)
                }
            }
        }
    }
    val question = ResumePrompts.pending ?: return
    var forList by remember(question) { mutableStateOf(false) }
    NagDialog(onDismissRequest = ResumePrompts::cancel) {
        DialogFrame(ResumePrompts::cancel) {
            DialogCard(stringResource(R.string.nag_resume_title), Modifier.testTag("resume-prompt")) {
                val colors = NagTheme.colors
                Text(question.confirmation.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (question.leftAtMs > 0) {
                    Text(stringResource(R.string.nag_resume_at, formatDuration(question.leftAtMs)), style = NagTheme.type.body, color = colors.textSecondary)
                }
                NagCheckbox(forList, { forList = it }, stringResource(R.string.nag_resume_for_list))
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_resume_start_over), { ResumePrompts.answer(resume = false, forList = forList) }, Modifier.testTag("resume-restart"))
                    NagButton(
                        stringResource(R.string.nag_resume_continue),
                        { ResumePrompts.answer(resume = true, forList = forList) },
                        Modifier.focusRequester(focus).testTag("resume-continue"),
                        primary = true,
                    )
                }
            }
        }
    }
}
