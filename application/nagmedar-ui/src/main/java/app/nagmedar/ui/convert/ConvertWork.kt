package app.nagmedar.ui.convert

import android.annotation.SuppressLint
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
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.jobs.jobForegroundInfo
import app.nagmedar.ui.jobs.stoppedForForegroundTime
import app.nagmedar.ui.notify.NotificationIds
import app.nagmedar.ui.shell.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** S-1010: what the music library shows of the converter: a job under way, or the last one done. */
sealed interface ConvertStatus {
    val jobId: String

    /**
     * [progress] once the job has begun; [queued] jobs wait behind it. [resumes]: Android stopped
     * it for a while (a foreground job's time limit, say) and it goes on from where it was.
     */
    data class Running(override val jobId: String, val progress: ConvertProgress?, val queued: Int, val resumes: Boolean = false) : ConvertStatus

    data class Finished(override val jobId: String, val converted: Int, val skipped: Int, val failed: Int, val cancelled: Boolean) : ConvertStatus
}

/**
 * S-1010: the converter's jobs, one at a time: each a WorkManager job of its own, appended
 * after the one running (a unique chain), its files and outcome in [ConvertStore]. Stopping a
 * job marks it; the worker sees the mark at its next step and fails the files left as
 * cancelled, so the jobs queued after it still run.
 */
object ConvertQueue {
    const val WORK = "nagmedar-convert"
    internal const val KEY_JOB = "job"
    internal const val KEY_INDEX = "index"
    internal const val KEY_TOTAL = "total"
    internal const val KEY_TITLE = "title"
    internal const val KEY_FRACTION = "fraction"
    internal const val KEY_CONVERTED = "converted"
    internal const val KEY_SKIPPED = "skipped"
    internal const val KEY_FAILED = "failed"
    internal const val KEY_WAITING = "waiting"
    internal const val KEY_CANCELLED = "cancelled"
    internal const val KEY_FINISHED_AT = "finished_at"
    internal const val TAG_JOB = "job:"

    /** The jobs stopped in this process, seen at once (the mark in the store survives a restart). */
    private val stopped: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** A new job for [items] with [options]; its id. */
    fun start(context: Context, options: ConvertOptions, items: List<ConvertItem>, now: Long = System.currentTimeMillis()): String {
        val app = context.applicationContext
        val job = ConvertJob(UUID.randomUUID().toString(), now, options, items)
        val store = ConvertStore(app)
        store.prune(now)
        store.save(job)
        val request = OneTimeWorkRequestBuilder<ConvertWorker>()
            .setInputData(workDataOf(KEY_JOB to job.id))
            .addTag(WORK)
            .addTag(TAG_JOB + job.id)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        return job.id
    }

    /** Stops job [id]: the file under way is removed, the rest are not made. */
    fun cancel(context: Context, id: String) {
        stopped += id
        ConvertStore(context.applicationContext).markCancelled(id)
    }

    internal fun isCancelled(store: ConvertStore, id: String): Boolean = id in stopped || store.isCancelled(id)

    /** A finished job put away: its summary is not shown again. */
    fun dismiss(context: Context, id: String) {
        stopped -= id
        ConvertStore(context.applicationContext).delete(id)
    }

    /** What to show of the jobs, as WorkManager tells them. */
    fun observe(context: Context): Flow<ConvertStatus?> {
        val app = context.applicationContext
        val store = ConvertStore(app)
        // WorkManager starts with the app; a test without it shows no converter
        val work = runCatching { WorkManager.getInstance(app) }.getOrNull() ?: return flowOf(null)
        return work.getWorkInfosForUniqueWorkFlow(WORK).map { infos ->
            withContext(Dispatchers.IO) {
                status(
                    infos.map { info ->
                        WorkState(info.state, info.progress, info.outputData, info.tags.firstOrNull { it.startsWith(TAG_JOB) }?.removePrefix(TAG_JOB).orEmpty(), info.runAttemptCount)
                    },
                    store::has,
                )
            }
        }
    }

    /** A WorkInfo's part the status is made from: its job's id (from its tag) and how often it ran. */
    internal data class WorkState(val state: WorkInfo.State, val progress: Data, val output: Data, val jobId: String, val attempts: Int = 0)

