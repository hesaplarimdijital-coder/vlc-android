package app.nagmedar.ui.tvlab.remux

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.RemuxJobEntry
import app.nagmedar.data.RemuxJobError
import app.nagmedar.data.RemuxJobState
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.convert.AndroidDeviceState
import app.nagmedar.ui.jobs.jobForegroundInfo
import app.nagmedar.ui.jobs.stoppedForForegroundTime
import app.nagmedar.ui.notify.NotificationIds
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.tvlab.percentLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * The queue's worker (S-1206): a foreground job (media processing on Android 15 and later,
 * data sync before) with an ongoing notification that shows the job, its progress and a
 * Cancel; WorkManager keeps it through a restart. One worker at a time: a new one is appended
 * at every [ensure], and finds the queue empty when the running one drained it. It asks for the
 * foreground first, before anything else touches a drive, while a start from the night run's
 * alarm still lets it (S-1210); a night run that starts only once the device charges then waits
 * for the charger in the foreground. When the system lets no foreground job start (the app in
 * the background on Android 12 and later) the job stays queued, marked so, until the app shows
 * and [RemuxQueue.resume] asks again; when the foreground time runs out (six hours a day of
 * media processing) the job goes back to the queue with that reason and the user is told.
 */
class RemuxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val control = RemuxQueueControl.get(applicationContext)

    override suspend fun doWork(): Result {
        val dao = NagmedarDatabase.get(applicationContext).remuxJobs()
        val engine = NativeRemuxEngine(applicationContext)
        val charger = ChargerWatch(applicationContext, control)
        val runner = RemuxRunner(
            engine,
            dao,
            AndroidDeviceState(applicationContext),
            held = { control.current.held },
            waits = { control.current.waitsForNight(System.currentTimeMillis()) },
            charger = charger,
        ) { job -> show(job) }
        withContext(Dispatchers.IO) {
            val scope = coroutineContext
            // one runner at a time: a worker the system stopped finishes its step (the engine sees the stop) before the next one starts
            synchronized(RUN_LOCK) {
                try {
                    // the foreground first: an alarm's start lets it for seconds only, which removing what an earlier job left
                    // on a drive just woken up could take; a worker that starts no job stays a background one
                    val starts = !runner.stopped() && (dao.next() != null || dao.interrupted().isNotEmpty())
                    var foreground = starts && goForeground(dao.next())
                    runner.cleanUp()
                    runner.recoverInterrupted()
                    // S-1210: a queue paused or waiting for its night run starts nothing; Resume or the alarm asks again
                    if (runner.stopped()) return@synchronized
                    // nothing waits: a night run still waiting for the charger is over
                    val first = dao.next() ?: return@synchronized runner.endNight()
                    // a job queued meanwhile, by the app on show: asked for it now
                    if (!starts) foreground = goForeground(first)
                    // a foreground start can be refused (Android 12+, from the background): a job that ran as a plain
                    // background job would be stopped after ten minutes and start over, so it waits for the app instead
                    if (!foreground) {
                        runner.waitForApp(first)
                        return@synchronized
                    }
                    var stoppedFor: String? = null
                    runner.drain { if (scope.isActive) null else interruptionReason().also { stoppedFor = it } }
                    runCatching { manager.cancel(NOTIFICATION_ID) }
                    // Android 15's six hours a day of a foreground job: the job went back to the queue; said, so that the wait is not a mystery
                    if (stoppedFor == RemuxEvent.FOREGROUND_TIMEOUT) timedOut()
                } finally {
                    charger.close()
                }
            }
        }
        return Result.success()
    }

    /** The worker in the foreground with [job]'s notification; whether the system let it. Blocking. */
    private fun goForeground(job: RemuxJobEntry?): Boolean = runCatching { setForegroundAsync(foregroundInfo(notification(job))).get() }.isSuccess

    /** Why the worker was stopped: its foreground time ran out, or the system took it for another reason. */
    private fun interruptionReason(): String = if (stoppedForForegroundTime()) RemuxEvent.FOREGROUND_TIMEOUT else RemuxEvent.WORKER_STOPPED

    /** The job went back to the queue for the system's time limit: it starts again from the beginning when Nağmedar is opened. */
    @SuppressLint("MissingPermission")
    private fun timedOut() {
        val context = applicationContext
        channel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(org.videolan.resources.R.drawable.ic_nagmedar_mark_mono)
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
            RemuxJobState.PAUSED -> context.getString(if (job.error == RemuxJobError.HELD) R.string.nag_tvlab_queue_held else R.string.nag_jobs_paused)
            // S-1210: the night run, its time come, waits for the charger
            else -> context.getString(if (control.current.waitsForCharger(System.currentTimeMillis())) R.string.nag_tvlab_queue_charger_short else R.string.nag_jobs_queued)
        }
        val open = openTvLab(context)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(org.videolan.resources.R.drawable.ic_nagmedar_mark_mono)
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
            builder.addAction(org.videolan.resources.R.drawable.nagmedar_notif_close, context.getString(R.string.nag_jobs_cancel), cancel)
        }
        return builder.build()
    }

    companion object {
        const val NAME = "nagmedar-remux"
        const val CHANNEL = "nagmedar_remux"
        const val ACTION_CANCEL = "app.nagmedar.ui.tvlab.remux.CANCEL"
        const val EXTRA_JOB = "job"
        private const val NOTIFICATION_ID = NotificationIds.REMUX
        private const val TIMEOUT_NOTIFICATION_ID = NotificationIds.REMUX_TIME_LIMIT
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

        /** The tap of the queue's notifications: the TV Lab. Their own PendingIntent ([NotificationIds]). */
        private fun openTvLab(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, NagmedarActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(NAGMEDAR_EXTRA_DESTINATION, Destination.TvLab.route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /**
         * A worker for the queue, after the one running, if any, so that nothing queued is
         * missed. Never bound to a constraint: a worker that waited for one would start later
         * from the background, where Android 12 and later let it no foreground, and every worker
         * appended after it would wait as well (S-1210's night run waits for the charger itself).
         */
        fun ensure(context: Context) = enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)

        /**
         * When the app shows: a worker for whatever waits, one already running kept. When none
         * runs, a waiting one is replaced: it may be one an older version bound to the charger,
         * which would hold every later one back. Blocking: WorkManager's database is read.
         */
        fun resume(context: Context) {
            val infos = runCatching { WorkManager.getInstance(context.applicationContext).getWorkInfosForUniqueWork(NAME).get() }.getOrNull()
            val stuck = infos != null && infos.none { it.state == WorkInfo.State.RUNNING } &&
                infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
            enqueue(context, if (stuck) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
        }

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(NAME, policy, OneTimeWorkRequestBuilder<RemuxWorker>().build())
        }
    }
}

/**
 * S-1210: the night run's charger as the worker, in the foreground since the alarm, waits for
 * it: Android's word that the device was plugged in wakes it at once (a receiver while it
 * waits), and the battery's state is read again every few seconds anyway.
 */
private class ChargerWatch(private val context: Context, private val control: RemuxQueueControl) : NightCharger, AutoCloseable {
    private val plugged = Semaphore(0)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = plugged.release()
    }
    private var registered = false

    override fun waiting(): Boolean = control.current.waitsForCharger(System.currentTimeMillis())

    override fun begin() = control.clearNight()

    override fun await(ms: Long) {
        if (!registered) {
            registered = runCatching {
                ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED)
            }.isSuccess
        }
        // an interrupted wait is a wait that ended: the runner looks at the queue and the battery again
        runCatching { plugged.tryAcquire(ms, TimeUnit.MILLISECONDS) }
    }

    override fun close() {
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        registered = false
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
