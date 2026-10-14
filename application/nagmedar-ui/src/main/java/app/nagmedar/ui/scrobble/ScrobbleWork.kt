package app.nagmedar.ui.scrobble

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * S-1008: the job that sends the scrobble queue ([ScrobbleFlusher]): when there is a network, and
 * again after a wait that doubles from half a minute when a flush could not send everything. The
 * queue is in the database, so a job lost to a restart or an update loses nothing.
 */
class ScrobbleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (Scrobbling.get(applicationContext).flusher.flush()) {
        FlushResult.Done -> Result.success()
        FlushResult.Retry -> Result.retry()
    }
}

/** [ScrobbleScheduler] on WorkManager: one job at a time, with a network, and a backoff. */
class WorkScrobbleScheduler(private val context: Context) : ScrobbleScheduler {

    override fun schedule(now: Boolean) {
        val request = OneTimeWorkRequestBuilder<ScrobbleWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        // "send now" starts over, even from a wait; a job that waits or runs sends what is queued since as well
        val policy = if (now) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(NAME, policy, request)
    }

    companion object {
        const val NAME = "nagmedar-scrobble-flush"
        private const val BACKOFF_SECONDS = 30L
    }
}
