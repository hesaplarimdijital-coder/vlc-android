package app.nagmedar.ui.transcribe

import android.content.Context
import app.nagmedar.ui.podcast.executeCancellable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** S-709: why a model did not come down: no network, Hugging Face's answer, the file, or the device's storage. */
class ModelDownloadException(val reason: WhisperFailure, message: String, cause: Throwable? = null) : IOException(message, cause)

/** What Hugging Face tells of a model file before it is downloaded: where its bytes are, their SHA-256 and their size. */
internal data class RemoteModel(val location: String, val sha256: String?, val size: Long?) {
    fun toJson(): String = JSONObject().put("sha256", sha256 ?: "").put("size", size ?: -1L).toString()

    /** Whether a download begun for [json] is of this very file. */
    fun sameAs(json: String?): Boolean {
        val o = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        return o.optString("sha256") == (sha256 ?: "") && o.optLong("size", -1L) == (size ?: -1L)
    }
}

/**
 * S-709: a whisper model from Hugging Face into the app's storage ([WhisperModels]), only ever
 * started by the user. Hugging Face is asked first what the file is (its SHA-256 and its size,
 * which it tells of every large file); the bytes then come in an HTTP range from where a download
 * before stopped, a ".part" until they are all there and their size and SHA-256 match. A part of
 * another file (the model changed since) starts over. Where Hugging Face tells nothing, the size
 * must be close to the model's and the file must begin as a ggml model does.
 */
