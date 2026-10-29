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
 * The database's 21 → 22 drops the tables only the removed features used (the remux queue among
 * them) and notes the files the jobs held a permission to ([NagmedarDatabase.retiredGrants]):
 * those permissions go here, as far as nothing else in the app keeps them ([FolderGrants]), with
 * what TV Lab kept under files/tvlab (its profiles, the charts it shared, its bitrate cache).
 * 0.5's other video features (the subtitle tools, the conversion and the HLS stream, the player's
 * screenshots and recordings) leave their queued work, their files and their folders ([videoFeatures]).
 * Off the main thread: it reads preferences, the databases and the disk.
 */
object RetiredWork {
    private const val FILE = "nagmedar_retired"
    private const val TV_LAB_DONE = "tv_lab"
    private const val TABLES_DONE = "video_tables"
    private const val VIDEO_DONE = "video_features"

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
        if (!prefs.getBoolean(VIDEO_DONE, false)) videoFeatures(app, prefs)
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
        val settings = Settings.getInstance(app)
        val folders = runCatching { settings.getStringSet(TV_LAB_FOLDERS, null).orEmpty().toList() }.getOrDefault(emptyList())
        if (!folders.map { FolderGrants.releaseNow(app, it) }.all { it }) return
        settings.edit().remove(TV_LAB_PROFILE).remove(TV_LAB_FOLDERS).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { app.deleteSharedPreferences(TV_LAB_PREFS) }
        }
        prefs.edit().putBoolean(TV_LAB_DONE, true).apply()
    }

    /**
     * After 21 → 22: the database is opened first (which migrates it, whoever asked first), then the permissions it
     * noted go and the note with them. A database that cannot be opened now is tried again at the next start.
     */
    private fun tables(app: Context, prefs: SharedPreferences) {
        if (runCatching { NagmedarDatabase.get(app).openHelper.writableDatabase }.isFailure) return
        val note = NagmedarDatabase.retiredGrants(app)
        val granted = runCatching { if (note.exists()) note.readLines().filter { it.isNotBlank() } else emptyList() }.getOrElse { return }
        if (!granted.map { FolderGrants.releaseNow(app, it) }.all { it }) return
        runCatching { note.delete() }
        runCatching { File(app.filesDir, TV_LAB_FILES).deleteRecursively() }
        prefs.edit().putBoolean(TABLES_DONE, true).apply()
    }

    /**
     * 0.5's video features: their unique works and every request of their workers (WorkManager tags each with its
     * worker's class, which 1.0 cannot load: "Could not create Worker"), the files they kept in the app's storage
     * and the folders they were given. A folder's permission goes as far as nothing in 1.0 keeps it ([FolderGrants]);
     * when that cannot be told now, the step is tried again at the next start.
     */
    private fun videoFeatures(app: Context, prefs: SharedPreferences) {
        runCatching {
            val work = WorkManager.getInstance(app)
            VIDEO_WORK.forEach { work.cancelUniqueWork(it) }
            VIDEO_WORKERS.forEach { work.cancelAllWorkByTag(it) }
        }
        val external = runCatching { app.getExternalFilesDir(null) }.getOrNull()
        val files = listOf(
            File(app.noBackupFilesDir, "translate-models"),
            File(app.noBackupFilesDir, "translate-jobs"),
            File(app.noBackupFilesDir, "extract-jobs"),
            File(app.filesDir, "hls"),
            File(app.filesDir, "subtitles"),
            File(app.filesDir, "transcode"),
            File(app.cacheDir, "film-images"),
            File(app.cacheDir, "overlays"),
        ) + listOfNotNull(external?.let { File(it, "transcode") })
        files.forEach { runCatching { it.deleteRecursively() } }
        val settings = Settings.getInstance(app)
        val folders = VIDEO_FOLDERS.mapNotNull { key -> runCatching { settings.getString(key, null) }.getOrNull()?.takeIf(String::isNotBlank) }
        if (!folders.map { FolderGrants.releaseNow(app, it) }.all { it }) return
        settings.edit().apply { VIDEO_FOLDERS.forEach { remove(it) } }.apply()
        prefs.edit().putBoolean(VIDEO_DONE, true).apply()
    }

    /** The video features' unique works: the conversion's queue, the subtitle tools' extraction and translation, the models' downloads. */
    private val VIDEO_WORK = listOf(
        "nagmedar-transcode",
        "nagmedar-subtitle-extract",
        "nagmedar-subtitle-translate",
        "nagmedar-translate-model-opus-mt-tr-en-int8",
        "nagmedar-translate-model-opus-mt-tc-big-en-tr-f16",
    )

    /** Their workers' classes as 0.5 named them, with TV Lab's remux worker and Whisper's before it moved to ui.transcribe. */
    private val VIDEO_WORKERS = listOf(
        "app.nagmedar.ui.transcode.TranscodeWorker",
        "app.nagmedar.ui.player.subtitles.extract.SubtitleExtractWorker",
        "app.nagmedar.ui.player.subtitles.translate.SubtitleTranslateWorker",
        "app.nagmedar.ui.player.subtitles.translate.ModelDownloadWorker",
        "app.nagmedar.ui.player.subtitles.whisper.WhisperWorker",
        "app.nagmedar.ui.tvlab.remux.RemuxWorker",
    )

    /** The folders of the player's screenshots and recordings and of the local subtitle provider (Settings, in 0.5). */
    private val VIDEO_FOLDERS = listOf(
        "nagmedar_snapshot_folder",
        "nagmedar_recording_folder",
        "nagmedar_subtitle_folder",
    )
}
