package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.mixSrgb

/**
 * S-1601: a card that can be pressed: a surface with an edge, the hover, press and selected
 * backgrounds of the other controls and the keyboard's focus ring. [content] gets the card's
 * state, so a part of it can appear while the pointer rests on the card or the keyboard is in it.
 */
@Composable
fun NagCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: Shape = RoundedCornerShape(NagTheme.dimens.radiusCard),
    onClickLabel: String? = null,
    content: @Composable BoxScope.(InteractionState) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    Box(
        modifier
            .then(if (state.focused) Modifier else Modifier.border(1.dp, colors.border, shape))
            .focusRing(state, shape)
            .background(stateBackground(state, selected, rest = colors.surface), shape)
            .semantics { this.selected = selected }
            .nagClickable(interaction, onClick, role = Role.Button, onClickLabel = onClickLabel),
    ) { content(state) }
}

/**
 * S-1601: a tile of colour for what has no picture: a gradient between two of the theme's hues,
 * chosen by [seed], so one name always has the same tile. [content] (an initial, an icon) sits on it.
 */
@Composable
fun GradientTile(seed: String, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val colors = NagTheme.colors
    val hues = listOf(colors.brandBlue, colors.brandCoral, colors.success, colors.warning, colors.accent)
    val hash = seed.hashCode()
    val first = Math.floorMod(hash, hues.size)
    val second = (first + 1 + Math.floorMod(hash / hues.size, hues.size - 1)) % hues.size
    // paled towards the surface, so that an icon of the text colour reads on either theme
    val brush = Brush.linearGradient(listOf(mixSrgb(hues[first], colors.surfaceRaised, 0.4f), mixSrgb(hues[second], colors.surfaceRaised, 0.25f)))
    Box(modifier.background(brush), contentAlignment = Alignment.Center) { content() }
}
