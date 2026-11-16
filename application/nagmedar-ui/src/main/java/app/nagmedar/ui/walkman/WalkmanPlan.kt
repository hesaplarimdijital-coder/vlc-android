/*
 * Nağmedar 1.5.0: after Walkman Sync (app/src/main/kotlin/com/walkmansync/ui/DiffPreviewScreen.kt), the owner's own
 * app; drawn again with Nağmedar's parts.
 */
package app.nagmedar.ui.walkman

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.ClearSelectionOnBack
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.LocalClickModifiers
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MultiSelection
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckMark
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.NagSearchField
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SelectionBar
import app.nagmedar.ui.components.SelectionCommands
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.components.TableSort
import app.nagmedar.ui.components.rememberMultiSelection
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.walkman.model.SyncPlan
import app.nagmedar.walkman.model.SyncState

/**
 * "Aktarılacaklar": what the scan found new on the tablet's card, to be copied (all of it checked), and what it passed
 * over and why, some of which can be copied all the same; found by words, filtered by format and reason, ordered by a
 * column. A row opens its details; Aktar copies what is checked.
 */
@Composable
internal fun WalkmanPlan(viewModel: WalkmanViewModel) {
    val state by viewModel.state.collectAsState()
    val ui by viewModel.diff.collectAsState()
    val current = state
    val plan = current.readyPlan()
    val dimens = NagTheme.dimens
    if (current is SyncState.Scanning) {
        Column(Modifier.fillMaxSize().padding(dimens.pagePadding)) { ScanPanel(current.progress, viewModel::requestCancel) }
        return
    }
    if (plan == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StatePanel(
                tone = StateTone.Neutral,
                icon = NagIcons.Headphones,
                title = stringResource(R.string.nag_walkman_diff_empty_title),
                description = stringResource(R.string.nag_walkman_diff_empty_desc),
                primary = if (current.canScan()) app.nagmedar.ui.components.StateAction(stringResource(R.string.nag_walkman_action_scan), viewModel::scan) else null,
                modifier = Modifier.testTag("walkman-plan-empty"),
            )
        }
        return
    }
    if (ui.rows?.plan !== plan) {
        // the rows are being made, off the main thread
        Column(Modifier.fillMaxWidth().padding(dimens.pagePadding)) { NagProgressBar(null, stringResource(R.string.nag_walkman_nav_new_music)) }
        return
    }
    PlanContent(viewModel, ui, plan)
}

