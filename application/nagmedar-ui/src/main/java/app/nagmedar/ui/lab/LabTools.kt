package app.nagmedar.ui.lab

import android.app.Activity
import android.content.res.Resources
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.os.ConfigurationCompat
import app.nagmedar.lab.DvConversion
import app.nagmedar.lab.NativeLab
import app.nagmedar.lab.UsbAudioLab
import app.nagmedar.lab.WhisperLab
import app.nagmedar.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.resume

/** What a tool needs picked before it runs. */
enum class LabInput {
    None,

    /** a video or audio file to read */
    Media,

    /** a file to read and a new file to write (the output type follows the "container" option) */
    MediaAndOutput,
}

/** A value of an option, and how its button names it in the interface's language (S-1501). */
class LabChoice(val value: String, val label: (Resources) -> String)

/** A choice shown on the tool's row; values are passed to [LabTool.run] by [key]. */
class LabOption(val key: String, @StringRes val label: Int, val choices: List<LabChoice>) {
    val default get() = choices.first().value

    /** The values and their names, for the row's buttons. */
    fun named(resources: Resources): List<Pair<String, String>> = choices.map { it.value to it.label(resources) }
}

private fun Resources.locale(): Locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()

/** "10 sn", "10 s". */
private fun seconds(count: Int) = LabChoice("$count") { it.getString(R.string.nag_duration_seconds, count) }

/** "10 dk", "10 min"; the value in seconds. */
private fun minutes(count: Int) = LabChoice("${count * 60}") { it.getString(R.string.nag_duration_minutes, count) }

/** "44,1 kHz" in Turkish, "44.1 kHz" in English; the value in Hz. */
private fun kiloHertz(hertz: Int) = LabChoice("$hertz") { res ->
    NumberFormat.getNumberInstance(res.locale()).apply { maximumFractionDigits = 1 }.format(hertz / 1000.0) + " kHz"
}

/** A name that is the same in every language: "MKV", "24 bit". */
private fun fixed(value: String, name: String) = LabChoice(value) { name }

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

    private val dvDuration = LabOption(
        "seconds", R.string.nag_lab_option_duration,
        listOf(seconds(60), LabChoice("0") { it.getString(R.string.nag_filter_all) }),
    )
    private val dvContainer = LabOption("container", R.string.nag_lab_option_container, listOf(fixed("mkv", "MKV"), fixed("mp4", "MP4")))
    private val whisperModel = LabOption("model", R.string.nag_lab_option_model, WhisperLab.Model.entries.map { fixed(it.name, it.label) })
    private val whisperDuration = LabOption("seconds", R.string.nag_lab_option_duration, listOf(minutes(1), minutes(10)))
    private val usbRate = LabOption("rate", R.string.nag_lab_option_rate, listOf(kiloHertz(96000), kiloHertz(44100), kiloHertz(192000)))
    private val usbBits = LabOption("bits", R.string.nag_lab_option_bits, listOf(fixed("24", "24 bit"), fixed("16", "16 bit"), fixed("32", "32 bit")))
    private val usbDuration = LabOption("seconds", R.string.nag_lab_option_duration, listOf(seconds(10), minutes(10)))

    val all: List<LabTool> = listOf(
        LabTool("SP-01", R.string.nag_lab_audio_report, R.string.nag_lab_audio_report_desc) { run ->
            withContext(Dispatchers.Default) { AudioReport.build(run.activity) }
        },
        LabTool("SP-01", R.string.nag_lab_bit_perfect, R.string.nag_lab_bit_perfect_desc) { run ->
            withContext(Dispatchers.Default) { AudioReport.tryBitPerfect(run.activity) }
        },
        LabTool("SP-02", R.string.nag_lab_usb_describe, R.string.nag_lab_usb_describe_desc) { run ->
            UsbAudioLab.describe(run.activity)
        },
        LabTool(
            "SP-02", R.string.nag_lab_usb_tone, R.string.nag_lab_usb_tone_desc,
            options = listOf(usbRate, usbBits, usbDuration), cancellable = true,
        ) { run ->
            UsbAudioLab.tone(run.activity, run.option("rate").toInt(), run.option("bits").toInt(), run.option("seconds").toDouble(), run.progress)
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
