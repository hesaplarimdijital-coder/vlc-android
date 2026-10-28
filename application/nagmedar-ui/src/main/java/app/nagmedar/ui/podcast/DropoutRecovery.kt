package app.nagmedar.ui.podcast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.nagmedar.ui.R
import app.nagmedar.ui.playback.DropoutVerdict
import app.nagmedar.ui.playback.StreamDropout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.tools.AppScope
import org.videolan.tools.Notices
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.DropoutGuard

/**
 * A streamed podcast episode libVLC ends or fails on before its end, its connection dropped ([StreamDropout]): opened
 * again where it was after a pause, once there is a network again; stopped, its place kept, with a notice when that does
 * not help. The playlist's [DropoutGuard] while the service lives, set by [PodcastPlaybackObserver], which tells it what
 * plays at each look ([look]) and starts its [EpisodeTracker] again on each opening ([reopened]). All on the main thread.
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

    @Volatile
    private var waiting = false

    override val recovering: Boolean get() = waiting

    /**
     * One look of the observer: [location] is loaded, the episode [context] (null for another media), at [timeMs] of
     * [lengthMs]. After a dropout libVLC's state is the dropped stream's (it still says it plays, at the place it
     * stopped), so until the stream opened again plays a second past the place it was opened at, the looks are not taken.
     */
    fun look(location: String?, context: EpisodeContext?, timeMs: Long, lengthMs: Long, playing: Boolean) {
        if (location != this.location) {
            reset()
            this.location = location
            seenMs = 0L
            seenLengthMs = 0L
        }
        episode = context
        if (context == null || !playing || timeMs <= 0L) return
        if (reopenedAtMs >= 0L && (!opened || timeMs < reopenedAtMs + PLAYS_AGAIN_MS)) return
        seenMs = timeMs
        if (lengthMs > 0L) seenLengthMs = lengthMs
        if (reopenedAtMs < 0L) return
        // it plays again: the service's notification follows the player again
        waiting = false
        playedSinceReopen = true
        if (timeMs >= reopenedAtMs + StreamDropout.RECOVERED_MS) forget()
    }

    override fun takesOver(location: String, timeMs: Long, lengthMs: Long, error: Boolean): Boolean {
        val episode = episode ?: return false
        if (location != this.location) return false
        // an opening again is waited for: libVLC's stop after its error, or another word of the same dropout
        if (reopenedAtMs >= 0L && !opened) return true
        val position = maxOf(seenMs, timeMs)
        val duration = lengthMs.takeIf { it > 0L } ?: seenLengthMs.takeIf { it > 0L } ?: episode.durationMs
        val verdict = StreamDropout.verdict(streamed(location), position, duration, retries, error, droppedAtMs, playedSinceReopen)
        return when (verdict) {
            DropoutVerdict.End -> {
                reset()
                false
            }
            DropoutVerdict.GiveUp -> {
                giveUp()
                true
            }
            is DropoutVerdict.Reopen -> {
                reopen(location, maxOf(position, droppedAtMs), verdict)
                true
            }
        }
    }

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

    /** Stopped where it got to (the tracker keeps the place), and said. */
    private fun giveUp() {
        reset()
        service.stop()
        Notices.show(service, R.string.nag_pod_dropout_gave_up, long = true)
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
