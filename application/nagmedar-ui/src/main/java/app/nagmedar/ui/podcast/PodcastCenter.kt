package app.nagmedar.ui.podcast

import android.content.Context
import android.net.Uri
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.ui.BuildConfig as UiBuildConfig
import app.nagmedar.ui.playback.PlaybackActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.BuildConfig as VlcBuildConfig
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * S-1101: the app's one podcast repository and one search, built on first use: Room's database, OkHttp,
 * and Podcast Index's key and secret from the build (`NAGMEDAR_PODCASTINDEX_KEY` and
 * `NAGMEDAR_PODCASTINDEX_SECRET`; without them iTunes answers alone, and the search says so).
 */
object PodcastCenter {
    @Volatile
    private var repository: PodcastRepository? = null

    @Volatile
    private var search: PodcastSearch? = null

    @Volatile
    private var index: PodcastIndexDirectory? = null

    private val focusPending = MutableStateFlow(false)

    /** The OPML import in progress or just done, which outlives the screen. */
    val imports = ImportTracker()

    /** True from Ctrl+Shift+P until the Podcasts screen, which may not be on screen yet, has focused its search field. */
    val searchFocusPending: StateFlow<Boolean> = focusPending

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

    fun search(): PodcastSearch = search ?: synchronized(this) {
        search ?: PodcastSearch(podcastIndex(), ITunesDirectory(userAgent(), Locale.getDefault().country)).also { search = it }
    }

    private fun podcastIndex(): PodcastIndexDirectory = index ?: synchronized(this) {
        index ?: PodcastIndexDirectory(UiBuildConfig.PODCASTINDEX_KEY, UiBuildConfig.PODCASTINDEX_SECRET, userAgent()).also { index = it }
    }
}

/** Starts an episode: streamed from its enclosure now; downloaded copies are S-1103's, resume points S-1104's. */
fun interface EpisodePlayer {
    fun play(feed: PodcastFeed, episode: PodcastEpisode)
}

/**
 * [EpisodePlayer] through the existing playback service: the enclosure's address as a stream, a video enclosure in the video player.
 * Starting playback needs an Activity, which is only weakly held: the controller (and the app-scope work that captures it)
 * outlives the screen and must not keep the Activity alive. Episodes are played from the screen, so it is there when it is used.
 */
class ServiceEpisodePlayer(activity: Context) : EpisodePlayer {
    private val activity = WeakReference(activity)

    override fun play(feed: PodcastFeed, episode: PodcastEpisode) {
        val context = activity.get() ?: return
        val media = MLServiceLocator.getAbstractMediaWrapper(Uri.parse(episode.audioUrl))
        val video = episode.mimeType?.startsWith("video/") == true
        media.setType(if (video) MediaWrapper.TYPE_VIDEO else MediaWrapper.TYPE_AUDIO)
        media.setTitle(episode.title)
        media.setDisplayTitle(episode.title)
        media.setArtist(feed.title)
        (episode.imageUrl ?: feed.imageUrl)?.let(media::setArtworkURL)
        if (video) PlaybackActions.playVideo(context, media) else PlaybackActions.playList(context, listOf(media), 0)
    }
}
