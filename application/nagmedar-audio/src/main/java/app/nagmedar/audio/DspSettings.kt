package app.nagmedar.audio

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** An EQ band's filter, as NagEqType. */
enum class EqType { Peak, LowShelf, HighShelf, LowPass, HighPass }

/** One band of a parametric EQ or a headphone profile. */
data class EqBand(
    val freq: Double,
    val gainDb: Double = 0.0,
    val q: Double = 1.0,
    val type: EqType = EqType.Peak,
    val enabled: Boolean = true,
) {
    /** Whether the band changes the signal: a pass, or a peak or shelf with a gain. */
    val active: Boolean get() = enabled && freq > 0 && (type == EqType.LowPass || type == EqType.HighPass || gainDb != 0.0)

    fun toJson(): JSONObject = JSONObject().put("freq", freq).put("gain", gainDb).put("q", q).put("type", type.name).put("enabled", enabled)

    companion object {
        fun fromJson(o: JSONObject): EqBand = EqBand(
            freq = o.optDouble("freq", 1000.0),
            gainDb = o.optDouble("gain", 0.0),
            q = o.optDouble("q", 1.0),
            type = runCatching { EqType.valueOf(o.optString("type", "Peak")) }.getOrDefault(EqType.Peak),
            enabled = o.optBoolean("enabled", true),
        )
    }
}

/**
 * S-906: the DSP chain's settings, as the user keeps them: preamp → 20-band graphic EQ →
 * parametric EQ → headphone profile → limiter, and the one bypass over all of it. Saved as
 * JSON in the settings (NAGMEDAR_DSP); handed to the engine flat ([flags], [values]).
 */
