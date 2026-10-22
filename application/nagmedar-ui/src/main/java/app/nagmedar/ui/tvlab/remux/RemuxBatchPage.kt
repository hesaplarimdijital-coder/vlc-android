package app.nagmedar.ui.tvlab.remux

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.library.videoLine
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.tvlab.Card
import app.nagmedar.ui.tvlab.CardTitle
import app.nagmedar.ui.tvlab.resources

/** What the batch's page asks for (S-1210). */
interface RemuxBatchActions {
    /** A folder from the system's picker to analyse. */
    fun chooseFolder()

    /** A library folder to analyse. */
    fun open(folder: BatchFolder)

    /** The folder the outputs go to, from the system's picker. */
    fun chooseOutput()

    /** [item]'s own plan, in the planner. */
    fun editPlan(item: BatchItem)

    fun queue()
}

/**
 * The batch's page (S-1210): the folder analysed (one from the system's picker, or a library
 * folder) with how far its probes got; the default plan for every film, against the TV Lab's
 * device; the output folder; then the films, each with what the probe found, its plan in a
 * line, whether it is chosen, its own plan or the default one; and the queuing with what came of it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemuxBatchPage(
    batch: RemuxBatchController,
    device: String,
    libraryFolders: List<BatchFolder>,
    actions: RemuxBatchActions,
    modifier: Modifier = Modifier,
) {
    val res = resources()
    val locale = currentLocale()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val films = batch.items
    val chosen = films.count { it.included }
    LazyColumn(
        modifier.fillMaxSize().testTag("tvlab-batch"),
        contentPadding = PaddingValues(start = dimens.pagePadding, end = dimens.pagePadding, bottom = dimens.gapL, top = dimens.gapS),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "source") {
            Card(Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.nag_tvlab_batch_source))
                Text(
                    batch.source?.label ?: stringResource(R.string.nag_tvlab_batch_no_folder),
                    style = NagTheme.type.mono.copy(fontSize = 13.sp),
                    color = if (batch.source == null) colors.textTertiary else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NagButton(stringResource(R.string.nag_tvlab_batch_choose_folder), actions::chooseFolder, icon = NagIcons.Folder, enabled = batch.available, modifier = Modifier.testTag("tvlab-batch-folder"))
                    libraryFolders.forEach { folder -> NagButton(folder.label, { actions.open(folder) }, icon = NagIcons.Movies, enabled = batch.available) }
                }
                if (libraryFolders.isNotEmpty()) Text(stringResource(R.string.nag_tvlab_batch_library_note), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                when {
                    !batch.available -> Text(stringResource(R.string.nag_tvlab_no_tools_text), style = NagTheme.type.labelSmall, color = toneColors(StateTone.Warning).content)
                    batch.unreadable -> Text(stringResource(R.string.nag_tvlab_batch_unreadable), style = NagTheme.type.label, color = toneColors(StateTone.Error).content)
                    batch.scanning -> {
                        val total = films.size
                        val label = if (total == 0) stringResource(R.string.nag_tvlab_batch_listing) else stringResource(R.string.nag_tvlab_batch_probing, batch.probed, total)
                        NagProgressBar(if (total == 0) null else batch.probed.toFloat() / total, label)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = NagTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
                            NagButton(stringResource(R.string.nag_tvlab_stop), batch::stop, icon = NagIcons.Close)
                        }
                    }
                    batch.source != null && films.isEmpty() -> Text(stringResource(R.string.nag_tvlab_batch_none_found), style = NagTheme.type.label, color = colors.textSecondary)
                    else -> Unit
                }
            }
        }
        item(key = "options") { OptionsCard(batch, device) }
        item(key = "output") {
            Card(Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.nag_tvlab_batch_output))
                Text(
                    batch.output?.label ?: stringResource(R.string.nag_tvlab_batch_no_folder),
                    style = NagTheme.type.mono.copy(fontSize = 13.sp),
                    color = if (batch.output == null) colors.textTertiary else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                NagButton(stringResource(R.string.nag_tvlab_batch_choose_output), actions::chooseOutput, icon = NagIcons.FolderAdd, modifier = Modifier.testTag("tvlab-batch-output"))
                Text(stringResource(R.string.nag_tvlab_batch_output_text), style = NagTheme.type.labelSmall, color = colors.textSecondary)
            }
        }
        if (films.isNotEmpty()) {
            item(key = "films") {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        pluralStringResource(R.plurals.nag_tvlab_batch_films, films.size, films.size),
                        style = NagTheme.type.bodyStrong,
                        color = colors.text,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    Spacer(Modifier.width(8.dp))
                    NagButton(stringResource(R.string.nag_tvlab_batch_all), { batch.includeAll(true) })
                    NagButton(stringResource(R.string.nag_tvlab_batch_none), { batch.includeAll(false) })
                }
            }
            items(films, key = { it.uri.toString() }) { item -> FilmRow(batch, item, actions, res, locale) }
        } else if (batch.source == null) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
                    StatePanel(
                        tone = StateTone.Info,
                        icon = NagIcons.Folder,
                        title = stringResource(R.string.nag_tvlab_batch_empty),
                        description = stringResource(R.string.nag_tvlab_batch_empty_text),
                    )
                }
            }
        }
        item(key = "queue") {
            Card(Modifier.fillMaxWidth().testTag("tvlab-batch-queue")) {
                batch.report?.let { report ->
                    Text(
                        pluralStringResource(R.plurals.nag_tvlab_batch_queued, report.queued, report.queued),
                        style = NagTheme.type.label,
                        color = if (report.queued > 0) toneColors(StateTone.Success).content else colors.textSecondary,
                        modifier = Modifier.semantics(mergeDescendants = true) {},
                    )
                    report.refused.forEach { (name, why) ->
                        Text(res.getString(R.string.nag_tvlab_batch_refused, name, refusalText(res, why)), style = NagTheme.type.labelSmall, color = toneColors(StateTone.Warning).content)
                    }
                }
                if (batch.output == null && chosen > 0) {
                    Text(stringResource(R.string.nag_tvlab_batch_needs_output), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    NagButton(
                        pluralStringResource(R.plurals.nag_tvlab_batch_queue, chosen, chosen),
                        actions::queue,
                        primary = true,
                        enabled = chosen > 0 && batch.output != null && !batch.queuing && !batch.scanning,
                        modifier = Modifier.testTag("tvlab-batch-queue-button"),
                    )
                }
            }
        }
    }
}

/** The default plan: the suggestions it applies to every film, chapters, attachments and tags. */
@Composable
private fun OptionsCard(batch: RemuxBatchController, device: String) {
    val options = batch.options
    Card(Modifier.fillMaxWidth().testTag("tvlab-batch-options")) {
        CardTitle(stringResource(R.string.nag_tvlab_batch_default_plan))
        Text(stringResource(R.string.nag_tvlab_batch_default_plan_text, device), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
        NagSwitch(options.convertDolbyVision, { batch.changeOptions(options.copy(convertDolbyVision = it)) }, stringResource(R.string.nag_tvlab_batch_o_dv))
        NagSwitch(options.fixDefaultAudio, { batch.changeOptions(options.copy(fixDefaultAudio = it)) }, stringResource(R.string.nag_tvlab_batch_o_default_audio))
        NagSwitch(options.dropUnsupportedSubtitles, { batch.changeOptions(options.copy(dropUnsupportedSubtitles = it)) }, stringResource(R.string.nag_tvlab_batch_o_subtitles))
        NagSwitch(options.chapters, { batch.changeOptions(options.copy(chapters = it)) }, stringResource(R.string.nag_tvlab_remux_plan_chapters_title))
        NagSwitch(options.attachments, { batch.changeOptions(options.copy(attachments = it)) }, stringResource(R.string.nag_tvlab_plan_attachments))
        NagSwitch(options.metadata, { batch.changeOptions(options.copy(metadata = it)) }, stringResource(R.string.nag_tvlab_plan_tags))
    }
}

/** A film: chosen or not with its name and state, what the probe found, its plan in a line, its own plan or the default one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilmRow(batch: RemuxBatchController, item: BatchItem, actions: RemuxBatchActions, res: Resources, locale: java.util.Locale) {
    val colors = NagTheme.colors
    val draft = item.draft
    Card(Modifier.fillMaxWidth().testTag("tvlab-batch-film")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NagCheckbox(item.included, { batch.include(item, it) }, item.name, modifier = Modifier.weight(1f), enabled = item.status == BatchStatus.Ready && !item.queued)
            Spacer(Modifier.width(8.dp))
            if (item.queued) ToneBadge(stringResource(R.string.nag_tvlab_batch_was_queued), StateTone.Success) else ToneBadge(statusLabel(res, item.status, batch.scanning), statusTone(item.status))
        }
        val tech = item.tech
        val facts = listOfNotNull(
            item.size.takeIf { it > 0 }?.let { sizeLabel(it, locale) },
            tech?.container?.takeIf { it.isNotEmpty() },
            tech?.video?.let(::videoLine),
            tech?.let { res.getQuantityString(R.plurals.nag_tvlab_plan_audio_count, it.audio.size, it.audio.size) },
            tech?.let { res.getQuantityString(R.plurals.nag_tvlab_plan_subtitle_count, it.subtitles.size, it.subtitles.size) },
        ).joinToString(" · ")
        if (facts.isNotEmpty()) Text(facts, style = NagTheme.type.labelSmall, color = colors.textSecondary)
        if (draft != null && item.status == BatchStatus.Ready) {
            val summary = draft.summary()
            val applied = draft.suggestions.count { draft.applied(it) }
            Text(
                listOfNotNull(
                    summaryLines(res, draft, summary).firstOrNull(),
                    res.getString(R.string.nag_tvlab_remux_plan_dv).takeIf { draft.convertDolbyVision && draft.canConvert },
                    res.getQuantityString(R.plurals.nag_tvlab_batch_suggestions, applied, applied).takeIf { applied > 0 },
                ).joinToString(" · "),
                style = NagTheme.type.labelSmall,
                color = colors.text,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FidelityBadge(if (summary.lossy > 0) Fidelity.Lossy else if (summary.metadataChanged > 0) Fidelity.LosslessMetadata else Fidelity.Lossless)
                if (item.overridden) ToneBadge(stringResource(R.string.nag_tvlab_batch_own_plan), StateTone.Info)
            }
            if (!item.queued) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NagButton(stringResource(R.string.nag_tvlab_batch_edit_plan), { actions.editPlan(item) })
                    if (item.overridden) NagButton(stringResource(R.string.nag_tvlab_batch_default_again), { batch.resetPlan(item) })
                }
            }
        }
        item.refusal?.let { Text(refusalText(res, it), style = NagTheme.type.labelSmall, color = toneColors(StateTone.Warning).content) }
    }
}

private fun statusLabel(res: Resources, status: BatchStatus, scanning: Boolean): String = res.getString(
    when (status) {
        BatchStatus.Waiting -> if (scanning) R.string.nag_tvlab_batch_s_waiting else R.string.nag_tvlab_batch_s_not_read
        BatchStatus.Probing -> R.string.nag_tvlab_batch_s_probing
        BatchStatus.Ready -> R.string.nag_tvlab_batch_s_ready
        BatchStatus.Unreadable -> R.string.nag_tvlab_batch_s_unreadable
        BatchStatus.NoVideo -> R.string.nag_tvlab_no_video
        BatchStatus.NeedsEncode -> R.string.nag_tvlab_video_reencode
    },
)

private fun statusTone(status: BatchStatus): StateTone = when (status) {
    BatchStatus.Ready -> StateTone.Success
    BatchStatus.Unreadable, BatchStatus.NeedsEncode, BatchStatus.NoVideo -> StateTone.Warning
    BatchStatus.Probing -> StateTone.Info
    BatchStatus.Waiting -> StateTone.Neutral
}

/** Why a film was not queued, in words. */
fun refusalText(res: Resources, refusal: BatchRefusal): String = res.getString(
    when (refusal) {
        BatchRefusal.Exists -> R.string.nag_tvlab_batch_r_exists
        BatchRefusal.NotCreated -> R.string.nag_tvlab_batch_r_not_created
        BatchRefusal.SameFile -> R.string.nag_tvlab_remux_same_file
        BatchRefusal.NotEmpty -> R.string.nag_tvlab_remux_not_empty
        BatchRefusal.NotFile -> R.string.nag_tvlab_remux_not_file
        BatchRefusal.InUse -> R.string.nag_tvlab_remux_in_use
    },
)
