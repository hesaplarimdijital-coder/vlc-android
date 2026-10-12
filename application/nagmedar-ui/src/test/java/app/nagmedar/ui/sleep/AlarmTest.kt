package app.nagmedar.ui.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** S-1007: when the alarm rings, and how the settings keep it. */
class AlarmTest {

    private val istanbul = TimeZone.getTimeZone("Europe/Istanbul")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(istanbul).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun theNextTimeItComes() {
        // Monday 12 October 2026
        val alarm = Alarm(enabled = true, hour = 7, minute = 30)
        assertEquals(at(2026, 10, 12, 7, 30), alarm.next(at(2026, 10, 12, 6, 0), istanbul))
        assertEquals(at(2026, 10, 13, 7, 30), alarm.next(at(2026, 10, 12, 8, 0), istanbul))
        // at the minute itself: the next one
        assertEquals(at(2026, 10, 13, 7, 30), alarm.next(at(2026, 10, 12, 7, 30), istanbul))
        // the weekend only: Saturday
        assertEquals(at(2026, 10, 17, 7, 30), alarm.copy(days = setOf(6, 7)).next(at(2026, 10, 12, 6, 0), istanbul))
        // Monday, once past: the next Monday
        assertEquals(at(2026, 10, 19, 7, 30), alarm.copy(days = setOf(1)).next(at(2026, 10, 12, 9, 0), istanbul))
        assertNull(alarm.copy(enabled = false).next(at(2026, 10, 12, 6, 0), istanbul))
    }

    @Test
    fun aDaylightSavingNightKeepsTheHour() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val alarm = Alarm(enabled = true, hour = 7, minute = 0)
        val saturday = Calendar.getInstance(berlin).apply { clear(); set(2026, 9, 24, 22, 0, 0) }.timeInMillis
        val sunday = Calendar.getInstance(berlin).apply { clear(); set(2026, 9, 25, 7, 0, 0) }.timeInMillis
        assertEquals(sunday, alarm.next(saturday, berlin))
    }

    @Test
    fun keptAsText() {
        val alarms = listOf(
            Alarm(),
            Alarm(enabled = true, hour = 6, minute = 5, days = setOf(1, 2, 3, 4, 5), source = AlarmSource.Playlist(42, "Sabah; şarkılar: 1"), rampSeconds = 120, loudness = 35),
            Alarm(enabled = true, source = AlarmSource.Stream("http://radyo.example/canli;stream.mp3?a=1&b=2")),
        )
        for (alarm in alarms) assertEquals(alarm, Alarm.decode(alarm.encode()))
        assertEquals("1;06:05;1,2,3,4,5;playlist:42:Sabah%3B+%C5%9Fark%C4%B1lar%3A+1;120;35", alarms[1].encode())
        assertEquals(Alarm(), Alarm.decode(null))
        assertEquals(Alarm(), Alarm.decode("bozuk"))
        assertEquals(Alarm(), Alarm.decode("1;ab:cd;;last;60;50"))
    }

    @Test
    fun isoDays() {
        val day = Calendar.getInstance(istanbul).apply { timeInMillis = at(2026, 10, 12, 12, 0) }
        assertEquals(1, Alarm.isoDay(day))
        day.add(Calendar.DAY_OF_YEAR, 6)
        assertEquals(7, Alarm.isoDay(day))
        assertEquals(Calendar.MONDAY, Alarm.calendarDay(1))
        assertEquals(Calendar.SATURDAY, Alarm.calendarDay(6))
        assertEquals(Calendar.SUNDAY, Alarm.calendarDay(7))
    }

    @Test
    fun theTimeStepsRound() {
        // minutes by five, to the next multiple
        assertEquals(10, Alarm.step(7, 5, 59, up = true))
        assertEquals(5, Alarm.step(7, 5, 59, up = false))
        assertEquals(15, Alarm.step(10, 5, 59, up = true))
        assertEquals(5, Alarm.step(10, 5, 59, up = false))
        assertEquals(0, Alarm.step(55, 5, 59, up = true))
        assertEquals(55, Alarm.step(0, 5, 59, up = false))
        // hours by one
        assertEquals(0, Alarm.step(23, 1, 23, up = true))
        assertEquals(23, Alarm.step(0, 1, 23, up = false))
        assertEquals(8, Alarm.step(7, 1, 23, up = true))
    }

    @Test
    fun daysUntilTheNextRing() {
        val now = at(2026, 10, 12, 23, 50)
        assertEquals(0, Alarm.daysUntil(at(2026, 10, 12, 23, 55), now, istanbul))
        assertEquals(1, Alarm.daysUntil(at(2026, 10, 13, 0, 5), now, istanbul))
        assertEquals(6, Alarm.daysUntil(at(2026, 10, 18, 7, 0), now, istanbul))
        assertEquals(1, Alarm.daysUntil(at(2027, 1, 1, 7, 0), at(2026, 12, 31, 22, 0), istanbul))
    }
}
