package app.nagmedar.ui.podcast

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.Charset

/** S-1104: a chapter of an episode: where it starts, what it is called, and what the file adds (a picture, a link). */
@Immutable
data class Chapter(
    val startMs: Long,
    /** Empty when the file names none; the lists then say "Kısım 3". */
    val title: String,
    val endMs: Long? = null,
    val imageUrl: String? = null,
    val url: String? = null,
)

/**
 * S-1104: an episode's chapters from the two places they come from: Podcasting 2.0's `podcast:chapters` JSON file (the
 * feed names it) and the ID3 tag of an MP3 (`CHAP` frames, in the order of the `CTOC` frame when there is one).
 */
object Chapters {
    /** More chapters than this in a file are not chapters but a mistake. */
    const val MAX_CHAPTERS = 1_000

    /** The chapter playing at [ms]: its index, or null before the first. */
    fun indexAt(chapters: List<Chapter>, ms: Long): Int? = chapters.indexOfLast { it.startMs <= ms }.takeIf { it >= 0 }

    /**
     * The chapter to go to from [nowMs]: the next one, or for [forward] false the start of this one, or the one before
     * when this one began less than three seconds ago. Null where there is none to go to.
     */
    fun step(chapters: List<Chapter>, nowMs: Long, forward: Boolean): Int? {
        if (chapters.isEmpty()) return null
        val current = chapters.indexOfLast { it.startMs <= nowMs }.coerceAtLeast(0)
        return if (forward) {
            (current + 1).takeIf { it < chapters.size }
        } else if (nowMs - chapters[current].startMs > BACK_TO_START_MS || current == 0) {
            current
        } else {
            current - 1
        }
    }

    private const val BACK_TO_START_MS = 3_000L

    // ------------------------------------------------------------ Podcasting 2.0

    /**
     * The chapters of a Podcasting 2.0 chapters file: `{"version": "1.2.0", "chapters": [{"startTime": 0, "title": "…",
     * "img": "…", "url": "…", "toc": false}]}` (a bare array of chapters reads too). Start times are seconds, fractions
     * allowed. Chapters that say `"toc": false` are not for lists and are left out; the rest in the order of their time,
     * the first at 0 (an episode that starts with no chapter gets none made up). Empty for anything that is not such a file.
     */
    fun parseJson(text: String): List<Chapter> {
        val array = try {
            val trimmed = text.trim().removePrefix("\uFEFF")
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("chapters")
        } catch (e: JSONException) {
            null
        } ?: return emptyList()
        val chapters = ArrayList<Chapter>()
        for (i in 0 until minOf(array.length(), MAX_CHAPTERS)) {
            val item = array.optJSONObject(i) ?: continue
            if (!item.has("startTime")) continue
            if (item.has("toc") && !item.optBoolean("toc", true)) continue
            val start = item.optDouble("startTime", Double.NaN)
            if (start.isNaN() || start < 0 || start > MAX_SECONDS) continue
            val end = item.optDouble("endTime", Double.NaN)
            chapters += Chapter(
                startMs = (start * 1_000).toLong(),
                title = item.optString("title", "").trim(),
                endMs = if (end.isNaN() || end < start || end > MAX_SECONDS) null else (end * 1_000).toLong(),
                imageUrl = item.optString("img", "").trim().takeIf { isHttp(it) },
                url = item.optString("url", "").trim().takeIf { isHttp(it) },
            )
        }
        return chapters.sortedBy { it.startMs }.distinctBy { it.startMs }
    }

    private const val MAX_SECONDS = 7 * 24 * 3_600.0

    private fun isHttp(url: String): Boolean = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    // ------------------------------------------------------------ ID3

