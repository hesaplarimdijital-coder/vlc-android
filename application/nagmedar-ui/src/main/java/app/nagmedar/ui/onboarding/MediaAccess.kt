package app.nagmedar.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.runtime.Immutable
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.ACTION_DISCOVER
import org.videolan.resources.AndroidDevices
import org.videolan.resources.EXTRA_PATH
import org.videolan.resources.MEDIAPARSING_SERVICE
import org.videolan.resources.util.launchForeground
import org.videolan.resources.util.startMedialibrary
import org.videolan.tools.KEY_MEDIALIBRARY_SCAN
import org.videolan.tools.ML_SCAN_ON
import org.videolan.tools.putSingle
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.util.Permissions

/** Media access for the first-run flow (S-309): runtime permissions, all-files access, notifications. */
object MediaAccess {

    /** Runtime permissions that let the medialibrary scan video and audio. */
    val mediaPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasMediaAccess(context: Context): Boolean = Permissions.canReadStorage(context, skipCache = true)

    /** Android 11 and later ask for all-files access on a system settings page. */
    val canAskAllFiles: Boolean get() = Build.VERSION.SDK_INT >= 30

    /** All files: subtitles and lyrics next to the media, and everything on external drives. */
    fun hasAllFiles(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else hasMediaAccess(context)

    fun allFilesSettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun appSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** Android 13 and later ask before showing notifications. */
    val notificationPermission: String?
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null

    fun hasNotifications(context: Context): Boolean = Permissions.canSendNotifications(context)
}

/** A place the medialibrary can scan: this device's shared storage or a removable drive. */
@Immutable
data class StorageSource(val path: String, val label: String, val removable: Boolean, val totalBytes: Long)

/** This device's shared storage, then the mounted removable drives (USB disks, SD cards). */
fun storageSources(context: Context): List<StorageSource> {
    val internal = AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY
    val drives = if (Build.VERSION.SDK_INT >= 30) {
        context.getSystemService(StorageManager::class.java)?.storageVolumes.orEmpty()
            .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { volume ->
                val directory = volume.directory ?: return@mapNotNull null
                StorageSource(directory.path, volume.getDescription(context), removable = true, totalBytes = totalBytes(directory.path))
            }
    } else {
        emptyList()
    }
    return listOf(StorageSource(internal, label = "", removable = false, totalBytes = totalBytes(internal))) + drives
}

private fun totalBytes(path: String): Long = runCatching { StatFs(path).totalBytes }.getOrDefault(0L)

/**
 * Starts the medialibrary on the chosen [paths] the first time, or adds them to a library that
 * already runs (the flow can be repeated from Settings). Nothing happens without media access.
 */
fun scanSources(context: Context, paths: List<String>) {
    if (paths.isEmpty() || !MediaAccess.hasMediaAccess(context)) return
    if (Medialibrary.getInstance().isStarted) {
        for (path in paths) {
            context.launchForeground(Intent(ACTION_DISCOVER).setClassName(context, MEDIAPARSING_SERVICE).putExtra(EXTRA_PATH, path))
        }
    } else {
        MediaParsingService.preselectedStorages.apply {
            clear()
            addAll(paths)
        }
        org.videolan.tools.Settings.getInstance(context).putSingle(KEY_MEDIALIBRARY_SCAN, ML_SCAN_ON)
        context.startMedialibrary(firstRun = true, upgrade = true, parse = true)
    }
}
