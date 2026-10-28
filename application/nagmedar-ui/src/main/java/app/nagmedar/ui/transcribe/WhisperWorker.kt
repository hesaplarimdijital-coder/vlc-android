package app.nagmedar.ui.transcribe

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.ConfigurationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.nagmedar.data.DownloadState
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.lab.NativeLab
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.notify.NotificationIds
import app.nagmedar.ui.podcast.DownloadFailure
import app.nagmedar.ui.podcast.DownloadFiles
import app.nagmedar.ui.podcast.EpisodeDownloader
import app.nagmedar.ui.podcast.EpisodeTranscripts
import app.nagmedar.ui.podcast.PodcastCenter
import app.nagmedar.ui.podcast.PodcastNotifier
import app.nagmedar.ui.podcast.TranscriptLanguage
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.work.TimeLimit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import org.videolan.tools.Settings
import java.io.File
import java.text.NumberFormat
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume

/**
 * S-709, podcast transcripts: one Whisper job, for the first request of the queue ([WhisperQueueStore]). A foreground
 * job (media processing on Android 15 and later, data sync before) with an ongoing notification: the model's download,
 * the episode's audio downloaded into the cache when the episode is not downloaded (and deleted after), then how much of
 * it is transcribed, the time left, what it runs on and how fast, and Cancel. The model comes down once (resumed if it
 * was stopped); the audio is transcribed on the GPU through Vulkan where the device has one and Settings allows it,
 * else on the processor's performance cores, in the show's language ([TranscriptLanguage]). The passages are written
 * as a WebVTT file in the app's storage ([EpisodeTranscripts]) and the episode's row says so; a notification tells what
 * came of it. Then the next request's job starts. If Android stops the app, WorkManager starts the job again, from the
 * beginning; if Android's time limit for a foreground job ends it, it fails for good and says so, and the rest of the
 * queue waits for the app to be opened again. If the app stopped while the GPU worked (a driver can take it down), the
 * jobs after it run on the processor until Settings tries the GPU again.
 */
class WhisperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** When the progress was last told: once a second at most (the system drops quicker notifications). */
    @Volatile
    private var toldAt = 0L

    /** What the transcription runs on, once the model is loaded. */
    @Volatile
    private var engine: WhisperEngine? = null

    /** The show of the episode, for the notifications' tap. */
    @Volatile
    private var feedId: Long? = null

    override suspend fun doWork(): Result {
        val app = applicationContext
        val request = WhisperQueueStore.state(app).value.head ?: return Result.success()
        // a job started with less than its request needs (one asked for while another ran) leaves it to one that waits for it
        val needs = withContext(Dispatchers.IO) { WhisperJobs.needsOf(app, request) }
        if (!WhisperJobs.needsIn(inputData).covers(needs)) {
            WhisperJobs.start(app, after = true, needs = needs)
            return Result.success()
        }
        WhisperQueueStore.update(app) { it.begin(request.episodeId) }
        tell(request, WhisperStage.Waiting, force = true)
        // a foreground start can be refused (Android 12+, from the background): the job then runs as it can
        runCatching { setForeground(foregroundInfo(notification(request, null))) }
        val outcome = try {
            withContext(Dispatchers.IO) { produce(request) }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { stopped(request) }
            throw e
        } finally {
            runCatching { manager.cancel(NOTIFICATION_ID) }
        }
        when (outcome) {
            is Outcome.Saved -> {
                WhisperQueueStore.update(app) { it.remove(request.episodeId) }
                finished(request, outcome.passages, outcome.engine)
            }
            is Outcome.Failed -> {
                WhisperQueueStore.update(app) { it.failed(request.episodeId, outcome.reason) }
                failed(request, outcome.reason)
            }
        }
        // the next request's job, after this one
        WhisperJobs.start(app, after = true)
        return Result.success()
    }

    /**
     * The job stopped before its end: by Android's time limit (it fails for good), by the user (its request is out of
     * the queue: the next one starts), or by the system (it stays first, and WorkManager starts it again).
     */
    private fun stopped(request: WhisperRequest) {
        val app = applicationContext
        when {
            TimeLimit.reached(this) -> {
                WhisperQueueStore.update(app) { it.failed(request.episodeId, WhisperFailure.Timeout) }
                runCatching { WorkManager.getInstance(app).cancelWorkById(id) }
                say(app, app.getString(R.string.nag_whisper_failed_title, request.name), app.getString(failureText(WhisperFailure.Timeout)))
            }
            !WhisperQueueStore.state(app).value.has(request.episodeId) -> WhisperJobs.kick(app)
            else -> WhisperQueueStore.update(app) { it.paused(request.episodeId) }
        }
    }

    private sealed interface Outcome {
        data class Saved(val passages: Int, val engine: WhisperEngine?) : Outcome

        data class Failed(val reason: WhisperFailure) : Outcome
    }

    /** The model, the episode's audio, the transcription, the file: what came of them. */
    private suspend fun produce(request: WhisperRequest): Outcome {
        val app = applicationContext
        if (!NativeLab.available) return Outcome.Failed(WhisperFailure.NoNative)
        val podcasts = NagmedarDatabase.get(app).podcasts()
        val episode = podcasts.episode(request.episodeId) ?: return Outcome.Failed(WhisperFailure.Input)
        val feed = podcasts.feed(episode.feedId)
        feedId = episode.feedId
        val model = try {
            if (!WhisperModels.ready(app, request.model)) tell(request, WhisperStage.Downloading)
            ModelDownloader(PodcastCenter.userAgent()).download(app, request.model) { done, total ->
                tell(request, WhisperStage.Downloading, bytes = done, bytesTotal = total)
            }
        } catch (e: ModelDownloadException) {
            return Outcome.Failed(e.reason)
        }
        val audio = File(File(app.cacheDir, AUDIO_FOLDER), "${episode.id}-$id.part")
        try {
            val input = when (val opened = open(episode, request, audio)) {
                is Opened.Ready -> opened.input
                is Opened.Failed -> return Outcome.Failed(opened.reason)
            }
            return transcribed(request, episode.id, model, input, TranscriptLanguage.choose(feed?.transcriptLanguage, feed?.language))
        } finally {
            audio.delete()
            File(audio.path + PART_MARKER).delete()
        }
    }

    private sealed interface Opened {
        class Ready(val input: ParcelFileDescriptor) : Opened

        class Failed(val reason: WhisperFailure) : Opened
    }

    /**
     * The episode's audio to read: its downloaded file, else the enclosure downloaded into [audio] in the cache (what an
     * earlier job left there is removed first).
     */
    private suspend fun open(episode: PodcastEpisode, request: WhisperRequest, audio: File): Opened {
        val app = applicationContext
        val saved = episode.downloadPath?.takeIf { episode.downloadState == DownloadState.DONE }
        if (saved != null) {
            runCatching { app.contentResolver.openFileDescriptor(DownloadFiles.uriOf(saved), "r") }.getOrNull()?.let { return Opened.Ready(it) }
        }
        audio.parentFile?.listFiles()?.forEach { it.delete() }
        tell(request, WhisperStage.Fetching, force = true)
        try {
            EpisodeDownloader(PodcastCenter.userAgent()).download(episode.audioUrl, audio) { done, total ->
                tell(request, WhisperStage.Fetching, bytes = done, bytesTotal = total.coerceAtLeast(0L))
            }
        } catch (e: DownloadFailure) {
            return Opened.Failed(if (e is DownloadFailure.DiskFull) WhisperFailure.Storage else WhisperFailure.Fetch)
        }
        return runCatching { ParcelFileDescriptor.open(audio, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()?.let { Opened.Ready(it) }
            ?: Opened.Failed(WhisperFailure.Fetch)
    }

    /** The transcription of [input] in [language] ("auto" for whisper's own guess), and its file. */
    private suspend fun transcribed(request: WhisperRequest, episodeId: Long, model: File, input: ParcelFileDescriptor, language: String): Outcome {
        val app = applicationContext
        val prefs = WhisperPrefs(Settings.getInstance(app))
        tell(request, WhisperStage.Transcribing, force = true)
        val segments = Collections.synchronizedList(ArrayList<SpeechSegment>())
        var heard: String? = null
        val startedAt = SystemClock.elapsedRealtime()
        val listener = object : NativeLab.Transcription {
            override fun onProgress(doneSeconds: Double, totalSeconds: Double) {
                val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
                val eta = if (totalSeconds > 0 && doneSeconds >= ETA_AFTER_S) (elapsed * (totalSeconds - doneSeconds) / doneSeconds).toLong() else null
                // the speed so far, once there is enough of the audio to tell it by
                if (doneSeconds >= ETA_AFTER_S && elapsed > 0) engine = engine?.copy(speed = doneSeconds / elapsed)
                tell(request, WhisperStage.Transcribing, done = doneSeconds, total = totalSeconds, eta = eta)
            }

            override fun onSegment(startMs: Long, endMs: Long, text: String) {
                segments += SpeechSegment(startMs, endMs, text)
            }

            override fun onLanguage(code: String) {
                heard = code
            }

            override fun onEngine(gpu: Boolean, device: String, threads: Int) {
                // the processor runs: the GPU can no longer take the app down
                if (!gpu) prefs.gpuRunning = null
                engine = WhisperEngine(gpu, device, threads, engine?.speed, fellBack = askedGpu && !gpu)
                tell(request, WhisperStage.Transcribing, force = true)
            }
        }
        val result = runCatching { JSONObject(transcribe(model.absolutePath, input, language, prefs, listener)) }.getOrNull()
            ?: return Outcome.Failed(WhisperFailure.Engine)
        when (result.optString("error")) {
            "" -> Unit
            "open", "no_audio" -> return Outcome.Failed(WhisperFailure.Input)
            "decoder" -> return Outcome.Failed(WhisperFailure.Decoder)
            "model" -> {
                // a model that cannot be read is downloaded again next time
                WhisperModels.delete(app, request.model)
                return Outcome.Failed(WhisperFailure.Model)
            }
            else -> return Outcome.Failed(WhisperFailure.Engine)
        }
        val cues = WhisperLines.cues(segments.toList())
        if (cues.isEmpty()) return Outcome.Failed(WhisperFailure.Empty)
        val spoken = (if (language != WhisperLanguage.Auto.code) language else heard ?: result.optString("language")).takeIf { it != WhisperLanguage.Auto.code }.orEmpty()
        val bytes = WhisperLines.vtt(cues, EpisodeTranscripts.note(request.model, spoken))
        if (!EpisodeTranscripts.write(app, episodeId, bytes)) return Outcome.Failed(WhisperFailure.Output)
        // the episode's row says it has its own transcript; one gone meanwhile (its show unsubscribed) leaves nothing behind
        if (NagmedarDatabase.get(app).podcasts().setWhisperModel(episodeId, request.model.key) == 0) {
            EpisodeTranscripts.delete(app, episodeId)
            return Outcome.Failed(WhisperFailure.Input)
        }
        // how fast it went, the model's load included: seconds of the audio a second
        val audio = result.optDouble("audio_seconds")
        val took = result.optDouble("seconds").takeIf { it > 0 } ?: ((SystemClock.elapsedRealtime() - startedAt) / 1000.0)
        val ran = engine?.copy(speed = if (audio > 0 && took > 0) audio / took else null)
        return Outcome.Saved(cues.size, ran)
    }

    /** Whether the GPU was asked for this job's transcription (Settings allows it and it did not stop the app before). */
    @Volatile
    private var askedGpu = false

    /**
     * The native transcription on a thread of its own, which closes [input] when it is done; a
     * cancelled job stops it. One runs at a time: a job started right after one was cancelled
     * waits for it to stop. On the GPU when Settings allows it, unless the app stopped while the
     * GPU worked on a job before ([WhisperPrefs.gpuRunning] still there once that job is over):
     * then on the processor until Settings tries the GPU again.
     */
    private suspend fun transcribe(model: String, input: ParcelFileDescriptor, language: String, prefs: WhisperPrefs, listener: NativeLab.Transcription): String =
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

                override fun onEngine(gpu: Boolean, device: String, threads: Int) = listener.onEngine(gpu, device, threads)
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
                            if (prefs.gpuRunning != null) {
                                prefs.gpuStopped = true
                                prefs.gpuRunning = null
                            }
                            val gpu = prefs.acceleration == WhisperAcceleration.Auto && !prefs.gpuStopped
                            askedGpu = gpu
                            if (gpu) prefs.gpuRunning = id.toString()
                            try {
                                // the best audio track (-1), not into English; threads 0: the performance cores, as the native side counts them
                                NativeLab.transcribe(model, "/proc/self/fd/${input.fd}", -1, language, false, 0, gpu, watched)
                            } finally {
                                if (gpu) prefs.gpuRunning = null
                            }
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

    /**
     * The job's progress for the pages and the notification, with its episode and what it runs on; once a second at
     * most, but every change of stage and of what it runs on ([force], keeping how far it is).
     */
    private fun tell(
        request: WhisperRequest,
        stage: WhisperStage,
        done: Double = 0.0,
        total: Double = 0.0,
        bytes: Long = 0L,
        bytesTotal: Long = 0L,
        eta: Long? = null,
        force: Boolean = false,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - toldAt < TELL_EVERY_MS && stage == lastStage && !force) return
        toldAt = now
        lastStage = stage
        val last = lastTold
        val running = if (force && last != null && last.stage == stage) {
            last.copy(engine = engine)
        } else {
            WhisperStatus.Running(id.toString(), request, stage, done, total, bytes, bytesTotal, eta, engine = engine)
        }
        lastTold = running
        setProgressAsync(
            workDataOf(
                WhisperJobs.KEY_EPISODE to request.episodeId,
                WhisperJobs.KEY_STAGE to running.stage.key,
                WhisperJobs.KEY_DONE to running.doneSeconds,
                WhisperJobs.KEY_TOTAL to running.totalSeconds,
                WhisperJobs.KEY_BYTES to running.doneBytes,
                WhisperJobs.KEY_BYTES_TOTAL to running.totalBytes,
                WhisperJobs.KEY_ETA to (running.etaSeconds ?: -1L),
                *(running.engine?.toData() ?: emptyArray()),
            ),
        )
        if (stage != WhisperStage.Waiting) runCatching { manager.notify(NOTIFICATION_ID, notification(request, running)) }
    }

    /** The progress told last, for a change of what it runs on between two of its steps. */
    @Volatile
    private var lastTold: WhisperStatus.Running? = null

    @Volatile
    private var lastStage: WhisperStage? = null

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(notification(WhisperQueueStore.state(applicationContext).value.head, null))

    private fun foregroundInfo(notification: Notification): ForegroundInfo = when {
        Build.VERSION.SDK_INT >= 35 -> ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else -> ForegroundInfo(NOTIFICATION_ID, notification)
    }

    /** The ongoing notification: the episode, the stage and how far it is, the time left, what it runs on, Cancel. */
    private fun notification(request: WhisperRequest?, status: WhisperStatus.Running?): Notification {
        val context = applicationContext
        channel(context)
        val text = status?.let { progressLine(context, it) } ?: context.getString(R.string.nag_whisper_starting)
        val fraction = status?.fraction
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(context.getString(R.string.nag_whisper_running_title, request?.name.orEmpty()))
            .setContentText(text)
            .setSubText(status?.engine?.let { engineLine(context, it) })
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).toInt(), fraction == null)
        if (request != null) builder.addAction(R.drawable.nagmedar_notif_close, context.getString(R.string.nag_whisper_cancel), cancelIntent(context, request.episodeId))
        return builder.build()
    }

    /** The transcript is there: its passages, what it was made on and how fast; a tap opens the show. */
    private fun finished(request: WhisperRequest, passages: Int, ran: WhisperEngine?) {
        val context = applicationContext
        val lines = context.resources.getQuantityString(R.plurals.nag_whisper_ready, passages, passages)
        say(context, context.getString(R.string.nag_whisper_done_title, request.name), lines, ran?.let { engineLine(context, it) })
    }

    private fun failed(request: WhisperRequest, reason: WhisperFailure) {
        val context = applicationContext
        say(context, context.getString(R.string.nag_whisper_failed_title, request.name), context.getString(failureText(reason)))
    }

    /** An outcome's notification: [text], and [more] under it when it is opened. */
    @SuppressLint("MissingPermission")
    private fun say(context: Context, title: String, text: String, more: String? = null) {
        channel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (more == null) text else "$text\n$more"))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        // without the permission the episode's page says it all
        runCatching { NotificationManagerCompat.from(context).notify(OUTCOME_ID, notification) }
    }

    /** The podcasts, on the episode's show once it is known. */
    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, NagmedarActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(NAGMEDAR_EXTRA_DESTINATION, Destination.Podcasts.route)
        feedId?.let { intent.putExtra(PodcastNotifier.EXTRA_PODCAST, it) }
        return PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL = "nagmedar_whisper"
        private const val NOTIFICATION_ID = NotificationIds.WHISPER
        private const val OUTCOME_ID = NotificationIds.WHISPER_DONE
        private const val PROGRESS_MAX = 1000
        private const val TELL_EVERY_MS = 1000L
        private const val THREAD = "nagmedar-whisper"

        /** The cache's folder of the audio a job downloads for itself. */
        private const val AUDIO_FOLDER = "whisper-audio"

        /** What [EpisodeDownloader] keeps beside a partial file. */
        private const val PART_MARKER = ".etag"

        /** The time left is told once this much of the audio is done: before, it is a guess. */
        private const val ETA_AFTER_S = 60.0
        private const val CANCELLED_JSON = """{"error":"cancelled"}"""
        private const val ERROR_JSON = """{"error":"native"}"""

        internal const val ACTION_CANCEL = "app.nagmedar.ui.transcribe.CANCEL"
        internal const val EXTRA_EPISODE = "episode"

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

        /** The notification's Cancel: the job of [episodeId] taken out of the queue ([WhisperCancelReceiver]). */
        private fun cancelIntent(context: Context, episodeId: Long): PendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            Intent(context, WhisperCancelReceiver::class.java).setAction(ACTION_CANCEL).setPackage(context.packageName).putExtra(EXTRA_EPISODE, episodeId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** "Model: 54 / 181 MB", "Episode: 12 / 58 MB", "%45 · about 12 min left": the line under a job's name. */
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
                WhisperStage.Fetching -> if (status.totalBytes > 0) {
                    context.getString(R.string.nag_whisper_fetching, megabytes(status.doneBytes), megabytes(status.totalBytes))
                } else {
                    context.getString(R.string.nag_whisper_fetching_unknown, megabytes(status.doneBytes))
                }
                WhisperStage.Transcribing -> {
                    val fraction = status.fraction ?: return context.getString(R.string.nag_whisper_transcribing)
                    val percent = NumberFormat.getPercentInstance(locale).format(fraction.toDouble())
                    val eta = status.etaSeconds
                    if (eta == null) percent else context.getString(R.string.nag_whisper_eta, percent, durationText(context, eta))
                }
            }
        }

        /**
         * "GPU (Vulkan) · gerçek zamanın 4,2 katı", "İşlemci · 4 çekirdek (GPU kullanılamadı)":
         * what a job runs on, and its speed as a multiple of real time once it can be told.
         */
        fun engineLine(context: Context, engine: WhisperEngine): String {
            val res = context.resources
            val locale = ConfigurationCompat.getLocales(res.configuration)[0] ?: Locale.getDefault()
            val where = if (engine.gpu) {
                context.getString(R.string.nag_whisper_engine_gpu)
            } else {
                res.getQuantityString(R.plurals.nag_whisper_engine_cpu, engine.threads, engine.threads)
            }
            val named = if (engine.fellBack) context.getString(R.string.nag_whisper_engine_fallback, where) else where
            val speed = engine.speed ?: return named
            val factor = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = if (speed < SPEED_DECIMALS_BELOW) 1 else 0 }.format(speed)
            return context.getString(R.string.nag_whisper_engine_speed, named, factor)
        }

        /** A speed below ten is told with a decimal: "1,8", "12". */
        private const val SPEED_DECIMALS_BELOW = 10.0

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
            WhisperFailure.Fetch -> R.string.nag_whisper_fail_fetch
            WhisperFailure.Decoder -> R.string.nag_whisper_fail_decoder
            WhisperFailure.Model -> R.string.nag_whisper_fail_model
            WhisperFailure.Engine -> R.string.nag_whisper_fail_engine
            WhisperFailure.Empty -> R.string.nag_whisper_fail_empty
            WhisperFailure.Output -> R.string.nag_whisper_fail_output
            WhisperFailure.Timeout -> R.string.nag_whisper_fail_timeout
        }
    }
}

/** The job notification's Cancel: its episode's job taken out of the queue and stopped; only the app's own intent reaches it. */
class WhisperCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WhisperWorker.ACTION_CANCEL) return
        val episode = intent.getLongExtra(WhisperWorker.EXTRA_EPISODE, -1L)
        if (episode >= 0) WhisperJobs.cancel(context, episode)
    }
}
