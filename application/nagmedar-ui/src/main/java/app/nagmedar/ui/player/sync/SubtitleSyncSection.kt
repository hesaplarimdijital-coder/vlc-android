package app.nagmedar.ui.player.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.input.ShortcutId

/**
 * The subtitles' tab's delay (S-704), after the mockups' player: the subtitles' delay in 10 and
 * 50 ms steps, "Remember for this file" under it, then the sync assistant, which sets the delay
 * by ear and eye. The same delay as the sync tab's.
 */
@Composable
fun SubtitleSyncSection(
    delayMs: Long,
    remembered: Boolean,
    audioMarkMs: Long?,
    subtitleMarkMs: Long?,
    applied: AppliedSync?,
    onDelay: (Long) -> Unit,
    onRemember: (Boolean) -> Unit,
    onMarkAudio: () -> Unit,
    onMarkSubtitle: () -> Unit,
    onApply: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.testTag("subtitles-sync"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DelaySection(
            title = stringResource(R.string.nag_subs_delay),
            valueMs = delayMs,
            remembered = remembered,
            hint = stringResource(R.string.nag_sync_subtitle_hint),
            keys = ShortcutId.SubtitleDelayDown to ShortcutId.SubtitleDelayUp,
            tag = "subtitles-delay",
            onSet = onDelay,
        )
        NagCheckbox(remembered, onRemember, stringResource(R.string.nag_sync_remember), Modifier.testTag("subtitles-remember"))
        SyncAssistantCard(
            audioMarkMs = audioMarkMs,
            subtitleMarkMs = subtitleMarkMs,
            applied = applied,
            onMarkAudio = onMarkAudio,
            onMarkSubtitle = onMarkSubtitle,
            onApply = onApply,
            onUndo = onUndo,
        )
    }
}