internal class ModelDownloader(
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient,
) {
    private val noRedirects: OkHttpClient by lazy { client.newBuilder().followRedirects(false).followSslRedirects(false).build() }

    /** [model] on the device; [onProgress] is told the bytes there of all of them (0 when unknown). Call it off the main thread. */
    suspend fun download(context: Context, model: WhisperModel, onProgress: (Long, Long) -> Unit): File {
        val target = WhisperModels.file(context, model)
        if (target.isFile) return target
        val folder = WhisperModels.folder(context)
        if (!folder.isDirectory && !folder.mkdirs()) throw ModelDownloadException(WhisperFailure.Storage, "no folder for the models")
        val part = WhisperModels.part(context, model)
        val info = WhisperModels.partInfo(context, model)
        val remote = network { describe(model) }
        if (!remote.sameAs(runCatching { info.readText() }.getOrNull())) {
            part.delete()
            storage { info.writeText(remote.toJson()) }
        }
        var have = if (part.isFile) part.length() else 0L
        if (remote.size != null && have > remote.size) {
            part.delete()
            have = 0L
        }
        val missing = (remote.size ?: model.expectedBytes) - have
        if (folder.usableSpace < missing + SPARE_BYTES) throw ModelDownloadException(WhisperFailure.Storage, "not enough space")
        if (remote.size == null || have < remote.size) fetch(remote, part, have, onProgress)
        verify(model, remote, part)
        if (!part.renameTo(target)) throw ModelDownloadException(WhisperFailure.Storage, "the model could not be put in place")
        info.delete()
        return target
    }

    /** Hugging Face's word on the file: its own redirect, not followed, tells its SHA-256 and size. */
    private suspend fun describe(model: WhisperModel): RemoteModel {
        val request = Request.Builder().url(model.url).head().header("User-Agent", userAgent).build()
        return noRedirects.newCall(request).executeCancellable { response ->
            val sha = response.header("X-Linked-Etag")?.trim('"', ' ', 'W', '/')?.lowercase()?.takeIf { it.matches(SHA256) }
            val size = response.header("X-Linked-Size")?.toLongOrNull()?.takeIf { it > 0 }
            when {
                response.isRedirect -> {
                    val location = response.header("Location")?.let { response.request.url.resolve(it) }
                        ?: throw ModelDownloadException(WhisperFailure.Server, "a redirect to nowhere")
                    RemoteModel(location.toString(), sha, size)
                }
                response.isSuccessful -> RemoteModel(model.url, sha, size ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 })
                else -> throw ModelDownloadException(WhisperFailure.Server, "HTTP ${response.code}")
            }
        }
    }

    /** The bytes from [have] on, onto [part]. */
    private suspend fun fetch(remote: RemoteModel, part: File, have: Long, onProgress: (Long, Long) -> Unit) {
        val builder = Request.Builder().url(remote.location).header("User-Agent", userAgent)
        if (have > 0) builder.header("Range", "bytes=$have-")
        network {
            client.newCall(builder.build()).executeCancellable { response ->
                val append = when {
                    response.code == HTTP_PARTIAL && have > 0 -> true
                    response.isSuccessful -> false
                    // the range is past the end: the part is all there, the check says whether it is right
                    response.code == HTTP_RANGE && have > 0 -> return@executeCancellable
                    else -> throw ModelDownloadException(WhisperFailure.Server, "HTTP ${response.code}")
                }
                val start = if (append) have else 0L
                val total = remote.size ?: (response.body?.contentLength()?.takeIf { it > 0 }?.plus(start) ?: 0L)
                val body = response.body ?: throw ModelDownloadException(WhisperFailure.Server, "no body")
                body.byteStream().use { input -> copy(input, part, append, start, total, onProgress) }
            }
        }
    }

    private fun copy(input: InputStream, part: File, append: Boolean, start: Long, total: Long, onProgress: (Long, Long) -> Unit) {
        val output = storage { FileOutputStream(part, append) }
        output.use { out ->
            val buffer = ByteArray(BUFFER)
            var done = start
            onProgress(done, total)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                storage { out.write(buffer, 0, n) }
                done += n
                onProgress(done, total)
            }
        }
    }

    /** The part checked: its size and SHA-256 as Hugging Face told them; a part that is not the model is removed. */
    private fun verify(model: WhisperModel, remote: RemoteModel, part: File) {
        val length = part.length()
        val right = when {
            remote.size != null && length != remote.size -> false
            remote.sha256 != null -> sha256(part) == remote.sha256
            // nothing told: about the model's size, and a ggml file
            else -> length in (model.expectedBytes * 8 / 10)..(model.expectedBytes * 5 / 4) && ggml(part)
        }
        if (!right) {
            part.delete()
            throw ModelDownloadException(WhisperFailure.Checksum, "the downloaded file is not ${model.file}")
        }
    }

    private fun sha256(file: File): String = storage {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Whether [file] begins as whisper.cpp's models do: "ggml" as a little-endian word. */
    private fun ggml(file: File): Boolean = storage {
        val head = ByteArray(GGML_MAGIC.size)
        file.inputStream().use { it.read(head) } == head.size && head.contentEquals(GGML_MAGIC)
    }

    /** A network step: an unreachable host or a silent one is "offline", any other failure the server's. */
    private suspend fun <T> network(block: suspend () -> T): T = try {
        block()
    } catch (e: ModelDownloadException) {
        throw e
    } catch (e: UnknownHostException) {
        throw ModelDownloadException(WhisperFailure.Offline, "offline", e)
    } catch (e: ConnectException) {
        throw ModelDownloadException(WhisperFailure.Offline, "offline", e)
    } catch (e: SocketTimeoutException) {
        throw ModelDownloadException(WhisperFailure.Offline, "timed out", e)
    } catch (e: IOException) {
        throw ModelDownloadException(WhisperFailure.Server, e.message ?: "download failed", e)
    }

    /** A step on the device's storage: its failure is the storage's (a full disk, say). */
    private inline fun <T> storage(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw ModelDownloadException(WhisperFailure.Storage, e.message ?: "storage", e)
    }

    private companion object {
        const val HTTP_PARTIAL = 206
        const val HTTP_RANGE = 416
        const val BUFFER = 1 shl 16

        /** Room left beside the model: the rest of the app and the system need some. */
        const val SPARE_BYTES = 200_000_000L
        val SHA256 = Regex("[0-9a-f]{64}")
        val GGML_MAGIC = byteArrayOf(0x6c, 0x6d, 0x67, 0x67)

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
