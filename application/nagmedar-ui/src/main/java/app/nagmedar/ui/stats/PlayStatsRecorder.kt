package app.nagmedar.ui.stats

import app.nagmedar.ui.scrobble.ScrobbleTrack
import app.nagmedar.ui.scrobble.Scrobbling
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.AppScope
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.PlaybackObserver

/**
 * S-808: the playback service's music, counted into Nağmedar's statistics for as long as the
 * service lives, whatever started it. A look a second while it plays, every few seconds while it
 * does not; videos and streams are not counted. The same looks go to the scrobbling (S-1008), which
 * decides for itself what it sends.
 */
class PlayStatsRecorder : PlaybackObserver {

    private var job: Job? = null

    override fun attach(service: PlaybackService) {
        val context = service.applicationContext
        val scrobbling = Scrobbling.get(context).hub
        scrobbling.resume()
        val counter = ListenCounter(
            onPlayed = { key -> TrackStatsIndex.record(context, key, played = true) },
            onSkipped = { key -> TrackStatsIndex.record(context, key, played = false) },
        )
        job?.cancel()
        job = AppScope.launch {
            // the service finishes its start first
            var playing = false
            while (isActive) {
                delay(if (playing) SAMPLE_MS else IDLE_MS)
                playing = runCatching { service.isPlaying }.getOrDefault(false)
                val media = runCatching { service.currentMediaWrapper }.getOrNull()
                val key = media?.let(::statsKey)
                val time = if (media == null) 0L else service.getTime()
                val length = if (media == null) 0L else service.length
                if (key == null) {
                    counter.sample(null, 0, 0, playing = false)
                } else {
                    counter.sample(key, time, length, playing)
                }
                scrobbling.sample({ media?.let { ScrobbleTrack.of(it) } }, time, length, playing && media != null)
            }
        }
    }

    override fun detach() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val SAMPLE_MS = 1_000L
        const val IDLE_MS = 4_000L
    }
}

/** The key [media]'s statistics are kept by: a music file's uri; null for what is not counted. */
fun statsKey(media: MediaWrapper): String? {
    if (media.type != MediaWrapper.TYPE_AUDIO) return null
    val uri = media.uri ?: return null
    if (uri.scheme != "file" && uri.scheme != "content") return null
    return uri.toString()
}
