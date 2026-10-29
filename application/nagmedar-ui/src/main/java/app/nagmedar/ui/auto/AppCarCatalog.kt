package app.nagmedar.ui.auto

import android.content.Context
import android.net.Uri
import android.os.Build
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.library.MediaRepository
import app.nagmedar.ui.music.MediaLibraryPlaylists
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.music.toTrack
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.podcast.DownloadFiles
import app.nagmedar.ui.podcast.EpisodeMedia
import app.nagmedar.ui.podcast.PodcastCovers
import app.nagmedar.ui.radio.DirectoryTag
import app.nagmedar.ui.radio.RadioCenter
import app.nagmedar.ui.radio.Station
import app.nagmedar.ui.radio.StationFilters
import app.nagmedar.ui.radio.StationOrder
import app.nagmedar.ui.radio.StationQuery
import app.nagmedar.ui.radio.toStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.util.getFromMl
import org.videolan.tools.getResourceUri
import org.videolan.vlc.ArtworkProvider
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.CarCatalog
import org.videolan.vlc.media.CarHook
import org.videolan.vlc.media.CarItem
import org.videolan.vlc.media.PlaybackObserver
import org.videolan.vlc.media.VoiceRequest
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * 1.0: Android Auto's catalog for as long as the playback service lives (Auto binds to it): set into [CarHook], where the
 * service's browser and session callback find it. Found by name by [PlaybackObserver]; the app's keep rules keep it.
 */
class CarCatalogObserver : PlaybackObserver {
    override fun attach(service: PlaybackService) {
        // the radio directory and the logos are asked in the app; Auto may start the service without its window
        if (Build.FINGERPRINT != "robolectric") RadioCenter.active = true
        CarHook.catalog = AppCarCatalog(service.applicationContext)
    }

    override fun detach() {
        CarHook.catalog = null
    }
}

/** [CarCatalog] over the app's music, podcasts and radio ([AppCarData]); plays as the app's pages do. */
class AppCarCatalog(context: Context) : CarCatalog {
    private val data = AppCarData(context.applicationContext)

    /** The tree in the language of [context] (the service's, which follows the app's language). */
    private fun tree(context: Context) = CarTree(data, AppCarLook(context, data::media))

    override suspend fun children(context: Context, parentId: String): List<CarItem>? =
        withContext(Dispatchers.IO) { tree(context).children(parentId) }

    override suspend fun search(context: Context, query: String): List<CarItem> =
        withContext(Dispatchers.IO) { tree(context).search(query) }

    override suspend fun play(service: PlaybackService, mediaId: String): Boolean {
        val what = withContext(Dispatchers.IO) { tree(service).play(mediaId) } ?: return false
        return start(service, what)
    }

    override suspend fun play(service: PlaybackService, request: VoiceRequest): Boolean {
        if (request.isEmpty) {
            // "çal": what played last plays on
            withContext(Dispatchers.Main) { if (service.hasMedia()) service.play() else PlaybackService.loadLastAudio(service, playOnly = true) }
            return true
        }
        val what = withContext(Dispatchers.IO) { CarVoiceSearch(data).resolve(request) } ?: return false
        return start(service, what)
    }

    @Volatile
    private var toldAt = 0L

    /** The music is read again when next asked for; Auto is told at most every [TELL_EVERY_MS], as a scan changes it at every file. */
    override fun libraryChanged(history: Boolean): List<String> {
        if (history) return listOf(CarIds.RECENT, CarIds.CONTINUE)
        data.forgetMusic()
        val now = System.currentTimeMillis()
        if (now - toldAt < TELL_EVERY_MS) return emptyList()
        toldAt = now
        return listOf(CarIds.ALBUMS, CarIds.ARTISTS, CarIds.PLAYLISTS, CarIds.YEARS, CarIds.FOLDERS, CarIds.RECENT)
    }

