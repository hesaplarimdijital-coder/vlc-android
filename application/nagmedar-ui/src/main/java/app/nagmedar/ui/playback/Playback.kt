package app.nagmedar.ui.playback

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.NAGMEDAR_ACTION_CLASSIC
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.StartActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager

/** What the existing PlaybackService is playing, observed from its LiveData. */
@Immutable
data class NowPlaying(val media: MediaWrapper?, val playing: Boolean) {
    val isVideo: Boolean get() = media?.type == MediaWrapper.TYPE_VIDEO
}

@Composable
fun rememberNowPlaying(): NowPlaying {
    val media by PlaylistManager.currentPlayedMedia.observeAsState()
    val playing by PlaylistManager.playingState.observeAsState(false)
    return NowPlaying(media, playing == true)
}

/** Position of the current media as 0..1, refreshed twice a second while playing. */
@Composable
fun rememberPlaybackProgress(nowPlaying: NowPlaying): State<Float> = produceState(0f, nowPlaying.media, nowPlaying.playing) {
    while (true) {
        val service = PlaybackService.instance
        val length = service?.length ?: 0L
        value = if (service != null && length > 0) (service.getTime().toFloat() / length).coerceIn(0f, 1f) else 0f
        if (!nowPlaying.playing) break
        delay(500)
    }
}

/**
 * Commands to the existing playback service (ADR-05 keeps one service and one session; the
 * PlaybackEngine interface of S-501 sits behind these calls later).
 */
object PlaybackActions {

    fun playVideo(context: Context, media: MediaWrapper) = MediaUtils.openMedia(context, media)

    fun playList(context: Context, list: List<MediaWrapper>, position: Int) = MediaUtils.openList(context, list, position)

    fun togglePlayPause() {
        val service = PlaybackService.instance ?: return
        if (service.isPlaying) service.pause() else service.play()
    }

    fun next() {
        PlaybackService.instance?.next()
    }

    fun previous() {
        PlaybackService.instance?.previous(false)
    }

    fun seekTo(fraction: Float) {
        val service = PlaybackService.instance ?: return
        val length = service.length
        if (length > 0) service.seek((length * fraction.coerceIn(0f, 1f)).toLong(), fromUser = true)
    }

    /** Brings back the player: the video player for videos, the classic audio player otherwise. */
    fun openPlayer(context: Context) {
        val service = PlaybackService.instance
        if (service != null && service.currentMediaWrapper?.type == MediaWrapper.TYPE_VIDEO) {
            service.switchToVideo()
        } else {
            // the launcher's default is the new interface: ask StartActivity for the classic one
            context.startActivity(
                Intent(context, StartActivity::class.java).setAction(NAGMEDAR_ACTION_CLASSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
