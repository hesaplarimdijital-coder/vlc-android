package app.nagmedar.ui.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.library.LibrarySources
import app.nagmedar.ui.podcast.PodcastSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.videolan.tools.Settings
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Nağmedar 1.0: what the removed features left on an install upgraded from 0.5 goes at the next start, once: TV Lab's
 * queued work, night alarm and folders, the dropped tables' permissions, and the video features' work, files and
 * folders, while what 1.0 still uses stays.
 */
@RunWith(AndroidJUnit4::class)
class RetiredWorkTest {

    private val app = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun workManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    /** As the app does it: on a background thread (the database is not read on the main one). */
    private fun start() {
        var failure: Throwable? = null
        thread { runCatching { RetiredWork.onProcessStart(app) }.onFailure { failure = it } }.join()
        failure?.let { throw it }
    }

    private fun tree(name: String) = "content://com.android.externalstorage.documents/tree/primary%3A$name"

    private fun grant(uri: String, modes: Int) = app.contentResolver.takePersistableUriPermission(Uri.parse(uri), modes)

    /** [uri]'s persisted permission: reading and writing, or null when it has none. */
    private fun granted(uri: String): Pair<Boolean, Boolean>? =
        app.contentResolver.persistedUriPermissions.firstOrNull { it.uri.toString() == uri }?.let { it.isReadPermission to it.isWritePermission }

    private fun file(dir: File?, path: String): File = File(requireNotNull(dir), path).apply { parentFile?.mkdirs(); writeBytes(ByteArray(4)) }

