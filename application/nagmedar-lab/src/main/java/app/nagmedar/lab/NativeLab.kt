package app.nagmedar.lab

/**
 * libnagmedar_lab.so: FFmpeg and libdovi, linked privately so they never meet libvlc's own
 * copies. Every call is blocking and runs on a background thread; [cancel]
 * stops the running one. Results are JSON objects (see native/lab).
 */
object NativeLab {

    fun interface Progress {
        fun onProgress(fraction: Double, message: String)
    }

    /** False when this build was made without the native lab tools (local builds). */
    val available: Boolean by lazy {
        runCatching { System.loadLibrary("nagmedar_lab") }.isSuccess
    }

    @JvmStatic external fun version(): String

    @JvmStatic external fun cancel()

    /** SP-03: remux [input] to [output] with the Dolby Vision RPU converted to profile 8.1. */
    @JvmStatic external fun dvConvert(input: String, output: String, maxSeconds: Double, mp4: Boolean, listener: Progress?): String
}
