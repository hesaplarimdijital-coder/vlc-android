package app.nagmedar.ui.transcribe

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import org.videolan.tools.Settings

/**
 * S-709: what is asked of a job: the film (its URI and file name), its audio track (the n-th of
 * the file's audio tracks, 0 first; -1 for the one FFmpeg finds best) and what it is called, the
 * language heard, whether into English, and the model.
 */
@Immutable
data class WhisperRequest(
    val film: String,
    val name: String,
    val audioTrack: Int = -1,
    val audioLabel: String? = null,
    val language: WhisperLanguage = WhisperLanguage.Auto,
    val translate: Boolean = false,
    val model: WhisperModel = WhisperModel.Default,
) {
    fun toJson(): String = JSONObject()
        .put("film", film)
        .put("name", name)
        .put("audio", audioTrack)
        .put("audio_label", audioLabel ?: "")
        .put("language", language.code)
        .put("translate", translate)
        .put("model", model.key)
        .toString()

    companion object {
        fun parse(json: String?): WhisperRequest? = runCatching {
            val o = JSONObject(json ?: return null)
            WhisperRequest(
                film = o.getString("film"),
                name = o.getString("name"),
                audioTrack = o.optInt("audio", -1),
                audioLabel = o.optString("audio_label").ifEmpty { null },
                language = WhisperLanguage.of(o.optString("language")),
                translate = o.optBoolean("translate"),
                model = WhisperModel.of(o.optString("model")),
            )
        }.getOrNull()
    }
}

/** S-709: why a job gave no subtitles. */
enum class WhisperFailure(val key: String) {
    /** This build has no native lab library. */
    NoNative("no-native"),

    /** The model could not be downloaded: no network. */
    Offline("offline"),

    /** Hugging Face did not give the model. */
    Server("server"),

    /** What came down is not the model (its size or SHA-256 differ). */
    Checksum("checksum"),

    /** The device has no room for the model, or could not write it. */
    Storage("storage"),

    /** The film could not be opened: gone, or no longer ours to read. */
    Input("input"),

    /** The film has no audio track, or not the one asked for. */
    NoAudio("no-audio"),

    /** The audio track's format cannot be decoded. */
    Decoder("decoder"),

    /** The model on the device cannot be read: it is removed, to be downloaded again. */
    Model("model"),

    /** whisper stopped on the audio. */
    Engine("engine"),

    /** No speech was heard. */
    Empty("empty"),

    /** The new file could be written neither beside the film nor in the app's folder. */
    Output("output"),

    /** Android's time limit for a job in the background ended it. */
    Timeout("timeout"),
    ;

    companion object {
        fun of(key: String?): WhisperFailure = entries.firstOrNull { it.key == key } ?: Engine
    }
}

/** What a job is doing. */
enum class WhisperStage(val key: String) {
    Waiting("waiting"),
    Downloading("downloading"),
    Transcribing("transcribing"),
    ;

    companion object {
        fun of(key: String?): WhisperStage = entries.firstOrNull { it.key == key } ?: Waiting
    }
}

/**
 * S-709: what a job runs on: the GPU through Vulkan ([gpu], its name in [device]) or the
 * processor's [threads] cores, and how fast: [speed] seconds of the film a second, null until it
 * can be told. [fellBack]: the GPU was asked for and the processor ran.
 */
@Immutable
data class WhisperEngine(val gpu: Boolean, val device: String = "", val threads: Int = 0, val speed: Double? = null, val fellBack: Boolean = false) {
    /** The engine as a job's progress or result carries it. */
    fun toData(): Array<Pair<String, Any?>> = arrayOf(
        WhisperJobs.KEY_GPU to gpu,
        WhisperJobs.KEY_DEVICE to device,
        WhisperJobs.KEY_THREADS to threads,
        WhisperJobs.KEY_SPEED to (speed ?: -1.0),
        WhisperJobs.KEY_FALLBACK to fellBack,
    )

    companion object {
        /** The engine in a job's progress or result; null before the model was loaded. */
        fun of(data: Data): WhisperEngine? {
            val threads = data.getInt(WhisperJobs.KEY_THREADS, 0)
            if (threads <= 0) return null
            return WhisperEngine(
                gpu = data.getBoolean(WhisperJobs.KEY_GPU, false),
                device = data.getString(WhisperJobs.KEY_DEVICE).orEmpty(),
                threads = threads,
                speed = data.getDouble(WhisperJobs.KEY_SPEED, -1.0).takeIf { it > 0 },
                fellBack = data.getBoolean(WhisperJobs.KEY_FALLBACK, false),
            )
        }
    }
}

/** S-709: the job as the windows show it: under way, done, failed or cancelled. */
@Immutable
sealed interface WhisperStatus {
    val id: String
    val request: WhisperRequest?

