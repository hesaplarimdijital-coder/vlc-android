package app.nagmedar.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.shell.ShellOverlayHost
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** Where an [ExpandablePlayer] rests. */
enum class PlayerValue { Collapsed, Expanded }

/**
 * State of an [ExpandablePlayer] (Desktop Kit 1.0): the full player's top edge, dragged between the
 * mini player's place ([PlayerValue.Collapsed]) and the top of the window ([PlayerValue.Expanded]).
 * [progress] runs from 0 to 1 with it; read it while drawing (`graphicsLayer { alpha = progress }`)
 * to fade things along. Survives configuration changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class ExpandablePlayerState(initialValue: PlayerValue, density: Density) {
    internal val draggable = AnchoredDraggableState(
        initialValue = initialValue,
        positionalThreshold = { distance -> distance * 0.5f },
        velocityThreshold = { with(density) { 400.dp.toPx() } },
        snapAnimationSpec = NagMotion.gesture(),
        decayAnimationSpec = exponentialDecay(),
    )

    /** Distance from the window top to the mini player; the collapsed anchor. */
    internal var collapsedTop by mutableFloatStateOf(0f)
        private set

    /** The settled value, or the one being animated to. */
    val currentValue: PlayerValue get() = draggable.currentValue

    /** The value the player is moving towards (while dragged: the one it would settle at). */
    val targetValue: PlayerValue get() = draggable.targetValue

    val isExpanded: Boolean get() = draggable.currentValue == PlayerValue.Expanded

    /** 0 collapsed … 1 expanded, following the finger. */
    val progress: Float
        get() {
            val top = sheetTop()
            return if (collapsedTop <= 0f) (if (draggable.currentValue == PlayerValue.Expanded) 1f else 0f)
            else (1f - top / collapsedTop).coerceIn(0f, 1f)
        }

    /** Set by the player: brings the bars back and measures where the mini player rests, before it expands. */
    internal var beforeExpand: () -> Unit = {}

    /** Opens the full player on the gesture spring (from a tap, the notification, a link). */
    suspend fun expand() {
        beforeExpand()
        draggable.animateTo(PlayerValue.Expanded)
    }

    /** Back to the mini player. */
    suspend fun collapse() = draggable.animateTo(PlayerValue.Collapsed)

    /** Jumps to [value] without animation. */
    suspend fun snapTo(value: PlayerValue) = draggable.snapTo(value)

    internal fun sheetTop(): Float {
        val offset = draggable.offset
        if (!offset.isNaN()) return offset
        return if (draggable.currentValue == PlayerValue.Expanded) 0f else collapsedTop
    }

    internal fun updateCollapsedTop(top: Float) {
        // the bars' slide is taken out of the position again: a float's rounding is no move
        if (top <= 0f || abs(top - collapsedTop) < 0.5f) return
        collapsedTop = top
        draggable.updateAnchors(
            DraggableAnchors {
                PlayerValue.Collapsed at top
                PlayerValue.Expanded at 0f
            },
            draggable.targetValue,
        )
    }

    companion object {
        fun saver(density: Density): Saver<ExpandablePlayerState, PlayerValue> =
            Saver(save = { it.currentValue }, restore = { ExpandablePlayerState(it, density) })
    }
}

@Composable
fun rememberExpandablePlayerState(initialValue: PlayerValue = PlayerValue.Collapsed): ExpandablePlayerState {
    val density = LocalDensity.current
    return rememberSaveable(density, saver = ExpandablePlayerState.saver(density)) { ExpandablePlayerState(initialValue, density) }
}

