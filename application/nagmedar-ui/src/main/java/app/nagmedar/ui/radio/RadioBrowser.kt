package app.nagmedar.ui.radio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import app.nagmedar.ui.lyrics.RequestPace
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * S-1105: the Radio Browser directory (radio-browser.info, free, no account). A request goes to one
 * of the servers [servers] finds; one that does not answer (a timeout, a connection error, a 5xx)
 * is left aside for a while and the next server is asked, so the app works while any server is up.
 * A refusal that would be the same everywhere (4xx) is the answer. Every request says who asks
 * ([userAgent], as the directory asks) and waits its turn ([pace]).
 */
class OkHttpRadioBrowser(
    private val userAgent: String,
    private val servers: RadioServers,
    private val pace: RequestPace = RequestPace(spacingMs = 250, perWindow = 60),
    private val client: OkHttpClient = defaultClient,
) : RadioDirectory {

    override suspend fun search(query: StationQuery): List<Station> =
        call({ RadioBrowser.searchUrl(it, query) }, RadioBrowser::stations)

    override suspend fun countries(): List<DirectoryCountry> =
        call(RadioBrowser::countriesUrl, RadioBrowser::countries)

    override suspend fun languages(): List<DirectoryLanguage> =
        call(RadioBrowser::languagesUrl, RadioBrowser::languages)

    override suspend fun tags(): List<DirectoryTag> =
        call(RadioBrowser::tagsUrl, RadioBrowser::tags)

    override suspend fun click(uuid: String): StationClick =
        call({ RadioBrowser.clickUrl(it, uuid) }, RadioBrowser::click)

    override suspend fun vote(uuid: String): VoteResult =
        call({ RadioBrowser.voteUrl(it, uuid) }, RadioBrowser::vote)

    /** [read] of the first answer a server gives to the request [url] makes for it. */
    private suspend fun <T> call(url: (String) -> HttpUrl, read: (String) -> T): T = withContext(Dispatchers.IO) {
        val hosts = servers.candidates()
        if (hosts.isEmpty()) throw RadioFailure.Offline()
        var failure: RadioFailure = RadioFailure.Offline()
        for (host in hosts) {
            if (!pace.take()) throw RadioFailure.Busy(pace.waitMs())
            try {
                val text = fetch(url(host))
                // an answer that cannot be read is the server's failure as much as no answer
                val result = try {
                    read(text)
                } catch (garbled: RadioFailure.Server) {
                    throw ServerDown(garbled)
                }
                servers.markGood(host)
                pace.answered()
                return@withContext result
            } catch (down: ServerDown) {
                servers.markBad(host)
                failure = down.failure
            }
        }
        // none answered: the servers are looked for again next time
        servers.forget()
        throw failure
    }

    /** One server could not answer; the next is asked. */
    private class ServerDown(val failure: RadioFailure) : Exception()

    private suspend fun fetch(url: HttpUrl): String {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "application/json").build()
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw ServerDown(RadioFailure.Offline(e))
        }
        return response.use {
            when {
                it.isSuccessful -> try {
                    it.body?.string() ?: throw ServerDown(RadioFailure.Server(it.code))
                } catch (e: IOException) {
                    throw ServerDown(RadioFailure.Offline(e))
                }
                it.code == HTTP_TOO_MANY -> {
                    pace.refused(RadioBrowser.retryAfterMs(it.header("Retry-After")))
                    throw RadioFailure.Busy(pace.waitMs())
                }
                it.code >= HTTP_SERVER_ERROR -> throw ServerDown(RadioFailure.Server(it.code))
                else -> throw RadioFailure.Server(it.code)
            }
        }
    }

    companion object {
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_SERVER_ERROR = 500

        /** "Nagmedar/1.2.0 (+https://github.com/…)": the app and its version, and where to read about it. */
        fun userAgent(version: String?): String =
            "Nagmedar/${version?.takeIf { it.isNotBlank() } ?: "1.0"} (+https://github.com/hesaplarimdijital-coder/vlc-android)"

        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }

        /**
         * The app's directory: servers found by DNS, the server list read from the directory's own
         * address when DNS gives none.
         */
        fun create(version: String?, dns: ServerDns = SystemServerDns): OkHttpRadioBrowser {
            val agent = userAgent(version)
            val client = defaultClient
            val servers = RadioServers(dns, listing = {
                val request = Request.Builder().url(RadioServers.LIST_URL).header("User-Agent", agent).build()
                client.newCall(request).await().use { response ->
                    if (response.isSuccessful) RadioServers.parseListing(response.body?.string().orEmpty()) else emptyList()
                }
            })
            return OkHttpRadioBrowser(agent, servers, client = client)
        }
    }
}

/** The call's answer; cancelling the coroutine cancels the call. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}

/** The directory's addresses and answers (api.radio-browser.info). */
object RadioBrowser {

    private const val LIST_LIMIT = 200

    private fun base(host: String): HttpUrl.Builder = HttpUrl.Builder().scheme("https").host(host).addPathSegment("json")

