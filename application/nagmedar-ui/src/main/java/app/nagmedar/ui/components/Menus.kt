package app.nagmedar.ui.components

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
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
import app.nagmedar.ui.input.MenuBarKey
import app.nagmedar.ui.input.keyHint
import app.nagmedar.ui.theme.GlassMode
import app.nagmedar.ui.theme.GlassThickness
import app.nagmedar.ui.theme.NagGlass
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.glass

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
 *
 * As Android's own pop-up menus (Desktop Kit 0.2.0) it grows in from its corner while fading in
 * ([NagMotion.COLOR]) and fades out when closed ([NagMotion.SHORT]); while it fades it takes
 * neither the focus nor a click. With "Remove animations" it simply appears and goes. Inside the
 * shell it is frosted glass over the screen (Desktop Kit 1.0, [NagGlass]); elsewhere a solid
 * raised surface with a shadow.
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
    /**
     * The menu bar's: Escape, which then returns to the menu's title. The menu handles Escape and
     * Back itself then, and has the focus even when the pointer opened it, so its keys reach it.
     */
    onEscape: (() -> Unit)? = null,
    /** The menu bar's: Alt alone or F10, which leave the bar. */
    onAlt: (() -> Unit)? = null,
) {
    val open = visible && entries.isNotEmpty()
    // the last entries stay on screen while the menu fades out; callers pass an empty list when closed
    val held = remember { MenuEntriesHolder(entries) }
    if (open) held.entries = entries
    val transition = remember { MutableTransitionState(false) }
    SideEffect { transition.targetState = open }
    if (!open && !transition.currentState && !transition.targetState) return
    val shownEntries = held.entries
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val motion = NagTheme.motion
    val shape = RoundedCornerShape(dimens.radiusCard.coerceAtMost(10.dp))
    val glassy = NagGlass.source != null && NagGlass.mode != GlassMode.Solid
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ownKeys = onEscape != null
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestOpen by rememberUpdatedState(open)
    // the corner it grows from: its start, at the top when it opens downwards
    val origin = if (rtl) TransformOrigin(1f, 0f) else TransformOrigin(0f, 0f)
    Popup(
        popupPositionProvider = remember(offset) { ClampedOffsetPositionProvider(offset) },
        // a menu fading out is gone already: what is outside it gets the click
        onDismissRequest = { if (latestOpen) latestOnDismiss() },
        properties = PopupProperties(focusable = open, dismissOnBackPress = open && !ownKeys, dismissOnClickOutside = open),
    ) {
        val menu = remember { FocusRequester() }
        val first = remember { FocusRequester() }
        val last = remember { FocusRequester() }
        val altKey = remember { MenuBarKey() }
        var menuFocused by remember { mutableStateOf(false) }
        val firstEnabled = shownEntries.indexOfFirst { it is MenuEntry.Item && it.enabled }
        val lastEnabled = shownEntries.indexOfLast { it is MenuEntry.Item && it.enabled }
        AnimatedVisibility(
            visibleState = transition,
            enter = fadeIn(motion.enter(NagMotion.COLOR)) + scaleIn(motion.enter(NagMotion.COLOR), initialScale = 0.92f, transformOrigin = origin),
            exit = fadeOut(motion.exit()) + scaleOut(motion.exit(), targetScale = 0.96f, transformOrigin = origin),
            label = "menu",
        ) {
            Column(
                modifier = Modifier
                    // a menu on its way out takes no click
                    .then(if (open) Modifier else Modifier.pointerInput(Unit) { consumeEveryPointerEvent() })
                    .widthIn(min = minWidth, max = 420.dp)
                    .width(IntrinsicSize.Max)
                    // a shadow would show through glass; solid menus keep it
                    .then(if (glassy) Modifier else Modifier.shadow(12.dp, shape))
                    .glass(NagGlass.raised(colors, GlassThickness.Regular), shape)
                    .border(1.dp, colors.borderStrong, shape)
                    .onPreviewKeyEvent { event ->
                        val down = event.type == KeyEventType.KeyDown
                        if (onAlt != null && altKey.onKey(event.nativeKeyEvent.keyCode, down)) {
                            onAlt()
                            return@onPreviewKeyEvent true
                        }
                        when (event.key) {
                            // acted on when released, so the release does not reach the window below
                            Key.Escape, Key.Back -> {
                                if (!down) (if (event.key == Key.Escape) onEscape ?: onDismiss else onDismiss)()
                                true
                            }
                            Key.DirectionLeft, Key.DirectionRight -> {
                                val move = onMove ?: return@onPreviewKeyEvent false
                                if (down) move(if ((event.key == Key.DirectionRight) != rtl) 1 else -1)
                                true
                            }
                            // opened by the pointer, the menu has the focus: Down and Up go to its first or last item
                            Key.DirectionDown, Key.DirectionUp -> {
                                if (!menuFocused || firstEnabled < 0) return@onPreviewKeyEvent false
                                if (down) (if (event.key == Key.DirectionDown) first else last).requestFocus()
                                true
                            }
                            else -> false
                        }
                    }
                    // the menu holds the focus whoever opened it, so its keys (Esc, Up, Down) reach it
                    .onFocusChanged { menuFocused = it.isFocused }
                    .focusRequester(menu)
                    .focusTarget()
                    .verticalScroll(rememberScrollState())
                    .padding(4.dp),
            ) {
                shownEntries.forEachIndexed { index, entry ->
                    when (entry) {
                        is MenuEntry.Item -> {
                            val modifier = Modifier
                                .then(if (index == firstEnabled) Modifier.focusRequester(first) else Modifier)
                                .then(if (index == lastEnabled) Modifier.focusRequester(last) else Modifier)
                            MenuItemRow(entry, modifier) {
                                onDismiss()
                                entry.onClick()
                            }
                        }
                        MenuEntry.Separator -> HorizontalRule(Modifier.padding(vertical = 4.dp))
                    }
                }
            }
            LaunchedEffect(Unit) {
                if (focusFirst && firstEnabled >= 0) first.requestFocus() else menu.requestFocus()
            }
        }
    }
}

