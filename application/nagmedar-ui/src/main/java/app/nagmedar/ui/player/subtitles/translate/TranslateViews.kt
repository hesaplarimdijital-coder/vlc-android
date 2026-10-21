package app.nagmedar.ui.player.subtitles.translate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.translate.TranslationDirection
import app.nagmedar.translate.TranslationModel
import app.nagmedar.translate.TranslationModels
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.panel.PanelRule
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/**
 * The tools' "Translate" tab (S-710): the direction (guessed from the file), the model it needs
 * (downloaded on the user's word, its size said first), Translate with its progress and Cancel,
 * and what came of it: the new file's name and place, whether it shows in the film with the
 * original, and swapping the two. [hasDocument]: the tools have a file read; [playing]: a film.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TranslateTab(controller: SubtitleTranslateController, hasDocument: Boolean, playing: Boolean, onTranslate: () -> Unit) {
    val state = controller.state
    val colors = NagTheme.colors
    val locale = currentLocale()
    val model = state.model
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("subtools-translate"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCaption(stringResource(R.string.nag_translate_hint))
        PanelCaption(stringResource(R.string.nag_translate_files_only))
        Text(stringResource(R.string.nag_translate_direction), style = NagTheme.type.label, color = colors.textSecondary)
        SegmentedChoice(
            options = listOf(
                TranslationDirection.EnglishToTurkish to stringResource(R.string.nag_translate_en_tr),
                TranslationDirection.TurkishToEnglish to stringResource(R.string.nag_translate_tr_en),
            ),
            selected = state.direction,
            onSelect = controller::setDirection,
            enabled = !state.running,
            modifier = Modifier.testTag("translate-direction"),
        )
        PanelRule()
        Text(stringResource(R.string.nag_translate_model, model.name, model.license, sizeLabel(model.size, locale)), style = NagTheme.type.label, color = colors.text, modifier = Modifier.testTag("translate-model"))
        if (!state.engine) {
            Text(stringResource(R.string.nag_translate_no_engine), style = NagTheme.type.label, color = colors.warning, modifier = Modifier.testTag("translate-no-engine"))
        } else {
            ModelLine(state.modelStatus, model, locale, onDownload = controller::download, onCancel = controller::cancelDownload)
        }
        PanelRule()
        val ready = state.engine && state.modelStatus == ModelStatus.Ready
        if (state.outputName.isNotEmpty()) PanelCaption(stringResource(R.string.nag_translate_output, state.outputName), Modifier.testTag("translate-output"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NagButton(
                stringResource(R.string.nag_translate_start),
                onTranslate,
                primary = true,
                enabled = ready && hasDocument && !state.running,
                icon = NagIcons.Subtitle,
                modifier = Modifier.testTag("translate-start"),
            )
            if (state.running) NagButton(stringResource(R.string.nag_translate_cancel), controller::cancel, modifier = Modifier.testTag("translate-cancel"))
        }
        RunLine(state, locale)
        val done = state.run as? TranslateRun.Done
        if (done != null && done.shown == true && playing) {
            Row { NagButton(stringResource(R.string.nag_translate_swap), controller::swap, modifier = Modifier.testTag("translate-swap")) }
        }
        Spacer(Modifier.height(4.dp))
        PanelCaption(stringResource(R.string.nag_translate_credit, model.license))
    }
}

/** The model's state: download it (its size), coming (how far, Stop), here, or why it did not come. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelLine(status: ModelStatus, model: TranslationModel, locale: Locale, onDownload: () -> Unit, onCancel: () -> Unit) {
    val colors = NagTheme.colors
    val size = sizeLabel(model.size, locale)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().testTag("translate-model-state")) {
        when (status) {
            ModelStatus.Ready -> Text(stringResource(R.string.nag_translate_model_ready), style = NagTheme.type.label, color = colors.success)
            is ModelStatus.Downloading -> {
                val text = if (status.waiting) R.string.nag_translate_waiting else R.string.nag_translate_downloading
                Text(stringResource(text, sizeLabel(status.done, locale), sizeLabel(status.total, locale)), style = NagTheme.type.label, color = colors.text)
                NagProgressBar(
                    progress = if (status.total > 0) (status.done.toFloat() / status.total).coerceIn(0f, 1f) else null,
                    label = stringResource(R.string.nag_translate_download_label),
                )
                Row { NagButton(stringResource(R.string.nag_translate_cancel_download), onCancel, modifier = Modifier.testTag("translate-download-cancel")) }
            }
            ModelStatus.Missing, is ModelStatus.Failed -> {
                if (status is ModelStatus.Failed) {
                    Text(stringResource(problemText(status.problem)), style = NagTheme.type.label, color = colors.danger, modifier = Modifier.testTag("translate-model-problem"))
                }
                PanelCaption(stringResource(R.string.nag_translate_model_missing, size))
                Row {
                    NagButton(stringResource(R.string.nag_translate_download, size), onDownload, icon = NagIcons.Downloads, modifier = Modifier.testTag("translate-download"))
                }
            }
        }
    }
}

/** How far the translation is, or what came of it. */
@Composable
private fun RunLine(state: TranslateState, locale: Locale) {
    val colors = NagTheme.colors
    val number = NumberFormat.getIntegerInstance(locale)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().testTag("translate-status")) {
        when (val run = state.run) {
            TranslateRun.Idle -> Unit
            TranslateRun.Starting -> {
                PanelCaption(stringResource(R.string.nag_translate_starting))
                NagProgressBar(null, stringResource(R.string.nag_translate_progress_label))
            }
            is TranslateRun.Running -> {
                Text(stringResource(R.string.nag_translate_progress, number.format(run.done), number.format(run.total)), style = NagTheme.type.label, color = colors.text)
                NagProgressBar(if (run.total > 0) run.done.toFloat() / run.total else null, stringResource(R.string.nag_translate_progress_label))
            }
            is TranslateRun.Done -> {
                val where = stringResource(if (run.saved.beside) R.string.nag_translate_done_beside else R.string.nag_translate_done_app, run.saved.name)
                Text(where, style = NagTheme.type.label, color = colors.success)
                when (run.shown) {
                    true -> PanelCaption(stringResource(R.string.nag_translate_shown))
                    false -> Text(stringResource(R.string.nag_translate_not_shown), style = NagTheme.type.labelSmall, color = colors.warning)
                    null -> Unit
                }
            }
            is TranslateRun.Problem -> Text(stringResource(problemText(run.problem)), style = NagTheme.type.label, color = colors.danger)
            TranslateRun.Cancelled -> Text(stringResource(R.string.nag_translate_cancelled), style = NagTheme.type.label, color = colors.textSecondary)
            TranslateRun.Busy -> Text(stringResource(R.string.nag_translate_busy), style = NagTheme.type.label, color = colors.warning)
        }
    }
}

