package app.nagmedar.ui.radio.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** S-1107: when scheduled recordings run: wall-clock times, repeats, time zones and daylight saving. */
class ScheduleMathTest {

    private val istanbul = ZoneId.of("Europe/Istanbul")
    private val berlin = ZoneId.of("Europe/Berlin")
    private val station = RecordingStation("Gece Cazı", "https://yayin.example/caz")

    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, s, 0, zone).toInstant().toEpochMilli()

    private fun schedule(repeat: Repeat, hour: Int = 7, minute: Int = 0, minutes: Int = 60, date: LocalDate = LocalDate.of(2026, 10, 15), zone: ZoneId = istanbul) =
        RecordingSchedule.of(station, date, hour, minute, minutes, repeat, zone, now = 0, id = 1)

    @Test
    fun aScheduleRecordsFromItsStartForItsDuration() {
        val s = schedule(Repeat.Once, 7, 30, 90)
        val run = ScheduleMath.next(s, at(istanbul, 2026, 10, 14, 12, 0), istanbul)!!
        assertEquals(at(istanbul, 2026, 10, 15, 7, 30), run.start)
        assertEquals(at(istanbul, 2026, 10, 15, 9, 0), run.end)
        assertEquals(4, s.weekday)
    }

    @Test
    fun aOneOffInThePastOrSwitchedOffHasNoNext() {
        val once = schedule(Repeat.Once)
        assertNull(ScheduleMath.next(once, at(istanbul, 2026, 10, 15, 7, 0, 1), istanbul))
        assertNull(ScheduleMath.next(once, at(istanbul, 2026, 10, 15, 7, 0), istanbul))
        assertNull(ScheduleMath.next(schedule(Repeat.Daily).copy(enabled = false), 0, istanbul))
    }

    @Test
    fun dailyIsTheNextTimeStrictlyAfterNow() {
        val daily = schedule(Repeat.Daily)
        assertEquals(at(istanbul, 2026, 10, 15, 7, 0), ScheduleMath.next(daily, at(istanbul, 2026, 10, 15, 6, 59, 59), istanbul)!!.start)
        assertEquals(at(istanbul, 2026, 10, 16, 7, 0), ScheduleMath.next(daily, at(istanbul, 2026, 10, 15, 7, 0), istanbul)!!.start)
        assertEquals(at(istanbul, 2026, 10, 16, 7, 0), ScheduleMath.next(daily, at(istanbul, 2026, 10, 15, 23, 59), istanbul)!!.start)
    }

    @Test
    fun weekdaysSkipTheWeekend() {
        val weekdays = schedule(Repeat.Weekdays, 8, 0)
        // Friday 2026-10-16 after 08:00 → Monday 10-19
        assertEquals(at(istanbul, 2026, 10, 19, 8, 0), ScheduleMath.next(weekdays, at(istanbul, 2026, 10, 16, 8, 1), istanbul)!!.start)
        assertEquals(at(istanbul, 2026, 10, 19, 8, 0), ScheduleMath.next(weekdays, at(istanbul, 2026, 10, 18, 12, 0), istanbul)!!.start)
        assertEquals(at(istanbul, 2026, 10, 16, 8, 0), ScheduleMath.next(weekdays, at(istanbul, 2026, 10, 15, 9, 0), istanbul)!!.start)
    }

    @Test
    fun weeklyKeepsTheWeekdayItWasSetOn() {
        // set on Thursday 2026-10-15
        val weekly = schedule(Repeat.Weekly, 20, 15)
        assertEquals(4, weekly.weekday)
        assertEquals(at(istanbul, 2026, 10, 22, 20, 15), ScheduleMath.next(weekly, at(istanbul, 2026, 10, 15, 21, 0), istanbul)!!.start)
        assertEquals(at(istanbul, 2026, 10, 22, 20, 15), ScheduleMath.next(weekly, at(istanbul, 2026, 10, 17, 9, 0), istanbul)!!.start)
    }

    @Test
    fun theWallClockHoldsOverTheClocksGoingBack() {
        val daily = schedule(Repeat.Daily, 7, 0, zone = berlin)
        // summer time ends Sunday 2026-10-25
        val saturday = ScheduleMath.next(daily, at(berlin, 2026, 10, 24, 2, 0), berlin)!!
        val sunday = ScheduleMath.next(daily, saturday.start, berlin)!!
        val monday = ScheduleMath.next(daily, sunday.start, berlin)!!
        assertEquals(at(berlin, 2026, 10, 24, 7, 0), saturday.start)
        assertEquals(at(berlin, 2026, 10, 25, 7, 0), sunday.start)
        assertEquals(at(berlin, 2026, 10, 26, 7, 0), monday.start)
        // the day of the change is 25 hours long
        assertEquals(25 * 3_600_000L, sunday.start - saturday.start)
        assertEquals(24 * 3_600_000L, monday.start - sunday.start)
    }

    @Test
    fun aTimeTheClocksJumpOverIsTheFirstMomentAfterTheJump() {
        val daily = schedule(Repeat.Daily, 2, 30, zone = berlin)
        // 2026-03-29 02:00 → 03:00: 02:30 does not exist
        val run = ScheduleMath.next(daily, at(berlin, 2026, 3, 28, 12, 0), berlin)!!
        assertEquals(at(berlin, 2026, 3, 29, 3, 30), run.start)
    }

    @Test
    fun aTimeThatHappensTwiceRunsOnce() {
        val daily = schedule(Repeat.Daily, 2, 30, zone = berlin)
        val first = ScheduleMath.next(daily, at(berlin, 2026, 10, 24, 12, 0), berlin)!!
        val second = ScheduleMath.next(daily, first.start, berlin)!!
        // 02:30 on the 25th is the first of its two times; the next run is the 26th
        assertEquals(at(berlin, 2026, 10, 26, 2, 30), second.start)
        assertEquals(25 * 3_600_000L, second.start - first.start)
    }

    @Test
    fun aRunIsAsLongAsItsMinutesWhateverTheClocksDo() {
        val s = schedule(Repeat.Daily, 1, 0, 180, zone = berlin)
        val run = ScheduleMath.next(s, at(berlin, 2026, 10, 24, 12, 0), berlin)!!
        assertEquals(3 * 3_600_000L, run.end - run.start)
    }

    @Test
    fun theSameWallClockIsAnotherMomentInAnotherZone() {
        val daily = schedule(Repeat.Daily, 7, 0)
        val now = at(istanbul, 2026, 10, 15, 0, 0)
        val home = ScheduleMath.next(daily, now, istanbul)!!.start
        val away = ScheduleMath.next(daily, now, ZoneId.of("America/New_York"))!!.start
        assertEquals(at(istanbul, 2026, 10, 15, 7, 0), home)
        assertEquals(at(ZoneId.of("America/New_York"), 2026, 10, 15, 7, 0), away)
        assertTrue(away > home)
    }

    @Test
    fun aRunThatBeganAndIsNotOverIsRunning() {
        val s = schedule(Repeat.Daily, 7, 0, 60)
        assertEquals(at(istanbul, 2026, 10, 15, 7, 0), ScheduleMath.running(s, at(istanbul, 2026, 10, 15, 7, 20), istanbul)!!.start)
        assertNull(ScheduleMath.running(s, at(istanbul, 2026, 10, 15, 6, 59), istanbul))
        assertNull(ScheduleMath.running(s, at(istanbul, 2026, 10, 15, 8, 0), istanbul))
        // started already: not again
        val started = s.copy(lastStartedAt = at(istanbul, 2026, 10, 15, 7, 0))
        assertNull(ScheduleMath.running(started, at(istanbul, 2026, 10, 15, 7, 20), istanbul))
    }

    @Test
    fun aRunPastMidnightIsStillRunningTheNextMorning() {
        val s = schedule(Repeat.Daily, 23, 30, 120)
        val run = ScheduleMath.running(s, at(istanbul, 2026, 10, 16, 0, 15), istanbul)!!
        assertEquals(at(istanbul, 2026, 10, 15, 23, 30), run.start)
        assertEquals(at(istanbul, 2026, 10, 16, 1, 30), run.end)
        assertNull(ScheduleMath.running(s, at(istanbul, 2026, 10, 16, 1, 30), istanbul))
    }

    @Test
    fun aOneOffRunningIsFoundToo() {
        val once = schedule(Repeat.Once, 7, 0, 60)
        assertEquals(at(istanbul, 2026, 10, 15, 7, 0), ScheduleMath.running(once, at(istanbul, 2026, 10, 15, 7, 30), istanbul)!!.start)
        assertNull(ScheduleMath.running(once.copy(lastStartedAt = once.startAt), at(istanbul, 2026, 10, 15, 7, 30), istanbul))
    }

    @Test
    fun theRunsInAStretchOfTimeAreListed() {
        val weekdays = schedule(Repeat.Weekdays, 8, 0)
        val runs = ScheduleMath.between(weekdays, at(istanbul, 2026, 10, 12, 0, 0), at(istanbul, 2026, 10, 19, 0, 0), istanbul)
        assertEquals(5, runs.size)
        assertEquals(at(istanbul, 2026, 10, 12, 8, 0), runs.first().start)
        assertEquals(at(istanbul, 2026, 10, 16, 8, 0), runs.last().start)
    }

    @Test
    fun overlappingSchedulesAreConflicts() {
        val seven = schedule(Repeat.Daily, 7, 0, 60).copy(id = 1)
        val half = schedule(Repeat.Daily, 7, 30, 90).copy(id = 2, station = RecordingStation("Başka", "https://x"))
        val next = schedule(Repeat.Daily, 8, 0, 60).copy(id = 3)
        val now = at(istanbul, 2026, 10, 15, 0, 0)
        assertEquals(listOf(2L), ScheduleMath.conflicts(seven, listOf(seven, half, next), now, zone = istanbul).map { it.id })
        // one hour long, ending at 08:00: the next one starts as it ends
        assertTrue(ScheduleMath.conflicts(seven, listOf(next), now, zone = istanbul).isEmpty())
        // a schedule being made has no id yet
        assertEquals(2, ScheduleMath.conflicts(seven.copy(id = 0), listOf(seven, half), now, zone = istanbul).size)
    }

    @Test
    fun weeklyAndDailyOnDifferentDaysDoNotMeet() {
        val weekdays = schedule(Repeat.Weekdays, 7, 0).copy(id = 1)
        val sunday = RecordingSchedule.of(station, LocalDate.of(2026, 10, 18), 7, 0, 60, Repeat.Weekly, istanbul, now = 0, id = 2)
        assertTrue(ScheduleMath.conflicts(weekdays, listOf(sunday), at(istanbul, 2026, 10, 15, 0, 0), zone = istanbul).isEmpty())
    }

    @Test
    fun thePeakIsTheMostThatRunAtOnce() {
        val a = schedule(Repeat.Daily, 7, 0, 120).copy(id = 1)
        val b = schedule(Repeat.Daily, 7, 30, 60).copy(id = 2)
        val c = schedule(Repeat.Daily, 7, 45, 15).copy(id = 3)
        val d = schedule(Repeat.Daily, 12, 0, 30).copy(id = 4)
        val now = at(istanbul, 2026, 10, 15, 0, 0)
        assertEquals(3, ScheduleMath.peak(listOf(a, b, c, d), now, zone = istanbul))
        assertEquals(1, ScheduleMath.peak(listOf(a, d), now, zone = istanbul))
        assertEquals(0, ScheduleMath.peak(emptyList(), now, zone = istanbul))
    }
}
