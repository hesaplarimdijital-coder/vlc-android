package app.nagmedar.ui.player.subtitles.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.LocalClickModifiers
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.TrackClickModifiers
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.panel.PanelRule
import app.nagmedar.ui.player.panel.signed
import app.nagmedar.ui.shell.KeyCap
import app.nagmedar.ui.theme.NagTheme
import java.text.DecimalFormatSymbols
import java.util.Locale

/*
 * The line timing editor (S-708): a table of the file's lines (number, start, end, text) on a
 * wide window, a list on a phone; the fields of the line chosen, with the film's position as a
 * start or an end; split, merge, remove and shift. The lines with a problem are marked.
 */

/**
 * The editor's tab: the check of times and the steps back, the lines, and the fields of the one
 * chosen. [wide] windows (DeX, tablets) show a table and the fields side by side.
 */
@Composable
internal fun LinesTab(state: SubtitleToolsState, controller: SubtitleToolsController, document: SubtitleDocument, locale: Locale, wide: Boolean) {
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    TrackClickModifiers(Modifier.fillMaxSize()) {
        // on a narrow window the lines and the fields do not both fit: the list keeps a height of its own and the tab scrolls
        val body = if (wide) Modifier.fillMaxSize() else Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        Column(body.testTag("subtools-lines"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IssueBar(state, controller)
            CueList(state, controller, document.cues, separator, wide, if (wide) Modifier.weight(1f) else Modifier.height(COMPACT_LIST))
            PanelRule()
            CueFields(state, controller, document.cues.firstOrNull { it.id == state.focus }, separator, locale, wide)
        }
    }
}

/** What is wrong with the times (or that nothing is), the next problem, and the steps back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueBar(state: SubtitleToolsState, controller: SubtitleToolsController) {
    val colors = NagTheme.colors
    val issues = state.issues
    FlowRow(
        Modifier.fillMaxWidth().testTag("cue-issues"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val parts = listOfNotNull(
            pluralStringResource(R.plurals.nag_cue_errors, issues.errors, issues.errors).takeIf { issues.errors > 0 },
            pluralStringResource(R.plurals.nag_cue_overlaps, issues.overlaps, issues.overlaps).takeIf { issues.overlaps > 0 },
            pluralStringResource(R.plurals.nag_cue_empties, issues.empty, issues.empty).takeIf { issues.empty > 0 },
        )
        Text(
            if (parts.isEmpty()) stringResource(R.string.nag_cue_valid) else parts.joinToString(" · "),
            style = NagTheme.type.label,
            color = if (issues.errors > 0) colors.danger else if (parts.isEmpty()) colors.success else colors.warning,
            modifier = Modifier.heightIn(min = 32.dp).padding(end = 8.dp).testTag("cue-summary"),
        )
        NagButton(stringResource(R.string.nag_cue_next_issue), controller::focusIssue, enabled = !issues.none, modifier = Modifier.testTag("cue-next-issue"))
        NagButton(stringResource(R.string.nag_cue_undo), controller::undo, enabled = state.steps > 0, modifier = Modifier.testTag("cue-undo"))
        NagButton(stringResource(R.string.nag_cue_revert), controller::revert, enabled = state.steps > 0, modifier = Modifier.testTag("cue-revert"))
    }
}

// -------------------------------------------------------------------- the lines

@Composable
private fun CueList(
    state: SubtitleToolsState,
    controller: SubtitleToolsController,
    cues: List<TimedCue>,
    separator: Char,
    wide: Boolean,
    modifier: Modifier,
) {
    val colors = NagTheme.colors
    val listState = rememberLazyListState()
    ScrollToFocus(listState, cues, state.focus)
    // after a key the keyboard focus goes to the line chosen; after a click it stays where it is
    var followKeys by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        if (wide) {
            Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("#", style = NagTheme.type.caption, color = colors.textTertiary, modifier = Modifier.width(NUMBER_WIDTH))
                Text(stringResource(R.string.nag_cue_start), style = NagTheme.type.caption, color = colors.textTertiary, modifier = Modifier.width(TIME_WIDTH))
                Text(stringResource(R.string.nag_cue_end), style = NagTheme.type.caption, color = colors.textTertiary, modifier = Modifier.width(TIME_WIDTH))
                Text(stringResource(R.string.nag_cue_text), style = NagTheme.type.caption, color = colors.textTertiary, modifier = Modifier.weight(1f))
            }
            PanelRule()
        }
        if (cues.isEmpty()) {
            PanelCaption(stringResource(R.string.nag_cue_none), Modifier.padding(8.dp).testTag("cue-none"))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).onPreviewKeyEvent { event -> listKey(event, controller, cues).also { if (it) followKeys = true } }.testTag("cue-list"),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            itemsIndexed(cues, key = { _, cue -> cue.id }) { index, cue ->
                CueRow(
                    number = index + 1,
                    cue = cue,
                    issues = state.issues[cue.id],
                    selected = cue.id in state.selected || cue.id == state.focus && state.selected.isEmpty(),
                    playing = cue.id == state.playing,
                    separator = separator,
                    wide = wide,
                    takesFocus = followKeys && cue.id == state.focus,
                    onClick = { ctrl, shift ->
                        followKeys = false
                        controller.click(cue.id, ctrl, shift)
                    },
                    onOpen = {
                        controller.click(cue.id)
                        controller.seekToFocus()
                    },
                )
            }
        }
    }
}

private val COMPACT_LIST = 200.dp
private val NUMBER_WIDTH = 48.dp
private val TIME_WIDTH = 112.dp

/** The list is kept showing the chosen line when keys or "next problem" move it. */
@Composable
private fun ScrollToFocus(listState: LazyListState, cues: List<TimedCue>, focus: Long?) {
    LaunchedEffect(focus) {
        val index = cues.indexOfFirst { it.id == focus }
        if (index < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || index <= visible.first().index || index >= visible.last().index) listState.animateScrollToItem((index - 1).coerceAtLeast(0))
    }
}

/** Up, Down, Home, End and Ctrl+A in the list; Enter, on the row, takes the film to the line (the rows' double click). */
private fun listKey(event: KeyEvent, controller: SubtitleToolsController, cues: List<TimedCue>): Boolean {
    if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isMetaPressed) return false
    if (event.isCtrlPressed) {
        if (event.key != Key.A) return false
        controller.selectAll()
        return true
    }
    when (event.key) {
        Key.DirectionDown -> controller.step(forward = true)
        Key.DirectionUp -> controller.step(forward = false)
        Key.MoveHome -> cues.firstOrNull()?.let { controller.click(it.id) }
        Key.MoveEnd -> cues.lastOrNull()?.let { controller.click(it.id) }
        else -> return false
    }
    return true
}

