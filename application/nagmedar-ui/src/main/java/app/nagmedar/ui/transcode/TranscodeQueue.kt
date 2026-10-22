package app.nagmedar.ui.transcode

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.TranscodeJobEntry
import app.nagmedar.data.TranscodeJobError
import app.nagmedar.data.TranscodeJobState
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.convert.AndroidDeviceState
import app.nagmedar.ui.convert.ConvertPolicy
import app.nagmedar.ui.jobs.jobForegroundInfo
import app.nagmedar.ui.jobs.stoppedForForegroundTime
import app.nagmedar.ui.notify.NotificationIds
import app.nagmedar.ui.shell.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION

/**
 * S-1303: the video conversions' queue: the jobs in Nağmedar's database, run one at a time by
 * [TranscodeWorker] (a unique WorkManager chain). Blocking calls: a worker's or IO's thread.
 */
object TranscodeQueue {
    /** What [enqueue] answers when the new file is the source itself. */
    const val REFUSED = -1L

    private const val WORK = "nagmedar-transcode"

    private fun dao(context: Context) = NagmedarDatabase.get(context.applicationContext).transcodeJobs()

    /** Every job, the newest first. */
    fun observe(context: Context): Flow<List<TranscodeJobEntry>> = dao(context).observe()

    /** Queues a conversion of [input] with [profile] into [output]; the job's id, or [REFUSED] when [output] is [input]. */
    fun enqueue(context: Context, input: Uri, inputName: String, inputSize: Long, output: Uri, outputName: String, profile: TranscodeProfile): Long {
        if (input == output) return REFUSED
        val id = dao(context).insert(
            TranscodeJobEntry(
                inputUri = input.toString(),
                inputName = inputName,
                inputSize = inputSize,
                outputUri = output.toString(),
                outputName = outputName,
                profile = profile.toJson(),
                createdAt = System.currentTimeMillis(),
            ),
        )
        ensure(context)
        return id
    }

    /**
     * Stops job [id]: one still waiting is cancelled at once and its new document removed while it
     * is the job's own or still empty (the picker's); a running one is asked to stop and does so at
     * its next step, its unfinished document removed.
     */
    fun cancel(context: Context, id: Long) {
        val dao = dao(context)
        val job = dao.get(id) ?: return
        if (dao.cancelQueued(id, System.currentTimeMillis()) == 1) {
            val files = DeviceTranscodeFiles(context)
            val output = Uri.parse(job.outputUri)
            if (job.inputUri != job.outputUri && (job.ownsOutput || files.outputEmpty(output) == true)) files.removeOutput(output)
        } else {
            dao.requestCancel(id)
        }
    }

    /** A finished job's row put away; the files stay. */
    fun remove(context: Context, id: Long) {
        dao(context).deleteFinished(id)
    }

    /** Jobs left waiting (by a process that died, or by Android's time limit) go on: the app is in front now. */
    fun resume(context: Context) {
        if (dao(context).pending() > 0) ensure(context)
    }

    /** A worker after the one running, if any: it takes up what waits then. */
    private fun ensure(context: Context) {
        val app = context.applicationContext
        // WorkManager starts with the app; without it (a test) nothing runs
        val work = runCatching { WorkManager.getInstance(app) }.getOrNull() ?: return
        work.enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<TranscodeWorker>().addTag(WORK).build())
    }
}

/**
 * S-1303: the conversions' worker: a foreground job (media processing on Android 15 and later,
 * data sync before) with a notification of the job under way and its Cancel; it runs every
 * waiting job in turn. Stopped by the system (Android 15's time limit, say), the job under way
 * goes back to the queue and starts over when the app is opened.
 */
class TranscodeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val app = applicationContext
        // a foreground start can be refused (Android 12 and later, from the background): the job then runs as it can
        runCatching { setForeground(foregroundInfo(notification(null))) }
        // one runner at a time: a worker stopped by the system finishes its step before the next one starts
        withContext(Dispatchers.IO) { synchronized(RUN_LOCK) { drain(app) } }
        runCatching { manager.cancel(NOTIFICATION_ID) }
        return Result.success()
    }

    private fun drain(app: Context) {
        val dao = NagmedarDatabase.get(app).transcodeJobs()
        val runner = TranscodeRunner(LibVlcSoutEngine(app), DeviceTranscodeFiles(app), dao, AndroidDeviceState(app), onUpdate = { show(it) })
        runner.recoverInterrupted()
        runner.drain {
            when {
                !isStopped -> null
                stoppedForForegroundTime() -> TranscodeJobError.TIMEOUT
                else -> STOPPED
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(notification(null))

    private fun foregroundInfo(notification: Notification): ForegroundInfo = jobForegroundInfo(NOTIFICATION_ID, notification)

    /** When the notification was last changed: once a second at most (the system drops quicker updates). */
    private var shownAt = 0L

    private fun show(job: TranscodeJobEntry) {
        if (job.state != TranscodeJobState.RUNNING && job.state != TranscodeJobState.PAUSED) return
        val now = SystemClock.elapsedRealtime()
        if (now - shownAt < NOTIFY_EVERY_MS && job.progress > 0f) return
        shownAt = now
        runCatching { manager.notify(NOTIFICATION_ID, notification(job)) }
    }

    /** The ongoing notification: the job under way, how far it got, a Cancel. */
    private fun notification(job: TranscodeJobEntry?): Notification {
        val context = applicationContext
        channel(context)
        val text = when {
            job == null -> context.getString(R.string.nag_jobs_starting)
            job.state == TranscodeJobState.PAUSED && job.error == TranscodeJobError.BATTERY ->
                context.getString(R.string.nag_jobs_paused_battery, ConvertPolicy.RESUME_ABOVE)
            job.state == TranscodeJobState.PAUSED -> context.getString(R.string.nag_jobs_paused_thermal)
            else -> job.outputName
        }
        // the tap's own PendingIntent (NotificationIds): another notification's extras never replace its page
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, NagmedarActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(NAGMEDAR_EXTRA_DESTINATION, Destination.Convert.route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(org.videolan.resources.R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(context.getString(R.string.nag_conv_running_badge))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(PROGRESS_MAX, ((job?.progress ?: 0f) * PROGRESS_MAX).toInt(), job == null)
        if (job != null) {
            val cancel = PendingIntent.getBroadcast(
                context,
                job.id.toInt(),
                Intent(context, TranscodeCancelReceiver::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_JOB, job.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(org.videolan.resources.R.drawable.nagmedar_notif_close, context.getString(R.string.nag_jobs_cancel), cancel)
        }
        return builder.build()
    }

    companion object {
        const val CHANNEL = "nagmedar_transcode"
        const val ACTION_CANCEL = "app.nagmedar.ui.transcode.CANCEL"
        const val EXTRA_JOB = "job"
        private const val STOPPED = "stopped"
        private const val NOTIFICATION_ID = NotificationIds.TRANSCODE
        private const val PROGRESS_MAX = 1000
        private const val NOTIFY_EVERY_MS = 1000L
        private val RUN_LOCK = Any()

        private fun channel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.nag_jobs_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            )
        }
    }
}

/** S-1303: the notification's Cancel: the job is asked to stop; only the app's own intent reaches it. */
class TranscodeCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TranscodeWorker.ACTION_CANCEL) return
        val id = intent.getLongExtra(TranscodeWorker.EXTRA_JOB, -1L).takeIf { it > 0 } ?: return
        val pending = goAsync()
        Thread {
            try {
                TranscodeQueue.cancel(context, id)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
