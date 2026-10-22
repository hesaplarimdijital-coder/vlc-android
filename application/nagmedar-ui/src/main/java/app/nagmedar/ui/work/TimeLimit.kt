package app.nagmedar.ui.work

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

/**
 * Android 15's daily time limit for foreground work (six hours of data sync, six of media
 * processing, for the whole app): whether the system stopped a worker for it, and the
 * notification that tells the user what comes of the work.
 */
object TimeLimit {

    /** Whether the system stopped [worker] because the app's foreground time for the worker's type ran out. */
    fun reached(worker: ListenableWorker): Boolean =
        Build.VERSION.SDK_INT >= 35 && worker.isStopped && worker.stopReason == WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT

    /**
     * [tell] runs if the limit stops [worker] (its work is cancelled then): called at the start
     * of a CoroutineWorker's doWork.
     */
    suspend fun onReached(worker: ListenableWorker, tell: () -> Unit) {
        currentCoroutineContext()[Job]?.invokeOnCompletion { cause -> if (cause != null && reached(worker)) runCatching(tell) }
    }

    /** Tells the user, on [channel] (made by the caller), that the limit stopped the work and [text] what comes of it; a tap opens the app. */
    @SuppressLint("MissingPermission")
    fun tell(context: Context, channel: String, id: Int, @DrawableRes icon: Int, @StringRes text: Int) {
        val message = context.getString(text)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, NagmedarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setContentTitle(context.getString(R.string.nag_time_limit_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // without the permission the app's windows say what came of the work
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
