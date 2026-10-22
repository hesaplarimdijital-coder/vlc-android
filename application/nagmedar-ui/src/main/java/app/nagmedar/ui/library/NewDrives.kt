package app.nagmedar.ui.library

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import app.nagmedar.ui.notify.NotificationIds
import org.videolan.tools.Notices
import org.videolan.vlc.util.NewStorageHandler
import java.io.File

/**
 * Classic UI: a drive the medialibrary had not seen was connected. With a window of the app in
 * front it says so there, with a button to the library's folders (Settings › Library); else a
 * notification does, whose tap opens them. VLC's DialogActivity ("scan this drive?") is not
 * started any more.
 */
object NewDrives : NewStorageHandler {
    /** Extra of the Nağmedar activity: Settings opens on the library's folders. */
    const val EXTRA_LIBRARY_SOURCES = "nagmedar_library_sources"

    private const val CHANNEL = "nagmedar_drives"

    override fun newStorage(context: Context, path: String) {
        val app = context.applicationContext
        val name = driveName(app, path)
        val text = app.getString(R.string.nag_drive_new, name)
        if (Notices.host.value != null) {
            Notices.show(app, text, long = true, actionLabel = app.getString(R.string.nag_drive_open), action = {
                runCatching { app.startActivity(open(app)) }
            })
            return
        }
        notify(app, text, path)
    }

    private fun open(context: Context): Intent = Intent(context, NagmedarActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_LIBRARY_SOURCES, true)

    /** What the system calls the drive ("SanDisk USB"), else its folder's name. */
    private fun driveName(context: Context, path: String): String {
        val volume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { context.getSystemService(StorageManager::class.java)?.getStorageVolume(File(path))?.getDescription(context) }.getOrNull()
        } else {
            null
        }
        return volume?.takeIf { it.isNotBlank() } ?: File(path).name
    }

    private fun notify(context: Context, text: String, path: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.nag_drive_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        // one per drive; its tap's PendingIntent is its own (NotificationIds)
        val id = NotificationIds.NEW_DRIVE.idOf(path.hashCode())
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(org.videolan.resources.R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(text)
            .setContentText(context.getString(R.string.nag_drive_new_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.nag_drive_new_text)))
            .setContentIntent(PendingIntent.getActivity(context, id, open(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        runCatching { manager.notify(id, builder.build()) }
    }
}
