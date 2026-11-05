package app.nagmedar.ui.components

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.input.consumePointerUntilAllUp
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme

/** Visual state of an interactive element: hover, press and keyboard focus. */
data class InteractionState(val hovered: Boolean, val pressed: Boolean, val focused: Boolean)

@Composable
fun InteractionSource.collectState(): InteractionState {
    val hovered by collectIsHoveredAsState()
    val pressed by collectIsPressedAsState()
    val focused by collectIsFocusedAsState()
    return InteractionState(hovered, pressed, focused)
}

/**
 * Background for a desktop-style control: pressed > selected > hovered > [rest]. By touch it eases
 * from one state to the next over [app.nagmedar.ui.theme.NagMotion.COLOR] instead of jumping
 * (Desktop Kit 0.2.0). With a mouse (Desktop Kit 0.2.1) hover and press change it in the frame they
 * happen, as on a desktop: only what is under the pointer is lit, nothing trails behind it; a change
 * of [selected] (a click, a key) still eases. A fade to or from a clear colour changes only the
 * opacity, never passing through grey. With "Remove animations" it changes at once.
 */
@Composable
fun stateBackground(state: InteractionState, selected: Boolean = false, rest: Color = Color.Transparent): Color {
    val colors = NagTheme.colors
    val target = when {
        state.pressed -> colors.pressed
        selected -> colors.selection
        state.hovered -> colors.hover
        else -> rest
    }
    return animateInteractionColor(target, selected, label = "stateBackground").value
}

/** A control's [target] colour (its fill, text or icon) easing like [stateBackground] does by touch. */
@Composable
fun animateStateColor(target: Color, label: String = "stateColor"): Color =
    animateInteractionColor(target, instant = false, label = label).value

/**
 * The colour of a control's hover and press feedback that computes its own [target] (a button's
 * fill, the navigation's pill), as [stateBackground] moves (Desktop Kit 0.2.1): at once with a mouse
 * unless [selected] changed, easing in [spec] otherwise; a fade to or from a clear colour changes
 * only the opacity (an Oklab fade toward clear black would pass through grey). [instant] says
 * whether the window's pointer changes it at once; false eases every change, as for a colour that
 * follows a state rather than the pointer.
 */
@Composable
fun animateInteractionColor(
    target: Color,
    selected: Boolean = false,
    spec: FiniteAnimationSpec<Color> = NagTheme.motion.color(),
    instant: Boolean = NagTheme.inputMode == InputMode.Pointer,
    label: String = "interactionColor",
): State<Color> {
    val fade = remember { InteractionFade(target, selected, label) }
    fade.retarget(target, selected, instant)
    val current = rememberUpdatedState(target)
    val tier = LocalMotionTier.current
    LaunchedEffect(fade, target) { fade.run(target, NagMotion.fade(tier, spec)) }
    return if (fade.easing) fade.color.asState() else current
}

/** The colour of [animateInteractionColor]: where it is and whether its latest change eases. */
private class InteractionFade(initial: Color, private var selected: Boolean, label: String) {
    val color = Animatable(initial, Color.VectorConverter(initial.colorSpace), label = label)
    private var target = initial

    /** Whether the latest change eases; when false the caller gets the target itself, with no frame in between. */
    var easing = false
        private set

    fun retarget(newTarget: Color, newSelected: Boolean, instant: Boolean) {
        if (newTarget == target && newSelected == selected) return
        easing = !instant || newSelected != selected
        target = newTarget
        selected = newSelected
    }

    suspend fun run(to: Color, spec: AnimationSpec<Color>) {
        if (!easing) {
            color.snapTo(to)
            return
        }
        val from = color.value
        // from or to a clear colour only the opacity changes: the hue stays the visible one's
        if (from.alpha == 0f && to.alpha > 0f) color.snapTo(to.copy(alpha = 0f))
        color.animateTo(if (to.alpha == 0f) from.copy(alpha = 0f) else to, spec)
        color.snapTo(to)
    }
}

/**
 * Touch feedback in place of a ripple: the element shrinks while a finger holds it and springs back
 * on release (Desktop Kit 0.2.0). By touch only: with a mouse nothing moves, as on a desktop. On a
 * phone (Desktop Kit 2.3) the press goes three quarters as deep as [scale] (the finger covers the
 * control: the shrink is felt more than seen) and letting go bounces back a touch over the rest;
 * wider windows keep [scale] and a plain spring both ways. Drawn in the layer, so the press does not
 * recompose what it scales.
 */
