package app.nagmedar.ui.podcast

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.net.URLDecoder
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException

/** S-1101: what is in front of a feed's XML that parsers choke on: gzip, a byte order mark, blank lines. */
internal object XmlInput {
    private val GZIP = intArrayOf(0x1f, 0x8b)

    /** [raw] unzipped when it is gzip (some servers send the file as it is stored), without a UTF-8 mark or leading blanks. */
    fun prepare(raw: InputStream): PushbackInputStream {
        val buffered = PushbackInputStream(BufferedInputStream(raw), HEAD)
        val head = ByteArray(2)
        val n = readFully(buffered, head)
        if (n > 0) buffered.unread(head, 0, n)
        val gzipped = n == 2 && (head[0].toInt() and 0xff) == GZIP[0] && (head[1].toInt() and 0xff) == GZIP[1]
        val plain = if (gzipped) PushbackInputStream(BufferedInputStream(unzip(buffered)), HEAD) else buffered
        return skipPreamble(plain)
    }

    private fun unzip(input: InputStream): InputStream = try {
        GZIPInputStream(input)
    } catch (e: IOException) {
        // a header that is none (ZipException), or ends before it does (EOFException)
        throw FeedFailure.Invalid(InvalidReason.NotXml, e)
    }

    private fun skipPreamble(input: PushbackInputStream): PushbackInputStream {
        val mark = ByteArray(3)
        val n = readFully(input, mark)
        val hasMark = n == 3 && mark[0] == 0xEF.toByte() && mark[1] == 0xBB.toByte() && mark[2] == 0xBF.toByte()
        if (!hasMark && n > 0) input.unread(mark, 0, n)
        // blank lines before the declaration are an error to XML: drop them
        while (true) {
            val b = input.read()
            if (b < 0) break
            if (b != ' '.code && b != '\t'.code && b != '\r'.code && b != '\n'.code) {
                input.unread(b)
                break
            }
        }
        return input
    }

    private fun readFully(input: InputStream, into: ByteArray): Int {
        var total = 0
        while (total < into.size) {
            val n = input.read(into, total, into.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private const val HEAD = 4
}

/** The first [limit] bytes of a stream, then its end: a huge feed is read as far as it goes and no further. */
internal class LimitedInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
    /** Whether the stream ended at the limit instead of at its own end. */
    var truncated = false
        private set

    override fun read(): Int {
        if (remaining <= 0) return end()
        val b = super.read()
        if (b >= 0) remaining--
        return b
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0) return end()
        val n = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (n > 0) remaining -= n
        return n
    }

    private fun end(): Int {
        truncated = true
        return -1
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(minOf(n, remaining))
        remaining -= skipped
        return skipped
    }

    override fun markSupported(): Boolean = false
}

/**
 * S-1101: podcast feeds read with a pull parser: RSS 2.0 with the iTunes namespace and Podcasting
 * 2.0's (`podcast:guid`, `podcast:chapters`, `podcast:transcript`, `podcast:funding`), and Atom with
 * enclosure links. Prefixes are matched by the namespaces the file declares (the usual names where it
 * declares none), names in any case. Made to read what exists, not what the specifications allow:
 * HTML in descriptions, dates in any form, items without an enclosure (left out), duplicate guids
 * (the first one stays), relative enclosure addresses, a feed cut short (what came before the cut is
 * kept), a huge feed (read as far as [MAX_BYTES], the newest [MAX_EPISODES] kept).
 */
object FeedParser {
    /** The most of a feed that is read: 40 MB holds tens of thousands of episodes. */
    const val MAX_BYTES = 40L * 1024 * 1024

    /** The most episodes kept, the newest. */
    const val MAX_EPISODES = 5_000

    private const val NS_ITUNES = "itunes"
    private const val NS_PODCAST = "podcast"
    private const val NS_CONTENT = "content"
    private const val NS_MEDIA = "media"
    private const val NS_DC = "dc"

