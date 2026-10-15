package app.nagmedar.ui.player.subtitles

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A subtitle file OpenSubtitles.com has for a film (S-706). */
@Immutable
data class OnlineSubtitle(
    val fileId: Long,
    val fileName: String,
    /** The release it was timed for: "Northern.Light.2024.1080p.WEB-DL". */
    val release: String,
    /** "tr", "en", "pt-BR". */
    val language: String,
    val downloads: Long,
    /** Found by the file's fingerprint: timed for this very file. */
    val hashMatch: Boolean = false,
    val hearingImpaired: Boolean = false,
    val fps: Double? = null,
    val title: String = "",
    val year: Int? = null,
    val machineTranslated: Boolean = false,
    /** Users' rating, 0 to 1; null when nobody rated it. */
    val rating: Double? = null,
)

/** What was asked for: the file's fingerprint, the film's name, the languages; the film's IMDb number, the season and the episode of a series. */
@Immutable
data class OnlineQuery(
    val hash: String?,
    val name: String?,
    val languages: List<String>,
    val imdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

/** A download granted: where the file is, and how many more the day allows. */
@Immutable
data class OnlineTicket(val link: String, val fileName: String, val remaining: Int, val requests: Int, val resetUtc: String?)

/** Why OpenSubtitles could not answer. */
sealed class OnlineFailure(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** This build was made without Nağmedar's API key. */
    class NoKey : OnlineFailure("no key")

    /** No network, or the server could not be reached. */
    class Offline(cause: Throwable? = null) : OnlineFailure("offline", cause)

    /** The day's downloads are used up; they come back at [resetUtc]. */
    class QuotaOver(val resetUtc: String?) : OnlineFailure("quota")

    /** Another answer: its HTTP status. */
    class Server(val status: Int) : OnlineFailure("status $status")

    /** A provider that searches a folder of the user's, and none is chosen. */
    class NoFolder : OnlineFailure("no folder")
}

/** OpenSubtitles.com's REST API (S-706), as the subtitles' search uses it. */
interface OpenSubtitlesApi {
    /** Whether this build has an API key. */
    val available: Boolean

    suspend fun search(query: OnlineQuery): List<OnlineSubtitle>

    suspend fun download(fileId: Long): OnlineTicket

    suspend fun fetch(link: String): ByteArray
}

/**
 * OpenSubtitles.com's REST API with Nağmedar's own key ([apiKey], from the build): the search,
 * by the file's fingerprint or the film's name, and a download's link with the day's quota.
 * Query parameters go in alphabetical order and in lower case, as the API asks.
 */
class OkHttpOpenSubtitles(
    private val apiKey: String,
    private val userAgent: String = USER_AGENT,
    private val client: OkHttpClient = defaultClient,
    private val base: String = BASE_URL,
) : OpenSubtitlesApi {

    override val available: Boolean get() = apiKey.isNotBlank()

    override suspend fun search(query: OnlineQuery): List<OnlineSubtitle> = withContext(Dispatchers.IO) {
        val url = base.toHttpUrl().newBuilder().addPathSegment("subtitles").apply {
            query.episode?.let { addQueryParameter("episode_number", it.toString()) }
            // the number without its "tt"
            query.imdbId?.filter { it.isDigit() }?.trimStart('0')?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("imdb_id", it) }
            if (query.languages.isNotEmpty()) addQueryParameter("languages", query.languages.map { it.lowercase(Locale.ROOT) }.sorted().joinToString(","))
            query.hash?.let { addQueryParameter("moviehash", it.lowercase(Locale.ROOT)) }
            addQueryParameter("order_by", "download_count")
            query.name?.takeIf { it.isNotBlank() }?.let { addQueryParameter("query", it.trim().lowercase(Locale.ROOT)) }
            query.season?.let { addQueryParameter("season_number", it.toString()) }
        }.build()
        OpenSubtitlesJson.results(call(Request.Builder().url(url).get()))
    }

    override suspend fun download(fileId: Long): OnlineTicket = withContext(Dispatchers.IO) {
        val body = JSONObject().put("file_id", fileId).toString().toRequestBody(JSON)
        OpenSubtitlesJson.ticket(call(Request.Builder().url(base.toHttpUrl().newBuilder().addPathSegment("download").build()).post(body)))
    }

    override suspend fun fetch(link: String): ByteArray = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(Request.Builder().url(link).header("User-Agent", userAgent).build()).execute()
        } catch (e: IOException) {
            throw OnlineFailure.Offline(e)
        }
        response.use {
            if (!it.isSuccessful) throw OnlineFailure.Server(it.code)
            val bytes = it.body?.bytes() ?: throw OnlineFailure.Server(it.code)
            if (bytes.size > MAX_FILE_BYTES) throw OnlineFailure.Server(it.code)
            bytes
        }
    }

    private fun call(request: Request.Builder): String {
        if (!available) throw OnlineFailure.NoKey()
        val built = request
            .header("Api-Key", apiKey)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()
        val response = try {
            client.newCall(built).execute()
        } catch (e: IOException) {
            throw OnlineFailure.Offline(e)
        }
        return response.use {
            val text = it.body?.string().orEmpty()
            when {
                it.isSuccessful -> text
                // "You have downloaded your allowed 5 subtitles for 24h"
                it.code == HTTP_NOT_ACCEPTABLE -> throw OnlineFailure.QuotaOver(OpenSubtitlesJson.resetTime(text))
                else -> throw OnlineFailure.Server(it.code)
            }
        }
    }

    companion object {
        const val BASE_URL = "https://api.opensubtitles.com/api/v1/"

        /** As the API asks: the app's name and version, as the key was registered for. */
        const val USER_AGENT = "Nagmedar v1.0"
        private const val HTTP_NOT_ACCEPTABLE = 406

        /** A subtitle is small: a larger answer is not one. */
        private const val MAX_FILE_BYTES = 5 * 1024 * 1024
        private val JSON = "application/json".toMediaType()

        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}

