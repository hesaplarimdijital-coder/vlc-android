package app.nagmedar.ui.shell

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.createChildTransition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.BackSource
import app.nagmedar.ui.components.fromFinger
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where the list and the detail are in one frame of [DetailMotion]: shifts as fractions of the width, towards the end. */
@Immutable
data class DetailFrame(val listShift: Float, val listAlpha: Float, val detailShift: Float, val detailAlpha: Float)

/**
 * Android's screen transition between a list and the detail opened from it on tablet and DeX
 * windows (Desktop Kit 0.2.0, Material's shared axis): opening, the detail comes a tenth of the
 * width from the end while it fades in and the list goes the other way fading out; closing is the
 * same backwards. The frames are a function of the progress (0 the list, 1 the detail) run linearly
 * over [NagMotion.LONG]; each part keeps its own curve and timing within it. A phone's window has
 * the depth of [DetailDepth] instead (1.0.3).
 *
 * 1.0.3 (Desktop Kit 2.4.0): the fades come in turn, as Material's shared axis has them: the screen going
 * away is gone in the first third ([NagMotion.SHORT]) and only then the other fades in, over the rest
 * ([NagMotion.MEDIUM]). They used to overlap (50 to 100 ms opening, 50 to 150 ms closing): two screens part
 * transparent at once, the text of one over the other's. The slides and the lengths are as they were.
 */
object DetailMotion {
    /** How far the parts travel: a tenth of the width. */
    const val SHIFT = 0.1f

    /** During the predictive Back gesture: the detail shrinks to 90 %, rounds 28 dp corners and leans 24 dp. */
    const val BACK_SCALE = 0.9f
    const val BACK_CORNER_DP = 28
    const val BACK_LEAN_DP = 24

    /**
     * 1.0.3: where the phone's spring counts the way as done (Desktop Kit 2.3.0's rule: once less than half a pixel
     * is left to move). The parts' curves flatten towards both ends: within a fiftieth of the way less than a
     * twentieth of a pixel of the slide is left on the widest phone and the fades are over, so the spring stops
     * there and an open detail's list is no longer drawn under it (a thousandth would add some 130 ms of frames
     * that move nothing; without a threshold Compose stopped it at a hundredth of the old, slower spring).
     */
    const val ARRIVED = 0.02f

    /** The frame at [progress]; [opening] tells which way it runs, as each way has its own curves. */
    fun frame(progress: Float, opening: Boolean): DetailFrame {
        val p = progress.coerceIn(0f, 1f)
        val ms = NagMotion.LONG.toFloat()
        fun part(elapsed: Float, delay: Float, duration: Float) = ((elapsed - delay) / duration).coerceIn(0f, 1f)
        return if (opening) {
            val t = p * ms
            DetailFrame(
                listShift = -NagMotion.Emphasized.transform(p) * SHIFT,
                listAlpha = 1f - NagMotion.StandardAccelerate.transform(part(t, 0f, NagMotion.SHORT.toFloat())),
                detailShift = (1f - NagMotion.EmphasizedDecelerate.transform(p)) * SHIFT,
                detailAlpha = NagMotion.StandardDecelerate.transform(part(t, NagMotion.SHORT.toFloat(), NagMotion.MEDIUM.toFloat())),
            )
        } else {
            val c = 1f - p
            val t = c * ms
            DetailFrame(
                listShift = -(1f - NagMotion.EmphasizedDecelerate.transform(c)) * SHIFT,
                listAlpha = NagMotion.StandardDecelerate.transform(part(t, NagMotion.SHORT.toFloat(), NagMotion.MEDIUM.toFloat())),
                detailShift = NagMotion.EmphasizedAccelerate.transform(part(t, 0f, ms - 50f)) * SHIFT,
                detailAlpha = 1f - NagMotion.StandardAccelerate.transform(part(t, 0f, NagMotion.SHORT.toFloat())),
            )
        }
    }
}

/**
 * The state of a list and its detail on one screen: how far the detail is open ([progress], run
 * by [show]), which way it last went, and the predictive Back gesture's progress ([back]) with the
 * edge it came from.
 */
