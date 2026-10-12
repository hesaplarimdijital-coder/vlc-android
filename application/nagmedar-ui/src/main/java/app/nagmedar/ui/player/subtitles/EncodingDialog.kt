package app.nagmedar.ui.player.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.theme.NagTheme

/**
 * A subtitle file of the playing film, to read in another character set (S-702): its name, its
 * first bytes for the preview, what they were detected as and what the user chose for the film
 * (null: detected).
 */
@Immutable
class EncodingSample(val name: String, val head: ByteArray, val detected: SubtitleEncoding, val chosen: SubtitleEncoding?)

/** "Subtitle encoding…" (S-702) in its own window, over the video. */
@Composable
fun EncodingDialog(sample: EncodingSample, onApply: (SubtitleEncoding?) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) { EncodingSurface(sample, onApply, onDismiss) }
    }
}

/**
 * The film's subtitle file and the character sets to read it in: "Automatic" (what it was
 * detected as), UTF-8, Turkish in Windows-1254 and ISO-8859-9, and the other code pages; under
 * them its lines as the one chosen reads them, those with Turkish letters first; the choice is
 * kept for the film.
 */
@Composable
fun EncodingSurface(sample: EncodingSample, onApply: (SubtitleEncoding?) -> Unit, onDismiss: () -> Unit) {
    val colors = NagTheme.colors
    var selected by remember { mutableStateOf(sample.chosen) }
    val reading = selected ?: sample.detected
    val apply = {
        onApply(selected)
        onDismiss()
    }
    DialogCard(
        stringResource(R.string.nag_enc_title),
        Modifier
            .testTag("encoding-dialog")
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        onDismiss()
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        apply()
                        true
                    }
                    else -> false
                }
            },
    ) {
        Text(sample.name, style = NagTheme.type.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .selectableGroup(),
        ) {
            val automatic = stringResource(R.string.nag_enc_automatic, stringResource(sample.detected.label))
            ChoiceLine(automatic, selected == null, tag = "encoding-auto") { selected = null }
            SubtitleEncoding.choices.forEach { encoding ->
                ChoiceLine(stringResource(encoding.label), selected == encoding, tag = "encoding-${encoding.key}") { selected = encoding }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(8.dp))
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("encoding-preview"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.nag_enc_preview).uppercase(currentLocale()), style = NagTheme.type.caption, color = colors.textTertiary)
            val lines = remember(reading) { previewLines(decodeSubtitle(sample.head, reading)) }
            if (lines.isEmpty()) {
                Text(stringResource(R.string.nag_enc_no_lines), style = NagTheme.type.labelSmall, color = colors.textTertiary)
            } else {
                lines.forEach { Text(it, style = NagTheme.type.label, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nag_enc_kept), style = NagTheme.type.labelSmall, color = colors.textTertiary, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
                NagButton(stringResource(R.string.nag_enc_apply), { apply() }, primary = true, modifier = Modifier.testTag("encoding-apply"))
            }
        }
    }
}

/** A character set to choose: a round mark and its name; the whole line chooses it. */
@Composable
private fun ChoiceLine(text: String, chosen: Boolean, tag: String, onChoose: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .background(stateBackground(state), shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .selectable(selected = chosen, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onChoose)
            .padding(horizontal = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .border(1.5.dp, if (chosen) colors.accent else colors.textTertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (chosen) Box(Modifier.size(8.dp).background(colors.accent, CircleShape))
        }
        Text(text, style = NagTheme.type.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
    }
}
