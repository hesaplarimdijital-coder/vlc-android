package app.nagmedar.ui.tvlab.remux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** S-1210: the night run's time, and the wait for the charger once that time has come. */
class RemuxScheduleTest {
    private val zone = ZoneId.of("Europe/Istanbul")

    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun theNextNightIsTodaysWhileItIsStillToCome() {
        assertEquals(at(23, 2, 0), RemuxNight.next(2, 0, at(23, 1, 30), zone))
        assertEquals(at(24, 2, 0), RemuxNight.next(2, 0, at(23, 2, 0), zone))
        assertEquals(at(24, 2, 0), RemuxNight.next(2, 0, at(23, 22, 15), zone))
    }

    @Test
    fun aNightRunWaitsForTheChargerOnlyOnceItsTimeHasCome() {
        val night = QueueControl(nightAt = 5_000, nightCharging = true)
        assertTrue(night.waitsForNight(4_999))
        assertFalse(night.waitsForCharger(4_999))
        assertFalse(night.waitsForNight(5_000))
        assertTrue(night.waitsForCharger(5_000))
        assertTrue("until the worker sees the device charge", night.waitsForCharger(9_000_000))
        // without "only while charging", or without a night run, nothing waits for the charger
        assertFalse(QueueControl(nightAt = 5_000).waitsForCharger(6_000))
        assertFalse(QueueControl(nightCharging = true).waitsForCharger(6_000))
    }
}
