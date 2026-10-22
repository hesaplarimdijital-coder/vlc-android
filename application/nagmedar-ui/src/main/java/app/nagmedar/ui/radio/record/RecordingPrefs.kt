package app.nagmedar.ui.radio.record

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.onboarding.FolderGrants

/**
 * S-1107: the settings of radio recording (Settings › Radio), kept in the app's preferences: the
 * recordings' folder, the shortest track kept, the padding, and the limits for the room. The
 * folder belongs to this device (a permission the system gave): a backup leaves it out.
 */
object RecordingPrefs {
    /** The folder chosen: a tree the system's picker gave; absent for the app's own music folder. */
    const val KEY_FOLDER = "nagmedar_radio_rec_folder"
    const val KEY_MIN_SECONDS = "nagmedar_radio_rec_min_seconds"
    const val KEY_KEEP_SHORT = "nagmedar_radio_rec_keep_short"
    const val KEY_PAD_BEFORE = "nagmedar_radio_rec_pad_before"
    const val KEY_PAD_AFTER = "nagmedar_radio_rec_pad_after"
    const val KEY_MIN_FREE_MB = "nagmedar_radio_rec_min_free_mb"
    const val KEY_MAX_TOTAL_MB = "nagmedar_radio_rec_max_total_mb"
    const val KEY_ON_LIMIT = "nagmedar_radio_rec_on_limit"

    /** Every key the settings use. */
    val keys: Set<String> = setOf(
        KEY_FOLDER, KEY_MIN_SECONDS, KEY_KEEP_SHORT, KEY_PAD_BEFORE, KEY_PAD_AFTER, KEY_MIN_FREE_MB, KEY_MAX_TOTAL_MB, KEY_ON_LIMIT,
    )

    fun read(prefs: SharedPreferences): RecordingSettings {
        val defaults = StorageLimits()
        val minFree = prefs.getInt(KEY_MIN_FREE_MB, (defaults.minFreeBytes / StorageLimits.MB).toInt()).coerceAtLeast(0)
        val maxTotal = prefs.getInt(KEY_MAX_TOTAL_MB, (defaults.maxTotalBytes / StorageLimits.MB).toInt()).coerceAtLeast(0)
        return RecordingSettings(
            folder = prefs.getString(KEY_FOLDER, null)?.takeIf { it.isNotEmpty() },
            minSeconds = prefs.getInt(KEY_MIN_SECONDS, RecordingSettings.DEFAULT_MIN_SECONDS).coerceIn(0, MAX_SECONDS),
            keepShort = prefs.getBoolean(KEY_KEEP_SHORT, false),
            padBeforeSeconds = prefs.getInt(KEY_PAD_BEFORE, 0).coerceIn(0, MAX_PADDING),
            padAfterSeconds = prefs.getInt(KEY_PAD_AFTER, 0).coerceIn(0, MAX_PADDING),
            limits = StorageLimits(
                minFreeBytes = minFree * StorageLimits.MB,
                maxTotalBytes = maxTotal * StorageLimits.MB,
                action = if (prefs.getString(KEY_ON_LIMIT, null) == LimitAction.PruneOldest.name) LimitAction.PruneOldest else LimitAction.Stop,
            ),
        )
    }

    fun write(editor: SharedPreferences.Editor, value: RecordingSettings): SharedPreferences.Editor = editor
        .putInt(KEY_MIN_SECONDS, value.minSeconds)
        .putBoolean(KEY_KEEP_SHORT, value.keepShort)
        .putInt(KEY_PAD_BEFORE, value.padBeforeSeconds)
        .putInt(KEY_PAD_AFTER, value.padAfterSeconds)
        .putInt(KEY_MIN_FREE_MB, (value.limits.minFreeBytes / StorageLimits.MB).toInt())
        .putInt(KEY_MAX_TOTAL_MB, (value.limits.maxTotalBytes / StorageLimits.MB).toInt())
        .putString(KEY_ON_LIMIT, value.limits.action.name)

    private const val MAX_SECONDS = 600
    private const val MAX_PADDING = 10
}

/**
 * A folder chosen for the radio recordings: written in from now on, the permission kept across
 * restarts. The folder before it lets go of its permission as far as nothing else uses it
 * (the player's recordings, the screenshots, the library…: [FolderGrants]).
 */
fun keepRadioRecordingFolder(context: Context, prefs: NagmedarPreferences, uri: Uri) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    val before = prefs.radioRecording.folder
    prefs.updateRadioRecordingFolder(uri.toString())
    if (before != null && before != uri.toString()) FolderGrants.release(context, before)
}

/** Back to the app's own music folder: the chosen folder lets go of its permission as far as nothing else uses it. */
fun forgetRadioRecordingFolder(context: Context, prefs: NagmedarPreferences) {
    val before = prefs.radioRecording.folder ?: return
    prefs.updateRadioRecordingFolder(null)
    FolderGrants.release(context, before)
}
