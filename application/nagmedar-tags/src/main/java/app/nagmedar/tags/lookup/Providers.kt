/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.3.0: from Metamatik (lookup/musicbrainz, lookup/deezer, lookup/itunes and core/network/NetworkErrors.kt),
 * a fork of Metadator, under the AGPL-3.0; their answers read with org.json instead of kotlinx.serialization, asked
 * through [WebGetter] instead of Ktor.
 */
package app.nagmedar.tags.lookup

import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Nağmedar 1.3.0: what the catalogues are asked through: a GET and its answer. OkHttp in the app, a fake in the tests. */
fun interface WebGetter {
    /** Throws an [IOException] when the network could not be reached. */
    suspend fun get(url: String): WebReply
}

/** An answer: its HTTP status and its body as text. */
class WebReply(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299
}

/** Whether [error] means "no connection" rather than "the service answered badly". */
internal fun isOfflineError(error: Throwable): Boolean = error is IOException || error.cause is IOException

/** Runs [block], letting cancellation through and handing every other failure to [onError]. */
internal inline fun <T> networkCall(onError: (Throwable) -> T, block: () -> T): T =
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        onError(error)
    }

private fun failure(error: Throwable): LookupResult =
    if (isOfflineError(error)) LookupResult.Offline else LookupResult.Failed(error.message ?: error.javaClass.simpleName)

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

private fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

private fun JSONObject.int(name: String): Int? = if (has(name) && !isNull(name)) optInt(name, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } else null

private fun JSONObject.long(name: String): Long? = if (has(name) && !isNull(name)) optLong(name, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } else null

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }

/**
 * MusicBrainz, the open music encyclopedia, with covers from the Cover Art Archive. No key is needed, but the service
 * allows one request a second from a client and blocks clients that ask more.
 */
