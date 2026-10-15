package app.nagmedar.ui.player.subtitles.tools

import androidx.compose.runtime.Immutable
import java.util.Locale

/*
 * Subtitle files as the tools read and write them (S-708): SRT, WebVTT and ASS/SSA. A file is
 * read into cues with their times and text, edited, and written again. An ASS file keeps every
 * line it had: only the times and the text of the Dialogue lines that changed are rewritten, so
 * styles, override tags, comments and fonts stay as they were, byte for byte.
 */

/** The subtitle formats the tools write. MicroDVD and SAMI are not among them: the player's search reads neither. */
enum class SubtitleFormat(val extension: String, val mime: String, val label: String) {
    Srt("srt", "application/x-subrip", "SRT"),
    Vtt("vtt", "text/vtt", "WebVTT"),
    Ass("ass", "text/x-ssa", "ASS"),
    Ssa("ssa", "text/x-ssa", "SSA"),
    ;

    /** Styled text, with override tags in its lines: ASS and SSA. */
    val styled: Boolean get() = this == Ass || this == Ssa

    companion object {
        fun ofName(name: String): SubtitleFormat? {
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return entries.firstOrNull { it.extension == extension }
        }

        /** What [text] is by its first lines, whatever its name says; null when it is none of them. */
        fun sniff(text: String): SubtitleFormat? = when {
            text.startsWith("WEBVTT") -> Vtt
            text.contains("[Script Info]", ignoreCase = true) || text.contains("\n[Events]", ignoreCase = true) -> Ass
            TimeLines.srt.containsMatchIn(text) -> Srt
            else -> null
        }
    }
}

/**
 * One line of a subtitle file. [text] has its lines apart by "\n" and keeps its markup (italics,
 * ASS override tags); [source] is what the line was read from, so that a file written again keeps
 * what the tools do not touch.
 */
@Immutable
data class TimedCue(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val source: CueSource? = null,
) {
    val durationMs: Long get() = endMs - startMs
}

/** Where a cue came from in its file. */
sealed interface CueSource {
    /** An SRT or WebVTT cue: its identifier line (WebVTT) and what follows its times (settings, coordinates). */
    data class Plain(val identifier: String?, val tail: String) : CueSource

    /**
     * An ASS Dialogue line as it was written, with where its start, end and text are in it and
     * their values: a field that was not changed is written back as it was.
     */
    class Ass(
        val line: String,
        val startAt: Int,
        val startEnd: Int,
        val endAt: Int,
        val endEnd: Int,
        val textAt: Int,
        val startMs: Long,
        val endMs: Long,
        val text: String,
    ) : CueSource
}

/** A piece of an ASS file: lines kept as they are, or the place of a Dialogue line. */
sealed interface Piece {
    data class Verbatim(val text: String) : Piece

    /** The cue [id]'s line, and the line break that ended it ("" for a last line without one). */
    data class Slot(val id: Long, val end: String) : Piece
}

/**
 * A subtitle file read: its [cues] by start time, and what the format keeps besides them. For ASS
 * and SSA [pieces] hold the whole file in order; for WebVTT [head] holds the header and the notes
 * and styles before the first cue, [tail] those after the last.
 */
@Immutable
data class SubtitleDocument(
    val format: SubtitleFormat,
    val cues: List<TimedCue>,
    val lineBreak: String = "\n",
    /** Whether the file began with a UTF-8 byte order mark. */
    val bom: Boolean = false,
    val nextId: Long = (cues.maxOfOrNull { it.id } ?: 0L) + 1,
    val head: String = "",
    val tail: String = "",
    val pieces: List<Piece> = emptyList(),
) {
    /** The same cues with [next] for the ones changed, in start order. */
    fun withCues(next: List<TimedCue>, nextId: Long = this.nextId): SubtitleDocument = copy(cues = sortedByStart(next), nextId = nextId)

    /** A new cue's place in an ASS file: right after the line it came from. */
    fun withSlotAfter(parent: Long, id: Long): SubtitleDocument {
        if (!format.styled) return this
        val at = pieces.indexOfFirst { it is Piece.Slot && it.id == parent }
        val end = (pieces.getOrNull(at) as? Piece.Slot)?.end ?: lineBreak
        val slot = Piece.Slot(id, end)
        return copy(pieces = if (at < 0) pieces + slot else pieces.take(at + 1) + slot + pieces.drop(at + 1))
    }

    /** The file as text; the byte order mark is the caller's to add ([toBytes]). */
    fun write(): String = SubtitleCodec.write(this)

    /** The file as UTF-8 bytes, with a byte order mark when [withBom]. */
    fun toBytes(withBom: Boolean = bom): ByteArray {
        val body = write().toByteArray(Charsets.UTF_8)
        return if (withBom) byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body else body
    }

    /** An SRT as WebVTT or the other way; styled files cannot change (their styles would be lost). */
    fun asFormat(target: SubtitleFormat): SubtitleDocument? {
        if (target == format) return this
        if (format.styled || target.styled) return null
        val plain = cues.map { it.copy(source = (it.source as? CueSource.Plain)?.copy(identifier = null)) }
        return copy(format = target, cues = plain, head = if (target == SubtitleFormat.Vtt) "WEBVTT$lineBreak$lineBreak" else "", tail = "")
    }

    companion object {
        fun sortedByStart(cues: List<TimedCue>): List<TimedCue> = cues.sortedBy { it.startMs }
    }
}

