package app.nagmedar.ui.scrobble

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** S-1008: Last.fm's calls: signed as the API asks, the sign-in, the scrobbles in batches, the errors told apart. */
@RunWith(AndroidJUnit4::class)
class LastFmTest {

    private val http = FakeHttp()
    private val userAgent = Scrobbling.userAgent("0.2.0")
    private val service = LastFmService("0123456789abcdef", "shhh", userAgent, http.client)

    @Test
    fun aSignatureIsTheMd5OfTheSortedNamesAndValuesAndTheSecret() {
        val params = mapOf(
            "method" to "track.scrobble",
            "api_key" to "0123456789abcdef",
            "sk" to "sessionkey123",
            "artist[0]" to "Ada Karan",
            "track[0]" to "Sabaha Kadar",
            "timestamp[0]" to "1791837600",
            "album[0]" to "Gece Mavisi",
            // left out of the signature
            "format" to "json",
            "callback" to "x",
        )
        assertEquals("71c631e367c1874c3e3c05c98736443a", LastFmService.signature(params, "shhh"))
        // UTF-8, and the order of the names is the order of their characters
        assertEquals(
            "9951878b2f036904e037d05140e67099",
            LastFmService.signature(mapOf("method" to "auth.getMobileSession", "username" to "ada", "password" to "şifre ğ", "api_key" to "0123456789abcdef"), "shhh"),
        )
        assertEquals(
            "b24915b0b1d1194b728798d09b0d6081",
            LastFmService.signature(mapOf("artist[2]" to "D", "artist[10]" to "C", "album[0]" to "B", "albumArtist[0]" to "A"), "shhh"),
        )
    }

    @Test
    fun signingInSendsTheSignedMobileSessionCallAndKeepsTheSessionKey() {
        http.answer = { Result.success(200 to """{"session":{"name":"ada_karan","key":"d580d57f32848f5dcf574d1ce18d78b2","subscriber":0}}""") }
        val account = runBlocking { service.signIn(SignInInput(" ada_karan ", "parola ğ")) }
        assertEquals(ScrobbleAccount("ada_karan", "d580d57f32848f5dcf574d1ce18d78b2"), account)
        val request = http.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://ws.audioscrobbler.com/2.0/", request.url.toString())
        assertEquals("Nagmedar/0.2.0 (+https://github.com/hesaplarimdijital-coder/vlc-android)", request.header("User-Agent"))
        val form = http.form()
        assertEquals("auth.getMobileSession", form["method"])
        assertEquals("ada_karan", form["username"])
        assertEquals("parola ğ", form["password"])
        assertEquals("json", form["format"])
        assertEquals(LastFmService.signature(form - "api_sig", "shhh"), form["api_sig"])
    }

    @Test
    fun signInFailuresAreToldApart() {
        fun failure(answer: Result<Pair<Int, String>>): SignInFailure {
            http.answer = { answer }
            try {
                runBlocking { service.signIn(SignInInput("ada", "x")) }
            } catch (e: SignInFailure) {
                return e
            }
            fail("signed in")
            throw IllegalStateException()
        }
        assertTrue(failure(Result.success(403 to """{"error":4,"message":"Invalid authentication token supplied"}""")) is SignInFailure.Refused)
        assertEquals(26, (failure(Result.success(403 to """{"error":26,"message":"Suspended API key"}""")) as SignInFailure.Server).code)
        assertTrue(failure(Result.failure(IOException("unreachable"))) is SignInFailure.Offline)
        assertEquals(500, (failure(Result.success(500 to "<html>")) as SignInFailure.Server).code)
        // a build without keys does not even ask
        val noKey = LastFmService("", "", userAgent, http.client)
        assertFalse(noKey.available)
        http.requests.clear()
        try {
            runBlocking { noKey.signIn(SignInInput("ada", "x")) }
            fail("signed in")
        } catch (e: SignInFailure.NoKey) {
            assertTrue(http.requests.isEmpty())
        }
    }

