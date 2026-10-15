package app.nagmedar.ui.podcast

import app.nagmedar.ui.lyrics.RequestPace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * S-1101: Podcast Index (api.podcastindex.org), the primary directory: a free, open index with
 * Nagmedar's own key and secret from the build ([key], [secret]; either empty turns it off). Asks
 * `search/byterm` for shows and `podcasts/byfeedurl` for the id of one feed. Every request says who
 * asks ([userAgent]) and waits its turn ([pace]).
 */
class PodcastIndexDirectory(
    private val key: String,
    private val secret: String,
    private val userAgent: String,
    private val pace: RequestPace = RequestPace(spacingMs = 300, perWindow = 60),
    private val client: OkHttpClient = defaultClient,
    private val base: String = BASE_URL,
    /** Unix seconds; Podcast Index refuses requests whose time is far from its own. */
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) : PodcastDirectory {

    override val available: Boolean get() = key.isNotBlank() && secret.isNotBlank()

    override suspend fun search(term: String, limit: Int): List<PodcastHit> = withContext(Dispatchers.IO) {
        if (!available) return@withContext emptyList()
        val url = base.toHttpUrl().newBuilder().addPathSegments("search/byterm")
            .addQueryParameter("q", term.trim())
            .addQueryParameter("max", limit.toString())
            .build()
        PodcastIndexJson.hits(call(url) ?: return@withContext emptyList())
    }

    /** The show at [feedUrl], if Podcast Index lists it; null when it does not, or the service cannot answer. */
    suspend fun byFeedUrl(feedUrl: String): PodcastHit? = withContext(Dispatchers.IO) {
        if (!available) return@withContext null
        val url = base.toHttpUrl().newBuilder().addPathSegments("podcasts/byfeedurl").addQueryParameter("url", feedUrl).build()
        val text = try {
            call(url)
        } catch (e: DirectoryFailure) {
            null
        }
        text?.let { PodcastIndexJson.byFeedUrl(it) }
    }

    /** The answer's text; null when there is none (404). */
    private suspend fun call(url: HttpUrl): String? {
        if (!pace.take()) throw DirectoryFailure.Busy(pace.waitMs())
        val request = Request.Builder().url(url).header("Accept", "application/json").apply {
            PodcastIndexAuth.headers(key, secret, userAgent, clock()).forEach { (name, value) -> header(name, value) }
        }.build()
        return try {
            client.newCall(request).executeCancellable { answer(it) }
        } catch (e: DirectoryFailure) {
            throw e
        } catch (e: IOException) {
            // the connection, or the body being read, broke: the same as being offline
            throw DirectoryFailure.Offline(e)
        }
    }

    private fun answer(response: Response): String? = when {
        response.isSuccessful -> {
            pace.answered()
            response.body?.let { response.peekBody(MAX_ANSWER).string() } ?: throw DirectoryFailure.Server(response.code)
        }
        response.code == HTTP_NOT_FOUND -> {
            pace.answered()
            null
        }
        response.code == HTTP_TOO_MANY || response.code == HTTP_UNAVAILABLE -> {
            pace.refused(response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1_000))
            throw DirectoryFailure.Busy(pace.waitMs())
        }
        else -> throw DirectoryFailure.Server(response.code)
    }

    companion object {
        const val BASE_URL = "https://api.podcastindex.org/api/1.0/"
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_UNAVAILABLE = 503

        /** The most of an answer read: a list of thirty shows is far smaller. */
        private const val MAX_ANSWER = 4L * 1024 * 1024

        private val defaultClient by lazy {
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        }
    }
}

/**
 * S-1101: iTunes Search (itunes.apple.com/search), the second directory: no key, about twenty
 * requests a minute from one address, so [pace] allows eighteen. Shows with no `feedUrl` (Apple's
 * own, paid ones) cannot be subscribed to and are left out. [country] (a two-letter region) picks
 * the store it asks.
 */
class ITunesDirectory(
    private val userAgent: String,
    private val country: String? = null,
    private val pace: RequestPace = RequestPace(spacingMs = 1_000, perWindow = 18),
    private val client: OkHttpClient = defaultClient,
    private val base: String = BASE_URL,
) : PodcastDirectory {

    override val available: Boolean get() = true

    override suspend fun search(term: String, limit: Int): List<PodcastHit> {
        if (!pace.take()) throw DirectoryFailure.Busy(pace.waitMs())
        val request = Request.Builder().url(searchUrl(base, term, limit, country)).header("User-Agent", userAgent).header("Accept", "application/json").build()
        return try {
            client.newCall(request).executeCancellable { answer(it) }
        } catch (e: DirectoryFailure) {
            throw e
        } catch (e: IOException) {
            // the connection, or the body being read, broke: the same as being offline
            throw DirectoryFailure.Offline(e)
        }
    }

    private fun answer(response: Response): List<PodcastHit> = when {
        response.isSuccessful -> {
            pace.answered()
            ITunesJson.hits(response.body?.let { response.peekBody(MAX_ANSWER).string() } ?: throw DirectoryFailure.Server(response.code))
        }
        // iTunes answers 403 or 429 to a client over its limit
        response.code == HTTP_FORBIDDEN || response.code == HTTP_TOO_MANY || response.code == HTTP_UNAVAILABLE -> {
            pace.refused(response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1_000))
            throw DirectoryFailure.Busy(pace.waitMs())
        }
        else -> throw DirectoryFailure.Server(response.code)
    }

    companion object {
        const val BASE_URL = "https://itunes.apple.com/search"
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_UNAVAILABLE = 503
        private const val MAX_ANSWER = 4L * 1024 * 1024

        /** `?media=podcast&entity=podcast&term=…&limit=…[&country=…]`. */
        fun searchUrl(base: String, term: String, limit: Int, country: String?): HttpUrl = base.toHttpUrl().newBuilder()
            .addQueryParameter("media", "podcast")
            .addQueryParameter("entity", "podcast")
            .addQueryParameter("term", term.trim())
            .addQueryParameter("limit", limit.toString())
            .apply { country?.takeIf { it.length == 2 }?.let { addQueryParameter("country", it.uppercase(Locale.ROOT)) } }
            .build()

        private val defaultClient by lazy {
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        }
    }
}
