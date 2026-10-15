package app.nagmedar.ui.player.subtitles.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.player.panel.ChoiceRow
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.panel.PanelChip
import app.nagmedar.ui.player.panel.PanelDisabledAlpha
import app.nagmedar.ui.player.panel.PanelRule
import app.nagmedar.ui.player.panel.decimal
import app.nagmedar.ui.player.panel.signed
import app.nagmedar.ui.player.subtitles.SubtitleEncoding
import app.nagmedar.ui.shell.KeyCap
import app.nagmedar.ui.theme.NagTheme
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/**
 * The subtitle tools in a window over the video or the library (S-708): [SubtitleToolsSurface]
 * with the system's pickers for the file to open and, where the app may not write beside the
 * original, for the new file's place.
 */
@Composable
fun SubtitleToolsDialog(controller: SubtitleToolsController, onOpenFile: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) { SubtitleToolsSurface(controller, onOpenFile, onDismiss) }
    }
}

/**
 * The window itself: the file, its tools as tabs (cleaning, frame rate, lines) and, at the foot,
 * the new file's name with Save. Esc closes; the line editor's keys (save, split, merge, set the
 * start and the end from the film, next and previous line) are the shortcut table's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitleToolsSurface(controller: SubtitleToolsController, onOpenFile: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val state = controller.state ?: return
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val locale = currentLocale()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // the keys are read above everything in the window: Esc and the editor's shortcuts work wherever the focus is
    BoxWithConstraints(modifier.fillMaxSize().onPreviewKeyEvent { event -> toolsKey(event, controller, onDismiss) }, contentAlignment = Alignment.Center) {
        val wide = maxWidth >= WIDE_AT
        val width = (maxWidth - 32.dp).coerceAtMost(CARD_WIDTH)
        val height = (maxHeight - 32.dp).coerceAtMost(CARD_HEIGHT)
        Column(
            Modifier
                .width(width)
                .height(height)
                .shadow(24.dp, shape)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                // taps inside stay inside
                .pointerInput(Unit) { detectTapGestures { } }
                .focusRequester(focus)
                .focusable()
                .padding(16.dp)
                .testTag("subtools"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nag_subtools_title), style = NagTheme.type.title, color = colors.text, modifier = Modifier.weight(1f))
                KeyCap("Esc")
            }
            FileBar(state, controller, onOpenFile)
            val document = state.document
            when {
                state.loading -> PanelCaption(stringResource(R.string.nag_subtools_loading), Modifier.testTag("subtools-status"))
                state.problem != null -> Text(
                    stringResource(if (state.problem == ToolsProblem.Unsupported) R.string.nag_subtools_unsupported else R.string.nag_subtools_unreadable),
                    style = NagTheme.type.label,
                    color = colors.warning,
                    modifier = Modifier.testTag("subtools-status"),
                )
                document == null -> PanelCaption(stringResource(R.string.nag_subtools_none), Modifier.testTag("subtools-status"))
            }
            if (document != null) {
                PanelCaption(fileInfo(state, document, locale), Modifier.testTag("subtools-info"))
                SegmentedChoice(
                    options = listOf(
                        ToolsTab.Clean to stringResource(R.string.nag_subtools_tab_clean),
                        ToolsTab.FrameRate to stringResource(R.string.nag_subtools_tab_rate),
                        ToolsTab.Edit to stringResource(R.string.nag_subtools_tab_lines),
                    ),
                    selected = state.tab,
                    onSelect = controller::setTab,
                    fill = true,
                    modifier = Modifier.fillMaxWidth().testTag("subtools-tabs"),
                )
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.tab) {
                        ToolsTab.Clean -> CleanTab(state, controller, locale, wide)
                        ToolsTab.FrameRate -> RateTab(state, controller, locale)
                        ToolsTab.Edit -> LinesTab(state, controller, document, locale, wide)
                    }
                }
                PanelRule()
                SaveBar(state, controller, locale)
            } else {
                Column(Modifier.weight(1f)) {}
            }
        }
    }
}

/** The subtitles' tab's entry to the tools (S-708): a button and a line on what they do. */
@Composable
fun SubtitleToolsSection(onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("subtitles-tools"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NagButton(
            stringResource(R.string.nag_subtools_menu),
            onOpen,
            modifier = Modifier.fillMaxWidth().testTag("tools-open"),
            icon = NagIcons.Subtitle,
        )
        PanelCaption(stringResource(R.string.nag_subtools_section_hint))
    }
}