@Composable
fun Modifier.pressScale(pressed: Boolean, scale: Float = 0.96f): Modifier {
    val touch = NagTheme.inputMode == InputMode.Touch
    val tier = LocalMotionTier.current
    val phone = tier == MotionTier.Gesture && !NagTheme.motion.reduced
    val target = when {
        !pressed || !touch -> 1f
        phone -> phonePressScale(scale)
        else -> scale
    }
    val spec = if (phone && !pressed) PressRelease else NagTheme.motion.fastSpatial<Float>()
    val value = animateFloatAsState(target, spec, label = "pressScale")
    // with a pointer nothing scales: no layer either
    if (!touch) return this
    return graphicsLayer {
        scaleX = value.value
        scaleY = value.value
    }
}

/** How deep a phone's press goes for a control asking for [scale]: three quarters of the way (0.96 → 0.97). */
internal fun phonePressScale(scale: Float): Float = 1f - (1f - scale) * 0.75f

/** A phone's press let go: back to the rest with a light bounce over it, settled within a thousandth. */
private val PressRelease = spring(dampingRatio = 0.75f, stiffness = 1200f, visibilityThreshold = 0.001f)

/**
 * Click handling shared by all Nağmedar controls: hand cursor, hover tracking, no ripple
 * (the background states above replace it), keyboard focus with a visible ring.
 */
fun Modifier.nagClickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    /**
     * A second action on double click (play a selected item); a single click acts at once. From
     * the keyboard (S-407) Enter does it, as on desktops, and Space does the click (selects).
     */
    onDoubleClick: (() -> Unit)? = null,
): Modifier = this
    .pointerHoverIcon(PointerIcon.Hand)
    .hoverable(interactionSource, enabled)
    .then(if (enabled) Modifier.ignoreSecondaryButtons() else Modifier)
    .then(
        if (onDoubleClick == null) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = role,
                onClick = onClick,
            )
        } else {
            Modifier.doubleClickable(interactionSource, enabled, onClickLabel, role, onClick, onDoubleClick)
        },
    )

/**
 * A press of a mouse button other than the primary one (right, middle, back, forward) is consumed
 * before the click handler below sees it, so a right click never acts as a left click, wherever
 * the element sits: a [app.nagmedar.ui.input.ContextMenuArea] around it still opens its menu, as
 * it reads the press first (device test, 22 Oct).
 */
fun Modifier.ignoreSecondaryButtons(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val buttons = currentEvent.buttons
        if (down.type == PointerType.Mouse && !buttons.isPrimaryPressed &&
            (buttons.isSecondaryPressed || buttons.isTertiaryPressed || buttons.isBackPressed || buttons.isForwardPressed)
        ) {
            down.consume()
            consumePointerUntilAllUp()
        }
    }
}

/**
 * The keys of an item in a list (S-407), as desktops have them: Enter (or the number pad's) opens
 * it, Space selects it (with Ctrl it adds or removes it, with Shift it takes the range), each when
 * released. True when the key was one of them, so the player's Space does not play or pause too.
 */
private fun listKeys(event: KeyEvent, clickModifiers: ClickModifiers, select: () -> Unit, open: () -> Unit): Boolean {
    when (event.key) {
        Key.Enter, Key.NumPadEnter -> if (event.type == KeyEventType.KeyUp) open()
        Key.Spacebar -> if (event.type == KeyEventType.KeyUp) {
            clickModifiers.pressed(ctrl = event.isCtrlPressed || event.isMetaPressed, shift = event.isShiftPressed)
            select()
        }
        else -> return false
    }
    return true
}

/**
 * A click that acts at once and a second click within the double-tap time that opens: desktop
 * lists select on the first click and do not wait to see whether a second one comes.
 */
private fun Modifier.doubleClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    onClickLabel: String?,
    role: Role?,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
): Modifier = composed {
    val last = remember { longArrayOf(0L) }
    val clickModifiers = LocalClickModifiers.current
    (if (enabled) Modifier.onPreviewKeyEvent { event -> listKeys(event, clickModifiers, onClick, onDoubleClick) } else Modifier).clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
    ) {
        val now = SystemClock.uptimeMillis()
        if (last[0] != 0L && now - last[0] <= ViewConfiguration.getDoubleTapTimeout()) {
            last[0] = 0L
            onDoubleClick()
        } else {
            last[0] = now
            onClick()
        }
    }
}

/** 2 dp focus ring in the accent colour, drawn only while the element has keyboard focus. */
@Composable
fun Modifier.focusRing(state: InteractionState, shape: Shape): Modifier =
    if (state.focused) this.border(2.dp, NagTheme.colors.focusRing, shape) else this

/**
 * Tab and Shift+Tab take the keyboard from a one-line text field on to the next or the previous
 * control, as they do from any other one (S-1403): typed in, Compose's text field keeps them.
 */
fun Modifier.tabMovesFocus(focusManager: FocusManager): Modifier = onPreviewKeyEvent { event ->
    if (event.key != Key.Tab || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
    true
}
