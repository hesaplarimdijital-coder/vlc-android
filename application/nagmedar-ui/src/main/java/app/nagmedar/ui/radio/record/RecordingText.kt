package app.nagmedar.ui.radio.record

import android.content.res.Resources
import app.nagmedar.ui.R
import app.nagmedar.ui.radio.Station
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The station as a recording knows it. */
fun Station.toRecording(): RecordingStation = RecordingStation(name = name, streamUrl = streamUrl, uuid = uuid, logoUrl = logoUrl)

/** The station of a recording as the radio screens show one (its logo). */
internal fun RecordingStation.asStation(): Station = Station(uuid = uuid, name = name, streamUrl = streamUrl, logoUrl = logoUrl)

/** S-207: the words the Recordings tab puts on days, times, schedules and notices. */
internal object RecordingText {

    private fun format(pattern: String, at: Long, zone: ZoneId, locale: Locale): String =
        SimpleDateFormat(pattern, locale).apply { timeZone = TimeZone.getTimeZone(zone) }.format(Date(at))

    /** "14:02". */
    fun time(at: Long, zone: ZoneId): String = format("HH:mm", at, zone, Locale.ROOT)

    /** "07:30–08:30" for a start and a length in minutes. */
    fun range(hour: Int, minute: Int, minutes: Int): String {
        val end = hour * 60 + minute + minutes
        return String.format(Locale.ROOT, "%02d:%02d–%02d:%02d", hour, minute, (end / 60) % 24, end % 60)
    }

    /** "1 h 30 min". */
    fun length(res: Resources, minutes: Int): String = when {
        minutes < 60 -> res.getString(R.string.nag_duration_minutes, minutes)
        minutes % 60 == 0 -> res.getString(R.string.nag_duration_hours, minutes / 60)
        else -> res.getString(R.string.nag_duration_hours_minutes, minutes / 60, minutes % 60)
    }

