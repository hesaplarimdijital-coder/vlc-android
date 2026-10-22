package app.nagmedar.ui.downloads

import android.content.Context
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.nagmedar.data.DownloadState
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.translate.TranslationModel
import app.nagmedar.translate.TranslationModels
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.SectionCaption
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.player.subtitles.translate.TranslationJobs
import app.nagmedar.ui.player.subtitles.translate.directionLabel
import app.nagmedar.ui.player.subtitles.translate.problem
import app.nagmedar.ui.player.subtitles.translate.problemText
import app.nagmedar.ui.player.subtitles.whisper.WhisperJobs
import app.nagmedar.ui.player.subtitles.whisper.WhisperModel
import app.nagmedar.ui.player.subtitles.whisper.WhisperModels
import app.nagmedar.ui.player.subtitles.whisper.WhisperStage
import app.nagmedar.ui.player.subtitles.whisper.WhisperStatus
import app.nagmedar.ui.player.subtitles.whisper.rememberWhisperStatus
import app.nagmedar.ui.podcast.DownloadProgress
import app.nagmedar.ui.podcast.PodcastCenter
import app.nagmedar.ui.podcast.ServiceEpisodePlayer
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/** A translation model's download as WorkManager tells it, and what the model has on the device. */
@Immutable
data class ModelDownload(val model: TranslationModel, val work: WorkInfo?, val ready: Boolean, val bytesOnDisk: Long)

/** A whisper model on the device, whole or half way. */
@Immutable
data class WhisperFile(val model: WhisperModel, val ready: Boolean, val bytesOnDisk: Long)

/** A removal asked for and waiting for its confirmation. */
private sealed interface Removal {
    val name: String

    data class Episode(val episode: PodcastEpisode) : Removal {
        override val name: String get() = episode.title
    }

    data class Whisper(val file: WhisperFile, override val name: String) : Removal

    data class Translation(val download: ModelDownload, override val name: String) : Removal
}

/**
 * Tools › Downloads: what the app downloads, in one place. The podcast episodes being downloaded
 * (with their progress), waiting, done and failed; the whisper models of the subtitles from speech
 * and the translation models, whole, half way or coming down. A download under way is cancelled,
 * a failed one tried again, a finished episode played; a downloaded file is deleted only after
 * its confirmation (an episode stays in its show's list). Nothing here downloads by itself.
 */
