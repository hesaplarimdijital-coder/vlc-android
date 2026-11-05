package app.nagmedar.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.pointer.PointerEventType
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
 *
 * 1.0.3 (Compose 1.12): how a release settles is the drag's own ([playerFlingBehavior]); [density] is
 * kept for the saver's signature.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class ExpandablePlayerState(initialValue: PlayerValue, @Suppress("unused") density: Density) {
    internal val draggable = AnchoredDraggableState(initialValue)

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

    /**
     * Set by the player (1.0.3, Desktop Kit 2.3.0): measures where the strip is drawn now, the bars'
     * slide included, so that the sheet opens from there and lands there again.
     */
    internal var measureAnchor: () -> Unit = {}

    /** 1.0.3: the motion tier of the shell around the player; set by [ExpandablePlayer], so [expand] jumps with reduced motion. */
    internal var tier: MotionTier = MotionTier.Gesture

    /** 1.0.3: the gesture spring, arrived within half a pixel (Desktop Kit 2.3.0); a jump with reduced motion. */
    private fun settle(): FiniteAnimationSpec<Float> = NagMotion.spatial(tier, NagMotion.gesture(NagMotion.PIXEL), NagMotion.PIXEL)

    /** Opens the full player on the gesture spring (from a tap, the notification, a link). */
    suspend fun expand() {
        measureAnchor()
        draggable.animateTo(PlayerValue.Expanded, settle())
    }

    /** Back to the mini player, where its strip is drawn now. */
    suspend fun collapse() {
        measureAnchor()
        draggable.animateTo(PlayerValue.Collapsed, settle())
    }

    /** Jumps to [value] without animation. */
    suspend fun snapTo(value: PlayerValue) = draggable.snapTo(value)

    internal fun sheetTop(): Float {
        val offset = draggable.offset
        if (!offset.isNaN()) return offset
        return if (draggable.currentValue == PlayerValue.Expanded) 0f else collapsedTop
    }

    internal fun updateCollapsedTop(top: Float) {
        // a float's rounding is no move
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
    val tier = LocalMotionTier.current
    SideEffect { state.tier = tier }
    val expandLabel = stringResource(R.string.nag_open_player)
    val canExpand = expandable && host != null
    var miniCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var miniHeight by remember { mutableIntStateOf(0) }
    val sheet = remember(state) { PlayerSheetScope(state) }
    // 1.0.3 (Desktop Kit 2.3.0): the sheet opens from where the strip is drawn and lands there again, the bars' slide (a
    // layer translation) included: with the bars hidden by a scroll they stay where they are (the sheet covers them).
    // The strip's top as laid out, without the slide, is measured on every press, since a moved bar moves the strip
    // without laying it out again; while the sheet is out its landing place follows the bars (a page opened from the
    // player shows them again)
    var restTop by remember { mutableFloatStateOf(Float.NaN) }
    fun measureAnchor() {
        val shell = host ?: return
        val coordinates = miniCoordinates ?: return
        val overlay = shell.coordinates ?: return
        if (!coordinates.isAttached || !overlay.isAttached) return
        val slide = shell.navigationOffset()
        val drawnTop = overlay.localPositionOf(coordinates, Offset.Zero).y
        restTop = drawnTop - slide
        state.updateCollapsedTop(drawnTop)
    }
    val latestMeasure by rememberUpdatedState<() -> Unit>(newValue = { measureAnchor() })
    SideEffect { state.measureAnchor = { latestMeasure() } }
    val fling = playerFlingBehavior(state)
    if (host != null) {
        val progressOf = remember(state, canExpand) { if (canExpand) ({ state.progress }) else ({ 0f }) }
        SideEffect {
            if (host.expandProgress !== progressOf) host.expandProgress = progressOf
        }
        DisposableEffect(host) { onDispose { host.expandProgress = { 0f } } }
        // the overlay and the strip are positioned in the same pass, in no fixed order
        LaunchedEffect(host.coordinates, miniCoordinates) { measureAnchor() }
        LaunchedEffect(host, state, sheet) {
            snapshotFlow { if (sheet.shown && !restTop.isNaN()) restTop + host.navigationOffset() else Float.NaN }
                .collect { top -> if (!top.isNaN()) state.updateCollapsedTop(top) }
        }
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
            .graphicsLayer { alpha = if (canExpand && state.progress > SHEET_OUT) 0f else 1f }
            .then(
                if (!canExpand) Modifier else Modifier
                    // the anchor where the strip is drawn, before the press's drag or click moves anything (watched only)
                    .pointerInput(state) {
                        awaitPointerEventScope {
                            while (true) {
                                if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) latestMeasure()
                            }
                        }
                    }
                    .anchoredDraggable(state.draggable, Orientation.Vertical, enabled = state.collapsedTop > 0f, flingBehavior = fling)
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
    private val out = derivedStateOf { state.progress > SHEET_OUT || state.targetValue == PlayerValue.Expanded }

    /** The full player is out: dragged, opening, open or closing. */
    val shown: Boolean get() = out.value

    /** Where the sheet is, between its places: what a release settles. */
    private val between: Boolean get() = state.collapsedTop > 0f && state.sheetTop() > 0.5f

    /** The sheet's release rules ([playerFlingBehavior]), set while the sheet is composed. */
    internal var fling: FlingBehavior? = null

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
            settle(available.y)
            return available
        }
    }

    /** Settles the sheet as a drag on it that ends at [velocity] would (Compose 1.12: through its fling behaviour). */
    private suspend fun settle(velocity: Float) {
        val behavior = fling ?: return
        val draggable = state.draggable
        draggable.anchoredDrag {
            val scroll = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float {
                    val from = draggable.requireOffset()
                    val to = (from + pixels).coerceIn(draggable.anchors.minPosition(), draggable.anchors.maxPosition())
                    dragTo(to)
                    return to - from
                }
            }
            with(behavior) { scroll.performFling(velocity) }
        }
    }
}

