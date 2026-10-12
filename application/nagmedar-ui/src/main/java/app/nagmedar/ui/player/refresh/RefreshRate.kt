package app.nagmedar.ui.player.refresh

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.nagmedar.ui.R
import org.videolan.tools.KEY_VIDEO_MATCH_FRAME_RATE
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * S-612: whether the screen's refresh rate follows the playing film's frame rate: always (a TV
 * over HDMI goes to 23,976 Hz and may be black for a moment as it does), only when the screen can
 * change without going black, or never.
 */
enum class RefreshMatch(val key: String, @StringRes val label: Int) {
    Always("always", R.string.nag_refresh_always),
    Seamless("seamless", R.string.nag_refresh_seamless),
    Off("off", R.string.nag_refresh_off),
    ;

    companion object {
        /** Where the choice is kept, with the other preferences of the new interface. */
        const val KEY = "nagmedar_refresh_match"

        /** Never a black screen unasked: the screen follows a film where it can do so seamlessly. */
        val Default = Seamless

        fun of(key: String?): RefreshMatch? = entries.firstOrNull { it.key == key }

        /** The choice kept in [prefs]; before there is one, VLC's own "Match display frame rate" switch speaks. */
        fun read(prefs: SharedPreferences): RefreshMatch =
            of(prefs.getString(KEY, null)) ?: if (prefs.getBoolean(KEY_VIDEO_MATCH_FRAME_RATE, false)) Always else Default
    }
}

/** How a film's frames fall on the screen's refreshes (S-612). */
@Immutable
sealed interface Cadence {
    /** Every frame stays on the screen as long as the others, [refreshes] refreshes each: nothing repeats. */
    data class Even(val refreshes: Int) : Cadence

    /**
     * Now and then a frame stays a refresh longer or shorter, or, [dropped], is not shown: once
     * every [seconds]. 23,976 fps on a 120 Hz panel: every 8,3 s.
     */
    data class Drift(val seconds: Double, val dropped: Boolean) : Cadence

    /** Frames stay unevenly, [perSecond] of them a second: 23,976 fps on 60 Hz, three refreshes and two. */
    data class Uneven(val perSecond: Double, val dropped: Boolean) : Cadence
}

/** Fewer uneven frames than this a second (one in five minutes) is an even cadence: what the screens' rates round to. */
private const val EVEN_PER_SECOND = 1.0 / 300

/**
 * The frames that do not stay as long as the others, a second: the refreshes by which [hz] misses
 * the nearest whole multiple of [fps] (at least the film's own rate).
 */
fun unevenPerSecond(fps: Double, hz: Double): Double {
    val refreshes = (hz / fps).roundToInt().coerceAtLeast(1)
    return abs(hz - refreshes * fps)
}

/** How a film of [fps] frames a second falls on a screen refreshing [hz] times a second; null when either is unknown. */
fun cadenceOf(fps: Double, hz: Double): Cadence? {
    if (!(fps > 0) || !(hz > 0)) return null
    val refreshes = (hz / fps).roundToInt().coerceAtLeast(1)
    val off = hz - refreshes * fps
    val uneven = abs(off)
    // the screen is slower than the film: frames go unseen
    val dropped = refreshes == 1 && off < 0
    return when {
        uneven < EVEN_PER_SECOND -> Cadence.Even(refreshes)
        uneven < 1 -> Cadence.Drift(1 / uneven, dropped)
        else -> Cadence.Uneven(uneven, dropped)
    }
}

/** Whether a film of [fps] frames a second falls evenly on a screen refreshing [hz] times a second. */
fun isEven(fps: Double, hz: Double): Boolean = cadenceOf(fps, hz) is Cadence.Even

/**
 * The rate among [rates] a film of [fps] frames a second falls on best: an even one if there is
 * any, the lowest of them (the film's own rate before its multiples); else the one with the fewest
 * uneven frames. Null when there is no rate or the film's is unknown.
 */
fun bestRate(fps: Double, rates: Collection<Double>): Double? {
    if (!(fps > 0)) return null
    return rates.filter { it > 0 }.minWithOrNull(
        compareBy<Double>({ unevenPerSecond(fps, it).takeIf { uneven -> uneven >= EVEN_PER_SECOND } ?: 0.0 }, { it }),
    )
}

/** Which screen the video is on: the device's own, a phone's or a tablet's, or one connected to it (HDMI, DeX). */
enum class ScreenKind { Phone, Tablet, External }

/** Android's own "Match content frame rate" (Android 12 and later; a TV shows it in its settings). */
enum class SystemMatch { Unknown, Never, Seamless, Always }

/**
 * What the screen does with the playing film (S-612): the film's frame rate (0 when unknown), the
 * screen's refresh rate now, which screen it is (its name when it is connected to the device),
 * the rates it has at its resolution and what Android's own setting allows.
 */
@Immutable
data class RefreshStatus(
    val fps: Double = 0.0,
    val hz: Double = 0.0,
    val screen: ScreenKind = ScreenKind.Tablet,
    val screenName: String? = null,
    val rates: List<Double> = emptyList(),
    val system: SystemMatch = SystemMatch.Unknown,
) {
    val cadence: Cadence? get() = cadenceOf(fps, hz)

    /** Whether one of the screen's rates takes the film evenly; true while either is unknown. */
    val hasEvenRate: Boolean get() = !(fps > 0) || rates.isEmpty() || rates.any { isEven(fps, it) }
}