@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val dao = remember(app) { runCatching { NagmedarDatabase.get(app).podcasts() }.getOrNull() }
    val episodes by remember(dao) { dao?.observeDownloads() ?: flowOf(emptyList()) }.collectAsState(initial = null)
    val feeds by remember(dao) { dao?.feeds() ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val progress by PodcastCenter.tracker.progress.collectAsState()
    val whisperJob by rememberWhisperStatus()
    val translationWork by remember(app) { translationWork(app) }.collectAsState(initial = emptyMap())
    var refresh by remember { mutableIntStateOf(0) }
    // the models' files, read again when a job starts or ends and after a removal
    val running = whisperJob is WhisperStatus.Running
    val whisperFiles by produceState(emptyList<WhisperFile>(), refresh, running) {
        value = withContext(Dispatchers.IO) {
            WhisperModel.entries.map { WhisperFile(it, WhisperModels.ready(app, it), WhisperModels.bytesOnDisk(app, it)) }.filter { it.bytesOnDisk > 0 }
        }
    }
    val models by produceState(emptyList<ModelDownload>(), refresh, translationWork) {
        value = withContext(Dispatchers.IO) {
            val store = TranslationJobs.store(app)
            TranslationModels.all.map { ModelDownload(it, translationWork[it.id], store.isReady(it), store.used(it)) }
                .filter { it.bytesOnDisk > 0 || (it.work != null && it.work.state != WorkInfo.State.CANCELLED && it.work.state != WorkInfo.State.SUCCEEDED) }
        }
    }
    var removing by remember { mutableStateOf<Removal?>(null) }
    val manager = remember(app) { runCatching { PodcastCenter.downloads(app) }.getOrNull() }
    val player = remember(context) { ServiceEpisodePlayer(context) }
    val byFeed = remember(feeds) { feeds.associateBy { it.id } }
    val downloading = whisperJob.takeIf { (it as? WhisperStatus.Running)?.stage == WhisperStage.Downloading } as? WhisperStatus.Running
    val list = episodes
    Column(Modifier.fillMaxSize()) {
        val total = (list?.size ?: 0) + whisperFiles.size + models.size + (if (downloading != null && whisperFiles.none { it.model == downloading.request?.model }) 1 else 0)
        PageHeader(title = stringResource(R.string.nag_nav_downloads), subtitle = list?.let { pluralStringResource(R.plurals.nag_downloads_count, total, total) })
        HorizontalRule()
        if (list == null) return@Column
        if (total == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StatePanel(
                    tone = StateTone.Neutral,
                    icon = NagIcons.Downloads,
                    title = stringResource(R.string.nag_downloads_empty_title),
                    description = stringResource(R.string.nag_downloads_empty_desc),
                    modifier = Modifier.testTag("downloads-empty"),
                )
            }
            return@Column
        }
        val dimens = NagTheme.dimens
        LazyColumn(Modifier.fillMaxSize().testTag("downloads-list"), contentPadding = PaddingValues(bottom = dimens.gapXl)) {
            if (list.isNotEmpty()) {
                item(key = "episodes") { Caption(stringResource(R.string.nag_downloads_episodes)) }
                items(list, key = { "episode-${it.id}" }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        feed = byFeed[episode.feedId],
                        progress = progress[episode.id],
                        onCancel = { scope.launch { manager?.cancel(episode.id) } },
                        onRetry = { scope.launch { manager?.queue(listOf(episode.id)) } },
                        onPlay = { byFeed[episode.feedId]?.let { player.play(it, episode) } },
                        onRemove = { removing = Removal.Episode(episode) },
                        onDismiss = { scope.launch { manager?.delete(episode.id) } },
                    )
                }
            }
            if (downloading != null || whisperFiles.isNotEmpty() || models.isNotEmpty()) {
                item(key = "models") { Caption(stringResource(R.string.nag_downloads_models)) }
            }
            if (downloading != null && whisperFiles.none { it.model == downloading.request?.model }) {
                item(key = "whisper-coming") {
                    val model = downloading.request?.model ?: WhisperModel.Default
                    WhisperRow(WhisperFile(model, ready = false, bytesOnDisk = downloading.doneBytes), downloading, onCancel = { WhisperJobs.cancel(app) }, onRemove = {})
                }
            }
            items(whisperFiles, key = { "whisper-${it.model.key}" }) { file ->
                val job = downloading?.takeIf { it.request?.model == file.model }
                val name = stringResource(file.model.label)
                WhisperRow(file, job, onCancel = { WhisperJobs.cancel(app) }, onRemove = { removing = Removal.Whisper(file, name) }, removable = !running)
            }
            items(models, key = { "translation-${it.model.id}" }) { download ->
                val name = directionLabel(context, download.model.direction)
                TranslationRow(
                    download = download,
                    onCancel = { TranslationJobs.cancelDownload(app, download.model) },
                    onRetry = { TranslationJobs.download(app, download.model) },
                    onRemove = { removing = Removal.Translation(download, name) },
                )
            }
        }
    }
    removing?.let { removal ->
        RemoveDialog(
            removal = removal,
            onConfirm = {
                scope.launch {
                    when (removal) {
                        is Removal.Episode -> manager?.delete(removal.episode.id)
                        is Removal.Whisper -> withContext(Dispatchers.IO) { WhisperModels.delete(app, removal.file.model) }
                        is Removal.Translation -> withContext(Dispatchers.IO) { TranslationJobs.store(app).delete(removal.download.model) }
                    }
                    refresh++
                }
            },
            onDismiss = { removing = null },
        )
    }
}

