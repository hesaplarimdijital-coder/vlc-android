package app.nagmedar.audio

/**
 * libnagmedar_audio.so (native/audio): the engine as handles. Every call is cheap and
 * thread-safe but [render], which only the sink's thread calls. Events arrive on the engine's
 * own thread through [Listener]; a NagEvent's type and argument are the C header's.
 */
object NativeAudio {

    fun interface Listener {
        fun onEvent(type: Int, arg: Long, text: String)
    }

    /** False when this build was made without the native engine (local builds without CI's libraries). */
    val available: Boolean by lazy { runCatching { System.loadLibrary("nagmedar_audio") }.isSuccess }

    // NagSampleFormat
    const val FORMAT_NONE = 0
    const val FORMAT_S16 = 1
    const val FORMAT_S24_3 = 2
    const val FORMAT_S32 = 3
    const val FORMAT_F32 = 4
    const val FORMAT_F64 = 5

    // NagEventType
    const val EVENT_STATE = 1
    const val EVENT_TRACK = 2
    const val EVENT_TRACK_ENDED = 3
    const val EVENT_FORMAT = 4
    const val EVENT_ERROR = 5
    const val EVENT_DEVICE_LOST = 6
    const val EVENT_QUEUE_EMPTY = 7

    // NagState
    const val STATE_STOPPED = 0
    const val STATE_PLAYING = 1
    const val STATE_PAUSED = 2
    const val STATE_ENDED = 3

    // sinks of setSink
    const val SINK_PULL = 0
    const val SINK_AAUDIO = 1

    // crossfade curves
    const val CROSSFADE_EQUAL_POWER = 0
    const val CROSSFADE_LINEAR = 1
    const val CROSSFADE_MAX_MS = 12_000

    // S-906: the DSP chain
    const val GRAPHIC_BANDS = 20
    const val EQ_BANDS = 10
    const val EQ_PEAK = 0
    const val EQ_LOW_SHELF = 1
    const val EQ_HIGH_SHELF = 2
    const val EQ_LOW_PASS = 3
    const val EQ_HIGH_PASS = 4
    const val DSP_LOOKAHEAD_MAX_MS = 10.0
    /** setDsp's flags: 6 switches, then (enabled, type) of 10 parametric and 10 profile bands. */
    const val DSP_FLAGS = 6 + 2 * 2 * EQ_BANDS
    /** setDsp's values: 5 gains and times, the 20 graphic bands' dB, then (freq, dB, Q) of 10 + 10 bands. */
    const val DSP_VALUES = 5 + GRAPHIC_BANDS + 2 * 3 * EQ_BANDS

    // S-907: NagReplayGainMode
    const val REPLAYGAIN_OFF = 0
    const val REPLAYGAIN_TRACK = 1
    const val REPLAYGAIN_ALBUM = 2

    @JvmStatic external fun version(): String

    /** An engine; 0 on failure. [maxFormat] caps the output's depth, [fixedFormat] (or FORMAT_NONE) fixes it. */
    @JvmStatic external fun create(listener: Listener, ringSeconds: Double, maxFormat: Int, fixedFormat: Int, dither: Boolean): Long

    @JvmStatic external fun destroy(handle: Long)

    @JvmStatic external fun setSink(handle: Long, kind: Int): Int

    @JvmStatic external fun open(handle: Long, id: Long, url: String, startMs: Long): Int

    /** The next track; with [crossfade] it comes in over the end of the one playing (S-905). */
    @JvmStatic external fun setNext(handle: Long, id: Long, url: String?, crossfade: Boolean): Int

    /** S-905: the crossfade's length (0: off, at most 12 000) and curve: 0 equal power, 1 linear. */
    @JvmStatic external fun setCrossfade(handle: Long, ms: Int, curve: Int)

    @JvmStatic external fun play(handle: Long): Int

    @JvmStatic external fun pause(handle: Long): Int

    @JvmStatic external fun stop(handle: Long): Int

    @JvmStatic external fun seek(handle: Long, ms: Long): Int

    @JvmStatic external fun state(handle: Long): Int

    @JvmStatic external fun timeMs(handle: Long): Long

    @JvmStatic external fun lengthMs(handle: Long): Long

    @JvmStatic external fun trackId(handle: Long): Long

    @JvmStatic external fun setVolume(handle: Long, volume: Double)

    /** rate shl 16 or channels shl 8 or format; 0 while the output is not open. */
    @JvmStatic external fun outputFormat(handle: Long): Long

    /** The pull sink's render: [frames] frames into the direct [buffer]; how many came from a track. */
    @JvmStatic external fun render(handle: Long, buffer: java.nio.ByteBuffer, frames: Int): Int

    @JvmStatic external fun signalPath(handle: Long): String

    @JvmStatic external fun sourceInfo(handle: Long): String

    /** S-906: the chain's parameters, flat (DspSettings.flags and values); the render takes them at its next block. */
    @JvmStatic external fun setDsp(handle: Long, flags: IntArray, values: DoubleArray, profileName: String): Int

    @JvmStatic external fun dspActive(handle: Long): Boolean

    /** The limiter's lookahead at the output's rate, in frames. */
    @JvmStatic external fun dspLatencyFrames(handle: Long): Long

    /** frames, clipped samples, peak before the limiter, the limiter's largest reduction in dB. */
    @JvmStatic external fun dspMeter(handle: Long, reset: Boolean): DoubleArray

    /** S-907: the ReplayGain applied per track; takes effect on the tracks loaded too. */
    @JvmStatic external fun setReplayGain(handle: Long, mode: Int, preampDb: Double, untaggedDb: Double, preventClipping: Boolean)

    /** The gain in dB the playing track gets from ReplayGain. */
    @JvmStatic external fun replayGainDb(handle: Long): Double

    /** S-907: a scan: an album's accumulator and the cancel flag of its file scans; 0 on failure. */
    @JvmStatic external fun loudnessNew(): Long

    /**
     * Scans [path] whole (EBU R128); with [forAlbum] its blocks count for the album too. Returns
     * loudness LUFS (-inf for silence), true peak, sample peak, ReplayGain dB, frames, rate; or
     * null when the file cannot be read or the scan was cancelled. Slow: a worker thread's.
     */
    @JvmStatic external fun loudnessScan(handle: Long, path: String, forAlbum: Boolean): DoubleArray?

    /** The album's result over the files scanned for it, as [loudnessScan] returns one; null when none. */
    @JvmStatic external fun loudnessAlbum(handle: Long): DoubleArray?

    /** Stops the scan under way on [handle]; it returns null. */
    @JvmStatic external fun loudnessCancel(handle: Long)

    @JvmStatic external fun loudnessFree(handle: Long)

    /** Writes the ReplayGain tags into the file behind [fd] (read-write; [name] helps the type detection); NaN leaves a tag alone. 0, or negative. */
    @JvmStatic external fun writeReplayGain(fd: Int, name: String, trackGain: Double, trackPeak: Double, albumGain: Double, albumPeak: Double, trackLufs: Double, albumLufs: Double): Int

    /** The file as the engine reads it, with its ReplayGain tags, JSON (SourceInfo.parse); "{}" when it cannot be opened. */
    @JvmStatic external fun probe(path: String): String
}