/** The time lines of SRT and WebVTT, and the times of ASS. */
internal object TimeLines {
    private const val TIME = """(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})"""

    val srt = Regex("""(?m)^\s*$TIME\s*-->\s*$TIME""")

    /** A cue's times, then what follows them. */
    val line = Regex("""^\s*$TIME\s*-->\s*$TIME(.*)$""")

    private val ass = Regex("""^\s*(\d+):(\d{1,2}):(\d{1,2})[.:](\d{1,3})\s*$""")

    fun parse(hours: String?, minutes: String, seconds: String, fraction: String): Long =
        (((hours?.toLong() ?: 0L) * 60 + minutes.toLong()) * 60 + seconds.toLong()) * 1000 + fraction.padEnd(3, '0').toLong()

    fun parseAss(text: String): Long? {
        val match = ass.matchEntire(text) ?: return null
        val (h, m, s, fraction) = match.destructured
        // hundredths, as ASS writes them; a longer fraction is milliseconds
        val millis = when (fraction.length) {
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + millis
    }
}

/** Times as the formats write them. */
object Clock {
    /** "00:01:02,345" (SRT), or with [separator] "." (WebVTT). */
    fun format(ms: Long, separator: Char = ','): String {
        val t = ms.coerceAtLeast(0)
        return "%02d:%02d:%02d%c%03d".format(Locale.ROOT, t / 3_600_000, t / 60_000 % 60, t / 1000 % 60, separator, t % 1000)
    }

    /** "0:01:02.35" (ASS), in hundredths of a second, rounded. */
    fun formatAss(ms: Long): String {
        val t = (ms.coerceAtLeast(0) + 5) / 10
        return "%d:%02d:%02d.%02d".format(Locale.ROOT, t / 360_000, t / 6_000 % 60, t / 100 % 60, t % 100)
    }

    /** "0:01:02,345": a time in an editor's field, the decimal sign as the interface's language has it. */
    fun field(ms: Long, separator: Char): String {
        val t = ms.coerceAtLeast(0)
        return "%d:%02d:%02d%c%03d".format(Locale.ROOT, t / 3_600_000, t / 60_000 % 60, t / 1000 % 60, separator, t % 1000)
    }

    private val typed = Regex("""^\s*(?:(?:(\d+):)?(\d{1,2}):)?(\d+)(?:[,.](\d{1,3}))?\s*$""")

    /**
     * A time typed in a field: "1:02:03,5", "62:03.250", "3723,5", with a comma or a dot; null
     * when it is none of those.
     */
    fun parse(text: String): Long? {
        val match = typed.matchEntire(text) ?: return null
        val (hours, minutes, seconds, fraction) = match.destructured
        val value = seconds.toLongOrNull() ?: return null
        val total = ((hours.ifEmpty { "0" }.toLong() * 60 + minutes.ifEmpty { "0" }.toLong()) * 60 + value) * 1000 + fraction.padEnd(3, '0').toLong()
        return total.takeIf { it >= 0 }
    }
}

/** Reads and writes subtitle files as [SubtitleDocument]s. */
object SubtitleCodec {

    /**
     * The cues of [text], a file named [name]; the format is what the text is, whatever the name
     * says. Null when the text is none of the formats.
     */
    fun parse(name: String, text: String): SubtitleDocument? {
        val bom = text.startsWith("﻿")
        val body = text.removePrefix("﻿")
        val named = SubtitleFormat.ofName(name)
        var format = SubtitleFormat.sniff(body) ?: named ?: return null
        if (format == SubtitleFormat.Ass && named == SubtitleFormat.Ssa) format = SubtitleFormat.Ssa
        val lineBreak = if (body.contains("\r\n")) "\r\n" else "\n"
        return if (format.styled) parseAss(format, body, lineBreak, bom) else parseBlocks(format, body, lineBreak, bom)
    }

    // ---------------------------------------------------------------- SRT and WebVTT

    private val blockBreak = Regex("""(?:\r?\n){2,}""")

    private val blankLine = Regex("""(?m)^[ \t]+$""")

