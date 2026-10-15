package org.videolan.vlc.media

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.Player
import app.nagmedar.session.SessionItem
import app.nagmedar.session.SessionSource
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.ArtworkProvider
import org.videolan.vlc.PlaybackService

/**
 * The playback service as the Media3 session sees it (S-502). Commands go through the legacy
 * session callback, so media keys, Android Auto requests and custom actions keep their existing
 * behaviour: headset double-click, chapter jumps in podcasts, bookmarks, the speed cycle.
 */
internal class PlaybackSessionSource(private val service: PlaybackService) : SessionSource {

    private val callback get() = service.sessionCallback

    override val hasMedia get() = service.hasMedia()
    override val isPlaying get() = service.isPlaying
    override val isPaused get() = service.isPaused
    override val isSeekable get() = service.isSeekable
    override val positionMs get() = service.getTime()
    override val durationMs get() = service.length
    override val speed get() = service.rate
    override val repeatMode
        get() = when (service.repeatType) {
            PlaybackStateCompat.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
            PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    override val shuffling get() = service.isShuffling
    override val canRepeat get() = service.playlistManager.canRepeat()
    override val canShuffle get() = service.canShuffle()
    override val hasNext get() = service.hasNext()
    override val hasPrevious get() = service.hasPrevious()
    override val queueSize get() = service.mediaListSize
    override val currentIndex get() = service.currentMediaPosition

    override fun queueItem(index: Int): SessionItem? = service.playlistManager.getMedia(index)?.let { media ->
        // S-1106: a radio stream's current title, split, with the station under it, and its logo as the artwork (also with no title)
        val stream = media.streamDisplay()
        val logo = media.streamLogo()
        SessionItem(
            mediaId = MediaSessionBrowser.generateMediaId(media),
            title = stream?.title ?: media.title,
            artist = stream?.artist ?: media.artistName,
            album = stream?.album ?: media.albumName,
            artworkUri = if (logo != null) {
                ArtworkProvider.buildUri(service, Uri.Builder().appendPath(ArtworkProvider.REMOTE).appendQueryParameter(ArtworkProvider.PATH, logo).build())
            } else {
                ArtworkProvider.buildMediaUri(service, media)
            },
            durationMs = media.length,
            isVideo = media.type == MediaWrapper.TYPE_VIDEO,
        )
    }

    override fun play() = callback.onPlay()
    override fun pause() = callback.onPause()
    override fun stop() = callback.onStop()
    override fun seekTo(positionMs: Long) = callback.onSeekTo(positionMs)
    override fun skipToIndex(index: Int) = callback.onSkipToQueueItem(index.toLong())
    override fun next() = callback.onSkipToNext()
    override fun previous() = callback.onSkipToPrevious()
    override fun setSpeed(speed: Float) = callback.onSetPlaybackSpeed(speed)

    override fun setRepeatMode(mode: Int) = callback.onSetRepeatMode(
        when (mode) {
            Player.REPEAT_MODE_ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
            else -> PlaybackStateCompat.REPEAT_MODE_NONE
        },
    )

    override fun setShuffle(enabled: Boolean) =
        callback.onSetShuffleMode(if (enabled) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)

    override fun playFromMediaId(mediaId: String, extras: Bundle?) = callback.onPlayFromMediaId(mediaId, extras)
    override fun playFromSearch(query: String, extras: Bundle?) = callback.onPlayFromSearch(query, extras)
    override fun customAction(action: String, extras: Bundle?) = callback.onCustomAction(action, extras)
    override fun mediaButton(intent: Intent) = callback.onMediaButtonEvent(intent)
}