    private val MEDIA_EXTENSIONS = setOf(
        "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "mp4", "m4v", "mov", "webm", "mkv", "mpga", "mp2", "wma", "m3u8",
    )
    private val GENERIC_TYPES = setOf("application/octet-stream", "binary/octet-stream", "application/ogg", "application/x-mpegurl", "application/vnd.apple.mpegurl", "")
    private val BLOCKS = setOf("p", "div", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6")
    private val TRANSCRIPT_PREFERENCE = listOf("text/vtt", "application/x-subrip", "application/srt", "application/json", "text/html", "text/plain")

    /**
     * The feed in [input] (fetched from [baseUrl], which relative addresses in it are read against).
     * Throws [FeedFailure.Invalid] when it is not a podcast feed; other [IOException]s are the stream's.
     */
    fun parse(input: InputStream, baseUrl: String? = null, maxBytes: Long = MAX_BYTES): ParsedFeed {
        val prepared = XmlInput.prepare(input)
        val first = prepared.read()
        if (first < 0) throw FeedFailure.Invalid(InvalidReason.Empty)
        prepared.unread(first)
        val limited = LimitedInputStream(prepared, maxBytes)
        val parser = newParser()
        try {
            parser.setInput(limited, null)
        } catch (e: XmlPullParserException) {
            throw FeedFailure.Invalid(InvalidReason.NotXml, e)
        }
        return try {
            Reader(parser, baseUrl).read()
        } catch (e: ZipException) {
            // gzip that breaks half way
            throw FeedFailure.Invalid(InvalidReason.NotXml, e)
        }
    }

    private fun newParser(): XmlPullParser = Xml.newPullParser().also { parser ->
        // prefixes are resolved here from what the file declares, so the parser reports qualified names and xmlns attributes
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false) }
        // no DOCTYPE processing: the declarations in it (entities, external ones too) are skipped, never read or expanded,
        // so a billion-laughs chain or a file: entity does nothing; a reference to an entity is kept as its own text (&nbsp; stays "&nbsp;")
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
    }

    private class EnclosureCandidate(val url: String, val type: String?, val length: Long)

    private class ItemBuilder {
        var title: String? = null
        var itunesTitle: String? = null
        var guid: String? = null
        var published: String? = null
        var updated: String? = null
        var duration: String? = null
        var description: String? = null
        var contentEncoded: String? = null
        var summary: String? = null

        /** The markup the three of them were written with, for the show notes; null when one was plain text. */
        var descriptionHtml: String? = null
        var contentHtml: String? = null
        var summaryHtml: String? = null
        var imageUrl: String? = null
        var season: Int? = null
        var episode: Int? = null
        var chaptersUrl: String? = null
        val enclosures = ArrayList<EnclosureCandidate>(1)
        val mediaContent = ArrayList<EnclosureCandidate>(1)
        val transcripts = ArrayList<Pair<String, String?>>(1)
    }

    private class Reader(val parser: XmlPullParser, val baseUrl: String?) {
        private val prefixes = HashMap<String, String>()
        private val episodes = ArrayList<ParsedEpisode>()
        private val seen = HashSet<String>()
        private var skipped = 0

        /** Episodes dropped on the way for being among the oldest of a feed with more than [MAX_EPISODES]. */
        private var trimmed = 0

        private var title: String? = null
        private var itunesAuthor: String? = null
        private var ownerName: String? = null
        private var otherAuthor: String? = null
        private var description: String? = null
        private var summary: String? = null
        private var imageUrl: String? = null
        private var rssImage: String? = null
        private var link: String? = null
        private var guid: String? = null
        private var fundingUrl: String? = null
        private var fundingMessage: String? = null

