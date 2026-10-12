package app.nagmedar.ui.player.navigate

/** Where "Go to time" goes (S-608): a time in the film, or why not. */
sealed interface JumpTarget {
    data class To(val ms: Long) : JumpTarget

    /** Past the film's end: it lasts [lengthMs]. */
    data class PastEnd(val lengthMs: Long) : JumpTarget

    /** Nothing a time can be read from. */
    data object Unreadable : JumpTarget
}

/**
 * Reads what was typed in "Go to time" (S-608), as a film's times are written:
 * - "1:12:33", "72:33", "1:12:33,5": hours, minutes and seconds;
 * - "45", "45 sn", "12 dk", "1 sa 12 dk", "1h12m", "90s": a number alone is seconds;
 * - "%50" or "50%": a share of the film;
 * - a "+" or "−" before any of them: from now, forwards or back.
 * Before the start is the start; past the end is said ([JumpTarget.PastEnd]) unless the length is
 * unknown (a stream).
 */
fun parseJumpTime(text: String, nowMs: Long, lengthMs: Long): JumpTarget {
    var rest = text.trim().replace('−', '-')
    if (rest.isEmpty()) return JumpTarget.Unreadable
    val sign = when (rest.first()) {
        '+' -> 1
        '-' -> -1
        else -> 0
    }
    if (sign != 0) rest = rest.drop(1).trim()
    val amount = percent(rest, lengthMs) ?: clock(rest) ?: units(rest) ?: return JumpTarget.Unreadable
    val target = if (sign == 0) amount else nowMs + sign * amount
    return when {
        lengthMs in 1 until target -> JumpTarget.PastEnd(lengthMs)
        else -> JumpTarget.To(target.coerceAtLeast(0L))
    }
}

/** "%50", "50%", "50 %": that share of the film; null when it is not one or the length is unknown. */
private fun percent(text: String, lengthMs: Long): Long? {
    val number = when {
        text.startsWith('%') -> text.drop(1)
        text.endsWith('%') -> text.dropLast(1)
        else -> return null
    }.trim()
    val share = decimal(number) ?: return null
    if (lengthMs <= 0 || share < 0 || share > 100) return null
    return (lengthMs * share / 100).toLong()
}

/** "1:12:33", "72:33", "0:45,5": the last part seconds, the one before minutes, then hours. */
private fun clock(text: String): Long? {
    if (':' !in text) return null
    val parts = text.split(':').map { it.trim() }
    if (parts.size > 3 || parts.any { it.isEmpty() }) return null
    val seconds = decimal(parts.last()) ?: return null
    val whole = parts.dropLast(1).map { part -> part.toLongOrNull()?.takeIf { it >= 0 && part.all(Char::isDigit) } ?: return null }
    // below the first part, minutes and seconds stay under sixty
    if (seconds >= 60) return null
    if (whole.size == 2 && whole[1] >= 60) return null
    val minutes = whole.last()
    val hours = if (whole.size == 2) whole[0] else 0
    return ((hours * 3600 + minutes * 60) * 1000.0 + seconds * 1000).toLong()
}

/** "45", "45 sn", "1 sa 12 dk", "1h12m33s", "1,5 dk": numbers with their units, seconds without one. */
private fun units(text: String): Long? {
    val tokens = UNIT.findAll(text.lowercase()).toList()
    if (tokens.isEmpty()) return null
    // everything typed is read: nothing is left between the numbers and their units
    if (tokens.joinToString("") { it.value }.filterNot(Char::isWhitespace) != text.lowercase().filterNot(Char::isWhitespace)) return null
    if (tokens.size > 1 && tokens.any { it.groupValues[2].isEmpty() }) return null
    var total = 0.0
    for (token in tokens) {
        val number = decimal(token.groupValues[1]) ?: return null
        val unit = when (token.groupValues[2]) {
            "", "s", "sn", "sec", "saniye" -> 1.0
            "m", "dk", "min", "dakika" -> 60.0
            "h", "sa", "saat" -> 3600.0
            else -> return null
        }
        total += number * unit
    }
    return (total * 1000).toLong()
}

private val UNIT = Regex("""(\d+(?:[.,]\d+)?)\s*(saniye|dakika|saat|sec|min|sn|dk|sa|s|m|h)?""")

/** "33", "33,5", "33.5". */
private fun decimal(text: String): Double? =
    text.replace(',', '.').takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() || c == '.' } && it.count { c -> c == '.' } <= 1 }?.toDoubleOrNull()