private val WIDE_AT = 720.dp
private val CARD_WIDTH = 1000.dp
private val CARD_HEIGHT = 780.dp

/** Esc closes; the editor's shortcuts act; everything else is the focused control's. */
private fun toolsKey(event: KeyEvent, controller: SubtitleToolsController, onDismiss: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key == Key.Escape) {
        onDismiss()
        return true
    }
    val shortcut = ShortcutRegistry.match(event, setOf(ShortcutScope.Player)) ?: return false
    return controller.perform(shortcut.id)
}

/** "SRT · UTF-8 · 1.204 lines". */
@Composable
private fun fileInfo(state: SubtitleToolsState, document: SubtitleDocument, locale: Locale): String {
    val count = document.cues.size
    return stringResource(
        R.string.nag_subtools_info,
        document.format.label,
        state.encoding?.let { stringResource(it.label) } ?: stringResource(SubtitleEncoding.Utf8.label),
        pluralStringResource(R.plurals.nag_subtools_lines, count, NumberFormat.getIntegerInstance(locale).format(count)),
    )
}

/** The files to choose from, and the system's picker for another. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileBar(state: SubtitleToolsState, controller: SubtitleToolsController, onOpenFile: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().testTag("subtools-files"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.files.forEach { file ->
            PanelChip(file.name, selected = file.uri == state.file?.uri, enabled = true) { controller.choose(file) }
        }
        NagButton(stringResource(R.string.nag_subtools_open_file), onOpenFile, icon = NagIcons.Folder, modifier = Modifier.testTag("subtools-open"))
    }
}

// -------------------------------------------------------------------- cleaning

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CleanTab(state: SubtitleToolsState, controller: SubtitleToolsController, locale: Locale, wide: Boolean) {
    val colors = NagTheme.colors
    val options = state.sdh
    val result = state.sdhResult
    val number = NumberFormat.getIntegerInstance(locale)
    Column(Modifier.fillMaxSize().testTag("subtools-clean"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelCaption(stringResource(R.string.nag_sdh_hint))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val box = Modifier.then(if (wide) Modifier.width(470.dp) else Modifier.fillMaxWidth())
            NagCheckbox(options.brackets, { controller.setSdh(options.copy(brackets = it)) }, stringResource(R.string.nag_sdh_brackets), box.testTag("sdh-brackets"))
            NagCheckbox(options.parentheses, { controller.setSdh(options.copy(parentheses = it)) }, stringResource(R.string.nag_sdh_parentheses), box.testTag("sdh-parentheses"))
            NagCheckbox(options.speakerLabels, { controller.setSdh(options.copy(speakerLabels = it)) }, stringResource(R.string.nag_sdh_speakers), box.testTag("sdh-speakers"))
            NagCheckbox(options.namedSpeakers, { controller.setSdh(options.copy(namedSpeakers = it)) }, stringResource(R.string.nag_sdh_named), box.testTag("sdh-named"))
            NagCheckbox(options.music, { controller.setSdh(options.copy(music = it)) }, stringResource(R.string.nag_sdh_music), box.testTag("sdh-music"))
            NagCheckbox(options.fontTags, { controller.setSdh(options.copy(fontTags = it)) }, stringResource(R.string.nag_sdh_font), box.testTag("sdh-font"))
        }
        PanelRule()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val text = when {
                result == null || result.changes.isEmpty() -> stringResource(R.string.nag_sdh_nothing)
                else -> stringResource(R.string.nag_sdh_summary, number.format(result.before), number.format(result.after)) +
                    " · " + stringResource(R.string.nag_sdh_counts, number.format(result.removed), number.format(result.edited))
            }
            Text(text, style = NagTheme.type.label, color = colors.text, modifier = Modifier.weight(1f).testTag("sdh-summary"))
            NagButton(
                stringResource(R.string.nag_subtools_apply),
                controller::applySdh,
                primary = true,
                enabled = result?.changes?.isNotEmpty() == true,
                modifier = Modifier.testTag("sdh-apply"),
            )
        }
        val changes = result?.changes.orEmpty()
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("sdh-changes"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(changes.take(MAX_SHOWN_CHANGES), key = { it.cueId }) { change -> ChangeRow(change, locale) }
            if (changes.size > MAX_SHOWN_CHANGES) {
                item { PanelCaption(stringResource(R.string.nag_sdh_more, number.format(changes.size - MAX_SHOWN_CHANGES))) }
            }
        }
    }
}

private const val MAX_SHOWN_CHANGES = 300

/** One changed cue: its number and time, its text struck through, and what it became or that it goes. */
@Composable
private fun ChangeRow(change: CueChange, locale: Locale) {
    val colors = NagTheme.colors
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    Row(
        Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(vertical = 3.dp).testTag("sdh-change-${change.cueId}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.width(92.dp)) {
            Text("#" + change.number, style = NagTheme.type.mono, color = colors.textTertiary)
            Text(Clock.field(change.startMs, separator).substringBefore(separator), style = NagTheme.type.mono, color = colors.textTertiary)
        }
        Column(Modifier.weight(1f)) {
            Text(
                oneLine(change.before),
                style = NagTheme.type.label,
                color = colors.textTertiary,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                change.after?.let(::oneLine) ?: stringResource(R.string.nag_sdh_removed),
                style = NagTheme.type.label,
                color = if (change.after == null) colors.danger else colors.success,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A text on one line, its line breaks marked. */
internal fun oneLine(text: String): String = text.replace("\n", " ↵ ")

// -------------------------------------------------------------------- frame rate

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RateTab(state: SubtitleToolsState, controller: SubtitleToolsController, locale: Locale) {
    val colors = NagTheme.colors
    val preview = state.ratePreview
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("subtools-rate"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCaption(stringResource(R.string.nag_rate_hint))
        RateRow(stringResource(R.string.nag_rate_from), state.from, locale, "rate-from") { controller.setRates(it, state.to) }
        RateRow(stringResource(R.string.nag_rate_to), state.to, locale, "rate-to") { controller.setRates(state.from, it) }
        PanelRule()
        Text(stringResource(R.string.nag_rate_shift), style = NagTheme.type.label, color = colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (step in SHIFT_STEPS_MS) {
                NagButton(shiftLabel(-step, locale), { controller.setShift(state.shiftMs - step) }, modifier = Modifier.testTag("shift-minus-$step"))
            }
            ToolField(
                value = state.shiftMs.toString(),
                onValueChange = { text -> text.replace('−', '-').trim().toLongOrNull()?.let(controller::setShift) },
                label = stringResource(R.string.nag_rate_shift),
                modifier = Modifier.width(110.dp).testTag("rate-shift"),
                mono = true,
                numbers = true,
            )
            Text(stringResource(R.string.nag_unit_ms), style = NagTheme.type.label, color = colors.textSecondary)
            for (step in SHIFT_STEPS_MS.reversed()) {
                NagButton(shiftLabel(step, locale), { controller.setShift(state.shiftMs + step) }, modifier = Modifier.testTag("shift-plus-$step"))
            }
            NagButton(stringResource(R.string.nag_rate_shift_zero), { controller.setShift(0) }, enabled = state.shiftMs != 0L, modifier = Modifier.testTag("shift-zero"))
        }
        PanelRule()
        if (preview != null) {
            val same = state.from == state.to && state.shiftMs == 0L
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("rate-preview")) {
                PreviewLine(stringResource(R.string.nag_rate_first), Clock.field(preview.firstBefore, separator), Clock.field(preview.firstAfter, separator))
                PreviewLine(stringResource(R.string.nag_rate_last), Clock.field(preview.lastBefore, separator), Clock.field(preview.lastAfter, separator))
                PanelCaption(stringResource(R.string.nag_rate_factor, decimal(preview.factor, 5, locale)))
                if (preview.dropped > 0) {
                    Text(
                        pluralStringResource(R.plurals.nag_rate_dropped, preview.dropped, preview.dropped),
                        style = NagTheme.type.labelSmall,
                        color = colors.warning,
                        modifier = Modifier.testTag("rate-dropped"),
                    )
                }
                if (same) PanelCaption(stringResource(R.string.nag_rate_same))
            }
        }
        Row {
            NagButton(
                stringResource(R.string.nag_subtools_apply),
                controller::applyRates,
                primary = true,
                enabled = state.from != state.to || state.shiftMs != 0L,
                modifier = Modifier.testTag("rate-apply"),
            )
        }
    }
}

private val SHIFT_STEPS_MS = listOf(1_000L, 100L)

/** "−1 s", "+100 ms". */
@Composable
private fun shiftLabel(ms: Long, locale: Locale): String =
    if (kotlin.math.abs(ms) >= 1_000) stringResource(R.string.nag_rate_seconds, signed(ms / 1_000, locale)) else stringResource(R.string.nag_sync_ms, signed(ms, locale))

@Composable
private fun RateRow(label: String, selected: FrameRate, locale: Locale, tag: String, onSelect: (FrameRate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = NagTheme.type.label, color = NagTheme.colors.textSecondary)
        ChoiceRow(
            options = FrameRate.entries.map { it to rateLabel(it, locale) },
            selected = selected,
            enabled = true,
            onSelect = onSelect,
            modifier = Modifier.testTag(tag),
        )
    }
}

/** "23,976", "24": a frame rate as a number of the interface's language. */
internal fun rateLabel(rate: FrameRate, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 }.format(rate.fps)

/** A time before and after: "0:00:01,000 → 0:00:01,043". */
@Composable
private fun PreviewLine(label: String, before: String, after: String) {
    val colors = NagTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NagTheme.type.label, color = colors.textSecondary, modifier = Modifier.width(150.dp))
        Text(before, style = NagTheme.type.mono, color = colors.textTertiary)
        Text("→", style = NagTheme.type.mono, color = colors.textTertiary)
        Text(after, style = NagTheme.type.mono, color = colors.text)
    }
}