class MusicBrainzProvider(
    private val web: WebGetter,
    private val clock: () -> Long = System::currentTimeMillis,
) : MetadataProvider {

    override val id: String = ID
    override val displayName: String = "MusicBrainz"
    override val providesArtwork: Boolean = true

    private val rateLimit = Mutex()
    private var lastRequestAt = 0L

    override suspend fun search(query: LookupQuery): LookupResult {
        if (query.isBlank) return LookupResult.Success(emptyList())
        return networkCall(onError = ::failure) {
            val url = "$BASE_URL/recording?query=${encode(buildLuceneQuery(query))}&fmt=json&limit=$RESULT_LIMIT"
            val reply = throttled { web.get(url) }
            when {
                reply.status == 503 || reply.status == 429 -> LookupResult.RateLimited
                !reply.ok -> LookupResult.Failed("HTTP ${reply.status}")
                else -> {
                    val recordings = JSONObject(reply.body).optJSONArray("recordings").objects()
                    LookupResult.Success(rank(query, recordings.flatMap(::candidates)).take(MAX_CANDIDATES))
                }
            }
        }
    }

    override fun artworkFor(identifiers: Map<String, String>): List<ProvidedArtwork> =
        // the release itself first: the group's cover is one of its releases', maybe another edition's
        listOf("release" to TAG_RELEASE, "release-group" to TAG_RELEASE_GROUP).mapNotNull { (kind, tag) ->
            identifiers[tag]?.trim()?.takeIf { MBID.matches(it) }?.let { id ->
                ProvidedArtwork(url = "$COVER_ART_URL/$kind/$id/front-1200", thumbnailUrl = "$COVER_ART_URL/$kind/$id/front-250")
            }
        }

    private suspend fun <T> throttled(block: suspend () -> T): T = rateLimit.withLock {
        val wait = lastRequestAt + MIN_INTERVAL_MS - clock()
        if (wait > 0) delay(wait)
        try {
            block()
        } finally {
            lastRequestAt = clock()
        }
    }

    companion object {
        const val ID = "musicbrainz"
        private const val BASE_URL = "https://musicbrainz.org/ws/2"
        private const val COVER_ART_URL = "https://coverartarchive.org"
        private const val TAG_RELEASE = "MUSICBRAINZ_ALBUMID"
        private const val TAG_RELEASE_GROUP = "MUSICBRAINZ_RELEASEGROUPID"

        /** What a MusicBrainz id looks like: anything else in the tag is not put in a URL. */
        private val MBID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private const val RESULT_LIMIT = 15
        private const val MAX_CANDIDATES = 20
        private const val MIN_INTERVAL_MS = 1_100L

        /** At most this many releases a recording: the rest are reissues of the same thing. */
        private const val RELEASES_PER_RECORDING = 3

        fun buildLuceneQuery(query: LookupQuery): String = buildList {
            add("recording:${quote(query.title)}")
            query.artist?.takeIf { it.isNotBlank() }?.let { add("artist:${quote(it)}") }
            query.album?.takeIf { it.isNotBlank() }?.let { add("release:${quote(it)}") }
        }.joinToString(" AND ")

        /** A phrase quoted for Lucene, what would end it early escaped. */
        private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        private fun credits(array: JSONArray?): List<Pair<String, String?>> =
            array.objects().mapNotNull { credit -> credit.text("name")?.let { it to credit.optJSONObject("artist")?.text("id") } }

        internal fun candidates(recording: JSONObject): List<LookupCandidate> {
            val id = recording.text("id") ?: return emptyList()
            val title = recording.text("title") ?: return emptyList()
            val credits = credits(recording.optJSONArray("artist-credit"))
            val artists = credits.map { it.first }
            val genres = recording.optJSONArray("tags").objects()
                .sortedByDescending { it.optInt("count") }
                .take(3)
                .mapNotNull { it.text("name")?.toTitleCase() }
            val isrc = recording.optJSONArray("isrcs").strings().firstOrNull()
            val length = recording.long("length")
            val firstRelease = recording.text("first-release-date")
            val releases = recording.optJSONArray("releases").objects()
                .sortedWith(
                    compareByDescending<JSONObject> { it.text("status") == "Official" }
                        .thenByDescending { it.optJSONObject("release-group")?.text("primary-type") == "Album" }
                        .thenBy { it.optJSONObject("release-group")?.optJSONArray("secondary-types")?.length() ?: 0 }
                        .thenBy { it.text("date") ?: "9999" },
                )
                .take(RELEASES_PER_RECORDING)
            if (releases.isEmpty()) {
                return listOf(
                    LookupCandidate(
                        providerId = ID, providerName = "MusicBrainz", id = id, title = title, artists = artists,
                        date = firstRelease, genres = genres, isrc = isrc, durationMs = length,
                        identifiers = mapOf("MUSICBRAINZ_TRACKID" to id),
                    ),
                )
            }
            return releases.mapNotNull { release ->
                val releaseId = release.text("id") ?: return@mapNotNull null
                val medium = release.optJSONArray("media").objects().firstOrNull()
                val track = medium?.optJSONArray("track").objects().firstOrNull()
                val group = release.optJSONObject("release-group")?.text("id")
                val cover = group?.let { "$COVER_ART_URL/release-group/$it" } ?: "$COVER_ART_URL/release/$releaseId"
                LookupCandidate(
                    providerId = ID,
                    providerName = "MusicBrainz",
                    id = "$id/$releaseId",
                    title = title,
                    artists = artists,
                    album = release.text("title"),
                    albumArtists = credits(release.optJSONArray("artist-credit")).map { it.first }.ifEmpty { artists },
                    date = release.text("date") ?: firstRelease,
                    trackNumber = track?.text("number")?.toIntOrNull() ?: medium?.int("track-offset")?.plus(1),
                    trackTotal = medium?.int("track-count") ?: release.int("track-count"),
                    discNumber = medium?.int("position"),
                    genres = genres,
                    isrc = isrc,
                    durationMs = length ?: track?.long("length"),
                    // the release group's cover is the likeliest to be there: a group takes one of its releases'
                    artworkUrl = "$cover/front-1200",
                    artworkThumbnailUrl = "$cover/front-250",
                    identifiers = buildMap {
                        put("MUSICBRAINZ_TRACKID", id)
                        put(TAG_RELEASE, releaseId)
                        group?.let { put(TAG_RELEASE_GROUP, it) }
                        credits.firstOrNull()?.second?.let { put("MUSICBRAINZ_ARTISTID", it) }
                        track?.text("id")?.let { put("MUSICBRAINZ_RELEASETRACKID", it) }
                    },
                )
            }
        }

        private fun String.toTitleCase(): String = split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
    }
}

