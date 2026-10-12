package app.nagmedar.ui.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-1404: a playback's record from libVLC's statistics, a sample a second: its seconds, its shown
 * and dropped frames, the battery it used in an hour; the next film's, the short ones, the paused
 * seconds.
 */
class PlaybackPerfTest {

    private var now = 0L
    private var level = BatteryReading(chargeMicroAh = 5_800_000, percent = 50, discharging = true)
    private val perf = PlaybackPerf(battery = { level }, clock = { now }, wallClock = { 1_000L + now })

    /** [seconds] of a film at 24 frames a second, from [shown] frames, one dropped every [dropEvery] seconds. */
    private fun play(seconds: Int, shown: Long = 0, dropped: Long = 0, dropEvery: Int = 0): Pair<Long, Long> {
        var frames = shown
        var lost = dropped
        repeat(seconds) { second ->
            now += 1_000
            frames += 24
            if (dropEvery > 0 && (second + 1) % dropEvery == 0) lost++
            perf.sample(stats(frames, lost))
        }
        return frames to lost
    }

    @Test
    fun aPlaybacksRecord() {
        perf.start("content://film/1", "Kuzey_Isigi.mkv")
        perf.describe("content://film/1", 3840, 2160, "DV P8")
        play(20, dropEvery = 10)
        val record = perf.finish()!!
        assertEquals("Kuzey_Isigi.mkv", record.name)
        assertEquals(3840, record.width)
        assertEquals("DV P8", record.range)
        assertEquals(20, record.seconds)
        assertEquals(480, record.shown)
        assertEquals(2, record.dropped)
        assertEquals(2 * 100.0 / 482, record.droppedPercent, 1e-9)
        assertEquals(21_000L, record.atMs)
        assertNull("over 20 seconds the battery cannot tell", record.batteryPerHour)
        assertNull("finished once", perf.finish())
    }

    @Test
    fun pausedSecondsDoNotCount() {
        perf.start("a", "a.mkv")
        val (frames, lost) = play(6)
        // paused: the counts stay
        repeat(10) {
            now += 1_000
            perf.sample(stats(frames, lost))
        }
        play(4, frames, lost)
        assertEquals(10, perf.finish()!!.seconds)
    }

    @Test
    fun aShortPlaybackIsNoRecord() {
        perf.start("a", "a.mkv")
        play(PlaybackPerf.MIN_SECONDS - 1)
        assertNull(perf.finish())
        // nor one that showed nothing (the sound of a video without a picture)
        perf.start("b", "b.mkv")
        repeat(30) {
            now += 1_000
            perf.sample(stats(0))
        }
        assertNull(perf.finish())
    }

    @Test
    fun theNextFilmEndsTheOneBefore() {
        perf.start("a", "a.mkv")
        play(8)
        // the next film's first second comes before its start: fewer frames, left
        perf.sample(stats(24))
        val first = perf.start("b", "b.mkv")!!
        assertEquals("a.mkv", first.name)
        assertEquals(8 * 24L, first.shown)
        assertEquals("b", perf.playing)
        // a picture told late, of the film before, is left
        perf.describe("a", 1920, 1080, "SDR")
        assertTrue(perf.needsPicture)
        perf.describe("b", 1920, 1080, "")
        assertFalse(perf.needsPicture)
        play(6)
        val second = perf.finish()!!
        assertEquals(6, second.seconds)
        assertEquals("", second.range)
        assertEquals(1920, second.width)
    }

    @Test
    fun theBatteryUsedInAnHour() {
        perf.start("a", "a.mkv")
        play(30)
        now = 30 * 60_000L
        // 580 000 µAh of a full 11 600 000: 5 % in half an hour
        level = BatteryReading(5_220_000, 45, discharging = true)
        perf.sample(stats(30 * 24L + 43_200))
        assertEquals(10.0, perf.finish()!!.batteryPerHour!!, 0.05)
    }

    @Test
    fun theBatteryIsToldOffTheChargerAndInTime() {
        val start = BatteryReading(5_800_000, 50, discharging = true)
        assertEquals(10.0, batteryPerHour(start, BatteryReading(5_220_000, 45, true), 30 * 60_000L)!!, 1e-9)
        // charging at either end
        assertNull(batteryPerHour(start.copy(discharging = false), BatteryReading(5_220_000, 45, true), 30 * 60_000L))
        assertNull(batteryPerHour(start, BatteryReading(5_220_000, 45, false), 30 * 60_000L))
        // too short for the counter
        assertNull(batteryPerHour(start, BatteryReading(5_790_000, 50, true), 4 * 60_000L))
        // without a counter, from the level: half an hour at least
        val level = BatteryReading(0, 60, true)
        assertEquals(5.0, batteryPerHour(level, BatteryReading(0, 55, true), 60 * 60_000L)!!, 1e-9)
        assertNull(batteryPerHour(level, BatteryReading(0, 59, true), 20 * 60_000L))
        // a charge that went up reads as none used
        assertEquals(0.0, batteryPerHour(start, BatteryReading(5_900_000, 51, true), 10 * 60_000L)!!, 1e-9)
        assertNull(batteryPerHour(null, start, 60 * 60_000L))
    }
}
