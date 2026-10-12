package app.nagmedar.ui.player.subtitles

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * S-702: before a film starts VLC is told the character set of the subtitle files beside it or
 * kept with it, or the one chosen for the film; a file added in another one is read into UTF-8
 * with its mark, once.
 */
@RunWith(AndroidJUnit4::class)
class SubtitleEncodingsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun film(vararg subtitles: Pair<String, ByteArray>): Uri {
        val dir = folder.newFolder()
        val video = File(dir, "Kuzey Işığı.mkv").apply { writeBytes(ByteArray(16)) }
        subtitles.forEach { (name, bytes) -> File(dir, name).writeBytes(bytes) }
        return Uri.fromFile(video)
    }

    @Test
    fun aTurkishSrtBesideTheFilmTellsVlcItsCharacterSet() = runBlocking {
        val video = film("Kuzey Işığı.tr.srt" to TURKISH_SRT.toByteArray(SubtitleEncoding.Windows1254.charset))
        assertEquals("Windows-1254", SubtitleEncodings.find(context, video, video, emptyList()))
        assertEquals(SubtitleEncoding.Windows1254, SubtitleEncodings.toldFor(video.toString()))
    }

    @Test
    fun unicodeAndNoSubtitlesLeaveVlcsOwn() = runBlocking {
        val unicode = film("Kuzey Işığı.srt" to TURKISH_SRT.toByteArray(Charsets.UTF_8))
        assertNull(SubtitleEncodings.find(context, unicode, unicode, emptyList()))
        val bare = film()
        assertNull(SubtitleEncodings.find(context, bare, bare, emptyList()))
    }

    @Test
    fun aSubtitleKeptElsewhereCountsToo() = runBlocking {
        val elsewhere = File(folder.newFolder(), "indirilen.srt").apply { writeBytes(TURKISH_SRT_ISO.toByteArray(SubtitleEncoding.Iso88599.charset)) }
        val video = film()
        assertEquals("ISO-8859-9", SubtitleEncodings.find(context, video, video, listOf(Uri.fromFile(elsewhere).toString())))
    }

    @Test
    fun theFilmsChoiceComesFirst() = runBlocking {
        val video = film("Kuzey Işığı.tr.srt" to TURKISH_SRT.toByteArray(SubtitleEncoding.Windows1254.charset))
        SubtitleEncodings.choose(context, video, SubtitleEncoding.Windows1252)
        assertEquals(SubtitleEncoding.Windows1252, SubtitleEncodings.chosen(context, video))
        assertEquals("Windows-1252", SubtitleEncodings.find(context, video, video, emptyList()))
        // back to detected
        SubtitleEncodings.choose(context, video, null)
        assertEquals("Windows-1254", SubtitleEncodings.find(context, video, video, emptyList()))
    }

    @Test
    fun aFileInAnotherCharacterSetIsReadIntoUtf8Once() {
        val subtitle = File(folder.newFolder(), "Kuzey Işığı.tr.srt").apply { writeBytes(TURKISH_SRT.toByteArray(SubtitleEncoding.Windows1254.charset)) }
        val uri = Uri.fromFile(subtitle)
        val copy = SubtitleEncodings.readable(context, "file:///films/other.mkv", uri, subtitle.name)
        assertTrue(copy.toString(), SubtitleEncodings.isCopy(context, copy.toString()))
        val bytes = File(copy.path!!).readBytes()
        assertEquals("﻿" + TURKISH_SRT, String(bytes, Charsets.UTF_8))
        assertEquals(copy, SubtitleEncodings.readable(context, "file:///films/other.mkv", uri, subtitle.name))
        // a Unicode file is read as it is
        val unicode = File(folder.newFolder(), "Film.srt").apply { writeText(TURKISH_SRT) }
        assertEquals(Uri.fromFile(unicode), SubtitleEncodings.readable(context, "file:///films/other.mkv", Uri.fromFile(unicode), unicode.name))
        // chosen by hand, read so
        val western = SubtitleEncodings.readable(context, "file:///films/other.mkv", uri, subtitle.name, SubtitleEncoding.Windows1252)
        assertTrue(String(File(western.path!!).readBytes(), Charsets.UTF_8).contains("Þafak"))
    }
}
