package app.nagmedar.ui.player.subtitles.translate

import app.nagmedar.translate.TranslationDirection
import app.nagmedar.ui.player.subtitles.tools.SubtitleDocument
import app.nagmedar.ui.player.subtitles.tools.SubtitleFormat
import app.nagmedar.ui.player.subtitles.tools.TimedCue
import java.util.Locale

/*
 * The subtitle translation's text work (S-710): what of each cue goes to the model and how its
 * translation comes back. A cue keeps its times exactly. Its markup is taken off first (ASS
 * override tags, HTML tags) and an italic, bold or underlined cue is so again afterwards; the
 * lines of a dialogue ("- Hi." / "- Hello.") are translated one by one and stay apart, the lines
 * of one sentence go together and are broken again near the middle when the result is long.
 */

/** One piece of a cue the model translates: [text], with what stays around it ([lead] "- ", [tail] " ♪"). */
internal data class Segment(val lead: String, val text: String, val tail: String) {
    /** Whether there is anything to translate: a line of notes, numbers or dots is kept as it is. */
    val translatable: Boolean get() = text.any { it.isLetter() }
}

/** A cue taken apart: its segments, whether they are dialogue lines, the styles around all of it, its lines' count. */
internal data class CueParts(
    val segments: List<Segment>,
    val dialogue: Boolean,
    val italic: Boolean,
    val bold: Boolean,
    val underline: Boolean,
    val lines: Int,
)

/** The translation's work of a file: each cue's parts and the texts to translate, each once. */
internal class TranslationPlan(val cues: List<TimedCue>, val parts: List<CueParts>) {
    /** The distinct texts to translate, in the order they come. */
    val texts: List<String> = parts.flatMap { cue -> cue.segments.filter { it.translatable }.map { it.text } }.distinct()
}

internal object CueTranslation {
    /** A line longer than this is broken in two (the usual subtitle line). */
    const val MAX_LINE = 42

    private val assTags = Regex("""\{[^}]*\}""")
    private val htmlTags = Regex("""</?[A-Za-z][^>]*>""")
    private val spaces = Regex("""\s+""")
    private val dash = Regex("""^[-‐–—]\s*""")
    private val leadMarks = Regex("""^[♪♫#*]+\s*""")
    private val tailMarks = Regex("""\s*[♪♫#*]+$""")

    fun plan(document: SubtitleDocument): TranslationPlan {
        val styled = document.format.styled
        return TranslationPlan(document.cues, document.cues.map { parts(it.text, styled) })
    }

    /** A cue's [text] taken apart; [styled]: an ASS or SSA line, whose "\h" and "\n" are spaces. */
    fun parts(text: String, styled: Boolean): CueParts {
        var body = text
        if (styled) body = body.replace("\\h", " ").replace("\\n", " ")
        val trimmed = body.trim()
        val italic = wraps(trimmed, "i")
        val bold = wraps(trimmed, "b")
        val underline = wraps(trimmed, "u")
        val plain = trimmed.replace(assTags, "").replace(htmlTags, "")
        val lines = plain.split('\n').map { it.replace(spaces, " ").trim() }.filter { it.isNotEmpty() }
        val dialogue = lines.size > 1 && lines.count { dash.containsMatchIn(it) } >= lines.size - 1 && dash.containsMatchIn(lines.last())
        val segments = if (dialogue) lines.map(::segment) else listOfNotNull(lines.joinToString(" ").takeIf { it.isNotEmpty() }?.let(::segment))
        return CueParts(segments, dialogue, italic, bold, underline, lines.size)
    }

    /** Whether the whole of [text] is in the style [tag]: "<i>…</i>", or "{\i1}…" in ASS. */
    private fun wraps(text: String, tag: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        if (lower.startsWith("<$tag>") && lower.endsWith("</$tag>")) return lower.indexOf("</$tag>") == lower.length - tag.length - 3
        val head = Regex("""^\{[^}]*\\$tag(1)?[\\}]""").find(lower) ?: return false
        // turned off again before the end: only a part of the cue is in it
        return !lower.substring(head.range.last + 1).contains(Regex("""\\$tag(0)""")) || lower.trimEnd().endsWith("\\${tag}0}")
    }

