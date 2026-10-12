package app.nagmedar.ui.music

import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

/** S-804: the playlist files the library reads and writes. */
enum class PlaylistFormat(val extension: String, val mimeType: String) {
    M3U("m3u", "audio/x-mpegurl"),
    M3U8("m3u8", "audio/x-mpegurl"),
    PLS("pls", "audio/x-scpls"),
    XSPF("xspf", "application/xspf+xml");

    companion object {
        /** By the file's name; null for anything else. */
        fun of(fileName: String): PlaylistFormat? {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extension == extension }
        }
    }
}

/** One line of a playlist: where the file is (an absolute path or a URL), with what the file said of it. */
data class PlaylistEntry(val location: String, val title: String? = null, val artist: String? = null, val album: String? = null, val durationMs: Long = 0)

/**
 * S-804: M3U, M3U8, PLS and XSPF in and out. Reading, a relative location is taken from the
 * playlist's folder ([baseDir], a path); writing, a file under the same tree as the playlist is
 * written relative to it, so a folder copied elsewhere with its playlist still plays.
 */
object PlaylistFiles {

    // ------------------------------------------------------------------ reading

    /** The text of [bytes]: UTF-8 when it is (M3U8, XSPF, most M3U today), else Windows-1254 (an old Turkish M3U). */
    fun decode(bytes: ByteArray, format: PlaylistFormat): String {
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) bytes.copyOfRange(3, bytes.size) else bytes
        if (format == PlaylistFormat.M3U8 || format == PlaylistFormat.XSPF) return String(body, StandardCharsets.UTF_8)
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString()
        } catch (e: CharacterCodingException) {
            String(body, Charset.forName("windows-1254"))
        }
    }

    fun parse(text: String, format: PlaylistFormat, baseDir: String): List<PlaylistEntry> = when (format) {
        PlaylistFormat.M3U, PlaylistFormat.M3U8 -> parseM3u(text, baseDir)
        PlaylistFormat.PLS -> parsePls(text, baseDir)
        PlaylistFormat.XSPF -> parseXspf(text, baseDir)
    }

    private fun parseM3u(text: String, baseDir: String): List<PlaylistEntry> {
        val entries = mutableListOf<PlaylistEntry>()
        var info: Pair<Long, String?>? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    // #EXTINF:241,Artist - Title; or with attributes: #EXTINF:-1 tvg-name="a, b",Title
                    val body = line.substring(8)
                    val comma = nameComma(body)
                    val head = if (comma >= 0) body.substring(0, comma) else body
                    val seconds = head.trim().substringBefore(' ').toLongOrNull() ?: -1
                    val name = if (comma >= 0) body.substring(comma + 1).trim().ifEmpty { null } else null
                    info = (if (seconds > 0) seconds * 1000 else 0L) to name
                }
                line.startsWith("#") -> Unit
                else -> {
                    val (durationMs, name) = info ?: (0L to null)
                    val (artist, title) = splitName(name)
                    entries += PlaylistEntry(resolve(line, baseDir), title, artist, durationMs = durationMs)
                    info = null
                }
            }
        }
        return entries
    }

    /** The comma before an #EXTINF line's name: the first one outside quotes; -1 for none. */
    private fun nameComma(body: String): Int {
        var quoted = false
        for (i in body.indices) {
            when (body[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return i
            }
        }
        return -1
    }

    /** "Artist - Title" as M3U writes it; no dash: the title alone. */
    private fun splitName(name: String?): Pair<String?, String?> {
        if (name == null) return null to null
        val dash = name.indexOf(" - ")
        return if (dash > 0) name.substring(0, dash).trim() to name.substring(dash + 3).trim() else null to name
    }

    private fun parsePls(text: String, baseDir: String): List<PlaylistEntry> {
        val files = sortedMapOf<Int, String>()
        val titles = HashMap<Int, String>()
        val lengths = HashMap<Int, Long>()
        val key = Regex("""^(File|Title|Length)(\d+)\s*=\s*(.*)$""", RegexOption.IGNORE_CASE)
        for (raw in text.lineSequence()) {
            val match = key.find(raw.trim()) ?: continue
            val index = match.groupValues[2].toInt()
            val value = match.groupValues[3].trim()
            when (match.groupValues[1].lowercase()) {
                "file" -> files[index] = value
                "title" -> titles[index] = value
                "length" -> value.toLongOrNull()?.takeIf { it > 0 }?.let { lengths[index] = it * 1000 }
            }
        }
        return files.map { (index, file) ->
            val (artist, title) = splitName(titles[index])
            PlaylistEntry(resolve(file, baseDir), title, artist, durationMs = lengths[index] ?: 0)
        }
    }

    private fun parseXspf(text: String, baseDir: String): List<PlaylistEntry> {
        val tracks = Regex("""<track\b[^>]*>(.*?)</track>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(text)
        return tracks.mapNotNull { track ->
            val body = track.groupValues[1]
            fun field(name: String) = Regex("""<$name\b[^>]*>(.*?)</$name>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(body)?.groupValues?.get(1)?.let(::unescapeXml)?.trim()?.ifEmpty { null }
            val location = field("location") ?: return@mapNotNull null
            PlaylistEntry(
                resolveUri(location, baseDir),
                title = field("title"),
                artist = field("creator"),
                album = field("album"),
                durationMs = field("duration")?.toLongOrNull() ?: 0,
            )
        }.toList()
    }

    /**
     * A path or URL of an M3U or PLS line: absolute as it is, relative from [baseDir]; Windows'
     * backslashes turned, and its drive's paths ("D:/Müzik/…") kept for [PlaylistMatcher].
     */
    private fun resolve(location: String, baseDir: String): String {
        val text = location.trim()
        if (URL_SCHEME.containsMatchIn(text) || text.startsWith("file:", ignoreCase = true)) return resolveUri(text, baseDir)
        return fromDir(text.replace('\\', '/'), baseDir)
    }

    /** An XSPF location: a URI, relative ones from [baseDir]; file URIs as paths. */
    private fun resolveUri(location: String, baseDir: String): String {
        val text = location.trim()
        if (text.startsWith("file:", ignoreCase = true)) {
            val path = decodePath(text.substring(5)).replace('\\', '/')
            // file:///a/b, file://localhost/a/b, file:/a/b; file:///D:/a/b
            val local = when {
                path.startsWith("//localhost/", ignoreCase = true) -> path.substring(11)
                path.startsWith("//") -> path.substring(2)
                else -> path
            }
            val drive = local.removePrefix("/")
            return if (WINDOWS_DRIVE.containsMatchIn(drive)) drive else normalize(local)
        }
        if (Regex("""^[a-zA-Z][a-zA-Z0-9+.-]+:""").containsMatchIn(text) && !WINDOWS_DRIVE.containsMatchIn(text)) return text
        return fromDir(decodePath(text).replace('\\', '/'), baseDir)
    }

    private fun fromDir(path: String, baseDir: String): String = when {
        WINDOWS_DRIVE.containsMatchIn(path) -> path.substring(0, 2) + normalize(path.substring(2))
        path.startsWith("/") -> normalize(path)
        else -> normalize("$baseDir/$path")
    }

    private val URL_SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]+://""")

    private val WINDOWS_DRIVE = Regex("""^[a-zA-Z]:/""")

    private fun decodePath(text: String): String = runCatching { URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") }.getOrDefault(text)

    /** "/a/b/../c/./d" → "/a/c/d". */
    fun normalize(path: String): String {
        val parts = ArrayDeque<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(part)
            }
        }
        return "/" + parts.joinToString("/")
    }

    private fun unescapeXml(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("""&#(\d+);""")) { it.groupValues[1].toIntOrNull()?.let { code -> String(Character.toChars(code)) } ?: it.value }
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { it.groupValues[1].toIntOrNull(16)?.let { code -> String(Character.toChars(code)) } ?: it.value }
        .replace("&amp;", "&")

    // ------------------------------------------------------------------ writing

    /**
     * [entries] as a file of [format] that will be in [playlistDir]: local files relative to it
     * where they can be; with no folder known (a file of Drive's, say), every path as it is.
     */
    fun write(entries: List<PlaylistEntry>, format: PlaylistFormat, playlistDir: String?, name: String): String = when (format) {
        PlaylistFormat.M3U, PlaylistFormat.M3U8 -> buildString {
            append("#EXTM3U\n")
            if (name.isNotBlank()) append("#PLAYLIST:").append(name).append('\n')
            for (entry in entries) {
                val seconds = if (entry.durationMs > 0) (entry.durationMs + 500) / 1000 else -1
                append("#EXTINF:").append(seconds).append(',').append(displayName(entry)).append('\n')
                append(relative(entry.location, playlistDir)).append('\n')
            }
        }
        PlaylistFormat.PLS -> buildString {
            append("[playlist]\n")
            entries.forEachIndexed { i, entry ->
                val n = i + 1
                append("File").append(n).append('=').append(relative(entry.location, playlistDir)).append('\n')
                append("Title").append(n).append('=').append(displayName(entry)).append('\n')
                append("Length").append(n).append('=').append(if (entry.durationMs > 0) (entry.durationMs + 500) / 1000 else -1).append('\n')
            }
            append("NumberOfEntries=").append(entries.size).append('\n')
            append("Version=2\n")
        }
        PlaylistFormat.XSPF -> buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\">\n")
            if (name.isNotBlank()) append("  <title>").append(escapeXml(name)).append("</title>\n")
            append("  <trackList>\n")
            for (entry in entries) {
                append("    <track>\n")
                append("      <location>").append(escapeXml(relativeUri(entry.location, playlistDir))).append("</location>\n")
                entry.title?.let { append("      <title>").append(escapeXml(it)).append("</title>\n") }
                entry.artist?.let { append("      <creator>").append(escapeXml(it)).append("</creator>\n") }
                entry.album?.let { append("      <album>").append(escapeXml(it)).append("</album>\n") }
                if (entry.durationMs > 0) append("      <duration>").append(entry.durationMs).append("</duration>\n")
                append("    </track>\n")
            }
            append("  </trackList>\n")
            append("</playlist>\n")
        }
    }

    private fun displayName(entry: PlaylistEntry): String = listOfNotNull(entry.artist?.takeIf { it.isNotBlank() }, entry.title?.takeIf { it.isNotBlank() })
        .joinToString(" - ")
        .ifEmpty { entry.location.substringAfterLast('/') }

    /**
     * [location] (a path) from [dir]: "01.flac", "../Albüm/01.flac"; the same tree's files only
     * (the first folder under the root shared), others and URLs as they are.
     */
    fun relative(location: String, dir: String?): String {
        if (!location.startsWith("/") || dir.isNullOrEmpty()) return location
        val target = normalize(location).split('/').filter { it.isNotEmpty() }
        val from = normalize(dir).split('/').filter { it.isNotEmpty() }
        if (from.isEmpty()) return location
        var common = 0
        while (common < target.size - 1 && common < from.size && target[common] == from[common]) common++
        // nothing shared, or only a mount point like /storage: absolute
        if (common < SHARED_ROOT.coerceAtMost(from.size)) return location
        return (List(from.size - common) { ".." } + target.drop(common)).joinToString("/")
    }

    /** At least this many folders shared from the root (/storage/emulated/0/...) for a relative path. */
    private const val SHARED_ROOT = 3

    /** [location] for XSPF: a relative or file URI, each byte outside the unreserved set %-encoded. */
    private fun relativeUri(location: String, dir: String?): String {
        if (WINDOWS_DRIVE.containsMatchIn(location)) return "file:///" + location.split('/').joinToString("/") { if (it.length == 2 && it[1] == ':') it else encodeSegment(it) }
        if (!location.startsWith("/")) return location
        val path = relative(location, dir)
        val encoded = path.split('/').joinToString("/") { segment -> if (segment == "..") segment else encodeSegment(segment) }
        return if (path.startsWith("/")) "file://$encoded" else encoded
    }

    private fun encodeSegment(segment: String): String {
        val out = StringBuilder(segment.length)
        for (byte in segment.toByteArray(StandardCharsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            if (c < 0x80 && (c.toChar().isLetterOrDigit() || c.toChar() in "-._~!$'()*,;=@")) {
                out.append(c.toChar())
            } else {
                out.append('%').append(HEX[c shr 4]).append(HEX[c and 15])
            }
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/**
 * S-804: a playlist's entries to the library's files ([paths], absolute). The path itself first,
 * then without regard to case; else, for a list made on another device or computer (D:/Müzik/…,
 * /home/…/Music/…), the file of the same name whose folders agree the most from the end. One file
 * of that name is enough; of several, at least the folder above must agree, and only one the most.
 */
class PlaylistMatcher(paths: List<String>) {
    private val exact = HashMap<String, Int>(paths.size * 2)
    private val folded = HashMap<String, Int>(paths.size * 2)
    private val byName = HashMap<String, MutableList<Int>>(paths.size * 2)
    private val parts: List<List<String>>

    init {
        parts = paths.mapIndexed { i, path ->
            exact.putIfAbsent(path, i)
            val lower = path.lowercase(Locale.ROOT)
            folded.putIfAbsent(lower, i)
            val segments = lower.split('/').filter { it.isNotEmpty() }
            segments.lastOrNull()?.let { byName.getOrPut(it) { mutableListOf() } += i }
            segments
        }
    }

    /** The index in the paths of [location]'s file; null for none (a stream, a file not in the library). */
    fun match(location: String): Int? {
        exact[location]?.let { return it }
        val lower = location.lowercase(Locale.ROOT)
        folded[lower]?.let { return it }
        // a URL is its own; only paths are looked for by name
        if (!lower.startsWith("/") && !Regex("""^[a-z]:/""").containsMatchIn(lower)) return null
        val segments = lower.split('/').filter { it.isNotEmpty() }
        val candidates = byName[segments.lastOrNull() ?: return null] ?: return null
        if (candidates.size == 1) return candidates[0]
        var best: Int? = null
        var bestScore = 1
        var tie = false
        for (i in candidates) {
            val score = commonTail(segments, parts[i])
            if (score > bestScore) {
                best = i
                bestScore = score
                tie = false
            } else if (score == bestScore && best != null) {
                tie = true
            }
        }
        return if (tie) null else best
    }

    private fun commonTail(a: List<String>, b: List<String>): Int {
        var n = 0
        while (n < a.size && n < b.size && a[a.size - 1 - n] == b[b.size - 1 - n]) n++
        return n
    }
}
