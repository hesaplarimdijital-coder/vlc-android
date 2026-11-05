package app.nagmedar.ui.auto

import app.nagmedar.data.DownloadState
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.ui.music.MusicGroup
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.music.SmartList
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.playback.PlayFrom
import app.nagmedar.ui.playback.PlaySource
import app.nagmedar.ui.playback.PlaybackRules
import app.nagmedar.ui.podcast.ApplePodcasts
import app.nagmedar.ui.podcast.ChartGenre
import app.nagmedar.ui.podcast.ChartShow
import app.nagmedar.ui.podcast.DirectoryFailure
import app.nagmedar.ui.podcast.FeedFailure
import app.nagmedar.ui.podcast.FeedUrl
import app.nagmedar.ui.podcast.ParsedEpisode
import app.nagmedar.ui.podcast.ParsedFeed
import app.nagmedar.ui.podcast.PodcastHit
import app.nagmedar.ui.podcast.addressKeys
import app.nagmedar.ui.radio.DirectoryCountry
import app.nagmedar.ui.radio.DirectoryLanguage
import app.nagmedar.ui.radio.DirectoryTag
import app.nagmedar.ui.radio.RadioFailure
import app.nagmedar.ui.radio.RadioLabels
import app.nagmedar.ui.radio.Station
import app.nagmedar.ui.radio.StationFilters
import app.nagmedar.ui.radio.StationOrder
import app.nagmedar.ui.radio.StationQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.vlc.media.CarItem
import org.videolan.vlc.media.CarStyle
import java.text.Collator
import java.util.Locale

/**
 * 1.0: what Android Auto's catalog reads: the Music page's library and its playlists, the podcasts in Room, the radio's
 * kept stations and its directory. The app's in [AppCarData], fakes in the tests. Every call is made off the main thread.
 * What the medialibrary answers (the music, the tracks played last, the playlists) throws [MusicUnavailable] when it
 * does not answer in time.
 */
interface CarData {
    /** The Music page's library (the Music section's folders), its groupings made on first use. Throws [MusicUnavailable]. */
    suspend fun music(): MusicLibrary

    /** The music tracks played last, the latest first (the Music page's "Son çalınan müzik" shelf). Throws [MusicUnavailable]. */
    suspend fun recentTracks(): List<Track>

    /** Throws [MusicUnavailable]. */
    suspend fun playlists(): List<PlaylistSummary>

    /** Throws [MusicUnavailable]. */
    suspend fun playlistTracks(id: Long): List<Track>

    /** 1.0.3: the smart lists, in the order they were made (the Playlists page's). */
    suspend fun smartLists(): List<SmartList>

    /**
     * 1.0.3: the tracks [list] takes from the music, with the ratings and plays, as the Playlists page reads them; the
     * same for as long as the music read is kept, so that a track tapped in Auto is the one it showed. Throws
     * [MusicUnavailable].
     */
    suspend fun smartTracks(list: SmartList): List<Track>

    /** The subscriptions, by title. */
    suspend fun feeds(): List<PodcastFeed>

    /** A show's episodes, the newest first. */
    suspend fun episodes(feedId: Long): List<PodcastEpisode>

    suspend fun episode(id: Long): PodcastEpisode?

    /** The episodes left in the middle, not played: the one listened to last first ([CarPodcasts.continuing]). */
    suspend fun continuing(): List<PodcastEpisode>

    /** 1.0.3: the country Keşfet starts from, the device's: a storefront of [ApplePodcasts.COUNTRIES]. */
    fun country(): String

    /** 1.0.3: the most listened shows of the storefront [country] ([ApplePodcasts]), kept a while. Throws [DirectoryFailure]. */
    suspend fun chart(country: String): List<ChartShow>

    /** 1.0.3: the podcast directory's shows for [query], as the Podcasts page's search finds them. Throws [DirectoryFailure]. */
    suspend fun findShows(query: String): List<PodcastHit>

    /** 1.0.3: the feed at [url] read (a show not followed), its episodes the newest first, kept a while. Throws [FeedFailure]. */
    suspend fun readFeed(url: String): ParsedFeed

    /** The new episodes, unplayed and added by a refresh after the subscription, the newest first. */
    suspend fun fresh(): List<PodcastEpisode>

    /** The episodes whose title answers [query] ([CarVoice.score] at least [CarVoice.WORDS]), the best first, at most [limit]. */
    suspend fun findEpisodes(query: String, limit: Int): List<PodcastEpisode>

    suspend fun favouriteStations(): List<Station>

    suspend fun recentStations(): List<Station>

    suspend fun station(id: Long): Station?

    /** The directory's tags, the most used first. Throws [RadioFailure]. */
    suspend fun tags(): List<DirectoryTag>

    /** 1.0.3: the device's country (its mobile network's, its SIM's, the region of its language), an ISO 3166-1 code; null when none is known. */
    fun homeCountry(): String?

    /** 1.0.3: the radio directory's countries with their station counts. Throws [RadioFailure]. */
    suspend fun radioCountries(): List<DirectoryCountry>

    /** 1.0.3: the radio directory's languages with their station counts, the most used. Throws [RadioFailure]. */
    suspend fun languages(): List<DirectoryLanguage>

    /**
     * 1.0.3: the directory's stations for [query] (a country's, a language's, a tag's, the most listened or liked), in
     * the directory's order; those the app keeps carry the user's marks (a favourite, the row). Throws [RadioFailure].
     */
    suspend fun stations(query: StationQuery): List<Station>

    /** The directory's stations whose name has [query]. Throws [RadioFailure]. */
    suspend fun findStations(query: String): List<Station>

    /**
     * 1.0.3: the directory's station [uuid] as the app knows it: as a list of the directory gave it lately (its country,
     * tags, codec), with the user's marks when it is kept; the kept row alone else; null when it knows none.
     */
    suspend fun knownStation(uuid: String): Station?

    /** 1.0.3: [station] made a favourite ([on]) or no longer one, as the Radio page's star does; returns it as kept. */
    suspend fun favourite(station: Station, on: Boolean): Station
}

/** 1.0: the medialibrary did not answer in time: the music cannot be listed now; the podcasts and the radio still can. */
class MusicUnavailable : Exception("the medialibrary did not answer in time")

/** A text of Android Auto's entries. */
enum class CarText {
    Music, Podcasts, Radio, Recent,
    Albums, Artists, Playlists, Years, Folders,
    Genres, AllTracks, ShuffleAll, Smart, UnknownGenre,
    Continue, Subscriptions, NewEpisodes,
    Discover, PopularShows, Countries, Subscribe, Subscribed, FoundNewShows, PodcastsOffline, ShowUnavailable,
    FavouriteStations, RecentStations, Categories,
    Languages, MostListened, MostLiked, MoreStations, NoFavourites, NoRecentStations, NoStations, RadioBusy,
    UnknownAlbum, UnknownArtist, UnknownYear, UnknownFolder,
    FoundTracks, FoundAlbums, FoundArtists, FoundEpisodes, FoundShows, FoundStations,
    Empty, RadioOffline, NoResult, MusicUnavailable,
}

