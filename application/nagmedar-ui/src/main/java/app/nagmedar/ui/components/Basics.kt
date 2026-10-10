package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme

/** Square icon button with hover, press and focus states. Its size never drops below the touch or pointer target. */
@Composable
fun NagIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = NagTheme.dimens.minTarget.coerceAtLeast(NagTheme.dimens.controlHeight),
    iconSize: Dp = NagTheme.dimens.icon,
    tint: Color = if (selected) NagTheme.colors.onSelection else NagTheme.colors.textSecondary,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Box(
        modifier = modifier
            .size(size)
            .focusRing(state, shape)
            .background(stateBackground(state, selected), shape)
            .nagClickable(interaction, onClick, enabled = enabled, onClickLabel = contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Text button. [primary] fills it with the accent colour. */
@Composable
fun NagButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val background = when {
        primary && state.pressed -> colors.accent.copy(alpha = 0.85f)
        primary -> colors.accent
        else -> stateBackground(state, rest = colors.surfaceRaised)
    }
    val content = if (primary) colors.onAccent else colors.text
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
            .focusRing(state, shape)
            .background(background, shape)
            .then(if (primary) Modifier else Modifier.border(1.dp, colors.borderStrong, shape))
            .nagClickable(interaction, onClick, enabled = enabled, role = Role.Button)
            .padding(horizontal = dimens.gapM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(dimens.icon))
            Spacer(Modifier.width(dimens.gapS))
        }
        Text(text, style = NagTheme.type.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small uppercase-free section caption used in the sidebar and settings. */
@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = NagTheme.type.labelSmall,
        color = NagTheme.colors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(NagTheme.colors.border))
}

@Composable
fun VerticalRule(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).background(NagTheme.colors.border))
}
