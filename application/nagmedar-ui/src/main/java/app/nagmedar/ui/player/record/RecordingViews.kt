package app.nagmedar.ui.player.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.player.panel.TextAction
import app.nagmedar.ui.player.playerClock
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import java.text.NumberFormat

/**
 * The recording's mark over the video (S-607), at its top right whether the controls show or not:
 * a red dot, the time recorded and the size written, and a button that stops it (Shift+R too);
 * then the copy into its folder, with how far it is.
 */
@Composable
fun RecordingIndicator(status: RecordingStatus, onStop: () -> Unit, modifier: Modifier = Modifier) {
    if (status == RecordingStatus.Off) return
    val colors = NagTheme.colors
    val locale = currentLocale()
    val text = when (status) {
        RecordingStatus.Off -> return
        RecordingStatus.Starting -> stringResource(R.string.nag_rec_starting)
        is RecordingStatus.On -> playerClock(status.elapsedMs) + " · " + sizeLabel(status.bytes, locale)
        is RecordingStatus.Saving -> stringResource(R.string.nag_rec_saving, NumberFormat.getPercentInstance(locale).format(status.fraction.toDouble()))
    }
    val recording = status is RecordingStatus.On || status == RecordingStatus.Starting
    val said = if (recording) stringResource(R.string.nag_rec_recording, text) else text
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .heightIn(min = 32.dp)
            .background(Color.Black.copy(alpha = 0.66f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(start = 12.dp, end = if (recording) 2.dp else 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("recording-indicator"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(if (recording) colors.danger else Color.White.copy(alpha = 0.5f), CircleShape)
                .semantics { contentDescription = said },
        )
        Text(text, style = NagTheme.type.mono, color = Color.White, maxLines = 1, modifier = Modifier.testTag("recording-text"))
        if (recording) {
            NagIconButton(NagIcons.Stop, stringResource(R.string.nag_rec_stop), onStop, size = 28.dp, iconSize = 14.dp, tint = Color.White, shortcut = ShortcutId.Record)
        }
    }
}

/**
 * The card a recording leaves over the video once kept (S-607), with the screenshots' card: its
 * file, folder, length and size, "Play" and "Open folder"; or why it was not made or kept, with
 * "Choose a folder…" when the folder or a full storage is the cause and "Try again" when the
 * recording waits in the app's storage. It goes after eight seconds, later while the pointer rests on it.
 */
@Composable
fun RecordingCard(
    outcome: RecordingOutcome?,
    onPlay: (RecordingOutcome.Saved) -> Unit,
    onOpenFolder: (RecordingOutcome.Saved) -> Unit,
    onChooseFolder: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** For tests and screenshots: the card stays. */
    stays: Boolean = false,
) {
    var hovered by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    // a failure that can be put right waits for it
    val waits = outcome is RecordingOutcome.Failed && outcome.canRetry
    LaunchedEffect(outcome, hovered, stays) {
        if (outcome == null || hovered || stays || waits) return@LaunchedEffect
        delay(SHOWN_MS)
        dismiss()
    }
    if (outcome == null) return
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .widthIn(max = 380.dp)
            .shadow(12.dp, shape)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.borderStrong, shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        when (awaitPointerEvent().type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> hovered = false
                        }
                    }
                }
            }
            .padding(10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("recording-card"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (outcome) {
            is RecordingOutcome.Saved -> Saved(outcome, onPlay, onOpenFolder)
            is RecordingOutcome.Failed -> Failed(outcome, onChooseFolder, onRetry)
        }
        NagIconButton(NagIcons.Close, stringResource(R.string.nag_snap_dismiss), onDismiss, size = 24.dp, iconSize = 14.dp)
    }
}

@Composable
private fun Saved(saved: RecordingOutcome.Saved, onPlay: (RecordingOutcome.Saved) -> Unit, onOpenFolder: (RecordingOutcome.Saved) -> Unit) {
    val colors = NagTheme.colors
    val locale = currentLocale()
    Icon(NagIcons.Record, contentDescription = null, tint = colors.danger, modifier = Modifier.padding(top = 1.dp).size(18.dp))
    Column(Modifier.widthIn(min = 180.dp, max = 280.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.nag_rec_saved), style = NagTheme.type.label, color = colors.text, maxLines = 1)
        Text(
            saved.fileName,
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("recording-file"),
        )
        Text(
            listOfNotNull(saved.folder, saved.durationMs.takeIf { it > 0 }?.let(::playerClock), sizeLabel(saved.bytes, locale)).joinToString(" · "),
            style = NagTheme.type.labelSmall,
            color = colors.textTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (saved.stoppedForSpace) {
            Text(stringResource(R.string.nag_rec_stopped_for_space), style = NagTheme.type.labelSmall, color = colors.warning)
        }
        // the buttons' words in line with the lines above
        Row(Modifier.padding(top = 2.dp).offset(x = (-6).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (saved.uri != null) TextAction(stringResource(R.string.nag_rec_play), enabled = true) { onPlay(saved) }
            TextAction(stringResource(R.string.nag_rec_open_folder), enabled = true) { onOpenFolder(saved) }
        }
    }
}

@Composable
private fun Failed(failed: RecordingOutcome.Failed, onChooseFolder: () -> Unit, onRetry: () -> Unit) {
    val colors = NagTheme.colors
    Icon(NagIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.padding(top = 1.dp).size(18.dp))
    Column(Modifier.widthIn(min = 180.dp, max = 280.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.nag_rec_failed), style = NagTheme.type.label, color = colors.text, maxLines = 1)
        Text(stringResource(failureText(failed.reason)), style = NagTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.testTag("recording-reason"))
        if (failed.canRetry) Text(stringResource(R.string.nag_rec_kept_in_app), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        Row(Modifier.padding(top = 2.dp).offset(x = (-6).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // a folder gone, or a full storage: the recording can go to another folder (a drive)
            if (failed.reason == RecordingFailure.Folder || failed.reason == RecordingFailure.NoSpace && failed.canRetry) TextAction(stringResource(R.string.nag_snap_choose_folder), enabled = true, onClick = onChooseFolder)
            if (failed.canRetry) TextAction(stringResource(R.string.nag_rec_retry), enabled = true, onClick = onRetry)
        }
    }
}

private const val SHOWN_MS = 8_000L
