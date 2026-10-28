package app.nagmedar.ui.notify

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Every notification id the app posts, in one place: the fixed ones, and the ranges of those made
 * one per item (a show's news, a job's outcome, a drive) by [IdRange.idOf]. No fixed id is in a
 * range, no two are equal and no two ranges meet (NotificationIdsTest), so that no notification
 * takes another's place. VLC's classic services number theirs in
 * [org.videolan.resources.NotificationIds]; they are listed here as they are.
 *
 * A PendingIntent that opens Nağmedar's window from a notification takes its request code from
 * here too: the notification's id, or for the notifications of one job that open the same page,
 * the job's ongoing notification's. Such intents differ only by their extras (the page to open):
 * with one request code they would be one PendingIntent, and FLAG_UPDATE_CURRENT would give one
 * notification's extras to another's tap. The widget's tap has [WIDGET_TAP], which no
 * notification has.
 */
object NotificationIds {

    // ------------------------------------------------------------ VLC's classic services

    /** The playback service, and VLC's small window. */
    const val VLC_PLAYBACK = 3

    /** The medialibrary's scan. */
    const val VLC_MEDIA_PARSING = 43
    const val VLC_REMOTE_ACCESS = 44
    const val VLC_REMOTE_ACCESS_OTP = 45
    const val VLC_DEBUG_LOGS = 46

    /** Android TV's recommendations (before Android 8): 47 and the three after it. */
    @JvmField
    val VLC_RECOMMENDATIONS = IdRange(47, 4)

    // ------------------------------------------------------------ Nağmedar

    /** S-709: the transcription from speech: the job under way. */
    const val WHISPER = 709

    /** The widget's tap: not a notification, the request code of the PendingIntent that opens the window. */
    const val WIDGET_TAP = 1000

    /** S-1010: the music converter's job under way. */
    const val CONVERT = 1010

    /** S-1103: the podcasts: the download under way, the storage, the summary of the news, Android's time limit. */
    const val PODCAST_DOWNLOAD = 1103
    const val PODCAST_STORAGE = 1104
    const val PODCAST_NEWS_SUMMARY = 1105
    const val PODCAST_TIME_LIMIT = 1107

    /** S-1301: libVLC asks something (a sign-in, a certificate) while no window is in front. */
    const val PROMPT = 1301

    /** S-1107: the radio recordings under way (the foreground service's), and how one ended: one per station, its tag. */
    const val RADIO_RECORDING = 6110
    const val RADIO_RECORDING_ALERT = 6111

    /** S-709: what came of the transcription from speech. */
    const val WHISPER_DONE = 7090


    /** S-1103: a show's news, one per show (its feed's id). */
    @JvmField
    val PODCAST_NEWS = IdRange(20_000, 100_000)

    /** S-1010: what came of a conversion (done, stopped, waiting), one per job. */
    @JvmField
    val CONVERT_DONE = IdRange(200_000, 100_000)

    /** A drive the medialibrary had not seen, connected while no window is in front: one per drive. */
    @JvmField
    val NEW_DRIVE = IdRange(400_000, 100_000)
}

/** The ids [first] … [last]: one notification per item, each item's id its own ([idOf]). */
class IdRange(val first: Int, val size: Int) {
    init {
        require(size > 0 && first > 0 && first.toLong() + size <= Int.MAX_VALUE) { "ids $first + $size" }
    }

    val last: Int get() = first + size - 1

    operator fun contains(id: Int): Boolean = id in first..last

    /** Whether [other] holds an id of this range. */
    fun meets(other: IdRange): Boolean = first <= other.last && other.first <= last

    /**
     * The id of the item [key] (a feed's id, a job's hash): the same for the same item, so that
     * its next notification replaces the one before; items [size] apart share one.
     */
    fun idOf(key: Long): Int = first + key.mod(size.toLong()).toInt()

    fun idOf(key: Int): Int = idOf(key.toLong())

    override fun toString(): String = "$first…$last"
}

/**
 * 1.0: the notification channels of the video features 0.5 had (the player's recordings, the
 * subtitles' extraction and translation, the conversions and the HLS stream) and of TV Lab's
 * remux queue. Android keeps a
 * channel until the app deletes it: [delete] takes them out of the system's settings (a no-op
 * once they are gone). A binder call each: off the main thread.
 */
object RemovedChannels {
    internal val ids = listOf("nagmedar_recordings", "nagmedar_subtitle_extract", "nagmedar_translate", "nagmedar_hls", "nagmedar_transcode", "nagmedar_remux")

    fun delete(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ids.forEach { id -> runCatching { manager.deleteNotificationChannel(id) } }
    }
}
