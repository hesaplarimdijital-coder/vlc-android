package app.nagmedar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** How far a swipe goes before letting go removes, as a part of the item's width. */
private const val REMOVE_AT = 0.35f

/** A fling toward the start this fast (px/s) removes however short it was. */
private const val REMOVE_FLING = 1_800f

/**
 * Swipe to remove (S-310): by touch, the item dragged toward the start (left, right in a
 * right-to-left language) shows "[label]" on the danger colour under it, and goes when let go
 * beyond a third of its width or flung; otherwise it comes back. A mouse does not swipe: with a
 * pointer the item's menu and the Delete key remove. Screen readers have [label] as an action.
 */
@Composable
fun SwipeToRemove(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether the swipe is on: by touch. The action for screen readers stays either way. */
    swipe: Boolean = true,
    /** The item's other actions for screen readers. */
    actions: List<CustomAccessibilityAction> = emptyList(),
    /** What the item is drawn on: it slides over the danger colour as an opaque row. */
    color: Color = NagTheme.colors.background,
    content: @Composable () -> Unit,
) {
    val colors = NagTheme.colors
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val currentOnRemove by rememberUpdatedState(onRemove)
    val motion by rememberUpdatedState(NagTheme.motion)
    // toward the start: left, or right in a right-to-left language
    val toStart = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    Box(
        modifier
            .onSizeChanged { width = it.width }
            .clipToBounds()
            .semantics {
                customActions = actions + CustomAccessibilityAction(label) { currentOnRemove(); true }
            },
    ) {
        if (offset.value != 0f) {
            // under the item, what letting go does
            val onDanger = if (colors.isDark) colors.background else Color.White
            Row(
                Modifier.matchParentSize().background(colors.danger).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(NagIcons.Delete, contentDescription = null, tint = onDanger, modifier = Modifier.size(NagTheme.dimens.icon))
                Spacer(Modifier.width(8.dp))
                Text(label, style = NagTheme.type.label, color = onDanger, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(color)
                .pointerInput(swipe, toStart) {
                    if (!swipe) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // a mouse drags files and bands, not swipes
                        if (down.type != PointerType.Touch) return@awaitEachGesture
                        val velocity = VelocityTracker()
                        velocity.addPointerInputChange(down)
                        // only a move toward the start is a swipe; the list scrolls the rest
                        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            if (over * toStart > 0) change.consume()
                        } ?: return@awaitEachGesture
                        // where the finger has the item; the offset follows it a frame later
                        var position = offset.value
                        fun follow(dx: Float) {
                            position = (position + dx).let { if (toStart < 0) it.coerceIn(-width.toFloat(), 0f) else it.coerceIn(0f, width.toFloat()) }
                            val to = position
                            scope.launch { offset.snapTo(to) }
                        }
                        follow(drag.positionChange().x)
                        val completed = horizontalDrag(drag.id) { change ->
                            velocity.addPointerInputChange(change)
                            follow(change.positionChange().x)
                            change.consume()
                        }
                        val speed = velocity.calculateVelocity().x * toStart
                        val far = abs(position) > width * REMOVE_AT
                        scope.launch {
                            if (completed && width > 0 && (far || speed > REMOVE_FLING)) {
                                // it leaves on the emphasized accelerate curve
                                offset.animateTo(toStart * width, motion.emphasizedExit())
                                currentOnRemove()
                                // a list that keeps the item (nothing was removed) shows it again
                                offset.snapTo(0f)
                            } else {
                                offset.animateTo(0f, motion.spatial())
                            }
                        }
                    }
                },
        ) {
            content()
        }
    }
}
