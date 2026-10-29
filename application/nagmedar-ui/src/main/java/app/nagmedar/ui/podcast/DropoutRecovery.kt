package app.nagmedar.ui.podcast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import androidx.annotation.StringRes
import app.nagmedar.ui.R
import app.nagmedar.ui.playback.DropoutVerdict
import app.nagmedar.ui.playback.StreamCounters
import app.nagmedar.ui.playback.StreamDropout
import app.nagmedar.ui.playback.StreamStall
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import kotlin.math.abs

/**
 * What [DropoutRecovery] works on: the playback service ([ServiceDropoutHost]) in the app, a fake in the tests. Main
 * thread.
 */
internal interface DropoutHost {
    /** The location of the item loaded; null with none. */
    val location: String?

    /** Whether the player is paused: the listener's pause, a call, headphones unplugged (or a stream waiting, paused). */
    val paused: Boolean

    /** Whether a stream waiting to be opened again was paused meanwhile, or when it dropped: it opens paused. */
    val reopenPaused: Boolean

    /** Whether the stream that plays can be sought in: a file can, a live stream cannot. */
    val seekable: Boolean

    /** libVLC's network caching, ms (0: its default). */
    val networkCachingMs: Int

    /** What libVLC counts of the stream it plays (bytes read, audio played); null when it is not libVLC's or counts nothing. */
    fun counters(): StreamCounters?

    /** The item opened again at [atMs] ([org.videolan.vlc.media.PlaylistManager.reopenCurrent]); false with nothing to open. */
    fun reopen(atMs: Long): Boolean

    /** libVLC stopped on a stalled stream ([org.videolan.vlc.media.PlaylistManager.dropStalled]); false when it does not have it. */
    fun dropStalled(): Boolean

    /** Playback stopped, the queue cleared. */
    fun stop()

    /** What waits changed by itself (given up while paused): the player says it, the service lets go. */
    fun changed()

    fun notice(@StringRes text: Int)

    suspend fun wait(ms: Long)

    /** Whether a network with internet access is there, waiting [timeoutMs] at most for one to come. */
    suspend fun awaitNetwork(timeoutMs: Long): Boolean
}

/**
 * A streamed podcast episode libVLC ends or fails on before its end, its connection dropped ([StreamDropout]): opened
 * again where it was after a pause, once there is a network again; stopped, its place kept, with a notice when that does
 * not help. A live stream (a radio station, a stream with no length) that drops is connected to again the same way, and
 * given up with a notice of its own. A network stream that plays with nothing coming ([StreamStall]) has dropped too:
 * libVLC is stopped on it and it is opened again. The playlist's [DropoutGuard] while the service lives, set by
 * [PodcastPlaybackObserver], which tells it what plays at each look ([look]) and starts its [EpisodeTracker] again on
 * each opening ([reopened]). All on the main thread.
 *
 * Two times of a dropout: the opening is pending ([reopenPending]: libVLC stopped, the playlist shows the stream playing
 * or paused as it was, and a pause, a call or headphones unplugged make it open paused), then it is opened and plays on
 * (a pause is libVLC's own again). The service holds on ([recovering]: in the foreground, the Wi-Fi awake) from the drop
 * until it plays again, or the wait ends; not while it is paused once opened. Given up while paused, the stream stays
 * loaded and paused, and is opened when the listener plays ([playRequested]).
 */
