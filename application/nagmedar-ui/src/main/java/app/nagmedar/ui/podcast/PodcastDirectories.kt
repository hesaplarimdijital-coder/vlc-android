package app.nagmedar.ui.podcast

import androidx.compose.runtime.Immutable
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

/** Which directory a search result came from. */
enum class HitSource { PodcastIndex, ITunes }

/** S-1101: a show a directory lists: enough to show it in the results and to subscribe to it. */
@Immutable
data class PodcastHit(
    val feedUrl: String,
    val title: String,
    val author: String? = null,
    val imageUrl: String? = null,
    /** How many episodes the directory counts; null when it does not say. */
    val episodeCount: Int? = null,
    val description: String? = null,
    /** Podcast Index's id of the show; kept with the subscription. */
    val podcastIndexId: Long? = null,
    val source: HitSource = HitSource.PodcastIndex,
)

/** Why a directory could not answer. */
sealed class DirectoryFailure(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** No network, or the server could not be reached. */
    class Offline(cause: Throwable? = null) : DirectoryFailure("offline", cause)

    /** The service asked to wait (429, 503), or its wait still lasts. */
    class Busy(val waitMs: Long = 0) : DirectoryFailure("busy")

    /** Another answer: its HTTP status (401 for keys the service refuses, or a clock far off). */
    class Server(val status: Int) : DirectoryFailure("status $status")
}

/** S-1101: a podcast directory: Podcast Index and iTunes in the app, fakes in the tests. */
interface PodcastDirectory {
    /** Whether this build can ask it: Podcast Index needs the keys the build was given. */
    val available: Boolean

    /** The shows for [term], the directory's best first. Throws [DirectoryFailure]. */
    suspend fun search(term: String, limit: Int = SEARCH_LIMIT): List<PodcastHit>

    companion object {
        const val SEARCH_LIMIT = 30
    }
}

/**
 * Podcast Index's request headers (podcastindex.org/docs/api-pindex): `X-Auth-Date` is the time in
 * Unix seconds, `Authorization` the SHA-1, in hex, of the key, the secret and that time written one
 * after the other, `X-Auth-Key` the key, and `User-Agent` says who asks.
 */
object PodcastIndexAuth {
    fun headers(key: String, secret: String, userAgent: String, epochSeconds: Long): Map<String, String> = mapOf(
        "User-Agent" to userAgent,
        "X-Auth-Key" to key,
        "X-Auth-Date" to epochSeconds.toString(),
        "Authorization" to sha1Hex(key + secret + epochSeconds),
    )

    fun sha1Hex(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** Podcast Index's answers, read. */
object PodcastIndexJson {
    /** `search/byterm`'s shows; those marked dead and those with no feed address are left out. */
    fun hits(text: String): List<PodcastHit> {
        val feeds = try {
            JSONObject(text).optJSONArray("feeds")
        } catch (e: JSONException) {
            throw DirectoryFailure.Server(HTTP_OK_BAD_BODY)
        } ?: return emptyList()
        return (0 until feeds.length()).mapNotNull { feeds.optJSONObject(it)?.let(::hit) }
    }

    /** `podcasts/byfeedurl`'s show; null when it lists none. */
    fun byFeedUrl(text: String): PodcastHit? = try {
        JSONObject(text).optJSONObject("feed")?.let(::hit)
    } catch (e: JSONException) {
        null
    }

    private fun hit(feed: JSONObject): PodcastHit? {
        if (feed.optInt("dead", 0) != 0) return null
        val url = feed.text("url") ?: return null
        val title = feed.text("title") ?: return null
        return PodcastHit(
            feedUrl = url,
            title = HtmlText.line(title) ?: return null,
            author = HtmlText.line(feed.text("author") ?: feed.text("ownerName")),
            imageUrl = (feed.text("image") ?: feed.text("artwork"))?.takeIf { it.startsWith("http", ignoreCase = true) },
            episodeCount = feed.optInt("episodeCount", -1).takeIf { it >= 0 },
            description = HtmlText.plain(feed.text("description"), limit = 600),
            podcastIndexId = feed.optLong("id", -1).takeIf { it > 0 },
            source = HitSource.PodcastIndex,
        )
    }

    /** The string at [name]; null when it is absent, null or empty. */
    private fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() }

    /** A 200 whose body is not JSON. */
    private const val HTTP_OK_BAD_BODY = 200
}

/** iTunes Search's answers, read. */
object ITunesJson {
    fun hits(text: String): List<PodcastHit> {
        val results = try {
            JSONObject(text).optJSONArray("results")
        } catch (e: JSONException) {
            throw DirectoryFailure.Server(HTTP_OK_BAD_BODY)
        } ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val feed = item.optString("feedUrl").trim().takeIf { it.isNotEmpty() && !item.isNull("feedUrl") } ?: return@mapNotNull null
            val title = HtmlText.line(item.optString("collectionName").ifEmpty { item.optString("trackName") }) ?: return@mapNotNull null
            PodcastHit(
                feedUrl = feed,
                title = title,
                author = HtmlText.line(item.optString("artistName")),
                imageUrl = listOf("artworkUrl600", "artworkUrl100", "artworkUrl60").firstNotNullOfOrNull { name -> item.optString(name).takeIf { it.startsWith("http") } },
                episodeCount = item.optInt("trackCount", -1).takeIf { it >= 0 },
                source = HitSource.ITunes,
            )
        }
    }

