package app.nagmedar.ui.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S-205: the play button's wait while the player opens an episode, and what ends it. */
class StartWaitTest {

    private val length = 37 * 60_000L
    private val wait = StartWait()

    @Test
    fun itWaitsUntilThePlayerPlaysAtThePlaceForTwoLooks() {
        wait.start(7, 600_000, haltStamp = 0)
        assertEquals(7L, wait.look(7, null, 0))
        assertEquals(7L, wait.look(7, Listening(0, length, playing = true), 0))
        assertEquals(7L, wait.look(7, Listening(599_000, length, playing = true), 0))
        assertNull(wait.look(7, Listening(600_000, length, playing = true), 0))
        assertNull(wait.episodeId)
    }

    @Test
    fun anErrorAnEndOrAStopOfTheEpisodeEndsTheWaitAtOnce() {
        wait.start(7, 600_000, haltStamp = 3)
        assertEquals(7L, wait.look(7, null, 3))
        // the player failed on it, ended it or was stopped: the button follows the player again
        assertNull(wait.look(7, null, 4))
    }

    @Test
    fun anEarlierHaltDoesNotEndIt() {
        wait.start(7, 0, haltStamp = 5)
        repeat(10) { assertEquals(7L, wait.look(7, null, 5)) }
    }

    @Test
    fun itWaitsTwentySecondsAtMost() {
        wait.start(7, 600_000, haltStamp = 0)
        repeat(StartWait.START_LOOKS - 1) { assertEquals(7L, wait.look(7, null, 0)) }
        assertNull(wait.look(7, null, 0))
    }

    @Test
    fun anotherPageEndsIt() {
        wait.start(7, 0, haltStamp = 0)
        assertNull(wait.look(8, null, 0))
        wait.start(7, 0, haltStamp = 0)
        assertNull(wait.look(null, null, 0))
    }

    @Test
    fun nothingWaitsWithoutAStartOrAfterClearing() {
        assertNull(wait.look(7, null, 1))
        wait.start(7, 0, haltStamp = 0)
        wait.clear()
        assertNull(wait.episodeId)
        assertNull(wait.look(7, null, 0))
    }
}
