package app.nagmedar.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * S-611: what the visualisers draw at one moment: the spectrum of the last ~43 ms, each
 * channel's peak and RMS, and ~20 ms of the waveform from a rising zero crossing. The music
 * engine's tap makes it natively (native/audio/scope.c); [ScopeAnalysis] makes the same from
 * samples got elsewhere (the system's visualiser on libVLC's output).
 */
class ScopeFrame(
    val rate: Int,
    val fftSize: Int,
    /** fftSize / 2 bins in dB of a sine's amplitude: bin k at k · rate / fftSize Hz. */
    val spectrumDb: FloatArray,
    /** The largest sample of each channel since the frame before, dBFS. */
    val peakDb: FloatArray,
    /** Each channel's RMS over the window, dBFS (a full-scale sine reads -3.01). */
    val rmsDb: FloatArray,
    val waveLeft: FloatArray,
    val waveRight: FloatArray,
    /** Frames taken since the tap was turned on; the same as the frame before's when nothing played since. */
    val frames: Long,
) {
    val binHz: Float get() = if (fftSize > 0) rate.toFloat() / fftSize else 0f

    companion object {
        /** The floor every level is held at: silence. */
        const val FLOOR_DB = -120f

        /** Places in NativeAudio.scope's array. */
        const val HEADER = 8

        /** The array NativeAudio.scope fills: the header, the largest spectrum and two waveforms. */
        fun buffer(): FloatArray = FloatArray(HEADER + NativeAudio.SCOPE_FFT_MAX / 2 + 2 * NativeAudio.SCOPE_WAVE_MAX)

        /** The frame in [buffer] as NativeAudio.scope left it, [frames] its answer. */
        fun decode(buffer: FloatArray, frames: Long): ScopeFrame {
            val rate = buffer[0].toInt()
            val fft = buffer[1].toInt()
            val bins = fft / 2
            val points = buffer[6].toInt()
            return ScopeFrame(
                rate = rate,
                fftSize = fft,
                spectrumDb = buffer.copyOfRange(HEADER, HEADER + bins),
                peakDb = floatArrayOf(buffer[2], buffer[3]),
                rmsDb = floatArrayOf(buffer[4], buffer[5]),
                waveLeft = buffer.copyOfRange(HEADER + bins, HEADER + bins + points),
                waveRight = buffer.copyOfRange(HEADER + bins + points, HEADER + bins + 2 * points),
                frames = frames,
            )
        }

        /** Nothing heard: every level at the floor, a flat line. */
        fun silent(rate: Int = 48_000, wavePoints: Int = 0): ScopeFrame {
            val fft = ScopeAnalysis.fftSize(rate)
            return ScopeFrame(
                rate, fft, FloatArray(fft / 2) { FLOOR_DB }, floatArrayOf(FLOOR_DB, FLOOR_DB), floatArrayOf(FLOOR_DB, FLOOR_DB),
                FloatArray(wavePoints), FloatArray(wavePoints), 0L,
            )
        }

        /** 20·log10(x), [FLOOR_DB] for silence. */
        fun db(linear: Double): Float = if (linear > 0.0) max(20.0 * log10(linear), FLOOR_DB.toDouble()).toFloat() else FLOOR_DB
    }
}

/**
 * S-611: the engine's analysis (native/audio/scope.c) in Kotlin, for samples that do not come
 * from the engine: a periodic Hann window, both channels' mean power per bin scaled so a sine of
 * amplitude A on a bin reads 20·log10(A), the RMS of the window and the waveform from the last
 * rising zero crossing that leaves room for it.
 */
object ScopeAnalysis {

    /** The FFT's length for [rate]: about 43 ms, a power of two from 1024 to 16 384, as the engine's. */
    fun fftSize(rate: Int): Int {
        val want = if (rate > 0) rate * (2048.0 / 48_000.0) else 2048.0
        var size = NativeAudio.SCOPE_FFT_MIN
        while (size < NativeAudio.SCOPE_FFT_MAX && size * 1.5 < want) size *= 2
        return size
    }

