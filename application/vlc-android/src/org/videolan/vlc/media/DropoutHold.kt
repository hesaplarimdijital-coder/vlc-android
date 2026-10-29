package org.videolan.vlc.media

/** What libVLC's player was at a moment: [paused] or not, at [timeMs] of [lengthMs] (0: unknown). */
data class PlayerPlace(val paused: Boolean, val timeMs: Long, val lengthMs: Long)

/**
 * Nağmedar: the playlist's side of a stream that dropped ([DropoutGuard]). While the guard has an opening again
 * pending (libVLC stopped on the stream, which is not opened yet), the player is in one explicit state: playing, or
 * paused ([paused]), as it was before it dropped. Every "is it playing?" (the audio focus lost to a call, headphones
 * unplugged, the sleep timer, the buttons) sees that state; a pause makes the stream open paused, a stop leaves it, and
 * another item started leaves it too ([left]). Once the stream is opened, a pause or a play is libVLC's again. Kept by
 * [PlaylistManager]; main thread.
 */
class DropoutHold(private val guard: () -> DropoutGuard?) {
    /** Paused while the opening is pending (or paused when it dropped): it opens paused. */
    var paused = false
        private set

    /** Whether an opening again is pending: libVLC stopped on the stream, nothing plays. */
    val pending: Boolean get() = guard()?.reopenPending == true

    /**
     * libVLC ended ([error] false) or failed on the stream at [location], which was [before] it (its place and whether
     * it was paused, before an error reset them): true when the guard takes it over, to open it again.
     */
    fun dropped(location: String, before: PlayerPlace, error: Boolean): Boolean {
        val guard = guard() ?: return false
        // an opening pending already (libVLC's stop after its error) keeps the state it was given
        if (!guard.reopenPending) paused = before.paused
        val taken = try {
            guard.takesOver(location, before.timeMs, before.lengthMs, error)
        } catch (e: RuntimeException) {
            false
        }
        if (!taken) paused = false
        return taken
    }

    /** Play while the opening is pending: true when it was (it opens playing). */
    fun play(): Boolean {
        if (!pending) return false
        paused = false
        guard()?.playRequested()
        return true
    }

    /** Pause while the opening is pending: true when it was (it opens paused). */
    fun pause(): Boolean {
        if (!pending) return false
        paused = true
        return true
    }

    /** The stream is being opened again: whether it opens paused. */
    fun opening(): Boolean = paused.also { paused = false }

    /** The playlist stops: an opening pending is left. */
    fun stopped() {
        guard()?.stopped()
        paused = false
    }

    /** Another item starts: an opening pending for the one before is left. */
    fun left() {
        if (pending) stopped()
    }
}
