package app.nagmedar.audio.cue

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.Charset

/** S-808: a CUE sheet read as the test set writes one, in UTF-8 and in Windows-1254; the tracks' bounds; the track uris. */
@RunWith(AndroidJUnit4::class)
class CueSheetTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val sheet = """
        REM GENRE Test
        REM DATE 2026
        REM DISCNUMBER 2
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
            ISRC TRA012600003
            INDEX 01 00:20:00
    """.trimIndent()

    @Test
    fun theTracksAndTheirBounds() {
        val parsed = CueSheet.parse(sheet)!!
        assertEquals("CUE albümü", parsed.title)
        assertEquals("Cemre Kaya", parsed.performer)
        assertEquals("Test", parsed.genre)
        assertEquals(2026, parsed.year)
        assertEquals(2, parsed.discNumber)
        val file = parsed.files.single()
        assertEquals("album.flac", file.name)
        assertEquals(listOf(1, 2, 3), file.tracks.map { it.number })
        assertEquals(listOf("Giriş", "Orta bölüm", "Çıkış"), file.tracks.map { it.title })
        // a track runs from its INDEX 01 to the next's; the pregap (INDEX 00) belongs to the one before
        assertEquals(listOf(0L, 750L, 1500L), file.tracks.map { it.from })
        assertEquals(listOf(750L, 1500L, null), file.tracks.map { it.to })
        assertEquals(10_000L, file.tracks[0].lengthMs)
        assertNull(file.tracks[2].lengthMs)
        // the track's own performer, else the album's
        assertEquals("Deniz Su", file.tracks[1].performer)
        assertNull(file.tracks[0].performer)
        assertEquals("TRA012600003", file.tracks[2].isrc)
        assertEquals(file, parsed.fileNamed("ALBUM.FLAC"))
        assertEquals(file, parsed.fileNamed("C:\\Müzik\\album.flac"))
    }

    @Test
    fun theTimes() {
        assertEquals(0L, CueSheet.frames("00:00:00"))
        assertEquals(74L, CueSheet.frames("00:00:74"))
        assertEquals((3 * 60 + 25) * 75L + 12, CueSheet.frames("03:25:12"))
        assertEquals(100L * 60 * 75, CueSheet.frames("100:00:00"))
        assertNull(CueSheet.frames("00:00:75"))
        assertNull(CueSheet.frames("1:2"))
        assertNull(CueSheet.frames("aa:bb:cc"))
    }

    @Test
    fun theEncodings() {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sheet.toByteArray()
        assertEquals("Çıkış", CueSheet.parse(CueSheet.decode(utf8Bom))!!.tracks[2].title)
        assertEquals("Çıkış", CueSheet.parse(CueSheet.decode(sheet.toByteArray()))!!.tracks[2].title)
        // an old Turkish sheet
        val cp1254 = sheet.toByteArray(Charset.forName("windows-1254"))
        assertEquals("Çıkış", CueSheet.parse(CueSheet.decode(cp1254))!!.tracks[2].title)
        assertEquals("Orta bölüm", CueSheet.parse(CueSheet.decode(cp1254))!!.tracks[1].title)
    }

    @Test
    fun whatIsNotASheet() {
        assertNull(CueSheet.parse(""))
        assertNull(CueSheet.parse("TITLE \"Nothing\"\nPERFORMER \"No one\""))
        // a file per track: each file's tracks are its own
        val multi = """
            FILE "01.flac" WAVE
              TRACK 01 AUDIO
                INDEX 01 00:00:00
            FILE "02.flac" WAVE
              TRACK 02 AUDIO
                INDEX 01 00:00:00
        """.trimIndent()
        val parsed = CueSheet.parse(multi)!!
        assertEquals(listOf("01.flac", "02.flac"), parsed.files.map { it.name })
        assertEquals(listOf(1), parsed.fileNamed("01.flac")!!.tracks.map { it.number })
        assertNull(parsed.fileNamed("03.flac"))
        // a data track is passed over; a track without an INDEX too
        val data = """
            FILE album.bin BINARY
              TRACK 01 MODE1/2352
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                INDEX 01 00:05:00
              TRACK 03 AUDIO
                TITLE "No index"
        """.trimIndent()
        assertEquals(listOf(2), CueSheet.parse(data)!!.tracks.map { it.number })
    }

    @Test
    fun theTrackUris() {
        val file = Uri.parse("file:///storage/emulated/0/Music/Cemre%20Kaya/album.flac")
        val track = CueUri.of(file, 3)
        assertEquals("file:///storage/emulated/0/Music/Cemre%20Kaya/album.flac#cue=3", track.toString())
        assertEquals(3, CueUri.trackNumber(track))
        assertEquals(3, CueUri.trackNumber(track.toString()))
        assertTrue(CueUri.isTrack(track))
        assertFalse(CueUri.isTrack(file))
        assertNull(CueUri.trackNumber(Uri.parse("file:///a.flac#4")))
        assertEquals(file, CueUri.file(track))
        assertEquals(file.toString(), CueUri.file(track.toString()))
        // a document's uri keeps its own path
        val document = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Falbum.flac")
        assertEquals("$document#cue=12", CueUri.of(document, 12).toString())
        assertEquals(document, CueUri.file(CueUri.of(document, 12)))
        assertEquals("album.flac", CueUri.of(document, 12).lastPathSegment?.substringAfterLast('/'))
    }

    @Test
    fun theSheetBesideAFile() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = folder.newFolder("album")
        File(dir, "album.flac").writeBytes(ByteArray(16))
        File(dir, "album.cue").writeText(sheet)
        // another sheet in the folder lists another file: not this one's
        File(dir, "other.cue").writeText("FILE \"other.flac\" WAVE\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\n  TRACK 02 AUDIO\n    INDEX 01 00:01:00\n")
        val audio = Uri.fromFile(File(dir, "album.flac"))
        val (found, file) = CueSheets.forFile(context, audio)!!
        assertEquals(Uri.fromFile(File(dir, "album.cue")).toString(), found.uri)
        assertEquals(3, file.tracks.size)
        assertEquals("Orta bölüm", CueSheets.track(context, CueUri.of(audio, 2))!!.title)
        assertNull(CueSheets.track(context, CueUri.of(audio, 9)))
        // a sheet named otherwise still lists the file
        File(dir, "album.cue").delete()
        CueSheets.clear()
        File(dir, "disc.cue").writeText(sheet)
        assertEquals(Uri.fromFile(File(dir, "disc.cue")).toString(), CueSheets.forFile(context, audio)!!.first.uri)
        // a file the sheets do not list, or a sheet with one track: the file is a track of its own
        assertNull(CueSheets.forFile(context, Uri.fromFile(File(dir, "single.flac"))))
        File(dir, "disc.cue").writeText("FILE \"album.flac\" WAVE\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\n")
        CueSheets.clear()
        assertNull(CueSheets.forFile(context, audio))
    }
}
