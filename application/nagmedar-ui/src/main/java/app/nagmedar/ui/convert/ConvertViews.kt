package app.nagmedar.ui.convert

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.audio.convert.AudioConverter
import app.nagmedar.audio.convert.ConvertCodec
import app.nagmedar.ui.R
import app.nagmedar.ui.onboarding.FolderGrants
import app.nagmedar.ui.audio.kiloHertz
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.SectionCaption
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.player.snapshot.snapshotFolderLabel
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.AppScope
import org.videolan.tools.Settings

// ------------------------------------------------------------------- the window

/**
 * S-1010: "Dönüştür…" for [tracks]: the window that asks for the format and quality, the rate,
 * the names and the destination folder (the system's picker, the permission kept), remembers
 * the choices and starts the job; [onDismiss] closes it, once started too.
 */
@Composable
fun ConvertDialog(tracks: List<Track>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { Settings.getInstance(context) }
    var options by remember { mutableStateOf(ConvertOptions.read(prefs)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) options = options.copy(folder = keepFolder(context, prefs, options.folder, uri))
    }
    val unknownArtist = stringResource(R.string.nag_unknown_artist)
    val unknownAlbum = stringResource(R.string.nag_music_unknown_album)
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            ConvertSurface(
                tracks = tracks,
                options = options,
                onOptions = { options = it },
                engineAvailable = AudioConverter.available,
                onPickFolder = { runCatching { picker.launch(options.folder.takeIf { it.isNotEmpty() }?.let(Uri::parse)) } },
                onStart = {
                    val chosen = options
                    chosen.write(prefs.edit()).apply()
                    val app = context.applicationContext
                    // the window closes at once: the job is written and queued in the app's scope
                    AppScope.launch(Dispatchers.IO) {
                        val items = ConvertPlanner.plan(tracks, chosen.naming, chosen.preset.codec.extension, unknownArtist, unknownAlbum)
                        ConvertQueue.start(app, chosen, items)
                    }
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * A folder the picker gave: its permission kept; the converter's earlier one let go as far as
 * nothing uses it any more (a job still writing there, the recordings, the library…: [FolderGrants]).
 */
private fun keepFolder(context: Context, prefs: SharedPreferences, before: String, uri: Uri): String {
    val resolver = context.contentResolver
    runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    val folder = uri.toString()
    prefs.edit().putString(ConvertOptions.KEY_FOLDER, folder).apply()
    if (before.isNotEmpty() && before != folder) FolderGrants.release(context, before)
    return folder
}

/** The window's content: what is converted, the choices, the folder, Vazgeç and Dönüştür. */
@Composable
fun ConvertSurface(
    tracks: List<Track>,
    options: ConvertOptions,
    onOptions: (ConvertOptions) -> Unit,
    engineAvailable: Boolean,
    onPickFolder: () -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NagTheme.colors
    val locale = currentLocale()
    DialogCard(stringResource(R.string.nag_convert_title), Modifier.testTag("convert-dialog"), cardWidth = 520.dp) {
        // the choices scroll on a short window; the title and the buttons stay
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (tracks.size == 1) tracks[0].title else pluralStringResource(R.plurals.nag_tracks_count, tracks.size, tracks.size),
                style = NagTheme.type.body,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // the format, then its qualities
            Labelled(stringResource(R.string.nag_convert_format)) {
                SegmentedChoice(
                    options = ConvertCodec.entries.map { it to codecName(it) },
                    selected = options.preset.codec,
                    onSelect = { codec -> if (codec != options.preset.codec) onOptions(options.copy(preset = firstOf(codec))) },
                    modifier = Modifier.testTag("convert-codec"),
                )
                Text(stringResource(codecHint(options.preset.codec)), style = NagTheme.type.labelSmall, color = colors.textSecondary)
            }
            Labelled(stringResource(R.string.nag_convert_quality)) {
                SegmentedChoice(
                    options = ConvertPreset.entries.filter { it.codec == options.preset.codec }.map { it to qualityLabel(it) },
                    selected = options.preset,
                    onSelect = { onOptions(options.copy(preset = it)) },
                    modifier = Modifier.testTag("convert-quality"),
                )
            }
            Labelled(stringResource(R.string.nag_convert_rate)) {
                SegmentedChoice(
                    options = ConvertRate.entries.map { rate ->
                        rate to if (rate == ConvertRate.Source) stringResource(R.string.nag_convert_rate_source) else kiloHertz(rate.hz, locale)
                    },
                    selected = if (options.rateApplies) options.rate else ConvertRate.Hz48000,
                    onSelect = { onOptions(options.copy(rate = it)) },
                    enabled = options.rateApplies,
                    modifier = Modifier.testTag("convert-rate"),
                )
                when (options.preset.codec) {
                    ConvertCodec.Opus -> Text(stringResource(R.string.nag_convert_rate_opus), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                    ConvertCodec.Mp3 -> Text(stringResource(R.string.nag_convert_rate_mp3), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                    ConvertCodec.Aac -> Unit
                }
            }
            Labelled(stringResource(R.string.nag_convert_channels)) {
                SegmentedChoice(
                    options = listOf(
                        ConvertChannels.Source to stringResource(R.string.nag_convert_channels_source),
                        ConvertChannels.Stereo to stringResource(R.string.nag_convert_channels_stereo),
                    ),
                    selected = if (options.channelsApply) options.channels else ConvertChannels.Stereo,
                    onSelect = { onOptions(options.copy(channels = it)) },
                    enabled = options.channelsApply,
                    modifier = Modifier.testTag("convert-channels"),
                )
                Text(
                    stringResource(if (options.channelsApply) R.string.nag_convert_channels_hint else R.string.nag_convert_channels_mp3),
                    style = NagTheme.type.labelSmall,
                    color = colors.textSecondary,
                )
            }
            Labelled(stringResource(R.string.nag_convert_naming)) {
                SegmentedChoice(
                    options = listOf(
                        ConvertNaming.Folders to stringResource(R.string.nag_convert_naming_folders),
                        ConvertNaming.Flat to stringResource(R.string.nag_convert_naming_flat),
                    ),
                    selected = options.naming,
                    onSelect = { onOptions(options.copy(naming = it)) },
                    modifier = Modifier.testTag("convert-naming"),
                )
                val unknownArtist = stringResource(R.string.nag_unknown_artist)
                val unknownAlbum = stringResource(R.string.nag_music_unknown_album)
                val example = remember(tracks, options.naming, options.preset) {
                    tracks.firstOrNull()?.let { ConvertPlanner.plan(listOf(it), options.naming, options.preset.codec.extension, unknownArtist, unknownAlbum).first().path }
                }
                if (example != null) {
                    Text(
                        stringResource(R.string.nag_convert_example, example),
                        style = NagTheme.type.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("convert-example"),
                    )
                }
                NagCheckbox(
                    checked = options.skipExisting,
                    onCheckedChange = { onOptions(options.copy(skipExisting = it)) },
                    label = stringResource(R.string.nag_convert_skip_existing),
                    modifier = Modifier.testTag("convert-skip"),
                )
            }
            Labelled(stringResource(R.string.nag_convert_folder)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val none = stringResource(R.string.nag_convert_folder_none)
                    Text(
                        if (options.folder.isEmpty()) none else snapshotFolderLabel(options.folder, none),
                        style = NagTheme.type.body,
                        color = if (options.folder.isEmpty()) colors.textSecondary else colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).testTag("convert-folder"),
                    )
                    NagButton(
                        stringResource(if (options.folder.isEmpty()) R.string.nag_snap_choose_folder else R.string.nag_snap_change_folder),
                        onPickFolder,
                        icon = NagIcons.Folder,
                        modifier = Modifier.testTag("convert-pick-folder"),
                    )
                }
            }
            val lossy = remember(tracks) { tracks.count { it.tech?.lossless == false } }
            val notes = listOfNotNull(
                stringResource(R.string.nag_convert_originals),
                lossy.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.nag_convert_lossy, it, it) },
            )
            notes.forEach { note -> Text(note, style = NagTheme.type.labelSmall, color = colors.textSecondary) }
            if (!engineAvailable) {
                Text(
                    stringResource(R.string.nag_convert_no_engine),
                    style = NagTheme.type.labelSmall,
                    color = colors.danger,
                    modifier = Modifier.testTag("convert-no-engine"),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
            NagButton(
                stringResource(R.string.nag_convert_start),
                onStart,
                icon = NagIcons.Convert,
                primary = true,
                enabled = engineAvailable && options.folder.isNotEmpty() && tracks.isNotEmpty(),
                modifier = Modifier.testTag("convert-start"),
            )
        }
    }
}

/** A choice's caption above it. */
@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionCaption(label)
        content()
    }
}

