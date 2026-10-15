package app.nagmedar.ui.player.subtitles.tools

import androidx.compose.runtime.Immutable

/** What is wrong with a cue's times or text (S-708). */
enum class CueIssue(val error: Boolean) {
    /** It ends at or before its start. */
    EndNotAfterStart(true),

    /** It starts before the film does. */
    NegativeStart(true),

    /** It starts before the cue before it has ended (or the next starts before it ends). */
    Overlap(false),

    /** It says nothing. */
    Empty(false),
}

/** The issues of a document's cues, by the cue's id; cues with none are not in it. */
@Immutable
class CueIssues(private val byId: Map<Long, Set<CueIssue>>) {
    operator fun get(id: Long): Set<CueIssue> = byId[id].orEmpty()

    val errors: Int get() = byId.values.count { issues -> issues.any { it.error } }

    val overlaps: Int get() = byId.values.count { CueIssue.Overlap in it }

    val empty: Int get() = byId.values.count { CueIssue.Empty in it }

    /** Whether the file can be saved: no cue ends before it starts or starts before the film. */
    val saveable: Boolean get() = errors == 0

    val none: Boolean get() = byId.isEmpty()

    /** The id of the first cue after [after] (in [order]) with an issue, wrapping round; null when none has. */
    fun next(order: List<Long>, after: Long?): Long? {
        if (order.isEmpty()) return null
        val from = after?.let(order::indexOf)?.plus(1) ?: 0
        return (order.drop(from) + order.take(from)).firstOrNull { it in byId }
    }
}

/** The result of an edit: the document, and the cue to look at next. */
@Immutable
data class Edit(val document: SubtitleDocument, val focus: Long?)

/**
 * The line timing editor's operations (S-708), each one a new document from the old: times and
 * text of a cue, a shift of several, a split and a merge, and the check of times.
 */
object CueOps {

    /** A cue starts at [startMs]; with [keepLength], a start past the end carries the end along, the cue as long as it was. */
    fun setStart(document: SubtitleDocument, id: Long, startMs: Long, keepLength: Boolean = true): SubtitleDocument = change(document, id) { cue ->
        if (keepLength && startMs >= cue.endMs) cue.copy(startMs = startMs, endMs = startMs + cue.durationMs.coerceAtLeast(MIN_LENGTH_MS)) else cue.copy(startMs = startMs)
    }

    fun setEnd(document: SubtitleDocument, id: Long, endMs: Long): SubtitleDocument = change(document, id) { it.copy(endMs = endMs) }

    fun setText(document: SubtitleDocument, id: Long, text: String): SubtitleDocument = change(document, id) { it.copy(text = text) }

    /** The cues [ids] [deltaMs] later (earlier when negative); none goes before zero. */
    fun shift(document: SubtitleDocument, ids: Set<Long>, deltaMs: Long): SubtitleDocument {
        val earliest = document.cues.filter { it.id in ids }.minOfOrNull { it.startMs } ?: return document
        // a shift that would take a cue before zero stops at the first one reaching it
        val delta = maxOf(deltaMs, -earliest)
        return document.withCues(document.cues.map { if (it.id in ids) it.copy(startMs = it.startMs + delta, endMs = it.endMs + delta) else it })
    }

    fun delete(document: SubtitleDocument, ids: Set<Long>): SubtitleDocument = document.withCues(document.cues.filterNot { it.id in ids })

    /**
     * [id] split in two at [atMs] (the middle of the cue when null): the first ends where the
     * second starts. A cue of two lines or more splits between its lines; one line splits at the
     * space nearest to the same share of its length. Null when [atMs] is not inside the cue.
     */
    fun split(document: SubtitleDocument, id: Long, atMs: Long? = null): Edit? {
        val cue = document.cues.firstOrNull { it.id == id } ?: return null
        val at = atMs ?: (cue.startMs + cue.durationMs / 2)
        if (at <= cue.startMs || at >= cue.endMs) return null
        val (first, second) = splitText(cue.text, (at - cue.startMs).toDouble() / cue.durationMs)
        val (a, b) = Markup.balance(first, second)
        val newId = document.nextId
        val head = cue.copy(endMs = at, text = a)
        val tail = cue.copy(id = newId, startMs = at, text = b, source = (cue.source as? CueSource.Plain)?.copy(identifier = null) ?: cue.source)
        val next = document.copy(cues = SubtitleDocument.sortedByStart(document.cues.map { if (it.id == id) head else it } + tail), nextId = newId + 1)
        return Edit(next.withSlotAfter(id, newId), newId)
    }