        fun read(): ParsedFeed {
            val root = findRoot()
            try {
                when (root) {
                    "rss" -> readRss()
                    "feed" -> readAtomFeed()
                    else -> throw FeedFailure.Invalid(InvalidReason.NotFeed)
                }
            } catch (e: XmlPullParserException) {
                // a feed cut short or broken half way: what was read before is the feed
                if (episodes.isEmpty()) throw FeedFailure.Invalid(InvalidReason.NotXml, e)
            }
            if (episodes.isEmpty()) throw FeedFailure.Invalid(InvalidReason.NoEpisodes)
            val sorted = episodes.withIndex()
                .sortedWith(compareByDescending<IndexedValue<ParsedEpisode>> { it.value.publishedAt }.thenBy { it.index })
                .map { it.value }
            return ParsedFeed(
                title = title.orEmpty(),
                author = itunesAuthor ?: ownerName ?: otherAuthor,
                description = description ?: summary,
                imageUrl = imageUrl ?: rssImage,
                link = link,
                podcastGuid = guid,
                fundingUrl = fundingUrl,
                fundingMessage = fundingMessage,
                episodes = sorted.take(MAX_EPISODES),
                skipped = skipped + trimmed + (sorted.size - minOf(sorted.size, MAX_EPISODES)),
            )
        }

        /** The root element's name; throws when the text is no XML at all. */
        private fun findRoot(): String {
            var wordsBeforeRoot = false
            try {
                while (true) {
                    when (parser.next()) {
                        XmlPullParser.START_TAG -> {
                            declare()
                            return local(parser.name)
                        }
                        XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (parser.text?.isNotBlank() == true) wordsBeforeRoot = true
                        XmlPullParser.END_DOCUMENT -> throw FeedFailure.Invalid(if (wordsBeforeRoot) InvalidReason.NotXml else InvalidReason.Empty)
                    }
                }
            } catch (e: XmlPullParserException) {
                throw FeedFailure.Invalid(InvalidReason.NotXml, e)
            }
        }

        private fun readRss() {
            // children of rss: the channel, or (old feeds) the items themselves
            forEachChild(parser.depth) { name ->
                when (name) {
                    "channel" -> readChannel()
                    "item" -> readItem(atom = false)
                    else -> skip()
                }
            }
        }

        private fun readChannel() {
            forEachChild(parser.depth) { name ->
                when (name) {
                    "title" -> title = keep(title) { HtmlText.line(readText()) }
                    "link" -> link = keep(link) { readText().trim().takeIf { it.startsWith("http", ignoreCase = true) } }
                    "description" -> description = keep(description) { HtmlText.plain(readText()) }
                    "itunes:summary" -> summary = keep(summary) { HtmlText.plain(readText()) }
                    "itunes:author" -> itunesAuthor = keep(itunesAuthor) { HtmlText.line(readText()) }
                    "dc:creator", "author", "managingeditor" -> otherAuthor = keep(otherAuthor) { HtmlText.line(readText()) }
                    "itunes:image" -> {
                        imageUrl = imageUrl ?: attr("href")?.let { FeedUrl.resolve(baseUrl, it) }
                        skip()
                    }
                    "image" -> readRssImage()
                    "itunes:owner" -> readOwner()
                    "podcast:guid" -> guid = keep(guid) { readText().trim().takeIf { it.isNotEmpty() } }
                    "podcast:funding" -> {
                        val url = attr("url")?.let { FeedUrl.resolve(baseUrl, it) }
                        val message = HtmlText.line(readText())
                        if (fundingUrl == null && url != null) {
                            fundingUrl = url
                            fundingMessage = message
                        }
                    }
                    "item" -> readItem(atom = false)
                    else -> skip()
                }
            }
        }

        private fun readRssImage() {
            forEachChild(parser.depth) { name ->
                if (name == "url") rssImage = keep(rssImage) { FeedUrl.resolve(baseUrl, readText()) } else skip()
            }
        }

        private fun readOwner() {
            forEachChild(parser.depth) { name ->
                if (name == "itunes:name") ownerName = keep(ownerName) { HtmlText.line(readText()) } else skip()
            }
        }

