package app.nagmedar.ui.lyrics.editor

import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.LyricsTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** What LRCLIB asks to be solved before it takes lyrics: the [prefix] to put the nonce after, and the [target] a hash must not pass. */
data class PublishChallenge(val prefix: String, val target: ByteArray) {
    override fun equals(other: Any?): Boolean = other is PublishChallenge && prefix == other.prefix && target.contentEquals(other.target)

    override fun hashCode(): Int = 31 * prefix.hashCode() + target.contentHashCode()

    /** The tries it takes to find a nonce, on average: 2^256 over the target. */
    val expectedTries: Double
        get() {
            var value = 0.0
            for (b in target) value = value * 256 + (b.toInt() and 0xFF)
            return if (value <= 0) Double.MAX_VALUE else Math.pow(2.0, 256.0) / value
        }

    companion object {
        /** The challenge for a [target] given as 64 hex digits; null for anything else. */
        fun of(prefix: String, target: String): PublishChallenge? {
            if (prefix.isEmpty() || target.length != HASH_BYTES * 2) return null
            val bytes = ByteArray(HASH_BYTES)
            for (i in 0 until HASH_BYTES) {
                val high = Character.digit(target[i * 2], 16)
                val low = Character.digit(target[i * 2 + 1], 16)
                if (high < 0 || low < 0) return null
                bytes[i] = (high * 16 + low).toByte()
            }
            return PublishChallenge(prefix, bytes)
        }

        const val HASH_BYTES = 32
    }
}

/**
 * S-1005: LRCLIB's proof of work. A nonce is found when SHA-256 of the challenge's prefix and the
 * nonce written in decimal, taken as a 256-bit number, is not above the target (LRCLIB compares
 * byte by byte, as does [accepts]). The search tries nonces in turn on [workers] threads of the
 * background pool (worker k tries k, k + workers, ...), reports how many tries were made, and stops
 * as soon as the coroutine is cancelled.
 */
object ProofOfWork {

    /** Whether [nonce] solves [challenge]. */
    fun accepts(challenge: PublishChallenge, nonce: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(challenge.prefix.toByteArray(Charsets.UTF_8))
        digest.update(nonce.toByteArray(Charsets.UTF_8))
        return notAbove(digest.digest(), challenge.target)
    }

    /**
     * The nonce that solves [challenge], found on [workers] threads; [onTries] hears the tries so far
     * every few thousand. Throws [CancellationException] when cancelled.
     */
    suspend fun solve(
        challenge: PublishChallenge,
        workers: Int = defaultWorkers(),
        onTries: (Long) -> Unit = {},
    ): String = withContext(Dispatchers.Default) {
        val count = workers.coerceAtLeast(1)
        val tries = AtomicLong()
        coroutineScope {
            val found = java.util.concurrent.atomic.AtomicReference<String?>(null)
            val jobs = List(count) { index ->
                async(Dispatchers.Default) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val prefix = challenge.prefix.toByteArray(Charsets.UTF_8)
                    val out = ByteArray(challenge.target.size)
                    var nonce = index.toLong()
                    var sinceReport = 0
                    while (found.get() == null) {
                        if (sinceReport >= REPORT_EVERY) {
                            if (!isActive) throw CancellationException("cancelled")
                            onTries(tries.addAndGet(sinceReport.toLong()))
                            sinceReport = 0
                        }
                        val text = nonce.toString()
                        digest.update(prefix)
                        digest.update(text.toByteArray(Charsets.UTF_8))
                        digest.digest(out, 0, out.size)
                        sinceReport++
                        if (notAbove(out, challenge.target)) {
                            found.compareAndSet(null, text)
                            break
                        }
                        nonce += count
                    }
                    tries.addAndGet(sinceReport.toLong())
                }
            }
            jobs.forEach { it.await() }
            onTries(tries.get())
            found.get() ?: throw CancellationException("cancelled")
        }
    }

    /** Hash bytes against target bytes, the first that differs deciding: true when the hash is not above. */
    internal fun notAbove(hash: ByteArray, target: ByteArray): Boolean {
        if (hash.size != target.size) return false
        for (i in hash.indices) {
            val h = hash[i].toInt() and 0xFF
            val t = target[i].toInt() and 0xFF
            if (h > t) return false
            if (h < t) return true
        }
        return true
    }

    /** A core is left to the app: all but one of them, at most four. */
    fun defaultWorkers(): Int = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)

    private const val REPORT_EVERY = 4_096
}

/** What LRCLIB is sent: the track as it knows tracks, and the lyrics, plain and timed. */
data class PublishRequest(
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Double,
    val plainLyrics: String,
    val syncedLyrics: String,
) {
    fun json(): String = JSONObject()
        .put("trackName", trackName)
        .put("artistName", artistName)
        .put("albumName", albumName)
        .put("duration", durationSec)
        .put("plainLyrics", plainLyrics)
        .put("syncedLyrics", syncedLyrics)
        .toString()

    companion object {
        /** The request for [track] and its timed [lines]; null when LRCLIB could not take them: no title, artist or length, or nothing timed. */
        fun of(track: LyricsTrack, lines: List<LyricLine>): PublishRequest? {
            val title = track.title?.trim().orEmpty()
            val artist = track.artist?.trim().orEmpty()
            if (title.isEmpty() || artist.isEmpty() || track.lengthMs <= 0 || lines.none { it.startMs >= 0 && it.text.isNotBlank() }) return null
            return PublishRequest(
                trackName = title,
                artistName = artist,
                albumName = track.album?.trim().orEmpty(),
                durationSec = track.lengthMs / 1_000.0,
                plainLyrics = LrcDocument.plain(lines),
                syncedLyrics = LrcDocument.lineLrc(lines),
            )
        }
    }
}