    /** How many bytes the ID3v2 tag at the start of a file takes, header included, from its first ten [header] bytes; null when the file has none. */
    fun id3Size(header: ByteArray): Int? {
        if (header.size < ID3_HEADER || header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
        val size = syncsafe(header, 6) ?: return null
        val footer = if (header[3] >= 4 && (header[5].toInt() and 0x10) != 0) 10 else 0
        return ID3_HEADER + size + footer
    }

    private const val ID3_HEADER = 10

    /**
     * The chapters in the ID3v2.3 or v2.4 tag at the start of [file] (all of the tag, or as much of it as there is: a frame
     * cut short is left out). They are in the order of the table of contents frame (`CTOC`), else of their times. Empty
     * for a file with no tag, an older tag, or no chapters.
     */
    fun parseId3(file: ByteArray): List<Chapter> {
        val total = id3Size(file.copyOf(minOf(file.size, ID3_HEADER))) ?: return emptyList()
        val version = file[3].toInt()
        if (version != 3 && version != 4) return emptyList()
        val flags = file[5].toInt()
        var body = file.copyOfRange(ID3_HEADER, minOf(file.size, total))
        if (flags and 0x80 != 0) body = undoUnsynchronisation(body)
        var at = 0
        if (flags and 0x40 != 0 && body.size >= 4) {
            // an extended header: its size, which v2.4 writes syncsafe and counts whole, v2.3 without its own four bytes
            val size = if (version == 4) syncsafe(body, 0) ?: return emptyList() else beInt(body, 0) + 4
            at = size
        }
        val found = LinkedHashMap<String, Chapter>()
        var order: List<String>? = null
        while (at + FRAME_HEADER <= body.size) {
            val frame = readFrame(body, at, version) ?: break
            at = frame.next
            when (frame.id) {
                "CHAP" -> chapterOf(body, frame, version)?.let { (id, chapter) -> found.putIfAbsent(id, chapter) }
                "CTOC" -> if (order == null || tocIsTopLevel(body, frame)) tocOf(body, frame)?.let { order = it }
            }
        }
        if (found.isEmpty()) return emptyList()
        val listed = order?.mapNotNull { found[it] }?.takeIf { it.isNotEmpty() }
        val chapters = listed ?: found.values.sortedBy { it.startMs }
        return chapters.take(MAX_CHAPTERS)
    }

    private const val FRAME_HEADER = 10

    private class Frame(val id: String, val start: Int, val end: Int, val next: Int)

    /** The frame at [at]: null at the padding that ends the frames, or for one that does not fit. */
    private fun readFrame(body: ByteArray, at: Int, version: Int): Frame? {
        if (body[at].toInt() == 0) return null
        val id = String(body, at, 4, Charsets.ISO_8859_1)
        if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) return null
        val size = if (version == 4) syncsafe(body, at + 4) ?: return null else beInt(body, at + 4)
        if (size < 0) return null
        val start = at + FRAME_HEADER
        val end = start + size
        if (end > body.size) return null
        // v2.4 frame flags: a data length indicator puts four bytes before the data
        val formatFlags = body[at + 9].toInt()
        val skip = if (version == 4 && formatFlags and 0x01 != 0) 4 else 0
        return Frame(id, minOf(start + skip, end), end, end)
    }

    private fun chapterOf(body: ByteArray, frame: Frame, version: Int): Pair<String, Chapter>? {
        val idEnd = indexOfZero(body, frame.start, frame.end)
        if (idEnd < 0 || idEnd + 1 + 16 > frame.end) return null
        val id = String(body, frame.start, idEnd - frame.start, Charsets.ISO_8859_1)
        val times = idEnd + 1
        val start = beInt(body, times).toLong() and 0xFFFFFFFFL
        val end = (beInt(body, times + 4).toLong() and 0xFFFFFFFFL).takeIf { it != 0xFFFFFFFFL && it > start }
        var title = ""
        var url: String? = null
        var at = times + 16
        while (at + FRAME_HEADER <= frame.end) {
            val sub = readFrame(body, at, version)?.takeIf { it.end <= frame.end } ?: break
            when (sub.id) {
                "TIT2" -> if (title.isEmpty()) title = textOf(body, sub.start, sub.end)
                "TIT3" -> if (title.isEmpty()) title = textOf(body, sub.start, sub.end)
                "WXXX" -> if (url == null) url = linkOf(body, sub.start, sub.end)
            }
            at = sub.next
        }
        return id to Chapter(start, title.trim(), end, null, url)
    }

