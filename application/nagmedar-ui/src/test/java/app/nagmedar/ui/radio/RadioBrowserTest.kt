package app.nagmedar.ui.radio

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.lyrics.RequestPace
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.random.Random

/** S-1105: the directory's client: a server that is down hands over to the next, and every request says who asks. No network. */
@RunWith(AndroidJUnit4::class)
class RadioBrowserTest {

    private val version = "1.4.0"
    private val agent = "Nagmedar/1.4.0 (+https://github.com/hesaplarimdijital-coder/vlc-android)"

    private val names = listOf("de1.api.radio-browser.info", "fr1.api.radio-browser.info", "nl1.api.radio-browser.info")

    private class Dns(private val names: List<String>) : ServerDns {
        override fun addresses(name: String): List<String> = names.indices.map { "10.0.0.$it" }

        override fun hostName(address: String): String? = names[address.substringAfterLast('.').toInt()]
    }

    /** What the servers answer: by host, a status and a body, or a connection that fails. */
    private class Net {
        val requests = mutableListOf<Request>()
        val replies = HashMap<String, Triple<Int, String, Map<String, String>>>()
        val broken = HashSet<String>()

        fun answer(host: String, status: Int, body: String, headers: Map<String, String> = emptyMap()) {
            replies[host] = Triple(status, body, headers)
        }

        fun handle(request: Request): Response {
            requests += request
            val host = request.url.host
            if (host in broken) throw IOException("connection to $host failed")
            val reply = replies[host] ?: return json(request, 200, fine(request))
            return json(request, reply.first, reply.second, reply.third)
        }
    }

    private val net = Net()
    private val client = OkHttpClient.Builder().addInterceptor { chain -> net.handle(chain.request()) }.build()
    private var now = 5_000_000L

    private fun directory(): Pair<OkHttpRadioBrowser, RadioServers> {
        val servers = RadioServers(Dns(names), { emptyList() }, Random(3), clock = { now })
        val pace = RequestPace(spacingMs = 0, perWindow = 1_000, clock = { now }, sleep = {})
        return OkHttpRadioBrowser(OkHttpRadioBrowser.userAgent(version), servers, pace, client) to servers
    }

    @Test
    fun theUserAgentNamesTheAppAndItsVersionAndNoOneElse() {
        assertEquals(agent, OkHttpRadioBrowser.userAgent(version))
        assertEquals("Nagmedar/1.0 (+https://github.com/hesaplarimdijital-coder/vlc-android)", OkHttpRadioBrowser.userAgent(null))
        assertEquals("Nagmedar/1.0 (+https://github.com/hesaplarimdijital-coder/vlc-android)", OkHttpRadioBrowser.userAgent("  "))
        assertFalse(OkHttpRadioBrowser.userAgent(version).contains("@"))
    }

    @Test
    fun everyRequestSendsTheUserAgentAndUsesHttps() = runBlocking {
        val (directory, _) = directory()
        directory.search(StationQuery("caz"))
        directory.countries()
        directory.languages()
        directory.tags()
        directory.click("abc")
        directory.vote("abc")
        assertEquals(6, net.requests.size)
        net.requests.forEach {
            assertEquals(agent, it.header("User-Agent"))
            assertEquals("https", it.url.scheme)
            assertTrue(it.url.host in names)
        }
    }

    @Test
    fun aServerThatIsDownHandsOverToTheNext() = runBlocking {
        val (directory, servers) = directory()
        val order = servers.candidates()
        net.answer(order[0], 503, "Service Unavailable")
        net.broken += order[1]
        net.replies[order[2]] = Triple(200, """[{"stationuuid":"u1","name":"Gece Cazı","url":"https://yayin.example/caz"}]""", emptyMap())
        val found = directory.search(StationQuery("caz"))
        assertEquals(listOf("Gece Cazı"), found.map { it.name })
        // each was asked once, in order
        assertEquals(order, net.requests.map { it.url.host })
    }

