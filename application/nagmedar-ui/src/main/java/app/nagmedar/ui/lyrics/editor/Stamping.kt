package app.nagmedar.ui.lyrics.editor

import androidx.compose.runtime.Immutable
import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.LyricWord
import app.nagmedar.ui.lyrics.Lrc

/** A time not set yet. */
const val UNSET = -1L

/** What a stamp times: a whole line, or the words of a line one by one. */
enum class StampMode { Line, Word }

/** A word of an edited line from [startMs] ([UNSET] before it is stamped); [text] keeps the space after it. */
@Immutable
data class EditWord(val text: String, val startMs: Long = UNSET)

/** A line of the lyrics being timed: sung from [startMs] ([UNSET] before it is stamped), made of [words]. */
@Immutable
data class EditLine(val text: String, val startMs: Long = UNSET, val words: List<EditWord> = EditWords.of(text)) {

    val stamped: Boolean get() = startMs >= 0

    /** Every word has its time: the line is written with word stamps. */
    val wordTimed: Boolean get() = words.isNotEmpty() && words.all { it.startMs >= 0 }

    /** The line as lyrics: word-timed when every word is, else timed as a line, else not timed (-1). */
    fun lyric(): LyricLine = when {
        !stamped -> LyricLine(UNSET, text)
        wordTimed -> LyricLine(startMs, text, words.map { LyricWord(it.startMs, it.text) })
        else -> LyricLine(startMs, text)
    }

    internal fun cleared(): EditLine = copy(startMs = UNSET, words = words.map { it.copy(startMs = UNSET) })
}

internal object EditWords {
    private val WORD = Regex("""\S+\s*""")
    private val SPACES = Regex("""\s+""")

    /** [text]'s words, each with the space after it, the last without. A blank line has none. */
    fun of(text: String): List<EditWord> {
        val words = WORD.findAll(text.trim()).map { EditWord(it.value.replace(SPACES, " ")) }.toMutableList()
        if (words.isNotEmpty()) words[words.lastIndex] = words.last().let { it.copy(text = it.text.trimEnd()) }
        return words
    }
}

/** Where the next stamp goes: the [line] and, in word mode, the [word] of it. */
@Immutable
data class StampCursor(val line: Int, val word: Int = 0)

/**
 * S-1005: the lyrics being timed, as a state machine. A stamp times what the cursor is on (a line,
 * or in word mode its next word) and moves the cursor on; Backspace takes the last stamp back; the
 * cursor can be moved to any line (re-timing it, or timing from there); the last stamp can be
 * nudged by tenths of a second. A stamp is made at hundredths of a second, as LRC writes them, and
 * never before the stamp before it: a line stamped over later ones takes their times away, so the
 * times only grow. The state is a value: every operation gives the next one.
 */