@Stable
class DetailMotionState internal constructor(open: Boolean) {
    val progress = Animatable(if (open) 1f else 0f)
    val back = Animatable(0f)
    var opening by mutableStateOf(open)
        private set
    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)

    /** The detail covers the list and nothing moves: the list is neither drawn nor read out. */
    val covered: Boolean get() = progress.value >= 1f && back.value == 0f

    /**
     * Opens or closes the detail; the rest of the way takes its share of [NagMotion.LONG]. On a
     * phone ([MotionTier.Gesture], Desktop Kit 1.0) the way runs on the gesture spring instead, which
     * a new open or close catches where it is.
     */
    suspend fun show(open: Boolean, motion: NagMotion, tier: MotionTier = MotionTier.Timed) {
        opening = open
        val target = if (open) 1f else 0f
        if (motion.reduced || tier == MotionTier.Reduced) {
            progress.snapTo(target)
        } else if (tier == MotionTier.Gesture) {
            progress.animateTo(target, NagMotion.gesture(DetailMotion.ARRIVED))
        } else {
            val duration = (abs(target - progress.value) * NagMotion.LONG).roundToInt()
            progress.animateTo(target, tween(duration, easing = LinearEasing))
        }
        // the shade and the shrink of a Back gesture go once the detail has gone
        if (!open) back.snapTo(0f)
    }

    /** 1.0.3: the detail at rest, open or closed, at once (a phone's [DetailDepth] took over, or gave the window back). */
    suspend fun stand(open: Boolean) {
        opening = open
        progress.snapTo(if (open) 1f else 0f)
        back.snapTo(0f)
    }

    /** The list's layer in this frame; [direction] is -1 in right-to-left layouts. */
    internal fun GraphicsLayerScope.listLayer(widthPx: Int, direction: Float) {
        val frame = DetailMotion.frame(progress.value, opening)
        translationX = frame.listShift * widthPx * direction
        alpha = frame.listAlpha
    }

    /** The detail's layer in this frame, with the predictive Back gesture's shrink and lean. */
    internal fun GraphicsLayerScope.detailLayer(widthPx: Int, direction: Float) {
        val frame = DetailMotion.frame(progress.value, opening)
        val b = back.value
        val lean = if (b > 0f) DetailMotion.BACK_LEAN_DP.dp.toPx() * b * (if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f) else 0f
        translationX = frame.detailShift * widthPx * direction + lean
        alpha = frame.detailAlpha
        if (b > 0f) {
            val scale = 1f - (1f - DetailMotion.BACK_SCALE) * b
            scaleX = scale
            scaleY = scale
            shape = RoundedCornerShape((DetailMotion.BACK_CORNER_DP * b).dp)
            clip = true
        }
    }
}

@Composable
fun rememberDetailMotion(open: Boolean): DetailMotionState = remember { DetailMotionState(open) }

/**
 * The predictive Back gesture (Android 14 and later) on an open detail: while the finger moves the
 * detail shrinks, rounds its corners and leans towards the edge the swipe came from; letting go
 * calls [onBack] (the detail leaves from where the finger let it go), swiping back cancels and it
 * springs back. On older systems Back simply calls [onBack].
 *
 * 1.0.4: a finger's alone. The Back button (the taskbar's, a keyboard's key) sends a predictive Back too, with
 * a progress of the system's own ([BackSource]): the detail stays whole under it and its release calls [onBack].
 */
@Composable
fun DetailBackHandler(enabled: Boolean, state: DetailMotionState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val motion = NagTheme.motion
    val tier = LocalMotionTier.current
    val latestOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { events ->
        state.back.snapTo(0f)
        try {
            events.collect { event ->
                state.swipeEdge = event.swipeEdge
                if (!motion.reduced && event.fromFinger) state.back.snapTo(event.progress)
            }
            latestOnBack()
        } catch (e: CancellationException) {
            // 1.0.3 (Desktop Kit 2.3.0): on a phone back to full size on the gesture spring, arrived within a thousandth
            // (the spatial spring's bounce took the scale past 1 for a frame); wider windows keep it; at once with reduced motion
            scope.launch { state.back.animateTo(0f, NagMotion.spatial(tier, motion.spatial(), NagMotion.FRACTION)) }
            throw e
        }
    }
}

/** Behind the detail while a Back gesture shrinks it: the page darkens towards the pressed tone. */
@Composable
fun Modifier.detailShade(state: DetailMotionState): Modifier {
    val page = NagTheme.colors.background
    val behind = NagTheme.colors.pressed
    return drawBehind {
        val b = state.back.value
        if (b > 0f) drawRect(lerp(page, behind, b))
    }
}

// ------------------------------------------------------------------ the phone's depth (1.0.3, Desktop Kit 2.3.0 and 2.4.0)

/**
 * 1.0.3 (Desktop Kit 2.4.0, "Ayrıntı opak kayarak gelir"): on a phone the detail opens as Android's own list →
 * detail does. Opaque from its first frame, it slides in from the end edge by the whole width on the opening
 * spring ([NagMotion.open]), over the list; the list is not faded: on the same spring it sinks to [LIST_SCALE]
 * and darkens under [SCRIM] of the theme's scrim, so the scrim keeps to the detail's edge and the list's text
 * never shows through the detail. Closing is the same way back: the detail slides out over the end edge, the
 * list rises out of the scrim. (Desktop Kit 2.3.0 had the detail come 12 % of the way fading in while the list
 * faded under it: for a tenth of a second both were part transparent, one's text over the other's.)
 *
 * With a [detailSharedCard] the card carries the way and the rest fades through: the list is gone in
 * [FADE_OUT_MS], only then the detail appears, over [FADE_IN_MS]; back the other way round.
 *
 * The predictive Back gesture seeks the closing up to [GESTURE_END] with the finger ([gestureFrame]): the list
 * is really there behind the detail, dark under the scrim, and rises from [GESTURE_LIST_SCALE]; the detail
 * shrinks towards [DetailMotion.BACK_SCALE], rounds its corners and leans away from the edge the swipe came
 * from. Let go, the rest of the way runs from where the finger left it in [COMMIT_MS]: the detail, opaque to
 * the end, slides on until it is out of the window while the scrim lightens. A swipe given up runs everything
 * back the way it came.
 *
 * In no frame are both screens part transparent.
 */
object DetailDepth {
    /** What the list sinks to under an open detail. */
    const val LIST_SCALE = 0.95f

