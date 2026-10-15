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
 * stream in it) is followed to the first stream in it; a segmented stream (HLS) is refused.
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
                    address = firstStream(response, type, address) ?: throw StreamUnsupported(UnsupportedStream.Format)
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

    /** Whether the answer is a playlist file with a stream's address in it, not the stream. */
    private fun isPlaylist(type: String?, url: String): Boolean {
        val t = type?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        if (t in PLAYLIST_TYPES) return true
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return t == null && (path.endsWith(".pls") || path.endsWith(".m3u"))
    }

    /** The first stream address in the playlist [response] is; null when there is none. HLS (segments) is [StreamUnsupported]. */
    private suspend fun firstStream(response: Response, type: String?, url: String): String? = withContext(Dispatchers.IO) {
        val body = response.body ?: return@withContext null
        val bytes = ByteArray(StationPlaylists.MAX_BYTES.coerceAtMost(MAX_PLAYLIST))
        var total = 0
        body.byteStream().use { input ->
            while (total < bytes.size) {
                val n = input.read(bytes, total, bytes.size - total)
                if (n < 0) break
                total += n
            }
        }
        val head = String(bytes, 0, total.coerceAtMost(4096), Charsets.ISO_8859_1)
        if (head.contains("#EXT-X-")) throw StreamUnsupported(UnsupportedStream.Segmented)
        val name = if (type?.contains("scpls", ignoreCase = true) == true || url.substringBefore('?').endsWith(".pls", ignoreCase = true)) "list.pls" else "list.m3u"
        when (val read = StationPlaylists.read(bytes.copyOf(total), name)) {
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

    companion object {
        private const val MAX_HOPS = 3
        private const val MAX_PLAYLIST = 64 * 1024

        private val PLAYLIST_TYPES = setOf(
            "audio/x-scpls", "application/pls+xml", "audio/scpls", "audio/x-mpegurl", "audio/mpegurl",
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
