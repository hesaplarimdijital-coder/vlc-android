package app.nagmedar.ui.playback

import kotlin.math.abs

/**
 * What an end or an error libVLC reports for a podcast episode is. libVLC ends a stream, or fails on it, when its
 * connection drops (the network lost, Wi-Fi handed over to mobile data) as it does at the episode's real end; before
 * the last [PlaybackRules.RESUME_END_THRESHOLD_MS] of a length that is known it is a dropout: the episode is not
 * played, it is opened again where it was, [MAX_RETRIES] times at most with [BACKOFF_MS] between, and then given up
 * with a notice. Pure: [app.nagmedar.ui.podcast.DropoutRecovery] gives the facts and does what comes back.
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
     */
    fun verdict(
        streamed: Boolean,
        positionMs: Long,
        durationMs: Long,
        retries: Int,
        error: Boolean,
        lastDropMs: Long = -1L,
        playedSinceReopen: Boolean = false,
    ): DropoutVerdict {
        if (!streamed || durationMs <= 0L) return DropoutVerdict.End
        if (positionMs >= durationMs - PlaybackRules.RESUME_END_THRESHOLD_MS) return DropoutVerdict.End
        if (!error && playedSinceReopen && lastDropMs >= 0L && abs(positionMs - lastDropMs) <= SAME_PLACE_MS) return DropoutVerdict.End
        if (retries >= MAX_RETRIES) return DropoutVerdict.GiveUp
        val at = (positionMs - PlaybackRules.REWIND_MS).coerceAtLeast(0L)
        return DropoutVerdict.Reopen(at, BACKOFF_MS[retries.coerceIn(0, BACKOFF_MS.lastIndex)], retries + 1)
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
