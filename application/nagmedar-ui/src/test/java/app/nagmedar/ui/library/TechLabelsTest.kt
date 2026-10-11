package app.nagmedar.ui.library

import app.nagmedar.lab.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** S-802: the badges and the technical lines read as in the mockups, numbers and languages in Turkish. */
class TechLabelsTest {

    private val tr = Locale("tr", "TR")
    private val en = Locale.ENGLISH

    @Test
    fun cardBadges() {
        assertEquals("4K DV", techBadge(TechSamples.kuzeyIsigi, 0, 0))
        assertEquals("4K HDR", techBadge(TechSamples.sessizLiman, 0, 0))
        assertEquals("4K HDR10+", techBadge(TechSamples.kirikPusula, 0, 0))
        assertEquals("HD", techBadge(TechSamples.maviKanal, 0, 0))
        // before the probe: the medialibrary's size, by its larger side
        assertEquals("4K", techBadge(null, 3840, 1600))
        assertEquals("HD", techBadge(null, 1920, 800))
        assertNull(techBadge(null, 0, 0))
    }

    @Test
    fun cardSummaries() {
        assertEquals("2160p · DV P7 · Atmos", techSummary(TechSamples.kuzeyIsigi))
        assertEquals("2160p · DV P8 · TrueHD", techSummary(TechSamples.demirVadi))
        assertEquals("2160p · HDR10+ · E-AC-3", techSummary(TechSamples.kirikPusula))
        assertEquals("1080p · SDR · DTS", techSummary(TechSamples.maviKanal))
        assertEquals("2160p · DV P5 · Atmos", techSummary(TechSamples.camSehir))
    }

    @Test
    fun detailLines() {
        val tech = TechSamples.kuzeyIsigi
        val video = tech.video!!
        assertEquals("HEVC Main 10 · 3840×2160", videoLine(video))
        assertEquals("23,976 fps", frameRateLabel(video.fps, tr))
        assertEquals("Dolby Vision P7 FEL · HDR10", rangeLine(video, tr))
        assertEquals(
            listOf("TrueHD 7.1 Atmos · İng", "DTS-HD MA 5.1 · Tür", "AC-3 2.0 · Yorum"),
            tech.audio.map { audioLine(it, tr, "Yorum") },
        )
        assertEquals("PGS Tür, İng · SRT Tür", subtitleLine(tech.subtitles, tr))
        assertEquals("56 Mbps", bitrateLabel(tech.bitrate, tr))
        assertEquals("58,4 GB", sizeLabel(tech.sizeBytes, tr))
        assertEquals("MKV", tech.container)
        assertEquals("MP4", TechSamples.camSehir.container)
        // profile 5 has no base layer a TV without Dolby Vision could show
        assertEquals("Dolby Vision P5", rangeLine(TechSamples.camSehir.video!!, tr))
        assertEquals("HDR10 · 1.000 nit", rangeLine(TechSamples.sessizLiman.video!!, tr))
        assertEquals("SDR · 8 bit", rangeLine(TechSamples.maviKanal.video!!, tr))
    }

    @Test
    fun numbersFollowTheLocale() {
        assertEquals("23.976 fps", frameRateLabel(23.976, en))
        assertEquals("25 fps", frameRateLabel(25.0, tr))
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
    }

    @Test
    fun mainAudioSkipsCommentaries() {
        val tech = TechSamples.tech(
            TechSamples.video(),
            listOf(TechSamples.audio("ac3", commentary = true, default = true), TechSamples.audio("dts", "DTS-HD MA")),
        )
        assertEquals("dts", tech.mainAudio()!!.codec)
        assertEquals(Resolution.UHD, resolutionOf(3840, 2160))
    }
}
