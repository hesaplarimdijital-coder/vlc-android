package app.nagmedar.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an end or an error of a streamed podcast episode is: its end, or a dropout to open it again where it was; of a
 * live stream: a drop to connect to again; and when a stream that plays with nothing coming has stalled.
 */
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
        episode: Boolean = true,
    ) = StreamDropout.verdict(streamed, position, duration, retries, error, lastDrop, played, episode)

    private fun live(position: Long, retries: Int = 0, error: Boolean = false, lastDrop: Long = -1L, streamed: Boolean = true) =
        verdict(position, duration = 0, retries = retries, error = error, streamed = streamed, lastDrop = lastDrop, episode = false)

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

    @Test
    fun aLiveStreamThatDropsIsConnectedToAgainAtItsLiveEdge() {
        assertEquals(DropoutVerdict.Reopen(0, 1_000, 1), live(600_000))
        assertEquals(DropoutVerdict.Reopen(0, 1_000, 1), live(600_000, error = true))
        assertEquals(DropoutVerdict.Reopen(0, 3_000, 2), live(2_000, retries = 1, error = true))
        assertEquals(DropoutVerdict.Reopen(0, 8_000, 3), live(0, retries = 2, error = true))
        assertEquals(DropoutVerdict.GiveUp, live(0, retries = 3, error = true))
        assertEquals(DropoutVerdict.GiveUp, live(30_000, retries = 3))
    }

    @Test
    fun aLiveStreamThatNeverPlayedEndsAsItAlwaysDid() {
        // a .pls or .m3u address (its stations are played next), or one that does not answer
        assertEquals(DropoutVerdict.End, live(0))
        assertEquals(DropoutVerdict.End, live(0, error = true))
        // not from the network
        assertEquals(DropoutVerdict.End, live(600_000, streamed = false))
    }

    @Test
    fun aStreamWithNoLengthThatEndsWhereItEndedIsAFileThatEnded() {
        assertEquals(DropoutVerdict.End, live(181_000, lastDrop = 180_000))
        // an error, another place, or the first seconds: a drop again
        assertEquals(DropoutVerdict.Reopen(0, 1_000, 1), live(181_000, error = true, lastDrop = 180_000))
        assertEquals(DropoutVerdict.Reopen(0, 1_000, 1), live(240_000, lastDrop = 180_000))
        assertEquals(DropoutVerdict.Reopen(0, 3_000, 2), live(1_500, retries = 1, lastDrop = 1_000))
    }

    @Test
    fun aStreamThatIsNoEpisodeButHasALengthIsNotOurs() {
        assertEquals(DropoutVerdict.End, verdict(1_200_000, episode = false))
        assertEquals(DropoutVerdict.End, verdict(1_200_000, error = true, episode = false))
        // an episode with no length known stays as it was
        assertEquals(DropoutVerdict.End, verdict(1_200_000, duration = 0, error = true))
    }

    @Test
    fun aStreamWhoseTimeStandsStillWhilePlayingHasStalled() {
        val stall = StreamStall(20_000)
        assertFalse(stall.look(10_000, playing = true, nowMs = 0))
        assertFalse(stall.look(11_000, playing = true, nowMs = 1_000))
        assertFalse(stall.look(11_000, playing = true, nowMs = 20_999))
        assertTrue(stall.look(11_000, playing = true, nowMs = 21_000))
        // the wait starts again after a stall
        assertFalse(stall.look(11_000, playing = true, nowMs = 22_000))
        assertFalse(stall.look(11_000, playing = true, nowMs = 41_999))
        assertTrue(stall.look(11_000, playing = true, nowMs = 42_000))
    }

    @Test
    fun progressAPauseOrASeekIsNoStall() {
        val stall = StreamStall(20_000)
        assertFalse(stall.look(0, playing = true, nowMs = 0))
        assertFalse(stall.look(1_000, playing = true, nowMs = 1_000))
        // paused for a minute: not playing, no stall, and the wait starts when it plays again
        assertFalse(stall.look(1_000, playing = false, nowMs = 10_000))
        assertFalse(stall.look(1_000, playing = false, nowMs = 70_000))
        assertFalse(stall.look(1_000, playing = true, nowMs = 71_000))
        assertFalse(stall.look(1_000, playing = true, nowMs = 90_000))
        // a seek (back or forward) moves the time
        assertFalse(stall.look(500_000, playing = true, nowMs = 91_000))
        assertFalse(stall.look(500_000, playing = true, nowMs = 110_000))
        assertFalse(stall.look(400_000, playing = true, nowMs = 111_000))
        assertFalse(stall.look(400_000, playing = true, nowMs = 130_999))
        assertTrue(stall.look(400_000, playing = true, nowMs = 131_000))
    }

    @Test
    fun aStreamThatNeverMovedIsStillOpening() {
        val stall = StreamStall(20_000)
        assertFalse(stall.look(0, playing = true, nowMs = 0))
        assertFalse(stall.look(0, playing = true, nowMs = 60_000))
        assertFalse(stall.look(0, playing = true, nowMs = 120_000))
    }

    @Test
    fun theStallTimeFollowsTheNetworkCaching() {
        assertEquals(20_000L, StreamStall.limitMs(0))
        assertEquals(20_000L, StreamStall.limitMs(1_000))
        assertEquals(20_000L, StreamStall.limitMs(10_000))
        assertEquals(30_000L, StreamStall.limitMs(15_000))
        assertEquals(120_000L, StreamStall.limitMs(60_000))
        assertEquals(120_000L, StreamStall.limitMs(600_000))
        assertEquals(20_000L, StreamStall.limitMs(-5))
    }
}
