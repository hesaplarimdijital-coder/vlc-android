package app.nagmedar.ui.tvlab.remux

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nagmedar.data.RemuxJobError
import app.nagmedar.data.RemuxJobState
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.tvlab.Card
import app.nagmedar.ui.tvlab.CardTitle
import app.nagmedar.ui.tvlab.clockLabel
import app.nagmedar.ui.tvlab.elapsedLabel
import app.nagmedar.ui.tvlab.percentLabel
import app.nagmedar.ui.tvlab.resources
import java.util.Locale

/** The remux queue's card (S-1206): the film's jobs, newest first, each with its state. */
@Composable
fun RemuxCard(jobs: List<RemuxJobView>, onCancel: (Long) -> Unit, onRemove: (Long) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().testTag("tvlab-remux")) {
        CardTitle(stringResource(R.string.nag_tvlab_remux_queue))
        Text(stringResource(R.string.nag_tvlab_remux_note), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
        jobs.forEachIndexed { index, job ->
            if (index > 0) HorizontalRule(Modifier.padding(vertical = 4.dp))
            RemuxJobRow(job, onCancel = { onCancel(job.id) }, onRemove = { onRemove(job.id) })
        }
    }
}

/**
 * One job: the output's name and the state's badge, the plan in a line, then what the state
 * calls for: the progress with how far and how much, why it waits, the verdict with its badge
 * ("Video bit bit aynı") and the hash, or why it failed; and Cancel or Remove.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemuxJobRow(job: RemuxJobView, onCancel: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val res = resources()
    val locale = currentLocale()
    val colors = NagTheme.colors
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("tvlab-remux-job"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(NagIcons.Movies, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    job.outputName,
                    style = NagTheme.type.mono.copy(fontSize = 13.sp),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    res.getString(R.string.nag_tvlab_remux_from, job.inputName),
                    style = NagTheme.type.labelSmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            ToneBadge(stateLabel(res, job), stateTone(job))
        }
        Text(planLine(res, job), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        when (job.state) {
            RemuxJobState.QUEUED -> Text(
                stringResource(
                    when {
                        job.waitingForApp -> R.string.nag_tvlab_remux_waiting_app
                        job.interruption == RemuxEvent.FOREGROUND_TIMEOUT -> R.string.nag_tvlab_remux_timeout
                        else -> R.string.nag_tvlab_remux_queued_text
                    },
                ),
                style = NagTheme.type.labelSmall,
                color = if (job.waitingForApp || job.interruption == RemuxEvent.FOREGROUND_TIMEOUT) toneColors(StateTone.Warning).content else colors.textSecondary,
            )
            RemuxJobState.RUNNING, RemuxJobState.VERIFYING, RemuxJobState.PAUSED -> {
                val known = job.totalSeconds > 0
                val label = if (job.state == RemuxJobState.VERIFYING) {
                    stringResource(R.string.nag_tvlab_remux_verifying, percentLabel(job.progress, locale))
                } else {
                    stringResource(R.string.nag_tvlab_remux_running, percentLabel(job.progress, locale))
                }
                NagProgressBar(if (known) job.progress else null, label, Modifier.padding(top = 2.dp))
                Text(progressDetail(res, job, locale), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                job.pauseReason?.let { reason ->
                    Text(
                        stringResource(if (reason == RemuxJobError.BATTERY) R.string.nag_tvlab_remux_paused_battery else R.string.nag_tvlab_remux_paused_thermal, RemuxPolicy.RESUME_ABOVE),
                        style = NagTheme.type.label,
                        color = toneColors(StateTone.Warning).content,
                    )
                }
            }
            RemuxJobState.DONE -> Verdict(job, locale)
            else -> Text(errorText(res, job), style = NagTheme.type.label, color = if (job.state == RemuxJobState.FAILED) toneColors(StateTone.Error).content else colors.textSecondary)
        }
        if (job.restarted) Text(stringResource(R.string.nag_tvlab_remux_restarted, job.attempts), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        if (job.slowed && job.active) Text(stringResource(R.string.nag_tvlab_remux_slowed), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (job.active) {
                NagButton(stringResource(R.string.nag_tvlab_remux_cancel), onCancel, icon = NagIcons.Close)
            } else {
                NagButton(stringResource(R.string.nag_tvlab_remux_remove), onRemove)
            }
        }
    }
}

/** A finished job: the badge of the verification, what was compared, the hash and the Dolby Vision check. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Verdict(job: RemuxJobView, locale: Locale) {
    val res = resources()
    val colors = NagTheme.colors
    val verification = job.verification
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("tvlab-remux-verdict")) {
        if (job.verified) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(NagIcons.Shield, contentDescription = null, tint = toneColors(StateTone.Success).content, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                ToneBadge(stringResource(R.string.nag_tvlab_remux_identical), StateTone.Success)
            }
        }
        val took = listOfNotNull(
            job.startedAt?.let { start -> job.finishedAt?.let { end -> elapsedLabel(res, end - start) } },
            job.bytesOut.takeIf { it > 0 }?.let { sizeLabel(it, locale) },
        ).joinToString(" · ")
        if (took.isNotEmpty()) Text(took, style = NagTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.align(Alignment.CenterVertically))
    }
    if (verification != null) {
        Text(
            res.getString(R.string.nag_tvlab_remux_compared, verification.packets, scopeLabel(res, verification.hashScope)),
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
        )
        Text(
            "SHA-256 " + verification.hash,
            style = NagTheme.type.mono.copy(fontSize = 11.sp),
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (verification.dvExpectedProfile > 0) {
            Text(
                res.getString(R.string.nag_tvlab_remux_dv_checked, verification.rpus),
                style = NagTheme.type.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}

/** "Sırada", "Remux yapılıyor", "Doğrulanıyor", "Tamamlandı" … */
fun stateLabel(res: Resources, job: RemuxJobView): String = res.getString(
    when (job.state) {
        RemuxJobState.QUEUED -> R.string.nag_tvlab_remux_queued
        RemuxJobState.RUNNING -> R.string.nag_tvlab_remux_running_badge
        RemuxJobState.PAUSED -> R.string.nag_tvlab_remux_paused
        RemuxJobState.VERIFYING -> R.string.nag_tvlab_remux_verifying_badge
        RemuxJobState.DONE -> R.string.nag_tvlab_remux_done
        RemuxJobState.CANCELLED -> R.string.nag_tvlab_remux_cancelled
        else -> R.string.nag_tvlab_remux_failed
    },
)