data class DspSettings(
    val bypass: Boolean = false,
    val preampDb: Double = 0.0,
    /** The chain's largest gain taken back, so an EQ boost cannot clip. */
    val autoPreamp: Boolean = true,
    val graphicEnabled: Boolean = false,
    val graphicDb: List<Double> = List(NativeAudio.GRAPHIC_BANDS) { 0.0 },
    val parametricEnabled: Boolean = false,
    val parametric: List<EqBand> = emptyList(),
    val profileEnabled: Boolean = false,
    val profileName: String = "",
    val profilePreampDb: Double = 0.0,
    val profile: List<EqBand> = emptyList(),
    val limiterEnabled: Boolean = false,
    val limiterThresholdDb: Double = -1.0,
    val limiterReleaseMs: Double = 100.0,
    val limiterLookaheadMs: Double = 5.0,
) {
    /** Whether the chain leaves the samples alone (nag_dsp_params_identity). */
    val identity: Boolean
        get() = bypass || !(
            preampDb != 0.0 || limiterEnabled ||
                graphicEnabled && graphicDb.any { it != 0.0 } ||
                parametricEnabled && parametric.any { it.active } ||
                profileEnabled && (profilePreampDb != 0.0 || profile.any { it.active })
            )

    /** NativeAudio.setDsp's switches. */
    val flags: IntArray
        get() {
            val f = IntArray(NativeAudio.DSP_FLAGS)
            f[0] = if (bypass) 1 else 0
            f[1] = if (autoPreamp) 1 else 0
            f[2] = if (graphicEnabled) 1 else 0
            f[3] = if (parametricEnabled) 1 else 0
            f[4] = if (profileEnabled) 1 else 0
            f[5] = if (limiterEnabled) 1 else 0
            listOf(parametric, profile).forEachIndexed { set, bands ->
                for (i in 0 until NativeAudio.EQ_BANDS) {
                    val band = bands.getOrNull(i)
                    f[6 + (set * NativeAudio.EQ_BANDS + i) * 2] = if (band != null && band.enabled) 1 else 0
                    f[6 + (set * NativeAudio.EQ_BANDS + i) * 2 + 1] = band?.type?.ordinal ?: 0
                }
            }
            return f
        }

    /** NativeAudio.setDsp's numbers. */
    val values: DoubleArray
        get() {
            val v = DoubleArray(NativeAudio.DSP_VALUES)
            v[0] = preampDb
            v[1] = profilePreampDb
            v[2] = limiterThresholdDb
            v[3] = limiterReleaseMs
            v[4] = limiterLookaheadMs
            for (i in 0 until NativeAudio.GRAPHIC_BANDS) v[5 + i] = graphicDb.getOrElse(i) { 0.0 }
            listOf(parametric, profile).forEachIndexed { set, bands ->
                for (i in 0 until NativeAudio.EQ_BANDS) {
                    val band = bands.getOrNull(i)
                    val at = 5 + NativeAudio.GRAPHIC_BANDS + (set * NativeAudio.EQ_BANDS + i) * 3
                    v[at] = band?.freq ?: 1000.0
                    v[at + 1] = band?.gainDb ?: 0.0
                    v[at + 2] = band?.q ?: 1.0
                }
            }
            return v
        }

    fun toJson(): String = JSONObject()
        .put("bypass", bypass)
        .put("preamp", preampDb)
        .put("auto_preamp", autoPreamp)
        .put("graphic_enabled", graphicEnabled)
        .put("graphic", JSONArray(graphicDb))
        .put("parametric_enabled", parametricEnabled)
        .put("parametric", JSONArray(parametric.map { it.toJson() }))
        .put("profile_enabled", profileEnabled)
        .put("profile_name", profileName)
        .put("profile_preamp", profilePreampDb)
        .put("profile", JSONArray(profile.map { it.toJson() }))
        .put("limiter_enabled", limiterEnabled)
        .put("limiter_threshold", limiterThresholdDb)
        .put("limiter_release", limiterReleaseMs)
        .put("limiter_lookahead", limiterLookaheadMs)
        .toString()

    companion object {
        /** The graphic EQ's centre frequencies (nag_graphic_frequencies): ISO half-octave bands. */
        val graphicFrequencies: List<Double> = listOf(
            25.0, 35.5, 50.0, 71.0, 100.0, 140.0, 200.0, 280.0, 400.0, 560.0, 800.0, 1120.0, 1600.0, 2240.0, 3150.0, 4500.0, 6300.0, 9000.0, 12500.0, 18000.0,
        )

        const val GAIN_DB_MAX = 30.0

        /** [json] as saved by [toJson]; the defaults for anything missing or broken. */
        fun fromJson(json: String?): DspSettings {
            if (json.isNullOrBlank()) return DspSettings()
            return runCatching {
                val o = JSONObject(json)
                val defaults = DspSettings()
                val graphic = o.optJSONArray("graphic")?.let { a -> List(NativeAudio.GRAPHIC_BANDS) { i -> if (i < a.length()) a.optDouble(i, 0.0) else 0.0 } }
                DspSettings(
                    bypass = o.optBoolean("bypass", defaults.bypass),
                    preampDb = o.optDouble("preamp", defaults.preampDb),
                    autoPreamp = o.optBoolean("auto_preamp", defaults.autoPreamp),
                    graphicEnabled = o.optBoolean("graphic_enabled", defaults.graphicEnabled),
                    graphicDb = graphic ?: defaults.graphicDb,
                    parametricEnabled = o.optBoolean("parametric_enabled", defaults.parametricEnabled),
                    parametric = bands(o.optJSONArray("parametric")),
                    profileEnabled = o.optBoolean("profile_enabled", defaults.profileEnabled),
                    profileName = o.optString("profile_name", ""),
                    profilePreampDb = o.optDouble("profile_preamp", 0.0),
                    profile = bands(o.optJSONArray("profile")),
                    limiterEnabled = o.optBoolean("limiter_enabled", defaults.limiterEnabled),
                    limiterThresholdDb = o.optDouble("limiter_threshold", defaults.limiterThresholdDb),
                    limiterReleaseMs = o.optDouble("limiter_release", defaults.limiterReleaseMs),
                    limiterLookaheadMs = o.optDouble("limiter_lookahead", defaults.limiterLookaheadMs),
                )
            }.getOrDefault(DspSettings())
        }

        private fun bands(a: JSONArray?): List<EqBand> =
            a?.let { arr -> List(minOf(arr.length(), NativeAudio.EQ_BANDS)) { i -> EqBand.fromJson(arr.getJSONObject(i)) } } ?: emptyList()

        /**
         * A headphone profile in AutoEq's ParametricEQ.txt form:
         *   Preamp: -6.4 dB
         *   Filter 1: ON PK Fc 105 Hz Gain -3.2 dB Q 0.70
         * PK is a peak, LSC/LS a low shelf, HSC/HS a high shelf, LP and HP the passes. Returns
         * the preamp and the bands (at most 10), or null when nothing in [text] is a filter.
         */
        fun parseAutoEq(text: String): Pair<Double, List<EqBand>>? {
            var preamp = 0.0
            val bands = ArrayList<EqBand>()
            val preampLine = Regex("""^\s*Preamp:\s*([-+]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
            val filterLine = Regex(
                """^\s*Filter\s*\d*:\s*(ON|OFF)\s+(PK|LSC|LS|HSC|HS|LP|HP)\s+Fc\s+([-+]?\d+(?:\.\d+)?)\s*Hz(?:\s+Gain\s+([-+]?\d+(?:\.\d+)?)\s*dB)?(?:\s+Q\s+([-+]?\d+(?:\.\d+)?))?""",
                RegexOption.IGNORE_CASE,
            )
            for (line in text.lineSequence()) {
                preampLine.find(line)?.let { preamp = it.groupValues[1].toDouble() }
                val m = filterLine.find(line) ?: continue
                if (bands.size >= NativeAudio.EQ_BANDS) continue
                val type = when (m.groupValues[2].uppercase(Locale.ROOT)) {
                    "PK" -> EqType.Peak
                    "LSC", "LS" -> EqType.LowShelf
                    "HSC", "HS" -> EqType.HighShelf
                    "LP" -> EqType.LowPass
                    else -> EqType.HighPass
                }
                bands += EqBand(
                    freq = m.groupValues[3].toDouble(),
                    gainDb = m.groupValues[4].toDoubleOrNull() ?: 0.0,
                    q = m.groupValues[5].toDoubleOrNull() ?: 0.7,
                    type = type,
                    enabled = m.groupValues[1].equals("ON", ignoreCase = true),
                )
            }
            return if (bands.isEmpty()) null else preamp to bands
        }
    }
}

/** The clip meter (nag_audio_dsp_meter). */
data class DspMeter(val frames: Long, val clipped: Long, val peak: Double, val reductionDb: Double) {
    /** The peak before the limiter in dBFS; -inf for silence. */
    val peakDb: Double get() = if (peak > 0) 20 * kotlin.math.log10(peak) else Double.NEGATIVE_INFINITY
    val clipping: Boolean get() = clipped > 0
}
