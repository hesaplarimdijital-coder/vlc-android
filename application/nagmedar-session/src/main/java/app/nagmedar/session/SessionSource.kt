package app.nagmedar.session

import android.net.Uri
import android.os.Bundle

/** One entry of the play queue as controllers see it. */
data class SessionItem(
    /** Browse-tree id (Android Auto, Assistant), or a stable id for the queue entry. */
    val mediaId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    /** content:// address other apps (System UI, Android Auto) can read the artwork from. */
    val artworkUri: Uri?,
    val durationMs: Long,
    val isVideo: Boolean,
)

/**
 * What the media session needs from the playback service: its state, read on the main thread
 * whenever controllers ask, and the commands they send. Implemented over PlaybackService and its
 * legacy session callback, so media keys, Android Auto requests and custom actions keep their
 * existing behaviour (headset double-click, chapter jumps in podcasts, bookmarks).
 */
interface SessionSource {
    val hasMedia: Boolean
    val isPlaying: Boolean
    val isPaused: Boolean
    val isSeekable: Boolean
    val positionMs: Long
    val durationMs: Long
    val speed: Float

    /** One of the Player.REPEAT_MODE_* values. */
    val repeatMode: Int
    val shuffling: Boolean
    val canRepeat: Boolean
    val canShuffle: Boolean
    val hasNext: Boolean
    val hasPrevious: Boolean

    val queueSize: Int
    val currentIndex: Int
    fun queueItem(index: Int): SessionItem?

    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun skipToIndex(index: Int)
    fun next()
    fun previous()
    fun setSpeed(speed: Float)

    /** [mode] is one of the Player.REPEAT_MODE_* values. */
    fun setRepeatMode(mode: Int)
    fun setShuffle(enabled: Boolean)

    /** An item chosen in the browse tree (Android Auto) or by id. */
    fun playFromMediaId(mediaId: String, extras: Bundle?)

    /** A voice or text search ("play Sezen Aksu"). */
    fun playFromSearch(query: String, extras: Bundle?)

    /** A custom action of the notification, Android Auto or Wear (bookmark, ±10 s, speed …). */
    fun customAction(action: String, extras: Bundle?)

    /** A media key; true when handled. */
    fun mediaButton(intent: android.content.Intent): Boolean
}
