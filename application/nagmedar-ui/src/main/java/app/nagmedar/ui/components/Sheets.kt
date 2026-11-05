package app.nagmedar.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.R
import app.nagmedar.ui.input.keyHint
import app.nagmedar.ui.shell.LocalShellOverlayHost
import app.nagmedar.ui.shell.bottomInsetPadding
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
import app.nagmedar.ui.theme.rememberNagHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
        // the rows read the groups of this composition: a switch flipped in the sheet (1.0.3) shows its new state
        val shown = held.groups
        GlassBottomSheet(visible = visible, onDismiss = onDismiss, label = title) {
            if (title != null) {
                Text(title, style = NagTheme.type.title, color = NagTheme.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = NagTheme.dimens.gapL + 4.dp, vertical = NagTheme.dimens.gapS))
            }
            ActionGroupList(shown, onDismiss)
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

/**
 * The rows of [MoreSheet]: group captions, then one row per action. An action closes the sheet; an on/off
 * one (1.0.3, Desktop Kit 2.3: `checked` set) is a switch that flips in view and leaves the sheet open.
 */
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
                if (action.checked == null) onDismiss()
                action.onClick()
            }
        }
    }
}

/**
 * One action of [MoreSheet]: its icon, its label and (with a keyboard) its keys. 1.0.3 (Desktop Kit 2.3): an
 * item with `checked` set is a switch at the end of the line; the whole line flips it, a screen reader reads it
 * as a switch, and by touch it vibrates as it turns on or off. A menu ([MenuPopup], [MenuSheet]) keeps its check mark.
 */
