package app.nagmedar.ui.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-708: SRT, WebVTT and ASS/SSA read into cues and written again. An ASS file keeps every byte
 * it had but the times and the text of the lines that changed.
 */
class SubtitleDocumentTest {

    private val srt = "1\n00:00:01,000 --> 00:00:03,500\nGece yarısı Kuzey Işığı'nda buluşalım.\n\n" +
        "2\n00:00:04,000 --> 00:00:06,250\n<i>Geç kalma.</i>\nSakın.\n\n" +
        "3\n01:02:03,004 --> 01:02:05,000\nSon.\n\n"

    private val ass = "﻿[Script Info]\r\n; written by hand\r\nScriptType: v4.00+\r\nPlayResX: 1920\r\n\r\n" +
        "[V4+ Styles]\r\nFormat: Name, Fontname, Fontsize, PrimaryColour, Bold\r\nStyle: Default,Arial,48,&H00FFFFFF,0\r\nStyle: Top,Arial,42,&H0000FFFF,-1\r\n\r\n" +
        "[Events]\r\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\r\n" +
        "Comment: 0,0:00:00.00,0:00:00.50,Default,,0,0,0,,not a cue\r\n" +
        "Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,{\\an8\\i1}Merhaba, dünya{\\i0}\\Nİkinci satır\r\n" +
        "Dialogue: 1,0:00:04.00,0:00:06.25,Top,Ada,10,10,5,Banner;20,{\\pos(960,540)\\fad(200,200)}Uzun, virgüllü, bir metin\r\n" +
        "Dialogue: 0,1:02:03.00,1:02:05.00,Default,,0,0,0,,Son"

    @Test
    fun anSrtIsReadAndWrittenAgain() {
        val doc = SubtitleCodec.parse("film.tr.srt", srt)!!
        assertEquals(SubtitleFormat.Srt, doc.format)
        assertEquals(3, doc.cues.size)
        assertEquals(1_000L, doc.cues[0].startMs)
        assertEquals(3_500L, doc.cues[0].endMs)
        assertEquals("<i>Geç kalma.</i>\nSakın.", doc.cues[1].text)
        assertEquals(3_723_004L, doc.cues[2].startMs)
        assertEquals(srt, doc.write())
    }

    @Test
    fun anSrtWithWindowsLineBreaksKeepsThem() {
        val windows = srt.replace("\n", "\r\n")
        val doc = SubtitleCodec.parse("a.srt", windows)!!
        assertEquals("\r\n", doc.lineBreak)
        assertEquals(windows, doc.write())
    }

    @Test
    fun anSrtThatIsNotTidyIsReadAnyway() {
        // no numbers, dots for commas, short fractions, cues run together without a blank line, extra spaces
        val sloppy = "00:00:01.5 --> 00:00:02.000   \nBir\n   \n" +
            "7\n0:00:03,000 --> 0:00:04,000 X1:100 X2:200\nİki\n8\n00:00:05,000 --> 00:00:06,000\nÜç\n"
        val doc = SubtitleCodec.parse("x.srt", sloppy)!!
        assertEquals(listOf(1_500L, 3_000L, 5_000L), doc.cues.map { it.startMs })
        assertEquals(listOf("Bir", "İki", "Üç"), doc.cues.map { it.text })
        assertEquals("X1:100 X2:200", (doc.cues[1].source as CueSource.Plain).tail)
        // written tidily, the coordinates kept
        assertTrue(doc.write().contains("00:00:03,000 --> 00:00:04,000 X1:100 X2:200\n"))
        assertTrue(doc.write().startsWith("1\n00:00:01,500 --> 00:00:02,000\nBir\n\n2\n"))
    }

    @Test
    fun theByteOrderMarkIsRemembered() {
        val doc = SubtitleCodec.parse("a.srt", "﻿$srt")!!
        assertTrue(doc.bom)
        assertEquals(1_000L, doc.cues[0].startMs)
        val bytes = doc.toBytes()
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
        assertEquals(srt, String(doc.toBytes(withBom = false), Charsets.UTF_8))
    }

    @Test
    fun webVttKeepsItsHeaderNotesIdentifiersAndSettings() {
        val vtt = "WEBVTT - Kuzey Işığı\n\nSTYLE\n::cue { color: yellow }\n\nNOTE bir not\n\n" +
            "intro\n00:01.000 --> 00:03.500 align:start position:10%\nMerhaba\n\n" +
            "00:04.000 --> 00:06.000\n<v Ada>Selam</v>\n"
        val doc = SubtitleCodec.parse("film.vtt", vtt)!!
        assertEquals(SubtitleFormat.Vtt, doc.format)
        assertEquals(listOf(1_000L, 4_000L), doc.cues.map { it.startMs })
        assertEquals("intro", (doc.cues[0].source as CueSource.Plain).identifier)
        assertEquals("align:start position:10%", (doc.cues[0].source as CueSource.Plain).tail)
        val written = doc.write()
        assertTrue(written.startsWith("WEBVTT - Kuzey Işığı\n\nSTYLE\n::cue { color: yellow }\n\nNOTE bir not\n\nintro\n00:00:01.000 --> 00:00:03.500 align:start position:10%\nMerhaba\n\n"))
        assertTrue(written.contains("00:00:04.000 --> 00:00:06.000\n<v Ada>Selam</v>\n"))
        // read again, the same cues
        assertEquals(doc.cues.map { it.text }, SubtitleCodec.parse("x.vtt", written)!!.cues.map { it.text })
    }

