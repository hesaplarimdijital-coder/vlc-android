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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme

/**
 * Row of mutually exclusive options (theme, density, view mode); [fill], the options share the
 * row's width equally (the player panel's). Not [enabled], it shows its choice faded and takes none.
 *
 * 1.0.1: options that do not fit the width side by side go on as many lines as they need
 * ([segmentLines]), each line filling the width, and a label longer than the whole width wraps
 * onto a second line: no option is ever cut (a phone at 320 dp, One UI's largest font).
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
    val gap = 2.dp
    Layout(
        modifier = modifier
            .then(if (enabled) Modifier else Modifier.alpha(0.45f))
            .background(colors.surface, outer)
            .border(1.dp, colors.border, outer)
            .padding(2.dp)
            .selectableGroup(),
        content = {
            options.forEach { (value, label) ->
                val interaction = remember { MutableInteractionSource() }
                val state = interaction.collectState()
                val isSelected = value == selected
                val shape = RoundedCornerShape(dimens.radiusControl)
                Box(
                    modifier = Modifier
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
                    // as the kit has it, no "…": the options take more lines instead, and a label wider than the whole
                    // width wraps within its option
                    Text(
                        label,
                        style = NagTheme.type.label,
                        color = animateStateColor(if (isSelected) colors.onSelection else colors.textSecondary, "segmentLabel"),
                        maxLines = 2,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val bounded = constraints.hasBoundedWidth
        val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val lines = if (bounded) segmentLines(natural, constraints.maxWidth, gapPx) else listOf(natural.indices)
        val single = lines.size == 1
        // one line keeps the options' own widths (or shares the width, [fill]); more lines each fill the width
        val stretch = bounded && (!single || fill)
        val widths = IntArray(measurables.size)
        for (line in lines) {
            val count = line.last - line.first + 1
            val room = if (bounded) constraints.maxWidth - gapPx * (count - 1) else 0
            when {
                !stretch -> line.forEach { widths[it] = minOf(natural[it], if (bounded) room else natural[it]) }
                // the width shared as a Row's weights share it: the pixels left over go to the first ones
                fill && single -> line.forEachIndexed { n, i -> widths[i] = room / count + if (n < room % count) 1 else 0 }
                else -> {
                    // the width left over goes to the options of the line evenly; one alone wider than the width takes it all
                    val used = line.sumOf { natural[it] }
                    val extra = (room - used).coerceAtLeast(0)
                    line.forEachIndexed { n, i -> widths[i] = minOf(natural[i], room) + extra / count + if (n < extra % count) 1 else 0 }
                }
            }
        }
        val heights = lines.map { line -> line.maxOf { measurables[it].maxIntrinsicHeight(widths[it]) } }
        val placeables = measurables.mapIndexed { i, m ->
            val h = heights[lines.indexOfFirst { i in it }]
            m.measure(Constraints.fixed(widths[i], h))
        }
        val lineWidths = lines.map { line -> line.sumOf { widths[it] } + gapPx * (line.last - line.first) }
        val width = (lineWidths.maxOrNull() ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (heights.sum() + gapPx * (lines.size - 1).coerceAtLeast(0)).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var y = 0
            lines.forEachIndexed { l, line ->
                var x = 0
                line.forEach { i ->
                    placeables[i].placeRelative(x, y)
                    x += widths[i] + gapPx
                }
                y += heights[l] + gapPx
            }
        }
    }
}

/**
 * The options of a [SegmentedChoice] by line (1.0.1): in order, all on one line when they fit in
 * [maxWidth] with [gap] between them. Otherwise on as few lines as they need, as even as they fit
 * (four options two by two rather than three and one); an option wider than [maxWidth] on a line
 * of its own.
 */
internal fun segmentLines(widths: List<Int>, maxWidth: Int, gap: Int): List<IntRange> {
    if (widths.isEmpty()) return emptyList()
    val greedy = mutableListOf<IntRange>()
    var start = 0
    var used = widths[0]
    for (i in 1 until widths.size) {
        if (used + gap + widths[i] <= maxWidth) {
            used += gap + widths[i]
        } else {
            greedy += start until i
            start = i
            used = widths[i]
        }
    }
    greedy += start until widths.size
    if (greedy.size == 1) return greedy
    // the same number of lines, the options shared out evenly, where each line still fits
    val perLine = (widths.size + greedy.size - 1) / greedy.size
    val even = widths.indices.chunked(perLine).map { it.first()..it.last() }
    val fits = even.size == greedy.size && even.all { line -> line.sumOf { widths[it] } + gap * (line.last - line.first) <= maxWidth }
    return if (fits) even else greedy
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
        // the thumb moves in the layout phase: its spring does not recompose the switch frame by frame
        val thumbStart = animateDpAsState(if (checked) 18.dp else 4.dp, NagTheme.motion.fastSpatial(), label = "switchThumb")
        Box(
            Modifier
                .size(width = 38.dp, height = 24.dp)
                .background(animateStateColor(if (checked) colors.accent else colors.borderStrong, "switchTrack"), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset { IntOffset(thumbStart.value.roundToPx(), 0) }
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
