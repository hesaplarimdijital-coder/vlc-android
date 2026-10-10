package app.nagmedar.ui.components

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
import androidx.compose.ui.Modifier
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
): Modifier = this
    .pointerHoverIcon(PointerIcon.Hand)
    .hoverable(interactionSource, enabled)
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )

/** 2 dp focus ring in the accent colour, drawn only while the element has keyboard focus. */
@Composable
fun Modifier.focusRing(state: InteractionState, shape: Shape): Modifier =
    if (state.focused) this.border(2.dp, NagTheme.colors.focusRing, shape) else this
