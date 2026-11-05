package app.nagmedar.ui.auto

import android.content.Context
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.library.MediaRepository
import app.nagmedar.ui.music.DatabaseSmartLists
import app.nagmedar.ui.music.MediaLibraryPlaylists
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.music.SmartList
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.music.TrackTechIndex
import app.nagmedar.ui.music.toTrack
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.podcast.ApplePodcasts
import app.nagmedar.ui.podcast.ChartGenre
import app.nagmedar.ui.podcast.ChartShow
import app.nagmedar.ui.podcast.DownloadFiles
import app.nagmedar.ui.podcast.EpisodeMedia
import app.nagmedar.ui.podcast.FeedFailure
import app.nagmedar.ui.podcast.FeedFetch
import app.nagmedar.ui.podcast.FeedUrl
import app.nagmedar.ui.podcast.InvalidReason
import app.nagmedar.ui.podcast.ListenedEpisodes
import app.nagmedar.ui.podcast.OkHttpFeedSource
import app.nagmedar.ui.podcast.ParsedFeed
import app.nagmedar.ui.podcast.PodcastCenter
import app.nagmedar.ui.podcast.PodcastHit
import app.nagmedar.ui.podcast.SubscribeResult
import app.nagmedar.ui.podcast.describe
import app.nagmedar.ui.radio.RadioLabels
import app.nagmedar.ui.radio.DirectoryTag
import app.nagmedar.ui.radio.RadioCenter
import app.nagmedar.ui.radio.RadioSession
import app.nagmedar.ui.radio.Station
import app.nagmedar.ui.radio.StationFilters
import app.nagmedar.ui.radio.StationOrder
import app.nagmedar.ui.radio.StationQuery
import app.nagmedar.ui.radio.toStation
import app.nagmedar.ui.stats.TrackStatsIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.util.getFromMl
import org.videolan.tools.getResourceUri
import org.videolan.vlc.ArtworkProvider
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.CarAction
import org.videolan.vlc.media.CarActionResult
import org.videolan.vlc.media.CarCatalog
import org.videolan.vlc.media.CarHook
import org.videolan.vlc.media.CarItem
import org.videolan.vlc.media.PlaybackObserver
import org.videolan.vlc.media.VoiceRequest
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 1.0: Android Auto's catalog for as long as the playback service lives (Auto binds to it): set into [CarHook], where the
 * service's browser and session callback find it. Found by name by [PlaybackObserver]; the app's keep rules keep it.
 */
class CarCatalogObserver : PlaybackObserver {
    private var catalog: AppCarCatalog? = null

    override fun attach(service: PlaybackService) {
        // the radio directory and the logos are asked in the app; Auto may start the service without its window
        if (Build.FINGERPRINT != "robolectric") RadioCenter.active = true
        catalog?.release()
        // what a later change tells Auto goes to the service that lives then, never held here
        val made = AppCarCatalog(service.applicationContext) { ids -> PlaybackService.instance?.let { live -> ids.forEach(live::notifyChildrenChanged) } }
        catalog = made
        CarHook.catalog = made
    }

    override fun detach() {
        catalog?.release()
        catalog = null
        CarHook.catalog = null
    }
}

/**
 * [CarCatalog] over the app's music, podcasts and radio ([AppCarData]); plays as the app's pages do. [tell] tells Auto
 * that lists changed after the call that changed them (on the main thread): the end of a burst of medialibrary changes,
 * music read after Auto was told it could not be.
 */
class AppCarCatalog(context: Context, private val tell: (List<String>) -> Unit = {}) : CarCatalog {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val data = AppCarData(context.applicationContext, scope) { tellLater(musicLists()) }

    /** The tree in the language of [context] (the service's, which follows the app's language). */
    private fun tree(context: Context) = CarTree(data, AppCarLook(context, data::media))

    /** The lists of music Auto asked for lately (an album's tracks, a page of a long list, a search's tracks), the latest last. */
    private val asked = LinkedHashSet<String>()

