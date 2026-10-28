package app.nagmedar.ui.podcast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import app.nagmedar.ui.R
import app.nagmedar.ui.playback.DropoutVerdict
import app.nagmedar.ui.playback.StreamCounters
import app.nagmedar.ui.playback.StreamDropout
import app.nagmedar.ui.playback.StreamStall
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.tools.AppScope
import org.videolan.tools.KEY_NETWORK_CACHING_VALUE
import org.videolan.tools.Notices
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.DropoutGuard

/**
 * A streamed podcast episode libVLC ends or fails on before its end, its connection dropped ([StreamDropout]): opened
 * again where it was after a pause, once there is a network again; stopped, its place kept, with a notice when that does
 * not help. A live stream (a radio station, a stream with no length) that drops is connected to again the same way, and
 * given up with a notice of its own. A network stream that plays with nothing coming ([StreamStall]) has dropped too:
 * libVLC is stopped on it and it is opened again. The playlist's [DropoutGuard] while the service lives, set by
 * [PodcastPlaybackObserver], which tells it what plays at each look ([look]) and starts its [EpisodeTracker] again on
 * each opening ([reopened]). All on the main thread.
 */
internal class DropoutRecovery(
    private val service: PlaybackService,
    private val reopened: () -> Unit,
) : DropoutGuard {
    private var location: String? = null
    private var episode: EpisodeContext? = null
    private var seenMs = 0L
    private var seenLengthMs = 0L
    private var retries = 0
    private var droppedAtMs = -1L
    private var reopenedAtMs = -1L
    private var opened = false
    private var playedSinceReopen = false
    private var job: Job? = null
    private var stall = StreamStall(StreamStall.MIN_STALL_MS)

    @Volatile
    private var waiting = false

    override val recovering: Boolean get() = waiting

    /**
     * One look of the observer: [location] is loaded, the episode [context] (null for another media), at [timeMs] of
     * [lengthMs]. After a dropout libVLC's state is the dropped stream's (it still says it plays, at the place it
     * stopped), so until the stream opened again plays a second past the place it was opened at, the looks are not taken.
     * A network stream whose time stands still while it plays is a stall ([StreamStall], [stalled]); [nowMs] is a
     * monotonic clock.
     */
    fun look(location: String?, context: EpisodeContext?, timeMs: Long, lengthMs: Long, playing: Boolean, nowMs: Long = SystemClock.elapsedRealtime()) {
        if (location != this.location) {
            reset()
            this.location = location
            seenMs = 0L
            seenLengthMs = 0L
            stall = StreamStall(StreamStall.limitMs(networkCachingMs()))
        }
        episode = context
        if (location != null && streamed(location)) {
            // waiting to be opened again, libVLC's state is the dropped stream's: no stall to see
            val watched = playing && !(reopenedAtMs >= 0L && !opened)
            val liveNow = context == null && lengthMs <= 0L && seenLengthMs <= 0L
            if (stall.look(timeMs, watched, nowMs, if (watched) counters() else null, liveNow)) {
                stalled(location, timeMs, lengthMs)
                return
            }
        } else if (context == null) {
            return
        }
        if (!playing || timeMs <= 0L) return
        if (reopenedAtMs >= 0L && (!opened || timeMs < reopenedAtMs + PLAYS_AGAIN_MS)) return
        seenMs = timeMs
        if (lengthMs > 0L) seenLengthMs = lengthMs
        if (reopenedAtMs < 0L) return
        // it plays again: the service's notification follows the player again
        waiting = false
        playedSinceReopen = true
        if (timeMs >= reopenedAtMs + StreamDropout.RECOVERED_MS) {
            // a live stream keeps where it ended: one that ends there again is a file with no length that ended
            val lastEnd = droppedAtMs
            forget()
            if (live()) droppedAtMs = lastEnd
        }
    }

    override fun takesOver(location: String, timeMs: Long, lengthMs: Long, error: Boolean): Boolean {
        if (location != this.location) return false
        if (episode == null && !streamed(location)) return false
        // an opening again is waited for: libVLC's stop after its error, or another word of the same dropout
        if (reopenedAtMs >= 0L && !opened) return true
        return when (val verdict = verdict(location, timeMs, lengthMs, error)) {
            DropoutVerdict.End -> {
                reset()
                false
            }
            DropoutVerdict.GiveUp -> {
                giveUp()
                true
            }
            is DropoutVerdict.Reopen -> {
                reopen(location, dropPlace(timeMs), verdict)
                true
            }
        }
    }

    /**
     * libVLC plays the stream with nothing coming: a dropout like an error. It is stopped on it (its stop comes as an
     * end, which the opening again takes over) and opened again; at its end, or not libVLC's, it is left as it is.
     */
    private fun stalled(location: String, timeMs: Long, lengthMs: Long) {
        when (val verdict = verdict(location, timeMs, lengthMs, error = true)) {
            DropoutVerdict.End -> Unit
            DropoutVerdict.GiveUp -> giveUp()
            is DropoutVerdict.Reopen -> if (service.playlistManager.dropStalled()) reopen(location, dropPlace(timeMs), verdict)
        }
    }

    /** What [StreamDropout] says of an end, an error or a stall of the stream at [location], last at [timeMs] of [lengthMs]. */
    private fun verdict(location: String, timeMs: Long, lengthMs: Long, error: Boolean): DropoutVerdict {
        val episode = episode
        val duration = lengthMs.takeIf { it > 0L } ?: seenLengthMs.takeIf { it > 0L } ?: episode?.durationMs ?: 0L
        return StreamDropout.verdict(
            streamed(location), maxOf(seenMs, timeMs), duration, retries, error, droppedAtMs, playedSinceReopen, episode = episode != null,
        )
    }

    /** The place of a dropout at [timeMs]: an episode's furthest; a live stream's in this connection. */
    private fun dropPlace(timeMs: Long): Long = if (episode != null) maxOf(seenMs, timeMs, droppedAtMs) else maxOf(seenMs, timeMs)

    /** A stream that is not an episode and has shown no length: a radio station, a live stream. */
    private fun live(): Boolean = episode == null && seenLengthMs <= 0L

    /** What libVLC counts of the stream it plays (bytes read, audio played); null when it is not libVLC's or it counts nothing. */
    private fun counters(): StreamCounters? = runCatching { service.playlistManager.currentStats() }.getOrNull()
        ?.let { StreamCounters(it.readBytes, it.demuxReadBytes, it.playedAbuffers) }

    private fun networkCachingMs(): Int = runCatching { Settings.getInstance(service).getInt(KEY_NETWORK_CACHING_VALUE, 0) }.getOrDefault(0)

    override fun stopped() = reset()

    /** The service is going. */
    fun end() {
        reset()
        location = null
        episode = null
    }

    private fun reopen(location: String, position: Long, verdict: DropoutVerdict.Reopen) {
        job?.cancel()
        retries = verdict.attempt
        droppedAtMs = position
        reopenedAtMs = verdict.atMs
        opened = false
        playedSinceReopen = false
        waiting = true
        // a live stream's time starts again at 0 on the new connection
        if (episode == null) seenMs = 0L
        job = AppScope.launch {
            delay(verdict.delayMs)
            if (!awaitNetwork(service, StreamDropout.OFFLINE_WAIT_MS)) {
                giveUp()
                return@launch
            }
            if (service.currentMediaLocation != location || !service.playlistManager.reopenCurrent(verdict.atMs)) {
                reset()
                return@launch
            }
            opened = true
            // paused meanwhile, it opens paused: a pause as any other from now on
            if (!service.isPlaying) waiting = false
            reopened()
        }
    }

    /** Stopped where it got to (the tracker keeps an episode's place), and said. */
    private fun giveUp() {
        val notice = if (episode != null) R.string.nag_pod_dropout_gave_up else R.string.nag_live_dropout_gave_up
        reset()
        service.stop()
        Notices.show(service, notice, long = true)
    }

    private fun reset() {
        job?.cancel()
        job = null
        forget()
    }

    /** The dropout is over: the openings are counted afresh. */
    private fun forget() {
        retries = 0
        droppedAtMs = -1L
        reopenedAtMs = -1L
        opened = false
        playedSinceReopen = false
        waiting = false
    }

    private companion object {
        /** Played this far past the place it was opened again at, the stream plays again. */
        const val PLAYS_AGAIN_MS = 1_000L

        fun streamed(location: String): Boolean =
            location.startsWith("http://", ignoreCase = true) || location.startsWith("https://", ignoreCase = true)

        /** Whether a network with internet access is there, waiting [timeoutMs] at most for one to come. */
        suspend fun awaitNetwork(context: Context, timeoutMs: Long): Boolean {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
            if (hasInternet(manager)) return true
            val available = CompletableDeferred<Unit>()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    available.complete(Unit)
                }
            }
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            try {
                manager.registerNetworkCallback(request, callback)
            } catch (e: RuntimeException) {
                // no callback to be had (too many registered, a firmware's refusal): libVLC tries, and says
                return true
            }
            return try {
                withTimeoutOrNull(timeoutMs) { available.await() } != null
            } finally {
                runCatching { manager.unregisterNetworkCallback(callback) }
            }
        }

        private fun hasInternet(manager: ConnectivityManager): Boolean = runCatching {
            manager.activeNetwork?.let(manager::getNetworkCapabilities)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
    }
}
