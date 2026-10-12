package app.nagmedar.ui.lyrics

import java.util.Locale

/**
 * S-1003: LRC read and written. A line's `[mm:ss.xx]` stamps (several repeat it; `[mm:ss]`,
 * `[mm:ss.xxx]` and `[mm:ss:xx]` read too), the `[offset:±ms]` tag (a positive one shows every
 * line sooner), the other tags (`[ar:]`, `[ti:]`, `[length:]`, ...) left out, the enhanced form's
 * `<mm:ss.xx>` word stamps. Text without stamps is plain lyrics; a file whose stamps are all the
 * same time is not timed either.
 */
object Lrc {

    private val STAMP = Regex("""(\d{1,3}):(\d{1,2})(?:[.:,](\d{1,3}))?""")
    private val WORD_STAMP = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:,](\d{1,3}))?>""")
    private val TAG = Regex("""([A-Za-z#]+)\s*:(.*)""")
    private val SPACES = Regex("""\s+""")

    /** LRC's own tags; "[Nakarat: Ali]" in plain lyrics is not one of them. */
    private val TAGS = setOf("ar", "al", "ti", "au", "by", "length", "offset", "re", "ve", "tool", "la", "lang", "id", "#")

    /** The lines of [text]: timed and in order when it is LRC, else plain (blank runs made one, none at the ends). */
    fun parse(text: String): List<LyricLine> {
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        var offset = 0L
        for (raw in text.removePrefix("﻿").lineSequence()) {
            var rest = raw.trim()
            val stamps = ArrayList<Long>(1)
            var tagged = false
            while (rest.startsWith('[')) {
                val close = rest.indexOf(']')
                if (close < 0) break
                val inside = rest.substring(1, close).trim()
                val stamp = STAMP.matchEntire(inside)
                if (stamp != null) {
                    stamps += millis(stamp)
                } else {
                    val tag = if (stamps.isEmpty()) TAG.matchEntire(inside)?.takeIf { it.groupValues[1].lowercase(Locale.ROOT) in TAGS } else null
                    if (tag == null) break
                    if (tag.groupValues[1].equals("offset", ignoreCase = true)) {
                        tag.groupValues[2].trim().removePrefix("+").toLongOrNull()?.let { offset = it }
                    }
                    tagged = true
                }
                rest = rest.substring(close + 1)
            }
            when {
                stamps.isNotEmpty() -> {
                    val (line, words) = body(rest, stamps[0])
                    // a line sung more than once keeps its word stamps only where they were written
                    stamps.forEach { timed += LyricLine(it, line, if (stamps.size == 1) words else emptyList()) }
                }
                !tagged -> plain += rest
            }
        }
        if (timed.isEmpty()) return plainLines(plain)
        if (timed.size > 2 && timed.all { it.startMs == timed[0].startMs }) return plainLines(timed.map { it.text })
        return timed.map { if (offset != 0L) it.shifted(-offset) else it }.sortedBy { it.startMs }
    }

    /** [lines] as LRC: stamps (word stamps for word-timed lines) or, for plain lines, the text alone. */
    fun write(lines: List<LyricLine>): String = lines.joinToString("\n") { line ->
        when {
            line.startMs < 0 -> line.text
            line.words.isEmpty() -> "[${stamp(line.startMs)}]${line.text}"
            else -> "[${stamp(line.startMs)}]" + line.words.joinToString("") { "<${stamp(it.startMs)}>${it.text}" }
        }
    }

    /** "01:02.50"; milliseconds in three digits only when hundredths do not hold them. */
    fun stamp(ms: Long): String {
        val minutes = ms / 60_000
        val seconds = ms / 1_000 % 60
        val rest = ms % 1_000
        return if (rest % 10 == 0L) {
            String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, rest / 10)
        } else {
            String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, rest)
        }
    }

    private fun millis(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3]
        val part = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return minutes * 60_000 + seconds * 1_000 + part
    }

    /** A timed line's text and, with word stamps, its words: spaces made single, none at the ends. */
    private fun body(text: String, start: Long): Pair<String, List<LyricWord>> {
        val marks = WORD_STAMP.findAll(text).toList()
        if (marks.isEmpty()) return text.trim().replace(SPACES, " ") to emptyList()
        val segments = ArrayList<LyricWord>()
        var at = start
        var cursor = 0
        for (mark in marks) {
            segments += LyricWord(at, text.substring(cursor, mark.range.first))
            at = millis(mark)
            cursor = mark.range.last + 1
        }
        segments += LyricWord(at, text.substring(cursor))
        val line = StringBuilder()
        val words = ArrayList<LyricWord>()
        for (segment in segments) {
            var part = segment.text.replace(SPACES, " ")
            if (line.isEmpty() || line.endsWith(' ')) part = part.trimStart()
            if (part.isEmpty()) continue
            if (part.isBlank()) {
                // a space with a stamp of its own: the pause after the word before
                line.append(' ')
                words[words.lastIndex] = words.last().let { it.copy(text = it.text + " ") }
                continue
            }
            line.append(part)
            words += LyricWord(segment.startMs, part)
        }
        if (words.isNotEmpty()) words[words.lastIndex] = words.last().let { it.copy(text = it.text.trimEnd()) }
        return line.toString().trim() to words
    }

    private fun plainLines(texts: List<String>): List<LyricLine> {
        val lines = ArrayList<LyricLine>()
        for (text in texts) {
            val blank = text.isBlank()
            if (blank && (lines.isEmpty() || lines.last().text.isEmpty())) continue
            lines += LyricLine(-1, if (blank) "" else text)
        }
        while (lines.isNotEmpty() && lines.last().text.isEmpty()) lines.removeAt(lines.lastIndex)
        return lines
    }
}
