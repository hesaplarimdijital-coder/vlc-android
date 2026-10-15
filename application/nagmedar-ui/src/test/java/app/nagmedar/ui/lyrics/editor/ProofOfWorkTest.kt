package app.nagmedar.ui.lyrics.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.LyricsTrack
import app.nagmedar.ui.scrobble.FakeHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** S-1005: LRCLIB's proof of work (SHA-256 of prefix and nonce under a target), the requests, and the publishing flow. */
@RunWith(AndroidJUnit4::class)
class ProofOfWorkTest {

    /** A target a nonce meets once in 256 tries. */
    private val easy = PublishChallenge.of("abcDEF123", "00" + "FF".repeat(31))!!

    @Test
    fun aChallengeIsReadFromItsHex() {
        val real = PublishChallenge.of("p", "000000FF00000000000000000000000000000000000000000000000000000000")!!
        assertEquals(32, real.target.size)
        assertEquals(0xFF, real.target[3].toInt() and 0xFF)
        // 2^32 / 255 tries on average
        assertEquals(4294967296.0 / 255, real.expectedTries, 1.0)
        assertNull(PublishChallenge.of("p", "00FF"))
        assertNull(PublishChallenge.of("", "00".repeat(32)))
        assertNull(PublishChallenge.of("p", "zz".repeat(32)))
    }

    @Test
    fun hashesAreComparedByteByByte() {
        val target = byteArrayOf(0, 0x10, -1)
        assertTrue(ProofOfWork.notAbove(byteArrayOf(0, 0x0F, -1), target))
        assertTrue(ProofOfWork.notAbove(byteArrayOf(0, 0x10, -1), target))
        assertFalse(ProofOfWork.notAbove(byteArrayOf(0, 0x11, 0), target))
        assertFalse(ProofOfWork.notAbove(byteArrayOf(1, 0, 0), target))
        assertFalse(ProofOfWork.notAbove(byteArrayOf(0, 0), target))
    }

    @Test
    fun theNonceFoundMeetsTheTargetAsTheServerChecksIt() = runBlocking {
        val nonce = ProofOfWork.solve(easy, workers = 1)
        assertTrue(ProofOfWork.accepts(easy, nonce))
        // by the book: SHA-256 of "<prefix><nonce>"
        val hash = MessageDigest.getInstance("SHA-256").digest(("abcDEF123$nonce").toByteArray())
        assertEquals(0, hash[0].toInt())
        // one worker tries 0, 1, 2 ...: the first nonce that does
        val first = generateSequence(0L) { it + 1 }.first { ProofOfWork.accepts(easy, it.toString()) }
        assertEquals(first.toString(), nonce)
    }

    @Test
    fun severalWorkersFindOneToo() = runBlocking {
        val tries = ArrayList<Long>()
        val nonce = ProofOfWork.solve(easy, workers = 3) { synchronized(tries) { tries += it } }
        assertTrue(ProofOfWork.accepts(easy, nonce))
        assertTrue(tries.isNotEmpty())
    }

    @Test
    fun theSearchStopsWhenCancelled() = runBlocking {
        val impossible = PublishChallenge("x", ByteArray(32))
        val reported = java.util.concurrent.atomic.AtomicLong()
        val job = async(Dispatchers.Default) { ProofOfWork.solve(impossible, workers = 2) { reported.set(it) } }
        withTimeout(10_000) { while (reported.get() == 0L) delay(10) }
        job.cancel()
        withTimeout(10_000) { job.join() }
        assertTrue(job.isCancelled)
    }

    private val track = LyricsTrack("file:///m/a.flac", " Parça ", "Sanatçı", null, 215_400)
    private val timed = listOf(LyricLine(1_000, "bir"), LyricLine(2_000, "iki"))

    @Test
    fun theRequestHasTheTrackAndBothLyrics() {
        val request = PublishRequest.of(track, timed)!!
        val json = JSONObject(request.json())
        assertEquals("Parça", json.getString("trackName"))
        assertEquals("Sanatçı", json.getString("artistName"))
        assertEquals("", json.getString("albumName"))
        assertEquals(215.4, json.getDouble("duration"), 1e-9)
        assertEquals("bir\niki", json.getString("plainLyrics"))
        assertEquals("[00:01.00]bir\n[00:02.00]iki", json.getString("syncedLyrics"))
    }

    @Test
    fun lrclibNeedsATitleAnArtistALengthAndTimedLines() {
        assertNull(PublishRequest.of(track.copy(title = null), timed))
        assertNull(PublishRequest.of(track.copy(artist = " "), timed))
        assertNull(PublishRequest.of(track.copy(lengthMs = 0), timed))
        assertNull(PublishRequest.of(track, listOf(LyricLine(-1, "bir"))))
        assertNotNull(PublishRequest.of(track, timed))
    }