/** The first quality of [codec]: the one the window starts at when the format changes. */
private fun firstOf(codec: ConvertCodec): ConvertPreset =
    if (codec == ConvertPreset.DEFAULT.codec) ConvertPreset.DEFAULT else ConvertPreset.entries.first { it.codec == codec }

/** The format's name: Opus, MP3, AAC (as they are written in every language). */
internal fun codecName(codec: ConvertCodec): String = when (codec) {
    ConvertCodec.Opus -> "Opus"
    ConvertCodec.Mp3 -> "MP3"
    ConvertCodec.Aac -> "AAC"
}

private fun codecHint(codec: ConvertCodec): Int = when (codec) {
    ConvertCodec.Opus -> R.string.nag_convert_hint_opus
    ConvertCodec.Mp3 -> R.string.nag_convert_hint_mp3
    ConvertCodec.Aac -> R.string.nag_convert_hint_aac
}

/** "160 kbps", "V0 (değişken)". */
@Composable
internal fun qualityLabel(preset: ConvertPreset): String =
    preset.kbps?.let { stringResource(R.string.nag_sp_kbps, it) } ?: stringResource(R.string.nag_convert_quality_v0)

// ------------------------------------------------------------- progress, summary

/**
 * S-1010: the converter's line above the music library: the job under way (the file, how far,
 * İptal; why it waits), or what the last one came to (Ayrıntılar, close). Nothing without a job.
 */
