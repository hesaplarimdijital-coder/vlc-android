package app.nagmedar.ui.components

import android.graphics.Picture
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import app.nagmedar.ui.R
import app.nagmedar.ui.shell.LocalOnBottomGlass
import app.nagmedar.ui.shell.ShellOverlayHost
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.compose.ui.graphics.lerp as lerpColor

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

    /**
     * 1.0.3 (Desktop Kit 2.3.0): a tap's expand or collapse, on the opening spring's damping a little
     * softer for the whole window's way (0.9 at 500), arrived within half a pixel; a jump with reduced
     * motion. A finger's release settles on the gesture spring instead ([playerFlingBehavior]).
     */
    private fun settle(): FiniteAnimationSpec<Float> =
        NagMotion.fade(tier, spring(dampingRatio = TAP_DAMPING, stiffness = TAP_STIFFNESS, visibilityThreshold = NagMotion.PIXEL))

    /** Opens the full player on the tap's spring (from a tap, the notification, a link). */
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
 * 1.0.3: the card a mini player draws in its strip (the phone's floating card, 1.0.2): [horizontal]
 * and [vertical] its margins inside the strip, [corner] its corners. The player's sheet starts as this
 * card and grows into the page ([ExpandablePlayer]).
 */
@Immutable
class PlayerCard(val horizontal: Dp, val vertical: Dp, val corner: Dp)

/**
 * The phone's player (Desktop Kit 1.0's ExpandablePlayer): the mini player strip in the shell's
 * player slot, which grows into the full player over the whole window.
 *
 * - Drag the mini player up (or tap it) to open the full player; drag the full player down to
 *   close it. Letting go settles on a spring that takes over the fling's speed; half way decides.
 *   Scrollable content in [full] scrolls first; at its top a downward drag pulls the player down.
 * - 1.0.3 (Desktop Kit 2.3.0), one surface: the player starts as the strip's [card] itself and grows
 *   into the page, the page's colour coming over the card's, its corners rounding on the way and
 *   square once it is the page; the scrim darkens with it and the bottom bar is pushed away under it.
 *   The [mini] controls fade out in the first quarter of the way and are then not placed (not drawn,
 *   touched or read out); the [full] ones come in over the last part. An artwork and a title marked
 *   with [playerSharedElement] in both fly from the strip to the full player and back.
 * - It opens from where the strip is drawn, also with the bars hidden by a scroll (they stay where
 *   they are), and lands there again.
 * - Back, and the predictive Back gesture that shrinks the full player with the finger, close it;
 *   the shrink rides down with the sheet.
 * - Swiping the mini player sideways skips: [onNext] (towards the start edge) or [onPrevious]; the
 *   strip follows the finger, ticks once past the threshold and springs back if let go before it.
 *
 * [expandable] false (what plays has a page of its own: a station, an episode) leaves the strip a
 * plain mini player whose taps are its own. [mini] draws its own surface (the card) where it stands
 * alone; in the sheet the sheet is its card ([inPlayerSheetStrip]). [full] draws its own background
 * and a collapse button (`scope.launch { state.collapse() }`). Only inside the shell (its overlay
 * layer); elsewhere only the mini player shows.
 *
 * 1.0: [full] is composed with the strip and stays so, not drawn while the strip rests, so that
 * opening it never composes it on the drag's first frame; it learns from [LocalPlayerSheet] that it
 * is in the sheet (whose drag down and Back close it) and whether it is out. 1.0.3: so is the strip's
 * copy in the sheet.
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
    card: PlayerCard? = null,
) {
    val host = shellOverlayHere()
    val scope = rememberCoroutineScope()
    val tier = LocalMotionTier.current
    SideEffect { state.tier = tier }
    val expandLabel = stringResource(R.string.nag_open_player)
    val canExpand = expandable && host != null
    // the strip lies on the phone's bottom glass (the user's glass decision): its card has no colour of its own
    val onGlass = LocalOnBottomGlass.current
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
            // hidden while the sheet (which starts as the same strip) is out
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
        // first frame; while the mini player rests it is neither drawn, read out, focused nor touched. The collapse
        // runs in this scope: the Back handler's own one ends once the player is no longer expanded
        OverlayLayer(host, ShellOverlayHost.PLAYER) {
            PlayerSheet(state, sheet, miniHeight, card, onGlass, mini, full, collapse = { scope.launch { state.collapse() } })
        }
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

    /** Desktop Kit 2.3.0's PlayerPull (1.0.3 keeps it): the content scrolls first, then the sheet follows the finger. */
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

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // a pulled sheet nothing settled (the drag ended without a fling of its own) settles here
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

/** The sheet the full player lies in, when [ExpandablePlayer] holds it; null where the player stands alone (a tablet's window). */
val LocalPlayerSheet = staticCompositionLocalOf<PlayerSheetScope?> { null }

/**
 * 1.0.3 (Desktop Kit 2.3.0): below this progress the sheet is back on the strip: the strip shows and
 * the sheet goes, without waiting for the spring's last fraction of a pixel (which kept the page over
 * the bottom bar under the strip).
 */
internal const val SHEET_OUT = 0.001f

/** The strip's controls fade out over this first part of the way. */
internal const val MINI_FADE = 0.25f

/** From here on the strip's controls are not placed: not drawn, touched or read out. */
internal const val MINI_GONE = 0.3f

/** The full player's controls come in from here to the end of the way. */
internal const val FULL_FROM = 0.6f

/** A tap's spring (Desktop Kit 2.3.0): the opening spring's damping, a little softer. */
private const val TAP_DAMPING = 0.9f
private const val TAP_STIFFNESS = 500f

/** How round the travelling surface is half way, and how much rounder the Back gesture makes it. */
private val TRAVEL_CORNER = 16.dp
private val BACK_CORNER = 28.dp

/** How far the Back gesture shrinks the player and moves it down. */
private const val BACK_SCALE = 0.1f
private val BACK_DROP = 48.dp

/**
 * 1.0.3 (Desktop Kit 2.3.0's one surface, from the app's floating card): where the player's surface
 * is on its way, free of Compose so that it can be tested on its own.
 */
internal object PlayerSurface {
    /**
     * The surface at [progress] in the sheet's own coordinates (its top at the sheet's top; [width] ×
     * [height] the window's): the strip's card ([insetX], [insetY] inside the strip, [stripHeight]
     * high, the strip [collapsedTop] below the window's top) at 0, the whole window at 1. Its top rises
     * with the sheet, its sides go out to the window's edges and its bottom down from where the card
     * rests to the window's bottom, so that it grows out of the card in every direction.
     */
    fun bounds(progress: Float, collapsedTop: Float, stripHeight: Float, width: Float, height: Float, insetX: Float, insetY: Float): Rect {
        val p = progress.coerceIn(0f, 1f)
        val rest = 1f - p
        val sheetTop = collapsedTop * rest
        val cardBottom = collapsedTop + stripHeight - insetY
        val bottom = cardBottom + (height - cardBottom) * p
        return Rect(insetX * rest, insetY * rest, width - insetX * rest, bottom - sheetTop)
    }

    /** Its corners at [progress]: the card's [card] at the start, rounding to [travel] on the way, square once it is the page. */
    fun radius(progress: Float, card: Float, travel: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return maxOf(card * (1f - p), travel * 4f * p * (1f - p))
    }

    /** The surface's colour at [progress]: the card's [card] under the [page]'s, which comes in with the way. */
    fun fill(progress: Float, card: Color, page: Color): Color = lerpColor(card, page, progress.coerceIn(0f, 1f))
}

/** A rounded rectangle at [rect] within a layer: the player's surface on its way. */
private class SurfaceShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius.coerceAtLeast(0f))))
}

