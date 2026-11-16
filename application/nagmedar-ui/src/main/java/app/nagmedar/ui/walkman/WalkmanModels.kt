/*
 * Nağmedar 1.5.0: from Walkman Sync (app/src/main/kotlin/com/walkmansync/ui/DiffModels.kt), the owner's own app.
 */
package app.nagmedar.ui.walkman

import androidx.compose.runtime.Immutable
import androidx.compose.ui.state.ToggleableState
import app.nagmedar.walkman.model.AudioFormat
import app.nagmedar.walkman.model.SkipReason
import app.nagmedar.walkman.model.SkippedItem
import app.nagmedar.walkman.model.SyncItem
import app.nagmedar.walkman.model.SyncPlan
import app.nagmedar.walkman.model.SyncState
import java.util.Locale

/** The two tabs of the list of new music. */
enum class DiffTab { Transfer, Skipped }

/** The table's controls: its tab, its order, its filters. [sortColumn] < 0: the plan's order. */
data class DiffControls(
    val tab: DiffTab = DiffTab.Transfer,
    val sortColumn: Int = NO_SORT,
    val sortAscending: Boolean = true,
    val query: String = "",
    val format: AudioFormat? = null,
    val reason: SkipReason? = null,
) {
    companion object {
        const val NO_SORT = -1
    }
}

/**
 * A row of the table: a file to copy or one passed over. A file passed over that can be copied all the same has its
 * [item] (what is copied when it is checked).
 */
@Immutable
class DiffRow(
    val id: String,
    val item: SyncItem?,
    val skipped: SkippedItem?,
    val title: String,
    val artist: String,
    val album: String,
    val format: AudioFormat?,
    val size: Long,
    val sourcePath: String,
    val targetPath: String,
    val searchText: String,
) {
    val isSkipped: Boolean get() = skipped != null

    /** Whether it can be checked to be copied (a file of the plan, or one passed over that can be copied all the same). */
    val canInclude: Boolean get() = item != null

    companion object {
        fun of(item: SyncItem): DiffRow = build(
            id = item.id,
            item = item,
            skipped = null,
            fileName = item.source.name,
            tags = item,
            size = item.size,
            sourcePath = item.source.relativePath,
            targetPath = item.targetPath,
        )

        fun of(skipped: SkippedItem): DiffRow = build(
            id = skipped.id,
            item = skipped.forceable,
            skipped = skipped,
            fileName = skipped.source.name,
            tags = skipped.forceable,
            size = skipped.source.size,
            sourcePath = skipped.source.relativePath,
            targetPath = skipped.forceable?.targetPath.orEmpty(),
        )

        private fun build(
            id: String,
            item: SyncItem?,
            skipped: SkippedItem?,
            fileName: String,
            tags: SyncItem?,
            size: Long,
            sourcePath: String,
            targetPath: String,
        ): DiffRow {
            val t = tags?.tags
            val title = t?.title ?: fileName.substringBeforeLast('.', fileName)
            val artist = t?.artist ?: t?.albumArtist ?: DASH
            val album = t?.album ?: DASH
            val format = tags?.format ?: item?.format ?: skipped?.source?.format
            val search = buildString {
                append(title).append('\n').append(artist).append('\n').append(album).append('\n')
                append(sourcePath).append('\n').append(targetPath)
            }.lowercase(Locale.ROOT)
            return DiffRow(id, item, skipped, title, artist, album, format, size, sourcePath, targetPath, search)
        }
    }
}

