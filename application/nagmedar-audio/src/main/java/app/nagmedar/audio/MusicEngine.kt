package app.nagmedar.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import org.json.JSONObject
import java.io.Closeable

/** The engine's state, as NagState. */
enum class EngineState { Stopped, Playing, Paused, Ended }

/** S-905: the crossfade's gains over its length, in the engine's order. */
enum class CrossfadeCurve { EqualPower, Linear }

/** What the output is open for: the sink's rate, channels and sample format. */
data class OutputFormat(val rate: Int, val channels: Int, val sampleFormat: Int) {
    val bits: Int
        get() = when (sampleFormat) {
            NativeAudio.FORMAT_S16 -> 16
            NativeAudio.FORMAT_S24_3 -> 24
            NativeAudio.FORMAT_S32, NativeAudio.FORMAT_F32 -> 32
            NativeAudio.FORMAT_F64 -> 64
            else -> 0
        }
    val bytesPerFrame: Int get() = channels * (bits / 8)
    val isFloat: Boolean get() = sampleFormat == NativeAudio.FORMAT_F32 || sampleFormat == NativeAudio.FORMAT_F64

    companion object {
        /** The packed value of NativeAudio.outputFormat; null for 0. */
        fun of(packed: Long): OutputFormat? =
            if (packed == 0L) null else OutputFormat((packed shr 16).toInt(), (packed shr 8 and 0xFF).toInt(), (packed and 0xFF).toInt())
    }
}

/** The playing track's file as the engine's source read it (nag_audio_source_info), with its ReplayGain tags when it has them. */
data class SourceInfo(
    val codec: String,
    val container: String,
    val rate: Int,
    val channels: Int,
    val bits: Int,
    val isFloat: Boolean,
    val durationMs: Long,
    val bitrate: Long,
    val trackGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumGainDb: Double? = null,
    val albumPeak: Double? = null,
    /** S-908: a DSD file's bit rate (2 822 400 for DSD64), 0 for PCM. */
    val dsdRate: Int = 0,
    /** S-908: the file goes out as DSD over PCM. */
    val dop: Boolean = false,
) {
    /** Whether the file carries a ReplayGain (or Opus R128) gain. */
    val hasReplayGain: Boolean get() = trackGainDb != null || albumGainDb != null

    val isDsd: Boolean get() = dsdRate > 0

    /** "DSD64", "DSD128", … for a DSD file; null otherwise. */
    val dsdName: String? get() = if (dsdRate > 0) "DSD${Math.round(dsdRate / 44100.0)}" else null

    companion object {
        fun parse(json: String): SourceInfo? = runCatching {
            val o = JSONObject(json)
            if (!o.has("rate")) return null
            fun number(key: String): Double? = if (o.has(key)) o.optDouble(key).takeUnless { it.isNaN() } else null
            SourceInfo(
                o.optString("codec"), o.optString("container"), o.getInt("rate"), o.optInt("channels"), o.optInt("bits"),
                o.optBoolean("float"), o.optLong("duration_ms"), o.optLong("bitrate"),
                number("track_gain_db"), number("track_peak"), number("album_gain_db"), number("album_peak"),
                o.optInt("dsd_rate", 0), o.optBoolean("dop", false),
            )
        }.getOrNull()
    }
}

/** S-912: the A/B test's sides. */
enum class AbSide { A, B }

/** S-908: how a DSD file plays, as NagDsdMode: decoded to PCM, or as DoP words for a DAC that takes them on a bit-perfect output. */
enum class DsdMode { Pcm, Dop }

/** S-907: how the tags' gains are applied, as NagReplayGain. */
enum class ReplayGainMode { Off, Track, Album }

data class ReplayGainSettings(
    val mode: ReplayGainMode = ReplayGainMode.Off,
    /** On top of the tags' gain, dB. */
    val preampDb: Double = 0.0,
    /** For a track without tags, dB (0: as it is). */
    val untaggedDb: Double = 0.0,
    /** The gain is held back where the tags' peak would pass full scale. */
    val preventClipping: Boolean = true,
)

