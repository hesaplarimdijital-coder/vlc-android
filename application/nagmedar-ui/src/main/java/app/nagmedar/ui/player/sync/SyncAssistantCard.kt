package app.nagmedar.ui.player.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.panel.PanelDisabledAlpha
import app.nagmedar.ui.player.panel.TextAction
import app.nagmedar.ui.player.panel.signed
import app.nagmedar.ui.player.playerClock
import app.nagmedar.ui.theme.NagTheme
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * The sync assistant (S-704), after the mockups' player: the moment the line is heard, the
 * moment its subtitle shows, the difference to put onto the delay; one step after the other, the
 * next one lit, each done by its key or a click. What was applied can be taken back.
 */
@Composable
fun SyncAssistantCard(
    /** The film's time when the line was heard. */
    audioMarkMs: Long?,
    /** The film's time when its subtitle showed. */
    subtitleMarkMs: Long?,
    applied: AppliedSync?,
    onMarkAudio: () -> Unit,
    onMarkSubtitle: () -> Unit,
    onApply: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NagTheme.colors
    val locale = currentLocale()
    val resources = LocalContext.current.resources
    val shape = RoundedCornerShape(10.dp)
    val difference = if (audioMarkMs != null && subtitleMarkMs != null) audioMarkMs - subtitleMarkMs else null
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, shape)
            .padding(12.dp)
            .testTag("sync-assistant"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.nag_assist_title), style = NagTheme.type.label, color = colors.text)
        Text(stringResource(R.string.nag_assist_hint), style = NagTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
        Step(
            text = audioMarkMs?.let { stringResource(R.string.nag_assist_audio_marked, preciseClock(it, locale)) } ?: stringResource(R.string.nag_assist_mark_audio),
            done = audioMarkMs != null,
            next = audioMarkMs == null,
            enabled = true,
            keys = ShortcutRegistry.chordLabel(ShortcutId.SyncMarkAudio, resources),
            tag = "assist-audio",
            onClick = onMarkAudio,
        )
        Step(
            text = subtitleMarkMs?.let { stringResource(R.string.nag_assist_subtitle_marked, preciseClock(it, locale)) } ?: stringResource(R.string.nag_assist_mark_subtitle),
            done = subtitleMarkMs != null,
            next = audioMarkMs != null && subtitleMarkMs == null,
            enabled = true,
            keys = ShortcutRegistry.chordLabel(ShortcutId.SyncMarkSubtitle, resources),
            tag = "assist-subtitle",
            onClick = onMarkSubtitle,
        )
        Step(
            text = difference?.let { stringResource(R.string.nag_assist_apply_difference, stringResource(R.string.nag_sync_ms, signed(it, locale))) }
                ?: stringResource(R.string.nag_assist_apply),
            done = false,
            next = difference != null,
            enabled = difference != null,
            keys = ShortcutRegistry.chordLabel(ShortcutId.SyncApply, resources),
            tag = "assist-apply",
            onClick = onApply,
        )
        if (applied != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp, start = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(NagIcons.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(R.string.nag_assist_applied, stringResource(R.string.nag_sync_ms, signed(applied.toMs - applied.fromMs, locale))),
                    style = NagTheme.type.labelSmall,
                    color = colors.success,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("assist-applied"),
                )
                TextAction(stringResource(R.string.nag_assist_undo), enabled = true, onClick = onUndo, modifier = Modifier.testTag("assist-undo"))
                ShortcutRegistry.chordLabel(ShortcutId.SyncUndo, resources)?.let { keys ->
                    Text(keys, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** One step: done (a check, on a raised ground), next (lit) or to come (a ring, in an outline); its keys at its end. */
@Composable
private fun Step(text: String, done: Boolean, next: Boolean, enabled: Boolean, keys: String?, tag: String, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(7.dp)
    val ground = when {
        done -> colors.surfaceRaised
        next -> colors.accent.copy(alpha = 0.15f)
        else -> stateBackground(state)
    }
    val edge = when {
        done -> null
        next -> colors.accent.copy(alpha = 0.6f)
        else -> colors.border
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .alpha(if (enabled) 1f else PanelDisabledAlpha)
            .then(if (edge != null) Modifier.border(1.dp, edge, shape) else Modifier)
            .background(ground, shape)
            .nagClickable(interaction, onClick, enabled = enabled, onClickLabel = text)
            .padding(horizontal = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (done) {
                Icon(NagIcons.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
            } else {
                Box(Modifier.size(12.dp).border(1.5.dp, if (next) colors.accent else colors.textTertiary, CircleShape))
            }
        }
        Text(text, style = NagTheme.type.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (keys != null) Text(keys, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "1:11:42,180": a film's time to the millisecond, the decimal sign the language's. */
fun preciseClock(ms: Long, locale: Locale): String {
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val millis = (ms.coerceAtLeast(0L) % 1000).toString().padStart(3, '0')
    return playerClock(ms) + separator + millis
}
