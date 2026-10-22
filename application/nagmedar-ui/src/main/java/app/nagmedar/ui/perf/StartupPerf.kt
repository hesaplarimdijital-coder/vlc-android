package app.nagmedar.ui.perf

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.view.OneShotPreDrawListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.tools.AppScope

/**
 * S-1404: a cold start-up from the launcher, timed to its first frame and kept. From Android 15
 * the system's own record of the start tells it (from the launch to the first frame); before it,
 * the time from the process's start to this window's first frame, when the process started for
 * the window.
 */
object StartupPerf {
    /** A process older than this when its first window comes was started for something else (a service, a widget). */
    private const val FOR_THE_WINDOW_MS = 5_000L

    private var watched = false

    /** The process's first window [activity], new ([restored] false): its start-up is kept once it shows. */
    fun watch(activity: Activity, restored: Boolean) {
        if (watched) return
        watched = true
        if (restored) return
        val app = activity.applicationContext
        // kept on a background thread: the records' preferences file is opened and read there, not on the main thread
        val keep = { ms: Long ->
            if (ms in 1..120_000) {
                val record = StartupRecord(System.currentTimeMillis(), ms)
                AppScope.launch(Dispatchers.IO) { PrefsPerfStore.get(app).keep(record) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            fromSystem(activity, keep)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            fromProcess(activity, keep)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun fromSystem(activity: Activity, keep: (Long) -> Unit) {
        val manager = activity.getSystemService(ActivityManager::class.java) ?: return
        runCatching {
            manager.addApplicationStartInfoCompletionListener(activity.mainExecutor) { info ->
                val launcher = info.reason == ApplicationStartInfo.START_REASON_LAUNCHER || info.reason == ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS
                if (info.startType == ApplicationStartInfo.START_TYPE_COLD && launcher) {
                    val stamps = info.startupTimestamps
                    val from = stamps[ApplicationStartInfo.START_TIMESTAMP_LAUNCH] ?: stamps[ApplicationStartInfo.START_TIMESTAMP_FORK]
                    val to = stamps[ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME]
                    // nanoseconds
                    if (from != null && to != null) keep((to - from) / 1_000_000)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun fromProcess(activity: Activity, keep: (Long) -> Unit) {
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Process.getStartRequestedUptimeMillis() else Process.getStartUptimeMillis()
        if (SystemClock.uptimeMillis() - started > FOR_THE_WINDOW_MS) return
        OneShotPreDrawListener.add(activity.window.decorView) { keep(SystemClock.uptimeMillis() - started) }
    }
}
