package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme

/**
 * A filter that is on or off (S-801): "4K", "Dolby Vision", "İzlenmedi". On, it is filled and
 * ticked; screen readers hear a checkbox.
 */
@Composable
fun FilterChip(label: String, selected: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .heightIn(min = dimens.minTarget.coerceAtLeast(30.dp))
            .focusRing(state, shape)
            .background(if (selected) colors.selection else stateBackground(state), shape)
            .border(1.dp, if (selected) colors.accent else colors.borderStrong, shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .toggleable(value = selected, interactionSource = interaction, indication = null, role = Role.Checkbox, onValueChange = onToggle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(NagIcons.Check, contentDescription = null, tint = colors.onSelection, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, style = NagTheme.type.label, color = if (selected) colors.onSelection else colors.textSecondary, maxLines = 1)
    }
}

/** A row of chips that scrolls sideways when the window is narrow. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp), content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** "Sırala: Eklenme ▾": a button that opens a menu of [entries] under itself. */
@Composable
fun MenuButton(text: String, entries: () -> List<MenuEntry>, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val menu = rememberMenuState()
    var height by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(dimens.radiusControl)
    Box(modifier) {
        Row(
            modifier = Modifier
                .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
                .widthIn(min = dimens.minTarget)
                .onGloballyPositioned { height = it.size.height }
                .focusRing(state, shape)
                .background(stateBackground(state, menu.isOpen), shape)
                .border(1.dp, colors.borderStrong, shape)
                .nagClickable(interaction, { menu.open(IntOffset(0, height + 4)) }, role = Role.DropdownList)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(dimens.icon))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = NagTheme.type.label, color = colors.text, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Icon(NagIcons.ChevronDown, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
        }
        MenuPopup(visible = menu.isOpen, onDismiss = menu::close, entries = if (menu.isOpen) entries() else emptyList(), offset = menu.offset ?: IntOffset.Zero,
            minWidth = 180.dp)
    }
}

/** An icon that opens a menu of [entries] under itself: "Sırala" where a phone has no room for words. */
@Composable
fun IconMenuButton(icon: ImageVector, contentDescription: String, entries: () -> List<MenuEntry>, modifier: Modifier = Modifier) {
    val menu = rememberMenuState()
    var height by remember { mutableIntStateOf(0) }
    Box(modifier) {
        NagIconButton(
            icon,
            contentDescription,
            { menu.open(IntOffset(0, height + 4)) },
            modifier = Modifier.onGloballyPositioned { height = it.size.height },
            selected = menu.isOpen,
        )
        MenuPopup(visible = menu.isOpen, onDismiss = menu::close, entries = if (menu.isOpen) entries() else emptyList(), offset = menu.offset ?: IntOffset.Zero,
            minWidth = 180.dp)
    }
}
