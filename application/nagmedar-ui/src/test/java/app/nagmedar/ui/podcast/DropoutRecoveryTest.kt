package app.nagmedar.ui.podcast

import app.nagmedar.ui.R
import app.nagmedar.ui.playback.StreamCounters
import app.nagmedar.ui.sleep.SleepTarget
import app.nagmedar.ui.sleep.SleepTimer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.vlc.media.DropoutHold
import org.videolan.vlc.media.PlayerPlace

/**
 * A stream that drops: [DropoutRecovery] with the playlist's [DropoutHold] over a fake player that does what
 * PlaylistManager's dropout paths do (play, pause, stop, libVLC's end or error, the opening again), and what the
 * service's guards do with it: the audio focus lost to a call, headphones unplugged, the sleep timer.
 */
class DropoutRecoveryTest {

    private enum class State { Playing, Paused, Stopped }

    /** The playlist and the service as the guard sees them; the paths are PlaylistManager's. */
    private inner class Player : DropoutHost {
        override var location: String? = RADIO
        var state = State.Playing
        var time = 0L
        var length = 0L
        val hold = DropoutHold { recovery }
        val reopens = mutableListOf<Pair<Long, Boolean>>()
        val notices = mutableListOf<Int>()
        var stops = 0
        var stalls = 0
        var network = true
        val waits = mutableListOf<Long>()
        private var gate = CompletableDeferred<Unit>()

        val isPlaying: Boolean get() = state == State.Playing

        /** The wait before the opening is over. */
        fun proceed() {
            val open = gate
            gate = CompletableDeferred()
            open.complete(Unit)
        }

        fun play() {
            if (hold.pending && hold.play()) {
                state = State.Playing
                return
            }
            if (location != null) state = State.Playing
        }

        fun pause() {
            if (hold.pending && hold.pause()) {
                state = State.Paused
                return
            }
            if (state == State.Playing) state = State.Paused
        }

        fun stopAll() {
            hold.stopped()
            state = State.Stopped
            location = null
            stops++
        }

        /** libVLC ends ([error] false) or fails on the stream: true when the guard takes it over. */
        fun drop(error: Boolean): Boolean {
            val before = PlayerPlace(state == State.Paused, time, length)
            // libVLC's error resets the player: stopped, at 0
            if (error) {
                state = State.Stopped
                time = 0L
            }
            val taken = hold.dropped(location!!, before, error)
            if (taken && hold.pending) {
                if (error) time = before.timeMs
                state = if (hold.paused) State.Paused else State.Playing
            }
            return taken
        }

        /** The observer's look, a second later. */
        fun look(at: Long, context: EpisodeContext? = null) {
            time = at
            clock += 1_000L
            recovery.look(location, context, time, length, isPlaying, clock)
        }

        override val paused: Boolean get() = state == State.Paused
        override val reopenPaused: Boolean get() = hold.paused
        override var seekable = false
        override val networkCachingMs = 0

        override fun counters(): StreamCounters? = null

        override fun reopen(atMs: Long): Boolean {
            val paused = hold.opening()
            reopens += atMs to paused
            state = if (paused) State.Paused else State.Playing
            time = atMs
            return true
        }

        override fun dropStalled(): Boolean {
            stalls++
            return true
        }

        override fun stop() = stopAll()

        override fun changed() {
            if (hold.pending) state = if (hold.paused) State.Paused else State.Playing
        }

        override fun notice(text: Int) {
            notices += text
        }

        override suspend fun wait(ms: Long) {
            waits += ms
            gate.await()
        }

        override suspend fun awaitNetwork(timeoutMs: Long): Boolean = network
    }

    private var clock = 1_000_000L
    private var reopenings = 0
    private val player = Player()
    private val recovery = DropoutRecovery(player, CoroutineScope(Dispatchers.Unconfined)) { reopenings++ }

