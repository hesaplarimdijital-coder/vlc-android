package app.nagmedar.ui.player.subtitles.translate

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.nagmedar.translate.NativeTranslate
import app.nagmedar.translate.TranslationDirection
import app.nagmedar.translate.TranslationModel
import app.nagmedar.translate.TranslationModels
import app.nagmedar.ui.player.subtitles.SavedSubtitle
import app.nagmedar.ui.player.subtitles.tools.SubtitleDocument
import app.nagmedar.ui.player.subtitles.tools.ToolsFile
import app.nagmedar.ui.player.subtitles.tools.ToolsPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.Settings
import java.util.UUID

/** A model as the window shows it (S-710): not here, coming, here, or why it did not come. */
sealed interface ModelStatus {
    data object Missing : ModelStatus

    /** [done] of [total] bytes here; [waiting]: for a network, or to try again. */
    data class Downloading(val done: Long, val total: Long, val waiting: Boolean) : ModelStatus

    data object Ready : ModelStatus

    data class Failed(val problem: JobProblem) : ModelStatus
}

/** The translation of the file, as the window shows it (S-710). */
sealed interface TranslateRun {
    data object Idle : TranslateRun

    /** Asked for; WorkManager has not begun it yet. */
    data object Starting : TranslateRun

    /** [done] of [total] texts through the model; [name]: the file's. */
    data class Running(val done: Int, val total: Int, val name: String) : TranslateRun

    /** Written as [saved]; [shown]: it shows in the film with the original (null when no film plays). */
    data class Done(val saved: SavedSubtitle, val cues: Int, val shown: Boolean?) : TranslateRun

    data class Problem(val problem: JobProblem) : TranslateRun

    data object Cancelled : TranslateRun

    /** Another file's translation runs; this one waits for it to end. */
    data object Busy : TranslateRun
}

/** The translation's state in the tools' window (S-710). */
@Immutable
data class TranslateState(
    val direction: TranslationDirection = TranslationDirection.EnglishToTurkish,
    /** The user chose the direction: a file chosen next does not change it. */
    val chosen: Boolean = false,
    /** This build has the engine. */
    val engine: Boolean = true,
    val models: Map<TranslationDirection, ModelStatus> = emptyMap(),
    val run: TranslateRun = TranslateRun.Idle,
    /** The new file's name: "Film.tr.srt". */
    val outputName: String = "",
    /** Whether the film's two subtitles changed places. */
    val swapped: Boolean = false,
) {
    val model: TranslationModel get() = TranslationModels.of(direction)
    val modelStatus: ModelStatus get() = models[direction] ?: ModelStatus.Missing
    val running: Boolean get() = run is TranslateRun.Running || run == TranslateRun.Starting
}

/** A translation job as WorkManager tells it. */
data class JobInfo(
    val id: String,
    val finished: Boolean,
    val succeeded: Boolean,
    val cancelled: Boolean,
    val done: Int,
    val total: Int,
    val name: String,
    val saved: SavedSubtitle?,
    val problem: JobProblem,
)

/** What the translation works with: the models, their downloads and the jobs (the app's: WorkManager). */
interface TranslationService {
    val engineAvailable: Boolean

    fun model(model: TranslationModel): Flow<ModelStatus>

    fun online(): Boolean

    fun download(model: TranslationModel)

    fun cancelDownload(model: TranslationModel)

    suspend fun delete(model: TranslationModel)

    /** The translation started as the job [id]; false while another one runs. */
    suspend fun start(id: String, bytes: ByteArray, source: ToolsFile, outputName: String, direction: TranslationDirection): Boolean

    fun cancel()

    /** The last translation job, as it goes. */
    fun job(): Flow<JobInfo?>
}

/**
 * The tools' translation tab (S-710): the file the tools have open, translated offline between
 * Turkish and English into a new SRT file beside it; then shown in the film with the original,
 * the translation as the secondary subtitles. The direction is guessed from the file and can be
 * changed; a model not yet here is downloaded on the user's word, its size said first.
 */
