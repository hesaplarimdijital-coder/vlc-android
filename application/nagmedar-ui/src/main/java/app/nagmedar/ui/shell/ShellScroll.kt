package app.nagmedar.ui.shell

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion

/**
 * How a phone's chrome follows the content's scrolling (Desktop Kit 0.2.0), as Android's own apps do:
 * - scrolling down slides the bottom navigation bar out of the window and the player strip above
 *   it down to the edge, so the list gets the height; scrolling up brings them back. When the
 *   finger lifts half way they settle fully shown or fully hidden ([settle]), on a phone on a
 *   spring that takes over the fling's speed (Desktop Kit 1.0).
 *
 * 1.0.1: the top bar no longer follows the scrolling; it keeps one solid colour.
 *
 * [offsetPx] is only read while drawing (a layer translation) and by the screens that leave their
 * padding to the shell, so a scrolled pixel neither measures nor recomposes a screen that lies
 * under the bars. The shell keeps one, turns it off on wider windows and while the keyboard is
 * up, and resets it whenever the screen changes.
 */
@Stable
class ShellScrollState {
    /** Pixels of the bottom bar pushed out of the window: 0 shown, [maxOffsetPx] hidden. */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /** The bottom bar's height: how far it can go. */
    var maxOffsetPx by mutableFloatStateOf(0f)

    /** False on tablet and DeX windows: nothing moves. */
    var enabled by mutableStateOf(true)

    /** False keeps the bottom bar in place (the keyboard is up). */
    var hideOnScroll by mutableStateOf(true)

    /** How a bar left half way settles: the gesture spring on a phone, nothing with reduced motion. */
    var tier: MotionTier = MotionTier.Gesture

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            onScrolled(consumed.y)
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // the bars move against the content; what the list could not use (it hit an end) carries on into them
            if (enabled && hideOnScroll) settle(-available.y)
            return Velocity.Zero
        }
    }

    /** The content moved by [consumedY] px (negative: towards its end). */
    fun onScrolled(consumedY: Float) {
        if (!enabled) return
        if (hideOnScroll && consumedY != 0f) offsetPx = (offsetPx - consumedY).coerceIn(0f, maxOffsetPx)
    }

    /**
     * Finishes a bar left half way: hidden past half its height, shown otherwise. [velocity] (px/s,
     * positive downwards) starts the phone's spring at the speed the content was moving.
     */
    suspend fun settle(velocity: Float = 0f) {
        val start = offsetPx
        val target = settleTarget(start, maxOffsetPx) ?: return
        when (tier) {
            MotionTier.Reduced -> offsetPx = target
            MotionTier.Gesture -> animate(start, target, initialVelocity = velocity, animationSpec = NagMotion.gesture()) { value, _ ->
                offsetPx = value.coerceIn(0f, maxOffsetPx)
            }
            MotionTier.Timed -> animate(start, target, animationSpec = timedSettle) { value, _ -> offsetPx = value }
        }
    }

    /** Brings the bottom chrome back at once (an expanding player starts from it). */
    fun showBars() {
        offsetPx = 0f
    }

    /** A new screen starts with the bars shown. */
    fun reset() {
        offsetPx = 0f
    }

    companion object {
        /** Where a bar left at [offset] of [max] settles; null when it is shown or hidden already. */
        fun settleTarget(offset: Float, max: Float): Float? = when {
            offset <= 0f || offset >= max -> null
            offset > max / 2f -> max
            else -> 0f
        }

        /** A bar settling from half way on the timed curves: a little quicker than the panels. */
        const val SETTLE_MS = 250

        private val timedSettle: AnimationSpec<Float> = tween(SETTLE_MS, easing = NagMotion.Emphasized)
    }
}