    /** How much of the theme's scrim darkens the list all the way behind a detail. */
    const val SCRIM = 0.7f

    /** Where the list rises from behind the detail while a Back gesture closes it. */
    const val GESTURE_LIST_SCALE = 0.94f

    /** How far the finger seeks the closing; the rest is the detail's way out once let go. */
    const val GESTURE_END = 0.85f

    /** The way out after the finger lets go, from wherever it was. */
    const val COMMIT_MS = 220

    /**
     * Fade-through with a [detailSharedCard]: the side going away is gone after [FADE_OUT_MS], only then the one
     * coming in appears, over [FADE_IN_MS]. The card's own content fades on the same timing.
     */
    const val FADE_OUT_MS = NagMotion.SHORT - 10
    const val FADE_IN_MS = NagMotion.MEDIUM + 10

    /** The Back gesture's frame at [fraction] of the closing (0 the detail open, 1 the list). */
    fun gestureFrame(fraction: Float): DepthGestureFrame {
        val f = fraction.coerceIn(0f, 1f)
        val lean = (f / GESTURE_END).coerceIn(0f, 1f)
        // after the finger: nothing while it seeks, then the way out, evenly (the commit's own curve shapes it in time;
        // a slide this long on an accelerating curve jumped at its end)
        val out = ((f - GESTURE_END) / (1f - GESTURE_END)).coerceIn(0f, 1f)
        val rise = NagMotion.Standard.transform(f)
        return DepthGestureFrame(
            detailScale = 1f - (1f - DetailMotion.BACK_SCALE) * lean,
            lean = lean,
            out = out,
            listScale = GESTURE_LIST_SCALE + (1f - GESTURE_LIST_SCALE) * rise,
            listScrim = 1f - out,
        )
    }
}

/** One frame of [DetailDepth.gestureFrame]. The detail is opaque in every one of them, and the list is never faded. */
@Immutable
data class DepthGestureFrame(
    val detailScale: Float,
    /** How far the detail leans and rounds its corners: 0 to 1 of [DetailMotion.BACK_LEAN_DP] and [DetailMotion.BACK_CORNER_DP]. */
    val lean: Float,
    /** How far the detail is on its way out of the window: 0 to 1 of its width. */
    val out: Float,
    val listScale: Float,
    /** How dark the list is behind the detail: 1 (all of [DetailDepth.SCRIM]) while the finger moves, lightening on the way out. */
    val listScrim: Float,
)

/** The transition's own values of the phone's depth: what a tap's opening and closing play. */
internal class DepthValues(
    /** 1: the whole width towards the end, out of the window; 0: in place. The list's depth is the rest of it. */
    val detailShift: State<Float>,
    /** The detail's fade when a [detailSharedCard] carries the way: after the list has gone. */
    val cardAlpha: State<Float>,
    /** The list's fade when a [detailSharedCard] carries the way: gone before the detail appears. */
    val listAlpha: State<Float>,
)

/**
 * The phone's depth between a list and its detail: a [SeekableTransitionState] (false the list, true the
 * detail) that a tap animates and the predictive Back gesture seeks, and what the gesture and the turns of
 * mind leave on it.
 */
@Stable
internal class DepthState(open: Boolean) {
    val seekable = SeekableTransitionState(open)

    /** The transition of [seekable], set where it is composed. */
    lateinit var transition: Transition<Boolean>

    /** Its values, set where it is composed. */
    var values: DepthValues? = null

