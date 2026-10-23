package app.nagmedar.ui.playback

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.podcast.PodcastCenter
import app.nagmedar.ui.radio.RadioSession
import app.nagmedar.ui.shell.Destination
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.PLAY_EXTRA_START_TIME
import org.videolan.tools.NAGMEDAR_DESTINATION_NOW_PLAYING
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.media.Progress

/** What the existing PlaybackService is playing, observed from its LiveData. */
@Immutable
data class NowPlaying(val media: MediaWrapper?, val playing: Boolean) {
    val isVideo: Boolean get() = media?.type == MediaWrapper.TYPE_VIDEO
}

@Composable
fun rememberNowPlaying(): NowPlaying {
    val media by PlaylistManager.currentPlayedMedia.observeAsState()
    // the engine's own state, either engine: playingState only says that the service has something to play
    val playing by PlaylistManager.enginePlaying.collectAsState()
    return NowPlaying(media, playing)
}

/** How often the position of what plays is read while it plays and the window is in sight. */
internal const val PROGRESS_READING_MS = 500L

/**
 * Position of the current media as 0..1 while the window is in sight: read twice a second while it plays; paused,
 * once and again whenever the service's position is set (a seek from the notification, the lock screen or a headset
 * shows); nothing is read while no media is there. The timelines glide between the readings.
 */
@Composable
fun rememberPlaybackProgress(nowPlaying: NowPlaying): State<Float> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(0f, nowPlaying.media, nowPlaying.playing, lifecycle) {
        if (nowPlaying.media == null) {
            value = 0f
            return@produceState
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value = currentProgress()
            if (nowPlaying.playing) {
                while (true) {
                    delay(PROGRESS_READING_MS)
                    value = currentProgress()
                }
            } else {
                PlaybackService.instance?.positionSets()?.collect { value = currentProgress() }
            }
        }
    }
}

/** Where the service's media is, 0..1; 0 without a service or a length. */
private fun currentProgress(): Float {
    val service = PlaybackService.instance ?: return 0f
    val length = service.length
    return if (length > 0) (service.preciseTime().toFloat() / length).coerceIn(0f, 1f) else 0f
}

/**
 * Each time the service's position is set: a seek of either engine, a new length; while libVLC plays, its steps of a
 * second too. The service's own progress, which both engines feed.
 */
private fun PlaybackService.positionSets(): Flow<Unit> = callbackFlow {
    val progress = playlistManager.player.progress
    val observer = Observer<Progress> { trySend(Unit) }
    progress.observeForever(observer)
    awaitClose { progress.removeObserver(observer) }
}

/** The speed of what plays (1 for its own); the timelines move at it. */
fun playbackRate(): Float = PlaybackService.instance?.rate?.takeIf { it > 0f } ?: 1f

/**
 * S-1004: where the current media is, in milliseconds, for the lyrics: read ten times a second
 * while it plays and the window is in sight (once when paused); a seek of [seekTo] shows at once,
 * paused or not.
 */
@Stable
class PlaybackTime internal constructor() {
    var ms by mutableLongStateOf(0L)
        internal set

    fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        PlaybackActions.seekToMs(target)
        this.ms = target
    }
}

@Composable
fun rememberPlaybackTime(nowPlaying: NowPlaying): PlaybackTime {
    val time = remember { PlaybackTime() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(nowPlaying.media, nowPlaying.playing, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                time.ms = PlaybackService.instance?.preciseTime() ?: 0L
                if (!nowPlaying.playing) break
                delay(100)
            }
        }
    }
    return time
}

/**
 * Commands to the existing playback service (ADR-05 keeps one service and one session; the
 * PlaybackEngine interface of S-501 sits behind these calls later).
 */
object PlaybackActions {

    fun playVideo(context: Context, media: MediaWrapper) = MediaUtils.openMedia(context, media)

    /** Plays a video from [startMs] (S-311: a line found in its subtitles). */
    fun playVideoAt(context: Context, media: MediaWrapper, startMs: Long) {
        val intent = VideoPlayerActivity.getIntent(context, media.uri, media.title, false, -1).putExtra(PLAY_EXTRA_START_TIME, startMs)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Plays [list] from [position]; [from] is the album, artist or playlist it comes from, which the phone's player names. */
    fun playList(context: Context, list: List<MediaWrapper>, position: Int, from: PlaySource? = null) {
        PlaySources.start(from, list)
        MediaUtils.openList(context, list, position)
    }

    /** Adds [list] after what is playing (S-405: the selection bar's "Sıraya ekle"). */
    fun appendList(context: Context, list: List<MediaWrapper>) = MediaUtils.appendMedia(context, list)

    fun togglePlayPause() {
        val service = PlaybackService.instance ?: return
        if (service.isPlaying) service.pause() else service.play()
    }

    fun next() {
        PlaybackService.instance?.next()
    }

    /** The track restarts, or within three seconds of its start the one before plays (PlaylistManager.previous). */
    fun previous() {
        PlaybackService.instance?.previous(false)
    }

    /** The volume the user sets (the bars' slider, the keys, mute), 0–200: kept for the next start. */
    fun setVolume(volume: Int) {
        PlaybackService.instance?.setUserVolume(volume)
    }

    /** S-1004: to [ms] of the current media (a line of its lyrics). */
    fun seekToMs(ms: Long) {
        val service = PlaybackService.instance ?: return
        val length = service.length
        service.seek(if (length > 0) ms.coerceIn(0L, length) else ms.coerceAtLeast(0L), fromUser = true)
    }

    fun seekTo(fraction: Float) {
        val service = PlaybackService.instance ?: return
        val length = service.length
        if (length > 0) service.seek((length * fraction.coerceIn(0f, 1f)).toLong(), fromUser = true)
    }

    /** Whether what plays is a video (or a stream showing one): the video player shows it. */
    fun playsVideo(): Boolean {
        val service = PlaybackService.instance ?: return false
        return service.currentMediaWrapper?.type == MediaWrapper.TYPE_VIDEO || service.isVideoPlaying
    }

    /**
     * The page of the shell that shows what plays, when it is not a video: the music view for
     * music, Podcasts for an episode, Radio for a station, Streams for any other stream; null when
     * nothing plays or a video does.
     */
    fun nowPlayingDestination(): Destination? {
        val service = PlaybackService.instance ?: return null
        val media = service.currentMediaWrapper ?: return null
        if (playsVideo()) return null
        if (media.type == MediaWrapper.TYPE_AUDIO) return Destination.Music
        if (PodcastCenter.playing.value != null) return Destination.Podcasts
        val station = RadioSession.playing.value
        if (station != null && (service.currentMediaLocation == station.url || media.title == station.station.name)) return Destination.Radio
        return Destination.Streams
    }

    /**
     * Brings back the player: the video player for videos; anything else in the Nağmedar window,
     * on the page that shows it (an open window takes the request instead of a new one).
     */
    fun openPlayer(context: Context) {
        val service = PlaybackService.instance
        if (service != null && playsVideo()) {
            service.switchToVideo()
        } else {
            val intent = Intent(context, NagmedarActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(NAGMEDAR_EXTRA_DESTINATION, NAGMEDAR_DESTINATION_NOW_PLAYING)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}