@Composable
private fun CueRow(
    number: Int,
    cue: TimedCue,
    issues: Set<CueIssue>,
    selected: Boolean,
    playing: Boolean,
    separator: Char,
    wide: Boolean,
    takesFocus: Boolean,
    onClick: (ctrl: Boolean, shift: Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val requester = remember { FocusRequester() }
    LaunchedEffect(takesFocus) {
        if (takesFocus) runCatching { requester.requestFocus() }
    }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(6.dp)
    val clickModifiers = LocalClickModifiers.current
    val start = Clock.field(cue.startMs, separator)
    val end = Clock.field(cue.endMs, separator)
    val error = issues.any { it.error }
    val timeColor = when {
        selected -> colors.onSelection
        error -> colors.danger
        CueIssue.Overlap in issues -> colors.warning
        else -> colors.textSecondary
    }
    val textColor = if (selected) colors.onSelection else colors.text
    val resources = LocalContext.current.resources
    val problem = issues.joinToString("; ") { resources.getString(it.label()) }
    val said = stringResource(R.string.nag_cue_playing)
    val description = stringResource(R.string.nag_cue_row, number, start, end, oneLine(cue.text))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = NagTheme.dimens.rowHeight.coerceAtLeast(if (wide) 32.dp else 48.dp))
            .focusRequester(requester)
            .focusRing(state, shape)
            .background(if (playing && !selected) colors.accent.copy(alpha = 0.14f) else stateBackground(state, selected), shape)
            .nagClickable(interaction, { val (ctrl, shift) = clickModifiers.take(); onClick(ctrl, shift) }, role = Role.Button, onDoubleClick = onOpen)
            .semantics {
                contentDescription = description
                this.selected = selected
                val notes = listOfNotNull(problem.takeIf { it.isNotEmpty() }, said.takeIf { playing })
                if (notes.isNotEmpty()) stateDescription = notes.joinToString("; ")
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("cue-row-${cue.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (wide) {
            Row(Modifier.width(NUMBER_WIDTH), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (playing) Icon(NagIcons.Play, contentDescription = null, tint = if (selected) colors.onSelection else colors.accentText, modifier = Modifier.size(10.dp))
                Text(number.toString(), style = NagTheme.type.mono, color = if (selected) colors.onSelection else colors.textTertiary, maxLines = 1)
            }
            Text(start, style = NagTheme.type.mono, color = timeColor, maxLines = 1, modifier = Modifier.width(TIME_WIDTH))
            Text(end, style = NagTheme.type.mono, color = timeColor, maxLines = 1, modifier = Modifier.width(TIME_WIDTH))
            Text(oneLine(cue.text), style = NagTheme.type.label, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IssueMark(issues, selected)
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (playing) Icon(NagIcons.Play, contentDescription = null, tint = if (selected) colors.onSelection else colors.accentText, modifier = Modifier.size(10.dp))
                    Text("$number · $start → $end", style = NagTheme.type.mono, color = timeColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IssueMark(issues, selected)
                }
                Text(oneLine(cue.text), style = NagTheme.type.label, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A mark for a line with a problem, not by colour alone. */
@Composable
private fun IssueMark(issues: Set<CueIssue>, selected: Boolean) {
    val colors = NagTheme.colors
    when {
        issues.any { it.error } -> Icon(NagIcons.ErrorCircle, contentDescription = null, tint = if (selected) colors.onSelection else colors.danger, modifier = Modifier.size(14.dp))
        issues.isNotEmpty() -> Icon(NagIcons.Warning, contentDescription = null, tint = if (selected) colors.onSelection else colors.warning, modifier = Modifier.size(14.dp))
        else -> Unit
    }
}

private fun CueIssue.label(): Int = when (this) {
    CueIssue.EndNotAfterStart -> R.string.nag_cue_issue_end
    CueIssue.NegativeStart -> R.string.nag_cue_issue_negative
    CueIssue.Overlap -> R.string.nag_cue_issue_overlap
    CueIssue.Empty -> R.string.nag_cue_issue_empty
}

// -------------------------------------------------------------------- the fields

/** The chosen line's start, end and text, with the film's position as a start or an end; split, merge, remove, shift. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CueFields(state: SubtitleToolsState, controller: SubtitleToolsController, cue: TimedCue?, separator: Char, locale: Locale, wide: Boolean) {
    val resources = LocalContext.current.resources
    val playing = controller.playback != null
    Column(Modifier.fillMaxWidth().testTag("cue-fields"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (cue == null) {
            PanelCaption(stringResource(R.string.nag_cue_none))
            return@Column
        }
        key(cue.id) {
            val issues = state.issues[cue.id]
            val times = @Composable {
                TimeField(stringResource(R.string.nag_cue_start), cue.startMs, separator, issues.any { it.error }, "cue-start", Modifier.width(150.dp)) { controller.setStart(cue.id, it) }
                if (playing) {
                    NagButton(stringResource(R.string.nag_cue_set_start), controller::setStartHere, modifier = Modifier.testTag("cue-set-start"))
                    if (wide) ShortcutRegistry.chordLabel(ShortcutId.CueSetStart, resources)?.let { KeyCap(it) }
                }
                TimeField(stringResource(R.string.nag_cue_end), cue.endMs, separator, issues.any { it.error }, "cue-end", Modifier.width(150.dp)) { controller.setEnd(cue.id, it) }
                if (playing) {
                    NagButton(stringResource(R.string.nag_cue_set_end), controller::setEndHere, modifier = Modifier.testTag("cue-set-end"))
                    if (wide) ShortcutRegistry.chordLabel(ShortcutId.CueSetEnd, resources)?.let { KeyCap(it) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { times() }
            CueTextField(cue, controller)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NagButton(stringResource(R.string.nag_cue_split), controller::split, modifier = Modifier.testTag("cue-split"))
            NagButton(stringResource(R.string.nag_cue_merge), controller::merge, modifier = Modifier.testTag("cue-merge"))
            NagButton(stringResource(R.string.nag_cue_remove), controller::deleteSelected, modifier = Modifier.testTag("cue-remove"))
            PanelCaption(stringResource(R.string.nag_cue_shift), Modifier.padding(start = 8.dp, top = 12.dp))
            for (delta in SHIFTS_MS) {
                NagButton(shiftText(delta, locale), { controller.shiftSelected(delta) }, modifier = Modifier.testTag("cue-shift-$delta"))
            }
        }
        if (wide && playing) {
            PanelCaption(stringResource(R.string.nag_cue_hint_player))
        }
    }
}

private val SHIFTS_MS = listOf(-1_000L, -100L, 100L, 1_000L)

@Composable
private fun shiftText(ms: Long, locale: Locale): String =
    if (kotlin.math.abs(ms) >= 1_000) stringResource(R.string.nag_rate_seconds, signed(ms / 1_000, locale)) else stringResource(R.string.nag_sync_ms, signed(ms, locale))

/** A time in a field: typed to commit when it reads as a time, marked when it does not; follows the cue when a button or a step changes it. */
@Composable
private fun TimeField(label: String, value: Long, separator: Char, invalid: Boolean, tag: String, modifier: Modifier, onValue: (Long) -> Unit) {
    var text by remember { mutableStateOf(Clock.field(value, separator)) }
    LaunchedEffect(value) {
        if (Clock.parse(text) != value) text = Clock.field(value, separator)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PanelCaption(label)
        ToolField(
            value = text,
            onValueChange = { typed ->
                text = typed
                Clock.parse(typed)?.takeIf { it != value }?.let(onValue)
            },
            label = label,
            modifier = Modifier.fillMaxWidth().testTag(tag),
            mono = true,
            invalid = invalid || Clock.parse(text) == null,
        )
    }
}

/** The line's text, several lines; what is typed is the line's text at once. */
@Composable
private fun CueTextField(cue: TimedCue, controller: SubtitleToolsController) {
    var text by remember { mutableStateOf(cue.text) }
    LaunchedEffect(cue.text) {
        if (text != cue.text) text = cue.text
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PanelCaption(stringResource(R.string.nag_cue_text))
        ToolField(
            value = text,
            onValueChange = {
                text = it
                controller.setText(cue.id, it)
            },
            label = stringResource(R.string.nag_cue_text),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("cue-text"),
            singleLine = false,
        )
    }
}