    override suspend fun children(context: Context, parentId: String): List<CarItem>? {
        if (parentId.startsWith(CarIds.MUSIC) || parentId.startsWith(CarIds.RECENT) || parentId.startsWith(CarIds.SEARCH)) {
            synchronized(asked) {
                asked.remove(parentId)
                asked += parentId
                if (asked.size > MAX_ASKED) asked.remove(asked.first())
            }
        }
        return withContext(Dispatchers.IO) { tree(context).children(parentId) }
    }

    override suspend fun search(context: Context, query: String): List<CarItem> =
        withContext(Dispatchers.IO) { tree(context).search(query) }

    override suspend fun play(service: PlaybackService, mediaId: String): Boolean {
        val what = withContext(Dispatchers.IO) { tree(service).play(mediaId) } ?: return false
        if (what is CarPlay.Subscribe) {
            // 1.0.3: the row "Abone ol" plays nothing: it subscribes, and Auto shows how that went
            val done = subscribe(service, what.show)
            withContext(Dispatchers.Main) { service.displayCarMessage(done.message) }
            return true
        }
        return start(service, what, mediaId)
    }

    /** 1.0.3: "Abone ol", the browse action Auto offers on a show of the directory not followed. */
    override fun browseActions(context: Context): List<CarAction> =
        listOf(CarAction(CarActions.SUBSCRIBE, context.getString(R.string.nag_pod_subscribe), AppCarLook(context, data::media).icon(CarIcon.Subscribe)))

    override suspend fun item(context: Context, mediaId: String): CarItem? = withContext(Dispatchers.IO) { tree(context).item(mediaId) }

    /** 1.0.3: "Abone ol" on a show of the directory: subscribed as the Podcasts page subscribes; Auto reads the show again. */
    override suspend fun act(context: Context, action: String, mediaId: String): CarActionResult? {
        if (action != CarActions.SUBSCRIBE) return null
        val show = tree(context).subscription(mediaId) ?: return null
        return CarActionResult(subscribe(context, show).message, refresh = mediaId)
    }

    /**
     * 1.0.3: subscribes to [show] through the app's own path ([app.nagmedar.ui.podcast.PodcastRepository.subscribe], with
     * what the directory said of it): Auto is told that Programlarım, the new episodes and the show's own page changed
     * (it opens on its episodes now, without the row "Abone ol"), and the refresh job is scheduled. The result says, in
     * [context]'s language, that it worked or why not.
     */
    private suspend fun subscribe(context: Context, show: PodcastHit): CarActionResult {
        val app = context.applicationContext
        val result = withContext(Dispatchers.IO) { PodcastCenter.repository(app).subscribe(show.feedUrl, show) }
        val res = context.resources
        val message = when (result) {
            is SubscribeResult.Added -> res.getString(R.string.nag_pod_subscribed_to, result.feed.title)
            is SubscribeResult.Already -> res.getString(R.string.nag_pod_already, result.feed.title)
            is SubscribeResult.Failed -> res.getString(R.string.nag_auto_subscribe_failed, result.failure.describe(res))
        }
        if (result is SubscribeResult.Added) {
            PodcastCenter.schedule(app)
            tellNow(listOf(CarIds.SHOWS, CarIds.NEW, CarIds.directoryShow(show.feedUrl, show.title, show.author, show.imageUrl)))
        }
        return CarActionResult(message)
    }

    override suspend fun play(service: PlaybackService, request: VoiceRequest): Boolean {
        if (request.isEmpty) {
            // "çal": what played last plays on
            withContext(Dispatchers.Main) { if (service.hasMedia()) service.play() else PlaybackService.loadLastAudio(service, playOnly = true) }
            return true
        }
        val what = withContext(Dispatchers.IO) { CarVoiceSearch(data).resolve(request) } ?: return false
        return start(service, what, null)
    }

    @Volatile
    private var toldAt = 0L

    @Volatile
    private var trailing: Job? = null