    @Test
    fun theServerThatFailedIsRememberedAndLeftAsideForAWhile() = runBlocking {
        val (directory, servers) = directory()
        val order = servers.candidates()
        net.answer(order[0], 502, "Bad Gateway")
        directory.countries()
        net.requests.clear()
        directory.countries()
        // the second request goes straight to a server that works
        assertEquals(listOf(order[1]), net.requests.map { it.url.host })
        // its time is up: it is asked first again
        now += 11 * 60_000L
        net.requests.clear()
        directory.countries()
        assertEquals(order[0], net.requests.first().url.host)
    }

    @Test
    fun aTimeoutIsAFailureToo() = runBlocking {
        val (directory, servers) = directory()
        val order = servers.candidates()
        net.broken += order[0]
        directory.tags()
        assertEquals(order.take(2), net.requests.map { it.url.host })
    }

    @Test
    fun anAnswerThatCannotBeReadHandsOverToo() = runBlocking {
        val (directory, servers) = directory()
        val order = servers.candidates()
        net.answer(order[0], 200, "<html>captive portal</html>")
        directory.countries()
        assertEquals(order.take(2), net.requests.map { it.url.host })
    }

    @Test
    fun whenAllAreDownTheAnswerIsThatAndTheyAreLookedForAgain() = runBlocking {
        val (directory, servers) = directory()
        val order = servers.candidates()
        order.forEach { net.broken += it }
        try {
            directory.countries()
            fail()
        } catch (e: RadioFailure.Offline) {
            assertEquals(order, net.requests.map { it.url.host })
        }
        order.forEach { net.broken -= it }
        net.requests.clear()
        assertEquals(emptyList<DirectoryCountry>(), directory.countries())
    }

    @Test
    fun serversThatAnswerWithAnErrorAreTheServerFailure() = runBlocking {
        val (directory, servers) = directory()
        servers.candidates().forEach { net.answer(it, 500, "oops") }
        try {
            directory.tags()
            fail()
        } catch (e: RadioFailure.Server) {
            assertEquals(500, e.status)
        }
    }

    @Test
    fun aRefusalThatWouldBeTheSameEverywhereIsNotRetried() = runBlocking {
        val (directory, servers) = directory()
        val order = servers.candidates()
        net.answer(order[0], 404, "{}")
        try {
            directory.click("nope")
            fail()
        } catch (e: RadioFailure.Server) {
            assertEquals(404, e.status)
        }
        assertEquals(1, net.requests.size)
        // and the server is not marked bad for it
        net.requests.clear()
        net.replies.clear()
        directory.countries()
        assertEquals(order[0], net.requests.single().url.host)
    }

    @Test
    fun tooManyRequestsWaitInsteadOfTryingTheOthers() = runBlocking {
        val (directory, servers) = directory()
        val order = servers.candidates()
        net.answer(order[0], 429, "slow down", mapOf("Retry-After" to "90"))
        try {
            directory.search(StationQuery())
            fail()
        } catch (e: RadioFailure.Busy) {
            assertEquals(90_000L, e.waitMs)
        }
        assertEquals(1, net.requests.size)
        // while the wait lasts nothing is asked
        net.requests.clear()
        try {
            directory.countries()
            fail()
        } catch (e: RadioFailure.Busy) {
            assertTrue(e.waitMs > 0)
        }
        assertTrue(net.requests.isEmpty())
    }

    @Test
    fun withoutAnyServerItIsOffline() = runBlocking {
        val servers = RadioServers(object : ServerDns {
            override fun addresses(name: String) = emptyList<String>()

            override fun hostName(address: String): String? = null
        }, { emptyList() }, Random(1), clock = { now }, seeds = emptyList())
        val directory = OkHttpRadioBrowser(agent, servers, RequestPace(0, 1_000, clock = { now }, sleep = {}), client)
        try {
            directory.countries()
            fail()
        } catch (e: RadioFailure.Offline) {
            assertTrue(net.requests.isEmpty())
        }
    }

