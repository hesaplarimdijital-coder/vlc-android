package app.nagmedar.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/** S-808: what counts as a play, and what as a skip. */
class ListenCounterTest {

    private val events = mutableListOf<String>()
    private val counter = ListenCounter(onPlayed = { events += "played $it" }, onSkipped = { events += "skipped $it" })

    /** [key] played from [from] to [to] ms, a sample a second. */
    private fun play(key: String, length: Long, from: Long, to: Long, step: Long = 1_000) {
        var time = from
        while (time <= to) {
            counter.sample(key, time, length, playing = true)
            time += step
        }
    }

    @Test
    fun halfOfATrackIsAPlayOnce() {
        play("a", length = 180_000, from = 0, to = 89_000)
        assertEquals(emptyList<String>(), events)
        play("a", length = 180_000, from = 90_000, to = 180_000)
        assertEquals(listOf("played a"), events)
        // the next track takes over: a play is not a skip too
        play("b", length = 200_000, from = 0, to = 5_000)
        assertEquals(listOf("played a"), events)
    }

    @Test
    fun fourMinutesOfALongTrackAreAPlay() {
        play("long", length = 20 * 60_000L, from = 0, to = 239_000)
        assertEquals(emptyList<String>(), events)
        play("long", length = 20 * 60_000L, from = 240_000, to = 241_000)
        assertEquals(listOf("played long"), events)
    }

    @Test
    fun leftEarlyForAnotherTrackIsASkip() {
        play("a", length = 180_000, from = 0, to = 20_000)
        play("b", length = 180_000, from = 0, to = 1_000)
        assertEquals(listOf("skipped a"), events)
        // passed through without being heard: nothing
        counter.sample("c", 0, 180_000, playing = true)
        counter.sample("d", 0, 180_000, playing = true)
        assertEquals(listOf("skipped a", "skipped b"), events)
    }

    @Test
    fun aStopIsNotASkip() {
        play("a", length = 180_000, from = 0, to = 30_000)
        counter.sample(null, 0, 0, playing = false)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun aSeekIsNotListening() {
        play("a", length = 180_000, from = 0, to = 10_000)
        // to near the end; a few seconds heard there
        play("a", length = 180_000, from = 170_000, to = 180_000)
        assertEquals("21 s heard of 180", emptyList<String>(), events)
        // paused: the time stands
        repeat(100) { counter.sample("a", 180_000, 180_000, playing = false) }
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun fasterPlaybackCountsItsSteps() {
        play("a", length = 180_000, from = 0, to = 92_000, step = 4_000)
        assertEquals(listOf("played a"), events)
    }

    @Test
    fun playedAgainFromTheStartCountsAgain() {
        play("a", length = 60_000, from = 0, to = 60_000)
        // repeat one: the same track from its beginning
        play("a", length = 60_000, from = 0, to = 31_000)
        assertEquals(listOf("played a", "played a"), events)
    }
}