/**
 * Nağmedar Ses Motoru (S-901): one engine, its output and its events, for the playback service.
 * A track is a URI: a file, or a document whose descriptor the engine reads through
 * /proc/self/fd while it opens it. Events come on the main thread.
 */
class MusicEngine(
    context: Context,
    private val listener: Listener,
    ringSeconds: Double = 0.5,
    dither: Boolean = true,
) : Closeable {

    interface Listener {
        fun onStateChanged(state: EngineState)
        /** Track [id] renders now: a start, or a gapless hand-over from the one before. */
        fun onTrackStarted(id: Long)
        fun onTrackEnded(id: Long)
        /** The last track ended and no next was set. */
        fun onQueueEmpty()
        fun onError(id: Long, message: String)
        fun onOutputChanged(format: OutputFormat)
        /** The output's device went away; the engine reopens it, the owner may choose another sink. */
        fun onDeviceLost()
    }

    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var handle = 0L
    @Volatile private var closed = false

    /** The track ids given to [open] and [setNext], for events to be matched to them. */
    val available: Boolean get() = handle != 0L

    init {
        if (NativeAudio.available) {
            handle = NativeAudio.create(NativeAudio.Listener { type, arg, text -> dispatch(type, arg, text) }, ringSeconds, NativeAudio.FORMAT_S32, NativeAudio.FORMAT_NONE, dither)
            if (handle == 0L) Log.e(TAG, "the engine could not be created")
        } else {
            Log.i(TAG, "libnagmedar_audio is not in this build")
        }
    }

    private fun dispatch(type: Int, arg: Long, text: String) {
        if (closed) return
        main.post {
            if (closed) return@post
            when (type) {
                NativeAudio.EVENT_STATE -> listener.onStateChanged(stateOf(arg.toInt()))
                NativeAudio.EVENT_TRACK -> listener.onTrackStarted(arg)
                NativeAudio.EVENT_TRACK_ENDED -> listener.onTrackEnded(arg)
                NativeAudio.EVENT_FORMAT -> outputFormat?.let(listener::onOutputChanged)
                NativeAudio.EVENT_ERROR -> listener.onError(arg, text)
                NativeAudio.EVENT_DEVICE_LOST -> listener.onDeviceLost()
                NativeAudio.EVENT_QUEUE_EMPTY -> listener.onQueueEmpty()
            }
        }
    }

    /** The AAudio output (the default): exclusive and low latency where the device allows. */
    fun useAAudio(): Boolean = available && NativeAudio.setSink(handle, NativeAudio.SINK_AAUDIO) == 0

    /** A sink of the owner's that pulls through [render] (the bit-perfect AudioTrack path, S-902). */
    fun usePullSink(): Boolean = available && NativeAudio.setSink(handle, NativeAudio.SINK_PULL) == 0

    /**
     * S-903: the USB DAC behind [connection] as the output, driven directly; the connection stays
     * the caller's to close after the engine moves to another sink. False when the engine could
     * not take it (the device lost, no 2-channel format): [usbError] says why.
     */
    fun useUsbSink(connection: android.hardware.usb.UsbDeviceConnection): Boolean = available && NativeAudio.setUsbSink(handle, connection.fileDescriptor) == 0

    /** S-903: the DAC's own volume, 0..1; false without a volume control. */
    fun setUsbVolume(volume: Double): Boolean = available && NativeAudio.usbSetVolume(handle, volume) == 0

    val usbHasVolume: Boolean get() = available && NativeAudio.usbHasVolume(handle)

    val usbError: String get() = if (available) NativeAudio.usbError(handle) else ""

    /** S-902: the depths the sink is asked for, NativeAudio.FORMAT_* (FORMAT_NONE: no bound). */
    fun setFormatRange(minFormat: Int, maxFormat: Int) {
        if (available) NativeAudio.setFormatRange(handle, minFormat, maxFormat)
    }

    /** S-902: the pull sink's owner reports whether its output is bit-perfect, for the signal path. */
    fun setPullBitPerfect(bitPerfect: Boolean, latencyFrames: Long) {
        if (available) NativeAudio.setPullBitPerfect(handle, bitPerfect, latencyFrames)
    }

    /**
     * Starts [uri] as track [id] at [startMs]; false when the engine cannot open it (the caller
     * falls back to libVLC). The decoder has it open before this returns.
     */
    fun open(id: Long, uri: Uri, startMs: Long): Boolean = withPath(uri) { path -> NativeAudio.open(handle, id, path, startMs) == 0 }

    /**
     * The track to follow the one playing, decoded ahead (S-904); null clears it. With
     * [crossfade] the two overlap for the crossfade's length (S-905), without it the next
     * starts on the sample after the last.
     */
    fun setNext(id: Long, uri: Uri?, crossfade: Boolean = false): Boolean {
        if (!available) return false
        if (uri == null) return NativeAudio.setNext(handle, id, null, false) == 0
        return withPath(uri) { path -> NativeAudio.setNext(handle, id, path, crossfade) == 0 }
    }

    /** S-905: how long a crossfade lasts (0 turns it off) and its curve. A track opened from then on keeps enough audio ahead for it. */
    fun setCrossfade(ms: Int, curve: CrossfadeCurve) {
        if (available) NativeAudio.setCrossfade(handle, ms.coerceIn(0, NativeAudio.CROSSFADE_MAX_MS), curve.ordinal)
    }

    private inline fun withPath(uri: Uri, block: (String) -> Boolean): Boolean {
        if (!available) return false
        return when (uri.scheme) {
            null, "file" -> uri.path?.let(block) ?: false
            "content" -> {
                val descriptor: ParcelFileDescriptor? = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
                if (descriptor == null) {
                    Log.w(TAG, "cannot open $uri")
                    false
                } else {
                    // the engine opens /proc/self/fd/N for itself while this descriptor lives
                    descriptor.use { block("/proc/self/fd/${it.fd}") }
                }
            }
            else -> false
        }
    }

    fun play(): Boolean = available && NativeAudio.play(handle) == 0

    fun pause(): Boolean = available && NativeAudio.pause(handle) == 0

    fun stop() {
        if (available) NativeAudio.stop(handle)
    }

    fun seek(ms: Long): Boolean = available && NativeAudio.seek(handle, ms) == 0

    val state: EngineState get() = if (available) stateOf(NativeAudio.state(handle)) else EngineState.Stopped

    val timeMs: Long get() = if (available) NativeAudio.timeMs(handle) else 0L

    val lengthMs: Long get() = if (available) NativeAudio.lengthMs(handle) else 0L

    val trackId: Long get() = if (available) NativeAudio.trackId(handle) else -1L

    /** 0..1 linear (1 leaves the samples as they are; more amplifies). */
    var volume: Double = 1.0
        set(value) {
            field = value.coerceAtLeast(0.0)
            if (available) NativeAudio.setVolume(handle, field)
        }

    val outputFormat: OutputFormat? get() = if (available) OutputFormat.of(NativeAudio.outputFormat(handle)) else null

    /** The pull sink's render: [frames] frames into [buffer] (direct, large enough); how many came from a track. */
    fun render(buffer: java.nio.ByteBuffer, frames: Int): Int = if (available) NativeAudio.render(handle, buffer, frames) else 0

    /** The signal path (S-910) as the engine tells it, JSON. */
    fun signalPath(): String = if (available) NativeAudio.signalPath(handle) else "{}"

    /** S-906: the DSP chain's settings; the render takes them at its next block, without a click. */
    var dsp: DspSettings = DspSettings()
        set(value) {
            field = value
            if (available) NativeAudio.setDsp(handle, value.flags, value.values, value.profileName)
        }

    /** Whether the chain changes the samples (the bit-perfect badge goes off). */
    val dspActive: Boolean get() = available && NativeAudio.dspActive(handle)

    /** The limiter's lookahead at the output's rate, in frames. */
    val dspLatencyFrames: Long get() = if (available) NativeAudio.dspLatencyFrames(handle) else 0L

    /** The clip meter; with [reset] the counts start over after this read. */
    fun dspMeter(reset: Boolean = false): DspMeter {
        if (!available) return DspMeter(0, 0, 0.0, 0.0)
        val m = NativeAudio.dspMeter(handle, reset)
        return DspMeter(m[0].toLong(), m[1].toLong(), m[2], m[3])
    }

    /** S-912: the chain's level change over the last stretch of playing, dB. */
    val dspLevelDb: Double get() = if (available) NativeAudio.dspLevelDb(handle) else 0.0

    /** S-912: the A/B test's side: A the chain as set, B bypassed at the chain's level. */
    var abSide: AbSide
        get() = if (available && NativeAudio.abSide(handle) == 1) AbSide.B else AbSide.A
        set(value) {
            if (available) NativeAudio.setAbSide(handle, value.ordinal)
        }

    /** S-912: side B's gain in dB, frozen when B was chosen. */
    val abGainDb: Double get() = if (available) NativeAudio.abGainDb(handle) else 0.0

    /** S-910: the signal path as a model. */
    val signalPathModel: SignalPath? get() = SignalPath.parse(signalPath())

    /** S-907: ReplayGain, applied per track from its tags; the tracks loaded take it at once. */
    var replayGain: ReplayGainSettings = ReplayGainSettings()
        set(value) {
            field = value
            if (available) NativeAudio.setReplayGain(handle, value.mode.ordinal, value.preampDb, value.untaggedDb, value.preventClipping)
        }

    /** The gain in dB the playing track gets from ReplayGain (0 when off or untagged). */
    val replayGainDb: Double get() = if (available) NativeAudio.replayGainDb(handle) else 0.0

    /** S-909: the pace, the pitch kept (audiobooks and podcasts: 0.5..3); 1 leaves the samples alone. */
    var tempo: Double = 1.0
        set(value) {
            field = value.coerceIn(NativeAudio.TEMPO_MIN, NativeAudio.TEMPO_MAX)
            if (available) NativeAudio.setTempo(handle, field)
        }

    /** S-909: the pitch in semitones, the pace kept; 0 leaves the samples alone. */
    var pitch: Double = 0.0
        set(value) {
            field = value.coerceIn(-NativeAudio.PITCH_MAX_SEMITONES, NativeAudio.PITCH_MAX_SEMITONES)
            if (available) NativeAudio.setPitch(handle, field)
        }

    /** S-909: -1 (left only) .. 1 (right only); 0 leaves the samples alone. */
    var balance: Double = 0.0
        set(value) {
            field = value.coerceIn(-1.0, 1.0)
            if (available) NativeAudio.setBalance(handle, field)
        }

    /** S-909: every channel the average of them all. */
    var mono: Boolean = false
        set(value) {
            field = value
            if (available) NativeAudio.setMono(handle, value)
        }

    /** S-908: DSD as PCM or as DoP, from the next track on. */
    var dsdMode: DsdMode = DsdMode.Pcm
        set(value) {
            field = value
            if (available) NativeAudio.setDsdMode(handle, value.ordinal)
        }

    val sourceInfo: SourceInfo? get() = if (available) SourceInfo.parse(NativeAudio.sourceInfo(handle)) else null

    override fun close() {
        if (closed) return
        closed = true
        if (handle != 0L) {
            NativeAudio.destroy(handle)
            handle = 0L
        }
    }

    private fun stateOf(state: Int) = when (state) {
        NativeAudio.STATE_PLAYING -> EngineState.Playing
        NativeAudio.STATE_PAUSED -> EngineState.Paused
        NativeAudio.STATE_ENDED -> EngineState.Ended
        else -> EngineState.Stopped
    }

    companion object {
        private const val TAG = "NagmedarAudio"

        /** Whether this build carries the engine. */
        val available: Boolean get() = NativeAudio.available

        val version: String get() = if (NativeAudio.available) NativeAudio.version() else ""
    }
}
