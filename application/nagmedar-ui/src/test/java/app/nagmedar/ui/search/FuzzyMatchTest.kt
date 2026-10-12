package app.nagmedar.ui.search

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-305, S-311: Turkish folding and fuzzy matching. */
class FuzzyMatchTest {

    @Test
    fun turkishLettersFold() {
        assertEquals("isik izlenmedi sarki cagri olu uzum", foldForSearch("IŞIK İzlenmedi Şarkı Çağrı Ölü Üzüm"))
        assertEquals("kar", foldForSearch("Kâr"))
    }

    @Test
    fun charactersMustComeInOrder() {
        assertNotNull(FuzzyMatcher.match("isik", "Işık Yılı"))
        assertNotNull(FuzzyMatcher.match("ISIK", "ışık"))
        assertNotNull(FuzzyMatcher.match("sarki", "Şarkı Listesi"))
        assertNull(FuzzyMatcher.match("kis", "Işık"))
        assertNull(FuzzyMatcher.match("x", "Sesi artır"))
        // spaces in the query are ignored
        assertNotNull(FuzzyMatcher.match("ses ar", "Sesi artır"))
    }

    @Test
    fun matchedPositionsPointIntoTheText() {
        val match = FuzzyMatcher.match("ekran", "Tam ekran")!!
        assertArrayEquals(intArrayOf(4, 5, 6, 7, 8), match.positions)
        val start = FuzzyMatcher.match("dosya", "Altyazı dosyası ekle")!!
        assertArrayEquals(intArrayOf(8, 9, 10, 11, 12), start.positions)
    }

    @Test
    fun wordStartsAndRunsRankFirst() {
        fun score(query: String, text: String) = FuzzyMatcher.match(query, text)!!.score
        // a run beats scattered letters
        assertTrue(score("ses", "Sesi artır") > score("ses", "Sonraki ekrana sür"))
        // a word start beats the middle of a word
        assertTrue(score("ac", "Ayarları aç") > score("ac", "Kenar çubuğunu daralt"))
        // at the start of the text beats later
        assertTrue(score("dosya", "Dosya aç…") > score("dosya", "Altyazı dosyası ekle"))
        // a shorter text with the same match first
        assertTrue(score("tam", "Tam ekran") > score("tam", "Tam ekran ve kenarlıksız pencere"))
        // a whole word beats initials, however early
        assertTrue(score("aç", "Dosya aç…") > score("aç", "A/B testi: çalanı değiştir"))
        assertArrayEquals(intArrayOf(6, 7), FuzzyMatcher.match("aç", "Dosya aç…")!!.positions)
    }

    @Test
    fun tenThousandTitlesInAFewMilliseconds() {
        val titles = (0 until 10_000).map { "Parça $it — Sanatçı ${it % 300} · Albüm ${it % 900}" }
        repeat(3) { titles.count { FuzzyMatcher.match("sanat 12 alb", it) != null } }
        val start = System.nanoTime()
        val hits = titles.count { FuzzyMatcher.match("sanat 12 alb", it) != null }
        val millis = (System.nanoTime() - start) / 1_000_000
        assertTrue("$hits hits", hits > 0)
        // S-311 asks for 100 ms on a device; the JVM of CI is given room
        assertTrue("$millis ms", millis < 400)
    }
}
