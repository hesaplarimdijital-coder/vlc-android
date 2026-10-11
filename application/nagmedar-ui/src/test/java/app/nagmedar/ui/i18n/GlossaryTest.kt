package app.nagmedar.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * S-1501: the texts use the terms of the glossary (TERIMLER.md): no variant of its "Kullanmayın"
 * (Turkish) and "Avoid" (English) columns appears in them, outside the proper names it lists.
 * A variant is found at a word's start, with whatever suffix ("kuyruk" finds "kuyruğa").
 */
class GlossaryTest {

    private class Term(val turkish: String, val english: String, val avoidTurkish: List<String>, val avoidEnglish: List<String>)

    private val turkish = Locale.forLanguageTag("tr")

    private val glossary = File("TERIMLER.md").readText()

    private fun section(title: String): List<String> = glossary.substringAfter("\n## $title\n", "").substringBefore("\n## ").lines()

    private val terms: List<Term> = section("Terimler").filter { it.startsWith("|") }.drop(2).map { row ->
        val cells = row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
        assertEquals(row, 5, cells.size)
        fun list(cell: String) = cell.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        Term(cells[0], cells[1], list(cells[2]), list(cells[3]))
    }

    private val properNames = section("Özel adlar").filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }

    @Test
    fun theGlossaryIsRead() {
        assertTrue("${terms.size} terms", terms.size >= 40)
        assertTrue(terms.count { it.avoidTurkish.isNotEmpty() } >= 10)
        assertTrue(terms.count { it.avoidEnglish.isNotEmpty() } >= 10)
        assertTrue(terms.any { it.turkish == "Sıradaki" && it.english == "Up next" && "kuyruk" in it.avoidTurkish })
        assertTrue("GNU General Public License" in properNames)
    }

    @Test
    fun aVariantIsFoundWithItsSuffixes() {
        assertTrue(uses("Yerel laboratuvar kitaplığı yok", "kitaplık", turkish))
        assertTrue(uses("Kuyruğa eklendi", "kuyruk", turkish))
        assertTrue(uses("İnternet radyosu dizini", "dizin", turkish))
        assertFalse(uses("Tüm diziler", "dizin", turkish))
        assertTrue(uses("Oynatma sırasına ekle", "oynatma sırası", turkish))
        assertTrue(uses("Browse movies", "movie", Locale.ROOT))
        assertFalse(uses("Remove from the queue", "play queue", Locale.ROOT))
    }

    @Test
    fun turkishTextsUseTheTerms() = check(Texts.turkish, turkish) { it.avoidTurkish to it.turkish }

    @Test
    fun englishTextsUseTheTerms() = check(Texts.english, Locale.ROOT) { it.avoidEnglish to it.english }

    private fun check(texts: Map<String, String>, locale: Locale, pick: (Term) -> Pair<List<String>, String>) {
        val wrong = mutableListOf<String>()
        for ((key, text) in texts) {
            val checked = properNames.fold(text) { rest, name -> rest.replace(name, "") }
            for (term in terms) {
                val (avoid, use) = pick(term)
                avoid.filter { uses(checked, it, locale) }.forEach { wrong += "$key: “$it” → “$use” in “$text”" }
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    /** Whether a word of [text] starts with [phrase]; in Turkish also with its last consonant softened (kitaplık → kitaplığı). */
    private fun uses(text: String, phrase: String, locale: Locale): Boolean {
        val lower = text.lowercase(locale)
        val stem = phrase.lowercase(locale)
        val stems = if (locale == turkish) setOf(stem, softened(stem)) else setOf(stem)
        return stems.any { Regex("""(?<![\p{L}\p{N}])""" + Regex.escape(it)).containsMatchIn(lower) }
    }

    private fun softened(word: String): String = when (word.lastOrNull()) {
        'k' -> word.dropLast(1) + 'ğ'
        'p' -> word.dropLast(1) + 'b'
        't' -> word.dropLast(1) + 'd'
        'ç' -> word.dropLast(1) + 'c'
        else -> word
    }
}
