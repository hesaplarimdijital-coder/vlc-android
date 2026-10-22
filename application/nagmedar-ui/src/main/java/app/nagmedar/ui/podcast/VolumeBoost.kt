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
 * percent over the listener's own when a boosted episode plays, and put back to the listener's when the episode ends,
 * another media follows, or the engine changes. The listener's own changes meanwhile come through [listenerSets]:
 * the boost goes on over the volume they chose, which is theirs, not the boosted one. Anyone else's (the sleep
 * timer's fade, the system's ducking) has the engine while it lasts; whatever it leaves, the listener's volume comes
 * back at the end, so nothing but the episode ever plays boosted. No clock and no service: [read] and [write] are the
 * engine's volume, so the rules are testable.
 */
class VolumeBooster<E : Any>(private val read: (E) -> Int, private val write: (E, Int) -> Unit) {
    private var held: E? = null

    /** The listener's own volume while the boost holds the engine. */
    private var base = 0
    private var applied = 0
    private var appliedPercent = 100

    /**
     * One look: [engine] is what plays now (null for nothing), [episodeId] the podcast episode it plays (null for any
     * other media) and [boost] its show's step.
     */
    @Synchronized
    fun update(engine: E?, episodeId: Long?, boost: Int) {
        val percent = if (engine == null || episodeId == null) 100 else VolumeBoost.percent(boost)
        val current = held
        if (current != null) {
            when {
                current !== engine -> {
                    // the volume goes with the player from one engine to the other: both are put back
                    release(current)
                    if (engine != null && same(read(engine), applied)) write(engine, base)
                }
                percent == 100 -> {
                    release(current)
                    return
                }
                // a fade or the system's ducking has the volume for now: the boost waits until it gives it back
                !same(read(current), applied) -> return
                else -> {
                    if (percent != appliedPercent) raise(current, percent)
                    return
                }
            }
        }
        if (engine == null || percent <= 100) return
        val volume = read(engine)
        if (volume <= 0) return
        base = volume
        raise(engine, percent)
        held = engine
    }

    /**
     * The listener sets their volume to [volume] (the bars' slider, the volume keys, mute): while the boost holds the
     * engine it goes on over [volume], which becomes the one given back at the end; the engine's volume for it is
     * returned, for the caller to write. Without a boost [volume] itself.
     */
    @Synchronized
    fun listenerSets(volume: Int): Int {
        if (held == null) return volume
        base = volume
        applied = boosted(volume, appliedPercent)
        return applied
    }

    /** The listener's own volume while the engine plays at [engineVolume]: without the boost, while one holds it. */
    @Synchronized
    fun listenerVolume(engineVolume: Int): Int = if (held != null && same(engineVolume, applied)) base else engineVolume

    /** The playback is going, or another media starts: the listener's volume. Safe from any thread. */
    @Synchronized
    fun end() {
        held?.let(::release)
    }

    private fun raise(engine: E, percent: Int) {
        applied = boosted(base, percent)
        appliedPercent = percent
        if (applied != base) write(engine, applied)
    }

    private fun boosted(volume: Int, percent: Int): Int = maxOf((volume.toLong() * percent / 100).toInt().coerceAtMost(VolumeBoost.MAX_PERCENT), volume)

    /** The listener's volume back on [engine], whatever a fade or the ducking left there. */
    private fun release(engine: E) {
        if (read(engine) != base) write(engine, base)
        held = null
    }

    private fun same(a: Int, b: Int): Boolean = abs(a - b) <= 1
}
