/*
 * Nağmedar 1.5.0: from Walkman Sync (app/src/main/kotlin/com/walkmansync/ui/SyncViewModel.kt), the owner's own app.
 */
package app.nagmedar.ui.walkman

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.shell.Destination
import app.nagmedar.walkman.Walkman
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import app.nagmedar.walkman.model.AudioFormat
import app.nagmedar.walkman.model.BindRejection
import app.nagmedar.walkman.model.BindResult
import app.nagmedar.walkman.model.RemovableVolume
import app.nagmedar.walkman.model.SkipReason
import app.nagmedar.walkman.model.SyncState
import app.nagmedar.walkman.model.TargetFileSystem
import app.nagmedar.walkman.model.VolumeId
import app.nagmedar.walkman.model.VolumeRole
import app.nagmedar.walkman.workflow.VolumeSnapshot
import app.nagmedar.walkman.sync.SyncController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a command came to, shown for a while (the page puts it in words). */
sealed interface UiMessage {
    data class Bound(val role: VolumeRole, val label: String) : UiMessage
    data class Rejected(val reason: BindRejection, val detail: String?) : UiMessage

    /** The command was not taken now (another job runs). */
    data object NotAccepted : UiMessage

    /** Something unexpected (its detail is for the log, not shown). */
    data object Failure : UiMessage
}

/** The target card as the last scan saw it (its file system and free space are known from a plan only). */
data class TargetInfo(
    val volume: VolumeId,
    val fileSystem: TargetFileSystem,
    val freeBytes: Long?,
)

/**
 * The page's state; it sees the [SyncController] only. The checks, the filters and the order are kept in the
 * [SavedStateHandle]: they outlive a DeX window made again and the process's death.
 */
