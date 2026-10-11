package app.nagmedar.ui.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Nağmedar's motion language (S-203). The interface moves little and quickly:
 *
 * - hover, focus, press and selection change at once ([INSTANT]): the pointer is never kept waiting;
 * - small state changes and anything leaving the screen fade out in [SHORT] with [Exit];
 * - screens and steps fade in over [MEDIUM] with [Enter]; a step also slides by [STEP_SLIDE_DP],
 *   from the side it comes from;
 * - large surfaces (sheets, side panels) move over [LONG] with [Standard];
 * - loops only say that work goes on: the skeleton's shimmer ([SHIMMER]) and the indeterminate
 *   progress sweep ([SWEEP]).
 *
 * With the system's "Remove animations" (animator duration scale 0) every transition is instant
 * and loops stand still: [reduced] specs snap, [loop] is null and the caller draws a still frame.
 */
@Immutable
class NagMotion private constructor(val reduced: Boolean) {

    /** A spec for screen and panel content arriving. */
    fun <T> enter(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(MEDIUM, easing = Enter)

    /** A spec for content leaving: shorter than [enter], so the new content is not kept waiting. */
    fun <T> exit(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(SHORT, easing = Exit)

    /** Something that stays on screen and changes size or place. */
    fun <T> standard(durationMillis: Int = MEDIUM): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = Standard)

    /** A looping animation, or null: then the caller shows a still frame instead. */
    fun loop(durationMillis: Int): InfiniteRepeatableSpec<Float>? =
        if (reduced) null else infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart)

    /** Screen changes in the shell: a cross-fade. */
    fun screenEnter(): EnterTransition = if (reduced) EnterTransition.None else fadeIn(enter())

    fun screenExit(): ExitTransition = if (reduced) ExitTransition.None else fadeOut(exit())

    /**
     * The next step of a flow: it fades in and slides [slidePx] (see [STEP_SLIDE_DP]) from the side
     * it comes from, [forward] meaning from the end; the step it replaces fades out the other way.
     */
    fun step(forward: Boolean, slidePx: Int): ContentTransform {
        if (reduced) return ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
        val direction = if (forward) 1 else -1
        return ContentTransform(
            targetContentEnter = fadeIn(enter()) + slideInHorizontally(enter()) { slidePx * direction },
            initialContentExit = fadeOut(exit()) + slideOutHorizontally(exit()) { -slidePx * direction },
            sizeTransform = null,
        )
    }

    override fun equals(other: Any?): Boolean = other is NagMotion && other.reduced == reduced

    override fun hashCode(): Int = reduced.hashCode()

    override fun toString(): String = if (reduced) "NagMotion.Reduced" else "NagMotion.Full"

    companion object {
        const val INSTANT = 0
        const val SHORT = 120
        const val MEDIUM = 200
        const val LONG = 300
        const val SHIMMER = 1400
        const val SWEEP = 1200
        const val STEP_SLIDE_DP = 24

        /** Moves that start and end on screen. */
        val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        /** Arriving: fast start, gentle stop. */
        val Enter: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

        /** Leaving: gentle start, gone quickly. */
        val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

        val Full = NagMotion(reduced = false)
        val Reduced = NagMotion(reduced = true)

        fun of(reduced: Boolean): NagMotion = if (reduced) Reduced else Full
    }
}

/** True when the system's "Remove animations" setting is on (animator duration scale 0). */
fun animationsRemoved(resolver: ContentResolver): Boolean =
    runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f

/** [animationsRemoved], kept up to date while the setting changes (Quick Settings, DeX). */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var removed by remember(resolver) { mutableStateOf(animationsRemoved(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                removed = animationsRemoved(resolver)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        // the setting may have changed between the first read and the registration
        removed = animationsRemoved(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return removed
}