/** The text of [problem], for the window and the notification. */
internal fun problemText(problem: JobProblem): Int = when (problem) {
    JobProblem.NoNetwork -> R.string.nag_translate_no_network
    JobProblem.Corrupt -> R.string.nag_translate_corrupt
    JobProblem.NoSpace -> R.string.nag_translate_no_space
    JobProblem.NothingToTranslate -> R.string.nag_translate_nothing
    JobProblem.Unsupported -> R.string.nag_translate_unsupported
    JobProblem.NoModel -> R.string.nag_translate_no_model
    JobProblem.NoEngine -> R.string.nag_translate_no_engine
    JobProblem.WriteFailed -> R.string.nag_translate_write_failed
    JobProblem.Failed -> R.string.nag_translate_failed
}

/** "English → Turkish" in the interface's language. */
@Composable
internal fun directionText(direction: TranslationDirection): String = stringResource(
    when (direction) {
        TranslationDirection.TurkishToEnglish -> R.string.nag_translate_tr_en
        TranslationDirection.EnglishToTurkish -> R.string.nag_translate_en_tr
    },
)

/**
 * Settings › Subtitles (S-710): the translation's models, each with its size and whether it is
 * here; one here can be deleted, one not here downloaded. Shown inside the subtitles' section.
 */
@Composable
fun ColumnScope.TranslationModelRows() {
    val context = LocalContext.current
    val service = remember(context) { WorkTranslationService(context) }
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    var refresh by remember { mutableIntStateOf(0) }
    var offline by remember { mutableStateOf(false) }
    PanelCaption(stringResource(R.string.nag_translate_settings_hint))
    for (model in TranslationModels.all) {
        val flow = remember(service, model, refresh) { service.model(model) }
        val status by flow.collectAsState(initial = ModelStatus.Missing)
        // what a stopped download left: it can be deleted too
        val used by produceState(0L, status, refresh) { value = withContext(Dispatchers.IO) { TranslationJobs.store(context).used(model) } }
        val size = sizeLabel(model.size, locale)
        val subtitle = model.name + " · " + model.license + " · " + when (status) {
            ModelStatus.Ready -> stringResource(R.string.nag_translate_status_ready, size)
            is ModelStatus.Downloading -> (status as ModelStatus.Downloading).let { stringResource(R.string.nag_translate_downloading, sizeLabel(it.done, locale), sizeLabel(it.total, locale)) }
            else -> stringResource(R.string.nag_translate_status_missing, size)
        }
        SettingRow(directionText(model.direction), modifier = Modifier.testTag("translate-model-${model.id}"), subtitle = subtitle) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (status) {
                    ModelStatus.Ready -> NagButton(
                        stringResource(R.string.nag_translate_delete),
                        {
                            scope.launch {
                                service.delete(model)
                                refresh++
                            }
                        },
                        icon = NagIcons.Delete,
                        modifier = Modifier.testTag("translate-delete-${model.id}"),
                    )
                    is ModelStatus.Downloading -> NagButton(stringResource(R.string.nag_translate_cancel), { service.cancelDownload(model) }, modifier = Modifier.testTag("translate-stop-${model.id}"))
                    else -> {
                        if (used > 0) {
                            NagButton(
                                stringResource(R.string.nag_translate_delete),
                                {
                                    scope.launch {
                                        service.delete(model)
                                        refresh++
                                    }
                                },
                                icon = NagIcons.Delete,
                                modifier = Modifier.testTag("translate-delete-${model.id}"),
                            )
                        }
                        NagButton(
                            stringResource(R.string.nag_translate_settings_download),
                            {
                                offline = !service.online()
                                if (!offline) service.download(model)
                            },
                            enabled = service.engineAvailable,
                            icon = NagIcons.Downloads,
                            modifier = Modifier.testTag("translate-get-${model.id}"),
                        )
                    }
                }
            }
        }
    }
    if (offline) Text(stringResource(R.string.nag_translate_no_network), style = NagTheme.type.labelSmall, color = NagTheme.colors.danger, modifier = Modifier.testTag("translate-offline"))
    if (!service.engineAvailable) Text(stringResource(R.string.nag_translate_no_engine), style = NagTheme.type.labelSmall, color = NagTheme.colors.warning)
}