    @Test
    fun theClickIsTheDirectorysUrlRequestAndGivesTheAddressToPlay() = runBlocking {
        val (directory, servers) = directory()
        val host = servers.candidates()[0]
        net.answer(host, 200, """{"ok":true,"message":"retrieved station url","stationuuid":"u-9","name":"Gece Cazı","url":"https://cdn.example/caz-live.mp3"}""")
        val click = directory.click("u-9")
        assertEquals("https://cdn.example/caz-live.mp3", click.streamUrl)
        assertEquals("https://$host/json/url/u-9", net.requests.single().url.toString())
    }

    @Test
    fun aVoteIsTheDirectorysVoteRequest() = runBlocking {
        val (directory, servers) = directory()
        val host = servers.candidates()[0]
        net.answer(host, 200, """{"ok":true,"message":"voted for station successfully"}""")
        assertTrue(directory.vote("u-9").ok)
        assertEquals("https://$host/json/vote/u-9", net.requests.single().url.toString())
        net.answer(host, 200, """{"ok":false,"message":"VoteError 'you are voting for the same station too often'"}""")
        val refused = directory.vote("u-9")
        assertFalse(refused.ok)
        assertTrue(refused.message.contains("too often"))
    }

    @Test
    fun theSearchAsksForWhatWasTyped() {
        val query = StationQuery(
            text = "  gece  caz ",
            filters = StationFilters(countryCode = "tr", language = "turkish", tag = "jazz", codec = "MP3", minBitrate = 128),
            order = StationOrder.Bitrate,
            offset = 50,
            limit = 50,
        )
        assertEquals(
            "https://de1.api.radio-browser.info/json/stations/search?name=gece%20%20caz&countrycode=TR&language=turkish&languageExact=true" +
                "&tag=jazz&tagExact=true&codec=MP3&bitrateMin=128&order=bitrate&reverse=true&offset=50&limit=50&hidebroken=true",
            RadioBrowser.searchUrl("de1.api.radio-browser.info", query).toString(),
        )
    }

    @Test
    fun aPlainSearchLeavesOutWhatIsNotSet() {
        assertEquals(
            "https://fr1.api.radio-browser.info/json/stations/search?order=votes&reverse=true&offset=0&limit=50&hidebroken=true",
            RadioBrowser.searchUrl("fr1.api.radio-browser.info", StationQuery()).toString(),
        )
        assertEquals(
            "https://fr1.api.radio-browser.info/json/stations/search?name=a%26b%3Dc&order=name&reverse=false&offset=0&limit=500&hidebroken=true",
            RadioBrowser.searchUrl("fr1.api.radio-browser.info", StationQuery("a&b=c", order = StationOrder.Name, offset = -4, limit = 9_000)).toString(),
        )
    }

    @Test
    fun theListsAreAskedForBrokenStationsHidden() {
        val host = "nl1.api.radio-browser.info"
        assertEquals("https://$host/json/countries?order=name&hidebroken=true", RadioBrowser.countriesUrl(host).toString())
        assertTrue(RadioBrowser.languagesUrl(host).toString().startsWith("https://$host/json/languages?"))
        assertTrue(RadioBrowser.tagsUrl(host).toString().contains("hidebroken=true"))
        assertEquals("https://$host/json/url/ab-12", RadioBrowser.clickUrl(host, "ab-12").toString())
        assertEquals("https://$host/json/vote/ab-12", RadioBrowser.voteUrl(host, "ab-12").toString())
        // an id is one path segment, whatever it holds
        assertEquals("https://$host/json/url/a%2Fb", RadioBrowser.clickUrl(host, "a/b").toString())
    }

