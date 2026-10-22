package app.nagmedar.ui.podcast

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.notify.NotificationIds
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.work.TimeLimit
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import java.text.NumberFormat

/**
 * S-1103: the notifications of the podcasts: "Yeni bölümler" (one per show with news, grouped under a summary when there
 * are several), the download's progress, and the warnings of the storage. Nothing is posted without the permission
 * (Android 13 and later ask for POST_NOTIFICATIONS; before it the person may have switched the app's notifications off);
 * a tap on a show's notification opens its page.
 */
class PodcastNotifier(private val context: Context) {
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Whether a notification may be posted now. */
    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun ensureChannel(id: String, name: Int, importance: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || manager.getNotificationChannel(id) != null) return
        manager.createNotificationChannel(NotificationChannel(id, context.getString(name), importance).apply { setShowBadge(id == CHANNEL_NEW) })
    }

    // ------------------------------------------------------------ new episodes

    /** Posts [plan]: a notification for each show with news, and the summary over them when there are several. */
    fun showNews(plan: NoticePlan) {
        if (plan.isEmpty || !canPost()) return
        ensureChannel(CHANNEL_NEW, R.string.nag_pod_channel_new, NotificationManager.IMPORTANCE_DEFAULT)
        val res = context.resources
        plan.feeds.forEach { notice ->
            val lines = NotificationCompat.InboxStyle().also { style ->
                notice.titles.forEach(style::addLine)
                if (notice.count > notice.titles.size) style.setSummaryText(res.getQuantityString(R.plurals.nag_pod_notice_more, notice.count - notice.titles.size, notice.count - notice.titles.size))
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_NEW)
                .setSmallIcon(org.videolan.resources.R.drawable.ic_nagmedar_mark_mono)
                .setContentTitle(notice.feedTitle)
                .setContentText(res.getQuantityString(R.plurals.nag_pod_notice_count, notice.count, notice.count))
                .setStyle(lines)
                .setContentIntent(open(newsId(notice.feedId), notice.feedId))
                .setAutoCancel(true)
                .setGroup(GROUP_NEW)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setOnlyAlertOnce(true)
            runCatching { manager.notify(newsId(notice.feedId), builder.build()) }
        }
        if (plan.summary) {
            val summary = NotificationCompat.Builder(context, CHANNEL_NEW)
                .setSmallIcon(org.videolan.resources.R.drawable.ic_nagmedar_mark_mono)
                .setContentTitle(context.getString(R.string.nag_pod_channel_new))
                .setContentText(res.getQuantityString(R.plurals.nag_pod_notice_summary, plan.total, plan.total, plan.feeds.size))
                .setContentIntent(open(ID_NEWS_SUMMARY))
                .setAutoCancel(true)
                .setGroup(GROUP_NEW)
                .setGroupSummary(true)
                .setOnlyAlertOnce(true)
            runCatching { manager.notify(ID_NEWS_SUMMARY, summary.build()) }
        }
    }

    /** The notification of a show's news goes when its page is opened. */
    fun clearNews(feedId: Long) {
        runCatching { manager.cancel(newsId(feedId)) }
    }

    /** The tap of the notification [id]: the podcasts, on the show [feedId] if one is given. Its own PendingIntent ([NotificationIds]). */
    private fun open(id: Int, feedId: Long? = null): PendingIntent {
        val intent = Intent(context, NagmedarActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(NAGMEDAR_EXTRA_DESTINATION, Destination.Podcasts.route)
        if (feedId != null) intent.putExtra(EXTRA_PODCAST, feedId)
        return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // ------------------------------------------------------------ downloads

    /** The ongoing notification of the download worker: the episode, how far it is, a Cancel. [fraction] null for a length not known. */
    fun downloading(title: String, fraction: Float?, episodeId: Long, waiting: Int): Notification {
        ensureChannel(CHANNEL_DOWNLOAD, R.string.nag_pod_channel_download, NotificationManager.IMPORTANCE_LOW)
        val locale = context.resources.locale()
        val percent = fraction?.let { NumberFormat.getPercentInstance(locale).format(it.toDouble()) }
        val cancel = PendingIntent.getBroadcast(
            context,
            episodeId.hashCode(),
            Intent(context, DownloadCancelReceiver::class.java).setAction(ACTION_CANCEL).setPackage(context.packageName).putExtra(EXTRA_EPISODE, episodeId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(org.videolan.resources.R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(title)
            .setContentText(
                listOfNotNull(
                    percent,
                    waiting.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.nag_pod_download_waiting, it, it) },
                ).joinToString(" · ").ifEmpty { context.getString(R.string.nag_pod_download_starting) },
            )
            .setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).toInt(), fraction == null)
            .setContentIntent(open(ID_DOWNLOAD))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(org.videolan.resources.R.drawable.nagmedar_notif_close, context.getString(R.string.nag_pod_download_cancel), cancel)
            .build()
    }

    fun updateDownload(notification: Notification) {
        if (canPost()) runCatching { manager.notify(ID_DOWNLOAD, notification) }
    }

    /** The download worker ended: its notification goes. One a foreground service shows stays until the service leaves the foreground. */
    fun cancelDownload() {
        runCatching { manager.cancel(ID_DOWNLOAD) }
    }

    /** The device ran out of room: the downloads stopped. */
    fun showDiskFull() {
        if (!canPost()) return
        ensureChannel(CHANNEL_DOWNLOAD, R.string.nag_pod_channel_download, NotificationManager.IMPORTANCE_LOW)
        val n = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(org.videolan.resources.R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(context.getString(R.string.nag_pod_disk_full_title))
            .setContentText(context.getString(R.string.nag_pod_disk_full_text))
            .setContentIntent(open(ID_STORAGE))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(ID_STORAGE, n) }
    }

    /** Android's daily time limit for background work stopped the downloads: they are paused and go on later. */
    fun showTimeLimit() {
        if (!canPost()) return
        ensureChannel(CHANNEL_DOWNLOAD, R.string.nag_pod_channel_download, NotificationManager.IMPORTANCE_LOW)
        TimeLimit.tell(context, CHANNEL_DOWNLOAD, ID_TIME_LIMIT, R.string.nag_time_limit_podcast)
    }

    /** The downloads fill the limit of Settings › Podcasts, or nearly. */
    fun showStorageNear(usedBytes: Long, limitBytes: Long) {
        if (!canPost()) return
        ensureChannel(CHANNEL_DOWNLOAD, R.string.nag_pod_channel_download, NotificationManager.IMPORTANCE_LOW)
        val n = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(org.videolan.resources.R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(context.getString(R.string.nag_pod_storage_near_title))
            .setContentText(context.getString(R.string.nag_pod_storage_near_text, sizeLabel(usedBytes, context.resources.locale()), sizeLabel(limitBytes, context.resources.locale())))
            .setContentIntent(open(ID_STORAGE))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(ID_STORAGE, n) }
    }

    companion object {
        const val CHANNEL_NEW = "nagmedar_podcast_new"
        const val CHANNEL_DOWNLOAD = "nagmedar_podcast_download"
        const val GROUP_NEW = "nagmedar_podcast_new_group"
        const val ACTION_CANCEL = "app.nagmedar.ui.podcast.CANCEL_DOWNLOAD"
        const val EXTRA_EPISODE = "episode"

        /** The Long extra of the intent a notification starts the app with: the show to open. */
        const val EXTRA_PODCAST = "nagmedar_podcast_feed"

        const val ID_DOWNLOAD = NotificationIds.PODCAST_DOWNLOAD
        const val ID_STORAGE = NotificationIds.PODCAST_STORAGE
        const val ID_NEWS_SUMMARY = NotificationIds.PODCAST_NEWS_SUMMARY
        const val ID_TIME_LIMIT = NotificationIds.PODCAST_TIME_LIMIT
        private const val PROGRESS_MAX = 1000

        /** The id of a show's news notification: stable, so that a later refresh replaces it. */
        fun newsId(feedId: Long): Int = NotificationIds.PODCAST_NEWS.idOf(feedId)
    }
}

/** The notification's Cancel: the running download of the episode stops; only the app's own intent reaches it. */
class DownloadCancelReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PodcastNotifier.ACTION_CANCEL) return
        val id = intent.getLongExtra(PodcastNotifier.EXTRA_EPISODE, -1L)
        if (id < 0) return
        PodcastCenter.tracker.cancel(id)
    }
}