@Composable
private fun SheetActionRow(item: MenuEntry.Item, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val haptics = rememberNagHaptics()
    val content = when {
        !item.enabled -> colors.textTertiary
        item.destructive -> colors.danger
        else -> colors.text
    }
    val checked = item.checked
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gapS)
            .heightIn(min = SHEET_ROW_HEIGHT.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .then(
                if (checked == null) {
                    Modifier.nagClickable(interaction, onClick, enabled = item.enabled)
                } else {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .hoverable(interaction, item.enabled)
                        .focusMemory()
                        .toggleable(checked, interaction, indication = null, enabled = item.enabled, role = Role.Switch) { on ->
                            haptics.toggle(on)
                            onClick()
                        }
                },
            )
            .padding(horizontal = dimens.gapM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the icon column stays when an item has no icon, so the labels line up
        Box(Modifier.size(dimens.iconLarge), contentAlignment = Alignment.Center) {
            if (item.icon != null) {
                Icon(item.icon, contentDescription = null, tint = if (item.destructive) colors.danger else colors.textSecondary,
                    modifier = Modifier.size(dimens.icon))
            }
        }
        Spacer(Modifier.width(dimens.gapM))
        Text(item.label, style = NagTheme.type.body, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        // 1.0.1: the keys with a keyboard only
        val shortcut = keyHint(item.shortcut)
        if (shortcut != null) {
            Text(shortcut, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1)
        }
        if (checked != null) {
            Spacer(Modifier.width(dimens.gapM))
            SwitchTrack(checked)
        }
    }
}

/**
 * A modal bottom sheet of glass (Desktop Kit 1.0): slides up over a scrim and closes on Esc, Back
 * and a tap on the scrim ([onDismiss]). 1.0.2: dragged down anywhere on it (its handle, its title,
 * its rows) it follows the finger as the phone's full player does; what it holds scrolls back to
 * its top first. Let go past a third of its height or flung down it closes, short of that it
 * springs back ([SheetDrag]). At most 640 dp wide, centred; [label] names it for a screen reader.
 *
 * 1.0.3 (Desktop Kit 2.3): on a phone it comes and goes on one firm spring ([sheetWay]); taken out
 * by the finger it carries on at the finger's speed and is dismissed the moment it is out, with
 * nothing left to animate; closed part way down by the scrim or Back it goes from where it is. In
 * the shell, the keyboard's focus goes back where it was when it closes ([ReturnFocusOnClose]).
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
    val latestDismiss by rememberUpdatedState(onDismiss)
    val drag = rememberSheetDrag { latestDismiss() }
    LaunchedEffect(visible) {
        when {
            visible -> {
                drag.reset()
                progress.animateTo(1f, sheetWay(tier, tween(NagMotion.LONG, easing = NagMotion.EmphasizedDecelerate)))
            }
            // pulled or flung out by the finger (1.0.3): it is gone already, nothing to animate
            drag.carriedOut -> progress.snapTo(0f)
            // the scrim, Back, Esc or an action: down from where it is
            else -> progress.animateTo(0f, sheetWay(tier, tween(NagMotion.MEDIUM, easing = NagMotion.EmphasizedAccelerate)))
        }
    }
    val host = shellOverlayHere()
    if (host != null) {
        OverlayLayer(host, ShellOverlayHost.SHEET) { SheetSurface(progress, drag, label, modifier, inShell = true, { latestDismiss() }, content, host.systemBottomPx) }
        ReturnFocusOnClose(host, visible)
        BackHandler(enabled = visible) { latestDismiss() }
    } else {
        Dialog(onDismissRequest = { latestDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            // the sheet slides up opaque; the window does not fade it in over the page (1.0.1)
            StillDialogWindow()
            SheetSurface(progress, drag, label, modifier, inShell = false, { latestDismiss() }, content)
        }
    }
}

/**
 * The sheet's way in and out (1.0.3, Desktop Kit 2.3): on a phone one firm spring both ways, 0.92 at 800 (a
 * sheet is a small thing: the screens' spring is slow on it), arrived within a thousandth of the way; [timed]
 * (the 0.2.0 curve) wider up; a jump with reduced motion.
 */
internal fun sheetWay(tier: MotionTier, timed: FiniteAnimationSpec<Float>): FiniteAnimationSpec<Float> = when (tier) {
    MotionTier.Gesture -> spring(dampingRatio = SHEET_DAMPING, stiffness = SHEET_STIFFNESS, visibilityThreshold = NagMotion.FRACTION)
    MotionTier.Timed -> timed
    MotionTier.Reduced -> snap()
}

/** The phone's sheet spring ([sheetWay]): all but critically damped, firmer than the screens' 600. */
private const val SHEET_DAMPING = 0.92f
private const val SHEET_STIFFNESS = 800f

/**
 * 1.0.3 (Desktop Kit 2.3): while a layer over the shell is [open] (a sheet), remembers the control that had
 * the keyboard focus when it opened ([FocusMemory]); when the layer closes the focus goes back there, so Tab
 * goes on from where it was (a layer in the shell's window would otherwise leave the focus nowhere).
 */
@Composable
internal fun ReturnFocusOnClose(host: ShellOverlayHost, open: Boolean) {
    DisposableEffect(host, open) {
        val previous = if (open) host.focusMemory.current() else null
        onDispose { previous?.restore() }
    }
}

/**
 * 1.0.2: a sheet pulled down by the finger, anywhere on it (the user's words: as the player goes
 * down to the mini player from anywhere on it). [offset] is how far it is below its place. Through
 * [sheetDrag] a drag on what does not scroll pulls it at once; a list in it ([nestedScroll]) scrolls
 * back to its top first and hands the rest of the drag down to the sheet, and a sheet pulled down
 * goes back up before the list scrolls on. Let go past a third of its [height], or flung down
 * faster than [FLING_CLOSE], it closes ([onClose]); short of that it springs back on the motion
 * tier's spring with the finger's speed (a jump with reduced motion). Only a pressed pointer pulls:
 * a mouse wheel at the list's top leaves the sheet where it is.
 *
 * 1.0.3 (Desktop Kit 2.3): on a phone a sheet let go to close carries on down at the finger's speed
 * and [onClose] comes the moment it is out (half a pixel left), [carriedOut] telling what closes it
 * that nothing is left to animate: before, it was closed at once and went down from a standstill, a
 * visible pause after a fling. Wider windows and reduced motion close at once, as in 1.0.2. On the
 * way past the closing point the finger feels one tick ([onThreshold]: touch only).
 */
@Stable
class SheetDrag internal constructor(private val scope: CoroutineScope, private val flingClosePx: Float) {
    /** How far below its place the sheet is, in px; read it in a layer or draw lambda. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** The sheet's height, in px: a third of it closes. */
    internal var height by mutableIntStateOf(0)

    internal var onClose: () -> Unit = {}
    internal var tier: MotionTier = MotionTier.Gesture

    /** The drag went past the point where letting go closes the sheet (1.0.3: the haptic tick). */
    internal var onThreshold: () -> Unit = {}

    /** A finger (or a pressed button) is on the sheet. */
    internal var held = false
        set(value) {
            // a new press pulls from where the sheet is: caught past the closing point, it does not tick again there
            if (value && !field) armed = pastClosing(offset)
            field = value
        }

    /**
     * 1.0.3: the finger took the sheet out (let go past a third or flung, on a phone): it is gone when
     * [onClose] comes, and what closes it has nothing left to animate. Cleared by [reset].
     */
    var carriedOut by mutableStateOf(false)
        private set

    /** Let go far enough: it is going, nothing pulls it any more. */
    private var closing = false
    private var settling: Job? = null

    /** The pull is past the closing point: the tick came on the way there. */
    private var armed = false

    /** The sheet is below its place: dragged, or springing back. */
    val pulled: Boolean get() = offset > 0f

    /** Moves the sheet [delta] px down (up when negative), never above its place; returns what it took. */
    fun pull(delta: Float): Float {
        // going: the drag's rest goes nowhere else
        if (closing) return delta
        settling?.cancel()
        settling = null
        val before = offset
        offset = (before + delta).coerceAtLeast(0f)
        val past = pastClosing(offset)
        if (past != armed) {
            armed = past
            if (past) onThreshold()
        }
        return offset - before
    }

    private fun pastClosing(at: Float): Boolean = height > 0 && at > height / 3f

    /** Let go at [velocity] px/s (down positive): it closes or springs back. */
    fun release(velocity: Float) {
        if (closing || !pulled) return
        armed = false
        if (SheetDragRules.closes(offset, height, velocity, flingClosePx)) {
            closing = true
            settling?.cancel()
            settling = null
            if (SheetDragRules.carriesOut(tier) && height > 0) {
                settling = scope.launch { carryOut(velocity) }
            } else {
                onClose()
            }
            return
        }
        settling?.cancel()
        settling = scope.launch {
            val spec = NagMotion.spatial(tier, tween<Float>(NagMotion.MEDIUM, easing = NagMotion.Emphasized), NagMotion.PIXEL)
            animate(offset, 0f, velocity, spec) { value, _ -> offset = value.coerceAtLeast(0f) }
        }
    }

    /**
     * Down and out at [velocity] px/s on the phone's gesture spring, closed ([onClose]) the moment it is out
     * whatever the spring still does; a slow let-go past a third still leaves on the spring, from rest.
     */
    private suspend fun carryOut(velocity: Float) {
        val end = height.toFloat()
        var gone = false
        fun out() {
            if (gone) return
            gone = true
            carriedOut = true
            onClose()
        }
        animate(offset, end, velocity.coerceAtLeast(0f), NagMotion.gesture(NagMotion.PIXEL)) { value, _ ->
            offset = value.coerceAtLeast(0f)
            if (value >= end - NagMotion.PIXEL) out()
        }
        out()
    }

    /** Back at its place at once, nothing going: the sheet (re)opens. */
    fun reset() {
        settling?.cancel()
        settling = null
        closing = false
        carriedOut = false
        armed = false
        offset = 0f
    }

    internal val draggable = DraggableState { delta -> pull(delta) }

    /** What a list in the sheet leaves of a drag moves the sheet; see the class. */
    val nestedScroll: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // pushed back up while pulled down: the sheet goes back first, then the list scrolls
            if (source != NestedScrollSource.UserInput || available.y >= 0f || !pulled) return Offset.Zero
            return Offset(0f, pull(available.y))
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // what the list cannot take (pulled down at its top) moves the sheet; a fling's rest and a wheel do not
            if (source != NestedScrollSource.UserInput || available.y <= 0f || !held) return Offset.Zero
            return Offset(0f, pull(available.y))
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            // let go with the sheet pulled down: it settles with the finger's speed and the list does not fling
            if (!pulled) return Velocity.Zero
            release(available.y)
            return available
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // a drag that ended without a fling of its own leaves the sheet at its place or going
            if (pulled && settling?.isActive != true) release(0f)
            return Velocity.Zero
        }
    }
}