@Composable
fun ConvertStatusBar(status: ConvertStatus?, onCancel: (String) -> Unit, onDetails: (String) -> Unit, onDismiss: (String) -> Unit, modifier: Modifier = Modifier) {
    status ?: return
    val colors = NagTheme.colors
    val res = LocalContext.current.resources
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl + 2.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            // the end is read out; the progress is not, at every step
            .then(if (status is ConvertStatus.Finished) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
            .testTag("convert-status"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(NagIcons.Convert, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                when (status) {
                    is ConvertStatus.Running -> {
                        val p = status.progress
                        Text(stringResource(R.string.nag_convert_running_title), style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val line = when {
                            status.resumes -> stringResource(R.string.nag_convert_waiting_resume)
                            p == null -> stringResource(R.string.nag_convert_starting)
                            p.waiting == ConvertReason.Battery -> stringResource(R.string.nag_convert_waiting_battery)
                            p.waiting == ConvertReason.Thermal -> stringResource(R.string.nag_convert_waiting_heat)
                            else -> stringResource(R.string.nag_convert_progress, p.index + 1, p.total, p.title)
                        }
                        val queued = status.queued.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.nag_convert_queued, it, it) }
                        Text(
                            listOfNotNull(line, queued).joinToString(" · "),
                            style = NagTheme.type.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("convert-status-line"),
                        )
                    }
                    is ConvertStatus.Finished -> {
                        Text(
                            stringResource(if (status.cancelled) R.string.nag_convert_stopped_title else R.string.nag_convert_done_title),
                            style = NagTheme.type.bodyStrong,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            countsLine(res, status.converted, status.skipped, status.failed),
                            style = NagTheme.type.labelSmall,
                            color = if (status.failed > 0) colors.danger else colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("convert-status-line"),
                        )
                    }
                }
            }
            when (status) {
                is ConvertStatus.Running -> if (status.jobId.isNotEmpty()) {
                    NagButton(stringResource(R.string.nag_convert_cancel), { onCancel(status.jobId) }, modifier = Modifier.testTag("convert-cancel"))
                }
                is ConvertStatus.Finished -> {
                    NagButton(stringResource(R.string.nag_convert_details), { onDetails(status.jobId) }, modifier = Modifier.testTag("convert-details"))
                    NagIconButton(NagIcons.Close, stringResource(R.string.nag_sc_close), { onDismiss(status.jobId) }, modifier = Modifier.testTag("convert-dismiss"))
                }
            }
        }
        if (status is ConvertStatus.Running) {
            NagProgressBar(status.progress?.overall, stringResource(R.string.nag_convert_running_title))
        }
    }
}

/** "11 dönüştürüldü · 1 atlandı · 2 başarısız": what is not nought, the converted always. */
internal fun countsLine(res: Resources, converted: Int, skipped: Int, failed: Int): String = listOfNotNull(
    res.getQuantityString(R.plurals.nag_convert_done_converted, converted, converted),
    skipped.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.nag_convert_done_skipped, it, it) },
    failed.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.nag_convert_done_failed, it, it) },
).joinToString(" · ")