@Composable
private fun PlanContent(viewModel: WalkmanViewModel, ui: DiffUiState, plan: SyncPlan) {
    val res = LocalContext.current.resources
    val dimens = NagTheme.dimens
    val controls = ui.controls
    val rows = ui.rows
    val wide = currentWidthClass() >= WidthClass.Expanded
    Column(Modifier.fillMaxSize().testTag("walkman-plan")) {
        Column(Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            WalkmanMessage(viewModel)
            for (warning in plan.warnings) WalkmanNote(StateTone.Warning, res.planWarningText(warning))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
            ) {
                SegmentedChoice(
                    listOf(
                        DiffTab.Transfer to res.getString(R.string.nag_walkman_tab_transfer, ui.transferCount),
                        DiffTab.Skipped to res.getString(R.string.nag_walkman_tab_skipped, ui.skippedCount),
                    ),
                    controls.tab,
                    viewModel::setTab,
                    modifier = Modifier.testTag("walkman-plan-tabs"),
                )
                MenuButton(
                    controls.format?.name ?: stringResource(R.string.nag_walkman_filter_all_formats),
                    entries = {
                        buildList {
                            add(MenuEntry.Item(res.getString(R.string.nag_walkman_filter_all_formats), { viewModel.setFormatFilter(null) }, checked = controls.format == null))
                            rows?.formats.orEmpty().forEach { format ->
                                add(MenuEntry.Item(format.name, { viewModel.setFormatFilter(format) }, checked = controls.format == format))
                            }
                        }
                    },
                )
                if (controls.tab == DiffTab.Skipped) {
                    MenuButton(
                        controls.reason?.let { res.skipReasonLabel(it) } ?: stringResource(R.string.nag_walkman_filter_all_reasons),
                        entries = {
                            buildList {
                                add(MenuEntry.Item(res.getString(R.string.nag_walkman_filter_all_reasons), { viewModel.setReasonFilter(null) }, checked = controls.reason == null))
                                rows?.reasons.orEmpty().forEach { reason ->
                                    val count = rows?.skipped?.count { it.skipped?.reason == reason } ?: 0
                                    add(
                                        MenuEntry.Item(
                                            res.getString(R.string.nag_walkman_filter_reason_item, res.skipReasonLabel(reason), count),
                                            { viewModel.setReasonFilter(reason) },
                                            checked = controls.reason == reason,
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
                if (controls.sortColumn >= 0) NagButton(stringResource(R.string.nag_walkman_action_plan_order), viewModel::clearSort)
            }
            NagSearchField(
                controls.query,
                viewModel::setQuery,
                placeholder = stringResource(R.string.nag_walkman_filter_hint),
                clearLabel = stringResource(R.string.nag_walkman_action_clear_search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.weight(1f)) {
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1.6f)) { TableArea(viewModel, ui, compact = false) }
                    Box(Modifier.weight(1f).background(NagTheme.colors.surface)) { Detail(viewModel, ui) }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { TableArea(viewModel, ui, compact = true) }
                    if (ui.detail != null) {
                        HorizontalRule()
                        Box(Modifier.heightIn(max = 260.dp)) { Detail(viewModel, ui) }
                    }
                }
            }
        }
        SummaryBar(viewModel, ui, plan)
    }
}

/**
 * The table: a check box for each row that can be copied, the shown rows checked or unchecked at once over it. As in
 * Walkman Sync, rows are also highlighted, several at once (Ctrl and Shift with a click, Ctrl+A; by touch the row's
 * menu's "Seç"), apart from what is checked: the highlighted rows are then checked or left out together, from the bar
 * over the table or a row's menu.
 */
@Composable
private fun TableArea(viewModel: WalkmanViewModel, ui: DiffUiState, compact: Boolean) {
    val res = LocalContext.current.resources
    val dimens = NagTheme.dimens
    val controls = ui.controls
    val skippedTab = controls.tab == DiffTab.Skipped
    val selected = ui.selected
    val defs = remember(controls.tab, compact, selected, res) { columns(res, skippedTab, compact, selected, viewModel::setIncluded) }
    val sort = if (controls.sortColumn >= 0) {
        defs.indexOfFirst { it.first == controls.sortColumn }.takeIf { it >= 0 }?.let { TableSort(it, controls.sortAscending) }
    } else {
        null
    }
    val includable = ui.visible.any { it.canInclude }
    val allLabel = stringResource(if (skippedTab) R.string.nag_walkman_select_all_shown_skipped else R.string.nag_walkman_select_all_shown)
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val clicks = LocalClickModifiers.current
    val highlight = rememberMultiSelection()
    val order = remember(ui.visible) { ui.visible.map<DiffRow, Any> { it.id } }
    LaunchedEffect(controls.tab) { highlight.clear() }
    LaunchedEffect(order) { highlight.retain(order) }
    // the view model sees the same rows highlighted (a single one shows its details when none was opened)
    LaunchedEffect(highlight) { snapshotFlow { highlight.keys }.collect { viewModel.onHighlightChange(it) } }
    SelectionCommands(highlight, order)
    ClearSelectionOnBack(highlight, touchLayout = !pointer)
    val highlighted = highlight.keys.filterIsInstance<String>()
    Column(Modifier.fillMaxSize()) {
        if (highlight.count > 1 || highlight.selecting) {
            SelectionBar(
                text = res.getString(R.string.nag_walkman_highlighted_count, highlight.count),
                onClear = highlight::clear,
                clearLabel = stringResource(R.string.nag_selection_clear),
                modifier = Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs).testTag("walkman-highlight"),
            ) {
                NagButton(
                    stringResource(if (skippedTab) R.string.nag_walkman_action_force_selected else R.string.nag_walkman_action_include_selected),
                    { viewModel.include(highlighted) },
                    icon = NagIcons.Plus,
                    enabled = highlighted.isNotEmpty(),
                    modifier = Modifier.testTag("walkman-highlight-include"),
                )
                NagButton(
                    stringResource(R.string.nag_walkman_action_exclude_selected),
                    { viewModel.exclude(highlighted) },
                    icon = NagIcons.Close,
                    enabled = highlighted.isNotEmpty(),
                    modifier = Modifier.testTag("walkman-highlight-exclude"),
                )
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding), verticalAlignment = Alignment.CenterVertically) {
                NagCheckbox(
                    ui.visibleCheckState == ToggleableState.On,
                    { viewModel.toggleVisible() },
                    allLabel,
                    enabled = includable,
                    modifier = Modifier.testTag("walkman-select-all"),
                )
            }
        }
        Box(Modifier.weight(1f)) {
            if (ui.visible.isEmpty()) {
                val empty = when {
                    !skippedTab && ui.transferCount == 0 -> R.string.nag_walkman_empty_nothing_new
                    skippedTab && ui.skippedCount == 0 -> R.string.nag_walkman_empty_nothing_skipped
                    else -> R.string.nag_walkman_empty_no_match
                }
                Box(Modifier.fillMaxSize().padding(dimens.pagePadding), contentAlignment = Alignment.Center) {
                    Text(stringResource(empty), style = NagTheme.type.body, color = NagTheme.colors.textSecondary, modifier = Modifier.testTag("walkman-plan-none"))
                }
            } else {
                DenseTable(
                    columns = defs.map { it.second },
                    rows = ui.visible,
                    key = { it.id },
                    onRowClick = { row, _ ->
                        val (ctrl, shift) = clicks.take()
                        when {
                            highlight.selecting -> highlight.toggle(row.id)
                            ctrl || shift -> highlight.click(row.id, order, ctrl, shift)
                            else -> {
                                // a plain click: its details shown, and with a mouse the row alone highlighted (a tap
                                // highlights nothing, so that Back leaves the page rather than letting go of a row)
                                if (pointer) highlight.click(row.id, order)
                                viewModel.onRowClick(row.id)
                            }
                        }
                    },
                    modifier = Modifier.testTag("walkman-table"),
                    selectedKey = ui.detail?.id,
                    selectedKeys = highlight.keys.takeIf { it.isNotEmpty() },
                    sort = sort,
                    onSortChange = { next -> defs.getOrNull(next.column)?.let { viewModel.setSort(it.first, next.ascending) } },
                    onExtendSelection = if (pointer) ({ row, delta -> highlight.extend(row.id, order, delta) }) else null,
                    rowMenu = { row, _ -> rowMenu(res, row, highlight, skippedTab, selected, viewModel, startSelecting = !pointer) },
                    rowMenuLabel = stringResource(R.string.nag_walkman_row_menu),
                    rowMenuAsSheet = !pointer,
                )
            }
        }
    }
}

/**
 * A row's menu: the highlighted rows checked or left out together when the row is one of several highlighted, otherwise
 * the row itself; its details; by touch "Seç", which starts highlighting rows with taps.
 */
private fun rowMenu(
    res: Resources,
    row: DiffRow,
    highlight: MultiSelection,
    skippedTab: Boolean,
    selected: Set<String>,
    viewModel: WalkmanViewModel,
    startSelecting: Boolean,
): List<MenuEntry> = buildList {
    if (row.id in highlight && highlight.count > 1) {
        val ids = highlight.keys.filterIsInstance<String>()
        add(
            MenuEntry.Item(
                res.getString(if (skippedTab) R.string.nag_walkman_menu_force_selected else R.string.nag_walkman_menu_include_selected, ids.size),
                { viewModel.include(ids) },
                icon = NagIcons.Plus,
            ),
        )
        add(MenuEntry.Item(res.getString(R.string.nag_walkman_menu_exclude_selected, ids.size), { viewModel.exclude(ids) }, icon = NagIcons.Close))
    } else if (row.canInclude) {
        if (row.id in selected) {
            add(MenuEntry.Item(res.getString(R.string.nag_walkman_menu_exclude_one), { viewModel.setIncluded(row.id, false) }, icon = NagIcons.Close))
        } else {
            val label = if (row.isSkipped) R.string.nag_walkman_detail_force else R.string.nag_walkman_detail_include
            add(MenuEntry.Item(res.getString(label), { viewModel.setIncluded(row.id, true) }, icon = NagIcons.Plus))
        }
    }
    if (isNotEmpty()) add(MenuEntry.Separator)
    add(MenuEntry.Item(res.getString(R.string.nag_walkman_menu_details), { viewModel.onRowClick(row.id) }, icon = NagIcons.Info))
    if (startSelecting && !highlight.selecting) {
        add(MenuEntry.Item(res.getString(R.string.nag_select), { highlight.startSelecting(row.id) }, icon = NagIcons.Check))
    }
}

/**
 * The table's columns by the rows' own (logical) numbers, the ViewModel's sort's: the transfer's 0 title, 1 artist,
 * 2 album, 3 format, 4 size, 5 target path; the skipped's 0 name, 1 reason, 2 format, 3 size, 4 source path. The check
 * box is none of them (-1).
 */
private fun columns(
    res: Resources,
    skippedTab: Boolean,
    compact: Boolean,
    selected: Set<String>,
    include: (String, Boolean) -> Unit,
): List<Pair<Int, TableColumn<DiffRow>>> {
    val mark = -1 to TableColumn<DiffRow>(
        "",
        ColumnWidth.Fixed(40.dp),
        sortable = false,
        content = { row, _ -> if (row.canInclude) NagCheckMark(if (row.id in selected) ToggleableState.On else ToggleableState.Off, onClick = null) },
        id = "include",
        onCellClick = { row -> if (row.canInclude) include(row.id, row.id !in selected) },
        cellClickLabel = { row -> res.getString(if (row.isSkipped) R.string.nag_walkman_detail_force else R.string.nag_walkman_detail_include) },
    ) { row -> if (row.id in selected) res.getString(R.string.nag_walkman_included) else "" }
    val title = 0 to TableColumn<DiffRow>(res.getString(R.string.nag_walkman_col_title), ColumnWidth.Weight(1.6f)) { it.title }
    val format = TableColumn<DiffRow>(res.getString(R.string.nag_walkman_col_format), ColumnWidth.Fixed(64.dp), mono = true) { it.format?.name ?: DASH }
    val size = TableColumn<DiffRow>(res.getString(R.string.nag_walkman_col_size), ColumnWidth.Fixed(88.dp), alignEnd = true, mono = true) { formatBytes(it.size) }
    return if (!skippedTab) {
        buildList {
            add(mark)
            add(title)
            add(1 to TableColumn<DiffRow>(res.getString(R.string.nag_walkman_col_artist), ColumnWidth.Weight(1.1f)) { it.artist })
            if (!compact) add(2 to TableColumn<DiffRow>(res.getString(R.string.nag_walkman_col_album), ColumnWidth.Weight(1.1f)) { it.album })
            add(3 to format)
            add(4 to size)
            if (!compact) add(5 to TableColumn<DiffRow>(res.getString(R.string.nag_walkman_col_target), ColumnWidth.Weight(2f), ellipsis = TextOverflow.MiddleEllipsis) { it.targetPath })
        }
    } else {
        buildList {
            add(mark)
            add(title)
            add(1 to TableColumn<DiffRow>(res.getString(R.string.nag_walkman_col_reason), ColumnWidth.Weight(1.5f)) { row -> row.skipped?.reason?.let { res.skipReasonLabel(it) } ?: DASH })
            add(2 to format)
            add(3 to size)
            if (!compact) add(4 to TableColumn<DiffRow>(res.getString(R.string.nag_walkman_col_source), ColumnWidth.Weight(2f), ellipsis = TextOverflow.MiddleEllipsis) { it.sourcePath })
        }
    }
}

/** A row's details: its tags, why it is copied or passed over, where it is and where it goes; its check box. */
@Composable
private fun Detail(viewModel: WalkmanViewModel, ui: DiffUiState) {
    val row = ui.detail
    val dimens = NagTheme.dimens
    val colors = NagTheme.colors
    val res = LocalContext.current.resources
    if (row == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StatePanel(
                tone = StateTone.Neutral,
                icon = NagIcons.Info,
                title = stringResource(R.string.nag_walkman_detail_empty_title),
                description = stringResource(R.string.nag_walkman_detail_empty_desc),
                dense = true,
            )
        }
        return
    }
    val item = row.item
    val skipped = row.skipped
    val tags = item?.tags
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dimens.pagePadding).testTag("walkman-detail"),
        verticalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            Text(row.title, style = NagTheme.type.title, color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            row.format?.let { Text(it.name, style = NagTheme.type.mono, color = colors.textSecondary) }
        }
        val subtitle = listOf(row.artist, row.album).filter { it != DASH }.joinToString(" · ")
        if (subtitle.isNotEmpty()) Text(subtitle, style = NagTheme.type.body, color = colors.textSecondary)
        if (row.canInclude) {
            NagCheckbox(
                row.id in ui.selected,
                { on -> viewModel.setIncluded(row.id, on) },
                stringResource(if (skipped != null) R.string.nag_walkman_detail_force else R.string.nag_walkman_detail_include),
                modifier = Modifier.testTag("walkman-detail-include"),
            )
        }
        HorizontalRule()
        Column {
            if (skipped != null) {
                WalkmanFact(stringResource(R.string.nag_walkman_detail_skip_reason), res.skipReasonLabel(skipped.reason))
                Text(res.skipReasonDescription(skipped.reason), style = NagTheme.type.labelSmall, color = colors.textTertiary, modifier = Modifier.padding(vertical = 4.dp))
                skipped.detail?.let { WalkmanFact(stringResource(R.string.nag_walkman_detail_note), it, mono = true, maxLines = 4) }
            } else if (item != null) {
                WalkmanFact(stringResource(R.string.nag_walkman_detail_copy_reason), res.copyReasonLabel(item.reason))
            }
            WalkmanFact(stringResource(R.string.nag_walkman_detail_size), formatBytes(row.size))
            if (tags != null) {
                tags.trackNumber?.let { WalkmanFact(stringResource(R.string.nag_walkman_detail_track), it.toString()) }
                tags.discNumber?.let { WalkmanFact(stringResource(R.string.nag_walkman_detail_disc), it.toString()) }
                tags.durationMs?.let { WalkmanFact(stringResource(R.string.nag_walkman_detail_duration), formatDuration(it)) }
            }
            WalkmanFact(stringResource(R.string.nag_walkman_detail_source), row.sourcePath, mono = true, maxLines = 6)
            if (row.targetPath.isNotEmpty()) WalkmanFact(stringResource(R.string.nag_walkman_detail_target), row.targetPath, mono = true, maxLines = 6)
            item?.companionOfId?.let { WalkmanFact(stringResource(R.string.nag_walkman_detail_companion), it, mono = true, maxLines = 4) }
        }
    }
}