    /** A request as WorkManager keeps it: tagged with its worker's class, waiting, as [name]'s unique work when it has one. */
    private fun queued(name: String?, worker: String): UUID {
        val request = OneTimeWorkRequestBuilder<StandInWorker>().addTag(worker).setInitialDelay(1, TimeUnit.DAYS).build()
        val work = WorkManager.getInstance(app)
        if (name == null) work.enqueue(request).result.get() else work.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request).result.get()
        return request.id
    }

    private fun state(id: UUID): WorkInfo.State? = WorkManager.getInstance(app).getWorkInfoById(id).get()?.state

    @Test
    fun theDroppedJobsNoteAndTvLabsFilesGoOnce() {
        val profiles = File(app.filesDir, "tvlab/profiles").apply { mkdirs() }
        File(profiles, "user-1.json").writeText("{}")
        File(app.filesDir, "tvlab/share").apply { mkdirs() }.resolve("chart.png").writeBytes(ByteArray(4))
        val note = NagmedarDatabase.retiredGrants(app).apply { writeText("content://docs/document/film.mkv\ncontent://docs/document/film%20(remux).mkv\n") }
        val kept = File(app.filesDir, "covers").apply { mkdirs() }.resolve("a.user").apply { writeBytes(ByteArray(4)) }

        start()
        assertFalse(File(app.filesDir, "tvlab").exists())
        assertFalse(note.exists())
        assertTrue("the app's other files stay", kept.exists())
    }

    @Test
    fun theVideoFeaturesFilesGoAndWhatOneZeroUsesStays() {
        val gone = listOf(
            file(app.noBackupFilesDir, "translate-models/opus-mt-tr-en-int8/model.bin"),
            file(app.noBackupFilesDir, "translate-models/opus-mt-tc-big-en-tr-f16/model.bin.part"),
            file(app.noBackupFilesDir, "translate-jobs/0b7c.sub"),
            file(app.noBackupFilesDir, "extract-jobs/job-3/track-2.srt"),
            file(app.filesDir, "hls/session-1/index.m3u8"),
            file(app.filesDir, "subtitles/3f2a9c.srt"),
            file(app.filesDir, "transcode/job-7.mkv"),
            file(app.getExternalFilesDir(null), "transcode/job-8.mkv"),
            file(app.cacheDir, "film-images/w342/poster.jpg"),
            file(app.cacheDir, "overlays/logo-1.png"),
        )
        val kept = listOf(
            file(app.noBackupFilesDir, "whisper-models/ggml-small.bin"),
            file(app.noBackupFilesDir, "whisper-models/ggml-base.bin.part"),
            file(app.filesDir, "podcast-transcripts/12.vtt"),
            file(app.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "Bolum 12.mp3"),
            file(app.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Radio/TRT Nagme/kayit.mp3"),
            file(app.filesDir, "covers/a.user"),
            file(app.filesDir, "convert/job-1.json"),
            file(app.getExternalFilesDir(null), "Subtitles/film.tr.srt"),
        )

        start()
        gone.forEach { assertFalse("$it goes", it.exists()) }
        listOf("translate-models", "translate-jobs", "extract-jobs").forEach { assertFalse(File(app.noBackupFilesDir, it).exists()) }
        listOf("hls", "subtitles", "transcode").forEach { assertFalse(File(app.filesDir, it).exists()) }
        kept.forEach { assertTrue("$it stays", it.exists()) }
    }

    @Test
    fun theRemovedFeaturesWorkIsCancelledAndOneZerosStays() {
        val removed = listOf(
            queued("nagmedar-transcode", "app.nagmedar.ui.transcode.TranscodeWorker"),
            queued("nagmedar-subtitle-extract", "app.nagmedar.ui.player.subtitles.extract.SubtitleExtractWorker"),
            queued("nagmedar-subtitle-translate", "app.nagmedar.ui.player.subtitles.translate.SubtitleTranslateWorker"),
            queued("nagmedar-translate-model-opus-mt-tr-en-int8", "app.nagmedar.ui.player.subtitles.translate.ModelDownloadWorker"),
            // a model 0.5 did not list by name: its worker's class still finds it
            queued("nagmedar-translate-model-other", "app.nagmedar.ui.player.subtitles.translate.ModelDownloadWorker"),
            queued("nagmedar-remux", "app.nagmedar.ui.tvlab.remux.RemuxWorker"),
        )
        // 0.5's Whisper job, under the name 1.0's still uses: its old class finds it, and 1.0's own job is left
        val oldWhisper = queued("nagmedar-whisper", "app.nagmedar.ui.player.subtitles.whisper.WhisperWorker")
        val whisper = queued(null, "app.nagmedar.ui.transcribe.WhisperWorker")
        val podcasts = queued(null, "app.nagmedar.ui.podcast.PodcastRefreshWorker")
        removed.forEach { assertEquals(WorkInfo.State.ENQUEUED, state(it)) }

        start()
        (removed + oldWhisper).forEach { assertEquals(WorkInfo.State.CANCELLED, state(it)) }
        assertEquals(WorkInfo.State.ENQUEUED, state(whisper))
        assertEquals(WorkInfo.State.ENQUEUED, state(podcasts))
    }

    @Test
    fun theVideoFoldersLetGoAsFarAsNothingInOneZeroKeepsThem() {
        val both = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val music = tree("RetiredMuzik")
        val podcasts = tree("RetiredPodcast")
        val subtitles = tree("RetiredAltyazi")
        val batch = tree("RetiredToplu")
        val film = "content://com.android.providers.media.documents/document/video%3A12"
        val other = tree("RetiredBaska")
        grant(music, both)
        grant(podcasts, both)
        grant(subtitles, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        grant(batch, both)
        grant(film, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        grant(other, both)
        Settings.getInstance(app).edit()
            .putString(LibrarySources.key(LibrarySection.Music), "+$music")
            .putString(PodcastSettingsStore.KEY_FOLDER, podcasts)
            // 0.5's recordings went into the music folder, its screenshots into the podcasts'
            .putString("nagmedar_recording_folder", music)
            .putString("nagmedar_snapshot_folder", podcasts)
            .putString("nagmedar_subtitle_folder", subtitles)
            .putStringSet("nagmedar_kept_trees_tvlab", setOf(batch))
            .putString("nagmedar_tvlab_profile", "user-1")
            .commit()
        NagmedarDatabase.retiredGrants(app).writeText("$film\n")

        start()
        assertEquals("the music section keeps reading; the recordings' writing goes", true to false, granted(music))
        assertEquals("the podcasts' downloads keep both", true to true, granted(podcasts))
        assertNull(granted(subtitles))
        assertNull(granted(batch))
        assertNull(granted(film))
        assertEquals("a folder none of them had stays", true to true, granted(other))
        val settings = Settings.getInstance(app)
        listOf("nagmedar_recording_folder", "nagmedar_snapshot_folder", "nagmedar_subtitle_folder", "nagmedar_kept_trees_tvlab", "nagmedar_tvlab_profile")
            .forEach { assertFalse("$it goes", settings.contains(it)) }
        assertEquals("+$music", settings.getString(LibrarySources.key(LibrarySection.Music), null))
        assertEquals(podcasts, settings.getString(PodcastSettingsStore.KEY_FOLDER, null))
        settings.edit().remove(LibrarySources.key(LibrarySection.Music)).remove(PodcastSettingsStore.KEY_FOLDER).commit()
    }

    @Test
    fun theTvLabNightAlarmIsCancelled() {
        // as 0.5's RemuxSchedule made it: Intent(context, RemuxNightReceiver::class.java).setAction(ACTION_START), 1210, immutable
        fun night(flags: Int) = PendingIntent.getBroadcast(
            app,
            1210,
            Intent().setClassName(app, "app.nagmedar.ui.tvlab.remux.RemuxNightReceiver").setAction("app.nagmedar.action.REMUX_NIGHT_START"),
            PendingIntent.FLAG_IMMUTABLE or flags,
        )
        val alarms = app.getSystemService(AlarmManager::class.java)
        alarms.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(6), night(PendingIntent.FLAG_UPDATE_CURRENT))
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        assertNotNull(night(PendingIntent.FLAG_NO_CREATE))

        start()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertNull(night(PendingIntent.FLAG_NO_CREATE))
    }

    @Test
    fun eachStepRunsOnce() {
        start()
        // what is there at a later start is left alone: TV Lab's, the dropped tables', the video features'
        val tvLab = File(app.filesDir, "tvlab").apply { mkdirs() }.resolve("later").apply { writeText("") }
        val profile = "nagmedar_tvlab_profile"
        val note = NagmedarDatabase.retiredGrants(app).apply { writeText("content://docs/document/later.mkv\n") }
        val models = file(app.noBackupFilesDir, "translate-models/later/model.bin")
        val folder = "nagmedar_subtitle_folder"
        Settings.getInstance(app).edit().putString(profile, "later").putString(folder, tree("RetiredSonra")).commit()
        val work = queued("nagmedar-transcode", "app.nagmedar.ui.transcode.TranscodeWorker")

        start()
        assertTrue(tvLab.exists())
        assertTrue(note.exists())
        assertTrue(models.exists())
        assertTrue(Settings.getInstance(app).contains(profile))
        assertTrue(Settings.getInstance(app).contains(folder))
        assertEquals(WorkInfo.State.ENQUEUED, state(work))
        note.delete()
        Settings.getInstance(app).edit().remove(profile).remove(folder).commit()
    }
}

/** Stands in for a worker 0.5 had: these tests only queue it, never run it. */
class StandInWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = Result.success()
}
