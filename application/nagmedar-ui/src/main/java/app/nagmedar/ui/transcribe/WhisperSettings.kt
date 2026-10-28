package app.nagmedar.ui.transcribe

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.Settings

/**
 * Settings › Subtitles (S-709): the subtitles from speech: the model a new job starts with
 * (small q5_1 unless changed), the language and where it runs (the GPU where the device has one,
 * or the processor alone); each model with what it takes on the device, removed with Delete (not
 * while a job runs). Models are downloaded only when a job needs one and the user agreed to its
 * size. When the app stopped while the GPU worked, jobs run on the processor until "Try the GPU
 * again".
 */
@Composable
fun WhisperSettings() {
    val context = LocalContext.current
    val app = context.applicationContext
    val prefs = remember(app) { WhisperPrefs(Settings.getInstance(app)) }
    var model by remember { mutableStateOf(prefs.model) }
    var language by remember { mutableStateOf(prefs.language) }
    var acceleration by remember { mutableStateOf(prefs.acceleration) }
    var refresh by remember { mutableIntStateOf(0) }
    val status by rememberWhisperStatus()
    val running = status is WhisperStatus.Running
    // what each model takes on the device, read again after a delete and when a job ends
    val disk by produceState(emptyMap<WhisperModel, Long>(), refresh, running) {
        value = withContext(Dispatchers.IO) { WhisperModel.entries.associateWith { WhisperModels.bytesOnDisk(app, it) } }
    }
    val ready by produceState(emptySet<WhisperModel>(), refresh, running) {
        value = withContext(Dispatchers.IO) { WhisperModel.entries.filter { WhisperModels.ready(app, it) }.toSet() }
    }
    // the app stopped while the GPU worked: read again when a job ends and after "Try the GPU again"
    val gpuStopped by produceState(false, refresh, running) { value = withContext(Dispatchers.IO) { prefs.gpuStopped } }
    val scope = rememberCoroutineScope()
    SettingsSection(stringResource(R.string.nag_whisper_settings)) {
        Text(stringResource(R.string.nag_whisper_settings_hint), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
        Spacer(Modifier.height(NagTheme.dimens.gapS))
        SettingRow(stringResource(R.string.nag_whisper_model), subtitle = stringResource(model.hint), modifier = Modifier.testTag("whisper-settings-model")) {
            SegmentedChoice(
                options = WhisperModel.entries.map { it to stringResource(it.label) },
                selected = model,
                onSelect = {
                    model = it
                    prefs.model = it
                },
            )
        }
        SettingRow(stringResource(R.string.nag_whisper_language), modifier = Modifier.testTag("whisper-settings-language")) {
            SegmentedChoice(
                options = WhisperLanguage.entries.map { it to stringResource(it.label) },
                selected = language,
                onSelect = {
                    language = it
                    prefs.language = it
                },
            )
        }
        SettingRow(stringResource(R.string.nag_whisper_acceleration), subtitle = stringResource(acceleration.hint), modifier = Modifier.testTag("whisper-settings-acceleration")) {
            SegmentedChoice(
                options = WhisperAcceleration.entries.map { it to stringResource(it.label) },
                selected = acceleration,
                onSelect = {
                    acceleration = it
                    prefs.acceleration = it
                },
            )
        }
        if (gpuStopped && acceleration == WhisperAcceleration.Auto) {
            SettingRow(stringResource(R.string.nag_whisper_gpu_stopped), subtitle = stringResource(R.string.nag_whisper_gpu_stopped_hint), modifier = Modifier.testTag("whisper-settings-gpu-stopped")) {
                NagButton(
                    stringResource(R.string.nag_whisper_gpu_retry),
                    {
                        scope.launch {
                            withContext(Dispatchers.IO) { prefs.gpuStopped = false }
                            refresh++
                        }
                    },
                    enabled = !running,
                    modifier = Modifier.testTag("whisper-gpu-retry"),
                )
            }
        }
        WhisperModel.entries.forEach { entry ->
            val bytes = disk[entry] ?: 0L
            val state = when {
                entry in ready -> stringResource(R.string.nag_whisper_model_ready, megabytes(bytes))
                bytes > 0 -> stringResource(R.string.nag_whisper_model_partial, megabytes(bytes), entry.sizeMb)
                else -> stringResource(R.string.nag_whisper_model_absent, entry.sizeMb)
            }
            SettingRow(stringResource(entry.label), subtitle = state, modifier = Modifier.testTag("whisper-settings-${entry.key}")) {
                NagButton(
                    stringResource(R.string.nag_whisper_delete),
                    {
                        scope.launch {
                            withContext(Dispatchers.IO) { WhisperModels.delete(app, entry) }
                            refresh++
                        }
                    },
                    icon = NagIcons.Delete,
                    enabled = bytes > 0 && !running,
                    modifier = Modifier.testTag("whisper-delete-${entry.key}"),
                )
            }
        }
        if (running) Text(stringResource(R.string.nag_whisper_delete_running), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
    }
}