/** How much is checked against the room the Walkman's card has; Aktar copies it. */
@Composable
private fun SummaryBar(viewModel: WalkmanViewModel, ui: DiffUiState, plan: SyncPlan) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val res = LocalContext.current.resources
    val free = plan.targetFreeBytes
    val over = free != null && ui.selectedBytes > free
    val count = ui.selected.size
    Column(Modifier.fillMaxWidth().background(colors.chrome)) {
        HorizontalRule()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapL),
        ) {
            Column(Modifier.weight(1f)) {
                Text(res.getString(R.string.nag_walkman_summary_selected, count, formatBytes(ui.selectedBytes)), style = NagTheme.type.bodyStrong, color = colors.text, modifier = Modifier.testTag("walkman-selected"))
                Text(
                    res.getString(R.string.nag_walkman_summary_free, free?.let { formatBytes(it) } ?: res.getString(R.string.nag_walkman_unknown), res.fileSystemLabel(plan.targetFileSystem)),
                    style = NagTheme.type.label,
                    color = if (over) colors.danger else colors.textSecondary,
                )
                if (over) Text(stringResource(R.string.nag_walkman_summary_over), style = NagTheme.type.labelSmall, color = colors.danger)
            }
            NagButton(
                res.getString(R.string.nag_walkman_action_transfer_n, count),
                { viewModel.startTransfer() },
                icon = NagIcons.Downloads,
                primary = true,
                enabled = count > 0,
                modifier = Modifier.testTag("walkman-transfer"),
            )
        }
    }
}
