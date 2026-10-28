package app.nagmedar.ui.library

import app.nagmedar.lab.AudioTech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** S-802: the technical lines read as in the mockups, numbers and languages in Turkish. */
class TechLabelsTest {

    private val tr = Locale("tr", "TR")
    private val en = Locale.ENGLISH

    @Test
    fun cardSummaries() {
        assertEquals("Atmos", techSummary(TechSamples.kuzeyIsigi))
        assertEquals("TrueHD", techSummary(TechSamples.demirVadi))
        assertEquals("Atmos", techSummary(TechSamples.camSehir))
        assertEquals("", techSummary(TechSamples.tech(emptyList())))
    }

    @Test
    fun detailLines() {
        val tech = TechSamples.kuzeyIsigi
        assertEquals(
            listOf("TrueHD 7.1 Atmos · İng", "DTS-HD MA 5.1 · Tür", "AC-3 2.0 · Yorum"),
            tech.audio.map { audioLine(it, tr, "Yorum") },
        )
        assertEquals("PGS Tür, İng · SRT Tür", subtitleLine(tech.subtitles, tr))
        assertEquals("56 Mbps", bitrateLabel(tech.bitrate, tr))
        assertEquals("58,4 GB", sizeLabel(tech.sizeBytes, tr))
        assertEquals("MKV", tech.container)
        assertEquals("MP4", TechSamples.camSehir.container)
    }

    @Test
    fun numbersFollowTheLocale() {
        assertEquals("1,8 TB", sizeLabel(1_800_000_000_000, tr))
        assertEquals("1.8 TB", sizeLabel(1_800_000_000_000, en))
        assertEquals("4,1 Mbps", bitrateLabel(4_100_000, tr))
        assertEquals("320 kbps", bitrateLabel(320_000, tr))
        assertEquals("734 MB", sizeLabel(734_000_000, tr))
    }

    @Test
    fun languagesAreShortened() {
        assertEquals("Tür", languageShort("tur", tr))
        assertEquals("İng", languageShort("eng", tr))
        assertEquals("Alm", languageShort("ger", tr))
        assertEquals("Alm", languageShort("deu", tr))
        assertEquals("Fra", languageShort("fre", tr))
        assertEquals("Tur", languageShort("tur", en))
        assertEquals("Eng", languageShort("eng", en))
        assertNull(languageShort("und", tr))
        assertNull(languageShort("", tr))
        // S-1501: a code with no name is shown in Latin capitals, not Turkish ones (QAI, not QAİ)
        assertEquals("QAI", languageShort("qai", tr))
    }

    @Test
    fun mainAudioSkipsCommentaries() {
        val tech = TechSamples.tech(
            listOf(TechSamples.audio("ac3", commentary = true, default = true), TechSamples.audio("dts", "DTS-HD MA")),
        )
        assertEquals("dts", tech.mainAudio()!!.codec)
    }

    /** S-304: the format a player bar shows. */
    @Test
    fun musicFormats() {
        fun track(codec: String, rate: Int, bits: Int, bitrate: Long = 0) =
            AudioTech(codec, "", 2, "stereo", rate, bits, bitrate, "", "", true, false, false)
        assertEquals("FLAC 24/96", audioFormatLabel(track("flac", 96000, 24)))
        assertEquals("FLAC 16/44.1", audioFormatLabel(track("flac", 44100, 16)))
        assertEquals("PCM 24/192", audioFormatLabel(track("pcm_s24le", 192000, 24)))
        assertEquals("ALAC 24/88.2", audioFormatLabel(track("alac", 88200, 24)))
        assertEquals("MP3 320 kb/s", audioFormatLabel(track("mp3", 44100, 0, 320_000)))
        assertEquals("AAC", audioFormatLabel(track("aac", 44100, 0)))
    }
}
