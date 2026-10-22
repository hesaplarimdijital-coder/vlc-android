package app.nagmedar.ui.tvlab.remux

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nagmedar.data.RemuxJobState
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.tvlab.Card
import app.nagmedar.ui.tvlab.elapsedLabel
import app.nagmedar.ui.tvlab.resources
import java.text.DateFormat
import java.util.Date

/** What the history asks for (S-208). */
interface RemuxHistoryActions {
    /** A new job of the same film and plan, into a new file the system's picker names. */
    fun rerun(job: RemuxJobView)

    /** The output opened by another app. */
    fun open(job: RemuxJobView)

    /** The output sent with the share sheet. */
    fun share(job: RemuxJobView)

    /** The report sent with the share sheet ([json]: as a JSON file, else as text). */
    fun shareReport(job: RemuxJobView, json: Boolean)

    /** The report written into a file the system's picker names. */
    fun saveReport(job: RemuxJobView, json: Boolean)

    /** The entry forgotten; the files stay (a job's unfinished output, which is not one, is removed with it). */
    fun forget(job: RemuxJobView)
}

/** The finished jobs, the last finished first. */
fun finishedJobs(jobs: List<RemuxJobView>): List<RemuxJobView> =
    jobs.filter { !it.active }.sortedWith(compareByDescending<RemuxJobView> { it.finishedAt ?: it.createdAt }.thenByDescending { it.id })

/**
 * The remux history (S-208): every finished job with its state, files, when, how long, how much
 * and how fast; opened, its plan, the verification (what was hashed, the hash, the packets
 * compared, the Dolby Vision checks), what happened on the way (pauses, restarts, the system's
 * time limits) and why it failed; and a new run of it, its output opened or shared, its report
 * shared or saved as text or JSON, the entry forgotten.
 */
@Composable
fun RemuxHistoryPage(jobs: List<RemuxJobView>, actions: RemuxHistoryActions, modifier: Modifier = Modifier) {
    val finished = finishedJobs(jobs)
    val dimens = NagTheme.dimens
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }
    if (finished.isEmpty()) {
        Box(modifier.fillMaxSize().padding(top = 32.dp).testTag("tvlab-history"), contentAlignment = Alignment.TopCenter) {
            StatePanel(
                tone = StateTone.Info,
                icon = NagIcons.History,
                title = stringResource(R.string.nag_tvlab_history_empty),
                description = stringResource(R.string.nag_tvlab_history_empty_text),
            )
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize().testTag("tvlab-history"),
        contentPadding = PaddingValues(start = dimens.pagePadding, end = dimens.pagePadding, top = dimens.gapS, bottom = dimens.gapL),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "note") {
            Text(stringResource(R.string.nag_tvlab_history_note), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
        }
        items(finished, key = { it.id }) { job ->
            HistoryEntry(job, open = opened == job.id, onToggle = { opened = if (opened == job.id) null else job.id }, actions = actions)
        }
    }
}

