package app.nagmedar.ui.components

import android.os.Parcelable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.nagmedar.ui.R
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.input.TabNavigation
import app.nagmedar.ui.theme.GlassMode
import app.nagmedar.ui.theme.GlassThickness
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagGlass
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.glass
import app.nagmedar.ui.theme.glassSource
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.isCtrlPressed as pointerCtrl
import androidx.compose.ui.input.pointer.isMetaPressed as pointerMeta
import androidx.compose.ui.input.pointer.isShiftPressed as pointerShift

/** Width of a table column: a share of the free space or a fixed size. */
sealed interface ColumnWidth {
    data class Weight(val weight: Float) : ColumnWidth
    data class Fixed(val width: Dp) : ColumnWidth
}

@Immutable
class TableColumn<T>(
    val title: String,
    val width: ColumnWidth,
    val alignEnd: Boolean = false,
    /** Monospace for durations, formats and sizes. */
    val mono: Boolean = false,
    val sortable: Boolean = true,
    /** What the cell shows instead of [value]'s text (S-808: the rating's stars); [value] is still its text. */
    val content: (@Composable (item: T, selected: Boolean) -> Unit)? = null,
    /** The column's stable name (Desktop Kit 1.0): what a kept order or choice of columns refers to; its title by default. */
    val id: String = title,
    /**
     * 1.0.8 (Desktop Kit 2.8.0): where a value too long for the cell is cut: at its end (the default) or, with
     * [TextOverflow.MiddleEllipsis], in its middle, so a file name keeps its extension and a path its last folder.
     */
    val ellipsis: TextOverflow = TextOverflow.Ellipsis,
    /**
     * 1.0.8 (Desktop Kit 2.8.0): makes the cell a button of its own that does not open the row (a star toggled in place),
     * with hover, press and focus; [cellClickLabel] names what it does for TalkBack.
     */
    val onCellClick: ((T) -> Unit)? = null,
    val cellClickLabel: ((T) -> String)? = null,
    val value: (T) -> String,
)

@Immutable
data class TableSort(val column: Int, val ascending: Boolean)

/** 1.0.8: the order a table last laid its rows out in ([DenseTable]'s order); plain, so keeping it redraws nothing. */
private class OrderSeen(var order: Any?)

/**
 * A table's sort kept by its column's [id] rather than its place: a narrower window takes columns away and a wider
 * one brings them back, and the sort stays with its column; while that column is not shown nothing is sorted by it.
 */
@Immutable
data class ColumnSort(val id: String, val ascending: Boolean) {
    /** The sort among the columns shown now, [ids] in their order; null while its column is not one of them. */
    fun among(ids: List<String>): TableSort? = ids.indexOf(id).takeIf { it >= 0 }?.let { TableSort(it, ascending) }

    companion object {
        /** [sort] of a table showing the columns [ids], by its column's id. */
        fun of(sort: TableSort, ids: List<String>): ColumnSort? = ids.getOrNull(sort.column)?.let { ColumnSort(it, sort.ascending) }

        /** Kept with the window's state as "+id" (ascending) or "-id". */
        val StateSaver: Saver<ColumnSort?, String> = Saver(
            save = { sort -> sort?.let { (if (it.ascending) "+" else "-") + it.id } },
            restore = { saved -> saved.takeIf { it.length > 1 && (it[0] == '+' || it[0] == '-') }?.let { ColumnSort(it.substring(1), it[0] == '+') } },
        )
    }
}