    /**
     * The music is read again when next asked for. Auto is told at most every [TELL_EVERY_MS], as a scan changes it at
     * every file; what a burst leaves untold is told once it is over. Told: the music's lists and those Auto opened.
     */
    override fun libraryChanged(history: Boolean): List<String> {
        if (history) return listOf(CarIds.RECENT, CarIds.CONTINUE)
        data.forgetMusic()
        val wait = TELL_EVERY_MS - (System.currentTimeMillis() - toldAt)
        if (wait > 0) {
            if (trailing?.isActive != true) {
                trailing = scope.launch {
                    delay(wait)
                    toldAt = System.currentTimeMillis()
                    tellNow(musicLists())
                }
            }
            return emptyList()
        }
        toldAt = System.currentTimeMillis()
        trailing?.cancel()
        return musicLists()
    }

    /**
     * The lists of music: the Music tab (1.0.3: its Genres come and go with the music's genres) and its own lists, the
     * tracks played last, and those Auto opened.
     */
    private fun musicLists(): List<String> {
        val opened = synchronized(asked) { asked.toList() }
        val own = listOf(CarIds.MUSIC, CarIds.ALBUMS, CarIds.ARTISTS, CarIds.GENRES, CarIds.PLAYLISTS, CarIds.YEARS, CarIds.FOLDERS, CarIds.TRACKS, CarIds.RECENT)
        return (own + opened).distinct()
    }

    private fun tellLater(ids: List<String>) {
        scope.launch { tellNow(ids) }
    }

    private suspend fun tellNow(ids: List<String>) {
        withContext(Dispatchers.Main) { tell(ids) }
    }

    override fun nothingText(context: Context, mediaId: String?): String {
        val reason = mediaId?.let { CarIds.parse(it)?.params?.get("t") }?.let { name -> CarText.entries.firstOrNull { it.name == name } }
        return AppCarLook(context, data::media).text(reason ?: CarText.NoResult)
    }

    /** What Auto played last, by the address each of its media plays from: the tree's id of each. */
    @Volatile
    private var played: Map<String, String> = emptyMap()

    override fun playingId(location: String?): String? {
        if (location == null) return null
        played[location]?.let { return it }
        PodcastCenter.playing.value?.let { return CarIds.episode(it.episodeId) }
        return RadioSession.playing.value?.takeIf { it.url == location }?.station?.takeIf { it.id != 0L }?.let { CarIds.station(it.id) }
    }

    /** The catalog's work stops: the service is going. */
    fun release() {
        scope.cancel()
    }

    /**
     * Plays [what] the way the app's pages play it: the music's list with its source, an episode's media, a station by the
     * radio's player. [mediaId]: the entry of the tree it was played from (none for a voice request), which Auto then
     * marks as playing ([playingId]).
     */
    private suspend fun start(service: PlaybackService, what: CarPlay, mediaId: String?): Boolean = when (what) {
        is CarPlay.Tracks -> {
            val kept = what.tracks.withIndex().mapNotNull { (i, track) -> data.media(track)?.let { i to it } }
            if (kept.isEmpty()) {
                false
            } else {
                // a track the medialibrary no longer has is left out; the one asked for keeps its place
                val start = kept.indexOfFirst { it.first >= what.start }.coerceAtLeast(0)
                val list = mediaId?.substringBefore('?')
                played = if (list == null) emptyMap() else kept.mapNotNull { (i, media) -> media.location?.let { it to CarIds.track(list, i) } }.toMap()
                withContext(Dispatchers.Main) { PlaybackActions.playList(service, kept.map { it.second }, start, what.source) }
                true
            }
        }
        is CarPlay.Episode -> {
            val media = withContext(Dispatchers.IO) { EpisodeMedia.of(what.feed, what.episode) }
            played = media.location?.let { mapOf(it to CarIds.episode(what.episode.id)) }.orEmpty()
            withContext(Dispatchers.Main) { PlaybackActions.playList(service, listOf(media), 0) }
            true
        }
        // subscribing is done by play() itself
        is CarPlay.Subscribe -> false
        is CarPlay.Radio -> {
            played = emptyMap()
            val kept = try {
                RadioCenter.player(service).start(what.station)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // the station plays even when it could not be noted as played
                null
            }
            val id = mediaId ?: kept?.takeIf { it.id != 0L }?.let { CarIds.station(it.id) }
            val url = kept?.streamUrl ?: RadioSession.playing.value?.url
            if (id != null && url != null) played = mapOf(url to id)
            true
        }
    }

