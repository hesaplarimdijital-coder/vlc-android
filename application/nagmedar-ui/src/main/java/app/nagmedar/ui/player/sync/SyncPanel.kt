package app.nagmedar.ui.player.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.panel.ChoiceRow
import app.nagmedar.ui.player.panel.PanelBadge
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.panel.PanelRule
import app.nagmedar.ui.player.panel.PanelSection
import app.nagmedar.ui.player.panel.PendingNote
import app.nagmedar.ui.player.panel.SectionTitle
import app.nagmedar.ui.player.panel.decimal
import app.nagmedar.ui.player.panel.signed
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.PlexMono

/**
 * The sync tab of the player's panel (S-604), after the mockups' "Panel · Sync": the audio delay
 * and the subtitles' delay in 10 and 50 ms steps, the subtitles' speed between frame rates, then
 * "Remember for this file" and "Reset all delays". The delays are the player's, read back while
 * the tab shows; a positive delay makes the sound or the subtitles come later.
 */
@Composable
fun SyncPanel(
    values: SyncValues,
    remembered: Boolean,
    supportsRate: Boolean,
    onAudioDelay: (Long) -> Unit,
    onSubtitleDelay: (Long) -> Unit,
    onRate: (SubtitleRate) -> Unit,
    onResetAll: () -> Unit,
    onRemember: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Under the video in a wide window: two columns of sections. */
    columns: Int = 1,
) {
    Column(modifier.fillMaxSize().testTag("sync-panel")) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            val audio = @Composable {
                DelaySection(
                    title = stringResource(R.string.nag_sync_audio_delay),
                    valueMs = values.audioDelayMs,
                    remembered = remembered,
                    hint = stringResource(R.string.nag_sync_audio_hint),
                    keys = ShortcutId.AudioDelayDown to ShortcutId.AudioDelayUp,
                    tag = "sync-audio",
                    onSet = onAudioDelay,
                )
            }
            val subtitles = @Composable {
                DelaySection(
                    title = stringResource(R.string.nag_sync_subtitle_delay),
                    valueMs = values.subtitleDelayMs,
                    remembered = remembered,
                    hint = stringResource(R.string.nag_sync_subtitle_hint),
                    keys = ShortcutId.SubtitleDelayDown to ShortcutId.SubtitleDelayUp,
                    tag = "sync-subtitles",
                    onSet = onSubtitleDelay,
                )
            }
            val rate = @Composable { RateSection(values.subtitleRate, supportsRate, onRate) }
            if (columns >= 2) {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        audio()
                        PanelRule()
                        subtitles()
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        rate()
                    }
                }
            } else {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    audio()
                    PanelRule()
                    subtitles()
                    rate()
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NagCheckbox(remembered, onRemember, stringResource(R.string.nag_sync_remember), Modifier.testTag("sync-remember"))
            NagButton(
                stringResource(R.string.nag_sync_reset_all),
                onClick = onResetAll,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp).testTag("sync-reset-all"),
            )
        }
    }
}

/**
 * A delay: its value at the section's end ("this file" when the film remembers it), the steps
 * the mockup has (−50, −10, Reset, +10, +50), what a positive delay means and the keys that
 * move it by 50 ms.
 */
@Composable
private fun DelaySection(
    title: String,
    valueMs: Long,
    remembered: Boolean,
    hint: String,
    keys: Pair<ShortcutId, ShortcutId>,
    tag: String,
    onSet: (Long) -> Unit,
) {
    val locale = currentLocale()
    val colors = NagTheme.colors
    val resources = LocalContext.current.resources
    PanelSection(spacing = 8.dp) {
        SectionTitle(title) {
            if (remembered) PanelBadge(stringResource(R.string.nag_sync_this_file), Modifier.padding(end = 8.dp))
            Text(
                stringResource(R.string.nag_sync_ms, signed(valueMs, locale)),
                style = NagTheme.type.title.copy(fontFamily = PlexMono),
                color = if (valueMs == 0L) colors.textSecondary else colors.text,
                maxLines = 1,
                modifier = Modifier.testTag("$tag-value"),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (step in listOf(-SyncController.STEP_MS, -SyncController.SMALL_STEP_MS)) {
                StepButton(signed(step, locale), stringResource(R.string.nag_sync_step, title, signed(step, locale)), tag) { onSet(valueMs + step) }
            }
            StepButton(stringResource(R.string.nag_fx_reset), stringResource(R.string.nag_sync_reset, title), tag, enabled = valueMs != 0L, numeric = false) { onSet(0) }
            for (step in listOf(SyncController.SMALL_STEP_MS, SyncController.STEP_MS)) {
                StepButton(signed(step, locale), stringResource(R.string.nag_sync_step, title, signed(step, locale)), tag) { onSet(valueMs + step) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PanelCaption(hint, Modifier.weight(1f))
            val down = ShortcutRegistry.chordLabel(keys.first, resources)
            val up = ShortcutRegistry.chordLabel(keys.second, resources)
            if (down != null && up != null) {
                KeyCap(down)
                KeyCap(up)
                PanelCaption(stringResource(R.string.nag_sync_keys_step))
            }
        }
    }
}

/** A step of a delay: one of five buttons as wide as each other; the steps in figures, "Reset" in words. */
@Composable
private fun RowScope.StepButton(text: String, label: String, tag: String, enabled: Boolean = true, numeric: Boolean = true, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 28.dp)
            .border(1.dp, colors.borderStrong, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onClick, enabled = enabled, onClickLabel = label)
            .semantics { contentDescription = label }
            .testTag("$tag-step"),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (numeric) NagTheme.type.mono else NagTheme.type.labelSmall, color = if (enabled) colors.text else colors.textTertiary, maxLines = 1)
    }
}

/** A key as it is written on the keyboard. */
@Composable
private fun KeyCap(text: String) {
    Text(
        text,
        style = NagTheme.type.mono,
        color = NagTheme.colors.textSecondary,
        maxLines = 1,
        modifier = Modifier
            .border(1.dp, NagTheme.colors.borderStrong, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp),
    )
}

/** The subtitles' speed: as they are or converted between 23,976 and 25 frames a second; its factor at the end. */
@Composable
private fun RateSection(rate: SubtitleRate, supported: Boolean, onRate: (SubtitleRate) -> Unit) {
    val locale = currentLocale()
    val factor = decimal(rate.factor, 4, locale) + "×"
    PanelSection(spacing = 8.dp) {
        SectionTitle(stringResource(R.string.nag_sync_rate)) {
            Text(factor, style = NagTheme.type.mono, color = NagTheme.colors.text, maxLines = 1, modifier = Modifier.testTag("sync-rate-value"))
        }
        ChoiceRow(
            options = SubtitleRate.entries.map { it to stringResource(it.label) },
            selected = rate,
            enabled = supported,
            onSelect = onRate,
        )
        PanelCaption(if (rate == SubtitleRate.Same) stringResource(rate.hint) else stringResource(rate.hint, factor))
        if (!supported) PendingNote("sync-pending")
    }
}
