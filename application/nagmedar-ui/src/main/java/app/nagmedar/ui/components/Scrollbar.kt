package app.nagmedar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay

/**
 * 1.0.8 (Desktop Kit 2.8.0, the design audit's high finding 3): what a scrollbar needs of a scrolling container: how long
 * the content is, how much of it is in view and where the view is, in pixels, and how to move it. A lazy list or grid
 * knows only the items in view, so its length is an estimate from their average height.
 */
@Stable
internal interface ScrollbarAdapter {
    /** The content's length, the paddings included. */
    val contentLength: Float

    /** The view's length. */
    val viewportLength: Float

    /** How far the content has scrolled. */
    val offset: Float

    /** Room left at the start and the end of the track (a table's header, the phone's bars), in pixels. */
    val insetStart: Int
    val insetEnd: Int

    val scrolling: Boolean

    /** Scrolls the content so [offset] is [to]. */
    fun scrollTo(to: Float)
}

/** The share of the content in view, 0…1; 1: nothing to scroll. */
internal val ScrollbarAdapter.shown: Float
    get() = if (contentLength <= viewportLength || contentLength <= 0f) 1f else viewportLength / contentLength

/** Where the view is, 0 (top) … 1 (end). */
internal val ScrollbarAdapter.position: Float
    get() {
        val range = contentLength - viewportLength
        return if (range <= 0f) 0f else (offset / range).coerceIn(0f, 1f)
    }

private class LazyListAdapter(private val state: LazyListState) : ScrollbarAdapter {
    /** An item's average height and the gap after it. */
    private val step: Float
        get() {
            val info = state.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) return 0f
            return items.sumOf { it.size }.toFloat() / items.size + info.mainAxisItemSpacing
        }
    override val contentLength: Float
        get() {
            val info = state.layoutInfo
            return info.beforeContentPadding + info.totalItemsCount * step - info.mainAxisItemSpacing + info.afterContentPadding
        }
    override val viewportLength: Float get() = state.layoutInfo.viewportSize.height.toFloat()
    override val offset: Float get() = state.firstVisibleItemIndex * step + state.firstVisibleItemScrollOffset
    override val insetStart: Int get() = state.layoutInfo.beforeContentPadding
    override val insetEnd: Int get() = state.layoutInfo.afterContentPadding
    override val scrolling: Boolean get() = state.isScrollInProgress

    override fun scrollTo(to: Float) {
        val step = step
        if (step <= 0f) return
        val index = (to / step).toInt().coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        state.requestScrollToItem(index, (to - index * step).toInt().coerceAtLeast(0))
    }
}

private class LazyGridAdapter(private val state: LazyGridState) : ScrollbarAdapter {
    /** Cells in a line. */
    private val perLine: Int
        get() = state.layoutInfo.visibleItemsInfo.groupingBy { it.row }.eachCount().values.maxOrNull() ?: 1

    /** A line's average height and the gap after it. */
    private val step: Float
        get() {
            val info = state.layoutInfo
            val lines = info.visibleItemsInfo.groupBy { it.row }.values
            if (lines.isEmpty()) return 0f
            return lines.sumOf { line -> line.maxOf { it.size.height } }.toFloat() / lines.size + info.mainAxisItemSpacing
        }
    private val lineCount: Int
        get() {
            val per = perLine
            return (state.layoutInfo.totalItemsCount + per - 1) / per
        }
    override val contentLength: Float
        get() {
            val info = state.layoutInfo
            return info.beforeContentPadding + lineCount * step - info.mainAxisItemSpacing + info.afterContentPadding
        }
    override val viewportLength: Float get() = state.layoutInfo.viewportSize.height.toFloat()
    override val offset: Float get() = state.firstVisibleItemIndex / perLine * step + state.firstVisibleItemScrollOffset
    override val insetStart: Int get() = state.layoutInfo.beforeContentPadding
    override val insetEnd: Int get() = state.layoutInfo.afterContentPadding
    override val scrolling: Boolean get() = state.isScrollInProgress

    override fun scrollTo(to: Float) {
        val step = step
        if (step <= 0f) return
        val line = (to / step).toInt().coerceIn(0, (lineCount - 1).coerceAtLeast(0))
        state.requestScrollToItem(line * perLine, (to - line * step).toInt().coerceAtLeast(0))
    }
}

private class ScrollStateAdapter(private val state: ScrollState) : ScrollbarAdapter {
    override val contentLength: Float get() = (state.viewportSize + state.maxValue).toFloat()
    override val viewportLength: Float get() = state.viewportSize.toFloat()
    override val offset: Float get() = state.value.toFloat()
    override val insetStart: Int get() = 0
    override val insetEnd: Int get() = 0
    override val scrolling: Boolean get() = state.isScrollInProgress

    override fun scrollTo(to: Float) {
        state.dispatchRawDelta(to.coerceIn(0f, state.maxValue.toFloat()) - state.value)
    }
}

