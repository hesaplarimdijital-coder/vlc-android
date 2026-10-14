package app.nagmedar.ui.library

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.audio.cue.CueSheets
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.music.toTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.media.CueMedia
import java.io.File
import java.util.Locale

/**
 * S-808: a file with a CUE sheet beside it is the sheet's tracks in the library, each an item
 * of its own with what the sheet says of it; the queue's items come back from their uris.
 */
@RunWith(AndroidJUnit4::class)
class CueLibraryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private lateinit var image: MediaWrapper
    private lateinit var other: MediaWrapper

    @Before
    fun album() {
        CueSheets.clear()
        dir = folder.newFolder("Cemre Kaya - CUE albümü")
        File(dir, "album.flac").writeBytes(ByteArray(32))
        File(dir, "other.flac").writeBytes(ByteArray(32))
        File(dir, "album.cue").writeText(
            """
            REM GENRE Test
            REM DATE 2026
            PERFORMER "Cemre Kaya"
            TITLE "CUE albümü"
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Giriş"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Orta bölüm"
                PERFORMER "Deniz Su"
                INDEX 00 00:09:50
                INDEX 01 00:10:00
              TRACK 03 AUDIO
                TITLE "Çıkış"
                INDEX 01 00:20:00
            """.trimIndent(),
        )
        image = MLServiceLocator.getAbstractMediaWrapper(Uri.fromFile(File(dir, "album.flac"))).apply { length = 30_000L }
        other = MLServiceLocator.getAbstractMediaWrapper(Uri.fromFile(File(dir, "other.flac")))
    }

    @Test
    fun theSheetsTracksStandForTheFile() {
        val expanded = CueLibrary.expand(context, listOf(other, image))
        assertEquals(4, expanded.size)
        assertEquals(other, expanded[0])
        val tracks = expanded.drop(1)
        assertEquals(listOf("Giriş", "Orta bölüm", "Çıkış"), tracks.map { it.title })
        assertEquals(listOf(1, 2, 3), tracks.map { it.trackNumber })
        assertEquals(listOf(10_000L, 10_000L, 10_000L), tracks.map { it.length })
        assertEquals(listOf("Cemre Kaya", "Deniz Su", "Cemre Kaya"), tracks.map { it.artistName })
        assertEquals("CUE albümü", tracks[0].albumName)
        assertEquals("Cemre Kaya", tracks[0].albumArtistName)
        assertEquals("Test", tracks[0].genre)
        assertEquals(2026, tracks[0].releaseYear)
        assertEquals(MediaWrapper.TYPE_AUDIO, tracks[1].type)
        assertEquals(CueUri.of(image.uri, 2), tracks[1].uri)
        // as the library shows them: one album, in the sheet's order, each with its own key
        val library = MusicLibrary(tracks.map { it.toTrack(null) }, Locale("tr"))
        val album = library.groups(MusicView.Albums).single()
        assertEquals("CUE albümü", album.title)
        assertEquals(listOf("Giriş", "Orta bölüm", "Çıkış"), album.tracks.map { it.title })
        assertEquals(3, album.tracks.map { it.statsKey }.toSet().size)
        assertEquals(dir.path, album.tracks[0].folder)
    }

    @Test
    fun aFileWithoutASheetStaysAsItIs() {
        File(dir, "album.cue").delete()
        CueSheets.clear()
        assertEquals(listOf(image, other), CueLibrary.expand(context, listOf(image, other)))
    }

    @Test
    fun aTrackComesBackFromItsUri() {
        val restored = CueMedia.restore(context, CueUri.of(image.uri, 2), fileLengthMs = 30_000L)!!
        assertEquals("Orta bölüm", restored.title)
        assertEquals("Deniz Su", restored.artistName)
        assertEquals("CUE albümü", restored.albumName)
        assertEquals(2, restored.trackNumber)
        assertEquals(10_000L, restored.length)
        // the last track lasts to the file's end
        assertEquals(10_000L, CueMedia.restore(context, CueUri.of(image.uri, 3), fileLengthMs = 30_000L)!!.length)
        assertNull(CueMedia.restore(context, CueUri.of(image.uri, 7)))
        assertNull(CueMedia.restore(context, image.uri))
    }
}
