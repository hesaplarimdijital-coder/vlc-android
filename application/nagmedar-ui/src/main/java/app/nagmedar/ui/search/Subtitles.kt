package app.nagmedar.ui.search

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/*
 * Searching what is said (S-311): the subtitle files next to the videos are read into cues, so the
 * search page finds a line and plays the film from it.
 */

/** One line of a subtitle file: when it starts and what it says (tags removed). */
class SubtitleCue(val startMs: Long, val text: String) {
    internal val folded: String = foldForSearch(text)
}

object SubtitleParser {
    private val srtTime = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->""")
    private val vttShortTime = Regex("""^(\d{2}):(\d{2})[.](\d{3})\s*-->""")
    private val tags = Regex("""<[^>]+>|\{[^}]*\}""")

    /** Cues of an SRT, WebVTT or ASS/SSA file; [name] tells the format by its extension. */
    fun parse(name: String, bytes: ByteArray): List<SubtitleCue> {
        val text = decode(bytes)
        return when (name.substringAfterLast('.', "").lowercase()) {
            "ass", "ssa" -> parseAss(text)
            else -> parseSrt(text)
        }
    }

    /**
     * The file's text: UTF-8 when it is valid UTF-8 (with or without a byte order mark), otherwise
     * Windows-1254, the Turkish code page most older subtitles are written in.
     */
    fun decode(bytes: ByteArray): String {
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, runCatching { Charset.forName("windows-1254") }.getOrDefault(Charsets.ISO_8859_1))
        }
    }

    private fun parseSrt(text: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        var start = -1L
        val lines = StringBuilder()
        fun flush() {
            if (start >= 0 && lines.isNotBlank()) cues += SubtitleCue(start, clean(lines.toString()))
            start = -1L
            lines.setLength(0)
        }
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val time = srtTime.find(line)
            val short = if (time == null) vttShortTime.find(line) else null
            when {
                time != null -> {
                    flush()
                    val (h, m, s, ms) = time.destructured
                    start = ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + ms.padEnd(3, '0').toLong()
                }
                short != null -> {
                    flush()
                    val (m, s, ms) = short.destructured
                    start = (m.toLong() * 60 + s.toLong()) * 1000 + ms.toLong()
                }
                line.isEmpty() -> flush()
                start >= 0 -> {
                    if (lines.isNotEmpty()) lines.append(' ')
                    lines.append(line)
                }
            }
        }
        flush()
        return cues
    }

    private fun parseAss(text: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        var textColumn = 9
        var startColumn = 1
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("Format:", ignoreCase = true)) {
                val fields = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                if ("text" in fields) textColumn = fields.indexOf("text")
                if ("start" in fields) startColumn = fields.indexOf("start")
                continue
            }
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue
            val fields = line.substringAfter(':').split(',', limit = textColumn + 1)
            if (fields.size <= textColumn) continue
            val start = assTime(fields[startColumn].trim()) ?: continue
            val said = clean(fields[textColumn].replace("\\N", " ").replace("\\n", " ").replace("\\h", " "))
            if (said.isNotEmpty()) cues += SubtitleCue(start, said)
        }
        return cues.sortedBy { it.startMs }
    }

    /** "0:01:02.34" → 62340 ms. */
    private fun assTime(time: String): Long? {
        val parts = time.split(':', '.')
        if (parts.size != 4) return null
        val (h, m, s, cs) = parts.map { it.toLongOrNull() ?: return null }
        return ((h * 60 + m) * 60 + s) * 1000 + cs * 10
    }

    private fun clean(text: String): String = text.replace(tags, "").replace(Regex("""\s+"""), " ").trim()
}

/** A line found in a subtitle file of [video]. */
class CueHit<V>(val video: V, val cue: SubtitleCue, val file: String)

/** The cues saying every word and phrase of [query], in the films' order and then by time; at most [limit]. */
fun <V> searchCues(files: List<Triple<V, String, List<SubtitleCue>>>, query: SearchQuery, limit: Int = 50): List<CueHit<V>> {
    if (query.isEmpty) return emptyList()
    val hits = mutableListOf<CueHit<V>>()
    for ((video, file, cues) in files) {
        for (cue in cues) {
            val text = cue.folded
            if (query.words.all { text.contains(it) } && query.phrases.all { text.contains(it) } && query.excluded.none { text.contains(it) }) {
                hits += CueHit(video, cue, file)
                if (hits.size >= limit) return hits
            }
        }
    }
    return hits
}