    private fun segment(line: String): Segment {
        var rest = line
        val lead = StringBuilder()
        dash.find(rest)?.let {
            lead.append("- ")
            rest = rest.substring(it.range.last + 1)
        }
        val marks = leadMarks.find(rest)
        if (marks != null) {
            lead.append(marks.value)
            rest = rest.substring(marks.range.last + 1)
        }
        val tail = tailMarks.find(rest)?.value.orEmpty()
        if (tail.isNotEmpty()) rest = rest.dropLast(tail.length)
        return Segment(lead.toString(), rest.trim(), tail)
    }

    /** The cue's text again, with [translations] of its texts (a text without one stays as it was). */
    fun rebuild(parts: CueParts, translations: Map<String, String>): String {
        val texts = parts.segments.map { segment ->
            val translated = translations[segment.text]?.trim()?.takeIf { it.isNotEmpty() && segment.translatable } ?: segment.text
            segment.lead + translated + segment.tail
        }
        val lines = if (parts.dialogue) texts else texts.flatMap { breakLine(it, parts.lines) }
        var out = lines.joinToString("\n")
        if (parts.underline) out = "<u>$out</u>"
        if (parts.bold) out = "<b>$out</b>"
        if (parts.italic) out = "<i>$out</i>"
        return out
    }

    /** [text] on one line, or on two at the space nearest its middle when it is long; [was]: the lines it had. */
    fun breakLine(text: String, was: Int): List<String> {
        if (text.length <= MAX_LINE || was == 0) return listOf(text)
        val middle = text.length / 2
        var best = -1
        for (i in text.indices) {
            if (text[i] == ' ' && (best < 0 || kotlin.math.abs(i - middle) < kotlin.math.abs(best - middle))) best = i
        }
        if (best <= 0) return listOf(text)
        return listOf(text.substring(0, best).trimEnd(), text.substring(best + 1).trimStart())
    }

    /** The translated file: SRT, every cue at its times, the texts from [translations]. */
    fun result(plan: TranslationPlan, translations: Map<String, String>, lineBreak: String): SubtitleDocument {
        val cues = plan.cues.mapIndexed { index, cue -> TimedCue(cue.id, cue.startMs, cue.endMs, rebuild(plan.parts[index], translations)) }
        return SubtitleDocument(SubtitleFormat.Srt, cues, lineBreak = lineBreak, bom = false)
    }

    // ---------------------------------------------------------------- names and languages

    private val turkishTokens = setOf("tr", "tur", "turkish", "turkce")
    private val englishTokens = setOf("en", "eng", "english", "ingilizce")

    /**
     * "Film.en.srt" translated into Turkish → "Film.tr.srt": the original's name with the
     * target language before the extension, its own language left out when the name says it.
     */
    fun outputName(original: String, direction: TranslationDirection): String {
        var base = original.substringBeforeLast('.', original)
        val last = base.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val source = if (direction.source == "tr") turkishTokens else englishTokens
        if (last.isNotEmpty() && last in source) base = base.substringBeforeLast('.')
        val clean = base.replace(Regex("""[/\\:*?"<>|]"""), "_").ifBlank { "subtitles" }
        return "$clean.${direction.target}.srt"
    }

    /**
     * The direction a file most likely needs: by the language its name says, else by its letters
     * (ğ, ı, ş and the like are Turkish), else into the interface's language.
     */
    fun suggest(name: String, document: SubtitleDocument?, interfaceLanguage: String): TranslationDirection {
        val tokens = name.lowercase(Locale.ROOT).split('.', '_', '-', ' ', '[', ']', '(', ')').toSet()
        if (tokens.any { it in turkishTokens }) return TranslationDirection.TurkishToEnglish
        if (tokens.any { it in englishTokens }) return TranslationDirection.EnglishToTurkish
        if (document != null) {
            var letters = 0
            var turkish = 0
            for (cue in document.cues.take(SAMPLE_CUES)) for (c in cue.text) {
                if (c.isLetter()) letters++
                if (c in TURKISH_LETTERS) turkish++
            }
            if (letters > 0) return if (turkish * 100 >= letters) TranslationDirection.TurkishToEnglish else TranslationDirection.EnglishToTurkish
        }
        return if (interfaceLanguage == "tr") TranslationDirection.EnglishToTurkish else TranslationDirection.TurkishToEnglish
    }

    private const val SAMPLE_CUES = 200
    /** ğ Ğ ı İ ş Ş ç Ç ö Ö ü Ü. */
    private const val TURKISH_LETTERS = "\u011f\u011e\u0131\u0130\u015f\u015e\u00e7\u00c7\u00f6\u00d6\u00fc\u00dc"
}
