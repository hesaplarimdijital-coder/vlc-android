package app.nagmedar.ui.player.record

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.nagmedar.ui.NagmedarPreferences

/** S-607: the recordings' folder (Settings › Recording): a tree the system's picker gave; none for the default ones. */
const val KEY_RECORDING_FOLDER = "nagmedar_recording_folder"

/**
 * A folder chosen for the recordings (Settings, or the card's "Choose a folder…"): written in from
 * now on, the permission kept across restarts. The folder before it lets go of its writing
 * permission; its reading one stays when the library reads it too.
 */
fun keepRecordingFolder(context: Context, prefs: NagmedarPreferences, uri: Uri) {
    val resolver = context.contentResolver
    val modes = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching { resolver.takePersistableUriPermission(uri, modes) }
    val before = prefs.recordingFolder
    if (before != null && before != uri.toString()) release(context, prefs, before)
    prefs.updateRecordingFolder(uri.toString())
}

/** Back to Movies, Music or Download/Nağmedar: the chosen folder lets go of its writing permission. */
fun forgetRecordingFolder(context: Context, prefs: NagmedarPreferences) {
    val before = prefs.recordingFolder ?: return
    release(context, prefs, before)
    prefs.updateRecordingFolder(null)
}

/** The screenshots' folder and the library may hold the same tree: what they use stays. */
private fun release(context: Context, prefs: NagmedarPreferences, folder: String) {
    if (folder == prefs.snapshot.folder) return
    val release = if (folder in prefs.safFolders) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(folder), release) }
}
