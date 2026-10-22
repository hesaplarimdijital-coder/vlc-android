package app.nagmedar.ui.tvlab.remux

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.lab.StreamType
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.NagTextField
import app.nagmedar.ui.components.ReorderState
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.rememberReorderState
import app.nagmedar.ui.components.reorderHandle
import app.nagmedar.ui.components.reorderItem
import app.nagmedar.ui.components.reorderList
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.tvlab.resources

/**
 * The planner in its dialog (S-1205): [onQueue] when the plan is to be queued, [onCancel] when it
 * is left. [confirm] names the main button ("Sıraya al…"; the batch's "Tamam", S-1210, whose
 * edits are the film's plan at once and so have no Cancel: [cancellable] false).
 */
@Composable
fun RemuxPlannerDialog(
    draft: PlanDraft,
    fileName: String,
    onQueue: () -> Unit,
    onCancel: () -> Unit,
    confirm: String? = null,
    cancellable: Boolean = true,
) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        RemuxPlannerSurface(draft, fileName, onQueue, onCancel, confirm = confirm, cancellable = cancellable)
    }
}

/** What the planner's list holds, in order: the suggestions, the container, the video, the tracks, the rest, the output, the summary. */
private sealed interface PlanRow {
    val key: Any

    data class Suggestion(val suggestion: PlanSuggestion, val index: Int) : PlanRow {
        override val key: Any get() = "suggestion-$index"
    }

    data class Heading(val text: String, override val key: Any) : PlanRow

    data object Container : PlanRow {
        override val key: Any get() = "container"
    }

    data object Video : PlanRow {
        override val key: Any get() = "video"
    }

    data class Track(val track: PlanTrack, val position: Int, val count: Int) : PlanRow {
        override val key: Any get() = track.ref.toString()
    }

    data object ExtraAudio : PlanRow {
        override val key: Any get() = "extra-audio"
    }

    data object More : PlanRow {
        override val key: Any get() = "more"
    }

    data object Output : PlanRow {
        override val key: Any get() = "output"
    }

    data object Summary : PlanRow {
        override val key: Any get() = "summary"
    }
}

/**
 * The planner (S-1205): what the device profile suggests, each with Apply or Undo; the
 * container; the video, always copied, with the Dolby Vision conversion and what it keeps; every
 * audio and subtitle track to keep or drop, its default and forced flags, language and title,
 * in an order set by dragging its handle, its arrows or Alt+↑ and Alt+↓; chapters, attachments
 * and tags; the output's name; and a summary. Every step's badge says what it keeps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemuxPlannerSurface(
    draft: PlanDraft,
    fileName: String,
    onQueue: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    confirm: String? = null,
    cancellable: Boolean = true,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    val res = resources()
    val audioHeading = stringResource(R.string.nag_tvlab_plan_audio)
    val subtitleHeading = stringResource(R.string.nag_tvlab_plan_subtitles)
    val suggestionsHeading = stringResource(R.string.nag_tvlab_plan_suggestions, draft.device)
    val rows = buildList {
        if (draft.suggestions.isNotEmpty()) {
            add(PlanRow.Heading(suggestionsHeading, "h-suggestions"))
            draft.suggestions.forEachIndexed { i, s -> add(PlanRow.Suggestion(s, i)) }
        }
        add(PlanRow.Container)
        add(PlanRow.Video)
        if (draft.audio.isNotEmpty()) {
            add(PlanRow.Heading(audioHeading, "h-audio"))
            draft.audio.forEachIndexed { i, t -> add(PlanRow.Track(t, i, draft.audio.size)) }
        }
        add(PlanRow.ExtraAudio)
        if (draft.subtitles.isNotEmpty()) {
            add(PlanRow.Heading(subtitleHeading, "h-subtitles"))
            draft.subtitles.forEachIndexed { i, t -> add(PlanRow.Track(t, i, draft.subtitles.size)) }
        }
        add(PlanRow.More)
        add(PlanRow.Output)
        add(PlanRow.Summary)
    }
    val list = rememberLazyListState()
    val reorder = rememberReorderState(list) { from, to ->
        // a track moves among its own kind: the list's other rows are its bounds
        val moved = rows.getOrNull(from) as? PlanRow.Track ?: return@rememberReorderState
        val type = moved.track.ref.type
        val first = rows.indexOfFirst { it is PlanRow.Track && it.track.ref.type == type }
        draft.move(type, moved.position, to - first)
    }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val narrow = maxWidth < 600.dp
        Column(
            Modifier
                .padding(if (narrow) 8.dp else dimens.gapL)
                .widthIn(max = 820.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                .padding(dimens.gapL)
                .testTag("tvlab-planner"),
        ) {
            Text(stringResource(R.string.nag_tvlab_plan_title), style = NagTheme.type.title, color = colors.text, modifier = Modifier.semantics { heading() })
            Text(fileName, style = NagTheme.type.mono.copy(fontSize = 12.sp), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.nag_tvlab_plan_note), style = NagTheme.type.labelSmall, color = colors.textTertiary)
            Spacer(Modifier.padding(top = dimens.gapS))
            HorizontalRule()
            LazyColumn(
                Modifier.weight(1f, fill = false).fillMaxWidth().reorderList(reorder),
                state = list,
                contentPadding = PaddingValues(vertical = dimens.gapM),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    when (row) {
                        is PlanRow.Heading -> Text(
                            row.text,
                            style = NagTheme.type.bodyStrong.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                            modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                        )
                        is PlanRow.Suggestion -> SuggestionRow(draft, row.suggestion, res)
                        PlanRow.Container -> Step(
                            stringResource(R.string.nag_tvlab_container),
                            Fidelity.Lossless,
                            stringResource(R.string.nag_tvlab_plan_container_text),
                        )
                        PlanRow.Video -> VideoStep(draft)
                        is PlanRow.Track -> TrackRow(draft, row, reorder, index, narrow)
                        PlanRow.ExtraAudio -> ExtraAudio()
                        PlanRow.More -> MoreStep(draft)
                        PlanRow.Output -> OutputStep(draft)
                        PlanRow.Summary -> SummaryStep(draft)
                    }
                }
            }
            HorizontalRule()
            Spacer(Modifier.padding(top = dimens.gapM))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cancellable) NagButton(stringResource(R.string.nag_sc_cancel), onCancel)
                NagButton(
                    confirm ?: stringResource(R.string.nag_tvlab_plan_queue),
                    onQueue,
                    primary = true,
                    // the batch's plan is checked when it is queued
                    enabled = draft.valid || !cancellable,
                    modifier = Modifier.testTag("tvlab-planner-queue"),
                )
            }
        }
    }
}

/** What a step keeps, as a badge. */
@Composable
fun FidelityBadge(fidelity: Fidelity) {
    ToneBadge(fidelityLabel(resources(), fidelity), fidelity.tone())
}

