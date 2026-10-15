package app.nagmedar.ui.remoteaccess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.FilterChip
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme
import org.videolan.tools.Settings

/** The remote access settings over the app's preferences, following them while the page is shown. */
@Composable
internal fun rememberRemoteAccessSettings(): RemoteAccessSettings {
    val context = LocalContext.current.applicationContext
    val settings = remember(context) { RemoteAccessSettings(Settings.getInstance(context), AppRemoteAccessControl(context)) }
    DisposableEffect(settings) {
        settings.attach()
        onDispose { settings.detach() }
    }
    return settings
}

/**
 * S-1304: Settings › Remote access: the switch, what a browser may see and do, how it is kept safe
 * (said, not set: the one-time code and the encrypted connection are always there), and the pages
 * with the addresses and with how it works. The screenshot and unit tests give [settings] over
 * their own preferences and a recording control; the app builds its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RemoteAccessSection(settings: RemoteAccessSettings = rememberRemoteAccessSettings()) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    SettingsSection(stringResource(R.string.nag_ra_settings)) {
        SettingRow(stringResource(R.string.nag_ra_enable), subtitle = stringResource(R.string.nag_ra_enable_hint)) {
            RaSwitch(settings.enabled, settings::enable, "ra-enable")
        }
        Column(Modifier.fillMaxWidth().padding(vertical = dimens.gapS)) {
            Text(stringResource(R.string.nag_ra_library), style = NagTheme.type.bodyStrong, color = colors.text)
            Text(stringResource(R.string.nag_ra_library_hint), style = NagTheme.type.labelSmall, color = colors.textTertiary)
            Spacer(Modifier.height(dimens.gapS))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryPart.entries.forEach { part ->
                    FilterChip(
                        label = stringResource(part.label),
                        selected = part in settings.library,
                        onToggle = { settings.showLibraryPart(part, it) },
                        modifier = Modifier.testTag("ra-part-${part.name}"),
                    )
                }
            }
        }
        SettingRow(stringResource(R.string.nag_ra_files), subtitle = stringResource(R.string.nag_ra_files_hint)) {
            RaSwitch(settings.files, settings::showFiles, "ra-files")
        }
        SettingRow(stringResource(R.string.nag_ra_network), subtitle = stringResource(R.string.nag_ra_network_hint)) {
            RaSwitch(settings.network, settings::showNetwork, "ra-network")
        }
        SettingRow(stringResource(R.string.nag_ra_history), subtitle = stringResource(R.string.nag_ra_history_hint)) {
            RaSwitch(settings.history, settings::showHistory, "ra-history")
        }
        SettingRow(stringResource(R.string.nag_ra_playback), subtitle = stringResource(R.string.nag_ra_playback_hint)) {
            RaSwitch(settings.playback, settings::controlPlayback, "ra-playback")
        }
        SettingRow(stringResource(R.string.nag_ra_logs), subtitle = stringResource(R.string.nag_ra_logs_hint)) {
            RaSwitch(settings.logs, settings::shareLogs, "ra-logs")
        }
        Column(Modifier.fillMaxWidth().padding(vertical = dimens.gapS)) {
            Text(stringResource(R.string.nag_ra_security), style = NagTheme.type.bodyStrong, color = colors.text)
            Text(
                stringResource(R.string.nag_ra_security_hint),
                style = NagTheme.type.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.testTag("ra-security"),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NagButton(
                stringResource(R.string.nag_ra_addresses),
                settings::showAddresses,
                enabled = settings.enabled,
                primary = settings.enabled,
                modifier = Modifier.testTag("ra-addresses"),
            )
            NagButton(stringResource(R.string.nag_ra_how), settings::showHowItWorks, modifier = Modifier.testTag("ra-how"))
        }
    }
}

@Composable
private fun RaSwitch(checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    NagSwitch(
        checked = checked,
        onCheckedChange = onChange,
        label = stringResource(if (checked) R.string.nag_ra_on else R.string.nag_ra_off),
        modifier = Modifier.testTag(tag),
    )
}

private val LibraryPart.label: Int
    get() = when (this) {
        LibraryPart.Video -> R.string.nag_ra_part_video
        LibraryPart.Audio -> R.string.nag_ra_part_audio
        LibraryPart.Playlists -> R.string.nag_ra_part_playlists
        LibraryPart.Search -> R.string.nag_ra_part_search
    }