/**
 * Deezer's public catalogue: no key, broad coverage of commercial releases and covers up to 1000 px. Its search
 * results are thin, so the best few are completed with their track's details.
 */
class DeezerProvider(private val web: WebGetter) : MetadataProvider {

    override val id: String = ID
    override val displayName: String = "Deezer"
    override val providesArtwork: Boolean = true

    override suspend fun search(query: LookupQuery): LookupResult {
        if (query.isBlank) return LookupResult.Success(emptyList())
        return networkCall(onError = ::failure) {
            val reply = web.get("$BASE_URL/search?q=${encode(buildQuery(query))}&limit=$RESULT_LIMIT")
            if (!reply.ok) return@networkCall LookupResult.Failed("HTTP ${reply.status}")
            val search = JSONObject(reply.body)
            search.optJSONObject("error")?.let { error ->
                return@networkCall if (error.int("code") == QUOTA_EXCEEDED) LookupResult.RateLimited
                else LookupResult.Failed(error.text("message") ?: "Deezer error ${error.int("code")}")
            }
            val ranked = rank(query, search.optJSONArray("data").objects().mapNotNull(::candidate))
            val detailed = coroutineScope { ranked.take(DETAILED_RESULTS).map { async { details(it) ?: it } }.awaitAll() }
            LookupResult.Success(rank(query, detailed + ranked.drop(DETAILED_RESULTS)))
        }
    }

    private suspend fun details(found: LookupCandidate): LookupCandidate? = networkCall(onError = { null }) {
        val reply = web.get("$BASE_URL/track/${found.id}")
        if (!reply.ok) return@networkCall null
        val track = JSONObject(reply.body)
        if (track.has("error")) return@networkCall null
        val detailed = candidate(track) ?: return@networkCall null
        // the search's hit has the album's covers even when the details have not
        detailed.copy(
            artworkUrl = detailed.artworkUrl ?: found.artworkUrl,
            artworkThumbnailUrl = detailed.artworkThumbnailUrl ?: found.artworkThumbnailUrl,
        )
    }

    companion object {
        const val ID = "deezer"
        private const val BASE_URL = "https://api.deezer.com"
        private const val RESULT_LIMIT = 15
        private const val DETAILED_RESULTS = 5
        private const val QUOTA_EXCEEDED = 4

        fun buildQuery(query: LookupQuery): String = buildList {
            add("track:${quote(query.title)}")
            query.artist?.takeIf { it.isNotBlank() }?.let { add("artist:${quote(it)}") }
            query.album?.takeIf { it.isNotBlank() }?.let { add("album:${quote(it)}") }
        }.joinToString(" ")

        private fun quote(value: String) = "\"" + value.replace("\"", "") + "\""

        internal fun candidate(track: JSONObject): LookupCandidate? {
            val id = track.long("id") ?: return null
            val title = track.text("title") ?: return null
            val artist = track.optJSONObject("artist")?.text("name")
            val main = track.optJSONArray("contributors").objects()
                .filter { it.text("role") == null || it.text("role") == "Main" }
                .mapNotNull { it.text("name") }
            val album = track.optJSONObject("album")
            return LookupCandidate(
                providerId = ID,
                providerName = "Deezer",
                id = id.toString(),
                title = title,
                artists = main.ifEmpty { listOfNotNull(artist) },
                album = album?.text("title"),
                albumArtists = listOfNotNull(artist),
                date = track.text("release_date") ?: album?.text("release_date"),
                trackNumber = track.int("track_position"),
                discNumber = track.int("disk_number"),
                isrc = track.text("isrc"),
                durationMs = track.long("duration")?.times(1000),
                artworkUrl = album?.text("cover_xl")?.takeUnless { it.isPlaceholder() },
                artworkThumbnailUrl = album?.text("cover_medium")?.takeUnless { it.isPlaceholder() },
            )
        }

        /** An album without a cover still gets an address, with nothing where the picture's name goes. */
        private fun String.isPlaceholder(): Boolean = contains("/cover//")
    }
}