/** What a count counts. */
enum class CarCount { Tracks, Albums, Episodes, Stations, Shows }

/** The icons of Android Auto's tabs and lists. */
enum class CarIcon {
    Music, Podcasts, Radio, Recent, Albums, Artists, Playlists, Years, Folders, Continue, New, Favourites, Categories, NoCover,
    Genres, Tracks, Shuffle, Smart,
    Discover, Popular, Countries, Subscribe,
    Languages, Listened, Liked, More, Star,
}

/** How Android Auto's entries read and look: the texts in the app's language, the artwork's addresses. */
interface CarLook {
    fun text(text: CarText): String

    fun count(count: CarCount, n: Int): String

    /** "1990'lar", "2000'ler", "1990s". */
    fun decade(start: Int): String

    /** A day as the app writes it; null for an unknown one (0). */
    fun date(ms: Long): String?

    fun icon(icon: CarIcon): String

    /** 1.0.3: the language of the texts: lists of names are sorted in it. */
    val locale: Locale

    /** 1.0.3: a country by its two-letter code, in the app's language ("Türkiye"); [name] (a directory's) when the code is not known. */
    fun country(code: String, name: String? = null): String

    /** 1.0.3: a language of the radio directory ([name] as it spells it, [code] its ISO 639 code) in the app's language ("Türkçe"). */
    fun language(name: String, code: String?): String

    /** 1.0.3: one of Apple's podcast categories in the app's language; its own (English) name when it is not known. */
    fun category(genre: ChartGenre): String

    /** The track's cover (its album's), or the placeholder. */
    fun cover(track: Track?): String

    fun playlistCover(id: Long): String

    /** A show's cover or a station's logo, a web address; the placeholder for none. */
    fun webImage(url: String?): String
}

/** What an entry of Android Auto plays. */
sealed interface CarPlay {
    /** A list of tracks from [start]; [source] names it for the phone's player. */
    data class Tracks(val tracks: List<Track>, val start: Int, val source: PlaySource? = null) : CarPlay

    data class Episode(val feed: PodcastFeed, val episode: PodcastEpisode) : CarPlay

    data class Radio(val station: Station) : CarPlay

    /** 1.0.3: the row "Abone ol" of a show not followed: it subscribes to [show] and plays nothing. */
    data class Subscribe(val show: PodcastHit) : CarPlay
}

/** 1.0.3: the custom browse actions of Android Auto's entries. */
object CarActions {
    /** "Abone ol" on a show of the directory not followed. */
    const val SUBSCRIBE = "app.nagmedar.auto.SUBSCRIBE"

    /** "Favorilere ekle" on a station that is no favourite, "Favorilerden çıkar" on one that is: one of the two at a time. */
    const val FAVOURITE = "app.nagmedar.auto.FAVOURITE"
    const val UNFAVOURITE = "app.nagmedar.auto.UNFAVOURITE"
}

/** 1.0: how a decade's name ends in Turkish: "1990'lar", "2000'ler", by the vowel of its last spoken word. */
object CarDecades {
    /**
     * Whether the decade from [start] takes "-ler" (a front vowel): its tens are spoken yirmi, elli, yetmiş, seksen, or
     * there are none and its last word is yüz or bin ("1900'ler", "2000'ler"); on, otuz, kırk, altmış, doksan take "-lar".
     */
    fun frontVowel(start: Int): Boolean = (start / 10 % 10) in setOf(0, 2, 5, 7, 8)
}

/** 1.0: the podcast rules of Android Auto's lists, apart from where the episodes come from. */
object CarPodcasts {
    /**
     * The episodes left in the middle: not played, with a place the episode's page would offer to resume from
     * ([PlaybackRules.shownPositionMs]); the one listened to last first. 1.0.3: [heard] is the order the app noted
     * (the episodes as they were last played, the latest first: [app.nagmedar.ui.podcast.ListenedEpisodes]); those it
     * does not know (played before 1.0.3) follow by [listened] (their place in the playback history, 0 the latest; null
     * when it is not there), then the newest.
     */
    fun continuing(episodes: List<PodcastEpisode>, heard: List<Long> = emptyList(), listened: (PodcastEpisode) -> Int?): List<PodcastEpisode> {
        val rank = HashMap<Long, Int>()
        heard.forEachIndexed { i, id -> rank.putIfAbsent(id, i) }
        return episodes.filter { !it.played && PlaybackRules.shownPositionMs(it.positionMs, it.played, null, durationMs = it.durationMs) > 0 }
            .sortedWith(
                compareBy<PodcastEpisode> { rank[it.id] ?: Int.MAX_VALUE }
                    .thenBy { listened(it) ?: Int.MAX_VALUE }
                    .thenByDescending { it.publishedAt },
            )
    }

    /** What a show plays when it is asked for by name: where it was left, else its newest unplayed episode, else its newest. */
    fun pick(episodes: List<PodcastEpisode>, continuing: List<PodcastEpisode>): PodcastEpisode? {
        val ids = episodes.mapTo(HashSet()) { it.id }
        return continuing.firstOrNull { it.id in ids }
            ?: episodes.filter { !it.played }.maxByOrNull { it.publishedAt }
            ?: episodes.maxByOrNull { it.publishedAt }
    }

    /** How far [episode] was heard, 0 to 1: its place of its length; 1 once played. */
    fun progress(episode: PodcastEpisode): Double = when {
        episode.played -> 1.0
        episode.durationMs <= 0 -> 0.0
        else -> (episode.positionMs.toDouble() / episode.durationMs).coerceIn(0.0, 1.0)
    }
}

/**
 * 1.0: Android Auto's browse tree. The root's four tabs: Müzik (1.0.3: the whole music shuffled at one tap, albums,
 * artists, genres when the music has some, playlists and smart lists, years, the Music section's folders, every track
 * A–Z), Podcast (the episodes left in the middle, the subscriptions, the new episodes; 1.0.3: Keşfet, Apple's popular
 * shows of the device's country, by category and of the other countries, whose shows not followed open on their feed's
 * episodes under a row "Abone ol"), Radyo (the favourite stations, those played last; 1.0.3: the whole world's radio
 * directory by country, by language and by category, its most listened and its most liked stations, each list a page
 * of the most listened first with a last row to the next; every station offers "Favorilere ekle" or "Favorilerden
 * çıkar") and Son çalınanlar (the music played last). Long lists are cut into pages of [pageSize]; an empty list says
 * so in one line, as does music the medialibrary does not give in time ([MusicUnavailable]) and a directory that
 * cannot be reached. Also the search, what an entry plays ([play]), the show a browse action subscribes to
 * ([subscription]) and the station it makes a favourite ([favourite]).
 */
class CarTree(private val data: CarData, private val look: CarLook, private val pageSize: Int = PAGE) {

    /** 1.0.3: the stations a page of the directory's lists shows: [STATION_PAGE], fewer when the tree's pages are smaller. */
    private val stationPage = minOf(pageSize, STATION_PAGE)

    /** The entries under [id]; null when it is none of the tree's. */
    suspend fun children(id: String): List<CarItem>? {
        val ref = CarIds.parse(id) ?: return null
        return try {
            entries(ref)
        } catch (e: MusicUnavailable) {
            listOf(nothing(CarText.MusicUnavailable))
        }
    }

