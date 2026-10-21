package app.nagmedar.ui.transcode

import android.content.res.Resources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.data.TranscodeJobEntry
import app.nagmedar.data.TranscodeJobError
import app.nagmedar.data.TranscodeJobState
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.convert.ConvertPolicy
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat
import java.util.Locale
import org.videolan.vlc.util.UrlUtils

/** S-1303: a profile in a line: "MP4 · H.264 4000 kbps, ≤ 720p · AAC 160 kbps, 2 kanal · altyazılar korunur". */
fun profileSummary(res: Resources, profile: TranscodeProfile): String {
    val video = profile.video?.let { v ->
        if (v.codec == VideoCodec.Copy) {
            res.getString(R.string.nag_conv_sum_video_copy)
        } else {
            listOfNotNull(
                res.getString(R.string.nag_conv_sum_kbps, v.codec.label, v.bitrateKbps),
                v.maxHeight?.let { res.getString(R.string.nag_conv_sum_height, it) },
                v.fps?.let { res.getString(R.string.nag_vis_fps_value, it) },
            ).joinToString(", ")
        }
    }
    val audio = profile.audio?.let { a ->
        when {
            a.codec == AudioCodec.Copy -> res.getString(R.string.nag_conv_sum_audio_copy)
            else -> listOfNotNull(
                if (a.codec.lossless) res.getString(R.string.nag_conv_sum_lossless, a.codec.label) else res.getString(R.string.nag_conv_sum_kbps, a.codec.label, a.bitrateKbps),
                res.getQuantityString(R.plurals.nag_conv_sum_channels, a.channels, a.channels),
            ).joinToString(", ")
        }
    }
    val subtitles = when (profile.subtitles) {
        SubtitleMode.Keep -> res.getString(R.string.nag_conv_sum_subs_keep)
        SubtitleMode.Burn -> res.getString(R.string.nag_conv_sum_subs_burn)
        SubtitleMode.None -> null
    }
    return listOfNotNull(profile.container.label, video, audio, subtitles).joinToString(" · ")
}

/** S-1303: a profile's name as the interface shows it: a preset's in the interface's language. */
@Composable
fun profileName(profile: TranscodeProfile): String = profile.nameRes?.let { stringResource(it) } ?: profile.name

/**
 * S-1303: "İşler": the video conversions' queue, newest first, each with its state, progress and
 * Cancel or Remove.
 */
@Composable
fun JobsCard(jobs: List<TranscodeJobView>, onCancel: (Long) -> Unit, onRemove: (Long) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().testTag("convert-jobs")) {
        CardTitle(stringResource(R.string.nag_jobs_title))
        Text(stringResource(R.string.nag_jobs_note), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
        if (jobs.isEmpty()) {
            Text(stringResource(R.string.nag_jobs_empty), style = NagTheme.type.body, color = NagTheme.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
        }
        jobs.forEachIndexed { index, job ->
            if (index > 0) HorizontalRule(Modifier.padding(vertical = 4.dp))
            TranscodeJobRow(job, onCancel = { onCancel(job.id) }, onRemove = { onRemove(job.id) })
        }
    }
}

/**
 * S-1303: the profiles: the presets and the user's own, each in a line with what it makes; a
 * preset this build cannot encode says why. The user's are edited and deleted here.
 */
@Composable
fun ProfilesCard(
    profiles: List<TranscodeProfile>,
    onNew: () -> Unit,
    onEdit: (TranscodeProfile) -> Unit,
    onDelete: (TranscodeProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = resources()
    val colors = NagTheme.colors
    Card(modifier.fillMaxWidth().testTag("convert-profiles")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.nag_conv_profiles), Modifier.weight(1f))
            NagButton(stringResource(R.string.nag_conv_profile_new), onNew, icon = NagIcons.Plus, modifier = Modifier.testTag("convert-profile-new"))
        }
        profiles.forEachIndexed { index, profile ->
            if (index > 0) HorizontalRule(Modifier.padding(vertical = 2.dp))
            val usable = TranscodeProfiles.validate(profile).isEmpty()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(profileName(profile), style = NagTheme.type.bodyStrong, color = if (usable) colors.text else colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(profileSummary(res, profile), style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!usable) Text(stringResource(R.string.nag_conv_problem_encoder), style = NagTheme.type.labelSmall, color = toneColors(StateTone.Warning).content)
                }
                if (!profile.builtin) {
                    NagIconButton(NagIcons.Settings, stringResource(R.string.nag_conv_profile_edit), { onEdit(profile) })
                    NagIconButton(NagIcons.Delete, stringResource(R.string.nag_conv_profile_delete), { onDelete(profile) })
                } else if (usable) {
                    ToneBadge(stringResource(R.string.nag_conv_preset), StateTone.Neutral)
                }
            }
        }
    }
}

