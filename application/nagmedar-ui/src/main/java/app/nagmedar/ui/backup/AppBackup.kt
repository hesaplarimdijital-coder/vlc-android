package app.nagmedar.ui.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.covers.CoverCenter
import app.nagmedar.ui.input.ShortcutOverrides
import app.nagmedar.ui.input.toText
import app.nagmedar.ui.music.MediaLibraryPlaylists
import app.nagmedar.ui.radio.record.AndroidTrackFiles
import app.nagmedar.ui.radio.record.RecordingAlarms
import app.nagmedar.ui.radio.record.RecordingSchedule
import app.nagmedar.ui.radio.record.RecordingStore
import app.nagmedar.ui.radio.record.Repeat
import app.nagmedar.ui.sleep.Alarms
import app.nagmedar.ui.tvlab.DeviceProfileCodec
import app.nagmedar.ui.tvlab.TvLabSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.AppScope
import org.videolan.tools.NAGMEDAR_ALARM
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.util.EqualizerUtil
import org.videolan.vlc.util.VersionMigration
import java.io.IOException

/** S-1502: what a restore brought back. */
data class RestoreReport(
    /** How many of each part's things were taken: settings, shortcuts, profiles, lists, tracks, files, covers, schedules. */
    val restored: Map<BackupPart, Int>,
    /** Playlist lines whose files this library does not have (yet: a scan may find them). */
    val missingTracks: Int = 0,
    /** The parts that could not be written. */
    val failed: Set<BackupPart> = emptySet(),
)

/** S-1502: the playlists a backup holds: the medialibrary's in the app, a list in the tests. */
interface BackupPlaylists {
    suspend fun lists(): List<BackupPlaylist>

    /** [list] made again: over the one of its name, unless that one is the same. Its lines not found here; null when it could not be made. */
    suspend fun restore(list: BackupPlaylist): Int?
}

/** S-1502: the medialibrary's playlists (S-804), their lines found as an imported playlist file's are. */
class MediaLibraryBackupPlaylists(context: Context) : BackupPlaylists {
    private val store = MediaLibraryPlaylists(context.applicationContext)

    override suspend fun lists(): List<BackupPlaylist> = store.playlists().map { BackupPlaylist(it.name, store.entries(it.id)) }

    override suspend fun restore(list: BackupPlaylist): Int? {
        val ids = store.resolve(list.entries)
        val found = ids.filterNotNull()
        val missing = ids.size - found.size
        val same = store.playlists().firstOrNull { it.name == list.name }
        if (same != null && store.tracks(same.id).map { it.id } == found) return missing
        // the new one made before the old one goes: a list is never lost on the way
        store.create(list.name, found) ?: return null
        if (same != null) store.delete(same.id)
        return missing
    }
}

/**
 * S-1502: the app's side of a backup: what a backup holds, read from the preferences, Nağmedar's
 * database, the playlists and the app's folders; and written back part by part. What is restored
 * joins what is here: a setting, a shortcut, a profile or a list of the same name is replaced,
 * the statistics are merged ([app.nagmedar.data.RestoreMerge]), the rest stays.
 */