/**
 * The full part, drawn in the shell's overlay from the mini player's place to the top. While the
 * mini player rests it is measured but not placed: nothing of it is drawn or touched, and focus
 * never moves into it; its semantics are cleared too.
 *
 * 1.0.3 (Desktop Kit 2.3.0): one surface that starts as the strip's [card] (a full-width strip
 * without one) and grows into the page ([PlayerSurface]); on it the strip's copy, the full player and,
 * above both, the shared elements on their way ([PlayerFlight]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerSheet(
    state: ExpandablePlayerState,
    sheet: PlayerSheetScope,
    miniHeight: Int,
    card: PlayerCard?,
    onGlass: Boolean,
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
    val flight = remember { PlayerFlight() }
    SideEffect { flight.progress = { state.progress } }
    // the strip's controls are placed in the first part of the way, and until they have drawn once (a player that starts
    // open records them for its first way down); decided by a derived state, not on every frame of the way
    val miniPlaced by remember(state, flight) { derivedStateOf { state.progress < MINI_GONE || !flight.miniDrawn } }
    val insetX = with(density) { (card?.horizontal ?: 0.dp).toPx() }
    val insetY = with(density) { (card?.vertical ?: 0.dp).toPx() }
    val cardRadius = with(density) { (card?.corner ?: 0.dp).toPx() }
    val travel = with(density) { TRAVEL_CORNER.toPx() }
    val backCorner = with(density) { BACK_CORNER.toPx() }
    val rule = with(density) { 1.dp.toPx() }
    // under the bottom glass the card is the glass: only the page's colour comes over it
    val cardColor = if (onGlass) colors.background.copy(alpha = 0f) else colors.surfaceRaised
    val pageColor = colors.background
    val ruleColor = colors.borderStrong
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
                    // the Back gesture's shrink rides down with the sheet instead of opening back up first
                    val back = backProgress.value * p
                    val scale = 1f - BACK_SCALE * back
                    scaleX = scale
                    scaleY = scale
                    translationY = BACK_DROP.toPx() * back
                    // the card growing into the page; rounder under the Back finger
                    val bounds = PlayerSurface.bounds(p, state.collapsedTop, miniHeight.toFloat(), size.width, size.height, insetX, insetY)
                    shape = SurfaceShape(bounds, PlayerSurface.radius(p, cardRadius, travel) + backCorner * back)
                    clip = true
                }
                .drawBehind {
                    val p = state.progress
                    drawRect(PlayerSurface.fill(p, cardColor, pageColor))
                    // the card's rule, gone with the strip's controls
                    val fading = (1f - p / MINI_FADE).coerceIn(0f, 1f)
                    if (card != null && fading > 0f) {
                        val bounds = PlayerSurface.bounds(p, state.collapsedTop, miniHeight.toFloat(), size.width, size.height, insetX, insetY)
                        val radius = PlayerSurface.radius(p, cardRadius, travel) + backCorner * backProgress.value * p
                        drawRoundRect(
                            ruleColor.copy(alpha = ruleColor.alpha * fading),
                            Offset(bounds.left + rule / 2f, bounds.top + rule / 2f),
                            Size(bounds.width - rule, bounds.height - rule),
                            CornerRadius((radius - rule / 2f).coerceAtLeast(0f)),
                            style = Stroke(rule),
                        )
                    }
                }
                .onGloballyPositioned { flight.sheet = it }
                .anchoredDraggable(state.draggable, Orientation.Vertical, flingBehavior = fling)
                .nestedScroll(sheet.nestedScroll)
                .semantics { paneTitle = label },
        ) {
            // the strip's controls, fading out over the first quarter of the way; gone (not touched, not read out) once
            // they are, back just before the way down needs them
            CompositionLocalProvider(LocalPlayerFlight provides flight, LocalPlayerSide provides PlayerSide.Mini) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { miniHeight.toDp() })
                        .graphicsLayer { alpha = (1f - state.progress / MINI_FADE).coerceIn(0f, 1f) }
                        .placedWhile { miniPlaced }
                        .drawWithContent {
                            drawContent()
                            if (!flight.miniDrawn) flight.miniDrawn = true
                        },
                ) { mini() }
            }
            CompositionLocalProvider(LocalPlayerFlight provides flight, LocalPlayerSide provides PlayerSide.Full, LocalPlayerSheet provides sheet) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((state.progress - FULL_FROM) / (1f - FULL_FROM)).coerceIn(0f, 1f) },
                ) { full() }
            }
            // the shared elements on their way between the strip and the full player, above both
            Box(Modifier.matchParentSize().drawBehind { flight.draw(this, state.progress) })
        }
    }

    PredictiveBackHandler(enabled = state.currentValue == PlayerValue.Expanded) { events ->
        backProgress.snapTo(0f)
        val release = NagMotion.spatial(state.tier, tween(NagMotion.MEDIUM, easing = NagMotion.Emphasized), NagMotion.FRACTION)
        try {
            events.collect { event -> backProgress.snapTo(event.progress) }
            collapse()
            // 1.0.3: the shrink rides down with the sheet (scaled by the way, above) and lets go once the sheet has landed
            // on the strip, or a finger has caught it on the way. The collapse starts with the next frame; should it not
            // (a finger already on the sheet, nowhere to go), the shrink lets go now
            scope.launch {
                var waited = 0
                while (!state.draggable.isAnimationRunning && waited++ < 2) withFrameNanos { }
                if (state.draggable.isAnimationRunning) snapshotFlow { state.draggable.isAnimationRunning }.first { !it }
                if (state.progress <= SHEET_OUT) backProgress.snapTo(0f) else backProgress.animateTo(0f, release)
            }
        } catch (e: CancellationException) {
            // a change of mind: back to full size on the spring, arrived within a thousandth (a jump with reduced motion)
            scope.launch { backProgress.animateTo(0f, release) }
            throw e
        }
    }
}

/** Lays the content out always, but places it only while [visible]: not placed, it is not drawn, hit or read out. */
private fun Modifier.placedWhile(visible: () -> Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (visible()) placeable.place(0, 0) }
}

