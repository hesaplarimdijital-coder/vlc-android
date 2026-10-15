package app.nagmedar.ui.scrobble

import app.nagmedar.data.ScrobbleEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * S-1008: ListenBrainz's API (1): the user's token, pasted from their settings page, checked with
 * `/1/validate-token`; `playing_now`, and `single` or `import` (more than one) listens through
 * `/1/submit-listens`. The token is sent as `Authorization: Token …`, and every request says who
 * asks ([userAgent]); every listen says what sent it, Nagmedar and its [version], as ListenBrainz
 * asks of its clients.
 */
class ListenBrainzService(
    private val version: String,
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient,
    private val base: String = BASE_URL,
) : ScrobbleService {

    override val id = ScrobbleServiceId.ListenBrainz
    override val available = true
    override val batchSize = BATCH_SIZE
    override val maxAgeSec: Long? = null

    override suspend fun signIn(input: SignInInput): ScrobbleAccount {
        val token = input.secret.trim()
        if (token.isEmpty()) throw SignInFailure.Refused("no token")
        val request = request("validate-token", token).get().build()
        val (status, json) = try {
            send(request)
        } catch (e: IOException) {
            throw SignInFailure.Offline(e)
        }
        if (status == HTTP_UNAUTHORIZED) throw SignInFailure.Refused("status $status")
        if (status !in 200..299) throw SignInFailure.Server(status)
        val answer = json ?: throw SignInFailure.Server(status)
        if (!answer.optBoolean("valid", false)) throw SignInFailure.Refused("not valid")
        val name = answer.optString("user_name")
        if (name.isEmpty()) throw SignInFailure.Server(status)
        return ScrobbleAccount(name, token)
    }

    override suspend fun nowPlaying(secret: String, track: ScrobbleTrack) {
        val listen = JSONObject().put("track_metadata", metadata(track.artist, track.title, track.album, track.durationSec))
        val body = JSONObject().put("listen_type", "playing_now").put("payload", JSONArray().put(listen))
        try {
            send(request("submit-listens", secret).post(body.toString().toRequestBody(JSON)).build())
        } catch (e: IOException) {
            // not worth a word: the listen itself is what counts
        }
    }

    override suspend fun submit(secret: String, entries: List<ScrobbleEntry>): SubmitOutcome {
        require(entries.size in 1..BATCH_SIZE) { "a batch holds 1 to $BATCH_SIZE listens" }
        val payload = JSONArray()
        entries.forEach { entry ->
            payload.put(
                JSONObject()
                    .put("listened_at", entry.listenedAt)
                    .put("track_metadata", metadata(entry.artist, entry.track, entry.album, entry.durationSec)),
            )
        }
        val body = JSONObject().put("listen_type", if (entries.size == 1) "single" else "import").put("payload", payload)
        val (status, _) = try {
            send(request("submit-listens", secret).post(body.toString().toRequestBody(JSON)).build())
        } catch (e: IOException) {
            return SubmitOutcome.Retry(e.message)
        }
        return when {
            status in 200..299 -> SubmitOutcome.Accepted
            status == HTTP_UNAUTHORIZED -> SubmitOutcome.AuthFailed("status $status")
            status == HTTP_TOO_MANY || status >= HTTP_SERVER_ERROR -> SubmitOutcome.Retry("status $status")
            status in 400..499 -> SubmitOutcome.Rejected("status $status")
            else -> SubmitOutcome.Retry("status $status")
        }
    }

    private fun metadata(artist: String, track: String, album: String?, durationSec: Int): JSONObject {
        val info = JSONObject()
            .put("media_player", CLIENT)
            .put("submission_client", CLIENT)
            .put("submission_client_version", version)
        if (durationSec > 0) info.put("duration", durationSec)
        val metadata = JSONObject().put("artist_name", artist).put("track_name", track)
        album?.let { metadata.put("release_name", it) }
        return metadata.put("additional_info", info)
    }

    private fun request(path: String, token: String): Request.Builder = Request.Builder()
        .url(base.toHttpUrl().newBuilder().addPathSegments("1/$path").build())
        .header("Authorization", "Token $token")
        .header("User-Agent", userAgent)
        .header("Accept", "application/json")

    /** The request's status and JSON answer (null when it is not JSON). Throws [IOException] when there is none. */
    private suspend fun send(request: Request): Pair<Int, JSONObject?> = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                null
            }
            response.code to json
        }
    }

    companion object {
        const val BASE_URL = "https://api.listenbrainz.org/"
        const val BATCH_SIZE = 50

        /** Where the user finds their token. */
        const val SETTINGS_URL = "https://listenbrainz.org/settings/"

        private const val CLIENT = "Nagmedar"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_SERVER_ERROR = 500
        private val JSON = "application/json".toMediaType()

        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