/** The entries a menu shows, kept while it fades out; plain, so keeping them never redraws anything. */
private class MenuEntriesHolder(var entries: List<MenuEntry>)

/** Takes every pointer event before the children see it. */
private suspend fun PointerInputScope.consumeEveryPointerEvent() {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
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
            // a disabled row lets the menu's surface (glass, Desktop Kit 1.0) show through
            .background(if (item.enabled) stateBackground(state) else Color.Transparent, shape)
            // a menu is a popup window of its own, whose input mode may still be touch's when the
            // keyboard opened it: its items take the focus in any mode
            .focusProperties { canFocus = item.enabled }
            .nagClickable(interaction, onClick, enabled = item.enabled, role = if (item.checked != null) Role.Checkbox else Role.Button)
            // a screen reader says whether a checkable item is ticked
            .semantics { if (item.checked != null) toggleableState = ToggleableState(item.checked) }
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
        // 1.0.1: the keys with a keyboard only
        val shortcut = keyHint(item.shortcut)
        if (shortcut != null) {
            Spacer(Modifier.width(24.dp))
            Text(shortcut, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The menu bar's state (S-302). Like a desktop menu bar, Alt does not move the window's focus:
 * it highlights the first title, and while a title is [highlighted] the window's keys come here
 * ([onKey]) — Left and Right move between the titles, Down, Up, Enter or Space open the menu with
 * its first item focused, Escape or Alt again give the keyboard back to whatever had it.
 */
@Stable
class MenuBarState {
    /** The open menu, -1 for none. */
    var open by mutableIntStateOf(-1)
        private set

    /** The open menu came from the keyboard: its first item has the focus. */
    var fromKeyboard by mutableStateOf(false)
        private set

    /** The title the keyboard is on, -1 when the bar does not have the keyboard. */
    var highlighted by mutableIntStateOf(-1)
        private set

    /** The number of menus on screen; 0 while the bar is not shown (touch). */
    internal var count by mutableIntStateOf(0)

    /** A title has the keyboard (Alt, F10, or Escape from a menu). */
    val hasKeyboard: Boolean get() = highlighted >= 0

    /** Alt alone or F10: highlights the first title, or gives the keyboard back. False when no bar is shown. */
    fun onAlt(): Boolean {
        when {
            highlighted >= 0 || open >= 0 -> leave()
            count > 0 -> highlighted = 0
            else -> return false
        }
        return true
    }

    /**
     * A key of the window ([KeyEvent] key code) while a title has the keyboard and no menu is open;
     * true when it was the bar's. Any other key gives the keyboard back and goes on to the window.
     */
    fun onKey(keyCode: Int, down: Boolean, rtl: Boolean = false): Boolean {
        if (highlighted < 0 || open >= 0 || count == 0 || KeyEvent.isModifierKey(keyCode)) return false
        val step = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (rtl) 1 else -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (rtl) -1 else 1
            else -> 0
        }
        val opens = keyCode in OpenKeys
        val leaves = keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK
        if (!down) {
            // Escape and Back act when released, so the release does not reach the window
            if (leaves) leave()
            return step != 0 || opens || leaves
        }
        when {
            step != 0 -> highlighted = (highlighted + step).mod(count)
            opens -> open(highlighted, keyboard = true)
            leaves -> Unit
            else -> {
                leave()
                return false
            }
        }
        return true
    }

    fun open(index: Int, keyboard: Boolean) {
        open = index
        fromKeyboard = keyboard
        highlighted = if (keyboard || highlighted >= 0) index else -1
    }

    /** Escape in a menu: it closes and its title keeps the keyboard. */
    fun backToTitle() {
        if (open >= 0) highlighted = open
        open = -1
        fromKeyboard = false
    }

    /** Closes the menu and gives the keyboard back. */
    fun leave() {
        open = -1
        fromKeyboard = false
        highlighted = -1
    }

    private companion object {
        val OpenKeys = setOf(
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE,
        )
    }
}

@Composable
fun rememberMenuBarState(): MenuBarState = remember { MenuBarState() }

/**
 * The drop-down menus of the menu bar (Medya, Oynatma, …). Hovering another title while a menu
 * is open switches to it. From the keyboard (S-302) see [MenuBarState]; in an open menu Up and
 * Down move between the items, Left and Right go to the menu before or after, Escape returns to
 * its title and Alt leaves the bar.
 */
@Composable
fun RowScope.MenuBarMenus(menus: List<MenuSpec>, state: MenuBarState = rememberMenuBarState()) {
    val inputModes = LocalInputModeManager.current
    DisposableEffect(state, menus.size) {
        state.count = menus.size
        onDispose {
            state.count = 0
            state.leave()
        }
    }
    // with the predictive Back gesture's routing (Android 14 and later) Back no longer passes the window's keys:
    // a title that has the keyboard gives it back here; registered anew each time, so it comes before the screens'
    key(state.hasKeyboard) {
        BackHandler(enabled = state.hasKeyboard && state.open < 0) { state.leave() }
    }
    menus.forEachIndexed { index, menu ->
        var titleHeight by remember { mutableIntStateOf(0) }
        Box(Modifier.onSizeChanged { titleHeight = it.height }) {
            MenuTitle(
                text = menu.title,
                open = state.open == index,
                onClick = {
                    if (state.open == index) state.leave() else state.open(index, keyboard = inputModes.inputMode == InputMode.Keyboard)
                },
                onHover = { if (state.open >= 0 && state.open != index) state.open(index, keyboard = false) },
                highlighted = if (state.hasKeyboard) state.highlighted == index else null,
                // a title reached with Tab opens with Down too
                modifier = Modifier.onKeyEvent { event ->
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
                onDismiss = { if (state.open == index) state.leave() },
                entries = if (state.open == index) menu.entries() else emptyList(),
                offset = IntOffset(0, titleHeight + 2),
                focusFirst = state.fromKeyboard,
                onMove = { step -> state.open((index + step).mod(menus.size), keyboard = true) },
                onEscape = { state.backToTitle() },
                onAlt = { state.leave() },
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
fun MenuTitle(
    text: String,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onHover: () -> Unit = {},
    /** The keyboard is on this title (Alt); null when the ring follows the focus. */
    highlighted: Boolean? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val lit = open || highlighted == true
    LaunchHover(state.hovered, onHover)
    Box(
        modifier = modifier
            .heightIn(min = dimens.controlHeight.coerceAtLeast(dimens.minTarget))
            .then(if (highlighted ?: state.focused) Modifier.border(2.dp, colors.focusRing, shape) else Modifier)
            .background(if (lit) colors.hover else stateBackground(state), shape)
            .nagClickable(interaction, onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NagTheme.type.body, color = if (lit) colors.text else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
