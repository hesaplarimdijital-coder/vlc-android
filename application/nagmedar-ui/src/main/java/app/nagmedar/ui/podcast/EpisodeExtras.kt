package app.nagmedar.ui.podcast

import android.content.Context
import android.net.Uri
import app.nagmedar.data.PodcastEpisode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** S-1104: what asking for an episode's chapters or transcript came to. */
sealed interface ExtraLoad<out T> {
    data class Ready<T>(val value: T) : ExtraLoad<T>

    /** The episode has none, or what it names is no use. */
    data object None : ExtraLoad<Nothing>

    /** It may have one, but the network did not answer. */
    data object Offline : ExtraLoad<Nothing>
}

/** S-1104: where the bytes of chapters and transcripts come from: the network, and the downloaded file. A fake in the tests. */
interface ExtraSource {
    /** At most [limit] bytes of [url] from [offset]; null when it is not there (a 4xx answer); throws [IOException] when the network fails or the server does. */
    suspend fun bytes(url: String, limit: Int, offset: Long = 0): ByteArray?

    /** At most [limit] bytes from the start of the downloaded file at [location]; null when it cannot be read. */
    fun local(location: String, limit: Int): ByteArray?
}

/**
 * S-1104, S-205: an episode's chapters and transcript, fetched once and kept in the cache's folder. Chapters come from
 * the feed's Podcasting 2.0 file when it names one, else from the ID3 tag at the start of the audio (the downloaded
 * file, else the first bytes of the stream); the transcript from the file the feed names, read by what its text says it
 * is. Nothing is kept that came to nothing, so a later try asks again. The folder is asked of [cacheFolder] on [io], at
 * the first read or write: the app's cacheDir looks at the disk, and the pages make this on the main thread.
 */