/**
 * The iTunes Store's public search: no key, the catalogue of what is sold and streamed, and covers in any size up to
 * the one the label delivered. The catalogue differs between countries: the store of [country] (the device's) is
 * searched, so that a song released only there is found.
 */
class ITunesProvider(
    private val web: WebGetter,
    private val country: () -> String = { Locale.getDefault().country },
) : MetadataProvider {

    override val id: String = ID
    override val displayName: String = "iTunes"
    override val providesArtwork: Boolean = true

    override suspend fun search(query: LookupQuery): LookupResult {
        if (query.isBlank) return LookupResult.Success(emptyList())
        return networkCall(onError = ::failure) {
            val store = country().takeIf { it.length == 2 }
            var reply = web.get(url(query, store))
            // a country without a store of its own is refused: the default store will do
            if (reply.status == 400 && store != null) reply = web.get(url(query, null))
            when {
                // past some twenty searches a minute the store turns the client away for a while
                reply.status == 403 || reply.status == 429 -> LookupResult.RateLimited
                !reply.ok -> LookupResult.Failed("HTTP ${reply.status}")
                else -> LookupResult.Success(rank(query, JSONObject(reply.body).optJSONArray("results").objects().mapNotNull(::candidate)))
            }
        }
    }

    private fun url(query: LookupQuery, store: String?): String =
        "$SEARCH_URL?term=${encode(buildTerm(query))}&media=music&entity=song&limit=$RESULT_LIMIT" + (store?.let { "&country=$it" } ?: "")

    companion object {
        const val ID = "itunes"
        private const val SEARCH_URL = "https://itunes.apple.com/search"
        private const val RESULT_LIMIT = 15

        /** Sharp wherever a cover is shown, and a few hundred kilobytes: a cover is stored again in every song of its album. */
        private const val ARTWORK_EDGE = 1500
        private const val THUMBNAIL_EDGE = 250

        /** The size in the name of the picture the store links to, which any other replaces. */
        private val ARTWORK_SIZE = Regex("/\\d+x\\d+(bb|cc)?(?=\\.[A-Za-z]+$)")

        /** The store searches plain words: the artist, then the song. */
        fun buildTerm(query: LookupQuery): String = listOfNotNull(query.artist?.takeIf { it.isNotBlank() }, query.title).joinToString(" ").trim()

        /** The same picture [edge] pixels wide, or as large as the store has it: it never enlarges one. */
        fun artworkUrl(url: String, edge: Int): String = url.replace(ARTWORK_SIZE, "/${edge}x${edge}bb")

        internal fun candidate(result: JSONObject): LookupCandidate? {
            if (result.text("kind") != "song") return null
            val id = result.long("trackId") ?: return null
            val title = result.text("trackName") ?: return null
            val artist = result.text("artistName")
            val artwork = result.text("artworkUrl100")
            return LookupCandidate(
                providerId = ID,
                providerName = "iTunes",
                id = id.toString(),
                title = title,
                artists = listOfNotNull(artist),
                album = result.text("collectionName"),
                albumArtists = listOfNotNull(result.text("collectionArtistName") ?: artist),
                // "2008-11-14T08:00:00Z": the day is all a tag keeps
                date = result.text("releaseDate")?.take(10),
                trackNumber = result.int("trackNumber"),
                trackTotal = result.int("trackCount"),
                discNumber = result.int("discNumber"),
                discTotal = result.int("discCount"),
                genres = listOfNotNull(result.text("primaryGenreName")),
                durationMs = result.long("trackTimeMillis"),
                artworkUrl = artwork?.let { artworkUrl(it, ARTWORK_EDGE) },
                artworkThumbnailUrl = artwork?.let { artworkUrl(it, THUMBNAIL_EDGE) },
            )
        }
    }
}
