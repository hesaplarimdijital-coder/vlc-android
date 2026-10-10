package app.nagmedar.ui.lab

import android.app.Activity
import androidx.annotation.StringRes
import app.nagmedar.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An experiment of an MVP0 spike that has to run on the user's device (Settings > Lab).
 * [run] returns a plain-text report that the user can copy or share into the spike's decision
 * note; tools that touch the UI (presentations) run on the main thread, the rest move off it.
 */
class LabTool(
    val spike: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val run: suspend (Activity) -> String,
)

object LabTools {

    val all: List<LabTool> = listOf(
        LabTool("SP-01", R.string.nag_lab_audio_report, R.string.nag_lab_audio_report_desc) { activity ->
            withContext(Dispatchers.Default) { AudioReport.build(activity) }
        },
        LabTool("SP-01", R.string.nag_lab_bit_perfect, R.string.nag_lab_bit_perfect_desc) { activity ->
            withContext(Dispatchers.Default) { AudioReport.tryBitPerfect(activity) }
        },
    )
}
