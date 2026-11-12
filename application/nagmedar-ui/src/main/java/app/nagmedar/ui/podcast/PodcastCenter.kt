package app.nagmedar.ui.podcast

import android.content.Context
import android.net.Uri
import app.nagmedar.data.DownloadState
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.ui.BuildConfig as UiBuildConfig
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.playback.PlaybackRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.AppScope
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig as VlcBuildConfig
import org.videolan.vlc.media.ResumeHook
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * S-1101: the app's one podcast repository and one search, built on first use: Room's database, OkHttp,
 * and Podcast Index's key and secret from the build (`NAGMEDAR_PODCASTINDEX_KEY` and
 * `NAGMEDAR_PODCASTINDEX_SECRET`; without them iTunes answers alone, and the search says so).
 * S-1103, S-1104: and what the background work and the playback share: the settings, the downloads and the
 * storage rules, the refresh, the chapters and transcripts, the show a notification asked to open and the episode that plays.
 */
object PodcastCenter {
    @Volatile
    private var repository: PodcastRepository? = null

    @Volatile
    private var search: PodcastSearch? = null

    @Volatile
    private var index: PodcastIndexDirectory? = null

    @Volatile
    private var itunes: ITunesDirectory? = null

    private val focusPending = MutableStateFlow(false)

    /** The OPML import in progress or just done, which outlives the screen. */
    val imports = ImportTracker()

    /** True from Ctrl+Shift+P until the Podcasts screen, which may not be on screen yet, has focused its search field. */
    val searchFocusPending: StateFlow<Boolean> = focusPending

    /** S-1103: the progress of the downloads of this process, and the requests to stop them. */
    val tracker = DownloadTracker()

    private val openPending = MutableStateFlow<Long?>(null)

    /** S-1103: the show a notification's tap asked to open, until the Podcasts screen has opened it. */
    val openRequest: StateFlow<Long?> = openPending

    fun requestOpen(feedId: Long) {
        openPending.value = feedId
    }

    /** The request is answered. */
    fun opened() {
        openPending.value = null
    }

    private val nowPlaying = MutableStateFlow<PlayingEpisode?>(null)

    /** S-1104: the podcast episode the playback service plays now, with its chapters once they are read; null for anything else. */
    val playing: StateFlow<PlayingEpisode?> = nowPlaying

    fun setPlaying(episode: PlayingEpisode?) {
        nowPlaying.value = episode
    }

    /** What the storage rules leave alone: the episode playing now. */
    fun playingEpisodes(): Set<Long> = nowPlaying.value?.let { setOf(it.episodeId) } ?: emptySet()

    fun requestSearchFocus() {
        focusPending.value = true
    }

    /** The search field has the focus: the request is answered. */
    fun searchFocused() {
        focusPending.value = false
    }

    /** "Nagmedar/1.2.0 (+https://github.com/hesaplarimdijital-coder/vlc-android)": no contact address. */
    fun userAgent(): String = OkHttpFeedSource.userAgent(VlcBuildConfig.VLC_VERSION_NAME)

    fun repository(context: Context): PodcastRepository = repository ?: synchronized(this) {
        repository ?: PodcastRepository(
            dao = NagmedarDatabase.get(context).podcasts(),
            source = OkHttpFeedSource(userAgent()),
            indexId = { url -> podcastIndex().byFeedUrl(url)?.podcastIndexId },
        ).also { repository = it }
    }

    fun settingsStore(context: Context): PodcastSettingsStore = PodcastSettingsStore(Settings.getInstance(context.applicationContext))

    fun store(context: Context): DownloadStore {
        val app = context.applicationContext
        return AndroidDownloadStore(app) { settingsStore(app).read().downloadFolder }
    }

    fun downloads(context: Context): DownloadManager {
        val app = context.applicationContext
        return DownloadManager(NagmedarDatabase.get(app).podcasts(), store(app), tracker, WorkManagerDownloadScheduler(app))
    }

    fun runner(context: Context): DownloadRunner {
        val app = context.applicationContext
        return DownloadRunner(NagmedarDatabase.get(app).podcasts(), EpisodeDownloader(userAgent()), store(app), tracker)
    }

    fun cleaner(context: Context): StorageCleaner {
        val app = context.applicationContext
        return StorageCleaner(NagmedarDatabase.get(app).podcasts(), store(app), { settingsStore(app).read() })
    }

    fun refresher(context: Context): PodcastRefresher {
        val app = context.applicationContext
        return PodcastRefresher(
            repository(app), NagmedarDatabase.get(app).podcasts(), { settingsStore(app).read() },
            downloads(app), cleaner(app), protectedNow = ::playingEpisodes,
        )
    }