/** Each translation model's download work, by model id: the latest WorkManager knows of. */
private fun translationWork(context: Context): Flow<Map<String, WorkInfo?>> {
    val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return emptyFlow()
    val flows = TranslationModels.all.map { model ->
        work.getWorkInfosForUniqueWorkFlow(TranslationJobs.modelWork(model))
    }
    return combine(flows) { infos -> TranslationModels.all.mapIndexed { index, model -> model.id to infos[index].lastOrNull() }.toMap() }
}

@Composable
private fun Caption(text: String) {
    val dimens = NagTheme.dimens
    SectionCaption(text, Modifier.fillMaxWidth().padding(start = dimens.pagePadding, end = dimens.pagePadding, top = dimens.gapL, bottom = dimens.gapXs).semantics { heading() })
}

/** "%42", "42%": a fraction as the interface's language writes it. */
private fun percent(fraction: Float, locale: Locale): String = NumberFormat.getPercentInstance(locale).format(fraction.coerceIn(0f, 1f).toDouble())

/** "12,3 MB / 30,1 MB · %41", or the bytes alone when the whole is not known. */
@Composable
private fun progressLine(done: Long, total: Long): String {
    val locale = currentLocale()
    return if (total > 0) {
        stringResource(R.string.nag_downloads_progress, sizeLabel(done, locale), sizeLabel(total, locale), percent(done.toFloat() / total, locale))
    } else {
        sizeLabel(done, locale)
    }
}

