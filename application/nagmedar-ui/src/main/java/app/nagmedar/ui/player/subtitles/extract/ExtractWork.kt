package app.nagmedar.ui.player.subtitles.extract

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.ConfigurationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.nagmedar.lab.NativeLab
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.notify.NotificationIds
import app.nagmedar.ui.player.subtitles.SavedSubtitle
import app.nagmedar.ui.player.subtitles.saveSubtitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.tools.Settings
import org.videolan.vlc.repository.SlaveRepository
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume

/** What an extraction is asked for: the film (its URI and file name), the track and the new file's name. */
@Immutable
data class ExtractRequest(
    val film: String,
    val filmName: String,
    val track: EmbeddedTrack,
    val outputName: String,
) {
    fun toJson(): String = JSONObject()
        .put("film", film)
        .put("film_name", filmName)
        .put("index", track.index)
        .put("codec", track.codec)
        .put("language", track.language)
        .put("title", track.title)
        .put("forced", track.forced)
        .put("sdh", track.hearingImpaired)
        .put("output", outputName)
        .toString()

    companion object {
        fun parse(json: String?): ExtractRequest? = runCatching {
            val o = JSONObject(json ?: return null)
            ExtractRequest(
                film = o.getString("film"),
                filmName = o.optString("film_name"),
                track = EmbeddedTrack(
                    index = o.getInt("index"),
                    codec = o.optString("codec"),
                    language = o.optString("language"),
                    title = o.optString("title"),
                    forced = o.optBoolean("forced"),
                    hearingImpaired = o.optBoolean("sdh"),
                ),
                outputName = o.getString("output"),
            )
        }.getOrNull()
    }
}

/** Why an extraction gave no file. */
enum class ExtractFailure(val key: String) {
    /** This build has no native lab library. */
    NoNative("no-native"),

    /** The film could not be opened: gone, or no longer the app's to read. */
    Input("input"),

    /** The film no longer has the track. */
    NoTrack("no-track"),

    /** The track's lines are pictures (PGS, VobSub, DVB): they would need OCR. */
    Picture("picture"),

    /** A text format the extraction does not know (teletext, Blu-ray text …). */
    Unsupported("unsupported"),

    /** The track has no lines. */
    Empty("empty"),

    /** More lines than a file of subtitles holds. */
    TooLarge("too-large"),

    /** The new file could be written neither beside the film nor in the app's folder. */
    Output("output"),

    /** The native side stopped on the film. */
    Engine("engine"),

    /** Android's time limit for a job in the background ended it. */
    Timeout("timeout"),
    ;

    /** Its text names the track's format ("PGS"). */
    val namesFormat: Boolean get() = this == Picture || this == Unsupported

    companion object {
        fun of(key: String?): ExtractFailure = entries.firstOrNull { it.key == key } ?: Engine
    }
}

/** An extraction job as WorkManager tells it. */
@Immutable
data class ExtractJob(
    val id: String,
    val request: ExtractRequest?,
    val finished: Boolean,
    /** How far, 0..1; null before the film is being read. */
    val fraction: Float?,
    val saved: SavedSubtitle? = null,
    val lines: Int = 0,
    /** The film's file ended early: the lines up to there were written. */
    val partial: Boolean = false,
    val failure: ExtractFailure? = null,
    val cancelled: Boolean = false,
)

/**
 * The embedded subtitles' extraction as WorkManager jobs, one at a time ([ExistingWorkPolicy.KEEP]);
 * the request rides in the job's data, so a job WorkManager starts again knows what it is for.
 */
object ExtractJobs {
    const val WORK = "nagmedar-subtitle-extract"
    internal const val KEY_REQUEST = "request"
    internal const val KEY_FRACTION = "fraction"
    internal const val KEY_URI = "uri"
    internal const val KEY_NAME = "name"
    internal const val KEY_BESIDE = "beside"
    internal const val KEY_LINES = "lines"
    internal const val KEY_PARTIAL = "partial"
    internal const val KEY_REASON = "reason"
    internal const val KEY_DETAIL = "detail"

    /** The job Android's time limit ended (WorkManager tells it only as cancelled); this device's, a backup leaves it out. */
    const val KEY_TIMED_OUT = "nagmedar_extract_timed_out"