    @Test
    fun stationsAreReadAsTheDirectoryGivesThem() {
        val text = """[
          {"stationuuid":"u1","name":" Gece Cazı ","url":"http://a.example/raw","url_resolved":"https://a.example/resolved.mp3",
           "homepage":"https://a.example","favicon":"https://a.example/logo.png","tags":"jazz, gece,jazz,,","country":"Turkey","countrycode":"tr",
           "language":"turkish","codec":"mp3","bitrate":128,"votes":120},
          {"stationuuid":"u2","name":"Eksik Adres","url":"","url_resolved":""},
          {"stationuuid":"","name":"Kimliksiz","url":"http://x.example"},
          {"stationuuid":"u3","name":"Logo Yanlış","url":"http://b.example/s","favicon":"data:image/png;base64,AAAA","codec":"UNKNOWN","bitrate":-5,"votes":null},
          {"stationuuid":"u4","name":"Ham Adres","url":"http://c.example/s","url_resolved":null}
        ]"""
        val stations = RadioBrowser.stations(text)
        assertEquals(listOf("u1", "u3", "u4"), stations.map { it.uuid })
        val first = stations[0]
        assertEquals("Gece Cazı", first.name)
        assertEquals("https://a.example/resolved.mp3", first.streamUrl)
        assertEquals("https://a.example/logo.png", first.logoUrl)
        assertEquals(listOf("jazz", "gece"), first.tags)
        assertEquals("TR", first.countryCode)
        assertEquals("MP3", first.codec)
        assertEquals(128, first.bitrate)
        assertEquals(120, first.votes)
        // a logo that is not a web address is none; an unknown codec is none; a bitrate cannot be negative
        assertNull(stations[1].logoUrl)
        assertNull(stations[1].codec)
        assertEquals(0, stations[1].bitrate)
        assertEquals(0, stations[1].votes)
        assertEquals("http://c.example/s", stations[2].streamUrl)
    }

    @Test
    fun theListsAreRead() {
        val countries = RadioBrowser.countries(
            """[{"name":"Turkey","iso_3166_1":"tr","stationcount":812},{"name":"Nowhere","iso_3166_1":"","stationcount":3},{"name":"Empty","iso_3166_1":"XX","stationcount":0}]""",
        )
        assertEquals(listOf(DirectoryCountry("Turkey", "TR", 812)), countries)
        val languages = RadioBrowser.languages("""[{"name":"turkish","iso_639":"tr","stationcount":700},{"name":"klingon","iso_639":null,"stationcount":2}]""")
        assertEquals(listOf(DirectoryLanguage("turkish", "tr", 700), DirectoryLanguage("klingon", null, 2)), languages)
        assertEquals(listOf(DirectoryTag("jazz", 400)), RadioBrowser.tags("""[{"name":"jazz","stationcount":400},{"name":"","stationcount":9}]"""))
    }

    @Test
    fun clickAndVoteAnswersAreRead() {
        assertEquals(StationClick("https://x.example/s", "Gece Cazı"), RadioBrowser.click("""{"ok":true,"name":"Gece Cazı","url":"https://x.example/s"}"""))
        // a click the directory did not take gives no address: the kept one plays
        assertNull(RadioBrowser.click("""{"ok":false,"message":"station not found"}""").streamUrl)
        assertEquals(VoteResult(true, "voted for station successfully"), RadioBrowser.vote("""{"ok":true,"message":"voted for station successfully"}"""))
        assertFalse(RadioBrowser.vote("""{"message":"?"}""").ok)
    }

    @Test
    fun anAnswerThatIsNotJsonIsTheServersFailure() {
        for (read in listOf<(String) -> Any>(RadioBrowser::stations, RadioBrowser::countries, RadioBrowser::click, RadioBrowser::vote)) {
            try {
                read("<html>502</html>")
                fail()
            } catch (e: RadioFailure.Server) {
                assertEquals(200, e.status)
            }
        }
    }

    @Test
    fun retryAfterIsInSeconds() {
        assertEquals(120_000L, RadioBrowser.retryAfterMs(" 120 "))
        assertNull(RadioBrowser.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertNull(RadioBrowser.retryAfterMs(null))
        assertNull(RadioBrowser.retryAfterMs("-3"))
    }

    private companion object {
        /** What a healthy server answers to [request]: a list, or the click's and the vote's object. */
        fun fine(request: Request): String = when {
            "/url/" in request.url.encodedPath -> """{"ok":true,"url":"https://cdn.example/live"}"""
            "/vote/" in request.url.encodedPath -> """{"ok":true,"message":"voted for station successfully"}"""
            else -> "[]"
        }

        fun json(request: Request, status: Int, body: String, headers: Map<String, String> = emptyMap()): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("status $status")
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
    }
}
