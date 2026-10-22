package app.nagmedar.ui.radio.record

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** How a scheduled recording repeats. */
enum class Repeat {
    /** Once, at [RecordingSchedule.startAt]. */
    Once,

    /** Every day at the time. */
    Daily,

    /** Monday to Friday at the time. */
    Weekdays,

    /** On the weekday of [RecordingSchedule.startAt] (kept in [RecordingSchedule.weekday]), at the time. */
    Weekly,
}

/**
 * S-1107: a recording set for later. A repeating schedule is on the wall clock, as an alarm is: at
 * [hour]:[minute] wherever the device is, in the time zone it is in when the time comes (07:00
 * stays 07:00 over a daylight saving change). A one-off [Repeat.Once] is the instant [startAt].
 * It records [durationMinutes] from the start. [lastStartedAt] is the start (ms) of the latest run
 * that began, so that a run is never made twice.
 */
data class RecordingSchedule(
    val id: Long = 0,
    val station: RecordingStation,
    val startAt: Long,
    val hour: Int,
    val minute: Int,
    val durationMinutes: Int,
    val repeat: Repeat,
    /** ISO weekday, 1 Monday … 7 Sunday: for [Repeat.Weekly]. */
    val weekday: Int = 1,
    val enabled: Boolean = true,
    val lastStartedAt: Long = 0,
    val createdAt: Long = 0,
    /** Until when the runs were looked over for ones nobody recorded (ms; 0: not yet). */
    val checkedAt: Long = 0,
) {
    companion object {
        /** The longest a scheduled recording runs, minutes. */
        const val MAX_MINUTES = 12 * 60

        /** Offered for the duration, minutes. */
        val DURATIONS = listOf(15, 30, 60, 90, 120, 180, 240)

        /**
         * A schedule that starts at [hour]:[minute] on [date] in [zone], for [durationMinutes],
         * repeating as [repeat]; its weekday is [date]'s.
         */
        fun of(
            station: RecordingStation,
            date: LocalDate,
            hour: Int,
            minute: Int,
            durationMinutes: Int,
            repeat: Repeat,
            zone: ZoneId,
            now: Long,
            id: Long = 0,
            enabled: Boolean = true,
        ): RecordingSchedule = RecordingSchedule(
            id = id,
            station = station,
            startAt = ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant().toEpochMilli(),
            hour = hour,
            minute = minute,
            durationMinutes = durationMinutes.coerceIn(1, MAX_MINUTES),
            repeat = repeat,
            weekday = date.dayOfWeek.value,
            enabled = enabled,
            createdAt = now,
        )
    }
}

/** A run of a schedule: from [start] to [end], epoch ms. */
data class RecordingWindow(val start: Long, val end: Long) {
    /** Whether the two runs share a moment. */
    fun overlaps(other: RecordingWindow): Boolean = start < other.end && other.start < end
}

/** S-1107: when scheduled recordings start and end: wall-clock times, repeats, daylight saving. */
object ScheduleMath {

    /** How far ahead a repeat is looked for: a week and a day are enough for every repeat. */
    private const val LOOKAHEAD_DAYS = 8L

    private fun start(schedule: RecordingSchedule, day: LocalDate, zone: ZoneId): Long =
        // a time that does not exist (the clocks jumped over it) is the first moment after the jump
        ZonedDateTime.of(day, LocalTime.of(schedule.hour, schedule.minute), zone).toInstant().toEpochMilli()

    private fun runsOn(schedule: RecordingSchedule, day: LocalDate): Boolean = when (schedule.repeat) {
        Repeat.Once -> false
        Repeat.Daily -> true
        Repeat.Weekdays -> day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY
        Repeat.Weekly -> day.dayOfWeek.value == schedule.weekday
    }

    private fun window(schedule: RecordingSchedule, start: Long) = RecordingWindow(start, start + schedule.durationMinutes * 60_000L)

    /** The next run that starts after [now]; null when there is none (off, or a one-off in the past). */
    fun next(schedule: RecordingSchedule, now: Long, zone: ZoneId = ZoneId.systemDefault()): RecordingWindow? {
        if (!schedule.enabled) return null
        if (schedule.repeat == Repeat.Once) return window(schedule, schedule.startAt).takeIf { schedule.startAt > now }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (offset in 0..LOOKAHEAD_DAYS) {
            val day = today.plusDays(offset)
            if (!runsOn(schedule, day)) continue
            val at = start(schedule, day, zone)
            if (at > now) return window(schedule, at)
        }
        return null
    }

    /**
     * A run that began before [now], is not over, and was not started yet: what to start at once
     * when the app finds a recording it was too late for (the alarm came late, or the app was
     * opened inside the time). Null when there is none.
     */
    fun running(schedule: RecordingSchedule, now: Long, zone: ZoneId = ZoneId.systemDefault()): RecordingWindow? {
        if (!schedule.enabled) return null
        if (schedule.repeat == Repeat.Once) {
            return window(schedule, schedule.startAt).takeIf { it.start <= now && now < it.end && schedule.lastStartedAt < it.start }
        }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // a run is at most twelve hours long: it began today or yesterday (a day more for daylight saving)
        for (offset in 0L..2L) {
            val day = today.minusDays(offset)
            if (!runsOn(schedule, day)) continue
            val w = window(schedule, start(schedule, day, zone))
            if (w.start <= now && now < w.end && schedule.lastStartedAt < w.start) return w
        }
        return null
    }

    /** The runs of [schedule] that start in [from] until [until], oldest first. */
    fun between(schedule: RecordingSchedule, from: Long, until: Long, zone: ZoneId = ZoneId.systemDefault()): List<RecordingWindow> {
        if (!schedule.enabled) return emptyList()
        if (schedule.repeat == Repeat.Once) {
            return listOf(window(schedule, schedule.startAt)).filter { it.start in from until until }
        }
        val first = Instant.ofEpochMilli(from).atZone(zone).toLocalDate().minusDays(1)
        val last = Instant.ofEpochMilli(until).atZone(zone).toLocalDate().plusDays(1)
        val runs = ArrayList<RecordingWindow>()
        var day = first
        while (!day.isAfter(last)) {
            if (runsOn(schedule, day)) {
                val at = start(schedule, day, zone)
                if (at in from until until) runs += window(schedule, at)
            }
            day = day.plusDays(1)
        }
        return runs
    }

    /**
     * The schedules (other than [schedule] itself) whose runs overlap one of its runs in the
     * coming [days] days after [now]; empty when none do.
     */
    fun conflicts(
        schedule: RecordingSchedule,
        others: List<RecordingSchedule>,
        now: Long,
        days: Int = 7,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<RecordingSchedule> {
        val until = now + days * 24L * 3_600_000L
        val mine = between(schedule, now, until, zone)
        if (mine.isEmpty()) return emptyList()
        return others.filter { it.id != schedule.id || schedule.id == 0L }.filter { other ->
            between(other, now, until, zone).any { run -> mine.any { it.overlaps(run) } }
        }
    }

    /** The most schedules that run at one moment in the coming [days] days after [now]. */
    fun peak(schedules: List<RecordingSchedule>, now: Long, days: Int = 7, zone: ZoneId = ZoneId.systemDefault()): Int {
        val until = now + days * 24L * 3_600_000L
        val runs = schedules.flatMap { between(it, now, until, zone) }
        // the most runs open at the moment one of them starts
        return runs.maxOfOrNull { a -> runs.count { b -> b.start <= a.start && a.start < b.end } } ?: 0
    }
}
