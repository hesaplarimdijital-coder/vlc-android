package app.nagmedar.ui.podcast

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/** S-1101: the dates and lengths feeds write, in all the ways they write them. */
object FeedDates {
    private val RFC822 = Regex(
        "^(?:[A-Za-z]{3,9}\\.?,?\\s+)?(\\d{1,2})[\\s-]+([A-Za-z]{3,9})\\.?[\\s-]+(\\d{2,4})" +
            "(?:\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:[.,]\\d+)?)?\\s*(.*)$",
    )
    private val ISO = Regex(
        "^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[Tt\\s]+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:[.,]\\d+)?)?\\s*(.*)$",
    )
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val ZONE_NAMES = mapOf(
        "z" to 0, "gmt" to 0, "ut" to 0, "utc" to 0, "est" to -5, "edt" to -4, "cst" to -6, "cdt" to -5,
        "mst" to -7, "mdt" to -6, "pst" to -8, "pdt" to -7, "cet" to 1, "cest" to 2, "bst" to 1, "eet" to 2, "eest" to 3, "msk" to 3,
    )
    private val OFFSET = Regex("^([+-])(\\d{2})(?::?(\\d{2}))?")
    private val SECONDS = Regex("^\\d{9,13}$")

    /**
     * [text] as epoch milliseconds: RFC 822 and RFC 2822 with or without the weekday, seconds or zone
     * ("Sat, 10 Oct 2026 09:00:00 +0300", "10 Oct 26 09:00 GMT"), ISO 8601 ("2026-10-10T09:00:00+03:00",
     * a date alone) and Unix seconds. Null when it is none of them: a feed's bad date costs the
     * episode its place in time, never the feed.
     */
    fun parse(text: String?): Long? {
        val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (SECONDS.matches(value)) {
            val number = value.toLong()
            return (if (value.length >= 12) number else number * 1000).takeIf { it > 0 }
        }
        ISO.matchEntire(value)?.let { m ->
            return build(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4], m.groupValues[5], m.groupValues[6], m.groupValues[7])
        }
        RFC822.matchEntire(value)?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[2].take(3).lowercase(Locale.ROOT)) + 1
            if (month == 0) return null
            val year = m.groupValues[3].toInt().let { if (m.groupValues[3].length <= 2) (if (it < 50) 2000 + it else 1900 + it) else it }
            return build(year, month, m.groupValues[1].toInt(), m.groupValues[4], m.groupValues[5], m.groupValues[6], m.groupValues[7])
        }
        return null
    }

    private fun build(year: Int, month: Int, day: Int, hour: String, minute: String, second: String, zone: String): Long? {
        if (year !in 1971..2200) return null
        return try {
            val time = LocalDateTime.of(year, month, day, hour.toIntOrNull() ?: 0, minute.toIntOrNull() ?: 0, (second.toIntOrNull() ?: 0).coerceAtMost(59))
            (time.toEpochSecond(ZoneOffset.ofTotalSeconds(offsetSeconds(zone))) * 1000).takeIf { it > 0 }
        } catch (e: DateTimeException) {
            null
        }
    }

    /** The zone as seconds east of UTC: "+0300", "-05:00", "EST", "GMT", "+0000 (UTC)"; UTC when it is none of them. */
    private fun offsetSeconds(zone: String): Int {
        val text = zone.trim()
        OFFSET.find(text)?.let { m ->
            val minutes = m.groupValues[2].toInt() * 60 + (m.groupValues[3].toIntOrNull() ?: 0)
            return (if (m.groupValues[1] == "-") -minutes else minutes).coerceIn(-18 * 60, 18 * 60) * 60
        }
        val name = text.takeWhile { it.isLetter() }.lowercase(Locale.ROOT)
        return (ZONE_NAMES[name] ?: 0) * 3600
    }

    /**
     * An `itunes:duration` in milliseconds: seconds ("3723", "3723.5"), "62:03", "1:02:03", with a
     * fraction ("00:01:00.500"). 0 for none and for what is not a length.
     */
    fun duration(text: String?): Long {
        val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return 0
        val parts = value.split(':')
        if (parts.size > 3) return 0
        var seconds = 0.0
        for (part in parts) {
            val number = part.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() } ?: return 0
            seconds = seconds * 60 + number
        }
        return (seconds * 1000).toLong().takeIf { it <= MAX_DURATION_MS } ?: 0
    }

    /** More than 100 hours is no episode's length. */
    private const val MAX_DURATION_MS = 100L * 3600 * 1000
}
