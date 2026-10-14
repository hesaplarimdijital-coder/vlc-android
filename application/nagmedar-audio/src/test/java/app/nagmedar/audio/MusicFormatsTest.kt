package app.nagmedar.audio

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-901: which items the engine takes from the playback service, and which stay with libVLC. */
@RunWith(AndroidJUnit4::class)
class MusicFormatsTest {

    @Test
    fun localMusicFilesAreTheEngines() {
        assertTrue(MusicFormats.canPlay(Uri.parse("file:///storage/emulated/0/Music/Gece%20Mavisi.flac"), null))
        assertTrue(MusicFormats.canPlay(Uri.parse("content://com.android.externalstorage.documents/document/1234-ABCD%3AMusic%2FSabaha%20Kadar.MP3"), null))
        assertTrue(MusicFormats.canPlay(Uri.parse("file:///a/b.dsf"), null))
        assertTrue(MusicFormats.canPlay(Uri.parse("content://media/external/audio/media/42"), "audio/flac"))
    }

    @Test
    fun streamsVideosAndUnknownsStayWithLibVlc() {
        assertFalse(MusicFormats.canPlay(Uri.parse("http://radio.example/stream.mp3"), "audio/mpeg"))
        assertFalse(MusicFormats.canPlay(Uri.parse("smb://nas/music/a.flac"), null))
        assertFalse(MusicFormats.canPlay(Uri.parse("file:///a/film.mkv"), null))
        assertFalse(MusicFormats.canPlay(Uri.parse("file:///a/noise.xyz"), "audio/flac"))
        assertFalse(MusicFormats.canPlay(Uri.parse("content://media/external/audio/media/42"), null))
        assertFalse(MusicFormats.canPlay(Uri.parse("content://media/external/audio/media/42"), "video/mp4"))
    }

    /** S-911: the tracker modules are the engine's, told by their kind in the library. */
    @Test
    fun trackerModulesAreTheEngines() {
        for (extension in listOf("mod", "xm", "s3m", "it", "mptm", "669", "med", "okt", "mo3", "umx")) {
            assertTrue(extension, MusicFormats.canPlay(Uri.parse("file:///a/Tracker/song.$extension"), null))
            assertTrue(extension, MusicFormats.isTracker(Uri.parse("file:///a/Tracker/song.$extension")))
        }
        assertTrue(MusicFormats.canPlay(Uri.parse("content://media/external/audio/media/42"), "audio/x-mod"))
        assertFalse(MusicFormats.isTracker(Uri.parse("file:///a/b.flac")))
        // a module's kind in the library: the file's extension; a decoded file's codec as the engine gave it
        assertEquals("xm", MusicFormats.codecOf(MusicFormats.TRACKER, Uri.parse("file:///a/b.XM")))
        assertEquals("mptm", MusicFormats.codecOf("tracker", Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMusic%2Fx.mptm")))
        assertEquals("tracker", MusicFormats.codecOf("tracker", Uri.parse("content://media/external/audio/media/42")))
        assertEquals("flac", MusicFormats.codecOf("flac", Uri.parse("file:///a/b.flac")))
        // a PPM is a picture, whatever libopenmpt makes of the name
        assertFalse(MusicFormats.canPlay(Uri.parse("file:///a/picture.ppm"), null))
    }

    @Test
    fun theExtension() {
        assertEquals("flac", MusicFormats.extensionOf(Uri.parse("file:///a/B.FLAC")))
        assertNull(MusicFormats.extensionOf(Uri.parse("content://media/external/audio/media/42")))
        assertNull(MusicFormats.extensionOf(Uri.parse("file:///a/.hidden")))
        assertNull(MusicFormats.extensionOf(Uri.parse("file:///a/trailing.")))
    }

    @Test
    fun theOutputFormat() {
        val f = OutputFormat.of((96000L shl 16) or (2L shl 8) or NativeAudio.FORMAT_S32.toLong())!!
        assertEquals(OutputFormat(96000, 2, NativeAudio.FORMAT_S32), f)
        assertEquals(32, f.bits)
        assertEquals(8, f.bytesPerFrame)
        assertFalse(f.isFloat)
        assertNull(OutputFormat.of(0))
        assertEquals(24, OutputFormat(44100, 2, NativeAudio.FORMAT_S24_3).bits)
        assertTrue(OutputFormat(44100, 2, NativeAudio.FORMAT_F32).isFloat)
    }

    @Test
    fun theSourceInfo() {
        val info = SourceInfo.parse("""{"codec":"flac","container":"flac","rate":96000,"channels":2,"bits":24,"float":false,"duration_ms":3000,"bitrate":2110618}""")!!
        assertEquals(SourceInfo("flac", "flac", 96000, 2, 24, false, 3000, 2110618), info)
        assertNull(SourceInfo.parse("{}"))
        assertNull(SourceInfo.parse("not json"))
    }
}