/** 1.0.2: what letting a pulled sheet go does (pure, for the tests). */
internal object SheetDragRules {
    /** Pulled [offset] px down a sheet [height] px high and let go at [velocity] px/s: past a third, or flung down, it closes. */
    fun closes(offset: Float, height: Int, velocity: Float, flingClosePx: Float): Boolean =
        offset > 0f && ((height > 0 && offset > height / 3f) || velocity > flingClosePx)

    /**
     * 1.0.3 (Desktop Kit 2.3): a sheet let go to close carries on out at the finger's speed on a phone; wider
     * windows close it at once and it goes from where it is (their timed curve), and reduced motion jumps.
     */
    fun carriesOut(tier: MotionTier): Boolean = tier == MotionTier.Gesture
}

/**
 * A [SheetDrag] that closes with [onClose] (the latest one), on the window's motion tier; the drag past the
 * closing point ticks by touch (1.0.3, [app.nagmedar.ui.theme.NagHaptics.threshold]).
 */
@Composable
internal fun rememberSheetDrag(onClose: () -> Unit): SheetDrag {
    val scope = rememberCoroutineScope()
    val flingClose = with(LocalDensity.current) { FLING_CLOSE.toPx() }
    val drag = remember(scope, flingClose) { SheetDrag(scope, flingClose) }
    val haptics = rememberNagHaptics()
    drag.onClose = onClose
    drag.tier = LocalMotionTier.current
    drag.onThreshold = { haptics.threshold() }
    return drag
}

