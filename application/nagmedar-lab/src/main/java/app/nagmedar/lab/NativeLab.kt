package app.nagmedar.lab

/**
 * libnagmedar_lab.so: FFmpeg, libdovi, whisper.cpp and libusb, linked privately so they never
 * meet libvlc's own copies: the Lab tools and the library's media probe (S-802). Every call is
 * blocking and runs on a background thread; [cancel] stops the running Lab tool. Results are
 * JSON objects (see native/lab).
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

    /** SP-05: transcribe the first audio track of [input] with the ggml model at [model]. */
    @JvmStatic external fun whisper(model: String, input: String, language: String, maxSeconds: Double, threads: Int, listener: Progress?): String

    /** S-709: what a transcription tells as it goes, on the thread that runs it. */
    interface Transcription {
        /** [doneSeconds] of the film transcribed of [totalSeconds] (0 when the film does not say). */
        fun onProgress(doneSeconds: Double, totalSeconds: Double)

        /** One line of speech, its times in milliseconds of the film. */
        fun onSegment(startMs: Long, endMs: Long, text: String)

        /** The language whisper heard ("tr"), when it was asked to find it. */
        fun onLanguage(code: String)
    }

    /**
     * S-709: the speech of audio track [audioTrack] of [input] (the n-th audio track, 0 first; -1
     * for the best one) transcribed with the ggml model at [model], in [language] ("tr", "en",
     * "auto"), into English when [translate], on [threads] threads; five minutes at a time, so a
     * film of any length fits in memory. Blocking; [cancelTranscription] stops it. The result is
     * a JSON object: "error" ("" when it went through), "detail", "language", "audio_seconds".
     */
    @JvmStatic external fun transcribe(model: String, input: String, audioTrack: Int, language: String, translate: Boolean, threads: Int, listener: Transcription): String

    /** S-709: stops the running [transcribe] soon; the Lab's [cancel] does not. */
    @JvmStatic external fun cancelTranscription()

    /** SP-02: formats, clock and rates of the USB audio device behind [fd]. */
    @JvmStatic external fun usbDescribe(fd: Int): String

    /** SP-02: a -6 dBFS sine straight to the USB DAC behind [fd]. */
    @JvmStatic external fun usbTone(fd: Int, rate: Int, bits: Int, seconds: Double, frequency: Double, listener: Progress?): String

    /** S-802: streams, HDR and Dolby Vision of [input] (a path or /proc/self/fd/N); see [MediaProbe]. */
    @JvmStatic external fun probe(input: String): String
}