    /** The children of a table of contents frame, in order. */
    private fun tocOf(body: ByteArray, frame: Frame): List<String>? {
        val idEnd = indexOfZero(body, frame.start, frame.end)
        if (idEnd < 0 || idEnd + 3 > frame.end) return null
        var at = idEnd + 3
        val count = body[idEnd + 2].toInt() and 0xFF
        val children = ArrayList<String>(count)
        repeat(count) {
            val end = indexOfZero(body, at, frame.end)
            if (end < 0) return children.takeIf { it.isNotEmpty() }
            children += String(body, at, end - at, Charsets.ISO_8859_1)
            at = end + 1
        }
        return children
    }

    private fun tocIsTopLevel(body: ByteArray, frame: Frame): Boolean {
        val idEnd = indexOfZero(body, frame.start, frame.end)
        return idEnd >= 0 && idEnd + 1 < frame.end && body[idEnd + 1].toInt() and 0x02 != 0
    }

    /** A text frame's text: its encoding byte, then the text up to its end or its terminator. */
    private fun textOf(body: ByteArray, start: Int, end: Int): String {
        if (end - start < 1) return ""
        val encoding = body[start].toInt()
        return decode(body, start + 1, end, encoding).substringBefore('\u0000')
    }

    /** A user URL frame's address: after its encoding byte and its description. */
    private fun linkOf(body: ByteArray, start: Int, end: Int): String? {
        if (end - start < 2) return null
        val encoding = body[start].toInt()
        val wide = encoding == 1 || encoding == 2
        var at = start + 1
        // the description ends with a terminator of one byte, two for UTF-16
        while (at < end) {
            if (wide) {
                if (at + 1 < end && body[at].toInt() == 0 && body[at + 1].toInt() == 0) {
                    at += 2
                    break
                }
                at += 2
            } else {
                if (body[at].toInt() == 0) {
                    at += 1
                    break
                }
                at++
            }
        }
        if (at > end) return null
        val link = String(body, at, end - at, Charsets.ISO_8859_1).substringBefore('\u0000').trim()
        return link.takeIf { isHttp(it) }
    }

    private fun decode(body: ByteArray, start: Int, end: Int, encoding: Int): String {
        if (start >= end) return ""
        val charset: Charset = when (encoding) {
            1 -> {
                // a byte order mark says which way; none: big endian as the standard has it
                val little = end - start >= 2 && body[start] == 0xFF.toByte() && body[start + 1] == 0xFE.toByte()
                val skip = if (end - start >= 2 && (little || (body[start] == 0xFE.toByte() && body[start + 1] == 0xFF.toByte()))) 2 else 0
                return String(body, start + skip, end - start - skip, if (little) Charsets.UTF_16LE else Charsets.UTF_16BE)
            }
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(body, start, end - start, charset)
    }

    private fun indexOfZero(body: ByteArray, from: Int, to: Int): Int {
        for (i in from until minOf(to, body.size)) if (body[i].toInt() == 0) return i
        return -1
    }

    private fun beInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)

    /** Four syncsafe bytes (seven bits each); null for bytes that are not. */
    private fun syncsafe(bytes: ByteArray, at: Int): Int? {
        if (at + 4 > bytes.size) return null
        var value = 0
        for (i in 0 until 4) {
            val b = bytes[at + i].toInt()
            if (b < 0 || b > 0x7F) return null
            value = (value shl 7) or b
        }
        return value
    }

    /** 0xFF 0x00 is 0xFF: what a tag written with unsynchronisation needs undone. */
    private fun undoUnsynchronisation(bytes: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(bytes.size)
        var i = 0
        while (i < bytes.size) {
            out.write(bytes[i].toInt())
            if (bytes[i] == 0xFF.toByte() && i + 1 < bytes.size && bytes[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }
}