    /** A closing driven by the Back gesture: the detail shrinks with the finger, the list rises behind it. */
    var gestureClose by mutableStateOf(false)

    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)

    /** The run back after a gesture given up or a change of mind; a change of the detail takes over from it. */
    var undoing by mutableStateOf<Job?>(null)

    /**
     * A run back is on its way. Observable, unlike [undoing]'s job: once it is over the depth leaves the
     * shared elements' layout (a job's end alone recomposes nothing, and the layout stayed with its sides'
     * transitions gone stale: the detail's cover drawn at the list's place).
     */
    var runningBack by mutableStateOf(false)

    /**
     * At the end of a run back the side that was on its way lets its shared elements go before the transition
     * stands still: in between, their partners would wait for an overlay that no longer draws them.
     */
    var dropped by mutableStateOf(false)

    /** The open detail was opened with a [detailSharedCard]: the card carried the way, the detail did not slide. */
    var withCard by mutableStateOf(false)

    /** The cards composed in the list. */
    val cards = DetailCards()

    /** The detail covers the list and nothing moves: the list is neither measured, drawn nor read out. */
    val covered: Boolean get() = seekable.currentState && seekable.targetState && !gestureClose && !dropped

    /** Something moves, or is about to: [open] is where the app wants the detail. */
    fun moving(open: Boolean): Boolean =
        open != seekable.currentState || seekable.targetState != seekable.currentState || transition.isRunning ||
            gestureClose || dropped || runningBack

    /** [side] was on its way when the transition was run back, and lets its shared elements go. */
    fun held(side: DetailSide): Boolean = dropped && (side == DetailSide.Detail) != seekable.currentState

    /** At rest at once: reduced motion, or a wider window's motion took over. */
    suspend fun stand(open: Boolean) {
        undoing?.cancel()
        undoing = null
        gestureClose = false
        dropped = false
        seekable.snapTo(open)
    }

    /** Where the app wants the detail ([open]): the way there, a Back gesture's way out or a run back. */
    suspend fun drive(open: Boolean, animated: Boolean) {
        undoing?.cancel()
        undoing = null
        when {
            !animated -> {
                gestureClose = false
                dropped = false
                seekable.snapTo(open)
            }
            gestureClose && !open -> {
                // let go early: the finger's part of the way is played first, quickly, then the way out
                val rest = (DetailDepth.COMMIT_MS * (DetailDepth.GESTURE_END - seekable.fraction) / DetailDepth.GESTURE_END).roundToInt()
                seekable.seekOn(DetailDepth.GESTURE_END, rest)
                seekable.animateTo(false, tween(DetailDepth.COMMIT_MS, easing = NagMotion.Standard))
                gestureClose = false
            }
            // a change of mind on the way (Back right after the tap, the tap again right after Back): the way is run
            // back from where it is, not retargeted (a retargeted seekable transition dropped the shared elements' match)
            seekable.targetState != seekable.currentState && seekable.currentState == open -> undo(NagMotion.gesture(NagMotion.FRACTION))
            else -> {
                gestureClose = false
                if (open && !seekable.currentState) withCard = cards.registered > 0
                seekable.animateTo(open)
            }
        }
    }

    /** The transition run back to where it came from on [spec]; then the side on its way lets go, and it stands still. */
    suspend fun undo(spec: FiniteAnimationSpec<Float>) {
        runningBack = true
        var done = false
        try {
            seekable.runBack(spec)
            dropped = true
            withFrameNanos { }
            withFrameNanos { }
            seekable.snapTo(seekable.currentState)
            // the content that was on its way leaves with this composition; only then may the next transition share
            withFrameNanos { }
            done = true
        } finally {
            // together, in one composition: the layout is left as the dropped side's elements would come back; a
            // gesture's look stays to the end (cancelled, it is left to what takes over)
            if (done) gestureClose = false
            dropped = false
            runningBack = false
        }
    }

    /** The list's layer in this frame: sunk under the detail, never faded but with a card ([DepthValues.listAlpha]). */
    fun GraphicsLayerScope.listLayer() {
        if (gestureClose) {
            val frame = DetailDepth.gestureFrame(seekable.fraction)
            scaleX = frame.listScale
            scaleY = frame.listScale
        } else {
            val v = values ?: return
            val scale = 1f - (1f - DetailDepth.LIST_SCALE) * (1f - v.detailShift.value)
            scaleX = scale
            scaleY = scale
            if (withCard) alpha = v.listAlpha.value
        }
    }

    /**
     * How dark the list is under the detail in this frame, 0 to 1 of [DetailDepth.SCRIM]: with the detail's way
     * (the same spring: the scrim keeps to its edge), full under a finger's Back and lightening on its way out;
     * with a card it goes with the fading list.
     */
    fun listScrim(): Float {
        if (gestureClose) return DetailDepth.gestureFrame(seekable.fraction).listScrim
        val v = values ?: return 0f
        val depth = (1f - v.detailShift.value).coerceIn(0f, 1f)
        return if (withCard) depth * v.listAlpha.value else depth
    }

    /**
     * The detail's layer in this frame; [direction] is -1 in right-to-left layouts (the gesture's lean is the
     * finger's). Opaque but with a card, where it fades in after the list has gone ([DepthValues.cardAlpha]).
     */
    fun GraphicsLayerScope.detailLayer(widthPx: Int, direction: Float) {
        if (gestureClose) {
            val frame = DetailDepth.gestureFrame(seekable.fraction)
            val away = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
            scaleX = frame.detailScale
            scaleY = frame.detailScale
            // all the way out of the window, from where it leans
            translationX = away * (DetailMotion.BACK_LEAN_DP.dp.toPx() * frame.lean + widthPx * frame.out)
            if (frame.lean > 0f) {
                shape = RoundedCornerShape((DetailMotion.BACK_CORNER_DP * frame.lean).dp)
                clip = true
            }
        } else {
            val v = values ?: return
            if (withCard) {
                alpha = v.cardAlpha.value
            } else {
                translationX = v.detailShift.value * widthPx * direction
            }
        }
    }
}

/** The phone's depth of a list and its detail; [open] is where it starts. */
@Composable
internal fun rememberDepth(open: Boolean): DepthState {
    val depth = remember { DepthState(open) }
    val transition = rememberTransition(depth.seekable, label = "detailDepth")
    // the detail's way, and with it the list's sinking and its scrim
    val detailShift = transition.animateFloat(transitionSpec = { NagMotion.open(NagMotion.FRACTION) }, label = "detailShift") { if (it) 0f else 1f }
    // with a card the two fade through, the one going away gone before the other appears
    val gone = tween<Float>(DetailDepth.FADE_OUT_MS, easing = NagMotion.StandardAccelerate)
    val appears = tween<Float>(DetailDepth.FADE_IN_MS, delayMillis = DetailDepth.FADE_OUT_MS, easing = NagMotion.StandardDecelerate)
    val cardAlpha = transition.animateFloat(transitionSpec = { if (targetState) appears else gone }, label = "cardAlpha") { if (it) 1f else 0f }
    val listAlpha = transition.animateFloat(transitionSpec = { if (targetState) gone else appears }, label = "listAlpha") { if (it) 0f else 1f }
    depth.transition = transition
    if (depth.values == null) depth.values = DepthValues(detailShift, cardAlpha, listAlpha)
    return depth
}