/**
 * The phone's player (Desktop Kit 1.0's ExpandablePlayer): the mini player strip in the shell's
 * player slot, which grows into the full player over the whole window.
 *
 * - Drag the mini player up (or tap it) to open the full player; drag the full player down to
 *   close it. Letting go settles on a spring that takes over the fling's speed; half way decides.
 * - The scrim behind it and the cross-fade from [mini] to [full] follow the drag; the bottom bar
 *   slides away as it opens.
 * - Back, and the predictive Back gesture that shrinks the full player with the finger, close it.
 * - Swiping the mini player sideways skips: [onNext] (towards the start edge) or [onPrevious]; the
 *   strip follows the finger, ticks once past the threshold and springs back if let go before it.
 *
 * [expandable] false (what plays has a page of its own: a station, an episode) leaves the strip a
 * plain mini player whose taps are its own. [mini] draws its own surface (the glass card); [full]
 * its own background and a collapse button (`scope.launch { state.collapse() }`). Only inside the
 * shell (its overlay layer); elsewhere only the mini player shows.
 *
 * 1.0: [full] is composed with the strip and stays so, not drawn while the strip rests, so that
 * opening it never composes it on the drag's first frame; it learns from [LocalPlayerSheet] that it
 * is in the sheet (whose drag down and Back close it) and whether it is out.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandablePlayer(
    state: ExpandablePlayerState,
    mini: @Composable () -> Unit,
    full: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    expandable: Boolean = true,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    val host = shellOverlayHere()
    val scope = rememberCoroutineScope()
    val expandLabel = stringResource(R.string.nag_open_player)
    val canExpand = expandable && host != null
    var miniCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var miniHeight by remember { mutableIntStateOf(0) }
    // where the strip rests with the bars shown: the bars' slide is a layer translation the position includes
    fun measureAnchor() {
        val shell = host ?: return
        val coordinates = miniCoordinates ?: return
        val overlay = shell.coordinates ?: return
        if (!coordinates.isAttached || !overlay.isAttached) return
        state.updateCollapsedTop(overlay.localPositionOf(coordinates, Offset.Zero).y - shell.navigationOffset())
    }
    val beforeExpand: () -> Unit = {
        measureAnchor()
        host?.onExpandStart?.invoke()
    }
    val latestBeforeExpand by rememberUpdatedState(beforeExpand)
    SideEffect { state.beforeExpand = beforeExpand }
    if (host != null) {
        val progressOf = remember(state, canExpand) { if (canExpand) ({ state.progress }) else ({ 0f }) }
        SideEffect {
            if (host.expandProgress !== progressOf) host.expandProgress = progressOf
        }
        DisposableEffect(host) { onDispose { host.expandProgress = { 0f } } }
        // the overlay and the strip are positioned in the same pass, in no fixed order
        LaunchedEffect(host.coordinates, miniCoordinates) { measureAnchor() }
    }
    // what plays has no full player any more (a station took over): back to the strip
    LaunchedEffect(canExpand) { if (!canExpand && state.currentValue != PlayerValue.Collapsed) state.snapTo(PlayerValue.Collapsed) }
    Box(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                miniCoordinates = it
                miniHeight = it.size.height
                measureAnchor()
            }
            // hidden while the sheet (which starts with the same strip) is out
            .graphicsLayer { alpha = if (canExpand && state.progress > 0.001f) 0f else 1f }
            .then(
                if (!canExpand) Modifier else Modifier
                    // a drag up brings the bars back first, so the sheet starts from where the strip rests; a press
                    // (Play, the start of a sideways swipe) leaves them where they are (a click expands through expand())
                    .pointerInput(Unit) { awaitVerticalDragStart { latestBeforeExpand() } }
                    .anchoredDraggable(state.draggable, Orientation.Vertical, enabled = state.collapsedTop > 0f)
                    .semantics {
                        customActions = listOf(CustomAccessibilityAction(expandLabel) { scope.launch { state.expand() }; true })
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = expandLabel) {
                        scope.launch { state.expand() }
                    },
            ),
    ) {
        MiniSwipeArea(onPrevious, onNext) { mini() }
    }

    if (host != null && canExpand) {
        // 1.0: composed for as long as there is a full player to show, so that an expand never composes it on its
        // first frame; while the mini player rests it is neither drawn, read out, focused nor touched
        val sheet = remember(state) { PlayerSheetScope(state) }
        // the collapse runs in this scope: the Back handler's own one ends once the player is no longer expanded
        OverlayLayer(host, ShellOverlayHost.PLAYER) { PlayerSheet(state, sheet, miniHeight, mini, full, collapse = { scope.launch { state.collapse() } }) }
    }
}

/**
 * What [ExpandablePlayer] tells the full player it holds (1.0): whether it is out ([shown]); while
 * the mini player rests the full player stays composed but is not drawn, so what runs on its own
 * (a visualiser's capture, a timeline's glide) waits for [shown]. Its [nestedScroll] hands what a
 * list in it cannot scroll (the lyrics pulled down at their top) to the sheet, so that the pull
 * closes the player as a drag on it does, with the finger's speed. Inside the sheet, the player
 * leaves vertical drags and Back to it ([LocalPlayerSheet]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class PlayerSheetScope internal constructor(private val state: ExpandablePlayerState) {
    private val out = derivedStateOf { state.progress > 0f || state.targetValue == PlayerValue.Expanded }

    /** The full player is out: dragged, opening, open or closing. */
    val shown: Boolean get() = out.value

    /** Where the sheet is, between its places: what a release settles. */
    private val between: Boolean get() = state.collapsedTop > 0f && state.sheetTop() > 0.5f

    val nestedScroll: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // pushed back up while the sheet is pulled down: the sheet goes back first, then the list scrolls
            if (source != NestedScrollSource.UserInput || available.y >= 0f || !between) return Offset.Zero
            return Offset(0f, state.draggable.dispatchRawDelta(available.y))
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // what the list cannot take (pulled down at its top) moves the sheet
            if (source != NestedScrollSource.UserInput || available.y == 0f || state.collapsedTop <= 0f) return Offset.Zero
            return Offset(0f, state.draggable.dispatchRawDelta(available.y))
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            // let go with the sheet pulled down: it settles with the finger's speed and the list does not fling
            if (!between) return Velocity.Zero
            state.draggable.settle(available.y)
            return available
        }
    }
}