    /** Plays [what] the way the app's pages play it: the music's list with its source, an episode's media, a station by the radio's player. */
    private suspend fun start(service: PlaybackService, what: CarPlay): Boolean = when (what) {
        is CarPlay.Tracks -> {
            val kept = what.tracks.withIndex().mapNotNull { (i, track) -> data.media(track)?.let { i to it } }
            if (kept.isEmpty()) {
                false
            } else {
                // a track the medialibrary no longer has is left out; the one asked for keeps its place
                val start = kept.indexOfFirst { it.first >= what.start }.coerceAtLeast(0)
                withContext(Dispatchers.Main) { PlaybackActions.playList(service, kept.map { it.second }, start, what.source) }
                true
            }
        }
        is CarPlay.Episode -> {
            val media = withContext(Dispatchers.IO) { EpisodeMedia.of(what.feed, what.episode) }
            withContext(Dispatchers.Main) { PlaybackActions.playList(service, listOf(media), 0) }
            true
        }
        is CarPlay.Radio -> {
            try {
                RadioCenter.player(service).start(what.station)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // the station plays even when it could not be noted as played
            }
            true
        }
    }

    private companion object {
        const val TELL_EVERY_MS = 10_000L
    }
}

/**
 * [CarData] in the app: the Music page's tracks read once and kept until the medialibrary changes (or for
 * [MUSIC_KEEP_MS]), the playlists of the medialibrary, the podcasts in Room, the stations kept and the radio directory,
 * whose categories are kept for [TAGS_KEEP_MS] and their stations for [TAGGED_KEEP_MS].
 */
class AppCarData(private val context: Context) : CarData {
    private val repository = MediaRepository(context)
    private val playlistStore = MediaLibraryPlaylists(context)
    private val podcasts get() = NagmedarDatabase.get(context).podcasts()

    /** The medialibrary's items of the tracks read, by uri: what playing them needs. */
    private val items = ConcurrentHashMap<String, MediaWrapper>()
    private val musicLock = Mutex()

    @Volatile
    private var music: Kept<MusicLibrary>? = null

    @Volatile
    private var tags: Kept<List<DirectoryTag>>? = null
    private val tagged = ConcurrentHashMap<String, Kept<List<Station>>>()

    private class Kept<T>(val value: T, val at: Long)

    fun media(track: Track): MediaWrapper? = items[track.uri]

    /** The medialibrary changed: the music is read again when it is next asked for. */
    fun forgetMusic() {
        music = null
    }

    override suspend fun music(): MusicLibrary = musicLock.withLock {
        music?.takeIf { System.currentTimeMillis() - it.at < MUSIC_KEEP_MS }?.value ?: run {
            val files = repository.tracks(LibrarySection.Music)
            val library = withContext(Dispatchers.Default) {
                files.forEach { item -> item.uri?.toString()?.let { items[it] = item } }
                MusicLibrary(files.map { it.toTrack(null) }, context.resources.locale())
            }
            music = Kept(library, System.currentTimeMillis())
            library
        }
    }

    override suspend fun recentTracks(): List<Track> = repository.recentAudio().map { item ->
        item.uri?.toString()?.let { items[it] = item }
        item.toTrack(null)
    }

    override suspend fun playlists(): List<PlaylistSummary> = playlistStore.playlists()

    override suspend fun playlistTracks(id: Long): List<Track> = playlistStore.tracks(id).also { items.putAll(playlistStore.media) }

    override suspend fun feeds(): List<PodcastFeed> = withContext(Dispatchers.IO) { podcasts.feedList() }

    override suspend fun episodes(feedId: Long): List<PodcastEpisode> = withContext(Dispatchers.IO) { podcasts.episodeList(feedId) }

    override suspend fun episode(id: Long): PodcastEpisode? = withContext(Dispatchers.IO) { podcasts.episode(id) }

    /** The episodes left in the middle, in the order the playback history has them (the latest first). */
    override suspend fun continuing(): List<PodcastEpisode> {
        val episodes = withContext(Dispatchers.IO) { podcasts.inProgress() }
        if (episodes.isEmpty()) return emptyList()
        val history = context.getFromMl { history(Medialibrary.HISTORY_TYPE_GLOBAL)?.toList().orEmpty() }
        val listened = HashMap<String, Int>()
        history.forEachIndexed { i, item -> (item.location ?: item.uri?.toString())?.let { listened.putIfAbsent(it, i) } }
        return CarPodcasts.continuing(episodes) { episode ->
            listOfNotNull(listened[episode.audioUrl], episode.downloadPath?.let { listened[DownloadFiles.locationOf(it)] }).minOrNull()
        }
    }