    @Test
    fun aBatchIsOneIndexedSignedCall() {
        http.answer = { Result.success(200 to """{"scrobbles":{"@attr":{"accepted":2,"ignored":0},"scrobble":[{},{}]}}""") }
        val outcome = runBlocking {
            service.submit(
                "sessionkey123",
                listOf(
                    entry(track = "Sabaha Kadar", at = 1_791_837_600).copy(albumArtist = "Ada Karan"),
                    entry(track = "Uzak", at = 1_791_837_900).copy(album = null, durationSec = 0),
                ),
            )
        }
        assertEquals(SubmitOutcome.Accepted, outcome)
        val form = http.form()
        assertEquals("track.scrobble", form["method"])
        assertEquals("sessionkey123", form["sk"])
        assertEquals("0123456789abcdef", form["api_key"])
        assertEquals("Ada Karan", form["artist[0]"])
        assertEquals("Sabaha Kadar", form["track[0]"])
        assertEquals("1791837600", form["timestamp[0]"])
        assertEquals("Gece Mavisi", form["album[0]"])
        assertEquals("Ada Karan", form["albumArtist[0]"])
        assertEquals("241", form["duration[0]"])
        assertEquals("Uzak", form["track[1]"])
        assertEquals("1791837900", form["timestamp[1]"])
        // what is not known is left out
        assertFalse(form.containsKey("album[1]"))
        assertFalse(form.containsKey("duration[1]"))
        assertEquals(LastFmService.signature(form - "api_sig", "shhh"), form["api_sig"])
    }

    @Test
    fun aBatchOfMoreThanFiftyIsNotSent() {
        try {
            runBlocking { service.submit("sk", List(51) { entry(at = it.toLong()) }) }
            fail("sent")
        } catch (e: IllegalArgumentException) {
            assertTrue(http.requests.isEmpty())
        }
    }

    @Test
    fun theAnswersToAScrobbleMeanWhatTheyShould() {
        fun outcome(answer: Result<Pair<Int, String>>): SubmitOutcome {
            http.answer = { answer }
            return runBlocking { service.submit("sk", listOf(entry())) }
        }
        // accepted, and ignored for good (Last.fm ignores what it cannot use): both leave the queue
        assertEquals(SubmitOutcome.Accepted, outcome(Result.success(200 to """{"scrobbles":{"@attr":{"accepted":0,"ignored":1},"scrobble":{}}}""")))
        // the account or the key is the problem
        for (code in listOf(4, 9, 10, 13, 14, 15, 26)) {
            assertTrue("error $code", outcome(Result.success(403 to """{"error":$code,"message":"x"}""")) is SubmitOutcome.AuthFailed)
        }
        assertEquals(SubmitOutcome.AuthFailed("status 403"), outcome(Result.success(403 to "forbidden")))
        // not now
        for (code in listOf(8, 11, 16, 29)) {
            assertTrue("error $code", outcome(Result.success(500 to """{"error":$code,"message":"x"}""")) is SubmitOutcome.Retry)
        }
        assertTrue(outcome(Result.success(503 to "")) is SubmitOutcome.Retry)
        assertTrue(outcome(Result.success(429 to "")) is SubmitOutcome.Retry)
        assertTrue(outcome(Result.failure(IOException("no route"))) is SubmitOutcome.Retry)
        // the scrobbles themselves are refused
        assertTrue(outcome(Result.success(400 to """{"error":6,"message":"Invalid parameters"}""")) is SubmitOutcome.Rejected)
        assertTrue(outcome(Result.success(404 to "")) is SubmitOutcome.Rejected)
    }

    @Test
    fun nowPlayingIsSignedAndItsFailureIsNotWorthAWord() {
        runBlocking { service.nowPlaying("sk", ScrobbleTrack("k", "Ada Karan", "Sabaha Kadar", null, null, 241)) }
        val form = http.form()
        assertEquals("track.updateNowPlaying", form["method"])
        assertEquals("Sabaha Kadar", form["track"])
        assertEquals("241", form["duration"])
        assertFalse(form.containsKey("album"))
        assertEquals(LastFmService.signature(form - "api_sig", "shhh"), form["api_sig"])
        http.answer = { Result.failure(IOException("offline")) }
        runBlocking { service.nowPlaying("sk", ScrobbleTrack("k", "Ada Karan", "Uzak", null, null, 0)) }
    }
}
