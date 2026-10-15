package app.nagmedar.ui.jobs

import android.app.Notification
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkInfo

/**
 * The foreground of a long job's worker (S-1010's converter, S-1303's conversions, S-1206's
 * remux): media processing on Android 15 and later, data sync before. WorkManager's
 * SystemForegroundService declares both in the manifest.
 */
fun jobForegroundInfo(id: Int, notification: Notification): ForegroundInfo = when {
    Build.VERSION.SDK_INT >= 35 -> ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    else -> ForegroundInfo(id, notification)
}

/** Whether the system stopped this worker for its foreground time (Android 15's six hours a day). */
fun ListenableWorker.stoppedForForegroundTime(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && stopReason == WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT
