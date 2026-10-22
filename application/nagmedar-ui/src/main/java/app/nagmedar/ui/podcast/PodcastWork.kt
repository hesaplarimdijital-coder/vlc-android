package app.nagmedar.ui.podcast

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.work.TimeLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * S-1103: the schedule of the feeds' refresh: a periodic WorkManager job every [PodcastSettings.refreshHours] hours that
 * runs only with a network (an unmetered one when Settings › Podcasts says Wi-Fi only) and a battery that is not low.
 * WorkManager keeps it through restarts and Doze; setting it again with the same choices changes nothing.
 */
object RefreshSchedule {
    const val NAME = "nagmedar-podcast-refresh"
    private const val BACKOFF_SECONDS = 60L

    /** What the job needs to run. */
    fun constraints(settings: PodcastSettings): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (settings.refreshUnmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /** Schedules the job for [settings]; the one before it is replaced when its choices differ. */
    fun apply(context: Context, settings: PodcastSettings) {
        val request = PeriodicWorkRequestBuilder<PodcastRefreshWorker>(settings.refreshHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints(settings))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** No subscriptions, nothing to refresh. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(NAME)
    }
}

/** S-1103: what a download needs to start: an automatic one waits for an unmetered network and a battery that is not low, one the person asked for for any network. */
object DownloadConstraints {
    fun of(auto: Boolean): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (auto) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(auto)
        .build()
}

/** S-1103: the periodic job: reads the feeds, tells the news, queues the automatic downloads and tidies the storage. */
class PodcastRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val report = PodcastCenter.refresher(applicationContext).run()
        val notifier = PodcastNotifier(applicationContext)
        notifier.showNews(report.notices)
        report.cleanup?.takeIf { it.nearLimit || it.overLimit }?.let { notifier.showStorageNear(it.keptBytes, it.limitBytes) }
        // every feed failed: the network is probably down; a few tries later, then the next period
        return if (report.allFailed && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

/** S-1103: starts the download worker through WorkManager: one chain for the automatic downloads and one for those asked for. */
class WorkManagerDownloadScheduler(private val context: Context) : DownloadScheduler {
    override fun start(ids: List<Long>, auto: Boolean) {
        val manager = WorkManager.getInstance(context.applicationContext)
        // WorkManager's input is small: a long queue goes in several pieces, one after the other
        ids.chunked(CHUNK).forEach { piece ->
            val request = OneTimeWorkRequestBuilder<EpisodeDownloadWorker>()
                .setConstraints(DownloadConstraints.of(auto))
                .setInputData(workDataOf(KEY_IDS to piece.toLongArray(), KEY_AUTO to auto))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            manager.enqueueUniqueWork(if (auto) NAME_AUTO else NAME_MANUAL, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }

    companion object {
        const val NAME_AUTO = "nagmedar-podcast-download-auto"
        const val NAME_MANUAL = "nagmedar-podcast-download"
        const val KEY_IDS = "ids"
        const val KEY_AUTO = "auto"
        private const val CHUNK = 400
        private const val BACKOFF_SECONDS = 30L
    }
}

/**
 * S-1103: downloads the queued episodes it is given, one after the other, as a foreground job with a progress
 * notification and a Cancel. A download the system stops (the network went, the battery is low) goes back to the queue
 * and WorkManager starts it again when the constraints hold, continuing from the partial file. When the system lets no
 * foreground job start it runs as a plain job, which is stopped after ten minutes and continues the same way.
 */
class EpisodeDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val notifier = PodcastNotifier(applicationContext)

    override suspend fun doWork(): Result {
        val ids = inputData.getLongArray(WorkManagerDownloadScheduler.KEY_IDS)?.toList().orEmpty()
        if (ids.isEmpty()) return Result.success()
        runCatching { setForeground(foregroundInfo(notifier.downloading("", null, ids.first(), ids.size - 1))) }
        // Android 15's daily time limit: the runner puts the running episode back in the queue, as any stop does, and
        // WorkManager starts the work again; the user is told once, by this notice
        TimeLimit.onReached(this) { notifier.showTimeLimit() }
        var left = ids.size
        var shownAt = 0L
        val outcome = try {
            PodcastCenter.runner(applicationContext).run(
                ids,
                final = runAttemptCount >= MAX_ATTEMPTS - 1,
                onStart = { episode ->
                    left--
                    notifier.updateDownload(notifier.downloading(episode.title, null, episode.id, left))
                },
                onProgress = { episode, progress ->
                    val now = System.nanoTime()
                    if (now - shownAt >= UPDATE_EVERY_NS) {
                        shownAt = now
                        notifier.updateDownload(notifier.downloading(episode.title, progress.fraction, episode.id, left))
                    }
                },
            )
        } finally {
            // WorkManager takes away the notification of a foreground it started; one posted while the system refused
            // the foreground (after the daily limit, say) is the app's own and would stay, ongoing
            notifier.cancelDownload()
        }
        val cleanup = PodcastCenter.cleaner(applicationContext).clean(PodcastCenter.playingEpisodes())
        when {
            outcome.diskFull -> notifier.showDiskFull()
            cleanup.nearLimit || cleanup.overLimit -> notifier.showStorageNear(cleanup.keptBytes, cleanup.limitBytes)
        }
        return if (outcome.retry) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val id = inputData.getLongArray(WorkManagerDownloadScheduler.KEY_IDS)?.firstOrNull()
        val title = withContext(Dispatchers.IO) { id?.let { NagmedarDatabase.get(applicationContext).podcasts().episode(it)?.title } }.orEmpty()
        return foregroundInfo(notifier.downloading(title, null, id ?: -1L, 0))
    }

    private fun foregroundInfo(notification: android.app.Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PodcastNotifier.ID_DOWNLOAD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PodcastNotifier.ID_DOWNLOAD, notification)
        }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val UPDATE_EVERY_NS = 1_000_000_000L
    }
}
