package app.nagmedar.ui.podcast

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat

/**
 * S-1101: "RSS adresiyle ekle": the address of a feed asked for in a small window. It is checked as
 * it is typed (is it a web address at all?), and when it is sent the window shows that the feed is
 * being read and, if it is no podcast feed, says why in words, and stays so that the address can be mended.
 */
@Composable
fun AddAddressDialog(add: AddUi, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) { AddAddressSurface(add, onSubmit, onDismiss) }
    }
}

@Composable
internal fun AddAddressSurface(add: AddUi, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val focusManager = LocalFocusManager.current
    var value by remember { mutableStateOf(TextFieldValue(add.input, TextRange(add.input.length))) }
    val valid = FeedUrl.normalize(value.text) != null
    val send = { if (valid && !add.working) onSubmit(value.text.trim()) }
    // the reason stays while the address it was given for does; typing makes it stale
    val failure = add.failure?.takeIf { value.text.trim() == add.input.trim() }
    val problem = when {
        failure != null -> failure.describe(res)
        value.text.isNotBlank() && !valid -> res.getString(R.string.nag_pod_fail_address)
        else -> null
    }
    DialogCard(stringResource(R.string.nag_pod_add_title), Modifier.testTag("podcast-add-dialog"), cardWidth = 460.dp) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val label = stringResource(R.string.nag_pod_add_label)
        Text(stringResource(R.string.nag_pod_add_hint), style = NagTheme.type.label, color = colors.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            enabled = !add.working,
            singleLine = true,
            textStyle = NagTheme.type.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { send() }),
            decorationBox = { field ->
                Box {
                    if (value.text.isEmpty()) Text(stringResource(R.string.nag_pod_add_example), style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (problem != null) toneColors(StateTone.Warning).base else colors.accent, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .focusRequester(focus)
                .semantics { contentDescription = label }
                .testTag("podcast-add-field")
                .tabMovesFocus(focusManager)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            send()
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
        if (add.working) {
            Text(stringResource(R.string.nag_pod_reading), style = NagTheme.type.label, color = colors.textSecondary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            NagProgressBar(null, stringResource(R.string.nag_pod_reading))
        } else if (problem != null) {
            Warning(problem, Modifier.testTag("podcast-add-warning"))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
            NagButton(
                stringResource(R.string.nag_pod_subscribe),
                { send() },
                primary = true,
                enabled = valid && !add.working,
                modifier = Modifier.testTag("podcast-add-submit"),
            )
        }
    }
}

/** A warning line: the warning's icon and its words, announced when they appear. */
@Composable
private fun Warning(text: String, modifier: Modifier = Modifier) {
    val tone = toneColors(StateTone.Warning)
    Row(modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(NagIcons.Warning, contentDescription = null, tint = tone.icon, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Text(text, style = NagTheme.type.label, color = tone.content)
    }
}

/**
 * S-1102: an OPML import's window: the file being read, the feeds being added with how far it is
 * ("Durdur" stops it; what was added stays), then how it went: how many were added, how many were
 * subscribed to already and which could not be, each with its reason. A file that is no OPML list is said so.
 */
@Composable
fun ImportDialog(state: ImportUi, onDismiss: () -> Unit) {
    NagDialog(onDismissRequest = onDismiss) {
        // a tap beside the window does not stop a running import
        DialogFrame(if (state is ImportUi.Done || state is ImportUi.Unusable) onDismiss else ({})) { ImportSurface(state, onDismiss) }
    }
}

@Composable
internal fun ImportSurface(state: ImportUi, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val locale = currentLocale()
    val title = stringResource(R.string.nag_pod_import_title)
    DialogCard(title, Modifier.testTag("podcast-import-dialog"), cardWidth = 460.dp) {
        when (state) {
            ImportUi.Reading -> {
                Text(stringResource(R.string.nag_pod_import_reading), style = NagTheme.type.body, color = colors.textSecondary)
                NagProgressBar(null, stringResource(R.string.nag_pod_import_reading))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { NagButton(stringResource(R.string.nag_sc_cancel), onDismiss) }
            }
            is ImportUi.Running -> {
                val numbers = NumberFormat.getIntegerInstance(locale)
                val progress = stringResource(R.string.nag_pod_import_progress, numbers.format(state.done), numbers.format(state.total))
                Text(progress, style = NagTheme.type.body, color = colors.text, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                NagProgressBar(if (state.total > 0) state.done.toFloat() / state.total else null, progress)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { NagButton(stringResource(R.string.nag_pod_import_stop), onDismiss) }
            }
            is ImportUi.Done -> {
                val summary = state.summary
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    if (summary.added > 0) SummaryLine(NagIcons.SuccessCircle, StateTone.Success, pluralStringResource(R.plurals.nag_pod_import_added, summary.added, summary.added), "podcast-import-added")
                    if (summary.already > 0) SummaryLine(NagIcons.Info, StateTone.Neutral, pluralStringResource(R.plurals.nag_pod_import_already, summary.already, summary.already), "podcast-import-already")
                    if (summary.failed.isNotEmpty()) {
                        SummaryLine(NagIcons.Warning, StateTone.Warning, pluralStringResource(R.plurals.nag_pod_import_failed, summary.failed.size, summary.failed.size), "podcast-import-failed")
                    }
                }
                if (summary.failed.isNotEmpty()) {
                    if (summary.offline) Text(stringResource(R.string.nag_pod_import_offline), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        summary.failed.forEach { failed ->
                            Column {
                                Text(failed.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(failed.failure.describe(res), style = NagTheme.type.labelSmall, color = toneColors(StateTone.Warning).content)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    NagButton(stringResource(R.string.nag_pod_ok), onDismiss, primary = true, modifier = Modifier.testTag("podcast-import-ok"))
                }
            }
            is ImportUi.Unusable -> {
                Warning(state.problem.describe(res), Modifier.testTag("podcast-import-unusable"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    NagButton(stringResource(R.string.nag_pod_ok), onDismiss, primary = true)
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(icon: ImageVector, tone: StateTone, text: String, tag: String) {
    val colors = toneColors(tone)
    Row(Modifier.testTag(tag), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = colors.icon, modifier = Modifier.size(18.dp))
        Text(text, style = NagTheme.type.body, color = NagTheme.colors.text)
    }
}
