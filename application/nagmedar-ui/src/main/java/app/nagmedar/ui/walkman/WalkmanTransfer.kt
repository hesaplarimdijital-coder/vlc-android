/*
 * Nağmedar 1.5.0: after Walkman Sync (app/src/main/kotlin/com/walkmansync/ui/TransferScreen.kt), the owner's own app;
 * drawn again with Nağmedar's parts.
 */
package app.nagmedar.ui.walkman

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.walkman.model.ItemFailure
import app.nagmedar.walkman.model.SyncError
import app.nagmedar.walkman.model.SyncState
import app.nagmedar.walkman.model.TransferOutcome
import app.nagmedar.walkman.model.TransferProgress
import app.nagmedar.walkman.model.TransferSummary

/**
 * "Aktarım": the copy as it goes (how much, how fast, how long still, the file being written; Duraklat, Sürdür, İptal),
 * then what it came to, with the files that could not be copied.
 */
@Composable
internal fun WalkmanTransfer(viewModel: WalkmanViewModel) {
    val state by viewModel.state.collectAsState()
    when (val current = state) {
        is SyncState.Transferring -> Running(viewModel, current.progress, paused = false)
        is SyncState.Paused -> Running(viewModel, current.progress, paused = true)
        is SyncState.Completed -> Summary(viewModel, current.summary, error = null)
        is SyncState.Failed -> {
            val summary = current.summary
            if (summary != null) {
                Summary(viewModel, summary, current.error)
            } else {
                Column(Modifier.fillMaxWidth().padding(NagTheme.dimens.pagePadding)) {
                    WalkmanNote(
                        StateTone.Error,
                        LocalContext.current.resources.errorText(current.error),
                        action = stringResource(R.string.nag_walkman_action_dismiss) to viewModel::dismissResult,
                    )
                }
            }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StatePanel(
                tone = StateTone.Neutral,
                icon = NagIcons.Downloads,
                title = stringResource(R.string.nag_walkman_transfer_empty_title),
                description = stringResource(R.string.nag_walkman_transfer_empty_desc),
                modifier = Modifier.testTag("walkman-transfer-empty"),
            )
        }
    }
}