    /** The extraction of [request] as the job [id]; false while another one runs. Blocking: not on the main thread. */
    fun start(context: Context, id: UUID, request: ExtractRequest): Boolean {
        val work = WorkManager.getInstance(context.applicationContext)
        val running = runCatching { work.getWorkInfosForUniqueWork(WORK).get() }.getOrNull().orEmpty().any { !it.state.isFinished }
        if (running) return false
        val job = OneTimeWorkRequestBuilder<SubtitleExtractWorker>()
            .setId(id)
            .setInputData(workDataOf(KEY_REQUEST to request.toJson()))
            .build()
        work.enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, job)
        return true
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK)
    }

    /** The last job, as it goes; null when there is none (or WorkManager is not there: previews, tests). */
    fun observe(context: Context): Flow<ExtractJob?> {
        val app = context.applicationContext
        val work = runCatching { WorkManager.getInstance(app) }.getOrNull() ?: return flowOf(null)
        val prefs = Settings.getInstance(app)
        return work.getWorkInfosForUniqueWorkFlow(WORK).map { infos ->
            val info = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull() ?: return@map null
            job(info.id.toString(), info.state, info.progress, info.outputData, ExtractRequest.parse(info.inputDataOrNull()), prefs.getString(KEY_TIMED_OUT, null))
        }
    }

    /** WorkInfo carries no input data; the request is read from the progress and the output, where the worker puts it. */
    private fun WorkInfo.inputDataOrNull(): String? = progress.getString(KEY_REQUEST) ?: outputData.getString(KEY_REQUEST)

    internal fun job(id: String, state: WorkInfo.State, progress: Data, output: Data, request: ExtractRequest?, timedOut: String?): ExtractJob = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ExtractJob(id, request, finished = false, fraction = null)
        WorkInfo.State.RUNNING -> ExtractJob(id, request, finished = false, fraction = progress.getFloat(KEY_FRACTION, -1f).takeIf { it >= 0f })
        WorkInfo.State.SUCCEEDED -> ExtractJob(
            id = id,
            request = request,
            finished = true,
            fraction = 1f,
            saved = output.getString(KEY_URI)?.let { SavedSubtitle(it, output.getString(KEY_NAME).orEmpty(), output.getBoolean(KEY_BESIDE, false)) },
            lines = output.getInt(KEY_LINES, 0),
            partial = output.getBoolean(KEY_PARTIAL, false),
        )
        WorkInfo.State.FAILED -> ExtractJob(id, request, finished = true, fraction = null, failure = ExtractFailure.of(output.getString(KEY_REASON)))
        WorkInfo.State.CANCELLED -> if (id == timedOut) {
            ExtractJob(id, request, finished = true, fraction = null, failure = ExtractFailure.Timeout)
        } else {
            ExtractJob(id, request, finished = true, fraction = null, cancelled = true)
        }
    }

    /** Where a job writes before the file takes its place: the app's private storage that backups leave out. */
    internal fun scratch(context: Context): File = File(context.applicationContext.noBackupFilesDir, "extract-jobs")
}

/**
 * One extraction: a short foreground job (media processing on Android 15 and later, data sync
 * before) with a notification of how far it is and Cancel. The film is read once by the native
 * side into a new file in the app's private storage; that file then goes as a NEW file beside
 * the film (or, where the app may not write there, into its own subtitles folder) and the film
 * keeps it among its subtitles. Nothing is written over and the film is not touched. A Cancel
 * stops it at once and nothing stays behind. If Android stops the app, WorkManager starts it
 * again from the beginning; if Android's time limit for a foreground job ends it, it is stopped
 * for good and says so.
 */
class SubtitleExtractWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Volatile
    private var toldAt = 0L

    override suspend fun doWork(): Result {
        val json = inputData.getString(ExtractJobs.KEY_REQUEST)
        val request = ExtractRequest.parse(json) ?: return Result.failure()
        // the windows read what the job is for from its progress
        setProgress(workDataOf(ExtractJobs.KEY_REQUEST to json))
        // a foreground start can be refused (Android 12+, from the background): the job then runs as it can
        runCatching { setForeground(foregroundInfo(notification(request, null))) }
        val outcome = try {
            withContext(Dispatchers.IO) { produce(request) }
        } catch (e: CancellationException) {
            if (stopReason == WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT) timedOut(request)
            throw e
        } finally {
            runCatching { manager.cancel(NOTIFICATION_ID) }
        }
        return when (outcome) {
            is Outcome.Saved -> {
                say(applicationContext.getString(R.string.nag_extract_done_title, request.filmName), where(applicationContext, outcome.saved))
                Result.success(
                    workDataOf(
                        ExtractJobs.KEY_REQUEST to json,
                        ExtractJobs.KEY_URI to outcome.saved.uri,
                        ExtractJobs.KEY_NAME to outcome.saved.name,
                        ExtractJobs.KEY_BESIDE to outcome.saved.beside,
                        ExtractJobs.KEY_LINES to outcome.lines,
                        ExtractJobs.KEY_PARTIAL to outcome.partial,
                    ),
                )
            }
            is Outcome.Failed -> {
                say(applicationContext.getString(R.string.nag_extract_failed_title, request.filmName), failureMessage(applicationContext, outcome.reason, request.track))
                Result.failure(workDataOf(ExtractJobs.KEY_REQUEST to json, ExtractJobs.KEY_REASON to outcome.reason.key, ExtractJobs.KEY_DETAIL to outcome.detail.take(DETAIL_CHARS)))
            }
        }
    }

    private sealed interface Outcome {
        data class Saved(val saved: SavedSubtitle, val lines: Int, val partial: Boolean) : Outcome

        data class Failed(val reason: ExtractFailure, val detail: String = "") : Outcome
    }

    private suspend fun produce(request: ExtractRequest): Outcome {
        val app = applicationContext
        if (!NativeLab.available) return Outcome.Failed(ExtractFailure.NoNative)
        if (request.track.picture) return Outcome.Failed(ExtractFailure.Picture)
        val film = Uri.parse(request.film)
        val folder = ExtractJobs.scratch(app)
        // what a job of a stopped process left
        folder.listFiles()?.forEach { it.delete() }
        if (!folder.isDirectory && !folder.mkdirs()) return Outcome.Failed(ExtractFailure.Output)
        val scratch = File(folder, id.toString() + "." + request.track.format.extension)
        try {
            val input = runCatching { app.contentResolver.openFileDescriptor(film, "r") }.getOrNull()
                ?: return Outcome.Failed(ExtractFailure.Input)
            tell(request, 0f)
            val result = runCatching { JSONObject(extract(input, request, scratch)) }.getOrNull()
                ?: return Outcome.Failed(ExtractFailure.Engine)
            val detail = result.optString("detail")
            when (result.optString("error")) {
                "" -> Unit
                "open" -> return Outcome.Failed(ExtractFailure.Input, detail)
                "no_stream" -> return Outcome.Failed(ExtractFailure.NoTrack, detail)
                "image" -> return Outcome.Failed(ExtractFailure.Picture, detail)
                "unsupported" -> return Outcome.Failed(ExtractFailure.Unsupported, result.optString("codec"))
                "empty" -> return Outcome.Failed(ExtractFailure.Empty, detail)
                "too_large" -> return Outcome.Failed(ExtractFailure.TooLarge, detail)
                "write" -> return Outcome.Failed(ExtractFailure.Output, detail)
                else -> return Outcome.Failed(ExtractFailure.Engine, result.optString("error") + " " + detail)
            }
            // copied as it is read: a track of millions of lines never sits in the heap whole
            val saved = runCatching { saveSubtitle(app, film, request.outputName) { out -> scratch.inputStream().use { it.copyTo(out) } } }.getOrNull()
                ?: return Outcome.Failed(ExtractFailure.Output)
            // the film keeps it: among its subtitles when it plays next, wherever the file is
            if (!saved.beside || film.scheme == ContentResolver.SCHEME_CONTENT) {
                runCatching { SlaveRepository.getInstance(app).saveSlave(request.film, IMedia.Slave.Type.Subtitle, KEPT_PRIORITY, saved.uri) }
            }
            return Outcome.Saved(saved, result.optInt("events"), result.optBoolean("partial"))
        } finally {
            withContext(NonCancellable) { scratch.delete() }
        }
    }

    /**
     * The native extraction on a thread of its own, which closes [input] when it is done; a
     * cancelled job stops it. One runs at a time: a job started right after one was cancelled
     * waits for it to stop.
     */
    private suspend fun extract(input: ParcelFileDescriptor, request: ExtractRequest, output: File): String =
        suspendCancellableCoroutine { continuation ->
            val cancelled = AtomicBoolean(false)
            val listener = NativeLab.SubtitleExtraction { fraction ->
                // a cancel that came before the extraction began
                if (cancelled.get()) NativeLab.cancelSubtitleExtraction()
                tell(request, fraction.toFloat())
            }
            continuation.invokeOnCancellation {
                cancelled.set(true)
                NativeLab.cancelSubtitleExtraction()
            }
            Thread({
                val json = try {
                    nativeLock.withLock {
                        if (cancelled.get()) CANCELLED_JSON else NativeLab.extractSubtitle("/proc/self/fd/${input.fd}", request.track.index, output.absolutePath, listener)
                    }
                } catch (e: Throwable) {
                    // the library failed to load or link: said as the engine's failure
                    ERROR_JSON
                } finally {
                    runCatching { input.close() }
                }
                if (continuation.isActive) continuation.resume(json)
            }, THREAD).start()
        }

    /** How far, for the windows and the notification: once a second at most. */
    private fun tell(request: ExtractRequest, fraction: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - toldAt < TELL_EVERY_MS && fraction < 1f) return
        toldAt = now
        setProgressAsync(workDataOf(ExtractJobs.KEY_REQUEST to request.toJson(), ExtractJobs.KEY_FRACTION to fraction))
        runCatching { manager.notify(NOTIFICATION_ID, notification(request, fraction)) }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(notification(ExtractRequest.parse(inputData.getString(ExtractJobs.KEY_REQUEST)), null))

    private fun foregroundInfo(notification: Notification): ForegroundInfo = when {
        Build.VERSION.SDK_INT >= 35 -> ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else -> ForegroundInfo(NOTIFICATION_ID, notification)
    }

    /** The ongoing notification: the film, how far, Cancel. */
    private fun notification(request: ExtractRequest?, fraction: Float?): Notification {
        val context = applicationContext
        channel(context)
        val text = if (fraction == null) context.getString(R.string.nag_extract_starting) else percent(context, fraction)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(context.getString(R.string.nag_extract_running_title, request?.filmName.orEmpty()))
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).toInt(), fraction == null)
            .addAction(R.drawable.nagmedar_notif_close, context.getString(R.string.nag_extract_cancel), WorkManager.getInstance(context).createCancelPendingIntent(id))
            .build()
    }

    /** Android's time limit for a foreground job ended it: it is not taken up again, and that is said. */
    private fun timedOut(request: ExtractRequest) {
        val context = applicationContext
        Settings.getInstance(context).edit().putString(ExtractJobs.KEY_TIMED_OUT, id.toString()).apply()
        runCatching { WorkManager.getInstance(context).cancelWorkById(id) }
        say(context.getString(R.string.nag_extract_failed_title, request.filmName), failureMessage(context, ExtractFailure.Timeout, request.track))
    }

    @SuppressLint("MissingPermission")
    private fun say(title: String, text: String) {
        val context = applicationContext
        channel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        // without the permission the tools' window says it all
        runCatching { NotificationManagerCompat.from(context).notify(OUTCOME_ID, notification) }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        Intent(context, NagmedarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL = "nagmedar_subtitle_extract"
        private const val NOTIFICATION_ID = NotificationIds.EXTRACT
        private const val OUTCOME_ID = NotificationIds.EXTRACT_DONE
        private const val PROGRESS_MAX = 1000
        private const val TELL_EVERY_MS = 1000L
        private const val DETAIL_CHARS = 300
        private const val THREAD = "nagmedar-subtitle-extract"

        /** The priority the classic player keeps a subtitle the user chose with (S-701). */
        private const val KEPT_PRIORITY = 2
        private const val CANCELLED_JSON = """{"error":"cancelled"}"""
        private const val ERROR_JSON = """{"error":"native"}"""

        /** One extraction at a time in the process: the native side has one cancel flag. */
        private val nativeLock = ReentrantLock()

        private fun channel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.nag_extract_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            )
        }

        /** "%45" in Turkish, "45%" in English. */
        fun percent(context: Context, fraction: Float): String {
            val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
            return NumberFormat.getPercentInstance(locale).format(fraction.coerceIn(0f, 1f).toDouble())
        }

        /** "Written beside the film: Film.tr.srt" or where else it went. */
        fun where(context: Context, saved: SavedSubtitle): String =
            context.getString(if (saved.beside) R.string.nag_extract_done_beside else R.string.nag_extract_done_app, saved.name)

        /** What a failure of [track]'s extraction tells the user. */
        fun failureMessage(context: Context, reason: ExtractFailure, track: EmbeddedTrack?): String =
            if (reason.namesFormat) context.getString(failureText(reason), track?.codecLabel.orEmpty()) else context.getString(failureText(reason))

        /** What a failure tells the user; those that [ExtractFailure.namesFormat] take the track's format. */
        fun failureText(reason: ExtractFailure): Int = when (reason) {
            ExtractFailure.NoNative -> R.string.nag_extract_fail_native
            ExtractFailure.Input -> R.string.nag_extract_fail_input
            ExtractFailure.NoTrack -> R.string.nag_extract_fail_no_track
            ExtractFailure.Picture -> R.string.nag_extract_fail_picture
            ExtractFailure.Unsupported -> R.string.nag_extract_fail_unsupported
            ExtractFailure.Empty -> R.string.nag_extract_fail_empty
            ExtractFailure.TooLarge -> R.string.nag_extract_fail_too_large
            ExtractFailure.Output -> R.string.nag_extract_fail_output
            ExtractFailure.Engine -> R.string.nag_extract_fail_engine
            ExtractFailure.Timeout -> R.string.nag_extract_fail_timeout
        }
    }
}
