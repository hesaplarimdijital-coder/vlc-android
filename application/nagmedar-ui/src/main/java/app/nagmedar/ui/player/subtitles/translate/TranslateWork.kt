package app.nagmedar.ui.player.subtitles.translate

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.nagmedar.translate.LoadedModel
import app.nagmedar.translate.ModelStore
import app.nagmedar.translate.NativeTranslate
import app.nagmedar.translate.TranslationDirection
import app.nagmedar.translate.TranslationModel
import app.nagmedar.translate.TranslationModels
import app.nagmedar.ui.R
import app.nagmedar.ui.player.subtitles.decodeSubtitle
import app.nagmedar.ui.player.subtitles.detectEncoding
import app.nagmedar.ui.player.subtitles.saveSubtitle
import app.nagmedar.ui.player.subtitles.tools.SubtitleCodec
import app.nagmedar.ui.work.TimeLimit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * The offline translation's background jobs (S-710), on WorkManager: a model's download (one a
 * model, waiting for a network, going on from where it stopped) and a file's translation (one
 * at a time). Both run in the foreground with a notification of how far they are and a Cancel.
 */
object TranslationJobs {
    const val TRANSLATE_WORK = "nagmedar-subtitle-translate"
    private const val MODEL_WORK = "nagmedar-translate-model-"

    internal const val KEY_MODEL = "model"
    internal const val KEY_SOURCE = "source"
    internal const val KEY_SOURCE_URI = "source_uri"
    internal const val KEY_SOURCE_NAME = "source_name"
    internal const val KEY_OUTPUT_NAME = "output_name"
    internal const val KEY_DIRECTION = "direction"
    internal const val KEY_DONE = "done"
    internal const val KEY_TOTAL = "total"
    internal const val KEY_URI = "uri"
    internal const val KEY_NAME = "name"
    internal const val KEY_BESIDE = "beside"
    internal const val KEY_PROBLEM = "problem"

    /** The translation Android's time limit ended (WorkManager tells it only as cancelled); this device's, a backup leaves it out. */
    const val KEY_TIMED_OUT = "nagmedar_translate_timed_out"

    fun modelWork(model: TranslationModel): String = MODEL_WORK + model.id

    /** Where the models are kept: the app's private storage that backups leave out. */
    fun store(context: Context): ModelStore = ModelStore(File(context.applicationContext.noBackupFilesDir, "translate-models"))

    /** [model]'s download, waiting for a network; a download under way goes on. */
    fun download(context: Context, model: TranslationModel) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(KEY_MODEL to model.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(modelWork(model), ExistingWorkPolicy.KEEP, request)
    }

    fun cancelDownload(context: Context, model: TranslationModel) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(modelWork(model))
    }

    /**
     * The translation of [bytes] (the subtitle file as the tools have it, named [sourceName]) in
     * [direction], into a new file [outputName] beside [sourceUri], as the job [id]. One at a time:
     * while one runs, another is not started (false). Blocking: not on the main thread.
     */
    fun translate(context: Context, id: UUID, bytes: ByteArray, sourceUri: String, sourceName: String, outputName: String, direction: TranslationDirection): Boolean {
        val app = context.applicationContext
        val folder = File(app.noBackupFilesDir, JOBS).apply { mkdirs() }
        val source = File(folder, UUID.randomUUID().toString() + ".sub")
        source.writeBytes(bytes)
        val request = OneTimeWorkRequestBuilder<SubtitleTranslateWorker>()
            .setId(id)
            .setInputData(
                workDataOf(
                    KEY_SOURCE to source.absolutePath,
                    KEY_SOURCE_URI to sourceUri,
                    KEY_SOURCE_NAME to sourceName.take(MAX_NAME),
                    KEY_OUTPUT_NAME to outputName.take(MAX_NAME),
                    KEY_DIRECTION to direction.name,
                ),
            )
            .build()
        val work = WorkManager.getInstance(app)
        val running = runCatching { work.getWorkInfosForUniqueWork(TRANSLATE_WORK).get() }.getOrNull().orEmpty().any { !it.state.isFinished }
        if (running) {
            source.delete()
            return false
        }
        work.enqueueUniqueWork(TRANSLATE_WORK, ExistingWorkPolicy.KEEP, request)
        return true
    }

    fun cancelTranslation(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(TRANSLATE_WORK)
    }

    /** The jobs' copies of the files they translate: a job's own is removed as it ends; strays of a killed process here. */
    internal fun pruneSources(context: Context, keep: String?) {
        val folder = File(context.applicationContext.noBackupFilesDir, JOBS)
        folder.listFiles()?.filter { it.absolutePath != keep }?.forEach { it.delete() }
    }

    internal const val CHANNEL = "nagmedar_translate"
    private const val JOBS = "translate-jobs"
    private const val MAX_NAME = 240

    internal fun channel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.nag_translate_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
    }

    /** A foreground job's notification: [title], [text], how far ([done] of [total], none yet when 0), its Cancel. */
    internal fun notification(worker: CoroutineWorker, title: String, text: String, done: Long, total: Long): Notification {
        val context = worker.applicationContext
        channel(context)
        val cancel = WorkManager.getInstance(context).createCancelPendingIntent(worker.id)
        val max = PROGRESS_MAX
        val at = if (total > 0) (done.coerceIn(0, total) * max / total).toInt() else 0
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(max, at, total <= 0)
            .addAction(R.drawable.nagmedar_notif_close, context.getString(R.string.nag_translate_cancel), cancel)
            .build()
    }

    internal fun foreground(id: Int, notification: Notification, mediaProcessing: Boolean): ForegroundInfo = when {
        mediaProcessing && Build.VERSION.SDK_INT >= 35 -> ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else -> ForegroundInfo(id, notification)
    }

    private const val PROGRESS_MAX = 1000
}

