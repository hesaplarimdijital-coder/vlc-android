package app.nagmedar.ui.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.SmartPlaylistEntry
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.music.PlaylistEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.tools.KEY_OPEN_SUBTITLES_USER
import org.videolan.tools.NAGMEDAR_CROSSFADE_MS
import org.videolan.tools.NAGMEDAR_SAF_FOLDERS
import org.videolan.tools.Settings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * S-1502: a backup made on one device and restored on another: the settings, the shortcuts, the
 * lists and the statistics come back; this device's folders and an account do not travel; a
 * restore done twice changes nothing more.
 */
@RunWith(AndroidJUnit4::class)
class AppBackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = Settings.getInstance(context)
    private lateinit var mine: NagmedarDatabase
    private lateinit var theirs: NagmedarDatabase

    /** The medialibrary's playlists: a line is found here when it is a path. */
    private class Playlists : BackupPlaylists {
        val lists = LinkedHashMap<String, BackupPlaylist>()

        override suspend fun lists() = lists.values.toList()

        override suspend fun restore(list: BackupPlaylist): Int {
            val found = list.entries.filter { it.location.startsWith("/") }
            lists[list.name] = list.copy(entries = found)
            return list.entries.size - found.size
        }
    }

    private fun database() = Room.inMemoryDatabaseBuilder(context, NagmedarDatabase::class.java).allowMainThreadQueries().build()

    @Before
    fun open() {
        mine = database()
        theirs = database()
    }

    @After
    fun close() {
        mine.close()
        theirs.close()
        prefs.edit().remove(KEY_THEME).remove(NAGMEDAR_CROSSFADE_MS).remove(NAGMEDAR_SAF_FOLDERS).remove(KEY_OPEN_SUBTITLES_USER)
            .remove(NagmedarPreferences.KEY_SHORTCUTS).remove(BackupSettings.KEY_LAST_BACKUP).commit()
    }

    @Test
    fun aBackupComesBackOnAnotherDevice() = runBlocking {
        prefs.edit()
            .putString(KEY_THEME, "Dark")
            .putInt(NAGMEDAR_CROSSFADE_MS, 4000)
            .putStringSet(NAGMEDAR_SAF_FOLDERS, setOf("content://tree/mine"))
            .putString(KEY_OPEN_SUBTITLES_USER, """{"token":"secret"}""")
            .putString(NagmedarPreferences.KEY_SHORTCUTS, "Stop=Q")
            .commit()
        mine.trackStats().rate("file:///m/01.flac", 4, 10)
        mine.trackStats().played("file:///m/02.flac", 20)
        mine.smartPlaylists().insert(SmartPlaylistEntry(name = "Sevilenler", rules = "rating|at_least|4", createdAt = 1))
        val here = Playlists().apply {
            lists["Yol"] = BackupPlaylist("Yol", listOf(PlaylistEntry("/m/01.flac"), PlaylistEntry("D:/Müzik/02.flac")))
        }
        val made = AppBackup(context, mine, here).make(now = 1_760_300_000_000L)
        val file = ByteArrayOutputStream().also { BackupCodec.write(made, it) }.toByteArray()

        // the other device: its own folder, none of these yet
        prefs.edit()
            .remove(KEY_THEME).remove(NAGMEDAR_CROSSFADE_MS).remove(KEY_OPEN_SUBTITLES_USER).remove(NagmedarPreferences.KEY_SHORTCUTS)
            .putStringSet(NAGMEDAR_SAF_FOLDERS, setOf("content://tree/theirs"))
            .commit()
        val there = Playlists()
        val backup = BackupCodec.read(ByteArrayInputStream(file))
        val report = AppBackup(context, theirs, there).restore(backup)

        assertEquals("Dark", prefs.getString(KEY_THEME, null))
        assertEquals(4000, prefs.getInt(NAGMEDAR_CROSSFADE_MS, 0))
        assertEquals("Stop=Q", prefs.getString(NagmedarPreferences.KEY_SHORTCUTS, null))
        // what is this device's stays, an account does not travel
        assertEquals(setOf("content://tree/theirs"), prefs.getStringSet(NAGMEDAR_SAF_FOLDERS, null))
        assertNull(prefs.getString(KEY_OPEN_SUBTITLES_USER, null))
        assertEquals(4, theirs.trackStats().get("file:///m/01.flac")!!.rating)
        assertEquals(1, theirs.trackStats().get("file:///m/02.flac")!!.playCount)
        assertEquals(listOf("Sevilenler"), theirs.smartPlaylists().all().map { it.name })
        assertEquals(listOf(PlaylistEntry("/m/01.flac")), there.lists.getValue("Yol").entries)
        assertEquals(1, report.missingTracks)
        assertEquals(emptySet<BackupPart>(), report.failed)
        assertEquals(1, report.restored[BackupPart.Shortcuts])

        // restored again: nothing doubles
        AppBackup(context, theirs, there).restore(backup)
        assertEquals(1, theirs.smartPlaylists().all().size)
        assertEquals(1, theirs.trackStats().get("file:///m/02.flac")!!.playCount)
        assertEquals(1, there.lists.size)
    }

    private companion object {
        const val KEY_THEME = "nagmedar_theme"
    }
}