class WalkmanViewModel(
    private val controller: SyncController,
    private val saved: SavedStateHandle,
) : ViewModel() {

    val state: StateFlow<SyncState> = controller.state
    val volumes: StateFlow<VolumeSnapshot> = controller.volumes
    val mountedVolumes: StateFlow<List<RemovableVolume>> = controller.mountedVolumes

    private val controls = MutableStateFlow(restoreControls())
    private val selection = MutableStateFlow(restoreSelection())
    private val highlight = MutableStateFlow<Set<String>>(emptySet())
    private val focusId = MutableStateFlow(saved.get<String>(KEY_FOCUS))
    private val cancelDialogState = MutableStateFlow(saved.get<Boolean>(KEY_CANCEL_DIALOG) ?: false)
    private val messageState = MutableStateFlow<UiMessage?>(null)
    private val targetInfoState = MutableStateFlow<TargetInfo?>(null)

    /** What a choice of card came to and the like (shown until closed). */
    val message: StateFlow<UiMessage?> = messageState.asStateFlow()

    /** Whether the "cancel the copy?" question is up. */
    val cancelDialog: StateFlow<Boolean> = cancelDialogState.asStateFlow()

    /** The target card from the last scan with a plan; null before any. */
    val targetInfo: StateFlow<TargetInfo?> = targetInfoState.asStateFlow()

    @Volatile
    private var latest: Visible = Visible.Empty

    /** The list of new music: the plan's rows, filtered and ordered, what is checked, the details; made off the main thread. */
    val diff: StateFlow<DiffUiState>

    init {
        val planRows: Flow<PlanRows?> = state
            .map { it.readyPlan() }
            .distinctUntilChanged { old, new -> old === new }
            .map { plan -> plan?.let { PlanRows.from(it) } }
            .onEach { highlight.value = emptySet() }
        val visible: Flow<Visible> = combine(planRows, controls) { rows, c ->
            Visible(rows, c, rows?.visibleFor(c) ?: emptyList())
        }.onEach { latest = it }
        diff = combine(visible, selection, highlight, focusId) { v, sel, hl, focus ->
            val rows = v.rows
            val selected = if (rows == null) emptySet() else sel.resolve(rows)
            var bytes = 0L
            if (rows != null) {
                for (id in selected) bytes += rows.row(id)?.size ?: 0L
            }
            val detail = rows?.row(focus) ?: rows?.let { r -> hl.singleOrNull()?.let { r.row(it) } }
            DiffUiState(
                rows = rows,
                controls = v.controls,
                visible = v.list,
                selected = selected,
                highlight = if (rows == null) emptySet() else hl,
                detail = detail,
                selectedBytes = bytes,
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, DiffUiState.Empty)

        viewModelScope.launch {
            state.collect { s ->
                val plan = s.readyPlan() ?: return@collect
                targetInfoState.value = TargetInfo(plan.pair.target, plan.targetFileSystem, plan.targetFreeBytes)
            }
        }
    }

    // ------------------------------------------------------------------ komutlar

    fun scan() {
        if (!controller.scan()) messageState.value = UiMessage.NotAccepted
    }

    /** Copies what is checked; false when nothing is, or the plan changed. */
    fun startTransfer(): Boolean {
        val current = state.value.readyPlan() ?: return false
        val rows = latest.rows ?: return false
        if (rows.plan !== current) return false
        val ids = selection.value.resolve(rows)
        if (ids.isEmpty()) return false
        val accepted = controller.startTransfer(ids)
        if (!accepted) messageState.value = UiMessage.NotAccepted
        return accepted
    }

    fun togglePause() {
        when (state.value) {
            is SyncState.Transferring -> controller.pause()
            is SyncState.Paused -> controller.resume()
            else -> Unit
        }
    }

    /** Cancels a scan at once, a copy after asking. */
    fun requestCancel() {
        when (state.value) {
            is SyncState.Scanning -> controller.cancel()
            is SyncState.Transferring, is SyncState.Paused -> setCancelDialog(true)
            else -> Unit
        }
    }

    fun confirmCancel() {
        setCancelDialog(false)
        controller.cancel()
    }

    fun dismissCancelDialog() = setCancelDialog(false)

    fun dismissResult() {
        controller.dismiss()
    }

    fun refreshVolumes() = controller.refreshVolumes()

    /** A USB card reader was put in: the cards are read again a few times, until it is mounted. */
    fun onUsbDeviceAttached() = controller.refreshVolumes(afterUsbAttach = true)

    fun clearMessage() {
        messageState.value = null
    }

    private fun setCancelDialog(open: Boolean) {
        cancelDialogState.value = open
        saved[KEY_CANCEL_DIALOG] = open
    }

    // ------------------------------------------------------------------ choosing a card

    /**
     * The folder picker's intent: [hint] the card it opens on; with [expected] only that card is taken (access given
     * again). The question waiting outlives the activity made again.
     */
    fun createPickerIntent(role: VolumeRole, hint: VolumeId?, expected: VolumeId?): Intent {
        saved[KEY_PICK_ROLE] = role.name
        saved[KEY_PICK_EXPECTED] = expected?.toString()
        return controller.createPickerIntent(role, hint)
    }

    fun onPickResult(uri: Uri?) {
        val role = saved.get<String>(KEY_PICK_ROLE)?.let { name -> VolumeRole.entries.firstOrNull { it.name == name } }
        val expected = VolumeId.of(saved.get<String>(KEY_PICK_EXPECTED))
        saved.remove<String>(KEY_PICK_ROLE)
        saved.remove<String>(KEY_PICK_EXPECTED)
        if (role == null || uri == null) return
        viewModelScope.launch {
            messageState.value = try {
                when (val result = controller.bindVolume(role, uri, expected)) {
                    is BindResult.Bound -> UiMessage.Bound(role, result.binding.label)
                    is BindResult.Rejected -> UiMessage.Rejected(result.reason, result.detail)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiMessage.Failure
            }
        }
    }

    fun unbind(role: VolumeRole) {
        viewModelScope.launch {
            try {
                controller.unbindVolume(role)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messageState.value = UiMessage.Failure
            }
        }
    }

    // ------------------------------------------------------------------ the list: how it is shown

    fun setTab(tab: DiffTab) {
        updateControls { it.copy(tab = tab, sortColumn = DiffControls.NO_SORT, sortAscending = true) }
        highlight.value = emptySet()
    }

    fun setSort(column: Int, ascending: Boolean) {
        updateControls { it.copy(sortColumn = column, sortAscending = ascending) }
    }

    fun clearSort() {
        updateControls { it.copy(sortColumn = DiffControls.NO_SORT, sortAscending = true) }
    }

    fun setQuery(query: String) {
        updateControls { it.copy(query = query) }
    }

    fun setFormatFilter(format: AudioFormat?) {
        updateControls { it.copy(format = format) }
    }

    fun setReasonFilter(reason: SkipReason?) {
        updateControls { it.copy(reason = reason) }
    }

    /** The table's highlighted rows (several); apart from what is checked to be copied. */
    fun onHighlightChange(keys: Set<Any>) {
        highlight.value = keys.filterIsInstance<String>().toSet()
    }

    /** How many rows are highlighted (a row's menu reads it as it is now). */
    fun highlightedCount(): Int = highlight.value.size

    fun onRowClick(id: String) {
        focusId.value = id
        saved[KEY_FOCUS] = id
    }

    // ------------------------------------------------------------------ the list: what is checked

    fun setIncluded(id: String, included: Boolean) = mutateSelection { current, rows ->
        if (id !in rows.selectableIds) current else if (included) current + id else current - id
    }

    fun includeHighlighted() = include(highlight.value)

    fun excludeHighlighted() = exclude(highlight.value)

    /** Checks [ids], those of them that can be copied (the table's highlighted rows, a row's menu). */
    fun include(ids: Collection<String>) = mutateSelection { current, rows ->
        current + ids.filter { it in rows.selectableIds }
    }

    fun exclude(ids: Collection<String>) = mutateSelection { current, _ -> current - ids.toSet() }

    /** Checks all of the shown (filtered) rows. */
    fun includeVisible() = mutateSelection { current, rows ->
        current + latest.list.map { it.id }.filter { it in rows.selectableIds }
    }

    fun excludeVisible() = mutateSelection { current, _ -> current - latest.list.map { it.id }.toSet() }

    /** The check box over the table: all checked, it clears them; otherwise it checks them all. */
    fun toggleVisible() {
        val rows = latest.rows ?: return
        val current = selection.value.resolve(rows)
        val allIncluded = latest.list.filter { it.canInclude }.all { it.id in current }
        if (allIncluded) excludeVisible() else includeVisible()
    }

    private inline fun mutateSelection(transform: (Set<String>, PlanRows) -> Set<String>) {
        val rows = latest.rows ?: return
        val current = selection.value.resolve(rows)
        val next = transform(current, rows)
        if (next == current) return
        val model = SelectionModel.encode(rows.plan, next)
        selection.value = model
        saved[KEY_SEL_STAMP] = model.planStamp
        saved[KEY_SEL_EMPTY] = model.startEmpty
        saved[KEY_SEL_TOGGLED] = ArrayList(model.toggled)
    }

    private inline fun updateControls(transform: (DiffControls) -> DiffControls) {
        val next = transform(controls.value)
        if (next == controls.value) return
        controls.value = next
        saved[KEY_TAB] = next.tab.name
        saved[KEY_SORT_COL] = next.sortColumn
        saved[KEY_SORT_ASC] = next.sortAscending
        saved[KEY_QUERY] = next.query
        saved[KEY_FORMAT] = next.format?.name
        saved[KEY_REASON] = next.reason?.name
    }

    private fun restoreControls(): DiffControls = DiffControls(
        tab = saved.get<String>(KEY_TAB)?.let { n -> DiffTab.entries.firstOrNull { it.name == n } } ?: DiffTab.Transfer,
        sortColumn = saved.get<Int>(KEY_SORT_COL) ?: DiffControls.NO_SORT,
        sortAscending = saved.get<Boolean>(KEY_SORT_ASC) ?: true,
        query = saved.get<String>(KEY_QUERY).orEmpty(),
        format = saved.get<String>(KEY_FORMAT)?.let { n -> AudioFormat.entries.firstOrNull { it.name == n } },
        reason = saved.get<String>(KEY_REASON)?.let { n -> SkipReason.entries.firstOrNull { it.name == n } },
    )

    private fun restoreSelection(): SelectionModel {
        val stamp = saved.get<Long>(KEY_SEL_STAMP) ?: return SelectionModel.None
        return SelectionModel(
            planStamp = stamp,
            startEmpty = saved.get<Boolean>(KEY_SEL_EMPTY) ?: false,
            toggled = saved.get<ArrayList<String>>(KEY_SEL_TOGGLED)?.toSet() ?: emptySet(),
        )
    }

    /** What the filters and the order came to; [latest] is for the commands to see the list as it is. */
    private class Visible(val rows: PlanRows?, val controls: DiffControls, val list: List<DiffRow>) {
        companion object {
            val Empty = Visible(null, DiffControls(), emptyList())
        }
    }

    companion object {
        private const val KEY_TAB = "diff_tab"
        private const val KEY_SORT_COL = "diff_sort_col"
        private const val KEY_SORT_ASC = "diff_sort_asc"
        private const val KEY_QUERY = "diff_query"
        private const val KEY_FORMAT = "diff_format"
        private const val KEY_REASON = "diff_reason"
        private const val KEY_FOCUS = "diff_focus"
        private const val KEY_SEL_STAMP = "sel_stamp"
        private const val KEY_SEL_EMPTY = "sel_empty"
        private const val KEY_SEL_TOGGLED = "sel_toggled"
        private const val KEY_CANCEL_DIALOG = "cancel_dialog"
        private const val KEY_PICK_ROLE = "pick_role"
        private const val KEY_PICK_EXPECTED = "pick_expected"

        /** Its factory: the one bridge, [Walkman]'s graph's sync controller. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                // the notifications' tap opens this page, in the window that is open if there is one (as the podcasts' do)
                Walkman.openApp = { context ->
                    Intent(context, NagmedarActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(NAGMEDAR_EXTRA_DESTINATION, Destination.Walkman.route)
                }
                WalkmanViewModel(Walkman.graph(app).syncController, createSavedStateHandle())
            }
        }
    }
}
