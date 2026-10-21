package app.nagmedar.ui.podcast

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

    private const val SPEED_EPSILON = 0.001f
}
