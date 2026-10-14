package app.nagmedar.ui.scrobble

import app.nagmedar.data.ScrobbleEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * S-1008: Last.fm's web API (2.0) with Nağmedar's own key ([apiKey], [secret], from the build):
 * the mobile sign-in (`auth.getMobileSession`: the password is sent once and not kept, only the
 * session key), `track.updateNowPlaying` and `track.scrobble` in batches of up to 50. Every call is
 * signed ([signature]) and says who asks ([userAgent]).
 */
class LastFmService(
    private val apiKey: String,
    private val secret: String,
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient,
    private val base: String = BASE_URL,
) : ScrobbleService {

    override val id = ScrobbleServiceId.LastFm
    override val available: Boolean get() = apiKey.isNotBlank() && secret.isNotBlank()
    override val batchSize = BATCH_SIZE

    /** Last.fm ignores a scrobble older than two weeks. */
    override val maxAgeSec: Long = MAX_AGE_SEC

    override suspend fun signIn(input: SignInInput): ScrobbleAccount {
        if (!available) throw SignInFailure.NoKey()
        val answer = try {
            call(mapOf("method" to "auth.getMobileSession", "username" to input.name.trim(), "password" to input.secret))
        } catch (e: IOException) {
            throw SignInFailure.Offline(e)
        }
        answer.error?.let { code ->
            throw if (code == ERROR_AUTH_FAILED) SignInFailure.Refused("code $code") else SignInFailure.Server(code)
        }
        if (answer.status !in 200..299) throw SignInFailure.Server(answer.status)
        val session = answer.json?.optJSONObject("session")
        val name = session?.optString("name").orEmpty()
        val key = session?.optString("key").orEmpty()
        if (name.isEmpty() || key.isEmpty()) throw SignInFailure.Server(answer.status)
        return ScrobbleAccount(name, key)
    }

    override suspend fun nowPlaying(secret: String, track: ScrobbleTrack) {
        val params = LinkedHashMap<String, String>()
        params["method"] = "track.updateNowPlaying"
        params["sk"] = secret
        params["artist"] = track.artist
        params["track"] = track.title
        track.album?.let { params["album"] = it }
        track.albumArtist?.let { params["albumArtist"] = it }
        if (track.durationSec > 0) params["duration"] = track.durationSec.toString()
        try {
            call(params)
        } catch (e: IOException) {
            // not worth a word: the scrobble itself is what counts
        }
    }

    override suspend fun submit(secret: String, entries: List<ScrobbleEntry>): SubmitOutcome {
        require(entries.size in 1..BATCH_SIZE) { "a batch holds 1 to $BATCH_SIZE scrobbles" }
        val params = LinkedHashMap<String, String>()
        params["method"] = "track.scrobble"
        params["sk"] = secret
        entries.forEachIndexed { i, entry ->
            params["artist[$i]"] = entry.artist
            params["track[$i]"] = entry.track
            params["timestamp[$i]"] = entry.listenedAt.toString()
            entry.album?.let { params["album[$i]"] = it }
            entry.albumArtist?.let { params["albumArtist[$i]"] = it }
            if (entry.durationSec > 0) params["duration[$i]"] = entry.durationSec.toString()
        }
        val answer = try {
            call(params)
        } catch (e: IOException) {
            return SubmitOutcome.Retry(e.message)
        }
        return outcome(answer)
    }

    /** What an answer means for the scrobbles sent. */
    private fun outcome(answer: Answer): SubmitOutcome {
        answer.error?.let { code ->
            val reason = "code $code"
            return when (code) {
                in AUTH_ERRORS -> SubmitOutcome.AuthFailed(reason)
                ERROR_INVALID_PARAMETERS, ERROR_INVALID_RESOURCE -> SubmitOutcome.Rejected(reason)
                else -> SubmitOutcome.Retry(reason)
            }
        }
        return when {
            answer.status in 200..299 && answer.json?.has("scrobbles") == true -> SubmitOutcome.Accepted
            answer.status == HTTP_FORBIDDEN -> SubmitOutcome.AuthFailed("status ${answer.status}")
            answer.status in 400..499 && answer.status != HTTP_TOO_MANY -> SubmitOutcome.Rejected("status ${answer.status}")
            else -> SubmitOutcome.Retry("status ${answer.status}")
        }
    }

    private class Answer(val status: Int, val json: JSONObject?) {
        /** Last.fm's own error code, when the answer is one. */
        val error: Int? get() = json?.takeIf { it.has("error") }?.optInt("error")
    }

    /** A signed POST of [params]; Last.fm's answer. Throws [IOException] when it cannot be had. */
    private suspend fun call(params: Map<String, String>): Answer = withContext(Dispatchers.IO) {
        val signed = LinkedHashMap(params)
        signed["api_key"] = apiKey
        val form = FormBody.Builder()
        signed.forEach { (name, value) -> form.add(name, value) }
        form.add("api_sig", signature(signed, secret))
        form.add("format", "json")
        val request = Request.Builder().url(base).header("User-Agent", userAgent).header("Accept", "application/json").post(form.build()).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                null
            }
            Answer(response.code, json)
        }
    }

    companion object {
        const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"
        const val BATCH_SIZE = 50
        private const val MAX_AGE_SEC = 14L * 24 * 60 * 60
        private const val ERROR_AUTH_FAILED = 4
        private const val ERROR_INVALID_RESOURCE = 7
        private const val ERROR_INVALID_PARAMETERS = 6
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY = 429

        /** Invalid sign-in (4), session (9), key (10, 26) or signature (13), an expired token (14, 15): the key or the account is the problem. */
        private val AUTH_ERRORS = setOf(4, 9, 10, 13, 14, 15, 26)

        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        /**
         * A call's signature: the parameters' names and values (`format` and `callback` aside) in the
         * order of the names, joined without separators, with the shared [secret] after them, as an MD5
         * in 32 lower-case hexadecimal digits.
         */
        fun signature(params: Map<String, String>, secret: String): String {
            val text = params.filterKeys { it != "format" && it != "callback" }.toSortedMap().entries.joinToString("") { it.key + it.value } + secret
            return MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}
