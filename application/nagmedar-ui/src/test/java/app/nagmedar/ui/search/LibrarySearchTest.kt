package app.nagmedar.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-311: the query's syntax, ranking, Turkish letters, and 10 000 tracks in under 100 ms. */
class LibrarySearchTest {

    private val items = listOf(
        Searchable("a", SearchKind.Film, "Kuzey Işığı", "Bilim Kurgu"),
        Searchable("b", SearchKind.Music, "Sabaha Kadar", "Ada Karan Gece Mavisi"),
        Searchable("c", SearchKind.Music, "Kuzeyden Esen", "Ada Karan"),
        Searchable("d", SearchKind.Film, "Güney Kuzey", "Dram"),
        Searchable("e", SearchKind.Film, "Kuzey Işığı Fragman", ""),
    )

    private fun find(query: String) = items.search(SearchQuery.parse(query)).map { it.value }

    @Test
    fun theQueryIsTakenApart() {
        val query = SearchQuery.parse("Kuzey \"ışık yılı\" -fragman tür:film tur:müzik")
        assertEquals(listOf("kuzey"), query.words)
        assertEquals(listOf("isik yili"), query.phrases)
        assertEquals(listOf("fragman"), query.excluded)
        assertEquals(setOf(SearchKind.Film, SearchKind.Music), query.kinds)
        assertTrue(SearchQuery.parse("   ").isEmpty)
    }

    @Test
    fun titlesStartingWithTheWordComeFirst() {
        assertEquals(listOf("a", "c", "e", "d"), find("kuzey"))
    }

    @Test
    fun turkishLettersMatchPlainOnes() {
        assertEquals(listOf("a", "e"), find("isigi"))
        assertEquals(listOf("a", "e"), find("IŞIĞI"))
        assertEquals(listOf("d"), find("guney"))
    }

    @Test
    fun wordsPhrasesExclusionsAndKinds() {
        assertEquals(setOf("b", "c"), find("ada").toSet())
        assertEquals(listOf("c"), find("kuzey ada"))
        assertEquals(listOf("a", "c", "d"), find("kuzey -fragman"))
        assertEquals(listOf("c"), find("kuzey tür:müzik"))
        assertEquals(listOf("a", "e"), find("\"kuzey isigi\""))
        assertEquals(emptyList<String>(), find("\"isigi kuzey\""))
    }

    @Test
    fun highlightsFollowTheShownText() {
        assertEquals(listOf(0 until 3, 6 until 11), highlightRanges("Kuzey Işığı Fragman", SearchQuery.parse("ışığı kuz")))
    }

    @Test
    fun tenThousandTracksInUnderAHundredMilliseconds() {
        val library = (0 until 10_000).map { Searchable(it, SearchKind.Music, "Parça numarası $it", "Sanatçı ${it % 300} Albüm ${it % 900}") }
        val query = SearchQuery.parse("sanatci 12 album")
        repeat(5) { library.search(query) }
        val start = System.nanoTime()
        val hits = library.search(query).size
        val millis = (System.nanoTime() - start) / 1_000_000
        assertTrue("$hits hits", hits > 0)
        assertTrue("$millis ms", millis < 100)
    }
}