@Stable
class SubtitleTranslateController(
    private val service: TranslationService,
    private val scope: CoroutineScope,
    /** The interface's language ("tr"): the direction for a file that says nothing of its own. */
    private val interfaceLanguage: String,
) {
    var state by mutableStateOf(TranslateState(engine = service.engineAvailable))
        private set

    private var watchers: List<Job> = emptyList()
    private var playback: ToolsPlayback? = null
    private var file: ToolsFile? = null
    private var document: SubtitleDocument? = null

    /** The job started from this window, and whether its outcome was taken in. */
    private var started: String? = null
    private var handled: String? = null

    /** The window opened ([playback] when a film plays): the models and the job are followed. */
    fun attach(playback: ToolsPlayback?) {
        this.playback = playback
        watchers.forEach { it.cancel() }
        state = TranslateState(engine = service.engineAvailable)
        watchers = TranslationModels.all.map { model ->
            scope.launch {
                service.model(model).collect { status -> state = state.copy(models = state.models + (model.direction to status)) }
            }
        } + scope.launch { service.job().collect(::onJob) }
    }

    /** The window closed: nothing is followed; a translation under way goes on in the background. */
    fun detach() {
        watchers.forEach { it.cancel() }
        watchers = emptyList()
        playback = null
        file = null
        document = null
    }

    /** The tools read [file]: the direction it most likely needs (unless one was chosen) and the new file's name. */
    fun setSource(file: ToolsFile?, document: SubtitleDocument?) {
        this.file = file
        this.document = document
        val current = state
        val direction = if (current.chosen || file == null) current.direction else CueTranslation.suggest(file.name, document, interfaceLanguage)
        state = current.copy(
            direction = direction,
            outputName = file?.let { CueTranslation.outputName(it.name, direction) }.orEmpty(),
            run = if (current.running || current.run == TranslateRun.Busy) current.run else TranslateRun.Idle,
        )
    }

    fun setDirection(direction: TranslationDirection) {
        val current = state
        state = current.copy(
            direction = direction,
            chosen = true,
            outputName = file?.let { CueTranslation.outputName(it.name, direction) }.orEmpty(),
            run = if (current.running) current.run else TranslateRun.Idle,
        )
    }

    /** The direction's model downloaded, after the user's word; without a network that is said instead. */
    fun download() {
        val model = state.model
        if (!service.online()) {
            state = state.copy(models = state.models + (model.direction to ModelStatus.Failed(JobProblem.NoNetwork)))
            return
        }
        state = state.copy(models = state.models + (model.direction to ModelStatus.Downloading(0, model.size, waiting = true)))
        service.download(model)
    }

    fun cancelDownload() = service.cancelDownload(state.model)

    /** [document], the file as the tools have it now (with their edits), translated in the background. */
    fun translate(document: SubtitleDocument? = this.document) {
        val current = state
        val file = file ?: return
        if (document == null) return
        if (current.running || current.modelStatus != ModelStatus.Ready || !current.engine) return
        if (CueTranslation.plan(document).texts.isEmpty()) {
            state = current.copy(run = TranslateRun.Problem(JobProblem.NothingToTranslate))
            return
        }
        state = current.copy(run = TranslateRun.Starting, swapped = false)
        // the job's id is known before it starts: an outcome that comes at once is this window's
        val id = UUID.randomUUID().toString()
        started = id
        scope.launch {
            val queued = try {
                val bytes = withContext(Dispatchers.Default) { document.toBytes(withBom = false) }
                service.start(id, bytes, file, current.outputName, current.direction)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(run = TranslateRun.Problem(JobProblem.Failed))
                return@launch
            }
            if (!queued) state = state.copy(run = TranslateRun.Busy)
        }
    }

    fun cancel() = service.cancel()

    /** The film's two subtitles change places: which shows on top. */
    fun swap() {
        val playback = playback ?: return
        if (playback.swapSubtitles()) state = state.copy(swapped = !state.swapped)
    }

    private suspend fun onJob(job: JobInfo?) {
        if (job == null) return
        if (!job.finished) {
            state = state.copy(run = TranslateRun.Running(job.done, job.total, job.name))
            return
        }
        // a job of an earlier window is not this one's to report
        if (job.id != started || job.id == handled) {
            if (state.running || state.run == TranslateRun.Busy) state = state.copy(run = TranslateRun.Idle)
            return
        }
        handled = job.id
        state = when {
            job.cancelled -> state.copy(run = TranslateRun.Cancelled)
            !job.succeeded || job.saved == null -> state.copy(run = TranslateRun.Problem(job.problem))
            else -> {
                state = state.copy(run = TranslateRun.Done(job.saved, job.total, shown = null))
                val source = file
                val film = playback
                val shown = if (film != null && source != null) runCatching { film.showTranslation(source, job.saved) }.getOrDefault(false) else null
                state.copy(run = TranslateRun.Done(job.saved, job.total, shown))
            }
        }
    }
}

