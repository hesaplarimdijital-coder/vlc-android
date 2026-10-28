package app.nagmedar.ui.sleep

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1007: the sleep timer's time, fade, track end and their undoing. */
class SleepTimerTest {

    private class Player : SleepTarget {
        override var playing = true

        /** Paused (or being opened again) with the item still loaded. */
        var paused = false
        override val loaded: Boolean get() = playing || paused
        private var level = 100
        override val volume: Int get() = level
        var stops = 0
        var stopAfterTrack = false
        override var trackLeftMs: Long? = null

        override fun setVolume(volume: Int) {
            level = volume
        }

        override fun stop() {
            stops++
            playing = false
        }

        override fun stopAtTrackEnd(): Boolean {
            if (!playing) return false
            stopAfterTrack = true
            return true
        }

        override fun goOnAfterTrack() {
            stopAfterTrack = false
        }
    }

    private var clock = 1_000_000L
    private val player = Player()

    /** A scope whose coroutines never run: the test turns the clock and ticks itself. */
    private val idle = CoroutineScope(Job().apply { cancel() })

    private val timer = SleepTimer(target = { player }, scope = idle, now = { clock })

    @Test
    fun theTimeUpStopsAfterAFadeAndTheVolumeComesBack() {
        timer.start(minutes = 1, atTrackEnd = false, fade = true)
        clock += 20_000
        assertTrue(timer.tick())
        assertEquals("no fade before its last 30 s", 100, player.volume)
        clock += 25_000
        assertTrue(timer.tick())
        assertEquals(50, player.volume)
        clock += 14_000
        assertTrue(timer.tick())
        assertEquals(3, player.volume)
        clock += 1_000
        assertFalse(timer.tick())
        assertEquals(1, player.stops)
        assertEquals(100, player.volume)
        assertNull(timer.state.value)
    }

    @Test
    fun withTheTrackEndTheTrackPlaysOut() {
        timer.start(minutes = 30, atTrackEnd = true, fade = false)
        clock += 30 * 60_000L
        assertTrue(timer.tick())
        assertTrue(player.stopAfterTrack)
        assertEquals(0, player.stops)
        assertTrue(timer.state.value!!.waitingForTrackEnd)
        // the service stops after the track
        player.playing = false
        assertFalse(timer.tick())
        assertNull(timer.state.value)
    }

    @Test
    fun withTheTrackEndTheFadeIsTheTracks() {
        timer.start(minutes = 1, atTrackEnd = true, fade = true)
        clock += 45_000
        player.trackLeftMs = 100_000
        assertTrue(timer.tick())
        assertEquals("the time's last seconds do not fade: the track's will", 100, player.volume)
        clock += 15_000
        assertTrue(timer.tick())
        assertTrue(timer.state.value!!.waitingForTrackEnd)
        player.trackLeftMs = 85_000
        assertTrue(timer.tick())
        assertEquals(100, player.volume)
        player.trackLeftMs = 15_000
        assertTrue(timer.tick())
        assertEquals(50, player.volume)
        player.playing = false
        assertFalse(timer.tick())
        assertEquals("the next play starts at the volume it had", 100, player.volume)
        // the track's end alone, faded
        player.playing = true
        timer.startAtTrackEnd(fade = true)
        player.trackLeftMs = 3_000
        assertTrue(timer.tick())
        assertEquals(10, player.volume)
        timer.cancel()
        assertEquals(100, player.volume)
    }

    @Test
    fun aPauseWhileTheTrackEndIsAwaitedKeepsTheTimer() {
        timer.startAtTrackEnd()
        player.playing = false
        player.paused = true
        assertTrue(timer.tick())
        assertTrue(timer.state.value!!.waitingForTrackEnd)
        assertTrue(player.stopAfterTrack)
        // the service stopped after the track
        player.paused = false
        assertFalse(timer.tick())
        assertNull(timer.state.value)
    }

    @Test
    fun theTrackEndAloneAndCancelled() {
        timer.startAtTrackEnd()
        assertTrue(player.stopAfterTrack)
        assertNull(timer.state.value!!.endsAt)
        timer.extend(15)
        assertNull("nothing to add to", timer.state.value!!.endsAt)
        timer.cancel()
        assertFalse(player.stopAfterTrack)
        assertNull(timer.state.value)
    }

    @Test
    fun moreTimeAndAFadeTakenBack() {
        timer.start(minutes = 1, atTrackEnd = false, fade = true)
        clock += 45_000
        timer.tick()
        assertEquals(50, player.volume)
        timer.extend(15)
        assertEquals(100, player.volume)
        assertEquals(15 * 60_000L + 15_000, timer.remainingMs())
        // a time up awaiting the track's end goes on when more is asked
        timer.start(minutes = 1, atTrackEnd = true, fade = false)
        clock += 60_000
        timer.tick()
        assertTrue(player.stopAfterTrack)
        timer.extend(10)
        assertFalse(player.stopAfterTrack)
        assertEquals(10 * 60_000L, timer.remainingMs())
    }

    @Test
    fun nothingPlayingWhenTheTimeIsUp() {
        timer.start(minutes = 5, atTrackEnd = false, fade = true)
        player.playing = false
        clock += 5 * 60_000L
        assertFalse(timer.tick())
        assertEquals(0, player.stops)
        assertNull(timer.state.value)
        assertFalse(SleepTimer(target = { player }, scope = idle, now = { clock }).let { it.startAtTrackEnd(); it.state.value != null })
    }
}
