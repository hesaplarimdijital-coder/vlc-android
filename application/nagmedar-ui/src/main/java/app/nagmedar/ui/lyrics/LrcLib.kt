package app.nagmedar.ui.lyrics

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/** S-1003: what LRCLIB is asked for: the track's tags and its length in whole seconds. */
data class LyricsQuery(val artist: String, val title: String, val album: String?, val durationSec: Int?)

/** An answer: timed lyrics (LRC), plain ones, or the word that the track is instrumental. */
data class OnlineLyrics(val synced: String?, val plain: String?, val instrumental: Boolean = false)

/** Why LRCLIB could not answer: no answer is not a failure (null). */
sealed class LyricsFailure(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** No network, or the server could not be reached. */
    class Offline(cause: Throwable? = null) : LyricsFailure("offline", cause)

    /** The server asked to wait (429, 503), or its wait still lasts: [waitMs] more. */
    class Busy(val waitMs: Long = 0) : LyricsFailure("busy")

    /** Another answer: its HTTP status. */
    class Server(val status: Int) : LyricsFailure("status $status")
}

/** S-1003: lyrics on the network: LRCLIB in the app, a fake in the tests. */
interface LyricsOnline {
    /** The track's lyrics; null when there are none to find. Throws [LyricsFailure]. */
    suspend fun lookup(query: LyricsQuery): OnlineLyrics?
}

/**
 * S-1003: LRCLIB (lrclib.net, free, no account): the track's exact signature first (`/api/get`,
 * which also looks further afield), then a search by title and artist for one of about the same
 * length. Every request says who asks ([userAgent], as LRCLIB asks) and waits its turn ([pace]).
 */
class OkHttpLrcLib(
    private val userAgent: String,
    private val pace: RequestPace = RequestPace(),
    private val client: OkHttpClient = defaultClient,
    private val base: String = BASE_URL,
) : LyricsOnline {

    override suspend fun lookup(query: LyricsQuery): OnlineLyrics? = withContext(Dispatchers.IO) {
        call(LrcLib.exactUrl(base, query))?.let { return@withContext LrcLib.one(it) }
        call(LrcLib.searchUrl(base, query))?.let { LrcLib.best(it, query.durationSec) }
    }

    /** The answer's text; null when there is none (404). */
    private suspend fun call(url: HttpUrl): String? {
        if (!pace.take()) throw LyricsFailure.Busy(pace.waitMs())
        val request = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "application/json").build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw LyricsFailure.Offline(e)
        }
        return response.use {
            when {
                it.isSuccessful -> {
                    pace.answered()
                    it.body?.string() ?: throw LyricsFailure.Server(it.code)
                }
                it.code == HTTP_NOT_FOUND -> {
                    pace.answered()
                    null
                }
                it.code == HTTP_TOO_MANY || it.code == HTTP_UNAVAILABLE -> {
                    pace.refused(LrcLib.retryAfterMs(it.header("Retry-After")))
                    throw LyricsFailure.Busy(pace.waitMs())
                }
                else -> {
                    if (it.code >= HTTP_SERVER_ERROR) pace.refused(null)
                    throw LyricsFailure.Server(it.code)
                }
            }
        }
    }

    companion object {
        const val BASE_URL = "https://lrclib.net/api/"
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_SERVER_ERROR = 500
        private const val HTTP_UNAVAILABLE = 503

        /** "Nagmedar 1.2.0 (Android)": the app and its version, as LRCLIB asks of its callers. */
        fun userAgent(version: String?): String = "Nagmedar ${version?.takeIf { it.isNotBlank() } ?: "1.0"} (Android)"

        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}

/** LRCLIB's addresses and answers. */
object LrcLib {
    /** How far a search's result may be from the track's length, in seconds (LRCLIB's own match allows 2). */
    private const val LENGTH_SLACK = 2.0

    fun exactUrl(base: String, query: LyricsQuery): HttpUrl = base.toHttpUrl().newBuilder()
        .addPathSegment("get")
        .addQueryParameter("artist_name", query.artist)
        .addQueryParameter("track_name", query.title)
        .apply {
            query.album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album_name", it) }
            query.durationSec?.takeIf { it > 0 }?.let { addQueryParameter("duration", it.toString()) }
        }
        .build()