/**
 * Dense, desktop-style table (S-202): sticky header with sortable columns, 32 dp rows in
 * pointer mode (taller in touch mode), hover and selection states, keyboard focus per row.
 * [rowMenu] gives every row a context menu (right click, long press, Menu key). With [reorder]
 * (made on the same [state]) each row starts with a handle that drags it to another place (S-804).
 *
 * Desktop Kit 1.0: with [glassHeader] the rows scroll beneath the header, an opaque band of the
 * page's colour (frosted glass once they pass beneath it, where the shell's [GlassMode] allows glass); [headerMenu] is each column title's context menu (the column chooser), told
 * which column it was opened on; [onExtendSelection] takes Shift+↑ and Shift+↓ on a focused row
 * (the row and the step, true when the selection grew) and the focus follows.
 *
 * 1.0.8 (Desktop Kit 2.8.0, the design audit's high findings 2 and 3, DeskMail's requests 2 and 22):
 * - the rows are one Tab stop: Tab reaches the focused row, else the selection's anchor or the selected row when it is in
 *   view, else the first row in view, and the next Tab leaves the table at once ([TabHop]); ↑ and ↓ move the focus row
 *   by row, Home and End to the first and the last row, Page Up and Page Down by a screenful, each scrolling the row into
 *   view. A focus asked for any other way (a click, the app) still reaches every row; a cell's button and a row's "⋯"
 *   are reached from their row, not by Tab;
 * - the column titles come before the rows in the Tab order (drawn over them, as before); each is a heading, the sorted
 *   one says which way it sorts and a sortable one what a tap does;
 * - with [selection], a mouse dragged from a row that is not selected, or from below the rows, draws a frame in the
 *   accent colour: the rows it touches are selected, Ctrl toggles them in the selection there was, Shift adds them; near
 *   the top or the bottom the list scrolls along; Esc drops the frame and puts the selection back. A selected row is
 *   left to its drag (the row's drag source starts at once for a mouse). While a finger is selecting
 *   ([MultiSelection.selecting]) every row has a check box and the header one for them all;
 * - a selected row has a bar at its start as well as its colour; the header is as tall as a row;
 * - [rowEmphasis] sets rows apart in bold and in the text colour; [emptyContent] is shown under the header while there
 *   are no rows (why, and what to do);
 * - a scrollbar for the mouse along the end edge ([verticalScrollbar]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> DenseTable(
    columns: List<TableColumn<T>>,
    rows: List<T>,
    key: (T) -> Any,
    onRowClick: (T, Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    selectedKey: Any? = null,
    /** Several rows selected (S-405); [selectedKey] counts when null; [selection]'s when both are. */
    selectedKeys: Set<Any>? = null,
    sort: TableSort? = null,
    onSortChange: ((TableSort) -> Unit)? = null,
    rowMenu: ((T, Int) -> List<MenuEntry>)? = null,
    rowMenuLabel: String? = null,
    /** S-1601: where the input is touch, a row's menu is a sheet at the bottom of the window. */
    rowMenuAsSheet: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(bottom = 8.dp),
    /** With a double-click action, a click only selects (a pointer's way: select, then open). */
    onRowDoubleClick: ((T, Int) -> Unit)? = null,
    /** More for a row: a drag source, a drop target (S-406). */
    rowModifier: (T) -> Modifier = { Modifier },
    /** The column titles' context menu: the columns to show and their order (S-1001); gets the column's index. */
    headerMenu: ((column: Int) -> List<MenuEntry>)? = null,
    headerMenuLabel: String? = null,
    /** Rows moved by their handles; its list must be [state]. */
    reorder: ReorderState? = null,
    /** What a row's handle says to a screen reader ("Neva Kâr: sürükleyerek taşı"). */
    handleLabel: (T) -> String = { "" },
    /**
     * Desktop Kit 1.0: each row ends with a "⋯" button opening [rowMenu]'s entries in a [MoreSheet]
     * (a phone's) under [rowMoreTitle]; the same entries as the long press.
     */
    rowMoreButton: Boolean = false,
    rowMoreTitle: (T) -> String? = { null },
    glassHeader: Boolean = false,
    onExtendSelection: ((focused: T, delta: Int) -> Boolean)? = null,
    /**
     * 1.0: an item over everything (a phone's shelf of the tracks played last) that scrolls away with the rows; the
     * column titles come under it and then stay at the top. Not with [reorder] or [glassHeader].
     */
    leading: (@Composable () -> Unit)? = null,
    /** 1.0.3 (Desktop Kit 2.3.0): the first rows arrive in turn on a phone ([rememberListAppear]); null: they are there. */
    rowAppear: ListAppear? = null,
    /**
     * 1.0.3: whether [leading] has something to show now. One that gets its content after the rows are on screen (the
     * shelf's history, read once the kept list is drawn) had no height when the list was laid out: the list then takes
     * the column titles for its first item and keeps them at the top, the leading item out of sight above them. Told
     * here that it has come, a list still resting at its top shows it; one the user has scrolled stays where it is.
     */
    leadingPresent: (() -> Boolean)? = null,
    /** 1.0.8: the selection a mouse's frame selects into and a finger's check boxes show (keys as [key] gives them). */
    selection: MultiSelection? = null,
    /** 1.0.8: rows set apart in bold and in the text colour throughout. */
    rowEmphasis: ((T) -> Boolean)? = null,
    /** 1.0.8: shown under the header while there are no rows: why, and what to do (otherwise an empty table is its header). */
    emptyContent: (@Composable () -> Unit)? = null,
    /**
     * 1.0.8: what the rows are ordered by ([sort] unless the screen orders them some other way too): another order
     * starts the rows at the top.
     */
    order: Any? = sort,
) {
    // 1.0.8: another order starts the rows at the top. Keyed rows keep the one first in view in view, wherever the new
    // order puts it: a drive sorted by size kept its first folder by name (Alarms) in view, last in that order, and
    // showed the end of its list, the folders now first (Music among them) out of sight above (the emulator walk's
    // workstation pass, seen in 1.0.7 and on both tablets of 1.0.8's). Before the rows are laid out in their new order,
    // so no frame shows the other place.
    val seenOrder = remember { OrderSeen(order) }
    SideEffect {
        if (seenOrder.order != order) {
            seenOrder.order = order
            state.requestScrollToItem(0)
        }
    }
    val lead = leading.takeIf { reorder == null && !glassHeader }
    if (lead != null && leadingPresent != null) {
        LaunchedEffect(state, leadingPresent) {
            snapshotFlow { leadingPresent() }.collect { present ->
                // at its top: the leading item (0) or, while that had no height, the column titles (1)
                if (present && state.firstVisibleItemIndex <= 1 && state.firstVisibleItemScrollOffset == 0 && !state.isScrollInProgress) {
                    state.scrollToItem(0)
                }
            }
        }
    }
    val more = rowMoreButton && rowMenu != null
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val focus = remember { TableFocus() }
    val hop = remember { TabHop() }
    val extend by rememberUpdatedState(onExtendSelection)
    val widths = fittedWidths(columns, sortable = onSortChange != null)
    val chosen = selectedKeys ?: selection?.keys
    // the select mode of a finger: a check box on every row and one for them all in the header
    val checks = selection?.selecting == true
    val checkWidth = if (NagTheme.inputMode == InputMode.Touch) 48.dp else 32.dp
    val selectAllLabel = stringResource(R.string.nag_sc_select_all)
    // the list's own keys: kept in a Bundle and unique; the caller's keys still say what is selected
    val callerKeys = remember(rows, key) { rows.map(key) }
    val itemKeys = remember(callerKeys) { lazyKeys(callerKeys) }
    val rowIndex = remember(itemKeys) { HashMap<Any, Int>(itemKeys.size * 2).also { map -> itemKeys.forEachIndexed { i, k -> map[k] = i } } }
    val callerIndex = remember(callerKeys) { HashMap<Any, Int>(callerKeys.size * 2).also { map -> callerKeys.forEachIndexed { i, k -> map.putIfAbsent(k, i) } } }
    // read through these in the key and pointer handlers and the focus properties, which outlive a composition
    val latestRows = rememberUpdatedState(rows)
    val latestItemKeys = rememberUpdatedState(itemKeys)
    val latestCallerKeys = rememberUpdatedState(callerKeys)
    val latestRowIndex = rememberUpdatedState(rowIndex)
    val latestCallerIndex = rememberUpdatedState(callerIndex)
    val latestSelectedKey = rememberUpdatedState(selectedKey)
    val latestChosen = rememberUpdatedState(chosen)
    val latestSelection = rememberUpdatedState(selection)
    // the leading item and the column titles come before the rows in the list
    val latestLeadItems = rememberUpdatedState(if (lead != null) 2 else 0)
    val rowFocus = remember { HashMap<Any, FocusRequester>() }
    val scope = rememberCoroutineScope()
    val frame = remember { TableFrame() }
    val frameColor = colors.accent
    // the reorder handles take their own drags: a frame never starts on them (from the rows' start to the handles' end)
    val handlesEnd = if (reorder == null) 0f else with(density) { (dimens.pagePadding - 12.dp + 4.dp + (if (checks) checkWidth else 0.dp) + handleWidth()).toPx() }
    val latestHandlesEnd = rememberUpdatedState(handlesEnd)
    val latestRtl = rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)

    /** Where the rows start being seen in the list: under the column titles held at its top (a list with a leading item). */
    fun coveredTop(info: LazyListLayoutInfo): Int {
        if (latestLeadItems.value == 0) return 0
        val titles = info.visibleItemsInfo.firstOrNull { it.key == HEADER_KEY } ?: return 0
        return (titles.offset + titles.size).coerceAtLeast(0)
    }

    fun listKeyOf(callerKey: Any?): Any? = callerKey?.let { latestCallerIndex.value[it] }?.let { latestItemKeys.value.getOrNull(it) }

    /**
     * The one row Tab reaches: the focused one, else the anchor of the selection or the selected one, when it is in
     * view; else the first row in view. Read when the focus moves, not composed.
     */
    fun rovingKey(): Any? {
        val index = latestRowIndex.value
        val visible = state.layoutInfo.visibleItemsInfo
        val active = focus.key ?: listKeyOf(latestSelection.value?.anchor) ?: listKeyOf(latestSelectedKey.value) ?: listKeyOf(latestChosen.value?.firstOrNull())
        return if (active != null && active in index && visible.any { it.key == active }) active else visible.firstOrNull { it.key in index }?.key
    }

    /** Moves the keyboard's focus to the row at [index], scrolling it into view. */
    fun focusRow(index: Int) {
        val keys = latestItemKeys.value
        if (keys.isEmpty()) return
        val at = index.coerceIn(0, keys.size - 1)
        val target = keys[at]
        focus.key = target
        scope.launch {
            val info = state.layoutInfo
            val shown = info.visibleItemsInfo.firstOrNull { it.key == target }
            val limit = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
            val top = coveredTop(info)
            when {
                shown == null -> {
                    val first = info.visibleItemsInfo.firstOrNull { it.key in latestRowIndex.value }?.key?.let { latestRowIndex.value[it] } ?: 0
                    val below = at > first
                    state.scrollToItem(at + latestLeadItems.value)
                    val after = state.layoutInfo
                    val row = after.visibleItemsInfo.firstOrNull { it.key == target }
                    if (row != null) {
                        if (below) {
                            // going down, the row comes in at the bottom, as a list moved by the keys does (at the end of the
                            // list it is there already: the list cannot scroll further)
                            val past = row.offset - (limit - row.size)
                            if (past < 0) state.scrollBy(past.toFloat())
                        } else {
                            val cover = coveredTop(after)
                            if (row.offset < cover) state.scrollBy((row.offset - cover).toFloat())
                        }
                    }
                }
                shown.offset < top -> state.scrollBy((shown.offset - top).toFloat())
                shown.offset + shown.size > limit -> state.scrollBy((shown.offset + shown.size - limit).toFloat())
            }
            withFrameNanos { }
            runCatching { rowFocus[target]?.requestFocus() }
        }
    }

    /** The rows that fit in view, for Page Up and Page Down. */
    fun pageRows(): Int = state.layoutInfo.visibleItemsInfo.count { it.key in latestRowIndex.value }.coerceAtLeast(2) - 1

    /** The row under [y] (in the list): -1 for the one before the first row in view, past the last one below it. */
    fun indexAt(y: Float): Int {
        val index = latestRowIndex.value
        val info = state.layoutInfo
        val at = y - info.beforeContentPadding
        val rowsInView = info.visibleItemsInfo.filter { it.key in index }
        val hit = rowsInView.firstOrNull { at >= it.offset && at < it.offset + it.size }
        return when {
            hit != null -> index.getValue(hit.key)
            rowsInView.isEmpty() -> latestItemKeys.value.size
            at < rowsInView.first().offset -> index.getValue(rowsInView.first().key) - 1
            else -> index.getValue(rowsInView.last().key) + 1
        }
    }

    /** The frame from its start to the row at [index]: alone, toggled into the selection there was ([toggle]) or added to it ([add]). */
    fun frameSelect(index: Int, toggle: Boolean, add: Boolean) {
        val target = latestSelection.value ?: return
        val keys = latestCallerKeys.value
        if (keys.isEmpty()) return
        val first = minOf(frame.startIndex, index).coerceAtLeast(0)
        val last = maxOf(frame.startIndex, index).coerceAtMost(keys.size - 1)
        val covered = if (first > last) emptySet() else keys.subList(first, last + 1).toCollection(LinkedHashSet())
        target.frame(covered, frame.before, toggle = toggle, add = add, anchor = keys[frame.startIndex.coerceIn(0, keys.size - 1)])
    }

    val header = @Composable { modifier: Modifier ->
        Column(modifier.coversRowsBeneath()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 1.0.8 (Desktop Kit 2.8.0): as tall as a row: the header reads as the first line of the table
                    .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
                    .padding(horizontal = dimens.pagePadding - 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (checks) {
                    val all = rows.isNotEmpty() && callerKeys.all { it in (chosen ?: emptySet()) }
                    val checkState = when {
                        all -> ToggleableState.On
                        chosen.isNullOrEmpty() -> ToggleableState.Off
                        else -> ToggleableState.Indeterminate
                    }
                    Box(Modifier.width(checkWidth), contentAlignment = Alignment.Center) {
                        NagCheckMark(
                            state = checkState,
                            onClick = { if (all) selection?.clear() else selection?.selectAll(callerKeys) },
                            modifier = Modifier.semantics { contentDescription = selectAllLabel },
                        )
                    }
                }
                if (reorder != null) Spacer(Modifier.width(handleWidth()))
                val rest = if (glassHeader) Color.Transparent else colors.background
                columns.forEachIndexed { index, column ->
                    if (headerMenu != null) {
                        ContextMenuArea(entries = { headerMenu(index) }, modifier = Modifier.cell(this, widths[index]), accessibilityLabel = headerMenuLabel) {
                            Row { HeaderCell(column, index, sort, onSortChange, rest, Modifier.weight(1f)) }
                        }
                    } else {
                        HeaderCell(column, index, sort, onSortChange, rest, Modifier.cell(this, widths[index]))
                    }
                }
                if (more) Spacer(Modifier.width(moreWidth()))
            }
            HorizontalRule()
            // 1.0.8: Shift+Tab from the rows comes here and goes on to the titles (it is held at the top with them)
            HopSpot(hop, hop.before)
        }
    }
    val rowsGlass = remember { HazeState() }
    val glassMode = NagGlass.mode
    // with a glass header the rows start under it; its height is known after the first layout
    val headerPx = remember { mutableIntStateOf(with(density) { (dimens.rowHeight.coerceAtLeast(dimens.minTarget) + 1.dp).roundToPx() }) }
    val padding = remember(contentPadding, density, glassHeader) {
        if (glassHeader) HeaderPadding(contentPadding, headerPx, density) else contentPadding
    }
    val scrolled by remember(state) { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 } }
    // 1.0.8 (Desktop Kit 2.8.0, DeskMail's request 22): the selection frame of a mouse, on the list itself
    val framing = if (selection == null) Modifier else Modifier
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // a press the scrollbar took is the scrollbar's
                if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed || down.isConsumed) return@awaitEachGesture
                val target = latestSelection.value ?: return@awaitEachGesture
                val handles = latestHandlesEnd.value
                if (handles > 0f && (if (latestRtl.value) down.position.x > size.width - handles else down.position.x < handles)) return@awaitEachGesture
                val info = state.layoutInfo
                val at = down.position.y - info.beforeContentPadding
                // not on the column titles (over the rows or held at the top)
                if (at < coveredTop(info)) return@awaitEachGesture
                val rowsInView = info.visibleItemsInfo.filter { it.key in latestRowIndex.value }
                if (rowsInView.isEmpty()) return@awaitEachGesture
                val onRow = rowsInView.any { at >= it.offset && at < it.offset + it.size }
                val belowRows = at >= rowsInView.last().offset + rowsInView.last().size
                // on a row or below the rows: not on the leading item or the empty state
                if (!onRow && !belowRows) return@awaitEachGesture
                val startIndex = indexAt(down.position.y)
                val startKey = latestCallerKeys.value.getOrNull(startIndex)
                // a selected row drags the selection instead
                if (startKey != null && startKey in target) return@awaitEachGesture
                val mods = currentEvent.keyboardModifiers
                val toggle = mods.pointerCtrl || mods.pointerMeta
                val add = mods.pointerShift
                val slop = viewConfiguration.touchSlop
                // a click stays a click; the frame starts once the mouse has moved
                var moved: Offset
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!change.pressed) return@awaitEachGesture
                    if ((change.position - down.position).getDistance() > slop) {
                        change.consume()
                        moved = change.position
                        break
                    }
                }
                val rowInfo = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == latestItemKeys.value.getOrNull(startIndex) }
                frame.start(startIndex, down.position, at - (rowInfo?.offset ?: 0), target.keys, target.anchor)
                // Esc reaches the table: the focus goes to the row the frame started on
                (latestItemKeys.value.getOrNull(startIndex) ?: rovingKey())?.let { first ->
                    focus.key = first
                    runCatching { rowFocus[first]?.requestFocus() }
                }
                // the move that started the frame already selects what it reaches
                frame.pointer = moved
                frameSelect(indexAt(moved.y), toggle, add)
                val edge = 32.dp.toPx()

                // near the rows' top or bottom edge the list scrolls along, and the frame with it; only there: frames are
                // asked for while the pointer is at an edge, not all the time it is down
                fun speedAt(y: Float): Float {
                    val top = (state.layoutInfo.beforeContentPadding + coveredTop(state.layoutInfo)).toFloat()
                    val bottom = size.height.toFloat()
                    return when {
                        y < top + edge -> -(top + edge - y).coerceAtMost(edge) / 2f
                        y > bottom - edge -> (y - (bottom - edge)).coerceAtMost(edge) / 2f
                        else -> 0f
                    }
                }
                var scrolling: Job? = null
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        frame.pointer = change.position
                        if (!frame.cancelled) frameSelect(indexAt(change.position.y), toggle, add)
                        if (speedAt(change.position.y) != 0f && scrolling?.isActive != true) {
                            scrolling = scope.launch {
                                while (true) {
                                    withFrameNanos { }
                                    val y = frame.pointer.y
                                    val speed = speedAt(y)
                                    if (speed == 0f || frame.cancelled) break
                                    state.dispatchRawDelta(speed)
                                    frameSelect(indexAt(y), toggle, add)
                                }
                            }
                        }
                    }
                } finally {
                    scrolling?.cancel()
                    frame.end()
                }
            }
        }
        .drawWithContent {
            drawContent()
            if (frame.active && !frame.cancelled) {
                val info = state.layoutInfo
                val top = (info.beforeContentPadding + coveredTop(info)).toFloat()
                val rect = frame.rect(info, latestItemKeys.value, latestRowIndex.value, top, size.height)
                drawRect(frameColor.copy(alpha = 0.12f), rect.topLeft, rect.size)
                drawRect(frameColor, rect.topLeft, rect.size, style = Stroke(1.dp.toPx()))
            }
        }
    val list = @Composable { modifier: Modifier ->
        LazyColumn(
            state = state,
            modifier = modifier
                // before the frame: a press on the scrollbar is the scrollbar's
                .verticalScrollbar(state)
                .then(framing)
                .then(if (reorder != null) Modifier.reorderList(reorder) else Modifier),
            contentPadding = padding,
        ) {
            if (lead != null) {
                item(key = LEADING_KEY, contentType = LEADING_KEY) { lead() }
                stickyHeader(key = HEADER_KEY, contentType = HEADER_KEY) { header(Modifier.fillMaxWidth().background(colors.background)) }
            }
            if (rows.isEmpty() && emptyContent != null) {
                item(key = EMPTY_KEY, contentType = EMPTY_KEY) {
                    Box(Modifier.fillParentMaxWidth().fillParentMaxHeight(0.8f), contentAlignment = Alignment.Center) { emptyContent() }
                }
            }
            itemsIndexed(rows, key = { index, _ -> itemKeys[index] }) { index, item ->
                // the reorder state finds its rows among the list's items: by the list's keys
                val itemKey = itemKeys[index]
                val requester = remember { FocusRequester() }
                DisposableEffect(itemKey, requester) {
                    rowFocus[itemKey] = requester
                    onDispose { if (rowFocus[itemKey] === requester) rowFocus.remove(itemKey) }
                }
                // the dragged row rises over the others: its outermost layout carries it
                val placed = if (reorder != null) Modifier.reorderItem(reorder, itemKey, index) else Modifier
                val place = if (rowAppear != null) Modifier.listAppear(rowAppear, index).then(placed) else placed
                val focusing = Modifier
                    .focusRequester(requester)
                    // 1.0.8: one Tab stop: Tab reaches only the current row (the arrows reach every one)
                    .focusProperties { canFocus = !TabNavigation.active || itemKey == rovingKey() }
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) focus.key = itemKey else if (focus.key == itemKey) focus.key = null
                        if (focusState.hasFocus) focus.within = itemKey else if (focus.within == itemKey) focus.within = null
                        hop.inside = focus.within != null
                    }
                val callerKey = callerKeys[index]
                val row = @Composable { outer: Modifier ->
                    TableRow(
                        columns = columns,
                        widths = widths,
                        item = item,
                        selected = chosen?.contains(callerKey) ?: (selectedKey != null && callerKey == selectedKey),
                        onClick = { onRowClick(item, index) },
                        onDoubleClick = onRowDoubleClick?.let { open -> { open(item, index) } },
                        modifier = outer.then(focusing).then(rowModifier(item)),
                        handle = reorder?.let { Modifier.reorderHandle(it, itemKey) },
                        handleLabel = if (reorder != null) handleLabel(item) else "",
                        trailing = if (more && rowMenu != null) ({
                            MoreButton(groups = { rowMenu(item, index).toActionGroups() }, title = rowMoreTitle(item))
                        }) else null,
                        emphasized = rowEmphasis?.invoke(item) == true,
                        checkWidth = if (checks) checkWidth else null,
                    )
                }
                if (rowMenu != null) {
                    ContextMenuArea(entries = { rowMenu(item, index) }, modifier = place, accessibilityLabel = rowMenuLabel, asSheet = rowMenuAsSheet) { row(Modifier) }
                } else {
                    row(place)
                }
            }
        }
    }
    val keys = Modifier
        .onPreviewKeyEvent { event ->
            // Tab leaves the rows at once, wherever in them the focus is (a row, a cell's button): the rows are one stop
            if (hop.handle(event, focusManager) { rovingKey()?.let { key -> runCatching { rowFocus[key]?.requestFocus() } } }) {
                return@onPreviewKeyEvent true
            }
            // the rows are one Tab stop: the arrows, Home, End and the page keys move the focus among them. Taken before
            // the list sees them: its own scrolling takes the page keys and would leave the focus behind
            val focusedIndex = focus.key?.let { latestRowIndex.value[it] } ?: -1
            if (event.type != KeyEventType.KeyDown || focusedIndex < 0 || event.isAltPressed || event.isShiftPressed ||
                event.isCtrlPressed || event.isMetaPressed
            ) {
                return@onPreviewKeyEvent false
            }
            when (event.key) {
                Key.DirectionDown -> { focusRow(focusedIndex + 1); true }
                Key.DirectionUp -> { focusRow(focusedIndex - 1); true }
                Key.MoveHome -> { focusRow(0); true }
                Key.MoveEnd -> { focusRow(latestItemKeys.value.size - 1); true }
                Key.PageDown -> { focusRow(focusedIndex + pageRows()); true }
                Key.PageUp -> { focusRow(focusedIndex - pageRows()); true }
                else -> false
            }
        }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            if (event.key == Key.Escape && frame.active && !frame.cancelled) {
                // the frame is dropped and the selection is as it was before it
                frame.cancel()
                latestSelection.value?.restore(frame.before, frame.anchorBefore)
                return@onKeyEvent true
            }
            val grow = extend ?: return@onKeyEvent false
            if (!event.isShiftPressed || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return@onKeyEvent false
            val delta = when (event.key) {
                Key.DirectionUp -> -1
                Key.DirectionDown -> 1
                else -> return@onKeyEvent false
            }
            val at = focus.key?.let { latestRowIndex.value[it] } ?: return@onKeyEvent false
            val row = latestRows.value.getOrNull(at) ?: return@onKeyEvent false
            if (grow(row, delta)) focusRow(at + delta)
            true
        }
    if (glassHeader) {
        // the header lies over the rows: opaque, the page's colour, whether rows pass beneath it or not (1.0.1); where
        // the shell lets glass be glass, it turns to glass once they do. 1.0.8: composed before the rows, so its sort
        // buttons come before them in the Tab order as they do on screen; drawn over them
        Box(modifier.fillMaxSize().then(keys)) {
            header(
                Modifier
                    .zIndex(1f)
                    .fillMaxWidth()
                    .onSizeChanged { headerPx.intValue = it.height }
                    .then(
                        if (glassMode == GlassMode.Solid) Modifier.background(colors.background)
                        else Modifier.glass(NagGlass.chrome(colors, GlassThickness.Thin), source = rowsGlass, mode = glassMode, enabled = scrolled, rest = colors.background),
                    ),
            )
            list(Modifier.fillMaxSize().glassSource(rowsGlass, glassMode))
            HopSpot(hop, hop.after)
        }
    } else if (lead != null) {
        // the titles are in the list, under the leading item
        Box(modifier.fillMaxSize().then(keys)) {
            list(Modifier.fillMaxSize())
            HopSpot(hop, hop.after)
        }
    } else {
        Column(modifier.fillMaxSize().then(keys)) {
            header(Modifier.fillMaxWidth())
            list(Modifier.fillMaxSize())
            HopSpot(hop, hop.after)
        }
    }
}