/**
 * The sheet [drag] pulls (1.0.2): its height, what its lists leave of a drag, and a vertical drag
 * on what does not scroll in it. A sideways drag (a slider, a row of chips) stays where it began.
 */
internal fun Modifier.sheetDrag(drag: SheetDrag): Modifier = this
    .onSizeChanged { drag.height = it.height }
    .pointerInput(drag) {
        // watches, before anything in the sheet, whether a pointer is pressed; consumes nothing
        awaitPointerEventScope {
            while (true) {
                drag.held = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
            }
        }
    }
    .nestedScroll(drag.nestedScroll)
    .draggable(drag.draggable, Orientation.Vertical, onDragStopped = { velocity -> drag.release(velocity) })

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
    drag: SheetDrag,
    label: String?,
    modifier: Modifier,
    inShell: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    /** 1.0.3: in the shell, the system's navigation bar under the sheet (px): its surface reaches behind it, its rows stay above. */
    bottomInset: () -> Int = { 0 },
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
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
                            val height = drag.height
                            val dragged = if (height > 0) (drag.offset / height).coerceIn(0f, 1f) else 0f
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
                .graphicsLayer {
                    val height = drag.height
                    val hidden = (1f - progress.value) * (if (height > 0) height.toFloat() else 2000f)
                    translationY = hidden + drag.offset
                }
                // in a window of its own there is nothing of the screen to blur: the solid surface
                .then(
                    if (inShell) Modifier.glass(NagGlass.raised(colors, GlassThickness.Regular), shape)
                    else Modifier.glass(NagGlass.raised(colors, GlassThickness.Regular), shape, source = null),
                )
                // taps inside stay inside
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics { paneTitle = name }
                .testTag("sheet")
                .onPreviewKeyEvent { event ->
                    val esc = event.key == Key.Escape && event.type == KeyEventType.KeyDown
                    if (esc) onDismiss()
                    esc
                }
                .focusRequester(focus)
                .focusable()
                // 1.0.2: the whole sheet follows a drag down, its rows and title as well as its handle
                .sheetDrag(drag)
                .then(if (inShell) Modifier.bottomInsetPadding(bottomInset) else Modifier.navigationBarsPadding())
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
