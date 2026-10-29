package app.nagmedar.ui.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.lab.NativeLab
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A running tool: its progress line and whether it can be stopped. */
private class Running(val tool: LabTool) {
    var fraction by mutableStateOf(0.0)
    var message by mutableStateOf("")
}

/**
 * Settings > Lab: the MVP0 spike experiments that need the real device (the audio output, the
 * USB DAC). Each one produces a text report to copy or share. [section] is the
 * settings card the tools sit in.
 */
@Composable
fun LabSection(section: @Composable (title: String, content: @Composable ColumnScope.() -> Unit) -> Unit, header: @Composable ColumnScope.() -> Unit = {}) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf<Running?>(null) }
    var result by remember { mutableStateOf<Pair<String, String>?>(null) }
    val choices = remember { mutableStateMapOf<String, String>() }

    fun options(tool: LabTool) = tool.options.associate { it.key to (choices["${tool.title}:${it.key}"] ?: it.default) }

    fun start(tool: LabTool) {
        val activity = context.findActivity() ?: return
        val title = context.getString(tool.title)
        val state = Running(tool)
        running = state
        scope.launch {
            val progress = NativeLab.Progress { fraction, message ->
                scope.launch(Dispatchers.Main) {
                    state.fraction = fraction
                    state.message = message
                }
            }
            val report = try {
                tool.run(LabRun(activity, options(tool), progress))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                "${e::class.java.simpleName}: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                running = null
                result = title to report
            }
        }
    }

    val tools = rememberLabAvailable()

    section(stringResource(R.string.nag_settings_lab)) {
        header()
        Text(stringResource(R.string.nag_lab_intro), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        if (tools == false) {
            Text(stringResource(R.string.nag_lab_native_missing), style = NagTheme.type.labelSmall, color = colors.warning,
                modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(dimens.gapS))
        LabTools.all.forEach { tool ->
            val title = stringResource(tool.title)
            val current = running
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = dimens.rowHeight), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = dimens.gapM)) {
                        Text("${tool.spike} · $title", style = NagTheme.type.bodyStrong, color = colors.text)
                        Text(stringResource(tool.description), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                    }
                    if (current?.tool === tool && tool.cancellable) {
                        NagButton(stringResource(R.string.nag_lab_stop), onClick = { if (NativeLab.loaded == true) NativeLab.cancel() })
                    } else {
                        NagButton(
                            text = stringResource(if (current?.tool === tool) R.string.nag_lab_running else R.string.nag_lab_run),
                            onClick = { start(tool) },
                            enabled = current == null,
                        )
                    }
                }
                if (tool.options.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tool.options.forEach { option ->
                            val key = "${tool.title}:${option.key}"
                            SegmentedChoice(
                                options = option.named(context.resources),
                                selected = choices[key] ?: option.default,
                                onSelect = { choices[key] = it },
                            )
                        }
                    }
                }
                if (current?.tool === tool) ProgressLine(current.fraction, current.message)
            }
        }
    }
    result?.let { (title, report) -> ReportDialog(title, report, onDismiss = { result = null }) }
}

@Composable
private fun ProgressLine(fraction: Double, message: String) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(2.dp)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(colors.border, shape)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.toFloat().coerceIn(0f, 1f), 0f..1f) },
        ) {
            Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f)).height(4.dp).background(colors.accent, shape))
        }
        if (message.isNotEmpty()) Text(message, style = NagTheme.type.caption, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Whether this build has the native lab tools, for a composition: what is known at once (null
 * before the library's first load), then the answer of a load made off the main thread. The
 * library reads the APK as it loads; the main thread never loads it nor waits for it.
 */
@Composable
fun rememberLabAvailable(): Boolean? =
    produceState(NativeLab.loaded) { value = withContext(Dispatchers.IO) { NativeLab.available } }.value

