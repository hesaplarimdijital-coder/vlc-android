package app.nagmedar.ui.podcast

import kotlin.math.abs

/**
 * S-1104: a show's volume boost ("Ses yükseltme"): off, light or strong. The playback engines take a volume in percent,
 * 100 being the samples as they are and 200 the most (libVLC's own audio boost, the music engine's gain); a boosted show
 * plays at that much more than the volume the listener has set, so quiet recordings do not need the phone's volume
 * turned up past what other media bear. Loud passages may distort: that is said in the show's settings.
 */
object VolumeBoost {
    const val OFF = 0
    const val LIGHT = 1
    const val STRONG = 2

    /** The steps in the order the settings list them. */
    val STEPS: List<Int> = listOf(OFF, LIGHT, STRONG)

    /** The engines' limit. */
    const val MAX_PERCENT = 200

    /** How much of the listener's volume a step plays at, in percent. */
    fun percent(boost: Int): Int = when (boost) {
        LIGHT -> 130
        STRONG -> 170
        else -> 100
    }

    fun clamp(boost: Int): Int = boost.coerceIn(OFF, STRONG)
}

/**
 * S-1104: applies the boost to a playback engine [E] and gives it back: the engine's volume is raised by the step's
 * percent when a boosted episode plays, and put as it was when the episode ends, another media follows, or the
 * engine changes. A volume the listener (or the sleep timer's fade) sets meanwhile is theirs: it is left alone for
 * that episode. No clock and no service: [read] and [write] are the engine's volume, so the rules are testable.
 */
class VolumeBooster<E : Any>(private val read: (E) -> Int, private val write: (E, Int) -> Unit) {
    private var held: E? = null
    private var base = 0
    private var applied = 0
    private var appliedPercent = 100
    private var overriddenFor: Long? = null

    /**
     * One look: [engine] is what plays now (null for nothing), [episodeId] the podcast episode it plays (null for any
     * other media) and [boost] its show's step.
     */
    fun update(engine: E?, episodeId: Long?, boost: Int) {
        val percent = if (engine == null || episodeId == null) 100 else VolumeBoost.percent(boost)
        if (episodeId != overriddenFor) overriddenFor = null
        val current = held
        if (current != null) {
            if (current !== engine) {
                // the volume goes with the player from one engine to the other: both are put back
                release(current)
                if (engine != null && same(read(engine), applied)) write(engine, base)
            } else if (!same(read(current), applied)) {
                held = null
                overriddenFor = episodeId
                return
            } else if (percent == 100) {
                release(current)
                return
            } else {
                if (percent != appliedPercent) raise(current, percent)
                return
            }
        }
        if (engine == null || percent <= 100 || overriddenFor != null) return
        val volume = read(engine)
        if (volume <= 0) return
        base = volume
        raise(engine, percent)
        held = engine
    }

    /** The playback is going: the volume as it was. */
    fun end() {
        held?.let(::release)
    }

    private fun raise(engine: E, percent: Int) {
        val target = (base.toLong() * percent / 100).toInt().coerceAtMost(VolumeBoost.MAX_PERCENT)
        applied = maxOf(target, base)
        appliedPercent = percent
        if (applied != base) write(engine, applied)
    }

    private fun release(engine: E) {
        if (same(read(engine), applied)) write(engine, base)
        held = null
    }

    private fun same(a: Int, b: Int): Boolean = abs(a - b) <= 1
}