/** The keys of a table's leading item, its column titles and its empty state in the list (saved with the list's state, so strings). */
private const val LEADING_KEY = "\u0000leading"
private const val HEADER_KEY = "\u0000header"
private const val EMPTY_KEY = "\u0000empty"

/**
 * 1.0.1: the lazy list's keys for rows of the caller's [keys], in order. A lazy list keeps its items'
 * state in the window's Bundle under their keys and ends the app on a key a Bundle cannot hold (a
 * plain data class: Bu cihaz's locations) or on one used twice. A key a Bundle holds as it is (a
 * string, a number, an enum, a Parcelable) stays; another becomes its text ("\u0001" first, so no
 * caller's string or the table's own keys are the same); a key met again becomes its text with how
 * many times it was met ("\u0002"…). The same keys in the same order give the same list.
 */
internal fun lazyKeys(keys: List<Any>): List<Any> {
    val used = HashSet<Any>(keys.size * 2)
    val seen = HashMap<Any, Int>()
    return keys.map { key ->
        val kept = if (bundleKey(key)) key else "\u0001$key"
        if (used.add(kept)) {
            kept
        } else {
            var n = seen[kept] ?: 1
            var unique: String
            do {
                n++
                unique = "\u0002$n\u0002$kept"
            } while (!used.add(unique))
            seen[kept] = n
            unique
        }
    }
}

