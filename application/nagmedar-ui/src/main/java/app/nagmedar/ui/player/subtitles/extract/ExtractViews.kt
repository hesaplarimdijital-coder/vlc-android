package app.nagmedar.ui.player.subtitles.extract

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.panel.PanelChip
import app.nagmedar.ui.player.subtitles.languageOf
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * The film's embedded subtitles in the tools' window: its subtitle tracks (language, title,
 * format), one tapped is written into a new file; how far the job is, with Cancel; where the
 * file went (and "Save elsewhere…" when it is in the app's folder), or why there is none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmbeddedSection(controller: SubtitleExtractController) {
    val state = controller.state
    if (state.film == null) return
    // nothing to say of a film without tracks unless the window was opened for them
    if (!state.asked && state.tracks.isEmpty() && state.run == ExtractRun.Idle) return
    val colors = NagTheme.colors
    val locale = currentLocale()
    Column(Modifier.fillMaxWidth().testTag("subtools-embedded"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.nag_extract_section), style = NagTheme.type.label, color = colors.textSecondary)
        when {
            !state.available -> PanelCaption(stringResource(R.string.nag_extract_no_native), Modifier.testTag("embedded-status"))
            state.loading -> PanelCaption(stringResource(R.string.nag_extract_loading), Modifier.testTag("embedded-status"))
            state.tracks.isEmpty() -> PanelCaption(stringResource(R.string.nag_extract_none), Modifier.testTag("embedded-status"))
            else -> {
                PanelCaption(stringResource(R.string.nag_extract_hint))
                val running = (state.run as? ExtractRun.Running)?.track ?: (state.run as? ExtractRun.Starting)?.track
                FlowRow(
                    Modifier.fillMaxWidth().testTag("embedded-tracks"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.tracks.forEach { track ->
                        PanelChip(trackLabel(track, locale), selected = running?.index == track.index, enabled = !state.running) { controller.extract(track) }
                    }
                }
            }
        }
        RunLine(state, controller, locale)
    }
}

/** How the extraction goes, or what came of it. */
@Composable
private fun RunLine(state: ExtractState, controller: SubtitleExtractController, locale: Locale) {
    val colors = NagTheme.colors
    val context = LocalContext.current
    val tag = Modifier.testTag("embedded-run")
    when (val run = state.run) {
        ExtractRun.Idle -> Unit
        is ExtractRun.Starting, is ExtractRun.Running -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val track = (run as? ExtractRun.Running)?.track ?: (run as? ExtractRun.Starting)?.track
            val fraction = (run as? ExtractRun.Running)?.fraction
            val text = when {
                fraction == null -> stringResource(R.string.nag_extract_waiting)
                track == null -> stringResource(R.string.nag_extract_running_other, percent(fraction, locale))
                else -> stringResource(R.string.nag_extract_running, trackLabel(track, locale), percent(fraction, locale))
            }
            Text(text, style = NagTheme.type.label, color = colors.text, modifier = tag.weight(1f, fill = false))
            NagButton(stringResource(R.string.nag_extract_cancel), controller::cancel, modifier = Modifier.testTag("embedded-cancel"))
        }
        is ExtractRun.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val lines = pluralStringResource(R.plurals.nag_subtools_lines, run.lines, NumberFormat.getIntegerInstance(locale).format(run.lines))
            val where = stringResource(if (run.saved.beside) R.string.nag_extract_done_beside else R.string.nag_extract_done_app, run.saved.name)
            Text("$where · $lines", style = NagTheme.type.label, color = colors.success, modifier = tag)
            if (run.partial) Text(stringResource(R.string.nag_extract_partial), style = NagTheme.type.labelSmall, color = colors.warning)
            if (!run.saved.beside) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NagButton(stringResource(R.string.nag_extract_save_elsewhere), controller::saveElsewhere, modifier = Modifier.testTag("embedded-save-elsewhere"))
                    val copied = state.copied
                    val note = when {
                        copied != null -> stringResource(R.string.nag_extract_saved_elsewhere, copied.name) to colors.success
                        state.copyProblem == CopyOutcome.SameFile -> stringResource(R.string.nag_save_same_file) to colors.danger
                        state.copyProblem == CopyOutcome.Failed -> stringResource(R.string.nag_save_failed) to colors.danger
                        else -> null
                    }
                    if (note != null) Text(note.first, style = NagTheme.type.labelSmall, color = note.second, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }
        is ExtractRun.Failed -> Text(
            SubtitleExtractWorker.failureMessage(context, run.failure, run.track),
            style = NagTheme.type.label,
            color = colors.warning,
            modifier = tag,
        )
        ExtractRun.Cancelled -> PanelCaption(stringResource(R.string.nag_extract_cancelled), tag)
        ExtractRun.Busy -> Text(stringResource(R.string.nag_extract_busy), style = NagTheme.type.label, color = colors.warning, modifier = tag)
    }
}

/** "Turkish · SRT · Full", "English · PGS · picture": a track as its chip says it. */
@Composable
internal fun trackLabel(track: EmbeddedTrack, locale: Locale): String {
    val tag = track.language.trim().lowercase(Locale.ROOT)
    val code = languageOf(tag) ?: tag
    val language = code.takeIf { it.isNotEmpty() && it !in setOf("und", "zxx", "mul", "mis") }
        ?.let { Locale(it).getDisplayLanguage(locale).takeIf { name -> name.isNotEmpty() && name != it } ?: tag.uppercase(Locale.ROOT) }
        ?.replaceFirstChar { it.titlecase(locale) }
        ?: stringResource(R.string.nag_extract_untagged)
    val parts = listOfNotNull(
        language,
        track.codecLabel,
        track.title.trim().takeIf { it.isNotEmpty() },
        stringResource(R.string.nag_extract_forced).takeIf { track.forced },
        stringResource(R.string.nag_extract_picture).takeIf { track.picture },
    )
    return parts.joinToString(" · ")
}

/** "%45" in Turkish, "45%" in English. */
private fun percent(fraction: Float, locale: Locale): String = NumberFormat.getPercentInstance(locale).format(fraction.coerceIn(0f, 1f).toDouble())
