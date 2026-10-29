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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import app.nagmedar.ui.shell.WidthClass
import kotlin.math.roundToInt

/**
 * Nağmedar's motion language (S-203), on Android's own motion system (Material 3) as Desktop Kit
 * 0.2.0 has it, so the app moves like the system around it:
 *
 * - hover, press and selection colours ease over [COLOR] ms ([color]); by touch a pressed control
 *   shrinks a little and springs back ([fastSpatial]); with a pointer nothing moves;
 * - what arrives decelerates ([EmphasizedDecelerate], [StandardDecelerate]), what leaves
 *   accelerates ([EmphasizedAccelerate], [StandardAccelerate]), what moves and stays uses
 *   [Emphasized]; small fades take [enter] and [exit];
 * - top-level screens fade through on phones and tablets and cross-fade still on a desktop
 *   ([screenEnter]); a list's detail moves on the shared axis ([app.nagmedar.ui.shell.DetailMotion]);
 *   the steps of a flow too ([step]);
 * - sizes and places that follow a state (the navigation pill, the slider's thumb, a pressed
 *   control) move on springs ([spatial], [fastSpatial], [effects]);
 * - loops only say that work goes on: the skeleton's shimmer ([SHIMMER]) and the indeterminate
 *   progress sweep ([SWEEP]).
 *
 * Nothing takes longer than [LONG] but the loops. Compose scales every duration by the system's
 * animator scale; with "Remove animations" (scale 0) the [reduced] specs snap, the transitions
 * are none and [loop] is null, so the caller draws a still frame.
 *
 * Desktop Kit 1.0 adds two tiers ([MotionTier], [LocalMotionTier]): on a phone what moves in space
 * or follows a finger runs on interruptible springs that take over a fling's speed ([gesture]:
 * the screens' scale-in, a list's detail, the bars' settle, the sheets, the player); tablet and
 * DeX windows keep the timed curves above unchanged; fades and colours stay short tweens everywhere.
 */
@Immutable
class NagMotion private constructor(val reduced: Boolean) {