class AppBackup(
    context: Context,
    private val db: NagmedarDatabase = NagmedarDatabase.get(context),
    private val playlists: BackupPlaylists = MediaLibraryBackupPlaylists(context),
) {
    private val context = context.applicationContext
    private val prefs: SharedPreferences = Settings.getInstance(this.context)
    private val profiles by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { TvLabSession.profileStore(this.context) }

    /** Everything a backup holds, as it is now. */
    suspend fun make(now: Long = System.currentTimeMillis()): Backup = withContext(Dispatchers.IO) {
        Backup(
            createdAt = now,
            appVersion = BuildConfig.VLC_VERSION_NAME,
            settingsVersion = VersionMigration.getCurrentVersion(),
            settings = BackupSettings.collect(prefs.all),
            equalizers = runCatching { EqualizerUtil.getEqualizerExportString(context) }.getOrNull(),
            shortcuts = ShortcutOverrides.parse(prefs.getString(NagmedarPreferences.KEY_SHORTCUTS, null))
                .entries.associate { (id, chord) -> id.name to chord?.toText().orEmpty() },
            profiles = profiles.all().filter { profiles.saved(it.id) }.map(DeviceProfileCodec::encode),
            selectedProfile = profiles.selected,
            playlists = playlists.lists(),
            smartLists = db.smartPlaylists().all(),
            stats = db.trackStats().all(),
            lyrics = db.lyrics().kept(),
            fileSettings = db.mediaSettings().all(),
            covers = CoverCenter.finder(context).chosenFiles().mapNotNull { file ->
                try {
                    BackupCover(file.name, file.readBytes())
                } catch (e: IOException) {
                    null
                }
            },
            latency = db.latencyProfiles().all(),
            radioSchedules = recordings().scheduleList(),
        )
    }

    /** [backup] written to the file at [uri]; when it was made is remembered. False when it could not be written. */
    suspend fun write(backup: Backup, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val stream = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull() ?: context.contentResolver.openOutputStream(uri)
            stream?.use { BackupCodec.write(backup, it) } ?: return@withContext false
            prefs.edit().putLong(BackupSettings.KEY_LAST_BACKUP, backup.createdAt).apply()
            true
        } catch (e: IOException) {
            Log.w(TAG, "the backup could not be written", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "the backup could not be written", e)
            false
        }
    }

    /** The backup in the file at [uri]. Throws a [BackupException] when it is not one this app reads, an IOException when it cannot be read. */
    suspend fun read(uri: Uri): Backup = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use(BackupCodec::read) ?: throw IOException("no stream")
        } catch (e: SecurityException) {
            throw IOException(e)
        }
    }

    /** [backup]'s parts written over what is here; what each brought, and which could not be. */
    suspend fun restore(backup: Backup): RestoreReport = withContext(Dispatchers.IO) {
        val restored = LinkedHashMap<BackupPart, Int>()
        val failed = LinkedHashSet<BackupPart>()
        var missing = 0
        for (part in backup.parts) {
            try {
                restored[part] = when (part) {
                    BackupPart.Settings -> restoreSettings(backup)
                    BackupPart.Shortcuts -> restoreShortcuts(backup)
                    BackupPart.Profiles -> restoreProfiles(backup)
                    BackupPart.Playlists -> {
                        val (lists, gone) = restorePlaylists(backup)
                        missing += gone
                        lists
                    }
                    BackupPart.SmartLists -> restoreSmartLists(backup)
                    BackupPart.Stats -> backup.stats.orEmpty().also { db.trackStats().restore(it) }.size
                    BackupPart.Lyrics -> backup.lyrics.orEmpty().also { db.lyrics().saveAll(it) }.size
                    BackupPart.FileSettings -> backup.fileSettings.orEmpty().also { db.mediaSettings().restore(it) }.size
                    BackupPart.Covers -> restoreCovers(backup)
                    BackupPart.Latency -> backup.latency.orEmpty().also { db.latencyProfiles().restore(it) }.size
                    BackupPart.RadioSchedules -> restoreRadioSchedules(backup)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$part could not be restored", e)
                failed += part
            }
        }
        RestoreReport(restored, missing, failed)
    }

    private suspend fun restoreSettings(backup: Backup): Int {
        val leftOut = BackupSettings.leftOut()
        val values = backup.settings.orEmpty().filter { it.key !in leftOut }
        // the windows follow the preferences they listen to (S-307)
        BackupSettings.write(values, prefs.edit(), leftOut).apply()
        backup.equalizers?.let { json ->
            try {
                EqualizerUtil.importAll(context, json) {}
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "the classic equalisers could not be restored", e)
            } catch (e: LinkageError) {
                // libVLC's presets are not there (the tests)
                Log.w(TAG, "the classic equalisers could not be restored", e)
            }
        }
        // the alarm on the system's clock as the backup had it (S-1007)
        if (values.any { it.key == NAGMEDAR_ALARM }) Alarms.schedule(context)
        return values.size
    }

    private fun restoreShortcuts(backup: Backup): Int {
        val text = backup.shortcuts.orEmpty().entries.joinToString("\n") { (id, keys) -> "$id=$keys" }
        val overrides = ShortcutOverrides.parse(text)
        prefs.edit().putString(NagmedarPreferences.KEY_SHORTCUTS, ShortcutOverrides.format(overrides)).apply()
        return overrides.size
    }

    private fun restoreProfiles(backup: Backup): Int {
        val restored = backup.profiles.orEmpty().mapNotNull { DeviceProfileCodec.decode(it) }
        restored.forEach(profiles::save)
        backup.selectedProfile?.let { id -> if (profiles.all().any { it.id == id }) profiles.selected = id }
        // an open TV Lab shows them
        AppScope.launch { TvLabSession.opened()?.reloadProfiles() }
        return restored.size
    }

    /** The lists restored, and their lines not found here. */
    private suspend fun restorePlaylists(backup: Backup): Pair<Int, Int> {
        var restored = 0
        var missing = 0
        for (list in backup.playlists.orEmpty()) {
            val gone = playlists.restore(list) ?: continue
            restored++
            missing += gone
        }
        return restored to missing
    }

    private fun restoreSmartLists(backup: Backup): Int {
        val dao = db.smartPlaylists()
        val here = dao.all().associateBy { it.name }
        val lists = backup.smartLists.orEmpty()
        lists.forEach { list ->
            val same = here[list.name]
            if (same != null) dao.update(list.copy(id = same.id)) else dao.insert(list.copy(id = 0))
        }
        return lists.size
    }

    private suspend fun restoreCovers(backup: Backup): Int {
        val finder = CoverCenter.finder(context)
        val restored = backup.covers.orEmpty().count { finder.restoreChosen(it.name, it.bytes) }
        if (restored > 0) CoverCenter.changed()
        return restored
    }

    /** The radio recordings' database, from [db]. */
    private fun recordings() = RecordingStore(db.radioRecordings(), AndroidTrackFiles(context))

    /**
     * The scheduled recordings of the backup that are not here already (the same station, time, length and
     * repeat), saved and set on the system's clock. A one-off whose time has passed is not brought back, and a
     * run that is on now is not begun: the schedule counts from the restore.
     */
    private suspend fun restoreRadioSchedules(backup: Backup): Int {
        val store = recordings()
        val here = store.scheduleList()
        val now = System.currentTimeMillis()
        var restored = 0
        for (schedule in backup.radioSchedules.orEmpty()) {
            if (here.any { sameSchedule(it, schedule) }) continue
            if (schedule.repeat == Repeat.Once && schedule.startAt <= now) continue
            val saved = store.save(schedule.copy(id = 0, lastStartedAt = now, checkedAt = now))
            RecordingAlarms.set(context, saved, now)
            restored++
        }
        return restored
    }

    private fun sameSchedule(a: RecordingSchedule, b: RecordingSchedule): Boolean =
        a.station.key == b.station.key && a.hour == b.hour && a.minute == b.minute && a.durationMinutes == b.durationMinutes &&
            a.repeat == b.repeat && (a.repeat != Repeat.Weekly || a.weekday == b.weekday) && (a.repeat != Repeat.Once || a.startAt == b.startAt)

    /** When the last backup was made; null when none was. */
    fun lastBackup(): Long? = prefs.getLong(BackupSettings.KEY_LAST_BACKUP, 0L).takeIf { it > 0L }

    private companion object {
        const val TAG = "Nagmedar/Backup"
    }
}
