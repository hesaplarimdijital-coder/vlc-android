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

    @JvmStatic external fun version(): String

    /** An engine; 0 on failure. [maxFormat] caps the output's depth, [fixedFormat] (or FORMAT_NONE) fixes it. */
    @JvmStatic external fun create(listener: Listener, ringSeconds: Double, maxFormat: Int, fixedFormat: Int, dither: Boolean): Long

    @JvmStatic external fun destroy(handle: Long)

    @JvmStatic external fun setSink(handle: Long, kind: Int): Int

    @JvmStatic external fun open(handle: Long, id: Long, url: String, startMs: Long): Int

    @JvmStatic external fun setNext(handle: Long, id: Long, url: String?): Int

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
}
