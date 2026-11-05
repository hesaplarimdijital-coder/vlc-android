package app.nagmedar.ui.auto

import app.nagmedar.data.DownloadState
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.music.SmartList
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.podcast.ChartGenre
import app.nagmedar.ui.podcast.ChartShow
import app.nagmedar.ui.podcast.DirectoryFailure
import app.nagmedar.ui.podcast.FeedFailure
import app.nagmedar.ui.podcast.ParsedEpisode
import app.nagmedar.ui.podcast.ParsedFeed
import app.nagmedar.ui.podcast.PodcastHit
import app.nagmedar.ui.radio.DirectoryCountry
import app.nagmedar.ui.radio.DirectoryLanguage
import app.nagmedar.ui.radio.DirectoryTag
import app.nagmedar.ui.radio.RadioFailure
import app.nagmedar.ui.radio.RadioLabels
import app.nagmedar.ui.radio.Station
import app.nagmedar.ui.radio.StationOrder
import app.nagmedar.ui.radio.StationQuery
import app.nagmedar.ui.stats.TrackStats
import java.util.Locale

/** 1.0: Android Auto's catalog over fixed data: the music, the podcasts and the radio of a test. */
class FakeCarData(
    var tracks: List<Track> = emptyList(),
    var recent: List<Track> = emptyList(),
    var playlists: Map<PlaylistSummary, List<Track>> = emptyMap(),
    /** 1.0.3: the smart lists, and the ratings and plays they read (by the track's uri) at [now]. */
    var smart: List<SmartList> = emptyList(),
    var stats: Map<String, TrackStats> = emptyMap(),
    var now: Long = 100L * DAY_MS,
    var feeds: List<PodcastFeed> = emptyList(),
    var episodes: List<PodcastEpisode> = emptyList(),
    /** The episodes in the order they were last listened to, the latest first. */
    var listened: List<Long> = emptyList(),
    /** 1.0.3: the episodes in the order the app noted them as they played, the latest first (ListenedEpisodes). */
    var heard: List<Long> = emptyList(),
    /** 1.0.3: Keşfet: the device's country, Apple's charts by country, the directory's shows, the feeds not followed by address. */
    var country: String = "tr",
    var charts: Map<String, List<ChartShow>> = emptyMap(),
    var shows: List<PodcastHit> = emptyList(),
    var feedsRead: Map<String, ParsedFeed> = emptyMap(),
    /** 1.0.3: the podcast directory cannot be reached. */
    var podcastsOffline: Boolean = false,
    var favourites: List<Station> = emptyList(),
    var recentStations: List<Station> = emptyList(),
    var tags: List<DirectoryTag> = emptyList(),
    /** The directory's stations: a list's are those of its country, language or tag, in its order. */
    var directory: List<Station> = emptyList(),
    /** 1.0.3: the directory's countries and languages; the device's country. */
    var radioCountries: List<DirectoryCountry> = emptyList(),
    var languages: List<DirectoryLanguage> = emptyList(),
    var homeCountry: String? = "TR",
    /** The directory cannot be reached; 1.0.3: it asks to wait. */
    var offline: Boolean = false,
    var busy: Boolean = false,
    /** The medialibrary does not answer in time. */
    var musicUnavailable: Boolean = false,
) : CarData {
    var directoryAsked = 0

    /** The words the directory was asked for stations by, in order. */
    val stationQueries = ArrayList<String>()

    override suspend fun music() = medialibrary { MusicLibrary(tracks, Locale.ROOT) }

    override suspend fun recentTracks() = medialibrary { recent }

    override suspend fun playlists() = medialibrary { playlists.keys.toList() }

    override suspend fun playlistTracks(id: Long) = medialibrary { playlists.entries.firstOrNull { it.key.id == id }?.value.orEmpty() }

    override suspend fun smartLists() = smart

    /** As the app chooses them: the music's tracks a list's rules take, in its order. */
    override suspend fun smartTracks(list: SmartList) = medialibrary { list.select(tracks, stats, now, Locale.ROOT) }

    private fun <T> medialibrary(answer: () -> T): T {
        if (musicUnavailable) throw MusicUnavailable()
        return answer()
    }

    override suspend fun feeds() = feeds.sortedBy { it.title }

    override suspend fun episodes(feedId: Long) = episodes.filter { it.feedId == feedId }.sortedByDescending { it.publishedAt }

    override suspend fun episode(id: Long) = episodes.firstOrNull { it.id == id }

    override suspend fun continuing() = CarPodcasts.continuing(episodes, heard) { listened.indexOf(it.id).takeIf { i -> i >= 0 } }

    /** The countries whose chart was asked for, in order; the feeds read, in order. */
    val chartsAsked = ArrayList<String>()
    val feedsAsked = ArrayList<String>()

    override fun country() = country

    override suspend fun chart(country: String): List<ChartShow> {
        chartsAsked += country
        if (podcastsOffline) throw DirectoryFailure.Offline()
        return charts[country].orEmpty()
    }

    /** As the directory's search: the title has the words, in any case. */
    override suspend fun findShows(query: String): List<PodcastHit> {
        if (podcastsOffline) throw DirectoryFailure.Offline()
        return shows.filter { it.title.contains(query, ignoreCase = true) }
    }

    override suspend fun readFeed(url: String): ParsedFeed {
        feedsAsked += url
        if (podcastsOffline) throw FeedFailure.Offline()
        return feedsRead[url] ?: throw FeedFailure.Server(404)
    }

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

    override fun homeCountry() = homeCountry

    override suspend fun radioCountries(): List<DirectoryCountry> = directory { radioCountries }

    override suspend fun languages(): List<DirectoryLanguage> = directory { languages }

    /** 1.0.3: the queries the directory was asked for stations by, in order. */
    val listQueries = ArrayList<StationQuery>()

    /**
     * 1.0.3: as radio-browser's search: the stations of the country, the language and the tag asked for (exactly, the
     * country in any case), most listened first (their [Station.bitrate] stands for the clicks here: the fixtures have
     * none) or most voted first, the page asked for; the kept ones with their marks.
     */
    override suspend fun stations(query: StationQuery): List<Station> = directory {
        listQueries += query
        val filters = query.filters
        val matching = directory.filter { station ->
            (filters.countryCode == null || station.countryCode.equals(filters.countryCode, ignoreCase = true)) &&
                (filters.language == null || station.language == filters.language) &&
                (filters.tag == null || filters.tag in station.tags)
        }
        val ordered = when (query.order) {
            StationOrder.Votes -> matching.sortedByDescending { it.votes }
            else -> matching.sortedByDescending { it.bitrate }
        }
        ordered.drop(query.offset).take(query.limit).map(::marked)
    }

    /** As radio-browser's name search: the name has the words as they are, in any case; nothing is folded ("ş" is no "s"). */
    override suspend fun findStations(query: String): List<Station> = directory {
        stationQueries += query
        directory.filter { it.name.contains(query, ignoreCase = true) }.map(::marked)
    }

    /** 1.0.3: the stations the app keeps: the favourites and those played, by their key. */
    private fun kept(): Map<String, Station> = (favourites + recentStations).associateBy { it.key }

    private fun marked(station: Station) = station.withMarks(kept()[station.key])

    /** 1.0.3: a station of a list, else one kept. */
    override suspend fun knownStation(uuid: String): Station? =
        directory.firstOrNull { it.uuid == uuid }?.let(::marked) ?: kept().values.firstOrNull { it.uuid == uuid }

    /** 1.0.3: the favourites made, in order: the station and on or off. */
    val favouritesMade = ArrayList<Pair<Station, Boolean>>()

    /** 1.0.3: as the Radio page's library: kept (a row of its own), its favourite mark set; the favourites by name. */
    override suspend fun favourite(station: Station, on: Boolean): Station {
        favouritesMade += station to on
        val known = kept()[station.key]
        val made = (known ?: station).copy(id = known?.id ?: (100L + favouritesMade.size), favourite = on)
        favourites = (favourites.filter { it.key != station.key } + listOfNotNull(made.takeIf { on })).sortedBy { it.name }
        recentStations = recentStations.map { if (it.key == station.key) made else it }
        return made
    }

    private fun <T> directory(answer: () -> T): T {
        directoryAsked++
        if (busy) throw RadioFailure.Busy(60_000)
        if (offline) throw RadioFailure.Offline()
        return answer()
    }
}