/** The sheet the full player lies in, when [ExpandablePlayer] holds it; null where the player stands alone (a tablet's overlay). */
val LocalPlayerSheet = staticCompositionLocalOf<PlayerSheetScope?> { null }

/**
 * 1.0.3 (Desktop Kit 2.3.0): below this progress the sheet is back on the strip: the strip shows and
 * the sheet goes, without waiting for the spring's last fraction of a pixel (which kept the page over
 * the bottom bar under the strip).
 */
internal const val SHEET_OUT = 0.001f

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
    val fling = playerFlingBehavior(state)
    SideEffect { sheet.fling = fling }
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
                .anchoredDraggable(state.draggable, Orientation.Vertical, flingBehavior = fling)
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
            // 1.0.3: at once with reduced motion, as the collapse
            scope.launch { backProgress.animateTo(0f, NagMotion.fade(state.tier, tween(NagMotion.LONG, easing = NagMotion.Emphasized))) }
            collapse()
        } catch (e: CancellationException) {
            // a change of mind: back to full size on the spring, arrived within a thousandth (a jump with reduced motion)
            scope.launch {
                backProgress.animateTo(0f, NagMotion.spatial(state.tier, tween(NagMotion.MEDIUM, easing = NagMotion.Emphasized), NagMotion.FRACTION))
            }
            throw e
        }
    }
}

/**
 * Letting go of the player (1.0.3, Compose 1.12's fling behaviour, as Desktop Kit 2's): past half way, or a fling
 * towards the other place, it goes there, else it goes back; on the gesture spring, with the finger's speed, arrived
 * within half a pixel (at once with reduced motion).
 */
@Composable
private fun playerFlingBehavior(state: ExpandablePlayerState): FlingBehavior =
    AnchoredDraggableDefaults.flingBehavior(
        state.draggable,
        positionalThreshold = { distance -> distance * 0.5f },
        animationSpec = NagMotion.spatial(LocalMotionTier.current, NagMotion.gesture(NagMotion.PIXEL), NagMotion.PIXEL),
    )

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
                            scope.launch { offset.animateTo(0f, NagMotion.spatial(tier, tween(NagMotion.MEDIUM), NagMotion.PIXEL)) }
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
    val spring = NagMotion.spatial(tier, tween<Float>(NagMotion.MEDIUM, easing = NagMotion.Emphasized), NagMotion.PIXEL)
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
