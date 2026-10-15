package app.nagmedar.ui.podcast

import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** S-1101: a feed fetched and read. */
sealed interface FeedFetch {
    /** Where the body came from, after the redirects. */
    val finalUrl: String

    /** Where the feed lives now when it moved for good (301, 308) on the way; null when it did not. */
    val permanentUrl: String?

    /** The feed, with the validators the server gave for a conditional refresh. */
    data class Fresh(
        val feed: ParsedFeed,
        override val finalUrl: String,
        override val permanentUrl: String?,
        val etag: String?,
        val lastModified: String?,
    ) : FeedFetch

    /** The server has nothing newer than the validators sent (304). */
    data class NotModified(override val finalUrl: String, override val permanentUrl: String?) : FeedFetch
}

/** S-1101: feeds on the network: OkHttp in the app, a fake in the tests. */
interface FeedSource {
    /**
     * The feed at [url]. [etag] and [lastModified], when given, make the request conditional.
     * Throws [FeedFailure].
     */
    suspend fun fetch(url: String, etag: String? = null, lastModified: String? = null): FeedFetch
}

/**
 * S-1101: [FeedSource] over OkHttp. Redirects are followed here, not by OkHttp, so that a permanent
 * one (301, 308 all the way) is told to the caller, who stores the new address. Compression is
 * OkHttp's (gzip is asked for and undone); a body that is gzip without saying so is undone by the
 * parser. The body is streamed into the parser and read no further than [FeedParser.MAX_BYTES].
 */
class OkHttpFeedSource(
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient,
) : FeedSource {

    /** One hop's outcome: the feed (or its 304), or the address to go on to. */
    private sealed interface Hop {
        class Done(val fetch: FeedFetch) : Hop

        class Next(val target: HttpUrl, val permanent: Boolean) : Hop
    }

    override suspend fun fetch(url: String, etag: String?, lastModified: String?): FeedFetch {
        var current: HttpUrl = url.toHttpUrlOrNull() ?: throw FeedFailure.BadAddress()
        val secure = current.isHttps
        var permanent: String? = null
        var allPermanent = true
        for (hop in 0..MAX_REDIRECTS) {
            coroutineContext.ensureActive()
            val request = Request.Builder().url(current)
                .header("User-Agent", userAgent)
                .header("Accept", ACCEPT)
                .apply {
                    if (hop == 0) {
                        etag?.let { header("If-None-Match", it) }
                        lastModified?.let { header("If-Modified-Since", it) }
                    }
                }
                .build()
            val here = current
            val outcome = try {
                client.newCall(request).executeCancellable { r -> read(r, here, permanent) }
            } catch (e: FeedFailure) {
                throw e
            } catch (e: IOException) {
                throw FeedFailure.Offline(e)
            }
            when (outcome) {
                is Hop.Done -> return outcome.fetch
                is Hop.Next -> {
                    // a move to plain http is followed but never kept: the subscription stays at the address that was https
                    val downgrade = secure && !outcome.target.isHttps
                    if (outcome.permanent && !downgrade) {
                        if (allPermanent) permanent = outcome.target.toString()
                    } else {
                        allPermanent = false
                    }
                    current = outcome.target
                }
            }
        }
        throw FeedFailure.Server(TOO_MANY_REDIRECTS)
    }

    private fun read(r: Response, current: HttpUrl, permanent: String?): Hop = when (r.code) {
        HTTP_OK -> {
            val body = r.body ?: throw FeedFailure.Invalid(InvalidReason.Empty)
            val feed = FeedParser.parse(body.byteStream(), current.toString())
            Hop.Done(FeedFetch.Fresh(feed, current.toString(), permanent, r.header("ETag"), r.header("Last-Modified")))
        }
        HTTP_NOT_MODIFIED -> Hop.Done(FeedFetch.NotModified(current.toString(), permanent))
        HTTP_MOVED, HTTP_FOUND, HTTP_SEE_OTHER, HTTP_TEMPORARY, HTTP_PERMANENT -> {
            // resolve() knows http and https only: a Location of any other kind (ftp:, file:) is refused here
            val target = r.header("Location")?.let { current.resolve(it) } ?: throw FeedFailure.Server(r.code)
            Hop.Next(target, permanent = r.code == HTTP_MOVED || r.code == HTTP_PERMANENT)
        }
        else -> throw FeedFailure.Server(r.code)
    }

    companion object {
        /** The status reported for a feed that redirects more than [MAX_REDIRECTS] times. */
        const val TOO_MANY_REDIRECTS = 310
        private const val MAX_REDIRECTS = 8
        private const val HTTP_OK = 200
        private const val HTTP_NOT_MODIFIED = 304
        private const val HTTP_MOVED = 301
        private const val HTTP_FOUND = 302
        private const val HTTP_SEE_OTHER = 303
        private const val HTTP_TEMPORARY = 307
        private const val HTTP_PERMANENT = 308
        private const val ACCEPT = "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8, */*;q=0.5"

        /** "Nagmedar/1.2.0 (+https://github.com/hesaplarimdijital-coder/vlc-android)": the app and where it is from, no contact. */
        fun userAgent(version: String?): String =
            "Nagmedar/${version?.takeIf { it.isNotBlank() } ?: "1.0"} (+https://github.com/hesaplarimdijital-coder/vlc-android)"

        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(2, TimeUnit.MINUTES)
                .build()
        }
    }
}