fun stateTone(job: RemuxJobView): StateTone = when (job.state) {
    RemuxJobState.DONE -> StateTone.Success
    RemuxJobState.FAILED -> StateTone.Error
    RemuxJobState.PAUSED -> StateTone.Warning
    RemuxJobState.RUNNING, RemuxJobState.VERIFYING -> StateTone.Info
    else -> StateTone.Neutral
}

/** "6 iz · Dolby Vision P7 → P8.1 · bölümler · ekler". */
fun planLine(res: Resources, job: RemuxJobView): String {
    val plan = job.plan ?: return ""
    return listOfNotNull(
        res.getQuantityString(R.plurals.nag_tvlab_remux_tracks, plan.streams.size, plan.streams.size),
        res.getString(R.string.nag_tvlab_remux_plan_dv).takeIf { job.convertsDolbyVision },
        res.getString(R.string.nag_tvlab_remux_plan_chapters).takeIf { plan.chapters },
        res.getString(R.string.nag_tvlab_remux_plan_attachments).takeIf { plan.attachments },
    ).joinToString(" · ")
}

/** "12:34 / 2:11:45 · 2,1 GB yazıldı". */
fun progressDetail(res: Resources, job: RemuxJobView, locale: Locale): String = res.getString(
    R.string.nag_tvlab_remux_progress_detail,
    clockLabel(job.mediaSeconds.toInt()),
    if (job.totalSeconds > 0) clockLabel(job.totalSeconds.toInt()) else "–",
    sizeLabel(job.bytesOut, locale),
)

/** What the hash covers, in words. */
fun scopeLabel(res: Resources, scope: String): String = res.getString(
    when (scope) {
        "base_layer_nal_units" -> R.string.nag_tvlab_remux_scope_base_layer
        "nal_units" -> R.string.nag_tvlab_remux_scope_nal_units
        else -> R.string.nag_tvlab_remux_scope_packets
    },
)

/** Why a job failed or stopped, in words. */
fun errorText(res: Resources, job: RemuxJobView): String {
    val error = job.error.orEmpty()
    return when {
        error.startsWith(RemuxJobError.ENGINE) -> res.getString(R.string.nag_tvlab_remux_e_engine, error.removePrefix(RemuxJobError.ENGINE))
        error == RemuxJobError.VERIFY -> res.getString(R.string.nag_tvlab_remux_e_verify)
        error == RemuxJobError.CANCELLED -> res.getString(R.string.nag_tvlab_remux_e_cancelled)
        error == RemuxJobError.NO_TOOLS -> res.getString(R.string.nag_tvlab_no_tools)
        error == RemuxJobError.OPEN_INPUT -> res.getString(R.string.nag_tvlab_remux_e_open_input)
        error == RemuxJobError.OPEN_OUTPUT -> res.getString(R.string.nag_tvlab_remux_e_open_output)
        error == RemuxJobError.BATTERY -> res.getString(R.string.nag_tvlab_remux_e_battery)
        error == RemuxJobError.THERMAL -> res.getString(R.string.nag_tvlab_remux_e_thermal)
        error == RemuxJobError.SAME_FILE -> res.getString(R.string.nag_tvlab_remux_same_file)
        error == RemuxJobError.OUTPUT_NOT_EMPTY -> res.getString(R.string.nag_tvlab_remux_not_empty)
        else -> res.getString(R.string.nag_tvlab_remux_failed)
    }
}
