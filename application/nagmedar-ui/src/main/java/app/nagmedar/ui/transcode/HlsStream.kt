package app.nagmedar.ui.transcode

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.notify.NotificationIds
import app.nagmedar.ui.shell.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import java.io.File
import java.net.InetAddress

/** S-1303: why the stream did not start or stopped. */
enum class HlsFailure(@StringRes val message: Int) {
    /** The device is on no Wi-Fi or Ethernet with a local address. */
    NoLan(R.string.nag_hls_no_lan),

    /** libVLC could not encode or read the media. */
    Engine(R.string.nag_hls_engine),

    /** The server could not listen. */
    Server(R.string.nag_hls_server),

    /** Android's time limit for the service's kind of work (Android 15 and later) ended it. */
    TimeLimit(R.string.nag_hls_time_limit),
}

/** S-1303: the HLS stream as the sheet and the notification show it. */
sealed interface HlsState {
    data object Idle : HlsState

    data class Starting(val title: String, val quality: HlsQuality) : HlsState

    /** On the air at [url]; [served] files sent so far; [ended]: the media was read to its end, its segments are still served. */
    data class Live(val title: String, val quality: HlsQuality, val url: String, val startedAt: Long, val served: Int = 0, val ended: Boolean = false) : HlsState

    data class Failed(val reason: HlsFailure) : HlsState
}

/** S-1303: the device's address on the local network (Wi-Fi or Ethernet) and its prefix; null off such a network. */
data class LanAddress(val address: InetAddress, val prefix: Int) {
    companion object {
        fun find(context: Context): LanAddress? {
            val connectivity = context.getSystemService<ConnectivityManager>() ?: return null
            val network = connectivity.activeNetwork ?: return null
            val caps = connectivity.getNetworkCapabilities(network) ?: return null
            val local = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!local) return null
            val link = connectivity.getLinkProperties(network) ?: return null
            return link.linkAddresses.firstOrNull { LanGuard.isLanAddress(it.address) }?.let { LanAddress(it.address, it.prefixLength) }
        }
    }
}

/**
 * S-1303: "Ağda yayınla (HLS)": the media encoded by libVLC into HLS segments in the app's storage
 * (a folder a session, emptied when it stops), served on the local network by [HlsServer] at
 * http://<address>:<port>/stream.m3u8, while a foreground service runs with its notification and
 * Stop. One stream at a time; a new one stops the one before.
 */
object HlsStream {
    private val mutableState = MutableStateFlow<HlsState>(HlsState.Idle)
    val state: StateFlow<HlsState> = mutableState.asStateFlow()

    internal fun publish(state: HlsState) {
        mutableState.value = state
    }

    /** Streams [media] as [quality]; [title] names it on the sheet and in the notification. */
    fun start(context: Context, media: Uri, title: String, quality: HlsQuality) {
        if (LanAddress.find(context) == null) {
            publish(HlsState.Failed(HlsFailure.NoLan))
            return
        }
        publish(HlsState.Starting(title, quality))
        val intent = Intent(context, HlsService::class.java)
            .setAction(HlsService.ACTION_START)
            .setData(media)
            .putExtra(HlsService.EXTRA_TITLE, title)
            .putExtra(HlsService.EXTRA_QUALITY, quality.name)
        runCatching { ContextCompat.startForegroundService(context, intent) }.onFailure {
            publish(HlsState.Failed(HlsFailure.Server))
        }
    }

    fun stop(context: Context) {
        if (state.value is HlsState.Idle || state.value is HlsState.Failed) {
            publish(HlsState.Idle)
            return
        }
        runCatching { context.startService(Intent(context, HlsService::class.java).setAction(HlsService.ACTION_STOP)) }
    }

    /** Back from a failure to nothing on the air. */
    fun dismiss() {
        if (state.value is HlsState.Failed) publish(HlsState.Idle)
    }

    /** The folders of the sessions, under the app's files. */
    fun sessionsDir(context: Context): File = File(context.filesDir, "hls")

    /** What a process that died left behind: the segments of a stream no one serves now. */
    fun cleanUp(context: Context) {
        if (state.value is HlsState.Idle || state.value is HlsState.Failed) sessionsDir(context).deleteRecursively()
    }
}

/**
 * S-1303: the HLS stream's foreground service (media processing on Android 15 and later, data sync
 * before): the server, libVLC's stream output on a thread of its own, the notification with the
 * address and Stop; the session's folder deleted when it ends.
 */