/** A key a Bundle holds as it is, and equal again once restored. */
private fun bundleKey(key: Any): Boolean = when (key) {
    is String, is Int, is Long, is Short, is Byte, is Char, is Boolean, is Float, is Double, is Enum<*>, is Parcelable -> true
    else -> false
}

/**
 * The table's keyboard focus (1.0.8): the row that has it ([key], by the list's keys) and the row it is in ([within]:
 * the row or a cell's button). Plain: nothing redraws from it.
 */
private class TableFocus {
    var key: Any? = null
    var within: Any? = null
}

/**
 * 1.0.8 (Desktop Kit 2.8.0): a selection frame being drawn with the mouse: where it started (the row and the point within
 * it, so the frame stays with the rows as they scroll), where the pointer is, and the selection there was before it.
 */
private class TableFrame {
    var active by mutableStateOf(false)
        private set
    var cancelled by mutableStateOf(false)
        private set
    var pointer by mutableStateOf(Offset.Zero)
    var startIndex = 0
        private set
    private var startX = 0f
    private var startInRow = 0f

    /** The selection and its anchor when the frame started, to toggle and add into and to put back with Esc. */
    var before: Set<Any> = emptySet()
        private set
    var anchorBefore: Any? = null
        private set

    fun start(index: Int, at: Offset, inRow: Float, selection: Set<Any>, anchor: Any?) {
        startIndex = index
        startX = at.x
        startInRow = inRow
        pointer = at
        before = selection
        anchorBefore = anchor
        cancelled = false
        active = true
    }

