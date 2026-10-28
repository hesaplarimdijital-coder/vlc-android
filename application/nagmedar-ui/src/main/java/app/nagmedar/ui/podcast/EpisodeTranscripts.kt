package app.nagmedar.ui.podcast

import android.content.Context
import androidx.compose.runtime.Immutable
import app.nagmedar.data.DownloadState
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.lab.NativeLab
import app.nagmedar.ui.transcribe.WhisperFailure
import app.nagmedar.ui.transcribe.WhisperJobs
import app.nagmedar.ui.transcribe.WhisperModel
import app.nagmedar.ui.transcribe.WhisperModels
import app.nagmedar.ui.transcribe.WhisperPrefs
import app.nagmedar.ui.transcribe.WhisperQueue
import app.nagmedar.ui.transcribe.WhisperRequest
import app.nagmedar.ui.transcribe.WhisperStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.videolan.tools.Settings
import java.io.File

/**
 * Podcast transcripts by Whisper: an episode's own transcript, a WebVTT file in the app's storage beside the podcasts'
 * data (`files/podcast-transcripts/<episode>.vtt`, never in the person's folders), recorded in the episode's row
 * ([PodcastEpisode.whisperModel]). It is read like a feed's ([Transcripts]), and comes before it on the episode's page.
 */
object EpisodeTranscripts {
    private const val FOLDER = "podcast-transcripts"
    private const val EXTENSION = ".vtt"

    fun folder(context: Context): File = File(context.filesDir, FOLDER)

    fun file(context: Context, episodeId: Long): File = File(folder(context), "$episodeId$EXTENSION")

    /** "Nagmedar · whisper.cpp small · tr": what made a transcript, in its file's NOTE. */
    fun note(model: WhisperModel, language: String): String =
        listOf("Nagmedar", "whisper.cpp ${model.key}", language).filter { it.isNotBlank() }.joinToString(" · ")

    /** [bytes] as [episodeId]'s transcript, in place of the one before only once they are all written. Writes the disk. */
    fun write(context: Context, episodeId: Long, bytes: ByteArray): Boolean = runCatching {
        val target = file(context, episodeId)
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        part.writeBytes(bytes)
        if (!part.renameTo(target)) {
            part.delete()
            error("not moved")
        }
        true
    }.getOrDefault(false)

    /** [text], an episode's transcript file, read; [model] the key it was written with. */
    fun parse(text: String, model: String): Transcript? = Transcripts.parse(text, VTT)?.copy(whisperModel = model)

    /** [episode]'s own transcript; null when it has none, or its file is gone. Reads the disk. */
    fun read(context: Context, episode: PodcastEpisode): Transcript? {
        val model = episode.whisperModel ?: return null
        val file = file(context, episode.id)
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()?.let { parse(it, model) }
    }

    /** [episodeId]'s own transcript removed. Writes the disk. */
    fun delete(context: Context, episodeId: Long): Boolean = file(context, episodeId).let { !it.exists() || it.delete() }

    /**
     * The files of episodes that no longer have their own transcript (their show unsubscribed) removed, but for those of
     * the last [RECENT_MS] (a job may be writing its row). Reads and writes the disk.
     */
    fun tidy(context: Context, kept: Set<Long>, now: Long = System.currentTimeMillis()) {
        folder(context).listFiles()?.forEach { file ->
            val id = file.name.removeSuffix(EXTENSION).toLongOrNull()
            if ((id == null || id !in kept) && now - file.lastModified() > RECENT_MS) file.delete()
        }
    }

    private const val RECENT_MS = 10 * 60_000L

    private const val VTT = "text/vtt"
}

/**
 * "İndirilen bölümleri kendiliğinden dök": a download that has just finished is transcribed by itself when the setting
 * is on, the episode has no transcript (neither the feed's nor its own), and the model is on the device already (a
 * model is downloaded only when the person asks for a transcript and sees its size).
 */
object AutoTranscribe {
    fun wants(on: Boolean, episode: PodcastEpisode, modelReady: Boolean): Boolean =
        on && modelReady && episode.downloadState == DownloadState.DONE && episode.transcriptUrl == null && episode.whisperModel == null
}