/** OpenSubtitles' answers, read. */
object OpenSubtitlesJson {
    /** Its ratings go to 10. */
    private const val RATING_SCALE = 10.0

    fun results(text: String): List<OnlineSubtitle> {
        val data = JSONObject(text).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            val attributes = data.optJSONObject(index)?.optJSONObject("attributes") ?: return@mapNotNull null
            val file = attributes.optJSONArray("files")?.optJSONObject(0) ?: return@mapNotNull null
            val fileId = file.optLong("file_id", -1).takeIf { it > 0 } ?: return@mapNotNull null
            val feature = attributes.optJSONObject("feature_details")
            OnlineSubtitle(
                fileId = fileId,
                fileName = file.text("file_name").orEmpty(),
                release = attributes.text("release").orEmpty(),
                language = attributes.text("language").orEmpty(),
                downloads = attributes.optLong("download_count", 0),
                hashMatch = attributes.optBoolean("moviehash_match", false),
                hearingImpaired = attributes.optBoolean("hearing_impaired", false),
                fps = attributes.optDouble("fps", 0.0).takeIf { it > 0 },
                title = feature?.text("title").orEmpty(),
                year = feature?.optInt("year", 0)?.takeIf { it > 0 },
                machineTranslated = attributes.optBoolean("machine_translated", false) || attributes.optBoolean("ai_translated", false),
                rating = attributes.optDouble("ratings", 0.0).takeIf { it > 0 }?.let { (it / RATING_SCALE).coerceAtMost(1.0) },
            )
        }
    }

    fun ticket(text: String): OnlineTicket {
        val answer = JSONObject(text)
        return OnlineTicket(
            link = answer.text("link") ?: throw OnlineFailure.Server(200),
            fileName = answer.text("file_name").orEmpty(),
            remaining = answer.optInt("remaining", -1),
            requests = answer.optInt("requests", -1),
            resetUtc = answer.text("reset_time_utc"),
        )
    }

    /** When the day's downloads come back, from a refusal. */
    fun resetTime(text: String): String? = runCatching { JSONObject(text).text("reset_time_utc") }.getOrNull()

    /** A string field, or null when it is missing or null. */
    private fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
}

/**
 * OpenSubtitles' fingerprint of a film's file: its size plus the 64-bit little-endian words of
 * its first and last 64 KiB, as 16 hexadecimal digits.
 */
fun movieHash(channel: FileChannel): String {
    val size = channel.size()
    val chunk = minOf(HASH_CHUNK.toLong(), size).toInt()
    val head = sumOfWords(read(channel, 0, chunk))
    val tail = sumOfWords(read(channel, (size - HASH_CHUNK).coerceAtLeast(0), chunk))
    return "%016x".format(Locale.ROOT, size + head + tail)
}

/** A local file's or a document's fingerprint; null for a stream, or what cannot be read. */
suspend fun movieHash(resolver: ContentResolver, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        when (uri.scheme) {
            null, ContentResolver.SCHEME_FILE -> FileInputStream(File(requireNotNull(uri.path))).use { movieHash(it.channel) }
            ContentResolver.SCHEME_CONTENT -> resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { movieHash(it.channel) }
            }
            else -> null
        }
    }.getOrNull()
}

private const val HASH_CHUNK = 64 * 1024

private fun read(channel: FileChannel, from: Long, length: Int): ByteBuffer {
    val buffer = ByteBuffer.allocate(length)
    var position = from
    while (buffer.hasRemaining()) {
        val read = channel.read(buffer, position)
        if (read <= 0) break
        position += read
    }
    buffer.flip()
    return buffer
}

private fun sumOfWords(buffer: ByteBuffer): Long {
    val words = buffer.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
    var sum = 0L
    while (words.hasRemaining()) sum += words.get()
    return sum
}