    private suspend fun entries(ref: CarRef): List<CarItem>? {
        return when (ref.kind) {
            null -> when (ref.path) {
                CarIds.ROOT -> root()
                CarIds.MUSIC -> music()
                CarIds.ALBUMS -> paged(ref, data.music().groups(MusicView.Albums), CarStyle.Grid, { it.title ?: look.text(CarText.UnknownAlbum) }, ::album)
                CarIds.ARTISTS -> paged(ref, data.music().groups(MusicView.Artists), CarStyle.Grid, { it.title ?: look.text(CarText.UnknownArtist) }, ::artist)
                CarIds.PLAYLISTS -> playlists()
                CarIds.GENRES -> paged(ref, genres(data.music()), CarStyle.List, { genreName(it.title) }, ::genre)
                CarIds.TRACKS -> data.music().let { library ->
                    tracks(CarIds.TRACKS, ref, library.tracksByTitle(), album = false) { track -> initial(track.title, library.locale) }
                }
                CarIds.YEARS -> filled(decades(data.music()))
                CarIds.FOLDERS -> paged(ref, data.music().groups(MusicView.Folders), CarStyle.List, { folderName(it.title) }, ::folder)
                CarIds.RECENT -> tracks(CarIds.RECENT, ref, data.recentTracks(), album = false)
                CarIds.PODCASTS -> podcasts()
                CarIds.CONTINUE -> episodes(data.continuing(), withShow = true)
                CarIds.SHOWS -> filled(data.feeds().map(::show))
                CarIds.NEW -> episodes(data.fresh(), withShow = true)
                CarIds.DISCOVER -> discover()
                CarIds.CATEGORIES -> data.country().let { country -> charted { categories(country, data.chart(country)) } }
                CarIds.COUNTRIES -> countries()
                CarIds.RADIO -> radio()
                CarIds.FAVOURITES -> data.favouriteStations().map(::station).ifEmpty { listOf(nothing(CarText.NoFavourites)) }
                CarIds.STATIONS_RECENT -> data.recentStations().map(::station).ifEmpty { listOf(nothing(CarText.NoRecentStations)) }
                CarIds.RADIO_COUNTRIES -> directory { radioCountries(data.radioCountries()) }
                CarIds.LANGUAGES -> directory { languages(data.languages()) }
                CarIds.TAGS -> directory { data.tags().take(MAX_TAGS).map(::tag) }
                CarIds.MOST_LISTENED -> listed(ref, StationQuery(order = StationOrder.Popularity))
                CarIds.MOST_LIKED -> listed(ref, StationQuery(order = StationOrder.Votes))
                else -> null
            }
            CarKind.Album -> groupTracks(ref, MusicView.Albums, album = true)
            CarKind.Artist -> filled(artistAlbums(data.music(), ref.key.orEmpty()).map(::album))
            CarKind.Playlist -> tracks(ref.path, ref, data.playlistTracks(ref.number ?: return emptyList()), album = false)
            CarKind.Decade -> filled(years(data.music(), ref.number?.toInt() ?: return emptyList()))
            CarKind.Year -> groupTracks(ref, MusicView.Years, album = false)
            CarKind.Folder -> groupTracks(ref, MusicView.Folders, album = false)
            CarKind.Genre -> groupTracks(ref, MusicView.Genres, album = false)
            CarKind.Smart -> {
                val list = data.smartLists().firstOrNull { it.id == ref.number } ?: return listOf(nothing(CarText.Empty))
                tracks(ref.path, ref, data.smartTracks(list), album = false)
            }
            CarKind.Show -> showEpisodes(ref.number ?: return emptyList())
            CarKind.Chart -> {
                val country = ApplePodcasts.storefront(ref.key) ?: return emptyList()
                charted { shows(data.chart(country).map { it.hit }) }
            }
            CarKind.Category -> {
                val genre = ref.number?.toInt() ?: return emptyList()
                val country = ApplePodcasts.storefront(ref.params["c"]) ?: return emptyList()
                charted { shows(data.chart(country).filter { show -> show.genres.any { it.id == genre } }.map { it.hit }) }
            }
            CarKind.DirectoryShow -> directoryShowEntries(hitOf(ref) ?: return emptyList())
            CarKind.Tag -> {
                val tag = ref.key?.takeIf { it.isNotEmpty() } ?: return emptyList()
                listed(ref, StationQuery(filters = StationFilters(tag = tag), order = StationOrder.Popularity))
            }
            CarKind.Country -> {
                val code = ref.key?.trim()?.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: return emptyList()
                listed(ref, StationQuery(filters = StationFilters(countryCode = code), order = StationOrder.Popularity))
            }
            CarKind.Language -> {
                val language = ref.key?.takeIf { it.isNotBlank() } ?: return emptyList()
                listed(ref, StationQuery(filters = StationFilters(language = language), order = StationOrder.Popularity))
            }
            CarKind.Search -> {
                val tracks = searchTracks(ref.key.orEmpty()).take(pageSize)
                tracks.mapIndexed { i, track -> trackItem(CarIds.searchTracks(ref.key.orEmpty()), i, track, album = false) }
            }
            CarKind.Episode, CarKind.Station, CarKind.DirectoryStation, CarKind.DirectoryEpisode, CarKind.Subscribe -> emptyList()
        }
    }

    /** What the entry [id] plays; null for one that plays nothing (or that is none of the tree's), or music not given in time. */
    suspend fun play(id: String): CarPlay? {
        val ref = CarIds.parse(id) ?: return null
        return try {
            playing(ref)
        } catch (e: MusicUnavailable) {
            null
        }
    }

