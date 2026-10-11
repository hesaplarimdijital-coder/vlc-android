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
    /** A second action on double click (play a selected item); a single click then waits for it. */
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
    clickable(
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
