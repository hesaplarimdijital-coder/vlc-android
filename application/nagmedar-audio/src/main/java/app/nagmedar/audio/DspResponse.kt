package app.nagmedar.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * S-906: the DSP chain's frequency response, as native/audio/dsp.c compiles it: the same RBJ
 * biquads, the same order of stages, the same automatic preamp. The equaliser screen draws it;
 * the tests hold it to the native model's figures.
 */
object DspResponse {

    /** The graphic EQ's Q: half an octave (dsp.c's GRAPHIC_Q). */
    const val GRAPHIC_Q = 2.871
    private const val GAIN_DB_MAX = 30.0
    private const val Q_MIN = 0.05
    private const val Q_MAX = 50.0
    private const val RESPONSE_POINTS = 120

    /** A biquad's coefficients, a0 normalised away. */
    data class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

    /** Whether [band] changes the signal (dsp.c's band_does_something). */
    fun active(band: EqBand): Boolean = band.enabled && band.freq > 0 && (band.type == EqType.LowPass || band.type == EqType.HighPass || band.gainDb != 0.0)

    /** The band's biquad at [rate]; null when it does nothing or lies beyond the output's reach. */
    fun biquad(band: EqBand, rate: Int): Biquad? {
        if (!active(band) || rate <= 0) return null
        var freq = band.freq
        if (freq >= rate * 0.49) return null
        if (freq < 1.0) freq = 1.0
        val gainDb = band.gainDb.coerceIn(-GAIN_DB_MAX, GAIN_DB_MAX)
        val q = if (band.q >= Q_MIN) band.q.coerceAtMost(Q_MAX) else Q_MIN
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / rate
        val cw = cos(w0)
        val sw = sin(w0)
        val alpha = sw / (2.0 * q)
        val sqA2 = 2.0 * sqrt(a) * alpha
        val (b, d) = when (band.type) {
            EqType.Peak -> doubleArrayOf(1 + alpha * a, -2 * cw, 1 - alpha * a) to doubleArrayOf(1 + alpha / a, -2 * cw, 1 - alpha / a)
            EqType.LowShelf -> doubleArrayOf(
                a * ((a + 1) - (a - 1) * cw + sqA2), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - sqA2),
            ) to doubleArrayOf((a + 1) + (a - 1) * cw + sqA2, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sqA2)
            EqType.HighShelf -> doubleArrayOf(
                a * ((a + 1) + (a - 1) * cw + sqA2), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - sqA2),
            ) to doubleArrayOf((a + 1) - (a - 1) * cw + sqA2, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - sqA2)
            EqType.LowPass -> doubleArrayOf((1 - cw) / 2, 1 - cw, (1 - cw) / 2) to doubleArrayOf(1 + alpha, -2 * cw, 1 - alpha)
            EqType.HighPass -> doubleArrayOf((1 + cw) / 2, -(1 + cw), (1 + cw) / 2) to doubleArrayOf(1 + alpha, -2 * cw, 1 - alpha)
        }
        val a0 = d[0]
        return Biquad(b[0] / a0, b[1] / a0, b[2] / a0, d[1] / a0, d[2] / a0)
    }

    /** |H(f)|² of one biquad at [hz]. */
    fun power(q: Biquad, hz: Double, rate: Int): Double {
        val w = 2.0 * PI * hz / rate
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2 * w)
        val s2 = sin(2 * w)
        val nr = q.b0 + q.b1 * c1 + q.b2 * c2
        val ni = q.b1 * s1 + q.b2 * s2
        val dr = 1 + q.a1 * c1 + q.a2 * c2
        val di = q.a1 * s1 + q.a2 * s2
        val den = dr * dr + di * di
        return if (den <= 0) 1e-30 else (nr * nr + ni * ni) / den
    }

    /** The graphic EQ's bands as the engine builds them. */
    fun graphicBands(settings: DspSettings): List<EqBand> = DspSettings.graphicFrequencies.mapIndexed { i, hz ->
        EqBand(hz, settings.graphicDb.getOrElse(i) { 0.0 }, GRAPHIC_Q, EqType.Peak, true)
    }

    /** The biquads of the chain, in its order: graphic, parametric, profile. None when bypassed. */
    fun biquads(settings: DspSettings, rate: Int): List<Biquad> {
        if (settings.bypass) return emptyList()
        val bands = buildList {
            if (settings.graphicEnabled) addAll(graphicBands(settings))
            if (settings.parametricEnabled) addAll(settings.parametric.take(NativeAudio.EQ_BANDS))
            if (settings.profileEnabled) addAll(settings.profile.take(NativeAudio.EQ_BANDS))
        }
        return bands.mapNotNull { biquad(it, rate) }
    }

    /** The EQ's response at [hz] in dB, without the preamps. */
    fun eqDb(biquads: List<Biquad>, hz: Double, rate: Int): Double = biquads.sumOf { 10.0 * log10(power(it, hz, rate)) }

    /** What the automatic preamp takes back, dB (0 or negative), as dsp.c finds it. */
    fun autoPreampDb(settings: DspSettings, rate: Int): Double {
        if (settings.bypass || !settings.autoPreamp) return 0.0
        val biquads = biquads(settings, rate)
        if (biquads.isEmpty()) return 0.0
        val top = minOf(rate * 0.49, 20_000.0)
        var max = 0.0
        for (i in 0..RESPONSE_POINTS) {
            val hz = 20.0 * (top / 20.0).pow(i.toDouble() / RESPONSE_POINTS)
            max = maxOf(max, eqDb(biquads, hz, rate))
        }
        val centres = buildList {
            if (settings.parametricEnabled) settings.parametric.filter { active(it) && it.freq < rate * 0.49 }.forEach { add(it.freq) }
            if (settings.profileEnabled) settings.profile.filter { active(it) && it.freq < rate * 0.49 }.forEach { add(it.freq) }
            if (settings.graphicEnabled) {
                DspSettings.graphicFrequencies.forEachIndexed { i, hz ->
                    if (settings.graphicDb.getOrElse(i) { 0.0 } != 0.0 && hz < rate * 0.49) add(hz)
                }
            }
        }
        for (hz in centres) max = maxOf(max, eqDb(biquads, hz, rate))
        return if (max > 0) -max else 0.0
    }

    /** Every gain of the chain before its filters, dB: the preamp, the profile's and the automatic one. */
    fun gainDb(settings: DspSettings, rate: Int): Double {
        if (settings.bypass) return 0.0
        var db = settings.preampDb
        if (settings.profileEnabled) db += settings.profilePreampDb
        return db + autoPreampDb(settings, rate)
    }

    /**
     * The whole chain's response at each of [frequencies], dB: what the screen draws. The
     * limiter is left out: it acts on the level, not on a frequency.
     */
    fun curve(settings: DspSettings, frequencies: List<Double>, rate: Int = 48_000): List<Double> {
        val biquads = biquads(settings, rate)
        val gain = gainDb(settings, rate)
        return frequencies.map { hz -> gain + eqDb(biquads, hz, rate) }
    }

    /** [count] frequencies from 20 Hz to 20 kHz, evenly on a log scale. */
    fun logFrequencies(count: Int = 200): List<Double> = List(count) { i -> 20.0 * 1000.0.pow(i.toDouble() / (count - 1)) }
}

