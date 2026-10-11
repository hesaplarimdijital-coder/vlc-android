package app.nagmedar.ui.components

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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

/** Background for a desktop-style control: pressed > selected > hovered > [rest]. */
@Composable
fun stateBackground(state: InteractionState, selected: Boolean = false, rest: Color = Color.Transparent): Color {
    val colors = NagTheme.colors
    return when {
        state.pressed -> colors.pressed
        selected -> colors.selection
        state.hovered -> colors.hover
        else -> rest
    }
}

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