    /** Esc: the frame is dropped. */
    fun cancel() {
        cancelled = true
    }

    fun end() {
        active = false
        cancelled = false
    }

    /** The frame in the list's coordinates: its start follows its row, clamped to the rows' area. */
    fun rect(info: LazyListLayoutInfo, keys: List<Any>, index: Map<Any, Int>, top: Float, bottom: Float): Rect {
        val key = keys.getOrNull(startIndex)
        val row = info.visibleItemsInfo.firstOrNull { it.key == key }
        val rowsInView = info.visibleItemsInfo.filter { it.key in index }
        val startY = when {
            row != null -> info.beforeContentPadding + row.offset + startInRow
            rowsInView.isNotEmpty() && startIndex < index.getValue(rowsInView.first().key) -> top
            else -> bottom
        }.coerceIn(top, bottom)
        val y = pointer.y.coerceIn(top, bottom)
        return Rect(minOf(startX, pointer.x), minOf(startY, y), maxOf(startX, pointer.x), maxOf(startY, y))
    }
}

/** The caller's padding with the glass header's height added on top: the first row starts under it. */
private class HeaderPadding(private val base: PaddingValues, private val headerPx: MutableIntState, private val density: Density) : PaddingValues {
    override fun calculateTopPadding() = base.calculateTopPadding() + with(density) { headerPx.intValue.toDp() }
    override fun calculateBottomPadding() = base.calculateBottomPadding()
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = base.calculateLeftPadding(layoutDirection)
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = base.calculateRightPadding(layoutDirection)
}

