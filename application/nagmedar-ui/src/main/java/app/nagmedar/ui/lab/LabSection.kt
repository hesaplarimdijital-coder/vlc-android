package app.nagmedar.ui.lab

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
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
import app.nagmedar.lab.displayName
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ACTION_CREATE_DOCUMENT with the MIME type and file name decided at launch time. */
private class CreateFile : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>) =
        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(input.first).putExtra(Intent.EXTRA_TITLE, input.second)

    override fun parseResult(resultCode: Int, intent: Intent?) = intent?.data
}

/** A running tool: its progress line and whether it can be stopped. */
private class Running(val tool: LabTool) {
    var fraction by mutableStateOf(0.0)
    var message by mutableStateOf("")
}

/**
 * Settings > Lab: the MVP0 spike experiments that need the real device (USB DAC, TV over HDMI,
 * remuxes on the external disk). Each one produces a text report to copy or share. [section]
 * is the settings card the tools sit in.
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
    var pending by remember { mutableStateOf<LabTool?>(null) }
    var pickedInput by remember { mutableStateOf<Uri?>(null) }

    fun options(tool: LabTool) = tool.options.associate { it.key to (choices["${tool.title}:${it.key}"] ?: it.default) }

    fun start(tool: LabTool, input: Uri?, output: Uri?) {
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
                tool.run(LabRun(activity, input, output, options(tool), progress))
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

    val createOutput = rememberLauncherForActivityResult(CreateFile()) { uri ->
        val tool = pending
        val input = pickedInput
        pending = null
        if (tool != null && input != null && uri != null) start(tool, input, uri)
    }
    val openInput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val tool = pending ?: return@rememberLauncherForActivityResult
        if (uri == null) {
            pending = null
            return@rememberLauncherForActivityResult
        }
        if (tool.input == LabInput.MediaAndOutput) {
            pickedInput = uri
            val mp4 = options(tool)["container"] == "mp4"
            val base = context.displayName(uri).substringBeforeLast('.')
            createOutput.launch((if (mp4) "video/mp4" else "video/x-matroska") to "$base.P8${if (mp4) ".mp4" else ".mkv"}")
        } else {
            pending = null
            start(tool, uri, null)
        }
    }

    section(stringResource(R.string.nag_settings_lab)) {
        header()
        Text(stringResource(R.string.nag_lab_intro), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        if (!NativeLab.available) {
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
                        NagButton(stringResource(R.string.nag_lab_stop), onClick = { NativeLab.takeIf { it.available }?.cancel() })
                    } else {
                        NagButton(
                            text = stringResource(if (current?.tool === tool) R.string.nag_lab_running else R.string.nag_lab_run),
                            onClick = {
                                when (tool.input) {
                                    LabInput.None -> start(tool, null, null)
                                    else -> {
                                        pending = tool
                                        openInput.launch(arrayOf("video/*", "audio/*", "application/octet-stream"))
                                    }
                                }
                            },
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
                                options = option.choices,
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

