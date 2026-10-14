package app.nagmedar.ui.scrobble

import androidx.compose.runtime.Immutable
import app.nagmedar.ui.stats.ListenCounter
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/** S-1008: a track as the scrobbling services are told of it. */
@Immutable
data class ScrobbleTrack(
    /** What tells one track from another while it plays: its uri. */
    val key: String,
    val artist: String,
    val title: String,
    val album: String?,
    val albumArtist: String?,
    val durationSec: Int,
) {
    companion object {
        /**
         * [media] as a scrobble; null for what is not scrobbled: video, podcasts and audiobooks, radio and
         * other streams (only a file of the device or a document), and a track without an artist and a title.
         */
        fun of(media: MediaWrapper): ScrobbleTrack? {
            if (media.type != MediaWrapper.TYPE_AUDIO || media.isPodcast) return null
            val uri = media.uri ?: return null
            if (uri.scheme != "file" && uri.scheme != "content") return null
            return of(uri.toString(), media.artistName, media.title, media.albumName, media.albumArtistName, media.length)
        }

        internal fun of(key: String, artist: String?, title: String?, album: String?, albumArtist: String?, lengthMs: Long): ScrobbleTrack? {
            val artistName = artist?.trim().orEmpty()
            val titleName = title?.trim().orEmpty()
            if (artistName.isEmpty() || titleName.isEmpty()) return null
            return ScrobbleTrack(
                key = key,
                artist = artistName,
                title = titleName,
                album = album?.trim()?.takeIf { it.isNotEmpty() },
                albumArtist = albumArtist?.trim()?.takeIf { it.isNotEmpty() },
                durationSec = ((lengthMs + 500) / 1_000).toInt().coerceAtLeast(0),
            )
        }
    }
}

/**
 * S-1008: Last.fm's rule for when a track is scrobbled, on the samples the playback service gives
 * statistics (S-808): a track longer than 30 seconds, heard for half of it or four minutes,
 * whichever is first. [onStarted] says what is playing when a track starts (Last.fm's "now
 * playing"), once a play; [onScrobble] a play that counts, with the second (since the epoch) it
 * started. The time heard grows only by the steps a playback makes while it plays, so a pause, a
 * seek or a stop is not listening; a track that starts again from its beginning is a new play,
 * whether the one before counted or not.
 */
class ScrobbleTracker(
    private val onStarted: (ScrobbleTrack) -> Unit,
    private val onScrobble: (track: ScrobbleTrack, startedAtSec: Long) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private var track: ScrobbleTrack? = null
    private var length = 0L
    private var heard = 0L
    private var last = 0L
    private var startedAt = 0L
    private var started = false
    private var announced = false
    private var counted = false

    /** What plays ([track]: null for nothing, or nothing scrobbled), where ([timeMs] of [lengthMs]), and whether it plays. */
    fun sample(track: ScrobbleTrack?, timeMs: Long, lengthMs: Long, playing: Boolean) {
        if (track?.key != this.track?.key) {
            this.track = track
            begin(timeMs, lengthMs)
        }
        val current = this.track ?: return
        if (lengthMs > 0) length = lengthMs
        val step = timeMs - last
        last = timeMs
        if (started && timeMs < ListenCounter.RESTART_MS && step < -ListenCounter.RESTART_MS) {
            // from its beginning again: a play of its own
            begin(timeMs, lengthMs)
        }
        if (playing && step in 1..ListenCounter.MAX_STEP_MS) heard += step
        if (playing && !started) {
            started = true
            startedAt = clock() - timeMs.coerceAtLeast(0)
        }
        if (started && !announced && scrobbleable()) {
            announced = true
            onStarted(current)
        }
        if (!counted && scrobbleable() && heard >= threshold()) {
            counted = true
            onScrobble(current, startedAt / 1_000)
        }
    }

    private fun begin(timeMs: Long, lengthMs: Long) {
        length = lengthMs
        heard = 0
        last = timeMs
        started = false
        announced = false
        counted = false
    }

    /** Last.fm scrobbles tracks longer than 30 seconds; a length not known yet waits. */
    private fun scrobbleable(): Boolean = length > MIN_LENGTH_MS

    private fun threshold(): Long = minOf(length / 2, ListenCounter.MAX_THRESHOLD_MS)

    companion object {
        /** A track this long or shorter is not scrobbled. */
        const val MIN_LENGTH_MS = 30_000L
    }
}
