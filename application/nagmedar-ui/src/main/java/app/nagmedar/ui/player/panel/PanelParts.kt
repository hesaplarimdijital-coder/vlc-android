package app.nagmedar.ui.player.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/*
 * The building blocks of the player panel's tabs (S-603, S-604), after the mockups' panels:
 * sections with capitalised names, rules between them, rows of a name and a control, faded
 * controls with a note while the player cannot do them yet.
 */

/** How much a control fades while the player cannot do what it sets yet. */
internal const val PanelDisabledAlpha = 0.45f

@Composable
internal fun PanelSection(spacing: Dp = 2.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

/** A section's name in capitals, with what acts on the whole section at its end. */
@Composable
internal fun SectionTitle(text: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(currentLocale()),
            style = NagTheme.type.caption.copy(letterSpacing = 0.08.em),
            color = NagTheme.colors.textTertiary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
internal fun PanelRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(NagTheme.colors.border))
}

@Composable
internal fun PanelCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary, modifier = modifier)
}

/** Under faded controls: they wait for the native side of the player. */
@Composable
internal fun PendingNote(tag: String, text: String = stringResource(R.string.nag_panel_pending)) {
    PanelCaption(text, Modifier.padding(top = 2.dp).testTag(tag))
}

/** A text button in the accent colour: "Reset". */
@Composable
internal fun TextAction(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    Box(
        modifier
            .heightIn(min = 24.dp)
            .background(stateBackground(state), RoundedCornerShape(5.dp))
            .nagClickable(interaction, onClick, enabled = enabled)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NagTheme.type.label, color = if (enabled) colors.accentText else colors.textTertiary)
    }
}

/** A small rounded label: "GPU load: medium", "this file". */
@Composable
internal fun PanelBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = NagTheme.type.labelSmall,
        color = NagTheme.colors.textSecondary,
        maxLines = 1,
        modifier = modifier
            .background(NagTheme.colors.surfaceRaised, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** A chip of a group where one is chosen: the aspect ratios. */
@Composable
internal fun PanelChip(text: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .heightIn(min = 28.dp)
            .alpha(if (enabled) 1f else PanelDisabledAlpha)
            .border(1.dp, if (selected) colors.accent.copy(alpha = 0.6f) else colors.borderStrong, shape)
            .background(if (selected) colors.accent.copy(alpha = 0.15f) else stateBackground(state), shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction, enabled)
            .selectable(selected = selected, interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NagTheme.type.labelSmall, color = if (selected) colors.text else colors.textSecondary, maxLines = 1)
    }
}

/** Choices side by side, one chosen, each as wide as the others: the subtitles' speeds, the secondary subtitles' place. */
@Composable
internal fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Figures ("1,000×", "25→23,976") in the monospaced type; words in the text's, on two lines where they must. */
    mono: Boolean = true,
) {
    val colors = NagTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else PanelDisabledAlpha)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            val interaction = remember { MutableInteractionSource() }
            val state = interaction.collectState()
            val chosen = value == selected
            val shape = RoundedCornerShape(7.dp)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 28.dp)
                    .border(1.dp, if (chosen) colors.accent.copy(alpha = 0.6f) else colors.borderStrong, shape)
                    .background(if (chosen) colors.accent.copy(alpha = 0.15f) else stateBackground(state), shape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .hoverable(interaction, enabled)
                    .selectable(selected = chosen, interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton) {
                        onSelect(value)
                    }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = if (mono) NagTheme.type.mono else NagTheme.type.labelSmall,
                    color = if (chosen) colors.text else colors.textSecondary,
                    maxLines = if (mono) 1 else 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = if (mono) 0.dp else 3.dp),
                )
            }
        }
    }
}

/** An on/off setting: its name (and what it does) with the switch at the end; the whole row toggles it. */
@Composable
internal fun SwitchRow(title: String, hint: String?, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .alpha(if (enabled) 1f else PanelDisabledAlpha)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction, enabled)
            .toggleable(checked, interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NagTheme.type.label, color = colors.text)
            if (hint != null) Text(hint, style = NagTheme.type.labelSmall, color = colors.textTertiary)
        }
        Box(
            Modifier
                .size(width = 38.dp, height = 24.dp)
                .background(if (checked) colors.accent else colors.borderStrong, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(start = if (checked) 18.dp else 4.dp)
                    .size(16.dp)
                    .background(if (checked) colors.onAccent else colors.textSecondary, RoundedCornerShape(8.dp)),
            )
        }
    }
}

/** A name on the left, its control taking the rest of the row. */
@Composable
internal fun LabelledRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary, maxLines = 2, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f)) { control() }
    }
}

/** A button showing a list's choice, which opens the list. */
@Composable
internal fun DropButton(text: String, enabled: Boolean, label: String, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(7.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .alpha(if (enabled) 1f else PanelDisabledAlpha)
            .border(1.dp, colors.borderStrong, shape)
            .background(stateBackground(state, rest = colors.background), shape)
            .nagClickable(interaction, onClick, enabled = enabled, onClickLabel = label)
            .semantics {
                contentDescription = label
                stateDescription = text
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = NagTheme.type.labelSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(NagIcons.ChevronDown, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(12.dp))
    }
}

/** "+6", "0", "−20": the plus and the minus sign of the typography rules. */
internal fun signed(value: Long, locale: Locale): String {
    val number = NumberFormat.getIntegerInstance(locale).format(abs(value))
    return when {
        value > 0 -> "+$number"
        value < 0 -> "−$number"
        else -> number
    }
}

internal fun signed(value: Int, locale: Locale): String = signed(value.toLong(), locale)

/** "1,06" in Turkish, "1.06" in English, with [digits] decimals. */
internal fun decimal(value: Double, digits: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(value)
