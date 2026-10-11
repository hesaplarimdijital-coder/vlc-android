package app.nagmedar.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-1501: every text of the new interface is in English and in Turkish, with the same
 * placeholders, and written by the rules of TERIMLER.md: “…” quotes, a one-character ellipsis,
 * Ctrl+K, a space between a number and its unit, and "siz" in Turkish.
 */
class StringsTest {

    private val english = Texts.english
    private val turkish = Texts.turkish

    /** Texts that show a straight quote on purpose: a search for an exact phrase is typed so. */
    private val straightQuotes = setOf("nag_search_syntax_phrase_example")

    @Test
    fun everyTextIsInBothLanguages() {
        assertTrue("${english.size} texts", english.size > 400)
        assertTrue(english.keys.any { it.startsWith("nagmedar_") })
        assertEquals("only in English", emptySet<String>(), english.keys - turkish.keys)
        assertEquals("only in Turkish", emptySet<String>(), turkish.keys - english.keys)
    }

    @Test
    fun pluralsHaveOneAndOther() {
        for (texts in listOf(english, turkish)) {
            val plurals = texts.keys.filter { '#' in it }.map { it.substringBefore('#') }.toSet()
            assertTrue(plurals.isNotEmpty())
            for (name in plurals) {
                assertTrue("$name#one", "$name#one" in texts)
                assertTrue("$name#other", "$name#other" in texts)
            }
        }
    }

    @Test
    fun theSamePlaceholdersInBothLanguages() {
        val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""")
        fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.sorted().toList()
        val different = english.keys.intersect(turkish.keys)
            .filter { placeholders(english.getValue(it)) != placeholders(turkish.getValue(it)) }
            .map { "$it: ${english[it]} / ${turkish[it]}" }
        assertEquals(emptyList<String>(), different)
    }

    @Test
    fun typography() {
        val ctrl = Regex("""\b(CTRL|ctrl)\b|\b(Ctrl|Shift|Alt|Meta)(\s+|-)[A-Z0-9](?![a-z])""")
        val glued = Regex("""\d(ms|sn|dk|sa|s|min|h|Hz|kHz|fps|kbps|Mbps|kB|MB|GB)\b""")
        val wrong = mutableListOf<String>()
        for ((language, texts) in listOf("en" to english, "tr" to turkish)) {
            for ((key, text) in texts) {
                if (text.isBlank()) wrong += "$language $key: empty"
                if (text != text.trim()) wrong += "$language $key: spaces around “$text”"
                if ("..." in text) wrong += "$language $key: … rather than ... in “$text”"
                if ('"' in text && key !in straightQuotes) wrong += "$language $key: “…” rather than \"…\" in “$text”"
                if (ctrl.containsMatchIn(text)) wrong += "$language $key: Ctrl+K in “$text”"
                if (glued.containsMatchIn(text)) wrong += "$language $key: a space before the unit in “$text”"
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    /** "Seçin", "basın", "değiştirebilirsiniz": never "seç", "bas", "değiştirebilirsin" said to the user. */
    @Test
    fun turkishSaysSiz() {
        val sen = Regex(
            """(?<!\p{L})(sen|senin|sana|seni|seninle|sende|senden)(?!\p{L})|\p{L}+(abilir|ebilir)sin(?!\p{L})|\p{L}+(ıyor|iyor|uyor|üyor)sun(?!\p{L})""",
            RegexOption.IGNORE_CASE,
        )
        val wrong = turkish.filter { (_, text) -> sen.containsMatchIn(text) }.map { (key, text) -> "$key: $text" }
        assertEquals(emptyList<String>(), wrong)
    }
}