/**
 * The phone's predictive Back on an open detail: the finger seeks the closing of [depth]; let go, [onBack] is
 * called and the closing finishes from there (an app that keeps its detail, [isOpen] still, gets it run back);
 * given up, everything runs back. A gesture while the opening is still on its way leaves it be: letting go runs
 * the opening back. Back without the gesture (the three buttons) plays only the way out.
 *
 * 1.0.4: and so it does since Android 16, whose Back button sends a progress of its own while it is down
 * ([BackSource]). 1.0.3 took it for a finger's: under the button the detail shrank into a rounded, leaning card
 * (an episode's page, a show's, an album's, a playlist's), then left; the owner's "eskisi gibi". Only a swipe's
 * events ([fromFinger]) seek the closing now; the button's release plays the way out the page's arrow plays.
 */
@Composable
internal fun DepthBackHandler(enabled: Boolean, depth: DepthState, animated: Boolean, isOpen: () -> Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val latestOnBack by rememberUpdatedState(onBack)
    val latestOpen by rememberUpdatedState(isOpen)
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collectLatest { event ->
                depth.swipeEdge = event.swipeEdge
                if (animated && depth.seekable.currentState && event.fromFinger) {
                    depth.gestureClose = true
                    depth.seekable.seekTo(event.progress * DetailDepth.GESTURE_END, false)
                }
            }
            latestOnBack()
            if (animated) {
                depth.undoing = scope.launch {
                    // the app's answer reaches the composition first
                    withFrameNanos { }
                    withFrameNanos { }
                    if (latestOpen() && depth.gestureClose) depth.undo(NagMotion.gesture(NagMotion.FRACTION))
                }
            }
        } catch (e: CancellationException) {
            if (animated && depth.gestureClose) depth.undoing = scope.launch { depth.undo(NagMotion.gesture(NagMotion.FRACTION)) }
            throw e
        }
    }
}

/**
 * A transition turned around on its way: its fraction runs back to 0 on [spec], the target held. Retargeted at
 * its own current state instead, a seekable transition drops the shared elements' match for a frame.
 */
private suspend fun <S> SeekableTransitionState<S>.runBack(spec: FiniteAnimationSpec<Float>) {
    val held = targetState
    if (held == currentState || fraction <= 0f) return
    val way = TargetBasedAnimation(spec, Float.VectorConverter, fraction, 0f)
    val start = withFrameNanos { it }
    while (true) {
        val played = withFrameNanos { it } - start
        seekTo(way.getValueFromNanos(played).coerceIn(0f, 1f), held)
        if (way.isFinishedFromNanos(played)) break
    }
}

/** The fraction run on to [to] over [millis], the target held: a Back gesture let go of early finishes the finger's part. */
private suspend fun <S> SeekableTransitionState<S>.seekOn(to: Float, millis: Int) {
    val held = targetState
    if (held == currentState || fraction >= to || millis <= 0) return
    val way = TargetBasedAnimation(tween<Float>(millis, easing = NagMotion.StandardAccelerate), Float.VectorConverter, fraction, to)
    val start = withFrameNanos { it }
    while (true) {
        val played = withFrameNanos { it } - start
        seekTo(way.getValueFromNanos(played).coerceIn(0f, 1f), held)
        if (way.isFinishedFromNanos(played)) break
    }
}

/** The layout id of [DepthScrim] in a list and detail's layout. */
internal const val DEPTH_SCRIM = "depthScrim"

/**
 * The darkening of the list under a phone's detail ([DepthState.listScrim], Desktop Kit 2.4.0: the list is not
 * faded), laid over all of the list's place, the margins its sinking opens too. A layer whose opacity alone
 * changes: nothing recomposes, and neither the scrim nor what is around it is drawn again as it moves.
 */
