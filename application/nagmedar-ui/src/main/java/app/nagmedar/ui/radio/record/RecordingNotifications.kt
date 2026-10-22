package app.nagmedar.ui.radio.record

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.nagmedar.ui.R
import java.util.Locale

/** What the ongoing notification says: one recording's station and track, or the count of them, and how long it has gone on. */
data class RecordingNoticeText(
    val title: String,
    val text: String,
    val detail: String,
    /** When the oldest recording began (ms): the notification counts the time from it. */
    val since: Long,
)

/** Words from the resources, so that the text is made (and tested) without a context. */
interface RecordingWords {
    fun get(id: Int, vararg args: Any): String
}

/**
 * S-1107: the notifications of radio recording: the ongoing one (elapsed time, size, the track, a
 * stop action) that the foreground service shows, and the ones that say a recording ended for a
 * reason the user should know (room, folder, the station).
 */
object RecordingNotifications {

    /** The ongoing notification's id: the foreground service's. */
    const val ONGOING_ID = 6110
    private const val ALERT_ID = 6111
    private const val CHANNEL = "nagmedar_radio_recording"
    private const val CHANNEL_ALERTS = "nagmedar_radio_recording_alerts"

    /** The words of the ongoing notification for the recordings [live] (at least one) at [now]. */
    fun text(live: List<LiveRecording>, words: RecordingWords, size: (Long) -> String): RecordingNoticeText {
        val first = live.first()
        val bytes = live.sumOf { it.state.bytes }
        val saved = live.sumOf { it.state.tracksSaved }
        val since = live.minOf { it.state.startedAt.takeIf { at -> at > 0 } ?: Long.MAX_VALUE }.takeIf { it != Long.MAX_VALUE } ?: 0L
        val detail = words.get(R.string.nag_radio_rec_notif_detail, saved, size(bytes))
        if (live.size > 1) {
            return RecordingNoticeText(
                title = words.get(R.string.nag_radio_rec_notif_many, live.size),
                text = live.joinToString(" · ") { it.request.station.name },
                detail = detail,
                since = since,
            )
        }
        val state = first.state
        val text = when {
            state.phase == Phase.Reconnecting -> words.get(R.string.nag_radio_rec_notif_reconnecting)
            state.phase == Phase.Connecting -> words.get(R.string.nag_radio_rec_notif_connecting)
            state.splits == false -> words.get(R.string.nag_radio_rec_notif_one_file)
            state.track != null -> words.get(R.string.nag_radio_rec_notif_track, state.track.label())
            else -> words.get(R.string.nag_radio_rec_notif_waiting)
        }
        return RecordingNoticeText(first.request.station.name, text, detail, since)
    }

    /** The title as a line: "Artist - Title", or the title alone. */
    fun TrackTag.label(): String = if (artist.isNullOrBlank()) title.orEmpty() else "$artist - $title"

    fun channels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.nag_radio_rec_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        if (manager.getNotificationChannel(CHANNEL_ALERTS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.nag_radio_rec_channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    private fun open(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, ONGOING_ID, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    /** The ongoing notification for [live] (nothing live: the one of a recording being started). */
    fun ongoing(context: Context, live: List<LiveRecording>): Notification {
        channels(context)
        val words = wordsOf(context)
        val stop = PendingIntent.getService(
            context, ONGOING_ID,
            Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.nag_radio_rec_stop), stop)
        open(context)?.let(builder::setContentIntent)
        if (live.isEmpty()) {
            return builder.setContentTitle(context.getString(R.string.nag_radio_rec_notif_connecting)).build()
        }
        val text = text(live, words) { RecordingText.size(it, Locale.getDefault()) }
        builder.setContentTitle(text.title).setContentText(text.text).setSubText(text.detail)
        if (text.since > 0) builder.setWhen(text.since).setShowWhen(true).setUsesChronometer(true)
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    private fun wordsOf(context: Context): RecordingWords = object : RecordingWords {
        override fun get(id: Int, vararg args: Any): String = context.getString(id, *args)
    }

    private fun post(context: Context, tag: String?, id: Int, notification: Notification) {
        // without the permission the screens say it all
        runCatching { NotificationManagerCompat.from(context).notify(tag, id, notification) }
    }

    private fun alert(context: Context, title: String, text: String): Notification {
        channels(context)
        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .apply { open(context)?.let(::setContentIntent) }
            .build()
    }

    /** What the user is told when [ended] was a recording that ended; null: nothing (the user stopped it, or all went well and nobody waits). */
    fun endText(ended: EndedRecording, words: RecordingWords, size: (Long) -> String): Pair<String, String>? {
        val station = ended.request.station.name
        val saved = ended.state.tracksSaved
        return when (val end = ended.end) {
            SessionEnd.UserStopped -> null
            SessionEnd.TimeUp ->
                if (ended.request.scheduleId != null) words.get(R.string.nag_radio_rec_done_title, station) to words.get(R.string.nag_radio_rec_done_text, saved, size(ended.state.bytes)) else null
            SessionEnd.StorageLow -> words.get(R.string.nag_radio_rec_low_title) to words.get(R.string.nag_radio_rec_low_text, station, saved)
            SessionEnd.LimitReached -> words.get(R.string.nag_radio_rec_limit_title) to words.get(R.string.nag_radio_rec_limit_text, station, saved)
            SessionEnd.NoFolder -> words.get(R.string.nag_radio_rec_nofolder_title) to words.get(R.string.nag_radio_rec_nofolder_text, station)
            is SessionEnd.Failed -> words.get(R.string.nag_radio_rec_failed_title, station) to
                (end.status?.let { words.get(R.string.nag_radio_rec_failed_http, it) } ?: words.get(R.string.nag_radio_rec_failed_text))
            is SessionEnd.Unsupported -> when (end.reason) {
                UnsupportedStream.Segmented -> words.get(R.string.nag_radio_rec_unsupported_title, station) to words.get(R.string.nag_radio_rec_unsupported_hls)
                UnsupportedStream.Format -> words.get(R.string.nag_radio_rec_unsupported_title, station) to words.get(R.string.nag_radio_rec_unsupported_format)
            }
        }
    }

    /** The notifications that tell how recordings ended or why one could not begin. */
    fun alerts(context: Context): RecordingAlerts = object : RecordingAlerts {
        private val words = wordsOf(context)

        override fun ended(ended: EndedRecording) {
            val (title, text) = endText(ended, words) { RecordingText.size(it, Locale.getDefault()) } ?: return
            post(context, ended.request.station.key, ALERT_ID, alert(context, title, text))
        }

        override fun refused(notice: RecordingNotice) {
            val (title, text) = when (notice) {
                is RecordingNotice.Full -> words.get(R.string.nag_radio_rec_full_title, notice.station) to words.get(R.string.nag_radio_rec_full_text, notice.maxParallel)
                is RecordingNotice.CannotStart -> words.get(R.string.nag_radio_rec_cannot_title, notice.station) to words.get(R.string.nag_radio_rec_cannot_text)
                is RecordingNotice.Merged -> return
            }
            post(context, notice.stationName(), ALERT_ID, alert(context, title, text))
        }
    }

    private fun RecordingNotice.stationName(): String = when (this) {
        is RecordingNotice.Full -> station
        is RecordingNotice.Merged -> station
        is RecordingNotice.CannotStart -> station
    }

    /** The alert about the Android 15 limit of a data-sync service: the recordings were stopped after it. */
    fun timedOut(context: Context) {
        post(context, "timeout", ALERT_ID, alert(context, context.getString(R.string.nag_radio_rec_timeout_title), context.getString(R.string.nag_radio_rec_timeout_text)))
    }
}
