package app.nagmedar.ui.shell

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where the list and the detail are in one frame of [DetailMotion]: shifts as fractions of the width, towards the end. */
@Immutable
data class DetailFrame(val listShift: Float, val listAlpha: Float, val detailShift: Float, val detailAlpha: Float)

/**
 * Android's screen transition between a list and the detail opened from it (Desktop Kit 0.2.0,
 * Material's shared axis; on a phone the progress runs on the gesture spring, Desktop Kit 1.0): opening, the detail comes a tenth of the width from the end while it
 * fades in and the list goes the other way fading out; closing is the same backwards. The frames
 * are a function of the progress (0 the list, 1 the detail) run linearly over [NagMotion.LONG];
 * each part keeps its own curve and timing within it.
 */
object DetailMotion {
    /** How far the parts travel: a tenth of the width. */
    const val SHIFT = 0.1f

    /** During the predictive Back gesture: the detail shrinks to 90 %, rounds 28 dp corners and leans 24 dp. */
    const val BACK_SCALE = 0.9f
    const val BACK_CORNER_DP = 28
    const val BACK_LEAN_DP = 24

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
                detailAlpha = NagMotion.StandardDecelerate.transform(part(t, 50f, NagMotion.MEDIUM.toFloat())),
            )
        } else {
            val c = 1f - p
            val t = c * ms
            DetailFrame(
                listShift = -(1f - NagMotion.EmphasizedDecelerate.transform(c)) * SHIFT,
                listAlpha = NagMotion.StandardDecelerate.transform(part(t, 50f, NagMotion.MEDIUM.toFloat())),
                detailShift = NagMotion.EmphasizedAccelerate.transform(part(t, 0f, ms - 50f)) * SHIFT,
                detailAlpha = 1f - NagMotion.StandardAccelerate.transform(part(t, 0f, NagMotion.COLOR.toFloat())),
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
            progress.animateTo(target, NagMotion.gesture())
        } else {
            val duration = (abs(target - progress.value) * NagMotion.LONG).roundToInt()
            progress.animateTo(target, tween(duration, easing = LinearEasing))
        }
        // the shade and the shrink of a Back gesture go once the detail has gone
        if (!open) back.snapTo(0f)
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
 */
@Composable
fun DetailBackHandler(enabled: Boolean, state: DetailMotionState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val motion = NagTheme.motion
    val latestOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { events ->
        state.back.snapTo(0f)
        try {
            events.collect { event ->
                state.swipeEdge = event.swipeEdge
                if (!motion.reduced) state.back.snapTo(event.progress)
            }
            latestOnBack()
        } catch (e: CancellationException) {
            scope.launch { state.back.animateTo(0f, motion.spatial()) }
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

private const val LIST = "list"
private const val DETAIL = "detail"

/**
 * A list and the detail opened from it, on one place of the screen (Desktop Kit 0.2.0's
 * DetailTransition): [detail] non-null opens it with [DetailMotion], null closes it, and Back
 * (the predictive gesture on Android 14 and later) calls [onBack] while it is open, unless
 * [handleBack] is false. Unlike the kit's, the list stays composed under the detail, so it keeps
 * its scroll position and selection; once covered it is neither drawn nor read out. [detailKey]
 * tells details apart; the detail's content stays while it closes.
 */
@Composable
fun <T : Any> DetailTransition(
    detail: T?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    handleBack: Boolean = true,
    detailKey: (T) -> Any = { it },
    list: @Composable () -> Unit,
    detailContent: @Composable (T) -> Unit,
) {
    val motion = NagTheme.motion
    val tier = LocalMotionTier.current
    val state = rememberDetailMotion(detail != null)
    var retained by remember { mutableStateOf(detail) }
    // registered before the detail's own handlers: those (a selection inside it) go first
    DetailBackHandler(enabled = handleBack && detail != null, state = state, onBack = onBack)
    LaunchedEffect(detail?.let(detailKey)) {
        if (detail != null) {
            retained = detail
            state.show(open = true, motion, tier)
        } else {
            state.show(open = false, motion, tier)
            retained = null
        }
    }
    val shown = detail ?: retained
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val covered by remember(state) { derivedStateOf { state.covered } }
    Layout(
        modifier = modifier.fillMaxSize().clipToBounds().detailShade(state),
        content = {
            Box(
                Modifier.layoutId(LIST).then(if (shown != null && covered) Modifier.clearAndSetSemantics {} else Modifier),
                propagateMinConstraints = true,
            ) { list() }
            if (shown != null) Box(Modifier.layoutId(DETAIL), propagateMinConstraints = true) { detailContent(shown) }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val fixed = Constraints.fixed(w, h)
        val paneM = measurables.firstOrNull { it.layoutId == DETAIL }
        // a covered list stays composed but is not measured
        val list = if (paneM != null && covered) null else measurables.first { it.layoutId == LIST }.measure(fixed)
        val pane = paneM?.measure(fixed)
        layout(w, h) { placeListAndDetail(state, list, pane, w, direction) }
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
