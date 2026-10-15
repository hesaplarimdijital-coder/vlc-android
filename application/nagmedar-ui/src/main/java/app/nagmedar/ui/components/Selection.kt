package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme
import kotlin.math.max
import kotlin.math.min

/**
 * A desktop selection over a list (S-405): a click takes one item, Ctrl+click adds or removes one,
 * Shift+click takes the range from the last item clicked (Ctrl+Shift adds it), Ctrl+A takes all
 * and a rubber band in a grid takes what it touches. Keys are the list's own (a uri, an id).
 * By touch (S-310) the item's menu starts [selecting]; taps then take items in and out.
 */
@Stable
class MultiSelection {
    var keys: Set<Any> by mutableStateOf(emptySet())
        private set

    /** Selecting by touch: a tap takes an item in or out rather than playing it, until none is left. */
    var selecting: Boolean by mutableStateOf(false)
        private set

    /** Where a Shift+click range starts: the last item clicked without Shift. */
    var anchor: Any? = null
        private set

    val count: Int get() = keys.size

    operator fun contains(key: Any): Boolean = key in keys

    /** A click on [key] in a list shown in [order]; [ctrl] and [shift] held as on desktops. */
    fun click(key: Any, order: List<Any>, ctrl: Boolean = false, shift: Boolean = false) {
        val from = anchor?.let(order::indexOf) ?: -1
        val to = order.indexOf(key)
        when {
            shift && from >= 0 && to >= 0 -> {
                val range = order.subList(min(from, to), max(from, to) + 1).toSet()
                keys = if (ctrl) keys + range else range
            }
            ctrl -> {
                keys = if (key in keys) keys - key else keys + key
                anchor = key
            }
            else -> {
                keys = setOf(key)
                anchor = key
            }
        }
    }

    /** The menu's "Seç" on [key]: selecting starts with it. */
    fun startSelecting(key: Any) {
        selecting = true
        keys = keys + key
        anchor = key
    }

    /** A tap while selecting: [key] goes in or out; with none left, selecting ends. */
    fun toggle(key: Any) {
        keys = if (key in keys) keys - key else keys + key
        anchor = key
        if (keys.isEmpty()) selecting = false
    }

    fun selectAll(order: List<Any>) {
        keys = order.toSet()
        if (anchor !in keys) anchor = order.firstOrNull()
    }

    /** What a rubber band touches, added to [base] (the selection when Ctrl held). */
    fun select(touched: Set<Any>, base: Set<Any> = emptySet()) {
        keys = base + touched
    }

    fun clear() {
        keys = emptySet()
        anchor = null
        selecting = false
    }

    /** Keeps what is still listed, after a filter or a scan took items away. */
    fun retain(order: Collection<Any>) {
        if (keys.all { it in order }) return
        keys = keys.filterTo(LinkedHashSet()) { it in order }
        if (anchor !in keys) anchor = null
        if (keys.isEmpty()) selecting = false
    }
}

/** An item's mark while selecting by touch (S-310): a ring, filled and ticked when the item is in. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    Box(
        modifier
            .size(26.dp)
            .background(if (selected) colors.accent else colors.scrim, CircleShape)
            .border(2.dp, if (selected) colors.accent else Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(NagIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun rememberMultiSelection(): MultiSelection = remember { MultiSelection() }

/** The keys held when the pointer last pressed: what the next click means for a selection. */
class ClickModifiers {
    private var ctrl = false
    private var shift = false

    internal fun pressed(ctrl: Boolean, shift: Boolean) {
        this.ctrl = ctrl
        this.shift = shift
    }

    /** Ctrl (or Meta) and Shift of the last press, then forgotten: Enter on a focused item has none. */
    fun take(): Pair<Boolean, Boolean> = (ctrl to shift).also { pressed(ctrl = false, shift = false) }
}

/** The window's [ClickModifiers], recorded by [TrackClickModifiers]. */
val LocalClickModifiers = staticCompositionLocalOf { ClickModifiers() }

/** Records, before any child sees a press, the keys held with it (S-405). */
@Composable
fun TrackClickModifiers(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val modifiers = remember { ClickModifiers() }
    CompositionLocalProvider(LocalClickModifiers provides modifiers) {
        Box(
            modifier.pointerInput(modifiers) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            val keys = event.keyboardModifiers
                            modifiers.pressed(ctrl = keys.isCtrlPressed || keys.isMetaPressed, shift = keys.isShiftPressed)
                        }
                    }
                }
            },
        ) { content() }
    }
}

/**
 * Ctrl+A and Esc go to the screen in front (S-405): it says what all is and what Esc lets go of.
 * The menu bar's "Tümünü seç", the palette and Ctrl+A run [selectAll]; an Esc nothing else used
 * runs [escape].
 */
class ScreenCommands {
    internal var selectAllHandler: (() -> Boolean)? = null
    internal var escapeHandler: (() -> Boolean)? = null

    /** S-1301: a browser's "up a folder" (Backspace) and "refresh" (F5), on a screen that has them. */
    internal var upHandler: (() -> Boolean)? = null
    internal var refreshHandler: (() -> Boolean)? = null

    fun selectAll(): Boolean = selectAllHandler?.invoke() ?: false

    fun escape(): Boolean = escapeHandler?.invoke() ?: false

    fun up(): Boolean = upHandler?.invoke() ?: false

    fun refresh(): Boolean = refreshHandler?.invoke() ?: false
}

val LocalScreenCommands = staticCompositionLocalOf { ScreenCommands() }

