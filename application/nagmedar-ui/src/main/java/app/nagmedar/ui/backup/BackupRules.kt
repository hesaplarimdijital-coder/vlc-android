package app.nagmedar.ui.backup

import android.content.SharedPreferences
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.player.record.KEY_RECORDING_FOLDER
import app.nagmedar.ui.player.snapshot.SnapshotSettings
import app.nagmedar.ui.player.subtitles.PrefsOnlineQuotaStore
import app.nagmedar.ui.tvlab.FileProfileStore
import org.videolan.tools.KEY_CURRENT_MAJOR_VERSION
import org.videolan.tools.KEY_CURRENT_SETTINGS_VERSION
import org.videolan.tools.KEY_CURRENT_SETTINGS_VERSION_AFTER_LIBVLC_INSTANTIATION
import org.videolan.tools.KEY_OPEN_SUBTITLES_LIMIT
import org.videolan.tools.KEY_OPEN_SUBTITLES_USER
import org.videolan.tools.NAGMEDAR_SAF_FOLDERS
import org.videolan.tools.NAGMEDAR_WINDOW_BOUNDS
import org.videolan.tools.Settings

/**
 * S-1502: which preferences a backup's settings hold. All of the app's, VLC's and Nağmedar's,
 * but what belongs to this device or this session, an account, and what other parts hold.
 */
object BackupSettings {
    /** When the last backup was made (ms), for Settings › Backup. */
    const val KEY_LAST_BACKUP = "nagmedar_last_backup"

    private val LEFT_OUT = setOf(
        // their own parts
        NagmedarPreferences.KEY_SHORTCUTS,
        FileProfileStore.KEY_SELECTED,
        // this device's: folder permissions, the window on this screen, a picked snapshot or recording folder
        NAGMEDAR_SAF_FOLDERS,
        NAGMEDAR_WINDOW_BOUNDS,
        SnapshotSettings.KEY_FOLDER,
        KEY_RECORDING_FOLDER,
        // this session's: what was run lately, the last backup
        NagmedarPreferences.KEY_PALETTE_RECENT,
        KEY_LAST_BACKUP,
        // an account: OpenSubtitles' sign-in and quotas
        KEY_OPEN_SUBTITLES_USER,
        KEY_OPEN_SUBTITLES_LIMIT,
        PrefsOnlineQuotaStore.KEY,
        // the settings' version, which the backup keeps on its own
        KEY_CURRENT_SETTINGS_VERSION,
        KEY_CURRENT_SETTINGS_VERSION_AFTER_LIBVLC_INSTANTIATION,
        KEY_CURRENT_MAJOR_VERSION,
    )

    /** What a backup leaves out: the keys above and what VLC's own export leaves (the last queue, remote access's keys…). */
    fun leftOut(): Set<String> = LEFT_OUT + Settings.getRestoreBlacklist()

    /** [all] the preferences as a backup holds them, by key; the [leftOut] ones and values of no preference type aside. */
    fun collect(all: Map<String, *>, leftOut: Set<String> = leftOut()): List<SettingValue> = all.entries
        .filter { (key, value) -> key !in leftOut && value != null && kept(value) }
        .sortedBy { it.key }
        .map { (key, value) -> SettingValue(key, if (value is Set<*>) value.map { it.toString() }.toSet() else value!!) }

    private fun kept(value: Any): Boolean =
        value is Boolean || value is Int || value is Long || value is Float || value is String || (value is Set<*> && value.all { it is String })

    /** [values] put with [editor], each as its type; the [leftOut] ones are not taken from a backup either. */
    fun write(values: List<SettingValue>, editor: SharedPreferences.Editor, leftOut: Set<String> = leftOut()): SharedPreferences.Editor {
        for ((key, value) in values) {
            if (key in leftOut) continue
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
                else -> editor.putString(key, value.toString())
            }
        }
        return editor
    }
}
