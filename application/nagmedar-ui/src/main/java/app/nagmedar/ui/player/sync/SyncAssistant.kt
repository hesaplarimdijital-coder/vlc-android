package app.nagmedar.ui.player.sync

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A moment marked: the film's time then ([filmMs]), the device's clock ([clockMs], counting on
 * through sleep) and the film's speed ([rate], 0 while it is paused).
 */
@Immutable
data class SyncMark(val filmMs: Long, val clockMs: Long, val rate: Float)

/** A difference put onto the subtitles' delay: from what delay to what, and whether the film remembered its delays before. */
@Immutable
data class AppliedSync(val fromMs: Long, val toMs: Long, val rememberedBefore: Boolean)

/** What the sync assistant reads and moves: the moment now, the subtitles' delay, the film's memory of it (S-604). */
interface SyncAssistTarget {
    /** The moment now; null without a film. */
    fun mark(): SyncMark?

    fun subtitleDelayMs(): Long

    fun setSubtitleDelay(ms: Long)

    /** Whether the film remembers its delays ("Remember for this file"). */
    fun remembered(): Boolean

    fun remember(on: Boolean)
}

/**
 * S-704: the subtitles synchronised by ear and eye, as VLC desktop does it: the moment a line is
 * heard is marked (Shift+H), then the moment its subtitle shows (Shift+J), in either order; their
 * difference goes onto the subtitles' delay (Shift+K): the subtitles come later when the sound
 * comes after them. The film remembers the delay; Ctrl+Shift+K takes the last one back.
 *
 * The player tells the film's time four times a second, too coarse for this: while the film plays
 * on between the marks, the clock measures the time between them, as VLC desktop's does.
 */
@Stable
class SyncAssistant(private val target: SyncAssistTarget) {
    /** When the line was heard. */
    var audioMark by mutableStateOf<SyncMark?>(null)
        private set

    /** When its subtitle showed. */
    var subtitleMark by mutableStateOf<SyncMark?>(null)
        private set

    /** The last difference applied, until it is taken back or another film plays. */
    var applied by mutableStateOf<AppliedSync?>(null)
        private set

    private var media: String? = null

    /** How much later the subtitles should come: positive when the sound comes after them; null while a mark is missing. */
    val differenceMs: Long?
        get() {
            val audio = audioMark ?: return null
            val subtitle = subtitleMark ?: return null
            return audio.filmMs - subtitle.filmMs
        }

    fun markAudio(): SyncMark? = target.mark()?.let { refined(it, subtitleMark) }?.also { audioMark = it }

    fun markSubtitle(): SyncMark? = target.mark()?.let { refined(it, audioMark) }?.also { subtitleMark = it }

    /** The difference onto the delay, which the film remembers; null while a mark is missing. The marks start again. */
    fun apply(): AppliedSync? {
        val difference = differenceMs ?: return null
        val from = target.subtitleDelayMs()
        val done = AppliedSync(from, from + difference, target.remembered())
        target.setSubtitleDelay(done.toMs)
        if (!done.rememberedBefore) target.remember(true)
        applied = done
        audioMark = null
        subtitleMark = null
        return done
    }

    /** The delay as it was before the last difference, and the film's memory of it; null when there is none to take back. */
    fun undo(): AppliedSync? {
        val last = applied ?: return null
        target.setSubtitleDelay(last.fromMs)
        if (!last.rememberedBefore) target.remember(false)
        applied = null
        return last
    }

    /** The marks gone, nothing applied; false when there were none. */
    fun clearMarks(): Boolean {
        if (audioMark == null && subtitleMark == null) return false
        audioMark = null
        subtitleMark = null
        return true
    }

    /** A film started playing (or went on): another one has nothing marked, nothing to take back. */
    fun onMediaStarted(uri: String?) {
        if (uri == media) return
        media = uri
        audioMark = null
        subtitleMark = null
        applied = null
    }

    /**
     * [mark] against the [other] one, the film playing on at the same speed in between: the
     * clock's time between them, finer than the player's. A pause or a jump in between shows as
     * the two disagreeing: the player's own times then.
     */
    private fun refined(mark: SyncMark, other: SyncMark?): SyncMark {
        if (other == null || mark.rate <= 0f || other.rate != mark.rate) return mark
        val byClock = ((mark.clockMs - other.clockMs) * mark.rate.toDouble()).roundToLong()
        val byFilm = mark.filmMs - other.filmMs
        if (abs(byClock - byFilm) > CLOCK_SLACK_MS) return mark
        return mark.copy(filmMs = other.filmMs + byClock)
    }

    companion object {
        /** The player's time lags by up to a quarter of a second at each mark: within this, the clock is believed. */
        const val CLOCK_SLACK_MS = 1_000L
    }
}