    private const val HTTP_OK_BAD_BODY = 200
}

/** What the search has to say about where its results came from. */
enum class SearchNotice {
    /** This build has no Podcast Index keys: the results are iTunes'. */
    PodcastIndexMissing,

    /** Podcast Index did not answer: the results are iTunes'. */
    PodcastIndexFailed,
}

/** The result of a search: the shows, and the [notice] when Podcast Index had no part in them. */
@Immutable
data class SearchOutcome(val hits: List<PodcastHit>, val notice: SearchNotice? = null)

/**
 * S-1101: the search across the directories, Podcast Index first and iTunes second. Podcast Index
 * is asked when the build has its keys; iTunes when it has not, when Podcast Index failed, and when
 * Podcast Index found fewer than [THIN] shows (iTunes knows shows it does not). Results are merged
 * by feed address ([merge]). Fails only when no directory could answer.
 */
class PodcastSearch(
    private val primary: PodcastDirectory,
    private val secondary: PodcastDirectory,
) {
    /** Whether Podcast Index will be asked; false in a build made without its keys. */
    val primaryAvailable: Boolean get() = primary.available

    suspend fun search(term: String): SearchOutcome {
        var first: List<PodcastHit> = emptyList()
        var failed = false
        if (primary.available) {
            try {
                first = primary.search(term)
            } catch (e: DirectoryFailure) {
                failed = true
            }
            if (!failed && first.size >= THIN) return SearchOutcome(first)
        }
        val notice = when {
            !primary.available -> SearchNotice.PodcastIndexMissing
            failed -> SearchNotice.PodcastIndexFailed
            else -> null
        }
        val second = try {
            secondary.search(term)
        } catch (e: DirectoryFailure) {
            // the second directory failing costs only what it would have added
            if (first.isNotEmpty()) return SearchOutcome(first)
            throw e
        }
        return SearchOutcome(merge(first, second), notice)
    }

    companion object {
        /** Fewer shows than this from Podcast Index and iTunes is asked as well. */
        const val THIN = 8

        /**
         * [first] then the shows of [second] it lacks, by feed address ([FeedUrl.key]); where both list
         * a show, the first's entry stays and takes what it lacks (cover, author, count, Podcast Index
         * id) from the second's.
         */
        fun merge(first: List<PodcastHit>, second: List<PodcastHit>): List<PodcastHit> {
            val merged = LinkedHashMap<String, PodcastHit>()
            (first + second).forEach { hit ->
                val key = FeedUrl.key(hit.feedUrl)
                val known = merged[key]
                merged[key] = if (known == null) {
                    hit
                } else {
                    known.copy(
                        author = known.author ?: hit.author,
                        imageUrl = known.imageUrl ?: hit.imageUrl,
                        episodeCount = known.episodeCount ?: hit.episodeCount,
                        description = known.description ?: hit.description,
                        podcastIndexId = known.podcastIndexId ?: hit.podcastIndexId,
                    )
                }
            }
            return merged.values.toList()
        }
    }
}
