/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.4.0: after Metamatik (feature/editor/.../components/LosslessCheckCard.kt and core/ui/.../component/Quality.kt),
 * a fork of Metadator, under the AGPL-3.0; drawn again in Nağmedar's look.
 */
package app.nagmedar.ui.quality

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.tags.quality.LosslessFormat
import app.nagmedar.tags.quality.LosslessReport
import app.nagmedar.tags.quality.LosslessVerdict
import app.nagmedar.tags.quality.SpectrumCurve
import app.nagmedar.tags.quality.TrackQuality
import app.nagmedar.tags.quality.UnclearReason
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.music.LocalCheckFindings
import app.nagmedar.ui.tags.LosslessCheckState
import app.nagmedar.ui.theme.NagTheme
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 1.4.0: whether one FLAC or WAV file is what it says: the verdict in a line, why in a sentence, the spectrum it was read
 * from, for whoever wants to see for themselves; its edge in the verdict's colour. Nothing for a file there is no
 * question about.
 */
@Composable
internal fun LosslessCard(state: LosslessCheckState, modifier: Modifier = Modifier) {
    when (state) {
        LosslessCheckState.None -> Unit
        LosslessCheckState.Checking -> CheckCard(Tone.Neutral, modifier) {
            val text = stringResource(R.string.nag_quality_checking_long)
            Text(text, style = NagTheme.type.body, color = NagTheme.colors.textSecondary)
            NagProgressBar(null, text)
        }
        LosslessCheckState.Failed -> CheckCard(Tone.Neutral, modifier) {
            VerdictLine(NagIcons.Info, Tone.Neutral, stringResource(R.string.nag_quality_failed), null)
        }
        is LosslessCheckState.Done -> ReportCard(state.report, modifier)
    }
}

/** The tone a verdict is said in. */
internal enum class Tone { Neutral, Success, Warning, Danger }

@Composable
internal fun Tone.color(): Color = when (this) {
    Tone.Neutral -> NagTheme.colors.textSecondary
    Tone.Success -> NagTheme.colors.success
    Tone.Warning -> NagTheme.colors.warning
    Tone.Danger -> NagTheme.colors.danger
}

