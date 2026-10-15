package app.nagmedar.audio.latency

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * S-613: the test signal the measurement plays: [bursts] chirps of [burst]'s length, the first
 * [leadSamples] in, and then [gapSamples] apart, all at [rate]. [samples] is the whole, mono;
 * [burstStarts] where each chirp begins in it.
 */
class TestSignal(val samples: FloatArray, val burst: FloatArray, val burstStarts: IntArray, val rate: Int) {
    val bursts: Int get() = burstStarts.size
    val durationMs: Int get() = samples.size * 1000 / rate
}

/** Where one burst of the signal was found in a recording: at [at] (a sample, fractional), scored 0..1; [at] null when it is not there. */
data class BurstHit(val index: Int, val at: Double?, val score: Double)

/** What a set of per-burst delays agree on: the median, how far apart the ones kept were, and how many were thrown out. */
data class Measurement(val medianMs: Double, val spreadMs: Double, val keptMs: List<Double>, val rejected: Int)

/**
 * S-613: the measurement's signal processing, pure and deterministic: a chirp train, where each
 * chirp lands in a recording by normalised cross-correlation (the earliest strong peak, so the
 * direct sound wins over the room's echoes), and the delays combined with their outliers thrown
 * out. The tests play synthetic recordings through it; the device check is the real room.
 */
object LatencyDsp {
    /** The rate the measurement runs at, in and out. */
    const val RATE = 48_000

    const val BURSTS = 5

    /** The chirps are this far apart: more than the longest delay looked for, so each window holds one. */
    const val GAP_MS = 1_400

    /** The first chirp waits this long for the output to settle. */
    const val LEAD_MS = 600

    const val CHIRP_MS = 30

    /** The delays looked for; the lower bound takes a clock that runs slightly ahead. */
    const val MIN_SEARCH_MS = -100
    const val MAX_SEARCH_MS = 1_200

    /** The chirp's level: −6 dBFS, safe at any volume that is not full. */
    private const val AMPLITUDE = 0.5f

    private const val CHIRP_FROM_HZ = 800.0
    private const val CHIRP_TO_HZ = 8_000.0

    /** A chirp's peak scores at least this, or it is not there: noise alone never reaches it, a chirp under noise of its own power scores about 0,7. */
    private const val MIN_SCORE = 0.2

    /** The earliest local peak this near the strongest counts as the direct sound. */
    private const val DIRECT_FRACTION = 0.5

    /** Delays this far from the median are thrown out, at the least. */
    private const val OUTLIER_FLOOR_MS = 3.0