/** What flies between the strip and the full player ([playerSharedElement]): the artwork and the title. */
enum class PlayerShared { Artwork, Title }

/** Which copy of the player's content a [playerSharedElement] is in. */
internal enum class PlayerSide { Mini, Full }

internal val LocalPlayerFlight = staticCompositionLocalOf<PlayerFlight?> { null }
internal val LocalPlayerSide = staticCompositionLocalOf { PlayerSide.Mini }

/**
 * 1.0.3: this is drawn as the strip's copy in the phone player's sheet: the sheet is its card (it
 * draws none of its own), and it shows only for the first part of the way (a timeline does not glide).
 */
internal val inPlayerSheetStrip: Boolean
    @Composable get() = LocalPlayerFlight.current != null && LocalPlayerSide.current == PlayerSide.Mini

/**
 * 1.0.3 (Desktop Kit 2.3.0): inside an [ExpandablePlayer], the element in its `mini` and the element
 * in its `full` marked with the same [key] (the artwork, the title) are one: as the player opens the
 * element flies from its place on the strip to its place in the full player, scaling on the way, the
 * strip's look giving way to the full player's over the first quarter; on the way down it flies back.
 * Put it first in the element's modifiers. [shape] is the element's own rounded shape, kept in flight.
 * In flight the element marked first in `mini` lies on top (a title crossing the artwork's path passes
 * under it). Outside an [ExpandablePlayer]'s sheet nothing flies, and with reduced motion the player
 * jumps as a whole.
 *
 * Each copy is recorded as it draws (into a graphics layer, so that an image's hardware bitmap draws
 * as anywhere else; into a picture on a software canvas, as in a screenshot test) and flies as it last
 * drew: the strip's copy as it looked when the strip's controls went, the full player's as it looks
 * now. Of two copies on one side (a title cross-fading to the next track's) the one drawn last flies.
 */
