package app.nagmedar.ui.podcast

import androidx.compose.runtime.Immutable
import java.util.Locale

/** S-205: a stretch of a show note's text in one style; [link] is where a tap goes (an http(s) or mailto address). */
@Immutable
data class NoteRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

/** S-205: the parts a show note is made of once its HTML is read: nothing else of the HTML reaches the screen. */
sealed interface NoteBlock {
    @Immutable
    data class Paragraph(val runs: List<NoteRun>) : NoteBlock

    @Immutable
    data class Heading(val level: Int, val runs: List<NoteRun>) : NoteBlock

    /** A list item: [marker] is a bullet or its number ("3."), [depth] 0 for a list not inside another. */
    @Immutable
    data class Item(val marker: String, val depth: Int, val runs: List<NoteRun>) : NoteBlock

    @Immutable
    data class Quote(val runs: List<NoteRun>) : NoteBlock

    /** A picture the note points to; drawn only when the person lets pictures load. */
    @Immutable
    data class Picture(val url: String, val description: String) : NoteBlock
}

/**
 * S-205: the show notes of an episode, read from the HTML a feed wrote into the structure above. Sanitising is
 * reading: only paragraphs, line breaks, headings, lists, quotes, bold, italic, code, links and pictures are
 * kept; scripts, styles, frames, forms and every other tag go (their text stays, except for the blocks that are code or
 * markup for something else, which go whole); a link survives only when its address is http(s) or mailto, a picture
 * only with an http(s) address; no attribute but those addresses and a picture's description is read at all; and
 * an address in the text, written out, becomes a link. Never throws, whatever the HTML is.
 */
object ShowNotes {
    /** A note is cut after this many blocks. */
    const val MAX_BLOCKS = 400

    /** Tags whose whole content is dropped. */
    private val DROPPED = setOf(
        "script", "style", "iframe", "object", "embed", "svg", "template", "noscript", "head", "title", "form", "button", "textarea", "select",
        "math", "audio", "video", "canvas", "applet", "frameset", "frame",
    )

