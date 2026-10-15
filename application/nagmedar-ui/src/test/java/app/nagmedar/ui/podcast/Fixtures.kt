package app.nagmedar.ui.podcast

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.GZIPOutputStream

/** S-1101: the feeds and OPML files in `src/test/resources/podcast/`, as the network would give them. */
internal object Fixtures {
    fun bytes(name: String): ByteArray =
        (Fixtures::class.java.classLoader!!.getResourceAsStream("podcast/$name") ?: error("no fixture $name")).use { it.readBytes() }

    fun stream(name: String): InputStream = ByteArrayInputStream(bytes(name))

    fun text(name: String): String = String(bytes(name), Charsets.UTF_8)

    fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    fun parse(name: String, base: String? = null): ParsedFeed = FeedParser.parse(stream(name), base)

    /** The reason [name] is not a feed; fails when it is one. */
    fun invalidReason(name: String): InvalidReason = reasonOf(bytes(name))

    fun reasonOf(bytes: ByteArray): InvalidReason = try {
        FeedParser.parse(ByteArrayInputStream(bytes))
        throw AssertionError("read as a feed")
    } catch (e: FeedFailure.Invalid) {
        e.reason
    }
}