/** The app's translation: models in the private storage, jobs on WorkManager. */
class WorkTranslationService(context: Context) : TranslationService {
    private val app = context.applicationContext

    override val engineAvailable: Boolean get() = NativeTranslate.available

    override fun model(model: TranslationModel): Flow<ModelStatus> {
        val store = TranslationJobs.store(app)
        val work = runCatching { WorkManager.getInstance(app) }.getOrNull() ?: return flowOf(ModelStatus.Missing)
        return work.getWorkInfosForUniqueWorkFlow(TranslationJobs.modelWork(model)).map { infos ->
            val info = infos.lastOrNull()
            when {
                info?.state == WorkInfo.State.RUNNING -> {
                    val done = info.progress.getLong(TranslationJobs.KEY_DONE, -1L).takeIf { it >= 0 } ?: store.downloaded(model)
                    ModelStatus.Downloading(done, model.size, waiting = false)
                }
                info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.BLOCKED -> ModelStatus.Downloading(store.downloaded(model), model.size, waiting = true)
                store.isReady(model) -> ModelStatus.Ready
                info?.state == WorkInfo.State.FAILED -> ModelStatus.Failed(info.outputData.problem())
                else -> ModelStatus.Missing
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun online(): Boolean {
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun download(model: TranslationModel) = TranslationJobs.download(app, model)

    override fun cancelDownload(model: TranslationModel) = TranslationJobs.cancelDownload(app, model)

    override suspend fun delete(model: TranslationModel) {
        TranslationJobs.cancelDownload(app, model)
        withContext(Dispatchers.IO) { TranslationJobs.store(app).delete(model) }
    }

    override suspend fun start(id: String, bytes: ByteArray, source: ToolsFile, outputName: String, direction: TranslationDirection): Boolean =
        withContext(Dispatchers.IO) { TranslationJobs.translate(app, UUID.fromString(id), bytes, source.uri, source.name, outputName, direction) }

    override fun cancel() = TranslationJobs.cancelTranslation(app)

    override fun job(): Flow<JobInfo?> {
        val work = runCatching { WorkManager.getInstance(app) }.getOrNull() ?: return flowOf(null)
        val prefs = Settings.getInstance(app)
        return work.getWorkInfosForUniqueWorkFlow(TranslationJobs.TRANSLATE_WORK).map { infos ->
            val info = infos.lastOrNull() ?: return@map null
            val output = info.outputData
            val progress = info.progress
            val uri = output.getString(TranslationJobs.KEY_URI)
            // Android's time limit ended it: WorkManager tells it as cancelled
            val timedOut = info.state == WorkInfo.State.CANCELLED && info.id.toString() == prefs.getString(TranslationJobs.KEY_TIMED_OUT, null)
            JobInfo(
                id = info.id.toString(),
                finished = info.state.isFinished,
                succeeded = info.state == WorkInfo.State.SUCCEEDED,
                cancelled = info.state == WorkInfo.State.CANCELLED && !timedOut,
                done = progress.getInt(TranslationJobs.KEY_DONE, 0),
                total = if (info.state.isFinished) output.getInt(TranslationJobs.KEY_TOTAL, 0) else progress.getInt(TranslationJobs.KEY_TOTAL, 0),
                name = progress.getString(TranslationJobs.KEY_NAME).orEmpty(),
                saved = uri?.let { SavedSubtitle(it, output.getString(TranslationJobs.KEY_NAME).orEmpty(), output.getBoolean(TranslationJobs.KEY_BESIDE, false)) },
                problem = if (timedOut) JobProblem.Timeout else output.problem(),
            )
        }
    }
}