    /** Content arriving: a fade or a small move over [durationMillis] on the standard decelerate curve. */
    fun <T> enter(durationMillis: Int = MEDIUM): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = StandardDecelerate)

    /** Content leaving: quicker than [enter], so what comes is not kept waiting. */
    fun <T> exit(durationMillis: Int = SHORT): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = StandardAccelerate)

    /** Hover, press and selection colours. */
    fun <T> color(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(COLOR, easing = Standard)

    /** Something small that stays on screen and changes: a colour fill, a cross-fade of icons. */
    fun <T> standard(durationMillis: Int = MEDIUM): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = Standard)

    /** Something large that moves and comes to rest: a panel, the sidebar, an artwork. */
    fun <T> emphasized(durationMillis: Int = LONG): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = Emphasized)

    /** Something leaving for good: a closing pane, a row swiped away. */
    fun <T> emphasizedExit(durationMillis: Int = MEDIUM): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = EmphasizedAccelerate)

    /** Sizes and places that follow a state (indicator pills, panels): a hint of bounce, settles fast. */
    fun <T> spatial(): FiniteAnimationSpec<T> = if (reduced) snap() else spring(dampingRatio = 0.85f, stiffness = 700f)

    /** Small elements (a pressed control, the slider's thumb): quicker, hardly any bounce. */
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = if (reduced) snap() else spring(dampingRatio = 0.9f, stiffness = 1400f)

    /** Opacity and elevation that follow a state, without overshoot. */
    fun <T> effects(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)

    /** A looping animation, or null: then the caller shows a still frame instead. */
    fun loop(durationMillis: Int): InfiniteRepeatableSpec<Float>? =
        if (reduced) null else infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart)

    /**
     * A top-level screen arriving. Phones and tablets: Android's fade-through, the new screen fades
     * in over [FADE_IN] once the old one has faded ([FADE_OUT]), growing from [initialScale]
     * ([PHONE_SCALE], [TABLET_SCALE]). On a [desktop] (the sidebar layout): a still [CROSSFADE].
     */
    fun screenEnter(desktop: Boolean = false, initialScale: Float = TABLET_SCALE, tier: MotionTier = MotionTier.Timed): EnterTransition = when {
        reduced || tier == MotionTier.Reduced -> EnterTransition.None
        desktop -> fadeIn(tween(CROSSFADE, easing = Standard))
        // a phone (Desktop Kit 1.0): the scale-in is the gesture spring, the fade stays timed
        tier == MotionTier.Gesture -> fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = EmphasizedDecelerate)) +
            scaleIn(gesture(), initialScale = initialScale)
        else -> fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = EmphasizedDecelerate)) +
            scaleIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = EmphasizedDecelerate), initialScale = initialScale)
    }

    /** The screen [screenEnter] replaces: gone in [FADE_OUT], or cross-faded on a [desktop]. */
    fun screenExit(desktop: Boolean = false, tier: MotionTier = MotionTier.Timed): ExitTransition = when {
        reduced || tier == MotionTier.Reduced -> ExitTransition.None
        desktop -> fadeOut(tween(CROSSFADE, easing = Standard))
        else -> fadeOut(tween(FADE_OUT, easing = EmphasizedAccelerate))
    }

    /**
     * The next step of a flow, on the shared axis: it slides [slidePx] (see [STEP_SLIDE_DP]) from
     * the side it comes from, [forward] meaning from the end, and fades in once the step it
     * replaces has faded out going the other way.
     */
    fun step(forward: Boolean, slidePx: Int): ContentTransform {
        if (reduced) return ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
        val direction = if (forward) 1 else -1
        return ContentTransform(
            targetContentEnter = fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = EmphasizedDecelerate)) +
                slideInHorizontally(tween(LONG, easing = EmphasizedDecelerate)) { slidePx * direction },
            initialContentExit = fadeOut(tween(FADE_OUT, easing = EmphasizedAccelerate)) +
                slideOutHorizontally(tween(LONG, easing = EmphasizedAccelerate)) { -slidePx * direction },
            sizeTransform = null,
        )
    }

    /**
     * A sheet over the whole window coming up from the bottom edge (the phone's player, "Sıradaki" on a phone): it
     * slides up the whole way while it fades in, over [LONG].
     */
    fun sheetEnter(): EnterTransition = if (reduced) {
        EnterTransition.None
    } else {
        slideInVertically(tween(LONG, easing = EmphasizedDecelerate)) { it } + fadeIn(tween(LONG, easing = EmphasizedDecelerate))
    }

    /** The sheet of [sheetEnter] going: down the whole way, quicker, over [SHEET_EXIT]. */
    fun sheetExit(): ExitTransition = if (reduced) {
        ExitTransition.None
    } else {
        slideOutVertically(tween(SHEET_EXIT, easing = EmphasizedAccelerate)) { it }
    }

    /**
     * A page over the whole window opened from the one under it (the lyrics, the visualiser): it comes a tenth of the
     * width ([DETAIL_SHIFT]) from the end while it fades in, as a list's detail does
     * ([app.nagmedar.ui.shell.DetailMotion]); [rtl] mirrors the side.
     */
    fun detailEnter(rtl: Boolean = false): EnterTransition {
        if (reduced) return EnterTransition.None
        val direction = if (rtl) -1 else 1
        return slideInHorizontally(tween(LONG, easing = EmphasizedDecelerate)) { (it * DETAIL_SHIFT).roundToInt() * direction } +
            fadeIn(tween(MEDIUM, delayMillis = DETAIL_FADE_DELAY, easing = StandardDecelerate))
    }

    /** The page of [detailEnter] going back towards the end, gone in [COLOR]. */
    fun detailExit(rtl: Boolean = false): ExitTransition {
        if (reduced) return ExitTransition.None
        val direction = if (rtl) -1 else 1
        return slideOutHorizontally(tween(LONG - DETAIL_FADE_DELAY, easing = EmphasizedAccelerate)) { (it * DETAIL_SHIFT).roundToInt() * direction } +
            fadeOut(tween(COLOR, easing = StandardAccelerate))
    }

    /**
     * 1.0.1: a page opened from another (Settings from "Daha fazla", the equaliser from the player): it comes in
     * from the end edge over the page it leaves ([pageExit]), on the shared axis. On a phone it moves on the
     * [gesture] spring, wider windows take [LONG] on the emphasized curve; nothing moves with reduced motion.
     * [rtl] mirrors the side. Back plays it in reverse ([pagePopEnter], [pagePopExit]), with the predictive gesture.
     */
    fun pageEnter(tier: MotionTier, rtl: Boolean): EnterTransition {
        if (reduced || tier == MotionTier.Reduced) return EnterTransition.None
        val end = if (rtl) -1 else 1
        return slideInHorizontally(pageSlide(tier)) { it * end }
    }

    /** The page [pageEnter] covers: it moves a quarter of the way towards the start ([PAGE_SHIFT]) and stays drawn. */
    fun pageExit(tier: MotionTier, rtl: Boolean): ExitTransition {
        if (reduced || tier == MotionTier.Reduced) return ExitTransition.None
        val end = if (rtl) -1 else 1
        return slideOutHorizontally(pageSlide(tier)) { -(it * PAGE_SHIFT).roundToInt() * end }
    }

    /** Back: the page under comes back from a quarter of the way towards the start. */
    fun pagePopEnter(tier: MotionTier, rtl: Boolean): EnterTransition {
        if (reduced || tier == MotionTier.Reduced) return EnterTransition.None
        val end = if (rtl) -1 else 1
        return slideInHorizontally(pageSlide(tier)) { -(it * PAGE_SHIFT).roundToInt() * end }
    }

    /** Back: the page leaves over the end edge. */
    fun pagePopExit(tier: MotionTier, rtl: Boolean): ExitTransition {
        if (reduced || tier == MotionTier.Reduced) return ExitTransition.None
        val end = if (rtl) -1 else 1
        return slideOutHorizontally(pageSlide(tier)) { it * end }
    }

    private fun pageSlide(tier: MotionTier): FiniteAnimationSpec<IntOffset> =
        if (tier == MotionTier.Gesture) gesture() else tween(LONG, easing = Emphasized)

    /**
     * The top bar's title, back arrow and search button changing with the page: the old ones fade out in
     * [FADE_OUT], then the new ones fade in, so two titles never show at once; at once with reduced motion.
     */
    fun topBarChange(): ContentTransform = if (reduced) {
        ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    } else {
        ContentTransform(
            targetContentEnter = fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = EmphasizedDecelerate)),
            initialContentExit = fadeOut(tween(FADE_OUT, easing = EmphasizedAccelerate)),
            sizeTransform = null,
        )
    }

    /**
     * 1.0.1: a dialog's card arriving: opaque from its first frame, it grows from [DIALOG_SCALE] over [MEDIUM];
     * only the dim behind it fades. At once with reduced motion.
     */
    fun <T> dialogEnter(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(MEDIUM, easing = EmphasizedDecelerate)

    override fun equals(other: Any?): Boolean = other is NagMotion && other.reduced == reduced

    override fun hashCode(): Int = reduced.hashCode()

    override fun toString(): String = if (reduced) "NagMotion.Reduced" else "NagMotion.Full"

    companion object {
        /** A menu closing, a tooltip appearing, what leaves. */
        const val SHORT = 100

        /** Hover, press and selection colours; a menu opening; the DeX screen change ([CROSSFADE]). */
        const val COLOR = 150

        /** What fades in and out over the content: the player's controls, notices; the top bar's change. */
        const val MEDIUM = 200

        /** Panels, the sidebar, a list's detail; the longest a transition takes. */
        const val LONG = 300

        /** A sheet going down ([sheetExit]): a little longer than [MEDIUM], as it goes the whole height. */
        const val SHEET_EXIT = MEDIUM + 50

        /** How far a page over the window travels ([detailEnter]): a tenth of the width. */
        const val DETAIL_SHIFT = 0.1f

        /** How far the page under a page opened over it moves towards the start ([pageExit]): a quarter of the width. */
        const val PAGE_SHIFT = 0.25f

        /** Where a dialog's card grows from ([dialogEnter]). */
        const val DIALOG_SCALE = 0.94f

        /** A page over the window starts fading in this long after it starts moving. */
        const val DETAIL_FADE_DELAY = 50

        /** Fade-through: the old screen fading. */
        const val FADE_OUT = 90

        /** Fade-through: the new screen arriving, after [FADE_OUT]. */
        const val FADE_IN = 210

        /** A screen change on a desktop: a still cross-fade. */
        const val CROSSFADE = 150

        const val SHIMMER = 1400
        const val SWEEP = 1200
        const val STEP_SLIDE_DP = 24

        /** Where a new screen grows from in the fade-through: a phone's, a tablet's. */
        const val PHONE_SCALE = 0.96f
        const val TABLET_SCALE = 0.98f

        /** Things that move on screen and come to rest (panels, the sidebar). */
        val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        /** Arriving: a fast start, a long soft landing (screens, menus, a detail). */
        val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

        /** Leaving: it speeds up and is gone. */
        val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

        /** Small changes: colour, opacity. */
        val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        /** Fading in. */
        val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

        /** Fading out. */
        val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

        val Full = NagMotion(reduced = false)
        val Reduced = NagMotion(reduced = true)

        fun of(reduced: Boolean): NagMotion = if (reduced) Reduced else Full

        /** Accent and theme colour changes: an accent taken from the artwork cross-fades in this time (Desktop Kit 1.0). */
        const val COLOR_CHANGE = COLOR

        /**
         * The phone's spring for everything a finger moves or flings (Desktop Kit 1.0): bars, sheets,
         * the player, a screen's scale. No bounce, medium-low stiffness, so a new touch can catch it
         * anywhere and a fling hands over its speed.
         */
        fun <T> gesture(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

        /** The tier of a window: springs on a phone, the timed curves from tablet width up, nothing with [reduced] motion. */
        fun tier(widthClass: WidthClass, reduced: Boolean = false): MotionTier = when {
            reduced -> MotionTier.Reduced
            widthClass == WidthClass.Compact -> MotionTier.Gesture
            else -> MotionTier.Timed
        }

        /**
         * A spatial animation (position, size, scale) for [tier]: the [gesture] spring on a phone,
         * [timed] (the 0.2.0 curve) on wider windows, a jump to the end with reduced motion.
         */
        fun <T> spatial(tier: MotionTier, timed: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = when (tier) {
            MotionTier.Gesture -> gesture()
            MotionTier.Timed -> timed
            MotionTier.Reduced -> snap()
        }

        /** A fade or a colour change: [spec] in both tiers, a jump with reduced motion. */
        fun <T> fade(tier: MotionTier, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
            if (tier == MotionTier.Reduced) snap() else spec
    }
}

/** How the interface moves in a window (Desktop Kit 1.0); see [NagMotion.tier]. */
enum class MotionTier {
    /** Phone: interruptible, velocity-aware springs for spatial and finger-driven motion. */
    Gesture,

    /** Tablet and DeX: the timed Material curves (90 ms fade-out, 210 ms scale-in, 150 ms cross-fade, 300 ms panels). */
    Timed,

    /** "Remove animations" or an animator duration scale of 0: everything jumps to its end. */
    Reduced,
}

/** The motion tier of the window: the theme's by its width, the shell's by its width class. */
val LocalMotionTier = compositionLocalOf { MotionTier.Timed }

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