    /**
     * "4.2 MB", "10 GB": [bytes] counted in 1024s, as the settings count them (the system's own
     * formatter counts in 1000s, so 10 GB would read 11 GB).
     */
    fun size(bytes: Long, locale: Locale): String {
        if (bytes < 1024) return "${bytes.coerceAtLeast(0)} B"
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < UNITS.size - 1) {
            value /= 1024
            unit++
        }
        val tenths = Math.round(value * 10) / 10.0
        val number = if (value >= 10 || tenths == Math.floor(tenths)) String.format(locale, "%d", Math.round(value)) else String.format(locale, "%.1f", tenths)
        return "$number ${UNITS[unit]}"
    }

    private val UNITS = listOf("KB", "MB", "GB", "TB")

    /** How long a recording has gone on: "40 s", "23 min", "1 h 05 min". */
    fun elapsed(res: Resources, millis: Long): String {
        val seconds = (millis / 1000).coerceAtLeast(0)
        return if (seconds < 60) res.getString(R.string.nag_duration_seconds, seconds.toInt()) else length(res, (seconds / 60).toInt())
    }

    private fun daysBetween(from: Long, to: Long, zone: ZoneId): Long {
        val a = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val b = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        return b.toEpochDay() - a.toEpochDay()
    }

    /** "Today 14:02", "Yesterday 21:30", "12 October 08:00": when a recording began. */
    fun began(res: Resources, at: Long, now: Long, zone: ZoneId, locale: Locale): String {
        val day = when (daysBetween(at, now, zone)) {
            0L -> res.getString(R.string.nag_radio_rec_today)
            1L -> res.getString(R.string.nag_radio_rec_yesterday)
            else -> format("d MMMM", at, zone, locale)
        }
        return "$day ${time(at, zone)}"
    }

    /** "tomorrow", "Wednesday", "21 October": the day of a coming run, to follow "Next:". */
    private fun comingDay(res: Resources, at: Long, now: Long, zone: ZoneId, locale: Locale): String = when (val days = daysBetween(now, at, zone)) {
        0L -> res.getString(R.string.nag_alarm_today)
        1L -> res.getString(R.string.nag_alarm_tomorrow)
        else -> if (days < 7) format("EEEE", at, zone, locale) else format("d MMMM", at, zone, locale)
    }

    /** "Weekdays", "Every day", "Once", "Every Monday". */
    fun repeat(res: Resources, schedule: RecordingSchedule, locale: Locale): String = when (schedule.repeat) {
        Repeat.Once -> res.getString(R.string.nag_radio_rec_repeat_once)
        Repeat.Daily -> res.getString(R.string.nag_radio_rec_repeat_daily)
        Repeat.Weekdays -> res.getString(R.string.nag_radio_rec_repeat_weekdays)
        Repeat.Weekly -> res.getString(R.string.nag_radio_rec_repeat_weekly, weekday(schedule.weekday, locale))
    }

    /** The name of ISO weekday [day] (1 Monday). */
    fun weekday(day: Int, locale: Locale): String =
        java.time.DayOfWeek.of(day.coerceIn(1, 7)).getDisplayName(TextStyle.FULL_STANDALONE, locale)

    /** When a schedule runs next: "Next: tomorrow 07:30", or why it does not ("Off", "Done", "Recording now"). */
    fun next(res: Resources, row: ScheduleRow, now: Long, zone: ZoneId, locale: Locale): String {
        val schedule = row.schedule
        return when {
            row.running -> res.getString(R.string.nag_radio_rec_sched_running)
            !schedule.enabled -> res.getString(R.string.nag_radio_rec_sched_off)
            row.next == null -> res.getString(R.string.nag_radio_rec_sched_done)
            else -> res.getString(R.string.nag_alarm_next, comingDay(res, row.next.start, now, zone, locale), time(row.next.start, zone))
        }
    }

    /** What the one-line word on a notice says. */
    fun notice(res: Resources, notice: RecordingsNotice): String = when (notice) {
        is RecordingsNotice.Deleted ->
            if (notice.name != null && notice.count == 1) res.getString(R.string.nag_radio_rec_deleted_one, notice.name)
            else res.getQuantityString(R.plurals.nag_radio_rec_deleted, notice.count, notice.count)
        is RecordingsNotice.PartialDeleted -> res.getQuantityString(R.plurals.nag_radio_rec_partial_deleted, notice.count, notice.count)
        is RecordingsNotice.Renamed -> res.getString(R.string.nag_radio_rec_renamed, notice.name)
        RecordingsNotice.RenameFailed -> res.getString(R.string.nag_radio_rec_rename_failed)
        is RecordingsNotice.ScheduleDeleted -> res.getString(R.string.nag_radio_rec_sched_deleted, notice.schedule.station.name)
        is RecordingsNotice.Started -> res.getString(R.string.nag_radio_rec_started, notice.station)
        RecordingsNotice.NothingPartial -> res.getString(R.string.nag_radio_rec_nothing_partial)
        is RecordingsNotice.Gone -> res.getQuantityString(R.plurals.nag_radio_rec_gone, notice.count, notice.count)
        RecordingsNotice.Failed -> res.getString(R.string.nag_radio_failed)
        is RecordingsNotice.Recording -> when (val n = notice.notice) {
            is RecordingNotice.Full -> res.getString(R.string.nag_radio_rec_notice_full, n.station, n.maxParallel)
            is RecordingNotice.Merged -> res.getString(R.string.nag_radio_rec_notice_merged, n.station)
            is RecordingNotice.CannotStart -> res.getString(R.string.nag_radio_rec_notice_cannot, n.station)
        }
    }

    /** Whether a notice can be taken back (it comes with an undo). */
    fun undoable(notice: RecordingsNotice): Boolean =
        notice is RecordingsNotice.Deleted || notice is RecordingsNotice.PartialDeleted || notice is RecordingsNotice.ScheduleDeleted
}