class EpisodeExtras(
    private val source: ExtraSource,
    cacheFolder: () -> File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val cache by lazy(cacheFolder)

    suspend fun chapters(episode: PodcastEpisode): ExtraLoad<List<Chapter>> {
        val kept = withContext(io) { readChapters(episode.id) }
        if (kept != null) return ExtraLoad.Ready(kept)
        var offline = false
        episode.chaptersUrl?.takeIf { isHttp(it) }?.let { url ->
            try {
                val bytes = source.bytes(url, MAX_JSON)
                val found = bytes?.let { Chapters.parseJson(String(it, Charsets.UTF_8)) }.orEmpty()
                if (found.isNotEmpty()) return keep(episode.id, found)
            } catch (e: IOException) {
                offline = true
            }
        }
        try {
            val found = id3Chapters(episode)
            if (found.isNotEmpty()) return keep(episode.id, found)
        } catch (e: IOException) {
            offline = true
        }
        return if (offline) ExtraLoad.Offline else ExtraLoad.None
    }

    private suspend fun id3Chapters(episode: PodcastEpisode): List<Chapter> {
        val local = episode.downloadPath
        val head: ByteArray?
        val tag: ByteArray?
        if (local != null) {
            head = withContext(io) { source.local(local, ID3_HEADER) }
            val size = head?.let(Chapters::id3Size) ?: return emptyList()
            tag = withContext(io) { source.local(local, minOf(size, MAX_ID3)) }
        } else {
            // only an MP3 carries ID3 at its start; asking the others for their head would only cost
            if (!looksLikeMp3(episode)) return emptyList()
            head = source.bytes(episode.audioUrl, ID3_HEADER)
            val size = head?.let(Chapters::id3Size) ?: return emptyList()
            tag = source.bytes(episode.audioUrl, minOf(size, MAX_ID3))
        }
        return tag?.let(Chapters::parseId3).orEmpty()
    }

    private fun looksLikeMp3(episode: PodcastEpisode): Boolean =
        DownloadFiles.extension(episode) == "mp3" && episode.mimeType?.lowercase(Locale.ROOT)?.let { it == "audio/mpeg" || it == "audio/mp3" } != false

    suspend fun transcript(episode: PodcastEpisode): ExtraLoad<Transcript> {
        val url = episode.transcriptUrl?.takeIf { isHttp(it) } ?: return ExtraLoad.None
        val kept = withContext(io) { readTranscript(episode.id) }
        if (kept != null) return ExtraLoad.Ready(kept)
        val bytes = try {
            source.bytes(url, MAX_TRANSCRIPT)
        } catch (e: IOException) {
            return ExtraLoad.Offline
        } ?: return ExtraLoad.None
        val text = String(bytes, Charsets.UTF_8)
        val parsed = Transcripts.parse(text, episode.transcriptType) ?: return ExtraLoad.None
        withContext(io) {
            runCatching {
                cache.mkdirs()
                File(cache, "transcript-${episode.id}.txt").writeText(episode.transcriptType.orEmpty() + "\n" + text)
            }
        }
        return ExtraLoad.Ready(parsed)
    }

    // ------------------------------------------------------------ the cache

    private suspend fun keep(episodeId: Long, chapters: List<Chapter>): ExtraLoad<List<Chapter>> {
        withContext(io) {
            runCatching {
                cache.mkdirs()
                File(cache, "chapters-$episodeId.json").writeText(encode(chapters))
            }
        }
        return ExtraLoad.Ready(chapters)
    }

    private fun readChapters(episodeId: Long): List<Chapter>? {
        val file = File(cache, "chapters-$episodeId.json")
        if (!file.isFile) return null
        return runCatching { decode(file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun readTranscript(episodeId: Long): Transcript? {
        val file = File(cache, "transcript-$episodeId.txt")
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        return Transcripts.parse(text.substringAfter('\n'), text.substringBefore('\n').ifEmpty { null })
    }

    /** Drops what is kept of [episodeId] (the episode is gone). */
    fun forget(episodeId: Long) {
        File(cache, "chapters-$episodeId.json").delete()
        File(cache, "transcript-$episodeId.txt").delete()
    }

    private fun isHttp(url: String) = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    companion object {
        const val MAX_JSON = 1_000_000
        const val MAX_TRANSCRIPT = 4_000_000
        const val MAX_ID3 = 4_000_000
        private const val ID3_HEADER = 10

        internal fun encode(chapters: List<Chapter>): String = JSONArray().apply {
            chapters.forEach { c ->
                put(
                    JSONObject().put("s", c.startMs).put("t", c.title).apply {
                        c.endMs?.let { put("e", it) }
                        c.imageUrl?.let { put("i", it) }
                        c.url?.let { put("u", it) }
                    },
                )
            }
        }.toString()

        internal fun decode(text: String): List<Chapter> = try {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Chapter(
                    startMs = o.optLong("s", -1).takeIf { it >= 0 } ?: return@mapNotNull null,
                    title = o.optString("t", ""),
                    endMs = if (o.has("e")) o.optLong("e") else null,
                    imageUrl = o.optString("i", "").ifEmpty { null },
                    url = o.optString("u", "").ifEmpty { null },
                )
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}

/** S-1104: [ExtraSource] in the app: OkHttp for the network (the app's User-Agent, no cookies), the files and documents of the downloads for the rest. */
class OkHttpExtraSource(
    private val context: Context,
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient,
) : ExtraSource {
    override suspend fun bytes(url: String, limit: Int, offset: Long): ByteArray? {
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Encoding", "identity")
            .apply { if (offset > 0 || limit < Int.MAX_VALUE) header("Range", "bytes=$offset-${offset + limit - 1}") }
            .build()
        return client.newCall(request).executeCancellable { response ->
            when {
                response.code == HTTP_OK || response.code == HTTP_PARTIAL -> {
                    val body = response.body ?: return@executeCancellable ByteArray(0)
                    val out = java.io.ByteArrayOutputStream(minOf(limit, BUFFER))
                    val buffer = ByteArray(BUFFER)
                    val stream = body.byteStream()
                    // a server that ignores the range sends the whole file: only the first bytes are read, then the call is closed
                    // an answer that starts elsewhere than asked for (200 with an offset) is of no use
                    if (response.code == HTTP_OK && offset > 0) return@executeCancellable null
                    while (out.size() < limit) {
                        val read = stream.read(buffer, 0, minOf(buffer.size, limit - out.size()))
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
                response.code >= HTTP_SERVER_ERROR || response.code == HTTP_TOO_MANY -> throw IOException("HTTP ${response.code}")
                else -> null
            }
        }
    }

    override fun local(location: String, limit: Int): ByteArray? = try {
        val stream = if (location.startsWith("/")) File(location).inputStream() else context.contentResolver.openInputStream(Uri.parse(location))
        stream?.use { input ->
            val out = ByteArray(limit)
            var filled = 0
            while (filled < limit) {
                val read = input.read(out, filled, limit - filled)
                if (read < 0) break
                filled += read
            }
            out.copyOf(filled)
        }
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_PARTIAL = 206
        const val HTTP_SERVER_ERROR = 500
        const val HTTP_TOO_MANY = 429
        const val BUFFER = 16 * 1024

        val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