    override suspend fun fresh(): List<PodcastEpisode> = withContext(Dispatchers.IO) { podcasts.newEpisodeList(MAX_NEW) }

    override suspend fun findEpisodes(query: String, limit: Int): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val folded = CarVoice.fold(query)
        if (folded.isEmpty()) return@withContext emptyList()
        val found = podcasts.episodeTitles()
            .mapNotNull { row -> CarVoice.foldedScore(folded, CarVoice.fold(row.title)).takeIf { it >= CarVoice.WORDS }?.let { row.id to it } }
            .sortedByDescending { it.second }
            .take(limit)
        if (found.isEmpty()) return@withContext emptyList()
        val rows = podcasts.episodesById(found.map { it.first }).associateBy { it.id }
        found.mapNotNull { rows[it.first] }
    }

    override suspend fun favouriteStations(): List<Station> = RadioCenter.library(context).favourites().first()

    override suspend fun recentStations(): List<Station> = RadioCenter.library(context).recent().first()

    override suspend fun station(id: Long): Station? = withContext(Dispatchers.IO) { NagmedarDatabase.get(context).radio().byId(id)?.toStation() }

    override suspend fun tags(): List<DirectoryTag> {
        tags?.takeIf { System.currentTimeMillis() - it.at < TAGS_KEEP_MS }?.let { return it.value }
        return RadioCenter.directory(context).tags().also { tags = Kept(it, System.currentTimeMillis()) }
    }

    override suspend fun tagged(tag: String): List<Station> {
        tagged[tag]?.takeIf { System.currentTimeMillis() - it.at < TAGGED_KEEP_MS }?.let { return it.value }
        val query = StationQuery(filters = StationFilters(tag = tag), order = StationOrder.Votes, limit = MAX_TAGGED)
        return RadioCenter.directory(context).search(query).also {
            if (tagged.size >= MAX_TAGS_KEPT) tagged.clear()
            tagged[tag] = Kept(it, System.currentTimeMillis())
        }
    }

    override suspend fun findStations(query: String): List<Station> =
        RadioCenter.directory(context).search(StationQuery(text = query, order = StationOrder.Votes, limit = MAX_FOUND_STATIONS))

    private companion object {
        const val MUSIC_KEEP_MS = 10 * 60_000L
        const val TAGS_KEEP_MS = 6 * 60 * 60_000L
        const val TAGGED_KEEP_MS = 30 * 60_000L
        const val MAX_TAGS_KEPT = 30
        const val MAX_TAGGED = 50
        const val MAX_FOUND_STATIONS = 20
        const val MAX_NEW = 100
    }
}

/** [CarLook] in the app: its texts in [context]'s language, the drawables' and the artwork provider's addresses. */
class AppCarLook(private val context: Context, private val media: (Track) -> MediaWrapper?) : CarLook {
    private val res = context.resources

    override fun text(text: CarText): String = res.getString(
        when (text) {
            CarText.Music -> R.string.nag_nav_music
            CarText.Podcasts -> R.string.nag_nav_podcasts_short
            CarText.Radio -> R.string.nag_nav_radio
            CarText.Recent -> R.string.nag_music_view_recent
            CarText.Albums -> R.string.nag_music_view_albums
            CarText.Artists -> R.string.nag_music_view_artists
            CarText.Playlists -> R.string.nag_nav_playlists
            CarText.Years -> R.string.nag_music_view_years
            CarText.Folders -> R.string.nag_music_view_folders
            CarText.Continue -> R.string.nag_auto_continue
            CarText.Subscriptions -> R.string.nag_auto_subscriptions
            CarText.NewEpisodes -> R.string.nag_auto_new_episodes
            CarText.FavouriteStations -> R.string.nag_auto_favourite_stations
            CarText.RecentStations -> R.string.nag_radio_tab_recent
            CarText.Categories -> R.string.nag_auto_categories
            CarText.UnknownAlbum -> R.string.nag_music_unknown_album
            CarText.UnknownArtist -> R.string.nag_unknown_artist
            CarText.UnknownYear -> R.string.nag_music_unknown_year
            CarText.UnknownFolder -> R.string.nag_music_unknown_folder
            CarText.FoundTracks -> R.string.nag_auto_found_tracks
            CarText.FoundAlbums -> R.string.nag_music_view_albums
            CarText.FoundArtists -> R.string.nag_music_view_artists
            CarText.FoundEpisodes -> R.string.nag_auto_found_episodes
            CarText.FoundShows -> R.string.nag_auto_found_shows
            CarText.FoundStations -> R.string.nag_auto_found_stations
            CarText.Empty -> R.string.nag_auto_empty
            CarText.RadioOffline -> R.string.nag_auto_radio_offline
            CarText.NoResult -> R.string.nag_auto_no_result
        },
    )