    /** Tags that end the paragraph being written and begin another. */
    private val BLOCK_TAGS = setOf("p", "div", "section", "article", "header", "footer", "aside", "nav", "main", "figure", "figcaption", "table", "tr", "dl", "dt", "dd", "hr", "address", "details", "summary")
    private val URL = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""[ \t\r\n ​]+""")

    /** The blocks of [html]; [base] is the address relative links and pictures are made absolute against (the feed's). */
    fun fromHtml(html: String, base: String? = null): List<NoteBlock> = Builder(base).apply { read(html) }.finish()

    /** The blocks of a note that is plain text: paragraphs at blank lines, addresses made links. */
    fun fromPlain(text: String): List<NoteBlock> {
        val blocks = ArrayList<NoteBlock>()
        for (paragraph in text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n"))) {
            val runs = linked(paragraph.trim().lines().joinToString("\n") { it.trim() }, NoteRun(""))
            if (runs.isNotEmpty()) blocks += NoteBlock.Paragraph(runs)
            if (blocks.size >= MAX_BLOCKS) break
        }
        return blocks
    }

    /** The note to draw for an episode: its HTML when the feed gave some, else its plain description. */
    fun of(html: String?, plain: String?, base: String? = null): List<NoteBlock> {
        val fromMarkup = html?.takeIf { it.isNotBlank() }?.let { fromHtml(it, base) }.orEmpty()
        return fromMarkup.ifEmpty { plain?.takeIf { it.isNotBlank() }?.let(::fromPlain).orEmpty() }
    }

    /** The addresses a note links to, once each, in the order they come. */
    fun links(blocks: List<NoteBlock>): List<String> = blocks.flatMap { block ->
        when (block) {
            is NoteBlock.Paragraph -> block.runs
            is NoteBlock.Heading -> block.runs
            is NoteBlock.Item -> block.runs
            is NoteBlock.Quote -> block.runs
            is NoteBlock.Picture -> emptyList()
        }
    }.mapNotNull { it.link }.distinct()

    /** [text] as runs in the style of [style], an address in it made a link of its own. */
    private fun linked(text: String, style: NoteRun): List<NoteRun> {
        if (text.isEmpty()) return emptyList()
        if (style.link != null || "://" !in text) return listOf(style.copy(text = text))
        val runs = ArrayList<NoteRun>()
        var at = 0
        for (match in URL.findAll(text)) {
            // a sentence's full stop or a closing bracket after the address is not part of it
            var end = match.range.last + 1
            while (end > match.range.first && text[end - 1] in TRAILING) end--
            if (end - match.range.first < "http://x".length) continue
            if (match.range.first > at) runs += style.copy(text = text.substring(at, match.range.first))
            val address = text.substring(match.range.first, end)
            runs += style.copy(text = address, link = address)
            at = end
        }
        if (at < text.length) runs += style.copy(text = text.substring(at))
        return runs
    }

    private const val TRAILING = ".,;:!?)]}'"

    private class ListLevel(val ordered: Boolean, var count: Int = 0)

    private enum class Kind { Paragraph, Heading, Item, Quote }

    private class Builder(val base: String?) {
        val blocks = ArrayList<NoteBlock>()
        var runs = ArrayList<NoteRun>()
        var kind = Kind.Paragraph
        var level = 0
        var marker = ""
        var depth = 0
        var bold = 0
        var italic = 0
        var code = 0
        var pre = false
        var quote = 0
        var link: String? = null
        val lists = ArrayList<ListLevel>()

        fun read(html: String) {
            var at = 0
            while (at < html.length && blocks.size < MAX_BLOCKS) {
                val open = html.indexOf('<', at)
                if (open < 0) {
                    text(html.substring(at))
                    break
                }
                if (open > at) text(html.substring(at, open))
                at = tag(html, open)
            }
            flush()
        }

        /** Handles the markup at [open] (a '<'); returns where reading goes on. */
        fun tag(html: String, open: Int): Int {
            if (html.startsWith("<!--", open)) {
                val close = html.indexOf("-->", open + 4)
                return if (close < 0) html.length else close + 3
            }
            val next = html.getOrNull(open + 1)
            if (next == '!' || next == '?') {
                val close = html.indexOf('>', open)
                return if (close < 0) html.length else close + 1
            }
            val closing = next == '/'
            var i = open + if (closing) 2 else 1
            val nameStart = i
            while (i < html.length && (html[i].isLetterOrDigit())) i++
            if (i == nameStart || !html[nameStart].isLetter()) {
                text("<")
                return open + 1
            }
            val name = html.substring(nameStart, i).lowercase(Locale.ROOT)
            val attrs = HashMap<String, String>(2)
            val end = readAttributes(html, i, attrs)
            if (end < 0) {
                // a tag that never ends: the "<" was text
                text("<")
                return open + 1
            }
            if (!closing && name in DROPPED) return afterBlock(html, name, end)
            if (closing) close(name) else start(name, attrs)
            return end
        }

        /** Past the element [name] that began before [from]: its closing tag, or the end of the HTML when nothing closes it. */
        private fun afterBlock(html: String, name: String, from: Int): Int {
            var search = from
            while (true) {
                val lt = html.indexOf("</", search)
                if (lt < 0) return html.length
                if (html.regionMatches(lt + 2, name, 0, name.length, ignoreCase = true)) {
                    val close = html.indexOf('>', lt)
                    return if (close < 0) html.length else close + 1
                }
                search = lt + 2
            }
        }

        /** Reads attributes from [from] up to the tag's '>' and returns the position after it; -1 when there is none. */
        private fun readAttributes(html: String, from: Int, into: MutableMap<String, String>): Int {
            var i = from
            while (i < html.length) {
                val c = html[i]
                when {
                    c == '>' -> return i + 1
                    c.isWhitespace() || c == '/' -> i++
                    else -> {
                        val nameStart = i
                        while (i < html.length && !html[i].isWhitespace() && html[i] != '=' && html[i] != '>' && html[i] != '/') i++
                        val name = html.substring(nameStart, i).lowercase(Locale.ROOT)
                        while (i < html.length && html[i].isWhitespace()) i++
                        if (i < html.length && html[i] == '=') {
                            i++
                            while (i < html.length && html[i].isWhitespace()) i++
                            val quote = html.getOrNull(i)
                            val value: String
                            if (quote == '"' || quote == '\'') {
                                val close = html.indexOf(quote, i + 1)
                                if (close < 0) return -1
                                value = html.substring(i + 1, close)
                                i = close + 1
                            } else {
                                val valueStart = i
                                while (i < html.length && !html[i].isWhitespace() && html[i] != '>') i++
                                value = html.substring(valueStart, i)
                            }
                            if (name.isNotEmpty() && name !in into) into[name] = HtmlText.decode(value)
                        }
                    }
                }
            }
            return -1
        }

        fun start(name: String, attrs: Map<String, String>) {
            when (name) {
                "br" -> add(NoteRun("\n"))
                in BLOCK_TAGS -> flush()
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flush()
                    kind = Kind.Heading
                    level = name[1] - '0'
                }
                "blockquote" -> {
                    flush()
                    quote++
                }
                "ul", "ol" -> {
                    flush()
                    if (lists.size < MAX_LIST_DEPTH) lists += ListLevel(ordered = name == "ol")
                }
                "li" -> {
                    flush()
                    val list = lists.lastOrNull()
                    kind = Kind.Item
                    depth = (lists.size - 1).coerceAtLeast(0)
                    marker = if (list != null && list.ordered) "${++list.count}." else "•"
                }
                "b", "strong" -> bold++
                "i", "em", "cite", "dfn" -> italic++
                "code", "kbd", "tt", "samp" -> code++
                "pre" -> {
                    flush()
                    pre = true
                    code++
                }
                "a" -> if (link == null) link = address(attrs["href"], mailto = true)
                "img" -> {
                    val src = address(attrs["src"], mailto = false)
                    if (src != null) {
                        flush()
                        blocks += NoteBlock.Picture(src, attrs["alt"]?.let { SPACES.replace(it, " ").trim() }.orEmpty())
                    }
                }
            }
        }

        fun close(name: String) {
            when (name) {
                in BLOCK_TAGS, "li", "h1", "h2", "h3", "h4", "h5", "h6" -> flush()
                "blockquote" -> {
                    flush()
                    if (quote > 0) quote--
                }
                "ul", "ol" -> {
                    flush()
                    if (lists.isNotEmpty()) lists.removeAt(lists.size - 1)
                }
                "b", "strong" -> bold = (bold - 1).coerceAtLeast(0)
                "i", "em", "cite", "dfn" -> italic = (italic - 1).coerceAtLeast(0)
                "code", "kbd", "tt", "samp" -> code = (code - 1).coerceAtLeast(0)
                "pre" -> {
                    flush()
                    pre = false
                    code = (code - 1).coerceAtLeast(0)
                }
                "a" -> link = null
            }
        }

        /** [href] made absolute, when it is an address a person can be sent to. */
        private fun address(href: String?, mailto: Boolean): String? {
            val value = href?.trim().orEmpty()
            if (value.isEmpty()) return null
            if (mailto && value.startsWith("mailto:", ignoreCase = true)) return value.takeIf { it.length > "mailto:".length && it.none { c -> c.isWhitespace() } }
            return FeedUrl.resolve(base, value)
        }

        fun text(raw: String) {
            if (raw.isEmpty()) return
            val decoded = HtmlText.decode(raw)
            val cleaned = if (pre) decoded.replace("\r", "") else SPACES.replace(decoded, " ")
            if (cleaned.isEmpty()) return
            val style = NoteRun("", bold = bold > 0, italic = italic > 0, code = code > 0, link = link)
            for (run in linked(cleaned, style)) add(run)
        }

        private fun add(run: NoteRun) {
            val last = runs.lastOrNull()
            if (last != null && last.bold == run.bold && last.italic == run.italic && last.code == run.code && last.link == run.link) {
                runs[runs.size - 1] = last.copy(text = last.text + run.text)
            } else {
                runs += run
            }
        }

        /** Ends the block being written: its runs trimmed, and dropped when nothing is left. */
        fun flush() {
            val trimmed = trimmed(runs)
            runs = ArrayList()
            val currentKind = if (kind == Kind.Paragraph && quote > 0) Kind.Quote else kind
            val currentLevel = level
            val currentMarker = marker
            val currentDepth = depth
            kind = Kind.Paragraph
            if (trimmed.isEmpty() || blocks.size >= MAX_BLOCKS) return
            blocks += when (currentKind) {
                Kind.Heading -> NoteBlock.Heading(currentLevel, trimmed)
                Kind.Item -> NoteBlock.Item(currentMarker, currentDepth, trimmed)
                Kind.Quote -> NoteBlock.Quote(trimmed)
                Kind.Paragraph -> NoteBlock.Paragraph(trimmed)
            }
        }

        fun finish(): List<NoteBlock> {
            flush()
            return blocks
        }

        /** [list] without the spaces and line breaks at its two ends (a block's own), and without runs that came to nothing. */
        private fun trimmed(list: List<NoteRun>): List<NoteRun> {
            if (list.isEmpty()) return list
            val out = ArrayList(list)
            out[0] = out[0].copy(text = if (pre) out[0].text.trimStart('\n') else out[0].text.trimStart(' ', '\n'))
            val last = out.size - 1
            out[last] = out[last].copy(text = if (pre) out[last].text.trimEnd('\n') else out[last].text.trimEnd(' ', '\n'))
            return out.filter { it.text.isNotEmpty() }
        }
    }

    private const val MAX_LIST_DEPTH = 6
}