    private fun splitText(text: String, share: Double): Pair<String, String> {
        val lines = text.split('\n')
        if (lines.size > 1) {
            val k = (lines.size / 2).coerceAtLeast(1)
            return lines.take(k).joinToString("\n") to lines.drop(k).joinToString("\n")
        }
        // the spaces between words, not those inside a tag; the share counts what is said, not the tags
        val inTag = BooleanArray(text.length)
        var depth = 0
        for (index in text.indices) {
            when (text[index]) {
                '<' -> if (text.getOrNull(index + 1)?.let { it.isLetter() || it == '/' } == true) depth++
                '{' -> depth++
                '>', '}' -> if (depth > 0) depth--
            }
            inTag[index] = depth > 0 || text[index] == '>' || text[index] == '}'
        }
        val said = text.indices.count { !inTag[it] }
        val spaces = text.indices.filter { text[it] == ' ' && !inTag[it] }
        if (spaces.isEmpty()) return text to ""
        val target = said * share
        val at = spaces.minBy { kotlin.math.abs(text.indices.take(it).count { i -> !inTag[i] } - target) }
        return text.substring(0, at).trimEnd() to text.substring(at + 1).trimStart()
    }

    /**
     * The cues [ids] as one, from the first start to the last end, their lines one under the
     * other; a lone cue joins the one after it. Null when there is nothing to join.
     */
    fun merge(document: SubtitleDocument, ids: Set<Long>): Edit? {
        val picked = document.cues.filter { it.id in ids }
        val group = when {
            picked.size > 1 -> picked
            picked.size == 1 -> {
                val index = document.cues.indexOf(picked[0])
                listOfNotNull(picked[0], document.cues.getOrNull(index + 1))
            }
            else -> emptyList()
        }
        if (group.size < 2) return null
        val first = group.first()
        val merged = first.copy(
            startMs = group.minOf { it.startMs },
            endMs = group.maxOf { it.endMs },
            text = group.map { it.text }.filter { it.isNotBlank() }.joinToString("\n"),
        )
        val gone = group.drop(1).map { it.id }.toSet()
        val next = document.withCues(document.cues.filterNot { it.id in gone }.map { if (it.id == first.id) merged else it })
        return Edit(next, first.id)
    }

    private fun change(document: SubtitleDocument, id: Long, edit: (TimedCue) -> TimedCue): SubtitleDocument =
        document.withCues(document.cues.map { if (it.id == id) edit(it) else it })

    /** The issues of [cues], the cues in start order. */
    fun validate(cues: List<TimedCue>): CueIssues {
        val issues = HashMap<Long, MutableSet<CueIssue>>()
        fun add(id: Long, issue: CueIssue) {
            issues.getOrPut(id) { LinkedHashSet() } += issue
        }
        var reaching: TimedCue? = null
        for (cue in cues.sortedBy { it.startMs }) {
            if (cue.endMs <= cue.startMs) add(cue.id, CueIssue.EndNotAfterStart)
            if (cue.startMs < 0) add(cue.id, CueIssue.NegativeStart)
            if (SdhCleaner.visible(cue.text).isBlank()) add(cue.id, CueIssue.Empty)
            val before = reaching
            if (before != null && cue.startMs < before.endMs) {
                add(cue.id, CueIssue.Overlap)
                add(before.id, CueIssue.Overlap)
            }
            if (before == null || cue.endMs > before.endMs) reaching = cue
        }
        return CueIssues(issues)
    }

    /** A cue's shortest length when its start passes its end: a second. */
    private const val MIN_LENGTH_MS = 1_000L
}

/** Italic, bold and underline tags across a split. */
internal object Markup {
    private val tags = listOf("i", "b", "u")

    /** [first] and [second], from one text cut in two, each with its tags closed and opened again. */
    fun balance(first: String, second: String): Pair<String, String> {
        var a = first
        var b = second
        for (tag in tags) {
            val open = Regex("<$tag>", RegexOption.IGNORE_CASE).findAll(a).count()
            val close = Regex("</$tag>", RegexOption.IGNORE_CASE).findAll(a).count()
            if (open > close) {
                a += "</$tag>"
                b = "<$tag>$b"
            }
        }
        return a to b
    }
}
