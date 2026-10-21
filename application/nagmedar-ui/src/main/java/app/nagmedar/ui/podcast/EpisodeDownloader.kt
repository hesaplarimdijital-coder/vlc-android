package app.nagmedar.ui.podcast

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** S-1103: why an episode could not be downloaded. */
sealed class DownloadFailure(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** The address is no http(s) address. */
    class BadAddress : DownloadFailure("bad address")

    /** The server answered with [code]. */
    class Server(val code: Int) : DownloadFailure("HTTP $code")

    /** The connection failed or broke; what was received stays for the next try. */
    class Network(cause: Throwable) : DownloadFailure("network", cause)

    /** The device has no room for the file. */
    class DiskFull : DownloadFailure("no space")

    /** The server ended the body before its length; what was received stays for the next try. */
    class Incomplete(val received: Long, val expected: Long) : DownloadFailure("incomplete: $received of $expected")
}

/** S-1103: downloads a file; [EpisodeDownloader] in the app, a fake in the tests. */
interface FileDownloader {
    /** Downloads [url] into [part], going on from what is there; returns the file's size. Throws [DownloadFailure]. */
    suspend fun download(url: String, part: File, progress: (done: Long, total: Long) -> Unit): Long
}

/** S-1103: thrown by a progress callback to stop the download at once; the partial file stays. */
class DownloadCancelled : RuntimeException("cancelled")

/**
 * S-1103: downloads one file with HTTP Range, so a download that was cut off goes on where it stopped: the partial
 * file is kept, with the validator (ETag or Last-Modified) the server gave beside it; a later request asks for the
 * bytes after those with `If-Range`, and a server that answers with the whole file (the file changed, or it knows no
 * ranges) is taken from the start. Compression is off (`Accept-Encoding: identity`), so lengths are the file's.
 * The call is tied to the coroutine: cancelling it cancels the connection.
 */
class EpisodeDownloader(
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient,
    /** Room kept free on the device while downloading, in bytes. */
    private val reserveBytes: Long = RESERVE_BYTES,
) : FileDownloader {
    /** [progress] hears the bytes done and the file's length (-1 when the server does not say) and may throw [DownloadCancelled]. */
    override suspend fun download(url: String, part: File, progress: (done: Long, total: Long) -> Unit): Long {
        val address = url.toHttpUrlOrNull() ?: throw DownloadFailure.BadAddress()
        part.parentFile?.mkdirs()
        val marker = File(part.path + MARKER)
        var from = if (part.isFile) part.length() else 0L
        var validator = if (marker.isFile) marker.readText().trim() else ""
        if (from > 0 && validator.isEmpty()) {
            part.delete()
            from = 0
        }
        for (pass in 0..1) {
            val request = Request.Builder().url(address)
                .header("User-Agent", userAgent)
                .header("Accept-Encoding", "identity")
                .apply {
                    if (from > 0) {
                        header("Range", "bytes=$from-")
                        header("If-Range", validator)
                    }
                }
                .build()
            val offset = from
            try {
                val size = client.newCall(request).executeCancellable { response -> receive(response, part, marker, offset, progress) }
                marker.delete()
                return size
            } catch (e: Restart) {
                // the server did not take the range: from the start
                part.delete()
                marker.delete()
                from = 0
                validator = ""
            } catch (e: DownloadFailure) {
                throw e
            } catch (e: IOException) {
                throw if (isDiskFull(e)) DownloadFailure.DiskFull() else DownloadFailure.Network(e)
            }
            if (pass == 1) break
        }
        throw DownloadFailure.Server(HTTP_RANGE_NOT_SATISFIABLE)
    }

    /** The answer was no use for resuming. */
    private class Restart : RuntimeException()

    private fun receive(response: Response, part: File, marker: File, from: Long, progress: (Long, Long) -> Unit): Long {
        val append: Boolean
        val total: Long
        when (response.code) {
            HTTP_OK -> {
                append = false
                total = response.body?.contentLength() ?: -1L
                (response.header("ETag") ?: response.header("Last-Modified"))?.let { marker.writeText(it) } ?: marker.delete()
            }
            HTTP_PARTIAL -> {
                val range = CONTENT_RANGE.matchEntire(response.header("Content-Range").orEmpty().trim()) ?: throw Restart()
                if (range.groupValues[1].toLong() != from) throw Restart()
                append = true
                total = range.groupValues[3].toLongOrNull() ?: -1L
            }
            HTTP_RANGE_NOT_SATISFIABLE -> {
                // "bytes */1234": the file is as long as what is here, so it is whole; any other length: start again
                val length = response.header("Content-Range")?.substringAfter('/', "")?.trim()?.toLongOrNull()
                if (length != null && length == from && from > 0) return from
                throw Restart()
            }
            else -> throw DownloadFailure.Server(response.code)
        }
        val body = response.body ?: throw DownloadFailure.Network(IOException("no body"))
        val remaining = if (total > 0) total - (if (append) from else 0L) else -1L
        val free = (part.parentFile ?: part).usableSpace
        if (remaining > 0 && free < remaining + reserveBytes) throw DownloadFailure.DiskFull()
        var done = if (append) from else 0L
        progress(done, total)
        FileOutputStream(part, append).use { out ->
            val buffer = ByteArray(BUFFER)
            val source = body.byteStream()
            var reported = System.nanoTime()
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                done += read
                val now = System.nanoTime()
                if (now - reported >= REPORT_EVERY_NS) {
                    reported = now
                    progress(done, total)
                }
            }
            out.flush()
        }
        progress(done, total)
        if (total > 0 && done != total) throw DownloadFailure.Incomplete(done, total)
        return done
    }

    private fun isDiskFull(e: IOException): Boolean {
        val text = e.message.orEmpty()
        return "ENOSPC" in text || "No space left" in text
    }

    companion object {
        const val RESERVE_BYTES = 50L * 1024 * 1024
        private const val MARKER = ".etag"
        private const val BUFFER = 64 * 1024
        private const val REPORT_EVERY_NS = 200_000_000L
        private const val HTTP_OK = 200
        private const val HTTP_PARTIAL = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private val CONTENT_RANGE = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")

        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