    private companion object {
        const val TELL_EVERY_MS = 10_000L

        /** The lists of music Auto opened that are told of a change. */
        const val MAX_ASKED = 64
    }
}

/**
 * [CarData] in the app: the Music page's tracks read once and kept until the medialibrary changes (or for
 * [MUSIC_KEEP_MS]), the playlists of the medialibrary (1.0.3: and the smart lists, their tracks chosen once from the
 * music kept), the podcasts in Room, the stations kept and the radio directory,
 * whose categories are kept for [TAGS_KEEP_MS] and their stations for [TAGGED_KEEP_MS]. What the medialibrary answers
 * is waited for [ML_WAIT_MS] at most ([MusicUnavailable]); the music goes on being read in [scope] and [musicLate] is
 * called once it is, when a wait gave up on it.
 */
class AppCarData(private val context: Context, private val scope: CoroutineScope, private val musicLate: () -> Unit = {}) : CarData {
    private val repository = MediaRepository(context)
    private val playlistStore = MediaLibraryPlaylists(context)
    private val smartStore = DatabaseSmartLists(context)
    private val podcasts get() = NagmedarDatabase.get(context).podcasts()

    /** The medialibrary's items of the tracks read, by uri: what playing them needs. Let go with the music read. */
    @Volatile
    private var items = ConcurrentHashMap<String, MediaWrapper>()
    private val musicLock = Mutex()

    /** The music read: the library, the ids of the files it was read from, when; the smart lists' tracks chosen from it. */
    private class Snapshot(val library: MusicLibrary, val files: Set<Long>, val at: Long) {
        val smart = ConcurrentHashMap<SmartList, List<Track>>()
    }

    @Volatile
    private var music: Snapshot? = null

    /** The reading of the music under way; [generation] tells one begun before the medialibrary last changed. */
    @Volatile
    private var reading: Deferred<Snapshot>? = null
    private val generation = AtomicInteger()

    @Volatile
    private var tags: Kept<List<DirectoryTag>>? = null
    private val tagged = ConcurrentHashMap<String, Kept<List<Station>>>()

    /** 1.0.3: Keşfet's charts by country, and the feeds of shows not followed by address, each kept a while. */
    private val charts = ConcurrentHashMap<String, Kept<List<ChartShow>>>()
    private val read = ConcurrentHashMap<String, Kept<ParsedFeed>>()
    private val feedSource by lazy { OkHttpFeedSource(PodcastCenter.userAgent()) }

    private class Kept<T>(val value: T, val at: Long)

    fun media(track: Track): MediaWrapper? = items[track.uri]

    /** The medialibrary changed: the music is read again when it is next asked for; a reading under way is not kept. */
    fun forgetMusic() {
        generation.incrementAndGet()
        music = null
        reading = null
        items = ConcurrentHashMap()
    }

    override suspend fun music(): MusicLibrary = snapshot().library

    /** The music read and kept, else read now: waited for [ML_WAIT_MS] at most, the reading going on behind. */
    private suspend fun snapshot(): Snapshot {
        keptMusic()?.let { return it }
        val job = musicLock.withLock {
            keptMusic()?.let { return it }
            reading?.takeIf { it.isActive } ?: generation.get().let { begun -> scope.async { read(begun) } }.also { reading = it }
        }
        return withTimeoutOrNull(ML_WAIT_MS) { job.await() } ?: throw MusicUnavailable()
    }

    private fun keptMusic(): Snapshot? = music?.takeIf { System.currentTimeMillis() - it.at < MUSIC_KEEP_MS }

    /** Reads the music; kept when the medialibrary did not change since [begun]. */
    private suspend fun read(begun: Int): Snapshot {
        val started = System.currentTimeMillis()
        val (files, ids) = repository.tracksOfFiles(LibrarySection.Music)
        val found = ConcurrentHashMap<String, MediaWrapper>()
        val snapshot = withContext(Dispatchers.Default) {
            files.forEach { item -> item.uri?.toString()?.let { found[it] = item } }
            Snapshot(MusicLibrary(files.map { it.toTrack(null) }, context.resources.locale()), ids, System.currentTimeMillis())
        }
        if (generation.get() == begun) {
            items = found
            music = snapshot
            // Auto was told the music could not be read: it is now
            if (snapshot.at - started > ML_WAIT_MS) musicLate()
        }
        return snapshot
    }