/**
 * 1.0.8 (Desktop Kit 2.8.0): a scrollbar along the end edge of this list, for a mouse or a touchpad (DeX, a keyboard
 * cover): a thin thumb shows where the view is and how much of the content it holds; it shows while the list scrolls
 * and while the pointer is over the list, fades out after it, and grows under the pointer, where it can be dragged; a
 * press on the track jumps there. On touch there is none (a finger scrolls the content itself). A press the scrollbar
 * took is neither a row's click, a selection frame nor a drag. Put it on the list itself; [DenseTable] carries one.
 */
@Composable
fun Modifier.verticalScrollbar(state: LazyListState): Modifier = scrollbar(remember(state) { LazyListAdapter(state) })

/** [verticalScrollbar] for a grid. */
@Composable
fun Modifier.verticalScrollbar(state: LazyGridState): Modifier = scrollbar(remember(state) { LazyGridAdapter(state) })

/** [verticalScrollbar] for a scrolling column (`Modifier.verticalScroll(state)`); put it before `verticalScroll`. */
@Composable
fun Modifier.verticalScrollbar(state: ScrollState): Modifier = scrollbar(remember(state) { ScrollStateAdapter(state) })

/** How long the scrollbar stays after the list stopped and the pointer left. */
private const val LINGER_MS = 900L

@Composable
private fun Modifier.scrollbar(adapter: ScrollbarAdapter): Modifier {
    if (NagTheme.inputMode != InputMode.Pointer) return this
    val colors = NagTheme.colors
    val tier = LocalMotionTier.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val bar = remember { ScrollbarState() }
    val scrolling = adapter.scrolling
    LaunchedEffect(scrolling, bar.hoverList, bar.dragging) {
        if (scrolling || bar.hoverList || bar.dragging) {
            bar.lingering = true
        } else {
            delay(LINGER_MS)
            bar.lingering = false
        }
    }
    val alpha by animateFloatAsState(if (bar.lingering) 1f else 0f, NagMotion.fade(tier, tween(NagMotion.MEDIUM)), label = "scrollbar")
    val thick by animateFloatAsState(if (bar.hoverBar || bar.dragging) 1f else 0f, NagMotion.fade(tier, tween(NagMotion.SHORT)), label = "scrollbarWidth")
    val thumb = colors.textTertiary
    return this
        .pointerInput(adapter, rtl) {
            val strip = 12.dp.toPx()
            fun inStrip(x: Float) = if (rtl) x <= strip else x >= size.width - strip
            fun track(): Pair<Float, Float> = adapter.insetStart.toFloat() to (size.height - adapter.insetEnd).toFloat()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    if (change.type != PointerType.Mouse) continue
                    when (event.type) {
                        PointerEventType.Enter, PointerEventType.Move -> {
                            bar.hoverList = true
                            bar.hoverBar = inStrip(change.position.x) && adapter.shown < 1f
                        }
                        PointerEventType.Exit -> {
                            bar.hoverList = false
                            bar.hoverBar = false
                        }
                        PointerEventType.Press -> if (event.buttons.isPrimaryPressed && inStrip(change.position.x) && adapter.shown < 1f) {
                            // the thumb (or the track: the view jumps there) follows the pointer
                            change.consume()
                            bar.dragging = true
                            val (top, bottom) = track()
                            val length = (bottom - top).coerceAtLeast(1f)
                            val thumbLength = (length * adapter.shown).coerceAtLeast(24.dp.toPx()).coerceAtMost(length)
                            val thumbTop = top + (length - thumbLength) * adapter.position
                            val grab = if (change.position.y in thumbTop..(thumbTop + thumbLength)) change.position.y - thumbTop else thumbLength / 2
                            fun follow(y: Float) {
                                val free = (length - thumbLength).coerceAtLeast(1f)
                                val range = (adapter.contentLength - adapter.viewportLength).coerceAtLeast(0f)
                                adapter.scrollTo(((y - grab - top) / free).coerceIn(0f, 1f) * range)
                            }
                            follow(change.position.y)
                            try {
                                while (true) {
                                    val move = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == change.id } ?: break
                                    move.consume()
                                    if (!move.pressed) break
                                    follow(move.position.y)
                                }
                            } finally {
                                bar.dragging = false
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            val shown = adapter.shown
            if (alpha <= 0f || shown >= 1f) return@drawWithContent
            val top = adapter.insetStart.toFloat()
            val bottom = size.height - adapter.insetEnd
            val length = (bottom - top).coerceAtLeast(0f)
            if (length <= 0f) return@drawWithContent
            val thumbLength = (length * shown).coerceAtLeast(24.dp.toPx()).coerceAtMost(length)
            val y = top + (length - thumbLength) * adapter.position
            val width = (4.dp + 4.dp * thick).toPx()
            val margin = 2.dp.toPx()
            val x = if (rtl) margin else size.width - margin - width
            drawRoundRect(
                color = thumb.copy(alpha = thumb.alpha * alpha * (0.55f + 0.3f * thick)),
                topLeft = Offset(x, y),
                size = Size(width, thumbLength),
                cornerRadius = CornerRadius(width / 2),
            )
        }
}

/** What the pointer is doing with a scrollbar. */
@Stable
private class ScrollbarState {
    var hoverList by mutableStateOf(false)
    var hoverBar by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var lingering by mutableStateOf(false)
}