@Composable
fun Modifier.playerSharedElement(key: Any, shape: CornerBasedShape? = null): Modifier {
    val flight = LocalPlayerFlight.current ?: return this
    val side = LocalPlayerSide.current
    val density = LocalDensity.current
    val layer = rememberGraphicsLayer()
    val copy = remember { PlayerFlight.Copy() }
    val entry = remember(flight, key) { flight.entry(key) }
    DisposableEffect(entry, side, copy) { onDispose { flight.release(entry, side, copy) } }
    return this
        .onGloballyPositioned { coordinates ->
            copy.place(coordinates, shape?.topStart?.toPx(coordinates.size.toSize(), density) ?: 0f)
        }
        .drawWithContent {
            // recorded on every draw; in flight the flight draws it, else it draws itself
            copy.record(this, layer)
            entry.use(side, copy)
            if (!flight.flying(entry)) copy.draw(this)
        }
}

/** The elements flying between the strip and the full player, and the sheet they fly in (1.0.3, Desktop Kit 2.3.0). */
internal class PlayerFlight {
    /** One copy of a shared element (on the strip or in the full player): where it is and what it last drew. */
    class Copy {
        var coordinates: LayoutCoordinates? = null
            private set
        var radius = 0f
            private set

        /** The element's own layer, recorded into on a hardware canvas: its content draws there as it does anywhere. */
        private var layer: GraphicsLayer? = null

