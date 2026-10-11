package app.nagmedar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.max
import kotlin.math.min

/**
 * Reordering a list by a handle (S-310): the handle (≡) grabbed, by touch or with the mouse, drags
 * its item at once; the items it passes make room, and let go it moves there, in one [onMove]
 * (from, to: indices in the list). Near the list's top and bottom edges the list scrolls. The list
 * gives its items [reorderItem], their handles [reorderHandle], and itself [reorderList].
 */
@Stable
class ReorderState internal constructor(val list: LazyListState) {
    /** The key of the item being dragged, null between drags. */
    var dragged: Any? by mutableStateOf(null)
        private set

    /** Where the dragged item came from. */
    var from by mutableIntStateOf(-1)
        private set

    /** Where the dragged item goes if let go now. */
    var target by mutableIntStateOf(-1)
        private set

    /** How far the dragged item is from its place, in px. */
    var offset by mutableFloatStateOf(0f)
        private set

    val dragging: Boolean get() = dragged != null

    internal var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }

    /** The dragged item's height, and its place in the list when grabbed. */
    private var size = 0
    private var start = 0

    /** What the list scrolled under the dragged item since it was grabbed. */
    private var scrolled = 0f

    /** The list's top in the window and its height, for scrolling at its edges. */
    internal var listTop = 0f
    internal var listHeight = 0

    /** The finger, in the list's coordinates. */
    internal var pointer = 0f

    internal fun start(key: Any): Boolean {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
        dragged = key
        from = item.index
        target = item.index
        size = item.size
        start = item.offset
        scrolled = 0f
        offset = 0f
        return true
    }

    internal fun drag(dy: Float) {
        offset += dy
        target = targetIndex()
    }

    /** The list scrolled [px] under the dragged item: it stays under the finger. */
    internal fun scrolledBy(px: Float) {
        scrolled += px
        offset += px
        target = targetIndex()
    }

    internal fun end() {
        val moved = from to target
        reset()
        if (moved.first >= 0 && moved.second >= 0 && moved.first != moved.second) onMove(moved.first, moved.second)
    }

    internal fun cancel() = reset()

    private fun reset() {
        dragged = null
        from = -1
        target = -1
        offset = 0f
    }

    /** How far the item at [index] moves aside for the dragged one, in px. */
    fun displacement(index: Int, key: Any): Float {
        if (dragged == null) return 0f
        return when {
            key == dragged -> offset
            index in (from + 1)..target -> -size.toFloat()
            index in target until from -> size.toFloat()
            else -> 0f
        }
    }

    /** The index the dragged item's middle is over, among the items laid out where they were. */
    private fun targetIndex(): Int {
        val middle = start - scrolled + offset + size / 2f
        var to = from
        for (item in list.layoutInfo.visibleItemsInfo) {
            if (item.key == dragged) continue
            val itemMiddle = item.offset + item.size / 2f
            if (item.index > from && middle > itemMiddle) to = max(to, item.index)
            if (item.index < from && middle < itemMiddle) to = min(to, item.index)
        }
        return to
    }
}

@Composable
fun rememberReorderState(list: LazyListState, onMove: (from: Int, to: Int) -> Unit): ReorderState {
    val state = remember(list) { ReorderState(list) }
    state.onMove = onMove
    return state
}

/** The list: where it is, and scrolling while an item is dragged near its edges. */
fun Modifier.reorderList(state: ReorderState): Modifier = composed {
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    val fastest = with(LocalDensity.current) { 20.dp.toPx() }
    LaunchedEffect(state, state.dragging) {
        while (state.dragging) {
            val speed = when {
                state.pointer < edge -> -fastest * ((edge - state.pointer) / edge).coerceIn(0f, 1f)
                state.pointer > state.listHeight - edge -> fastest * ((state.pointer - (state.listHeight - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) {
                val consumed = state.list.scrollBy(speed)
                if (consumed != 0f) state.scrolledBy(consumed)
            }
            withFrameNanos { }
        }
    }
    onGloballyPositioned { state.listTop = it.positionInRoot().y }.onSizeChanged { state.listHeight = it.height }
}

/**
 * An item of the list: the dragged one follows the finger above the others, which make room.
 * The dragged item stays composed when it scrolls out of sight, so the drag goes on.
 */
fun Modifier.reorderItem(state: ReorderState, key: Any, index: Int): Modifier = composed {
    val isDragged = state.dragged == key
    val aside by animateFloatAsState(
        targetValue = if (isDragged) 0f else state.displacement(index, key),
        animationSpec = if (state.dragging) tween(durationMillis = 150) else snap(),
        label = "reorder",
    )
    val pinnable = LocalPinnableContainer.current
    DisposableEffect(isDragged, pinnable) {
        val pinned = if (isDragged) pinnable?.pin() else null
        onDispose { pinned?.release() }
    }
    zIndex(if (isDragged) 1f else 0f).graphicsLayer {
        translationY = if (isDragged) state.offset else aside
        shadowElevation = if (isDragged) 8.dp.toPx() else 0f
    }
}

/** The handle of the item of [key]: grabbed by touch or with the mouse's main button, it drags the item. */
fun Modifier.reorderHandle(state: ReorderState, key: Any): Modifier = composed {
    val at = remember { arrayOfNulls<LayoutCoordinates>(1) }
    this
        .pointerHoverIcon(PointerIcon.Hand)
        .onGloballyPositioned { at[0] = it }
        .pointerInput(state, key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                // in the window's coordinates: the item moves under the finger as it follows it
                fun windowY(position: Offset): Float = at[0]?.takeIf { it.isAttached }?.localToRoot(position)?.y ?: position.y
                if (!state.start(key)) return@awaitEachGesture
                down.consume()
                var last = windowY(down.position)
                state.pointer = last - state.listTop
                var letGo = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) {
                            letGo = true
                            break
                        }
                        val y = windowY(change.position)
                        state.drag(y - last)
                        last = y
                        state.pointer = y - state.listTop
                    }
                } finally {
                    if (letGo) state.end() else state.cancel()
                }
            }
        }
}