/** The sheet the full player lies in, when [ExpandablePlayer] holds it; null where the player stands alone (a tablet's overlay). */
val LocalPlayerSheet = staticCompositionLocalOf<PlayerSheetScope?> { null }

/**
 * Waits, gesture by gesture, for a press that moves past the touch slop mostly up or down, and calls
 * [onStart] then, before the drag that follows moves anything; only watches, never consumes.
 */
private suspend fun PointerInputScope.awaitVerticalDragStart(onStart: () -> Unit) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var moved = Offset.Zero
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            moved += change.position - change.previousPosition
            if (abs(moved.y) > slop && abs(moved.y) > abs(moved.x)) {
                onStart()
                break
            }
            if (abs(moved.x) > slop) break
        }
    }
}

/**
 * The full part, drawn in the shell's overlay from the mini player's place to the top. While the
 * mini player rests it is measured but not placed: nothing of it is drawn or touched, and focus
 * never moves into it; its semantics are cleared too.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerSheet(
    state: ExpandablePlayerState,
    sheet: PlayerSheetScope,
    miniHeight: Int,
    mini: @Composable () -> Unit,
    full: @Composable () -> Unit,
    collapse: () -> Unit,
) {
    val colors = NagTheme.colors
    val scope = rememberCoroutineScope()
    val backProgress = remember { Animatable(0f) }
    val label = stringResource(R.string.nag_now_playing)
    val density = LocalDensity.current
    val shown = sheet.shown
    Box(
        Modifier
            .fillMaxSize()
            .then(if (shown) Modifier else Modifier.clearAndSetSemantics {})
            // read while placing: the first frame of a drag places it, nothing recomposes for it
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { if (sheet.shown) placeable.place(0, 0) }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.progress }
                .background(colors.scrim),
        )
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, state.sheetTop().roundToInt()) }
                .graphicsLayer {
                    val p = state.progress
                    val back = backProgress.value
                    val scale = 1f - 0.1f * back
                    scaleX = scale
                    scaleY = scale
                    translationY = 48.dp.toPx() * back
                    // rounded while it travels; at rest over the window its backdrop goes on under the system's bars
                    shape = sheetCorner(16f * (1f - p) + 28f * back)
                    clip = p < 1f || back > 0f
                }
                .background(colors.background)
                .anchoredDraggable(state.draggable, Orientation.Vertical)
                .nestedScroll(sheet.nestedScroll)
                .semantics { paneTitle = label },
        ) {
            // the strip's copy only while the sheet is out: it fades into the full player in the first quarter
            if (shown) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { miniHeight.toDp() })
                        .graphicsLayer { alpha = (1f - state.progress / 0.25f).coerceIn(0f, 1f) },
                ) { mini() }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ((state.progress - 0.15f) / 0.85f).coerceIn(0f, 1f) },
            ) { CompositionLocalProvider(LocalPlayerSheet provides sheet, content = full) }
        }
    }

    PredictiveBackHandler(enabled = state.currentValue == PlayerValue.Expanded) { events ->
        backProgress.snapTo(0f)
        try {
            events.collect { event -> backProgress.snapTo(event.progress) }
            scope.launch { backProgress.animateTo(0f, tween(NagMotion.LONG, easing = NagMotion.Emphasized)) }
            collapse()
        } catch (e: CancellationException) {
            scope.launch { backProgress.animateTo(0f, NagMotion.gesture()) }
            throw e
        }
    }
}

/** The sheet's corners for a radius of [dp] dp, rounded to a whole dp: made once each, not on every frame of a drag. */
private fun sheetCorner(dp: Float): Shape = SHEET_CORNERS[dp.roundToInt().coerceIn(0, SHEET_CORNERS.lastIndex)]

/** 0 … 44 dp: the travelling sheet's 16 dp and the Back gesture's 28 dp more. */
private val SHEET_CORNERS: Array<Shape> = Array(45) { RoundedCornerShape(it.dp) }

/** What a sideways swipe on the mini player does. */
enum class MiniSwipeAction { None, Previous, Next }