@Immutable
data class StampState(
    val lines: List<EditLine>,
    val mode: StampMode = StampMode.Line,
    val cursor: StampCursor = StampCursor(0),
    private val steps: List<Step> = emptyList(),
) {

    /** What one stamp changed, for taking it back: the lines and the cursor before it, and what it timed. */
    @Immutable
    class Step internal constructor(val lines: List<EditLine>, val cursor: StampCursor, val at: StampCursor, val word: Boolean)

    /** Every line is stamped (there is at least one). */
    val complete: Boolean get() = lines.isNotEmpty() && lines.all { it.stamped }

    val stampedLines: Int get() = lines.count { it.stamped }

    /** The cursor is past the last line: nothing is left to stamp. */
    val finished: Boolean get() = cursor.line >= lines.size

    /** A stamp can be taken back. */
    val canUndo: Boolean get() = steps.isNotEmpty()

    /** Stamps were made since the text was set. */
    val changed: Boolean get() = steps.isNotEmpty()

    /** The line the cursor is on; null when finished or empty. */
    val current: EditLine? get() = lines.getOrNull(cursor.line)

    /** The time of the stamp [undo] would take back, [UNSET] without one. */
    val lastStampMs: Long
        get() {
            val at = steps.lastOrNull()?.at ?: return UNSET
            val line = lines.getOrNull(at.line) ?: return UNSET
            return if (steps.last().word) line.words.getOrNull(at.word)?.startMs ?: UNSET else line.startMs
        }

    /** The lines as lyrics, the ones not stamped yet not timed. */
    fun lyrics(): List<LyricLine> = lines.map { it.lyric() }

    /** Only the stamped lines, as lyrics: what a preview plays. */
    fun stampedLyrics(): List<LyricLine> = lines.filter { it.stamped }.map { it.lyric() }

    /** The mode changed; the cursor is on the first word of its line. */
    fun withMode(next: StampMode): StampState = if (next == mode) this else copy(mode = next, cursor = cursor.copy(word = 0))

    /** The cursor on [line] (its first word). */
    fun moveTo(line: Int): StampState = if (lines.isEmpty()) this else copy(cursor = StampCursor(line.coerceIn(0, lines.lastIndex)))

    /** The cursor [delta] lines on, within the lines. */
    fun moveBy(delta: Int): StampState = if (finished && delta > 0) this else moveTo(cursor.line.coerceAtMost(lines.size) + delta)

    /** Stamps what the cursor is on at [timeMs] of the track; the cursor goes on. Nothing when finished. */
    fun stamp(timeMs: Long): StampState {
        val line = current ?: return this
        val rounded = timeMs.coerceAtLeast(0) / 10 * 10
        val at = cursor
        val word = mode == StampMode.Word && line.words.isNotEmpty()
        val index = cursor.line
        val floor = floorFor(at, word)
        val time = maxOf(rounded, floor)
        val next = lines.toMutableList()
        val moved: StampCursor
        if (word) {
            val words = line.words.toMutableList()
            val first = at.word == 0
            if (first) {
                for (i in words.indices) words[i] = words[i].copy(startMs = UNSET)
            }
            words[at.word] = words[at.word].copy(startMs = time)
            next[index] = line.copy(startMs = if (first) time else line.startMs, words = words)
            moved = if (at.word + 1 < words.size) StampCursor(index, at.word + 1) else StampCursor(index + 1)
            if (first) clearLaterThan(next, index, time)
        } else {
            next[index] = line.copy(startMs = time, words = line.words.map { it.copy(startMs = UNSET) })
            moved = StampCursor(index + 1)
            clearLaterThan(next, index, time)
        }
        return copy(lines = next, cursor = moved, steps = steps + Step(lines, cursor, at, word))
    }

    /** Takes the last stamp back: its time is gone and the cursor is where it was. */
    fun undo(): StampState {
        val last = steps.lastOrNull() ?: return this
        return copy(lines = last.lines, cursor = last.cursor, steps = steps.dropLast(1))
    }

    /**
     * The last stamp [deltaMs] later (earlier when negative), but not past the stamps around it.
     * Without a stamp, or when the nudge would not move it, the state is the same.
     */
    fun nudge(deltaMs: Long): StampState {
        val last = steps.lastOrNull() ?: return this
        val at = last.at
        val line = lines.getOrNull(at.line) ?: return this
        val old = if (last.word) line.words.getOrNull(at.word)?.startMs ?: return this else line.startMs
        if (old < 0) return this
        val low = floorFor(at, last.word)
        val high = ceilingFor(at, last.word)
        val time = (old + deltaMs).coerceAtLeast(low).let { if (high == null) it else it.coerceAtMost(high) }
        if (time == old) return this
        val next = lines.toMutableList()
        next[at.line] = if (last.word) {
            val words = line.words.toMutableList()
            words[at.word] = words[at.word].copy(startMs = time)
            line.copy(startMs = if (at.word == 0) time else line.startMs, words = words)
        } else {
            line.copy(startMs = time)
        }
        return copy(lines = next)
    }

    /** The time nothing at [at] may be before: the stamp before it. */
    private fun floorFor(at: StampCursor, word: Boolean): Long {
        if (word && at.word > 0) {
            val before = lines[at.line].words.getOrNull(at.word - 1)?.startMs ?: UNSET
            if (before >= 0) return before
        }
        for (i in at.line - 1 downTo 0) {
            val line = lines[i]
            if (line.stamped) return if (line.wordTimed) maxOf(line.startMs, line.words.last().startMs) else line.startMs
        }
        return 0
    }

    /** The time nothing at [at] may be after: the stamp after it; null for none. */
    private fun ceilingFor(at: StampCursor, word: Boolean): Long? {
        if (word) {
            val after = lines[at.line].words.drop(at.word + 1).firstOrNull { it.startMs >= 0 }?.startMs
            if (after != null) return after
        }
        return lines.drop(at.line + 1).firstOrNull { it.stamped }?.startMs
    }

    /** The lines after [index] stamped at or before [time] lose their stamps: it would run backwards. */
    private fun clearLaterThan(next: MutableList<EditLine>, index: Int, time: Long) {
        for (i in index + 1 until next.size) {
            val line = next[i]
            if (line.stamped && line.startMs <= time) next[i] = line.cleared()
        }
    }

    companion object {
        /** The lyrics [lines] to time: stamped ones keep their times (an LRC that is being corrected). */
        fun of(lines: List<LyricLine>, mode: StampMode = StampMode.Line): StampState = StampState(
            lines.map { line ->
                val words = if (line.words.isEmpty()) EditWords.of(line.text) else line.words.map { EditWord(it.text, it.startMs) }
                EditLine(line.text, if (line.startMs >= 0) line.startMs else UNSET, words)
            },
            mode,
            // timing goes on after what is timed already; lyrics that are all timed are corrected from the top
            StampCursor(lines.indexOfFirst { it.startMs < 0 }.coerceAtLeast(0)),
        )

        /** [text] (plain lyrics, or LRC whose stamps are kept) as lines to time. */
        fun parse(text: String, mode: StampMode = StampMode.Line): StampState = of(Lrc.parse(text), mode)
    }
}