        private fun readAtomFeed() {
            forEachChild(parser.depth) { name ->
                when (name) {
                    "title" -> title = keep(title) { HtmlText.line(readText()) }
                    "subtitle" -> description = keep(description) { HtmlText.plain(readText()) }
                    "itunes:summary" -> summary = keep(summary) { HtmlText.plain(readText()) }
                    "logo", "icon" -> rssImage = keep(rssImage) { FeedUrl.resolve(baseUrl, readText()) }
                    "itunes:image" -> {
                        imageUrl = imageUrl ?: attr("href")?.let { FeedUrl.resolve(baseUrl, it) }
                        skip()
                    }
                    "itunes:author" -> itunesAuthor = keep(itunesAuthor) { HtmlText.line(readText()) }
                    "author" -> readAtomAuthor()
                    "link" -> {
                        val rel = attr("rel")
                        if (link == null && (rel == null || rel.equals("alternate", ignoreCase = true))) link = attr("href")?.let { FeedUrl.resolve(baseUrl, it) }
                        skip()
                    }
                    "podcast:guid" -> guid = keep(guid) { readText().trim().takeIf { it.isNotEmpty() } }
                    "entry" -> readItem(atom = true)
                    else -> skip()
                }
            }
        }

        private fun readAtomAuthor() {
            forEachChild(parser.depth) { name ->
                if (name == "name") otherAuthor = keep(otherAuthor) { HtmlText.line(readText()) } else skip()
            }
        }

        private fun readItem(atom: Boolean) {
            val item = ItemBuilder()
            forEachChild(parser.depth) { name ->
                when (name) {
                    "title" -> item.title = keep(item.title) { HtmlText.line(readText()) }
                    "itunes:title" -> item.itunesTitle = keep(item.itunesTitle) { HtmlText.line(readText()) }
                    "guid", "id" -> item.guid = keep(item.guid) { readText().trim().takeIf { it.isNotEmpty() } }
                    "pubdate", "dc:date", "published" -> item.published = keep(item.published) { readText() }
                    "updated" -> item.updated = keep(item.updated) { readText() }
                    "itunes:duration" -> item.duration = readText()
                    "description", "summary" -> {
                        val raw = readText()
                        item.description = item.description ?: HtmlText.plain(raw)
                        item.descriptionHtml = item.descriptionHtml ?: markup(raw)
                    }
                    "content:encoded", "content" -> {
                        val raw = readText()
                        item.contentEncoded = item.contentEncoded ?: HtmlText.plain(raw)
                        item.contentHtml = item.contentHtml ?: markup(raw)
                    }
                    "itunes:summary" -> {
                        val raw = readText()
                        item.summary = item.summary ?: HtmlText.plain(raw)
                        item.summaryHtml = item.summaryHtml ?: markup(raw)
                    }
                    "itunes:image" -> {
                        item.imageUrl = keep(item.imageUrl) { attr("href")?.let { FeedUrl.resolve(baseUrl, it) } }
                        skip()
                    }
                    "itunes:season" -> item.season = readText().trim().toIntOrNull()
                    "itunes:episode" -> item.episode = readText().trim().toIntOrNull()
                    "enclosure" -> {
                        attr("url")?.let { item.enclosures += EnclosureCandidate(it, attr("type"), attr("length")?.trim()?.toLongOrNull() ?: 0) }
                        skip()
                    }
                    "link" -> {
                        // Atom's enclosure is a link
                        if (atom && attr("rel").equals("enclosure", ignoreCase = true)) {
                            attr("href")?.let { item.enclosures += EnclosureCandidate(it, attr("type"), attr("length")?.trim()?.toLongOrNull() ?: 0) }
                        }
                        skip()
                    }
                    "media:content" -> {
                        attr("url")?.let {
                            val type = attr("type") ?: attr("medium")?.takeIf { medium -> medium == "audio" || medium == "video" }?.let { medium -> "$medium/*" }
                            item.mediaContent += EnclosureCandidate(it, type, attr("filesize")?.trim()?.toLongOrNull() ?: 0)
                        }
                        skip()
                    }
                    "podcast:chapters" -> {
                        item.chaptersUrl = keep(item.chaptersUrl) { attr("url")?.let { FeedUrl.resolve(baseUrl, it) } }
                        skip()
                    }
                    "podcast:transcript" -> {
                        attr("url")?.let { FeedUrl.resolve(baseUrl, it) }?.let { item.transcripts += it to attr("type")?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) }
                        skip()
                    }
                    else -> skip()
                }
            }
            add(item)
        }

