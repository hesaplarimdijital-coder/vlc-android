package app.nagmedar.ui.tags

import android.text.format.Formatter
import java.text.NumberFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.tags.editor.MultiValueMode
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.NagTextField
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.quality.LibraryChecks
import app.nagmedar.ui.screens.SettingsSection
import kotlinx.coroutines.launch

/**
 * 1.2.0: Settings › Etiket düzenleyici (Metamatik's): how fields of several values are written (one by one, or joined
 * with the separator, which is also what typed values are split on), 1.3.0: the catalogues looked up, 1.4.0: the
 * library's checks, and the saves' backups, which can be deleted.
 * The screenshot tests give [backups]; the app counts them itself.
 */
@Composable
internal fun TagEditorSection(backups: Pair<Int, Long>? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(TagSettings.style(context)) }
    var separator by remember { mutableStateOf(style.separator) }
    var kept by remember { mutableStateOf(backups) }
    if (backups == null) {
        LaunchedEffect(Unit) { kept = runCatching { TagCenter.backupSize(context) }.getOrNull() }
    }
    val update = { next: MultiValueStyle ->
        style = next
        TagSettings.update(context, next)
    }
    SettingsSection(stringResource(R.string.nag_tag_settings)) {
        SettingRow(stringResource(R.string.nag_tag_multi_mode), subtitle = stringResource(R.string.nag_tag_multi_mode_hint)) {
            SegmentedChoice(
                listOf(
                    MultiValueMode.Separate to stringResource(R.string.nag_tag_multi_separate),
                    MultiValueMode.Joined to stringResource(R.string.nag_tag_multi_joined),
                ),
                style.mode,
                { mode -> update(style.copy(mode = mode)) },
                modifier = Modifier.testTag("tags-multi-mode"),
            )
        }
        SettingRow(stringResource(R.string.nag_tag_separator), subtitle = stringResource(R.string.nag_tag_separator_hint)) {
            Box(Modifier.width(96.dp)) {
                NagTextField(
                    value = separator,
                    onValueChange = { typed ->
                        separator = typed
                        // a separator of spaces alone separates nothing: the one before stays
                        if (typed.isNotBlank()) update(style.copy(separator = typed))
                    },
                    label = stringResource(R.string.nag_tag_separator),
                    showLabel = false,
                    modifier = Modifier.testTag("tags-separator"),
                )
            }
        }
        // 1.3.0: where details, covers and albums are looked up
        var catalogues by remember { mutableStateOf(TagSettings.catalogues(context)) }
        SettingRow(stringResource(R.string.nag_tag_catalogues), subtitle = stringResource(R.string.nag_tag_catalogues_hint)) {
            Column {
                TagLookups.catalogues.forEach { (id, name) ->
                    NagCheckbox(
                        id in catalogues,
                        { on ->
                            catalogues = if (on) catalogues + id else catalogues - id
                            TagSettings.updateCatalogues(context, catalogues)
                        },
                        name,
                        modifier = Modifier.testTag("tags-catalogue-$id"),
                    )
                }
            }
        }
        // 1.4.0: the library's checks (Metamatik's), in the background, and how far they have got
        var checks by remember { mutableStateOf(TagSettings.libraryChecks(context)) }
        val quality by LibraryChecks.quality.collectAsState()
        val read by LibraryChecks.tags.collectAsState()
        val numbers = remember(context) { NumberFormat.getIntegerInstance(context.resources.locale()) }
        SettingRow(
            stringResource(R.string.nag_tag_checks),
            subtitle = listOfNotNull(
                stringResource(R.string.nag_tag_checks_hint),
                stringResource(R.string.nag_tag_checks_lossless, numbers.format(quality.checked), numbers.format(quality.total)).takeIf { checks && quality.total > 0 },
                stringResource(R.string.nag_tag_checks_tags, numbers.format(read.checked), numbers.format(read.total)).takeIf { checks && read.total > 0 },
            ).joinToString("\n"),
        ) {
            NagSwitch(
                checks,
                { on ->
                    checks = on
                    LibraryChecks.setEnabled(context, on)
                },
                stringResource(if (checks) R.string.nag_prefs_on else R.string.nag_prefs_off),
                modifier = Modifier.testTag("tags-library-checks"),
            )
        }
        val count = kept
        SettingRow(
            stringResource(R.string.nag_tag_backups),
            subtitle = listOfNotNull(
                when {
                    count == null -> null
                    count.first == 0 -> stringResource(R.string.nag_tag_backups_none)
                    else -> pluralStringResource(R.plurals.nag_tag_backups_count, count.first, count.first, Formatter.formatShortFileSize(context, count.second))
                },
                stringResource(R.string.nag_tag_backups_hint),
            ).joinToString("\n"),
        ) {
            NagButton(
                stringResource(R.string.nag_tag_backups_clear),
                {
                    if (backups != null) {
                        kept = 0 to 0L
                    } else {
                        scope.launch {
                            runCatching { TagCenter.files(context).backups.clear() }
                            kept = runCatching { TagCenter.backupSize(context) }.getOrNull()
                        }
                    }
                },
                enabled = (count?.first ?: 0) > 0,
                modifier = Modifier.testTag("tags-backups-clear"),
            )
        }
    }
}