@Composable
private fun <T> HeaderCell(column: TableColumn<T>, index: Int, sort: TableSort?, onSortChange: ((TableSort) -> Unit)?, rest: Color, modifier: Modifier) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val active = sort?.column == index
    val clickable = onSortChange != null && column.sortable
    val shape = RoundedCornerShape(4.dp)
    // 1.0.8 (Desktop Kit 2.8.0): TalkBack hears which way the column sorts, and what a tap does
    val sortedState = if (active) stringResource(if (sort?.ascending != false) R.string.nag_sorted_ascending else R.string.nag_sorted_descending) else null
    val sortLabel = stringResource(R.string.nag_sort_by_column, column.title)
    Row(
        modifier = modifier
            .heightIn(min = NagTheme.dimens.minTarget)
            .focusRing(state, shape)
            .background(if (clickable) stateBackground(state) else rest, shape)
            .semantics {
                heading()
                if (sortedState != null) stateDescription = sortedState
            }
            .then(
                if (clickable) Modifier.nagClickable(interaction, {
                    onSortChange?.invoke(TableSort(index, if (active) !(sort?.ascending ?: true) else true))
                }, role = Role.Button, onClickLabel = sortLabel) else Modifier,
            )
            .padding(horizontal = CELL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (column.alignEnd) Spacer(Modifier.weight(1f))
        Text(column.title, style = NagTheme.type.labelSmall, color = if (active) colors.text else colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (active) {
            Spacer(Modifier.width(SORT_ARROW_GAP))
            Icon(if (sort?.ascending != false) NagIcons.ChevronUp else NagIcons.ChevronDown, contentDescription = null,
                tint = colors.textSecondary, modifier = Modifier.size(SORT_ARROW))
        }
    }
}

@Composable
private fun <T> TableRow(
    columns: List<TableColumn<T>>,
    widths: List<ColumnWidth>,
    item: T,
    selected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)?,
    modifier: Modifier,
    handle: Modifier? = null,
    handleLabel: String = "",
    trailing: (@Composable () -> Unit)? = null,
    /** 1.0.8: bold and in the text colour throughout. */
    emphasized: Boolean = false,
    /** 1.0.8: a finger's select mode: the check box's column. */
    checkWidth: Dp? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val marker = colors.accent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pagePadding - 12.dp)
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(stateBackground(state, selected), shape)
            // 1.0.8 (Desktop Kit 2.8.0): a selected row is marked by a bar at its start as well as by its colour
            .drawBehind {
                if (selected) {
                    val bar = 4.dp.toPx()
                    val inset = (size.height / 4).coerceAtMost(8.dp.toPx())
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - bar else 0f
                    drawRoundRect(marker, Offset(x, inset), Size(bar, size.height - 2 * inset), CornerRadius(bar / 2))
                }
            }
            .semantics { this.selected = selected }
            .nagClickable(interaction, onClick, onDoubleClick = onDoubleClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checkWidth != null) {
            Box(Modifier.width(checkWidth), contentAlignment = Alignment.Center) { NagCheckMark(ToggleableState(selected), onClick = null) }
        }
        if (handle != null) {
            Box(
                Modifier
                    .width(handleWidth())
                    .heightIn(min = dimens.minTarget)
                    // reached from its row, not a stop of the Tab key: the rows are one stop (1.0.8)
                    .focusProperties { canFocus = !TabNavigation.active }
                    .then(handle)
                    .semantics { contentDescription = handleLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(NagIcons.Menu, contentDescription = null, tint = if (selected) colors.onSelection else colors.textTertiary, modifier = Modifier.size(dimens.icon))
            }
        }
        columns.forEachIndexed { index, column ->
            val cellClick = column.onCellClick
            Box(
                Modifier
                    .cell(this, widths[index])
                    // a cell's own controls (a rating's stars) are reached from their row, not by Tab (1.0.8)
                    .focusProperties { canFocus = !TabNavigation.active }
                    .then(if (cellClick == null) Modifier else Modifier.cellButton(column.cellClickLabel?.invoke(item)) { cellClick(item) })
                    .padding(horizontal = CELL_PADDING),
                contentAlignment = if (column.alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                val content = column.content
                if (content != null) {
                    content(item, selected)
                } else {
                    val base = if (column.mono) NagTheme.type.mono else if (index == 0) NagTheme.type.bodyStrong else NagTheme.type.body
                    Text(
                        text = column.value(item),
                        // 1.0.8: an emphasized row is bold and in the text colour throughout
                        style = if (emphasized) base.copy(fontWeight = FontWeight.SemiBold) else base,
                        color = when {
                            selected -> colors.onSelection
                            index == 0 || emphasized -> colors.text
                            else -> colors.textSecondary
                        },
                        maxLines = 1,
                        overflow = column.ellipsis,
                        textAlign = if (column.alignEnd) TextAlign.End else TextAlign.Start,
                    )
                }
            }
        }
        if (trailing != null) {
            // reached from its row, not a stop of the Tab key (1.0.8)
            Box(Modifier.focusProperties { canFocus = !TabNavigation.active }) { trailing() }
        }
    }
}

