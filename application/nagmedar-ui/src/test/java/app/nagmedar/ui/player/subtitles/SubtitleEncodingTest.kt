package app.nagmedar.ui.player.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Turkish SRT: ş, ğ, ı, İ and their capitals, with Windows' quotes and ellipsis. */
internal const val TURKISH_SRT = """1
00:01:12,330 --> 00:01:15,100
Şafak sökerken gölün üstünde ışık doğdu.

2
00:01:15,400 --> 00:01:18,900
İçeride “Ağaçların arasında” dedi, sesi titriyordu…

3
00:01:19,000 --> 00:01:21,250
<i>Işığı gördün mü, Gülşen?</i>
"""

/** The same lines without Windows' punctuation, as ISO-8859-9 can write them. */
internal const val TURKISH_SRT_ISO = """1
00:01:12,330 --> 00:01:15,100
Şafak sökerken gölün üstünde ışık doğdu.

2
00:01:15,400 --> 00:01:18,900
İçeride "Ağaçların arasında" dedi, sesi titriyordu.
"""

/**
 * S-702: Turkish subtitles in Windows-1254, ISO-8859-9 and UTF-8 are told apart and read with
 * their ş, ğ, ı and İ; a Western, a Cyrillic and a Unicode file with its mark are known too; the
 * lines to see the letters by are the said ones.
 */
class SubtitleEncodingTest {

    private fun roundTrip(text: String, encoding: SubtitleEncoding): Pair<SubtitleEncoding, String> {
        val bytes = text.toByteArray(encoding.charset)
        val detected = detectEncoding(bytes)
        return detected to decodeSubtitle(bytes, detected)
    }

    @Test
    fun turkishInWindows1254() {
        val (detected, text) = roundTrip(TURKISH_SRT, SubtitleEncoding.Windows1254)
        assertEquals(SubtitleEncoding.Windows1254, detected)
        assertEquals(TURKISH_SRT, text)
    }

    @Test
    fun turkishInIso88599() {
        val (detected, text) = roundTrip(TURKISH_SRT_ISO, SubtitleEncoding.Iso88599)
        assertEquals(SubtitleEncoding.Iso88599, detected)
        assertEquals(TURKISH_SRT_ISO, text)
    }

    @Test
    fun turkishInUtf8WithAndWithoutItsMark() {
        assertEquals(SubtitleEncoding.Utf8 to TURKISH_SRT, roundTrip(TURKISH_SRT, SubtitleEncoding.Utf8))
        val marked = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + TURKISH_SRT.toByteArray(Charsets.UTF_8)
        assertEquals(SubtitleEncoding.Utf8, detectEncoding(marked))
        assertEquals(TURKISH_SRT, decodeSubtitle(marked, SubtitleEncoding.Utf8))
    }

    @Test
    fun aHeadCutInsideALetterIsStillUtf8() {
        val bytes = TURKISH_SRT.toByteArray(Charsets.UTF_8)
        // "Ş" is two bytes: the head ends after the first
        val cut = bytes.copyOf(bytes.indexOfFirst { it == 0xC5.toByte() } + 1)
        assertEquals(SubtitleEncoding.Utf8, detectEncoding(cut))
    }

    @Test
    fun otherLanguagesAndUnicodeWithItsMark() {
        val french = "1\n00:00:01,000 --> 00:00:02,000\nÇa été très étrange, à côté de la forêt.\n"
        assertEquals(SubtitleEncoding.Windows1252, detectEncoding(french.toByteArray(SubtitleEncoding.Windows1252.charset)))
        val russian = "1\n00:00:01,000 --> 00:00:02,000\nСвет над озером появился на рассвете.\n"
        val (detected, text) = roundTrip(russian, SubtitleEncoding.Windows1251)
        assertEquals(SubtitleEncoding.Windows1251, detected)
        assertEquals(russian, text)
        val utf16 = TURKISH_SRT.toByteArray(Charsets.UTF_16)
        assertEquals(SubtitleEncoding.Utf16, detectEncoding(utf16))
        assertEquals(TURKISH_SRT, decodeSubtitle(utf16, SubtitleEncoding.Utf16))
        assertEquals(SubtitleEncoding.Utf8, detectEncoding("1\n00:00:01,000 --> 00:00:02,000\nHello.\n".toByteArray()))
    }

    @Test
    fun theSaidLinesWithTheirLettersFirst() {
        assertEquals(
            listOf("Şafak sökerken gölün üstünde ışık doğdu.", "İçeride “Ağaçların arasında” dedi, sesi titriyordu…"),
            previewLines(TURKISH_SRT, count = 2),
        )
        val ass = "[Script Info]\nTitle: Kuzey\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:01:12.33,0:01:15.10,Default,,0,0,0,,{\\i1}Şafak{\\i0} sökerken\\Nışık doğdu.\n"
        assertEquals(listOf("Şafak sökerken ışık doğdu."), previewLines(ass))
        assertEquals(listOf("Merhaba dünya"), previewLines("{1025}{1110}Merhaba|dünya\n"))
    }

    @Test
    fun picturesAreNotReadAgain() {
        assertTrue(isTextSubtitle("Film.tr.srt", TURKISH_SRT.toByteArray()))
        assertTrue(isTextSubtitle("Film.sub", "{1025}{1110}Merhaba".toByteArray()))
        assertFalse(isTextSubtitle("Film.sub", byteArrayOf(0, 0, 1, 0xBA.toByte())))
        assertFalse(isTextSubtitle("Film.idx", "# VobSub index file".toByteArray()))
        assertFalse(isTextSubtitle("Film.sup", byteArrayOf(0x50, 0x47)))
    }

    @Test
    fun theChoicesAreKeptByTheirKeys() {
        assertEquals(SubtitleEncoding.entries, SubtitleEncoding.entries.map { SubtitleEncoding.of(it.key) })
        assertEquals("Windows-1254", SubtitleEncoding.Windows1254.vlc)
    }
}
