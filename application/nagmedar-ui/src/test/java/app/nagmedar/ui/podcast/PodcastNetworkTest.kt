package app.nagmedar.ui.podcast

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.lyrics.RequestPace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** S-1101: what the app asks of the network and how it reads the answers, with a fake in place of the network. */
@RunWith(AndroidJUnit4::class)
class PodcastNetworkTest {

    private val agent = "Nagmedar/9.9.9 (+https://github.com/hesaplarimdijital-coder/vlc-android)"
    private val requests = mutableListOf<Request>()
    private var respond: (Request) -> Response = { throw AssertionError("no network in tests: ${it.url}") }

    @Volatile
    private var currentCall: Call? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            requests += chain.request()
            currentCall = chain.call()
            respond(chain.request())
        }
        .build()

    private fun reply(request: Request, code: Int, body: ByteArray = ByteArray(0), type: String = "application/rss+xml", vararg headers: Pair<String, String>): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fake")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .body(body.toResponseBody(type.toMediaType()))
            .build()

    /** An answer whose body breaks half way: the connection reset, a timeout. */
    private fun withBrokenBody(request: Request): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fake")
            .body(object : ResponseBody() {
                override fun contentType() = "application/json".toMediaType()

                override fun contentLength() = -1L

                override fun source(): BufferedSource = object : Source {
                    override fun read(sink: Buffer, byteCount: Long): Long = throw IOException("connection reset")

                    override fun timeout() = Timeout.NONE

                    override fun close() = Unit
                }.buffer()
            })
            .build()

    private fun fetch(url: String, etag: String? = null, lastModified: String? = null): FeedFetch =
        runBlocking { OkHttpFeedSource(agent, client).fetch(url, etag, lastModified) }

    private fun failure(url: String): FeedFailure = try {
        fetch(url)
        throw AssertionError("fetched")
    } catch (e: FeedFailure) {
        e
    }

    // ------------------------------------------------------------ feeds

    @Test
    fun theUserAgentNamesTheAppAndNoOne() {
        assertEquals("Nagmedar/1.2.3 (+https://github.com/hesaplarimdijital-coder/vlc-android)", OkHttpFeedSource.userAgent("1.2.3"))
        assertEquals("Nagmedar/1.0 (+https://github.com/hesaplarimdijital-coder/vlc-android)", OkHttpFeedSource.userAgent(null))
        assertFalse('@' in OkHttpFeedSource.userAgent("1.2.3"))
    }

    @Test
    fun aFeedIsFetchedAndRead() {
        respond = { reply(it, 200, Fixtures.bytes("feed.xml"), headers = arrayOf("ETag" to "\"abc\"", "Last-Modified" to "Sat, 10 Oct 2026 06:00:00 GMT")) }
        val fresh = fetch("https://example.org/feed.xml") as FeedFetch.Fresh
        assertEquals("Nağmedar Podcast", fresh.feed.title)
        assertEquals("\"abc\"", fresh.etag)
        assertEquals("Sat, 10 Oct 2026 06:00:00 GMT", fresh.lastModified)
        assertEquals("https://example.org/feed.xml", fresh.finalUrl)
        assertNull(fresh.permanentUrl)
        assertEquals(agent, requests.single().header("User-Agent"))
        assertTrue(requests.single().header("Accept")!!.contains("application/rss+xml"))
        assertNull(requests.single().header("If-None-Match"))
    }

    @Test
    fun aRefreshIsConditionalAndAnswered304() {
        respond = { reply(it, 304) }
        val result = fetch("https://example.org/feed.xml", etag = "\"abc\"", lastModified = "Sat, 10 Oct 2026 06:00:00 GMT")
        assertTrue(result is FeedFetch.NotModified)
        assertEquals("\"abc\"", requests.single().header("If-None-Match"))
        assertEquals("Sat, 10 Oct 2026 06:00:00 GMT", requests.single().header("If-Modified-Since"))
    }

    @Test
    fun aFeedSentGzippedWithoutSayingSo() {
        respond = { reply(it, 200, Fixtures.gzip(Fixtures.bytes("feed.xml")), "application/x-gzip") }
        assertEquals("Nağmedar Podcast", (fetch("https://example.org/feed.xml") as FeedFetch.Fresh).feed.title)
    }

    @Test
    fun aPermanentRedirectIsToldToTheCaller() {
        respond = { request ->
            when (request.url.toString()) {
                "https://old.example.org/feed" -> reply(request, 301, headers = arrayOf("Location" to "https://new.example.org/feed"))
                "https://new.example.org/feed" -> reply(request, 308, headers = arrayOf("Location" to "/rss.xml"))
                else -> reply(request, 200, Fixtures.bytes("feed.xml"))
            }
        }
        val fresh = fetch("https://old.example.org/feed") as FeedFetch.Fresh
        assertEquals("https://new.example.org/rss.xml", fresh.permanentUrl)
        assertEquals("https://new.example.org/rss.xml", fresh.finalUrl)
        assertEquals(3, requests.size)
    }

    @Test
    fun aMoveFromHttpsToHttpIsFollowedAndNotKept() {
        respond = { request ->
            when (request.url.toString()) {
                "https://example.org/feed" -> reply(request, 301, headers = arrayOf("Location" to "http://example.org/feed.xml"))
                else -> reply(request, 200, Fixtures.bytes("feed.xml"))
            }
        }
        val fresh = fetch("https://example.org/feed") as FeedFetch.Fresh
        assertEquals("http://example.org/feed.xml", fresh.finalUrl)
        assertNull("a cleartext address is never the subscription's", fresh.permanentUrl)
    }

    @Test
    fun aMoveFromHttpToHttpsIsKept() {
        respond = { request ->
            when (request.url.toString()) {
                "http://example.org/feed" -> reply(request, 308, headers = arrayOf("Location" to "https://example.org/feed"))
                else -> reply(request, 200, Fixtures.bytes("feed.xml"))
            }
        }
        assertEquals("https://example.org/feed", (fetch("http://example.org/feed") as FeedFetch.Fresh).permanentUrl)
    }

    @Test
    fun aRedirectToAnotherKindOfAddressIsRefused() {
        listOf("ftp://example.org/feed.xml", "file:///etc/passwd", "content://media/external/file/1", "javascript:alert(1)").forEach { target ->
            requests.clear()
            respond = { request -> reply(request, 301, headers = arrayOf("Location" to target)) }
            val failure = failure("https://example.org/feed")
            assertTrue(target, failure is FeedFailure.Server)
            assertEquals(target, 1, requests.size)
        }
    }

    @Test
    fun aBodyThatBreaksIsOffline() {
        respond = { withBrokenBody(it) }
        assertTrue(failure("https://example.org/feed") is FeedFailure.Offline)
    }

    @Test
    fun cancellingTheCoroutineCancelsTheCall() {
        val started = CountDownLatch(1)
        var cancelledCall = false
        respond = {
            started.countDown()
            val end = System.currentTimeMillis() + 10_000
            while (currentCall?.isCanceled() != true && System.currentTimeMillis() < end) Thread.sleep(5)
            cancelledCall = currentCall?.isCanceled() == true
            throw IOException("Canceled")
        }
        runBlocking {
            val job = launch(Dispatchers.Default) { OkHttpFeedSource(agent, client).fetch("https://example.org/feed") }
            assertTrue(started.await(10, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }
        assertTrue("the call under the coroutine was cancelled, not left to its timeout", cancelledCall)
    }

    @Test
    fun aTemporaryRedirectIsFollowedAndNotRemembered() {
        respond = { request ->
            when (request.url.toString()) {
                "https://example.org/feed" -> reply(request, 302, headers = arrayOf("Location" to "https://cdn.example.org/feed.xml"))
                else -> reply(request, 200, Fixtures.bytes("feed.xml"))
            }
        }
        val fresh = fetch("https://example.org/feed") as FeedFetch.Fresh
        assertNull(fresh.permanentUrl)
        assertEquals("https://cdn.example.org/feed.xml", fresh.finalUrl)
    }

    @Test
    fun aPermanentRedirectAfterATemporaryOneIsNotTheFeedsAddress() {
        respond = { request ->
            when (request.url.toString()) {
                "https://example.org/a" -> reply(request, 302, headers = arrayOf("Location" to "https://example.org/b"))
                "https://example.org/b" -> reply(request, 301, headers = arrayOf("Location" to "https://example.org/c"))
                else -> reply(request, 200, Fixtures.bytes("feed.xml"))
            }
        }
        assertNull((fetch("https://example.org/a") as FeedFetch.Fresh).permanentUrl)
    }

    @Test
    fun relativeAddressesInAFeedAreReadAgainstWhereItWas() {
        respond = { request ->
            when (request.url.toString()) {
                "https://example.org/feed" -> reply(request, 302, headers = arrayOf("Location" to "https://cdn.example.org/shows/feed.xml"))
                else -> reply(request, 200, Fixtures.bytes("feed-nasty.xml"))
            }
        }
        val episode = (fetch("https://example.org/feed") as FeedFetch.Fresh).feed.episodes.first()
        assertEquals("https://cdn.example.org/media/relative%20episode.mp3", episode.audioUrl)
    }

    @Test
    fun aRedirectLoopEnds() {
        respond = { reply(it, 302, headers = arrayOf("Location" to it.url.toString() + "x")) }
        assertEquals(OkHttpFeedSource.TOO_MANY_REDIRECTS, (failure("https://example.org/a") as FeedFailure.Server).status)
    }

    @Test
    fun statusesAndTheNetwork() {
        respond = { reply(it, 404) }
        assertEquals(404, (failure("https://example.org/feed") as FeedFailure.Server).status)
        respond = { reply(it, 503) }
        assertEquals(503, (failure("https://example.org/feed") as FeedFailure.Server).status)
        respond = { throw IOException("unreachable") }
        assertTrue(failure("https://example.org/feed") is FeedFailure.Offline)
        respond = { reply(it, 302) }
        assertTrue(failure("https://example.org/feed") is FeedFailure.Server)
    }

    @Test
    fun aWebPageIsNoFeed() {
        respond = { reply(it, 200, Fixtures.bytes("not-a-feed.html"), "text/html") }
        assertEquals(InvalidReason.NotFeed, (failure("https://example.org/") as FeedFailure.Invalid).reason)
        respond = { reply(it, 200, ByteArray(0)) }
        assertEquals(InvalidReason.Empty, (failure("https://example.org/") as FeedFailure.Invalid).reason)
    }

    @Test
    fun anAddressThatIsNoAddress() {
        assertTrue(failure("not an address") is FeedFailure.BadAddress)
        assertTrue(requests.isEmpty())
    }

    // ------------------------------------------------------------ Podcast Index

    /** A pace that never waits; one for each directory, since a refusal blocks its pace for a long while. */
    private fun fastPace() = RequestPace(spacingMs = 0, perWindow = 1_000, clock = { 0L })

    private fun index(key: String = "ABC", secret: String = "SECRET") =
        PodcastIndexDirectory(key, secret, agent, fastPace(), client, clock = { 1_700_000_000L })

    @Test
    fun podcastIndexIsAskedWithSignedHeaders() = runBlocking {
        respond = { reply(it, 200, """{"status":"true","feeds":[{"id":1,"title":"Gece","url":"https://feeds.example.org/gece.xml"}]}""".toByteArray(), "application/json") }
        val hits = index().search("gece yayını", 30)
        assertEquals(listOf("Gece"), hits.map { it.title })
        val request = requests.single()
        assertEquals("api.podcastindex.org", request.url.host)
        assertEquals("/api/1.0/search/byterm", request.url.encodedPath)
        assertEquals("gece yayını", request.url.queryParameter("q"))
        assertEquals("30", request.url.queryParameter("max"))
        assertEquals(agent, request.header("User-Agent"))
        assertEquals("ABC", request.header("X-Auth-Key"))
        assertEquals("1700000000", request.header("X-Auth-Date"))
        assertEquals("e31761d681465c8f935fa442a83511ab6b22e23c", request.header("Authorization"))
    }

    @Test
    fun withoutKeysPodcastIndexIsNotAvailableAndNotAsked() = runBlocking {
        assertFalse(index(key = "").available)
        assertFalse(index(secret = " ").available)
        assertTrue(index(key = "").search("gece", 30).isEmpty())
        assertNull(index(secret = "").byFeedUrl("https://example.org/feed"))
        assertTrue(requests.isEmpty())
        assertTrue(index().available)
    }

    @Test
    fun aBodyThatBreaksIsAnOfflineDirectory() = runBlocking {
        respond = { withBrokenBody(it) }
        expectFailure<DirectoryFailure.Offline> { index().search("gece", 30) }
        expectFailure<DirectoryFailure.Offline> { ITunesDirectory(agent, null, fastPace(), client).search("gece", 30) }
        // the lookup of one feed is only a nicety: nothing, not an exception
        assertNull(index().byFeedUrl("https://example.org/feed"))
        Unit
    }

    @Test
    fun aBusyDirectoryStaysBusyAndIsNotMadeOffline() = runBlocking {
        respond = { reply(it, 429, headers = arrayOf("Retry-After" to "30")) }
        expectFailure<DirectoryFailure.Busy> { index().search("gece", 30) }
        Unit
    }

    @Test
    fun podcastIndexAnswersThatAreNotHits() = runBlocking {
        respond = { reply(it, 404) }
        assertTrue(index().search("gece", 30).isEmpty())
        respond = { reply(it, 401) }
        assertEquals(401, expectFailure<DirectoryFailure.Server> { index().search("gece", 30) }.status)
        respond = { reply(it, 429, headers = arrayOf("Retry-After" to "30")) }
        expectFailure<DirectoryFailure.Busy> { index().search("gece", 30) }
        respond = { throw IOException("unreachable") }
        expectFailure<DirectoryFailure.Offline> { index().search("gece", 30) }
        Unit
    }

    @Test
    fun podcastIndexKnowsAFeedByItsAddress() = runBlocking {
        respond = { reply(it, 200, """{"status":"true","feed":{"id":77,"title":"Gece","url":"https://feeds.example.org/gece.xml"}}""".toByteArray(), "application/json") }
        val hit = index().byFeedUrl("https://feeds.example.org/gece.xml")!!
        assertEquals(77L, hit.podcastIndexId)
        assertEquals("/api/1.0/podcasts/byfeedurl", requests.single().url.encodedPath)
        assertEquals("https://feeds.example.org/gece.xml", requests.single().url.queryParameter("url"))
        respond = { reply(it, 404) }
        assertNull(index().byFeedUrl("https://feeds.example.org/yok.xml"))
        respond = { throw IOException("unreachable") }
        assertNull(index().byFeedUrl("https://feeds.example.org/yok.xml"))
    }

    // ------------------------------------------------------------ iTunes

    @Test
    fun itunesIsAskedForPodcasts() = runBlocking {
        respond = { reply(it, 200, """{"resultCount":1,"results":[{"collectionName":"Gece","feedUrl":"https://feeds.example.org/gece.xml","trackCount":3}]}""".toByteArray(), "text/javascript") }
        val hits = ITunesDirectory(agent, "tr", fastPace(), client).search("gece yayını", 30)
        assertEquals(listOf("Gece"), hits.map { it.title })
        val request = requests.single()
        assertEquals("itunes.apple.com", request.url.host)
        assertEquals("podcast", request.url.queryParameter("media"))
        assertEquals("podcast", request.url.queryParameter("entity"))
        assertEquals("gece yayını", request.url.queryParameter("term"))
        assertEquals("30", request.url.queryParameter("limit"))
        assertEquals("TR", request.url.queryParameter("country"))
        assertEquals(agent, request.header("User-Agent"))
    }

    @Test
    fun itunesAddress() {
        assertEquals(
            "https://itunes.apple.com/search?media=podcast&entity=podcast&term=gece%20yay%C4%B1n%C4%B1&limit=30",
            ITunesDirectory.searchUrl(ITunesDirectory.BASE_URL, "  gece yayını ", 30, null).toString(),
        )
        assertTrue(ITunesDirectory.searchUrl(ITunesDirectory.BASE_URL, "x", 5, "tr").toString().endsWith("&country=TR"))
        assertFalse("country=" in ITunesDirectory.searchUrl(ITunesDirectory.BASE_URL, "x", 5, "tur").toString())
    }

    @Test
    fun itunesRefusingAndFailing() = runBlocking {
        fun directory() = ITunesDirectory(agent, null, fastPace(), client)
        respond = { reply(it, 403) }
        expectFailure<DirectoryFailure.Busy> { directory().search("gece", 30) }
        respond = { reply(it, 500) }
        assertEquals(500, expectFailure<DirectoryFailure.Server> { directory().search("gece", 30) }.status)
        respond = { throw IOException("unreachable") }
        expectFailure<DirectoryFailure.Offline> { directory().search("gece", 30) }
        Unit
    }

    private inline fun <reified T : DirectoryFailure> expectFailure(call: () -> Any?): T {
        try {
            call()
        } catch (e: DirectoryFailure) {
            if (e is T) return e
            throw e
        }
        fail("answered")
        throw AssertionError()
    }
}
