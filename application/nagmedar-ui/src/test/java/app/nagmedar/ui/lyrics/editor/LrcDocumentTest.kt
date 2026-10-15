package app.nagmedar.ui.lyrics.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.lyrics.Lrc
import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.LyricWord
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-1005: timed lyrics written as an LRC file, plain text, SYLT entries and LRCLIB's line-timed LRC. */
@RunWith(AndroidJUnit4::class)
class LrcDocumentTest {

    private val lines = listOf(LyricLine(1_000, "Birinci satır"), LyricLine(62_340, ""), LyricLine(125_500, "Üçüncü"))
    private val words = listOf(
        LyricLine(1_000, "Merhaba dünya", listOf(LyricWord(1_000, "Merhaba "), LyricWord(1_500, "dünya"))),
        LyricLine(3_000, "tek"),
    )

    @Test
    fun anLrcFileHasItsTagsOnTop() {
        val text = LrcDocument.lrc(lines, LrcHeader("Parça [1]", "Sanatçı", null, 215_400))
        assertEquals(
            "[ti:Parça [1)]\n[ar:Sanatçı]\n[length:03:35]\n[00:01.00]Birinci satır\n[01:02.34]\n[02:05.50]Üçüncü",
            text,
        )
        // the tags are not lyrics
        assertEquals(lines, Lrc.parse(text))
        assertEquals("[00:01.00]Birinci satır\n[01:02.34]\n[02:05.50]Üçüncü", LrcDocument.lrc(lines))
    }

    @Test
    fun wordTimedLinesGetWordStampsAndReadBackTheSame() {
        val text = LrcDocument.lrc(words)
        assertEquals("[00:01.00]<00:01.00>Merhaba <00:01.50>dünya\n[00:03.00]tek", text)
        assertEquals(words, Lrc.parse(text))
    }

    @Test
    fun lrclibGetsLineStampsWithoutWordStampsAndWithoutTags() {
        assertEquals("[00:01.00]Merhaba dünya\n[00:03.00]tek", LrcDocument.lineLrc(words))
        assertEquals("Merhaba dünya\ntek", LrcDocument.plain(words))
    }

    @Test
    fun syltHoldsOneEntryForEachLineWhenNoLineHasWords() {
        val json = JSONArray(LrcDocument.sylt(lines))
        assertEquals(3, json.length())
        assertEquals(1_000L, json.getJSONArray(0).getLong(0))
        assertEquals("Birinci satır", json.getJSONArray(0).getString(1))
        assertEquals("", json.getJSONArray(1).getString(1))
    }

    @Test
    fun syltEndsEachLineInABreakWhenSomeLineHasWords() {
        val json = JSONArray(LrcDocument.sylt(words + LyricLine(5_000, "")))
        val entries = (0 until json.length()).map { json.getJSONArray(it).getLong(0) to json.getJSONArray(it).getString(1) }
        assertEquals(listOf(1_000L to "Merhaba ", 1_500L to "dünya\n", 3_000L to "tek\n", 5_000L to " \n"), entries)
    }

    @Test
    fun syltLeavesOutLinesNotTimed() {
        assertEquals("[]", LrcDocument.sylt(listOf(LyricLine(-1, "x"))))
    }

    @Test
    fun id3FormatsKeepPlainTextAndSyltOthersKeepTheLrc() {
        val (text, synced) = LrcDocument.tagPayload(lines, id3 = true)
        assertEquals("Birinci satır\n\nÜçüncü", text)
        assertEquals(LrcDocument.sylt(lines), synced)
        val (lrc, none) = LrcDocument.tagPayload(lines, id3 = false)
        assertEquals(LrcDocument.lrc(lines), lrc)
        assertNull(none)
    }

    @Test
    fun whichFilesKeepTheirLyricsTheId3Way() {
        assertTrue(LrcDocument.usesId3("Parça.MP3"))
        assertTrue(LrcDocument.usesId3("a.wav"))
        assertTrue(LrcDocument.usesId3("a.aiff"))
        assertFalse(LrcDocument.usesId3("a.flac"))
        assertFalse(LrcDocument.usesId3("a.m4a"))
        assertFalse(LrcDocument.usesId3("a.opus"))
        assertFalse(LrcDocument.usesId3("no extension"))
    }
}