    private suspend fun playing(ref: CarRef): CarPlay? {
        val start = ref.index ?: 0
        return when (ref.kind) {
            null -> when (ref.path) {
                CarIds.RECENT -> CarPlay.Tracks(data.recentTracks(), start)
                CarIds.TRACKS -> CarPlay.Tracks(data.music().tracksByTitle(), start)
                // the whole music shuffled, as the Music page's Shuffle plays it
                CarIds.SHUFFLE -> CarPlay.Tracks(data.music().tracks.shuffled(), 0)
                else -> null
            }
            CarKind.Album, CarKind.Artist, CarKind.Year, CarKind.Folder, CarKind.Genre -> {
                val view = when (ref.kind) {
                    CarKind.Album -> MusicView.Albums
                    CarKind.Artist -> MusicView.Artists
                    CarKind.Year -> MusicView.Years
                    CarKind.Genre -> MusicView.Genres
                    else -> MusicView.Folders
                }
                val group = group(data.music(), view, ref) ?: return null
                val source = when (ref.kind) {
                    CarKind.Album -> group.title?.let { PlaySource(PlayFrom.Album, it) }
                    CarKind.Artist -> group.title?.let { PlaySource(PlayFrom.Artist, it) }
                    else -> null
                }
                CarPlay.Tracks(group.tracks, start, source)
            }
            CarKind.Playlist -> {
                val id = ref.number ?: return null
                val name = data.playlists().firstOrNull { it.id == id }?.name
                CarPlay.Tracks(data.playlistTracks(id), start, name?.let { PlaySource(PlayFrom.Playlist, it) })
            }
            CarKind.Smart -> {
                val list = data.smartLists().firstOrNull { it.id == ref.number } ?: return null
                CarPlay.Tracks(data.smartTracks(list), start, PlaySource(PlayFrom.Playlist, list.name))
            }
            CarKind.Search -> CarPlay.Tracks(searchTracks(ref.key.orEmpty()), start)
            CarKind.Episode -> {
                val episode = data.episode(ref.number ?: return null) ?: return null
                val feed = data.feeds().firstOrNull { it.id == episode.feedId } ?: return null
                CarPlay.Episode(feed, episode)
            }
            CarKind.DirectoryEpisode -> directoryEpisode(ref)
            CarKind.Subscribe -> hitOf(ref)?.let(CarPlay::Subscribe)
            CarKind.Station -> data.station(ref.number ?: return null)?.let(CarPlay::Radio)
            // 1.0.3: as the app knows it, so that playing it keeps its country and tags; the player reports its click
            CarKind.DirectoryStation -> directoryStation(ref)?.let(CarPlay::Radio)
            CarKind.Decade, CarKind.Show, CarKind.Tag, CarKind.Chart, CarKind.Category, CarKind.DirectoryShow, CarKind.Country, CarKind.Language -> null
        }?.takeUnless { it is CarPlay.Tracks && it.tracks.isEmpty() }
    }

    /**
     * 1.0.3: an episode of a show not followed, read from its feed: it plays as the app plays an episode (EpisodeMedia),
     * from its start. A show followed meanwhile plays its own episode, where it was left.
     */
    private suspend fun directoryEpisode(ref: CarRef): CarPlay? {
        val url = ref.params["u"]?.takeIf { it.isNotBlank() } ?: return null
        val guid = ref.key?.takeIf { it.isNotEmpty() } ?: return null
        followed(url)?.let { feed -> data.episodes(feed.id).firstOrNull { it.guid == guid }?.let { return CarPlay.Episode(feed, it) } }
        val parsed = try {
            data.readFeed(url)
        } catch (e: FeedFailure) {
            return null
        }
        val episode = parsed.episodes.firstOrNull { it.guid == guid } ?: return null
        return CarPlay.Episode(PodcastFeed(feedUrl = url, title = parsed.title, author = parsed.author, imageUrl = parsed.imageUrl), playable(episode))
    }

    /**
     * 1.0.3: the entry [id] alone, as a list shows it: a show of the directory (followed or not, with its browse action
     * while it is not), a show followed, a station (with "Favorilere ekle" or "Favorilerden çıkar"); null for any other.
     * Auto reads one again after a browse action changed it.
     */
    suspend fun item(id: String): CarItem? {
        val ref = CarIds.parse(id) ?: return null
        return when (ref.kind) {
            CarKind.DirectoryShow -> hitOf(ref)?.let { hit -> directoryShow(hit, followed(hit.feedUrl)) }
            CarKind.Show -> data.feeds().firstOrNull { it.id == ref.number }?.let(::show)
            // the same id as the list gave it, even when the station has been kept since
            CarKind.Station -> data.station(ref.number ?: return null)?.let { station(it).copy(id = id) }
            CarKind.DirectoryStation -> directoryStation(ref)?.let { station(it).copy(id = id) }
            else -> null
        }
    }

    /**
     * 1.0.3: the station the entry [id] plays, made a favourite ([on]) or no longer one through the app's own path (the
     * Radio page's star): returned as kept; null for an entry that is no station.
     */
    suspend fun favourite(id: String, on: Boolean): Station? {
        val ref = CarIds.parse(id) ?: return null
        val station = when (ref.kind) {
            CarKind.Station -> data.station(ref.number ?: return null)
            CarKind.DirectoryStation -> directoryStation(ref)
            else -> null
        } ?: return null
        return data.favourite(station, on)
    }

    /**
     * 1.0.3: the station of the directory an entry names: as the app knows it ([CarData.knownStation]: its country, its
     * tags, the user's marks), else as its id carries it (its name, address and logo).
     */
    private suspend fun directoryStation(ref: CarRef): Station? {
        val url = ref.params["u"]?.takeIf { it.isNotBlank() } ?: return null
        val uuid = ref.key?.takeIf { it.isNotEmpty() }
        uuid?.let { data.knownStation(it) }?.let { return it }
        return Station(uuid = uuid, name = ref.params["n"].orEmpty().ifBlank { url }, streamUrl = url, logoUrl = ref.params["l"])
    }

    /**
     * 1.0.3: the show the entry [id] subscribes to: a show of the directory (its browse action "Abone ol") or its row
     * "Abone ol"; null for any other entry. Followed already, it is still given (subscribing then says so).
     */
    fun subscription(id: String): PodcastHit? {
        val ref = CarIds.parse(id) ?: return null
        return if (ref.kind == CarKind.DirectoryShow || ref.kind == CarKind.Subscribe) hitOf(ref) else null
    }

    /**
     * Android Auto's search for [query]: music tracks, albums and artists, podcast episodes and shows, then radio stations
     * (the kept ones, then the directory's, given [DIRECTORY_WAIT_MS]); each group under its heading. One line says when
     * nothing answers; music the medialibrary does not give in time is one line too, before what the rest found.
     */
    suspend fun search(query: String): List<CarItem> {
        if (CarVoice.fold(query).isEmpty()) return listOf(nothing(CarText.NoResult))
        val found = ArrayList<CarItem>()
        val library = try {
            data.music()
        } catch (e: MusicUnavailable) {
            found += nothing(CarText.MusicUnavailable)
            null
        }
        if (library != null) {
            ranked(query, library.tracks) { it.title }.take(MAX_FOUND).forEachIndexed { i, track ->
                found += trackItem(CarIds.searchTracks(query), i, track, album = false).copy(group = look.text(CarText.FoundTracks))
            }
            ranked(query, library.groups(MusicView.Albums)) { it.title }.take(MAX_FOUND_GROUPS)
                .forEach { found += album(it).copy(group = look.text(CarText.FoundAlbums)) }
            ranked(query, library.groups(MusicView.Artists)) { it.title }.take(MAX_FOUND_GROUPS)
                .forEach { found += artist(it).copy(group = look.text(CarText.FoundArtists)) }
        }
        val feeds = data.feeds()
        val byFeed = feeds.associateBy { it.id }
        // 1.0.3: the two directories are asked side by side, each given [DIRECTORY_WAIT_MS]
        val (directoryShows, directoryStations) = coroutineScope {
            val shows = async { withTimeoutOrNull(DIRECTORY_WAIT_MS) { reachable { data.findShows(query) } }.orEmpty() }
            val stations = async { withTimeoutOrNull(DIRECTORY_WAIT_MS) { quietly { data.findStations(query) } }.orEmpty() }
            shows.await() to stations.await()
        }
        data.findEpisodes(query, MAX_FOUND).forEach { found += episode(it, byFeed[it.feedId]).copy(group = look.text(CarText.FoundEpisodes)) }
        ranked(query, feeds) { it.title }.take(MAX_FOUND_GROUPS).forEach { found += show(it).copy(group = look.text(CarText.FoundShows)) }
        // 1.0.3: the shows of the directory not followed, under their own heading
        val followedKeys = feeds.flatMapTo(HashSet()) { it.addressKeys() }
        directoryShows.filter { FeedUrl.key(it.feedUrl) !in followedKeys }.distinctBy { FeedUrl.key(it.feedUrl) }.take(MAX_FOUND_GROUPS)
            .forEach { found += directoryShow(it, null).copy(group = look.text(CarText.FoundNewShows)) }
        val kept = (data.favouriteStations() + data.recentStations()).distinctBy { it.key }
        val stations = ranked(query, kept) { it.name }.toMutableList()
        val keys = stations.mapTo(HashSet()) { it.key }
        stations += directoryStations.filter { it.key !in keys }
        stations.take(MAX_FOUND).forEach { found += station(it).copy(group = look.text(CarText.FoundStations)) }
        return found.ifEmpty { listOf(nothing(CarText.NoResult)) }
    }

