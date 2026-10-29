package app.nagmedar.ui.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.vlc.media.VoiceFocus
import org.videolan.vlc.media.VoiceRequest

/** 1.0: Android Auto's spoken requests, read as people say them. */
class CarVoiceTest {

    @Test
    fun wordsAreFoldedForComparing() {
        assertEquals("cagri sesli", CarVoice.fold("  ÇAĞRI   Şesli! "))
        assertEquals("istanbul", CarVoice.fold("İSTANBUL"))
        assertEquals("isik", CarVoice.fold("Işık"))
        assertEquals("tarkan", CarVoice.fold("Tarkan'ı"))
        assertEquals("radyo eksen", CarVoice.fold("Radyo Eksen'i"))
        // an English apostrophe is no Turkish ending
        assertEquals("don t stop me now", CarVoice.fold("Don't Stop Me Now"))
        assertEquals("", CarVoice.fold(null))
    }

    @Test
    fun aTurkishRequestLosesItsCommand() {
        assertEquals(VoiceQuery("tarkan", VoiceKind.Any), CarVoice.parse("Tarkan'ı çal"))
        assertEquals(VoiceQuery("sezen aksu", VoiceKind.Any), CarVoice.parse("Sezen Aksu oynat"))
        assertEquals(VoiceQuery("kalben", VoiceKind.Any), CarVoice.parse("Nağmedar'da Kalben'i çal"))
        assertEquals(VoiceQuery("kalben", VoiceKind.Any), CarVoice.parse("Kalben'i Nağmedar ile çal"))
    }

    @Test
    fun theKindARequestNamesIsReadOff() {
        assertEquals(VoiceQuery("fularsiz entellik", VoiceKind.Podcast), CarVoice.parse("Fularsız Entellik podcast'ini çal"))
        assertEquals(VoiceQuery("eksen", VoiceKind.Radio), CarVoice.parse("Eksen radyosunu aç"))
        assertEquals(VoiceQuery("radyo eksen", VoiceKind.Any), CarVoice.parse("Radyo Eksen'i aç"))
        assertEquals(VoiceQuery("muslum gurses", VoiceKind.Music), CarVoice.parse("Müslüm Gürses şarkılarını çal"))
        assertEquals(VoiceQuery("", VoiceKind.Radio), CarVoice.parse("radyo aç"))
        assertEquals(VoiceQuery("", VoiceKind.Podcast), CarVoice.parse("podcast dinle"))
    }

    @Test
    fun anEnglishRequestToo() {
        assertEquals(VoiceQuery("jazz fm", VoiceKind.Radio), CarVoice.parse("play Jazz FM radio on Nagmedar"))
        assertEquals(VoiceQuery("the daily", VoiceKind.Podcast), CarVoice.parse("play the podcast The Daily"))
        assertEquals(VoiceQuery("the beatles", VoiceKind.Any), CarVoice.parse("listen to The Beatles"))
        assertEquals(VoiceQuery("", VoiceKind.Any), CarVoice.parse(""))
    }

    @Test
    fun candidatesAreRanked() {
        assertEquals(CarVoice.EXACT, CarVoice.score("tarkan", "TARKAN"))
        assertEquals(CarVoice.PREFIX, CarVoice.score("sezen", "Sezen Aksu"))
        assertEquals(CarVoice.WORDS, CarVoice.score("eksen", "Radyo Eksen"))
        assertEquals(CarVoice.PART, CarVoice.score("ksen", "Radyo Eksen"))
        assertEquals(0, CarVoice.score("metallica", "Radyo Eksen"))
        assertEquals(0, CarVoice.score("", "Radyo Eksen"))
    }

    @Test
    fun theBestAnswerWinsAndTooLittleIsNone() {
        val names = listOf("Radyo Eksen", "Eksen", "Açık Radyo")
        assertEquals("Eksen" to CarVoice.EXACT, CarVoice.best("eksen", names) { it })
        assertEquals("Açık Radyo" to CarVoice.PREFIX, CarVoice.best("acik", names) { it })
        assertNull(CarVoice.best("ksen", names) { it })
        assertTrue(CarVoice.best("ksen", names, least = CarVoice.PART) { it } != null)
    }

    @Test
    fun anEmptyRequestAsksForWhatPlayedLast() {
        assertTrue(VoiceRequest("").isEmpty)
        assertTrue(VoiceRequest("çal").isEmpty)
        assertTrue(!VoiceRequest("radyo aç").isEmpty)
        assertTrue(!VoiceRequest("", focus = VoiceFocus.Artist, artist = "Tarkan").isEmpty)
    }
}
