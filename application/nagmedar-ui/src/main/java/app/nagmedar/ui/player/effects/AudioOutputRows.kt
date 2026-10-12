package app.nagmedar.ui.player.effects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.player.audio.AudioOutputMode
import app.nagmedar.ui.player.audio.AudioSink
import app.nagmedar.ui.player.audio.PassthroughCodec
import app.nagmedar.ui.player.panel.DropButton
import app.nagmedar.ui.player.panel.LabelledRow
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.panel.PanelChip
import app.nagmedar.ui.theme.NagTheme

/** What the effects tab's audio section shows of the output (S-508). */
@Immutable
data class AudioOutputView(
    val mode: AudioOutputMode,
    val codecs: Set<PassthroughCodec>,
    val sinks: List<AudioSink>,
    /** Whether something plugged in takes a bitstream. */
    val canPassthrough: Boolean,
    /** Whether the player refused the choice: its output cannot send a bitstream. */
    val refused: Boolean,
)

/**
 * The audio output (S-508), under the channel layout: how the sound leaves, the codecs forced as
 * a bitstream, and what is plugged in with the codecs each takes.
 */
@Composable
internal fun AudioOutputRows(output: AudioOutputView, onMode: (AudioOutputMode) -> Unit, onToggle: (PassthroughCodec) -> Unit) {
    val colors = NagTheme.colors
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.nag_out_title)
    val entries = AudioOutputMode.entries.map { mode -> MenuEntry.Item(stringResource(mode.label), { onMode(mode) }, checked = mode == output.mode) }
    LabelledRow(label) {
        Box(Modifier.testTag("output-mode")) {
            DropButton(stringResource(output.mode.label), true, label) { open = true }
            MenuPopup(visible = open, onDismiss = { open = false }, entries = entries, offset = IntOffset(0, 0), minWidth = 220.dp)
        }
    }
    if (output.mode == AudioOutputMode.Forced) {
        FlowRow(Modifier.fillMaxWidth().testTag("output-codecs"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PassthroughCodec.entries.forEach { codec ->
                PanelChip(codec.label, selected = codec in output.codecs, enabled = true) { onToggle(codec) }
            }
        }
    }
    when {
        output.refused -> Text(stringResource(R.string.nag_out_refused), style = NagTheme.type.labelSmall, color = colors.warning, modifier = Modifier.testTag("output-note"))
        output.mode == AudioOutputMode.Passthrough && !output.canPassthrough ->
            Text(stringResource(R.string.nag_out_no_bitstream), style = NagTheme.type.labelSmall, color = colors.warning, modifier = Modifier.testTag("output-note"))
    }
    if (output.sinks.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().testTag("output-sinks"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            output.sinks.forEach { sink -> SinkLine(sink) }
        }
    }
}

/** "HDMI · LG TV — AC-3, E-AC-3, DTS": a device, and the codecs it takes as a bitstream. */
@Composable
private fun SinkLine(sink: AudioSink) {
    val colors = NagTheme.colors
    val kind = stringResource(sink.kind.label)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (sink.name == kind) kind else "$kind · ${sink.name}",
            style = NagTheme.type.labelSmall,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PanelCaption(
            if (sink.codecs.isEmpty()) stringResource(R.string.nag_out_pcm_only) else PassthroughCodec.entries.filter { it in sink.codecs }.joinToString(", ") { it.label },
        )
    }
}