/** The Whisper jobs as the episode pages follow them: the queue, the job under way, whether the model must be downloaded first. */
@Immutable
data class TranscribeState(
    val queue: WhisperQueue = WhisperQueue(),
    val running: WhisperStatus.Running? = null,
    /** The model a new job starts with, and its size when it is not on the device yet (null when it is). */
    val model: WhisperModel = WhisperModel.Default,
    val downloadMb: Int? = null,
    /** Whether this build has the native library Whisper is in. */
    val native: Boolean = true,
)

/** S-205, Whisper: what the episode's page offers for its transcript. */
sealed interface TranscribeUi {
    /** No job: one can be asked for, with [model] ([downloadMb] when it is downloaded first); [failure] why the last try gave none. */
    data class Idle(val model: WhisperModel = WhisperModel.Default, val downloadMb: Int? = null, val failure: WhisperFailure? = null) : TranscribeUi

    /** Asked for and waiting: after the job under way, or ([auto]) for the charger and an idle device. */
    data class Queued(val auto: Boolean) : TranscribeUi

    /** The job under way. */
    data class Running(val status: WhisperStatus.Running) : TranscribeUi

    /** No Whisper here (a build without the native library, the tests of other things). */
    data object Unavailable : TranscribeUi

    companion object {
        fun of(episodeId: Long, state: TranscribeState?): TranscribeUi {
            if (state == null || !state.native) return Unavailable
            state.running?.takeIf { it.episodeId == episodeId }?.let { return Running(it) }
            state.queue.requests.firstOrNull { it.episodeId == episodeId }?.let { return Queued(it.auto) }
            return Idle(state.model, state.downloadMb, state.queue.failures[episodeId])
        }
    }
}

/** The Whisper jobs as the episode pages use them; [WhisperTranscriber] in the app, a fake in the tests. */
interface EpisodeTranscriber {
    /** The queue, the job under way and the model, as they change. */
    fun state(): Flow<TranscribeState>

    /** A transcript of [episode] asked for. */
    fun request(episode: PodcastEpisode)

    /** [episodeId]'s job taken out of the queue, stopped when it is under way. */
    fun cancel(episodeId: Long)

    /** [episodeId]'s own transcript removed: the feed's (if any) is shown again. */
    suspend fun delete(episodeId: Long)
}

/** [EpisodeTranscriber] through [WhisperJobs], with the model Settings › Podcasts chooses. */
class WhisperTranscriber(context: Context) : EpisodeTranscriber {
    private val app = context.applicationContext
    private val prefs = WhisperPrefs(Settings.getInstance(app))

    override fun state(): Flow<TranscribeState> = combine(WhisperJobs.queue(app), WhisperJobs.observe(app)) { queue, running -> queue to running }
        .map { (queue, running) ->
            val model = prefs.model
            TranscribeState(queue, running, model, model.sizeMb.takeIf { !WhisperModels.ready(app, model) }, NativeLab.available)
        }
        .flowOn(Dispatchers.IO)

    override fun request(episode: PodcastEpisode) {
        WhisperJobs.request(app, WhisperRequest(episode.id, episode.title, prefs.model))
    }

    override fun cancel(episodeId: Long) = WhisperJobs.cancel(app, episodeId)

    override suspend fun delete(episodeId: Long) = withContext(Dispatchers.IO) {
        EpisodeTranscripts.delete(app, episodeId)
        NagmedarDatabase.get(app).podcasts().setWhisperModel(episodeId, null)
        Unit
    }

    companion object {
        /**
         * The downloads that have just finished ([ids]) transcribed by themselves, as [AutoTranscribe] says; they wait for
         * the charger and an idle device. Reads the database and the disk.
         */
        fun downloaded(context: Context, ids: List<Long>, on: Boolean) {
            if (!on || ids.isEmpty()) return
            val app = context.applicationContext
            val model = WhisperPrefs(Settings.getInstance(app)).model
            val ready = WhisperModels.ready(app, model)
            val podcasts = NagmedarDatabase.get(app).podcasts()
            ids.mapNotNull(podcasts::episode).filter { AutoTranscribe.wants(on, it, ready) }.forEach { episode ->
                WhisperJobs.request(app, WhisperRequest(episode.id, episode.title, model, auto = true))
            }
        }

        /** The jobs taken up again and the transcripts of episodes gone removed: at each start of the app. Reads and writes the disk. */
        fun resume(context: Context) {
            val app = context.applicationContext
            runCatching { EpisodeTranscripts.tidy(app, NagmedarDatabase.get(app).podcasts().whisperEpisodes().toSet()) }
            WhisperJobs.resume(app)
        }
    }
}
