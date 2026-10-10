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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.theme.NagTheme

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
    val value: (T) -> String,
)

@Immutable
data class TableSort(val column: Int, val ascending: Boolean)

/**
 * Dense, desktop-style table (S-202): sticky header with sortable columns, 32 dp rows in
 * pointer mode (taller in touch mode), hover and selection states, keyboard focus per row.
 * [rowMenu] gives every row a context menu (right click, long press, Menu key).
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
    sort: TableSort? = null,
    onSortChange: ((TableSort) -> Unit)? = null,
    rowMenu: ((T, Int) -> List<MenuEntry>)? = null,
    rowMenuLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(bottom = 8.dp),
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTarget.coerceAtLeast(28.dp))
                .padding(horizontal = dimens.pagePadding - 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            columns.forEachIndexed { index, column ->
                HeaderCell(column, index, sort, onSortChange)
            }
        }
        HorizontalRule()
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            itemsIndexed(rows, key = { _, item -> key(item) }) { index, item ->
                val row = @Composable {
                    TableRow(
                        columns = columns,
                        item = item,
                        selected = selectedKey != null && key(item) == selectedKey,
                        onClick = { onRowClick(item, index) },
                        modifier = Modifier,
                    )
                }
                if (rowMenu != null) {
                    ContextMenuArea(entries = { rowMenu(item, index) }, accessibilityLabel = rowMenuLabel) { row() }
                } else {
                    row()
                }
            }
        }
    }
}

@Composable
private fun <T> RowScope.HeaderCell(column: TableColumn<T>, index: Int, sort: TableSort?, onSortChange: ((TableSort) -> Unit)?) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val active = sort?.column == index
    val clickable = onSortChange != null && column.sortable
    Row(
        modifier = Modifier
            .cell(this, column.width)
            .heightIn(min = NagTheme.dimens.minTarget)
            .background(if (clickable) stateBackground(state) else colors.background, RoundedCornerShape(4.dp))
            .then(
                if (clickable) Modifier.nagClickable(interaction, {
                    onSortChange?.invoke(TableSort(index, if (active) !(sort?.ascending ?: true) else true))
                }, role = Role.Button) else Modifier,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (column.alignEnd) Spacer(Modifier.weight(1f))
        Text(column.title, style = NagTheme.type.labelSmall, color = if (active) colors.text else colors.textTertiary, maxLines = 1)
        if (active) {
            Spacer(Modifier.width(4.dp))
            Icon(if (sort?.ascending != false) NagIcons.ChevronUp else NagIcons.ChevronDown, contentDescription = null,
                tint = colors.textSecondary, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun <T> TableRow(columns: List<TableColumn<T>>, item: T, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
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
            .nagClickable(interaction, onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEachIndexed { index, column ->
            Box(Modifier.cell(this, column.width).padding(horizontal = 8.dp), contentAlignment = if (column.alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
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

/** Column width inside a table row: weight needs the RowScope receiver. */
private fun Modifier.cell(scope: RowScope, width: ColumnWidth): Modifier = with(scope) {
    when (width) {
        is ColumnWidth.Weight -> this@cell.weight(width.weight)
        is ColumnWidth.Fixed -> this@cell.width(width.width)
    }
}
