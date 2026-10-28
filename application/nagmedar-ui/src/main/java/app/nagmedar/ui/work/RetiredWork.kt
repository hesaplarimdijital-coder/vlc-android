package app.nagmedar.ui.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.work.WorkManager
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.onboarding.FolderGrants
import org.videolan.tools.Settings
import java.io.File

/**
 * Nağmedar 1.0: what a removed feature left behind on an upgraded install, taken away once, at
 * the main process's start. TV Lab (removed in 1.0) queued its remuxes as the unique work
 * [TV_LAB_WORK] and set its night run as an alarm to its receiver; no code is behind either any
 * more, so the worker would fail and the alarm reach nothing (its notification channel goes with
 * [app.nagmedar.ui.notify.RemovedChannels]).
 * The database's 20 → 21 drops the tables only the removed features used (the remux queue among
 * them) and notes the files the jobs held a permission to ([NagmedarDatabase.retiredGrants]):
 * those permissions go here, as far as nothing else in the app keeps them ([FolderGrants]), with
 * what TV Lab kept under files/tvlab (its profiles, the charts it shared, its bitrate cache).
 * Off the main thread: it reads preferences, the databases and the disk.
 */
object RetiredWork {
    private const val FILE = "nagmedar_retired"
    private const val TV_LAB_DONE = "tv_lab"
    private const val TABLES_DONE = "video_tables"

    /** What TV Lab kept in the app's files: its profiles, the charts it shared, its bitrate cache. */
    private const val TV_LAB_FILES = "tvlab"

    /** The TV Lab's remux queue worker (RemuxWorker.NAME before 1.0). */
    private const val TV_LAB_WORK = "nagmedar-remux"

    /** The remux queue's state (held, the night run's time). */
    private const val TV_LAB_PREFS = "nagmedar_remux_queue"

    /** The profile chosen last, in the app's preferences: a backup's settings would carry it now. */
    private const val TV_LAB_PROFILE = "nagmedar_tvlab_profile"

    /** The batch folders it was given ([FolderGrants.keptKey] of its holder): their permissions go unless another holder keeps them. */
    private const val TV_LAB_FOLDERS = "nagmedar_kept_trees_tvlab"

    /** The night run's alarm: its receiver, action and request code, as the PendingIntent was made. */
    private const val TV_LAB_RECEIVER = "app.nagmedar.ui.tvlab.remux.RemuxNightReceiver"
    private const val TV_LAB_ACTION = "app.nagmedar.action.REMUX_NIGHT_START"
    private const val TV_LAB_REQUEST = 1210

    fun onProcessStart(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(TV_LAB_DONE, false)) tvLab(app, prefs)
        if (!prefs.getBoolean(TABLES_DONE, false)) tables(app, prefs)
    }

    private fun tvLab(app: Context, prefs: SharedPreferences) {
        runCatching { WorkManager.getInstance(app).cancelUniqueWork(TV_LAB_WORK) }
        runCatching {
            val intent = Intent(TV_LAB_ACTION).setComponent(ComponentName(app, TV_LAB_RECEIVER))
            PendingIntent.getBroadcast(app, TV_LAB_REQUEST, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let { pending ->
                app.getSystemService(AlarmManager::class.java)?.cancel(pending)
                pending.cancel()
            }
        }
        runCatching {
            val settings = Settings.getInstance(app)
            val folders = settings.getStringSet(TV_LAB_FOLDERS, null).orEmpty().toList()
            settings.edit().remove(TV_LAB_PROFILE).remove(TV_LAB_FOLDERS).apply()
            folders.forEach { FolderGrants.release(app, it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { app.deleteSharedPreferences(TV_LAB_PREFS) }
        }
        prefs.edit().putBoolean(TV_LAB_DONE, true).apply()
    }

    /**
     * After 20 → 21: the database is opened first (which migrates it, whoever asked first), then the permissions it
     * noted go and the note with them. A database that cannot be opened now is tried again at the next start.
     */
    private fun tables(app: Context, prefs: SharedPreferences) {
        if (runCatching { NagmedarDatabase.get(app).openHelper.writableDatabase }.isFailure) return
        runCatching {
            val note = NagmedarDatabase.retiredGrants(app)
            if (note.exists()) {
                note.readLines().filter { it.isNotBlank() }.forEach { FolderGrants.release(app, it) }
                note.delete()
            }
        }
        runCatching { File(app.filesDir, TV_LAB_FILES).deleteRecursively() }
        prefs.edit().putBoolean(TABLES_DONE, true).apply()
    }
}
