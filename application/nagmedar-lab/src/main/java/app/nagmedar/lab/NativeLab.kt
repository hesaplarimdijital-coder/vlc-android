package app.nagmedar.lab

/**
 * libnagmedar_lab.so: FFmpeg, libdovi, whisper.cpp and libusb, linked privately so they never
 * meet libvlc's own copies: the Lab tools, the library's media probe (S-802), the subtitles from
 * speech (S-709) and the extraction of embedded subtitles. Every call is
 * blocking and runs on a background thread; [cancel] stops the running Lab tool. Results are
 * JSON objects (see native/lab).
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

    /**
     * SP-05: transcribe the first audio track of [input] with the ggml model at [model], on
     * [threads] threads (0: the performance cores), on the GPU when [gpu] and the device has one.
     */
    @JvmStatic external fun whisper(model: String, input: String, language: String, maxSeconds: Double, threads: Int, gpu: Boolean, listener: Progress?): String

    /** S-709: what a transcription tells as it goes, on the thread that runs it. */
    interface Transcription {
        /** [doneSeconds] of the film transcribed of [totalSeconds] (0 when the film does not say). */
        fun onProgress(doneSeconds: Double, totalSeconds: Double)

        /** One line of speech, its times in milliseconds of the film. */
        fun onSegment(startMs: Long, endMs: Long, text: String)

        /** The language whisper heard ("tr"), when it was asked to find it. */
        fun onLanguage(code: String)

        /**
         * What it runs on: the GPU ([gpu], its name in [device]) or the processor, on [threads]
         * threads; once the model is loaded, and again when the GPU stopped and the processor took over.
         */
        fun onEngine(gpu: Boolean, device: String, threads: Int)
    }

    /**
     * S-709: the speech of audio track [audioTrack] of [input] (the n-th audio track, 0 first; -1
     * for the best one) transcribed with the ggml model at [model], in [language] ("tr", "en",
     * "auto"), into English when [translate], on [threads] threads (0: the performance cores), on
     * the GPU through Vulkan when [gpu] and the device has one (else, or when it stops, on the
     * processor); five minutes at a time, so a film of any length fits in memory. Blocking;
     * [cancelTranscription] stops it. The result is a JSON object: "error" ("" when it went
     * through), "detail", "language", "audio_seconds", "seconds" (the time it took), "gpu",
     * "device", "threads", "fallback" ("" or why the GPU asked for was not used).
     */
    @JvmStatic external fun transcribe(model: String, input: String, audioTrack: Int, language: String, translate: Boolean, threads: Int, gpu: Boolean, listener: Transcription): String

    /** S-709: stops the running [transcribe] soon; the Lab's [cancel] does not. */
    @JvmStatic external fun cancelTranscription()

    /** How far an extraction is: [fraction] of the film read, 0..1; on the thread that runs it. */
    fun interface SubtitleExtraction {
        fun onProgress(fraction: Double)
    }

    /**
     * Subtitle track [track] of [input] (the n-th subtitle track, 0 first, as [MediaProbe] lists
     * them; a path or /proc/self/fd/N) written into the new file [output]: SRT, or ASS/SSA kept
     * with its header and styles. The film is read once, start to end, with only the lines in
     * memory; picture subtitles are refused. Blocking; [cancelSubtitleExtraction] stops it. The
     * result is a JSON object: "error" ("" when it went through, "open", "no_stream", "image",
     * "unsupported", "empty", "too_large", "write", "cancelled"), "detail", "codec", "format"
     * ("srt", "ass", "ssa"), "events" and "partial" (the file ended early).
     */
    @JvmStatic external fun extractSubtitle(input: String, track: Int, output: String, listener: SubtitleExtraction): String

    /** Stops the running [extractSubtitle] soon; the Lab's [cancel] and [cancelTranscription] do not. */
    @JvmStatic external fun cancelSubtitleExtraction()

    /** SP-02: formats, clock and rates of the USB audio device behind [fd]. */
    @JvmStatic external fun usbDescribe(fd: Int): String

    /** SP-02: a -6 dBFS sine straight to the USB DAC behind [fd]. */
    @JvmStatic external fun usbTone(fd: Int, rate: Int, bits: Int, seconds: Double, frequency: Double, listener: Progress?): String

    /** S-802: streams, HDR and Dolby Vision of [input] (a path or /proc/self/fd/N); see [MediaProbe]. */
    @JvmStatic external fun probe(input: String): String
}
