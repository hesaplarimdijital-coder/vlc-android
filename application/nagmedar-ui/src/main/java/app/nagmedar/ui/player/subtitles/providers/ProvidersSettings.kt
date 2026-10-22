package app.nagmedar.ui.player.subtitles.providers

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.onboarding.FolderGrants
import app.nagmedar.ui.player.subtitles.translate.TranslationModelRows
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme

/** One provider as Settings shows it: its place, its capabilities, whether it is on and whether it can be asked. */
data class ProviderRow(
    val id: String,
    val name: String,
    val capabilities: ProviderCapabilities,
    val enabled: Boolean,
    /** What the provider still needs of the user: a folder. */
    val needsFolder: Boolean = false,
    val folderChosen: Boolean = false,
    /** This build has no key for it. */
    val keyMissing: Boolean = false,
)

/**
 * Settings › Subtitles (S-707): the providers subtitles are searched in, each turned on or off
 * and moved earlier or later (the order is the order of preference when two have the same file);
 * what each can be asked and what it asks in return; the folder of the local provider.
 */
@Composable
fun SubtitleProvidersSection(
    rows: List<ProviderRow>,
    folderName: String?,
    onEnabled: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
    onChooseFolder: () -> Unit,
    onForgetFolder: () -> Unit,
    folderError: Boolean = false,
    /** S-710: what else the section holds: the translation's models. */
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val dimens = NagTheme.dimens
    SettingsSection(stringResource(R.string.nag_prov_settings)) {
        Text(stringResource(R.string.nag_prov_hint), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
        Spacer(Modifier.height(dimens.gapS))
        rows.forEachIndexed { index, row ->
            SettingRow(row.name, modifier = Modifier.testTag("provider-${row.id}"), subtitle = capabilityLine(row)) {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalAlignment = Alignment.CenterVertically) {
                    NagButton(stringResource(R.string.nag_prov_up), { onMove(row.id, -1) }, enabled = index > 0, modifier = Modifier.testTag("provider-up-${row.id}"))
                    NagButton(stringResource(R.string.nag_prov_down), { onMove(row.id, 1) }, enabled = index < rows.lastIndex, modifier = Modifier.testTag("provider-down-${row.id}"))
                    NagSwitch(
                        checked = row.enabled,
                        onCheckedChange = { onEnabled(row.id, it) },
                        label = stringResource(if (row.enabled) R.string.nag_prov_on else R.string.nag_prov_off),
                        modifier = Modifier.testTag("provider-switch-${row.id}"),
                    )
                }
            }
            if (row.keyMissing) {
                Text(stringResource(R.string.nag_prov_no_key), style = NagTheme.type.labelSmall, color = NagTheme.colors.warning, modifier = Modifier.testTag("provider-nokey-${row.id}"))
            }
            if (row.needsFolder) {
                SettingRow(
                    stringResource(R.string.nag_prov_folder),
                    subtitle = folderName ?: stringResource(R.string.nag_prov_folder_none),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                        if (row.folderChosen) NagButton(stringResource(R.string.nag_prov_folder_forget), onForgetFolder, modifier = Modifier.testTag("provider-folder-forget"))
                        NagButton(stringResource(R.string.nag_prov_folder_choose), onChooseFolder, modifier = Modifier.testTag("provider-folder-choose"))
                    }
                }
                if (folderError) {
                    Text(stringResource(R.string.nag_prov_folder_failed), style = NagTheme.type.labelSmall, color = NagTheme.colors.danger, modifier = Modifier.testTag("provider-folder-error"))
                }
            }
        }
        extra()
    }
}

/** "Searches by fingerprint, IMDb number, name · 5 downloads a day · needs a key". */
@Composable
private fun capabilityLine(row: ProviderRow): String {
    val c = row.capabilities
    val by = listOfNotNull(
        stringResource(R.string.nag_prov_by_hash).takeIf { c.searchByHash },
        stringResource(R.string.nag_prov_by_imdb).takeIf { c.searchByImdb },
        stringResource(R.string.nag_prov_by_name).takeIf { c.searchByName },
        stringResource(R.string.nag_prov_by_episode).takeIf { c.searchBySeasonEpisode },
        stringResource(R.string.nag_prov_by_language).takeIf { c.searchByLanguage },
    ).joinToString(", ")
    val limit = c.rateLimit?.downloadsPerDay?.let { pluralStringResource(R.plurals.nag_prov_limit, it, it) }
    return listOfNotNull(
        stringResource(R.string.nag_prov_searches_by, by),
        limit,
        stringResource(R.string.nag_prov_needs_key).takeIf { c.needsKey },
        stringResource(R.string.nag_prov_needs_login).takeIf { c.needsLogin },
        stringResource(R.string.nag_prov_no_network).takeIf { !c.needsKey && !c.needsLogin && c.rateLimit == null && row.needsFolder },
    ).joinToString(" · ")
}

/** The settings screen's section, on the app's registry. */
@Composable
fun SubtitleProvidersSettings() {
    val context = LocalContext.current
    val registry = remember(context) { SubtitleProviders.registry(context) }
    val store = remember(context) { SubtitleProviders.folder(context) }
    var folder by remember { mutableStateOf(store.get()) }
    var folderError by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val resolver = context.contentResolver
            folderError = !FolderPermission.replace(
                old = folder,
                new = uri.toString(),
                take = { resolver.takePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION) },
                // the same folder may serve the library or the recordings: they keep it
                release = { FolderGrants.release(context, it, FolderGrants.READ) },
                store = store,
            )
            folder = store.get()
        }
    }
    val rows = registry.ordered.map { provider ->
        ProviderRow(
            id = provider.id,
            name = provider.displayName,
            capabilities = provider.capabilities,
            enabled = registry.isEnabled(provider.id),
            needsFolder = provider.id == LocalFolderProvider.ID,
            folderChosen = provider.id == LocalFolderProvider.ID && folder != null,
            keyMissing = provider.capabilities.needsKey && provider.unavailable() != null,
        )
    }
    SubtitleProvidersSection(
        rows = rows,
        folderName = folder?.let(::folderLabel),
        onEnabled = registry::setEnabled,
        onMove = registry::move,
        onChooseFolder = { runCatching { picker.launch(null) } },
        onForgetFolder = {
            val before = folder
            store.set(null)
            folder = null
            folderError = false
            before?.let { FolderGrants.release(context, it, FolderGrants.READ) }
        },
        folderError = folderError,
        extra = {
            Spacer(Modifier.height(NagTheme.dimens.gapM))
            TranslationModelRows()
        },
    )
}

/** The permission of the subtitle folder, kept for one folder at a time. */
internal object FolderPermission {
    /**
     * [new] becomes the folder once [take] has kept its permission; then [old]'s permission is
     * released (it is not needed again). When [take] fails nothing is stored and [old] stays as
     * it was; false then.
     */
    fun replace(old: String?, new: String, take: (String) -> Unit, release: (String) -> Unit, store: SubtitleFolderStore): Boolean {
        if (new == old) {
            // the same folder again: only make sure its permission is held
            return runCatching { take(new) }.isSuccess
        }
        if (runCatching { take(new) }.isFailure) return false
        store.set(new)
        if (old != null) runCatching { release(old) }
        return true
    }
}

/** "Download/Subtitles" from a tree's address "…/tree/primary%3ADownload%2FSubtitles". */
internal fun folderLabel(tree: String): String {
    val id = Uri.parse(tree).lastPathSegment.orEmpty()
    return id.substringAfter(':', id).ifEmpty { id }
}
