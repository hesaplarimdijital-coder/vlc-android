package app.nagmedar.ui.backup

import android.content.SharedPreferences
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.convert.ConvertOptions
import app.nagmedar.ui.library.LibrarySources
import app.nagmedar.ui.onboarding.FolderGrants
import app.nagmedar.ui.podcast.PodcastSettingsStore
import app.nagmedar.ui.radio.record.RecordingPrefs
import app.nagmedar.ui.scrobble.ScrobbleSettings
import app.nagmedar.ui.transcribe.WhisperPrefs
import app.nagmedar.ui.tvlab.FileProfileStore
import org.videolan.tools.KEY_CURRENT_MAJOR_VERSION
import org.videolan.tools.KEY_CURRENT_SETTINGS_VERSION
import org.videolan.tools.KEY_CURRENT_SETTINGS_VERSION_AFTER_LIBVLC_INSTANTIATION
import org.videolan.tools.KEY_OPEN_SUBTITLES_LIMIT
import org.videolan.tools.KEY_OPEN_SUBTITLES_USER
import org.videolan.tools.KEY_RESTRICT_SETTINGS
import org.videolan.tools.KEY_SAFE_MODE
import org.videolan.tools.KEY_SAFE_MODE_PIN
import org.videolan.tools.KEY_SET_LOCALE
import org.videolan.tools.NAGMEDAR_SAF_FOLDERS
import org.videolan.tools.NAGMEDAR_WINDOW_BOUNDS
import org.videolan.tools.PREF_TV_UI
import org.videolan.tools.Settings

/**
 * S-1502: which preferences a backup's settings hold. All of the app's, VLC's and Nağmedar's,
 * but what belongs to this device or this session, an account, and what other parts hold.
 */
object BackupSettings {
    /** When the last backup was made (ms), for Settings › Backup. */
    const val KEY_LAST_BACKUP = "nagmedar_last_backup"

    /**
     * 1.0: the keys of the video features 0.5 had (the screenshots' and recordings' folders, the
     * OpenSubtitles quota, the local subtitle folder, the subtitle jobs' time limits): an updated
     * device still has them, and a backup does not carry them about.
     */
    private val REMOVED_FEATURES = arrayOf(
        "nagmedar_snapshot_folder",
        "nagmedar_recording_folder",
        "nagmedar_opensubtitles_quota",
        "nagmedar_subtitle_folder",
        "nagmedar_translate_timed_out",
        "nagmedar_extract_timed_out",
    )

    private val LEFT_OUT = setOf(
        // their own parts
        NagmedarPreferences.KEY_SHORTCUTS,
        FileProfileStore.KEY_SELECTED,
        // this device's: folder permissions, the window on this screen, a picked converter's or podcast download folder
        NAGMEDAR_SAF_FOLDERS,
        NAGMEDAR_WINDOW_BOUNDS,
        ConvertOptions.KEY_FOLDER,
        PodcastSettingsStore.KEY_FOLDER,
        RecordingPrefs.KEY_FOLDER,
        // this device's: the folders each section of the library shows (paths and picked trees)
        *LibrarySources.keys.toTypedArray(),
        // this device's: the picked folders the TV Lab's batch and the lyrics editor were given
        *FolderGrants.keptKeys.toTypedArray(),
        // this session's: what was run lately, the last backup
        NagmedarPreferences.KEY_PALETTE_RECENT,
        KEY_LAST_BACKUP,
        // an account: OpenSubtitles' sign-in and quotas (VLC's, still in its preferences)
        KEY_OPEN_SUBTITLES_USER,
        KEY_OPEN_SUBTITLES_LIMIT,
        // what the video features of 0.5 left in an updated device's preferences: its folders, a quota, its jobs
        *REMOVED_FEATURES,
        // this device's: the transcription's last job and what its GPU did (S-709)
        WhisperPrefs.KEY_SEEN,
        WhisperPrefs.KEY_TIMED_OUT,
        WhisperPrefs.KEY_GPU_RUNNING,
        WhisperPrefs.KEY_GPU_STOPPED,
        // an account: the scrobbling's sign-ins (their secrets are sealed by this device's keystore) and switches
        *ScrobbleSettings.keys.toTypedArray(),
        // a TV's: VLC's TV interface and its language, which a phone, a tablet or DeX ignores
        PREF_TV_UI,
        KEY_SET_LOCALE,
        // this device's: the parental control's PIN and what it guards; a restore would undo them without the PIN
        KEY_SAFE_MODE_PIN,
        KEY_RESTRICT_SETTINGS,
        KEY_SAFE_MODE,
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