    /**
     * Under way: the model's bytes ([doneBytes] of [totalBytes]) or the film's seconds
     * ([doneSeconds] of [totalSeconds]), the time left when it can be told, what it runs on once
     * the model is loaded. [restarted]: Android stopped the app and the job began again from the start.
     */
    data class Running(
        override val id: String,
        override val request: WhisperRequest?,
        val stage: WhisperStage,
        val doneSeconds: Double = 0.0,
        val totalSeconds: Double = 0.0,
        val doneBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val etaSeconds: Long? = null,
        val restarted: Boolean = false,
        val engine: WhisperEngine? = null,
    ) : WhisperStatus {
        /** How far, 0..1; null when it cannot be told. */
        val fraction: Float?
            get() = when (stage) {
                WhisperStage.Downloading -> if (totalBytes > 0) (doneBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else null
                WhisperStage.Transcribing -> if (totalSeconds > 0) (doneSeconds / totalSeconds).toFloat().coerceIn(0f, 1f) else null
                WhisperStage.Waiting -> null
            }
    }

    /** Done: the file, its lines and language, and what it ran on and how fast. */
    data class Done(
        override val id: String,
        override val request: WhisperRequest?,
        val saved: SavedSubtitle,
        val lines: Int,
        val language: String,
        val engine: WhisperEngine? = null,
    ) : WhisperStatus

    data class Failed(override val id: String, override val request: WhisperRequest?, val reason: WhisperFailure, val detail: String = "") : WhisperStatus

    data class Cancelled(override val id: String, override val request: WhisperRequest?) : WhisperStatus
}

/**
 * S-709: the subtitles from speech, one job at a time: a WorkManager job of its own name, which
 * is not started while one is under way ([ExistingWorkPolicy.KEEP]). The request rides in the
 * job's tag, so a job WorkManager starts again after the app was stopped knows what it is for.
 */
object WhisperJobs {
    const val WORK = "nagmedar-whisper"
    internal const val KEY_REQUEST = "request"
    internal const val KEY_STAGE = "stage"
    internal const val KEY_DONE = "done"
    internal const val KEY_TOTAL = "total"
    internal const val KEY_BYTES = "bytes"
    internal const val KEY_BYTES_TOTAL = "bytes_total"
    internal const val KEY_ETA = "eta"
    internal const val KEY_URI = "uri"
    internal const val KEY_NAME = "name"
    internal const val KEY_BESIDE = "beside"
    internal const val KEY_LINES = "lines"
    internal const val KEY_LANGUAGE = "language"
    internal const val KEY_REASON = "reason"
    internal const val KEY_DETAIL = "detail"
    internal const val KEY_GPU = "gpu"
    internal const val KEY_DEVICE = "device"
    internal const val KEY_THREADS = "threads"
    internal const val KEY_SPEED = "speed"
    internal const val KEY_FALLBACK = "fallback"
    private const val TAG_REQUEST = "whisper-request:"

    /** Starts a job for [request]; one under way stays and this one is not started. */
    fun start(context: Context, request: WhisperRequest) {
        val app = context.applicationContext
        val work = OneTimeWorkRequestBuilder<WhisperWorker>()
            .setInputData(workDataOf(KEY_REQUEST to request.toJson()))
            .addTag(WORK)
            .addTag(TAG_REQUEST + request.toJson())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, work)
    }

    /** The job under way stopped: the file under way is not written. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK)
    }

    /** The outcome put away in this process: every window hides it at once. */
    private val dismissed = MutableStateFlow<String?>(null)

    /** A finished job's outcome put away: it is not shown again. */
    fun dismiss(context: Context, id: String) {
        WhisperPrefs(Settings.getInstance(context.applicationContext)).seen = id
        dismissed.value = id
    }

    /** The job under way, else the last one finished whose outcome was not put away; null when there is neither. */
    fun observe(context: Context): Flow<WhisperStatus?> {
        val app = context.applicationContext
        val prefs = WhisperPrefs(Settings.getInstance(app))
        // WorkManager starts with the app; a preview or a test without it shows no job
        val work = runCatching { WorkManager.getInstance(app) }.getOrNull() ?: return flowOf(null)
        return combine(work.getWorkInfosForUniqueWorkFlow(WORK), dismissed) { infos, put ->
            val info = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull() ?: return@combine null
            val request = info.tags.firstOrNull { it.startsWith(TAG_REQUEST) }?.removePrefix(TAG_REQUEST)?.let(WhisperRequest::parse)
            val status = status(info.id.toString(), info.state, info.progress, info.outputData, request, info.runAttemptCount, prefs.timedOut)
            val seen = put ?: prefs.seen
            status.takeIf { !info.state.isFinished || it.id != seen }
        }
    }

    /** A job's status from what WorkManager tells of it; [timedOut] is the job Android's time limit ended. */
    internal fun status(id: String, state: WorkInfo.State, progress: Data, output: Data, request: WhisperRequest?, attempts: Int, timedOut: String?): WhisperStatus =
        when (state) {
            WorkInfo.State.RUNNING -> WhisperStatus.Running(
                id = id,
                request = request,
                stage = WhisperStage.of(progress.getString(KEY_STAGE)),
                doneSeconds = progress.getDouble(KEY_DONE, 0.0),
                totalSeconds = progress.getDouble(KEY_TOTAL, 0.0),
                doneBytes = progress.getLong(KEY_BYTES, 0L),
                totalBytes = progress.getLong(KEY_BYTES_TOTAL, 0L),
                etaSeconds = progress.getLong(KEY_ETA, -1L).takeIf { it >= 0 },
                restarted = attempts > 0,
                engine = WhisperEngine.of(progress),
            )
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> WhisperStatus.Running(id, request, WhisperStage.Waiting, restarted = attempts > 0)
            WorkInfo.State.SUCCEEDED -> WhisperStatus.Done(
                id = id,
                request = request,
                saved = SavedSubtitle(output.getString(KEY_URI).orEmpty(), output.getString(KEY_NAME).orEmpty(), output.getBoolean(KEY_BESIDE, false)),
                lines = output.getInt(KEY_LINES, 0),
                language = output.getString(KEY_LANGUAGE).orEmpty(),
                engine = WhisperEngine.of(output),
            )
            WorkInfo.State.FAILED -> WhisperStatus.Failed(id, request, WhisperFailure.of(output.getString(KEY_REASON)), output.getString(KEY_DETAIL).orEmpty())
            WorkInfo.State.CANCELLED -> if (id == timedOut) WhisperStatus.Failed(id, request, WhisperFailure.Timeout) else WhisperStatus.Cancelled(id, request)
        }
}