    /** [block]'s answer from the medialibrary, given [ML_WAIT_MS]; [MusicUnavailable] when it does not answer in time. */
    private suspend fun <T : Any> fromMl(block: suspend () -> T): T = withTimeoutOrNull(ML_WAIT_MS) { block() } ?: throw MusicUnavailable()

    /** The history, kept to the music read: no listing of the whole library at each track played. */
    override suspend fun recentTracks(): List<Track> {
        val played = fromMl { repository.recentAudio { snapshot().files } }
        return played.map { item ->
            item.uri?.toString()?.let { items[it] = item }
            item.toTrack(null)
        }
    }

    override suspend fun playlists(): List<PlaylistSummary> = fromMl { playlistStore.playlists() }

    override suspend fun playlistTracks(id: Long): List<Track> = fromMl { playlistStore.tracks(id) }.also { items.putAll(playlistStore.media) }

    override suspend fun smartLists(): List<SmartList> = smartStore.lists.first()

    /**
     * The tracks [list] takes, chosen as the Playlists page chooses them: from the music kept, with what the probe read
     * (a CUE sheet's track has its file's) and the ratings and plays of the database. Chosen once for the music kept
     * (a list changed is chosen again): the track a list's line plays is the one Auto showed there.
     */
    override suspend fun smartTracks(list: SmartList): List<Track> {
        val music = snapshot()
        music.smart[list]?.let { return it }
        val tech = TrackTechIndex.read(context)
        val stats = TrackStatsIndex.read(context)
        val chosen = withContext(Dispatchers.Default) {
            val tracks = music.library.tracks.map { track -> tech[CueUri.file(track.uri)]?.let { track.copy(tech = it) } ?: track }
            list.select(tracks, stats, System.currentTimeMillis(), music.library.locale)
        }
        return music.smart.putIfAbsent(list, chosen) ?: chosen
    }

    override suspend fun feeds(): List<PodcastFeed> = withContext(Dispatchers.IO) { podcasts.feedList() }

    override suspend fun episodes(feedId: Long): List<PodcastEpisode> = withContext(Dispatchers.IO) { podcasts.episodeList(feedId) }

    override suspend fun episode(id: Long): PodcastEpisode? = withContext(Dispatchers.IO) { podcasts.episode(id) }

    /**
     * The episodes left in the middle, the one listened to last first: 1.0.3: in the order the app noted them as they
     * played ([ListenedEpisodes]); those it did not note (played before 1.0.3) in the order the playback history has
     * them; the newest first without either.
     */
    override suspend fun continuing(): List<PodcastEpisode> {
        val episodes = withContext(Dispatchers.IO) { podcasts.inProgress() }
        if (episodes.isEmpty()) return emptyList()
        val heard = withContext(Dispatchers.IO) { ListenedEpisodes(context).order() }
        val history = if (episodes.all { it.id in heard }) {
            emptyList()
        } else {
            withTimeoutOrNull(ML_WAIT_MS) { context.getFromMl { history(Medialibrary.HISTORY_TYPE_GLOBAL)?.toList().orEmpty() } }.orEmpty()
        }
        val listened = HashMap<String, Int>()
        history.forEachIndexed { i, item -> (item.location ?: item.uri?.toString())?.let { listened.putIfAbsent(it, i) } }
        return CarPodcasts.continuing(episodes, heard) { episode ->
            listOfNotNull(listened[episode.audioUrl], episode.downloadPath?.let { listened[DownloadFiles.locationOf(it)] }).minOrNull()
        }
    }

    /**
     * 1.0.3: the device's country, as the Podcasts page's search asks iTunes: the mobile network's, else the SIM's, else
     * the region of the language; the United States when none is a storefront Keşfet offers.
     */
    override fun country(): String {
        val phone = runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull()
        val candidates = listOf(
            runCatching { phone?.networkCountryIso }.getOrNull(),
            runCatching { phone?.simCountryIso }.getOrNull(),
            context.resources.locale().country,
            Locale.getDefault().country,
        )
        return candidates.firstNotNullOfOrNull { code -> ApplePodcasts.storefront(code)?.takeIf { it in ApplePodcasts.COUNTRIES } } ?: DEFAULT_COUNTRY
    }

