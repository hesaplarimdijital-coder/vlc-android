package app.nagmedar.ui.guide

import androidx.compose.runtime.Immutable
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.search.foldForSearch

/*
 * The user guide's text (S-1504): one small Markdown file per language in assets/guide, read
 * offline. Only what the guide needs is understood: "## Title {id}" starts a section, "### " a
 * sub-heading, "- " a bullet, "1. " a step, "> " a tip, **bold**, and {key:Id} for a shortcut's
 * keys as they are now (ShortcutRegistry), so a remapped key is never out of date in the guide.
 */

/** A piece of a line: plain text, bold text or the keys of a shortcut. */
@Immutable
sealed interface GuideSpan {
    data class Text(val text: String) : GuideSpan
    data class Bold(val text: String) : GuideSpan

    /** The keys of [id] as the table has them now. */
    data class Keys(val id: ShortcutId) : GuideSpan
}

@Immutable
sealed interface GuideBlock {
    /** A paragraph. */
    data class Paragraph(val spans: List<GuideSpan>) : GuideBlock

    /** A small heading inside a section. */
    data class Heading(val text: String) : GuideBlock

    /** Unordered points. */
    data class Bullets(val items: List<List<GuideSpan>>) : GuideBlock

    /** Numbered steps. */
    data class Steps(val items: List<List<GuideSpan>>) : GuideBlock

    /** A tip, set apart. */
    data class Tip(val spans: List<GuideSpan>) : GuideBlock
}

/** A section of the guide: [id] is its short name in the file, [summary] its first paragraph as plain text. */
@Immutable
data class GuideSection(val id: String, val title: String, val summary: String, val blocks: List<GuideBlock>) {
    /** What a search looks through: the title and every word of the section, folded the Turkish way. */
    internal val searchText: String by lazy(LazyThreadSafetyMode.NONE) {
        foldForSearch((listOf(title) + blocks.flatMap { it.plainLines() }).joinToString(" "))
    }

    /** Whether every word of [query] is in this section; an empty query matches all. */
    fun matches(query: String): Boolean {
        val words = foldForSearch(query).split(' ', '\t').filter { it.isNotBlank() }
        return words.all { it in searchText }
    }
}

private fun GuideBlock.plainLines(): List<String> = when (this) {
    is GuideBlock.Paragraph -> listOf(spans.plain())
    is GuideBlock.Heading -> listOf(text)
    is GuideBlock.Bullets -> items.map { it.plain() }
    is GuideBlock.Steps -> items.map { it.plain() }
    is GuideBlock.Tip -> listOf(spans.plain())
}

/** The words of [this] without styling; the keys are left out: they change with the user's table. */
internal fun List<GuideSpan>.plain(): String = joinToString("") {
    when (it) {
        is GuideSpan.Text -> it.text
        is GuideSpan.Bold -> it.text
        is GuideSpan.Keys -> ""
    }
}

/** Reads and understands the guide's file. */
object GuideParser {
    private val sectionId = Regex("""\s*\{([a-z0-9-]+)\}\s*$""")
    private val numbered = Regex("""^\d+\.\s+(.*)$""")
    private val inline = Regex("""\*\*(.+?)\*\*|\{key:([A-Za-z]+)\}""")
    private val ids: Map<String, ShortcutId> = ShortcutId.entries.associateBy { it.name }

    /** The sections of [text]; lines before the first "## " are not part of any section. */
    fun parse(text: String): List<GuideSection> {
        val sections = mutableListOf<GuideSection>()
        var title: String? = null
        var id = ""
        val lines = mutableListOf<String>()
        fun close() {
            val heading = title ?: return
            val blocks = blocks(lines)
            val summary = (blocks.firstOrNull { it is GuideBlock.Paragraph } as? GuideBlock.Paragraph)?.spans?.plain().orEmpty()
            sections += GuideSection(id.ifEmpty { "section-${sections.size + 1}" }, heading, summary, blocks)
            lines.clear()
        }
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            if (line.startsWith("## ")) {
                close()
                val heading = line.removePrefix("## ").trim()
                id = sectionId.find(heading)?.groupValues?.get(1).orEmpty()
                title = heading.replace(sectionId, "")
            } else if (title != null) {
                lines += line
            }
        }
        close()
        return sections
    }

    private fun blocks(lines: List<String>): List<GuideBlock> {
        val out = mutableListOf<GuideBlock>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index].trim()
            when {
                line.isEmpty() -> index++
                line.startsWith("### ") -> {
                    out += GuideBlock.Heading(line.removePrefix("### ").trim())
                    index++
                }
                line.startsWith("- ") -> {
                    val items = mutableListOf<List<GuideSpan>>()
                    while (index < lines.size && lines[index].trim().startsWith("- ")) {
                        items += spans(lines[index].trim().removePrefix("- "))
                        index++
                    }
                    out += GuideBlock.Bullets(items)
                }
                numbered.matches(line) -> {
                    val items = mutableListOf<List<GuideSpan>>()
                    while (index < lines.size) {
                        val step = numbered.matchEntire(lines[index].trim()) ?: break
                        items += spans(step.groupValues[1])
                        index++
                    }
                    out += GuideBlock.Steps(items)
                }
                line.startsWith("> ") -> {
                    val words = mutableListOf<String>()
                    while (index < lines.size && lines[index].trim().startsWith("> ")) {
                        words += lines[index].trim().removePrefix("> ")
                        index++
                    }
                    out += GuideBlock.Tip(spans(words.joinToString(" ")))
                }
                else -> {
                    val words = mutableListOf<String>()
                    while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines[index].trim())) {
                        words += lines[index].trim()
                        index++
                    }
                    out += GuideBlock.Paragraph(spans(words.joinToString(" ")))
                }
            }
        }
        return out
    }

    private fun startsBlock(line: String) = line.startsWith("### ") || line.startsWith("- ") || line.startsWith("> ") || numbered.matches(line)

    /** The styled pieces of one line; a {key:…} naming no shortcut stays as it was written. */
    internal fun spans(line: String): List<GuideSpan> {
        val out = mutableListOf<GuideSpan>()
        var from = 0
        for (match in inline.findAll(line)) {
            if (match.range.first > from) out += GuideSpan.Text(line.substring(from, match.range.first))
            val bold = match.groups[1]?.value
            val shortcut = match.groups[2]?.value?.let(ids::get)
            out += when {
                bold != null -> GuideSpan.Bold(bold)
                shortcut != null -> GuideSpan.Keys(shortcut)
                else -> GuideSpan.Text(match.value)
            }
            from = match.range.last + 1
        }
        if (from < line.length) out += GuideSpan.Text(line.substring(from))
        return out
    }
}