fun Fidelity.tone(): StateTone = when (this) {
    Fidelity.Lossless -> StateTone.Success
    Fidelity.LosslessMetadata -> StateTone.Info
    Fidelity.Lossy -> StateTone.Warning
}

/** "Kayıpsız", "Kayıpsız (üst veri değişir)", "Kayıplı". */
fun fidelityLabel(res: Resources, fidelity: Fidelity): String = res.getString(
    when (fidelity) {
        Fidelity.Lossless -> R.string.nag_tvlab_plan_lossless
        Fidelity.LosslessMetadata -> R.string.nag_tvlab_plan_lossless_tags
        Fidelity.Lossy -> R.string.nag_tvlab_plan_lossy
    },
)

/** A step of the plan: its name and badge over a line saying what it does. */
@Composable
private fun Step(title: String, fidelity: Fidelity, text: String?, content: @Composable () -> Unit = {}) {
    PlanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = NagTheme.type.bodyStrong, color = NagTheme.colors.text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FidelityBadge(fidelity)
        }
        if (text != null) Text(text, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
        content()
    }
}

@Composable
private fun PlanCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** A suggestion of the device profile, applied or not, with the button that changes that. */
@Composable
private fun SuggestionRow(draft: PlanDraft, suggestion: PlanSuggestion, res: Resources) {
    val colors = NagTheme.colors
    val applied = draft.applied(suggestion)
    val tone = if (applied) StateTone.Success else StateTone.Warning
    PlanCard(Modifier.testTag("tvlab-planner-suggestion")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (applied) NagIcons.Check else NagIcons.Warning, contentDescription = null, tint = toneColors(tone).content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(suggestionText(res, draft, suggestion), style = NagTheme.type.label, color = colors.text, modifier = Modifier.weight(1f))
            if (draft.actionable(suggestion)) {
                Spacer(Modifier.width(8.dp))
                NagButton(
                    stringResource(if (applied) R.string.nag_tvlab_plan_undo else R.string.nag_tvlab_plan_apply),
                    { draft.apply(suggestion, !applied) },
                )
            }
        }
    }
}

