package app.nagmedar.ui.lyrics

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** S-1004: what the lyrics views have of a track. */
@Immutable
sealed interface LyricsLoad {
    /** The track they are for; null before any. */
    val track: LyricsTrack?

    data object Idle : LyricsLoad {
        override val track: LyricsTrack? get() = null
    }

    data class Looking(override val track: LyricsTrack) : LyricsLoad

    data class Found(override val track: LyricsTrack, val lyrics: Lyrics) : LyricsLoad

    data class Missing(override val track: LyricsTrack) : LyricsLoad
}

/**
 * S-1004: the playing track's lyrics for every view of them (the now playing pane, the phone's
 * player, the full-screen view) and for the delay keys: looked for once a track, when a view shows
 * them. The delay goes to every view at once, in steps of [STEP_MS], and is kept for the track a
 * moment after the last change.
 */
@Stable
class LyricsSession(
    private val finder: () -> LyricsFinder?,
    private val scope: CoroutineScope,
    private val keepAfterMs: Long = KEEP_AFTER_MS,
) {

    var load: LyricsLoad by mutableStateOf(LyricsLoad.Idle)
        private set

    private var looking: Job? = null
    private var keeping: Job? = null

    /** The lyrics shown, when there are any. */
    val lyrics: Lyrics? get() = (load as? LyricsLoad.Found)?.lyrics

    /** A view shows [track]'s lyrics: they are looked for, unless they were already. */
    fun show(track: LyricsTrack?) {
        if (track == load.track) return
        looking?.cancel()
        if (track == null) {
            load = LyricsLoad.Idle
            return
        }
        look(track)
    }

    /** Looks for the track's lyrics again (LRCLIB was turned on, an .lrc was put beside it). */
    fun again() {
        load.track?.let(::look)
    }

    /** The delay [byMs] later (earlier when negative); the new delay, or null without synced lyrics. */
    fun shift(byMs: Long): Long? = lyrics?.let { setDelay(it.offsetMs + byMs) }

    /** The delay set to [ms] (positive: the lines come later), kept for the track; null without synced lyrics. */
    fun setDelay(ms: Long): Long? {
        val found = load as? LyricsLoad.Found ?: return null
        if (!found.lyrics.synced) return null
        val value = ms.coerceIn(-MAX_DELAY_MS, MAX_DELAY_MS)
        val shifted = found.lyrics.copy(offsetMs = value)
        load = found.copy(lyrics = shifted)
        keeping?.cancel()
        keeping = scope.launch {
            delay(keepAfterMs)
            runCatching { finder()?.setOffset(found.track, shifted, value) }
        }
        return value
    }

    private fun look(track: LyricsTrack) {
        load = LyricsLoad.Looking(track)
        looking = scope.launch {
            val found = try {
                finder()?.find(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            load = if (found != null) LyricsLoad.Found(track, found) else LyricsLoad.Missing(track)
        }
    }

    companion object {
        /** A key's or a button's step: a tenth of a second. */
        const val STEP_MS = 100L

        /** The delay goes no further than a minute either way. */
        const val MAX_DELAY_MS = 60_000L

        /** A delay is kept this long after its last change: a key held down writes once. */
        private const val KEEP_AFTER_MS = 600L

        @Volatile
        private var shared: LyricsSession? = null

        /** The app's session, on the main thread's scope; made by ProcessState.warmUp when that ran first, else in the caller's snapshot. */
        fun get(context: Context): LyricsSession = shared ?: synchronized(this) {
            shared ?: context.applicationContext.let { app -> LyricsSession({ LyricsCenter.finder(app) }, MainScope()) }.also { shared = it }
        }
    }
}
