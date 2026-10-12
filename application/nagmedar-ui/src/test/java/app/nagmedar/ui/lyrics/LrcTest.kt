package app.nagmedar.ui.lyrics

import app.nagmedar.audio.TagLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1003: LRC read and written; SYLT's lines; the line sung at a time. */
class LrcTest {

    @Test
    fun timedLinesInOrder() {
        val lines = Lrc.parse(
            """
            [ar:Ada Karan]
            [ti:Sabaha Kadar]
            [length: 04:01]
            [00:12.30]Gece mavisi
            [00:17.5] çöktü şehre
            [01:02]
            [00:21.250]  yine   sen
            Zamansız bir satır
            """.trimIndent(),
        )
        assertEquals(
            listOf(LyricLine(12_300, "Gece mavisi"), LyricLine(17_500, "çöktü şehre"), LyricLine(21_250, "yine sen"), LyricLine(62_000, "")),
            lines,
        )
    }

    @Test
    fun aLineSungAgain() {
        val lines = Lrc.parse("[00:10.00][00:40.00]Nakarat\n[00:20.00]Kıta")
        assertEquals(listOf(10_000L, 20_000L, 40_000L), lines.map { it.startMs })
        assertEquals(listOf("Nakarat", "Kıta", "Nakarat"), lines.map { it.text })
    }

    @Test
    fun theOffsetTagShowsLinesSooner() {
        val lines = Lrc.parse("[offset:+500]\n[00:01.00]bir\n[00:00.20]sıfır")
        assertEquals(listOf(0L, 500L), lines.map { it.startMs })
        assertEquals(listOf(1_500L), Lrc.parse("[offset:-500]\n[00:01.00]bir").map { it.startMs })
    }

    @Test
    fun wordStamps() {
        val line = Lrc.parse("[00:12.00]<00:12.00> Gece <00:12.50>  mavi<00:12.80>si <00:13.40>").single()
        assertEquals("Gece mavisi", line.text)
        assertEquals(listOf(LyricWord(12_000, "Gece "), LyricWord(12_500, "mavi"), LyricWord(12_800, "si")), line.words)
        // the first word without a stamp of its own starts with the line
        val bare = Lrc.parse("[00:05.00]Merhaba <00:05.60>dünya").single()
        assertEquals(listOf(LyricWord(5_000, "Merhaba "), LyricWord(5_600, "dünya")), bare.words)
    }

    @Test
    fun plainText() {
        val lines = Lrc.parse("﻿\n\nBirinci kıta\n[Nakarat]\n\n\n\nİkinci kıta\n\n")
        assertEquals(listOf("Birinci kıta", "[Nakarat]", "", "İkinci kıta"), lines.map { it.text })
        assertTrue(lines.all { it.startMs == -1L })
        // every line at one time: not timed at all
        assertEquals(listOf(-1L, -1L, -1L), Lrc.parse("[00:00.00]a\n[00:00.00]b\n[00:00.00]c").map { it.startMs })
    }

    @Test
    fun writtenAndReadBack() {
        val lines = listOf(
            LyricLine(0, "Başlangıç"),
            LyricLine(1_234, "Gece mavisi", listOf(LyricWord(1_234, "Gece "), LyricWord(1_800, "mavisi"))),
            LyricLine(65_430, ""),
        )
        val text = Lrc.write(lines)
        assertEquals("[00:00.00]Başlangıç\n[00:01.234]<00:01.234>Gece <00:01.80>mavisi\n[01:05.43]", text)
        assertEquals(lines, Lrc.parse(text))
        assertEquals("Bir\n\nİki", Lrc.write(Lrc.parse("Bir\n\nİki")))
    }

    @Test
    fun theLineAtATime() {
        val lyrics = Lyrics(Lrc.parse("[00:10.00]a\n[00:20.00]b\n[00:30.00]c"), LyricsOrigin.File)
        assertEquals(-1, lyrics.lineAt(9_999))
        assertEquals(0, lyrics.lineAt(10_000))
        assertEquals(1, lyrics.lineAt(29_999))
        assertEquals(2, lyrics.lineAt(400_000))
        // shown 500 ms later: the line comes when the music is 500 ms further
        assertEquals(-1, lyrics.copy(offsetMs = 500).lineAt(10_499))
        assertEquals(0, lyrics.copy(offsetMs = 500).lineAt(10_500))
        assertEquals(1, lyrics.copy(offsetMs = -500).lineAt(19_500))
        assertEquals(-1, Lyrics(Lrc.parse("düz"), LyricsOrigin.File).lineAt(5_000))
    }

    @Test
    fun theTagsLyrics() {
        // SYLT's entries, each a line
        val sylt = Lyrics.of(TagLyrics("Düz söz", listOf(TagLyrics.Line(1_200, "Yolcusu"), TagLyrics.Line(0, "Gece"))))!!
        assertEquals(listOf(LyricLine(0, "Gece"), LyricLine(1_200, "Yolcusu")), sylt.lines)
        assertEquals(LyricsOrigin.Embedded, sylt.origin)
        // syllables with line breaks: word-timed lines
        val karaoke = Lyrics.of(
            TagLyrics(
                null,
                listOf(TagLyrics.Line(0, "Ge"), TagLyrics.Line(300, "ce "), TagLyrics.Line(600, "mavi"), TagLyrics.Line(2_000, "\nŞehre"), TagLyrics.Line(2_500, " yağmur")),
            ),
        )!!
        assertEquals(listOf("Gece mavi", "Şehre yağmur"), karaoke.lines.map { it.text })
        assertEquals(listOf(0L, 300L, 600L), karaoke.lines[0].words.map { it.startMs })
        assertTrue(karaoke.wordTimed)
        // LRC in a plain tag is timed; plain text is not
        assertTrue(Lyrics.of(TagLyrics("[00:01.00]Birinci satır\n[00:01.50]İkinci", emptyList()))!!.synced)
        assertFalse(Lyrics.of(TagLyrics("Düz söz\nikinci", emptyList()))!!.synced)
    }
}
