package app.nagmedar.ui.transcribe

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.nagmedar.data.DownloadState
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.podcast.PodcastCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.videolan.tools.AppScope

/**
 * What is asked of a Whisper job: the transcript of the podcast episode [episodeId] ([name] its title, for the
 * notification) with [model]; [auto] when the automatic transcription of new downloads asked for it. The language is
 * the show's, read when the job starts ([app.nagmedar.ui.podcast.TranscriptLanguage]).
 */
@Immutable
data class WhisperRequest(
    val episodeId: Long,
    val name: String,
    val model: WhisperModel = WhisperModel.Default,
    val auto: Boolean = false,
) {
    fun toJson(): String = JSONObject()
        .put("episode", episodeId)
        .put("name", name)
        .put("model", model.key)
        .put("auto", auto)
        .toString()

    companion object {
        fun parse(json: String?): WhisperRequest? = runCatching {
            val o = JSONObject(json ?: return null)
            WhisperRequest(
                episodeId = o.getLong("episode"),
                name = o.optString("name"),
                model = WhisperModel.of(o.optString("model")),
                auto = o.optBoolean("auto"),
            )
        }.getOrNull()
    }
}

/** Why a job gave no transcript. */
enum class WhisperFailure(val key: String) {
    /** This build has no native lab library. */
    NoNative("no-native"),

    /** The model could not be downloaded: no network. */
    Offline("offline"),

    /** Hugging Face did not give the model. */
    Server("server"),

    /** What came down is not the model (its size or SHA-256 differ). */
    Checksum("checksum"),

    /** The device has no room for the model or the episode's audio, or could not write it. */
    Storage("storage"),

    /** The episode is gone, or its downloaded file could not be opened. */
    Input("input"),

    /** The episode's audio could not be downloaded for the job. */
    Fetch("fetch"),

    /** The episode's audio cannot be decoded. */
    Decoder("decoder"),

    /** The model on the device cannot be read: it is removed, to be downloaded again. */
    Model("model"),

    /** whisper stopped on the audio. */
    Engine("engine"),

    /** No speech was heard. */
    Empty("empty"),

    /** The transcript could not be written in the app's storage. */
    Output("output"),

    /** Android's time limit for a job in the background ended it. */
    Timeout("timeout"),
    ;

    companion object {
        fun of(key: String?): WhisperFailure = entries.firstOrNull { it.key == key } ?: Engine
    }
}

/** What a job is doing: waiting, downloading the model, downloading the episode's audio, transcribing. */
enum class WhisperStage(val key: String) {
    Waiting("waiting"),
    Downloading("downloading"),
    Fetching("fetching"),
    Transcribing("transcribing"),
    ;

    companion object {
        fun of(key: String?): WhisperStage = entries.firstOrNull { it.key == key } ?: Waiting
    }
}

/**
 * What a job runs on: the GPU through Vulkan ([gpu], its name in [device]) or the processor's [threads] cores, and how
 * fast: [speed] seconds of the audio a second, null until it can be told. [fellBack]: the GPU was asked for and the
 * processor ran.
 */
@Immutable
data class WhisperEngine(val gpu: Boolean, val device: String = "", val threads: Int = 0, val speed: Double? = null, val fellBack: Boolean = false) {
    /** The engine as a job's progress carries it. */
    fun toData(): Array<Pair<String, Any?>> = arrayOf(
        WhisperJobs.KEY_GPU to gpu,
        WhisperJobs.KEY_DEVICE to device,
        WhisperJobs.KEY_THREADS to threads,
        WhisperJobs.KEY_SPEED to (speed ?: -1.0),
        WhisperJobs.KEY_FALLBACK to fellBack,
    )

