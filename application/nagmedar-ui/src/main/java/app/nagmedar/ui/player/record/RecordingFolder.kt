package app.nagmedar.ui.player.record

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.onboarding.FolderGrants

/** S-607: the recordings' folder (Settings › Recording): a tree the system's picker gave; none for the default ones. */
const val KEY_RECORDING_FOLDER = "nagmedar_recording_folder"

/**
 * A folder chosen for the recordings (Settings, or the card's "Choose a folder…"): written in from
 * now on, the permission kept across restarts. The folder before it lets go of its permission as
 * far as nothing else uses it (the screenshots, the library…: [FolderGrants]).
 */
fun keepRecordingFolder(context: Context, prefs: NagmedarPreferences, uri: Uri) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    val before = prefs.recordingFolder
    prefs.updateRecordingFolder(uri.toString())
    if (before != null && before != uri.toString()) FolderGrants.release(context, before)
}

/** Back to Movies, Music or Download/Nağmedar: the chosen folder lets go of its permission as far as nothing else uses it. */
fun forgetRecordingFolder(context: Context, prefs: NagmedarPreferences) {
    val before = prefs.recordingFolder ?: return
    prefs.updateRecordingFolder(null)
    FolderGrants.release(context, before)
}
