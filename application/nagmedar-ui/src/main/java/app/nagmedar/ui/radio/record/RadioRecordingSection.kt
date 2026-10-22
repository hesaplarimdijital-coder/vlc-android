package app.nagmedar.ui.radio.record

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.player.snapshot.snapshotFolderLabel
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme

/**
 * S-1107: Settings › Radio: where the recordings of radio stations go (a folder chosen once with
 * the system's picker, else the app's own music folder), how short a track may be to be kept, a
 * little padding for the cuts, and how much room recording may take and what it does at the limit.
 * Cuts follow the titles a station sends, which are not sample-accurate: the hint says so.
 */
@Composable
internal fun RadioRecordingSection(prefs: NagmedarPreferences) {
    val context = LocalContext.current
    val res = context.resources
    val settings = prefs.radioRecording
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) keepRadioRecordingFolder(context, prefs, uri)
    }
    fun change(edit: (RecordingSettings) -> RecordingSettings) = prefs.updateRadioRecording(edit(prefs.radioRecording))
    SettingsSection(stringResource(R.string.nag_nav_radio)) {
        Text(stringResource(R.string.nag_radio_rec_settings_hint), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
        val label = snapshotFolderLabel(settings.folder, stringResource(R.string.nag_radio_rec_folder_default))
        SettingRow(stringResource(R.string.nag_radio_rec_folder), subtitle = label) {
            Row(horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS)) {
                if (settings.folder != null) NagButton(stringResource(R.string.nag_snap_default_folder), { forgetRadioRecordingFolder(context, prefs) }, modifier = Modifier.testTag("radio-rec-folder-default"))
                NagButton(stringResource(R.string.nag_snap_change_folder), { runCatching { picker.launch(null) } }, modifier = Modifier.testTag("radio-rec-folder-change"))
            }
        }
        SettingRow(stringResource(R.string.nag_radio_rec_min_length), subtitle = stringResource(R.string.nag_radio_rec_min_length_hint)) {
            MenuButton(
                seconds(res, settings.minSeconds, none = R.string.nag_radio_rec_no_minimum),
                entries = {
                    RecordingSettings.MIN_SECONDS.map { value ->
                        MenuEntry.Item(seconds(res, value, none = R.string.nag_radio_rec_no_minimum), { change { it.copy(minSeconds = value) } }, checked = value == settings.minSeconds)
                    }
                },
                modifier = Modifier.testTag("radio-rec-min"),
            )
        }
        NagSwitch(
            checked = settings.keepShort,
            onCheckedChange = { on -> change { it.copy(keepShort = on) } },
            label = stringResource(R.string.nag_radio_rec_keep_short),
            modifier = Modifier.testTag("radio-rec-keep-short"),
        )
        SettingRow(stringResource(R.string.nag_radio_rec_pad_before), subtitle = stringResource(R.string.nag_radio_rec_pad_hint)) {
            MenuButton(
                seconds(res, settings.padBeforeSeconds, none = R.string.nag_radio_rec_no_padding),
                entries = {
                    RecordingSettings.PADDING_SECONDS.map { value ->
                        MenuEntry.Item(seconds(res, value, none = R.string.nag_radio_rec_no_padding), { change { it.copy(padBeforeSeconds = value) } }, checked = value == settings.padBeforeSeconds)
                    }
                },
                modifier = Modifier.testTag("radio-rec-pad-before"),
            )
        }
        SettingRow(stringResource(R.string.nag_radio_rec_pad_after)) {
            MenuButton(
                seconds(res, settings.padAfterSeconds, none = R.string.nag_radio_rec_no_padding),
                entries = {
                    RecordingSettings.PADDING_SECONDS.map { value ->
                        MenuEntry.Item(seconds(res, value, none = R.string.nag_radio_rec_no_padding), { change { it.copy(padAfterSeconds = value) } }, checked = value == settings.padAfterSeconds)
                    }
                },
                modifier = Modifier.testTag("radio-rec-pad-after"),
            )
        }
        Spacer(Modifier.height(NagTheme.dimens.gapS))
        SettingRow(stringResource(R.string.nag_radio_rec_min_free), subtitle = stringResource(R.string.nag_radio_rec_min_free_hint)) {
            val current = (settings.limits.minFreeBytes / StorageLimits.MB).toInt()
            val options = (StorageLimits.MIN_FREE_MB + current).distinct().sorted()
            MenuButton(
                megabytes(res, current),
                entries = {
                    options.map { value ->
                        MenuEntry.Item(megabytes(res, value), { change { it.copy(limits = it.limits.copy(minFreeBytes = value * StorageLimits.MB)) } }, checked = value == current)
                    }
                },
                modifier = Modifier.testTag("radio-rec-min-free"),
            )
        }
        SettingRow(stringResource(R.string.nag_radio_rec_max_total), subtitle = stringResource(R.string.nag_radio_rec_max_total_hint)) {
            val current = (settings.limits.maxTotalBytes / StorageLimits.GB).toInt()
            val options = (StorageLimits.MAX_TOTAL_GB + current).distinct().sortedWith(compareBy({ it == 0 }, { it }))
            MenuButton(
                gigabytes(res, current),
                entries = {
                    options.map { value ->
                        MenuEntry.Item(gigabytes(res, value), { change { it.copy(limits = it.limits.copy(maxTotalBytes = value * StorageLimits.GB)) } }, checked = value == current)
                    }
                },
                modifier = Modifier.testTag("radio-rec-max-total"),
            )
        }
        SettingRow(stringResource(R.string.nag_radio_rec_on_limit), subtitle = stringResource(R.string.nag_radio_rec_on_limit_hint)) {
            SegmentedChoice(
                options = listOf(
                    LimitAction.Stop to stringResource(R.string.nag_radio_rec_on_limit_stop),
                    LimitAction.PruneOldest to stringResource(R.string.nag_radio_rec_on_limit_prune),
                ),
                selected = settings.limits.action,
                onSelect = { action -> change { it.copy(limits = it.limits.copy(action = action)) } },
                modifier = Modifier.testTag("radio-rec-on-limit"),
            )
        }
    }
}

private fun seconds(res: Resources, value: Int, none: Int): String =
    if (value == 0) res.getString(none) else res.getString(R.string.nag_duration_seconds, value)

private fun megabytes(res: Resources, mb: Int): String =
    if (mb >= 1000 && mb % 1000 == 0) res.getString(R.string.nag_radio_rec_unit_gb, mb / 1000) else res.getString(R.string.nag_radio_rec_unit_mb, mb)

private fun gigabytes(res: Resources, gb: Int): String =
    if (gb == 0) res.getString(R.string.nag_radio_rec_no_limit) else res.getString(R.string.nag_radio_rec_unit_gb, gb)
