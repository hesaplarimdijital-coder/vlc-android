package app.nagmedar.ui.radio.record

import app.nagmedar.ui.radio.OkHttpRadioBrowser
import app.nagmedar.ui.radio.StationImport
import app.nagmedar.ui.radio.StationPlaylists
import app.nagmedar.ui.radio.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * S-1107: opens a station's stream with a connection of its own (OkHttp), asking for the ICY
 * metadata (`Icy-MetaData: 1`) and reading `icy-metaint`; the player's connection (libVLC) is never
 * touched, so listening goes on while recording. A `.pls` or `.m3u` address (a playlist with the
 * stream in it) is followed to the first stream in it; an HLS playlist (`.m3u8`, or the tags in
 * any playlist) is handed back to be read by an [HlsReader] with an [OkHttpHlsClient].
 */
internal class OkHttpStreamOpener(
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient,
) : StreamOpener {

    override suspend fun open(url: String): OpenedStream {
        var address = url
        repeat(MAX_HOPS + 1) {
            val request = try {
                Request.Builder().url(address)
                    .header("User-Agent", userAgent)
                    .header("Icy-MetaData", "1")
                    .header("Accept", "*/*")
                    .build()
            } catch (e: IllegalArgumentException) {
                // rtsp:, mms: and the like are no HTTP stream
                throw StreamUnsupported(UnsupportedStream.Format)
            }
            val call = client.newCall(request)
            val response = call.await()
            try {
                if (!response.isSuccessful) throw StreamRefused(response.code)
                val type = response.header("Content-Type")
                if (isPlaylist(type, address)) {
                    val text = playlistText(response)
                    if (HlsPlaylists.isHls(text)) {
                        val start = HlsStart(response.request.url.toString(), text, OkHttpHlsClient(client, userAgent))
                        response.close()
                        return OpenedStream(type, 0, EmptySource, playlist = start)
                    }
                    address = firstStream(text, type, address) ?: throw StreamUnsupported(UnsupportedStream.Format)
                } else {
                    val body = response.body ?: throw IOException("no body")
                    val opened = OpenedStream(
                        contentType = type,
                        metaInt = IcyDemuxer.metaInt(response.header("icy-metaint")),
                        source = BodySource(call, response, body.byteStream()),
                        serverName = response.header("icy-name")?.trim()?.takeIf { it.isNotEmpty() },
                    )
                    return opened
                }
            } catch (e: Throwable) {
                response.close()
                throw e
            }
            response.close()
        }
        throw StreamUnsupported(UnsupportedStream.Format)
    }

    /** Whether the answer is a playlist file (a stream's address, or an HLS playlist) and not the stream. */
    private fun isPlaylist(type: String?, url: String): Boolean {
        val t = type?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        if (t in PLAYLIST_TYPES) return true
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return path.endsWith(".m3u8") || (t == null && (path.endsWith(".pls") || path.endsWith(".m3u")))
    }

    /** The text of the playlist [response] is (at most [MAX_PLAYLIST] bytes of it). */
    private suspend fun playlistText(response: Response): String = withContext(Dispatchers.IO) {
        val body = response.body ?: return@withContext ""
        val bytes = ByteArray(MAX_PLAYLIST)
        var total = 0
        body.byteStream().use { input ->
            while (total < bytes.size) {
                val n = input.read(bytes, total, bytes.size - total)
                if (n < 0) break
                total += n
            }
        }
        String(bytes, 0, total, Charsets.UTF_8)
    }

    /** The first stream address in the station list [text]; null when there is none. */
    private fun firstStream(text: String, type: String?, url: String): String? {
        val name = if (type?.contains("scpls", ignoreCase = true) == true || url.substringBefore('?').endsWith(".pls", ignoreCase = true)) "list.pls" else "list.m3u"
        val bytes = text.toByteArray(Charsets.UTF_8)
        return when (val read = StationPlaylists.read(bytes.copyOf(bytes.size.coerceAtMost(StationPlaylists.MAX_BYTES)), name)) {
            is StationImport.Found -> read.stations.firstOrNull()?.url
            else -> null
        }
    }

    /** The body of the response: closing it, from any thread, ends a read that waits. */
    private class BodySource(private val call: Call, private val response: Response, private val input: InputStream) : StreamSource {
        override fun read(buffer: ByteArray): Int = input.read(buffer)

        override fun close() {
            call.cancel()
            runCatching { response.close() }
        }
    }

    /** An HLS stream has no bytes of its own to read. */
    private object EmptySource : StreamSource {
        override fun read(buffer: ByteArray): Int = -1

        override fun close() = Unit
    }

    companion object {
        private const val MAX_HOPS = 3
        private const val MAX_PLAYLIST = 1024 * 1024

        private val PLAYLIST_TYPES = setOf(
            "audio/x-scpls", "application/pls+xml", "audio/scpls", "audio/x-mpegurl", "audio/mpegurl",
            "application/vnd.apple.mpegurl", "application/x-mpegurl", "application/mpegurl",
        )

        /** No overall timeout: a stream is open for hours; a connection that says nothing for this long is dead. */
        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        fun create(version: String?): OkHttpStreamOpener = OkHttpStreamOpener(OkHttpRadioBrowser.userAgent(version))
    }
}

/**
 * S-1107: fetches the playlists and segments of an HLS stream (OkHttp), one request at a time, with
 * the app's user agent. [cancel] ends the request in flight and refuses the rest.
 */
internal class OkHttpHlsClient(private val client: OkHttpClient, private val userAgent: String) : HlsClient {

    @Volatile
    private var current: Call? = null

    @Volatile
    private var cancelled = false

    override fun fetch(url: String, range: HlsByteRange?, limit: Int): ByteArray {
        if (cancelled) throw IOException("cancelled")
        val request = try {
            Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "*/*").apply {
                if (range != null) header("Range", "bytes=${range.offset}-${range.offset + range.length - 1}")
            }.build()
        } catch (e: IllegalArgumentException) {
            throw IOException("bad address", e)
        }
        val call = client.newCall(request)
        current = call
        // a cancel that came between the check and here
        if (cancelled) call.cancel()
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw StreamRefused(response.code)
                val body = response.body ?: throw IOException("no body")
                val input = body.byteStream()
                // a server that ignores the range sends the whole file: the part is cut out of it
                if (range != null && response.code == 200) input.skipFully(range.offset)
                val wanted = if (range != null) minOf(range.length, limit.toLong()).toInt() else limit
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(32 * 1024)
                while (out.size() < wanted) {
                    val n = input.read(buffer, 0, minOf(buffer.size, wanted - out.size()))
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                // one more byte than allowed means the file is larger than anything a segment is
                if (range == null && out.size() == limit && input.read() >= 0) throw IOException("too large")
                return out.toByteArray()
            }
        } finally {
            current = null
        }
    }

    override fun cancel() {
        cancelled = true
        current?.cancel()
    }

    private fun InputStream.skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val n = skip(left)
            if (n > 0) {
                left -= n
            } else {
                if (read() < 0) throw IOException("the range is past the end")
                left--
            }
        }
    }
}
