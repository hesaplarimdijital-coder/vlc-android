package app.nagmedar.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S-1501: no text the user reads is written in the code (TERIMLER.md, rules 13 and 15): no string
 * literal holds a Turkish word, no literal handed straight to a Text, a button or a description
 * holds a word at all, and no translation is upper- or lower-cased in the code, nor in Turkish when
 * the interface is English.
 */
class HardcodedTextTest {

    /** Sources whose Turkish literals are not texts of the interface, and why. */
    private val turkishAllowed = mapOf(
        "app/nagmedar/ui/lab/AudioReport.kt" to "the lab's SP-01 report: a diagnostic, in Turkish only",
        "app/nagmedar/ui/lab/DisplayReport.kt" to "the lab's SP-04 report: a diagnostic, in Turkish only",
        "app/nagmedar/ui/library/VideoLibrary.kt" to "patterns reading Turkish file names: 1. Sezon 2. Bölüm, türkçe",
    )

    private val roots = listOf("src/main/java", "../nagmedar-session/src/main/java", "../nagmedar-data/src/main/java")

    private val sources: List<KotlinSource> by lazy {
        roots.map(::File).filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }
                .map { KotlinSource(it.relativeTo(root).invariantSeparatorsPath, it.readText()) }
                .toList()
        }
    }

    private val turkishLetters = Regex("[çğıöşüâîûÇĞİÖŞÜÂÎÛ]")

    /** Where a literal is the text of something shown: Text("…"), NagButton("…"), contentDescription = "…". */
    private val shown = Regex(
        """(?:\b(?:Text|NagButton|SyntaxLine|ShelfTitle|SuggestionChip|FilterChip|PageHeader)\(\s*""" +
            """|\b(?:contentDescription|stateDescription|placeholder|text|title|subtitle|hint|message)\s*=\s*)$""",
    )

    private fun words(literal: String) = literal.replace(Regex("""\$\{}|\$[A-Za-z_][A-Za-z0-9_]*"""), "").any { it.isLetter() }

    @Test
    fun theSourcesAreReadAsKotlin() {
        val sample = KotlinSource(
            "sample.kt",
            listOf(
                "// \"yorum\" bir yorumda",
                "/* \"blok\" /* iç içe */ hâlâ yorum */",
                "val a = \"metin \${if (b) \"iç\" else \"dış\"} son\"",
                "val c = '\"'",
                "val d = \"\"\"ham \"tırnak\" metin\"\"\"",
                "Text(\"Başlık\")",
            ).joinToString("\n"),
        )
        assertEquals(listOf("iç", "dış", "metin \${} son", "ham \"tırnak\" metin", "Başlık"), sample.literals.map { it.text })
        assertEquals(listOf(3, 3, 3, 5, 6), sample.literals.map { it.line })
        assertEquals(listOf(false, false, false, false, true), sample.literals.map { shown.containsMatchIn(it.before) })
        assertTrue("comments are blanked", "yorum" !in sample.code && "Başlık" in sample.code)
        assertTrue("${sources.size} sources", sources.size > 50)
        assertTrue(sources.single { it.file == "app/nagmedar/ui/screens/AboutScreen.kt" }.literals.any { it.text == "about-libraries" })
    }

    @Test
    fun noTurkishInTheCode() {
        val wrong = sources.filter { it.file !in turkishAllowed }.flatMap { source ->
            source.literals.filter { turkishLetters.containsMatchIn(it.text) }.map { "${source.file}:${it.line} \"${it.text}\"" }
        }
        assertEquals(emptyList<String>(), wrong)
        // no stale exception
        turkishAllowed.keys.forEach { file -> assertTrue(file, sources.any { it.file == file }) }
    }

    @Test
    fun noWordsHandedToTheInterface() {
        val wrong = sources.flatMap { source ->
            source.literals.filter { shown.containsMatchIn(it.before) && words(it.text) }.map { "${source.file}:${it.line} \"${it.text}\"" }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun translationsAreCasedInTheirLanguage() {
        val turkishLocale = Regex("""forLanguageTag\(\s*"tr"\s*\)|Locale\(\s*"tr"""")
        // a translation may be upper-cased in the interface's language only: uppercase(currentLocale())
        val casedTranslation = Regex(
            """(stringResource|getString)\([^()]*\)\s*\.\s*(?:(?:lowercase|uppercase)\((?!\s*currentLocale\(\)\s*\))|capitalize\(|replaceFirstChar)""",
        )
        val wrong = sources.flatMap { source ->
            val found = mutableListOf<String>()
            // search folds the Turkish way in every language, on purpose
            if (source.file != "app/nagmedar/ui/search/FuzzyMatch.kt") {
                turkishLocale.findAll(source.code).forEach { found += "${source.file}:${source.lineOf(it.range.first)} a Turkish locale" }
            }
            casedTranslation.findAll(source.code).forEach { found += "${source.file}:${source.lineOf(it.range.first)} a translation cased in the code" }
            found
        }
        assertEquals(emptyList<String>(), wrong)
    }
}
