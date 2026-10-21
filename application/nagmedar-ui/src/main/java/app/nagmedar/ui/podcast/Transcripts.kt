package app.nagmedar.ui.podcast

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/** S-1104: one passage of a transcript: when it is said (-1 for a transcript with no times), who says it, and what. */
@Immutable
data class TranscriptCue(val startMs: Long, val endMs: Long, val speaker: String?, val text: String) {
    val timed: Boolean get() = startMs >= 0
}

/** S-1104: an episode's transcript: its passages in order; [timed] when they carry times (WebVTT, SubRip, JSON), else they only read. */
@Immutable
data class Transcript(val cues: List<TranscriptCue>, val timed: Boolean) {
    /** The passage being said at [ms]: its index, or -1 before the first or in a transcript with no times. */
    fun indexAt(ms: Long): Int {
        if (!timed) return -1
        var low = 0
        var high = cues.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMs <= ms) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    /** The passages whose words or speaker hold [term], ignoring case; their indexes. */
    fun find(term: String): List<Int> {
        val needle = term.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return emptyList()
        return cues.indices.filter { i -> cues[i].text.lowercase(Locale.ROOT).contains(needle) || cues[i].speaker?.lowercase(Locale.ROOT)?.contains(needle) == true }
    }
}

/**
 * S-1104: the transcript formats a feed's `podcast:transcript` names: WebVTT and SubRip (timed, preferred), the
 * Podcasting 2.0 JSON, and HTML or plain text (untimed). The type the feed gives is trusted only as far as the text
 * agrees with it: a file that starts with WEBVTT is WebVTT whatever it was called.
 */
object Transcripts {
    /** More passages than this in a file are cut: the longest episode's transcript is far fewer. */
    const val MAX_CUES = 30_000

    /**
     * The transcript in [text], read by what the text says it is, else by [type] (the feed's media type, which may be null
     * or wrong); null when nothing in it reads as a transcript.
     */
    fun parse(text: String, type: String?): Transcript? {
        val body = text.removePrefix("\uFEFF")
        for (kind in listOfNotNull(sniff(body), kindOf(type, body)).distinct()) {
            val cues = when (kind) {
                Kind.Vtt, Kind.Srt -> parseTimed(body)
                Kind.Json -> parseJson(body)
                Kind.Text -> parseText(body, html = false)
                Kind.Html -> parseText(body, html = true)
            }
            if (cues.isNotEmpty()) return Transcript(cues.take(MAX_CUES), timed = cues[0].timed)
        }
        return null
    }

    private enum class Kind { Vtt, Srt, Json, Html, Text }

    private fun kindOf(type: String?, body: String): Kind? = when (type?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)) {
        "text/vtt" -> Kind.Vtt
        "application/x-subrip", "application/srt", "text/srt", "application/x-srt" -> Kind.Srt
        "application/json", "text/json" -> Kind.Json
        "text/html", "application/xhtml+xml" -> Kind.Html
        "text/plain" -> Kind.Text
        // a feed that names no type: text, for lack of anything better
        else -> if (type.isNullOrBlank() && body.isNotBlank()) Kind.Text else null
    }

    /** What the text itself says it is, when it says: a header, a bracket, a timing line. */
    private fun sniff(body: String): Kind? {
        val head = body.trimStart().take(2_000)
        return when {
            head.startsWith("WEBVTT") -> Kind.Vtt
            head.startsWith("{") || head.startsWith("[") -> Kind.Json
            TIMING.containsMatchIn(head) -> Kind.Srt
            head.startsWith("<") && (head.contains("<html", ignoreCase = true) || head.contains("<p", ignoreCase = true) || head.contains("<div", ignoreCase = true)) -> Kind.Html
            else -> null
        }
    }

    // ------------------------------------------------------------ WebVTT and SubRip

    private val TIMING = Regex("""(?:(\d{1,3}):)?(\d{1,2}):(\d{2})[.,](\d{1,3})\s*-->\s*(?:(\d{1,3}):)?(\d{1,2}):(\d{2})[.,](\d{1,3})""")
    private val VOICE = Regex("""^<v(?:\.[^\s>]*)?\s+([^>]+)>""")
    private val TAGS = Regex("""<[^>]*>|\{\\[^}]*\}""")

    private fun millis(hours: String, minutes: String, seconds: String, fraction: String): Long {
        val ms = fraction.padEnd(3, '0').take(3).toLong()
        return (hours.ifEmpty { "0" }.toLong() * 3_600 + minutes.toLong() * 60 + seconds.toLong()) * 1_000 + ms
    }

    private fun parseTimed(body: String): List<TranscriptCue> {
        val cues = ArrayList<TranscriptCue>()
        val lines = body.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        while (i < lines.size) {
            val match = TIMING.find(lines[i])
            if (match == null) {
                i++
                continue
            }
            val g = match.groupValues
            val start = millis(g[1], g[2], g[3], g[4])
            val end = millis(g[5], g[6], g[7], g[8])
            i++
            val text = StringBuilder()
            var speaker: String? = null
            while (i < lines.size && lines[i].isNotBlank()) {
                val line = lines[i].trim()
                if (speaker == null) speaker = VOICE.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                val plain = HtmlText.decode(line.replace(TAGS, "")).trim()
                if (plain.isNotEmpty()) {
                    if (text.isNotEmpty()) text.append(' ')
                    text.append(plain)
                }
                i++
            }
            if (text.isNotEmpty()) cues += TranscriptCue(start, maxOf(end, start), speaker, text.toString())
            if (cues.size >= MAX_CUES) break
        }
        return cues.sortedBy { it.startMs }
    }

    // ------------------------------------------------------------ JSON

    /** Podcasting 2.0's: `{"version": "1.0.0", "segments": [{"speaker": "…", "startTime": 0, "endTime": 3.5, "body": "…"}]}`; times in seconds. */
    private fun parseJson(body: String): List<TranscriptCue> {
        val segments = try {
            val trimmed = body.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("segments")
        } catch (e: JSONException) {
            null
        } ?: return emptyList()
        val cues = ArrayList<TranscriptCue>()
        for (i in 0 until minOf(segments.length(), MAX_CUES)) {
            val item = segments.optJSONObject(i) ?: continue
            val text = item.optString("body", "").trim()
            val start = item.optDouble("startTime", Double.NaN)
            if (text.isEmpty() || start.isNaN() || start < 0) continue
            val end = item.optDouble("endTime", start)
            cues += TranscriptCue(
                startMs = (start * 1_000).toLong(),
                endMs = (maxOf(end, start) * 1_000).toLong(),
                speaker = item.optString("speaker", "").trim().takeIf { it.isNotEmpty() },
                text = text,
            )
        }
        return cues.sortedBy { it.startMs }
    }

    // ------------------------------------------------------------ untimed

    private fun parseText(body: String, html: Boolean): List<TranscriptCue> {
        // HTML gives a line to each paragraph; plain text a paragraph is a run of lines up to a blank one
        val paragraphs = if (html) {
            (HtmlText.plain(body, limit = 2_000_000) ?: return emptyList()).split('\n')
        } else {
            body.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n")).map { it.replace(Regex("\\s*\n\\s*"), " ") }
        }
        return paragraphs.map { it.trim() }.filter { it.isNotEmpty() }.map { TranscriptCue(-1, -1, null, it) }
    }
}
