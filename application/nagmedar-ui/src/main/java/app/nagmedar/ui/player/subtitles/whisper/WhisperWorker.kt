package app.nagmedar.ui.player.subtitles.whisper

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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.ConfigurationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.nagmedar.lab.NativeLab
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.player.subtitles.SavedSubtitle
import app.nagmedar.ui.player.subtitles.saveSubtitle
import app.nagmedar.ui.podcast.OkHttpFeedSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig as VlcBuildConfig
import org.videolan.vlc.repository.SlaveRepository
import java.text.NumberFormat
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume

/**
 * S-709: one job of the subtitles from speech. A foreground job (media processing on Android 15
 * and later, data sync before) with an ongoing notification: the model's download, then how much
 * of the film is transcribed and the time left, and Cancel. The model comes down once (resumed if
 * it was stopped), the film's audio is transcribed on the performance cores, and the lines are
 * written as a new SRT file beside the film, or in the app's folder where that cannot be written;
 * the film keeps it, so it is among its subtitles when it plays next. A notification says what
 * came of it. If Android stops the app, WorkManager starts the job again, from the beginning; if
 * Android's time limit for a foreground job ends it, it is stopped for good and says so.
 */
class WhisperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** When the progress was last told: once a second at most (the system drops quicker notifications). */
    @Volatile
    private var toldAt = 0L

    override suspend fun doWork(): Result {
        val request = WhisperRequest.parse(inputData.getString(WhisperJobs.KEY_REQUEST)) ?: return Result.failure()
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
                finished(request, outcome.saved)
                Result.success(
                    workDataOf(
                        WhisperJobs.KEY_URI to outcome.saved.uri,
                        WhisperJobs.KEY_NAME to outcome.saved.name,
                        WhisperJobs.KEY_BESIDE to outcome.saved.beside,
                        WhisperJobs.KEY_LINES to outcome.lines,
                        WhisperJobs.KEY_LANGUAGE to outcome.language,
                    ),
                )
            }
            is Outcome.Failed -> {
                failed(request, outcome.reason)
                Result.failure(workDataOf(WhisperJobs.KEY_REASON to outcome.reason.key, WhisperJobs.KEY_DETAIL to outcome.detail.take(DETAIL_CHARS)))
            }
        }
    }

    private sealed interface Outcome {
        data class Saved(val saved: SavedSubtitle, val lines: Int, val language: String) : Outcome

        data class Failed(val reason: WhisperFailure, val detail: String = "") : Outcome
    }

    /** The model, the transcription, the file: what came of them. */
    private suspend fun produce(request: WhisperRequest): Outcome {
        val app = applicationContext
        if (!NativeLab.available) return Outcome.Failed(WhisperFailure.NoNative)
        val model = try {
            if (!WhisperModels.ready(app, request.model)) tell(request, WhisperStage.Downloading)
            ModelDownloader(OkHttpFeedSource.userAgent(VlcBuildConfig.VLC_VERSION_NAME)).download(app, request.model) { done, total ->
                tell(request, WhisperStage.Downloading, bytes = done, bytesTotal = total)
            }
        } catch (e: ModelDownloadException) {
            return Outcome.Failed(e.reason, e.message.orEmpty())
        }
        val film = Uri.parse(request.film)
        val input = runCatching { app.contentResolver.openFileDescriptor(film, "r") }.getOrNull()
            ?: return Outcome.Failed(WhisperFailure.Input)
        tell(request, WhisperStage.Transcribing)
        val segments = Collections.synchronizedList(ArrayList<SpeechSegment>())
        var heard: String? = null
        val startedAt = SystemClock.elapsedRealtime()
        val listener = object : NativeLab.Transcription {
            override fun onProgress(doneSeconds: Double, totalSeconds: Double) {
                val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
                val eta = if (totalSeconds > 0 && doneSeconds >= ETA_AFTER_S) (elapsed * (totalSeconds - doneSeconds) / doneSeconds).toLong() else null
                tell(request, WhisperStage.Transcribing, done = doneSeconds, total = totalSeconds, eta = eta)
            }

            override fun onSegment(startMs: Long, endMs: Long, text: String) {
                segments += SpeechSegment(startMs, endMs, text)
            }

            override fun onLanguage(code: String) {
                heard = code
            }
        }
        val result = runCatching { JSONObject(transcribe(model.absolutePath, input, request, listener)) }.getOrNull()
            ?: return Outcome.Failed(WhisperFailure.Engine)
        when (val error = result.optString("error")) {
            "" -> Unit
            "open" -> return Outcome.Failed(WhisperFailure.Input, result.optString("detail"))
            "no_audio" -> return Outcome.Failed(WhisperFailure.NoAudio, result.optString("detail"))
            "decoder" -> return Outcome.Failed(WhisperFailure.Decoder, result.optString("detail"))
            "model" -> {
                // a model that cannot be read is downloaded again next time
                WhisperModels.delete(app, request.model)
                return Outcome.Failed(WhisperFailure.Model, result.optString("detail"))
            }
            else -> return Outcome.Failed(WhisperFailure.Engine, error + " " + result.optString("detail"))
        }
        val cues = WhisperLines.cues(segments.toList())
        if (cues.isEmpty()) return Outcome.Failed(WhisperFailure.Empty)
        val language = when {
            request.translate -> WhisperLanguage.English.code
            request.language != WhisperLanguage.Auto -> request.language.code
            else -> heard ?: result.optString("language").takeIf { it != WhisperLanguage.Auto.code }.orEmpty()
        }
        val name = WhisperLines.fileName(request.name, language.ifEmpty { null })
        val saved = saveSubtitle(app, film, name, WhisperLines.srt(cues)) ?: return Outcome.Failed(WhisperFailure.Output)
        // the film keeps it: among its subtitles when it plays next, wherever the file is
        if (!saved.beside || film.scheme == ContentResolver.SCHEME_CONTENT) {
            SlaveRepository.getInstance(app).saveSlave(request.film, IMedia.Slave.Type.Subtitle, KEPT_PRIORITY, saved.uri)
        }
        return Outcome.Saved(saved, cues.size, language)
    }

    /**
     * The native transcription on a thread of its own, which closes [input] when it is done; a
     * cancelled job stops it. One runs at a time: a job started right after one was cancelled
     * waits for it to stop.
     */
    private suspend fun transcribe(model: String, input: ParcelFileDescriptor, request: WhisperRequest, listener: NativeLab.Transcription): String =
        suspendCancellableCoroutine { continuation ->
            val cancelled = AtomicBoolean(false)
            val watched = object : NativeLab.Transcription {
                override fun onProgress(doneSeconds: Double, totalSeconds: Double) {
                    // a cancel that came before the transcription began
                    if (cancelled.get()) NativeLab.cancelTranscription()
                    listener.onProgress(doneSeconds, totalSeconds)
                }

                override fun onSegment(startMs: Long, endMs: Long, text: String) = listener.onSegment(startMs, endMs, text)

                override fun onLanguage(code: String) = listener.onLanguage(code)
            }
            continuation.invokeOnCancellation {
                cancelled.set(true)
                NativeLab.cancelTranscription()
            }
            Thread({
                val json = try {
                    nativeLock.withLock {
                        if (cancelled.get()) {
                            CANCELLED_JSON
                        } else {
                            NativeLab.transcribe(model, "/proc/self/fd/${input.fd}", request.audioTrack, request.language.code, request.translate, PerformanceCores.count(), watched)
                        }
                    }
                } catch (e: Throwable) {
                    // the library failed to load or link: said as the engine's failure
                    ERROR_JSON
                } finally {
                    runCatching { input.close() }
                }
                // a cancelled job's continuation takes no result
                if (continuation.isActive) continuation.resume(json)
            }, THREAD).start()
        }

    /** The job's progress for the windows and the notification; once a second at most, but every change of stage. */
    private fun tell(request: WhisperRequest, stage: WhisperStage, done: Double = 0.0, total: Double = 0.0, bytes: Long = 0L, bytesTotal: Long = 0L, eta: Long? = null) {
        val now = SystemClock.elapsedRealtime()
        if (now - toldAt < TELL_EVERY_MS && stage == lastStage) return
        toldAt = now
        lastStage = stage
        setProgressAsync(
            workDataOf(
                WhisperJobs.KEY_STAGE to stage.key,
                WhisperJobs.KEY_DONE to done,
                WhisperJobs.KEY_TOTAL to total,
                WhisperJobs.KEY_BYTES to bytes,
                WhisperJobs.KEY_BYTES_TOTAL to bytesTotal,
                WhisperJobs.KEY_ETA to (eta ?: -1L),
            ),
        )
        val running = WhisperStatus.Running(id.toString(), request, stage, done, total, bytes, bytesTotal, eta)
        runCatching { manager.notify(NOTIFICATION_ID, notification(request, running)) }
    }

    @Volatile
    private var lastStage: WhisperStage? = null

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(notification(WhisperRequest.parse(inputData.getString(WhisperJobs.KEY_REQUEST)), null))

    private fun foregroundInfo(notification: Notification): ForegroundInfo = when {
        Build.VERSION.SDK_INT >= 35 -> ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else -> ForegroundInfo(NOTIFICATION_ID, notification)
    }

    /** The ongoing notification: the film, the stage and how far it is, the time left, Cancel. */
    private fun notification(request: WhisperRequest?, status: WhisperStatus.Running?): Notification {
        val context = applicationContext
        channel(context)
        val text = status?.let { progressLine(context, it) } ?: context.getString(R.string.nag_whisper_starting)
        val fraction = status?.fraction
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(context.getString(R.string.nag_whisper_running_title, request?.name.orEmpty()))
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).toInt(), fraction == null)
            .addAction(R.drawable.nagmedar_notif_close, context.getString(R.string.nag_whisper_cancel), WorkManager.getInstance(context).createCancelPendingIntent(id))
            .build()
    }

    /** The subtitles are there: their name and where; a tap opens the app. */
    @SuppressLint("MissingPermission")
    private fun finished(request: WhisperRequest, saved: SavedSubtitle) {
        val context = applicationContext
        val where = context.getString(if (saved.beside) R.string.nag_whisper_saved_beside else R.string.nag_whisper_saved_app, saved.name)
        say(context, context.getString(R.string.nag_whisper_done_title, request.name), where)
    }

    @SuppressLint("MissingPermission")
    private fun failed(request: WhisperRequest, reason: WhisperFailure) {
        val context = applicationContext
        say(context, context.getString(R.string.nag_whisper_failed_title, request.name), context.getString(failureText(reason)))
    }

    /** Android's time limit for a foreground job ended it: it is not taken up again (it would start from the beginning), and that is said. */
    private fun timedOut(request: WhisperRequest) {
        val context = applicationContext
        WhisperPrefs(Settings.getInstance(context)).timedOut = id.toString()
        runCatching { WorkManager.getInstance(context).cancelWorkById(id) }
        say(context, context.getString(R.string.nag_whisper_failed_title, request.name), context.getString(failureText(WhisperFailure.Timeout)))
    }

    @SuppressLint("MissingPermission")
    private fun say(context: Context, title: String, text: String) {
        channel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        // without the permission the app's windows say it all
        runCatching { NotificationManagerCompat.from(context).notify(OUTCOME_ID, notification) }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        Intent(context, NagmedarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL = "nagmedar_whisper"
        private const val NOTIFICATION_ID = 709
        private const val OUTCOME_ID = 7090
        private const val PROGRESS_MAX = 1000
        private const val TELL_EVERY_MS = 1000L
        private const val DETAIL_CHARS = 300
        private const val THREAD = "nagmedar-whisper"

        /** The time left is told once this much of the film is done: before, it is a guess. */
        private const val ETA_AFTER_S = 60.0

        /** The priority the classic player keeps a subtitle the user chose with (S-701). */
        private const val KEPT_PRIORITY = 2
        private const val CANCELLED_JSON = """{"error":"cancelled"}"""
        private const val ERROR_JSON = """{"error":"native"}"""

        /** One transcription at a time in the process: the native side has one cancel flag. */
        private val nativeLock = ReentrantLock()

        private fun channel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.nag_whisper_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            )
        }

        /** "Model: 54 / 181 MB", "%45 · about 12 min left": the line under a job's name. */
        fun progressLine(context: Context, status: WhisperStatus.Running): String {
            val res = context.resources
            val locale = ConfigurationCompat.getLocales(res.configuration)[0] ?: Locale.getDefault()
            return when (status.stage) {
                WhisperStage.Waiting -> context.getString(if (status.restarted) R.string.nag_whisper_restarted else R.string.nag_whisper_starting)
                WhisperStage.Downloading -> if (status.totalBytes > 0) {
                    context.getString(R.string.nag_whisper_downloading, megabytes(status.doneBytes), megabytes(status.totalBytes))
                } else {
                    context.getString(R.string.nag_whisper_downloading_unknown, megabytes(status.doneBytes))
                }
                WhisperStage.Transcribing -> {
                    val fraction = status.fraction ?: return context.getString(R.string.nag_whisper_transcribing)
                    val percent = NumberFormat.getPercentInstance(locale).format(fraction.toDouble())
                    val eta = status.etaSeconds
                    if (eta == null) percent else context.getString(R.string.nag_whisper_eta, percent, durationText(context, eta))
                }
            }
        }

        /** "1 h 5 min", "12 min": a time left, a minute at least. */
        fun durationText(context: Context, seconds: Long): String {
            val minutes = ((seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).coerceAtLeast(1)
            return if (minutes >= MINUTES_PER_HOUR) {
                context.getString(R.string.nag_duration_hours_minutes, (minutes / MINUTES_PER_HOUR).toInt(), (minutes % MINUTES_PER_HOUR).toInt())
            } else {
                context.getString(R.string.nag_duration_minutes, minutes.toInt())
            }
        }

        private const val SECONDS_PER_MINUTE = 60L
        private const val MINUTES_PER_HOUR = 60L

        /** What a failure tells the user. */
        fun failureText(reason: WhisperFailure): Int = when (reason) {
            WhisperFailure.NoNative -> R.string.nag_whisper_fail_native
            WhisperFailure.Offline -> R.string.nag_whisper_fail_offline
            WhisperFailure.Server -> R.string.nag_whisper_fail_server
            WhisperFailure.Checksum -> R.string.nag_whisper_fail_checksum
            WhisperFailure.Storage -> R.string.nag_whisper_fail_storage
            WhisperFailure.Input -> R.string.nag_whisper_fail_input
            WhisperFailure.NoAudio -> R.string.nag_whisper_fail_no_audio
            WhisperFailure.Decoder -> R.string.nag_whisper_fail_decoder
            WhisperFailure.Model -> R.string.nag_whisper_fail_model
            WhisperFailure.Engine -> R.string.nag_whisper_fail_engine
            WhisperFailure.Empty -> R.string.nag_whisper_fail_empty
            WhisperFailure.Output -> R.string.nag_whisper_fail_output
            WhisperFailure.Timeout -> R.string.nag_whisper_fail_timeout
        }
    }
}
