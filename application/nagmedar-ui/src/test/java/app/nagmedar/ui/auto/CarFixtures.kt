package app.nagmedar.ui.auto

import app.nagmedar.data.DownloadState
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.radio.DirectoryTag
import app.nagmedar.ui.radio.RadioFailure
import app.nagmedar.ui.radio.Station
import java.util.Locale

/** 1.0: Android Auto's catalog over fixed data: the music, the podcasts and the radio of a test. */
class FakeCarData(
    var tracks: List<Track> = emptyList(),
    var recent: List<Track> = emptyList(),
    var playlists: Map<PlaylistSummary, List<Track>> = emptyMap(),
    var feeds: List<PodcastFeed> = emptyList(),
    var episodes: List<PodcastEpisode> = emptyList(),
    /** The episodes in the order they were last listened to, the latest first. */
    var listened: List<Long> = emptyList(),
    var favourites: List<Station> = emptyList(),
    var recentStations: List<Station> = emptyList(),
    var tags: List<DirectoryTag> = emptyList(),
    var tagged: Map<String, List<Station>> = emptyMap(),
    var directory: List<Station> = emptyList(),
    /** The directory cannot be reached. */
    var offline: Boolean = false,
) : CarData {
    var directoryAsked = 0

    override suspend fun music() = MusicLibrary(tracks, Locale.ROOT)

    override suspend fun recentTracks() = recent

    override suspend fun playlists() = playlists.keys.toList()

    override suspend fun playlistTracks(id: Long) = playlists.entries.firstOrNull { it.key.id == id }?.value.orEmpty()

    override suspend fun feeds() = feeds.sortedBy { it.title }

    override suspend fun episodes(feedId: Long) = episodes.filter { it.feedId == feedId }.sortedByDescending { it.publishedAt }

    override suspend fun episode(id: Long) = episodes.firstOrNull { it.id == id }

    override suspend fun continuing() = CarPodcasts.continuing(episodes) { listened.indexOf(it.id).takeIf { i -> i >= 0 } }

    override suspend fun fresh(): List<PodcastEpisode> {
        val subscribed = feeds.associate { it.id to it.subscribedAt }
        return episodes.filter { !it.played && it.addedAt > (subscribed[it.feedId] ?: 0) }.sortedByDescending { it.publishedAt }
    }

    override suspend fun findEpisodes(query: String, limit: Int) =
        episodes.filter { CarVoice.score(query, it.title) >= CarVoice.WORDS }.sortedByDescending { CarVoice.score(query, it.title) }.take(limit)

    override suspend fun favouriteStations() = favourites

    override suspend fun recentStations() = recentStations

    override suspend fun station(id: Long) = (favourites + recentStations).firstOrNull { it.id == id }

    override suspend fun tags(): List<DirectoryTag> = directory { tags }

    override suspend fun tagged(tag: String): List<Station> = directory { tagged[tag].orEmpty() }

    override suspend fun findStations(query: String): List<Station> = directory {
        directory.filter { CarVoice.score(query, it.name) >= CarVoice.PART }
    }

    private fun <T> directory(answer: () -> T): T {
        directoryAsked++
        if (offline) throw RadioFailure.Offline()
        return answer()
    }
}

/** Texts are their names, counts "3 Tracks", icons "icon:Music", covers "cover:<uri>": what the tests compare. */
object FakeCarLook : CarLook {
    override fun text(text: CarText) = text.name

    override fun count(count: CarCount, n: Int) = "$n $count"

    override fun decade(start: Int) = "${start}s"

    override fun date(ms: Long) = if (ms <= 0) null else "day:$ms"

    override fun icon(icon: CarIcon) = "icon:$icon"

    override fun cover(track: Track?) = track?.let { "cover:${it.uri}" } ?: icon(CarIcon.NoCover)

    override fun playlistCover(id: Long) = "playlist:$id"

    override fun webImage(url: String?) = url?.let { "web:$it" } ?: icon(CarIcon.NoCover)
}

/** A track of [album] by [artist], in [folder]. */
fun track(
    id: Long,
    title: String,
    artist: String = "Sezen Aksu",
    album: String? = "Gülümse",
    year: Int? = 1991,
    number: Int = id.toInt(),
    genre: String? = "Pop",
    folder: String = "/storage/emulated/0/Music/$artist",
) = Track(
    id = id,
    uri = "file:///music/$id.flac",
    title = title,
    artist = artist,
    albumArtist = artist,
    album = album,
    genre = genre,
    year = year,
    trackNumber = number,
    discNumber = 1,
    durationMs = 200_000,
    folder = folder,
)

fun feed(id: Long, title: String, subscribedAt: Long = 1_000) = PodcastFeed(
    id = id,
    feedUrl = "https://example.org/feed/$id",
    title = title,
    author = "Yayıncı $id",
    imageUrl = "https://example.org/cover/$id.jpg",
    subscribedAt = subscribedAt,
)

fun episode(
    id: Long,
    feedId: Long,
    title: String,
    published: Long = id * 1_000_000,
    positionMs: Long = 0,
    durationMs: Long = 3_600_000,
    played: Boolean = false,
    addedAt: Long = 1_000,
    downloaded: Boolean = false,
) = PodcastEpisode(
    id = id,
    feedId = feedId,
    guid = "guid-$id",
    title = title,
    audioUrl = "https://example.org/audio/$id.mp3",
    durationMs = durationMs,
    publishedAt = published,
    positionMs = positionMs,
    played = played,
    addedAt = addedAt,
    downloadState = if (downloaded) DownloadState.DONE else DownloadState.NONE,
    downloadPath = if (downloaded) "/data/podcasts/$id.mp3" else null,
)

fun station(id: Long, name: String, uuid: String? = "uuid-$id", tags: List<String> = listOf("pop")) = Station(
    uuid = uuid,
    name = name,
    streamUrl = "https://radio.example.org/$id",
    id = id,
    logoUrl = "https://radio.example.org/logo/$id.png",
    country = "Türkiye",
    tags = tags,
)