    /** The tracks whose title answers [query], the best first. */
    private suspend fun searchTracks(query: String): List<Track> = ranked(query, data.music().tracks) { it.title }

    private fun root() = listOf(
        tab(CarIds.MUSIC, CarText.Music, CarIcon.Music, CarStyle.Category),
        tab(CarIds.PODCASTS, CarText.Podcasts, CarIcon.Podcasts, CarStyle.Category),
        tab(CarIds.RADIO, CarText.Radio, CarIcon.Radio, CarStyle.Category),
        tab(CarIds.RECENT, CarText.Recent, CarIcon.Recent, CarStyle.List),
    )

    /**
     * The Music tab: the whole music shuffled at one tap first, then its lists as the Music page groups it, every track
     * last; Genres only when some track has a genre (music not given in time leaves it out, not the tab, and Auto is
     * told once it is read).
     */
    private suspend fun music(): List<CarItem> {
        val genres = try {
            genres(data.music()).any { it.title != null }
        } catch (e: MusicUnavailable) {
            false
        }
        return listOfNotNull(
            CarItem(id = CarIds.SHUFFLE, title = look.text(CarText.ShuffleAll), iconUri = look.icon(CarIcon.Shuffle), playable = true),
            tab(CarIds.ALBUMS, CarText.Albums, CarIcon.Albums, CarStyle.Grid),
            tab(CarIds.ARTISTS, CarText.Artists, CarIcon.Artists, CarStyle.Grid),
            if (genres) tab(CarIds.GENRES, CarText.Genres, CarIcon.Genres, CarStyle.List) else null,
            tab(CarIds.PLAYLISTS, CarText.Playlists, CarIcon.Playlists, CarStyle.Grid),
            tab(CarIds.YEARS, CarText.Years, CarIcon.Years, CarStyle.List),
            tab(CarIds.FOLDERS, CarText.Folders, CarIcon.Folders, CarStyle.List),
            tab(CarIds.TRACKS, CarText.AllTracks, CarIcon.Tracks, CarStyle.List),
        )
    }

    /** The playlists, then the smart lists (each read when opened), as the Playlists page lists them. */
    private suspend fun playlists(): List<CarItem> {
        val playlists = data.playlists().map(::playlist)
        val smart = data.smartLists().map(::smartList)
        return filled(playlists + smart)
    }

    /** The genres, the tracks of none last. */
    private fun genres(library: MusicLibrary): List<MusicGroup> = library.groups(MusicView.Genres)

    private fun podcasts() = listOf(
        tab(CarIds.CONTINUE, CarText.Continue, CarIcon.Continue, CarStyle.List),
        tab(CarIds.SHOWS, CarText.Subscriptions, CarIcon.Podcasts, CarStyle.Grid),
        tab(CarIds.NEW, CarText.NewEpisodes, CarIcon.New, CarStyle.List),
        tab(CarIds.DISCOVER, CarText.Discover, CarIcon.Discover, CarStyle.List),
    )

    /** 1.0.3: Keşfet: the popular shows of the device's country, its categories, the countries (each its popular shows). */
    private fun discover() = listOf(
        tab(CarIds.chart(data.country()), CarText.PopularShows, CarIcon.Popular, CarStyle.Grid),
        tab(CarIds.CATEGORIES, CarText.Categories, CarIcon.Categories, CarStyle.List),
        tab(CarIds.COUNTRIES, CarText.Countries, CarIcon.Countries, CarStyle.List),
    )

    /**
     * 1.0.3: the categories of [country]'s popular shows ([chart]), those with the most shows first (then by name); each
     * opens on its shows in the chart's order, its tile the cover of its first.
     */
    private fun categories(country: String, chart: List<ChartShow>): List<CarItem> {
        val shows = LinkedHashMap<Int, MutableList<ChartShow>>()
        val names = HashMap<Int, String>()
        chart.forEach { show ->
            show.genres.forEach { genre ->
                shows.getOrPut(genre.id) { ArrayList() } += show
                names.getOrPut(genre.id) { look.category(genre) }
            }
        }
        val collator = Collator.getInstance(look.locale)
        val order = compareByDescending<Map.Entry<Int, List<ChartShow>>> { it.value.size }
            .then { a, b -> collator.compare(names[a.key].orEmpty(), names[b.key].orEmpty()) }
        return shows.entries.sortedWith(order).map { (genre, list) ->
            CarItem(
                id = CarIds.category(country, genre),
                title = names[genre].orEmpty(),
                subtitle = look.count(CarCount.Shows, list.size),
                iconUri = look.webImage(list.first().hit.imageUrl),
                browsable = true,
                children = CarStyle.Grid,
            )
        }
    }

    /** 1.0.3: the countries whose popular shows Keşfet lists: the device's first, then the others by name in the app's language. */
    private fun countries(): List<CarItem> {
        val home = data.country()
        val collator = Collator.getInstance(look.locale)
        val others = ApplePodcasts.COUNTRIES.filter { it != home }.map { it to look.country(it) }.sortedWith { a, b -> collator.compare(a.second, b.second) }
        return (listOf(home to look.country(home)) + others).map { (code, name) ->
            CarItem(id = CarIds.chart(code), title = name, iconUri = look.icon(CarIcon.Countries), browsable = true, children = CarStyle.Grid)
        }
    }

    /** 1.0.3: shows of the directory as tiles: those followed say so, the others offer "Abone ol". */
    private suspend fun shows(hits: List<PodcastHit>): List<CarItem> {
        val feeds = data.feeds()
        return hits.map { hit -> directoryShow(hit, feeds.firstOrNull { FeedUrl.key(hit.feedUrl) in it.addressKeys() }) }
    }

    /** The feed followed at [url] (any address it answers to); null when it is not followed. */
    private suspend fun followed(url: String): PodcastFeed? {
        val key = FeedUrl.key(url)
        return data.feeds().firstOrNull { key in it.addressKeys() }
    }

