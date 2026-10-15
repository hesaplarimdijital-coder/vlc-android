package app.nagmedar.ui.podcast

import android.util.Xml
import androidx.compose.runtime.Immutable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipException

/** One subscription of an OPML file. */
@Immutable
data class OpmlFeed(
    /** The feed's address as the file writes it, made fetchable ([FeedUrl.normalize]). */
    val feedUrl: String,
    /** The `title` or `text` the exporting app gave it; null when it gave none. */
    val title: String?,
    /** The show's web page (`htmlUrl`). */
    val htmlUrl: String? = null,
)

/** An OPML file read: its own title, and its feeds in file order, a feed once. */
@Immutable
data class OpmlDocument(val title: String?, val feeds: List<OpmlFeed>, val skipped: Int = 0)

/** Why a file is not an OPML list of podcasts. */
enum class OpmlProblem {
    /** Not XML, or XML whose root is not `opml`. */
    NotOpml,

    /** An OPML file with no feed in it. */
    NoFeeds,

    /** More than [Opml.MAX_BYTES]: no list of subscriptions is that long. */
    TooLarge,
}

class OpmlException(val problem: OpmlProblem, cause: Throwable? = null) : Exception("opml: $problem", cause)

/**
 * S-1102: OPML 1.0 and 2.0, the lists podcast apps trade subscriptions in. Reading takes every
 * `outline` that names a feed (`xmlUrl`, in any case) at any depth, so categories (AntennaPod's and
 * Pocket Casts' folders, Podcast Addict's and Overcast's groups) are walked through and not kept;
 * `type` may be `rss`, `RSS`, `atom`, anything, or absent. Outlines with no feed address (the folders
 * themselves, Overcast's episodes, links) are left out, a feed listed twice stays once. Writing is
 * OPML 2.0 with `type="rss"` outlines, which every app above reads.
 */
object Opml {
    /** The most outlines read: a list longer than this is not a person's subscriptions. */
    private const val MAX_OUTLINES = 20_000

    /** The most of a file read (after gunzip): twenty thousand outlines come to about two megabytes. */
    const val MAX_BYTES = 5L * 1024 * 1024

    /** The feeds of the OPML file in [input]. Throws [OpmlException] when it is not one, or lists no feed. */
    fun parse(input: InputStream, maxBytes: Long = MAX_BYTES): OpmlDocument {
        val parser = Xml.newPullParser()
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        val feeds = LinkedHashMap<String, OpmlFeed>()
        var title: String? = null
        var skipped = 0
        var outlines = 0
        var rootSeen = false
        var inHead = false
        var inTitle = false
        val limited = try {
            LimitedInputStream(XmlInput.prepare(input), maxBytes)
        } catch (e: FeedFailure.Invalid) {
            throw OpmlException(OpmlProblem.NotOpml, e)
        } catch (e: ZipException) {
            throw OpmlException(OpmlProblem.NotOpml, e)
        }
        try {
            parser.setInput(limited, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.substringAfter(':').lowercase(Locale.ROOT)
                        if (!rootSeen) {
                            if (name != "opml") throw OpmlException(OpmlProblem.NotOpml)
                            rootSeen = true
                        }
                        when (name) {
                            "head" -> inHead = true
                            "title" -> inTitle = inHead
                            "outline" -> if (++outlines <= MAX_OUTLINES) {
                                val feed = outline(parser)
                                if (feed == null) {
                                    if (hasFeedType(parser)) skipped++
                                } else if (feeds.putIfAbsent(FeedUrl.key(feed.feedUrl), feed) != null) {
                                    skipped++
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inTitle && title == null) title = parser.text?.trim()?.takeIf { it.isNotEmpty() }
                    XmlPullParser.END_TAG -> when (parser.name.substringAfter(':').lowercase(Locale.ROOT)) {
                        "head" -> inHead = false
                        "title" -> inTitle = false
                    }
                }
                event = parser.next()
            }
        } catch (e: XmlPullParserException) {
            // a list cut short keeps what was read before the cut
            if (!rootSeen) throw OpmlException(OpmlProblem.NotOpml, e)
        } catch (e: FeedFailure.Invalid) {
            throw OpmlException(OpmlProblem.NotOpml, e)
        } catch (e: ZipException) {
            if (!rootSeen) throw OpmlException(OpmlProblem.NotOpml, e)
        }
        if (limited.truncated) throw OpmlException(OpmlProblem.TooLarge)
        if (!rootSeen) throw OpmlException(OpmlProblem.NotOpml)
        if (feeds.isEmpty()) throw OpmlException(OpmlProblem.NoFeeds)
        return OpmlDocument(title, feeds.values.toList(), skipped)
    }

    /** The `type` says it is a feed but no usable address came with it: the file's own mistake, counted. */
    private fun hasFeedType(parser: XmlPullParser): Boolean = attribute(parser, "type")?.lowercase(Locale.ROOT).let { it == "rss" || it == "atom" }

    private fun outline(parser: XmlPullParser): OpmlFeed? {
        val type = attribute(parser, "type")?.lowercase(Locale.ROOT)
        val address = attribute(parser, "xmlurl") ?: if (type == "rss" || type == "atom") attribute(parser, "url") else null
        // an address written with its entities escaped twice ("a?b=1&amp;c=2") is read once more
        val url = address?.replace("&amp;", "&")?.let(FeedUrl::normalize) ?: return null
        return OpmlFeed(
            feedUrl = url,
            title = HtmlText.line(attribute(parser, "title") ?: attribute(parser, "text")),
            htmlUrl = attribute(parser, "htmlurl")?.let { FeedUrl.resolve(null, it) },
        )
    }

    /** The attribute [name] (lower case) of the current element, whatever case or prefix the file wrote it in. */
    private fun attribute(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).substringAfter(':').lowercase(Locale.ROOT) == name) return parser.getAttributeValue(i)?.trim()?.takeIf { it.isNotEmpty() }
        }
        return null
    }

    /**
     * An OPML 2.0 file for [feeds], named [title] in its head and dated [createdAtMs]: one
     * `outline type="rss"` each with `text`, `title`, `xmlUrl` and, where known, `htmlUrl`.
     */
    fun write(feeds: List<OpmlFeed>, title: String, createdAtMs: Long): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<opml version=\"2.0\">\n")
        append("  <head>\n")
        append("    <title>").append(escape(title)).append("</title>\n")
        append("    <dateCreated>").append(DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ROOT).format(Instant.ofEpochMilli(createdAtMs).atOffset(ZoneOffset.UTC))).append("</dateCreated>\n")
        append("  </head>\n")
        append("  <body>\n")
        feeds.forEach { feed ->
            val name = escape(feed.title ?: feed.feedUrl)
            append("    <outline type=\"rss\" text=\"").append(name).append("\" title=\"").append(name).append("\" xmlUrl=\"").append(escape(feed.feedUrl)).append('"')
            if (feed.htmlUrl != null) append(" htmlUrl=\"").append(escape(feed.htmlUrl)).append('"')
            append("/>\n")
        }
        append("  </body>\n")
        append("</opml>\n")
    }

    /** [text] as XML attribute or element text: markup characters escaped, characters XML 1.0 cannot hold dropped. */
    internal fun escape(text: String): String = buildString(text.length + 8) {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            when {
                cp == '&'.code -> append("&amp;")
                cp == '<'.code -> append("&lt;")
                cp == '>'.code -> append("&gt;")
                cp == '"'.code -> append("&quot;")
                cp == '\''.code -> append("&apos;")
                cp == 0x9 || cp == 0xA || cp == 0xD || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF -> appendCodePoint(cp)
            }
        }
    }
}
