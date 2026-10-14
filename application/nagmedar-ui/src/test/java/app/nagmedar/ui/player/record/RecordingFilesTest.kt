package app.nagmedar.ui.player.record

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.tools.Settings
import java.io.File

/**
 * S-607: where recordings are made and how they are kept: beside where they stay (a hidden folder
 * in Download/Nağmedar, which takes any type) and renamed into Movies or Music, or in the app's
 * storage and copied when a folder is chosen or on Android 10; a copy cut short is deleted first.
 */
@RunWith(AndroidJUnit4::class)
class RecordingFilesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val app = context.getString(R.string.nagmedar_shell_title)

    @Suppress("DEPRECATION")
    private val shared: File = Environment.getExternalStorageDirectory()

    @Before
    fun clear() {
        Settings.getInstance(context).edit().remove(KEY_RECORDING_FOLDER).commit()
    }

    @After
    fun clean() {
        File(shared, "Download").deleteRecursively()
        File(shared, "Music").deleteRecursively()
        File(shared, "Movies").deleteRecursively()
    }

    @Test
    fun whereAndHowByAndroidsVersion() {
        assertTrue(RecordingPlaces.stagesShared(sdk = 36, folderChosen = false))
        assertTrue(RecordingPlaces.stagesShared(sdk = 30, folderChosen = false))
        // Android 10: no paths into the shared folders
        assertFalse(RecordingPlaces.stagesShared(sdk = 29, folderChosen = false))
        // before 10: VLC's storage permission
        assertTrue(RecordingPlaces.stagesShared(sdk = 28, folderChosen = false))
        // a chosen tree has no path libVLC could write
        assertFalse(RecordingPlaces.stagesShared(sdk = 36, folderChosen = true))

        assertEquals(RecordingPlaces.Keep.Rename, RecordingPlaces.keep(sdk = 36, folderChosen = false, stagedShared = true))
        assertEquals(RecordingPlaces.Keep.Tree, RecordingPlaces.keep(sdk = 36, folderChosen = true, stagedShared = true))
        assertEquals(RecordingPlaces.Keep.MediaStore, RecordingPlaces.keep(sdk = 29, folderChosen = false, stagedShared = false))
        assertEquals(RecordingPlaces.Keep.File, RecordingPlaces.keep(sdk = 28, folderChosen = false, stagedShared = false))
    }

    @Test
    fun withoutAChosenFolderItIsMadeInDownload() {
        val files = RecordingFiles(context, sdk = 34, scanTimeoutMs = 50)
        val folder = files.newFolder()
        assertNotNull(folder)
        assertEquals(File(shared, "Download/$app/.recording"), folder!!.parentFile)
        // the probe that tried it is gone
        assertTrue(folder.listFiles()!!.isEmpty())
    }

    @Test
    fun withAChosenFolderItIsMadeInTheAppsStorage() {
        Settings.getInstance(context).edit().putString(KEY_RECORDING_FOLDER, "content://com.android.externalstorage.documents/tree/1234-ABCD%3AKay%C4%B1tlar").commit()
        val folder = RecordingFiles(context, sdk = 34).newFolder()!!
        assertEquals(File(context.getExternalFilesDir(null) ?: context.filesDir, "recordings"), folder.parentFile)
        folder.deleteRecursively()
    }

    @Test
    fun onAndroid10ItIsMadeInTheAppsStorage() {
        val folder = RecordingFiles(context, sdk = 29).newFolder()!!
        assertEquals(File(context.getExternalFilesDir(null) ?: context.filesDir, "recordings"), folder.parentFile)
        folder.deleteRecursively()
    }

    @Test
    fun soundIsRenamedIntoMusicAndVideoIntoMovies() = runBlocking {
        val files = RecordingFiles(context, sdk = 34, scanTimeoutMs = 50)
        val radio = files.newFolder()!!
        val mp3 = File(radio, "vlc-record.mp3").apply { writeBytes(ByteArray(3_000)) }
        // the app's own marks are not the recording
        File(radio, ".keeping").writeText("")
        assertEquals(mp3, files.written(radio, null))
        val progress = mutableListOf<Long>()
        val kept = files.keep(mp3, "TRT Radyo 1 2026-10-15 21-04-33.mp3", 0L) { copied, _ -> progress += copied }
        assertEquals("Music/$app", kept.folder)
        assertEquals("audio/mpeg", kept.mime)
        assertNull(kept.tree)
        assertTrue(File(shared, "Music/$app/TRT Radyo 1 2026-10-15 21-04-33.mp3").isFile)
        assertFalse(mp3.exists())
        assertEquals(listOf(3_000L), progress)

        val film = files.newFolder()!!
        val ts = File(film, "vlc-record.ts").apply { writeBytes(ByteArray(10)) }
        assertEquals("Movies/$app", files.keep(ts, "Kuzey Işığı.ts", 0L) { _, _ -> }.folder)
        assertTrue(File(shared, "Movies/$app/Kuzey Işığı.ts").isFile)
    }

    @Test
    fun aSecondOfTheSameNameIsNumbered() = runBlocking {
        val files = RecordingFiles(context, sdk = 34, scanTimeoutMs = 50)
        repeat(2) {
            val folder = files.newFolder()!!
            Thread.sleep(2)
            files.keep(File(folder, "r.ts").apply { writeBytes(ByteArray(5)) }, "Yayın.ts", 0L) { _, _ -> }
        }
        assertTrue(File(shared, "Movies/$app/Yayın.ts").isFile)
        assertTrue(File(shared, "Movies/$app/Yayın (1).ts").isFile)
    }

    @Test
    fun aCopyCutShortIsDeletedBeforeItIsMadeAgain() = runBlocking {
        val files = RecordingFiles(context, sdk = 34, scanTimeoutMs = 50)
        val folder = files.newFolder()!!
        val recording = File(folder, "vlc-record.ts").apply { writeBytes(ByteArray(100)) }
        // an earlier run was copying it here when it ended
        val partial = File(shared, "Movies/$app/Kuzey Işığı.ts").apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(40))
        }
        File(folder, ".keeping").writeText(Uri.fromFile(partial).toString())
        files.keep(recording, "Kuzey Işığı.ts", 0L) { _, _ -> }
        // the whole one has the name, no "(1)" beside a half one
        assertEquals(100L, File(shared, "Movies/$app/Kuzey Işığı.ts").length())
        assertFalse(File(shared, "Movies/$app/Kuzey Işığı (1).ts").exists())
        assertFalse(File(folder, ".keeping").exists())
    }

    @Test
    fun leftoversOfBothPlacesAreFound() {
        val files = RecordingFiles(context, sdk = 34)
        val shared = files.newFolder()!!
        val own = File(context.getExternalFilesDir(null) ?: context.filesDir, "recordings/1").apply { mkdirs() }
        assertTrue(files.leftovers().containsAll(listOf(shared, own)))
        own.deleteRecursively()
    }
}
