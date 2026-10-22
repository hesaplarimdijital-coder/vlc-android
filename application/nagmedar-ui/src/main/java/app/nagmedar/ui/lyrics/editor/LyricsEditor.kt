@file:OptIn(ExperimentalLayoutApi::class)

package app.nagmedar.ui.lyrics.editor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.data.LyricsSource
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.NagTextField
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.lyrics.Lrc
import app.nagmedar.ui.lyrics.Lyrics
import app.nagmedar.ui.lyrics.LyricsLoad
import app.nagmedar.ui.lyrics.LyricsOrigin
import app.nagmedar.ui.lyrics.LyricsPane
import app.nagmedar.ui.lyrics.delayNumber
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.onboarding.FolderGrants
import app.nagmedar.ui.onboarding.GrantHolder
import java.text.NumberFormat

/** The shortcuts that act once per press: a key held down does not stamp a line after line. */
private val ONCE = setOf(ShortcutId.LrcStamp, ShortcutId.LrcUnstamp, ShortcutId.LrcWordMode, ShortcutId.PlayPause)

/** The width from which the lines and the preview stand side by side. */
private val SIDE_BY_SIDE = 840.dp

/**
 * S-1005: the lyrics editor over the whole window. The lyrics are lines to stamp while the track
 * plays: Enter stamps the line (in word mode the next word), Space plays and pauses, the arrows
 * choose the line, Backspace takes the last stamp back, "," and "." move it by a tenth of a second
 * (all through [ShortcutRegistry]'s editor scope), a click goes to a line. The result is previewed
 * as the views show it, saved beside the file or into its tags, or sent to LRCLIB after a
 * confirmation. [timeMs] is where the track is; [playing] whether it plays.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsEditorScreen(
    controller: LyricsEditorController,
    timeMs: () -> Long,
    playing: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onTrack: Boolean = true,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val context = LocalContext.current
    var confirmClose by remember { mutableStateOf(false) }
    val close = { if (controller.dirty) confirmClose = true else onClose() }
    BackHandler(onBack = close)
    val focus = remember { FocusRequester() }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            // later saves write there again: the other settings leave the folder's permission alone
            FolderGrants.note(context, GrantHolder.Lyrics, uri.toString())
            controller.save(uri)
        }
    }
    val dialogOpen = controller.importing || controller.publish != PublishState.Idle || confirmClose
    // the keys are the editor's again when a dialog closes
    LaunchedEffect(dialogOpen) { if (!dialogOpen) runCatching { focus.requestFocus() } }
    Column(
        modifier
            .fillMaxSize()
            // over the window: nothing under it takes a click
            .pointerInput(Unit) { detectTapGestures { } }
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = dimens.gapL, vertical = dimens.gapM)
            // the keys are asked before the focus target, which this is, is: the handler comes first in the chain
            .onPreviewKeyEvent { event -> editorKey(event, controller, dialogOpen) }
            .focusRequester(focus)
            .focusable()
            .testTag("lrc-editor"),
        verticalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        Header(controller, close)
        if (!onTrack) {
            Text(stringResource(R.string.nag_lrc_other_track), style = NagTheme.type.label, color = colors.warning, modifier = Modifier.testTag("lrc-other-track"))
        }
        Toolbar(controller, timeMs, playing)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= SIDE_BY_SIDE
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(dimens.gapL)) {
                    Box(Modifier.weight(1.3f).fillMaxSize()) { Lines(controller) }
                    Box(Modifier.weight(1f).fillMaxSize()) { Preview(controller, timeMs) }
                }
            } else if (controller.preview) {
                Preview(controller, timeMs)
            } else {
                Lines(controller)
            }
        }
        Footer(controller, onPickFolder = { runCatching { folderPicker.launch(null) } })
    }
    if (controller.importing) ImportDialog(controller)
    PublishDialogs(controller)
    if (confirmClose) {
        NagDialog(onDismissRequest = { confirmClose = false }) {
            DialogFrame({ confirmClose = false }) {
                DialogCard(stringResource(R.string.nag_lrc_discard_title), Modifier.testTag("lrc-discard")) {
                    Text(stringResource(R.string.nag_lrc_discard_body), style = NagTheme.type.body, color = colors.textSecondary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        NagButton(stringResource(R.string.nag_lrc_discard_keep), { confirmClose = false }, modifier = Modifier.testTag("lrc-discard-keep"))
                        NagButton(stringResource(R.string.nag_lrc_discard_close), {
                            confirmClose = false
                            onClose()
                        }, primary = true, modifier = Modifier.testTag("lrc-discard-close"))
                    }
                }
            }
        }
    }
}

/** The editor's key [event]: a shortcut of its scope, or Space; true when used. */
private fun editorKey(event: KeyEvent, controller: LyricsEditorController, dialogOpen: Boolean): Boolean {
    if (event.type != KeyEventType.KeyDown || ShortcutRegistry.recording || controller.typing || dialogOpen) return false
    val shortcut = ShortcutRegistry.match(event, setOf(ShortcutScope.Editor))
        ?: ShortcutRegistry.match(event)?.takeIf { it.id == ShortcutId.PlayPause }
        ?: return false
    if (event.nativeKeyEvent.repeatCount > 0 && shortcut.id in ONCE) return true
    return controller.perform(shortcut.id)
}

