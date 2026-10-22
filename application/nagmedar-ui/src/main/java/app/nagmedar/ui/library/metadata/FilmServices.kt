package app.nagmedar.ui.library.metadata

import app.nagmedar.ui.podcast.executeCancellable
import kotlinx.coroutines.delay
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Film information: a film or series TMDB found for a search. */
data class TmdbHit(
    val id: Long,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val overview: String?,
    val posterPath: String?,
)

/** One actor of a film or series, and whom they play. */
data class CastMember(val name: String, val character: String)

/** Film information: TMDB's details of a film or series. */
data class TmdbDetails(
    val id: Long,
    val imdbId: String?,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val overview: String?,
    val genres: List<String>,
    val runtimeMinutes: Int?,
    val rating: Double?,
    val votes: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val cast: List<CastMember>,
    val seasonCount: Int?,
    val episodeCount: Int?,
)

/** Film information: one episode of a season as TMDB lists it. */
data class TmdbEpisode(
    val season: Int,
    val episode: Int,
    val title: String?,
    val overview: String?,
    val stillPath: String?,
    val airDate: String?,
    val runtimeMinutes: Int?,
)

/** OMDb's ratings of a film or series: IMDb's (out of 10) with its votes, and the Rotten Tomatoes score (percent). */
data class OmdbRatings(val imdbRating: Double?, val imdbVotes: Int?, val rottenTomatoes: Int?)

/** Why a film information service did not answer. */
sealed class FilmServiceException(message: String) : IOException(message) {
    /** The key was refused (401): the build's key is wrong or revoked. */
    class KeyRefused : FilmServiceException("key refused")

    /** Too many requests (429), or OMDb's day is used up. */
    class Limited : FilmServiceException("rate limited")
}

/**
 * Film information: TMDB's API (v3), with Nağmedar's own [key]: a v3 API key goes in the query, a
 * v4 read access token (which has dots) in the Authorization header. Only titles, years and TMDB
 * ids are sent. Texts come in [language] ("tr-TR", "en-US"). A 429 waits as long as TMDB asks, once.
 */
class TmdbClient(private val key: String, private val userAgent: String, private val client: OkHttpClient = defaultClient) {

    suspend fun searchMovies(title: String, year: Int?, language: String): List<TmdbHit> =
        get("search/movie", language, "query" to title, "year" to year?.toString(), "include_adult" to "false")?.let(::hits).orEmpty()

    suspend fun searchSeries(name: String, year: Int?, language: String): List<TmdbHit> =
        get("search/tv", language, "query" to name, "first_air_date_year" to year?.toString(), "include_adult" to "false")?.let(::hits).orEmpty()

    suspend fun movie(id: Long, language: String): TmdbDetails? =
        get("movie/$id", language, "append_to_response" to "credits")?.let { details(it, series = false) }

    suspend fun series(id: Long, language: String): TmdbDetails? =
        get("tv/$id", language, "append_to_response" to "credits,external_ids")?.let { details(it, series = true) }

    suspend fun season(id: Long, season: Int, language: String): List<TmdbEpisode> =
        get("tv/$id/season/$season", language)?.optJSONArray("episodes")?.let { episodes ->
            objects(episodes).mapNotNull { e ->
                val number = e.optInt("episode_number", -1).takeIf { it >= 0 } ?: return@mapNotNull null
                TmdbEpisode(
                    season = e.optInt("season_number", season),
                    episode = number,
                    title = e.text("name"),
                    overview = e.text("overview"),
                    stillPath = e.text("still_path"),
                    airDate = e.text("air_date"),
                    runtimeMinutes = e.optInt("runtime", 0).takeIf { it > 0 },
                )
            }
        }.orEmpty()

    /** The answer of [path] as JSON; null for a 404 (no such film) or an answer that is not JSON. */
    private suspend fun get(path: String, language: String, vararg query: Pair<String, String?>): JSONObject? {
        val url = BASE.toHttpUrl().newBuilder().addPathSegments(path).apply {
            addQueryParameter("language", language)
            query.forEach { (name, value) -> if (value != null) addQueryParameter(name, value) }
            if (!bearer) addQueryParameter("api_key", key)
        }.build()
        repeat(2) { attempt ->
            val (code, body, wait) = call(url)
            when {
                code == 429 && attempt == 0 -> delay(wait.coerceIn(1, 30) * 1000L)
                code == 429 -> throw FilmServiceException.Limited()
                code == 401 -> throw FilmServiceException.KeyRefused()
                code == 404 -> return null
                code !in 200..299 -> throw IOException("TMDB $code")
                else -> return try {
                    JSONObject(body)
                } catch (e: JSONException) {
                    null
                }
            }
        }
        return null
    }

