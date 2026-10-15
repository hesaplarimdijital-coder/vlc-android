package app.nagmedar.ui.radio.record

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** S-1107: what a stream says a track is: [title] is null when it names none; [artist] is null when the text had no artist in it. */
data class TrackTag(val artist: String?, val title: String?) {
    /** Whether the tag names a title. */
    val named: Boolean get() = !title.isNullOrBlank()
}

/**
 * S-1107: the names of recorded files. A track is `Artist - Title.ext`; a title with no artist is
 * `Station - Title.ext`; a track with no title at all is `Station - 2026-10-15 14.30.05.ext`. A name
 * is made safe for FAT, exFAT and the storage access framework (none of `\ / : * ? " < > |`, no
 * control characters, no trailing dot or space, no reserved device name, at most [MAX_BYTES]
 * bytes), and a name already taken gets " (2)", " (3)"… before its extension. FAT does not tell
 * "A.mp3" from "a.mp3": names are compared in lower case.
 */
object TrackNames {

    /** The longest a file's name, extension included, grows in UTF-8 bytes (file systems allow 255). */
    const val MAX_BYTES = 200

    private val FORBIDDEN = Regex("""[\\/:*?"<>|\u0000-\u001F\u007F]""")

    private val RESERVED = Regex("""^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\..*)?$""", RegexOption.IGNORE_CASE)

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss", Locale.ROOT)

    /** [text] as one safe piece of a file name; empty when nothing of it is left. */
    fun sanitize(text: String): String {
        val normal = Normalizer.normalize(text, Normalizer.Form.NFC)
        val replaced = buildString {
            for (c in normal) {
                when {
                    c == '/' || c == '\\' || c == ':' || c == '|' -> append('-')
                    c == '"' -> append('\'')
                    FORBIDDEN.matches(c.toString()) -> Unit
                    else -> append(c)
                }
            }
        }
        val spaced = replaced.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        // FAT and Windows drop a trailing dot or space; a leading dot hides the file
        val trimmed = spaced.trim('.', ' ')
        if (trimmed.isEmpty()) return ""
        return if (RESERVED.matches(trimmed)) "_$trimmed" else trimmed
    }

    /** The name (no extension) a track gets: see the class's comment. */
    fun baseName(tag: TrackTag?, station: String, startedAt: Long, zone: ZoneId): String {
        val artist = tag?.artist?.let(::sanitize).orEmpty()
        val title = tag?.title?.let(::sanitize).orEmpty()
        val place = sanitize(station).ifEmpty { FALLBACK_STATION }
        val name = when {
            artist.isNotEmpty() && title.isNotEmpty() -> "$artist - $title"
            title.isNotEmpty() -> "$place - $title"
            else -> "$place - ${STAMP.format(Instant.ofEpochMilli(startedAt).atZone(zone))}"
        }
        return fit(name, MAX_BYTES - 8)
    }

    /** The folder a station's recordings go in. */
    fun folderName(station: String): String = fit(sanitize(station).ifEmpty { FALLBACK_STATION }, MAX_BYTES)

    /** `Base.ext`, or `Base (2).ext` and on when the lower-cased name is in [taken]. */
    fun unique(base: String, extension: String, taken: Set<String>): String {
        val ext = if (extension.isEmpty()) "" else ".$extension"
        var name = base + ext
        var n = 2
        while (name.lowercase(Locale.ROOT) in taken && n < MAX_COPIES) {
            name = "$base ($n)$ext"
            n++
        }
        return name
    }

    /** [text] cut at a letter's end so that it is at most [bytes] bytes of UTF-8. */
    fun fit(text: String, bytes: Int): String {
        if (text.toByteArray(Charsets.UTF_8).size <= bytes) return text
        var end = text.length
        while (end > 0 && text.substring(0, end).toByteArray(Charsets.UTF_8).size > bytes) {
            end--
            // not between the halves of a surrogate pair
            if (end > 0 && Character.isHighSurrogate(text[end - 1])) end--
        }
        return text.substring(0, end).trimEnd('.', ' ')
    }

    private const val MAX_COPIES = 1000

    private const val FALLBACK_STATION = "Radio"
}
