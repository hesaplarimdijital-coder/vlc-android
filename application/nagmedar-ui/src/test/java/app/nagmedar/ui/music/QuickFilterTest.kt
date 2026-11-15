package app.nagmedar.ui.music

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Collator
import java.util.Locale

/** 1.1.0 (Ebru): the quick filters over a way in's groups, and the letters the grid puts its groups under. */
class QuickFilterTest {

    private val albums = MusicLibrary(MusicSamples.tracks, Locale.forLanguageTag("tr")).groups(MusicView.Albums)

    /** The samples were all added in 2025 (October). */
    private fun titles(filter: QuickFilter, year: Int = 2025) = albums.quick(filter, MusicSamples.stats, year).map { it.title }.toSet()

    @Test
    fun allKeepsEveryGroup() = assertEquals(albums, albums.quick(QuickFilter.All, MusicSamples.stats, 2025))

    @Test
    fun halfHeardAreThoseWithSomeTracksPlayedAndSomeNot() {
        // Tek: Uzak played, Kuzey Işığı not; Konuk: Ada played, Liman not. Itrî'nin İzinde and Gece Yolcusu were played
        // through, Demo never
        assertEquals(setOf("Tek", "Konuk"), titles(QuickFilter.Unfinished))
    }

    @Test
    fun neverPlayedAreThoseWithoutAPlay() = assertEquals(setOf("Demo"), titles(QuickFilter.Unheard))

    @Test
    fun withoutCountsEveryGroupIsUnheardAndNoneHalfHeard() {
        assertEquals(albums, albums.quick(QuickFilter.Unheard, emptyMap(), 2025))
        assertEquals(emptyList<MusicGroup>(), albums.quick(QuickFilter.Unfinished, emptyMap(), 2025))
    }

    @Test
    fun addedThisYearGoesByTheTracksDates() {
        assertEquals(albums.map { it.title }.toSet(), titles(QuickFilter.ThisYear, 2025))
        assertEquals(emptySet<String>(), titles(QuickFilter.ThisYear, 2026))
    }

    @Test
    fun theDateAddedInSecondsOrMilliseconds() {
        // the medialibrary keeps seconds, a picked folder's list milliseconds (9 October 2025)
        assertEquals(2025, yearAdded(1_760_000_000L))
        assertEquals(2025, yearAdded(1_760_000_000_000L))
        assertEquals(-1, yearAdded(0))
    }

    private fun letter(title: String?, language: String): String {
        val locale = Locale.forLanguageTag(language)
        return letterOf(title, locale, Collator.getInstance(locale).apply { strength = Collator.PRIMARY })
    }

    @Test
    fun turkishKeepsItsOwnLetters() {
        assertEquals("Ç", letter("Çağla Akın", "tr"))
        assertEquals("C", letter("Cemre Kaya", "tr"))
        assertEquals("Ö", letter("öykü", "tr"))
        assertEquals("İ", letter("ilk", "tr"))
        assertEquals("I", letter("ılık", "tr"))
        assertEquals("Ş", letter("Şebnem", "tr"))
    }

    @Test
    fun englishPutsMarkedLettersUnderTheirBareOnes() {
        assertEquals("C", letter("Çağla Akın", "en"))
        assertEquals("O", letter("Öykü", "en"))
        assertEquals("I", letter("İstanbul", "en"))
        assertEquals("E", letter("Élan", "en"))
    }

    private fun group(key: String, title: String?) = MusicGroup(key, title, null, null, emptyList())

    /**
     * The headings over the groups as their order has them: a digit first under "#" at the top, those without the name
     * under their own words at the end; a letter that comes back later is a heading of its own, and no two headings
     * share the grid's key (two with one key would end the grid).
     */
    @Test
    fun theHeadingsAreTheRunsOfTheirLetters() {
        val groups = listOf(group("a", "10cc"), group("b", "Ada"), group("c", "Akın"), group("d", "Bora"), group("e", "Ali"), group("f", null), group("g", " "))
        val entries = lettered(groups, { it.title }, Locale.forLanguageTag("tr"), "Bilinmeyen sanatçı")
        val headings = entries.filterIsInstance<GridEntry.Letter>()
        assertEquals(listOf("#", "A", "B", "A", "Bilinmeyen sanatçı"), headings.map { it.letter })
        assertEquals(listOf(1, 2, 1, 1, 2), headings.map { it.count })
        assertEquals(headings.size, headings.map { it.key }.toSet().size)
        assertEquals(groups.size, entries.count { it is GridEntry.Group })
    }

    @Test
    fun aNameWithoutALetterFirstGoesUnderTheHash() {
        assertEquals("#", letter("3 Doors Down", "en"))
        assertEquals("#", letter("“Kuzey”", "tr"))
        assertEquals("#", letter(null, "tr"))
        assertEquals("#", letter("   ", "tr"))
        // a Hangul syllable is a letter of its own, whole
        assertEquals("한", letter("한국", "ko"))
    }
}
