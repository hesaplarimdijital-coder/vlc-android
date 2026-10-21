package app.nagmedar.ui.podcast

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-1104: transcripts in the formats feeds name: WebVTT, SubRip, Podcasting 2.0's JSON, HTML and plain text. */
@RunWith(AndroidJUnit4::class)
class TranscriptsTest {

    private val vtt = """
        WEBVTT

        NOTE bir not
        burada

        intro
        00:00:01.000 --> 00:00:04.500 align:start
        <v Ayşe>Merhaba, <i>gece</i> yayınına hoş geldiniz.

        01:02.250 --> 01:05.000
        <v Mert>Teşekkürler &amp; selamlar.
        İkinci satır.

        1:00:00.000 --> 1:00:03.000
        Bir saat sonra.
    """.trimIndent()

    @Test
    fun webVttReadsItsCuesSpeakersAndTimes() {
        val transcript = Transcripts.parse(vtt, "text/vtt")!!
        assertTrue(transcript.timed)
        assertEquals(3, transcript.cues.size)
        val first = transcript.cues[0]
        assertEquals(1_000L, first.startMs)
        assertEquals(4_500L, first.endMs)
        assertEquals("Ayşe", first.speaker)
        assertEquals("Merhaba, gece yayınına hoş geldiniz.", first.text)
        assertEquals(62_250L, transcript.cues[1].startMs)
        assertEquals("Teşekkürler & selamlar. İkinci satır.", transcript.cues[1].text)
        assertEquals("Mert", transcript.cues[1].speaker)
        assertEquals(3_600_000L, transcript.cues[2].startMs)
        assertNull(transcript.cues[2].speaker)
    }

    @Test
    fun aFileThatStartsWithWebVttIsOneWhateverItWasCalled() {
        assertEquals(3, Transcripts.parse(vtt, "text/plain")!!.cues.size)
        assertEquals(3, Transcripts.parse(vtt, null)!!.cues.size)
    }

    private val srt = """
        1
        00:00:00,500 --> 00:00:02,000
        <i>Merhaba</i> dünya.

        2
        00:00:02,500 --> 00:00:05,000
        {\an8}Gece yarısı
        ikinci satır
    """.trimIndent()

    @Test
    fun subRipReadsItsCues() {
        val transcript = Transcripts.parse(srt, "application/x-subrip")!!
        assertEquals(listOf("Merhaba dünya.", "Gece yarısı ikinci satır"), transcript.cues.map { it.text })
        assertEquals(listOf(500L, 2_500L), transcript.cues.map { it.startMs })
        assertEquals(5_000L, transcript.cues[1].endMs)
    }

    @Test
    fun aByteOrderMarkAndWindowsLineEndsRead() {
        val transcript = Transcripts.parse("﻿" + srt.replace("\n", "\r\n"), "application/srt")!!
        assertEquals(2, transcript.cues.size)
    }

    @Test
    fun podcastingJsonReadsSegments() {
        val json = """{"version": "1.0.0", "segments": [
            {"speaker": "Ayşe", "startTime": 4.5, "endTime": 8, "body": "İkinci."},
            {"speaker": "Ayşe", "startTime": 0, "endTime": 4.5, "body": "Birinci."},
            {"startTime": 9, "body": ""}
        ]}"""
        val transcript = Transcripts.parse(json, "application/json")!!
        assertEquals(listOf("Birinci.", "İkinci."), transcript.cues.map { it.text })
        assertEquals(4_500L, transcript.cues[1].startMs)
        assertEquals("Ayşe", transcript.cues[0].speaker)
        assertTrue(transcript.timed)
    }

    @Test
    fun htmlAndPlainTextAreUntimed() {
        val html = Transcripts.parse("<html><body><p>Birinci paragraf.</p><p>İkinci <b>paragraf</b>.</p></body></html>", "text/html")!!
        assertFalse(html.timed)
        assertEquals(listOf("Birinci paragraf.", "İkinci paragraf."), html.cues.map { it.text })
        assertEquals(-1, html.indexAt(10_000))

        val plain = Transcripts.parse("Satır bir\nsatır iki\n\nYeni paragraf", "text/plain")!!
        assertEquals(listOf("Satır bir satır iki", "Yeni paragraf"), plain.cues.map { it.text })
    }

    @Test
    fun plainTextInBracketsIsNotMistakenForJson() {
        val transcript = Transcripts.parse("[Müzik]\n\nHoş geldiniz.", "text/plain")!!
        assertEquals(listOf("[Müzik]", "Hoş geldiniz."), transcript.cues.map { it.text })
    }

    @Test
    fun nothingReadableIsNull() {
        assertNull(Transcripts.parse("", "text/vtt"))
        assertNull(Transcripts.parse("   ", null))
        assertNull(Transcripts.parse("WEBVTT\n\nNOTE yalnız not", "text/vtt"))
        assertNull(Transcripts.parse("{\"version\": 1}", "application/json"))
        assertNull(Transcripts.parse("bu bir şey", "application/pdf"))
    }

    @Test
    fun theCueBeingSaidAtATimeIsTheLastThatStarted() {
        val transcript = Transcripts.parse(vtt, "text/vtt")!!
        assertEquals(-1, transcript.indexAt(500))
        assertEquals(0, transcript.indexAt(1_000))
        assertEquals(0, transcript.indexAt(62_000))
        assertEquals(1, transcript.indexAt(62_250))
        assertEquals(2, transcript.indexAt(9_999_999))
    }

    @Test
    fun searchingFindsWordsAndSpeakersIgnoringCase() {
        val transcript = Transcripts.parse(vtt, "text/vtt")!!
        assertEquals(listOf(0), transcript.find("GECE"))
        assertEquals(listOf(1), transcript.find("mert"))
        assertEquals(emptyList<Int>(), transcript.find("yok böyle bir şey"))
        assertEquals(emptyList<Int>(), transcript.find("  "))
    }

    @Test
    fun cuesOutOfOrderAreSorted() {
        val transcript = Transcripts.parse("WEBVTT\n\n00:00:09.000 --> 00:00:10.000\nson\n\n00:00:01.000 --> 00:00:02.000\nilk", "text/vtt")!!
        assertEquals(listOf("ilk", "son"), transcript.cues.map { it.text })
    }
}
