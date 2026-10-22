package app.nagmedar.ui.radio.record

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * S-1107: the app kept alive (a foreground service of the data-sync type, with a notification
 * showing the time, the size, the track and a stop action) and the device awake while radio
 * recordings go on. It holds nothing else: the recordings are [RecordingCenter]'s. It goes when
 * the last recording ends. Android 15 limits a data-sync service to six hours a day: it then
 * stops the recordings and says so.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updater: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var watchedAt = 0L
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val center = RadioRecordings.center(this)
        // a service started for the foreground must go there, however soon it is over
        try {
            val notification = RecordingNotifications.ongoing(this, center.live.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(RecordingNotifications.ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(RecordingNotifications.ONGOING_ID, notification)
            }
        } catch (e: RuntimeException) {
            // the system refuses a service from the background: nothing is recorded without it
            Log.w(TAG, "no foreground service for the recording", e)
            center.stopAll()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) center.stopAll()
        if (updater?.isActive != true) updater = scope.launch { follow(center) }
        return START_NOT_STICKY
    }

    /** The notification follows the recordings every few seconds; the service goes when none is left. */
    private suspend fun follow(center: RecordingCenter) {
        var idle = 0
        while (true) {
            val live = center.live.value
            if (live.isEmpty() && center.starting.value == 0) {
                // a request may be a moment from its first state
                if (++idle >= IDLE_ROUNDS) break
            } else {
                idle = 0
            }
            if (live.isNotEmpty()) {
                hold()
                watch(live)
                runCatching {
                    getSystemService(NotificationManager::class.java)?.notify(RecordingNotifications.ONGOING_ID, RecordingNotifications.ongoing(this, live))
                }
            }
            delay(UPDATE_MS)
        }
        finish()
    }

    /** The device stays awake while the stream is read (renewed, so that a lost service does not leave it held). */
    private fun hold() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nagmedar:radio-recording")?.also {
            it.setReferenceCounted(false)
            wakeLock = it
        }
        runCatching { lock?.acquire(WAKE_MS) }
    }

    /**
     * While a scheduled recording goes on, an alarm is kept ahead of the clock: if Android kills the app's
     * process, the alarm rings after it and the recording is taken up again.
     */
    private fun watch(live: List<LiveRecording>) {
        if (live.none { it.request.scheduleId != null }) return
        val now = SystemClock.elapsedRealtime()
        if (watching && now - watchedAt < WATCH_RENEW_MS) return
        watchedAt = now
        watching = true
        RecordingAlarms.watch(this)
    }

    private fun release() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun finish() {
        release()
        if (watching) RecordingAlarms.cancelCatchUp(this)
        watching = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Android 15's limit for data-sync services is reached: the recordings stop, and the user is told. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        RadioRecordings.center(this).stopAll()
        RecordingNotifications.timedOut(this)
        finish()
    }

    override fun onDestroy() {
        updater?.cancel()
        scope.cancel()
        release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Nagmedar/RadioRecording"
        const val ACTION_STOP = "app.nagmedar.action.RADIO_RECORDING_STOP"
        private const val UPDATE_MS = 2_000L
        private const val IDLE_ROUNDS = 5
        private const val WAKE_MS = 5 * 60_000L
        private const val WATCH_RENEW_MS = 60_000L

        /** Starts the service (a recording is about to begin); false when the system does not let it. */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
            true
        } catch (e: RuntimeException) {
            // IllegalStateException, ForegroundServiceStartNotAllowedException
            Log.w(TAG, "the service could not be started", e)
            false
        }
    }
}
