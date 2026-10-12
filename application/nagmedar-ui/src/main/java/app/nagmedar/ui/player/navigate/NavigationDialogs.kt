package app.nagmedar.ui.player.navigate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.player.ChapterMark
import app.nagmedar.ui.player.playerClock
import app.nagmedar.ui.shell.KeyCap
import app.nagmedar.ui.theme.NagTheme

/** "Jump to time" (Ctrl+T, S-608) in its own window, over the video: the keys are its own while it is open. */
@Composable
fun JumpDialog(nowMs: Long, lengthMs: Long, chapters: List<ChapterMark>, onJump: (Long) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) { JumpSurface(nowMs, lengthMs, chapters, onJump, onDismiss) }
    }
}

/**
 * "Jump to time" itself: the time playing, selected, so what is typed replaces it; under it where
 * that goes (and in which chapter), or why it cannot; Enter goes, Escape closes. Any of the ways
 * [parseJumpTime] reads is taken: "1:12:33", "72:33", "45 sn", "+30", "%50".
 */
@Composable
fun JumpSurface(
    nowMs: Long,
    lengthMs: Long,
    chapters: List<ChapterMark>,
    onJump: (Long) -> Unit,
    onDismiss: () -> Unit,
    /** For tests and screenshots: what was typed. */
    initial: String? = null,
) {
    val colors = NagTheme.colors
    val start = initial ?: playerClock(nowMs)
    var value by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    val target = parseJumpTime(value.text, nowMs, lengthMs)
    val go = {
        if (target is JumpTarget.To) {
            onJump(target.ms)
            onDismiss()
        }
    }
    DialogCard(stringResource(R.string.nag_jump_title), Modifier.testTag("jump-dialog")) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val label = stringResource(R.string.nag_jump_field)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .border(1.dp, colors.accent, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = NagTheme.type.mono.copy(fontSize = 22.sp, color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .semantics { contentDescription = label }
                    .testTag("jump-field")
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                go()
                                true
                            }
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    },
            )
            if (lengthMs > 0) {
                Text(stringResource(R.string.nag_jump_length, playerClock(lengthMs)), style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1)
            }
        }
        // where it goes, or why it cannot
        val (outcome, warn) = when (target) {
            is JumpTarget.To -> {
                val index = chapters.indexOfLast { it.startMs <= target.ms }
                val chapter = chapters.getOrNull(index)?.let { mark ->
                    if (mark.name.isBlank()) stringResource(R.string.nag_player_chapter, index + 1) else stringResource(R.string.nag_player_chapter_named, index + 1, mark.name)
                }
                listOfNotNull(stringResource(R.string.nag_jump_to, playerClock(target.ms)), chapter).joinToString(" · ") to false
            }
            is JumpTarget.PastEnd -> stringResource(R.string.nag_jump_past_end, playerClock(target.lengthMs)) to true
            JumpTarget.Unreadable -> stringResource(R.string.nag_jump_unreadable) to value.text.isNotBlank()
        }
        Text(
            outcome,
            style = NagTheme.type.label,
            color = if (warn) colors.warning else colors.textSecondary,
            maxLines = 2,
            modifier = Modifier.testTag("jump-outcome"),
        )
        Text(stringResource(R.string.nag_jump_hint), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
            NagButton(stringResource(R.string.nag_jump_go), { go() }, primary = true, enabled = target is JumpTarget.To, modifier = Modifier.testTag("jump-go"))
        }
    }
}

/** Naming a bookmark (S-608) in its own window: its name selected, Enter keeps it, Escape leaves it. */
@Composable
fun RenameBookmarkDialog(mark: BookmarkMark, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) { RenameSurface(mark, onRename, onDismiss) }
    }
}

@Composable
fun RenameSurface(mark: BookmarkMark, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = NagTheme.colors
    var value by remember { mutableStateOf(TextFieldValue(mark.name, TextRange(0, mark.name.length))) }
    val save = {
        if (value.text.isNotBlank()) {
            onRename(value.text.trim())
            onDismiss()
        }
    }
    DialogCard(stringResource(R.string.nag_bm_rename_title), Modifier.testTag("rename-dialog")) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Text(playerClock(mark.timeMs), style = NagTheme.type.mono, color = colors.textSecondary)
        val label = stringResource(R.string.nag_bm_name)
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = NagTheme.type.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.accent, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .focusRequester(focus)
                .semantics { contentDescription = label }
                .testTag("rename-field")
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            save()
                            true
                        }
                        Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                },
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
            NagButton(stringResource(R.string.nag_bm_save), { save() }, primary = true, enabled = value.text.isNotBlank(), modifier = Modifier.testTag("rename-save"))
        }
    }
}

/** A dialog's window: the card in the middle, a tap beside it closes it. */
@Composable
private fun DialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The card of the player's small dialogs: a title with Esc, then what it asks. */
@Composable
private fun DialogCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val width = if (maxWidth < 420.dp) maxWidth - 32.dp else 380.dp
        Column(
            modifier
                .width(width)
                .shadow(24.dp, shape)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                // taps inside stay inside
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = NagTheme.type.title, color = colors.text, modifier = Modifier.weight(1f))
                KeyCap(ESCAPE)
            }
            content()
        }
    }
}

/** The key's name, as keyboards write it. */
private const val ESCAPE = "Esc"
