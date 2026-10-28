package app.nagmedar.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/** What an end or an error of a streamed podcast episode is: its end, or a dropout to open it again where it was. */
class StreamDropoutTest {

    private val hour = 3_600_000L

    private fun verdict(
        position: Long,
        duration: Long = hour,
        retries: Int = 0,
        error: Boolean = false,
        streamed: Boolean = true,
        lastDrop: Long = -1L,
        played: Boolean = false,
    ) = StreamDropout.verdict(streamed, position, duration, retries, error, lastDrop, played)

    @Test
    fun anEndInTheMiddleIsADropoutOpenedAgainAFewSecondsEarlier() {
        assertEquals(DropoutVerdict.Reopen(1_197_000, 1_000, 1), verdict(1_200_000))
        assertEquals(DropoutVerdict.Reopen(1_197_000, 1_000, 1), verdict(1_200_000, error = true))
        // at the very start: from the start
        assertEquals(DropoutVerdict.Reopen(0, 1_000, 1), verdict(1_000, error = true))
    }

    @Test
    fun theWaitGrowsAndTheThirdFailureGivesUp() {
        assertEquals(DropoutVerdict.Reopen(1_197_000, 3_000, 2), verdict(1_200_000, retries = 1, error = true))
        assertEquals(DropoutVerdict.Reopen(1_197_000, 8_000, 3), verdict(1_200_000, retries = 2))
        assertEquals(DropoutVerdict.GiveUp, verdict(1_200_000, retries = 3, error = true))
        assertEquals(DropoutVerdict.GiveUp, verdict(1_200_000, retries = 3))
    }

    @Test
    fun theLastSecondsAreTheEnd() {
        assertEquals(DropoutVerdict.End, verdict(hour - 5_000))
        assertEquals(DropoutVerdict.End, verdict(hour - 1_000, error = true))
        assertEquals(DropoutVerdict.End, verdict(hour))
        assertEquals(DropoutVerdict.Reopen(hour - 9_000, 1_000, 1), verdict(hour - 6_000))
    }

    @Test
    fun withoutAKnownLengthOrFromAFileEveryEndIsTheEnd() {
        assertEquals(DropoutVerdict.End, verdict(1_200_000, duration = 0))
        assertEquals(DropoutVerdict.End, verdict(1_200_000, streamed = false))
        assertEquals(DropoutVerdict.End, verdict(1_200_000, streamed = false, error = true))
    }

    @Test
    fun aStreamThatEndsAgainWhereItEndedIsTheEnd() {
        // opened again, played up to the same place, ended without an error: the stream's own end (its length was an estimate)
        assertEquals(DropoutVerdict.End, verdict(1_201_000, retries = 1, lastDrop = 1_200_000, played = true))
        // not played since, or an error, or somewhere else: a dropout again
        assertEquals(DropoutVerdict.Reopen(1_197_000, 3_000, 2), verdict(1_200_000, retries = 1, lastDrop = 1_200_000, played = false))
        assertEquals(DropoutVerdict.Reopen(1_198_000, 3_000, 2), verdict(1_201_000, retries = 1, error = true, lastDrop = 1_200_000, played = true))
        assertEquals(DropoutVerdict.Reopen(1_207_000, 3_000, 2), verdict(1_210_000, retries = 1, lastDrop = 1_200_000, played = true))
    }
}