@Composable
internal fun DepthScrim(depth: DepthState, modifier: Modifier = Modifier) {
    val scrim = NagTheme.colors.scrim
    Box(
        modifier
            .graphicsLayer {
                alpha = (DetailDepth.SCRIM * depth.listScrim()).coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .background(scrim),
    )
}

/**
 * Places [listPlaceable] and [detailPlaceable] as the phone's [depth] has them, [scrimPlaceable] ([DepthScrim])
 * between the two; a covered list is left out by the caller, and its scrim with it.
 */
internal fun Placeable.PlacementScope.placeDepth(
    depth: DepthState,
    listPlaceable: Placeable?,
    scrimPlaceable: Placeable?,
    detailPlaceable: Placeable?,
    widthPx: Int,
    direction: Float,
) {
    if (listPlaceable != null) {
        if (detailPlaceable == null) {
            listPlaceable.placeRelative(0, 0)
        } else {
            listPlaceable.placeRelativeWithLayer(0, 0) { with(depth) { listLayer() } }
            scrimPlaceable?.placeRelative(0, 0)
        }
    }
    detailPlaceable?.placeRelativeWithLayer(0, 0) { with(depth) { detailLayer(widthPx, direction) } }
}

// ------------------------------------------------------------------ shared elements (1.0.3, Desktop Kit 2.0–2.3)

/** The two sides of a list and its detail. */
internal enum class DetailSide {
    List,
    Detail;

    /** This side's visibility when the detail is [open]. */
    fun state(open: Boolean): EnterExitState = when (this) {
        List -> if (open) EnterExitState.PostExit else EnterExitState.Visible
        Detail -> if (open) EnterExitState.Visible else EnterExitState.PostExit
    }
}

/** What a shared element needs: the layout it flies in and the side it is on. */
internal class DetailSharedScopes(
    val transition: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
    val side: DetailSide,
)

/** Both sides' scopes while the phone's depth moves. */
internal class DepthShared(val list: DetailSharedScopes, val detail: DetailSharedScopes) {
    fun of(side: DetailSide): DetailSharedScopes = if (side == DetailSide.List) list else detail
}

/** A side's visibility, a child of the depth's transition. */
private class SideVisibility(override val transition: Transition<EnterExitState>) : AnimatedVisibilityScope

/** The cards composed in a list right now (their count, also while nothing moves). */
internal class DetailCards {
    var registered = 0
}

/**
 * The scopes of the side composed under it: null while nothing moves (no lookahead pass), outside a phone's
 * [DetailTransition] and with reduced motion, so a shared element is then a plain one. Dynamic: only the marked
 * elements recompose when a transition starts and ends.
 */
internal val LocalDetailSharedScopes = compositionLocalOf<DetailSharedScopes?> { null }

/** The list side's cards of a phone's [DetailTransition], also while nothing moves. */
internal val LocalDetailCards = compositionLocalOf<DetailCards?> { null }

/** Bounds of a shared element on the opening spring, arrived within half a pixel. */
private val DepthBounds = BoundsTransform { _, _ -> NagMotion.open(Rect.VisibilityThreshold) }

/**
 * The list and the detail of [depth] in [modifier]'s place: in a plain box while nothing moves, moved (state
 * and all: scroll places, text, effects) into a [SharedTransitionLayout] while something does when [shared], so a
 * list scrolled at rest is measured once a frame (Desktop Kit 2.3.0, "Lookahead"). [content] gets the sides'
 * scopes then. A Back gesture moves the detail as a whole, without the layout.
 */
@Composable
internal fun DepthHost(depth: DepthState, open: Boolean, shared: Boolean, modifier: Modifier, content: @Composable (DepthShared?) -> Unit) {
    val latestContent by rememberUpdatedState(content)
    val body = remember { movableContentOf { scopes: DepthShared? -> latestContent(scopes) } }
    val latestOpen by rememberUpdatedState(open)
    // a finger closing the detail moves it as a whole: its shared elements fly only on a tap's way (in the kit 2.3.0's
    // strips they waited at the list's place through the gesture and its run back, then jumped)
    val moving by remember(depth) { derivedStateOf { depth.moving(latestOpen) && !depth.gestureClose } }
    if (shared && moving) {
        SharedTransitionLayout(modifier) {
            val list = sideScopes(this, depth.transition, DetailSide.List)
            val detail = sideScopes(this, depth.transition, DetailSide.Detail)
            body(remember(list, detail) { DepthShared(list, detail) })
        }
    } else {
        Box(modifier) { body(null) }
    }
}

@OptIn(ExperimentalTransitionApi::class)
@Composable
private fun sideScopes(shared: SharedTransitionScope, transition: Transition<Boolean>, side: DetailSide): DetailSharedScopes {
    val child = transition.createChildTransition(label = side.name) { open -> side.state(open) }
    return remember(shared, child) { DetailSharedScopes(shared, SideVisibility(child), side) }
}

/**
 * [content] of [side] with its shared-element scopes from [scopes] when [shared] (none for the side a run back
 * lets go of), the list side's cards too; without [shared] what is around it passes through.
 */
@Composable
internal fun DepthSide(scopes: DepthShared?, side: DetailSide, depth: DepthState, shared: Boolean, content: @Composable () -> Unit) {
    val own = if (shared) scopes?.of(side)?.takeUnless { depth.held(side) } else LocalDetailSharedScopes.current
    val cards = if (shared) depth.cards.takeIf { side == DetailSide.List } else LocalDetailCards.current
    CompositionLocalProvider(LocalDetailSharedScopes provides own, LocalDetailCards provides cards, content = content)
}

/**
 * Inside a phone's [DetailTransition] (or [ListDetailPanes]) with shared elements, this element and the element
 * with the same [key] on the other side (the list's row and the detail) are one: opening the detail moves it
 * from the row to its place in the detail, closing it (its back arrow, the Back button) moves it back; the Back
 * gesture shrinks it with the detail. For things that look the same at both sizes: a cover. Put it first in the element's modifiers, before
 * its size, so all of it flies; [shape] rounds it in place and in flight (a clip inside it does too). Elsewhere,
 * and with reduced motion, nothing flies.
 */
@Composable
fun Modifier.detailSharedElement(key: Any, shape: Shape? = null): Modifier {
    val clip = if (shape == null) Modifier else Modifier.clip(shape)
    val scopes = LocalDetailSharedScopes.current ?: return this.then(clip)
    return with(scopes.transition) {
        this@detailSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scopes.visibility,
            boundsTransform = DepthBounds,
            // above a card flying with it
            zIndexInOverlay = 1f,
        )
    }.then(clip)
}

