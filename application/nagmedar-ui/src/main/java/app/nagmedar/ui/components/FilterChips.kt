package app.nagmedar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * A filter that is on or off (S-801): "4K", "Dolby Vision", "İzlenmedi". On, it takes the
 * selection colour and a check mark slides in (Desktop Kit 0.2.0); screen readers hear a checkbox.
 * The pill is 28 dp with a pointer and 36 dp by touch, in a 48 dp target (S-310).
 */
@Composable
fun FilterChip(label: String, selected: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val motion = NagTheme.motion
    val touch = NagTheme.inputMode == InputMode.Touch
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(50)
    val content = animateStateColor(if (selected) colors.onSelection else colors.textSecondary, "chipText")
    val border = animateStateColor(if (selected) colors.selection else colors.borderStrong, "chipBorder")
    // the target: by touch 48 dp either way, "4K" too (S-310); the pill is drawn inside it
    Box(
        modifier = modifier
            .heightIn(min = dimens.minTarget.coerceAtLeast(28.dp))
            .widthIn(min = dimens.minTarget)
            .pressScale(state.pressed)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .toggleable(value = selected, interactionSource = interaction, indication = null, role = Role.Checkbox, onValueChange = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(if (touch) 36.dp else 28.dp)
                .focusRing(state, shape)
                .background(stateBackground(state, selected, rest = colors.surface), shape)
                .border(1.dp, border, shape)
                .padding(start = if (touch) 12.dp else 10.dp, end = if (touch) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(motion.enter(NagMotion.MEDIUM)) + fadeIn(motion.enter(NagMotion.MEDIUM)),
                exit = shrinkHorizontally(motion.emphasizedExit(NagMotion.COLOR)) + fadeOut(motion.exit()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(NagIcons.Check, contentDescription = null, tint = content, modifier = Modifier.size(if (touch) 16.dp else 14.dp))
                    Spacer(Modifier.width(6.dp))
                }
            }
            Text(label, style = NagTheme.type.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Chips (or other small controls) in a row (Desktop Kit 0.2.0): on tablets and DeX ([wrap]) they
 * go on to as many lines as they need; on a phone they keep to one line that scrolls sideways, so
 * filters never take two or three lines of a small screen, and its ends fade into [fadeColor] (the
 * surface behind) while there is more to scroll to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    wrap: Boolean = NagTheme.largeWindow,
    fadeColor: Color = NagTheme.colors.background,
    content: @Composable RowScope.() -> Unit,
) {
    if (wrap) {
        val touch = NagTheme.inputMode == InputMode.Touch
        FlowRow(
            modifier = modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // touch chips carry 6 dp above and below in their 48 dp target already
            verticalArrangement = Arrangement.spacedBy(if (touch) 0.dp else 8.dp),
            content = { content() },
        )
    } else {
        val scroll = rememberScrollState()
        Row(
            modifier
                .fadingEdges(scroll, 16.dp, fadeColor)
                .horizontalScroll(scroll)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Fades the start and end of a sideways-scrolling line into [color] while it can scroll that way.
 * Painting the surface's colour over the edge needs no offscreen layer.
 */
private fun Modifier.fadingEdges(scroll: ScrollState, length: Dp, color: Color): Modifier = drawWithContent {
    drawContent()
    val fade = length.toPx().coerceAtMost(size.width / 3f)
    if (scroll.value > 0) {
        drawRect(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0f)), startX = 0f, endX = fade), size = Size(fade, size.height))
    }
    if (scroll.value < scroll.maxValue) {
        drawRect(
            Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color), startX = size.width - fade, endX = size.width),
            topLeft = Offset(size.width - fade, 0f),
            size = Size(fade, size.height),
        )
    }
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
    val inputModes = LocalInputModeManager.current
    // opened from the keyboard, the menu's first item takes the focus (S-1403)
    var byKeys by remember { mutableStateOf(false) }
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
                .nagClickable(
                    interaction,
                    {
                        byKeys = inputModes.inputMode == ComposeInputMode.Keyboard
                        menu.open(IntOffset(0, height + 4))
                    },
                    role = Role.DropdownList,
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(dimens.icon))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = NagTheme.type.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp))
            Icon(NagIcons.ChevronDown, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
        }
        MenuPopup(visible = menu.isOpen, onDismiss = menu::close, entries = if (menu.isOpen) entries() else emptyList(), offset = menu.offset ?: IntOffset.Zero,
            minWidth = 180.dp, focusFirst = byKeys)
    }
}

/** An icon that opens a menu of [entries] under itself: "Sırala" where a phone has no room for words. */
@Composable
fun IconMenuButton(icon: ImageVector, contentDescription: String, entries: () -> List<MenuEntry>, modifier: Modifier = Modifier) {
    val menu = rememberMenuState()
    var height by remember { mutableIntStateOf(0) }
    val inputModes = LocalInputModeManager.current
    var byKeys by remember { mutableStateOf(false) }
    Box(modifier) {
        NagIconButton(
            icon,
            contentDescription,
            {
                byKeys = inputModes.inputMode == ComposeInputMode.Keyboard
                menu.open(IntOffset(0, height + 4))
            },
            modifier = Modifier.onGloballyPositioned { height = it.size.height },
            selected = menu.isOpen,
        )
        MenuPopup(visible = menu.isOpen, onDismiss = menu::close, entries = if (menu.isOpen) entries() else emptyList(), offset = menu.offset ?: IntOffset.Zero,
            minWidth = 180.dp, focusFirst = byKeys)
    }
}
