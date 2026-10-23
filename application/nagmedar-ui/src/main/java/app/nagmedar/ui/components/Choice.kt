package app.nagmedar.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme

/**
 * Row of mutually exclusive options (theme, density, view mode); [fill], the options share the
 * row's width equally (the player panel's). Not [enabled], it shows its choice faded and takes none.
 */
@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val outer = RoundedCornerShape(dimens.radiusControl + 2.dp)
    Row(
        modifier = modifier
            .then(if (enabled) Modifier else Modifier.alpha(0.45f))
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
                modifier = (if (fill) Modifier.weight(1f) else Modifier)
                    // by touch a 48 dp target either way (S-310)
                    .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
                    .widthIn(min = dimens.minTarget)
                    .pressScale(state.pressed)
                    .focusRing(state, shape)
                    .background(stateBackground(state, isSelected), shape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .selectable(selected = isSelected, interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton) {
                        onSelect(value)
                    }
                    .padding(horizontal = dimens.gapM),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = NagTheme.type.label,
                    color = animateStateColor(if (isSelected) colors.onSelection else colors.textSecondary, "segmentLabel"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                    .background(stateBackground(state), CircleShape) // hover and press: a halo around the swatch
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
                // a colour has no words: a resting mouse brings its name (S-404)
                HoverTooltip(state.hovered && !state.pressed, label)
            }
        }
    }
}

/**
 * Label and control on one settings line. When the control leaves the label less room than the
 * label needs, up to [MinLabelWidth] (a phone), the control goes under its label instead of
 * squeezing it; a short label keeps a wide control beside it (Desktop Kit 0.2.0).
 */
@Composable
fun SettingRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, control: @Composable () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Layout(
        modifier = modifier.padding(vertical = dimens.gapS),
        content = {
            Column {
                Text(title, style = NagTheme.type.bodyStrong, color = colors.text)
                if (subtitle != null) Text(subtitle, style = NagTheme.type.labelSmall, color = colors.textTertiary)
            }
            Box { control() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val control = measurables[1].measure(loose)
        val gap = dimens.gapL.roundToPx()
        val room = if (constraints.hasBoundedWidth) constraints.maxWidth - control.width - gap else Constraints.Infinity
        val needed = minOf(measurables[0].maxIntrinsicWidth(Constraints.Infinity), MinLabelWidth.roundToPx())
        if (room >= needed) {
            val label = measurables[0].measure(loose.copy(maxWidth = room))
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else label.width + gap + control.width
            val height = maxOf(label.height, control.height).coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                label.placeRelative(0, (height - label.height) / 2)
                control.placeRelative(width - control.width, (height - control.height) / 2)
            }
        } else {
            val label = measurables[0].measure(loose)
            val between = dimens.gapS.roundToPx()
            val width = maxOf(label.width, control.width).coerceIn(constraints.minWidth, constraints.maxWidth)
            layout(width, label.height + between + control.height) {
                label.placeRelative(0, 0)
                control.placeRelative(0, label.height + between)
            }
        }
    }
}

/** The narrowest a setting's label may get beside its control. */
private val MinLabelWidth = 160.dp

/** On/off switch with its label; the whole row toggles it (the states' "Resume automatically"). */
@Composable
fun NagSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        modifier = modifier
            .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction, enabled)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbStart by animateDpAsState(if (checked) 18.dp else 4.dp, NagTheme.motion.fastSpatial(), label = "switchThumb")
        Box(
            Modifier
                .size(width = 38.dp, height = 24.dp)
                .background(animateStateColor(if (checked) colors.accent else colors.borderStrong, "switchTrack"), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(start = thumbStart)
                    .size(16.dp)
                    .background(animateStateColor(if (checked) colors.onAccent else colors.textSecondary, "switchThumbColor"), CircleShape),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = NagTheme.type.body, color = if (enabled) colors.text else colors.textTertiary, maxLines = 2)
    }
}

/** A box to tick with its label; the whole row ticks it (S-603: "Save for this file"). */
@Composable
fun NagCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val box = RoundedCornerShape(3.dp)
    Row(
        modifier = modifier
            .heightIn(min = dimens.minTarget.coerceAtLeast(28.dp))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction, enabled)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .background(animateStateColor(if (checked) colors.accent else colors.accent.copy(alpha = 0f), "checkFill"), box)
                .border(1.5.dp, animateStateColor(if (checked) colors.accent else colors.textTertiary, "checkBorder"), box),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(NagIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = NagTheme.type.label, color = if (enabled) colors.text else colors.textTertiary, maxLines = 2)
    }
}
