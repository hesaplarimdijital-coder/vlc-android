package app.nagmedar.ui.covers

import app.nagmedar.ui.lyrics.RequestPace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * S-1006: Cover Art Archive's front covers, 1200 pixels wide: the release the tags name, its
 * release group, else the release group MusicBrainz finds by the album's title and artist. Every
 * request says who asks ([userAgent], as MusicBrainz asks) and waits its turn ([pace]: MusicBrainz
 * takes one a second).
 */
class CoverArtArchive(
    private val userAgent: String,
    private val pace: RequestPace = RequestPace(spacingMs = 1_100, perWindow = 30),
    private val client: OkHttpClient = defaultClient,
    private val musicBrainz: String = MUSICBRAINZ_URL,
    private val archive: String = ARCHIVE_URL,
) : CoverOnline {

    override suspend fun front(query: CoverQuery): ByteArray? = withContext(Dispatchers.IO) {
        query.ids.release?.let { release -> image(CoverArt.frontUrl(archive, "release", release))?.let { return@withContext it } }
        val group = query.ids.releaseGroup ?: search(query) ?: return@withContext null
        image(CoverArt.frontUrl(archive, "release-group", group))
    }

    /** The release group MusicBrainz finds for the album; null when none is sure enough. */
    private suspend fun search(query: CoverQuery): String? {
        val text = call(CoverArt.searchUrl(musicBrainz, query.artist, query.album)) { it.body?.string() } ?: return null
        return CoverArt.releaseGroup(text, query.album)
    }

    private suspend fun image(url: HttpUrl): ByteArray? = call(url) { response ->
        val body = response.body ?: return@call null
        if (body.contentLength() > MAX_BYTES) return@call null
        body.bytes().takeIf { it.size <= MAX_BYTES }
    }

    /** [read] of the answer; null when there is none (404). */
    private suspend fun <T> call(url: HttpUrl, read: (okhttp3.Response) -> T?): T? {
        if (!pace.take()) throw IOException("busy")
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        val response = client.newCall(request).execute()
        return response.use {
            when {
                it.isSuccessful -> {
                    pace.answered()
                    read(it)
                }
                it.code == HTTP_NOT_FOUND -> {
                    pace.answered()
                    null
                }
                it.code == HTTP_UNAVAILABLE || it.code == HTTP_TOO_MANY -> {
                    pace.refused(it.header("Retry-After")?.trim()?.toLongOrNull()?.times(1_000))
                    throw IOException("busy")
                }
                else -> throw IOException("status ${it.code}")
            }
        }
    }

    companion object {
        const val MUSICBRAINZ_URL = "https://musicbrainz.org/ws/2/"
        const val ARCHIVE_URL = "https://coverartarchive.org/"
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_UNAVAILABLE = 503

        /** A cover larger than this is not taken. */
        private const val MAX_BYTES = 12L * 1024 * 1024

        /** "Nagmedar/1.2.0 ( Android music player )": the app and its version, as MusicBrainz asks. */
        fun userAgent(version: String?): String = "Nagmedar/${version?.takeIf { it.isNotBlank() } ?: "1.0"} ( Android music player )"

        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}

/** MusicBrainz's and Cover Art Archive's addresses and answers. */
object CoverArt {
    /** How sure MusicBrainz must be of a release group (0–100). */
    private const val MIN_SCORE = 90

    /** "release-group/<id>/front-1200": the front cover, 1200 pixels wide (the archive scales it). */
    fun frontUrl(base: String, kind: String, id: String): HttpUrl =
        base.toHttpUrl().newBuilder().addPathSegment(kind).addPathSegment(id).addPathSegment("front-1200").build()

    /** The release groups of [album] by [artist], MusicBrainz's search syntax quoted. */
    fun searchUrl(base: String, artist: String, album: String): HttpUrl = base.toHttpUrl().newBuilder()
        .addPathSegment("release-group")
        .addPathSegment("")
        .addQueryParameter("query", "releasegroup:${quoted(album)} AND artist:${quoted(artist)}")
        .addQueryParameter("fmt", "json")
        .addQueryParameter("limit", "5")
        .build()

    /** The best release group of the search's [text] whose title is [album]'s; null when none is sure enough. */
    fun releaseGroup(text: String, album: String): String? {
        val groups = try {
            JSONObject(text).optJSONArray("release-groups")
        } catch (e: JSONException) {
            throw IOException("garbled", e)
        } ?: return null
        val wanted = fold(album)
        return (0 until groups.length()).mapNotNull { groups.optJSONObject(it) }
            .filter { it.optInt("score", 0) >= MIN_SCORE && fold(it.optString("title")) == wanted }
            .maxByOrNull { it.optInt("score", 0) }
            ?.optString("id")?.takeIf { it.isNotBlank() }
    }

    /** A Lucene phrase: in quotes, its quotes and backslashes escaped. */
    private fun quoted(text: String): String = "\"" + text.trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** A title as compared: lower case, without accents or spaces at the ends. */
    private fun fold(text: String): String =
        Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(MARKS, "").replace('\u0131', 'i')

    private val MARKS = Regex("\\p{M}+")
}