        private fun add(item: ItemBuilder) {
            val enclosure = (item.enclosures + item.mediaContent).firstOrNull { isMedia(it) }
            val url = enclosure?.let { FeedUrl.resolve(baseUrl, it.url) }
            if (enclosure == null || url == null) {
                skipped++
                return
            }
            val guid = item.guid ?: url
            if (!seen.add(guid)) {
                skipped++
                return
            }
            val transcript = TRANSCRIPT_PREFERENCE.firstNotNullOfOrNull { type -> item.transcripts.firstOrNull { it.second == type } } ?: item.transcripts.firstOrNull()
            episodes += ParsedEpisode(
                guid = guid,
                title = item.title ?: item.itunesTitle ?: fileTitle(url),
                audioUrl = url,
                mimeType = enclosure.type?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() && it != "audio/*" && it != "video/*" },
                sizeBytes = enclosure.length.coerceAtLeast(0),
                durationMs = FeedDates.duration(item.duration),
                publishedAt = FeedDates.parse(item.published) ?: FeedDates.parse(item.updated) ?: 0,
                description = item.description ?: item.contentEncoded ?: item.summary,
                imageUrl = item.imageUrl,
                season = item.season?.takeIf { it > 0 },
                episodeNumber = item.episode?.takeIf { it > 0 },
                chaptersUrl = item.chaptersUrl,
                transcriptUrl = transcript?.first,
                transcriptType = transcript?.second,
                notesHtml = item.contentHtml ?: item.descriptionHtml ?: item.summaryHtml,
            )
            if (episodes.size >= 2 * MAX_EPISODES) compact()
        }

        /**
         * Keeps the newest [MAX_EPISODES] so far (undated items count as the oldest, and among equals the order of the document
         * stays, so a feed without dates keeps its first episodes): a feed of a million items never holds more than twice that.
         */
        private fun compact() {
            val kept = episodes.sortedByDescending { it.publishedAt }.take(MAX_EPISODES)
            trimmed += episodes.size - kept.size
            episodes.clear()
            episodes.addAll(kept)
            seen.clear()
            kept.forEach { seen += it.guid }
        }

        /** [current], or what [read] reads; [read] runs either way, so the element is always read through. */
        private inline fun <T : Any> keep(current: T?, read: () -> T?): T? {
            val value = read()
            return current ?: value
        }

        private fun isMedia(enclosure: EnclosureCandidate): Boolean {
            val type = enclosure.type?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (type.startsWith("audio/") || type.startsWith("video/")) return true
            if (type !in GENERIC_TYPES) return false
            val path = enclosure.url.substringBefore('?').substringBefore('#')
            return path.substringAfterLast('.', "").lowercase(Locale.ROOT) in MEDIA_EXTENSIONS
        }

        /** [raw] as the show notes keep it: only text that has markup (plain text is the description), cut at [MAX_NOTES]. */
        private fun markup(raw: String): String? = raw.trim().takeIf { '<' in it && it.length > 1 }?.take(MAX_NOTES)

        /** "episode-01" from ".../episode-01.mp3": a title for an item that has none. */
        private fun fileTitle(url: String): String {
            val raw = url.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
            val name = runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
            return name.substringBeforeLast('.', name).ifEmpty { name }
        }

        // ------------------------------------------------------------ the pull parser's

        /** Calls [child] with the canonical name of each child element of the one at [depth]; [child] must read all of it. */
        private inline fun forEachChild(depth: Int, child: (String) -> Unit) {
            while (true) {
                when (parser.next()) {
                    XmlPullParser.END_DOCUMENT -> return
                    XmlPullParser.END_TAG -> if (parser.depth <= depth) return
                    XmlPullParser.START_TAG -> {
                        declare()
                        child(canonical(parser.name))
                    }
                }
            }
        }

        /** Past the element the parser is at the start of. */
        private fun skip() {
            var depth = 1
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.END_DOCUMENT -> return
                }
            }
        }

        /** The text of the element the parser is at the start of, through its end; the text of elements inside it too (HTML written raw). */
        private fun readText(): String {
            val text = StringBuilder()
            var depth = 1
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> parser.text?.let { text.append(it) }
                    // an entity the parser does not know (&nbsp;) stays as written; HtmlText reads it
                    XmlPullParser.ENTITY_REF -> text.append(parser.text ?: "&${parser.name};")
                    XmlPullParser.START_TAG -> {
                        depth++
                        // HTML written raw inside the element: its line breaks are kept
                        if (local(parser.name) == "br") text.append('\n')
                    }
                    XmlPullParser.END_TAG -> {
                        depth--
                        if (depth > 0 && local(parser.name) in BLOCKS) text.append('\n')
                    }
                    XmlPullParser.END_DOCUMENT -> return text.toString()
                }
                if (text.length > MAX_TEXT) {
                    skipToEnd(depth)
                    return text.toString()
                }
            }
            return text.toString()
        }

        private fun skipToEnd(open: Int) {
            var depth = open
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.END_DOCUMENT -> return
                }
            }
        }

        /** The attribute [name] of the current element, in any case and prefix; null when absent or empty. */
        private fun attr(name: String): String? {
            for (i in 0 until parser.attributeCount) {
                if (local(parser.getAttributeName(i)) == name) return parser.getAttributeValue(i)?.trim()?.takeIf { it.isNotEmpty() }
            }
            return null
        }

        /** Remembers the namespace prefixes the current element declares. */
        private fun declare() {
            for (i in 0 until parser.attributeCount) {
                val name = parser.getAttributeName(i)
                if (name.startsWith("xmlns:", ignoreCase = true)) prefixes[name.substring(6).lowercase(Locale.ROOT)] = parser.getAttributeValue(i).orEmpty().lowercase(Locale.ROOT)
            }
        }

        private fun local(name: String): String = name.substringAfter(':').lowercase(Locale.ROOT)

        /** "itunes:duration" for any prefix of the iTunes namespace; names of no known namespace keep their prefix. */
        private fun canonical(name: String): String {
            val colon = name.indexOf(':')
            if (colon < 0) return name.lowercase(Locale.ROOT)
            val prefix = name.substring(0, colon).lowercase(Locale.ROOT)
            val local = name.substring(colon + 1).lowercase(Locale.ROOT)
            val uri = prefixes[prefix]
            val space = when {
                uri != null && "itunes.com" in uri -> NS_ITUNES
                uri != null && "podcastindex.org/namespace" in uri -> NS_PODCAST
                uri != null && "rss/1.0/modules/content" in uri -> NS_CONTENT
                uri != null && "search.yahoo.com/mrss" in uri -> NS_MEDIA
                uri != null && "purl.org/dc/elements" in uri -> NS_DC
                // undeclared prefixes (and Atom's, which is the default namespace of its feeds) keep the usual names
                else -> prefix
            }
            return "$space:$local"
        }
    }

    /** More text than this in one element is cut: no description is that long. */
    private const val MAX_TEXT = 200_000

    /** S-205: the show notes of an episode are kept up to this many characters of markup. */
    private const val MAX_NOTES = 64_000
}