    /** The radio plays a minute. */
    private fun playRadio(toMs: Long = 60_000L) {
        var at = 1_000L
        while (at <= toMs) {
            player.look(at)
            at += 1_000L
        }
    }

    /** What the focus helper does on a call: it pauses what plays, and plays it again after. */
    private var wasPlaying = false

    private fun callStarts() {
        wasPlaying = player.isPlaying
        if (wasPlaying) player.pause()
    }

    private fun callEnds() {
        if (wasPlaying) player.play()
    }

    @Test
    fun anErrorWhileItPlaysLooksPlayingWhileItWaitsAndOpensPlaying() {
        playRadio()
        assertTrue(player.drop(error = true))
        assertEquals("one state while it waits: as it was", State.Playing, player.state)
        assertTrue(recovery.reopenPending)
        assertTrue(recovery.recovering)
        // libVLC's stop after its error is the same dropout
        assertTrue(player.drop(error = false))
        assertEquals(listOf(1_000L), player.waits)
        player.proceed()
        assertEquals(listOf(0L to false), player.reopens)
        assertEquals(1, reopenings)
        assertFalse(recovery.reopenPending)
        assertTrue("held until it really plays again", recovery.recovering)
        player.look(500L)
        player.look(1_500L)
        assertFalse(recovery.recovering)
    }

    @Test
    fun aCallWhileItWaitsPausesItAndItOpensPaused() {
        playRadio()
        player.drop(error = true)
        callStarts()
        assertEquals(State.Paused, player.state)
        assertTrue(player.hold.paused)
        player.proceed()
        assertEquals("never over the call", listOf(0L to true), player.reopens)
        assertEquals(State.Paused, player.state)
        assertFalse("paused, the service lets go", recovery.recovering)
        callEnds()
        assertEquals(State.Playing, player.state)
    }

    @Test
    fun headphonesUnpluggedWhileItWaitsPauseIt() {
        playRadio()
        player.drop(error = false)
        // the noisy broadcast: what plays is paused
        if (player.isPlaying) player.pause()
        player.proceed()
        assertEquals("never on the loudspeaker", listOf(0L to true), player.reopens)
        assertEquals(State.Paused, player.state)
    }

    @Test
    fun aStreamThatDropsWhilePausedOpensPaused() {
        playRadio()
        player.pause()
        assertTrue(player.drop(error = true))
        assertEquals(State.Paused, player.state)
        player.proceed()
        assertEquals(listOf(0L to true), player.reopens)
    }

    @Test
    fun aPlayWhileItWaitsOpensItPlaying() {
        playRadio()
        player.pause()
        player.drop(error = true)
        player.play()
        assertEquals(State.Playing, player.state)
        player.proceed()
        assertEquals(listOf(0L to false), player.reopens)
    }

    @Test
    fun theSleepTimerStopsAStreamThatWaits() {
        val now = clock
        val timer = SleepTimer(
            target = {
                object : SleepTarget {
                    override val playing: Boolean get() = player.isPlaying
                    override val loaded: Boolean get() = player.location != null
                    override val volume = 100
                    override fun setVolume(volume: Int) = Unit
                    override val trackLeftMs: Long? = null
                    override fun stop() = player.stopAll()
                    override fun stopAtTrackEnd() = false
                    override fun goOnAfterTrack() = Unit
                }
            },
            scope = CoroutineScope(Job().apply { cancel() }),
            now = { clock },
        )
        timer.start(minutes = 1, atTrackEnd = false, fade = false)
        playRadio()
        player.drop(error = true)
        clock = now + 61_000L
        assertFalse(timer.tick())
        assertEquals(1, player.stops)
        assertFalse(recovery.reopenPending)
        player.proceed()
        assertEquals("the stream never comes back by itself", emptyList<Pair<Long, Boolean>>(), player.reopens)
    }

    @Test
    fun aStopWhileItWaitsLeavesIt() {
        playRadio()
        player.drop(error = true)
        player.stopAll()
        player.proceed()
        assertTrue(player.reopens.isEmpty())
        assertFalse(recovery.recovering)
    }

