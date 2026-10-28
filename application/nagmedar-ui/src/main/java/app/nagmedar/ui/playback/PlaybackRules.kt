package app.nagmedar.ui.playback

import app.nagmedar.ui.podcast.Listening
import kotlin.math.abs

/**
 * S-1104: the rules of playing a podcast episode, apart from the player: where an episode starts (the
 * place it was left, or after the show's intro), when it counts as played, and where the show's outro
 * begins. Speeds are libVLC's rate (0.5 to 3).
 */
object PlaybackRules {
    const val MIN_SPEED = 0.5f
    const val MAX_SPEED = 3f

    /** The most seconds an intro or an outro skip may be: ten minutes. */
    const val MAX_SKIP_S = 600

    /** From this percent of its length an episode counts as played. */
    const val PLAYED_PERCENT = 95

    /** A saved place nearer the start than this is no place to resume from. */
    const val RESUME_MIN_MS = 5_000L

    /** Resuming goes back this far: the sentence the listener left in the middle of. */
    const val REWIND_MS = 3_000L

    /**
     * A saved place this near the end is no place to resume from either: the episode plays again ("Oynat"), not its
     * last seconds. Also how near its end a stream may stop and still have ended ([StreamDropout]).
     */
    const val RESUME_END_THRESHOLD_MS = 5_000L

    /** The speeds the buttons go through. */
    val SPEEDS: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

    /** Skip back and skip forward: the seconds the buttons offer. */
    val BACK_SECONDS: List<Int> = listOf(5, 10, 15, 30, 45, 60)
    val FORWARD_SECONDS: List<Int> = listOf(10, 15, 30, 45, 60, 90)

    /** The intro and outro lengths offered, in seconds; 0 skips nothing. */
    val SKIP_SECONDS: List<Int> = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120)

    fun clampSpeed(speed: Float): Float = speed.coerceIn(MIN_SPEED, MAX_SPEED)

    /** The speed after [speed] in [SPEEDS], or the same at the ends. */
    fun fasterThan(speed: Float): Float = SPEEDS.firstOrNull { it > speed + SPEED_EPSILON } ?: SPEEDS.last()

    fun slowerThan(speed: Float): Float = SPEEDS.lastOrNull { it < speed - SPEED_EPSILON } ?: SPEEDS.first()

    fun sameSpeed(a: Float, b: Float): Boolean = abs(a - b) < SPEED_EPSILON

    /**
     * Where an episode of [durationMs] starts (0 when unknown): after the intro, or where the listener left it,
     * [REWIND_MS] earlier. An episode that was played, or left in its outro, starts again after the intro.
     */
    fun startMs(positionMs: Long, played: Boolean, durationMs: Long, introSeconds: Int, outroSeconds: Int): Long {
        val intro = introSeconds.coerceAtLeast(0) * 1_000L
        val begin = if (durationMs > 0 && intro >= durationMs / 2) 0L else intro
        if (played || positionMs < RESUME_MIN_MS) return begin
        if (durationMs > 0 && isPlayed(positionMs, durationMs, outroSeconds)) return begin
        return maxOf(begin, positionMs - REWIND_MS)
    }

    /** Whether the episode is played when it has gone [timeMs] of [lengthMs]: the last 5 %, or inside the show's outro. */
    fun isPlayed(timeMs: Long, lengthMs: Long, outroSeconds: Int): Boolean {
        if (lengthMs <= 0) return false
        if (timeMs * 100 >= lengthMs * PLAYED_PERCENT) return true
        val outro = outroStartMs(lengthMs, outroSeconds)
        return outro != null && timeMs >= outro
    }

    /**
     * Where the outro of an episode of [lengthMs] begins; null with no outro skip, with no known length, or when the
     * outro would be more than half the episode (a setting for a longer show than this one).
     */
    fun outroStartMs(lengthMs: Long, outroSeconds: Int): Long? {
        if (outroSeconds <= 0 || lengthMs <= 0) return null
        val outro = outroSeconds * 1_000L
        return if (outro > lengthMs / 2) null else lengthMs - outro
    }

    /**
     * Where an episode's page says it is: the player's place once it has the episode at a real place or plays it, else
     * the saved one. A player that has the episode loaded but not started (its playlist brought back after a restart)
     * says 0 until it plays, and one that is opening it ([starting]) says where it opens; the saved place is the truth
     * then. A played episode starts again, so its saved place is none, nor is one in the last
     * [RESUME_END_THRESHOLD_MS] of its [durationMs] (0 when unknown).
     */
    fun shownPositionMs(savedMs: Long, played: Boolean, now: Listening?, starting: Boolean = false, durationMs: Long = 0L): Long {
        val player = now?.positionMs ?: 0L
        if (now != null && (player >= RESUME_MIN_MS || (now.playing && !starting))) return player
        if (!played && savedMs > player) return if (atTheEnd(savedMs, durationMs)) 0L else savedMs
        return player
    }

    /**
     * What an episode's play button offers, the one rule of its page and its rows: "Duraklat" while it plays or
     * opens, "Devam et · m:ss" from a place worth resuming ([shownPositionMs]), else "Oynat": also for a place saved in
     * the last [RESUME_END_THRESHOLD_MS] of its [durationMs].
     */
    fun playButton(savedMs: Long, played: Boolean, now: Listening?, starting: Boolean = false, durationMs: Long = 0L): PlayButton {
        if (starting) return PlayButton.Starting
        if (now?.playing == true) return PlayButton.Pause
        val at = shownPositionMs(savedMs, played, now, durationMs = durationMs)
        return if (at >= RESUME_MIN_MS) PlayButton.Resume(at) else PlayButton.Play
    }

    /** Whether [positionMs] is in the last [RESUME_END_THRESHOLD_MS] of a [durationMs] that is known. */
    private fun atTheEnd(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0L && durationMs - positionMs <= RESUME_END_THRESHOLD_MS

    private const val SPEED_EPSILON = 0.001f
}

/** S-205: what an episode's play button offers ([PlaybackRules.playButton]). */
sealed interface PlayButton {
    /** From the start, or after the show's intro: nothing to resume, or the episode was played. */
    data object Play : PlayButton

    /** From where it was left, [positionMs]. */
    data class Resume(val positionMs: Long) : PlayButton

    data object Pause : PlayButton

    /** Pressed, and the player is opening the episode: a pause button that waits, never back to resume meanwhile. */
    data object Starting : PlayButton
}
