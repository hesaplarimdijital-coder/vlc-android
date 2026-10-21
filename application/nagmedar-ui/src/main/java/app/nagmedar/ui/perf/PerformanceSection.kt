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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.lab.ReportDialog
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.resources
import app.nagmedar.ui.theme.NagTheme
import org.videolan.vlc.BuildConfig
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** What the Performance card shows: the start-ups and playbacks kept, the newest first. */
@Immutable
data class PerfView(val startups: List<StartupRecord>, val playbacks: List<PlaybackRecord>) {
    val empty: Boolean get() = startups.isEmpty() && playbacks.isEmpty()

    /** The middle start-up of those kept; null before the first. */
    val medianStartupMs: Long? get() = startups.map { it.ms }.sorted().let { if (it.isEmpty()) null else it[(it.size - 1) / 2] }

    companion object {
        fun of(store: PerfStore) = PerfView(store.startups(), store.playbacks())
    }
}

/**
 * Settings › Performance (S-1404): the cold start-ups and the playbacks this device measured,
 * against Nağmedar's targets; a report of them to copy or share; the records deleted. [section]
 * is the settings card it sits in.
 */
@Composable
fun PerformanceSection(section: @Composable (title: String, content: @Composable ColumnScope.() -> Unit) -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { PrefsPerfStore.get(context) }
    var view by remember { mutableStateOf(PerfView.of(store)) }
    DisposableEffect(store) {
        val stop = store.listen { view = PerfView.of(store) }
        onDispose { stop() }
    }
    var report by remember { mutableStateOf<String?>(null) }
    val res = resources()
    val locale = currentLocale()
    section(stringResource(R.string.nag_perf_title)) {
        PerformanceCard(view, onReport = { report = perfReport(res, view, locale) }, onClear = store::clear)
    }
    report?.let { ReportDialog(stringResource(R.string.nag_perf_report_title), it) { report = null } }
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
        Text(stringResource(R.string.nag_perf_playbacks), style = NagTheme.type.bodyStrong, color = colors.text)
        Text(
            stringResource(R.string.nag_perf_playbacks_target, perfPercent(PerfTargets.DROPPED_PERCENT, locale)),
            style = NagTheme.type.labelSmall,
            color = colors.textTertiary,
        )
        if (view.playbacks.isEmpty()) {
            Text(
                stringResource(R.string.nag_perf_playbacks_none),
                style = NagTheme.type.body,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = dimens.gapS),
            )
        } else {
            Column(Modifier.fillMaxWidth().padding(vertical = dimens.gapXs)) {
                view.playbacks.take(SHOWN).forEach { PlaybackRow(res, it, locale) }
            }
        }
        Spacer(Modifier.height(dimens.gapS))
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            NagButton(stringResource(R.string.nag_perf_report), onReport, enabled = !view.empty)
            NagButton(stringResource(R.string.nag_perf_clear), onClear, enabled = !view.empty)
        }
    }
}

/** The playbacks the card lists; the report has every one kept. */
private const val SHOWN = 5

@Composable
private fun PlaybackRow(res: Resources, record: PlaybackRecord, locale: Locale) {
    val colors = NagTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS)) {
            Text(
                record.name,
                style = NagTheme.type.body,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ToneBadge(droppedLabel(res, record, locale), if (PerfTargets.playbackOk(record)) StateTone.Success else StateTone.Warning)
        }
        Text(playbackDetails(res, record, locale), style = NagTheme.type.labelSmall, color = colors.textTertiary)
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

/** "%0,02", "0.02%": a percentage; a small one with a third decimal, so that it does not read as none. */
fun perfPercent(percent: Double, locale: Locale): String = NumberFormat.getPercentInstance(locale).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = if (percent > 0 && percent < 0.01) 3 else if (percent < 10) 2 else 1
}.format(percent / 100.0)

/** "%0,02 kayıp", "Kayıp yok". */
fun droppedLabel(res: Resources, record: PlaybackRecord, locale: Locale): String =
    if (record.dropped == 0L) res.getString(R.string.nag_perf_none_dropped) else res.getString(R.string.nag_perf_dropped, perfPercent(record.droppedPercent, locale))

/** "3840×2160 · HDR10 · 1 sa 52 dk · pil saatte %9,8". */
fun playbackDetails(res: Resources, record: PlaybackRecord, locale: Locale): String = listOfNotNull(
    if (record.width > 0 && record.height > 0) "${record.width}×${record.height}" else null,
    record.range.ifEmpty { null },
    playedLabel(res, record.seconds),
    record.batteryPerHour?.let { res.getString(R.string.nag_perf_battery, NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }.format(it / 100.0)) }
        ?: res.getString(R.string.nag_perf_battery_none),
).joinToString(" · ")

/** "10 sn", "42 dk", "1 sa 52 dk". */
fun playedLabel(res: Resources, seconds: Int): String = when {
    seconds < 60 -> res.getString(R.string.nag_duration_seconds, seconds)
    seconds < 3600 -> res.getString(R.string.nag_duration_minutes, seconds / 60)
    else -> res.getString(R.string.nag_duration_hours_minutes, seconds / 3600, seconds / 60 % 60)
}

/**
 * The report: the device and the build, then every start-up and playback kept with its time; to
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
    appendLine()
    appendLine(res.getString(R.string.nag_perf_report_playbacks, perfPercent(PerfTargets.DROPPED_PERCENT, locale)))
    if (view.playbacks.isEmpty()) appendLine("  " + res.getString(R.string.nag_perf_playbacks_none))
    view.playbacks.forEach {
        val frames = NumberFormat.getIntegerInstance(locale)
        appendLine("  ${time.format(Date(it.atMs))} · ${it.name}")
        appendLine("    " + playbackDetails(res, it, locale))
        appendLine("    " + res.getString(R.string.nag_perf_frames, frames.format(it.shown), frames.format(it.dropped)) + " · " + droppedLabel(res, it, locale))
    }
}.trimEnd()
