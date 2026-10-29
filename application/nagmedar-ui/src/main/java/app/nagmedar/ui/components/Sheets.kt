package app.nagmedar.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.R
import app.nagmedar.ui.shell.LocalShellOverlayHost
import app.nagmedar.ui.shell.ShellOverlayHost
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.GlassThickness
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagGlass
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.glass
import kotlinx.coroutines.launch
import androidx.compose.ui.input.InputMode as ComposeInputMode

/** The tallest a menu line is on a sheet: a thumb's row. */
private val SHEET_ROW_HEIGHT = 56.dp

/**
 * S-1601: a menu as a sheet at the bottom of the window, for the phone: [entries] in rows of a
 * thumb's height under an optional [title]; a tap outside, Back, Esc or a swipe down closes it.
 * The desktop's menus stay [MenuPopup]. Desktop Kit 1.0: the sheet is glass ([GlassBottomSheet]).
 */
@Composable
fun MenuSheet(visible: Boolean, onDismiss: () -> Unit, entries: List<MenuEntry>, title: String? = null) {
    // the last entries stay while the sheet goes down; callers pass an empty list when closed
    val held = remember { MenuEntriesKept(entries) }
    if (visible && entries.isNotEmpty()) held.entries = entries
    GlassBottomSheet(visible = visible && entries.isNotEmpty(), onDismiss = onDismiss, label = title) {
        if (title != null) {
            Text(
                title,
                style = NagTheme.type.bodyStrong,
                color = NagTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
        held.entries.forEach { entry ->
            when (entry) {
                is MenuEntry.Item -> MenuItemRow(entry, Modifier.padding(horizontal = 8.dp).heightIn(min = SHEET_ROW_HEIGHT)) {
                    onDismiss()
                    entry.onClick()
                }
                MenuEntry.Separator -> HorizontalRule(Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

/** The entries a sheet shows, kept while it goes down; plain, so keeping them never redraws anything. */
private class MenuEntriesKept(var entries: List<MenuEntry>)

/**
 * S-1601: an icon button that opens a menu of [entries]: a sheet at the bottom where the input is
 * touch, the desktop's pop-up under the button otherwise. The menu's [state] is the caller's, so
 * it can keep the button on screen while the menu is open.
 */
@Composable
fun ActionMenuButton(
    state: MenuState,
    icon: ImageVector,
    contentDescription: String,
    entries: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    sheetTitle: String? = null,
) {
    var height by remember { mutableIntStateOf(0) }
    val inputModes = LocalInputModeManager.current
    var byKeys by remember { mutableStateOf(false) }
    Box(modifier) {
        NagIconButton(
            icon,
            contentDescription,
            {
                byKeys = inputModes.inputMode == ComposeInputMode.Keyboard
                state.open(IntOffset(0, height + 4))
            },
            modifier = Modifier.onGloballyPositioned { height = it.size.height },
            selected = state.isOpen,
        )
        if (NagTheme.inputMode == InputMode.Touch) {
            MenuSheet(visible = state.isOpen, onDismiss = state::close, entries = if (state.isOpen) entries() else emptyList(), title = sheetTitle)
        } else {
            MenuPopup(
                visible = state.isOpen,
                onDismiss = state::close,
                entries = if (state.isOpen) entries() else emptyList(),
                offset = state.offset ?: IntOffset.Zero,
                minWidth = 180.dp,
                focusFirst = byKeys,
            )
        }
    }
}

/** [ActionMenuButton] with a menu state of its own. */
@Composable
fun ActionMenuButton(
    icon: ImageVector,
    contentDescription: String,
    entries: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    sheetTitle: String? = null,
) = ActionMenuButton(rememberMenuState(), icon, contentDescription, entries, modifier, sheetTitle)

/** A titled group of actions for [MoreSheet] and menus ("Oynatma", a screen's section); a null [title] draws a plain gap (Desktop Kit 1.0). */
@Immutable
data class ActionGroup(val title: String?, val actions: List<MenuEntry.Item>)

/** The groups as one menu, separated by rules; empty groups are left out. */
fun List<ActionGroup>.toMenuEntries(): List<MenuEntry> =
    filter { it.actions.isNotEmpty() }.flatMapIndexed { i, group -> (if (i > 0) listOf(MenuEntry.Separator) else emptyList()) + group.actions }

/** A menu's entries as groups split at its rules, for [MoreSheet]: the same entries a context menu has. */
fun List<MenuEntry>.toActionGroups(): List<ActionGroup> {
    val groups = mutableListOf(mutableListOf<MenuEntry.Item>())
    forEach { entry ->
        when (entry) {
            is MenuEntry.Item -> groups.last() += entry
            MenuEntry.Separator -> groups += mutableListOf<MenuEntry.Item>()
        }
    }
    return groups.filter { it.isNotEmpty() }.map { ActionGroup(null, it) }
}

/**
 * The "⋯" actions of something (a track, an album, a screen), Desktop Kit 1.0: on a phone a glass
 * bottom sheet listing [groups] with their icons under [title]; on wider windows the same entries
 * as a menu at [menuOffset] from the caller's top-left corner. Every entry is reachable with the
 * keyboard (Tab, arrows, Enter); Esc, Back, a tap on the scrim or a swipe down close it.
 */
@Composable
fun MoreSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    groups: List<ActionGroup>,
    title: String? = null,
    menuOffset: IntOffset = IntOffset.Zero,
    /** Opened from the keyboard: on a wider window the menu's first item takes the focus. */
    focusFirst: Boolean = false,
) {
    if (currentWidthClass() == WidthClass.Compact) {
        val held = remember { ActionGroupsKept(groups) }
        if (visible && groups.isNotEmpty()) held.groups = groups
        GlassBottomSheet(visible = visible, onDismiss = onDismiss, label = title) {
            if (title != null) {
                Text(title, style = NagTheme.type.title, color = NagTheme.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = NagTheme.dimens.gapL + 4.dp, vertical = NagTheme.dimens.gapS))
            }
            ActionGroupList(held.groups, onDismiss)
        }
    } else {
        MenuPopup(
            visible = visible,
            onDismiss = onDismiss,
            entries = if (visible) groups.toMenuEntries() else emptyList(),
            offset = menuOffset,
            minWidth = 180.dp,
            focusFirst = focusFirst,
        )
    }
}

/** The groups a sheet shows, kept while it goes down. */
private class ActionGroupsKept(var groups: List<ActionGroup>)

/**
 * A "⋯" icon button that opens [MoreSheet] with [groups] (a menu under the button on wider windows).
 * A screen reader reads it with [title], the item's name ([moreActionsLabel]), unless
 * [contentDescription] says otherwise.
 */
@Composable
fun MoreButton(
    groups: () -> List<ActionGroup>,
    modifier: Modifier = Modifier,
    title: String? = null,
    contentDescription: String = moreActionsLabel(title),
) {
    var open by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    val inputModes = LocalInputModeManager.current
    var byKeys by remember { mutableStateOf(false) }
    Box(modifier.onSizeChanged { height = it.height }) {
        NagIconButton(
            NagIcons.MoreHorizontal,
            contentDescription,
            {
                byKeys = inputModes.inputMode == ComposeInputMode.Keyboard
                open = true
            },
            selected = open,
        )
        MoreSheet(
            visible = open,
            onDismiss = { open = false },
            groups = if (open) groups() else emptyList(),
            title = title,
            menuOffset = IntOffset(0, height + 4),
            focusFirst = byKeys,
        )
    }
}

/**
 * What an item's "⋯" button is read as: "Sabaha Kadar için diğer işlemler" with the item's [name],
 * so that a screen reader tells one row's button from the next; "Diğer işlemler" without one.
 */
@Composable
fun moreActionsLabel(name: String?): String =
    if (name.isNullOrBlank()) stringResource(R.string.nag_more_actions) else stringResource(R.string.nag_more_actions_for, name)

/** The rows of [MoreSheet]: group captions, then one row per action. */
@Composable
internal fun ActionGroupList(groups: List<ActionGroup>, onDismiss: () -> Unit) {
    val dimens = NagTheme.dimens
    groups.filter { it.actions.isNotEmpty() }.forEachIndexed { index, group ->
        if (group.title != null) {
            SectionCaption(group.title, Modifier.padding(start = dimens.gapL + 4.dp, top = if (index == 0) 0.dp else dimens.gapM, bottom = 4.dp))
        } else if (index > 0) {
            HorizontalRule(Modifier.padding(vertical = dimens.gapXs, horizontal = dimens.gapL))
        }
        group.actions.forEach { action ->
            SheetActionRow(action) {
                onDismiss()
                action.onClick()
            }
        }
    }
}

@Composable
private fun SheetActionRow(item: MenuEntry.Item, onClick: () -> Unit) {
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
            .padding(horizontal = dimens.gapS)
            .heightIn(min = SHEET_ROW_HEIGHT.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onClick, enabled = item.enabled)
            .padding(horizontal = dimens.gapM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(dimens.iconLarge), contentAlignment = Alignment.Center) {
            val icon = if (item.checked == true) NagIcons.Check else item.icon
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (item.destructive) colors.danger else colors.textSecondary,
                    modifier = Modifier.size(dimens.icon))
            }
        }
        Spacer(Modifier.width(dimens.gapM))
        Text(item.label, style = NagTheme.type.body, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        if (item.shortcut != null) {
            Text(item.shortcut, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1)
        }
    }
}

/**
 * A modal bottom sheet of glass (Desktop Kit 1.0): slides up over a scrim, follows the finger when
 * dragged down and closes past a third of its height or on a downward fling (springs on a phone,
 * nothing moves with reduced motion). Esc, Back and a tap on the scrim call [onDismiss]. At most
 * 640 dp wide, centred; [label] names it for a screen reader.
 *
 * In the shell's window it is drawn in the shell's overlay over everything (and blurs the screen
 * behind it); elsewhere (in a dialog, before the shell) in a window of its own, solid. Closed and
 * at rest it is nothing at all (1.0's review: every row's "⋯" holds one): only while it is open or
 * going down does it animate, take the overlay, the focus and Back.
 */
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = remember { Animatable(0f) }
    // open, or still going down; read from the animation without recomposing on its frames
    val showing by remember(visible) { derivedStateOf { visible || progress.value > 0f } }
    // closed and at rest (every row's "⋯" and menu has one): no animation, no overlay, no focus, no blur
    if (!showing) return
    val tier = LocalMotionTier.current
    val drag = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            drag.snapTo(0f)
            progress.animateTo(1f, NagMotion.spatial(tier, tween(NagMotion.LONG, easing = NagMotion.EmphasizedDecelerate)))
        } else {
            progress.animateTo(0f, NagMotion.spatial(tier, tween(NagMotion.MEDIUM, easing = NagMotion.EmphasizedAccelerate)))
        }
    }
    val latestDismiss by rememberUpdatedState(onDismiss)
    val host = shellOverlayHere()
    if (host != null) {
        OverlayLayer(host, ShellOverlayHost.SHEET) { SheetSurface(progress, drag, tier, label, modifier, inShell = true, { latestDismiss() }, content) }
        BackHandler(enabled = visible) { latestDismiss() }
    } else {
        Dialog(onDismissRequest = { latestDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            // the sheet slides up opaque; the window does not fade it in over the page (1.0.1)
            StillDialogWindow()
            SheetSurface(progress, drag, tier, label, modifier, inShell = false, { latestDismiss() }, content)
        }
    }
}

/** The shell's overlay when this composition draws in the shell's own window (not in a dialog or pop-up of it); else null. */
@Composable
internal fun shellOverlayHere(): ShellOverlayHost? {
    val host = LocalShellOverlayHost.current ?: return null
    val root = LocalView.current.rootView
    return host.takeIf { it.rootView == null || it.rootView === root }
}

/**
 * Draws [content] in the shell's overlay at depth [z] ([ShellOverlayHost.PLAYER] or
 * [ShellOverlayHost.SHEET]) while this is in composition, with the caller's composition locals.
 */
@Composable
internal fun OverlayLayer(host: ShellOverlayHost, z: Int, content: @Composable () -> Unit) {
    val locals by rememberUpdatedState(currentCompositionLocalContext)
    val latest by rememberUpdatedState(content)
    val layer = remember(host, z) {
        ShellOverlayHost.Layer(z) { CompositionLocalProvider(locals) { latest() } }
    }
    DisposableEffect(host, layer) {
        host.layers.add(layer)
        onDispose { host.layers.remove(layer) }
    }
}

@Composable
private fun SheetSurface(
    progress: Animatable<Float, AnimationVector1D>,
    drag: Animatable<Float, AnimationVector1D>,
    tier: MotionTier,
    label: String?,
    modifier: Modifier,
    inShell: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var sheetHeight by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val name = label ?: stringResource(R.string.nag_more_actions)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        // the scrim darkens with the sheet and lightens while it is dragged down; a window of its own dims what is behind it itself
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (!inShell) Modifier else Modifier
                        .graphicsLayer {
                            val dragged = if (sheetHeight > 0) (drag.value / sheetHeight).coerceIn(0f, 1f) else 0f
                            alpha = progress.value.coerceIn(0f, 1f) * (1f - dragged)
                        }
                        .background(colors.scrim),
                )
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .onSizeChanged { sheetHeight = it.height }
                .graphicsLayer {
                    val hidden = (1f - progress.value) * (if (sheetHeight > 0) sheetHeight.toFloat() else 2000f)
                    translationY = hidden + drag.value
                }
                // in a window of its own there is nothing of the screen to blur: the solid surface
                .then(
                    if (inShell) Modifier.glass(NagGlass.raised(colors, GlassThickness.Regular), shape)
                    else Modifier.glass(NagGlass.raised(colors, GlassThickness.Regular), shape, source = null),
                )
                // taps inside stay inside
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics { paneTitle = name }
                .onPreviewKeyEvent { event ->
                    val esc = event.key == Key.Escape && event.type == KeyEventType.KeyDown
                    if (esc) onDismiss()
                    esc
                }
                .focusRequester(focus)
                .focusable()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                    },
                    onDragStopped = { velocity ->
                        val far = sheetHeight > 0 && drag.value > sheetHeight / 3f
                        val flung = velocity > with(density) { FLING_CLOSE.toPx() }
                        if (far || flung) {
                            onDismiss()
                        } else {
                            val spec = NagMotion.spatial(tier, tween<Float>(NagMotion.MEDIUM, easing = NagMotion.Emphasized))
                            drag.animateTo(0f, spec, initialVelocity = velocity)
                        }
                    },
                )
                .then(if (inShell) Modifier else Modifier.navigationBarsPadding())
                .padding(bottom = dimens.gapM),
        ) {
            // the handle says "drag me"
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(colors.borderStrong, RoundedCornerShape(2.dp)))
            }
            Column(Modifier.verticalScroll(rememberScrollState()), content = content)
        }
    }
}

/** A downward fling this fast closes a sheet wherever it is. */
private val FLING_CLOSE = 800.dp