    /** 1.0.3: Apple's popular shows of [country], kept for [CHART_KEEP_MS]; through the app's one iTunes client and its pace. */
    override suspend fun chart(country: String): List<ChartShow> {
        charts[country]?.takeIf { System.currentTimeMillis() - it.at < CHART_KEEP_MS }?.let { return it.value }
        return PodcastCenter.itunes().top(country).also {
            if (charts.size >= MAX_CHARTS_KEPT) charts.clear()
            charts[country] = Kept(it, System.currentTimeMillis())
        }
    }

    /** 1.0.3: the shows the Podcasts page's search finds (Podcast Index when the build has its keys, iTunes). */
    override suspend fun findShows(query: String): List<PodcastHit> = PodcastCenter.search().search(query).hits

    /**
     * 1.0.3: the feed at [url] as a subscription reads it ([OkHttpFeedSource]: the app's User-Agent, its size cap), kept
     * for [FEED_KEEP_MS] so that a list and the episode tapped in it are the same.
     */
    override suspend fun readFeed(url: String): ParsedFeed {
        val key = FeedUrl.key(url)
        read[key]?.takeIf { System.currentTimeMillis() - it.at < FEED_KEEP_MS }?.let { return it.value }
        // asked with no validators, a server has no 304 to give; one that does gives nothing to read
        val fetch = feedSource.fetch(url) as? FeedFetch.Fresh ?: throw FeedFailure.Invalid(InvalidReason.Empty)
        return fetch.feed.also {
            if (read.size >= MAX_FEEDS_KEPT) read.clear()
            read[key] = Kept(it, System.currentTimeMillis())
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

        /** How long a list or a search waits for the medialibrary. */
        const val ML_WAIT_MS = 5_000L
        const val TAGS_KEEP_MS = 6 * 60 * 60_000L
        const val TAGGED_KEEP_MS = 30 * 60_000L
        const val MAX_TAGS_KEPT = 30
        const val MAX_TAGGED = 50
        const val MAX_FOUND_STATIONS = 20
        const val MAX_NEW = 100

        /** 1.0.3: how long a chart and a feed not followed are kept, and how many of each. */
        const val CHART_KEEP_MS = 6 * 60 * 60_000L
        const val MAX_CHARTS_KEPT = 8
        const val FEED_KEEP_MS = 30 * 60_000L
        const val MAX_FEEDS_KEPT = 20

        /** 1.0.3: Keşfet's country when the device names none Apple offers. */
        const val DEFAULT_COUNTRY = "us"
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
            CarText.Genres -> R.string.nag_music_view_genres
            CarText.AllTracks -> R.string.nag_auto_all_tracks
            CarText.ShuffleAll -> R.string.nag_auto_shuffle_all
            CarText.Smart -> R.string.nag_auto_smart_list
            CarText.UnknownGenre -> R.string.nag_music_unknown_genre
            CarText.Continue -> R.string.nag_auto_continue
            CarText.Subscriptions -> R.string.nag_auto_subscriptions
            CarText.NewEpisodes -> R.string.nag_auto_new_episodes
            CarText.Discover -> R.string.nag_auto_discover
            CarText.PopularShows -> R.string.nag_auto_popular_shows
            CarText.Countries -> R.string.nag_auto_countries
            CarText.Subscribe -> R.string.nag_pod_subscribe
            CarText.Subscribed -> R.string.nag_pod_subscribed
            CarText.FoundNewShows -> R.string.nag_auto_found_new_shows
            CarText.PodcastsOffline -> R.string.nag_auto_podcasts_offline
            CarText.ShowUnavailable -> R.string.nag_auto_show_unavailable
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
            CarText.MusicUnavailable -> R.string.nag_auto_music_unavailable
        },
    )

