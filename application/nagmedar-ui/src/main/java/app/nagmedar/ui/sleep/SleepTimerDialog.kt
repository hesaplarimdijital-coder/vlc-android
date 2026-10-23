package app.nagmedar.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.FilterChip
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

/** S-1007: the sleep timer's window: set it, or see what is left and change it. */
@Composable
fun SleepTimerDialog(timer: SleepTimer = Sleep.timer, onDismiss: () -> Unit) {
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) { SleepTimerSurface(timer, onDismiss) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSurface(timer: SleepTimer, onDismiss: () -> Unit) {
    val colors = NagTheme.colors
    val running by timer.state.collectAsState()
    val locale = LocalContext.current.resources.locale()
    DialogCard(stringResource(R.string.nag_sleep_title), Modifier.testTag("sleep-dialog")) {
        val now = running
        if (now != null) {
            // what is left, a second at a time
            val left by produceState(timer.remainingMs(), now) {
                while (true) {
                    value = timer.remainingMs()
                    delay(1_000)
                }
            }
            val remaining = left
            Text(
                if (now.waitingForTrackEnd || remaining == null) stringResource(R.string.nag_sleep_at_track_end_now)
                else stringResource(R.string.nag_sleep_left, formatDuration(remaining.coerceAtLeast(1_000))),
                style = NagTheme.type.title,
                color = colors.text,
            )
            val then = when {
                now.waitingForTrackEnd -> if (now.fade) R.string.nag_sleep_then_fade_track else null
                now.atTrackEnd -> if (now.fade) R.string.nag_sleep_then_track_end_fade else R.string.nag_sleep_then_track_end
                now.fade -> R.string.nag_sleep_then_fade
                else -> R.string.nag_sleep_then_stop
            }
            if (then != null) Text(stringResource(then), style = NagTheme.type.labelSmall, color = colors.textTertiary)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (now.endsAt != null) NagButton(stringResource(R.string.nag_sleep_more, 15), { timer.extend(15) })
                NagButton(stringResource(R.string.nag_sleep_off), {
                    timer.cancel()
                    onDismiss()
                })
                NagButton(stringResource(R.string.nag_sleep_close), onDismiss, primary = true, modifier = Modifier.testTag("sleep-close"))
            }
        } else {
            // 0: the track's end
            var minutes by rememberSaveable { mutableIntStateOf(30) }
            var atTrackEnd by rememberSaveable { mutableStateOf(false) }
            var fade by rememberSaveable { mutableStateOf(true) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SleepTimer.PRESETS.forEach { preset ->
                    FilterChip(presetLabel(preset, locale), minutes == preset, { minutes = preset })
                }
                FilterChip(stringResource(R.string.nag_sleep_track_end), minutes == 0, { minutes = 0 })
            }
            if (minutes > 0) {
                NagSwitch(checked = atTrackEnd, onCheckedChange = { atTrackEnd = it }, label = stringResource(R.string.nag_sleep_wait_track))
            } else {
                Text(stringResource(R.string.nag_sleep_track_end_hint), style = NagTheme.type.labelSmall, color = colors.textTertiary)
            }
            NagSwitch(checked = fade, onCheckedChange = { fade = it }, label = stringResource(R.string.nag_sleep_fade))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
                NagButton(
                    stringResource(R.string.nag_sleep_start),
                    {
                        if (minutes == 0) timer.startAtTrackEnd(fade) else timer.start(minutes, atTrackEnd, fade)
                        onDismiss()
                    },
                    primary = true,
                    modifier = Modifier.testTag("sleep-start"),
                )
            }
        }
    }
}

/** "15 dk", "1 sa", "1,5 sa" in Turkish; "15 min", "1 h", "1.5 h" in English. */
@Composable
private fun presetLabel(minutes: Int, locale: Locale): String =
    if (minutes < 60) {
        stringResource(R.string.nag_sleep_minutes, minutes)
    } else {
        stringResource(R.string.nag_sleep_hours, NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(minutes / 60.0))
    }

/**
 * S-1007: the timer's button for the bars: the moon, lit while the timer runs, with what is left
 * beside it; it opens the window. [onlyWhileRunning]: shown only then (the desktop bar's, whose
 * menu bar and palette set the timer).
 */
@Composable
fun SleepTimerButton(
    timer: SleepTimer = Sleep.timer,
    size: Dp = NagTheme.dimens.minTarget.coerceAtLeast(NagTheme.dimens.controlHeight),
    onlyWhileRunning: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val running by timer.state.collectAsState()
    if (onlyWhileRunning && running == null && !open) return
    val left by produceState(timer.remainingMs(), running) {
        while (running?.endsAt != null) {
            value = timer.remainingMs()
            delay(1_000)
        }
        value = timer.remainingMs()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        NagIconButton(NagIcons.Sleep, stringResource(R.string.nag_sleep_title), { open = true }, selected = running != null, size = size)
        val remaining = left
        if (running != null && remaining != null && running?.waitingForTrackEnd == false) {
            Text(formatDuration(remaining.coerceAtLeast(1_000)), style = NagTheme.type.mono, color = NagTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(4.dp))
        }
    }
    if (open) SleepTimerDialog(timer) { open = false }
}
