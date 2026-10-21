package app.nagmedar.translate

import java.io.Closeable
import java.io.IOException

/**
 * libnagmedar_translate.so (S-710): CTranslate2 running an OPUS-MT model on the CPU in int8,
 * SentencePiece cutting its text into the model's pieces. Every call is blocking and runs on a
 * background thread; a model is opened once, used for every batch of a file, and closed.
 */
object NativeTranslate {

    /** False when this build was made without the translation engine (local builds). */
    val available: Boolean by lazy {
        runCatching { System.loadLibrary("nagmedar_translate") }.isSuccess
    }

    /** "CTranslate2 4.8.3 · SentencePiece 0.2.1". */
    @JvmStatic external fun version(): String

    /** The model in [folder] (see [TranslationModel]) loaded with [threads] threads; throws an IOException when it cannot be. */
    @JvmStatic external fun open(folder: String, threads: Int): Long

    /** [input]'s lines (UTF-8, apart by "\n") translated, as many lines in the same order. */
    @JvmStatic external fun translate(handle: Long, input: ByteArray, beam: Int): ByteArray

    @JvmStatic external fun close(handle: Long)
}

/**
 * A loaded model (S-710): [translate] takes lines and gives their translations, one batch at a
 * time. Not for two threads at once; [close] lets the model's memory go.
 */
class LoadedModel private constructor(private var handle: Long) : Closeable {

    /** [lines] translated, in their order; an empty or blank line stays empty. */
    @Synchronized
    fun translate(lines: List<String>, beam: Int = DEFAULT_BEAM): List<String> {
        check(handle != 0L) { "closed" }
        if (lines.isEmpty()) return emptyList()
        // a line break inside a line would be another line on the way
        val input = lines.joinToString("\n") { it.replace('\n', ' ').replace('\r', ' ') }.toByteArray(Charsets.UTF_8)
        val output = NativeTranslate.translate(handle, input, beam).toString(Charsets.UTF_8).split('\n')
        return List(lines.size) { output.getOrNull(it).orEmpty() }
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) NativeTranslate.close(handle)
        handle = 0L
    }

    companion object {
        /** Two hypotheses: nearly the quality of four, at half the time on a phone's CPU. */
        const val DEFAULT_BEAM = 2

        /** The model in [folder] with [threads] threads; an IOException when the engine or the model is missing or broken. */
        fun open(folder: String, threads: Int): LoadedModel {
            if (!NativeTranslate.available) throw IOException("no translation engine in this build")
            return LoadedModel(NativeTranslate.open(folder, threads))
        }
    }
}