/** One download: its icon, its name and what it belongs to, how it is, and what can be done with it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadRow(
    icon: ImageVector,
    title: String,
    detail: String?,
    status: String,
    progress: Float?,
    showProgress: Boolean,
    failed: Boolean = false,
    actions: @Composable () -> Unit,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(dimens.icon))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).heightIn(min = dimens.minTarget)) {
                Text(title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!detail.isNullOrEmpty()) Text(detail, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, style = NagTheme.type.labelSmall, color = if (failed) colors.danger else colors.textSecondary, maxLines = 2)
            }
        }
        if (showProgress) {
            Spacer(Modifier.height(6.dp))
            NagProgressBar(progress, label = status, modifier = Modifier.padding(start = dimens.icon + 10.dp).widthIn(max = 560.dp))
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(Modifier.padding(start = dimens.icon + 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions()
        }
    }
    HorizontalRule(Modifier.padding(horizontal = dimens.pagePadding))
}

@Composable
private fun EpisodeRow(
    episode: PodcastEpisode,
    feed: PodcastFeed?,
    progress: DownloadProgress?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale()
    val state = episode.downloadState
    val status = when {
        state == DownloadState.RUNNING && progress != null -> stringResource(R.string.nag_downloads_running, progressLine(progress.done, progress.total))
        state == DownloadState.RUNNING || state == DownloadState.QUEUED -> stringResource(R.string.nag_downloads_queued)
        state == DownloadState.DONE -> stringResource(R.string.nag_downloads_done, sizeLabel(episode.downloadBytes, locale))
        else -> stringResource(R.string.nag_downloads_failed)
    }
    DownloadRow(
        icon = NagIcons.Podcasts,
        title = episode.title,
        detail = feed?.title,
        status = status,
        progress = progress?.fraction,
        showProgress = state == DownloadState.RUNNING || state == DownloadState.QUEUED,
        failed = state == DownloadState.FAILED,
    ) {
        when (state) {
            DownloadState.RUNNING, DownloadState.QUEUED -> NagButton(stringResource(R.string.nag_sc_cancel), onCancel, icon = NagIcons.Close)
            DownloadState.DONE -> {
                NagButton(stringResource(R.string.nag_play), onPlay, icon = NagIcons.Play, primary = true, enabled = feed != null)
                NagButton(stringResource(R.string.nag_downloads_delete), onRemove, icon = NagIcons.Delete)
            }
            else -> {
                NagButton(stringResource(R.string.nag_downloads_retry), onRetry, icon = NagIcons.Repeat, primary = true)
                NagButton(stringResource(R.string.nag_downloads_dismiss), onDismiss)
            }
        }
    }
}

@Composable
private fun WhisperRow(file: WhisperFile, job: WhisperStatus.Running?, onCancel: () -> Unit, onRemove: () -> Unit, removable: Boolean = true) {
    val locale = currentLocale()
    val total = job?.totalBytes?.takeIf { it > 0 } ?: file.model.expectedBytes
    val status = when {
        job != null -> stringResource(R.string.nag_downloads_running, progressLine(job.doneBytes, job.totalBytes))
        file.ready -> stringResource(R.string.nag_downloads_done, sizeLabel(file.bytesOnDisk, locale))
        else -> stringResource(R.string.nag_downloads_partial, progressLine(file.bytesOnDisk, total))
    }
    DownloadRow(
        icon = NagIcons.Subtitle,
        title = stringResource(file.model.label),
        detail = stringResource(R.string.nag_downloads_whisper_model),
        status = status,
        progress = job?.fraction,
        showProgress = job != null,
    ) {
        if (job != null) {
            NagButton(stringResource(R.string.nag_downloads_stop_job), onCancel, icon = NagIcons.Close)
        } else {
            NagButton(stringResource(R.string.nag_downloads_delete), onRemove, icon = NagIcons.Delete, enabled = removable)
        }
    }
}

@Composable
private fun TranslationRow(download: ModelDownload, onCancel: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit) {
    val locale = currentLocale()
    val model = download.model
    val work = download.work
    val active = work != null && !work.state.isFinished
    val done = work?.progress?.getLong(TranslationJobs.KEY_DONE, -1L)?.takeIf { it >= 0 } ?: download.bytesOnDisk
    val failed = work?.state == WorkInfo.State.FAILED && !download.ready
    val status = when {
        work?.state == WorkInfo.State.RUNNING -> stringResource(R.string.nag_downloads_running, progressLine(done, model.size))
        active -> stringResource(R.string.nag_downloads_waiting_network)
        download.ready -> stringResource(R.string.nag_downloads_done, sizeLabel(download.bytesOnDisk, locale))
        failed -> stringResource(problemText(work!!.outputData.problem()))
        else -> stringResource(R.string.nag_downloads_partial, progressLine(download.bytesOnDisk, model.size))
    }
    DownloadRow(
        icon = NagIcons.Subtitle,
        title = directionLabel(LocalContext.current, model.direction),
        detail = stringResource(R.string.nag_downloads_translation_model, model.name),
        status = status,
        progress = if (model.size > 0) (done.toFloat() / model.size).coerceIn(0f, 1f) else null,
        showProgress = active,
        failed = failed,
    ) {
        when {
            active -> NagButton(stringResource(R.string.nag_sc_cancel), onCancel, icon = NagIcons.Close)
            download.ready -> NagButton(stringResource(R.string.nag_downloads_delete), onRemove, icon = NagIcons.Delete)
            else -> {
                NagButton(stringResource(if (failed) R.string.nag_downloads_retry else R.string.nag_downloads_resume), onRetry, icon = NagIcons.Repeat, primary = true)
                NagButton(stringResource(R.string.nag_downloads_delete), onRemove, icon = NagIcons.Delete)
            }
        }
    }
}

/** "Delete the downloaded file?": the file goes, an episode stays in its show's list. */
@Composable
private fun RemoveDialog(removal: Removal, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.nag_downloads_delete_title), Modifier.testTag("downloads-delete")) {
                Text(removal.name, style = NagTheme.type.bodyStrong, color = NagTheme.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (removal is Removal.Episode) R.string.nag_downloads_delete_episode else R.string.nag_downloads_delete_model),
                    style = NagTheme.type.body,
                    color = NagTheme.colors.textSecondary,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
                    NagButton(
                        stringResource(R.string.nag_downloads_delete),
                        {
                            onConfirm()
                            onDismiss()
                        },
                        primary = true,
                    )
                }
            }
        }
    }
}