    @Test
    fun aPauseOnceItIsOpenedIsARealPauseAndNoStallIsSeen() {
        playRadio()
        player.drop(error = true)
        player.proceed()
        // connecting: libVLC's time stands at 0; the listener pauses
        player.pause()
        assertFalse("not the dropout's: libVLC's own pause", player.hold.paused)
        assertEquals(State.Paused, player.state)
        assertFalse("the service lets go of a paused stream", recovery.recovering)
        repeat(60) { player.look(0L) }
        assertEquals("a paused stream is not reconnected", 0, player.stalls)
    }

    @Test
    fun aStreamThatPlaysWithNothingComingIsStoppedAndOpenedAgain() {
        playRadio()
        repeat(50) { player.look(60_000L) }
        assertEquals(1, player.stalls)
        assertTrue(recovery.reopenPending)
        assertEquals(State.Playing, player.state)
        // libVLC's stop on it is swallowed
        assertTrue(player.drop(error = false))
        player.proceed()
        assertEquals(listOf(0L to false), player.reopens)
    }

    @Test
    fun anEndOfAnEpisodeIsItsEndAndAnEndBeforeIsADropout() {
        player.location = EPISODE
        player.length = 3_600_000L
        val context = EpisodeContext(1, 1, 1f, 0, 0, 0L, false, 3_600_000L)
        player.look(1_200_000L, context)
        player.look(1_201_000L, context)
        assertTrue(player.drop(error = false))
        player.proceed()
        assertEquals(listOf(1_198_000L to false), player.reopens)
        player.look(1_199_000L, context)
        player.look(3_599_000L, context)
        assertFalse("the episode's own end", player.drop(error = false))
    }

    @Test
    fun aRadioThatNeverPlayedEndsAsBefore() {
        player.look(0L)
        assertFalse(player.drop(error = true))
        assertFalse(recovery.reopenPending)
    }

    @Test
    fun givenUpWhilePausedItStaysLoadedAndPausedAndPlayOpensIt() {
        playRadio()
        player.network = false
        player.drop(error = true)
        player.pause()
        player.proceed()
        assertEquals(0, player.stops)
        assertEquals(State.Paused, player.state)
        assertEquals(listOf(R.string.nag_live_dropout_paused), player.notices)
        assertTrue(recovery.reopenPending)
        assertFalse(recovery.recovering)
        player.network = true
        player.play()
        assertEquals(listOf(0L to false), player.reopens)
        assertEquals(State.Playing, player.state)
    }

    @Test
    fun givenUpWhilePlayingItStops() {
        playRadio()
        player.network = false
        player.drop(error = true)
        player.proceed()
        assertEquals(1, player.stops)
        assertEquals(listOf(R.string.nag_live_dropout_gave_up), player.notices)
    }

    /** The radio cut at the same place of each connection, [times] times; whether the last cut ended it. */
    private fun cutAtTheSamePlace(times: Int): Boolean {
        playRadio()
        repeat(times - 1) {
            assertTrue(player.drop(error = false))
            player.proceed()
            playRadio()
        }
        return !player.drop(error = false)
    }

    @Test
    fun aLiveStreamCutAtTheSamePlaceGoesOnUntilTheThirdTime() {
        assertFalse("a server's cut after a set time, not a file's end", cutAtTheSamePlace(2))
        player.proceed()
        playRadio()
        assertFalse("the third time it ends", player.drop(error = false))
        assertEquals(listOf(0L to false, 0L to false), player.reopens)
    }

    @Test
    fun aStreamThatCanBeSoughtInAndEndsAtTheSamePlaceIsAFileThatEnded() {
        player.seekable = true
        assertTrue(cutAtTheSamePlace(2))
    }

    private companion object {
        const val RADIO = "https://radio.example.org/live"
        const val EPISODE = "https://example.org/episode.mp3"
    }
}