    /**
     * [left] and [right] (the same array for mono) at [rate]: their last [size] samples, fewer
     * padded with silence before; [peakDb] the levels the caller measured since the frame before.
     */
    fun analyse(
        left: FloatArray,
        right: FloatArray,
        rate: Int,
        size: Int = fftSize(rate),
        wavePoints: Int = 256,
        peakDb: FloatArray? = null,
        frames: Long = 0L,
    ): ScopeFrame {
        require(size > 0 && size and (size - 1) == 0) { "the FFT's length is a power of two" }
        val l = lastOf(left, size)
        val r = if (right === left) l else lastOf(right, size)
        var sumL = 0.0
        var sumR = 0.0
        var maxL = 0f
        var maxR = 0f
        for (i in 0 until size) {
            sumL += l[i].toDouble() * l[i]
            sumR += r[i].toDouble() * r[i]
            maxL = max(maxL, abs(l[i]))
            maxR = max(maxR, abs(r[i]))
        }
        val re = DoubleArray(size)
        val im = DoubleArray(size)
        for (i in 0 until size) {
            val w = 0.5 - 0.5 * cos(2.0 * PI * i / size)
            re[i] = l[i] * w
            im[i] = r[i] * w
        }
        fft(re, im)
        val scale = 4.0 / size
        val spectrum = FloatArray(size / 2) { k ->
            val m = if (k == 0) 0 else size - k
            val zr = re[k]
            val zi = im[k]
            val mr = re[m]
            val mi = -im[m]
            val lr = (zr + mr) * 0.5
            val li = (zi + mi) * 0.5
            val dr = (zr - mr) * 0.5
            val di = (zi - mi) * 0.5
            ScopeFrame.db(sqrt((lr * lr + li * li + dr * dr + di * di) * 0.5) * scale)
        }
        val points = wavePoints.coerceIn(0, NativeAudio.SCOPE_WAVE_MAX)
        val span = (rate / 50).coerceIn(1, size / 2)
        var start = size - span
        var i = size - span
        while (i > size - 2 * span && i > 0) {
            if (l[i - 1] + r[i - 1] < 0f && l[i] + r[i] >= 0f) {
                start = i
                break
            }
            i--
        }
        val waveL = FloatArray(points) { p -> l[start + (p.toLong() * span / points).toInt()] }
        val waveR = FloatArray(points) { p -> r[start + (p.toLong() * span / points).toInt()] }
        return ScopeFrame(
            rate = rate,
            fftSize = size,
            spectrumDb = spectrum,
            peakDb = peakDb ?: floatArrayOf(ScopeFrame.db(maxL.toDouble()), ScopeFrame.db(maxR.toDouble())),
            rmsDb = floatArrayOf(ScopeFrame.db(sqrt(sumL / size)), ScopeFrame.db(sqrt(sumR / size))),
            waveLeft = waveL,
            waveRight = waveR,
            frames = frames,
        )
    }

    private fun lastOf(samples: FloatArray, size: Int): FloatArray =
        if (samples.size >= size) samples.copyOfRange(samples.size - size, samples.size) else FloatArray(size).also { samples.copyInto(it, size - samples.size) }

    /** In place, radix 2. */
    internal fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]
                re[i] = re[j]
                re[j] = t
                t = im[i]
                im[i] = im[j]
                im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val angle = -2.0 * PI / len
            for (k in 0 until half) {
                val wr = cos(angle * k)
                val wi = sin(angle * k)
                var start = 0
                while (start < n) {
                    val p = start + k
                    val q = p + half
                    val tr = re[q] * wr - im[q] * wi
                    val ti = re[q] * wi + im[q] * wr
                    re[q] = re[p] - tr
                    im[q] = im[p] - ti
                    re[p] += tr
                    im[p] += ti
                    start += len
                }
            }
            len = len shl 1
        }
    }
}