/** A day, in ms: the smart lists' clock. */
const val DAY_MS = 86_400_000L

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

    override val locale: Locale = Locale.ROOT

    /** "country:tr". */
    override fun country(code: String, name: String?) = "country:$code"

    /** As the app names it, in English: "Turkish". */
    override fun language(name: String, code: String?) = RadioLabels.language(name, code, Locale.ENGLISH)

    /** Apple's English name, as the app shows one it does not know. */
    override fun category(genre: ChartGenre) = genre.name
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

/** 1.0.3: a show of a directory, not followed: its feed at example.com. */
fun hit(n: Int, title: String, author: String? = "Yapımcı $n") = PodcastHit(
    feedUrl = "https://example.com/feed/$n.xml",
    title = title,
    author = author,
    imageUrl = "https://example.com/art/$n.jpg",
)

/** 1.0.3: a show of Apple's chart in [genres] (id to name). */
fun chartShow(hit: PodcastHit, vararg genres: Pair<Int, String>) = ChartShow(hit, genres.map { ChartGenre(it.first, it.second) })

/** 1.0.3: a feed as it was read: [episodes] guids "g1", "g2"…, the newest first. */
fun parsedFeed(title: String, episodes: Int = 2, image: String? = "https://example.com/feed-art.jpg") = ParsedFeed(
    title = title,
    author = "Yayıncı",
    imageUrl = image,
    episodes = (episodes downTo 1).map { n ->
        ParsedEpisode(guid = "g$n", title = "$title $n", audioUrl = "https://example.com/audio/$n.mp3", durationMs = 1_800_000, publishedAt = n * 1_000_000L)
    },
)

fun station(
    id: Long,
    name: String,
    uuid: String? = "uuid-$id",
    tags: List<String> = listOf("pop"),
    countryCode: String? = null,
    language: String? = null,
    /** 1.0.3: how often it was listened to (the fake directory orders by it), and its votes. */
    clicks: Int = 0,
    votes: Int = 0,
    favourite: Boolean = false,
) = Station(
    uuid = uuid,
    name = name,
    streamUrl = "https://radio.example.org/$id",
    id = id,
    logoUrl = "https://radio.example.org/logo/$id.png",
    countryCode = countryCode,
    country = "Türkiye",
    language = language,
    tags = tags,
    bitrate = clicks,
    votes = votes,
    favourite = favourite,
)
