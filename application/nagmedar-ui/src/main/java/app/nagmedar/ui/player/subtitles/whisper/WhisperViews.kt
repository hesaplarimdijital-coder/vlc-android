package app.nagmedar.ui.player.subtitles.whisper

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.player.panel.ChoiceRow
import app.nagmedar.ui.player.panel.DropButton
import app.nagmedar.ui.player.panel.LabelledRow
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.subtitles.SavedSubtitle
import app.nagmedar.ui.shell.KeyCap
import app.nagmedar.ui.theme.NagTheme

/**
 * S-709: "Generate subtitles…" in its own window. While a job is under way (one at a time) it
 * shows that job: its film, how far it is, the time left, Stop. Otherwise the choices: the audio
 * track, the language, whether into English, the model with its size, and what the download of a
 * model not yet on the device takes, which the button says too; the outcome of the last job for
 * this film above them. Esc closes; Tab goes through the choices; the job goes on when it closes.
 * [onLoad] (the player's) shows a finished file at once.
 */
@Composable
fun WhisperDialog(
    controller: WhisperController,
    state: WhisperDialogState,
    status: WhisperStatus?,
    onLoad: ((SavedSubtitle) -> Unit)?,
    onDismiss: () -> Unit,
    beforePicker: () -> Unit = {},
) {
    LaunchedEffect(status) { if (status != null) controller.settled() }
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(
                stringResource(R.string.nag_whisper_title),
                Modifier
                    .testTag("whisper-dialog")
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    },
                cardWidth = 460.dp,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (status is WhisperStatus.Running) {
                        RunningJob(status, onStop = controller::stop, onClose = onDismiss)
                    } else {
                        if (status != null && status.request?.film == state.film) {
                            Outcome(status, onLoad, onDismiss = { controller.dismiss(status.id) }, beforePicker = beforePicker)
                        }
                        Choices(controller, state, onDismiss)
                    }
                }
            }
        }
    }
}

