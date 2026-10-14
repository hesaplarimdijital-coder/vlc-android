package app.nagmedar.ui.scrobble

import app.nagmedar.data.ScrobbleEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Starts the job that sends the queue once there is a network. */
interface ScrobbleScheduler {
    /** Sends the queue when the network allows; [now]: at once, not after the wait of an earlier failure. */
    fun schedule(now: Boolean = false)
}

/**
 * S-1008: what the playback service's samples come to: "now playing" for each service that sends
 * when a track starts, and, when a play counts ([ScrobbleTracker]), a queued scrobble for each
 * service that is on, with the job that sends the queue ([ScrobbleScheduler]) started. A scrobble
 * is queued before it is sent, whatever the network: the queue is what survives a restart.
 * Nothing happens, not even a look at the track, while no service is on.
 */
class ScrobbleHub(
    private val settings: ScrobbleSettings,
    private val store: ScrobbleStore,
    private val services: List<ScrobbleService>,
    private val scheduler: ScrobbleScheduler,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val tracker = ScrobbleTracker(onStarted = ::started, onScrobble = ::scrobbled, clock = clock)

    /** Whether any service is on. */
    private val listening: Boolean get() = services.any { it.available && settings.enabled(it.id) }

    /**
     * What plays ([track]: what it is as a scrobble, null for what is not scrobbled), where, and
     * whether it plays; called about once a second. [track] is only asked for while a service is on.
     */
    fun sample(track: () -> ScrobbleTrack?, timeMs: Long, lengthMs: Long, playing: Boolean) {
        tracker.sample(if (listening) track() else null, timeMs, lengthMs, playing)
    }

    /** The service has started: what was queued before is sent, if the network allows. */
    fun resume() {
        scope.launch(io) {
            if (services.any { settings.enabled(it.id) && store.count(it.id) > 0 }) scheduler.schedule()
        }
    }

    private fun started(track: ScrobbleTrack) {
        for (service in services) {
            if (!service.available || !settings.active(service.id)) continue
            scope.launch(io) {
                val secret = settings.secret(service.id) ?: return@launch
                service.nowPlaying(secret, track)
            }
        }
    }

    private fun scrobbled(track: ScrobbleTrack, startedAtSec: Long) {
        val queued = services.filter { it.available && settings.enabled(it.id) }
        if (queued.isEmpty()) return
        scope.launch(io) {
            val now = clock()
            for (service in queued) {
                store.add(
                    ScrobbleEntry(
                        service = service.id.key,
                        artist = track.artist,
                        track = track.title,
                        album = track.album,
                        albumArtist = track.albumArtist,
                        durationSec = track.durationSec,
                        listenedAt = startedAtSec,
                        queuedAt = now,
                    ),
                )
            }
            scheduler.schedule()
        }
    }
}