/**
 * S-1303: the HLS stream on the page: nothing on the air with the way to start one; starting;
 * on the air with its address, a QR code to open it on another device, Copy and Stop; or why it
 * failed.
 */
@Composable
fun HlsCard(state: HlsState, onStart: () -> Unit, onStop: () -> Unit, onCopy: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().testTag("convert-hls")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.nag_hls_title), Modifier.weight(1f))
            when (state) {
                is HlsState.Live -> ToneBadge(stringResource(if (state.ended) R.string.nag_hls_ended_badge else R.string.nag_hls_live_badge), if (state.ended) StateTone.Neutral else StateTone.Success)
                is HlsState.Starting -> ToneBadge(stringResource(R.string.nag_hls_starting_badge), StateTone.Info)
                else -> Unit
            }
        }
        HlsBody(state, onStart, onStop, onCopy, onDismiss)
    }
}

/** S-1303: the stream's state under its title: the card's and the sheet's. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HlsBody(state: HlsState, onStart: () -> Unit, onStop: () -> Unit, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = NagTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (state) {
            HlsState.Idle -> {
                Text(stringResource(R.string.nag_hls_idle), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                NagButton(stringResource(R.string.nag_hls_start_menu), onStart, icon = NagIcons.Streams, modifier = Modifier.testTag("convert-hls-start"))
            }
            is HlsState.Starting -> {
                Text(state.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                NagProgressBar(null, stringResource(R.string.nag_hls_starting))
                NagButton(stringResource(R.string.nag_hls_stop), onStop, icon = NagIcons.Stop)
            }
            is HlsState.Live -> {
                Text(state.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(state.quality.label), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    QrCode(state.url, Modifier.size(132.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SelectionContainer {
                            Text(state.url, style = NagTheme.type.mono, color = colors.text, modifier = Modifier.testTag("convert-hls-url"))
                        }
                        Text(stringResource(R.string.nag_hls_how), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                    }
                }
                if (state.ended) Text(stringResource(R.string.nag_hls_ended), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NagButton(stringResource(R.string.nag_hls_copy), { onCopy(state.url) })
                    NagButton(stringResource(R.string.nag_hls_stop), onStop, icon = NagIcons.Stop, primary = true, modifier = Modifier.testTag("convert-hls-stop"))
                }
            }
            is HlsState.Failed -> {
                Text(stringResource(state.reason.message), style = NagTheme.type.label, color = toneColors(StateTone.Error).content)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NagButton(stringResource(R.string.nag_hls_again), onStart)
                    NagButton(stringResource(R.string.nag_hls_dismiss), onDismiss)
                }
            }
        }
    }
}

/** S-1303: [url] as a QR code, black on white whatever the theme (a camera reads it so), its pixels kept sharp. */
@Composable
fun QrCode(url: String, modifier: Modifier = Modifier) {
    val bitmap = remember(url) { runCatching { UrlUtils.generateQRCode(url, QR_PIXELS).asImageBitmap() }.getOrNull() }
    val description = stringResource(R.string.nag_hls_qr, url)
    if (bitmap != null) {
        Image(
            bitmap,
            contentDescription = null,
            filterQuality = FilterQuality.None,
            modifier = modifier
                .background(Color.White, RoundedCornerShape(6.dp))
                .padding(6.dp)
                .semantics { contentDescription = description }
                .testTag("convert-hls-qr"),
        )
    }
}