        /** Recorded into on a software canvas (a test's bitmap), which cannot draw a layer: a picture plays back on any canvas. */
        private val picture = Picture()

        /** Where the latest recording is: in [layer] (true), in [picture] (false), nowhere yet (null). */
        private var inLayer: Boolean? = null

        /** The copy is in the tree and has drawn once. */
        val ready: Boolean get() = coordinates?.isAttached == true && inLayer != null

        /** The recorded size. */
        val width: Int get() = if (inLayer == true) layer?.size?.width ?: 0 else picture.width
        val height: Int get() = if (inLayer == true) layer?.size?.height ?: 0 else picture.height

        fun place(coordinates: LayoutCoordinates, radius: Float) {
            this.coordinates = coordinates
            this.radius = radius
        }

        /** Records the element's content as it draws into [scope]: into [layer] on a hardware canvas, into the picture else. */
        fun record(scope: ContentDrawScope, layer: GraphicsLayer) {
            val width = scope.size.width.roundToInt().coerceAtLeast(1)
            val height = scope.size.height.roundToInt().coerceAtLeast(1)
            if (scope.drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                with(scope) { layer.record(IntSize(width, height)) { scope.drawContent() } }
                this.layer = layer
                inLayer = true
            } else {
                val canvas = Canvas(picture.beginRecording(width, height))
                val own = scope.drawContext.canvas
                scope.drawContext.canvas = canvas
                try {
                    scope.drawContent()
                } finally {
                    scope.drawContext.canvas = own
                    picture.endRecording()
                }
                inLayer = false
            }
        }

        /** Draws the latest recording at the origin of [scope], as recorded. */
        fun draw(scope: DrawScope) {
            val recorded = layer
            if (inLayer == true && recorded != null) scope.drawLayer(recorded) else scope.drawIntoCanvas { it.nativeCanvas.drawPicture(picture) }
        }

        fun clear() {
            coordinates = null
            layer = null
            inLayer = null
        }
    }

    /** One shared element: the copies that drew last on either side, and its way between them. */
    class Entry {
        var mini: Copy? = null
            private set
        var full: Copy? = null
            private set

        /** Where the element is on the strip and in the full player, in the sheet's coordinates (see [PlayerFlight.locate]). */
        var from = Rect.Zero
        var to = Rect.Zero
        val path = Path()

        /** [copy] has just drawn on [side]: it is the one that flies. */
        fun use(side: PlayerSide, copy: Copy) {
            if (side == PlayerSide.Mini) mini = copy else full = copy
        }

        /** [copy] has left [side]; another copy that drew since stays. */
        fun drop(side: PlayerSide, copy: Copy) {
            if (side == PlayerSide.Mini && mini === copy) mini = null
            if (side == PlayerSide.Full && full === copy) full = null
        }