    /**
     * The job running (with how many wait), else the last one finished whose summary is still
     * kept ([kept]); null when there is neither.
     */
    internal fun status(works: List<WorkState>, kept: (String) -> Boolean): ConvertStatus? {
        val running = works.firstOrNull { it.state == WorkInfo.State.RUNNING }
        val waiting = works.count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
        if (running != null) {
            val p = running.progress
            val jobId = p.getString(KEY_JOB) ?: running.jobId
            val progress = if (p.getInt(KEY_TOTAL, 0) > 0) {
                ConvertProgress(
                    index = p.getInt(KEY_INDEX, 0),
                    total = p.getInt(KEY_TOTAL, 0),
                    title = p.getString(KEY_TITLE).orEmpty(),
                    fraction = p.getDouble(KEY_FRACTION, 0.0),
                    converted = p.getInt(KEY_CONVERTED, 0),
                    skipped = p.getInt(KEY_SKIPPED, 0),
                    failed = p.getInt(KEY_FAILED, 0),
                    waiting = p.getString(KEY_WAITING)?.takeIf { it.isNotEmpty() }?.let(ConvertReason::of),
                )
            } else {
                null
            }
            return ConvertStatus.Running(jobId, progress, waiting)
        }
        if (waiting > 0) {
            // a job that ran before and waits again was stopped by Android; it goes on from where it was
            val stopped = works.firstOrNull { (it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED) && it.attempts > 0 }
            return ConvertStatus.Running(stopped?.jobId.orEmpty(), null, waiting - 1, resumes = stopped != null)
        }
        val done = works
            .filter { it.state == WorkInfo.State.SUCCEEDED && it.output.getString(KEY_JOB)?.let(kept) == true }
            .maxByOrNull { it.output.getLong(KEY_FINISHED_AT, 0L) } ?: return null
        val o = done.output
        return ConvertStatus.Finished(
            jobId = o.getString(KEY_JOB).orEmpty(),
            converted = o.getInt(KEY_CONVERTED, 0),
            skipped = o.getInt(KEY_SKIPPED, 0),
            failed = o.getInt(KEY_FAILED, 0),
            cancelled = o.getBoolean(KEY_CANCELLED, false),
        )
    }

    internal fun progressData(jobId: String, p: ConvertProgress): Data = workDataOf(
        KEY_JOB to jobId,
        KEY_INDEX to p.index,
        KEY_TOTAL to p.total,
        KEY_TITLE to p.title.take(200),
        KEY_FRACTION to p.fraction,
        KEY_CONVERTED to p.converted,
        KEY_SKIPPED to p.skipped,
        KEY_FAILED to p.failed,
        KEY_WAITING to (p.waiting?.key ?: ""),
    )

    internal fun outputData(summary: ConvertSummary, finishedAt: Long): Data = workDataOf(
        KEY_JOB to summary.jobId,
        KEY_FINISHED_AT to finishedAt,
        KEY_CONVERTED to summary.converted,
        KEY_SKIPPED to summary.skipped,
        KEY_FAILED to summary.failed.count { it.reason != ConvertReason.Cancelled },
        KEY_CANCELLED to summary.cancelled,
    )
}

/**
 * S-1010: one job's worker: a foreground job (media processing on Android 15 and later, data
 * sync before) with an ongoing notification of the file under way, the job's progress and a
 * Stop; a notification of what came of it at the end. A job the system stopped is taken up
 * again by WorkManager after the files it did.
 */
class ConvertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val app = applicationContext
        val id = inputData.getString(ConvertQueue.KEY_JOB) ?: return Result.failure()
        val store = ConvertStore(app)
        val job = withContext(Dispatchers.IO) { store.job(id) } ?: return Result.failure()
        // a foreground start can be refused (Android 12+, from the background): the job then runs as it can
        runCatching { setForeground(foregroundInfo(notification(job, null))) }
        val summary = withContext(Dispatchers.IO) {
            val runner = ConvertRunner(NativeConvertEngine(app), TreeDestination(app, Uri.parse(job.options.folder)), AndroidDeviceState(app))
            runner.run(
                job,
                done = store.summary(job),
                cancelled = { ConvertQueue.isCancelled(store, id) },
                interrupted = { isStopped },
                onProgress = { progress ->
                    setProgressAsync(ConvertQueue.progressData(id, progress))
                    show(job, progress)
                },
                onItem = store::saveSummary,
                leftover = store.partial(id),
                onPartial = { handle -> store.savePartial(id, handle) },
            )
        }
        runCatching { manager.cancel(NOTIFICATION_ID) }
        if (isStopped) {
            // Android 15's six hours of a foreground job, say: the job waits and goes on from where it was
            if (stoppedForForegroundTime()) paused(job)
            return Result.retry()
        }
        finished(job, summary)
        return Result.success(ConvertQueue.outputData(summary, System.currentTimeMillis()))
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val id = inputData.getString(ConvertQueue.KEY_JOB)
        val job = id?.let { withContext(Dispatchers.IO) { ConvertStore(applicationContext).job(it) } }
        return foregroundInfo(notification(job, null))
    }

    private fun foregroundInfo(notification: Notification): ForegroundInfo = jobForegroundInfo(NOTIFICATION_ID, notification)

    /** When the notification was last changed: once a second at most (the system drops quicker updates). */
    private var shownAt = 0L

    private fun show(job: ConvertJob, progress: ConvertProgress) {
        val now = SystemClock.elapsedRealtime()
        if (now - shownAt < NOTIFY_EVERY_MS && progress.fraction > 0.0) return
        shownAt = now
        runCatching { manager.notify(NOTIFICATION_ID, notification(job, progress)) }
    }

    /** The ongoing notification: the file under way, how far the job got, a Stop. */
    private fun notification(job: ConvertJob?, progress: ConvertProgress?): Notification {
        val context = applicationContext
        channel(context)
        val text = when {
            progress == null -> context.getString(R.string.nag_convert_starting)
            progress.waiting == ConvertReason.Battery -> context.getString(R.string.nag_convert_waiting_battery)
            progress.waiting == ConvertReason.Thermal -> context.getString(R.string.nag_convert_waiting_heat)
            else -> context.getString(R.string.nag_convert_progress, progress.index + 1, progress.total, progress.title)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(context.getString(R.string.nag_convert_running_title))
            .setContentText(text)
            .setContentIntent(openMusic(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(PROGRESS_MAX, ((progress?.overall ?: 0f) * PROGRESS_MAX).toInt(), progress == null)
        if (job != null) {
            val stop = PendingIntent.getBroadcast(
                context,
                job.id.hashCode(),
                Intent(context, ConvertCancelReceiver::class.java).setAction(ACTION_CANCEL).putExtra(ConvertQueue.KEY_JOB, job.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.nagmedar_notif_close, context.getString(R.string.nag_convert_cancel), stop)
        }
        return builder.build()
    }

    /** What came of the job, once it is done: the counts, a tap opens the music library and its summary. */
    @SuppressLint("MissingPermission")
    private fun finished(job: ConvertJob, summary: ConvertSummary) {
        val context = applicationContext
        val failed = summary.failed.count { it.reason != ConvertReason.Cancelled }
        val title = context.getString(if (summary.cancelled) R.string.nag_convert_stopped_title else R.string.nag_convert_done_title)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(title)
            .setContentText(countsLine(context.resources, summary.converted, summary.skipped, failed))
            .setContentIntent(openMusic(context))
            .setAutoCancel(true)
            .build()
        // without the permission the music library's summary says it all
        runCatching { NotificationManagerCompat.from(context).notify(NotificationIds.CONVERT_DONE.idOf(job.id.hashCode()), notification) }
    }

    /** Android stopped the job (a foreground job's time limit): said, so that the wait is not a mystery. */
    @SuppressLint("MissingPermission")
    private fun paused(job: ConvertJob) {
        val context = applicationContext
        channel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(context.getString(R.string.nag_convert_paused_title))
            .setContentText(context.getString(R.string.nag_convert_waiting_resume))
            .setContentIntent(openMusic(context))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NotificationIds.CONVERT_DONE.idOf(job.id.hashCode()), notification) }
    }

    /** The tap of the converter's notifications: the music library. Their own PendingIntent ([NotificationIds]). */
    private fun openMusic(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        Intent(context, NagmedarActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(NAGMEDAR_EXTRA_DESTINATION, Destination.Music.route),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL = "nagmedar_convert"
        const val ACTION_CANCEL = "app.nagmedar.ui.convert.CANCEL"
        private const val NOTIFICATION_ID = NotificationIds.CONVERT
        private const val PROGRESS_MAX = 1000
        private const val NOTIFY_EVERY_MS = 1000L

        private fun channel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.nag_convert_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            )
        }
    }
}

/** The notification's Stop: the job is asked to stop; only the app's own intent reaches it. */
class ConvertCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConvertWorker.ACTION_CANCEL) return
        val id = intent.getStringExtra(ConvertQueue.KEY_JOB) ?: return
        val pending = goAsync()
        Thread {
            try {
                ConvertQueue.cancel(context, id)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