    /**
     * 1.0.3: a show of a directory: followed ([followed]), it says so and opens on its episodes as Programlarım does; not
     * followed, it offers the browse action "Abone ol" and opens on its feed's episodes. Its id keeps what the directory
     * said of it, for subscribing.
     */
    private fun directoryShow(hit: PodcastHit, followed: PodcastFeed?) = CarItem(
        id = CarIds.directoryShow(hit.feedUrl, hit.title, hit.author, hit.imageUrl),
        title = followed?.title ?: hit.title,
        subtitle = if (followed != null) look.text(CarText.Subscribed) else hit.author,
        iconUri = look.webImage(followed?.imageUrl ?: hit.imageUrl),
        browsable = true,
        children = CarStyle.List,
        actions = if (followed == null) listOf(CarActions.SUBSCRIBE) else emptyList(),
    )

    /**
     * 1.0.3: what a show of a directory opens on: followed, its episodes as Programlarım lists them; not followed, the row
     * "Abone ol" every Auto shows, then its feed's episodes (the newest first, playable); a feed that cannot be read says so.
     */
    private suspend fun directoryShowEntries(hit: PodcastHit): List<CarItem> {
        followed(hit.feedUrl)?.let { return showEpisodes(it.id) }
        val feed = try {
            data.readFeed(hit.feedUrl)
        } catch (e: FeedFailure) {
            return listOf(nothing(CarText.ShowUnavailable))
        }
        val subscribe = CarItem(
            id = CarIds.subscribe(hit.feedUrl, hit.title, hit.author, hit.imageUrl),
            title = look.text(CarText.Subscribe),
            subtitle = feed.title.ifBlank { hit.title },
            iconUri = look.icon(CarIcon.Subscribe),
            playable = true,
        )
        val cover = feed.imageUrl ?: hit.imageUrl
        return listOf(subscribe) + feed.episodes.take(pageSize - 1).map { episode ->
            CarItem(
                id = CarIds.directoryEpisode(hit.feedUrl, episode.guid),
                title = episode.title,
                subtitle = look.date(episode.publishedAt),
                iconUri = look.webImage(episode.imageUrl ?: cover),
                playable = true,
            )
        }
    }

    /** A followed show's episodes, the newest first. */
    private suspend fun showEpisodes(feedId: Long): List<CarItem> {
        val episodes = data.episodes(feedId)
        if (episodes.isEmpty()) return listOf(nothing(CarText.Empty))
        return episodes.take(pageSize).map { episode(it, null) }
    }

    /** 1.0.3: the show an entry of a directory names: its feed's address, and its title, author and cover as the directory gave them. */
    private fun hitOf(ref: CarRef): PodcastHit? {
        val url = ref.key?.takeIf { it.isNotBlank() } ?: return null
        return PodcastHit(feedUrl = url, title = ref.params["n"]?.takeIf { it.isNotBlank() } ?: url, author = ref.params["a"], imageUrl = ref.params["l"])
    }

    /** 1.0.3: the podcast directory's answer, or one line saying it cannot be reached. */
    private suspend fun charted(read: suspend () -> List<CarItem>): List<CarItem> = try {
        filled(read())
    } catch (e: DirectoryFailure) {
        listOf(nothing(CarText.PodcastsOffline))
    }

    /**
     * The Radio tab: the favourite stations and those played last (tiles); 1.0.3: the directory's countries, languages
     * and categories, its most listened and its most liked stations (rows).
     */
    private fun radio() = listOf(
        tab(CarIds.FAVOURITES, CarText.FavouriteStations, CarIcon.Favourites, CarStyle.Grid),
        tab(CarIds.STATIONS_RECENT, CarText.RecentStations, CarIcon.Recent, CarStyle.Grid),
        tab(CarIds.RADIO_COUNTRIES, CarText.Countries, CarIcon.Countries, CarStyle.List),
        tab(CarIds.LANGUAGES, CarText.Languages, CarIcon.Languages, CarStyle.List),
        tab(CarIds.TAGS, CarText.Categories, CarIcon.Categories, CarStyle.List),
        tab(CarIds.MOST_LISTENED, CarText.MostListened, CarIcon.Listened, CarStyle.List),
        tab(CarIds.MOST_LIKED, CarText.MostLiked, CarIcon.Liked, CarStyle.List),
    )

    /**
     * 1.0.3: the radio directory's countries with their station counts: the device's first, then by name in the app's
     * language (a name the app does not know is the directory's); one with no ISO code cannot be asked for, and is left out.
     */
    private fun radioCountries(countries: List<DirectoryCountry>): List<CarItem> {
        val home = data.homeCountry()?.trim()?.uppercase(Locale.ROOT)
        val collator = Collator.getInstance(look.locale)
        val (first, others) = countries.filter { it.code.length == 2 }.map { it to look.country(it.code, it.name) }
            .partition { it.first.code.equals(home, ignoreCase = true) }
        return (first + others.sortedWith { a, b -> collator.compare(a.second, b.second) }).map { (country, name) ->
            CarItem(
                id = CarIds.radioCountry(country.code),
                title = name,
                subtitle = look.count(CarCount.Stations, country.stations),
                iconUri = look.icon(CarIcon.Countries),
                browsable = true,
                children = CarStyle.List,
            )
        }
    }

    /**
     * 1.0.3: the radio directory's [MAX_LANGUAGES] most used languages with their station counts, named in the app's
     * language (one the directory spells several ways once, its most used spelling): the app's own first, then by name.
     */
    private fun languages(languages: List<DirectoryLanguage>): List<CarItem> {
        val home = look.locale.language.lowercase(Locale.ROOT)
        val collator = Collator.getInstance(look.locale)
        val named = languages.sortedByDescending { it.stations }
            .map { it to look.language(it.name, it.code) }
            .distinctBy { it.second.lowercase(look.locale) }
            .take(MAX_LANGUAGES)
        val (first, others) = named.partition { home.isNotEmpty() && RadioLabels.languageCode(it.first.name, it.first.code) == home }
        return (first + others.sortedWith { a, b -> collator.compare(a.second, b.second) }).map { (language, name) ->
            CarItem(
                id = CarIds.language(language.name),
                title = name,
                subtitle = look.count(CarCount.Stations, language.stations),
                iconUri = look.icon(CarIcon.Languages),
                browsable = true,
                children = CarStyle.List,
            )
        }
    }

    /**
     * 1.0.3: a page of the directory's stations for [query], in its order (the most listened first, but for the most
     * liked): [stationPage] of them from the page [ref] asks for, with a last row that opens the next page when the
     * directory has more. One line says when it has none, or cannot be reached.
     */
    private suspend fun listed(ref: CarRef, query: StationQuery): List<CarItem> = directory(CarText.NoStations) {
        val page = (ref.page ?: 0).coerceAtLeast(0)
        // one more than a page: whether there is a next one
        val found = data.stations(query.copy(offset = page * stationPage, limit = stationPage + 1))
        // what the list itself says (its country, its category) is not said again on each row
        val shown = found.take(stationPage).map { station(it, placed = query.filters.countryCode == null, listTag = query.filters.tag) }
        if (found.size > stationPage) shown + more(ref, page + 1) else shown
    }

