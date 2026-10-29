package app.nagmedar.ui.auto

import app.nagmedar.data.DownloadState
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.ui.music.MusicGroup
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.playback.PlayFrom
import app.nagmedar.ui.playback.PlaySource
import app.nagmedar.ui.playback.PlaybackRules
import app.nagmedar.ui.radio.DirectoryTag
import app.nagmedar.ui.radio.RadioFailure
import app.nagmedar.ui.radio.Station
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.vlc.media.CarItem
import org.videolan.vlc.media.CarStyle

/**
 * 1.0: what Android Auto's catalog reads: the Music page's library and its playlists, the podcasts in Room, the radio's
 * kept stations and its directory. The app's in [AppCarData], fakes in the tests. Every call is made off the main thread.
 */
interface CarData {
    /** The Music page's library (the Music section's folders), its groupings made on first use. */
    suspend fun music(): MusicLibrary

    /** The music tracks played last, the latest first (the Music page's "Son çalınan müzik" shelf). */
    suspend fun recentTracks(): List<Track>

    suspend fun playlists(): List<PlaylistSummary>

    suspend fun playlistTracks(id: Long): List<Track>

    /** The subscriptions, by title. */
    suspend fun feeds(): List<PodcastFeed>

    /** A show's episodes, the newest first. */
    suspend fun episodes(feedId: Long): List<PodcastEpisode>

    suspend fun episode(id: Long): PodcastEpisode?

    /** The episodes left in the middle, not played: the one listened to last first ([CarPodcasts.continuing]). */
    suspend fun continuing(): List<PodcastEpisode>

    /** The new episodes, unplayed and added by a refresh after the subscription, the newest first. */
    suspend fun fresh(): List<PodcastEpisode>

    /** The episodes whose title answers [query] ([CarVoice.score] at least [CarVoice.WORDS]), the best first, at most [limit]. */
    suspend fun findEpisodes(query: String, limit: Int): List<PodcastEpisode>

    suspend fun favouriteStations(): List<Station>

    suspend fun recentStations(): List<Station>

    suspend fun station(id: Long): Station?

    /** The directory's tags, the most used first. Throws [RadioFailure]. */
    suspend fun tags(): List<DirectoryTag>

    /** The directory's stations with [tag], the most voted first. Throws [RadioFailure]. */
    suspend fun tagged(tag: String): List<Station>

    /** The directory's stations whose name has [query]. Throws [RadioFailure]. */
    suspend fun findStations(query: String): List<Station>
}

/** A text of Android Auto's entries. */
enum class CarText {
    Music, Podcasts, Radio, Recent,
    Albums, Artists, Playlists, Years, Folders,
    Continue, Subscriptions, NewEpisodes,
    FavouriteStations, RecentStations, Categories,
    UnknownAlbum, UnknownArtist, UnknownYear, UnknownFolder,
    FoundTracks, FoundAlbums, FoundArtists, FoundEpisodes, FoundShows, FoundStations,
    Empty, RadioOffline, NoResult,
}

/** What a count counts. */
enum class CarCount { Tracks, Albums, Episodes, Stations }

/** The icons of Android Auto's tabs and lists. */
enum class CarIcon { Music, Podcasts, Radio, Recent, Albums, Artists, Playlists, Years, Folders, Continue, New, Favourites, Categories, NoCover }

/** How Android Auto's entries read and look: the texts in the app's language, the artwork's addresses. */
interface CarLook {
    fun text(text: CarText): String

    fun count(count: CarCount, n: Int): String

    /** "1990'lar", "2000'ler", "1990s". */
    fun decade(start: Int): String

    /** A day as the app writes it; null for an unknown one (0). */
    fun date(ms: Long): String?