internal class DropoutRecovery(
    private val host: DropoutHost,
    private val scope: CoroutineScope = AppScope,
    private val reopened: () -> Unit,
) : DropoutGuard {
    private var location: String? = null
    private var episode: EpisodeContext? = null
    private var seenMs = 0L
    private var seenLengthMs = 0L

    /** Whether the stream could be sought in when it last played (a live stream cannot). */
    private var seenSeekable = false
    private var retries = 0
    private var droppedAtMs = -1L

    /** How many drops came one after the other at about [droppedAtMs] (a live stream's place in its connection). */
    private var samePlaceDrops = 0
    private var reopenedAtMs = -1L
    private var opened = false
    private var playedSinceReopen = false
    private var job: Job? = null

    /** Given up while paused: the opening made when the listener plays ([playRequested]). */
    private var parked: DropoutVerdict.Reopen? = null
    private var stall = StreamStall(StreamStall.MIN_STALL_MS)

    @Volatile
    private var waiting = false

    override val reopenPending: Boolean get() = reopenedAtMs >= 0L && !opened

    override val recovering: Boolean get() = waiting && !(opened && host.paused)

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
            seenSeekable = false
            stall = StreamStall(StreamStall.limitMs(host.networkCachingMs))
        }
        episode = context
        if (location != null && streamed(location)) {
            // waiting to be opened again, libVLC's state is the dropped stream's: no stall to see
            val watched = playing && !reopenPending
            val liveNow = context == null && lengthMs <= 0L && seenLengthMs <= 0L
            if (stall.look(timeMs, watched, nowMs, if (watched) host.counters() else null, liveNow)) {
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
        seenSeekable = host.seekable
        if (reopenedAtMs < 0L) return
        // it plays again: the service's notification follows the player again
        waiting = false
        playedSinceReopen = true
        if (timeMs >= reopenedAtMs + StreamDropout.RECOVERED_MS) {
            // a live stream keeps where it ended, and how many times in a row: one that ends there again may be a file with no length
            val lastEnd = droppedAtMs
            val repeats = samePlaceDrops
            forget()
            if (live()) {
                droppedAtMs = lastEnd
                samePlaceDrops = repeats
            }
        }
    }

    override fun takesOver(location: String, timeMs: Long, lengthMs: Long, error: Boolean): Boolean {
        if (location != this.location) return false
        if (episode == null && !streamed(location)) return false
        // an opening again is waited for: libVLC's stop after its error, or another word of the same dropout
        if (reopenPending) return true
        return when (val verdict = verdict(location, timeMs, lengthMs, error)) {
            DropoutVerdict.End -> {
                reset()
                false
            }
            DropoutVerdict.GiveUp -> {
                giveUp(location, dropPlace(timeMs))
                true
            }
            is DropoutVerdict.Reopen -> {
                dropped(location, dropPlace(timeMs), verdict)
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
            DropoutVerdict.GiveUp -> giveUp(location, dropPlace(timeMs))
            is DropoutVerdict.Reopen -> if (host.dropStalled()) dropped(location, dropPlace(timeMs), verdict)
        }
    }

    /** What [StreamDropout] says of an end, an error or a stall of the stream at [location], last at [timeMs] of [lengthMs]. */
    private fun verdict(location: String, timeMs: Long, lengthMs: Long, error: Boolean): DropoutVerdict {
        val episode = episode
        val duration = lengthMs.takeIf { it > 0L } ?: seenLengthMs.takeIf { it > 0L } ?: episode?.durationMs ?: 0L
        val position = maxOf(seenMs, timeMs)
        return StreamDropout.verdict(
            streamed(location), position, duration, retries, error, droppedAtMs, playedSinceReopen, episode = episode != null,
            seekable = seenSeekable, repeats = repeatsAt(position),
        )
    }

    /** The drops before one at [positionMs] that came one after the other at about the same place. */
    private fun repeatsAt(positionMs: Long): Int =
        if (droppedAtMs >= 0L && abs(positionMs - droppedAtMs) <= StreamDropout.SAME_PLACE_MS) samePlaceDrops else 0

    /** The place of a dropout at [timeMs]: an episode's furthest; a live stream's in this connection. */
    private fun dropPlace(timeMs: Long): Long = if (episode != null) maxOf(seenMs, timeMs, droppedAtMs) else maxOf(seenMs, timeMs)

    /** A stream that is not an episode and has shown no length: a radio station, a live stream. */
    private fun live(): Boolean = episode == null && seenLengthMs <= 0L

    override fun stopped() = reset()

    override fun playRequested() {
        val again = parked ?: return
        val location = location ?: return
        parked = null
        reopen(location, droppedAtMs, again)
    }

    /** The service is going. */
    fun end() {
        reset()
        location = null
        episode = null
    }

    /** The stream dropped at [position]: counted, and opened again as [verdict] says. */
    private fun dropped(location: String, position: Long, verdict: DropoutVerdict.Reopen) {
        samePlaceDrops = repeatsAt(position) + 1
        reopen(location, position, verdict)
    }

    private fun reopen(location: String, position: Long, verdict: DropoutVerdict.Reopen) {
        job?.cancel()
        retries = verdict.attempt
        droppedAtMs = position
        reopenedAtMs = verdict.atMs
        opened = false
        playedSinceReopen = false
        parked = null
        waiting = true
        // a live stream's time starts again at 0 on the new connection
        if (episode == null) seenMs = 0L
        job = scope.launch {
            if (verdict.delayMs > 0L) host.wait(verdict.delayMs)
            if (!host.awaitNetwork(StreamDropout.OFFLINE_WAIT_MS)) {
                giveUp(location, position)
                return@launch
            }
            // paused meanwhile, it opens paused: a pause as any other from then on, and the service lets go
            val paused = host.reopenPaused
            if (host.location != location || !host.reopen(verdict.atMs)) {
                reset()
                return@launch
            }
            opened = true
            if (paused) waiting = false
            reopened()
        }
    }

    /**
     * The openings did not help: stopped where it got to (the tracker keeps an episode's place), and said. Paused while
     * it waited, it stays loaded and paused instead, and is opened again when the listener plays.
     */
    private fun giveUp(location: String, position: Long) {
        val live = episode == null
        if (host.reopenPaused && host.location == location) {
            job?.cancel()
            job = null
            retries = 0
            droppedAtMs = position
            playedSinceReopen = false
            val again = DropoutVerdict.Reopen(StreamDropout.reopenAt(position, live), 0L, 1)
            // still pending: the playlist shows it paused, a play opens it
            reopenedAtMs = again.atMs
            opened = false
            waiting = false
            parked = again
            host.changed()
            host.notice(if (live) R.string.nag_live_dropout_paused else R.string.nag_pod_dropout_gave_up)
            return
        }
        reset()
        host.stop()
        host.notice(if (live) R.string.nag_live_dropout_gave_up else R.string.nag_pod_dropout_gave_up)
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
        samePlaceDrops = 0
        reopenedAtMs = -1L
        opened = false
        playedSinceReopen = false
        parked = null
        waiting = false
    }

    private companion object {
        /** Played this far past the place it was opened again at, the stream plays again. */
        const val PLAYS_AGAIN_MS = 1_000L

        fun streamed(location: String): Boolean =
            location.startsWith("http://", ignoreCase = true) || location.startsWith("https://", ignoreCase = true)
    }
}

/** [DropoutHost] on the playback service. */
internal class ServiceDropoutHost(private val service: PlaybackService) : DropoutHost {
    override val location: String? get() = service.currentMediaLocation

    override val paused: Boolean get() = service.isPaused

    override val reopenPaused: Boolean get() = service.playlistManager.dropoutHold.paused

    override val seekable: Boolean get() = runCatching { service.isSeekable }.getOrDefault(false)

    override val networkCachingMs: Int
        get() = runCatching { Settings.getInstance(service).getInt(KEY_NETWORK_CACHING_VALUE, 0) }.getOrDefault(0)

    override fun counters(): StreamCounters? = runCatching { service.playlistManager.currentStats() }.getOrNull()
        ?.let { StreamCounters(it.readBytes, it.demuxReadBytes, it.playedAbuffers) }

    override fun reopen(atMs: Long): Boolean = service.playlistManager.reopenCurrent(atMs)

    override fun dropStalled(): Boolean = service.playlistManager.dropStalled()

    override fun stop() = service.stop()

    override fun changed() = service.playlistManager.dropoutChanged()

    override fun notice(text: Int) = Notices.show(service, text, long = true)

    override suspend fun wait(ms: Long) = delay(ms)

    override suspend fun awaitNetwork(timeoutMs: Long): Boolean = awaitNetwork(service, timeoutMs)

    private companion object {
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

        fun hasInternet(manager: ConnectivityManager): Boolean = runCatching {
            manager.activeNetwork?.let(manager::getNetworkCapabilities)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
    }
}
