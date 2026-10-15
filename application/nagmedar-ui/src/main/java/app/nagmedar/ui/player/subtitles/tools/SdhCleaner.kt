package app.nagmedar.ui.player.subtitles.tools

import androidx.compose.runtime.Immutable

/**
 * What the cleaning of subtitles for the hard of hearing takes out (S-708): sound descriptions in
 * brackets and in parentheses, speaker labels, the lines of music, the font tags. Italics stay.
 */
@Immutable
data class SdhOptions(
    /** "[kapı çarpar]", "[door slams]": square brackets are always a description. */
    val brackets: Boolean = true,
    /** "(MÜZİK)", "(güler)": a description in parentheses when it is in capitals, or starts or ends its line. */
    val parentheses: Boolean = true,
    /** "JOHN:", "DR. SMITH:", "ADAM (O.S.):" at the start of a line. */
    val speakerLabels: Boolean = true,
    /** Also names written as names are: "John:", "Ayşe Hanım:". Off by default: "Not:" and "Dikkat:" look the same. */
    val namedSpeakers: Boolean = false,
    /** Lines with a music note: "♪ la la la ♪". Off by default: the lyrics are sung words too. */
    val music: Boolean = false,
    /** "<font color=…>" and "</font>": the colour tags of some releases. */
    val fontTags: Boolean = true,
) {
    /** Whether any rule is on. */
    val any: Boolean get() = brackets || parentheses || speakerLabels || namedSpeakers || music || fontTags
}

/** A cue the cleaning changed: its text before, and after (null: nothing was left, the cue goes). */
@Immutable
data class CueChange(val cueId: Long, val number: Int, val startMs: Long, val before: String, val after: String?)

/** The cleaning's result: the cues left, and what changed. */
@Immutable
data class SdhResult(val cues: List<TimedCue>, val changes: List<CueChange>, val before: Int) {
    val after: Int get() = cues.size
    val removed: Int get() = changes.count { it.after == null }
    val edited: Int get() = changes.count { it.after != null }
}

/**
 * The rules of the cleaning for the hard of hearing. Every rule works on one cue's text, its lines
 * apart by "\n"; markup (`<i>…</i>`, `{\an8}`) is not text and stays where it is.
 */
object SdhCleaner {