/** One finished job: its summary, and opened its whole report and actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryEntry(job: RemuxJobView, open: Boolean, onToggle: () -> Unit, actions: RemuxHistoryActions) {
    val res = resources()
    val locale = currentLocale()
    val colors = NagTheme.colors
    val shown = stringResource(if (open) R.string.nag_tvlab_history_expanded else R.string.nag_tvlab_history_collapsed)
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Card(Modifier.fillMaxWidth().testTag("tvlab-history-job")) {
        Row(
            Modifier
                .fillMaxWidth()
                .focusRing(state, shape)
                .background(stateBackground(state), shape)
                .nagClickable(interaction, onToggle, onClickLabel = stringResource(R.string.nag_tvlab_history_details))
                .padding(4.dp)
                .semantics(mergeDescendants = true) { stateDescription = shown },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (open) NagIcons.ChevronDown else NagIcons.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    job.outputName,
                    style = NagTheme.type.mono.copy(fontSize = 13.sp),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(res.getString(R.string.nag_jobs_from, job.inputName), style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(RemuxReport.line(res, job, locale), style = NagTheme.type.labelSmall, color = colors.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToneBadge(stateLabel(res, job), stateTone(job))
                if (job.verified) ToneBadge(stringResource(R.string.nag_tvlab_remux_identical), StateTone.Success)
            }
        }
        if (open) {
            Details(job)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                NagButton(stringResource(R.string.nag_tvlab_history_rerun), { actions.rerun(job) }, enabled = job.plan != null)
                if (job.state == RemuxJobState.DONE) {
                    NagButton(stringResource(R.string.nag_tvlab_history_open), { actions.open(job) }, icon = NagIcons.Play)
                    NagButton(stringResource(R.string.nag_lab_share), { actions.share(job) })
                }
                MenuButton(
                    text = stringResource(R.string.nag_tvlab_history_report),
                    entries = {
                        listOf(
                            MenuEntry.Item(res.getString(R.string.nag_tvlab_history_share_text), { actions.shareReport(job, json = false) }),
                            MenuEntry.Item(res.getString(R.string.nag_tvlab_history_share_json), { actions.shareReport(job, json = true) }),
                            MenuEntry.Separator,
                            MenuEntry.Item(res.getString(R.string.nag_tvlab_history_save_text), { actions.saveReport(job, json = false) }),
                            MenuEntry.Item(res.getString(R.string.nag_tvlab_history_save_json), { actions.saveReport(job, json = true) }),
                        )
                    },
                )
                NagButton(stringResource(R.string.nag_tvlab_history_forget), { actions.forget(job) }, icon = NagIcons.Delete)
            }
        }
    }
}

/** The report of an opened job: files and times, the plan, the verification, the events, the error. */
@Composable
private fun Details(job: RemuxJobView) {
    val res = resources()
    val locale = currentLocale()
    val colors = NagTheme.colors
    val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
    val times = DateFormat.getTimeInstance(DateFormat.MEDIUM, locale)
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section(stringResource(R.string.nag_tvlab_history_files)) {
            Fact(res.getString(R.string.nag_tvlab_history_input, listOfNotNull(job.inputName, job.inputSize.takeIf { it > 0 }?.let { sizeLabel(it, locale) }).joinToString(" · ")))
            Fact(res.getString(R.string.nag_tvlab_history_output, job.outputName))
            job.startedAt?.let { Fact(res.getString(R.string.nag_tvlab_history_started, dates.format(Date(it)))) }
            job.finishedAt?.let { Fact(res.getString(R.string.nag_tvlab_history_finished, dates.format(Date(it)))) }
            RemuxReport.durationMs(job)?.let { Fact(res.getString(R.string.nag_tvlab_history_duration, elapsedLabel(res, it))) }
            Fact(res.getString(R.string.nag_tvlab_history_attempts, job.attempts))
            job.result?.let { Fact(res.getString(R.string.nag_tvlab_history_bytes, sizeLabel(it.bytesIn, locale), sizeLabel(it.bytesOut, locale))) }
            RemuxReport.speed(res, job, locale)?.let { Fact(res.getString(R.string.nag_tvlab_history_rate, it)) }
        }
        job.plan?.let { plan ->
            Section(res.getString(R.string.nag_tvlab_history_plan, planLine(res, job))) {
                plan.streams.forEach { Fact(RemuxReport.streamLine(res, it), mono = true) }
            }
        }
        job.verification?.let { verification ->
            Section(stringResource(R.string.nag_tvlab_history_verification)) {
                SelectionContainer {
                    Text(
                        "SHA-256 " + verification.hash,
                        style = NagTheme.type.mono.copy(fontSize = 11.sp),
                        color = colors.text,
                        modifier = Modifier.testTag("tvlab-history-hash"),
                    )
                }
                if (!verification.ok && verification.expectedHash.isNotEmpty()) {
                    Fact(res.getString(R.string.nag_tvlab_history_v_expected, verification.expectedHash), mono = true)
                }
                RemuxReport.verificationLines(res, verification, locale).forEachIndexed { i, line ->
                    Text(
                        line,
                        style = NagTheme.type.labelSmall,
                        color = if (i == 0) toneColors(if (verification.ok) StateTone.Success else StateTone.Error).content else colors.textSecondary,
                    )
                }
            }
        }
        if (job.events.isNotEmpty()) {
            Section(stringResource(R.string.nag_tvlab_history_events)) {
                job.events.forEach { event ->
                    Row {
                        Text(times.format(Date(event.at)), style = NagTheme.type.mono.copy(fontSize = 11.sp), color = colors.textTertiary, modifier = Modifier.width(76.dp))
                        Text(RemuxReport.eventText(res, event), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                    }
                }
            }
        }
        if (job.state != RemuxJobState.DONE && !job.error.isNullOrEmpty()) {
            Text(
                res.getString(R.string.nag_tvlab_history_error, errorText(res, job)),
                style = NagTheme.type.label,
                color = if (job.state == RemuxJobState.FAILED) toneColors(StateTone.Error).content else colors.textSecondary,
            )
            if (job.leftover) Text(stringResource(R.string.nag_tvlab_remux_left), style = NagTheme.type.labelSmall, color = toneColors(StateTone.Warning).content)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = NagTheme.type.bodyStrong, color = NagTheme.colors.text, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun Fact(text: String, mono: Boolean = false) {
    Text(
        text,
        style = if (mono) NagTheme.type.mono.copy(fontSize = 12.sp) else NagTheme.type.labelSmall,
        color = NagTheme.colors.textSecondary,
    )
}
