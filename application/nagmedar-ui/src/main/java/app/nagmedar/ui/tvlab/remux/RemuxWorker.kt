package app.nagmedar.ui.tvlab.remux

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.RemuxJobEntry
import app.nagmedar.data.RemuxJobState
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.convert.AndroidDeviceState
import app.nagmedar.ui.jobs.jobForegroundInfo
import app.nagmedar.ui.jobs.stoppedForForegroundTime
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.tvlab.percentLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import kotlin.coroutines.coroutineContext

/**
 * The queue's worker (S-1206): a foreground job (media processing on Android 15 and later,
 * data sync before) with an ongoing notification that shows the job, its progress and a
 * Cancel; WorkManager keeps it through a restart. One worker at a time: a new one is appended
 * at every [ensure], and finds the queue empty when the running one drained it. When the
 * system lets no foreground job start (the app in the background on Android 12 and later) the
 * job stays queued, marked so, until the app shows and [RemuxQueue.resume] asks again; when the
 * foreground time runs out (six hours a day of media processing) the job goes back to the queue
 * with that reason and the user is told.
 */
class RemuxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val dao = NagmedarDatabase.get(applicationContext).remuxJobs()
        val engine = NativeRemuxEngine(applicationContext)
        val runner = RemuxRunner(engine, dao, AndroidDeviceState(applicationContext)) { job -> show(job) }
        withContext(Dispatchers.IO) {
            val scope = coroutineContext
            // one runner at a time: a worker the system stopped finishes its step (the engine sees the stop) before the next one starts
            synchronized(RUN_LOCK) {
                runner.cleanUp()
                runner.recoverInterrupted()
                val first = dao.next() ?: return@synchronized
                // a foreground start can be refused (Android 12+, from the background): a job that ran as a plain
                // background job would be stopped after ten minutes and start over, so it waits for the app instead
                val foreground = runCatching { setForegroundAsync(foregroundInfo(notification(first))).get() }.isSuccess
                if (!foreground) {
                    runner.waitForApp(first)
                    return@synchronized
                }
                var stoppedFor: String? = null
                runner.drain { if (scope.isActive) null else interruptionReason().also { stoppedFor = it } }
                runCatching { manager.cancel(NOTIFICATION_ID) }
                // Android 15's six hours a day of a foreground job: the job went back to the queue; said, so that the wait is not a mystery
                if (stoppedFor == RemuxEvent.FOREGROUND_TIMEOUT) timedOut()
            }
        }
        return Result.success()
    }

    /** Why the worker was stopped: its foreground time ran out, or the system took it for another reason. */
    private fun interruptionReason(): String = if (stoppedForForegroundTime()) RemuxEvent.FOREGROUND_TIMEOUT else RemuxEvent.WORKER_STOPPED

    /** The job went back to the queue for the system's time limit: it starts again from the beginning when Nağmedar is opened. */
    @SuppressLint("MissingPermission")
    private fun timedOut() {
        val context = applicationContext
        channel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(org.videolan.resources.R.drawable.ic_notif_scan)
            .setContentTitle(context.getString(R.string.nag_tvlab_remux_queue))
            .setContentText(context.getString(R.string.nag_tvlab_remux_timeout))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.nag_tvlab_remux_timeout)))
            .setContentIntent(openTvLab(context))
            .setAutoCancel(true)
            .build()
        // without the permission the TV Lab's card says it all
        runCatching { NotificationManagerCompat.from(context).notify(TIMEOUT_NOTIFICATION_ID, notification) }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val dao = NagmedarDatabase.get(applicationContext).remuxJobs()
        val job = withContext(Dispatchers.IO) { dao.next() }
        return foregroundInfo(notification(job))
    }

    private fun foregroundInfo(notification: Notification): ForegroundInfo = jobForegroundInfo(NOTIFICATION_ID, notification)

    private fun show(job: RemuxJobEntry) {
        if (job.state in RemuxJobState.FINAL) return
        runCatching { manager.notify(NOTIFICATION_ID, notification(job)) }
    }

    /** The ongoing notification: the film, the state and how far it got, a Cancel. */
    private fun notification(job: RemuxJobEntry?): Notification {
        val context = applicationContext
        channel(context)
        val locale = context.resources.locale()
        val percent = percentLabel(job?.progress ?: 0f, locale)
        val text = when (job?.state) {
            RemuxJobState.RUNNING -> context.getString(R.string.nag_tvlab_remux_running, percent)
            RemuxJobState.VERIFYING -> context.getString(R.string.nag_tvlab_remux_verifying, percent)
            RemuxJobState.PAUSED -> context.getString(R.string.nag_jobs_paused)
            else -> context.getString(R.string.nag_jobs_queued)
        }
        val open = openTvLab(context)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(org.videolan.resources.R.drawable.ic_notif_scan)
            .setContentTitle(job?.inputName ?: context.getString(R.string.nag_tvlab_remux_queue))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (job != null) {
            val indeterminate = job.state == RemuxJobState.QUEUED || (job.state == RemuxJobState.RUNNING && job.totalSeconds <= 0)
            builder.setProgress(PROGRESS_MAX, (job.progress * PROGRESS_MAX).toInt(), indeterminate)
            val cancel = PendingIntent.getBroadcast(
                context,
                job.id.toInt(),
                Intent(context, RemuxCancelReceiver::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_JOB, job.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(org.videolan.resources.R.drawable.ic_widget_close_w, context.getString(R.string.nag_jobs_cancel), cancel)
        }
        return builder.build()
    }

    companion object {
        const val NAME = "nagmedar-remux"
        const val CHANNEL = "nagmedar_remux"
        const val ACTION_CANCEL = "app.nagmedar.ui.tvlab.remux.CANCEL"
        const val EXTRA_JOB = "job"
        private const val NOTIFICATION_ID = 1206
        private const val TIMEOUT_NOTIFICATION_ID = 12061
        private const val PROGRESS_MAX = 1000
        private val RUN_LOCK = Any()

        private fun channel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.nag_tvlab_remux_queue), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            )
        }

        private fun openTvLab(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, NagmedarActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(NAGMEDAR_EXTRA_DESTINATION, Destination.TvLab.route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /**
         * A worker for the queue. [append] (a job just queued) puts it after the one running, if
         * any, so that nothing queued is missed; without it (the app showing again) one already
         * running or waiting is kept.
         */
        fun ensure(context: Context, append: Boolean = true) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                NAME,
                if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RemuxWorker>().build(),
            )
        }
    }
}

/** The notification's Cancel: the job is asked to stop; only the app's own intent reaches it. */
class RemuxCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RemuxWorker.ACTION_CANCEL) return
        val id = intent.getLongExtra(RemuxWorker.EXTRA_JOB, -1L)
        if (id < 0) return
        val pending = goAsync()
        Thread {
            try {
                RemuxQueue.get(context.applicationContext).cancelNow(id)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
