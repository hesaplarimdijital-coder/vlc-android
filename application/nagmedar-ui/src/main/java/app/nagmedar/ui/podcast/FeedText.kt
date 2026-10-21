package app.nagmedar.ui.podcast

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import java.util.regex.Pattern

/** S-1101: the addresses of feeds: what is typed, what is stored, and what two spellings of one feed share. */
object FeedUrl {
    /** Schemes that only say "this is a feed": the address under them is http(s). */
    private val FEED_SCHEMES = listOf("feed", "itpc", "pcast", "podcast", "rss")

    private val HTTP = Regex("^https?://[^/\\s?#]+(?:[/?#].*)?$", RegexOption.IGNORE_CASE)
    private val PARTS = Regex("^(?:[a-z][a-z0-9+.-]*:)?//([^/?#]*)([^?#]*)(\\?[^#]*)?(?:#.*)?$", RegexOption.IGNORE_CASE)

    /**
     * The address typed or pasted, made one the network can fetch: spaces trimmed, "feed://host/x"
     * and "itpc://host/x" read as https, "host/x" given a scheme, a feed address in a "feed:" wrapper
     * ("feed:https://host/x") unwrapped. Null when nothing like an address is left.
     */
    fun normalize(input: String): String? = candidates(input).firstOrNull()

    /**
     * The addresses to try for [input], in order: as typed when it has an http(s) scheme; https and
     * then http where the scheme was left out or only said "feed".
     */
    fun candidates(input: String): List<String> {
        var text = input.trim().trim('<', '>', '"', '\'').trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return emptyList()
        val lower = text.lowercase(Locale.ROOT)
        FEED_SCHEMES.firstOrNull { lower.startsWith("$it:") }?.let { text = text.substring(it.length + 1) }
        val inner = text.lowercase(Locale.ROOT)
        return when {
            inner.startsWith("http://") || inner.startsWith("https://") -> listOf(text).filter { HTTP.matches(it) }
            "://" in text -> emptyList()
            else -> {
                // a host and a path, no scheme yet: it needs a dot, or any word would pass
                val bare = text.removePrefix("//")
                val host = bare.substringBefore('/').substringBefore('?').substringBefore('#')
                if ('.' in host && !host.startsWith('.') && !host.endsWith('.')) listOf("https://$bare", "http://$bare").filter { HTTP.matches(it) } else emptyList()
            }
        }
    }

    /**
     * What two spellings of one feed's address share: the scheme is ignored (a feed that moved to https
     * is the same subscription), the host is lower case, the default port, the fragment and a trailing
     * slash are dropped. The query stays, a feed may be told apart by it.
     */
    fun key(url: String): String {
        val text = url.trim()
        val parts = PARTS.matchEntire(text) ?: return text.lowercase(Locale.ROOT)
        val authority = parts.groupValues[1].substringAfterLast('@').lowercase(Locale.ROOT).removeSuffix(":80").removeSuffix(":443")
        return authority + parts.groupValues[2].trimEnd('/') + parts.groupValues[3]
    }