    private suspend fun call(url: HttpUrl): Triple<Int, String, Long> {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "application/json")
            .apply { if (bearer) header("Authorization", "Bearer $key") }
            .build()
        return client.newCall(request).executeCancellable { response ->
            Triple(response.code, response.body?.string().orEmpty(), response.header("Retry-After")?.toLongOrNull() ?: 2L)
        }
    }

    private val bearer: Boolean get() = '.' in key

    private fun hits(json: JSONObject): List<TmdbHit> = json.optJSONArray("results")?.let(::objects).orEmpty().mapNotNull { r ->
        val id = r.optLong("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
        TmdbHit(
            id = id,
            title = r.text("title") ?: r.text("name") ?: return@mapNotNull null,
            originalTitle = r.text("original_title") ?: r.text("original_name"),
            year = yearOf(r.text("release_date") ?: r.text("first_air_date")),
            overview = r.text("overview"),
            posterPath = r.text("poster_path"),
        )
    }

    private fun details(json: JSONObject, series: Boolean): TmdbDetails? {
        val id = json.optLong("id", 0).takeIf { it > 0 } ?: return null
        val runtime = if (series) {
            json.optJSONArray("episode_run_time")?.let { times -> (0 until times.length()).map { times.optInt(it) }.firstOrNull { it > 0 } }
        } else {
            json.optInt("runtime", 0).takeIf { it > 0 }
        }
        val cast = json.optJSONObject("credits")?.optJSONArray("cast")?.let(::objects).orEmpty()
            .sortedBy { it.optInt("order", Int.MAX_VALUE) }
            .mapNotNull { c -> c.text("name")?.let { CastMember(it, c.text("character").orEmpty()) } }
            .take(CAST_KEPT)
        return TmdbDetails(
            id = id,
            imdbId = (json.text("imdb_id") ?: json.optJSONObject("external_ids")?.text("imdb_id"))?.takeIf { IMDB_ID.matches(it) },
            title = json.text("title") ?: json.text("name") ?: return null,
            originalTitle = json.text("original_title") ?: json.text("original_name"),
            year = yearOf(json.text("release_date") ?: json.text("first_air_date")),
            overview = json.text("overview"),
            genres = json.optJSONArray("genres")?.let(::objects).orEmpty().mapNotNull { it.text("name") },
            runtimeMinutes = runtime,
            rating = json.optDouble("vote_average", 0.0).takeIf { it > 0 && json.optInt("vote_count", 0) > 0 },
            votes = json.optInt("vote_count", 0).takeIf { it > 0 },
            posterPath = json.text("poster_path"),
            backdropPath = json.text("backdrop_path"),
            cast = cast,
            seasonCount = if (series) json.optInt("number_of_seasons", 0).takeIf { it > 0 } else null,
            episodeCount = if (series) json.optInt("number_of_episodes", 0).takeIf { it > 0 } else null,
        )
    }

    companion object {
        private const val BASE = "https://api.themoviedb.org/3/"

        /** Where TMDB's images are, at a size of its list ("w342", "w780", "original"). */
        fun imageUrl(size: String, path: String): String = "https://image.tmdb.org/t/p/$size$path"

        const val CAST_KEPT = 12

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        }
    }
}

/**
 * Film information: OMDb's API, with Nağmedar's own [key], asked by IMDb id only (never a title or a
 * path): the IMDb rating and votes, and the Rotten Tomatoes score from its "Ratings". The free key
 * allows 1 000 requests a day; the caller counts them.
 */
class OmdbClient(private val key: String, private val userAgent: String, private val client: OkHttpClient = TmdbClient.defaultClient) {

    suspend fun ratings(imdbId: String): OmdbRatings? {
        if (!IMDB_ID.matches(imdbId)) return null
        val url = "https://www.omdbapi.com/".toHttpUrl().newBuilder().addQueryParameter("i", imdbId).addQueryParameter("apikey", key).build()
        val request = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "application/json").build()
        val (code, body) = client.newCall(request).executeCancellable { it.code to it.body?.string().orEmpty() }
        if (code == 401) {
            // a refused key and a day used up both answer 401; the text tells them apart
            if (body.contains("limit", ignoreCase = true)) throw FilmServiceException.Limited()
            throw FilmServiceException.KeyRefused()
        }
        if (code !in 200..299) throw IOException("OMDb $code")
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            return null
        }
        if (!json.optString("Response").equals("True", ignoreCase = true)) return null
        return parseOmdb(json)
    }
}

/** OMDb's answer as ratings: "7.8", "1,234,567" and "87%"; "N/A" is no value. */
internal fun parseOmdb(json: JSONObject): OmdbRatings {
    val imdb = json.text("imdbRating")?.toDoubleOrNull()?.takeIf { it > 0 }
    val votes = json.text("imdbVotes")?.replace(",", "")?.toIntOrNull()
    val tomatoes = json.optJSONArray("Ratings")?.let(::objects).orEmpty()
        .firstOrNull { it.optString("Source").equals("Rotten Tomatoes", ignoreCase = true) }
        ?.text("Value")?.removeSuffix("%")?.trim()?.toIntOrNull()?.takeIf { it in 0..100 }
    return OmdbRatings(imdb, votes, tomatoes)
}

private val IMDB_ID = Regex("""tt\d{5,10}""")

private fun objects(array: JSONArray): List<JSONObject> = (0 until array.length()).mapNotNull { array.optJSONObject(it) }

/** A text field, null when missing, empty, JSON's null or OMDb's "N/A". */
private fun JSONObject.text(name: String): String? =
    if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() && it != "N/A" }

private fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()?.takeIf { it in 1870..2200 }