    /** A linear sweep from [from] to [to] Hz over [ms], under a Hann window so that it starts and ends in silence. */
    fun chirp(rate: Int = RATE, ms: Int = CHIRP_MS, from: Double = CHIRP_FROM_HZ, to: Double = CHIRP_TO_HZ, amplitude: Float = AMPLITUDE): FloatArray {
        val n = rate * ms / 1000
        val out = FloatArray(n)
        val seconds = n.toDouble() / rate
        val slope = (to - from) / seconds
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val phase = 2 * PI * (from * t + slope * t * t / 2)
            val window = 0.5 * (1 - cos(2 * PI * i / (n - 1)))
            out[i] = (amplitude * window * sin(phase)).toFloat()
        }
        return out
    }

    /** The whole signal: [bursts] chirps, the first after [leadMs], then [gapMs] apart, and a tail of [gapMs] so the last one is heard. */
    fun testSignal(rate: Int = RATE, bursts: Int = BURSTS, gapMs: Int = GAP_MS, leadMs: Int = LEAD_MS): TestSignal {
        val burst = chirp(rate)
        val gap = rate * gapMs / 1000
        val starts = IntArray(bursts) { rate * leadMs / 1000 + it * gap }
        val samples = FloatArray(starts.last() + gap)
        for (start in starts) System.arraycopy(burst, 0, samples, start, burst.size)
        return TestSignal(samples, burst, starts, rate)
    }

    /**
     * Where each chirp of [signal] lies in [recorded], which holds the signal's frame 0 at
     * [originSamples] (fractional; from the streams' timestamps, or the moment play started):
     * each is looked for from [minDelayMs] to [maxDelayMs] after where it was played.
     */
    fun findBursts(
        recorded: FloatArray,
        signal: TestSignal,
        originSamples: Double,
        minDelayMs: Int = MIN_SEARCH_MS,
        maxDelayMs: Int = MAX_SEARCH_MS,
    ): List<BurstHit> {
        val rate = signal.rate
        val energy = prefixEnergy(recorded)
        return signal.burstStarts.mapIndexed { index, start ->
            val expected = originSamples + start
            val from = (expected + minDelayMs * rate / 1000.0).toInt().coerceAtLeast(0)
            val to = (expected + maxDelayMs * rate / 1000.0).toInt().coerceAtMost(recorded.size - signal.burst.size)
            if (to <= from) return@mapIndexed BurstHit(index, null, 0.0)
            val scores = correlate(recorded, energy, signal.burst, from, to)
            val peak = pickPeak(scores)
            if (peak < 0) BurstHit(index, null, scores.maxOrNull()?.toDouble() ?: 0.0) else BurstHit(index, from + refine(scores, peak), scores[peak].toDouble())
        }
    }

    /** The delay of each burst found, ms: where it was heard less where it was played. */
    fun delaysMs(hits: List<BurstHit>, signal: TestSignal, originSamples: Double): List<Double> =
        hits.mapNotNull { hit -> hit.at?.let { (it - originSamples - signal.burstStarts[hit.index]) * 1000.0 / signal.rate } }

    /**
     * The delays combined: those farther from the median than three times the median deviation
     * (and at least [OUTLIER_FLOOR_MS]) are thrown out; null with fewer than two left, or fewer
     * than half of [total].
     */
    fun combine(delaysMs: List<Double>, total: Int = delaysMs.size): Measurement? {
        if (delaysMs.size < 2) return null
        val median = median(delaysMs)
        val mad = median(delaysMs.map { abs(it - median) })
        val limit = max(OUTLIER_FLOOR_MS, 3 * 1.4826 * mad)
        val kept = delaysMs.filter { abs(it - median) <= limit }
        if (kept.size < 2 || kept.size * 2 < total) return null
        return Measurement(median(kept), kept.max() - kept.min(), kept, delaysMs.size - kept.size)
    }

    /** Running sums of squares, so a window's energy is a subtraction. */
    private fun prefixEnergy(x: FloatArray): DoubleArray {
        val e = DoubleArray(x.size + 1)
        for (i in x.indices) e[i + 1] = e[i] + x[i].toDouble() * x[i]
        return e
    }

    /**
     * The normalised cross-correlation of [burst] against [x] at every lag from [from] to [to]:
     * 1 where the window is the burst at any level, near 0 over noise. [energy] is [x]'s
     * prefixEnergy.
     */
    internal fun correlate(x: FloatArray, energy: DoubleArray, burst: FloatArray, from: Int, to: Int): FloatArray {
        val n = burst.size
        var burstEnergy = 0.0
        for (b in burst) burstEnergy += b.toDouble() * b
        val out = FloatArray(to - from + 1)
        for (lag in from..to) {
            var sum = 0.0
            for (i in 0 until n) sum += x[lag + i].toDouble() * burst[i]
            val windowEnergy = energy[lag + n] - energy[lag]
            val norm = sqrt(windowEnergy * burstEnergy)
            out[lag - from] = if (norm > 1e-12) (sum / norm).toFloat() else 0f
        }
        return out
    }

    /**
     * The direct sound's peak: the earliest local maximum scoring at least [DIRECT_FRACTION] of
     * the strongest (an echo comes later and may be louder); −1 when nothing scores [MIN_SCORE].
     */
    internal fun pickPeak(scores: FloatArray): Int {
        var best = -1
        var bestScore = 0f
        for (i in scores.indices) if (scores[i] > bestScore) {
            bestScore = scores[i]
            best = i
        }
        if (best < 0 || bestScore < MIN_SCORE) return -1
        val threshold = bestScore * DIRECT_FRACTION
        for (i in scores.indices) {
            val s = scores[i]
            if (s < threshold) continue
            val leftOk = i == 0 || scores[i - 1] <= s
            val rightOk = i == scores.lastIndex || scores[i + 1] <= s
            if (leftOk && rightOk) return i
        }
        return best
    }

    /** The peak between the samples: a parabola through the three around [peak]. */
    private fun refine(scores: FloatArray, peak: Int): Double {
        if (peak <= 0 || peak >= scores.lastIndex) return peak.toDouble()
        val a = scores[peak - 1].toDouble()
        val b = scores[peak].toDouble()
        val c = scores[peak + 1].toDouble()
        val denominator = a - 2 * b + c
        if (abs(denominator) < 1e-12) return peak.toDouble()
        return peak + min(0.5, max(-0.5, 0.5 * (a - c) / denominator))
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
    }
}
