package app.nagmedar.ui.lyrics.editor

import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.Lrc
import org.json.JSONArray

/** The tags at the top of an LRC file: what the track is. Blank ones are left out. */
data class LrcHeader(val title: String? = null, val artist: String? = null, val album: String? = null, val lengthMs: Long = 0)

/**
 * S-1005: timed lyrics written out as the places that take them want them: an LRC file (with
 * `[ti:]`, `[ar:]`, `[al:]` and `[length:]` on top, `[mm:ss.xx]` before each line and, for
 * word-timed lines, `<mm:ss.xx>` before each word), the plain text, ID3v2's SYLT entries, and the
 * line-timed LRC LRCLIB takes.
 */
object LrcDocument {

    /** The LRC file for [lines], the header's tags first. Lines not timed are written as they are. */
    fun lrc(lines: List<LyricLine>, header: LrcHeader? = null): String {
        val tags = header?.let(::tags).orEmpty()
        val body = Lrc.write(lines)
        return if (tags.isEmpty()) body else tags.joinToString("\n") + "\n" + body
    }

    /** LRC with a stamp for each line and no word stamps, no tags: what LRCLIB's syncedLyrics holds. */
    fun lineLrc(lines: List<LyricLine>): String = Lrc.write(lines.map { it.copy(words = emptyList()) })

    /** The words alone, a line each: USLT, and LRCLIB's plainLyrics. */
    fun plain(lines: List<LyricLine>): String = lines.joinToString("\n") { it.text }

    /**
     * ID3v2's SYLT as the engine takes it, JSON `[[ms, "text"], ...]`. Lines are entries of their
     * own; when some line is word-timed every line is written as its words, the last of a line
     * ending in a line break, which is how readers tell where a line ends. A blank line is a space
     * and a break, or the break alone would be lost.
     */
    fun sylt(lines: List<LyricLine>): String {
        val timed = lines.filter { it.startMs >= 0 }
        val words = timed.any { it.words.isNotEmpty() }
        val entries = JSONArray()
        fun add(ms: Long, text: String) {
            entries.put(JSONArray().put(ms).put(text))
        }
        for (line in timed) {
            when {
                !words -> add(line.startMs, line.text)
                line.words.isNotEmpty() -> line.words.forEachIndexed { i, word ->
                    add(word.startMs, if (i == line.words.lastIndex) word.text.trimEnd() + "\n" else word.text)
                }
                else -> add(line.startMs, (line.text.ifEmpty { " " }) + "\n")
            }
        }
        return entries.toString()
    }

    /**
     * What goes into the tags of a file: the text (USLT, LYRICS, ©lyr) and the SYLT entries (null where the
     * format keeps none). ID3v2 formats ([id3]) keep the words as plain text and the times as SYLT; the
     * others keep one text, the LRC (word stamps too), which the lyrics views read back as timed lines.
     */
    fun tagPayload(lines: List<LyricLine>, id3: Boolean): Pair<String, String?> =
        if (id3) plain(lines) to sylt(lines) else Lrc.write(lines) to null

    /** Whether the file keeps its lyrics as ID3v2 does: USLT and SYLT, not one text (MP3, AIFF, WAV). */
    fun usesId3(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in ID3_EXTENSIONS

    private val ID3_EXTENSIONS = setOf("mp3", "mp2", "aif", "aiff", "aifc", "wav")

    private fun tags(header: LrcHeader): List<String> = buildList {
        header.title.tag()?.let { add("[ti:$it]") }
        header.artist.tag()?.let { add("[ar:$it]") }
        header.album.tag()?.let { add("[al:$it]") }
        if (header.lengthMs > 0) add("[length:${minutesSeconds(header.lengthMs)}]")
    }

    /** A tag's value on one line, with nothing in it that would end the tag. */
    private fun String?.tag(): String? = this?.replace('\n', ' ')?.replace('\r', ' ')?.replace(']', ')')?.trim()?.takeIf { it.isNotEmpty() }

    private fun minutesSeconds(ms: Long): String {
        val seconds = (ms + 500) / 1_000
        return "%02d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
    }
}