// -------------------------------------------------------------------- saving

/**
 * The new file: its name (edited as wanted), the format of an SRT or a WebVTT, the byte order
 * mark, loading it into the film; Save; and where it went or why not. The original is not touched.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaveBar(state: SubtitleToolsState, controller: SubtitleToolsController, locale: Locale) {
    val colors = NagTheme.colors
    val document = state.document ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("subtools-save-bar")) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolField(
                value = state.name,
                onValueChange = controller::setName,
                label = stringResource(R.string.nag_save_name),
                modifier = Modifier.width(320.dp).testTag("save-name"),
            )
            if (!document.format.styled) {
                SegmentedChoice(
                    options = listOf(SubtitleFormat.Srt to SubtitleFormat.Srt.label, SubtitleFormat.Vtt to SubtitleFormat.Vtt.label),
                    selected = state.format ?: document.format,
                    onSelect = controller::setFormat,
                    modifier = Modifier.testTag("save-format"),
                )
            }
            NagButton(
                stringResource(if (state.saving) R.string.nag_save_saving else R.string.nag_save_button),
                controller::save,
                primary = true,
                enabled = !state.saving && state.issues.saveable && state.name.isNotBlank(),
                modifier = Modifier.testTag("save-button"),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            NagCheckbox(state.bom, controller::setBom, stringResource(R.string.nag_save_bom), Modifier.testTag("save-bom"))
            if (controller.playback != null) {
                NagCheckbox(state.loadAfter, controller::setLoadAfter, stringResource(R.string.nag_save_load), Modifier.testTag("save-load"))
            }
        }
        val status = when {
            !state.issues.saveable -> stringResource(R.string.nag_save_blocked) to colors.warning
            state.saveFailed -> stringResource(R.string.nag_save_failed) to colors.danger
            state.saved != null -> {
                val saved = state.saved
                val where = stringResource(if (saved.beside) R.string.nag_save_saved_beside else R.string.nag_save_saved_elsewhere, saved.name)
                (if (state.loaded) where + " · " + stringResource(R.string.nag_save_loaded) else where) to colors.success
            }
            else -> stringResource(R.string.nag_save_original_kept) to colors.textTertiary
        }
        Text(status.first, style = NagTheme.type.labelSmall, color = status.second, modifier = Modifier.testTag("save-status"))
    }
}

// -------------------------------------------------------------------- fields

/** A one-line text field in the dialog's look; [invalid] marks its border. */
@Composable
internal fun ToolField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    invalid: Boolean = false,
    numbers: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = NagTheme.colors
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        enabled = enabled,
        textStyle = (if (mono) NagTheme.type.mono else NagTheme.type.body).copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = if (numbers) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier
            .alpha(if (enabled) 1f else PanelDisabledAlpha)
            .border(1.dp, if (invalid) colors.danger else colors.borderStrong, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .heightIn(min = NagTheme.dimens.controlHeight - 16.dp)
            .semantics { contentDescription = label }
            .then(if (singleLine) Modifier.tabMovesFocus(focusManager) else Modifier),
    )
}