    companion object {
        /** The engine in a job's progress; null before the model was loaded. */
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

/** The job under way, as the pages show it. */
@Immutable
sealed interface WhisperStatus {
    val id: String
    val request: WhisperRequest?

    /**
     * Under way on [episodeId]: the model's or the audio's bytes ([doneBytes] of [totalBytes]) or the audio's seconds
     * ([doneSeconds] of [totalSeconds]), the time left when it can be told, what it runs on once the model is loaded.
     * [restarted]: Android stopped the app and the job began again from the start.
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
        val episodeId: Long = request?.episodeId ?: -1L,
    ) : WhisperStatus {
        /** How far, 0..1; null when it cannot be told. */
        val fraction: Float?
            get() = when (stage) {
                WhisperStage.Downloading, WhisperStage.Fetching -> if (totalBytes > 0) (doneBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else null
                WhisperStage.Transcribing -> if (totalSeconds > 0) (doneSeconds / totalSeconds).toFloat().coerceIn(0f, 1f) else null
                WhisperStage.Waiting -> null
            }
    }
}

/**
 * The podcast transcripts' Whisper jobs, one at a time: the requests wait in [WhisperQueueStore], and one WorkManager
 * job of its own name ([WORK]) takes the first, transcribes it and leaves the next to a job after it. A job is started
 * with what its request needs ([JobNeeds]); one that finds the first request needing more starts the one that waits for
 * it and ends. Cancelling a job takes its request out of the queue, and the next one starts.
 */
object WhisperJobs {
    const val WORK = "nagmedar-whisper"
    internal const val KEY_EPISODE = "episode"
    internal const val KEY_STAGE = "stage"
    internal const val KEY_DONE = "done"
    internal const val KEY_TOTAL = "total"
    internal const val KEY_BYTES = "bytes"
    internal const val KEY_BYTES_TOTAL = "bytes_total"
    internal const val KEY_ETA = "eta"
    internal const val KEY_GPU = "gpu"
    internal const val KEY_DEVICE = "device"
    internal const val KEY_THREADS = "threads"
    internal const val KEY_SPEED = "speed"
    internal const val KEY_FALLBACK = "fallback"
    internal const val KEY_NETWORK = "needs_network"
    internal const val KEY_CHARGING = "needs_charging"
    internal const val KEY_IDLE = "needs_idle"

    /** One start at a time in the process: two at once would each think there is no job. */
    private val starts = Mutex()

    /** [request] queued: it starts when the one under way is done (at once when there is none), when the device allows it. */
    fun request(context: Context, request: WhisperRequest) {
        val app = context.applicationContext
        WhisperQueueStore.update(app) { it.add(request) }
        kick(app)
    }

    /** The job of [episodeId] taken out of the queue: stopped when it is under way, and the next one started. */
    fun cancel(context: Context, episodeId: Long) {
        val app = context.applicationContext
        var first = false
        WhisperQueueStore.update(app) { queue ->
            first = queue.head?.episodeId == episodeId || queue.running == episodeId
            queue.remove(episodeId)
        }
        if (!first) return
        runCatching { WorkManager.getInstance(app).cancelUniqueWork(WORK) }
        kick(app)
    }

    /** The job under way stopped (the Downloads page's model row). */
    fun cancel(context: Context) {
        val queue = WhisperQueueStore.state(context).value
        (queue.running ?: queue.head?.episodeId)?.let { cancel(context, it) }
    }

    /** The queue taken up again (the app starts, the settings change): a job for its first request when none waits or runs. */
    fun resume(context: Context) = kick(context.applicationContext)

    /** The requests waiting and the failures, as every page follows them. */
    fun queue(context: Context) = WhisperQueueStore.state(context.applicationContext)

    /** A job for the queue's first request, off the main thread (WorkManager's answers and the disk are read). */
    internal fun kick(app: Context) {
        AppScope.launch(Dispatchers.IO) { runCatching { start(app, after = false) } }
    }

    /**
     * Starts the job the queue's first request needs: in place of one that waits, after the one under way (its next),
     * or, from the job under way ([after]), after itself. A job under way with nothing after it starts none.
     */
    internal suspend fun start(app: Context, after: Boolean, needs: JobNeeds? = null) {
        starts.withLock {
            val queue = WhisperQueueStore.state(app).value
            val manager = WorkManager.getInstance(app)
            val running = after || manager.getWorkInfosForUniqueWork(WORK).get().any { it.state == WorkInfo.State.RUNNING }
            val first = (if (running) queue.next else queue.head) ?: return
            // one asked for while a job runs starts after it with no needs: it looks at what its request needs when it starts
            val wanted = needs ?: if (running) JobNeeds() else needsOf(app, first)
            val work = OneTimeWorkRequestBuilder<WhisperWorker>()
                .setConstraints(constraints(wanted))
                .setInputData(workDataOf(KEY_NETWORK to wanted.network.ordinal, KEY_CHARGING to wanted.charging, KEY_IDLE to wanted.idle))
                .addTag(WORK)
                .build()
            manager.enqueueUniqueWork(WORK, if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, work)
        }
    }

    /** What [request] needs now: the model and the episode's file as they are on the device, Settings › Podcasts' Wi-Fi rule. Reads the disk. */
    internal fun needsOf(app: Context, request: WhisperRequest): JobNeeds {
        // an episode gone needs nothing: its job fails at once
        val episode = runCatching { NagmedarDatabase.get(app).podcasts().episode(request.episodeId) }.getOrNull() ?: return JobNeeds()
        val downloaded = episode.downloadState == DownloadState.DONE && episode.downloadPath != null
        val wifiOnly = PodcastCenter.settingsStore(app).read().refreshUnmeteredOnly
        return JobNeeds.of(request, WhisperModels.ready(app, request.model), downloaded, wifiOnly)
    }

    /** What a job was started with. */
    internal fun needsIn(data: Data) = JobNeeds(
        network = NetworkNeed.entries.getOrElse(data.getInt(KEY_NETWORK, 0)) { NetworkNeed.None },
        charging = data.getBoolean(KEY_CHARGING, false),
        idle = data.getBoolean(KEY_IDLE, false),
    )

    private fun constraints(needs: JobNeeds): Constraints = Constraints.Builder()
        .setRequiredNetworkType(
            when (needs.network) {
                NetworkNeed.None -> NetworkType.NOT_REQUIRED
                NetworkNeed.Any -> NetworkType.CONNECTED
                NetworkNeed.Unmetered -> NetworkType.UNMETERED
            },
        )
        .setRequiresCharging(needs.charging)
        .setRequiresDeviceIdle(needs.idle)
        .build()

    /** The job under way, with the episode it is on; null when none runs. */
    fun observe(context: Context): Flow<WhisperStatus.Running?> {
        val app = context.applicationContext
        // WorkManager starts with the app; a preview or a test without it shows no job
        val work = runCatching { WorkManager.getInstance(app) }.getOrNull() ?: return flowOf(null)
        return combine(work.getWorkInfosForUniqueWorkFlow(WORK), WhisperQueueStore.state(app)) { infos, queue ->
            val info = infos.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: return@combine null
            val episode = info.progress.getLong(KEY_EPISODE, -1L).takeIf { it >= 0 } ?: return@combine null
            status(info.id.toString(), info.progress, queue.requests.firstOrNull { it.episodeId == episode }, episode, info.runAttemptCount)
        }
    }

    /** A running job's status from its progress. */
    internal fun status(id: String, progress: Data, request: WhisperRequest?, episodeId: Long, attempts: Int) = WhisperStatus.Running(
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
        episodeId = episodeId,
    )
}

/** The job under way, as every page shows it. */
@Composable
fun rememberWhisperStatus(): State<WhisperStatus.Running?> {
    val context = LocalContext.current
    val flow = remember(context) { WhisperJobs.observe(context) }
    return flow.collectAsState(null)
}