/**
 * Like [detailSharedElement] for things that differ in size between the two sides, such as a title set larger
 * in the detail: the bounds move and the content scales with them instead of reflowing, cross-fading from one
 * side's look to the other's.
 */
@Composable
fun Modifier.detailSharedBounds(key: Any): Modifier {
    val scopes = LocalDetailSharedScopes.current ?: return this
    return with(scopes.transition) {
        this@detailSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scopes.visibility,
            boundsTransform = DepthBounds,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.CenterStart),
            zIndexInOverlay = 1f,
        )
    }
}

/**
 * Inside a phone's [DetailTransition] (or [ListDetailPanes]) with shared elements, the card of a list item and
 * the header of its detail (the block holding the big cover and the title) marked with the same [key] open into
 * each other (Desktop Kit 2.3.0): the card grows into the header, its corners straightening from [shape] and its
 * surface turning into the page; on the way back the header shrinks into the card's place. The detail then does
 * not slide: it fades in once the list has gone. A [detailSharedElement] cover inside both still flies on its own,
 * above the card. Put it first in the modifiers of both. Elsewhere, and with reduced motion, nothing changes.
 *
 * 1.0.3 (Desktop Kit 2.4.0): the card's content fades out before the header's fades in
 * ([DetailDepth.FADE_OUT_MS], then [DetailDepth.FADE_IN_MS]), and the card stays opaque all the way: both sides
 * are drawn in full while they fly, the header over the card, the card's surface under everything. They used to
 * cross-fade as wholes, the card part transparent for a moment over the list.
 */
@Composable
fun Modifier.detailSharedCard(key: Any, shape: CornerBasedShape = RoundedCornerShape(NagTheme.dimens.radiusCard)): Modifier {
    val cards = LocalDetailCards.current
    if (cards != null) {
        DisposableEffect(cards) {
            cards.registered++
            onDispose { cards.registered-- }
        }
    }
    val scopes = LocalDetailSharedScopes.current ?: return this
    val colors = NagTheme.colors
    val list = scopes.side == DetailSide.List
    val density = LocalDensity.current
    // the corners: the card's at rest in the list, none at rest in the detail, moving with the transition
    val rested = with(density) { shape.topStart.toPx(Size(100f, 100f), this) }
    val corner by scopes.visibility.transition.animateDp(
        transitionSpec = { tween(NagMotion.LONG, easing = NagMotion.Emphasized) },
        label = "cardCorners",
    ) { state ->
        val atRest = state == EnterExitState.Visible
        with(density) { if (atRest == list) rested.toDp() else 0.dp }
    }
    val cornerPx: () -> Float = { with(density) { corner.toPx() } }
    val surface = if (list) colors.surface else colors.background
    val clip = remember(scopes) { CornerClip(cornerPx) }
    // the content fades through: the side going away is gone within FADE_OUT_MS, only then the other appears
    val shown = scopes.visibility.transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                tween(DetailDepth.FADE_IN_MS, delayMillis = DetailDepth.FADE_OUT_MS, easing = NagMotion.StandardDecelerate)
            } else {
                tween(DetailDepth.FADE_OUT_MS, easing = NagMotion.StandardAccelerate)
            }
        },
        label = "cardContent",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    return with(scopes.transition) {
        val state = rememberSharedContentState(key)
        val flying = { isTransitionActive && state.isMatchFound }
        // a surface only while this card flies (the others in the list stay as they are)
        val surfaceWhileFlying = Modifier.drawBehind {
            if (flying()) drawRoundRect(surface, cornerRadius = CornerRadius(cornerPx()))
        }
        val fading = Modifier.graphicsLayer { alpha = if (flying()) shown.value else 1f }
        this@detailSharedCard
            .sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = scopes.visibility,
                // both sides are drawn in full while they fly, the header above the card: the card's surface stays
                // opaque under everything, only the contents fade (below)
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                boundsTransform = DepthBounds,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
                zIndexInOverlay = if (list) 0f else 0.5f,
                clipInOverlayDuringTransition = clip,
            )
            // the card: an opaque surface under its fading content; the header: its page-coloured surface fading in
            // and out with its content, over the card
            .then(if (list) surfaceWhileFlying.then(fading) else fading.then(surfaceWhileFlying))
    }
}

/** The overlay clip of a flying card: a rounded rectangle whose radius [cornerPx] gives per draw. */
private class CornerClip(private val cornerPx: () -> Float) : SharedTransitionScope.OverlayClip {
    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path? {
        path.reset()
        path.addRoundRect(RoundRect(bounds, CornerRadius(cornerPx())))
        return path
    }
}

// ------------------------------------------------------------------ the transition

private const val LIST = "list"
private const val DETAIL = "detail"

/** The detail last given, kept while it closes; plain, so keeping it never redraws anything. */
private class DetailHold<T : Any> {
    var last: T? = null
}