@Composable
private fun Header(controller: LyricsEditorController, onClose: () -> Unit) {
    val colors = NagTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.nag_lrc_title), style = NagTheme.type.title, color = colors.text)
            val track = controller.track
            val subtitle = listOfNotNull(track.title?.takeIf { it.isNotBlank() }, track.artist?.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (subtitle.isNotEmpty()) Text(subtitle, style = NagTheme.type.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        NagIconButton(NagIcons.Close, stringResource(R.string.nag_lrc_close), onClose, modifier = Modifier.testTag("lrc-close"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(controller: LyricsEditorController, timeMs: () -> Long, playing: Boolean) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val locale = currentLocale()
    val state = controller.stamps
    val res = LocalContext.current.resources
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
        verticalArrangement = Arrangement.spacedBy(dimens.gapS),
    ) {
        NagIconButton(
            if (playing) NagIcons.Pause else NagIcons.Play,
            stringResource(if (playing) R.string.nag_lrc_pause else R.string.nag_lrc_play),
            controller::togglePlay,
            shortcut = ShortcutId.PlayPause,
            modifier = Modifier.testTag("lrc-play"),
        )
        val position = Lrc.stamp(timeMs())
        Text(
            position,
            style = NagTheme.type.mono,
            color = colors.text,
            modifier = Modifier.widthIn(min = 72.dp).semantics { contentDescription = res.getString(R.string.nag_lrc_position, position) }.testTag("lrc-position"),
        )
        SegmentedChoice(
            listOf(StampMode.Line to stringResource(R.string.nag_lrc_mode_line), StampMode.Word to stringResource(R.string.nag_lrc_mode_word)),
            state.mode,
            controller::setMode,
            modifier = Modifier.semantics { contentDescription = res.getString(R.string.nag_lrc_mode_label) }.testTag("lrc-mode"),
        )
        NagButton(
            stringResource(R.string.nag_lrc_stamp) + " · " + (ShortcutRegistry.chordLabel(ShortcutId.LrcStamp, res) ?: ""),
            controller::stamp,
            primary = true,
            enabled = !state.finished,
            modifier = Modifier.testTag("lrc-stamp"),
        )
        NagIconButton(NagIcons.History, stringResource(R.string.nag_lrc_unstamp), controller::unstamp, enabled = state.canUndo, shortcut = ShortcutId.LrcUnstamp, modifier = Modifier.testTag("lrc-unstamp"))
        val nudgeEarlier = stringResource(R.string.nag_lrc_nudge_earlier)
        val nudgeLater = stringResource(R.string.nag_lrc_nudge_later)
        NagButton(
            delayNumber(-LyricsEditorController.NUDGE_MS, locale),
            { controller.nudge(-LyricsEditorController.NUDGE_MS) },
            enabled = state.canUndo,
            modifier = Modifier.semantics { contentDescription = nudgeEarlier }.testTag("lrc-nudge-earlier"),
        )
        NagButton(
            delayNumber(LyricsEditorController.NUDGE_MS, locale),
            { controller.nudge(LyricsEditorController.NUDGE_MS) },
            enabled = state.canUndo,
            modifier = Modifier.semantics { contentDescription = nudgeLater }.testTag("lrc-nudge-later"),
        )
        NagButton(stringResource(R.string.nag_lrc_import), { controller.importing = true }, icon = NagIcons.PlaylistAdd, modifier = Modifier.testTag("lrc-import"))
        NagButton(
            stringResource(R.string.nag_lrc_preview),
            { controller.preview = !controller.preview },
            icon = NagIcons.Lyrics,
            primary = false,
            modifier = Modifier.semantics { selected = controller.preview }.testTag("lrc-preview"),
        )
    }
    val chord = { id: ShortcutId -> ShortcutRegistry.chordLabel(id, res).orEmpty() }
    Text(
        stringResource(
            R.string.nag_lrc_hint,
            chord(ShortcutId.LrcStamp), chord(ShortcutId.PlayPause), chord(ShortcutId.LrcLineUp), chord(ShortcutId.LrcLineDown),
            chord(ShortcutId.LrcUnstamp), chord(ShortcutId.LrcNudgeEarlier), chord(ShortcutId.LrcNudgeLater),
        ),
        style = NagTheme.type.labelSmall,
        color = colors.textTertiary,
        modifier = Modifier.testTag("lrc-hint"),
    )
}

@Composable
private fun Lines(controller: LyricsEditorController) {
    val colors = NagTheme.colors
    val state = controller.stamps
    if (state.lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StatePanel(
                tone = StateTone.Neutral,
                icon = NagIcons.Lyrics,
                title = stringResource(R.string.nag_lrc_empty_title),
                description = stringResource(R.string.nag_lrc_empty_desc),
                modifier = Modifier.testTag("lrc-empty"),
            )
        }
        return
    }
    val list = rememberLazyListState()
    val reduced = NagTheme.motion.reduced
    LaunchedEffect(state.cursor.line) {
        val target = (state.cursor.line - 2).coerceIn(0, state.lines.lastIndex)
        if (reduced) list.scrollToItem(target) else list.animateScrollToItem(target)
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.nag_lrc_progress, state.stampedLines, state.lines.size),
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp).testTag("lrc-progress"),
        )
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("lrc-lines")) {
            itemsIndexed(state.lines) { index, line ->
                LineRow(index, line, state, onClick = { controller.select(index) })
            }
        }
    }
}

