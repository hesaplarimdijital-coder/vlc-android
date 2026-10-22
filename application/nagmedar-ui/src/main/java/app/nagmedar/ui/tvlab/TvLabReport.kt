package app.nagmedar.ui.tvlab

import android.content.res.Resources
import app.nagmedar.ui.R
import app.nagmedar.ui.library.sizeLabel
import java.util.Locale

/**
 * The TV Lab's report as text, to copy or share (S-1203, S-1204): the film and the target, the
 * result, every finding with why and what to do, the streams, the bitrate and the drive.
 */
fun tvLabReport(res: Resources, view: TvLabView, report: CheckReport?, locale: Locale, commentary: String): String = buildString {
    val file = view.file ?: return@buildString
    appendLine(res.getString(R.string.nag_tvlab_report_title))
    appendLine(res.getString(R.string.nag_tvlab_report_file, file.name))
    appendLine(res.getString(R.string.nag_tvlab_report_target, view.profile.label()))
    if (report != null) {
        appendLine(res.getString(R.string.nag_tvlab_report_result, resultCounts(res, report) + " · " + resultVerdict(res, report)))
        appendLine()
        appendLine(res.getString(R.string.nag_tvlab_findings))
        report.findings.forEach { finding ->
            val text = findingText(res, finding, view.profile, locale)
            appendLine("[${levelLabel(res, finding.level)}] ${text.title}")
            appendLine("  ${text.why}")
            appendLine("  → ${text.fix}")
        }
    }
    view.tech?.let { tech ->
        appendLine()
        appendLine(res.getString(R.string.nag_tvlab_streams))
        streamRows(res, tech, locale, commentary).forEach { (label, value) -> appendLine("$label: $value") }
    }
    view.bitrate?.takeIf { it.complete }?.let { analysis ->
        appendLine()
        appendLine(res.getString(R.string.nag_tvlab_bitrate))
        appendLine(
            res.getString(
                R.string.nag_tvlab_report_bitrate,
                kbpsLabel(analysis.averageKbps.toLong(), locale),
                kbpsLabel(analysis.peakSecondKbps.toLong(), locale),
                kbpsLabel(analysis.peakFiveSecondsKbps.toLong(), locale),
            ),
        )
        val limit = view.profile.limitKbps
        if (limit > 0) {
            val over = analysis.secondsOver(limit)
            appendLine(res.getString(R.string.nag_tvlab_report_over, res.getQuantityString(R.plurals.nag_tvlab_seconds, over, over), kbpsLabel(limit.toLong(), locale)))
        }
    }
    val drive = view.drive
    val disk = view.disk
    if (drive != null || disk != null) {
        appendLine()
        appendLine(res.getString(R.string.nag_tvlab_drive))
        drive?.let {
            appendLine(
                listOfNotNull(
                    it.name ?: res.getString(R.string.nag_tvlab_unknown_drive),
                    it.filesystem,
                    res.getString(if (it.removable) R.string.nag_tvlab_external_drive else R.string.nag_tvlab_internal_storage),
                    it.usbVersion?.let { version -> "USB $version" },
                    it.totalBytes?.takeIf { total -> total > 0 }?.let { total -> sizeLabel(total, locale) },
                    it.freeBytes?.takeIf { free -> free >= 0 }?.let { free -> res.getString(R.string.nag_tvlab_free, sizeLabel(free, locale)) },
                ).joinToString(" · "),
            )
        }
        disk?.let {
            appendLine(
                megabytesLabel(res, it.megabytesPerSecond, locale) + " · " +
                    res.getString(R.string.nag_tvlab_sequential, res.getString(R.string.nag_tvlab_ms, it.firstMs.toInt())),
            )
        }
    }
}.trimEnd()
