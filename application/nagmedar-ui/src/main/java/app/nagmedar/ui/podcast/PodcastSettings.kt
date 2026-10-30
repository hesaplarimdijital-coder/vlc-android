package app.nagmedar.ui.podcast

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import app.nagmedar.ui.playback.PlaybackRules

/**
 * S-1103, S-1104: Settings › Podcasts, the choices that are for all shows: how often feeds are read and on what
 * network, notifications, the storage downloads may take and where they are put, and the skip buttons' seconds. What
 * belongs to one show (its speed, intro and outro, notifications, automatic download) is in its row in Room.
 */
@Immutable
data class PodcastSettings(
    /** One of [REFRESH_HOURS]. */
    val refreshHours: Int = 6,
    /** Feeds are read only on a network that is not metered (Wi-Fi). */
    val refreshUnmeteredOnly: Boolean = false,
    /** New episodes are told in a notification (for the shows that have theirs on). */
    val notifications: Boolean = true,
    /** The most the downloads may take, in GB; 0 for no limit. One of [STORAGE_GB]. */
    val storageLimitGb: Int = 0,
    /** Played episodes are deleted this many days after; 0 keeps them. One of [PLAYED_DAYS]. */
    val deletePlayedAfterDays: Int = 0,
    /** The folder (a document tree's address) downloads are put in; empty for the app's own storage. */
    val downloadFolder: String = "",
    /** The seconds the skip back and skip forward buttons go. */
    val skipBackSeconds: Int = 15,
    val skipForwardSeconds: Int = 30,
) {
    val storageLimitBytes: Long get() = storageLimitGb * BYTES_PER_GB

    companion object {
        val REFRESH_HOURS: List<Int> = listOf(1, 3, 6, 12, 24)
        val STORAGE_GB: List<Int> = listOf(0, 1, 2, 5, 10, 20, 50)
        val PLAYED_DAYS: List<Int> = listOf(0, 1, 3, 7, 14, 30)
        const val BYTES_PER_GB = 1_000_000_000L

        /** [value] if the list offers it, else the offer nearest to it. */
        internal fun nearest(value: Int, offers: List<Int>): Int = if (value in offers) value else offers.minBy { kotlin.math.abs(it - value) }
    }
}

/** S-1103: [PodcastSettings] in the app's shared preferences, next to the others. */
class PodcastSettingsStore(private val prefs: SharedPreferences) {
    fun read(): PodcastSettings {
        val defaults = PodcastSettings()
        return PodcastSettings(
            refreshHours = PodcastSettings.nearest(prefs.getInt(KEY_REFRESH_HOURS, defaults.refreshHours), PodcastSettings.REFRESH_HOURS),
            refreshUnmeteredOnly = prefs.getBoolean(KEY_REFRESH_UNMETERED, defaults.refreshUnmeteredOnly),
            notifications = prefs.getBoolean(KEY_NOTIFICATIONS, defaults.notifications),
            storageLimitGb = PodcastSettings.nearest(prefs.getInt(KEY_STORAGE_GB, defaults.storageLimitGb), PodcastSettings.STORAGE_GB),
            deletePlayedAfterDays = PodcastSettings.nearest(prefs.getInt(KEY_PLAYED_DAYS, defaults.deletePlayedAfterDays), PodcastSettings.PLAYED_DAYS),
            downloadFolder = prefs.getString(KEY_FOLDER, null).orEmpty(),
            skipBackSeconds = PodcastSettings.nearest(prefs.getInt(KEY_SKIP_BACK, defaults.skipBackSeconds), PlaybackRules.BACK_SECONDS),
            skipForwardSeconds = PodcastSettings.nearest(prefs.getInt(KEY_SKIP_FORWARD, defaults.skipForwardSeconds), PlaybackRules.FORWARD_SECONDS),
        )
    }

    fun write(settings: PodcastSettings) {
        prefs.edit()
            .putInt(KEY_REFRESH_HOURS, settings.refreshHours)
            .putBoolean(KEY_REFRESH_UNMETERED, settings.refreshUnmeteredOnly)
            .putBoolean(KEY_NOTIFICATIONS, settings.notifications)
            .putInt(KEY_STORAGE_GB, settings.storageLimitGb)
            .putInt(KEY_PLAYED_DAYS, settings.deletePlayedAfterDays)
            .putString(KEY_FOLDER, settings.downloadFolder)
            .putInt(KEY_SKIP_BACK, settings.skipBackSeconds)
            .putInt(KEY_SKIP_FORWARD, settings.skipForwardSeconds)
            .apply()
    }

    companion object {
        const val KEY_REFRESH_HOURS = "nagmedar_podcast_refresh_hours"
        const val KEY_REFRESH_UNMETERED = "nagmedar_podcast_refresh_unmetered"
        const val KEY_NOTIFICATIONS = "nagmedar_podcast_notifications"
        const val KEY_STORAGE_GB = "nagmedar_podcast_storage_gb"
        const val KEY_PLAYED_DAYS = "nagmedar_podcast_played_days"
        const val KEY_FOLDER = "nagmedar_podcast_download_folder"
        const val KEY_SKIP_BACK = "nagmedar_podcast_skip_back"
        const val KEY_SKIP_FORWARD = "nagmedar_podcast_skip_forward"
    }
}