/** A suggestion in words. */
fun suggestionText(res: Resources, draft: PlanDraft, suggestion: PlanSuggestion): String = when (suggestion.kind) {
    SuggestionKind.ConvertDolbyVision -> res.getString(R.string.nag_tvlab_plan_s_dv, suggestion.subject, draft.device)
    SuggestionKind.DefaultAudio -> res.getString(R.string.nag_tvlab_plan_s_default, suggestion.subject, draft.device, suggestion.target)
    SuggestionKind.NoPlayableAudio -> res.getString(R.string.nag_tvlab_plan_s_none, suggestion.subject, draft.device)
    SuggestionKind.DropSubtitles -> res.getString(R.string.nag_tvlab_plan_s_subs, suggestion.subject, draft.device)
}

/** The video: always copied; the Dolby Vision conversion with what it keeps, when the film is profile 7. */
@Composable
private fun VideoStep(draft: PlanDraft) {
    val colors = NagTheme.colors
    Step(stringResource(R.string.nag_tech_video) + " · " + draft.videoLabel, draft.videoFidelity, stringResource(R.string.nag_tvlab_plan_video_text)) {
        if (draft.canConvert) {
            NagSwitch(
                draft.convertDolbyVision,
                { draft.convertDolbyVision = it },
                stringResource(R.string.nag_tvlab_plan_dv_switch),
                modifier = Modifier.testTag("tvlab-planner-dv"),
            )
            Text(stringResource(R.string.nag_tvlab_plan_dv_text), style = NagTheme.type.labelSmall, color = colors.textSecondary)
            Text(
                stringResource(if (draft.fullEnhancement) R.string.nag_tvlab_plan_dv_fel else R.string.nag_tvlab_plan_dv_mel),
                style = NagTheme.type.labelSmall,
                color = if (draft.fullEnhancement && draft.convertDolbyVision) toneColors(StateTone.Warning).content else colors.textSecondary,
            )
        }
    }
}

/**
 * A track: its handle, kept or not with its name, its badge and its arrows; when kept, its
 * default and forced flags, language and title. Alt+↑ and Alt+↓ move it with the keyboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackRow(draft: PlanDraft, row: PlanRow.Track, reorder: ReorderState, listIndex: Int, narrow: Boolean) {
    val res = resources()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val track = row.track
    val type = track.ref.type
    val up = res.getString(R.string.nag_queue_move_up)
    val down = res.getString(R.string.nag_queue_move_down)
    val moveUp = { draft.move(type, row.position, row.position - 1) }
    val moveDown = { draft.move(type, row.position, row.position + 1) }
    val fidelity = draft.fidelity(track)
    PlanCard(
        Modifier
            .reorderItem(reorder, row.key, listIndex)
            .background(colors.surfaceRaised)
            .semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(up) { moveUp(); true }.takeIf { row.position > 0 },
                    CustomAccessibilityAction(down) { moveDown(); true }.takeIf { row.position < row.count - 1 },
                )
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !event.isAltPressed || event.isCtrlPressed) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> moveUp()
                    Key.DirectionDown -> moveDown()
                    else -> return@onKeyEvent false
                }
                true
            }
            .testTag("tvlab-planner-track"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val handle = res.getString(R.string.nag_queue_handle, track.source.label)
            Box(
                Modifier
                    .size(dimens.minTarget.coerceAtLeast(36.dp))
                    .reorderHandle(reorder, row.key)
                    .semantics { contentDescription = handle },
                contentAlignment = Alignment.Center,
            ) {
                Icon(NagIcons.Menu, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
            }
            NagCheckbox(track.kept, { track.kept = it }, track.source.label, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FidelityBadge(fidelity)
            NagIconButton(NagIcons.ChevronUp, up, moveUp, enabled = row.position > 0)
            NagIconButton(NagIcons.ChevronDown, down, moveDown, enabled = row.position < row.count - 1)
        }
        if (!track.source.playable && draft.device.isNotEmpty()) {
            Text(
                res.getString(if (type == StreamType.Audio) R.string.nag_tvlab_plan_not_played else R.string.nag_tvlab_plan_not_shown, draft.device),
                style = NagTheme.type.labelSmall,
                color = toneColors(StateTone.Warning).content,
            )
        }
        if (!track.kept) {
            Text(stringResource(R.string.nag_tvlab_plan_dropped), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NagCheckbox(track.isDefault, { draft.setDefault(track, it) }, stringResource(R.string.nag_tvlab_plan_default))
                NagCheckbox(track.forced, { track.forced = it }, stringResource(R.string.nag_tvlab_plan_forced))
                Box(Modifier.width(if (narrow) 132.dp else 150.dp)) {
                    NagTextField(track.language, { track.language = it.take(3) }, stringResource(R.string.nag_tvlab_plan_language), placeholder = track.source.language.ifEmpty { null })
                }
                Box(Modifier.width(if (narrow) 240.dp else 280.dp)) {
                    NagTextField(track.title, { track.title = it }, stringResource(R.string.nag_tvlab_plan_title_field))
                }
            }
            if (!track.languageValid) {
                Text(stringResource(R.string.nag_tvlab_plan_language_error), style = NagTheme.type.labelSmall, color = toneColors(StateTone.Error).content)
            }
        }
    }
}

/** Where the compatibility track of S-1207 is added: not in this version, said so. */
@Composable
private fun ExtraAudio() {
    PlanCard(Modifier.testTag("tvlab-planner-extra-audio")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NagButton(stringResource(R.string.nag_tvlab_plan_extra_audio), {}, icon = NagIcons.Plus, enabled = false)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.nag_tvlab_plan_extra_audio_text), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        }
    }
}