    @Test
    fun anSrtBecomesWebVttAndBack() {
        val doc = SubtitleCodec.parse("a.srt", srt)!!
        val vtt = doc.asFormat(SubtitleFormat.Vtt)!!
        assertTrue(vtt.write().startsWith("WEBVTT\n\n00:00:01.000 --> 00:00:03.500\nGece"))
        assertEquals(srt, vtt.asFormat(SubtitleFormat.Srt)!!.write())
        assertNull("styles would be lost", SubtitleCodec.parse("a.ass", ass)!!.asFormat(SubtitleFormat.Srt))
    }

    @Test
    fun theFormatIsWhatTheTextIsWhateverTheNameSays() {
        assertEquals(SubtitleFormat.Vtt, SubtitleCodec.parse("a.srt", "WEBVTT\n\n00:01.000 --> 00:02.000\nx\n")!!.format)
        assertEquals(SubtitleFormat.Ass, SubtitleCodec.parse("a.srt", ass)!!.format)
        assertEquals(SubtitleFormat.Ssa, SubtitleCodec.parse("a.ssa", ass)!!.format)
        assertEquals(SubtitleFormat.Srt, SubtitleCodec.parse("a.txt", srt)!!.format)
        assertNull(SubtitleCodec.parse("a.txt", "just some words\n"))
        assertNull(SubtitleFormat.ofName("film.mkv"))
        assertEquals(SubtitleFormat.Srt, SubtitleFormat.ofName("Film.TR.SRT"))
    }

    @Test
    fun anAssFileUntouchedIsWrittenByteForByte() {
        val doc = SubtitleCodec.parse("a.ass", ass)!!
        assertTrue(doc.bom)
        assertEquals("\r\n", doc.lineBreak)
        assertEquals(3, doc.cues.size)
        assertEquals("{\\an8\\i1}Merhaba, dünya{\\i0}\nİkinci satır", doc.cues[0].text)
        assertEquals("{\\pos(960,540)\\fad(200,200)}Uzun, virgüllü, bir metin", doc.cues[1].text)
        assertEquals(4_000L, doc.cues[1].startMs)
        assertEquals(6_250L, doc.cues[1].endMs)
        assertEquals(ass.removePrefix("﻿"), doc.write())
        // no trailing line break in, none out
        assertTrue(!doc.write().endsWith("\n"))
    }

    @Test
    fun assTimesAreRoundedToHundredths() {
        assertEquals("0:00:01.23", Clock.formatAss(1_234))
        assertEquals("0:00:01.24", Clock.formatAss(1_235))
        assertEquals("0:00:02.00", Clock.formatAss(1_999))
        assertEquals("1:02:03.00", Clock.formatAss(3_723_000))
        assertEquals("0:00:00.00", Clock.formatAss(-5))
        assertEquals("00:01:02,345", Clock.format(62_345))
        assertEquals("00:01:02.345", Clock.format(62_345, '.'))
        assertEquals("1:01:02,345", Clock.field(3_662_345, ','))
    }

    @Test
    fun timesTypedInAFieldAreRead() {
        assertEquals(3_723_500L, Clock.parse("1:02:03,5"))
        assertEquals(3_723_250L, Clock.parse("62:03.250"))
        assertEquals(3_723_500L, Clock.parse(" 3723,5 "))
        assertEquals(62_345L, Clock.parse("0:01:02.345"))
        assertEquals(0L, Clock.parse("0"))
        assertNull(Clock.parse(""))
        assertNull(Clock.parse("abc"))
        assertNull(Clock.parse("1:2:3:4"))
        assertNull(Clock.parse("-5"))
        assertNotNull(Clock.parse("1:02"))
    }

    @Test
    fun aHugeHourNeitherCrashesNorMakesATime() {
        val huge = "99999999999999999999999"
        assertNull(Clock.parse("$huge:00:00"))
        assertNull(Clock.parse("$huge,5"))
        assertNull(Clock.parse("99999:59:59"))
        assertNull(Clock.parse("101:00:00"))
        assertEquals(360_000_000L, Clock.parse("100:00:00"))
        // a file with such hours is no cue, and no crash
        val srt = "1\n$huge:00:01,000 --> 00:00:03,000\nHello\n\n2\n00:00:04,000 --> 00:00:06,000\nWorld\n"
        val doc = SubtitleCodec.parse("a.srt", srt)
        assertEquals(listOf("World"), doc?.cues?.map { it.text })
        val ass = "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,$huge:00:01.00,0:00:03.50,Default,,0,0,0,,Merhaba\n" +
            "Dialogue: 0,0:00:04.00,0:00:05.50,Default,,0,0,0,,Dünya\n"
        assertEquals(listOf("Dünya"), SubtitleCodec.parse("a.ass", ass)?.cues?.map { it.text })
    }

    @Test
    fun textAfterABlankLineInsideAnSrtCueIsKept() {
        val srt = "1\n00:00:01,000 --> 00:00:03,000\nBirinci satır\n\nikinci paragraf\n\n2\n00:00:04,000 --> 00:00:06,000\nSon\n"
        val doc = SubtitleCodec.parse("a.srt", srt)!!
        assertEquals(2, doc.cues.size)
        assertEquals("Birinci satır\nikinci paragraf", doc.cues[0].text)
    }

    @Test
    fun aBlankLineInEditedTextDoesNotBreakTheSrtItIsWrittenTo() {
        val doc = SubtitleCodec.parse("a.srt", srt)!!
        val edited = doc.copy(cues = doc.cues.mapIndexed { i, cue -> if (i == 0) cue.copy(text = "Bir\n\n  \nİki") else cue })
        val again = SubtitleCodec.parse("a.srt", edited.write())!!
        assertEquals(3, again.cues.size)
        assertEquals("Bir\nİki", again.cues[0].text)
        assertEquals(doc.cues[1].text, again.cues[1].text)
    }
}