class HlsService : Service() {
    @Volatile
    private var session: Session? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                goForeground(notification(getString(R.string.nag_hls_starting), null))
                session?.stop()
                val media = intent.data
                val quality = HlsQuality.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_QUALITY) } ?: HlsQuality.P720
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                if (media == null) finish() else session = Session(media, title, quality).also { it.start() }
            }
            else -> finish()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        session?.stop()
        session = null
        super.onDestroy()
    }

    /**
     * Android 15's time limit for the service's type reached: the stream stops at once (the
     * system wants the service gone within seconds), and the sheet says why.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        HlsStream.publish(HlsState.Failed(HlsFailure.TimeLimit))
        finish()
    }

    private fun finish() {
        session?.stop()
        session = null
        if (HlsStream.state.value !is HlsState.Failed) HlsStream.publish(HlsState.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(notification: Notification) {
        try {
            when {
                Build.VERSION.SDK_INT >= 35 -> startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else -> startForeground(ID, notification)
            }
        } catch (e: RuntimeException) {
            // refused from the background (Android 12 and later): the stream runs as it can
            Log.w(TAG, "no foreground service for the stream", e)
        }
    }

    private fun notification(text: String, url: String?): Notification {
        val manager = getSystemService<NotificationManager>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager?.getNotificationChannel(CHANNEL) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.nag_hls_title), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            )
        }
        // the tap's own PendingIntent (NotificationIds): another notification's extras never replace its page
        val open = PendingIntent.getActivity(
            this,
            ID,
            Intent(this, NagmedarActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(NAGMEDAR_EXTRA_DESTINATION, Destination.Convert.route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, HlsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(org.videolan.resources.R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(getString(R.string.nag_hls_title))
            .setContentText(text)
            .setSubText(url)
            .setContentIntent(open)
            .addAction(org.videolan.resources.R.drawable.nagmedar_notif_close, getString(R.string.nag_hls_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /** One stream: its folder, its server and its encoder's thread. */
    private inner class Session(private val media: Uri, private val title: String, private val quality: HlsQuality) {
        @Volatile
        private var stopping = false
        private var server: HlsServer? = null
        private val dir = File(HlsStream.sessionsDir(this@HlsService), System.currentTimeMillis().toString())
        private val engine = LibVlcSoutEngine(this@HlsService)

        /** The folder, the server and the encoding, on a thread of the session's own: nothing of it on the main thread. */
        fun start() {
            Thread({ work() }, "nagmedar-hls-sout").start()
        }

        private fun work() {
            val lan = LanAddress.find(this@HlsService) ?: return fail(HlsFailure.NoLan)
            HlsStream.sessionsDir(this@HlsService).listFiles()?.filter { it != dir }?.forEach { it.deleteRecursively() }
            if (stopping) return
            if (!dir.mkdirs()) return fail(HlsFailure.Server)
            val started = runCatching { HlsServer(dir, lan.address, { LanGuard.allowed(it, lan.address, lan.prefix) }).start() }.getOrNull()
                ?: return fail(HlsFailure.Server)
            synchronized(this) {
                if (stopping) {
                    started.close()
                    dir.deleteRecursively()
                    return
                }
                server = started
            }
            val host = lan.address.hostAddress
            val url = "http://$host:${started.port}/${SoutChain.HLS_PLAYLIST}"
            val startedAt = System.currentTimeMillis()
            val outcome = engine.run(media, SoutChain.hls(quality.profile, dir.path)) {
                if (stopping) return@run SoutSteer.Stop
                // on the air once the first segment is listed
                if (File(dir, SoutChain.HLS_PLAYLIST).isFile) {
                    val live = HlsState.Live(title, quality, url, startedAt, started.served)
                    if (HlsStream.state.value != live) {
                        if (HlsStream.state.value !is HlsState.Live) updateNotification(url)
                        HlsStream.publish(live)
                    }
                }
                SoutSteer.Go
            }
            when {
                stopping -> dir.deleteRecursively()
                outcome is SoutOutcome.Failed -> fail(HlsFailure.Engine)
                // the media read to its end: its segments stay on the air until Stop
                else -> (HlsStream.state.value as? HlsState.Live)?.let { HlsStream.publish(it.copy(served = started.served, ended = true)) }
            }
        }

        private fun updateNotification(url: String) {
            runCatching { getSystemService<NotificationManager>()?.notify(ID, notification(getString(R.string.nag_hls_live_notice, title), url)) }
        }

        private fun fail(reason: HlsFailure) {
            stop()
            HlsStream.publish(HlsState.Failed(reason))
            stopSelfFromSession()
        }

        private fun stopSelfFromSession() {
            ContextCompat.getMainExecutor(this@HlsService).execute { if (session === this) finish() }
        }

        /** Stops the stream: the server closed, the encoding told to stop at its next step, the folder deleted off the main thread. */
        fun stop() {
            val closing = synchronized(this) {
                stopping = true
                server.also { server = null }
            }
            Thread({
                closing?.close()
                dir.deleteRecursively()
            }, "nagmedar-hls-stop").start()
        }
    }

    companion object {
        private const val TAG = "Nagmedar/HLS"
        const val ACTION_START = "app.nagmedar.ui.transcode.HLS_START"
        const val ACTION_STOP = "app.nagmedar.ui.transcode.HLS_STOP"
        const val EXTRA_TITLE = "title"
        const val EXTRA_QUALITY = "quality"
        private const val CHANNEL = "nagmedar_hls"
        private const val ID = NotificationIds.HLS
    }
}
