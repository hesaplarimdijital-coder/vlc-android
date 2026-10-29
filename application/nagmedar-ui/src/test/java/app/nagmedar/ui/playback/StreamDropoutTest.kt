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
        seekable: Boolean = false,
        repeats: Int = 0,
    ) = StreamDropout.verdict(streamed, position, duration, retries, error, lastDrop, played, episode, seekable, repeats)

    private fun live(
        position: Long,
        retries: Int = 0,
        error: Boolean = false,
        lastDrop: Long = -1L,
        streamed: Boolean = true,
        seekable: Boolean = false,
        repeats: Int = 0,
    ) = verdict(position, duration = 0, retries = retries, error = error, streamed = streamed, lastDrop = lastDrop, episode = false, seekable = seekable, repeats = repeats)

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
        // one that can be sought in is a file
        assertEquals(DropoutVerdict.End, live(181_000, lastDrop = 180_000, seekable = true, repeats = 1))
        // an error, another place, or the first seconds: a drop again
        assertEquals(DropoutVerdict.Reopen(0, 1_000, 1), live(181_000, error = true, lastDrop = 180_000, seekable = true, repeats = 1))
        assertEquals(DropoutVerdict.Reopen(0, 1_000, 1), live(240_000, lastDrop = 180_000, seekable = true))
        assertEquals(DropoutVerdict.Reopen(0, 3_000, 2), live(1_500, retries = 1, lastDrop = 1_000, seekable = true, repeats = 1))
    }

    @Test
    fun aLiveStreamCutAfterTheSameTimeEachConnectionGoesOnUntilTheThirdCut() {
        // a server's listening limit or a token's expiry: an hour each time
        assertEquals(DropoutVerdict.Reopen(0, 1_000, 1), live(3_600_500, lastDrop = 3_600_000, repeats = 1))
        assertEquals(DropoutVerdict.End, live(3_600_500, lastDrop = 3_600_000, repeats = 2))
        // with an error it is a drop, however often
        assertEquals(DropoutVerdict.Reopen(0, 1_000, 1), live(3_600_500, error = true, lastDrop = 3_600_000, repeats = 5))
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

    @Test
    fun aStreamThatStillSoundsOrReadsIsNoStallWhileItsTimeIsFrozen() {
        val stall = StreamStall(20_000)
        assertFalse(stall.look(10_000, playing = true, nowMs = 0, counters = StreamCounters(1_000, 900, 10)))
        assertFalse(stall.look(11_000, playing = true, nowMs = 1_000, counters = StreamCounters(2_000, 1_900, 20)))
        // the time freezes; audio is still played, then only bytes still come: progress all the same
        for (second in 2..40) {
            assertFalse(stall.look(11_000, playing = true, nowMs = second * 1_000L, counters = StreamCounters(2_000, 1_900, 20L + second)))
        }
        for (second in 41..80) {
            assertFalse(stall.look(11_000, playing = true, nowMs = second * 1_000L, counters = StreamCounters(2_000L + second, 1_900, 60)))
        }
        // nothing moves any more: a stall after the limit
        assertFalse(stall.look(11_000, playing = true, nowMs = 99_999, counters = StreamCounters(2_080, 1_900, 60)))
        assertTrue(stall.look(11_000, playing = true, nowMs = 100_000, counters = StreamCounters(2_080, 1_900, 60)))
    }

    @Test
    fun aStreamWhoseCountersAndTimeStandStillHasStalled() {
        val stall = StreamStall(20_000)
        val frozen = StreamCounters(5_000, 4_000, 50)
        assertFalse(stall.look(10_000, playing = true, nowMs = 0, counters = StreamCounters(4_000, 3_000, 40), live = true))
        assertFalse(stall.look(11_000, playing = true, nowMs = 1_000, counters = frozen, live = true))
        assertFalse(stall.look(11_000, playing = true, nowMs = 20_999, counters = frozen, live = true))
        assertTrue(stall.look(11_000, playing = true, nowMs = 21_000, counters = frozen, live = true))
    }

    @Test
    fun withNoCountersALiveStreamWaitsLongerOnItsTimeAlone() {
        // none (null) or all 0: libVLC counts nothing for it
        for (counters in listOf(null, StreamCounters(0, 0, 0))) {
            val live = StreamStall(20_000)
            assertFalse(live.look(10_000, playing = true, nowMs = 0, counters = counters, live = true))
            assertFalse(live.look(11_000, playing = true, nowMs = 1_000, counters = counters, live = true))
            assertFalse(live.look(11_000, playing = true, nowMs = 45_999, counters = counters, live = true))
            assertTrue(live.look(11_000, playing = true, nowMs = 46_000, counters = counters, live = true))
            // an episode keeps the limit
            val episode = StreamStall(20_000)
            assertFalse(episode.look(10_000, playing = true, nowMs = 0, counters = counters))
            assertFalse(episode.look(11_000, playing = true, nowMs = 1_000, counters = counters))
            assertTrue(episode.look(11_000, playing = true, nowMs = 21_000, counters = counters))
        }
        // a longer caching's limit stays
        val cached = StreamStall(120_000)
        assertFalse(cached.look(0, playing = true, nowMs = 0, live = true))
        assertFalse(cached.look(1_000, playing = true, nowMs = 1_000, live = true))
        assertFalse(cached.look(1_000, playing = true, nowMs = 120_999, live = true))
        assertTrue(cached.look(1_000, playing = true, nowMs = 121_000, live = true))
    }
}