/** The dialog's choices and its buttons. */
@Composable
private fun Choices(controller: WhisperController, state: WhisperDialogState, onDismiss: () -> Unit) {
    val colors = NagTheme.colors
    Text(state.name, style = NagTheme.type.label, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    val tracks = state.tracks
    if (tracks != null && tracks.size > 1) {
        val label = stringResource(R.string.nag_whisper_audio)
        var open by remember { mutableStateOf(false) }
        LabelledRow(label) {
            Box(Modifier.testTag("whisper-audio")) {
                DropButton(tracks.getOrNull(state.track) ?: tracks.first(), true, label) { open = true }
                MenuPopup(
                    visible = open,
                    onDismiss = { open = false },
                    entries = tracks.mapIndexed { index, name -> MenuEntry.Item(name, { controller.setTrack(index) }, checked = index == state.track) },
                    offset = IntOffset(0, 0),
                    minWidth = 260.dp,
                )
            }
        }
    }
    LabelledRow(stringResource(R.string.nag_whisper_language)) {
        ChoiceRow(
            options = WhisperLanguage.entries.map { it to stringResource(it.label) },
            selected = state.language,
            enabled = true,
            onSelect = controller::setLanguage,
            mono = false,
            modifier = Modifier.testTag("whisper-language"),
        )
    }
    NagCheckbox(state.translate, controller::setTranslate, stringResource(R.string.nag_whisper_translate), Modifier.testTag("whisper-translate"))
    LabelledRow(stringResource(R.string.nag_whisper_model)) {
        ChoiceRow(
            options = WhisperModel.entries.map { it to stringResource(R.string.nag_whisper_model_choice, stringResource(it.label), it.sizeMb) },
            selected = state.model,
            enabled = true,
            onSelect = controller::setModel,
            mono = false,
            modifier = Modifier.testTag("whisper-model"),
        )
    }
    PanelCaption(stringResource(state.model.hint))
    if (state.needsDownload) {
        PanelCaption(stringResource(R.string.nag_whisper_download_note, state.model.sizeMb), Modifier.testTag("whisper-download-note"))
    }
    PanelCaption(stringResource(R.string.nag_whisper_job_note))
    if (!state.native) {
        Text(stringResource(R.string.nag_whisper_fail_native), style = NagTheme.type.labelSmall, color = colors.warning, modifier = Modifier.testTag("whisper-no-native"))
    }
    val start = remember { FocusRequester() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        NagButton(stringResource(R.string.nag_whisper_back_out), onDismiss)
        NagButton(
            if (state.needsDownload) stringResource(R.string.nag_whisper_download_start, state.model.sizeMb) else stringResource(R.string.nag_whisper_start),
            controller::start,
            modifier = Modifier.focusRequester(start).testTag("whisper-start"),
            icon = NagIcons.Subtitle,
            primary = true,
            enabled = state.native && !state.starting && state.tracks != null,
        )
    }
    val reading = state.tracks == null
    LaunchedEffect(reading) { if (!reading) runCatching { start.requestFocus() } }
}

/** The job under way: its film, the stage and how far, the time left, what it runs on and how fast; a word when it began again; Stop and Close. */
@Composable
private fun RunningJob(status: WhisperStatus.Running, onStop: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = NagTheme.colors
    val line = WhisperWorker.progressLine(context, status)
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("whisper-running"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(status.request?.name.orEmpty(), style = NagTheme.type.label, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        NagProgressBar(status.fraction, line)
        Text(line, style = NagTheme.type.labelSmall, color = colors.textSecondary)
        status.engine?.let { Text(WhisperWorker.engineLine(context, it), style = NagTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.testTag("whisper-engine")) }
        PanelCaption(stringResource(if (status.restarted) R.string.nag_whisper_restarted_note else R.string.nag_whisper_running_note))
    }
    val close = remember { FocusRequester() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        NagButton(stringResource(R.string.nag_whisper_stop), onStop, modifier = Modifier.testTag("whisper-stop"), icon = NagIcons.Stop)
        NagButton(stringResource(R.string.nag_whisper_close), onClose, modifier = Modifier.focusRequester(close), primary = true)
    }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
}

/** What came of the last job for this film: the file and where it is (Show, Save as…), or why there is none. */
@Composable
private fun Outcome(status: WhisperStatus, onLoad: ((SavedSubtitle) -> Unit)?, onDismiss: () -> Unit, beforePicker: () -> Unit) {
    val colors = NagTheme.colors
    var savedAs by remember { mutableStateOf<Boolean?>(null) }
    val saveAs = rememberWhisperSaveAs(beforePicker) { savedAs = it }
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("whisper-outcome"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutcomeLines(status, savedAs)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (status is WhisperStatus.Done) {
                if (!status.saved.beside) NagButton(stringResource(R.string.nag_whisper_save_as), { saveAs(status.saved, status.request?.film.orEmpty()) })
                if (onLoad != null) NagButton(stringResource(R.string.nag_whisper_load), { onLoad(status.saved) }, primary = true, modifier = Modifier.testTag("whisper-load"))
            }
            NagButton(stringResource(R.string.nag_whisper_dismiss), onDismiss)
        }
    }
}

/** "Subtitles ready · 1.204 lines", the file and where it is, what made it and how fast; or the failure. */
@Composable
private fun OutcomeLines(status: WhisperStatus, savedAs: Boolean?) {
    val context = LocalContext.current
    val colors = NagTheme.colors
    when (status) {
        is WhisperStatus.Done -> {
            Text(pluralStringResource(R.plurals.nag_whisper_ready, status.lines, status.lines), style = NagTheme.type.label, color = colors.text)
            val where = if (status.saved.beside) R.string.nag_whisper_saved_beside else R.string.nag_whisper_saved_app
            Text(stringResource(where, status.saved.name), style = NagTheme.type.labelSmall, color = colors.textSecondary)
            status.engine?.let { Text(WhisperWorker.engineLine(context, it), style = NagTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.testTag("whisper-outcome-engine")) }
            if (!status.saved.beside) PanelCaption(stringResource(R.string.nag_whisper_save_as_hint))
            when (savedAs) {
                true -> PanelCaption(stringResource(R.string.nag_whisper_saved_copy))
                false -> Text(stringResource(R.string.nag_whisper_save_failed), style = NagTheme.type.labelSmall, color = colors.danger)
                null -> Unit
            }
        }
        is WhisperStatus.Failed -> {
            Text(stringResource(R.string.nag_whisper_failed), style = NagTheme.type.label, color = colors.text)
            Text(stringResource(WhisperWorker.failureText(status.reason)), style = NagTheme.type.labelSmall, color = colors.danger)
        }
        is WhisperStatus.Cancelled -> Text(stringResource(R.string.nag_whisper_cancelled), style = NagTheme.type.label, color = colors.text)
        is WhisperStatus.Running -> Unit
    }
}

/**
 * A finished job's outcome in a corner of a window ([modifier] places it): over the video for its
 * film, with Show ([onLoad]); in the library's window for any film. Closed, it is not shown again.
 */
@Composable
fun WhisperOutcomeCorner(status: WhisperStatus?, onLoad: ((SavedSubtitle) -> Unit)?, modifier: Modifier = Modifier, beforePicker: () -> Unit = {}) {
    if (status == null || status is WhisperStatus.Running || status is WhisperStatus.Cancelled) return
    val context = LocalContext.current
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(10.dp)
    var savedAs by remember(status.id) { mutableStateOf<Boolean?>(null) }
    val saveAs = rememberWhisperSaveAs(beforePicker) { savedAs = it }
    val dismiss = { WhisperJobs.dismiss(context, status.id) }
    Column(
        modifier
            .widthIn(max = 380.dp)
            .shadow(12.dp, shape)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("whisper-corner"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(NagIcons.Subtitle, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.padding(top = 2.dp).size(16.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(status.request?.name.orEmpty(), style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OutcomeLines(status, savedAs)
            }
            NagIconButton(NagIcons.Close, stringResource(R.string.nag_whisper_dismiss), dismiss, size = 24.dp, iconSize = 14.dp)
        }
        if (status is WhisperStatus.Done) {
            Row(Modifier.fillMaxWidth().padding(end = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (!status.saved.beside) NagButton(stringResource(R.string.nag_whisper_save_as), { saveAs(status.saved, status.request?.film.orEmpty()) })
                if (onLoad != null) {
                    NagButton(stringResource(R.string.nag_whisper_load), {
                        onLoad(status.saved)
                        dismiss()
                    }, primary = true, modifier = Modifier.testTag("whisper-corner-load"))
                }
            }
        }
    }
}

/**
 * The subtitles' tab's row (S-709): "Generate subtitles…" with its keys, and under it the job
 * under way (how far, Stop) or what it does.
 */
@Composable
fun WhisperSection(status: WhisperStatus?, onOpen: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().testTag("subtitles-whisper"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NagButton(stringResource(R.string.nag_whisper_menu), onOpen, modifier = Modifier.fillMaxWidth().testTag("whisper-open"), icon = NagIcons.Subtitle)
        if (status is WhisperStatus.Running) {
            val line = WhisperWorker.progressLine(context, status)
            Text(status.request?.name.orEmpty(), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            NagProgressBar(status.fraction, line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelCaption(line, Modifier.weight(1f))
                NagButton(stringResource(R.string.nag_whisper_stop), onStop, modifier = Modifier.testTag("whisper-section-stop"))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShortcutRegistry.chordLabel(ShortcutId.GenerateSubtitles, context.resources)?.let { KeyCap(it) }
                PanelCaption(stringResource(R.string.nag_whisper_section_hint))
            }
        }
    }
}