/** The QR code's side in pixels: drawn smaller, sharp. */
private const val QR_PIXELS = 400

/** S-1303: a job as the card shows it: the row with its profile read. */
@Immutable
data class TranscodeJobView(
    val id: Long,
    val inputName: String,
    val outputName: String,
    val profile: TranscodeProfile?,
    val state: String,
    val progress: Float,
    val mediaMs: Long,
    val totalMs: Long,
    val bytesOut: Long,
    val attempts: Int,
    val startedAt: Long?,
    val finishedAt: Long?,
    val error: String?,
    val restarted: Boolean,
) {
    val active: Boolean get() = state !in TranscodeJobState.FINAL

    companion object {
        fun of(entry: TranscodeJobEntry): TranscodeJobView = TranscodeJobView(
            id = entry.id,
            inputName = entry.inputName,
            outputName = entry.outputName,
            profile = TranscodeProfile.parse(entry.profile),
            state = entry.state,
            progress = entry.progress,
            mediaMs = entry.mediaMs,
            totalMs = entry.totalMs,
            bytesOut = entry.bytesOut,
            attempts = entry.attempts,
            startedAt = entry.startedAt,
            finishedAt = entry.finishedAt,
            error = entry.error,
            restarted = entry.restarted,
        )
    }
}

/** S-1303: one job: the new file and its source, the profile, the state; the progress while it runs; why it failed; Cancel or Remove. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscodeJobRow(job: TranscodeJobView, onCancel: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val res = resources()
    val locale = res.locale()
    val colors = NagTheme.colors
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("convert-job"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(NagIcons.Convert, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    job.outputName,
                    style = NagTheme.type.bodyStrong,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(res.getString(R.string.nag_jobs_from, job.inputName), style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            ToneBadge(jobStateLabel(res, job), jobStateTone(job))
        }
        job.profile?.let { Text(profileSummary(res, it), style = NagTheme.type.labelSmall, color = colors.textSecondary) }
        when (job.state) {
            TranscodeJobState.QUEUED -> {
                val timedOut = job.error == TranscodeJobError.TIMEOUT
                Text(
                    stringResource(if (timedOut) R.string.nag_jobs_timeout else R.string.nag_jobs_queued_text),
                    style = NagTheme.type.labelSmall,
                    color = if (timedOut) toneColors(StateTone.Warning).content else colors.textSecondary,
                )
            }
            TranscodeJobState.RUNNING, TranscodeJobState.PAUSED -> {
                NagProgressBar(if (job.totalMs > 0 || job.progress > 0f) job.progress else null, stringResource(R.string.nag_conv_running, percentText(job.progress, locale)), Modifier.padding(top = 2.dp))
                Text(
                    res.getString(
                        R.string.nag_jobs_progress_detail,
                        clockText(job.mediaMs),
                        if (job.totalMs > 0) clockText(job.totalMs) else "–",
                        sizeLabel(job.bytesOut, locale),
                    ),
                    style = NagTheme.type.labelSmall,
                    color = colors.textSecondary,
                )
                if (job.state == TranscodeJobState.PAUSED) {
                    Text(
                        if (job.error == TranscodeJobError.BATTERY) {
                            stringResource(R.string.nag_jobs_paused_battery, ConvertPolicy.RESUME_ABOVE)
                        } else {
                            stringResource(R.string.nag_jobs_paused_thermal)
                        },
                        style = NagTheme.type.label,
                        color = toneColors(StateTone.Warning).content,
                    )
                }
            }
            TranscodeJobState.DONE -> {
                val took = listOfNotNull(
                    job.startedAt?.let { start -> job.finishedAt?.let { end -> clockText(end - start) } },
                    job.bytesOut.takeIf { it > 0 }?.let { sizeLabel(it, locale) },
                ).joinToString(" · ")
                if (took.isNotEmpty()) Text(took, style = NagTheme.type.labelSmall, color = colors.textSecondary)
            }
            else -> Text(
                jobErrorText(res, job),
                style = NagTheme.type.label,
                color = if (job.state == TranscodeJobState.FAILED) toneColors(StateTone.Error).content else colors.textSecondary,
            )
        }
        if (job.restarted && job.attempts > 0) Text(stringResource(R.string.nag_jobs_restarted, job.attempts), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (job.active) {
                NagButton(stringResource(R.string.nag_jobs_cancel), onCancel, icon = NagIcons.Close)
            } else {
                NagButton(stringResource(R.string.nag_jobs_remove), onRemove)
            }
        }
    }
}

/** "Sırada", "Dönüştürülüyor", "Duraklatıldı", "Tamamlandı" … */
fun jobStateLabel(res: Resources, job: TranscodeJobView): String = res.getString(
    when (job.state) {
        TranscodeJobState.QUEUED -> R.string.nag_jobs_queued
        TranscodeJobState.RUNNING -> R.string.nag_conv_running_badge
        TranscodeJobState.PAUSED -> R.string.nag_jobs_paused
        TranscodeJobState.DONE -> R.string.nag_jobs_done
        TranscodeJobState.CANCELLED -> R.string.nag_jobs_cancelled
        else -> R.string.nag_jobs_failed
    },
)