    override fun count(count: CarCount, n: Int): String = res.getQuantityString(
        when (count) {
            CarCount.Tracks -> R.plurals.nag_tracks_count
            CarCount.Albums -> R.plurals.nag_music_albums
            CarCount.Episodes -> R.plurals.nag_pod_episodes
            CarCount.Stations -> R.plurals.nag_auto_stations
            CarCount.Shows -> R.plurals.nag_pod_count
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
            CarIcon.Genres -> org.videolan.resources.R.drawable.nagmedar_auto_genres
            CarIcon.Tracks -> org.videolan.resources.R.drawable.nagmedar_auto_tracks
            CarIcon.Shuffle -> org.videolan.resources.R.drawable.nagmedar_auto_shuffle
            CarIcon.Smart -> org.videolan.resources.R.drawable.nagmedar_auto_smart
            CarIcon.Discover -> org.videolan.resources.R.drawable.nagmedar_auto_discover
            CarIcon.Popular -> org.videolan.resources.R.drawable.nagmedar_auto_popular
            CarIcon.Countries -> org.videolan.resources.R.drawable.nagmedar_auto_countries
            CarIcon.Subscribe -> org.videolan.resources.R.drawable.nagmedar_auto_subscribe
            CarIcon.NoCover -> ArtworkProvider.noCover(MediaWrapper.TYPE_AUDIO)
        },
    ).toString()

    override val locale: Locale get() = res.locale()

    override fun country(code: String): String = RadioLabels.country(code.uppercase(Locale.ROOT), null, locale)

    /** 1.0.3: Apple's top-level categories in the app's language; one Apple adds later keeps its English name. */
    override fun category(genre: ChartGenre): String = when (genre.id) {
        ApplePodcasts.ARTS -> R.string.nag_auto_genre_arts
        ApplePodcasts.BUSINESS -> R.string.nag_auto_genre_business
        ApplePodcasts.COMEDY -> R.string.nag_auto_genre_comedy
        ApplePodcasts.EDUCATION -> R.string.nag_auto_genre_education
        ApplePodcasts.FICTION -> R.string.nag_auto_genre_fiction
        ApplePodcasts.GOVERNMENT -> R.string.nag_auto_genre_government
        ApplePodcasts.HEALTH -> R.string.nag_auto_genre_health
        ApplePodcasts.HISTORY -> R.string.nag_auto_genre_history
        ApplePodcasts.KIDS -> R.string.nag_auto_genre_kids
        ApplePodcasts.LEISURE -> R.string.nag_auto_genre_leisure
        ApplePodcasts.MUSIC -> R.string.nag_auto_genre_music
        ApplePodcasts.NEWS -> R.string.nag_auto_genre_news
        ApplePodcasts.RELIGION -> R.string.nag_auto_genre_religion
        ApplePodcasts.SCIENCE -> R.string.nag_auto_genre_science
        ApplePodcasts.SOCIETY -> R.string.nag_auto_genre_society
        ApplePodcasts.SPORTS -> R.string.nag_auto_genre_sports
        ApplePodcasts.TECHNOLOGY -> R.string.nag_auto_genre_technology
        ApplePodcasts.TRUE_CRIME -> R.string.nag_auto_genre_true_crime
        ApplePodcasts.TV -> R.string.nag_auto_genre_tv
        else -> null
    }?.let { res.getString(it) } ?: genre.name

    override fun cover(track: Track?): String =
        track?.let(media)?.let { ArtworkProvider.buildMediaUri(context, it).toString() } ?: icon(CarIcon.NoCover)

    override fun playlistCover(id: Long): String =
        ArtworkProvider.buildUri(context, Uri.Builder().appendPath(ArtworkProvider.PLAYLIST).appendPath("$id").build()).toString()

    /**
     * A web image through the artwork provider, which has the app fetch it (its User-Agent, once, kept on disk:
     * [app.nagmedar.ui.podcast.PodcastCovers.webImages]); its address is Auto's key for the image it keeps.
     */
    override fun webImage(url: String?): String {
        if (url.isNullOrBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) return icon(CarIcon.NoCover)
        return ArtworkProvider.buildUri(context, Uri.Builder().appendPath(ArtworkProvider.REMOTE).appendQueryParameter(ArtworkProvider.PATH, url).build()).toString()
    }
}
