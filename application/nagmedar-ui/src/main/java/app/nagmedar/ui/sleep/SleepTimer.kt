package app.nagmedar.ui.sleep

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.tools.AppScope
import org.videolan.vlc.PlaybackService

/** What the sleep timer works on: the playback service in the app, a recorder in the tests. */
interface SleepTarget {
    val playing: Boolean

    /** Whether an item is loaded, playing or not: paused, or being opened again after its stream dropped. */
    val loaded: Boolean get() = playing

    /** 0–200, as the service's own volume goes. */
    val volume: Int

    fun setVolume(volume: Int)

    /** The volume the next play starts at: the listener's own, without a podcast show's boost over [volume]. */
    val userVolume: Int get() = volume

    /** Puts the listener's [volume] back (with the boost over it while a boosted episode still plays). */
    fun restoreUserVolume(volume: Int) = setVolume(volume)

    /** What is left of the item playing, ms; null when its length is not known (a stream). */
    val trackLeftMs: Long?

    fun stop()

    /** Playback stops when the item playing ends; false when nothing plays. */
    fun stopAtTrackEnd(): Boolean

    fun goOnAfterTrack()
}

/**
 * S-1007: the sleep timer. Set for some minutes, playback stops when they are up, the volume let
 * down over the last [FADE_MS] first ([State.fade]) and put back after; with [State.atTrackEnd]
 * the track playing then plays to its end instead, the fade over its last seconds. Set for the
 * track's end alone, playback stops when the track playing ends. [state] is what the bars and the
 * window show.
 */
class SleepTimer(
    private val target: () -> SleepTarget?,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** A timer running: [endsAt] null while the track playing is awaited ([waitingForTrackEnd]). */
    @Immutable
    data class State(val endsAt: Long?, val atTrackEnd: Boolean, val fade: Boolean, val waitingForTrackEnd: Boolean = false)

    private val running = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> get() = running

    private var job: Job? = null

    /** The volume before the fade began, which the fade lets down. */
    private var faded: Int? = null

    /** The listener's volume before the fade began, put back when the timer ends. */
    private var restored: Int? = null

    /** What is left of the time, ms; null without a time. */
    fun remainingMs(): Long? = running.value?.endsAt?.let { (it - now()).coerceAtLeast(0) }

    fun start(minutes: Int, atTrackEnd: Boolean, fade: Boolean) {
        cancel()
        running.value = State(now() + minutes * 60_000L, atTrackEnd, fade)
        watch()
    }

    /** Playback stops when the track playing ends; with [fade], the volume let down over its last seconds. */
    fun startAtTrackEnd(fade: Boolean = false) {
        cancel()
        if (target()?.stopAtTrackEnd() != true) return
        running.value = State(null, atTrackEnd = true, fade = fade, waitingForTrackEnd = true)
        watch()
    }

    /** [minutes] more; a track's end awaited after the time was up becomes a time again. */
    fun extend(minutes: Int) {
        val current = running.value ?: return
        // set for the track's end alone: nothing to add to
        val endsAt = current.endsAt ?: return
        if (current.waitingForTrackEnd) target()?.goOnAfterTrack()
        restoreVolume(target())
        running.value = current.copy(endsAt = maxOf(endsAt, now()) + minutes * 60_000L, waitingForTrackEnd = false)
        watch()
    }

    fun cancel() {
        job?.cancel()
        job = null
        val current = running.value
        if (current?.waitingForTrackEnd == true) target()?.goOnAfterTrack()
        restoreVolume(target())
        running.value = null
    }

    /** The volume the fade took, back on [player]: it plays at it next time. */
    private fun restoreVolume(player: SleepTarget?) {
        val volume = restored ?: return
        faded = null
        restored = null
        player?.restoreUserVolume(volume)
    }

    private fun watch() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                if (!tick()) break
                delay(TICK_MS)
            }
        }
    }

    /** One look at the clock and the player; false when the timer is done. */
    internal fun tick(): Boolean {
        val current = running.value ?: return false
        val player = target()
        if (current.waitingForTrackEnd) {
            // the service stopped after the track, or someone stopped it; a pause keeps the stop after the track, and the timer
            if (player == null || !player.loaded) {
                finish(player)
                return false
            }
            if (current.fade) player.trackLeftMs?.let { fade(player, it) }
            return true
        }
        val left = (current.endsAt ?: return false) - now()
        if (left <= 0) {
            when {
                player == null || !player.playing -> finish(player)
                current.atTrackEnd && player.stopAtTrackEnd() -> {
                    running.value = current.copy(waitingForTrackEnd = true)
                    return true
                }
                else -> {
                    player.stop()
                    finish(player)
                }
            }
            return false
        }
        // the track played out fades at its own end, not at the time's
        if (current.fade && !current.atTrackEnd && player != null && player.playing) fade(player, left)
        return true
    }

    /** The volume let down as [left] ms run out of the last [FADE_MS]. */
    private fun fade(player: SleepTarget, left: Long) {
        if (left > FADE_MS) return
        val start = faded ?: player.volume.also {
            faded = it
            // not a podcast show's boost, which ends with its episode: the listener's own volume
            restored = player.userVolume
        }
        player.setVolume((start * left.coerceAtLeast(0) / FADE_MS).toInt().coerceIn(0, start))
    }

    private fun finish(player: SleepTarget?) {
        running.value = null
        // the next play starts at the volume it had
        restoreVolume(player)
        job = null
    }

    companion object {
        /** The fade before a timed stop. */
        const val FADE_MS = 30_000L

        internal const val TICK_MS = 500L

        /** The window's choices, minutes. */
        val PRESETS = listOf(15, 30, 45, 60, 90, 120)
    }
}

/** The timer on the playback service: one for the app. */
object Sleep {
    val timer = SleepTimer(target = { PlaybackService.instance?.let(::ServiceSleepTarget) }, scope = AppScope)
}

private class ServiceSleepTarget(private val service: PlaybackService) : SleepTarget {
    override val playing: Boolean get() = service.isPlaying

    override val loaded: Boolean get() = service.playlistManager.hasCurrentMedia()

    override val volume: Int get() = service.volume

    override fun setVolume(volume: Int) {
        service.setVolume(volume)
    }

    override val userVolume: Int get() = service.userVolume

    override fun restoreUserVolume(volume: Int) = service.applyUserVolume(volume)

    override val trackLeftMs: Long?
        get() {
            val length = service.length.takeIf { it > 0 } ?: return null
            // at the speed it plays
            return ((length - service.getTime()).coerceAtLeast(0) / service.rate.coerceAtLeast(0.25f)).toLong()
        }

    override fun stop() = service.stop()

    override fun stopAtTrackEnd(): Boolean {
        val manager = service.playlistManager
        if (!manager.hasCurrentMedia()) return false
        manager.setStopAfterPosition(manager.currentIndex)
        return true
    }

    override fun goOnAfterTrack() {
        val manager = service.playlistManager
        if (manager.stopAfter == manager.currentIndex) manager.setStopAfterPosition(-1)
    }
}