    /** `/json/stations/search`: the name's words, the filters, the order and the page; broken stations hidden. */
    fun searchUrl(host: String, query: StationQuery): HttpUrl = base(host)
        .addPathSegment("stations")
        .addPathSegment("search")
        .apply {
            query.text.trim().takeIf { it.isNotEmpty() }?.let { addQueryParameter("name", it) }
            val filters = query.filters
            filters.countryCode?.let { addQueryParameter("countrycode", it.uppercase(Locale.ROOT)) }
            filters.language?.let {
                addQueryParameter("language", it)
                addQueryParameter("languageExact", "true")
            }
            filters.tag?.let {
                addQueryParameter("tag", it)
                addQueryParameter("tagExact", "true")
            }
            filters.codec?.let { addQueryParameter("codec", it) }
            if (filters.minBitrate > 0) addQueryParameter("bitrateMin", filters.minBitrate.toString())
            addQueryParameter("order", query.order.param)
            addQueryParameter("reverse", query.order.reverse.toString())
            addQueryParameter("offset", query.offset.coerceAtLeast(0).toString())
            addQueryParameter("limit", query.limit.coerceIn(1, 500).toString())
            addQueryParameter("hidebroken", "true")
        }
        .build()

    /** `/json/countries`, by name. */
    fun countriesUrl(host: String): HttpUrl = base(host).addPathSegment("countries")
        .addQueryParameter("order", "name").addQueryParameter("hidebroken", "true").build()

    /** `/json/languages`, the most used first. */
    fun languagesUrl(host: String): HttpUrl = listUrl(host, "languages")

    /** `/json/tags`, the most used first. */
    fun tagsUrl(host: String): HttpUrl = listUrl(host, "tags")

    private fun listUrl(host: String, what: String): HttpUrl = base(host).addPathSegment(what)
        .addQueryParameter("order", "stationcount").addQueryParameter("reverse", "true")
        .addQueryParameter("limit", LIST_LIMIT.toString()).addQueryParameter("hidebroken", "true").build()

    /** `/json/url/{uuid}`: the click the directory counts when a station starts playing; it answers with the address to play. */
    fun clickUrl(host: String, uuid: String): HttpUrl = base(host).addPathSegment("url").addPathSegment(uuid).build()

    /** `/json/vote/{uuid}`. */
    fun voteUrl(host: String, uuid: String): HttpUrl = base(host).addPathSegment("vote").addPathSegment(uuid).build()

    /** Retry-After in seconds, as milliseconds; null for none (or a date, not read). */
    fun retryAfterMs(header: String?): Long? = header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.times(1_000)

    /** The stations of a search's answer; entries without a UUID, a name or an address are left out. */
    fun stations(text: String): List<Station> {
        val array = parse { JSONArray(text) }
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::station) }
    }

    private fun station(o: JSONObject): Station? {
        val uuid = o.text("stationuuid") ?: return null
        val name = o.text("name") ?: return null
        val url = (o.text("url_resolved") ?: o.text("url")) ?: return null
        return Station(
            uuid = uuid,
            name = name,
            streamUrl = url,
            logoUrl = o.text("favicon")?.takeIf { it.startsWith("http://") || it.startsWith("https://") },
            homepage = o.text("homepage"),
            countryCode = o.text("countrycode")?.uppercase(Locale.ROOT),
            country = o.text("country"),
            language = o.text("language"),
            tags = o.text("tags")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty(),
            codec = o.text("codec")?.takeIf { !it.equals("unknown", ignoreCase = true) }?.uppercase(Locale.ROOT),
            bitrate = o.optInt("bitrate", 0).coerceAtLeast(0),
            votes = o.optInt("votes", 0).coerceAtLeast(0),
        )
    }

    fun countries(text: String): List<DirectoryCountry> = RadioLabels.mergeCountries(
        list(text) { o ->
            val name = o.text("name") ?: return@list null
            val code = o.text("iso_3166_1") ?: return@list null
            DirectoryCountry(name, code.uppercase(Locale.ROOT), o.optInt("stationcount", 0)).takeIf { it.stations > 0 }
        },
    )

    fun languages(text: String): List<DirectoryLanguage> = list(text) { o ->
        val name = o.text("name") ?: return@list null
        DirectoryLanguage(name, o.text("iso_639"), o.optInt("stationcount", 0)).takeIf { it.stations > 0 }
    }

    fun tags(text: String): List<DirectoryTag> = list(text) { o ->
        val name = o.text("name") ?: return@list null
        DirectoryTag(name, o.optInt("stationcount", 0)).takeIf { it.stations > 0 }
    }

    /** `/json/url/{uuid}`'s answer: `{"ok":true,"message":"…","stationuuid":"…","name":"…","url":"…"}`. */
    fun click(text: String): StationClick {
        val o = parse { JSONObject(text) }
        return StationClick(streamUrl = if (o.optBoolean("ok", true)) o.text("url") else null, name = o.text("name"))
    }

    /** `/json/vote/{uuid}`'s answer: `{"ok":true,"message":"voted for station successfully"}`. */
    fun vote(text: String): VoteResult {
        val o = parse { JSONObject(text) }
        return VoteResult(o.optBoolean("ok", false), o.text("message").orEmpty())
    }

    private inline fun <T : Any> list(text: String, item: (JSONObject) -> T?): List<T> {
        val array = parse { JSONArray(text) }
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(item) }
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

    /** An answer that cannot be read is the server's failure. */
    private inline fun <T> parse(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw RadioFailure.Server(200)
    }
}