/** While composed, the window's "up a folder" and "refresh" shortcuts do [onUp] and [onRefresh]; each says whether it did anything. */
@Composable
fun BrowserCommands(onUp: () -> Boolean, onRefresh: () -> Boolean) {
    val commands = LocalScreenCommands.current
    val up by rememberUpdatedState(onUp)
    val refresh by rememberUpdatedState(onRefresh)
    DisposableEffect(commands) {
        val upHandler = { up() }
        val refreshHandler = { refresh() }
        commands.upHandler = upHandler
        commands.refreshHandler = refreshHandler
        onDispose {
            if (commands.upHandler === upHandler) commands.upHandler = null
            if (commands.refreshHandler === refreshHandler) commands.refreshHandler = null
        }
    }
}

/** While composed, Ctrl+A selects the whole of [order] and Esc clears [selection]. */
@Composable
fun SelectionCommands(selection: MultiSelection, order: List<Any>) {
    val commands = LocalScreenCommands.current
    val currentOrder by rememberUpdatedState(order)
    DisposableEffect(commands, selection) {
        val selectAll = {
            selection.selectAll(currentOrder)
            currentOrder.isNotEmpty()
        }
        val escape = {
            val had = selection.count > 0
            selection.clear()
            had
        }
        commands.selectAllHandler = selectAll
        commands.escapeHandler = escape
        onDispose {
            // the next screen may already have its own
            if (commands.selectAllHandler === selectAll) commands.selectAllHandler = null
            if (commands.escapeHandler === escape) commands.escapeHandler = null
        }
    }
}

/**
 * A rubber band over a grid (S-405): the mouse dragged from the space between the items draws a
 * rectangle and selects the items it touches (with Ctrl or Shift, in addition to the selection).
 * Items report where they are with [item]; what else takes clicks in the grid (a shelf) with
 * [keepOut]; the grid takes the gesture with [area].
 */
@Stable
class RubberBand {
    private var container: LayoutCoordinates? = null
    private val items = HashMap<Any, LayoutCoordinates>()
    private val kept = HashMap<Any, LayoutCoordinates>()

    /** The rectangle being drawn, in the grid's coordinates. */
    var rect: Rect? by mutableStateOf(null)
        private set

    private fun boundsOf(coordinates: LayoutCoordinates): Rect? {
        val grid = container ?: return null
        if (!coordinates.isAttached || !grid.isAttached) return null
        return grid.localBoundingBoxOf(coordinates)
    }

    private fun touched(band: Rect): Set<Any> = items.entries.mapNotNullTo(LinkedHashSet()) { (key, coordinates) ->
        key.takeIf { boundsOf(coordinates)?.overlaps(band) == true }
    }

    private fun taken(at: Offset): Boolean = (items.values + kept.values).any { boundsOf(it)?.contains(at) == true }

    /** Where the item of [key] is. */
    fun item(key: Any): Modifier = Modifier.onGloballyPositioned { items[key] = it }

    /** Something in the grid that takes its own clicks and drags: no band starts on it. */
    fun keepOut(id: Any): Modifier = Modifier.onGloballyPositioned { kept[id] = it }

    /** The grid: a mouse drag that starts between items selects with a band. */
    fun area(selection: MultiSelection, enabled: Boolean = true): Modifier = Modifier
        .onGloballyPositioned { container = it }
        .pointerInput(selection, enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(PointerEventPass.Main)
                    val press = down.changes.firstOrNull() ?: continue
                    if (down.type != PointerEventType.Press || press.type != PointerType.Mouse || !down.buttons.isPrimaryPressed) continue
                    if (press.isConsumed || taken(press.position)) continue
                    val start = press.position
                    val add = down.keyboardModifiers.isCtrlPressed || down.keyboardModifiers.isShiftPressed
                    val base = if (add) selection.keys else emptySet()
                    if (!add) selection.clear()
                    while (true) {
                        // before the grid's own scrolling sees the drag
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val drag = event.changes.firstOrNull { it.id == press.id } ?: break
                        if (!drag.pressed) break
                        drag.consume()
                        val band = Rect(
                            Offset(min(start.x, drag.position.x), min(start.y, drag.position.y)),
                            Offset(max(start.x, drag.position.x), max(start.y, drag.position.y)),
                        )
                        rect = band
                        selection.select(touched(band), base)
                    }
                    rect = null
                }
            }
        }
}

@Composable
fun rememberRubberBand(): RubberBand = remember { RubberBand() }

/** Draws [band]'s rectangle over the content. */
@Composable
fun Modifier.rubberBandOverlay(band: RubberBand): Modifier {
    val fill = NagTheme.colors.accent.copy(alpha = 0.14f)
    val edge = NagTheme.colors.accent
    return drawWithContent {
        drawContent()
        val rect = band.rect ?: return@drawWithContent
        drawRect(fill, rect.topLeft, rect.size)
        drawRect(edge, rect.topLeft, rect.size, style = Stroke(width = 1.dp.toPx()))
    }
}

/**
 * The selection bar (S-405): how many are selected, what can be done with them all, and a way to
 * let go of them. It shows above the list while more than one item is selected.
 */
@Composable
fun SelectionBar(text: String, onClear: () -> Unit, clearLabel: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTarget.coerceAtLeast(44.dp))
            .background(colors.selection, shape)
            .padding(horizontal = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NagIconButton(NagIcons.Close, clearLabel, onClear, tint = colors.onSelection)
        Text(text, style = NagTheme.type.bodyStrong, color = colors.onSelection, maxLines = 1, modifier = Modifier.weight(1f))
        actions()
    }
}
