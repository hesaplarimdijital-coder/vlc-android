package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.theme.GlassThickness
import app.nagmedar.ui.theme.NagGlass
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.glass
import app.nagmedar.ui.theme.glassSource
import dev.chrisbanes.haze.HazeState

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
    val value: (T) -> String,
)

@Immutable
data class TableSort(val column: Int, val ascending: Boolean)

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
 * Desktop Kit 1.0: with [glassHeader] the rows scroll beneath the header, which turns to frosted
 * glass once they do; [headerMenu] is each column title's context menu (the column chooser), told
 * which column it was opened on; [onExtendSelection] takes Shift+↑ and Shift+↓ on a focused row
 * (the row and the step, true when the selection grew) and the focus follows.
 */
@Composable
fun <T> DenseTable(
    columns: List<TableColumn<T>>,
    rows: List<T>,
    key: (T) -> Any,
    onRowClick: (T, Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    selectedKey: Any? = null,
    /** Several rows selected (S-405); [selectedKey] counts when null. */
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
    glassHeader: Boolean = false,
    onExtendSelection: ((focused: T, delta: Int) -> Boolean)? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val focused = remember { FocusedRow<T>() }
    val extend by rememberUpdatedState(onExtendSelection)
    val header = @Composable { modifier: Modifier ->
        Column(modifier) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimens.minTarget.coerceAtLeast(28.dp))
                    .padding(horizontal = dimens.pagePadding - 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (reorder != null) Spacer(Modifier.width(handleWidth()))
                val rest = if (glassHeader) Color.Transparent else colors.background
                columns.forEachIndexed { index, column ->
                    if (headerMenu != null) {
                        ContextMenuArea(entries = { headerMenu(index) }, modifier = Modifier.cell(this, column.width), accessibilityLabel = headerMenuLabel) {
                            Row { HeaderCell(column, index, sort, onSortChange, rest, Modifier.weight(1f)) }
                        }
                    } else {
                        HeaderCell(column, index, sort, onSortChange, rest, Modifier.cell(this, column.width))
                    }
                }
            }
            HorizontalRule()
        }
    }
    val rowsGlass = remember { HazeState() }
    val glassMode = NagGlass.mode
    // with a glass header the rows start under it; its height is known after the first layout
    val headerPx = remember { mutableIntStateOf(with(density) { (dimens.minTarget.coerceAtLeast(28.dp) + 1.dp).roundToPx() }) }
    val padding = remember(contentPadding, density, glassHeader) {
        if (glassHeader) HeaderPadding(contentPadding, headerPx, density) else contentPadding
    }
    val scrolled by remember(state) { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 } }
    val list = @Composable { modifier: Modifier ->
        LazyColumn(
            state = state,
            modifier = modifier.then(if (reorder != null) Modifier.reorderList(reorder) else Modifier),
            contentPadding = padding,
        ) {
            itemsIndexed(rows, key = { _, item -> key(item) }) { index, item ->
                // the dragged row rises over the others: its outermost layout carries it
                val place = if (reorder != null) Modifier.reorderItem(reorder, key(item), index) else Modifier
                val tracked = if (onExtendSelection == null) Modifier else Modifier.onFocusChanged { focus ->
                    if (focus.isFocused) focused.item = item else if (focused.item == item) focused.item = null
                }
                val row = @Composable { outer: Modifier ->
                    TableRow(
                        columns = columns,
                        item = item,
                        selected = selectedKeys?.contains(key(item)) ?: (selectedKey != null && key(item) == selectedKey),
                        onClick = { onRowClick(item, index) },
                        onDoubleClick = onRowDoubleClick?.let { open -> { open(item, index) } },
                        modifier = outer.then(rowModifier(item)).then(tracked),
                        handle = reorder?.let { Modifier.reorderHandle(it, key(item)) },
                        handleLabel = if (reorder != null) handleLabel(item) else "",
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
    val keys = if (onExtendSelection == null) Modifier else Modifier.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || !event.isShiftPressed || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            return@onKeyEvent false
        }
        val delta = when (event.key) {
            Key.DirectionUp -> -1
            Key.DirectionDown -> 1
            else -> return@onKeyEvent false
        }
        val row = focused.item ?: return@onKeyEvent false
        if (extend?.invoke(row, delta) == true) focusManager.moveFocus(if (delta > 0) FocusDirection.Down else FocusDirection.Up)
        true
    }
    if (glassHeader) {
        // the header lies over the rows, glass once they pass beneath it
        Box(modifier.fillMaxSize().then(keys)) {
            list(Modifier.fillMaxSize().glassSource(rowsGlass, glassMode))
            header(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerPx.intValue = it.height }
                    .glass(NagGlass.chrome(colors, GlassThickness.Thin), source = rowsGlass, mode = glassMode, enabled = scrolled),
            )
        }
    } else {
        Column(modifier.fillMaxSize().then(keys)) {
            header(Modifier.fillMaxWidth())
            list(Modifier.fillMaxSize())
        }
    }
}

/** The row with the keyboard's focus, for Shift+↑ and Shift+↓; plain, nothing redraws from it. */
private class FocusedRow<T> {
    var item: T? = null
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
    Row(
        modifier = modifier
            .heightIn(min = NagTheme.dimens.minTarget)
            .focusRing(state, shape)
            .background(if (clickable) stateBackground(state) else rest, shape)
            .then(
                if (clickable) Modifier.nagClickable(interaction, {
                    onSortChange?.invoke(TableSort(index, if (active) !(sort?.ascending ?: true) else true))
                }, role = Role.Button) else Modifier,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (column.alignEnd) Spacer(Modifier.weight(1f))
        Text(column.title, style = NagTheme.type.labelSmall, color = if (active) colors.text else colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (active) {
            Spacer(Modifier.width(4.dp))
            Icon(if (sort?.ascending != false) NagIcons.ChevronUp else NagIcons.ChevronDown, contentDescription = null,
                tint = colors.textSecondary, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun <T> TableRow(
    columns: List<TableColumn<T>>,
    item: T,
    selected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)?,
    modifier: Modifier,
    handle: Modifier? = null,
    handleLabel: String = "",
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pagePadding - 12.dp)
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(stateBackground(state, selected), shape)
            .semantics { this.selected = selected }
            .nagClickable(interaction, onClick, onDoubleClick = onDoubleClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (handle != null) {
            Box(
                Modifier
                    .width(handleWidth())
                    .heightIn(min = dimens.minTarget)
                    .then(handle)
                    .semantics { contentDescription = handleLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(NagIcons.Menu, contentDescription = null, tint = if (selected) colors.onSelection else colors.textTertiary, modifier = Modifier.size(dimens.icon))
            }
        }
        columns.forEachIndexed { index, column ->
            Box(Modifier.cell(this, column.width).padding(horizontal = 8.dp), contentAlignment = if (column.alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
                val content = column.content
                if (content != null) {
                    content(item, selected)
                } else {
                    Text(
                        text = column.value(item),
                        style = if (column.mono) NagTheme.type.mono else if (index == 0) NagTheme.type.bodyStrong else NagTheme.type.body,
                        color = when {
                            selected -> colors.onSelection
                            index == 0 -> colors.text
                            else -> colors.textSecondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (column.alignEnd) TextAlign.End else TextAlign.Start,
                    )
                }
            }
        }
    }
}

/** The reorder handle's column: a finger's width by touch. */
@Composable
private fun handleWidth(): Dp = NagTheme.dimens.minTarget.coerceAtLeast(28.dp)

/** Column width inside a table row: weight needs the RowScope receiver. */
private fun Modifier.cell(scope: RowScope, width: ColumnWidth): Modifier = with(scope) {
    when (width) {
        is ColumnWidth.Weight -> this@cell.weight(width.weight)
        is ColumnWidth.Fixed -> this@cell.width(width.width)
    }
}