    private fun parseBlocks(format: SubtitleFormat, text: String, lineBreak: String, bom: Boolean): SubtitleDocument {
        // a line of spaces is a blank line
        val body = text.replace(blankLine, "")
        val cues = mutableListOf<TimedCue>()
        val head = StringBuilder()
        val tail = StringBuilder()
        var id = 1L
        var from = 0
        val blocks = mutableListOf<Pair<String, String>>()
        for (gap in blockBreak.findAll(body)) {
            blocks += body.substring(from, gap.range.first) to gap.value
            from = gap.range.last + 1
        }
        if (from < body.length) blocks += body.substring(from) to ""
        for ((block, separator) in blocks) {
            val timed = parseCueBlock(block.lines().map { it.trimEnd('\r') })
            if (timed == null) {
                // the header, a note, a style: kept before the first cue, else after the last
                (if (cues.isEmpty()) head else tail).append(block).append(separator.ifEmpty { lineBreak })
            } else {
                for (item in timed) cues += TimedCue(id++, item.start, item.end, item.text, item.source)
            }
        }
        val vtt = format == SubtitleFormat.Vtt
        return SubtitleDocument(
            format = format,
            cues = SubtitleDocument.sortedByStart(cues),
            lineBreak = lineBreak,
            bom = bom,
            nextId = id,
            head = if (vtt) head.toString() else "",
            tail = if (vtt) tail.toString() else "",
        )
    }

    private data class Timed(val start: Long, val end: Long, val source: CueSource.Plain, val text: String)

    /**
     * The cues of one block of lines: normally one, with an identifier or a number before its
     * times; null when the block has no times. Cues that run together without a blank line between
     * them are told apart by their times, and the number before each one is dropped.
     */
    private fun parseCueBlock(lines: List<String>): List<Timed>? {
        val times = lines.indices.filter { TimeLines.line.matches(lines[it]) }
        // an identifier or a number may come before the first times, nothing else
        if (times.isEmpty() || times.first() > 1) return null
        return times.mapIndexed { n, at ->
            val g = TimeLines.line.matchEntire(lines[at])!!.groupValues
            val stop = times.getOrNull(n + 1) ?: lines.size
            val text = lines.subList(at + 1, stop).dropLastWhile { it.isBlank() }.toMutableList()
            if (n + 1 < times.size && text.lastOrNull()?.trim()?.let { it.isNotEmpty() && it.all(Char::isDigit) } == true) text.removeAt(text.lastIndex)
            Timed(
                start = TimeLines.parse(g[1].ifEmpty { null }, g[2], g[3], g[4]),
                end = TimeLines.parse(g[5].ifEmpty { null }, g[6], g[7], g[8]),
                source = CueSource.Plain(if (n == 0 && at == 1) lines[0] else null, g[9].trim()),
                text = text.joinToString("\n") { it.trimEnd() },
            )
        }
    }

    // ---------------------------------------------------------------- ASS and SSA

    private val defaultColumns = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")

    private fun parseAss(format: SubtitleFormat, body: String, lineBreak: String, bom: Boolean): SubtitleDocument {
        val pieces = mutableListOf<Piece>()
        val cues = mutableListOf<TimedCue>()
        val verbatim = StringBuilder()
        fun flush() {
            if (verbatim.isNotEmpty()) pieces += Piece.Verbatim(verbatim.toString())
            verbatim.setLength(0)
        }
        var inEvents = false
        var columns = defaultColumns
        var id = 1L
        var position = 0
        while (position < body.length) {
            val newline = body.indexOf('\n', position)
            val stop = if (newline < 0) body.length else newline
            val lineEnd = if (stop > position && body[stop - 1] == '\r') stop - 1 else stop
            val line = body.substring(position, lineEnd)
            val end = body.substring(lineEnd, if (newline < 0) body.length else newline + 1)
            position = if (newline < 0) body.length else newline + 1
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) inEvents = trimmed.equals("[Events]", ignoreCase = true)
            if (inEvents && trimmed.startsWith("Format:", ignoreCase = true)) {
                columns = trimmed.substringAfter(':').split(',').map { it.trim().lowercase(Locale.ROOT) }
            }
            val cue = if (inEvents && line.startsWith("Dialogue:", ignoreCase = true)) dialogue(line, columns, id) else null
            if (cue == null) {
                verbatim.append(line).append(end)
            } else {
                flush()
                pieces += Piece.Slot(id, end)
                cues += cue
                id++
            }
        }
        flush()
        return SubtitleDocument(
            format = format,
            cues = SubtitleDocument.sortedByStart(cues),
            lineBreak = lineBreak,
            bom = bom,
            nextId = id,
            pieces = pieces,
        )
    }

