package app.nagmedar.ui.lab

import android.app.Activity
import android.net.Uri
import androidx.annotation.StringRes
import app.nagmedar.lab.DvConversion
import app.nagmedar.lab.NativeLab
import app.nagmedar.lab.WhisperLab
import app.nagmedar.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** What a tool needs picked before it runs. */
enum class LabInput {
    None,

    /** a video or audio file to read */
    Media,

    /** a file to read and a new file to write (the output type follows the "container" option) */
    MediaAndOutput,
}

/** A choice shown on the tool's row; values are passed to [LabTool.run] by [key]. */
class LabOption(val key: String, @StringRes val label: Int, val choices: List<Pair<String, String>>) {
    val default get() = choices.first().first
}

/** Everything a running tool gets: the picked files, the chosen options and a progress sink. */
class LabRun(
    val activity: Activity,
    val input: Uri?,
    val output: Uri?,
    val options: Map<String, String>,
    val progress: NativeLab.Progress,
) {
    fun option(key: String) = options.getValue(key)
}

/**
 * An experiment of an MVP0 spike that has to run on the user's device (Settings > Lab).
 * [run] returns a plain-text report that the user can copy or share into the spike's decision
 * note; tools that touch the UI (presentations) run on the main thread, the rest move off it.
 * [cancellable] tools stop through [NativeLab.cancel].
 */
class LabTool(
    val spike: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val input: LabInput = LabInput.None,
    val options: List<LabOption> = emptyList(),
    val cancellable: Boolean = false,
    val run: suspend (LabRun) -> String,
)

object LabTools {

    private val dvDuration = LabOption("seconds", R.string.nag_lab_option_duration, listOf("60" to "60 sn", "0" to "Tümü"))
    private val dvContainer = LabOption("container", R.string.nag_lab_option_container, listOf("mkv" to "MKV", "mp4" to "MP4"))
    private val whisperModel = LabOption("model", R.string.nag_lab_option_model, WhisperLab.Model.entries.map { it.name to it.label })
    private val whisperDuration = LabOption("seconds", R.string.nag_lab_option_duration, listOf("60" to "1 dk", "600" to "10 dk"))

    val all: List<LabTool> = listOf(
        LabTool("SP-01", R.string.nag_lab_audio_report, R.string.nag_lab_audio_report_desc) { run ->
            withContext(Dispatchers.Default) { AudioReport.build(run.activity) }
        },
        LabTool("SP-01", R.string.nag_lab_bit_perfect, R.string.nag_lab_bit_perfect_desc) { run ->
            withContext(Dispatchers.Default) { AudioReport.tryBitPerfect(run.activity) }
        },
        LabTool(
            "SP-03", R.string.nag_lab_dv_convert, R.string.nag_lab_dv_convert_desc, input = LabInput.MediaAndOutput,
            options = listOf(dvDuration, dvContainer), cancellable = true,
        ) { run ->
            DvConversion.run(run.activity, run.input!!, run.output!!, run.option("seconds").toDouble(), run.option("container") == "mp4", run.progress)
        },
        LabTool("SP-04", R.string.nag_lab_display_report, R.string.nag_lab_display_report_desc) { run ->
            DisplayReport.build(run.activity)
        },
        LabTool("SP-04", R.string.nag_lab_refresh_rate, R.string.nag_lab_refresh_rate_desc) { run ->
            suspendCancellableCoroutine { continuation ->
                DisplayReport.tryRefreshRate(run.activity) { result -> if (continuation.isActive) continuation.resume(result) }
            }
        },
        LabTool(
            "SP-05", R.string.nag_lab_whisper, R.string.nag_lab_whisper_desc, input = LabInput.Media,
            options = listOf(whisperModel, whisperDuration), cancellable = true,
        ) { run ->
            WhisperLab.run(run.activity, run.input!!, WhisperLab.Model.valueOf(run.option("model")), run.option("seconds").toDouble(), run.progress)
        },
    )
}