/** A plan's rows, ready for the table (made once, off the main thread). */
class PlanRows private constructor(
    val plan: SyncPlan,
    val items: List<DiffRow>,
    val skipped: List<DiffRow>,
    val selectableIds: Set<String>,
    val formats: List<AudioFormat>,
    val reasons: List<SkipReason>,
    private val byId: Map<String, DiffRow>,
) {
    fun row(id: String?): DiffRow? = if (id == null) null else byId[id]

    /** The chosen tab's rows, filtered and ordered. */
    fun visibleFor(controls: DiffControls): List<DiffRow> {
        val query = controls.query.trim().lowercase(Locale.ROOT)
        val onTransferTab = controls.tab == DiffTab.Transfer
        val source = if (onTransferTab) items else skipped
        val filtered = source.filter { row ->
            (controls.format == null || row.format == controls.format) &&
                (onTransferTab || controls.reason == null || row.skipped?.reason == controls.reason) &&
                (query.isEmpty() || row.searchText.contains(query))
        }
        if (controls.sortColumn < 0) return filtered
        val primary = if (onTransferTab) transferComparator(controls.sortColumn) else skippedComparator(controls.sortColumn)
        val ordered = if (controls.sortAscending) primary else primary.reversed()
        return filtered.sortedWith(ordered.thenComparing(PATH_ORDER))
    }

    companion object {
        fun from(plan: SyncPlan): PlanRows {
            val itemRows = plan.items.map { DiffRow.of(it) }
            val skippedRows = plan.skipped.map { DiffRow.of(it) }
            val all = HashMap<String, DiffRow>(itemRows.size + skippedRows.size)
            for (row in itemRows) all[row.id] = row
            for (row in skippedRows) all[row.id] = row
            val selectable = LinkedHashSet<String>()
            for (row in itemRows) selectable.add(row.id)
            for (row in skippedRows) if (row.canInclude) selectable.add(row.id)
            val formats = (itemRows + skippedRows).mapNotNull { it.format }.distinct().sortedBy { it.ordinal }
            val reasons = plan.skipped.map { it.reason }.distinct().sortedBy { it.ordinal }
            return PlanRows(plan, itemRows, skippedRows, selectable, formats, reasons, all)
        }

        private val TEXT_ORDER: Comparator<String> = String.CASE_INSENSITIVE_ORDER
        private val PATH_ORDER: Comparator<DiffRow> = compareBy<DiffRow, String>(TEXT_ORDER) { it.sourcePath }

        /** Columns: 0 title, 1 artist, 2 album, 3 format, 4 size, 5 target path. */
        private fun transferComparator(column: Int): Comparator<DiffRow> = when (column) {
            0 -> compareBy<DiffRow, String>(TEXT_ORDER) { it.title }
            1 -> compareBy<DiffRow, String>(TEXT_ORDER) { it.artist }
            2 -> compareBy<DiffRow, String>(TEXT_ORDER) { it.album }
            3 -> compareBy<DiffRow> { it.format?.ordinal ?: Int.MAX_VALUE }
            4 -> compareBy<DiffRow> { it.size }
            else -> compareBy<DiffRow, String>(TEXT_ORDER) { it.targetPath }
        }

        /** Columns: 0 name, 1 reason, 2 format, 3 size, 4 source path. */
        private fun skippedComparator(column: Int): Comparator<DiffRow> = when (column) {
            0 -> compareBy<DiffRow, String>(TEXT_ORDER) { it.title }
            1 -> compareBy<DiffRow> { it.skipped?.reason?.ordinal ?: Int.MAX_VALUE }
            2 -> compareBy<DiffRow> { it.format?.ordinal ?: Int.MAX_VALUE }
            3 -> compareBy<DiffRow> { it.size }
            else -> compareBy<DiffRow, String>(TEXT_ORDER) { it.sourcePath }
        }
    }
}

/**
 * What is checked to be copied. What is kept is the smaller of its difference from the default (all of the plan's
 * files) and from nothing, so that a large list keeps a small record.
 *
 * @property planStamp the [SyncPlan.createdAtMillis] of the plan it belongs to; another plan starts from its default
 * @property startEmpty true: it starts from nothing; false: from all of the plan's files
 * @property toggled the ids turned the other way from where it starts
 */
data class SelectionModel(
    val planStamp: Long,
    val startEmpty: Boolean,
    val toggled: Set<String>,
) {
    fun resolve(rows: PlanRows): Set<String> {
        val plan = rows.plan
        if (planStamp != plan.createdAtMillis) return plan.defaultSelection
        val result: MutableSet<String> = if (startEmpty) LinkedHashSet() else LinkedHashSet(plan.defaultSelection)
        for (id in toggled) {
            if (id !in rows.selectableIds) continue
            if (!result.remove(id)) result.add(id)
        }
        return result
    }

    companion object {
        const val NO_PLAN = Long.MIN_VALUE

        val None = SelectionModel(NO_PLAN, startEmpty = false, toggled = emptySet())

        fun encode(plan: SyncPlan, selected: Set<String>): SelectionModel {
            val default = plan.defaultSelection
            val againstDefault = LinkedHashSet<String>()
            for (id in default) if (id !in selected) againstDefault.add(id)
            for (id in selected) if (id !in default) againstDefault.add(id)
            return if (againstDefault.size <= selected.size) {
                SelectionModel(plan.createdAtMillis, startEmpty = false, toggled = againstDefault)
            } else {
                SelectionModel(plan.createdAtMillis, startEmpty = true, toggled = LinkedHashSet(selected))
            }
        }
    }
}

/** The list of new music's state, taken in one go. */
data class DiffUiState(
    val rows: PlanRows?,
    val controls: DiffControls,
    val visible: List<DiffRow>,
    val selected: Set<String>,
    val highlight: Set<String>,
    val detail: DiffRow?,
    val selectedBytes: Long,
) {
    val transferCount: Int get() = rows?.items?.size ?: 0
    val skippedCount: Int get() = rows?.skipped?.size ?: 0

    /** Of the shown rows that can be checked: none, some or all of them checked. */
    val visibleCheckState: ToggleableState
        get() {
            var includable = 0
            var included = 0
            for (row in visible) {
                if (!row.canInclude) continue
                includable++
                if (row.id in selected) included++
            }
            return when {
                included == 0 -> ToggleableState.Off
                included == includable -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
        }

    companion object {
        val Empty = DiffUiState(null, DiffControls(), emptyList(), emptySet(), emptySet(), null, 0L)
    }
}

/** The plan of a state that has one (PlanReady only). */
fun SyncState.readyPlan(): SyncPlan? = (this as? SyncState.PlanReady)?.plan

/** Whether a scan can start now (the two cards ready; nothing else running). */
fun SyncState.canScan(): Boolean = when (this) {
    is SyncState.Idle -> pair != null
    is SyncState.PlanReady, is SyncState.Completed, is SyncState.Failed -> true
    is SyncState.AwaitingVolumes, is SyncState.Scanning, is SyncState.Transferring, is SyncState.Paused -> false
}