/** Why lyrics were not published. */
sealed class PublishFailure(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** No network, or the server could not be reached. */
    class Offline(cause: Throwable? = null) : PublishFailure("offline", cause)

    /** The server did not take the answer to its challenge: it is old (it expires) or wrong. */
    class Refused : PublishFailure("token refused")

    /** The server asked to wait (429, 503). */
    class Busy : PublishFailure("busy")

    /** Another answer: its HTTP status. */
    class Server(val status: Int) : PublishFailure("status $status")
}

/** S-1005: LRCLIB's two calls for publishing: the challenge, and the lyrics with its answer. */
interface LyricsPublisher {
    /** A challenge to solve. Throws [PublishFailure]. */
    suspend fun challenge(): PublishChallenge

    /** Sends [request] with the solved [challenge]'s [nonce]. Throws [PublishFailure]. */
    suspend fun publish(request: PublishRequest, challenge: PublishChallenge, nonce: String)
}

/**
 * S-1005: LRCLIB over HTTP: `POST /api/request-challenge`, then `POST /api/publish` with the header
 * `X-Publish-Token: prefix:nonce` and the track and lyrics as JSON; 201 is done. Every request says
 * who sends it ([userAgent]).
 */
class OkHttpLyricsPublisher(
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient,
    private val base: String = BASE_URL,
) : LyricsPublisher {

    override suspend fun challenge(): PublishChallenge = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(base + "request-challenge").header("User-Agent", userAgent)
            .post("".toRequestBody(null)).build()
        val text = call(request)
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw PublishFailure.Server(OK)
        }
        PublishChallenge.of(json.optString("prefix"), json.optString("target")) ?: throw PublishFailure.Server(OK)
    }

    override suspend fun publish(request: PublishRequest, challenge: PublishChallenge, nonce: String) {
        withContext(Dispatchers.IO) {
            val http = Request.Builder().url(base + "publish").header("User-Agent", userAgent)
                .header("X-Publish-Token", challenge.prefix + ":" + nonce)
                .post(request.json().toRequestBody(JSON))
                .build()
            call(http)
        }
    }

    /** The body of a successful answer; the failures as [PublishFailure]. */
    private fun call(request: Request): String {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw PublishFailure.Offline(e)
        }
        return response.use {
            val body = it.body?.string().orEmpty()
            when {
                it.isSuccessful -> body
                it.code == BAD_REQUEST && body.contains("IncorrectPublishTokenError") -> throw PublishFailure.Refused()
                it.code == TOO_MANY || it.code == UNAVAILABLE -> throw PublishFailure.Busy()
                else -> throw PublishFailure.Server(it.code)
            }
        }
    }

    companion object {
        const val BASE_URL = "https://lrclib.net/api/"
        private const val OK = 200
        private const val BAD_REQUEST = 400
        private const val TOO_MANY = 429
        private const val UNAVAILABLE = 503
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}

/** Where a publication is. */
sealed interface PublishState {
    data object Idle : PublishState

    /** The user is asked whether to send [request]. */
    data class Confirming(val request: PublishRequest) : PublishState

    /** Asking for the challenge. */
    data object Challenging : PublishState

    /** Solving it: [tries] so far of about [expected]. */
    data class Solving(val tries: Long, val expected: Double) : PublishState {
        /** How likely a nonce would have been found by now, 0..1: the progress bar's, as the search is luck. */
        val progress: Float get() = (1.0 - Math.exp(-tries / expected.coerceAtLeast(1.0))).toFloat().coerceIn(0f, 0.99f)
    }

    /** Sending. */
    data object Sending : PublishState

    data object Done : PublishState

    data class Failed(val failure: PublishFailure) : PublishState
}

/**
 * S-1005: publishes [request] to [publisher]: the challenge, its proof of work on background threads
 * (progress to [onState], cancel by cancelling the coroutine), the lyrics. Ends in [PublishState.Done]
 * or [PublishState.Failed]; a cancellation is not a failure and leaves no state (the caller's).
 */
suspend fun publishLyrics(
    publisher: LyricsPublisher,
    request: PublishRequest,
    onState: (PublishState) -> Unit,
    workers: Int = ProofOfWork.defaultWorkers(),
) {
    try {
        onState(PublishState.Challenging)
        val challenge = publisher.challenge()
        val expected = challenge.expectedTries
        onState(PublishState.Solving(0, expected))
        val nonce = ProofOfWork.solve(challenge, workers) { tries -> onState(PublishState.Solving(tries, expected)) }
        onState(PublishState.Sending)
        publisher.publish(request, challenge, nonce)
        onState(PublishState.Done)
    } catch (e: CancellationException) {
        throw e
    } catch (e: PublishFailure) {
        onState(PublishState.Failed(e))
    }
}