        /** Both sides have a copy in the tree that has drawn. */
        val ready: Boolean get() = mini?.ready == true && full?.ready == true
    }

    /** The elements in the order they were first marked (the strip's copy is composed first: its first lies on top). */
    val entries = LinkedHashMap<Any, Entry>()
    var sheet: LayoutCoordinates? = null
    var progress: () -> Float = { 0f }

    /** Set once the strip's copy has drawn in the sheet; until then it is placed whatever the progress. */
    var miniDrawn by mutableStateOf(false)

    private val fade = Paint()

    fun entry(key: Any): Entry = entries.getOrPut(key) { Entry() }

    /** [copy] of [entry] has left [side]. The entry stays: the player's elements are a few fixed keys. */
    fun release(entry: Entry, side: PlayerSide, copy: Copy) {
        entry.drop(side, copy)
        copy.clear()
    }

    /** Finds [entry]'s way in [sheet] (its [Entry.from] and [Entry.to]); false while one end is unknown or empty. */
    private fun locate(entry: Entry, sheet: LayoutCoordinates): Boolean {
        if (!entry.ready) return false
        val mini = entry.mini?.coordinates ?: return false
        val full = entry.full?.coordinates ?: return false
        entry.from = sheet.localBoundingBoxOf(mini, clipBounds = false)
        entry.to = sheet.localBoundingBoxOf(full, clipBounds = false)
        return !entry.from.isEmpty && !entry.to.isEmpty
    }

    /** Whether the flight draws [entry] now (on the way, with both ends known): then the element does not draw itself. */
    fun flying(entry: Entry): Boolean {
        val p = progress()
        if (p <= 0f || p >= 1f) return false
        val sheet = sheet?.takeIf { it.isAttached } ?: return false
        return locate(entry, sheet)
    }

    /** Draws every ready element at [progress] of its way; the strip's copy fades away over the first [CROSS], the full player's under it. */
    fun draw(scope: DrawScope, progress: Float) {
        if (progress <= 0f || progress >= 1f) return
        val sheet = sheet?.takeIf { it.isAttached } ?: return
        // the first element marked lies on top: a title crossing the artwork's path passes under it
        for (entry in entries.values.reversed()) {
            if (!locate(entry, sheet)) continue
            val mini = entry.mini ?: continue
            val full = entry.full ?: continue
            val bounds = lerp(entry.from, entry.to, progress)
            val radius = lerp(mini.radius, full.radius, progress)
            val cross = (progress / CROSS).coerceIn(0f, 1f)
            entry.path.reset()
            entry.path.addRoundRect(RoundRect(0f, 0f, bounds.width, bounds.height, CornerRadius(radius)))
            with(scope) {
                withTransform({
                    translate(bounds.left, bounds.top)
                    clipPath(entry.path)
                }) {
                    // the full player's copy lies under the strip's from the start, so that an artwork (the same picture
                    // twice) never thins out while the strip's fades away
                    drawCopy(full, bounds.size, 1f)
                    if (cross < 1f) drawCopy(mini, bounds.size, 1f - cross)
                }
            }
        }
    }

    /** [copy], fitted into [size] from its recorded size (not stretched: a title keeps its letters' shape), at [alpha]. */
    private fun DrawScope.drawCopy(copy: Copy, size: Size, alpha: Float) {
        val width = copy.width.toFloat()
        val height = copy.height.toFloat()
        if (width <= 0f || height <= 0f) return
        val fit = minOf(size.width / width, size.height / height)
        withTransform({
            translate((size.width - width * fit) / 2f, (size.height - height * fit) / 2f)
            scale(fit, fit, Offset.Zero)
        }) {
            val canvas = drawContext.canvas
            if (alpha < 1f) {
                fade.alpha = alpha
                canvas.saveLayer(Rect(0f, 0f, width, height), fade)
            }
            copy.draw(this)
            if (alpha < 1f) canvas.restore()
        }
    }

    private companion object {
        /** The part of the way over which the strip's copy of an element fades away, leaving the full player's. */
        const val CROSS = 0.25f
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