    fun searchUrl(base: String, query: LyricsQuery): HttpUrl = base.toHttpUrl().newBuilder()
        .addPathSegment("search")
        .addQueryParameter("track_name", query.title)
        .addQueryParameter("artist_name", query.artist)
        .build()

    /** `/api/get`'s record; a record with nothing in it is no answer. */
    fun one(text: String): OnlineLyrics? = record(parse { JSONObject(text) })

    /** The best of `/api/search`'s records: about the track's length, timed before plain before instrumental, the closest first. */
    fun best(text: String, durationSec: Int?): OnlineLyrics? {
        val array = parse { JSONArray(text) }
        val records = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { o -> record(o)?.let { it to o.optDouble("duration", Double.NaN) } } }
        val near = if (durationSec == null || durationSec <= 0) {
            records
        } else {
            records.filter { (_, length) -> !length.isNaN() && abs(length - durationSec) <= LENGTH_SLACK }
        }
        return near.sortedWith(
            compareBy<Pair<OnlineLyrics, Double>> { (lyrics, _) ->
                when {
                    lyrics.synced != null -> 0
                    lyrics.plain != null -> 1
                    else -> 2
                }
            }.thenBy { (_, length) -> if (durationSec == null || length.isNaN()) 0.0 else abs(length - durationSec) },
        ).firstOrNull()?.first
    }

    /** Retry-After in seconds, as milliseconds; null for none (or a date, not read). */
    fun retryAfterMs(header: String?): Long? = header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.times(1_000)

    private fun record(o: JSONObject): OnlineLyrics? {
        val synced = o.text("syncedLyrics")
        val plain = o.text("plainLyrics")
        val instrumental = o.optBoolean("instrumental", false)
        return if (synced == null && plain == null && !instrumental) null else OnlineLyrics(synced, plain, instrumental && synced == null && plain == null)
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    /** A garbled answer is the server's failure, not "no lyrics". */
    private inline fun <T> parse(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw LyricsFailure.Server(200)
    }
}

/**
 * S-1003: how fast LRCLIB is asked: a request [spacingMs] after the one before, at most
 * [perWindow] in [windowMs], and none at all while the server's wait lasts: as long as it asked
 * (Retry-After), else a minute, doubling with each refusal up to an hour. Requests wait their turn
 * in order; one cancelled while it waits is not made.
 */
class RequestPace(
    private val spacingMs: Long = 1_000,
    private val perWindow: Int = 20,
    private val windowMs: Long = 60_000,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val turn = Mutex()
    private val recent = java.util.ArrayDeque<Long>()

    @Volatile
    private var blockedUntil = 0L
    private var refusals = 0

    /** Waits for a request's turn; false while the server's wait lasts (no request then). */
    suspend fun take(): Boolean = turn.withLock {
        var now = clock()
        if (now < blockedUntil) return@withLock false
        while (true) {
            while (recent.isNotEmpty() && now - recent.peekFirst()!! >= windowMs) recent.pollFirst()
            val afterLast = recent.peekLast()?.let { it + spacingMs - now } ?: 0L
            val windowFull = if (recent.size >= perWindow) recent.peekFirst()!! + windowMs - now else 0L
            val wait = max(afterLast, windowFull)
            if (wait <= 0) break
            sleep(wait)
            now = clock()
        }
        recent.addLast(now)
        true
    }

    /** The server answered: its earlier refusals are forgiven. */
    @Synchronized
    fun answered() {
        refusals = 0
    }

    /** The server asked to wait, [retryAfterMs] if it said how long. */
    @Synchronized
    fun refused(retryAfterMs: Long?) {
        refusals++
        val wait = retryAfterMs?.coerceIn(MIN_WAIT_MS, MAX_WAIT_MS) ?: (FIRST_WAIT_MS shl (refusals - 1).coerceAtMost(6)).coerceAtMost(MAX_WAIT_MS)
        blockedUntil = clock() + wait
    }

    /** How long the server's wait still lasts; 0 when it does not. */
    fun waitMs(): Long = max(0L, blockedUntil - clock())

    private companion object {
        const val MIN_WAIT_MS = 1_000L
        const val FIRST_WAIT_MS = 60_000L
        const val MAX_WAIT_MS = 3_600_000L
    }
}