@Composable
private fun LineRow(index: Int, line: EditLine, state: StampState, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val res = LocalContext.current.resources
    val interaction = remember { MutableInteractionSource() }
    val interactionState = interaction.collectState()
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    val current = index == state.cursor.line
    val stamp = if (line.stamped) Lrc.stamp(line.startMs) else UNSTAMPED
    val description = res.getString(R.string.nag_lrc_line_desc, index + 1, if (line.stamped) stamp else res.getString(R.string.nag_lrc_line_not_stamped))
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing(interactionState, shape)
            .background(stateBackground(interactionState, selected = false, rest = if (current) colors.surfaceRaised else Color.Transparent), shape)
            .then(if (current) Modifier.border(1.dp, colors.accent, shape) else Modifier)
            .nagClickable(interaction, onClick, onClickLabel = res.getString(R.string.nag_lrc_line_select))
            .semantics { this.selected = current; contentDescription = description }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("lrc-line-$index"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(stamp, style = NagTheme.type.mono, color = if (line.stamped) colors.accent else colors.textTertiary, modifier = Modifier.width(76.dp))
        Text(
            lineText(line, state, current),
            style = NagTheme.type.body,
            color = if (current) colors.text else colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A line's text; in word mode the words stamped lit and the next one underlined. */
@Composable
private fun lineText(line: EditLine, state: StampState, current: Boolean): AnnotatedString {
    val colors = NagTheme.colors
    if (line.text.isEmpty()) return AnnotatedString(INTERLUDE)
    if (state.mode != StampMode.Word || line.words.size < 2) return AnnotatedString(line.text)
    return buildAnnotatedString {
        line.words.forEachIndexed { i, word ->
            val next = current && i == state.cursor.word
            val style = when {
                next -> SpanStyle(color = colors.text, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
                word.startMs >= 0 -> SpanStyle(color = colors.accent)
                else -> SpanStyle(color = colors.textTertiary)
            }
            withStyle(style) { append(word.text) }
        }
    }
}

@Composable
private fun Preview(controller: LyricsEditorController, timeMs: () -> Long) {
    val lines = controller.stamps.stampedLyrics()
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.nag_lrc_preview_empty), style = NagTheme.type.body, color = NagTheme.colors.textTertiary, modifier = Modifier.testTag("lrc-preview-empty"))
        }
        return
    }
    val lyrics = Lyrics(lines, LyricsOrigin.User)
    LyricsPane(
        load = LyricsLoad.Found(controller.track, lyrics),
        timeMs = timeMs,
        onSeek = null,
        onDelay = null,
        modifier = Modifier.fillMaxSize().testTag("lrc-preview-pane"),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Footer(controller: LyricsEditorController, onPickFolder: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
        controller.message?.let { message -> MessageLine(message, onPickFolder) }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
            verticalArrangement = Arrangement.spacedBy(dimens.gapS),
            ) {
            Text(stringResource(R.string.nag_lrc_save_to), style = NagTheme.type.labelSmall, color = colors.textSecondary)
            SegmentedChoice(
                listOf(SaveTarget.File to stringResource(R.string.nag_lrc_target_file), SaveTarget.Tag to stringResource(R.string.nag_lrc_target_tag)),
                controller.target,
                { controller.target = it },
                modifier = Modifier.testTag("lrc-target"),
            )
            NagButton(
                stringResource(R.string.nag_lrc_save),
                { controller.save() },
                primary = true,
                enabled = !controller.saving && controller.stamps.lines.isNotEmpty(),
                modifier = Modifier.testTag("lrc-save"),
            )
            if (controller.canPublish) {
                NagButton(stringResource(R.string.nag_lrc_publish), controller::askPublish, enabled = controller.stamps.lines.isNotEmpty(), modifier = Modifier.testTag("lrc-publish"))
            }
        }
        Text(
            stringResource(if (controller.target == SaveTarget.File) R.string.nag_lrc_target_file_hint else R.string.nag_lrc_target_tag_hint),
            style = NagTheme.type.labelSmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun MessageLine(message: EditorMessage, onPickFolder: () -> Unit) {
    val colors = NagTheme.colors
    // only a save is good news; everything else asks for something or says what failed
    val bad = message !is EditorMessage.Saved
    val text = when (message) {
        is EditorMessage.Saved -> if (message.target == SaveTarget.File) stringResource(R.string.nag_lrc_saved_file, message.name.orEmpty()) else stringResource(R.string.nag_lrc_saved_tag)
        is EditorMessage.Incomplete -> pluralStringResource(R.plurals.nag_lrc_incomplete, message.left, message.left)
        EditorMessage.NeedsFolder -> stringResource(R.string.nag_lrc_needs_folder)
        EditorMessage.SaveFailed -> stringResource(R.string.nag_lrc_save_failed)
        EditorMessage.NoRoom -> stringResource(R.string.nag_lrc_save_no_room)
        EditorMessage.CannotPublish -> stringResource(R.string.nag_lrc_publish_cannot)
        EditorMessage.NothingToImport -> stringResource(R.string.nag_lrc_import_nothing)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = NagTheme.type.label, color = if (bad) colors.warning else colors.success, modifier = Modifier.weight(1f, fill = false).testTag("lrc-message"))
        if (message == EditorMessage.NeedsFolder) NagButton(stringResource(R.string.nag_lrc_pick_folder), onPickFolder, icon = NagIcons.FolderAdd, modifier = Modifier.testTag("lrc-pick-folder"))
    }
}

@Composable
private fun ImportDialog(controller: LyricsEditorController) {
    val colors = NagTheme.colors
    var text by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    // the keys are the editor's again when the box goes
    DisposableEffect(Unit) { onDispose { controller.typing = false } }
    NagDialog(onDismissRequest = { controller.importing = false }) {
        DialogFrame({ controller.importing = false }) {
            DialogCard(stringResource(R.string.nag_lrc_import_title), Modifier.testTag("lrc-import-dialog"), cardWidth = 520.dp) {
                Text(stringResource(R.string.nag_lrc_import_hint), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                NagTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.nag_lrc_import_field),
                    lines = 6,
                    modifier = Modifier.onFocusChanged { controller.typing = it.isFocused }.testTag("lrc-import-text"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NagButton(stringResource(R.string.nag_lrc_import_paste), { clipboard.getText()?.text?.let { text = it } }, modifier = Modifier.testTag("lrc-import-paste"))
                    if (controller.current != null) {
                        NagButton(stringResource(R.string.nag_lrc_import_current), { controller.useCurrent() }, modifier = Modifier.testTag("lrc-import-current"))
                    }
                    controller.originals.forEach { original ->
                        val label = if (original.source == LyricsSource.ORIGINAL_TAG) R.string.nag_lrc_import_original_tag else R.string.nag_lrc_import_original_file
                        NagButton(stringResource(label), { controller.restore(original) }, modifier = Modifier.testTag("lrc-import-${original.source}"))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_lrc_cancel), { controller.importing = false }, modifier = Modifier.testTag("lrc-import-cancel"))
                    NagButton(stringResource(R.string.nag_lrc_import_do), { controller.import(text) }, primary = true, enabled = text.isNotBlank(), modifier = Modifier.testTag("lrc-import-do"))
                }
            }
        }
    }
}

@Composable
private fun PublishDialogs(controller: LyricsEditorController) {
    val colors = NagTheme.colors
    val state = controller.publish
    if (state == PublishState.Idle) return
    val res = LocalContext.current.resources
    val locale = currentLocale()
    NagDialog(onDismissRequest = controller::dismissPublish) {
        DialogFrame(controller::dismissPublish) {
            DialogCard(stringResource(R.string.nag_lrc_publish_title), Modifier.testTag("lrc-publish-dialog"), cardWidth = 460.dp) {
                when (state) {
                    is PublishState.Confirming -> {
                        Text(stringResource(R.string.nag_lrc_publish_body), style = NagTheme.type.body, color = colors.textSecondary)
                        val request = state.request
                        Text(
                            res.getString(
                                R.string.nag_lrc_publish_summary,
                                request.trackName, request.artistName, request.albumName.ifEmpty { "—" },
                                formatDuration((request.durationSec * 1_000).toLong()), request.syncedLyrics.lines().size,
                            ),
                            style = NagTheme.type.label,
                            color = colors.text,
                            modifier = Modifier.testTag("lrc-publish-summary"),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            NagButton(stringResource(R.string.nag_lrc_cancel), controller::dismissPublish, modifier = Modifier.testTag("lrc-publish-cancel"))
                            NagButton(stringResource(R.string.nag_lrc_publish_send), controller::confirmPublish, primary = true, modifier = Modifier.testTag("lrc-publish-send"))
                        }
                    }
                    PublishState.Challenging -> Working(stringResource(R.string.nag_lrc_publish_challenge), null, controller)
                    is PublishState.Solving -> {
                        val tries = NumberFormat.getIntegerInstance(locale).format(state.tries)
                        Working(stringResource(R.string.nag_lrc_publish_solving, tries), state.progress, controller)
                        Text(stringResource(R.string.nag_lrc_publish_solving_hint), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                    }
                    PublishState.Sending -> Working(stringResource(R.string.nag_lrc_publish_sending), null, controller)
                    PublishState.Done -> {
                        Text(stringResource(R.string.nag_lrc_publish_done), style = NagTheme.type.body, color = colors.success, modifier = Modifier.testTag("lrc-publish-done"))
                        CloseRow(controller)
                    }
                    is PublishState.Failed -> {
                        val message = when (val failure = state.failure) {
                            is PublishFailure.Offline -> stringResource(R.string.nag_lrc_publish_offline)
                            is PublishFailure.Refused -> stringResource(R.string.nag_lrc_publish_refused)
                            is PublishFailure.Busy -> stringResource(R.string.nag_lrc_publish_busy)
                            is PublishFailure.Server -> stringResource(R.string.nag_lrc_publish_server, failure.status)
                        }
                        Text(message, style = NagTheme.type.body, color = colors.warning, modifier = Modifier.testTag("lrc-publish-failed"))
                        CloseRow(controller)
                    }
                    PublishState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun Working(label: String, progress: Float?, controller: LyricsEditorController) {
    Text(label, style = NagTheme.type.body, color = NagTheme.colors.text, modifier = Modifier.testTag("lrc-publish-working"))
    NagProgressBar(progress, label)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        NagButton(stringResource(R.string.nag_lrc_cancel), controller::cancelPublish, modifier = Modifier.testTag("lrc-publish-abort"))
    }
}

@Composable
private fun CloseRow(controller: LyricsEditorController) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        NagButton(stringResource(R.string.nag_lrc_publish_close), controller::dismissPublish, primary = true, modifier = Modifier.testTag("lrc-publish-close"))
    }
}

/** A line not stamped yet, in the stamps' column. */
private const val UNSTAMPED = "--:--.--"

/** A timed line without words: a pause in the singing. */
private const val INTERLUDE = "♪"

