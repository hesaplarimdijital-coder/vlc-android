package app.nagmedar.ui.player.subtitles.translate

import app.nagmedar.translate.ModelFile
import app.nagmedar.translate.ModelStore
import app.nagmedar.translate.TranslationModel
import app.nagmedar.ui.podcast.executeCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A model's files into the [store] (S-710), over HTTPS with [client]: each file not yet in place
 * goes into its ".part", asked for from where the part ends (a Range request), so a download that
 * stopped goes on; a finished part is checked against its pinned size and SHA-256 and only then
 * takes its name. [onProgress] is told the model's bytes here, as they come.
 */
internal class TranslationModelDownloader(private val client: OkHttpClient, private val store: ModelStore, private val userAgent: String) {

    enum class Outcome { Done, Network, Corrupt, NoSpace, Failed }

    suspend fun download(model: TranslationModel, onProgress: (Long) -> Unit): Outcome = withContext(Dispatchers.IO) {
        val folder = store.folder(model)
        if (!folder.isDirectory && !folder.mkdirs()) return@withContext Outcome.Failed
        // the space it still needs, and a little more: a full disk is said before a byte comes
        val missing = model.size - store.downloaded(model)
        if (folder.usableSpace < missing + SPARE_BYTES) return@withContext Outcome.NoSpace
        for (file in model.files) {
            if (store.has(model, file)) continue
            val outcome = try {
                fetch(model, file, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Outcome.Network
            }
            if (outcome != Outcome.Done) return@withContext outcome
            if (!store.accept(model, file)) return@withContext Outcome.Corrupt
        }
        if (store.isReady(model)) Outcome.Done else Outcome.Failed
    }

    /** [file] into its part, from where the part ends; Done when the part has the whole file. */
    private suspend fun fetch(model: TranslationModel, file: ModelFile, onProgress: (Long) -> Unit): Outcome {
        val part = store.part(model, file)
        var have = if (part.isFile) part.length() else 0L
        if (have > file.size) {
            part.delete()
            have = 0L
        }
        if (have == file.size) return Outcome.Done
        val request = Request.Builder()
            .url(model.url(file))
            .header("User-Agent", userAgent)
            .apply { if (have > 0) header("Range", "bytes=$have-") }
            .build()
        val base = store.downloaded(model) - have
        return client.newCall(request).executeCancellable { response ->
            when {
                // the part was the whole file already
                response.code == HTTP_RANGE_NOT_SATISFIABLE -> if (part.length() == file.size) Outcome.Done else {
                    part.delete()
                    Outcome.Corrupt
                }
                !response.isSuccessful -> if (response.code >= 500 || response.code == 429) Outcome.Network else Outcome.Failed
                else -> {
                    // a server that ignores the range sends the whole file: it is started again
                    val append = have > 0 && response.code == HTTP_PARTIAL
                    var written = if (append) have else 0L
                    val body = response.body ?: return@executeCancellable Outcome.Network
                    FileOutputStream(part, append).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(BUFFER)
                            while (written < file.size) {
                                val read = input.read(buffer, 0, minOf(BUFFER.toLong(), file.size - written).toInt())
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                written += read
                                onProgress(base + written)
                            }
                        }
                    }
                    if (written == file.size) Outcome.Done else Outcome.Network
                }
            }
        }
    }

    companion object {
        /** Long reads: a model file is hundreds of megabytes. */
        val client: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        }

        private const val BUFFER = 1 shl 16
        private const val SPARE_BYTES = 64L * 1024 * 1024
        private const val HTTP_PARTIAL = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