    /** Markup that is not said: HTML-like tags and ASS override blocks. */
    private val markup = Regex("""<[^>]*>|\{[^}]*\}""")
    private val fontTag = Regex("""</?font\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val square = Regex("""\[[^\[\]]*]""")
    private val round = Regex("""\(([^()]*)\)""")
    private val emptyPair = Regex("""<(i|b|u)>\s*</\1>""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""[ \t]{2,}""")
    private val innerSpace = Regex("""(<(?:i|b|u)>)[ \t]+|[ \t]+(</(?:i|b|u)>)""", RegexOption.IGNORE_CASE)
    private val overrides = Regex("""\{[^}]*\}""")
    private val spaceBeforePunctuation = Regex("""[ \t]+([.,!?…:;])""")
    private val note = Regex("""[♪♫]""")

    /** The name part of a label in capitals: JOHN, DR., O'BRIEN, ŞEREF, AGENT-7. */
    private const val CAPS_WORD = """\p{Lu}[\p{Lu}\p{Nd}'’.\-]*"""

    /** What may stand before a label: spaces, a dash for a speaker change, markup. */
    private const val LEAD = """((?:[ \t]|[-–—]|<[^>]*>|\{[^}]*\})*)"""

    private val capsLabel = Regex("""^$LEAD$CAPS_WORD(?:[ \t]+$CAPS_WORD){0,3}(?:[ \t]*\([^()]*\))?[ \t]*:(?:[ \t]+|$)""")
    private val namedLabel = Regex("""^$LEAD\p{Lu}[\p{L}'’.\-]+(?:[ \t]+\p{Lu}[\p{L}'’.\-]+)?[ \t]*:(?:[ \t]+|$)""")
    private val leadingDash = Regex("""^((?:<[^>]*>|\{[^}]*\})*)[ \t]*[-–—][ \t]*""")

    /** Spaces left between the override blocks that open a line and its words, where a description stood. */
    private val afterOverrides = Regex("""^((?:\{[^}]*\})+)[ \t]+""")

    fun visible(text: String): String = text.replace(markup, "")

    /** [cues] cleaned by [options]; the cues left in their order, and what changed in each. */
    fun clean(cues: List<TimedCue>, options: SdhOptions): SdhResult {
        val kept = ArrayList<TimedCue>(cues.size)
        val changes = ArrayList<CueChange>()
        cues.forEachIndexed { index, cue ->
            val text = cleanText(cue.text, options)
            when {
                text == cue.text -> kept += cue
                text == null -> changes += CueChange(cue.id, index + 1, cue.startMs, cue.text, null)
                else -> {
                    kept += cue.copy(text = text)
                    changes += CueChange(cue.id, index + 1, cue.startMs, cue.text, text)
                }
            }
        }
        return SdhResult(kept, changes, cues.size)
    }

    /** Stands for an override block while the rules look at the text around it. */
    private const val MASK = '\uE000'

    /** [rules] applied to [text] with each override block replaced by a mark; the blocks back in their places after. */
    private fun withoutOverrides(text: String, rules: (String) -> String): String {
        val blocks = overrides.findAll(text).map { it.value }.toList()
        if (blocks.isEmpty()) return rules(text)
        val result = rules(text.replace(overrides, MASK.toString()))
        var next = 0
        return result.map { if (it == MASK && next < blocks.size) blocks[next++] else it.toString() }.joinToString("")
    }

    /** [text] cleaned; the same text when no rule touched it, null when nothing is left of it. */
    fun cleanText(text: String, options: SdhOptions): String? {
        var work = text
        if (options.fontTags) work = work.replace(fontTag, "")
        // descriptions are looked for in the said text only: an ASS override block ({\clip(0,0,9,9)}) is not one
        if (options.brackets || options.parentheses) {
            work = withoutOverrides(work) { masked ->
                var step = masked
                if (options.brackets) step = step.replace(square) { it.value.filter { c -> c == MASK } }
                if (options.parentheses) step = removeParentheses(step)
                step
            }
        }
        var lines = work.split('\n')
        if (options.music) lines = lines.filterNot { note.containsMatchIn(visible(it)) }
        if (options.speakerLabels) lines = lines.map { dropLabel(it, capsLabel) }
        if (options.namedSpeakers) lines = lines.map { dropLabel(it, namedLabel) }
        val joined = lines.joinToString("\n")
        // nothing changed: the text stays as it was, spaces and all
        if (joined == text) return text
        val tidy = lines.map { it.replace(emptyPair, "").replace(innerSpace, "$1$2").replace(spaces, " ").replace(spaceBeforePunctuation, "$1").trim().replace(afterOverrides, "$1") }
        var cleaned = tidy.filter { isSaid(it) }
        if (cleaned.isEmpty()) return null
        // the override tags of a line that goes (an ASS cue's position) are kept on the first line left
        val orphaned = tidy.filterNot { isSaid(it) }.joinToString("") { line -> overrides.findAll(line).joinToString("") { it.value } }
        if (orphaned.isNotEmpty()) cleaned = listOf(orphaned + cleaned[0]) + cleaned.drop(1)
        // a lone line left of a dialogue loses the dash that told the speakers apart
        if (cleaned.size == 1 && text.split('\n').count { isSaid(it) } > 1) cleaned = listOf(cleaned[0].replace(leadingDash, "$1"))
        return cleaned.joinToString("\n")
    }

    /** Whether a line has words (or digits, or punctuation that says something) beyond dashes and music notes. */
    private fun isSaid(line: String): Boolean = visible(line).any { !it.isWhitespace() && it !in "-–—♪♫" }

    private fun dropLabel(line: String, label: Regex): String {
        val match = label.find(line) ?: return line
        return match.groupValues[1] + line.substring(match.range.last + 1)
    }

    /**
     * Descriptions in parentheses: those in capitals, and those that start or end their line
     * (beyond dashes, notes and punctuation). A lowercase aside in the middle of a sentence stays.
     */
    private fun removeParentheses(text: String): String = round.replace(text) { match ->
        val inside = match.groupValues[1]
        // an override block inside is kept where it was
        val kept = inside.filter { it == MASK }
        val letters = inside.filter { it.isLetter() }
        val capitals = letters.isNotEmpty() && letters.all { it.isUpperCase() }
        val lineStart = text.lastIndexOf('\n', match.range.first - 1) + 1
        val lineEnd = text.indexOf('\n', match.range.last + 1).let { if (it < 0) text.length else it }
        val before = text.substring(lineStart, match.range.first)
        val after = text.substring(match.range.last + 1, lineEnd)
        if (capitals || aside(before) || aside(after)) kept else match.value
    }

    /** Whether nothing said is left of a line's part: only markup, dashes, notes, spaces, punctuation. */
    private fun aside(part: String): Boolean = visible(part).all { it.isWhitespace() || it in "-–—♪♫.,!?…:;$MASK" }
}
