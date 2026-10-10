package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme

/** Row of mutually exclusive options (theme, density, view mode). */
@Composable
fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val outer = RoundedCornerShape(dimens.radiusControl + 2.dp)
    Row(
        modifier = modifier
            .background(colors.surface, outer)
            .border(1.dp, colors.border, outer)
            .padding(2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val interaction = remember { MutableInteractionSource() }
            val state = interaction.collectState()
            val isSelected = value == selected
            val shape = RoundedCornerShape(dimens.radiusControl)
            Box(
                modifier = Modifier
                    .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
                    .focusRing(state, shape)
                    .background(stateBackground(state, isSelected), shape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .selectable(selected = isSelected, interactionSource = interaction, indication = null, role = Role.RadioButton) {
                        onSelect(value)
                    }
                    .padding(horizontal = dimens.gapM),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = NagTheme.type.label, color = if (isSelected) colors.onSelection else colors.textSecondary, maxLines = 1)
            }
        }
    }
}

/** Colour swatches for the accent choice. */
@Composable
fun <T> SwatchChoice(options: List<Triple<T, Color, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEach { (value, swatch, label) ->
            val interaction = remember { MutableInteractionSource() }
            val state = interaction.collectState()
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .size(NagTheme.dimens.minTarget.coerceAtLeast(32.dp))
                    .focusRing(state, CircleShape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .semantics { contentDescription = label }
                    .selectable(selected = isSelected, interactionSource = interaction, indication = null, role = Role.RadioButton) {
                        onSelect(value)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .background(swatch, CircleShape)
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.text else colors.borderStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(NagIcons.Check, contentDescription = null, tint = Color(0xFF0E1420), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** Label and control on one settings line. */
@Composable
fun SettingRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, control: @Composable () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Row(
        modifier = modifier.padding(vertical = dimens.gapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = NagTheme.type.bodyStrong, color = colors.text)
            if (subtitle != null) Text(subtitle, style = NagTheme.type.labelSmall, color = colors.textTertiary)
        }
        Spacer(Modifier.width(dimens.gapL))
        control()
    }
}
