package app.nagmedar.ui.perf

import android.content.res.Resources
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.lab.ReportDialog
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.theme.NagTheme
import org.videolan.vlc.BuildConfig
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the Performance card shows: the start-ups kept, the newest first. */
@Immutable
data class PerfView(val startups: List<StartupRecord>) {
    val empty: Boolean get() = startups.isEmpty()

    /** The middle start-up of those kept; null before the first. */
    val medianStartupMs: Long? get() = startups.map { it.ms }.sorted().let { if (it.isEmpty()) null else it[(it.size - 1) / 2] }

    companion object {
        fun of(store: PerfStore) = PerfView(store.startups())
    }
}

/**
 * Settings › Performance (S-1404): the cold start-ups this device measured,
 * against Nağmedar's targets; a report of them to copy or share; the records deleted. [section]
 * is the settings card it sits in.
 */
@Composable
fun PerformanceSection(section: @Composable (title: String, content: @Composable ColumnScope.() -> Unit) -> Unit) {
    val context = LocalContext.current
    // the records' file is read off the main thread: the card shows none until it is read
    var view by remember { mutableStateOf(PerfView(emptyList())) }
    var store by remember { mutableStateOf<PrefsPerfStore?>(null) }
    LaunchedEffect(context) {
        val opened = withContext(Dispatchers.IO) { PrefsPerfStore.get(context) }
        view = withContext(Dispatchers.IO) { PerfView.of(opened) }
        store = opened
    }
    DisposableEffect(store) {
        val stop = store?.let { opened -> opened.listen { view = PerfView.of(opened) } }
        onDispose { stop?.invoke() }
    }
    var report by remember { mutableStateOf<String?>(null) }
    val res = resources()
    val locale = currentLocale()
    section(stringResource(R.string.nag_perf_title)) {
        PerformanceCard(view, onReport = { report = perfReport(res, view, locale) }, onClear = { store?.clear() })
    }
    report?.let { ReportDialog(stringResource(R.string.nag_perf_report_title), it) { report = null } }
}

/** The resources in the interface's language; read in composition, a change of language recomposes. */
@Composable
@ReadOnlyComposable
private fun resources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}

/** The card's content for [view]. */
@Composable
fun PerformanceCard(view: PerfView, onReport: () -> Unit, onClear: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val res = resources()
    val locale = currentLocale()
    Column(Modifier.fillMaxWidth().testTag("performance")) {
        Text(stringResource(R.string.nag_perf_intro), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        Spacer(Modifier.height(dimens.gapS))
        val median = view.medianStartupMs
        SettingRow(
            stringResource(R.string.nag_perf_startup),
            subtitle = when {
                median == null -> stringResource(R.string.nag_perf_startup_none)
                view.startups.size == 1 -> stringResource(R.string.nag_perf_startup_last, secondsLabel(res, PerfTargets.STARTUP_MS, locale, 0))
                else -> stringResource(R.string.nag_perf_startup_median, view.startups.size, secondsLabel(res, PerfTargets.STARTUP_MS, locale, 0))
            },
        ) {
            if (median != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalAlignment = Alignment.CenterVertically) {
                    Text(secondsLabel(res, median, locale), style = NagTheme.type.bodyStrong, color = colors.text)
                    if (PerfTargets.startupOk(median)) {
                        ToneBadge(stringResource(R.string.nag_perf_on_target), StateTone.Success)
                    } else {
                        ToneBadge(stringResource(R.string.nag_perf_slow), StateTone.Warning)
                    }
                }
            }
        }
        Spacer(Modifier.height(dimens.gapS))
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            NagButton(stringResource(R.string.nag_perf_report), onReport, enabled = !view.empty)
            NagButton(stringResource(R.string.nag_perf_clear), onClear, enabled = !view.empty)
        }
    }
}

/** "0,64 sn": milliseconds as seconds, with [digits] decimals. */
fun secondsLabel(res: Resources, ms: Long, locale: Locale, digits: Int = 2): String {
    val number = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(ms / 1000.0)
    return res.getString(R.string.nag_perf_seconds, number)
}

/**
 * The report: the device and the build, then every start-up kept with its time; to
 * copy into an issue or send on.
 */
fun perfReport(res: Resources, view: PerfView, locale: Locale): String = buildString {
    val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    appendLine(res.getString(R.string.nag_perf_report_title))
    appendLine(res.getString(R.string.nag_perf_report_device, "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE.orEmpty()))
    appendLine(res.getString(R.string.nag_settings_version, BuildConfig.VLC_VERSION_NAME, BuildConfig.LIBVLC_VERSION))
    appendLine()
    appendLine(res.getString(R.string.nag_perf_report_startups, secondsLabel(res, PerfTargets.STARTUP_MS, locale, 0)))
    if (view.startups.isEmpty()) appendLine("  " + res.getString(R.string.nag_perf_startup_none))
    view.startups.forEach { appendLine("  ${time.format(Date(it.atMs))} · ${secondsLabel(res, it.ms, locale)}") }
}.trimEnd()