/**
 * A list and the detail opened from it, on one place of the screen (Desktop Kit 0.2.0's
 * DetailTransition): [detail] non-null opens it, null closes it, and Back (the predictive gesture)
 * calls [onBack] while it is open, unless [handleBack] is false. Unlike the kit's, the list stays
 * composed under the detail, so it keeps its scroll position and selection; once covered it is
 * neither measured, drawn nor read out. [detailKey] tells details apart; the detail's content stays
 * while it closes and shows the latest object given under its key.
 *
 * 1.0.3 (Desktop Kit 2.3.0 and 2.4.0): on a phone the detail opens in depth ([DetailDepth]): opaque, it
 * slides in over the list, which sinks and darkens under a scrim; Back is the gesture seeking the closing
 * with the list really behind, and a change of mind runs the way back; with [sharedElements] the elements
 * marked with [detailSharedElement], [detailSharedBounds] and [detailSharedCard] fly between the two sides
 * (a lookahead pass only while something moves). Tablet and DeX windows keep [DetailMotion], its fades in
 * turn; with reduced motion the detail simply appears. No frame has both screens part transparent.
 */
@Composable
fun <T : Any> DetailTransition(
    detail: T?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    handleBack: Boolean = true,
    detailKey: (T) -> Any = { it },
    sharedElements: Boolean = false,
    list: @Composable () -> Unit,
    detailContent: @Composable (T) -> Unit,
) {
    val motion = NagTheme.motion
    val tier = LocalMotionTier.current
    val phone = tier == MotionTier.Gesture
    val animated = !motion.reduced && tier != MotionTier.Reduced
    val open = detail != null
    val state = rememberDetailMotion(open)
    val depth = rememberDepth(open)
    val hold = remember { DetailHold<T>() }
    if (detail != null) hold.last = detail
    // the wider windows' detail still drawn while it closes
    var lingering by remember { mutableStateOf(open) }
    val latestDetail by rememberUpdatedState(detail)
    // registered before the detail's own handlers: those (a selection inside it) go first
    DetailBackHandler(enabled = handleBack && open && !phone, state = state, onBack = onBack)
    DepthBackHandler(enabled = handleBack && open && phone, depth = depth, animated = animated, isOpen = { latestDetail != null }, onBack = onBack)
    LaunchedEffect(open, phone, animated) {
        if (open) lingering = true
        if (phone) {
            state.stand(open)
            depth.drive(open, animated)
        } else {
            // the phone's transition stands where this one goes, without holding it up (it waits for a composition)
            launch { depth.stand(open) }
            state.show(open, motion, tier)
        }
        if (!open) {
            lingering = false
            hold.last = null
        }
    }
    val slot = open || (if (phone) depth.seekable.currentState || depth.seekable.targetState else lingering)
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val covered by remember(state, depth, phone) { derivedStateOf { if (phone) depth.covered else state.covered } }
    val shared = sharedElements && phone && animated
    // on a phone the detail is opaque, from its first frame to its last: the list is behind it, dark under a scrim
    val page = if (phone) Modifier.background(NagTheme.colors.background) else Modifier
    DepthHost(
        depth,
        open,
        shared,
        modifier.fillMaxSize().clipToBounds().then(if (phone) Modifier else Modifier.detailShade(state)),
    ) { scopes ->
        val shown = detail ?: hold.last
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    Modifier.layoutId(LIST).then(if (slot && covered) Modifier.clearAndSetSemantics {} else Modifier),
                    propagateMinConstraints = true,
                ) { DepthSide(scopes, DetailSide.List, depth, shared, list) }
                if (slot && shown != null) {
                    if (phone && animated) DepthScrim(depth, Modifier.layoutId(DEPTH_SCRIM))
                    Box(Modifier.layoutId(DETAIL).then(page), propagateMinConstraints = true) {
                        DepthSide(scopes, DetailSide.Detail, depth, shared) { detailContent(shown) }
                    }
                }
            },
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val fixed = Constraints.fixed(w, h)
            val paneM = measurables.firstOrNull { it.layoutId == DETAIL }
            // a covered list stays composed but is not measured
            val listPlaceable = if (paneM != null && covered) null else measurables.first { it.layoutId == LIST }.measure(fixed)
            val scrimPlaceable = if (listPlaceable == null) null else measurables.firstOrNull { it.layoutId == DEPTH_SCRIM }?.measure(fixed)
            val pane = paneM?.measure(fixed)
            layout(w, h) {
                if (phone) {
                    placeDepth(depth, listPlaceable, scrimPlaceable, pane, w, direction)
                } else {
                    placeListAndDetail(state, listPlaceable, pane, w, direction)
                }
            }
        }
    }
}

/** Places [listPlaceable] and [detailPlaceable] as [state] has them; a covered list is left out. */
internal fun Placeable.PlacementScope.placeListAndDetail(
    state: DetailMotionState,
    listPlaceable: Placeable?,
    detailPlaceable: Placeable?,
    widthPx: Int,
    direction: Float,
) {
    if (listPlaceable != null && (detailPlaceable == null || !state.covered)) {
        if (detailPlaceable == null) {
            listPlaceable.placeRelative(0, 0)
        } else {
            listPlaceable.placeRelativeWithLayer(0, 0) { with(state) { listLayer(widthPx, direction) } }
        }
    }
    detailPlaceable?.placeRelativeWithLayer(0, 0) { with(state) { detailLayer(widthPx, direction) } }
}
