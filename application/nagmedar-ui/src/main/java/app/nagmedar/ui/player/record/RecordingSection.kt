package app.nagmedar.ui.player.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.player.snapshot.snapshotFolderLabel
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme

/**
 * Settings › Recording (S-607): what Shift+R records, and the folder recordings go to: one chosen
 * once with the system's picker (on a drive too), else Movies, Music or Download/Nağmedar by what
 * was recorded.
 */
@Composable
internal fun RecordingSection(prefs: NagmedarPreferences) {
    val context = LocalContext.current
    val folder = prefs.recordingFolder
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) keepRecordingFolder(context, prefs, uri)
    }
    SettingsSection(stringResource(R.string.nag_rec_settings)) {
        Text(stringResource(R.string.nag_rec_settings_hint), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
        val app = stringResource(R.string.nagmedar_shell_title)
        val defaults = stringResource(R.string.nag_rec_default_folder, defaultRecordingFolder("video/", app), defaultRecordingFolder("audio/", app))
        val label = snapshotFolderLabel(folder, defaults)
        SettingRow(stringResource(R.string.nag_snap_folder), subtitle = label) {
            Row(horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS)) {
                if (folder != null) NagButton(stringResource(R.string.nag_snap_default_folder), { forgetRecordingFolder(context, prefs) })
                NagButton(stringResource(R.string.nag_snap_change_folder), { runCatching { picker.launch(null) } })
            }
        }
    }
}