fun jobStateTone(job: TranscodeJobView): StateTone = when (job.state) {
    TranscodeJobState.DONE -> StateTone.Success
    TranscodeJobState.FAILED -> StateTone.Error
    TranscodeJobState.PAUSED -> StateTone.Warning
    TranscodeJobState.RUNNING -> StateTone.Info
    else -> StateTone.Neutral
}

/** Why a job failed or stopped, in words. */
fun jobErrorText(res: Resources, job: TranscodeJobView): String {
    val error = job.error.orEmpty()
    return when (error) {
        TranscodeJobError.ENGINE + TranscodeRunner.ENCODER -> res.getString(R.string.nag_conv_e_encoder)
        TranscodeJobError.ENGINE + TranscodeRunner.EMPTY -> res.getString(R.string.nag_conv_e_empty)
        TranscodeJobError.ENGINE + LibVlcSoutEngine.ENCODE -> res.getString(R.string.nag_conv_e_encode)
        TranscodeJobError.ENGINE + LibVlcSoutEngine.OPEN_INPUT, TranscodeJobError.OPEN_INPUT -> res.getString(R.string.nag_jobs_e_open_input)
        TranscodeJobError.OPEN_OUTPUT -> res.getString(R.string.nag_jobs_e_open_output)
        TranscodeJobError.CANCELLED -> res.getString(R.string.nag_jobs_e_cancelled)
        TranscodeJobError.BATTERY -> res.getString(R.string.nag_jobs_e_battery)
        TranscodeJobError.THERMAL -> res.getString(R.string.nag_jobs_e_thermal)
        TranscodeJobError.SAME_FILE -> res.getString(R.string.nag_jobs_same_file)
        TranscodeJobError.OUTPUT_NOT_EMPTY -> res.getString(R.string.nag_jobs_not_empty)
        else -> if (error.startsWith(TranscodeJobError.ENGINE)) {
            res.getString(R.string.nag_jobs_e_engine, error.removePrefix(TranscodeJobError.ENGINE))
        } else {
            res.getString(R.string.nag_jobs_failed)
        }
    }
}

/** "%45" / "45%", in the interface's language. */
internal fun percentText(fraction: Float, locale: Locale): String = NumberFormat.getPercentInstance(locale).format(fraction.coerceIn(0f, 1f).toDouble())

/** "1:02:03", "4:05": a media time or how long something took. */
internal fun clockText(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds) else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

/** The resources, read again when the configuration (the language) changes. */
@Composable
internal fun resources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}

/** A card of the page: the surface, its border and padding. */
@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusCard)
    Column(
        modifier
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** A card's heading: "İşler", "Profiller". */
@Composable
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = NagTheme.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold),
        color = NagTheme.colors.text,
        maxLines = 1,
        modifier = modifier.semantics { heading() },
    )
}