    @Test
    fun theTwoCallsSayWhoAsksAndCarryTheToken() = runBlocking {
        val http = FakeHttp { request ->
            if (request.url.encodedPath.endsWith("request-challenge")) {
                Result.success(200 to """{"prefix":"abcDEF123","target":"${"00" + "FF".repeat(31)}"}""")
            } else {
                Result.success(201 to "")
            }
        }
        val publisher = OkHttpLyricsPublisher("Nagmedar/1.0 (+https://example.org)", http.client, "https://lrclib.test/api/")
        val challenge = publisher.challenge()
        assertEquals(easy, challenge)
        publisher.publish(PublishRequest.of(track, timed)!!, challenge, "42")
        assertEquals("https://lrclib.test/api/request-challenge", http.requests[0].url.toString())
        assertEquals("POST", http.requests[0].method)
        assertEquals("https://lrclib.test/api/publish", http.requests[1].url.toString())
        assertEquals("abcDEF123:42", http.requests[1].header("X-Publish-Token"))
        assertEquals("Nagmedar/1.0 (+https://example.org)", http.requests[1].header("User-Agent"))
        assertEquals("Parça", JSONObject(http.bodies[1]).getString("trackName"))
    }

    @Test
    fun theFailuresAreTold() = runBlocking {
        fun publisher(answer: Result<Pair<Int, String>>) = OkHttpLyricsPublisher("ua", FakeHttp { answer }.client, "https://lrclib.test/api/")
        val request = PublishRequest.of(track, timed)!!
        suspend fun failure(answer: Result<Pair<Int, String>>): PublishFailure =
            try {
                publisher(answer).publish(request, easy, "1")
                fail("it should fail")
                throw IllegalStateException()
            } catch (e: PublishFailure) {
                e
            }
        assertTrue(failure(Result.success(400 to """{"name":"IncorrectPublishTokenError"}""")) is PublishFailure.Refused)
        assertTrue(failure(Result.success(429 to "")) is PublishFailure.Busy)
        assertTrue(failure(Result.success(503 to "")) is PublishFailure.Busy)
        assertEquals(500, (failure(Result.success(500 to "")) as PublishFailure.Server).status)
        assertTrue(failure(Result.failure(java.io.IOException("no route"))) is PublishFailure.Offline)
        // a challenge that is not one
        try {
            publisher(Result.success(200 to "{}")).challenge()
            fail("it should fail")
        } catch (e: PublishFailure.Server) {
            assertEquals(200, e.status)
        }
    }

    private class FakePublisher(var fail: PublishFailure? = null) : LyricsPublisher {
        var sent: Triple<PublishRequest, PublishChallenge, String>? = null
        var asked = 0

        override suspend fun challenge(): PublishChallenge {
            asked++
            fail?.let { throw it }
            return PublishChallenge.of("abcDEF123", "00" + "FF".repeat(31))!!
        }

        override suspend fun publish(request: PublishRequest, challenge: PublishChallenge, nonce: String) {
            sent = Triple(request, challenge, nonce)
        }
    }

    @Test
    fun publishingGoesThroughItsStatesAndSendsTheSolvedNonce() = runBlocking {
        val publisher = FakePublisher()
        val states = ArrayList<PublishState>()
        publishLyrics(publisher, PublishRequest.of(track, timed)!!, { synchronized(states) { states += it } }, workers = 1)
        assertEquals(PublishState.Challenging, states.first())
        assertTrue(states.any { it is PublishState.Solving })
        assertEquals(PublishState.Sending, states[states.lastIndex - 1])
        assertEquals(PublishState.Done, states.last())
        val (_, challenge, nonce) = publisher.sent!!
        assertTrue(ProofOfWork.accepts(challenge, nonce))
    }

    @Test
    fun aFailureEndsInFailedNotInAnException() = runBlocking {
        val states = ArrayList<PublishState>()
        publishLyrics(FakePublisher(PublishFailure.Offline()), PublishRequest.of(track, timed)!!, { states += it })
        assertTrue((states.last() as PublishState.Failed).failure is PublishFailure.Offline)
    }

    @Test
    fun theProgressGrowsWithTheTriesButNeverReachesOne() {
        assertEquals(0f, PublishState.Solving(0, 1_000.0).progress, 0f)
        assertTrue(PublishState.Solving(1_000, 1_000.0).progress in 0.6f..0.7f)
        assertEquals(0.99f, PublishState.Solving(1_000_000, 1_000.0).progress, 0f)
    }
}
