package app.nagmedar.audio

import org.json.JSONObject

/**
 * S-910: the signal path as the engine tells it (nag_audio_signal_path_json): the source, the
 * stages between it and the output, the output, and whether the samples reach it as they are,
 * with the first reason they do not. The panel draws it; the badge reads [bitPerfect].
 */
data class SignalPath(
    val state: EngineState,
    val source: Source?,
    val output: Output?,
    val volume: Double,
    val crossfadeMs: Int,
    val replayGain: ReplayGainStage,
    val tempo: Double,
    val pitch: Double,
    val balance: Double,
    val mono: Boolean,
    val dsp: List<DspStage>,
    val dspActive: Boolean,
    val dspLevelDb: Double,
    val abSide: AbSide,
    val abGainDb: Double,
    val bitPerfect: Boolean,
    val reason: Reason,
) {
    data class Source(
        val id: Long,
        val url: String,
        val codec: String,
        val container: String,
        val rate: Int,
        val channels: Int,
        val bits: Int,
        val isFloat: Boolean,
        val durationMs: Long,
        val bitrate: Long,
        val dsdRate: Int,
        val dop: Boolean,
    ) {
        /** "DSD64", "DSD128", … for a DSD file; null otherwise. */
        val dsdName: String? get() = if (dsdRate > 0) "DSD${Math.round(dsdRate / 44100.0)}" else null
    }

    data class Output(val sink: String, val rate: Int, val channels: Int, val format: String, val bits: Int, val exclusive: Boolean)

    data class ReplayGainStage(val mode: ReplayGainMode, val preampDb: Double, val gainDb: Double, val tagged: Boolean, val clippingPrevented: Boolean) {
        val active: Boolean get() = gainDb != 0.0
    }

    /** One stage of the DSP chain as compiled: preamp, graphic_eq, parametric_eq, profile, auto_preamp, limiter, bypass. */
    data class DspStage(val name: String, val bands: Int?, val db: Double?, val profileName: String?, val lookaheadMs: Double?, val releaseMs: Double?)

    /** Why the output is not bit-perfect, in the engine's order of precedence. */
    enum class Reason(val key: String) {
        None(""),
        Idle("idle"),
        DsdToPcm("dsd_pcm"),
        Lossy("lossy"),
        Volume("volume"),
        ReplayGain("replaygain"),
        Tempo("tempo"),
        Balance("balance"),
        Dsp("dsp"),
        Depth("depth"),
        Output("output"),
        Crossfade("crossfade");

        companion object {
            fun of(key: String): Reason = entries.firstOrNull { it.key == key } ?: Output
        }
    }

    /** The stages between the source and the output that change the samples, in order, as the panel lists them. */
    val activeStages: List<String>
        get() = buildList {
            if (replayGain.active) add("replaygain")
            if (tempo != 1.0 || pitch != 0.0) add("tempo")
            if (mono || balance != 0.0) add("balance")
            if (dspActive) addAll(dsp.map { it.name })
            if (volume != 1.0) add("volume")
            if (crossfadeMs > 0) add("crossfade")
        }

    companion object {
        fun parse(json: String): SignalPath? = runCatching {
            val o = JSONObject(json)
            if (!o.has("state")) return null
            val src = o.optJSONObject("source")?.let { s ->
                Source(
                    s.optLong("id"), s.optString("url"), s.optString("codec"), s.optString("container"), s.optInt("rate"), s.optInt("channels"),
                    s.optInt("bits"), s.optBoolean("float"), s.optLong("duration_ms"), s.optLong("bitrate"), s.optInt("dsd_rate", 0), s.optBoolean("dop", false),
                )
            }
            val out = o.optJSONObject("output")?.let { p ->
                Output(p.optString("sink"), p.optInt("rate"), p.optInt("channels"), p.optString("format"), p.optInt("bits"), p.optBoolean("exclusive"))
            }
            val rg = o.optJSONObject("replaygain")
            val replayGain = ReplayGainStage(
                mode = when (rg?.optString("mode")) {
                    "track" -> ReplayGainMode.Track
                    "album" -> ReplayGainMode.Album
                    else -> ReplayGainMode.Off
                },
                preampDb = rg?.optDouble("preamp_db", 0.0) ?: 0.0,
                gainDb = rg?.optDouble("gain_db", 0.0) ?: 0.0,
                tagged = rg?.optBoolean("tagged", false) ?: false,
                clippingPrevented = rg?.optBoolean("clipping_prevented", false) ?: false,
            )
            val stages = o.optJSONArray("dsp")?.let { a ->
                List(a.length()) { i ->
                    val st = a.getJSONObject(i)
                    DspStage(
                        st.optString("stage"),
                        if (st.has("bands")) st.optInt("bands") else null,
                        if (st.has("db")) st.optDouble("db") else null,
                        if (st.has("name")) st.optString("name") else null,
                        if (st.has("lookahead_ms")) st.optDouble("lookahead_ms") else null,
                        if (st.has("release_ms")) st.optDouble("release_ms") else null,
                    )
                }
            } ?: emptyList()
            SignalPath(
                state = when (o.optInt("state")) {
                    NativeAudio.STATE_PLAYING -> EngineState.Playing
                    NativeAudio.STATE_PAUSED -> EngineState.Paused
                    NativeAudio.STATE_ENDED -> EngineState.Ended
                    else -> EngineState.Stopped
                },
                source = src,
                output = out,
                volume = o.optDouble("volume", 1.0),
                crossfadeMs = o.optInt("crossfade_ms", 0),
                replayGain = replayGain,
                tempo = o.optDouble("tempo", 1.0),
                pitch = o.optDouble("pitch", 0.0),
                balance = o.optDouble("balance", 0.0),
                mono = o.optBoolean("mono", false),
                dsp = stages,
                dspActive = o.optBoolean("dsp_active", false),
                dspLevelDb = o.optDouble("dsp_level_db", 0.0),
                abSide = if (o.optString("ab_side") == "B") AbSide.B else AbSide.A,
                abGainDb = o.optDouble("ab_gain_db", 0.0),
                bitPerfect = o.optBoolean("bit_perfect", false),
                reason = Reason.of(o.optString("not_bit_perfect_because", "")),
            )
        }.getOrNull()
    }
}