    /** [href] resolved against the page it was found on, spaces encoded; null when it is no http(s) address. */
    fun resolve(base: String?, href: String): String? {
        val text = href.trim().replace(" ", "%20")
        if (text.isEmpty()) return null
        if (HTTP.matches(text)) return text
        if (base == null || "://" in text) return null
        return try {
            URI(base).resolve(text).toString().takeIf { HTTP.matches(it) }
        } catch (e: URISyntaxException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** S-1101: the HTML in a feed's descriptions, read as the plain text it carries. */
object HtmlText {
    private val BREAKS = Regex("(?i)<\\s*(?:br\\s*/?|/p|/div|/li|/h[1-6]|/tr)\\s*>")
    private val ITEMS = Regex("(?i)<\\s*li[^<>]*>")
    private val TAGS = Regex("</?[A-Za-z][^<>]*>")
    private val SPACES = Regex("[ \\t\\u00A0\\u200B]+")
    private val LINES = Regex("\\s*\\n\\s*(?:\\n\\s*)+")
    private val ENTITY = Pattern.compile("&(#[0-9]+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]{1,8});")

    private val BLOCKS = listOf("script", "style")

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "hellip" to "…", "mdash" to "—",
        "ndash" to "–", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "copy" to "©", "reg" to "®",
        "trade" to "™", "laquo" to "«", "raquo" to "»", "middot" to "·", "bull" to "•", "euro" to "€", "deg" to "°",
    )

    /** [html] as text: paragraphs and line breaks kept as lines, tags dropped, entities decoded, blank runs squeezed. */
    fun plain(html: String?, limit: Int = 4_000): String? {
        if (html.isNullOrBlank()) return null
        var text = withoutBlocks(html).replace(ITEMS, "\n• ").replace(BREAKS, "\n").replace(TAGS, "")
        text = decode(text).replace("\r", "").replace(SPACES, " ")
        text = text.lines().joinToString("\n") { it.trim() }.replace(LINES, "\n\n").trim()
        if (text.isEmpty()) return null
        return if (text.length > limit) text.substring(0, limit).trimEnd() + "…" else text
    }

    /**
     * [html] without its comments and its script and style blocks, each replaced by a space. One pass: a feed of
     * 200,000 characters of unclosed "<script" or "<!--" costs one search for a closing tag, not one per opener
     * (what a lazy regular expression would do, and take minutes over).
     */
    private fun withoutBlocks(html: String): String {
        if ('<' !in html) return html
        val out = StringBuilder(html.length)
        var at = 0
        var noComment = false
        var noScript = false
        var noStyle = false
        while (at < html.length) {
            val open = html.indexOf('<', at)
            if (open < 0) {
                out.append(html, at, html.length)
                break
            }
            out.append(html, at, open)
            var after = -1
            if (html.startsWith("<!--", open)) {
                if (!noComment) {
                    val close = html.indexOf("-->", open + 4)
                    if (close >= 0) after = close + 3 else noComment = true
                }
            } else {
                val name = blockName(html, open + 1)
                if (name != null && !(name == "script" && noScript) && !(name == "style" && noStyle)) {
                    after = closingEnd(html, open + 1, name)
                    if (after < 0) if (name == "script") noScript = true else noStyle = true
                }
            }
            if (after >= 0) {
                out.append(' ')
                at = after
            } else {
                out.append('<')
                at = open + 1
            }
        }
        return out.toString()
    }

    /** "script" or "style" when [from] (after a "<" and any spaces) starts that word and ends it. */
    private fun blockName(html: String, from: Int): String? {
        var i = from
        while (i < html.length && html[i].isWhitespace()) i++
        for (name in BLOCKS) {
            if (html.regionMatches(i, name, 0, name.length, ignoreCase = true)) {
                val next = i + name.length
                if (next >= html.length || !(html[next].isLetterOrDigit() || html[next] == '_')) return name
            }
        }
        return null
    }

    /** Where the "</name>" that closes the block opened before [from] ends; -1 when nothing closes it. */
    private fun closingEnd(html: String, from: Int, name: String): Int {
        var search = from
        while (true) {
            val lt = html.indexOf('<', search)
            if (lt < 0) return -1
            var i = lt + 1
            while (i < html.length && html[i].isWhitespace()) i++
            if (i < html.length && html[i] == '/') {
                i++
                while (i < html.length && html[i].isWhitespace()) i++
                if (html.regionMatches(i, name, 0, name.length, ignoreCase = true)) {
                    i += name.length
                    while (i < html.length && html[i].isWhitespace()) i++
                    if (i < html.length && html[i] == '>') return i + 1
                }
            }
            search = lt + 1
        }
    }

    /** One line: [plain] with its lines joined by spaces; for titles, which feeds sometimes wrap in markup. */
    fun line(text: String?): String? = plain(text, limit = 500)?.replace(Regex("\\s*\\n\\s*"), " ")?.trim()?.takeIf { it.isNotEmpty() }

    /** [text] with its character references (`&amp;`, `&#39;`, `&#x27;`) decoded. */
    internal fun decode(text: String): String {
        if ('&' !in text) return text
        val matcher = ENTITY.matcher(text)
        val out = StringBuffer()
        while (matcher.find()) {
            val body = matcher.group(1)!!
            val replacement = when {
                body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let(::codePoint)
                body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePoint)
                else -> NAMED[body.lowercase(Locale.ROOT)] ?: NAMED[body]
            }
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(replacement ?: matcher.group()))
        }
        matcher.appendTail(out)
        return out.toString()
    }

    private fun codePoint(value: Int): String? = if (value in 1..0x10FFFF && value !in 0xD800..0xDFFF) String(Character.toChars(value)) else null
}
