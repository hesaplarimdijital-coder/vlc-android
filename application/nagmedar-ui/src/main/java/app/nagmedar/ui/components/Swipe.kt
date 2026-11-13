package app.nagmedar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.rememberNagHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** How far a swipe goes before letting go removes, as a part of the item's width. */
private const val REMOVE_AT = 0.35f

/** 1.0.8 (Desktop Kit 2.8.0): a fling toward the start this fast (dp/s), half way to [REMOVE_AT], removes. */
private const val REMOVE_FLING_DP = 1_000f

/** 1.0.8: an item that should have gone comes back after this, if the list still has it. */
private const val STILL_THERE_MS = 1_000L

/**
 * Swipe to remove (S-310): by touch, the item dragged toward the start (left, right in a
 * right-to-left language) uncovers "[label]" under it, and goes when let go beyond a third of its
 * width or flung; otherwise it comes back. A mouse does not swipe: with a pointer the item's menu
 * and the Delete key remove. Screen readers have [label] as an action.
 *
 * 1.0.8 (Desktop Kit 2.8.0's swipe actions): what lies under the item says when letting go removes: past the third it
 * takes the danger colour, its icon grows and the phone ticks (and ticks again coming back short of it). A removed
 * item stays out for a second: the list takes it away meanwhile (the queue's removal goes through the player), and one
 * the list still has comes back then.
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
    /**
     * 1.0.8: what changes once the list has taken the item away (the list itself). A row still here when it changes is
     * another item in its place (the queue's second copy of a track takes the first one's key): it comes back at once.
     */
    stillThere: Any? = null,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val currentOnRemove by rememberUpdatedState(onRemove)
    val motion by rememberUpdatedState(NagTheme.motion)
    val haptics = rememberNagHaptics()
    val flingPx = with(LocalDensity.current) { REMOVE_FLING_DP.dp.toPx() }
    // 1.0.8: past the third: letting go removes
    var armed by remember { mutableStateOf(false) }
    // 1.0.8: swiped away, until the row comes back (the list kept it, or another item took its place)
    var removing by remember { mutableStateOf(false) }
    LaunchedEffect(stillThere) {
        if (removing) {
            removing = false
            armed = false
            offset.snapTo(0f)
        }
    }
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
        // under the item, what letting go does
        if (offset.value != 0f) RemoveBackdrop(label, armed, Modifier.matchParentSize())
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
                            // 1.0.8: the third passed or come back from: the colour and a tick say so
                            val nowArmed = width > 0 && abs(to) >= width * REMOVE_AT
                            if (nowArmed != armed) {
                                armed = nowArmed
                                if (nowArmed) haptics.threshold() else haptics.tick()
                            }
                        }
                        follow(drag.positionChange().x)
                        val completed = horizontalDrag(drag.id) { change ->
                            velocity.addPointerInputChange(change)
                            follow(change.positionChange().x)
                            change.consume()
                        }
                        val speed = velocity.calculateVelocity().x * toStart
                        val far = abs(position) > width * REMOVE_AT
                        val flung = speed > flingPx && abs(position) > width * REMOVE_AT / 2
                        scope.launch {
                            if (completed && width > 0 && (far || flung)) {
                                if (!armed) {
                                    armed = true
                                    haptics.threshold()
                                }
                                // it leaves on the emphasized accelerate curve, what lies under it still saying it goes
                                offset.animateTo(toStart * width, motion.emphasizedExit())
                                removing = true
                                currentOnRemove()
                                // a list that keeps the item (nothing was removed) shows it again, once the list had its time
                                delay(STILL_THERE_MS)
                                if (removing) {
                                    removing = false
                                    armed = false
                                    offset.snapTo(0f)
                                }
                            } else {
                                armed = false
                                offset.animateTo(0f, motion.spatial(NagMotion.PIXEL))
                            }
                        }
                    }
                },
        ) {
            content()
        }
    }
}

/**
 * What lies under an item being swiped away (1.0.8, Desktop Kit 2.8.0): "[label]" with the bin, quiet short of the third,
 * on the danger colour past it with the icon grown.
 */
@Composable
private fun RemoveBackdrop(label: String, armed: Boolean, modifier: Modifier) {
    val colors = NagTheme.colors
    val tier = LocalMotionTier.current
    val onDanger = if (colors.isDark) colors.background else Color.White
    val fill by animateColorAsState(if (armed) colors.danger else colors.surfaceRaised, NagMotion.fade(tier, tween(NagMotion.SHORT)), label = "swipeFill")
    val ink = if (armed) onDanger else colors.textSecondary
    val grow by animateFloatAsState(if (armed) 1.2f else 1f, NagMotion.fade(tier, NagTheme.motion.fastSpatial()), label = "swipeIcon")
    Row(
        modifier.background(fill).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            NagIcons.Delete,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(NagTheme.dimens.icon).graphicsLayer {
                scaleX = grow
                scaleY = grow
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = NagTheme.type.label, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