    /** 1.0.3: the last row of a page of stations: it opens the [next] page of the same list. */
    private fun more(ref: CarRef, next: Int) = CarItem(
        id = CarIds.page(ref.path, next),
        title = look.text(CarText.MoreStations),
        iconUri = look.icon(CarIcon.More),
        browsable = true,
        children = CarStyle.List,
    )

    private fun tab(id: String, text: CarText, icon: CarIcon, style: CarStyle) =
        CarItem(id = id, title = look.text(text), iconUri = look.icon(icon), browsable = true, children = style)

    private fun album(group: MusicGroup) = CarItem(
        id = CarIds.album(group.key),
        title = group.title ?: look.text(CarText.UnknownAlbum),
        subtitle = group.artist ?: look.text(CarText.UnknownArtist),
        iconUri = look.cover(group.tracks.firstOrNull()),
        browsable = true,
        children = CarStyle.List,
    )

    private fun artist(group: MusicGroup) = CarItem(
        id = CarIds.artist(group.key),
        title = group.title ?: look.text(CarText.UnknownArtist),
        subtitle = look.count(CarCount.Albums, group.albumCount),
        iconUri = look.cover(group.tracks.firstOrNull()),
        browsable = true,
        children = CarStyle.Grid,
    )

    private fun playlist(list: PlaylistSummary) = CarItem(
        id = CarIds.playlist(list.id),
        title = list.name,
        subtitle = look.count(CarCount.Tracks, list.count),
        iconUri = look.playlistCover(list.id),
        browsable = true,
        children = CarStyle.List,
    )

    /** 1.0.3: a smart list: its tracks are read only when it is opened (its line says it is a smart list, not a count). */
    private fun smartList(list: SmartList) = CarItem(
        id = CarIds.smart(list.id),
        title = list.name,
        subtitle = look.text(CarText.Smart),
        iconUri = look.icon(CarIcon.Smart),
        browsable = true,
        children = CarStyle.List,
    )

    private fun genre(group: MusicGroup) = CarItem(
        id = CarIds.genre(group.key),
        title = genreName(group.title),
        subtitle = look.count(CarCount.Tracks, group.tracks.size),
        iconUri = look.cover(group.tracks.firstOrNull()),
        browsable = true,
        children = CarStyle.List,
    )

    private fun genreName(title: String?): String = title ?: look.text(CarText.UnknownGenre)

