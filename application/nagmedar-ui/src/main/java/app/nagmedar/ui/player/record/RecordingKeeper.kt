package app.nagmedar.ui.player.record

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.nagmedar.ui.R
import app.nagmedar.ui.work.TimeLimit

/**
 * S-607: the app kept in the foreground (a dataSync service) while a recording is copied into its
 * folder, for a copy of several gigabytes outlives the player and the playback service; the
 * copy's notification is its own. It holds nothing else: the copy is [RecordingFiles]'.
 */
class RecordingKeeper : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // a service started for the foreground must go there, even when the copy ended meanwhile
        val notification = pending ?: placeholder()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(ID, notification)
            }
        } catch (e: RuntimeException) {
            // the system refused (Android 12 and later, from the background): the notification alone
            Log.w(TAG, "no foreground service for the copy", e)
        }
        running = this
        // the copy ended before the service started: it goes at once
        if (pending == null) finish()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running === this) running = null
        super.onDestroy()
    }

    private fun finish(keepNotification: Boolean = false) {
        ServiceCompat.stopForeground(this, if (keepNotification) ServiceCompat.STOP_FOREGROUND_DETACH else ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** The copy's notification at nought, for a start the copy did not wait for. */
    private fun placeholder(): Notification {
        RecordingFiles.Notice.channel(this)
        return RecordingFiles.Notice.progressNotification(this, getString(R.string.nag_rec_notice_saving), 0)
    }

    /**
     * Android 15's limit for dataSync reached: the service goes within the seconds the system
     * gives; the copy goes on as it can, its notification stays, and the user is told to keep
     * the app open (a copy cut short is deleted and made again by RecordingFiles' recovery).
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        RecordingFiles.Notice.channel(this)
        TimeLimit.tell(this, RecordingFiles.Notice.CHANNEL, RecordingFiles.Notice.TIME_LIMIT_ID, R.drawable.ic_notif_video, R.string.nag_time_limit_recording)
        finish(keepNotification = true)
    }

    companion object {
        private const val TAG = "Nagmedar/Recording"

        /** The copy's notification's id ([RecordingFiles]'), which the service shows. */
        private const val ID = 6070

        @Volatile
        private var pending: Notification? = null

        /** The service once it is in the foreground: only then is it stopped from outside. */
        @Volatile
        private var running: RecordingKeeper? = null

        /** Starts the service with [notification]; false when the system does not let it start. */
        fun start(context: Context, notification: Notification): Boolean {
            pending = notification
            return try {
                ContextCompat.startForegroundService(context, Intent(context, RecordingKeeper::class.java))
                true
            } catch (e: RuntimeException) {
                // IllegalStateException, ForegroundServiceStartNotAllowedException
                Log.w(TAG, "the copy goes on without a foreground service", e)
                false
            }
        }

        /**
         * The copy ended: the service leaves the foreground and goes. Not stopped from outside
         * before it went to the foreground (the system would end the app): it then goes by itself.
         */
        fun stop() {
            pending = null
            running?.let { service -> Handler(service.mainLooper).post { service.finish() } }
        }
    }
}