@Composable
private fun Running(viewModel: WalkmanViewModel, progress: TransferProgress, paused: Boolean) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val res = LocalContext.current.resources
    val compact = currentWidthClass() == WidthClass.Compact
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dimens.pagePadding).testTag("walkman-running"),
        verticalArrangement = Arrangement.spacedBy(dimens.gapL),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            Text(
                stringResource(if (paused) R.string.nag_walkman_transfer_paused else R.string.nag_walkman_transfer_running),
                style = NagTheme.type.title,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            NagButton(
                stringResource(if (paused) R.string.nag_walkman_action_resume else R.string.nag_walkman_action_pause),
                viewModel::togglePause,
                icon = if (paused) NagIcons.Play else NagIcons.Pause,
                primary = true,
                modifier = Modifier.testTag("walkman-pause"),
            )
            NagButton(stringResource(R.string.nag_walkman_action_cancel), viewModel::requestCancel, icon = NagIcons.Close, modifier = Modifier.testTag("walkman-cancel"))
        }
        WalkmanMessage(viewModel)
        WalkmanSection(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatPercent(progress.fraction), style = NagTheme.type.display, color = colors.text, modifier = Modifier.weight(1f))
                Text(res.getString(R.string.nag_walkman_bytes_of, formatBytes(progress.completedBytes), formatBytes(progress.totalBytes)), style = NagTheme.type.body, color = colors.textSecondary)
            }
            NagProgressBar(progress.fraction, stringResource(R.string.nag_walkman_nav_transfer), color = if (paused) colors.warning else colors.accent)
        }
        val tiles = listOf(
            stringResource(R.string.nag_walkman_stat_remaining) to res.fileCount(progress.remainingItems),
            stringResource(R.string.nag_walkman_stat_speed) to formatRate(progress.bytesPerSecond),
            stringResource(R.string.nag_walkman_stat_eta) to if (paused) DASH else formatEta(progress.etaMillis),
            stringResource(R.string.nag_walkman_stat_done) to res.getString(R.string.nag_walkman_stat_done_value, progress.completedItems, progress.totalItems),
        )
        (if (compact) tiles.chunked(2) else listOf(tiles)).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.gapL)) {
                line.forEach { (label, value) ->
                    WalkmanSection(Modifier.weight(1f)) {
                        Text(label, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1)
                        Text(value, style = NagTheme.type.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        CurrentFile(progress)
        if (progress.failedItems > 0) WalkmanNote(StateTone.Warning, res.getString(R.string.nag_walkman_transfer_failed_so_far, progress.failedItems))
        if (paused) WalkmanNote(StateTone.Info, stringResource(R.string.nag_walkman_transfer_paused_hint))
    }
}

/** The file being written: its name, its folder, how much of it is written. */
@Composable
private fun CurrentFile(progress: TransferProgress) {
    val colors = NagTheme.colors
    val res = LocalContext.current.resources
    val item = progress.currentItem
    WalkmanSection(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.nag_walkman_current_file), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        if (item == null) {
            Text(stringResource(R.string.nag_walkman_current_none), style = NagTheme.type.body, color = colors.textSecondary)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS)) {
                Text(item.format.name, style = NagTheme.type.mono, color = colors.textSecondary)
                Text(item.targetName, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(formatBytes(item.size), style = NagTheme.type.mono, color = colors.textSecondary, maxLines = 1)
            }
            Text(item.targetDir, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            NagProgressBar(if (item.size > 0) progress.currentItemBytes.toFloat() / item.size else null, stringResource(R.string.nag_walkman_current_file))
            Text(res.getString(R.string.nag_walkman_bytes_of, formatBytes(progress.currentItemBytes), formatBytes(item.size)), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        }
    }
}

/** What the copy came to: how many files, how much, how long, how fast; what went wrong, file by file. */
@Composable
private fun Summary(viewModel: WalkmanViewModel, summary: TransferSummary, error: SyncError?) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val res = LocalContext.current.resources
    val state by viewModel.state.collectAsState()
    val fatal = summary.fatalError ?: error
    val tone = when (summary.outcome) {
        TransferOutcome.COMPLETED -> StateTone.Success
        TransferOutcome.COMPLETED_WITH_ERRORS -> StateTone.Warning
        TransferOutcome.CANCELLED -> StateTone.Neutral
        TransferOutcome.ABORTED -> StateTone.Error
    }
    val elapsed = summary.elapsedMillis
    val average = if (elapsed > 0) summary.copiedBytes * 1000L / elapsed else 0L
    LazyColumn(
        Modifier.fillMaxSize().testTag("walkman-summary"),
        contentPadding = PaddingValues(dimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(dimens.gapL),
    ) {
        item(key = "actions") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                Text(res.outcomeTitle(summary.outcome), style = NagTheme.type.title, color = colors.text, modifier = Modifier.weight(1f))
                NagButton(stringResource(R.string.nag_walkman_action_done), viewModel::dismissResult, modifier = Modifier.testTag("walkman-done"))
                NagButton(stringResource(R.string.nag_walkman_action_scan_again), viewModel::scan, icon = NagIcons.Resume, primary = true, enabled = state.canScan())
            }
        }
        item(key = "message") { WalkmanMessage(viewModel) }
        item(key = "headline") {
            WalkmanNote(tone, res.getString(R.string.nag_walkman_summary_headline, summary.copiedItems, summary.totalItems, formatBytes(summary.copiedBytes)))
        }
        if (fatal != null && fatal !is SyncError.Cancelled) item(key = "fatal") { WalkmanNote(StateTone.Error, res.errorText(fatal)) }
        if (!summary.indexSaved) item(key = "index") { WalkmanNote(StateTone.Warning, stringResource(R.string.nag_walkman_summary_index_not_saved)) }
        item(key = "stats") {
            WalkmanSection(Modifier.fillMaxWidth()) {
                WalkmanFact(stringResource(R.string.nag_walkman_summary_copied), res.getString(R.string.nag_walkman_stat_done_value, summary.copiedItems, summary.totalItems))
                WalkmanFact(stringResource(R.string.nag_walkman_summary_data), formatBytes(summary.copiedBytes))
                WalkmanFact(stringResource(R.string.nag_walkman_summary_time), formatDuration(elapsed))
                WalkmanFact(stringResource(R.string.nag_walkman_summary_speed), formatRate(average))
                WalkmanFact(stringResource(R.string.nag_walkman_summary_failed), summary.failures.size.toString())
                WalkmanFact(stringResource(R.string.nag_walkman_summary_not_attempted), summary.notAttempted.toString())
            }
        }
        if (summary.failures.isNotEmpty()) {
            item(key = "failures") { Text(res.getString(R.string.nag_walkman_failures_title, summary.failures.size), style = NagTheme.type.title, color = colors.text) }
            items(summary.failures, key = { "f:" + it.itemId }) { failure -> FailureRow(failure) }
        }
    }
}

@Composable
private fun FailureRow(failure: ItemFailure) {
    val colors = NagTheme.colors
    val res = LocalContext.current.resources
    WalkmanSection(Modifier.fillMaxWidth()) {
        Text(failure.targetPath, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
        Text(res.errorText(failure.error), style = NagTheme.type.body, color = colors.danger, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}