    private fun folder(group: MusicGroup) = CarItem(
        id = CarIds.folder(group.key),
        title = folderName(group.title),
        subtitle = group.title?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() } ?: look.count(CarCount.Tracks, group.tracks.size),
        iconUri = look.cover(group.tracks.firstOrNull()),
        browsable = true,
        children = CarStyle.List,
    )

    private fun folderName(path: String?): String =
        path?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: look.text(CarText.UnknownFolder)

    /** The decades of the music's years, the newest first; the tracks of no year last, as one entry. */
    private fun decades(library: MusicLibrary): List<CarItem> {
        val groups = library.groups(MusicView.Years)
        val dated = groups.filter { it.year != null }.groupBy { it.year!! / 10 * 10 }
        val items = dated.entries.sortedByDescending { it.key }.map { (start, years) ->
            CarItem(
                id = CarIds.decade(start),
                title = look.decade(start),
                subtitle = look.count(CarCount.Tracks, years.sumOf { it.tracks.size }),
                iconUri = look.icon(CarIcon.Years),
                browsable = true,
                children = CarStyle.List,
            )
        }
        val undated = groups.firstOrNull { it.year == null }?.let { group ->
            CarItem(
                id = CarIds.year(null),
                title = look.text(CarText.UnknownYear),
                subtitle = look.count(CarCount.Tracks, group.tracks.size),
                iconUri = look.icon(CarIcon.Years),
                browsable = true,
                children = CarStyle.List,
            )
        }
        return items + listOfNotNull(undated)
    }

    /** The years of the decade from [start], the newest first. */
    private fun years(library: MusicLibrary, start: Int): List<CarItem> = library.groups(MusicView.Years)
        .filter { it.year != null && it.year / 10 * 10 == start }
        .sortedByDescending { it.year }
        .map { group ->
            CarItem(
                id = CarIds.year(group.year),
                title = group.year.toString(),
                subtitle = look.count(CarCount.Tracks, group.tracks.size),
                iconUri = look.cover(group.tracks.firstOrNull()),
                browsable = true,
                children = CarStyle.List,
            )
        }

    /** The albums of the artist whose group's key is [key], the way the Albums view sorts them. */
    private fun artistAlbums(library: MusicLibrary, key: String): List<MusicGroup> =
        library.groups(MusicView.Albums).filter { (it.artist?.let(MusicLibrary::artistKey) ?: "") == key }

    /** The group of [view] [ref] names; a year's by its number (0 for no year); a genre's by its key ("" for none). */
    private fun group(library: MusicLibrary, view: MusicView, ref: CarRef): MusicGroup? {
        val groups = library.groups(view)
        return if (view == MusicView.Years) {
            val year = ref.number?.toInt()?.takeIf { it > 0 }
            groups.firstOrNull { it.year == year }
        } else {
            groups.firstOrNull { it.key == ref.key }
        }
    }

    private suspend fun groupTracks(ref: CarRef, view: MusicView, album: Boolean): List<CarItem> {
        val group = group(data.music(), view, ref) ?: return listOf(nothing(CarText.Empty))
        return tracks(ref.path, ref, group.tracks, album)
    }

    /**
     * [tracks] as the list [listId], in pages when it is long, each named by its first and last track's [pageName] (their
     * titles; the first letters of a list in the order of its titles: "A – Ç"); [album]: an album's own, whose tracks need
     * no album line.
     */
    private fun tracks(listId: String, ref: CarRef, tracks: List<Track>, album: Boolean, pageName: (Track) -> String = { it.title }): List<CarItem> {
        if (tracks.isEmpty()) return listOf(nothing(CarText.Empty))
        val page = ref.page
        if (page == null && tracks.size > pageSize) return pages(listId, tracks, CarStyle.List, pageName) { look.cover(it) }
        val from = (page ?: 0) * pageSize
        return tracks.withIndex().drop(from).take(pageSize).map { (i, track) -> trackItem(listId, i, track, album) }
    }

    private fun trackItem(listId: String, index: Int, track: Track, album: Boolean) = CarItem(
        id = CarIds.track(listId, index),
        title = track.title,
        subtitle = if (album) track.artist ?: track.filedArtist else listOfNotNull(track.artist ?: track.filedArtist, track.album).joinToString(" · ").ifEmpty { null },
        iconUri = look.cover(track),
        playable = true,
    )

    /** [groups] in pages of [pageSize] when there are more, each page named by its first and last title; [style]: how the list draws them. */
    private fun <T> paged(ref: CarRef, groups: List<T>, style: CarStyle, title: (T) -> String, item: (T) -> CarItem): List<CarItem> {
        if (groups.isEmpty()) return listOf(nothing(CarText.Empty))
        val page = ref.page
        if (page == null && groups.size > pageSize) return pages(ref.path, groups, style, title) { item(it).iconUri }
        return groups.drop((page ?: 0) * pageSize).take(pageSize).map(item)
    }

    /**
     * The pages of the long list [listId]: each named by its first and last [title], with its first entry's [icon] (a tile
     * of a grid is never blank), its own entries drawn as the list's ([style]: a grid of albums stays a grid).
     */
    private fun <T> pages(listId: String, entries: List<T>, style: CarStyle, title: (T) -> String, icon: (T) -> String?): List<CarItem> =
        entries.chunked(pageSize).mapIndexed { page, chunk ->
            CarItem(
                id = CarIds.page(listId, page),
                title = listOf(title(chunk.first()), title(chunk.last())).distinct().joinToString(" – "),
                iconUri = icon(chunk.first()) ?: look.icon(CarIcon.NoCover),
                browsable = true,
                children = style,
            )
        }

    private suspend fun episodes(list: List<PodcastEpisode>, withShow: Boolean): List<CarItem> {
        if (list.isEmpty()) return listOf(nothing(CarText.Empty))
        val feeds = if (withShow) data.feeds().associateBy { it.id } else emptyMap()
        return list.take(pageSize).map { episode(it, feeds[it.feedId]) }
    }

    /** An episode: its show under it ([feed], in lists of several shows), else its date; its show's cover; how far it was heard. */
    private fun episode(episode: PodcastEpisode, feed: PodcastFeed?) = CarItem(
        id = CarIds.episode(episode.id),
        title = episode.title,
        subtitle = feed?.title ?: look.date(episode.publishedAt),
        iconUri = look.webImage(episode.imageUrl ?: feed?.imageUrl),
        playable = true,
        progress = CarPodcasts.progress(episode),
        played = episode.played,
        downloaded = episode.downloadState == DownloadState.DONE,
    )

    private fun show(feed: PodcastFeed) = CarItem(
        id = CarIds.show(feed.id),
        title = feed.title,
        subtitle = feed.author,
        iconUri = look.webImage(feed.imageUrl),
        browsable = true,
        children = CarStyle.List,
    )

    /**
     * A station ([stationId]); its country (1.0.3: named in the app's language; not in a country's own list: [placed]
     * false) and first tag (1.0.3: not the category [listTag] it is listed under) under it. 1.0.3: it offers "Favorilere
     * ekle", or "Favorilerden çıkar" when it is a favourite.
     */
    private fun station(station: Station, placed: Boolean = true, listTag: String? = null): CarItem {
        val id = stationId(station)
        val country = if (!placed) null else station.countryCode?.takeIf { it.isNotBlank() }?.let { look.country(it, station.country) } ?: station.country
        val tag = station.tags.firstOrNull { !it.equals(listTag, ignoreCase = true) }
        return CarItem(
            id = id,
            title = station.name,
            subtitle = listOfNotNull(country, tag).joinToString(" · ").ifEmpty { null },
            iconUri = look.webImage(station.logoUrl),
            playable = true,
            actions = listOf(if (station.favourite) CarActions.UNFAVOURITE else CarActions.FAVOURITE),
        )
    }

    /** A category: the directory's tag; 1.0.3: it opens on its stations as rows, the most listened first. */
    private fun tag(tag: DirectoryTag) = CarItem(
        id = CarIds.tag(tag.name),
        title = tag.name.replaceFirstChar { it.titlecase() },
        subtitle = look.count(CarCount.Stations, tag.stations),
        iconUri = look.icon(CarIcon.Categories),
        browsable = true,
        children = CarStyle.List,
    )

    /**
     * The directory's answer, or one line saying it cannot be reached; 1.0.3: or that it asked to wait, or ([empty]) that
     * it has nothing.
     */
    private suspend fun directory(empty: CarText = CarText.Empty, read: suspend () -> List<CarItem>): List<CarItem> = try {
        read().ifEmpty { listOf(nothing(empty)) }
    } catch (e: RadioFailure.Busy) {
        listOf(nothing(CarText.RadioBusy))
    } catch (e: RadioFailure) {
        listOf(nothing(CarText.RadioOffline))
    }

    private fun filled(items: List<CarItem>): List<CarItem> = items.ifEmpty { listOf(nothing(CarText.Empty)) }

    /** A line that says why a list is empty; it opens and plays nothing, and says so again when tapped (its id carries [text]). */
    private fun nothing(text: CarText) = CarItem(id = CarIds.nothing(text.name), title = look.text(text), iconUri = look.icon(CarIcon.NoCover), playable = true)

    private fun <T> ranked(query: String, items: Iterable<T>, name: (T) -> String?): List<T> {
        val folded = CarVoice.fold(query)
        if (folded.isEmpty()) return emptyList()
        return items.mapNotNull { item -> CarVoice.foldedScore(folded, CarVoice.fold(name(item))).takeIf { it >= CarVoice.WORDS }?.let { item to it } }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    companion object {
        /** The most entries one list sends; a longer one is cut into pages. */
        const val PAGE = 400

        /** The directory's categories listed: its most used tags. */
        const val MAX_TAGS = 60

        /** 1.0.3: the stations one page of the directory's lists shows (a country's, a category's…); a last row opens the next. */
        const val STATION_PAGE = 100

        /** 1.0.3: the languages listed: the directory's most used. */
        const val MAX_LANGUAGES = 100

        const val MAX_FOUND = 30
        const val MAX_FOUND_GROUPS = 10

        /** How long a search waits for the radio directory. */
        const val DIRECTORY_WAIT_MS = 4_000L

        /**
         * 1.0.3: the first letter of [title] as a capital of [locale] ("İ" for "ilk" in Turkish), a page of every track
         * named by it; "#" for a title that starts with no letter.
         */
        internal fun initial(title: String, locale: Locale): String =
            title.trim().firstOrNull()?.takeIf { it.isLetter() }?.toString()?.uppercase(locale) ?: "#"

        /**
         * A station's entry: 1.0.3: one of the directory always by what plays it (its UUID, name, address and logo), so
         * that the entry stays good when the app stops keeping the station (no longer a favourite, never played); the
         * user's own (imported) by its row.
         */
        internal fun stationId(station: Station): String = when {
            station.uuid == null && station.id != 0L -> CarIds.station(station.id)
            else -> CarIds.directoryStation(station.uuid.orEmpty(), station.name, station.streamUrl, station.logoUrl)
        }

        /** [block]'s answer, or null when the directory could not give one. */
        internal suspend fun <T> quietly(block: suspend () -> T): T? = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RadioFailure) {
            null
        }

        /** 1.0.3: [block]'s answer, or null when the podcast directory could not give one. */
        internal suspend fun <T> reachable(block: suspend () -> T): T? = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: DirectoryFailure) {
            null
        }

        /** 1.0.3: an episode of a feed as the player's episode: from its start, never played, not downloaded. */
        internal fun playable(episode: ParsedEpisode) = PodcastEpisode(
            feedId = 0,
            guid = episode.guid,
            title = episode.title,
            audioUrl = episode.audioUrl,
            mimeType = episode.mimeType,
            durationMs = episode.durationMs,
            publishedAt = episode.publishedAt,
            imageUrl = episode.imageUrl,
        )
    }
}