/** The mini player's swipe rules (Desktop Kit 1.0), free of Compose so they can be tested on their own. */
object MiniSwipe {
    /**
     * The action for a swipe released at [offset] px (negative: towards the left) moving at
     * [velocity] px/s. It commits past [threshold], or on a fling faster than [flingVelocity] in
     * the direction of the offset. Towards the start edge (left, or right in [rtl]) is "next".
     * A direction without a handler never commits.
     */
    fun decide(
        offset: Float,
        velocity: Float,
        threshold: Float,
        flingVelocity: Float,
        canPrevious: Boolean,
        canNext: Boolean,
        rtl: Boolean = false,
    ): MiniSwipeAction {
        if (offset == 0f) return MiniSwipeAction.None
        val past = abs(offset) >= threshold
        val flung = abs(velocity) >= flingVelocity && sign(velocity) == sign(offset) && abs(offset) >= threshold / 4f
        if (!past && !flung) return MiniSwipeAction.None
        val towardsStart = if (rtl) offset > 0f else offset < 0f
        return when {
            towardsStart && canNext -> MiniSwipeAction.Next
            !towardsStart && canPrevious -> MiniSwipeAction.Previous
            else -> MiniSwipeAction.None
        }
    }

    /** How far a drag of [raw] px moves the strip: freely towards a direction with a handler, a third as far otherwise. */
    fun resist(raw: Float, canPrevious: Boolean, canNext: Boolean, rtl: Boolean = false): Float {
        val towardsStart = if (rtl) raw > 0f else raw < 0f
        val allowed = if (towardsStart) canNext else canPrevious
        return if (allowed) raw else raw / 3f
    }
}

/**
 * The mini player's sideways swipe: the content follows the finger, a haptic tick marks the
 * threshold, a committed swipe slides the strip out and the next one in, anything else springs back.
 */
@Composable
private fun MiniSwipeArea(onPrevious: (() -> Unit)?, onNext: (() -> Unit)?, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val tier = LocalMotionTier.current
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val latestPrevious by rememberUpdatedState(onPrevious)
    val latestNext by rememberUpdatedState(onNext)
    val enabled = onPrevious != null || onNext != null
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(tier, rtl) {
                    val tracker = VelocityTracker()
                    var raw = 0f
                    var armed = false
                    val threshold = { minOf(with(density) { SWIPE_THRESHOLD.toPx() }, width * 0.3f) }
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            raw = offset.value
                            armed = false
                        },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().x
                            val action = MiniSwipe.decide(offset.value, velocity, threshold(), with(density) { SWIPE_FLING.toPx() },
                                latestPrevious != null, latestNext != null, rtl)
                            scope.launch { finishSwipe(offset, action, velocity, width, tier, rtl, latestPrevious, latestNext) }
                        },
                        onDragCancel = {
                            scope.launch { offset.animateTo(0f, NagMotion.spatial(tier, tween(NagMotion.MEDIUM))) }
                        },
                    ) { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        raw += amount
                        val moved = MiniSwipe.resist(raw, latestPrevious != null, latestNext != null, rtl)
                        scope.launch { offset.snapTo(moved) }
                        val past = abs(moved) >= threshold()
                        if (past != armed) {
                            armed = past
                            if (past) view.performHapticFeedback(thresholdHaptic())
                        }
                    }
                },
            )
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / width * 0.7f).coerceIn(0f, 0.7f)
            },
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

private suspend fun finishSwipe(
    offset: Animatable<Float, AnimationVector1D>,
    action: MiniSwipeAction,
    velocity: Float,
    width: Float,
    tier: MotionTier,
    rtl: Boolean,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    val spring = NagMotion.spatial(tier, tween<Float>(NagMotion.MEDIUM, easing = NagMotion.Emphasized))
    if (action == MiniSwipeAction.None) {
        offset.animateTo(0f, spring, initialVelocity = velocity)
        return
    }
    // out on the side it was swiped to, the new track in from the other side
    val towardsStart = action == MiniSwipeAction.Next
    val out = if (towardsStart != rtl) -width else width
    offset.animateTo(out, spring, initialVelocity = velocity)
    if (action == MiniSwipeAction.Next) onNext?.invoke() else onPrevious?.invoke()
    offset.snapTo(-out * 0.4f)
    offset.animateTo(0f, spring)
}

private fun thresholdHaptic(): Int =
    if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK

/** A sideways swipe this long (or a third of the strip) skips. */
private val SWIPE_THRESHOLD = 96.dp

/** A sideways fling this fast skips from a quarter of the way. */
private val SWIPE_FLING = 700.dp