    /** S-1104: the chapters and transcripts of episodes, kept in the app's cache. */
    fun extras(context: Context): EpisodeExtras {
        val app = context.applicationContext
        return EpisodeExtras(OkHttpExtraSource(app, userAgent()), { java.io.File(app.cacheDir, "podcast") })
    }

    /**
     * S-1103: the refresh job as the subscriptions and Settings › Podcasts ask for it: scheduled while there is a show to
     * read, not at all without. Cheap and safe to call again and again; the work is off the main thread.
     */
    fun schedule(context: Context, scope: CoroutineScope = AppScope) {
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (NagmedarDatabase.get(app).podcasts().feedList().isEmpty()) RefreshSchedule.cancel(app) else RefreshSchedule.apply(app, settingsStore(app).read())
            }
        }
    }

    fun search(): PodcastSearch = search ?: synchronized(this) {
        search ?: PodcastSearch(podcastIndex(), itunes()).also { search = it }
    }

    /**
     * 1.0.3: the one iTunes client of the app: the search's second directory and Android Auto's charts (Keşfet) share its
     * pace, Apple's limit of about twenty requests a minute.
     */
    fun itunes(): ITunesDirectory = itunes ?: synchronized(this) {
        itunes ?: ITunesDirectory(userAgent(), Locale.getDefault().country).also { itunes = it }
    }

    private fun podcastIndex(): PodcastIndexDirectory = index ?: synchronized(this) {
        index ?: PodcastIndexDirectory(UiBuildConfig.PODCASTINDEX_KEY, UiBuildConfig.PODCASTINDEX_SECRET, userAgent()).also { index = it }
    }
}

/** S-1104: the podcast episode that plays, and its chapters ([chapters] is empty while they are read, and for an episode with none). */
data class PlayingEpisode(val episodeId: Long, val feedId: Long, val chapters: List<Chapter> = emptyList())

/** Starts an episode: from its downloaded file when there is one, else streamed from its enclosure; where it was left, or after the show's intro. */
fun interface EpisodePlayer {
    fun play(feed: PodcastFeed, episode: PodcastEpisode)

    /** Starts the episode at [startMs] (a chapter, a line of the transcript) instead of where it was left. */
    fun playFrom(feed: PodcastFeed, episode: PodcastEpisode, startMs: Long) = play(feed, episode)
}

/** S-205: the player's own controls, as the episode pages use them: where the episode is, play and pause, a seek. */
interface EpisodeControl {
    /** The place of [episode] when the player has it loaded, else null. */
    fun listening(episode: PodcastEpisode): Listening?

    fun togglePlay()

    fun seekTo(ms: Long)

    /** A number that grows each time the player ends, fails on or is stopped on [episode]; 0 before the first. */
    fun haltStamp(episode: PodcastEpisode): Int = 0
}

/** No player: the pages of tests and previews. */
object NoEpisodeControl : EpisodeControl {
    override fun listening(episode: PodcastEpisode): Listening? = null

    override fun togglePlay() = Unit

    override fun seekTo(ms: Long) = Unit
}

/** [EpisodeControl] through the playback service: the episode is the loaded one when the media's address is its enclosure's or its downloaded file's. */
object ServiceEpisodeControl : EpisodeControl {
    override fun listening(episode: PodcastEpisode): Listening? {
        val service = org.videolan.vlc.PlaybackService.instance ?: return null
        val location = service.currentMediaLocation ?: return null
        val downloaded = episode.downloadPath?.let(DownloadFiles::locationOf)
        if (location != episode.audioUrl && location != downloaded) return null
        return Listening(service.getTime(), service.length, service.isPlaying)
    }

    override fun togglePlay() = PlaybackActions.togglePlayPause()

    override fun seekTo(ms: Long) = PlaybackActions.seekToMs(ms)

    override fun haltStamp(episode: PodcastEpisode): Int {
        val downloaded = episode.downloadPath?.let(DownloadFiles::locationOf)
        return maxOf(ResumeHook.haltStamp(episode.audioUrl), downloaded?.let(ResumeHook::haltStamp) ?: 0)
    }
}

/**
 * [EpisodePlayer] through the existing playback service: the episode's media ([EpisodeMedia]) played as a list of one. Starting
 * playback needs an Activity, which is only weakly held: the controller (and the app-scope work that captures it) outlives
 * the screen and must not keep the Activity alive. Episodes are played from the screen, so it is there when it is used.
 */
class ServiceEpisodePlayer(activity: Context) : EpisodePlayer {
    private val activity = WeakReference(activity)

