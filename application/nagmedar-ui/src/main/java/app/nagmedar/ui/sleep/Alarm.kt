package app.nagmedar.ui.sleep

import androidx.compose.runtime.Immutable
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** What the alarm plays. */
@Immutable
sealed interface AlarmSource {
    /** The music played last, where it was left. */
    data object Last : AlarmSource

    /** A playlist of the medialibrary, from its start. */
    data class Playlist(val id: Long, val name: String) : AlarmSource

    /** A stream: an internet radio's address. */
    data class Stream(val url: String) : AlarmSource
}

/**
 * S-1007: the alarm. At [hour]:[minute] on [days] (ISO: 1 Monday … 7 Sunday; none: once, the
 * next time that hour comes) it plays [source], the volume rising from nothing over
 * [rampSeconds], the device's media volume raised to [loudness] % first if it is lower.
 */
@Immutable
data class Alarm(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Set<Int> = emptySet(),
    val source: AlarmSource = AlarmSource.Last,
    val rampSeconds: Int = 60,
    val loudness: Int = 50,
) {
    /** When it rings next after [now]; null while it is off. */
    fun next(now: Long, zone: TimeZone = TimeZone.getDefault()): Long? {
        if (!enabled) return null
        val day = Calendar.getInstance(zone).apply { timeInMillis = now }
        // today, then each day of the coming week; the hour set anew each day (daylight saving)
        repeat(8) { offset ->
            if (offset > 0) day.add(Calendar.DAY_OF_YEAR, 1)
            day.set(Calendar.HOUR_OF_DAY, hour)
            day.set(Calendar.MINUTE, minute)
            day.set(Calendar.SECOND, 0)
            day.set(Calendar.MILLISECOND, 0)
            if (day.timeInMillis > now && (days.isEmpty() || isoDay(day) in days)) return day.timeInMillis
        }
        return null
    }

    /** As the settings keep it: "1;07:30;1,2,3,4,5;last;60;50". */
    fun encode(): String = listOf(
        if (enabled) "1" else "0",
        String.format(Locale.ROOT, "%02d:%02d", hour, minute),
        days.sorted().joinToString(","),
        when (source) {
            AlarmSource.Last -> "last"
            is AlarmSource.Playlist -> "playlist:${source.id}:${URLEncoder.encode(source.name, "UTF-8")}"
            is AlarmSource.Stream -> "stream:${URLEncoder.encode(source.url, "UTF-8")}"
        },
        rampSeconds.toString(),
        loudness.toString(),
    ).joinToString(";")

    companion object {
        /** Ramps offered, seconds. */
        val RAMPS = listOf(30, 60, 120, 300)

        /** The day of [calendar] the ISO way: Monday 1 … Sunday 7. */
        fun isoDay(calendar: Calendar): Int = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1

        /** The ISO [day]'s number in a [Calendar]: Sunday 1 … Saturday 7. */
        fun calendarDay(day: Int): Int = day % 7 + 1

        /** [value] a [step] up or down to the step's next multiple, round past 0 and [max]: the time's ↑ and ↓. */
        fun step(value: Int, step: Int, max: Int, up: Boolean): Int {
            val next = if (up) (value / step + 1) * step else ((value + step - 1) / step - 1) * step
            return Math.floorMod(next, max + 1)
        }

        /** How many dates on from [now]'s [at] falls: 0 today, 1 tomorrow. */
        fun daysUntil(at: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): Int {
            val day = Calendar.getInstance(zone).apply { timeInMillis = now }
            val target = Calendar.getInstance(zone).apply { timeInMillis = at }
            var days = 0
            while (days < 400 && (day.get(Calendar.YEAR) != target.get(Calendar.YEAR) || day.get(Calendar.DAY_OF_YEAR) != target.get(Calendar.DAY_OF_YEAR))) {
                day.add(Calendar.DAY_OF_YEAR, 1)
                days++
            }
            return days
        }

        /** What [encode] wrote; the default for nothing, or for what cannot be read. */
        fun decode(text: String?): Alarm {
            val parts = text?.split(';') ?: return Alarm()
            if (parts.size < 6) return Alarm()
            return runCatching {
                val (hour, minute) = parts[1].split(':').map { it.toInt() }
                val source = parts[3].let { s ->
                    when {
                        s.startsWith("playlist:") -> s.removePrefix("playlist:").split(':', limit = 2).let { AlarmSource.Playlist(it[0].toLong(), URLDecoder.decode(it[1], "UTF-8")) }
                        s.startsWith("stream:") -> AlarmSource.Stream(URLDecoder.decode(s.removePrefix("stream:"), "UTF-8"))
                        else -> AlarmSource.Last
                    }
                }
                Alarm(
                    enabled = parts[0] == "1",
                    hour = hour.coerceIn(0, 23),
                    minute = minute.coerceIn(0, 59),
                    days = parts[2].split(',').mapNotNull { it.toIntOrNull()?.takeIf { d -> d in 1..7 } }.toSet(),
                    source = source,
                    rampSeconds = parts[4].toInt().coerceIn(0, 600),
                    loudness = parts[5].toInt().coerceIn(0, 100),
                )
            }.getOrDefault(Alarm())
        }
    }
}