/** Why a job did not finish (the "problem" of its output), as the window tells it. */
enum class JobProblem(val key: String) {
    NoNetwork("network"),
    Corrupt("corrupt"),
    NoSpace("space"),
    NothingToTranslate("nothing"),
    Unsupported("unsupported"),
    NoModel("model"),
    NoEngine("engine"),
    WriteFailed("write"),
    Failed("failed"),

    /** Android's time limit for a foreground job ended it. */
    Timeout("timeout"),
    ;

    companion object {
        fun of(key: String?): JobProblem = entries.firstOrNull { it.key == key } ?: Failed
    }
}

/**
 * A model's download (S-710): every file not yet in place, each into its ".part" from where it
 * stopped, checked against its size and SHA-256 before it takes its name. A broken network
 * stops it; WorkManager starts it again when there is one, and it goes on.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private var shownAt = 0L

    override suspend fun doWork(): Result {
        val model = TranslationModels.all.firstOrNull { it.id == inputData.getString(TranslationJobs.KEY_MODEL) } ?: return Result.failure()
        val store = TranslationJobs.store(applicationContext)
        runCatching { setForeground(foregroundInfo(model, store.downloaded(model))) }
        // Android 15's daily time limit: the download goes on from its ".part" when WorkManager starts it again; the user is told
        TimeLimit.onReached(this) {
            TranslationJobs.channel(applicationContext)
            TimeLimit.tell(applicationContext, TranslationJobs.CHANNEL, TIME_LIMIT_ID, R.drawable.ic_notif_scan, R.string.nag_time_limit_model)
        }
        val downloader = TranslationModelDownloader(TranslationModelDownloader.client, store, userAgent())
        val outcome = downloader.download(model) { done ->
            // once a second: WorkManager keeps each progress in its database
            val now = SystemClock.elapsedRealtime()
            if (now - shownAt >= NOTIFY_EVERY_MS) {
                shownAt = now
                setProgressAsync(workDataOf(TranslationJobs.KEY_DONE to done, TranslationJobs.KEY_TOTAL to model.size))
                runCatching { setForegroundAsync(foregroundInfo(model, done)) }
            }
        }
        return when (outcome) {
            TranslationModelDownloader.Outcome.Done -> Result.success()
            // a network that went away: again later, from where it was (a few times)
            TranslationModelDownloader.Outcome.Network -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else failure(JobProblem.NoNetwork)
            TranslationModelDownloader.Outcome.Corrupt -> failure(JobProblem.Corrupt)
            TranslationModelDownloader.Outcome.NoSpace -> failure(JobProblem.NoSpace)
            TranslationModelDownloader.Outcome.Failed -> failure(JobProblem.Failed)
        }
    }

    private fun failure(problem: JobProblem) = Result.failure(workDataOf(TranslationJobs.KEY_PROBLEM to problem.key))

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val model = TranslationModels.all.firstOrNull { it.id == inputData.getString(TranslationJobs.KEY_MODEL) } ?: TranslationModels.englishToTurkish
        return foregroundInfo(model, 0)
    }

    private fun foregroundInfo(model: TranslationModel, done: Long): ForegroundInfo {
        val context = applicationContext
        val text = context.getString(R.string.nag_translate_download_progress, megabytes(done), megabytes(model.size))
        val notification = TranslationJobs.notification(this, context.getString(R.string.nag_translate_downloading_title, directionLabel(context, model.direction)), text, done, model.size)
        return TranslationJobs.foreground(NOTIFICATION_ID, notification, mediaProcessing = false)
    }

    private fun userAgent(): String =
        "Nagmedar/${BuildConfig.VLC_VERSION_NAME.takeIf { it.isNotBlank() } ?: "1.0"} (+https://github.com/hesaplarimdijital-coder/vlc-android)"

    private companion object {
        const val NOTIFICATION_ID = 7101
        const val TIME_LIMIT_ID = 7103
        const val NOTIFY_EVERY_MS = 1_000L
        const val MAX_ATTEMPTS = 5
    }
}

/**
 * A file's translation (S-710): the copy of the file the tools had read, every cue's text through
 * the model in batches, its times as they were; written as a NEW SRT file beside the original
 * (or, where the app may not write there, in its own subtitles folder). The original is not
 * touched. A Cancel stops it between two batches and nothing is written. If Android stops it
 * (the app's process), WorkManager starts it again from the beginning, with its copy of the file;
 * if Android's time limit for a foreground job ends it, it is stopped for good (again it would
 * only meet the limit) and says so. A notification tells what came of it at the end.
 */
class SubtitleTranslateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private var shownAt = 0L

    override suspend fun doWork(): Result {
        val path = inputData.getString(TranslationJobs.KEY_SOURCE) ?: return Result.failure()
        val source = File(path)
        var again = false
        try {
            val result = run(source)
            finished(result)
            return result
        } catch (e: CancellationException) {
            if (TimeLimit.reached(this)) {
                // the limit would only be met again: not taken up again, and that is said
                timedOut()
            } else {
                // stopped by Android rather than cancelled: the job runs again and needs its copy
                again = stopReason != WorkInfo.STOP_REASON_CANCELLED_BY_APP && stopReason != WorkInfo.STOP_REASON_UNKNOWN
            }
            throw e
        } finally {
            runCatching { NotificationManagerCompat.from(applicationContext).cancel(NOTIFICATION_ID) }
            if (!again) {
                withContext(NonCancellable + Dispatchers.IO) {
                    source.delete()
                    TranslationJobs.pruneSources(applicationContext, keep = null)
                }
            }
        }
    }

    /** What came of it, once it is done: the new file, or why there is none. */
    @SuppressLint("MissingPermission")
    private fun finished(result: Result) {
        val context = applicationContext
        val output = (result as? Result.Success)?.outputData
        val text = output?.getString(TranslationJobs.KEY_NAME)
            ?: context.getString(problemText((result as? Result.Failure)?.outputData?.problem() ?: JobProblem.Failed))
        val notification = NotificationCompat.Builder(context, TranslationJobs.CHANNEL)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(context.getString(if (output != null) R.string.nag_translate_done_title else R.string.nag_translate_failed_title))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        // without the permission the tools' window says it all
        runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) }
    }

    /** Android's time limit for a foreground job ended it: it is not taken up again, and that is said. */
    private fun timedOut() {
        val context = applicationContext
        Settings.getInstance(context).edit().putString(TranslationJobs.KEY_TIMED_OUT, id.toString()).apply()
        runCatching { WorkManager.getInstance(context).cancelWorkById(id) }
        TranslationJobs.channel(context)
        TimeLimit.tell(context, TranslationJobs.CHANNEL, TIME_LIMIT_ID, R.drawable.ic_notif_scan, R.string.nag_translate_timeout)
    }

    private suspend fun run(source: File): Result {
        val context = applicationContext
        val direction = TranslationDirection.entries.firstOrNull { it.name == inputData.getString(TranslationJobs.KEY_DIRECTION) } ?: return failure(JobProblem.Failed)
        val sourceName = inputData.getString(TranslationJobs.KEY_SOURCE_NAME).orEmpty()
        val sourceUri = inputData.getString(TranslationJobs.KEY_SOURCE_URI) ?: return failure(JobProblem.Failed)
        val outputName = inputData.getString(TranslationJobs.KEY_OUTPUT_NAME) ?: return failure(JobProblem.Failed)
        val title = context.getString(R.string.nag_translate_running_title, directionLabel(context, direction))
        runCatching { setForeground(TranslationJobs.foreground(NOTIFICATION_ID, TranslationJobs.notification(this, title, sourceName, 0, 0), mediaProcessing = true)) }
        if (!NativeTranslate.available) return failure(JobProblem.NoEngine)
        val model = TranslationModels.of(direction)
        val store = TranslationJobs.store(context)
        if (!store.isReady(model)) return failure(JobProblem.NoModel)
        val document = withContext(Dispatchers.Default) {
            val bytes = runCatching { source.readBytes() }.getOrNull() ?: return@withContext null
            runCatching { SubtitleCodec.parse(sourceName, decodeSubtitle(bytes, detectEncoding(bytes))) }.getOrNull()
        } ?: return failure(JobProblem.Unsupported)
        val plan = CueTranslation.plan(document)
        if (plan.texts.isEmpty()) return failure(JobProblem.NothingToTranslate)
        val translations = withContext(Dispatchers.Default) {
            val loaded = try {
                LoadedModel.open(store.folder(model).absolutePath, threads())
            } catch (e: IOException) {
                return@withContext null
            }
            loaded.use { engine ->
                val out = HashMap<String, String>(plan.texts.size * 2)
                var done = 0
                report(title, sourceName, 0, plan.texts.size)
                for (batch in plan.texts.chunked(BATCH)) {
                    // Cancel and the system's stop end it here, between two batches
                    coroutineContext.ensureActive()
                    val result = try {
                        engine.translate(batch)
                    } catch (e: IOException) {
                        return@withContext null
                    }
                    batch.forEachIndexed { i, text -> out[text] = result[i] }
                    done += batch.size
                    report(title, sourceName, done, plan.texts.size)
                }
                out
            }
        } ?: return failure(JobProblem.Failed)
        val bytes = CueTranslation.result(plan, translations, document.lineBreak).toBytes(withBom = true)
        val saved = withContext(Dispatchers.IO) {
            try {
                saveSubtitle(context, Uri.parse(sourceUri), outputName, bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        } ?: return failure(JobProblem.WriteFailed)
        return Result.success(
            workDataOf(
                TranslationJobs.KEY_URI to saved.uri,
                TranslationJobs.KEY_NAME to saved.name,
                TranslationJobs.KEY_BESIDE to saved.beside,
                TranslationJobs.KEY_TOTAL to plan.cues.size,
            ),
        )
    }

    private fun report(title: String, name: String, done: Int, total: Int) {
        setProgressAsync(workDataOf(TranslationJobs.KEY_DONE to done, TranslationJobs.KEY_TOTAL to total, TranslationJobs.KEY_NAME to name))
        val now = SystemClock.elapsedRealtime()
        if (now - shownAt < NOTIFY_EVERY_MS && done < total) return
        shownAt = now
        val text = applicationContext.getString(R.string.nag_translate_progress_note, done, total, name)
        runCatching { setForegroundAsync(TranslationJobs.foreground(NOTIFICATION_ID, TranslationJobs.notification(this, title, text, done.toLong(), total.toLong()), mediaProcessing = true)) }
    }

    private fun failure(problem: JobProblem) = Result.failure(workDataOf(TranslationJobs.KEY_PROBLEM to problem.key))

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        val notification = TranslationJobs.notification(this, context.getString(R.string.nag_translate_starting), inputData.getString(TranslationJobs.KEY_SOURCE_NAME).orEmpty(), 0, 0)
        return TranslationJobs.foreground(NOTIFICATION_ID, notification, mediaProcessing = true)
    }

    private companion object {
        const val NOTIFICATION_ID = 7102
        const val TIME_LIMIT_ID = 7104
        const val NOTIFY_EVERY_MS = 1_000L

        /** Lines a call: enough for the CPU's vector units, few enough for Cancel to answer within a second or two. */
        const val BATCH = 16

        /** The big cores' worth; more threads only fight over the memory. */
        fun threads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}

/** "English → Turkish". */
internal fun directionLabel(context: Context, direction: TranslationDirection): String = context.getString(
    when (direction) {
        TranslationDirection.TurkishToEnglish -> R.string.nag_translate_tr_en
        TranslationDirection.EnglishToTurkish -> R.string.nag_translate_en_tr
    },
)

/** A size in whole megabytes, as the notification's line says it. */
internal fun megabytes(bytes: Long): Long = (bytes + 524_288) / 1_048_576

/** A job's last output, read back. */
internal fun Data.problem(): JobProblem = JobProblem.of(getString(TranslationJobs.KEY_PROBLEM))