/**
 * S-906: VLC's equaliser presets (modules/audio_filter/equalizer_presets.h, its 60 Hz–16 kHz
 * bands) on the twenty bands of the engine's graphic EQ: each band takes the preset's curve at
 * its own frequency, read on a log scale between VLC's bands, to the half decibel.
 */
object GraphicPresets {

    private val vlcFrequencies = doubleArrayOf(60.0, 170.0, 310.0, 600.0, 1_000.0, 3_000.0, 6_000.0, 12_000.0, 14_000.0, 16_000.0)

    /** VLC's presets by their keys (EqualizerPreset's), in VLC's order. */
    private val vlc: Map<String, DoubleArray> = linkedMapOf(
        "flat" to doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        "classical" to doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -7.2, -7.2, -7.2, -9.6),
        "club" to doubleArrayOf(0.0, 0.0, 8.0, 5.6, 5.6, 5.6, 3.2, 0.0, 0.0, 0.0),
        "dance" to doubleArrayOf(9.6, 7.2, 2.4, 0.0, 0.0, -5.6, -7.2, -7.2, 0.0, 0.0),
        "fullbass" to doubleArrayOf(-8.0, 9.6, 9.6, 5.6, 1.6, -4.0, -8.0, -10.4, -11.2, -11.2),
        "fullbasstreble" to doubleArrayOf(7.2, 5.6, 0.0, -7.2, -4.8, 1.6, 8.0, 11.2, 12.0, 12.0),
        "fulltreble" to doubleArrayOf(-9.6, -9.6, -9.6, -4.0, 2.4, 11.2, 16.0, 16.0, 16.0, 16.8),
        "headphones" to doubleArrayOf(4.8, 11.2, 5.6, -3.2, -2.4, 1.6, 4.8, 9.6, 12.8, 14.4),
        "largehall" to doubleArrayOf(10.4, 10.4, 5.6, 5.6, 0.0, -4.8, -4.8, -4.8, 0.0, 0.0),
        "live" to doubleArrayOf(-4.8, 0.0, 4.0, 5.6, 5.6, 5.6, 4.0, 2.4, 2.4, 2.4),
        "party" to doubleArrayOf(7.2, 7.2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 7.2, 7.2),
        "pop" to doubleArrayOf(-1.6, 4.8, 7.2, 8.0, 5.6, 0.0, -2.4, -2.4, -1.6, -1.6),
        "reggae" to doubleArrayOf(0.0, 0.0, 0.0, -5.6, 0.0, 6.4, 6.4, 0.0, 0.0, 0.0),
        "rock" to doubleArrayOf(8.0, 4.8, -5.6, -8.0, -3.2, 4.0, 8.8, 11.2, 11.2, 11.2),
        "ska" to doubleArrayOf(-2.4, -4.8, -4.0, 0.0, 4.0, 5.6, 8.8, 9.6, 11.2, 9.6),
        "soft" to doubleArrayOf(4.8, 1.6, 0.0, -2.4, 0.0, 4.0, 8.0, 9.6, 11.2, 12.0),
        "softrock" to doubleArrayOf(4.0, 4.0, 2.4, 0.0, -4.0, -5.6, -3.2, 0.0, 2.4, 8.8),
        "techno" to doubleArrayOf(8.0, 5.6, 0.0, -5.6, -4.8, 0.0, 8.0, 9.6, 9.6, 8.8),
    )

    /** The presets' keys, in VLC's order. */
    val keys: List<String> get() = vlc.keys.toList()

    /** The twenty bands' dB of the preset [key]; null for an unknown key. */
    fun bands(key: String): List<Double>? {
        val preset = vlc[key] ?: return null
        return DspSettings.graphicFrequencies.map { hz -> roundHalf(interpolate(preset, hz)) }
    }

    /** The preset whose bands [graphicDb] are, if one is. */
    fun keyOf(graphicDb: List<Double>): String? = keys.firstOrNull { key -> bands(key) == graphicDb }

    private fun interpolate(preset: DoubleArray, hz: Double): Double {
        if (hz <= vlcFrequencies.first()) return preset.first()
        if (hz >= vlcFrequencies.last()) return preset.last()
        val i = vlcFrequencies.indexOfFirst { it >= hz }
        val lo = vlcFrequencies[i - 1]
        val hi = vlcFrequencies[i]
        val t = (log10(hz) - log10(lo)) / (log10(hi) - log10(lo))
        return preset[i - 1] + (preset[i] - preset[i - 1]) * t
    }

    private fun roundHalf(db: Double): Double = Math.round(db * 2) / 2.0
}
