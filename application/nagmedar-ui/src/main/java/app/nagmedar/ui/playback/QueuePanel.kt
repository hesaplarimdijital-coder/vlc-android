package app.nagmedar.ui.playback

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SwipeToRemove
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.components.blockInputBelow
import app.nagmedar.ui.components.blockPointerBelow
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.rememberReorderState
import app.nagmedar.ui.components.reorderHandle
import app.nagmedar.ui.components.reorderItem
import app.nagmedar.ui.components.reorderList
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.library.durationWords
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.shell.DetailBackHandler
import app.nagmedar.ui.shell.WindowPage
import app.nagmedar.ui.shell.rememberDetailMotion
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import kotlin.math.roundToInt

/**
 * "Sıradaki", the play queue (S-310): what plays now, then what comes after it. A row tapped
 * plays; by touch a row swiped toward the start goes and one dragged by its handle moves; with a
 * pointer, the handle drags and the row's menu, Delete, Alt+↑ and Alt+↓ do the rest. "Temizle"
 * takes everything after what plays now; what goes comes back with the undo bar. [onClose] null
 * (the music view's inspector, Desktop Kit 1.0): the panel stays, without its close button, and
 * leaves Back and Esc to the window; [background] is what it lies on. [page] (1.0.1, a phone's
 * [QueuePage]): a page's top bar instead, the back arrow before the title and "Temizle" only
 * while something is to come; Back is the page's.
 */
@Composable
fun QueuePanel(
    entries: List<QueueEntry>,
    current: Int,
    actions: QueueActions,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    undo: UndoBarState? = null,
    background: Color = NagTheme.colors.chrome,
    page: Boolean = false,
) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val touch = NagTheme.inputMode == InputMode.Touch
    val playing = entries.getOrNull(current)
    // what comes after what plays now; everything when nothing plays
    val first = if (playing != null) current + 1 else 0
    val upcoming = entries.drop(first)
    val title = stringResource(R.string.nag_queue_title)
    BackHandler(enabled = onClose != null && !page) { onClose?.invoke() }

    fun remove(index: Int) {
        val entry = entries.getOrNull(index) ?: return
        actions.remove(index)
        undo?.show(res.getString(R.string.nag_queue_removed, entry.title), res.getString(R.string.nag_sc_undo), ShortcutRegistry.chordLabel(ShortcutId.Undo, res)) {
            actions.restore(index, listOf(entry))
        }
    }

    fun clear() {
        val removed = upcoming
        if (removed.isEmpty()) return
        actions.clearUpcoming()
        undo?.show(
            res.getQuantityString(R.plurals.nag_queue_cleared, removed.size, removed.size),
            res.getString(R.string.nag_sc_undo),
            ShortcutRegistry.chordLabel(ShortcutId.Undo, res),
        ) { actions.restore(first, removed) }
    }

    Column(
        modifier
            .background(background)
            .semantics { paneTitle = title }
            .onKeyEvent { event ->
                // Esc closes the panel, wherever the focus is in it
                val esc = onClose != null && event.key == Key.Escape && event.type == KeyEventType.KeyDown
                if (esc) onClose?.invoke()
                esc
            }
            .testTag("queue-panel"),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (page) dimens.gapXs else dimens.gapL, end = dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page && onClose != null) {
                NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_prefs_back), onClose, modifier = Modifier.testTag("queue-back"))
                Spacer(Modifier.width(dimens.gapXs))
            }
            Text(title, style = NagTheme.type.title, color = colors.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.width(dimens.gapS))
            Text(
                summary(upcoming),
                style = NagTheme.type.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            when (clearShown(upcoming.size, page)) {
                ClearButton.Enabled -> NagButton(stringResource(R.string.nag_queue_clear), ::clear, modifier = Modifier.testTag("queue-clear"))
                ClearButton.Disabled -> NagButton(stringResource(R.string.nag_queue_clear), ::clear, enabled = false, modifier = Modifier.testTag("queue-clear"))
                ClearButton.Hidden -> Unit
            }
            if (onClose != null && !page) NagIconButton(NagIcons.Close, stringResource(R.string.nag_queue_close), onClose)
        }
        HorizontalRule()
        if (playing != null) {
            Text(
                stringResource(R.string.nag_now_playing),
                style = NagTheme.type.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = dimens.gapL, top = dimens.gapS, bottom = 2.dp),
            )
            QueueRow(playing, isPlaying = true, onPlay = { actions.play(current) }, menu = null, handle = null)
            HorizontalRule(Modifier.padding(top = dimens.gapXs))
        }
        if (upcoming.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(dimens.gapL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(NagIcons.Queue, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(dimens.gapS))
                Text(stringResource(R.string.nag_queue_empty_title), style = NagTheme.type.bodyStrong, color = colors.text, textAlign = TextAlign.Center)
                Text(stringResource(R.string.nag_queue_empty_desc), style = NagTheme.type.labelSmall, color = colors.textSecondary, textAlign = TextAlign.Center)
            }
        } else {
            val list = rememberLazyListState()
            val reorder = rememberReorderState(list) { from, to -> actions.move(first + from, first + to) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).reorderList(reorder), state = list) {
                itemsIndexed(upcoming, key = { _, entry -> entry.key }) { position, entry ->
                    val index = first + position
                    val up = res.getString(R.string.nag_queue_move_up)
                    val down = res.getString(R.string.nag_queue_move_down)
                    val moveUp = { if (position > 0) actions.move(index, index - 1) }
                    val moveDown = { if (position < upcoming.lastIndex) actions.move(index, index + 1) }
                    SwipeToRemove(
                        label = stringResource(R.string.nag_queue_remove_short),
                        onRemove = { remove(index) },
                        // by touch the swipe; with a pointer the menu and Delete
                        swipe = touch,
                        color = background,
                        actions = listOfNotNull(
                            CustomAccessibilityAction(up) { moveUp(); true }.takeIf { position > 0 },
                            CustomAccessibilityAction(down) { moveDown(); true }.takeIf { position < upcoming.lastIndex },
                        ),
                        modifier = Modifier
                            .testTag("queue-${entry.key}")
                            .reorderItem(reorder, entry.key, position)
                            .background(background)
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when {
                                    event.key == Key.Delete || event.key == Key.Backspace -> remove(index)
                                    event.isAltPressed && !event.isCtrlPressed && event.key == Key.DirectionUp -> moveUp()
                                    event.isAltPressed && !event.isCtrlPressed && event.key == Key.DirectionDown -> moveDown()
                                    else -> return@onKeyEvent false
                                }
                                true
                            },
                    ) {
                        QueueRow(
                            entry,
                            isPlaying = false,
                            onPlay = { actions.play(index) },
                            menu = {
                                listOfNotNull(
                                    MenuEntry.Item(res.getString(R.string.nag_play), { actions.play(index) }, icon = NagIcons.Play),
                                    MenuEntry.Item(up, moveUp, shortcut = "Alt+↑", enabled = position > 0),
                                    MenuEntry.Item(down, moveDown, shortcut = "Alt+↓", enabled = position < upcoming.lastIndex),
                                    MenuEntry.Separator,
                                    MenuEntry.Item(
                                        res.getString(R.string.nag_queue_remove),
                                        { remove(index) },
                                        shortcut = "Del",
                                        icon = NagIcons.Delete,
                                        destructive = true,
                                    ),
                                )
                            },
                            handle = Modifier.reorderHandle(reorder, entry.key),
                        )
                    }
                }
            }
        }
        if (touch && upcoming.isNotEmpty()) {
            Text(
                stringResource(R.string.nag_queue_hint_touch),
                style = NagTheme.type.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier
                    .padding(dimens.gapM)
                    .fillMaxWidth()
                    .background(colors.surfaceRaised, RoundedCornerShape(dimens.radiusControl))
                    .padding(horizontal = dimens.gapM, vertical = dimens.gapS),
            )
        }
    }
}

