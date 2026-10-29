package app.nagmedar.lab

/**
 * libnagmedar_lab.so: FFmpeg and libusb, linked privately so they never meet libvlc's own
 * copies: the Lab tools and the library's media probe (S-802). Every call is blocking and runs
 * on a background thread; [cancel] stops the running Lab tool. Results are JSON objects (see
 * native/lab).
 */
object NativeLab {

    fun interface Progress {
        fun onProgress(fraction: Double, message: String)
    }

    private val load = lazy { runCatching { System.loadLibrary("nagmedar_lab") }.isSuccess }

    /**
     * False when this build was made without the native lab tools (local builds). The first ask
     * loads the library, which reads the APK: never on the main thread, which reads [loaded].
     */
    val available: Boolean by load

    /** [available] once the library's load has been tried, null before: it never loads nor waits. */
    val loaded: Boolean? get() = if (load.isInitialized()) load.value else null

    @JvmStatic external fun version(): String

    @JvmStatic external fun cancel()

    /** SP-02: formats, clock and rates of the USB audio device behind [fd]. */
    @JvmStatic external fun usbDescribe(fd: Int): String

    /** SP-02: a -6 dBFS sine straight to the USB DAC behind [fd]. */
    @JvmStatic external fun usbTone(fd: Int, rate: Int, bits: Int, seconds: Double, frequency: Double, listener: Progress?): String

    /** S-802: the audio and subtitle streams of [input] (a path or /proc/self/fd/N); see [MediaProbe]. */
    @JvmStatic external fun probe(input: String): String
}