/** 1.0.8 (Desktop Kit 2.8.0): a cell that is a button of its own (a star), with hover, press and focus. */
@Composable
private fun Modifier.cellButton(label: String?, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(NagTheme.dimens.radiusSmall)
    return this
        .focusRing(state, shape)
        .background(stateBackground(state), shape)
        .nagClickable(interaction, onClick, onClickLabel = label)
}

/** The row's "⋯" button's column: an icon button's width. */
@Composable
private fun moreWidth(): Dp = NagTheme.dimens.minTarget.coerceAtLeast(NagTheme.dimens.controlHeight)

/** The reorder handle's column: a finger's width by touch. */
@Composable
private fun handleWidth(): Dp = NagTheme.dimens.minTarget.coerceAtLeast(28.dp)

/** A cell's padding at each side, the titles' and the rows'. */
private val CELL_PADDING = 8.dp

/** The sort's arrow beside its column's title, and the gap before it. */
private val SORT_ARROW = 12.dp
private val SORT_ARROW_GAP = 4.dp

/**
 * 1.0: the columns' widths, a fixed one never narrower than its title (with the sort's arrow, when the column sorts): a
 * title is read whole in either language and at any text size ("Duration" was "Durati…" on a tablet). Shares stay shares.
 */
@Composable
private fun <T> fittedWidths(columns: List<TableColumn<T>>, sortable: Boolean): List<ColumnWidth> {
    val measurer = rememberTextMeasurer()
    // as the title's Text sets it: its own style over the surrounding one (letter spacing and all)
    val style = LocalTextStyle.current.merge(NagTheme.type.labelSmall)
    val density = LocalDensity.current
    val titles = columns.map { Triple(it.title, it.width, it.sortable) }
    return remember(titles, style, density, sortable) {
        titles.map { (title, width, sorts) ->
            if (width !is ColumnWidth.Fixed) return@map width
            val text = measurer.measure(title, style, maxLines = 1, softWrap = false, density = density).size.width
            val arrow = if (sortable && sorts) SORT_ARROW + SORT_ARROW_GAP else 0.dp
            // and a pixel's slack: the paddings and the arrow are rounded to whole pixels apart
            val needed = with(density) { (text + 1).toDp() } + CELL_PADDING * 2 + arrow
            if (needed > width.width) ColumnWidth.Fixed(needed) else width
        }
    }
}

/**
 * 1.0: the column titles take every press in their band, beside and between the titles too, so that a long press there
 * never reaches a row passing beneath them (under the glass header, or under the titles kept at the top of a phone's
 * list), whose menu would open instead of the column chooser. Nothing is consumed: the titles under the finger still
 * sort and open their menu.
 */
private fun Modifier.coversRowsBeneath(): Modifier = pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
}

/** Column width inside a table row: weight needs the RowScope receiver. */
private fun Modifier.cell(scope: RowScope, width: ColumnWidth): Modifier = with(scope) {
    when (width) {
        is ColumnWidth.Weight -> this@cell.weight(width.weight)
        is ColumnWidth.Fixed -> this@cell.width(width.width)
    }
}
