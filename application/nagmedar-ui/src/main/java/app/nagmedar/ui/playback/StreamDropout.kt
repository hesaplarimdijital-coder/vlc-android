package app.nagmedar.ui.playback

import kotlin.math.abs

/**
 * What an end or an error libVLC reports for a podcast episode is. libVLC ends a stream, or fails on it, when its
 * connection drops (the network lost, Wi-Fi handed over to mobile data) as it does at the episode's real end; before
 * the last [PlaybackRules.RESUME_END_THRESHOLD_MS] of a length that is known it is a dropout: the episode is not
 * played, it is opened again where it was, [MAX_RETRIES] times at most with [BACKOFF_MS] between, and then given up
 * with a notice. A live stream (a radio station, a stream with no length that is not an episode) has no end: once it
 * played, its end or error is a dropout too, and it is connected to again, the same times. Pure:
 * [app.nagmedar.ui.podcast.DropoutRecovery] gives the facts and does what comes back.
 */
object StreamDropout {
    const val MAX_RETRIES = 3

    /** The wait before each opening again, by the openings already made. */
    val BACKOFF_MS: List<Long> = listOf(1_000L, 3_000L, 8_000L)

    /** The most an opening again waits for a network to come back while there is none. */
    const val OFFLINE_WAIT_MS = 120_000L

    /** Played on this far past the place it was opened again at: the dropout is over, the openings counted afresh. */
    const val RECOVERED_MS = 10_000L

    /** An end this near the place of the dropout before it is the same end. */
    const val SAME_PLACE_MS = 2_000L

    /**
     * The verdict on an end ([error] false) or an error of an episode. [streamed] says it plays from the network (a
     * downloaded file has no dropouts); [positionMs] is the furthest place it played at, [durationMs] its length (0:
     * unknown, every end is the end); [retries] the openings again made since it last played on, [lastDropMs] the place
     * of the dropout the last one was for (-1: none) and [playedSinceReopen] whether it played since. An end without an
     * error where the stream ended before, after it played again, is the stream's own end (its length was an estimate).
     * Not an [episode] and with no length, the stream is live ([live]); not an episode with a length, it is not ours.
     */
    fun verdict(
        streamed: Boolean,
        positionMs: Long,
        durationMs: Long,
        retries: Int,
        error: Boolean,
        lastDropMs: Long = -1L,
        playedSinceReopen: Boolean = false,
        episode: Boolean = true,
    ): DropoutVerdict {
        if (!streamed) return DropoutVerdict.End
        if (!episode) return if (durationMs <= 0L) live(positionMs, retries, error, lastDropMs) else DropoutVerdict.End
        if (durationMs <= 0L) return DropoutVerdict.End
        if (positionMs >= durationMs - PlaybackRules.RESUME_END_THRESHOLD_MS) return DropoutVerdict.End
        if (!error && playedSinceReopen && lastDropMs >= 0L && abs(positionMs - lastDropMs) <= SAME_PLACE_MS) return DropoutVerdict.End
        if (retries >= MAX_RETRIES) return DropoutVerdict.GiveUp
        val at = (positionMs - PlaybackRules.REWIND_MS).coerceAtLeast(0L)
        return DropoutVerdict.Reopen(at, BACKOFF_MS[retries.coerceIn(0, BACKOFF_MS.lastIndex)], retries + 1)
    }

    /**
     * The verdict on an end or an error of a live stream, [positionMs] into its connection (its time starts at 0 on each).
     * One that never played, and is not being connected to again, ends as it always did: an address that holds a playlist
     * (its stations are played next), or one that does not answer. One that ends without an error where it ended last
     * time ([lastDropMs]), well after its start, is a file with no length that ended, not a drop. Else it is connected to
     * again ([DropoutVerdict.Reopen] at 0: its live edge), [MAX_RETRIES] times at most.
     */
    private fun live(positionMs: Long, retries: Int, error: Boolean, lastDropMs: Long): DropoutVerdict {
        if (positionMs <= 0L && retries == 0) return DropoutVerdict.End
        if (!error && lastDropMs >= RECOVERED_MS && abs(positionMs - lastDropMs) <= SAME_PLACE_MS) return DropoutVerdict.End
        if (retries >= MAX_RETRIES) return DropoutVerdict.GiveUp
        return DropoutVerdict.Reopen(0L, BACKOFF_MS[retries.coerceIn(0, BACKOFF_MS.lastIndex)], retries + 1)
    }
}

/**
 * A network stream that plays with nothing coming: libVLC keeps the connection open and says it buffers or plays while
 * its time stands still. No progress for [limitMs] while it plays (not paused, not waiting to be opened again), once its
 * time has moved, is a stall, taken as a dropout ([StreamDropout]); a seek moves the time, so it is progress. A stream
 * that never moved (still opening) is left to libVLC. Fed a look a second by [app.nagmedar.ui.podcast.DropoutRecovery];
 * pure.
 */
class StreamStall(private val limitMs: Long) {
    private var lastTimeMs = -1L
    private var sinceMs = -1L
    private var moved = false

    /**
     * One look: at [nowMs] (a monotonic clock) the stream is at [timeMs], and [playing] says whether it plays and counts.
     * True when it stalled; the wait then starts again.
     */
    fun look(timeMs: Long, playing: Boolean, nowMs: Long): Boolean {
        if (!playing) {
            sinceMs = -1L
            return false
        }
        if (sinceMs < 0L || timeMs != lastTimeMs) {
            if (sinceMs >= 0L) moved = true
            lastTimeMs = timeMs
            sinceMs = nowMs
            return false
        }
        if (!moved || nowMs - sinceMs < limitMs) return false
        sinceMs = -1L
        return true
    }

    companion object {
        /** The least time with no progress that is a stall. */
        const val MIN_STALL_MS = 20_000L

        /** The most network caching VLCOptions gives libVLC, ms. */
        private const val MAX_CACHING_MS = 60_000

        /**
         * The stall time for libVLC's network caching of [networkCachingMs] (Settings' value, 0: libVLC's default of a
         * second): refilling a cache that ran dry takes about its length, so twice the caching, [MIN_STALL_MS] at least.
         */
        fun limitMs(networkCachingMs: Int): Long = maxOf(MIN_STALL_MS, networkCachingMs.coerceIn(0, MAX_CACHING_MS) * 2L)
    }
}

/** What [StreamDropout.verdict] says of an end or an error. */
sealed interface DropoutVerdict {
    /** The episode's end (or a media that has no dropouts): the playlist does as it always does. */
    data object End : DropoutVerdict

    /** A dropout: opened again at [atMs] (a few seconds before the place it reached) after [delayMs]; the [attempt]th time. */
    data class Reopen(val atMs: Long, val delayMs: Long, val attempt: Int) : DropoutVerdict

    /** A dropout the openings again did not get over: stopped, the place kept, a notice. */
    data object GiveUp : DropoutVerdict
}
