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

/** The playing track's file as the engine's source read it (nag_audio_source_info). */
data class SourceInfo(
    val codec: String,
    val container: String,
    val rate: Int,
    val channels: Int,
    val bits: Int,
    val isFloat: Boolean,
    val durationMs: Long,
    val bitrate: Long,
) {
    companion object {
        fun parse(json: String): SourceInfo? = runCatching {
            val o = JSONObject(json)
            if (!o.has("rate")) return null
            SourceInfo(
                o.optString("codec"), o.optString("container"), o.getInt("rate"), o.optInt("channels"), o.optInt("bits"),
                o.optBoolean("float"), o.optLong("duration_ms"), o.optLong("bitrate"),
            )
        }.getOrNull()
    }
}

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