/** The card a verdict is said on, edged with the verdict's colour. */
@Composable
private fun CheckCard(tone: Tone, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    val edge = if (tone == Tone.Neutral) colors.border else tone.color().copy(alpha = 0.45f)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, shape)
            .border(1.dp, edge, shape)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)
            .testTag("tag-lossless"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** The verdict in its tone's circle, why under it: read as one thing. */
@Composable
private fun VerdictLine(icon: ImageVector, tone: Tone, title: String, summary: String?) {
    val colors = NagTheme.colors
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).background(tone.color().copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tone.color(), modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NagTheme.type.bodyStrong, color = colors.text, modifier = Modifier.testTag("tag-lossless-verdict"))
            if (summary != null) Text(summary, style = NagTheme.type.labelSmall, color = colors.textSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportCard(report: LosslessReport, modifier: Modifier = Modifier) {
    val look = verdictLook(report.verdict)
    val locale = LocalContext.current.resources.locale()
    CheckCard(look.tone, modifier) {
        VerdictLine(look.icon, look.tone, stringResource(look.title), verdictSummary(report, locale))
        SpectrumChart(report.spectrum, report.cutoffHz, look.tone, locale)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val format = when (report.format) {
                LosslessFormat.Flac -> FLAC
                LosslessFormat.Wav -> WAV
            }
            FactChip(stringResource(R.string.nag_quality_format, format, report.bitsPerSample, kiloHertz(report.sampleRateHz, locale)), Tone.Neutral)
            report.estimatedSourceKbps?.let { FactChip(stringResource(R.string.nag_quality_source_bitrate, it), look.tone) }
            report.effectiveBitsPerSample?.let { FactChip(stringResource(R.string.nag_quality_bits_used, it, report.bitsPerSample), Tone.Warning) }
        }
        Text(
            stringResource(R.string.nag_quality_footnote, report.analyzedSeconds.roundToInt()),
            style = NagTheme.type.caption,
            color = NagTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun FactChip(text: String, tone: Tone) {
    val shape = RoundedCornerShape(50)
    val color = if (tone == Tone.Neutral) NagTheme.colors.textSecondary else tone.color()
    Text(
        text,
        style = NagTheme.type.labelSmall,
        color = color,
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.35f), shape).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * How loud each frequency is, from none at the left to the highest the file can hold at the right: the average as an
 * area in the verdict's colour, fading downwards. A lossy source shows as an area ending in a wall; where it does is
 * marked.
 */
@Composable
private fun SpectrumChart(curve: SpectrumCurve, cutoffHz: Int?, tone: Tone, locale: Locale) {
    val colors = NagTheme.colors
    val fill = if (tone == Tone.Neutral) colors.accent else tone.color()
    val marker = colors.text.copy(alpha = 0.6f)
    val description = stringResource(R.string.nag_quality_chart)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.hover)
                .semantics { contentDescription = description }
                .testTag("tag-lossless-chart"),
        ) {
            val count = curve.size
            if (count < 2) return@Canvas
            // a little air over the loudest average; under the quietest, down to where nothing is told apart any more
            val top = (curve.averageDb.max() + CHART_MARGIN_DB).coerceAtMost(0f)
            val bottom = (curve.averageDb.min() - CHART_MARGIN_DB).coerceAtLeast(CHART_FLOOR_DB)
            val range = (top - bottom).coerceAtLeast(1f)
            fun x(index: Int) = size.width * (index + 0.5f) / count
            fun y(level: Float) = size.height * ((top - level) / range).coerceIn(0f, 1f)
            val area = Path()
            val outline = Path()
            area.moveTo(0f, size.height)
            area.lineTo(0f, y(curve.averageDb[0]))
            outline.moveTo(0f, y(curve.averageDb[0]))
            for (index in 0 until count) {
                area.lineTo(x(index), y(curve.averageDb[index]))
                outline.lineTo(x(index), y(curve.averageDb[index]))
            }
            area.lineTo(size.width, y(curve.averageDb[count - 1]))
            area.lineTo(size.width, size.height)
            area.close()
            drawPath(area, Brush.verticalGradient(listOf(fill.copy(alpha = 0.6f), fill.copy(alpha = 0.06f))))
            drawPath(outline, fill, style = Stroke(width = 1.2.dp.toPx()))
            if (cutoffHz != null && curve.nyquistHz > 0f) {
                val at = size.width * (cutoffHz / curve.nyquistHz).coerceIn(0f, 1f)
                drawLine(
                    color = marker,
                    start = Offset(at, 6.dp.toPx()),
                    end = Offset(at, size.height - 6.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0f, curve.nyquistHz / 2, curve.nyquistHz).forEach { hz ->
                Text(stringResource(R.string.nag_quality_khz, kiloHertz(hz.roundToInt(), locale)), style = NagTheme.type.mono, color = colors.textTertiary)
            }
        }
        if (cutoffHz != null) {
            Text(stringResource(R.string.nag_quality_cutoff, kiloHertz(cutoffHz, locale)), style = NagTheme.type.mono, color = colors.textSecondary)
        }
    }
}

private class VerdictLook(val title: Int, val icon: ImageVector, val tone: Tone)

private fun verdictLook(verdict: LosslessVerdict): VerdictLook = when (verdict) {
    LosslessVerdict.Lossless -> VerdictLook(R.string.nag_quality_verdict_lossless, NagIcons.SuccessCircle, Tone.Success)
    LosslessVerdict.Upscaled -> VerdictLook(R.string.nag_quality_verdict_upscaled, NagIcons.Warning, Tone.Warning)
    LosslessVerdict.Unclear -> VerdictLook(R.string.nag_quality_verdict_unclear, NagIcons.Info, Tone.Neutral)
    LosslessVerdict.ProbablyTranscoded -> VerdictLook(R.string.nag_quality_verdict_probably_fake, NagIcons.Warning, Tone.Danger)
    LosslessVerdict.Transcoded -> VerdictLook(R.string.nag_quality_verdict_fake, NagIcons.ErrorCircle, Tone.Danger)
}

/** Why the verdict is what it is, in a sentence. */
@Composable
private fun verdictSummary(report: LosslessReport, locale: Locale): String {
    val cutoff = report.cutoffHz?.let { kiloHertz(it, locale) }
    val bitrate = report.estimatedSourceKbps
    return when (report.verdict) {
        LosslessVerdict.Lossless ->
            if (cutoff == null) stringResource(R.string.nag_quality_summary_full_band, kiloHertz(report.sampleRateHz / 2, locale))
            else stringResource(R.string.nag_quality_summary_natural_end, cutoff)
        LosslessVerdict.Upscaled ->
            if (report.upsampled && cutoff != null) stringResource(R.string.nag_quality_summary_upsampled, kiloHertz(report.sampleRateHz, locale), cutoff)
            else stringResource(R.string.nag_quality_summary_padded, report.bitsPerSample, report.effectiveBitsPerSample ?: report.bitsPerSample)
        LosslessVerdict.Unclear -> when (report.unclearReason) {
            UnclearReason.LowSampleRate -> stringResource(R.string.nag_quality_summary_low_rate)
            UnclearReason.BorderlineCutoff -> stringResource(R.string.nag_quality_summary_borderline, cutoff.orEmpty())
            UnclearReason.TooQuiet, null -> stringResource(R.string.nag_quality_summary_too_quiet)
        }
        LosslessVerdict.ProbablyTranscoded ->
            if (cutoff != null && bitrate != null) stringResource(R.string.nag_quality_summary_probably, cutoff, bitrate)
            else stringResource(R.string.nag_quality_summary_shelf)
        LosslessVerdict.Transcoded ->
            if (cutoff != null && bitrate != null) stringResource(R.string.nag_quality_summary_fake, cutoff, bitrate)
            else stringResource(R.string.nag_quality_summary_shelf)
    }
}

/**
 * What the library knows of a song's audio in a few words and their tone, for the table's Kalite column; null for a file
 * there is nothing to check in (a lossy one). [format] is what the file says it is ("FLAC").
 */
internal fun qualityText(resources: Resources, quality: TrackQuality, format: String): Pair<String, Tone>? = when (quality) {
    TrackQuality.NotApplicable -> null
    TrackQuality.Unchecked -> resources.getString(R.string.nag_quality_unchecked) to Tone.Neutral
    TrackQuality.Checking -> resources.getString(R.string.nag_quality_checking) to Tone.Neutral
    TrackQuality.Unreadable -> resources.getString(R.string.nag_quality_unreadable) to Tone.Neutral
    is TrackQuality.Checked -> when (quality.verdict) {
        LosslessVerdict.Lossless -> resources.getString(R.string.nag_quality_lossless) to Tone.Success
        LosslessVerdict.Upscaled -> resources.getString(R.string.nag_quality_upscaled) to Tone.Warning
        LosslessVerdict.Unclear -> resources.getString(R.string.nag_quality_unclear) to Tone.Neutral
        LosslessVerdict.ProbablyTranscoded -> resources.getString(R.string.nag_quality_probably_fake) to Tone.Danger
        LosslessVerdict.Transcoded -> resources.getString(R.string.nag_quality_fake, format) to Tone.Danger
    }
}

/** Where a song's quality sorts: the made from lossy first, the real lossless last; null for the rest (they go last). */
internal fun qualityRank(quality: TrackQuality): Int? = when (quality) {
    is TrackQuality.Checked -> when (quality.verdict) {
        LosslessVerdict.Transcoded -> 0
        LosslessVerdict.ProbablyTranscoded -> 1
        LosslessVerdict.Upscaled -> 2
        LosslessVerdict.Unclear -> 3
        LosslessVerdict.Lossless -> 4
    }
    else -> null
}

/** What a file says it is, as the table writes it: "FLAC", "WAV". */
internal fun formatOf(uri: String): String = CueUri.file(uri).substringAfterLast('/').substringAfterLast('.', "").uppercase(Locale.ROOT)

/**
 * 1.4.0, the table's Kalite cell: the library's verdict on a FLAC or WAV file in its tone, a dash for a lossy file (a
 * screen reader hears there is nothing to check in it). It follows the check as it goes.
 */
@Composable
fun QualityCell(uri: String, selected: Boolean) {
    val state = LocalCheckFindings.current?.quality ?: LibraryChecks.quality.collectAsState().value
    val resources = LocalContext.current.resources
    val label = qualityText(resources, state.of(CueUri.file(uri)), formatOf(uri))
    val colors = NagTheme.colors
    if (label == null) {
        val lossy = stringResource(R.string.nag_quality_lossy)
        Text("—", style = NagTheme.type.body, color = if (selected) colors.onSelection else colors.textTertiary, modifier = Modifier.semantics { contentDescription = lossy })
        return
    }
    val color = when {
        selected -> colors.onSelection
        label.second == Tone.Neutral -> colors.textTertiary
        else -> label.second.color()
    }
    Text(label.first, style = NagTheme.type.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** [hertz] in kHz as it is said: "44,1", "48", "22,05" being close enough to "22,1". */
internal fun kiloHertz(hertz: Int, locale: Locale): String {
    val tenths = (hertz / 100.0).roundToInt()
    return if (tenths % 10 == 0) (tenths / 10).toString() else String.format(locale, "%.1f", tenths / 10.0)
}

private const val FLAC = "FLAC"
private const val WAV = "WAV"
private val CHART_HEIGHT = 58.dp
private const val CHART_MARGIN_DB = 6f

/** Under this there is only the rounding of 24-bit samples. */
private const val CHART_FLOOR_DB = -150f
