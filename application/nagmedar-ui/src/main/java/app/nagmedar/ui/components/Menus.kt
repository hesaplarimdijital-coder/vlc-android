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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
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
    /** Opened from the keyboard: the first item that can be chosen takes the focus. */
    focusFirst: Boolean = false,
    /** The menu bar's: Left and Right go to the menu before or after (-1, +1). */
    onMove: ((Int) -> Unit)? = null,
    /** Escape; [onDismiss] when not given. */
    onEscape: (() -> Unit)? = null,
) {
    if (!visible || entries.isEmpty()) return
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard.coerceAtMost(10.dp))
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Popup(
        popupPositionProvider = remember(offset) { ClampedOffsetPositionProvider(offset) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val first = remember { FocusRequester() }
        val firstEnabled = entries.indexOfFirst { it is MenuEntry.Item && it.enabled }
        Column(
            modifier = Modifier
                .widthIn(min = minWidth, max = 420.dp)
                .width(IntrinsicSize.Max)
                .shadow(12.dp, shape)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            (onEscape ?: onDismiss)()
                            true
                        }
                        Key.DirectionLeft -> onMove?.let { it(if (rtl) 1 else -1) } != null
                        Key.DirectionRight -> onMove?.let { it(if (rtl) -1 else 1) } != null
                        else -> false
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(4.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                when (entry) {
                    is MenuEntry.Item -> MenuItemRow(entry, if (index == firstEnabled) Modifier.focusRequester(first) else Modifier) {
                        onDismiss()
                        entry.onClick()
                    }
                    MenuEntry.Separator -> HorizontalRule(Modifier.padding(vertical = 4.dp))
                }
            }
        }
        if (focusFirst && firstEnabled >= 0) {
            LaunchedEffect(Unit) { first.requestFocus() }
        }
    }
}

@Composable
internal fun MenuItemRow(item: MenuEntry.Item, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(if (item.enabled) stateBackground(state) else colors.surfaceRaised, shape)
            // a menu is a popup window of its own, whose input mode may still be touch's when the
            // keyboard opened it: its items take the focus in any mode
            .focusProperties { canFocus = item.enabled }
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

/**
 * The menu bar's state (S-302): the open menu, whether it was opened from the keyboard, and the
 * Alt key's requests to take the keyboard focus into the bar or out of it.
 */
@Stable
class MenuBarState {
    /** The open menu, -1 for none. */
    var open by mutableIntStateOf(-1)
        private set

    /** The open menu came from the keyboard: its first item has the focus. */
    var fromKeyboard by mutableStateOf(false)
        private set

    internal var altPresses by mutableIntStateOf(0)
        private set

    /** Alt alone (or F10): to the first menu title, or back out when the bar has the focus. */
    fun onAlt() {
        altPresses++
    }

    fun open(index: Int, keyboard: Boolean) {
        open = index
        fromKeyboard = keyboard
    }

    fun close() {
        open = -1
        fromKeyboard = false
    }
}

@Composable
fun rememberMenuBarState(): MenuBarState = remember { MenuBarState() }

/**
 * The drop-down menus of the menu bar (Medya, Oynatma, …). Hovering another title while a menu
 * is open switches to it. From the keyboard (S-302): Alt goes to the first title and back out,
 * Left and Right move between titles and between open menus, Down, Enter or Space open a menu
 * with its first item focused, Escape closes it and returns to its title.
 */
@Composable
fun RowScope.MenuBarMenus(menus: List<MenuSpec>, state: MenuBarState = rememberMenuBarState()) {
    val focusManager = LocalFocusManager.current
    val inputModes = LocalInputModeManager.current
    val titles = remember(menus.size) { List(menus.size) { FocusRequester() } }
    var focusedTitle by remember { mutableIntStateOf(-1) }
    val inBar = focusedTitle >= 0 || state.open >= 0
    LaunchedEffect(state.altPresses) {
        if (state.altPresses == 0) return@LaunchedEffect
        if (inBar) {
            state.close()
            focusManager.clearFocus()
        } else {
            titles.firstOrNull()?.requestFocus()
        }
    }
    menus.forEachIndexed { index, menu ->
        var titleHeight by remember { mutableIntStateOf(0) }
        Box(Modifier.onSizeChanged { titleHeight = it.height }) {
            MenuTitle(
                text = menu.title,
                open = state.open == index,
                onClick = {
                    if (state.open == index) state.close() else state.open(index, keyboard = inputModes.inputMode == InputMode.Keyboard)
                },
                onHover = { if (state.open >= 0 && state.open != index) state.open(index, keyboard = false) },
                modifier = Modifier
                    .focusRequester(titles[index])
                    .onFocusChanged { focus ->
                        if (focus.isFocused) {
                            focusedTitle = index
                        } else if (focusedTitle == index) {
                            focusedTitle = -1
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            state.open(index, keyboard = true)
                            true
                        } else {
                            false
                        }
                    },
            )
            MenuPopup(
                visible = state.open == index,
                onDismiss = { if (state.open == index) state.close() },
                entries = if (state.open == index) menu.entries() else emptyList(),
                offset = IntOffset(0, titleHeight + 2),
                focusFirst = state.fromKeyboard,
                onMove = { step -> state.open((index + step).mod(menus.size), keyboard = true) },
                onEscape = {
                    state.close()
                    titles[index].requestFocus()
                },
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
