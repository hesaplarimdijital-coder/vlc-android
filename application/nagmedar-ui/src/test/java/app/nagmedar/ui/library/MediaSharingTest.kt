package app.nagmedar.ui.library

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * The library's "Paylaş…": a document of a picked folder goes to the app chosen as it is, a file
 * of the device through the app's FileProvider; one with ACTION_SEND, several with
 * ACTION_SEND_MULTIPLE, each readable by the app chosen.
 */
@RunWith(AndroidJUnit4::class)
class MediaSharingTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** The app's FileProvider, as it names a file of the device. */
    private val provider = { file: File -> Uri.parse("content://app.nagmedar.provider/external_files" + Uri.encode(file.path, "/")) }

    private val document = Uri.parse(
        "content://com.android.externalstorage.documents/tree/primary%3AFilmler/document/primary%3AFilmler%2FKuzey%20I%C5%9F%C4%B1%C4%9F%C4%B1.mkv",
    )

    @Test
    fun aDocumentIsSentAsItIs() {
        val readable = MediaSharing.readable(document, isFile = { false }, provider = provider)
        assertEquals(document, readable)
        val send = MediaSharing.sendIntent(listOf(SharedFile(readable!!, "video/x-matroska", "Kuzey Işığı")))!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("video/x-matroska", send.type)
        assertEquals(document, send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertNotEquals(0, send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // the clip carries the permission to the app chosen, and to the sheet's preview
        assertEquals(1, send.clipData!!.itemCount)
        assertEquals(document, send.clipData!!.getItemAt(0).uri)
    }

    @Test
    fun aFileGoesThroughTheProvider() {
        val file = folder.newFile("Sabaha Kadar.flac")
        val readable = MediaSharing.readable(Uri.fromFile(file), File::isFile, provider)
        assertEquals(provider(file), readable)
        val send = MediaSharing.sendIntent(listOf(SharedFile(readable!!, "audio/flac", "Sabaha Kadar")))!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("audio/flac", send.type)
        assertEquals(provider(file), send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertNotEquals(0, send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(provider(file), send.clipData!!.getItemAt(0).uri)
        // a path as the medialibrary may keep it, without its scheme
        assertEquals(provider(file), MediaSharing.readable(Uri.parse(file.path), File::isFile, provider))
        // a file that is gone is not sent, nor is a stream
        assertNull(MediaSharing.readable(Uri.fromFile(File(folder.root, "yok.flac")), File::isFile, provider))
        assertNull(MediaSharing.readable(Uri.parse("https://radyo.example/canli.mp3"), File::isFile, provider))
    }

    @Test
    fun severalGoTogether() {
        val film = provider(File("/storage/emulated/0/Movies/Sessiz Liman.mp4"))
        val files = listOf(SharedFile(document, "video/x-matroska", "Kuzey Işığı"), SharedFile(film, "video/mp4", "Sessiz Liman"))
        val send = MediaSharing.sendIntent(files)!!
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.action)
        assertEquals("video/*", send.type)
        assertEquals(listOf(document, film), send.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertNotEquals(0, send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(listOf(document, film), (0 until send.clipData!!.itemCount).map { send.clipData!!.getItemAt(it).uri })
        // films and tracks together are of any type; nothing is nothing to send
        assertEquals("*/*", MediaSharing.commonType(listOf("video/mp4", "audio/flac")))
        assertEquals("audio/*", MediaSharing.commonType(listOf("audio/mpeg", "audio/flac")))
        assertNull(MediaSharing.sendIntent(emptyList()))
    }
}