/** How "Temizle" shows (1.0.1). */
internal enum class ClearButton { Enabled, Disabled, Hidden }

/**
 * "Temizle" with [upcoming] entries after what plays: pressable while there are any; with none, a
 * phone's [page] leaves it out (its empty state has the page), the panel beside a wide screen keeps
 * it in its place, not pressable.
 */
internal fun clearShown(upcoming: Int, page: Boolean): ClearButton = when {
    upcoming > 0 -> ClearButton.Enabled
    page -> ClearButton.Hidden
    else -> ClearButton.Disabled
}

/** "7 parça · 43 dk"; "öğe" when videos are queued too. */
@Composable
private fun summary(upcoming: List<QueueEntry>): String {
    if (upcoming.isEmpty()) return ""
    val count = if (upcoming.any { it.isVideo }) {
        pluralStringResource(R.plurals.nag_queue_items, upcoming.size, upcoming.size)
    } else {
        pluralStringResource(R.plurals.nag_queue_tracks, upcoming.size, upcoming.size)
    }
    val length = upcoming.sumOf { it.lengthMs.coerceAtLeast(0) }
    return if (length > 0) count + " · " + durationWords(length) else count
}

/** A row of the queue: the artwork, the title, the artist and length; the handle at the end. */
@Composable
private fun QueueRow(entry: QueueEntry, isPlaying: Boolean, onPlay: () -> Unit, menu: (() -> List<MenuEntry>)?, handle: Modifier?) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        Modifier.fillMaxWidth().heightIn(min = dimens.minTarget.coerceAtLeast(52.dp)).padding(horizontal = dimens.gapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContextMenuArea(entries = { menu?.invoke().orEmpty() }, modifier = Modifier.weight(1f), enabled = menu != null,
            accessibilityLabel = stringResource(R.string.nag_more_actions)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimens.minTarget.coerceAtLeast(48.dp))
                    .focusRing(state, shape)
                    .background(if (isPlaying) colors.selection else stateBackground(state), shape)
                    .nagClickable(interaction, onPlay, onClickLabel = entry.title)
                    .padding(horizontal = dimens.gapS, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(entry.media, 40.dp, video = entry.isVideo)
                Spacer(Modifier.width(dimens.gapM))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        style = NagTheme.type.bodyStrong,
                        color = if (isPlaying) colors.onSelection else colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(entry.subtitle, formatDuration(entry.lengthMs)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = NagTheme.type.labelSmall,
                        color = if (isPlaying) colors.onSelection.copy(alpha = 0.8f) else colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (handle != null) {
            val label = stringResource(R.string.nag_queue_handle, entry.title)
            Box(
                Modifier
                    .size(dimens.minTarget.coerceAtLeast(40.dp))
                    .then(handle)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(NagIcons.Menu, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
            }
        }
    }
}

/** Whether "Sıradaki" is open: the bars' button and Ctrl+L open and close it, Esc and Back close it. */
@Stable
class QueuePanelState(open: Boolean = false) {
    var open by mutableStateOf(open)
        private set

    fun toggle() {
        open = !open
    }

    /** Closes the panel; false when it was not open. */
    fun close(): Boolean = open.also { open = false }

    companion object {
        val Saver: Saver<QueuePanelState, Boolean> = Saver(save = { it.open }, restore = { QueuePanelState(it) })
    }
}

@Composable
fun rememberQueuePanelState(): QueuePanelState = rememberSaveable(saver = QueuePanelState.Saver) { QueuePanelState() }

/**
 * 1.0.1: "Sıradaki" on a phone, a page of its own over the whole window, the bars and the mini
 * player included (the shell's [WindowPage]), opaque from its first frame: it comes up from the
 * bottom and goes down again (nothing moves with reduced motion). Back, the predictive gesture
 * (the page shrinks with the finger, as a detail does) and its back arrow close it. While it is
 * open nothing under it reacts to a touch, a scroll or a key ([blockInputBelow]), and once it is
 * up a screen reader reads only it.
 */
@Composable
fun QueuePage(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    undo: UndoBarState? = null,
    /** The page's queue, given the page's modifier: the playback service's; the tests' own. */
    panel: @Composable (Modifier) -> Unit = { pageModifier ->
        val queue = rememberPlayQueue()
        QueuePanel(queue.entries, queue.current, queue, onClose, pageModifier, undo, NagTheme.colors.background, page = true)
    },
) {
    val motion = NagTheme.motion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = open
    AnimatedVisibility(visible, modifier = modifier, enter = pageEnter(motion), exit = motion.sheetExit()) {
        val back = rememberDetailMotion(open = true)
        // up and at rest, no Back gesture moving it: the shell may stop drawing what is under it
        val covers by remember(back, visible) { derivedStateOf { visible.isIdle && visible.currentState && back.covered } }
        WindowPage(covers = covers)
        panel(
            Modifier
                .fillMaxSize()
                .graphicsLayer { with(back) { detailLayer(size.width.roundToInt(), if (rtl) -1f else 1f) } }
                .blockInputBelow()
                .testTag("queue-page"),
        )
        DetailBackHandler(enabled = open, state = back, onBack = onClose)
    }
}

/** The page of [QueuePage] coming up: the whole of it slides up, opaque from the first frame (no fade). */
private fun pageEnter(motion: NagMotion): EnterTransition =
    if (motion.reduced) EnterTransition.None else slideInVertically(tween(NagMotion.LONG, easing = NagMotion.EmphasizedDecelerate)) { it }

/** The panel over the screen: beside it from the end on wide windows, over all of it on a phone. */
@Composable
fun QueueSheet(
    entries: List<QueueEntry>,
    current: Int,
    actions: QueueActions,
    compact: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    undo: UndoBarState? = null,
) {
    val colors = NagTheme.colors
    // 1.0.1: its empty places (the empty state, under the last row) keep the page beneath it still
    Row(modifier.fillMaxHeight().then(if (compact) Modifier.fillMaxWidth() else Modifier.width(380.dp)).blockPointerBelow()) {
        // a hairline between the screen and the panel
        if (!compact) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
        QueuePanel(entries, current, actions, onClose, Modifier.weight(1f).fillMaxHeight(), undo)
    }
}