    override fun count(count: CarCount, n: Int): String = res.getQuantityString(
        when (count) {
            CarCount.Tracks -> R.plurals.nag_tracks_count
            CarCount.Albums -> R.plurals.nag_music_albums
            CarCount.Episodes -> R.plurals.nag_pod_episodes
            CarCount.Stations -> R.plurals.nag_auto_stations
        },
        n,
        n,
    )

    override fun decade(start: Int): String =
        res.getString(if (CarDecades.frontVowel(start)) R.string.nag_auto_decade_front else R.string.nag_auto_decade_back, start)

    override fun date(ms: Long): String? = if (ms <= 0) null else DateFormat.getDateInstance(DateFormat.MEDIUM, res.locale()).format(Date(ms))

    override fun icon(icon: CarIcon): String = res.getResourceUri(
        when (icon) {
            CarIcon.Music -> org.videolan.resources.R.drawable.nagmedar_auto_music
            CarIcon.Podcasts -> org.videolan.resources.R.drawable.nagmedar_auto_podcasts
            CarIcon.Radio -> org.videolan.resources.R.drawable.nagmedar_auto_radio
            CarIcon.Recent -> org.videolan.resources.R.drawable.nagmedar_auto_recent
            CarIcon.Albums -> org.videolan.resources.R.drawable.nagmedar_auto_albums
            CarIcon.Artists -> org.videolan.resources.R.drawable.nagmedar_auto_artists
            CarIcon.Playlists -> org.videolan.resources.R.drawable.nagmedar_auto_playlists
            CarIcon.Years -> org.videolan.resources.R.drawable.nagmedar_auto_years
            CarIcon.Folders -> org.videolan.resources.R.drawable.nagmedar_auto_folders
            CarIcon.Continue -> org.videolan.resources.R.drawable.nagmedar_auto_continue
            CarIcon.New -> org.videolan.resources.R.drawable.nagmedar_auto_new
            CarIcon.Favourites -> org.videolan.resources.R.drawable.nagmedar_auto_favourites
            CarIcon.Categories -> org.videolan.resources.R.drawable.nagmedar_auto_categories
            CarIcon.NoCover -> ArtworkProvider.noCover(MediaWrapper.TYPE_AUDIO)
        },
    ).toString()

    override fun cover(track: Track?): String =
        track?.let(media)?.let { ArtworkProvider.buildMediaUri(context, it).toString() } ?: icon(CarIcon.NoCover)

    override fun playlistCover(id: Long): String =
        ArtworkProvider.buildUri(context, Uri.Builder().appendPath(ArtworkProvider.PLAYLIST).appendPath("$id").build()).toString()

    /** A web image through the artwork provider, which keeps what it fetched: a show's cover from the file the app keeps of it. */
    override fun webImage(url: String?): String {
        if (url.isNullOrBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) return icon(CarIcon.NoCover)
        val path = runCatching { PodcastCovers.keptFile(context, url)?.path }.getOrNull() ?: url
        return ArtworkProvider.buildUri(context, Uri.Builder().appendPath(ArtworkProvider.REMOTE).appendQueryParameter(ArtworkProvider.PATH, path).build()).toString()
    }
}
