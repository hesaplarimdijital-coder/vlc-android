package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.nagmedar.ui.theme.NagTheme

/** One line of a menu bar drop-down or a context menu. */
@Immutable
sealed interface MenuEntry {
    data class Item(
        val label: String,
        val onClick: () -> Unit,
        val shortcut: String? = null,
        val icon: ImageVector? = null,
        val enabled: Boolean = true,
        val checked: Boolean? = null,
        val destructive: Boolean = false,
    ) : MenuEntry

    data object Separator : MenuEntry
}

/** A titled drop-down of the menu bar. Entries are built when the menu opens. */
class MenuSpec(val title: String, val entries: () -> List<MenuEntry>)

/**
 * Menu surface shared by the menu bar and context menus. It opens at [offset] from the
 * anchor's top-left corner and moves inside the window when it would overflow (flipping above
 * the anchor point near the bottom edge, like desktop menus).
 */
@Composable
fun MenuPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    entries: List<MenuEntry>,
    offset: IntOffset = IntOffset.Zero,
    minWidth: Dp = 220.dp,
) {
    if (!visible || entries.isEmpty()) return
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard.coerceAtMost(10.dp))
    Popup(
        popupPositionProvider = remember(offset) { ClampedOffsetPositionProvider(offset) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = minWidth, max = 420.dp)
                .width(IntrinsicSize.Max)
                .shadow(12.dp, shape)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                .verticalScroll(rememberScrollState())
                .padding(4.dp),
        ) {
            entries.forEach { entry ->
                when (entry) {
                    is MenuEntry.Item -> MenuItemRow(entry) {
                        onDismiss()
                        entry.onClick()
                    }
                    MenuEntry.Separator -> HorizontalRule(Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun MenuItemRow(item: MenuEntry.Item, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val content = when {
        !item.enabled -> colors.textTertiary
        item.destructive -> colors.danger
        else -> colors.text
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(if (item.enabled) stateBackground(state) else colors.surfaceRaised, shape)
            .nagClickable(interaction, onClick, enabled = item.enabled, role = if (item.checked != null) Role.Checkbox else Role.Button)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(dimens.icon), contentAlignment = Alignment.Center) {
            val icon = if (item.checked == true) NagIcons.Check else item.icon
            if (icon != null) Icon(icon, contentDescription = null, tint = if (item.enabled) colors.textSecondary else content,
                modifier = Modifier.size(dimens.icon))
        }
        Spacer(Modifier.width(10.dp))
        Text(item.label, style = NagTheme.type.body, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        if (item.shortcut != null) {
            Spacer(Modifier.width(24.dp))
            Text(item.shortcut, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1)
        }
    }
}

/** The drop-down menus of the menu bar (Medya, Oynatma, …). Hovering another title while a menu is open switches to it. */
@Composable
fun RowScope.MenuBarMenus(menus: List<MenuSpec>) {
    var open by remember { mutableIntStateOf(-1) }
    menus.forEachIndexed { index, menu ->
        var titleHeight by remember { mutableIntStateOf(0) }
        Box(Modifier.onSizeChanged { titleHeight = it.height }) {
            MenuTitle(
                text = menu.title,
                open = open == index,
                onClick = { open = if (open == index) -1 else index },
                onHover = { if (open >= 0 && open != index) open = index },
            )
            MenuPopup(
                visible = open == index,
                onDismiss = { if (open == index) open = -1 },
                entries = if (open == index) menu.entries() else emptyList(),
                offset = IntOffset(0, titleHeight + 2),
            )
        }
    }
}

private class ClampedOffsetPositionProvider(private val offset: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        var x = anchorBounds.left + offset.x
        var y = anchorBounds.top + offset.y
        if (x + popupContentSize.width > windowSize.width) x = windowSize.width - popupContentSize.width
        if (y + popupContentSize.height > windowSize.height) {
            val above = anchorBounds.top + offset.y - popupContentSize.height
            y = if (above >= 0) above else windowSize.height - popupContentSize.height
        }
        return IntOffset(x.coerceAtLeast(0), y.coerceAtLeast(0))
    }
}

/** Shows a menu while [content] stays in place; use with [MenuEntry] lists built on demand. */
@Composable
fun rememberMenuState(): MenuState = remember { MenuState() }

class MenuState {
    var offset by androidx.compose.runtime.mutableStateOf<IntOffset?>(null)
        private set

    val isOpen: Boolean get() = offset != null

    fun open(at: IntOffset) {
        offset = at
    }

    fun close() {
        offset = null
    }
}

@Composable
internal fun LaunchHover(hovered: Boolean, onHover: () -> Unit) {
    LaunchedEffect(hovered) { if (hovered) onHover() }
}

/** Title of a menu bar menu. */
@Composable
fun MenuTitle(text: String, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onHover: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    LaunchHover(state.hovered, onHover)
    Box(
        modifier = modifier
            .heightIn(min = dimens.controlHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(if (open) colors.hover else stateBackground(state), shape)
            .nagClickable(interaction, onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NagTheme.type.body, color = if (open) colors.text else colors.textSecondary, maxLines = 1)
    }
}