    fun icon(icon: CarIcon): String

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
     * ([PlaybackRules.shownPositionMs]); the one listened to last first ([listened]: its place in the playback history,
     * 0 the latest; null when it is not there), then the newest.
     */
    fun continuing(episodes: List<PodcastEpisode>, listened: (PodcastEpisode) -> Int?): List<PodcastEpisode> =
        episodes.filter { !it.played && PlaybackRules.shownPositionMs(it.positionMs, it.played, null, durationMs = it.durationMs) > 0 }
            .sortedWith(compareBy<PodcastEpisode> { listened(it) ?: Int.MAX_VALUE }.thenByDescending { it.publishedAt })

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
 * 1.0: Android Auto's browse tree. The root's four tabs: Müzik (albums, artists, playlists, years, the Music section's
 * folders), Podcast (the episodes left in the middle, the subscriptions, the new episodes), Radyo (the favourite stations,
 * those played last, the directory's categories) and Son çalınanlar (the music played last). Long lists are cut into
 * pages of [pageSize]; an empty list says so in one line. Also the search and what an entry plays ([play]).
 */
class CarTree(private val data: CarData, private val look: CarLook, private val pageSize: Int = PAGE) {

    /** The entries under [id]; null when it is none of the tree's. */
    suspend fun children(id: String): List<CarItem>? {
        val ref = CarIds.parse(id) ?: return null
        return when (ref.kind) {
            null -> when (ref.path) {
                CarIds.ROOT -> root()
                CarIds.MUSIC -> music()
                CarIds.ALBUMS -> paged(ref, data.music().groups(MusicView.Albums), { it.title ?: look.text(CarText.UnknownAlbum) }, ::album)
                CarIds.ARTISTS -> paged(ref, data.music().groups(MusicView.Artists), { it.title ?: look.text(CarText.UnknownArtist) }, ::artist)
                CarIds.PLAYLISTS -> filled(data.playlists().map(::playlist))
                CarIds.YEARS -> filled(decades(data.music()))
                CarIds.FOLDERS -> paged(ref, data.music().groups(MusicView.Folders), { folderName(it.title) }, ::folder)
                CarIds.RECENT -> tracks(CarIds.RECENT, ref, data.recentTracks(), album = false)
                CarIds.PODCASTS -> podcasts()
                CarIds.CONTINUE -> episodes(data.continuing(), withShow = true)
                CarIds.SHOWS -> filled(data.feeds().map(::show))
                CarIds.NEW -> episodes(data.fresh(), withShow = true)
                CarIds.RADIO -> radio()
                CarIds.FAVOURITES -> filled(data.favouriteStations().map(::station))
                CarIds.STATIONS_RECENT -> filled(data.recentStations().map(::station))
                CarIds.TAGS -> directory { data.tags().take(MAX_TAGS).map(::tag) }
                else -> null
            }
            CarKind.Album -> groupTracks(ref, MusicView.Albums, album = true)
            CarKind.Artist -> filled(artistAlbums(data.music(), ref.key.orEmpty()).map(::album))
            CarKind.Playlist -> tracks(ref.path, ref, data.playlistTracks(ref.number ?: return emptyList()), album = false)
            CarKind.Decade -> filled(years(data.music(), ref.number?.toInt() ?: return emptyList()))
            CarKind.Year -> groupTracks(ref, MusicView.Years, album = false)
            CarKind.Folder -> groupTracks(ref, MusicView.Folders, album = false)
            CarKind.Show -> {
                val feedId = ref.number ?: return emptyList()
                val episodes = data.episodes(feedId)
                if (episodes.isEmpty()) return listOf(nothing(CarText.Empty))
                val shown = if (episodes.size > pageSize) episodes.take(pageSize) else episodes
                shown.map { episode(it, null) }
            }
            CarKind.Tag -> directory { data.tagged(ref.key.orEmpty()).map(::station) }
            CarKind.Search -> {
                val tracks = searchTracks(ref.key.orEmpty()).take(pageSize)
                tracks.mapIndexed { i, track -> trackItem(CarIds.searchTracks(ref.key.orEmpty()), i, track, album = false) }
            }
            CarKind.Episode, CarKind.Station, CarKind.DirectoryStation -> emptyList()
        }
    }

    /** What the entry [id] plays; null for one that plays nothing (or that is none of the tree's). */
    suspend fun play(id: String): CarPlay? {
        val ref = CarIds.parse(id) ?: return null
        val start = ref.index ?: 0
        return when (ref.kind) {
            null -> when (ref.path) {
                CarIds.RECENT -> CarPlay.Tracks(data.recentTracks(), start)
                else -> null
            }
            CarKind.Album, CarKind.Artist, CarKind.Year, CarKind.Folder -> {
                val view = when (ref.kind) {
                    CarKind.Album -> MusicView.Albums
                    CarKind.Artist -> MusicView.Artists
                    CarKind.Year -> MusicView.Years
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
            CarKind.Search -> CarPlay.Tracks(searchTracks(ref.key.orEmpty()), start)
            CarKind.Episode -> {
                val episode = data.episode(ref.number ?: return null) ?: return null
                val feed = data.feeds().firstOrNull { it.id == episode.feedId } ?: return null
                CarPlay.Episode(feed, episode)
            }
            CarKind.Station -> data.station(ref.number ?: return null)?.let(CarPlay::Radio)
            CarKind.DirectoryStation -> {
                val url = ref.params["u"]?.takeIf { it.isNotBlank() } ?: return null
                CarPlay.Radio(Station(uuid = ref.key, name = ref.params["n"].orEmpty().ifBlank { url }, streamUrl = url, logoUrl = ref.params["l"]))
            }
            CarKind.Decade, CarKind.Show, CarKind.Tag -> null
        }?.takeUnless { it is CarPlay.Tracks && it.tracks.isEmpty() }
    }

    /**
     * Android Auto's search for [query]: music tracks, albums and artists, podcast episodes and shows, then radio stations
     * (the kept ones, then the directory's, given [DIRECTORY_WAIT_MS]); each group under its heading. One line says when
     * nothing answers.
     */
    suspend fun search(query: String): List<CarItem> {
        if (CarVoice.fold(query).isEmpty()) return listOf(nothing(CarText.NoResult))
        val found = ArrayList<CarItem>()
        val library = data.music()
        searchTracks(query).take(MAX_FOUND).forEachIndexed { i, track ->
            found += trackItem(CarIds.searchTracks(query), i, track, album = false).copy(group = look.text(CarText.FoundTracks))
        }
        ranked(query, library.groups(MusicView.Albums)) { it.title }.take(MAX_FOUND_GROUPS)
            .forEach { found += album(it).copy(group = look.text(CarText.FoundAlbums)) }
        ranked(query, library.groups(MusicView.Artists)) { it.title }.take(MAX_FOUND_GROUPS)
            .forEach { found += artist(it).copy(group = look.text(CarText.FoundArtists)) }
        val feeds = data.feeds()
        val byFeed = feeds.associateBy { it.id }
        data.findEpisodes(query, MAX_FOUND).forEach { found += episode(it, byFeed[it.feedId]).copy(group = look.text(CarText.FoundEpisodes)) }
        ranked(query, feeds) { it.title }.take(MAX_FOUND_GROUPS).forEach { found += show(it).copy(group = look.text(CarText.FoundShows)) }
        val kept = (data.favouriteStations() + data.recentStations()).distinctBy { it.key }
        val stations = ranked(query, kept) { it.name }.toMutableList()
        val directory = withTimeoutOrNull(DIRECTORY_WAIT_MS) { quietly { data.findStations(query) } }.orEmpty()
        val keys = stations.mapTo(HashSet()) { it.key }
        stations += directory.filter { it.key !in keys }
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

    private fun music() = listOf(
        tab(CarIds.ALBUMS, CarText.Albums, CarIcon.Albums, CarStyle.Grid),
        tab(CarIds.ARTISTS, CarText.Artists, CarIcon.Artists, CarStyle.Grid),
        tab(CarIds.PLAYLISTS, CarText.Playlists, CarIcon.Playlists, CarStyle.Grid),
        tab(CarIds.YEARS, CarText.Years, CarIcon.Years, CarStyle.List),
        tab(CarIds.FOLDERS, CarText.Folders, CarIcon.Folders, CarStyle.List),
    )

    private fun podcasts() = listOf(
        tab(CarIds.CONTINUE, CarText.Continue, CarIcon.Continue, CarStyle.List),
        tab(CarIds.SHOWS, CarText.Subscriptions, CarIcon.Podcasts, CarStyle.Grid),
        tab(CarIds.NEW, CarText.NewEpisodes, CarIcon.New, CarStyle.List),
    )

    private fun radio() = listOf(
        tab(CarIds.FAVOURITES, CarText.FavouriteStations, CarIcon.Favourites, CarStyle.Grid),
        tab(CarIds.STATIONS_RECENT, CarText.RecentStations, CarIcon.Recent, CarStyle.Grid),
        tab(CarIds.TAGS, CarText.Categories, CarIcon.Categories, CarStyle.List),
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

    /** The group of [view] [ref] names; a year's by its number (0 for no year). */
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

    /** [tracks] as the list [listId], in pages when it is long; [album]: an album's own, whose tracks need no album line. */
    private fun tracks(listId: String, ref: CarRef, tracks: List<Track>, album: Boolean): List<CarItem> {
        if (tracks.isEmpty()) return listOf(nothing(CarText.Empty))
        val page = ref.page
        if (page == null && tracks.size > pageSize) return pages(listId, tracks.map { it.title })
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

    /** [groups] in pages of [pageSize] when there are more, each page named by its first and last title. */
    private fun <T> paged(ref: CarRef, groups: List<T>, title: (T) -> String, item: (T) -> CarItem): List<CarItem> {
        if (groups.isEmpty()) return listOf(nothing(CarText.Empty))
        val page = ref.page
        if (page == null && groups.size > pageSize) return pages(ref.path, groups.map(title))
        return groups.drop((page ?: 0) * pageSize).take(pageSize).map(item)
    }

    private fun pages(listId: String, titles: List<String>): List<CarItem> = titles.chunked(pageSize).mapIndexed { page, chunk ->
        CarItem(id = CarIds.page(listId, page), title = "${chunk.first()} – ${chunk.last()}", browsable = true)
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

    private fun station(station: Station): CarItem {
        val id = when {
            station.id != 0L -> CarIds.station(station.id)
            else -> CarIds.directoryStation(station.uuid.orEmpty(), station.name, station.streamUrl, station.logoUrl)
        }
        return CarItem(
            id = id,
            title = station.name,
            subtitle = listOfNotNull(station.country ?: station.countryCode, station.tags.firstOrNull()).joinToString(" · ").ifEmpty { null },
            iconUri = look.webImage(station.logoUrl),
            playable = true,
        )
    }

    private fun tag(tag: DirectoryTag) = CarItem(
        id = CarIds.tag(tag.name),
        title = tag.name.replaceFirstChar { it.titlecase() },
        subtitle = look.count(CarCount.Stations, tag.stations),
        iconUri = look.icon(CarIcon.Categories),
        browsable = true,
        children = CarStyle.Grid,
    )

    /** The directory's answer, or one line saying it cannot be reached. */
    private suspend fun directory(read: suspend () -> List<CarItem>): List<CarItem> = try {
        filled(read())
    } catch (e: RadioFailure) {
        listOf(nothing(CarText.RadioOffline))
    }

    private fun filled(items: List<CarItem>): List<CarItem> = items.ifEmpty { listOf(nothing(CarText.Empty)) }

    /** A line that says why a list is empty; it opens and plays nothing. */
    private fun nothing(text: CarText) = CarItem(id = CarIds.NOTHING, title = look.text(text), iconUri = look.icon(CarIcon.NoCover), playable = true)

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

        const val MAX_FOUND = 30
        const val MAX_FOUND_GROUPS = 10

        /** How long a search waits for the radio directory. */
        const val DIRECTORY_WAIT_MS = 4_000L

        /** [block]'s answer, or null when the directory could not give one. */
        internal suspend fun <T> quietly(block: suspend () -> T): T? = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RadioFailure) {
            null
        }
    }
}
