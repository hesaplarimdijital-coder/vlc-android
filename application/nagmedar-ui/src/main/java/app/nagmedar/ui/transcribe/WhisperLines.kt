package app.nagmedar.ui.transcribe

/** S-709: one line of speech as whisper heard it, its times in milliseconds of the audio. */
data class SpeechSegment(val startMs: Long, val endMs: Long, val text: String)

/**
 * S-709: whisper's lines as a transcript's passages: whisper's own notes ("[BLANK_AUDIO]") and
 * lines with no word left out, a line it repeats over and over (whisper does, on music) kept twice
 * at most; each passage long enough to be followed and over before the next one; at most two rows
 * of about 42 characters, a longer line shared out over several, their time by their length. The
 * file is WebVTT in UTF-8.
 */
object WhisperLines {
    /** A row of a passage, characters. */
    const val ROW = 42

    /** Rows of one passage. */
    const val ROWS = 2

    /** The least time a line shows, when the next one leaves it the room. */
    const val MIN_MS = 700L

    /** The most: speech heard over a long stretch of music is not followed through it. */
    const val MAX_MS = 10_000L

    /** Shorter than this, a line is not worth showing. */
    private const val SHORTEST_MS = 100L

    /** How often the same words may follow each other. */
    private const val REPEATS = 2

    /** The passages of [segments], in order. */
    fun cues(segments: List<SpeechSegment>): List<TimedCue> {
        val spoken = mutableListOf<SpeechSegment>()
        var repeated = 0
        for (segment in segments.sortedBy { it.startMs }) {
            val text = clean(segment.text) ?: continue
            if (spoken.lastOrNull()?.text == text) {
                if (++repeated >= REPEATS) continue
            } else {
                repeated = 0
            }
            spoken += segment.copy(text = text)
        }
        val pieces = spoken.flatMap(::share)
        val cues = mutableListOf<TimedCue>()
        var previousEnd = 0L
        pieces.forEachIndexed { index, piece ->
            val start = maxOf(piece.startMs, previousEnd)
            val nextStart = pieces.getOrNull(index + 1)?.startMs ?: Long.MAX_VALUE
            val end = maxOf(piece.endMs, start + MIN_MS).coerceAtMost(start + MAX_MS).coerceAtMost(maxOf(nextStart, start))
            if (end - start < SHORTEST_MS) return@forEachIndexed
            cues += TimedCue(cues.size + 1L, start, end, rows(piece.text).joinToString("\n"))
            previousEnd = end
        }
        return cues
    }

    /**
     * [cues] as a WebVTT file in UTF-8 without a byte order mark, [note] (what made it) in a NOTE block after its
     * header, which the transcript readers ([app.nagmedar.ui.podcast.Transcripts]) pass over. WebVTT allows no "-->"
     * in a cue's text nor in a note: whisper's are written "->".
     */
    fun vtt(cues: List<TimedCue>, note: String): ByteArray {
        val said = note.replace(SPACES, " ").replace(ARROW, "->").trim()
        val head = if (said.isEmpty()) "WEBVTT\n\n" else "WEBVTT\n\nNOTE $said\n\n"
        return SubtitleDocument(SubtitleFormat.Vtt, cues.map { it.copy(text = it.text.replace(ARROW, "->")) }, head = head).toBytes(withBom = false)
    }

    private const val ARROW = "-->"

    /** The words of a line, one space apart; null for whisper's notes and lines without a word. */
    internal fun clean(text: String): String? {
        val words = text.replace(SPACES, " ").trim()
        if (words.isEmpty() || NOTE.matches(words)) return null
        return words.takeIf { line -> line.any { it.isLetterOrDigit() } }
    }

    /** A line too long for two rows shared out over several, each its part of the time by its length. */
    private fun share(segment: SpeechSegment): List<SpeechSegment> {
        val parts = parts(segment.text, ROW * ROWS)
        if (parts.size == 1) return listOf(segment)
        val length = parts.sumOf { it.length }.coerceAtLeast(1)
        val span = (segment.endMs - segment.startMs).coerceAtLeast(0)
        var at = segment.startMs
        var counted = 0
        return parts.map { part ->
            counted += part.length
            val end = segment.startMs + span * counted / length
            SpeechSegment(at, end, part).also { at = end }
        }
    }

    /** [text] in parts of at most [max] characters, cut between words, as alike in length as can be. */
    internal fun parts(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val words = text.split(' ')
        val count = (text.length + max - 1) / max
        val target = text.length / count
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            val joined = current.length + (if (current.isEmpty()) 0 else 1) + word.length
            // a part ends before the word that takes it past [max], or past its share by more than leaving the word out falls short
            val overshoots = out.size < count - 1 && joined > target && joined - target > target - current.length
            if (current.isNotEmpty() && (joined > max || overshoots)) {
                out += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    /**
     * [text] on the screen: one row when it fits, else two cut between words where the longer
     * row is shortest, after a comma or a full stop when that costs little.
     */
    internal fun rows(text: String, width: Int = ROW): List<String> {
        if (text.length <= width) return listOf(text)
        var best: Int? = null
        var bestScore = Int.MAX_VALUE
        text.forEachIndexed { index, c ->
            if (c != ' ') return@forEachIndexed
            val longer = maxOf(index, text.length - index - 1)
            val after = text.getOrNull(index - 1)
            val score = longer - (if (after != null && after in PAUSES) PAUSE_BONUS else 0)
            if (score < bestScore) {
                bestScore = score
                best = index
            }
        }
        val at = best ?: return listOf(text)
        return listOf(text.substring(0, at), text.substring(at + 1))
    }

    private const val PAUSE_BONUS = 6
    private val PAUSES = setOf(',', '.', ';', ':', '?', '!', '…')
    private val SPACES = Regex("\\s+")

    /** whisper.cpp's notes of what is not speech: "[BLANK_AUDIO]", "[Music]". */
    private val NOTE = Regex("""^\[[^\]]*]$""")
}
