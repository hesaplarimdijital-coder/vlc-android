package app.nagmedar.ui.visual

import app.nagmedar.audio.ScopeFrame
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * S-611: one channel of the level meter, with a meter's ballistics rather than the raw reads: the
 * RMS rises with a 65 ms time constant and falls with 300 ms (a VU meter's 300 ms to 99 %); the
 * peak jumps up at once and falls 11.8 dB a second (IEC 60268-10 type I: 20 dB in 1.7 s); the
 * highest peak is held 1.5 s, then falls 20 dB a second; a peak at full scale lights the clip mark
 * for 2 s. Levels are dBFS, [ScopeFrame.FLOOR_DB] for silence.
 */
class LevelBallistics {
    var rmsDb: Float = ScopeFrame.FLOOR_DB
        private set
    var peakDb: Float = ScopeFrame.FLOOR_DB
        private set
    var holdDb: Float = ScopeFrame.FLOOR_DB
        private set
    private var holdAge = 0f
    private var clipAge = CLIP_SECONDS

    /** A peak reached full scale within the last two seconds. */
    val clipped: Boolean get() = clipAge < CLIP_SECONDS

    /** Everything has fallen below what a meter shows. */
    val atRest: Boolean get() = rmsDb <= REST_DB && peakDb <= REST_DB && holdDb <= REST_DB && !clipped

    /** A new read ([peakDb], [rmsDb]; null: silence), [seconds] after the one before. */
    fun update(peakIn: Float?, rmsIn: Float?, seconds: Float) {
        val dt = seconds.coerceIn(0f, 1f)
        val peak = peakIn ?: ScopeFrame.FLOOR_DB
        val rms = rmsIn ?: ScopeFrame.FLOOR_DB
        // the RMS in power, smoothed: rising faster than falling
        val tau = if (rms > rmsDb) RMS_ATTACK else RMS_RELEASE
        val k = 1f - exp(-dt / tau)
        val power = power(rmsDb) + (power(rms) - power(rmsDb)) * k
        rmsDb = db(power)
        peakDb = max(peak, peakDb - PEAK_FALL_DB * dt).coerceAtLeast(ScopeFrame.FLOOR_DB)
        if (peak >= holdDb) {
            holdDb = peak
            holdAge = 0f
        } else {
            holdAge += dt
            // it falls for the part of [dt] past the hold
            val falling = min(dt, holdAge - HOLD_SECONDS)
            if (falling > 0f) holdDb = max(peakDb, holdDb - HOLD_FALL_DB * falling)
        }
        clipAge = if (peak >= CLIP_DB) 0f else min(CLIP_SECONDS, clipAge + dt)
    }

    /** Straight to a read, no ballistics: a still frame (screenshots, a pane opening on a paused track). */
    fun set(peakIn: Float, rmsIn: Float) {
        peakDb = peakIn
        rmsDb = rmsIn
        holdDb = peakIn
        holdAge = 0f
        clipAge = if (peakIn >= CLIP_DB) 0f else CLIP_SECONDS
    }

    companion object {
        const val RMS_ATTACK = 0.065f
        const val RMS_RELEASE = 0.3f
        const val PEAK_FALL_DB = 11.8f
        const val HOLD_SECONDS = 1.5f
        const val HOLD_FALL_DB = 20f
        const val CLIP_DB = -0.1f
        const val CLIP_SECONDS = 2f

        /** Below this a meter shows nothing. */
        const val REST_DB = -90f

        private fun power(db: Float): Float = if (db <= ScopeFrame.FLOOR_DB) 0f else 10f.pow(db / 10f)

        private fun db(power: Float): Float = if (power <= 1e-12f) ScopeFrame.FLOOR_DB else max(10f * log10(power), ScopeFrame.FLOOR_DB)
    }
}

/** S-611: the stereo level meter: both channels' [LevelBallistics]. */
class LevelMeter {
    val left = LevelBallistics()
    val right = LevelBallistics()

    val atRest: Boolean get() = left.atRest && right.atRest

    fun update(frame: ScopeFrame?, seconds: Float) {
        left.update(frame?.peakDb?.getOrNull(0), frame?.rmsDb?.getOrNull(0), seconds)
        right.update(frame?.peakDb?.getOrNull(1), frame?.rmsDb?.getOrNull(1), seconds)
    }

    fun set(frame: ScopeFrame) {
        left.set(frame.peakDb[0], frame.rmsDb[0])
        right.set(frame.peakDb[1], frame.rmsDb[1])
    }
}

/**
 * S-611: the spectrum's bars over time: a bar rises at once and falls 40 dB a second; its cap, the
 * highest it reached, stays 0.5 s and falls 20 dB a second.
 */
class SpectrumBallistics(val count: Int) {
    val bars = FloatArray(count) { ScopeFrame.FLOOR_DB }
    val caps = FloatArray(count) { ScopeFrame.FLOOR_DB }
    private val capAge = FloatArray(count)

    val atRest: Boolean get() = bars.all { it <= SpectrumBands.FLOOR_SHOWN } && caps.all { it <= SpectrumBands.FLOOR_SHOWN }

    /** The bands' dB now ([bands]: null for silence), [seconds] after the read before. */
    fun update(bands: FloatArray?, seconds: Float) {
        val dt = seconds.coerceIn(0f, 1f)
        for (i in 0 until count) {
            val target = bands?.getOrNull(i) ?: ScopeFrame.FLOOR_DB
            bars[i] = max(target, bars[i] - BAR_FALL_DB * dt).coerceAtLeast(ScopeFrame.FLOOR_DB)
            if (bars[i] >= caps[i]) {
                caps[i] = bars[i]
                capAge[i] = 0f
            } else {
                capAge[i] += dt
                val falling = min(dt, capAge[i] - CAP_SECONDS)
                if (falling > 0f) caps[i] = max(bars[i], caps[i] - CAP_FALL_DB * falling)
            }
        }
    }

    /** Straight to [bands]: a still frame. */
    fun set(bands: FloatArray) {
        for (i in 0 until count) {
            bars[i] = bands.getOrElse(i) { ScopeFrame.FLOOR_DB }
            caps[i] = bars[i]
            capAge[i] = 0f
        }
    }

    companion object {
        const val BAR_FALL_DB = 40f
        const val CAP_SECONDS = 0.5f
        const val CAP_FALL_DB = 20f
    }
}

/**
 * S-611: the spectrum's bins grouped into bands spaced evenly on a log scale, as the ear hears
 * them: a band is its loudest bin; a band narrower than a bin (the bass at a short FFT) is the
 * spectrum between its two nearest bins at its centre.
 */
object SpectrumBands {

    /** The lowest level a bar shows: the spectrum's scale runs from here to 0 dBFS. */
    const val FLOOR_SHOWN = -84f

    /** The [count] + 1 edges from [minHz] to [maxHz], each band the same ratio wide. */
    fun edges(count: Int, minHz: Float, maxHz: Float): FloatArray {
        val ratio = ln(maxHz / minHz) / count
        return FloatArray(count + 1) { i -> minHz * exp(ratio * i) }
    }

    /** The bands' centres (the geometric middle of their edges). */
    fun centres(count: Int, minHz: Float = MIN_HZ, maxHz: Float = MAX_HZ): FloatArray {
        val e = edges(count, minHz, maxHz)
        return FloatArray(count) { i -> kotlin.math.sqrt(e[i] * e[i + 1]) }
    }

    /** [frame]'s spectrum as [count] bands between [minHz] and [maxHz] (at most half its rate). */
    fun bands(frame: ScopeFrame, count: Int, minHz: Float = MIN_HZ, maxHz: Float = MAX_HZ): FloatArray {
        val spectrum = frame.spectrumDb
        val binHz = frame.binHz
        if (spectrum.isEmpty() || binHz <= 0f) return FloatArray(count) { ScopeFrame.FLOOR_DB }
        val top = min(maxHz, frame.rate / 2f)
        val e = edges(count, minHz, top)
        return FloatArray(count) { i ->
            val from = kotlin.math.ceil(e[i] / binHz).toInt().coerceAtLeast(1)
            val to = (kotlin.math.ceil(e[i + 1] / binHz).toInt() - 1).coerceAtMost(spectrum.size - 1)
            if (from <= to) {
                var loudest = ScopeFrame.FLOOR_DB
                for (k in from..to) loudest = max(loudest, spectrum[k])
                loudest
            } else {
                val centre = kotlin.math.sqrt(e[i] * e[i + 1]) / binHz
                val k = centre.toInt().coerceIn(0, spectrum.size - 2)
                val f = (centre - k).coerceIn(0f, 1f)
                spectrum[k] + (spectrum[k + 1] - spectrum[k]) * f
            }
        }
    }

    const val MIN_HZ = 25f
    const val MAX_HZ = 20_000f
}