/** S-1010: the summary of job [jobId] in a window: the counts, then the files left alone and those not made, each with why. */
@Composable
fun ConvertSummaryDialog(jobId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // null while it is read; a job whose files are gone has nothing to show, and the window closes
    val read by produceState<Result<ConvertSummary?>?>(null, jobId) {
        value = Result.success(
            withContext(Dispatchers.IO) {
                val store = ConvertStore(context)
                store.job(jobId)?.let { job -> store.summary(job) ?: ConvertSummary(job.id, job.items.size, emptyList()) }
            },
        )
    }
    val summary = read?.getOrNull()
    LaunchedEffect(read) { if (read != null && summary == null) onDismiss() }
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) { ConvertSummarySurface(summary, onDismiss) }
    }
}

@Composable
fun ConvertSummarySurface(summary: ConvertSummary?, onDismiss: () -> Unit) {
    val colors = NagTheme.colors
    val res = LocalContext.current.resources
    DialogCard(stringResource(R.string.nag_convert_summary_title), Modifier.testTag("convert-summary"), cardWidth = 560.dp) {
        if (summary == null) {
            Text(stringResource(R.string.nag_convert_summary_loading), style = NagTheme.type.body, color = colors.textSecondary)
        } else {
            val failed = summary.failed
            val skipped = summary.outcomes.filterIsInstance<ItemOutcome.Skipped>()
            Text(
                countsLine(res, summary.converted, summary.skipped, failed.count { it.reason != ConvertReason.Cancelled }),
                style = NagTheme.type.body,
                color = colors.text,
                modifier = Modifier.testTag("convert-summary-counts"),
            )
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (failed.isNotEmpty()) {
                    HorizontalRule()
                    SectionCaption(stringResource(R.string.nag_convert_summary_failed))
                    failed.forEach { outcome -> OutcomeRow(outcome.item, reasonText(outcome.reason), outcome.detail, danger = outcome.reason != ConvertReason.Cancelled) }
                }
                if (skipped.isNotEmpty()) {
                    HorizontalRule()
                    SectionCaption(stringResource(R.string.nag_convert_summary_skipped))
                    skipped.forEach { outcome -> OutcomeRow(outcome.item, stringResource(R.string.nag_convert_reason_exists), "", danger = false) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            NagButton(stringResource(R.string.nag_sc_close), onDismiss, primary = true, modifier = Modifier.testTag("convert-summary-close"))
        }
    }
}

/** A file and why: its path under the destination, the reason, the engine's word when there is one. */
@Composable
private fun OutcomeRow(item: ConvertItem, reason: String, detail: String, danger: Boolean) {
    val colors = NagTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(item.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.path, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(reason, style = NagTheme.type.labelSmall, color = if (danger) colors.danger else colors.textSecondary)
        if (detail.isNotBlank()) Text(detail, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun reasonText(reason: ConvertReason): String = stringResource(
    when (reason) {
        ConvertReason.Exists -> R.string.nag_convert_reason_exists
        ConvertReason.NoEngine -> R.string.nag_convert_reason_no_engine
        ConvertReason.Input -> R.string.nag_convert_reason_input
        ConvertReason.Folder -> R.string.nag_convert_reason_folder
        ConvertReason.Output -> R.string.nag_convert_reason_output
        ConvertReason.Source -> R.string.nag_convert_reason_source
        ConvertReason.Engine -> R.string.nag_convert_reason_engine
        ConvertReason.Battery -> R.string.nag_convert_reason_battery
        ConvertReason.Thermal -> R.string.nag_convert_reason_thermal
        ConvertReason.Cancelled -> R.string.nag_convert_reason_cancelled
    },
)

/**
 * S-1010: the converter's line and its windows in the music library: the job's status from
 * WorkManager, İptal, the summary's window, and the window [converting] asks for.
 */
@Composable
fun ConvertHost(converting: List<Track>?, onConverted: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext
    val status by remember(context) { ConvertQueue.observe(context) }.collectAsState(null)
    var details by remember { mutableStateOf<String?>(null) }
    ConvertStatusBar(
        status,
        onCancel = { id -> AppScope.launch(Dispatchers.IO) { ConvertQueue.cancel(app, id) } },
        onDetails = { id -> details = id },
        onDismiss = { id -> AppScope.launch(Dispatchers.IO) { ConvertQueue.dismiss(app, id) } },
        modifier = modifier,
    )
    details?.let { id -> ConvertSummaryDialog(id) { details = null } }
    converting?.let { tracks -> ConvertDialog(tracks, onConverted) }
}
