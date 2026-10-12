package app.nagmedar.ui.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1003: LRCLIB is asked at a pace, and not at all while it asked to wait. */
class RequestPaceTest {

    private var now = 1_000_000L
    private val waits = ArrayList<Long>()

    private fun pace(spacing: Long = 1_000, perWindow: Int = 20, window: Long = 60_000) = RequestPace(
        spacingMs = spacing,
        perWindow = perWindow,
        windowMs = window,
        clock = { now },
        sleep = { waits += it; now += it },
    )

    @Test
    fun aSecondApart() = runBlocking {
        val pace = pace()
        assertTrue(pace.take())
        now += 300
        assertTrue(pace.take())
        assertEquals(listOf(700L), waits)
        now += 5_000
        assertTrue(pace.take())
        assertEquals(listOf(700L), waits)
    }

    @Test
    fun atMostSoManyAMinute() = runBlocking {
        val pace = pace(spacing = 0, perWindow = 3, window = 60_000)
        repeat(3) { assertTrue(pace.take()) }
        // the fourth waits for the first to leave the window
        assertTrue(pace.take())
        assertEquals(listOf(60_000L), waits)
    }

    @Test
    fun notWhileTheServerAskedToWait() = runBlocking {
        val pace = pace()
        assertTrue(pace.take())
        pace.refused(retryAfterMs = 120_000)
        assertFalse(pace.take())
        assertEquals(120_000L, pace.waitMs())
        now += 119_999
        assertFalse(pace.take())
        now += 1
        assertTrue(pace.take())
        assertEquals(0L, pace.waitMs())
    }

    @Test
    fun longerWithEachRefusal() {
        val pace = pace()
        pace.refused(null)
        assertEquals(60_000L, pace.waitMs())
        pace.refused(null)
        assertEquals(120_000L, pace.waitMs())
        pace.refused(null)
        assertEquals(240_000L, pace.waitMs())
        repeat(10) { pace.refused(null) }
        assertEquals(3_600_000L, pace.waitMs())
        // an answer forgives: the next refusal waits a minute again
        pace.answered()
        pace.refused(null)
        assertEquals(60_000L, pace.waitMs())
        // a server's own wait is kept to an hour
        pace.refused(retryAfterMs = 86_400_000)
        assertEquals(3_600_000L, pace.waitMs())
    }
}