    /** The cue of a Dialogue [line] whose fields are [columns]; null when it is not a well-formed one. */
    private fun dialogue(line: String, columns: List<String>, id: Long): TimedCue? {
        val startColumn = columns.indexOf("start")
        val endColumn = columns.indexOf("end")
        val textColumn = columns.indexOf("text")
        if (startColumn < 0 || endColumn < 0 || textColumn < 0) return null
        // the fields start after "Dialogue:" and are apart by commas; the last one, the text, may hold commas
        val starts = IntArray(textColumn + 1)
        val stops = IntArray(textColumn + 1)
        var from = line.indexOf(':') + 1
        for (index in 0..textColumn) {
            starts[index] = from
            if (index == textColumn) {
                stops[index] = line.length
            } else {
                val comma = line.indexOf(',', from)
                if (comma < 0) return null
                stops[index] = comma
                from = comma + 1
            }
        }
        fun trimmedRange(index: Int): IntRange {
            var a = starts[index]
            var b = stops[index]
            while (a < b && line[a] == ' ') a++
            while (b > a && line[b - 1] == ' ') b--
            return a until b
        }
        val startRange = trimmedRange(startColumn)
        val endRange = trimmedRange(endColumn)
        val start = TimeLines.parseAss(line.substring(startRange.first, startRange.last + 1)) ?: return null
        val end = TimeLines.parseAss(line.substring(endRange.first, endRange.last + 1)) ?: return null
        val textAt = starts[textColumn]
        val text = line.substring(textAt).replace("\\N", "\n")
        val source = CueSource.Ass(line, startRange.first, startRange.last + 1, endRange.first, endRange.last + 1, textAt, start, end, text)
        return TimedCue(id, start, end, text, source)
    }

    // ---------------------------------------------------------------- writing

    fun write(document: SubtitleDocument): String = when {
        document.format.styled -> writeAss(document)
        document.format == SubtitleFormat.Vtt -> writeBlocks(document, vtt = true)
        else -> writeBlocks(document, vtt = false)
    }

    private fun writeBlocks(document: SubtitleDocument, vtt: Boolean): String {
        val lb = document.lineBreak
        val out = StringBuilder()
        if (vtt) out.append(document.head.ifEmpty { "WEBVTT$lb$lb" })
        SubtitleDocument.sortedByStart(document.cues).forEachIndexed { index, cue ->
            val source = cue.source as? CueSource.Plain
            if (vtt) {
                source?.identifier?.let { out.append(it).append(lb) }
            } else {
                out.append(index + 1).append(lb)
            }
            val separator = if (vtt) '.' else ','
            out.append(Clock.format(cue.startMs, separator)).append(" --> ").append(Clock.format(cue.endMs, separator))
            source?.tail?.takeIf { it.isNotEmpty() }?.let { out.append(' ').append(it.trim()) }
            out.append(lb)
            out.append(cue.text.replace("\n", lb)).append(lb).append(lb)
        }
        if (vtt) out.append(document.tail)
        return out.toString()
    }

    private fun writeAss(document: SubtitleDocument): String {
        val byId = document.cues.associateBy { it.id }
        val out = StringBuilder()
        val written = HashSet<Long>()
        var needBreak = false
        fun append(text: String) {
            if (needBreak) out.append(document.lineBreak)
            needBreak = false
            out.append(text)
        }
        for (piece in document.pieces) {
            when (piece) {
                is Piece.Verbatim -> append(piece.text)
                is Piece.Slot -> {
                    val cue = byId[piece.id] ?: continue
                    written += cue.id
                    append(assLine(cue))
                    out.append(piece.end)
                    needBreak = piece.end.isEmpty()
                }
            }
        }
        // a cue with no place of its own goes last
        for (cue in document.cues) if (cue.id !in written) {
            append(assLine(cue))
            out.append(document.lineBreak)
        }
        return out.toString()
    }

    /** A Dialogue line of [cue]: the line it was read from with its times and text where they changed. */
    private fun assLine(cue: TimedCue): String {
        val source = cue.source as? CueSource.Ass ?: return defaultDialogue(cue)
        val line = source.line
        val start = if (cue.startMs == source.startMs) line.substring(source.startAt, source.startEnd) else Clock.formatAss(cue.startMs)
        val end = if (cue.endMs == source.endMs) line.substring(source.endAt, source.endEnd) else Clock.formatAss(cue.endMs)
        val text = if (cue.text == source.text) line.substring(source.textAt) else cue.text.replace("\n", "\\N")
        return line.substring(0, source.startAt) + start + line.substring(source.startEnd, source.endAt) + end +
            line.substring(source.endEnd, source.textAt) + text
    }

    private fun defaultDialogue(cue: TimedCue): String =
        "Dialogue: 0," + Clock.formatAss(cue.startMs) + "," + Clock.formatAss(cue.endMs) + ",Default,,0,0,0,," + cue.text.replace("\n", "\\N")
}
