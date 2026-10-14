package app.nagmedar.ui.scrobble

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** S-1008: ListenBrainz: the token checked, listens as `single` and `import`, `playing_now`, the errors told apart. */
@RunWith(AndroidJUnit4::class)
class ListenBrainzTest {

    private val http = FakeHttp()
    private val service = ListenBrainzService("0.2.0", Scrobbling.userAgent("0.2.0"), http.client)

    @Test
    fun aTokenIsCheckedAndKept() {
        http.answer = { Result.success(200 to """{"code":200,"message":"Token valid.","valid":true,"user_name":"ada_karan"}""") }
        val account = runBlocking { service.signIn(SignInInput(secret = "  3f0e1c6a-0000-4d5a-9a58-6b1b7a3c2f10 ")) }
        assertEquals(ScrobbleAccount("ada_karan", "3f0e1c6a-0000-4d5a-9a58-6b1b7a3c2f10"), account)
        val request = http.requests.single()
        assertEquals("GET", request.method)
        assertEquals("https://api.listenbrainz.org/1/validate-token", request.url.toString())
        assertEquals("Token 3f0e1c6a-0000-4d5a-9a58-6b1b7a3c2f10", request.header("Authorization"))
        assertEquals("Nagmedar/0.2.0 (+https://github.com/hesaplarimdijital-coder/vlc-android)", request.header("User-Agent"))
    }

    @Test
    fun aTokenThatIsNotRightIsRefused() {
        fun failure(answer: Result<Pair<Int, String>>): SignInFailure {
            http.answer = { answer }
            try {
                runBlocking { service.signIn(SignInInput(secret = "x")) }
            } catch (e: SignInFailure) {
                return e
            }
            fail("signed in")
            throw IllegalStateException()
        }
        assertTrue(failure(Result.success(200 to """{"code":200,"message":"Token invalid.","valid":false}""")) is SignInFailure.Refused)
        assertTrue(failure(Result.success(401 to """{"code":401,"error":"Invalid authorization token."}""")) is SignInFailure.Refused)
        assertTrue(failure(Result.failure(IOException("unreachable"))) is SignInFailure.Offline)
        assertEquals(503, (failure(Result.success(503 to "")) as SignInFailure.Server).code)
        // an empty token is not even asked
        http.requests.clear()
        try {
            runBlocking { service.signIn(SignInInput(secret = "   ")) }
            fail("signed in")
        } catch (e: SignInFailure.Refused) {
            assertTrue(http.requests.isEmpty())
        }
    }

    @Test
    fun oneListenIsSingleAndMoreAreAnImport() {
        runBlocking { service.submit("tok", listOf(entry(at = 1_791_837_600))) }
        val single = JSONObject(http.bodies[0])
        assertEquals("single", single.getString("listen_type"))
        val listen = single.getJSONArray("payload").getJSONObject(0)
        assertEquals(1_791_837_600L, listen.getLong("listened_at"))
        val metadata = listen.getJSONObject("track_metadata")
        assertEquals("Ada Karan", metadata.getString("artist_name"))
        assertEquals("Sabaha Kadar", metadata.getString("track_name"))
        assertEquals("Gece Mavisi", metadata.getString("release_name"))
        val info = metadata.getJSONObject("additional_info")
        assertEquals("Nagmedar", info.getString("submission_client"))
        assertEquals("0.2.0", info.getString("submission_client_version"))
        assertEquals("Nagmedar", info.getString("media_player"))
        assertEquals(241, info.getInt("duration"))
        assertEquals("https://api.listenbrainz.org/1/submit-listens", http.requests[0].url.toString())
        assertEquals("Token tok", http.requests[0].header("Authorization"))

        runBlocking { service.submit("tok", listOf(entry(track = "A", at = 1), entry(track = "B", at = 2).copy(album = null, durationSec = 0))) }
        val batch = JSONObject(http.bodies[1])
        assertEquals("import", batch.getString("listen_type"))
        assertEquals(2, batch.getJSONArray("payload").length())
        val second = batch.getJSONArray("payload").getJSONObject(1).getJSONObject("track_metadata")
        assertFalse(second.has("release_name"))
        assertFalse(second.getJSONObject("additional_info").has("duration"))
    }

    @Test
    fun theStartIsAPlayingNowWithoutATime() {
        runBlocking { service.nowPlaying("tok", ScrobbleTrack("k", "Ada Karan", "Sabaha Kadar", "Gece Mavisi", null, 241)) }
        val body = JSONObject(http.bodies.single())
        assertEquals("playing_now", body.getString("listen_type"))
        assertFalse(body.getJSONArray("payload").getJSONObject(0).has("listened_at"))
        http.answer = { Result.failure(IOException("offline")) }
        runBlocking { service.nowPlaying("tok", ScrobbleTrack("k", "Ada Karan", "Uzak", null, null, 0)) }
    }

    @Test
    fun theAnswersToListensMeanWhatTheyShould() {
        fun outcome(answer: Result<Pair<Int, String>>): SubmitOutcome {
            http.answer = { answer }
            return runBlocking { service.submit("tok", listOf(entry())) }
        }
        assertEquals(SubmitOutcome.Accepted, outcome(Result.success(200 to """{"status":"ok"}""")))
        assertEquals(SubmitOutcome.AuthFailed("status 401"), outcome(Result.success(401 to """{"code":401,"error":"Invalid authorization token."}""")))
        assertTrue(outcome(Result.success(429 to "")) is SubmitOutcome.Retry)
        assertTrue(outcome(Result.success(503 to "")) is SubmitOutcome.Retry)
        assertTrue(outcome(Result.failure(IOException("no route"))) is SubmitOutcome.Retry)
        assertEquals(SubmitOutcome.Rejected("status 400"), outcome(Result.success(400 to """{"code":400,"error":"Invalid JSON"}""")))
    }

    @Test
    fun aBatchOfMoreThanFiftyIsNotSent() {
        try {
            runBlocking { service.submit("tok", List(51) { entry(at = it.toLong()) }) }
            fail("sent")
        } catch (e: IllegalArgumentException) {
            assertTrue(http.requests.isEmpty())
        }
    }
}
