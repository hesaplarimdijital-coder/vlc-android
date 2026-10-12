package app.nagmedar.ui.lyrics

import androidx.compose.runtime.Immutable
import app.nagmedar.audio.TagLyrics
import app.nagmedar.data.LyricsSource

/** S-1003: where lyrics came from, by the cache's names for them. */
enum class LyricsOrigin(val key: String) {
    /** Written or corrected by hand. */
    User(LyricsSource.USER),

    /** The file's own tags: USLT, SYLT, LYRICS, ©lyr. */
    Embedded(LyricsSource.EMBEDDED),

    /** An .lrc file beside it. */
    File(LyricsSource.FILE),

    /** LRCLIB, kept for offline use. */
    Online(LyricsSource.LRCLIB),
    ;

    companion object {
        fun of(key: String): LyricsOrigin? = entries.firstOrNull { it.key == key }
    }
}

/** A word (or syllable) of a word-timed line, sung from [startMs]; [text] keeps the space after it. */
@Immutable
data class LyricWord(val startMs: Long, val text: String)

/** A line of lyrics, sung from [startMs] (-1: not timed); [words] when its words are timed too. */
@Immutable
data class LyricLine(val startMs: Long, val text: String, val words: List<LyricWord> = emptyList()) {
    internal fun shifted(byMs: Long): LyricLine = copy(
        startMs = (startMs + byMs).coerceAtLeast(0),
        words = words.map { it.copy(startMs = (it.startMs + byMs).coerceAtLeast(0)) },
    )
}

/**
 * S-1003: a track's lyrics as shown: timed lines (LRC, SYLT) or plain ones, where they came from,
 * and the user's shift for them: a positive [offsetMs] shows the lines later, as a subtitle delay
 * does. [instrumental]: LRCLIB knows the track has no words.
 */
@Immutable
data class Lyrics(
    val lines: List<LyricLine>,
    val origin: LyricsOrigin,
    val offsetMs: Long = 0,
    val instrumental: Boolean = false,
) {
    val synced: Boolean get() = lines.isNotEmpty() && lines.all { it.startMs >= 0 }

    /** Some line has its words timed: karaoke. */
    val wordTimed: Boolean get() = synced && lines.any { it.words.isNotEmpty() }

    /** The lines as the cache keeps them: LRC when timed, else the text. */
    val cacheText: String get() = Lrc.write(lines)

    /** The line sung at [positionMs] of the track, the shift taken into account; -1 before the first, or for plain lyrics. */
    fun lineAt(positionMs: Long): Int {
        if (!synced) return -1
        val at = positionMs - offsetMs
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (lines[middle].startMs <= at) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return found
    }

    companion object {
        /** Lyrics read from [text]: LRC when it has timed lines, else its plain lines; null when it holds none. */
        fun of(text: String, origin: LyricsOrigin, offsetMs: Long = 0): Lyrics? =
            Lrc.parse(text).takeIf { it.isNotEmpty() }?.let { Lyrics(it, origin, offsetMs) }

        /** What a file's tags hold: SYLT's lines first, else the plain text (which may be LRC). */
        fun of(tags: TagLyrics): Lyrics? {
            if (tags.synced.isNotEmpty()) return Lyrics(syltLines(tags.synced), LyricsOrigin.Embedded)
            return tags.plain?.let { of(it, LyricsOrigin.Embedded) }
        }

        /**
         * SYLT's entries as lines: each its own line, or, when they carry line breaks (a break
         * before or after an entry ends a line), syllables grouped into word-timed lines.
         */
        internal fun syltLines(entries: List<TagLyrics.Line>): List<LyricLine> {
            val sorted = entries.sortedBy { it.timeMs }
            val breaks = sorted.any { it.text.startsWith('\n') || it.text.startsWith('\r') || it.text.endsWith('\n') }
            if (!breaks) return sorted.map { LyricLine(it.timeMs, it.text.trim()) }
            val lines = ArrayList<LyricLine>()
            var words = ArrayList<LyricWord>()
            fun flush() {
                if (words.isEmpty()) return
                val text = words.joinToString("") { it.text }.trim()
                lines += LyricLine(words.first().startMs, text, if (words.size > 1) words.toList() else emptyList())
                words = ArrayList()
            }
            for (entry in sorted) {
                if (entry.text.startsWith('\n') || entry.text.startsWith('\r')) flush()
                val text = entry.text.trim('\n', '\r')
                if (text.isNotEmpty()) words += LyricWord(entry.timeMs, text)
                if (entry.text.endsWith('\n')) flush()
            }
            flush()
            return lines
        }
    }
}
