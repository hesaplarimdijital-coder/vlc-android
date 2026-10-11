package app.nagmedar.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

/** S-311: subtitle files become timed lines, whatever their format and encoding. */
class SubtitlesTest {

    private val srt = "1\r\n00:01:02,345 --> 00:01:04,000\r\n<i>Işıklar söndüğünde,</i>\r\nyolu yıldızlar gösterir.\r\n\r\n" +
        "2\r\n01:11:45,000 --> 01:11:47,000\r\nKuzey ışığı\r\n"

    private fun cues(name: String, bytes: ByteArray) = SubtitleParser.parse(name, bytes).map { it.startMs to it.text }

    @Test
    fun srtInUtf8() {
        assertEquals(
            listOf(62_345L to "Işıklar söndüğünde, yolu yıldızlar gösterir.", 4_305_000L to "Kuzey ışığı"),
            cues("film.tr.srt", srt.toByteArray()),
        )
        // with a byte order mark
        assertEquals(2, cues("film.tr.srt", byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + srt.toByteArray()).size)
    }

    @Test
    fun olderTurkishFilesInWindows1254() {
        assertEquals("Işıklar söndüğünde, yolu yıldızlar gösterir.", cues("eski.srt", srt.toByteArray(charset("windows-1254"))).first().second)
    }

    @Test
    fun webVttAndAss() {
        assertEquals(listOf(5_500L to "Merhaba"), cues("a.vtt", "WEBVTT\n\n00:05.500 --> 00:07.000\nMerhaba\n".toByteArray()))
        val ass = "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:00:03.50,0:00:05.00,Default,,0,0,0,,{\\i1}Kuzey\\Nışığı, bak!\n"
        assertEquals(listOf(3_500L to "Kuzey ışığı, bak!"), cues("a.ass", ass.toByteArray()))
    }

    @Test
    fun linesAreFoundByTheirWords() {
        val parsed = SubtitleParser.parse("film.tr.srt", srt.toByteArray())
        val found = searchCues(listOf(Triple("film", "film.tr.srt", parsed)), SearchQuery.parse("yildizlar"))
        assertEquals(listOf(62_345L), found.map { it.cue.startMs })
        assertEquals(emptyList<Long>(), searchCues(listOf(Triple("film", "f", parsed)), SearchQuery.parse("yildizlar -yolu")).map { it.cue.startMs })
    }
}