/** Chapters, attachments and tags: on or off, each with its badge. */
@Composable
private fun MoreStep(draft: PlanDraft) {
    PlanCard {
        Text(stringResource(R.string.nag_tvlab_plan_more), style = NagTheme.type.bodyStrong, color = NagTheme.colors.text)
        if (draft.tech.chapters > 0) {
            Toggle(
                pluralStringResource(R.plurals.nag_tvlab_chapters, draft.tech.chapters, draft.tech.chapters),
                draft.chapters,
            ) { draft.chapters = it }
        }
        Toggle(stringResource(R.string.nag_tvlab_plan_attachments), draft.attachments) { draft.attachments = it }
        Toggle(stringResource(R.string.nag_tvlab_plan_tags), draft.metadata) { draft.metadata = it }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NagSwitch(on, onChange, label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        FidelityBadge(if (on) Fidelity.Lossless else Fidelity.Lossy)
    }
}

/** The output's name; where it goes, the system's picker asks. */
@Composable
private fun OutputStep(draft: PlanDraft) {
    PlanCard {
        Text(stringResource(R.string.nag_tvlab_plan_output), style = NagTheme.type.bodyStrong, color = NagTheme.colors.text)
        NagTextField(draft.outputName, { draft.outputName = it }, stringResource(R.string.nag_tvlab_plan_output_name), modifier = Modifier.testTag("tvlab-planner-name"))
        Text(stringResource(R.string.nag_tvlab_plan_output_text), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
    }
}

/** The plan in a few lines: what the output holds, what is dropped, how many steps keep everything. */
@Composable
private fun SummaryStep(draft: PlanDraft) {
    val res = resources()
    val colors = NagTheme.colors
    val summary = draft.summary()
    PlanCard(Modifier.testTag("tvlab-planner-summary")) {
        Text(stringResource(R.string.nag_tvlab_plan_summary), style = NagTheme.type.bodyStrong, color = colors.text)
        summaryLines(res, draft, summary).forEach { line -> Text(line, style = NagTheme.type.label, color = colors.textSecondary) }
        if (summary.lossy > 0) {
            Text(stringResource(R.string.nag_tvlab_plan_lossy_note), style = NagTheme.type.labelSmall, color = toneColors(StateTone.Warning).content)
        }
    }
}

/** The summary's lines, as the planner and the batch's per-film plan say them. */
fun summaryLines(res: Resources, draft: PlanDraft, summary: PlanSummary = draft.summary()): List<String> = listOfNotNull(
    res.getString(
        R.string.nag_tvlab_plan_sum_streams,
        res.getQuantityString(R.plurals.nag_tvlab_plan_audio_count, summary.audioKept, summary.audioKept),
        res.getQuantityString(R.plurals.nag_tvlab_plan_subtitle_count, summary.subtitlesKept, summary.subtitlesKept),
    ),
    listOfNotNull(
        summary.audioDropped.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.nag_tvlab_plan_audio_count, it, it) },
        summary.subtitlesDropped.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.nag_tvlab_plan_subtitle_count, it, it) },
    ).takeIf { it.isNotEmpty() }?.let { res.getString(R.string.nag_tvlab_plan_sum_dropped, it.joinToString(", ")) },
    res.getString(R.string.nag_tvlab_remux_plan_dv).takeIf { draft.convertDolbyVision && draft.canConvert },
    res.getString(R.string.nag_tvlab_plan_sum_steps, summary.lossless, summary.metadataChanged, summary.lossy),
    res.getString(R.string.nag_tvlab_plan_sum_output, draft.outputFileName).takeIf { draft.outputNameValid },
)