    override fun play(feed: PodcastFeed, episode: PodcastEpisode) = start(feed, episode, null)

    override fun playFrom(feed: PodcastFeed, episode: PodcastEpisode, startMs: Long) = start(feed, episode, startMs)

    private fun start(feed: PodcastFeed, episode: PodcastEpisode, from: Long?) {
        val context = activity.get() ?: return
        PlaybackActions.playList(context, listOf(EpisodeMedia.of(feed, episode, from)), 0)
    }
}

/**
 * 1.0.4: the addresses handed to the player as podcast episodes, each with the length its feed gives it (0 when the feed
 * gives none). An episode streamed from its enclosure is, to the player, a stream without a length until libVLC has
 * found one, as a station on the air is for good: the bars took it for one and said "Canlı yayın" (the owner, 12 Nov:
 * "podcast çaldığında canlı yayın diyor müzik barı"). What [EpisodeMedia] starts is told here before it plays, so a
 * timeline knows the episode from its first frame, without a query; what the player was left with from an earlier run
 * is told once Room has been asked ([ask]). The last [KEPT] are kept.
 */
object EpisodeAddresses {
    private const val KEPT = 64

    private val known = object : LinkedHashMap<String, Long>(KEPT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > KEPT
    }

    /** [location] (a media's address, as the playback service reports it) plays an episode [feedLengthMs] long by its feed. */
    @Synchronized
    fun started(location: String, feedLengthMs: Long) {
        known[location] = feedLengthMs.coerceAtLeast(0L)
    }

    /** The feed's length of the episode at [location] (0: the feed gives none); null when no episode is known there. */
    @Synchronized
    fun lengthOf(location: String?): Long? = location?.let { known[it] }

    /** Room's answer for an address nobody told of: the feed's length of the episode there, null for none. Off the main thread. */
    @Volatile
    internal var source: (Context, String) -> Long? = { app, location ->
        PodcastPlaybackObserver.episodeAt(NagmedarDatabase.get(app).podcasts(), location)?.durationMs
    }

    /**
     * [lengthOf], and for an address not told of yet Room is asked (the player was left with it by an earlier run): an
     * episode found there is kept as [started]. Null when it is no episode's, or Room could not be asked.
     */
    suspend fun ask(context: Context, location: String): Long? {
        lengthOf(location)?.let { return it }
        val app = context.applicationContext
        val length = withContext(Dispatchers.IO) { runCatching { source(app, location) }.getOrNull() } ?: return null
        started(location, length)
        return lengthOf(location)
    }

    /** Forgets everything (tests). */
    @Synchronized
    fun forget() = known.clear()
}

/**
 * What an episode plays as, wherever it is started (the app's pages, Android Auto): the downloaded file (S-1103) or the
 * enclosure's address as a stream, a video enclosure heard as sound alone, titled by the episode and its show, started at
 * the place the listener left (S-1104) or after the show's intro. The show's speed, outro and volume boost follow from its
 * address ([PodcastPlaybackObserver]); the bars know it for an episode by that address too ([EpisodeAddresses]).
 */
object EpisodeMedia {
    /** The media of [episode] of [feed], started at [from] when given (a chapter, a line of the transcript). */
    fun of(feed: PodcastFeed, episode: PodcastEpisode, from: Long? = null): MediaWrapper {
        val media = MLServiceLocator.getAbstractMediaWrapper(playableUri(episode))
        // a video enclosure is heard as the others are: Nağmedar plays sound alone
        media.setType(MediaWrapper.TYPE_AUDIO)
        if (episode.mimeType?.startsWith("video/") == true) media.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
        media.setTitle(episode.title)
        media.setDisplayTitle(episode.title)
        media.setArtist(feed.title)
        (episode.imageUrl ?: feed.imageUrl)?.let(media::setArtworkURL)
        val start = from ?: PlaybackRules.startMs(episode.positionMs, episode.played, episode.durationMs, feed.skipIntroSeconds, feed.skipOutroSeconds)
        if (start > 0) media.time = start
        // 1.0.4: what plays from this address is an episode, from its first frame in the bars (never a station on the air)
        EpisodeAddresses.started(media.location, episode.durationMs)
        return media
    }

    private fun playableUri(episode: PodcastEpisode): Uri {
        val saved = episode.downloadPath?.takeIf { episode.downloadState == DownloadState.DONE }
        // a path in the app's own storage is looked at; a document is trusted (a look would cost a query on the main thread)
        if (saved != null && (!saved.startsWith("/") || java.io.File(saved).isFile)) return DownloadFiles.uriOf(saved)
        return Uri.parse(episode.audioUrl)
    }
}
