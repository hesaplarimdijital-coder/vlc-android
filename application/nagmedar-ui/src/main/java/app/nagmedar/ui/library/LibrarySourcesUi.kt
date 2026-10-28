package app.nagmedar.ui.library

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.IconMenuButton
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.onboarding.StorageSource
import app.nagmedar.ui.onboarding.folderName
import app.nagmedar.ui.onboarding.storageSources
import app.nagmedar.ui.components.DialogCard
import app.nagmedar.ui.components.DialogFrame
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.AppScope

/** The section's name as the navigation writes it. */
@Composable
internal fun sectionLabel(section: LibrarySection): String = stringResource(
    when (section) {
        LibrarySection.Music -> R.string.nag_nav_music
    },
)

/** Settings › Library: the folders of each section (since 1.0 the music's), each with its own list. */
@Composable
fun LibrarySourcesSection(prefs: NagmedarPreferences) {
    val sources = rememberSourceActions(prefs)
    SettingsSection(stringResource(R.string.nag_section_library)) {
        Text(stringResource(R.string.nag_lib_sources_hint), style = NagTheme.type.body, color = NagTheme.colors.textSecondary)
        LibrarySection.entries.forEach { section ->
            Spacer(Modifier.height(NagTheme.dimens.gapS))
            HorizontalRule()
            SectionSourcesEditor(section, prefs.librarySources, sources)
        }
    }
}

/**
 * One section's folders in a small window, opened from its "Kaynak klasörler" button and its
 * empty state's "Klasör ekle"; each change is kept as it is made.
 */
@Composable
fun SectionSourcesDialog(section: LibrarySection, prefs: NagmedarPreferences, onDismiss: () -> Unit) {
    val sources = rememberSourceActions(prefs)
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.nag_lib_sources_for, sectionLabel(section)), Modifier.testTag("library-sources"), cardWidth = 520.dp) {
                Text(stringResource(R.string.nag_lib_sources_hint), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                    SectionSourcesEditor(section, prefs.librarySources, sources)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    NagButton(stringResource(R.string.nag_lib_done), onDismiss, primary = true)
                }
            }
        }
    }
}

/** What the editors do: the system picker, a suggested folder, a folder left out or taken off. */
internal class SourceActions(
    val suggestions: List<SuggestedFolder>,
    val storages: List<StorageSource>,
    val pick: (LibrarySection) -> Unit,
    val add: (LibrarySection, SourceFolder) -> Unit,
    val toggleExclude: (LibrarySection, SourceFolder) -> Unit,
    val remove: (LibrarySection, SourceFolder) -> Unit,
)

@Composable
internal fun rememberSourceActions(prefs: NagmedarPreferences): SourceActions {
    val context = LocalContext.current.applicationContext
    // the storages and their folders are read once, off the main thread
    val suggestions by produceState(emptyList<SuggestedFolder>()) { value = LibrarySources.suggestions(context) }
    val storages by produceState(emptyList<StorageSource>()) { value = withContext(Dispatchers.IO) { storageSources(context) } }
    var pending by rememberSaveable { mutableStateOf<LibrarySection?>(null) }
    val add: (LibrarySection, SourceFolder) -> Unit = { section, folder -> addSource(context, prefs, section, folder) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val section = pending
        pending = null
        if (uri != null && section != null) {
            LibrarySources.keepPermission(context, uri)
            add(section, SourceFolder(uri.toString()))
        }
    }
    return SourceActions(
        suggestions = suggestions,
        storages = storages,
        pick = { section ->
            pending = section
            runCatching { picker.launch(null) }
        },
        add = add,
        toggleExclude = { section, folder -> prefs.updateLibrarySources(prefs.librarySources.plus(section, folder.copy(exclude = !folder.exclude))) },
        remove = { section, folder -> prefs.updateLibrarySources(prefs.librarySources.minus(section, folder.location)) },
    )
}

/** [folder] joins [section]; a folder to show that the medialibrary does not index yet is given to it to scan. */
private fun addSource(context: Context, prefs: NagmedarPreferences, section: LibrarySection, folder: SourceFolder) {
    prefs.updateLibrarySources(prefs.librarySources.plus(section, folder))
    if (!folder.exclude) AppScope.launch { runCatching { LibrarySources.ensureIndexed(context, folder.location) } }
}

/**
 * One section's line (how many folders it shows, and the button that adds one) and a row for each
 * of its folders. Settings › Library and the first run's Sources step both show it.
 */
@Composable
internal fun SectionSourcesEditor(section: LibrarySection, scopes: LibraryScopes, actions: SourceActions) {
    val folders = scopes.of(section)
    val shown = folders.count { !it.exclude }
    val thisDevice = stringResource(R.string.nag_nav_device)
    val chooseLabel = stringResource(R.string.nag_lib_choose_folder)
    val res = LocalContext.current.resources
    SettingRow(
        sectionLabel(section),
        subtitle = if (shown == 0) stringResource(R.string.nag_lib_sources_all) else pluralStringResource(R.plurals.nag_folders_count, shown, shown),
    ) {
        MenuButton(
            stringResource(R.string.nag_state_add_folder),
            icon = NagIcons.Plus,
            entries = {
                val taken = folders.map { it.location }.toSet()
                val offered = actions.suggestions.filter { it.path !in taken }
                listOf(MenuEntry.Item(chooseLabel, { actions.pick(section) }, icon = NagIcons.FolderAdd)) +
                    (if (offered.isEmpty()) emptyList() else listOf(MenuEntry.Separator)) +
                    offered.map { suggestion ->
                        val storage = suggestion.label.ifEmpty { thisDevice }
                        MenuEntry.Item(
                            if (suggestion.root) res.getString(R.string.nag_lib_storage_all, storage) else suggestion.name + " · " + storage,
                            { actions.add(section, SourceFolder(suggestion.path)) },
                            icon = if (suggestion.removable) NagIcons.ExternalDrive else NagIcons.Folder,
                        )
                    }
            },
            modifier = Modifier.testTag("library-sources-add-${section.key}"),
        )
    }
    folders.forEach { folder -> SourceRow(section, folder, actions, thisDevice) }
}

@Composable
private fun SourceRow(section: LibrarySection, folder: SourceFolder, actions: SourceActions, thisDevice: String) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val path = LibrarySources.pathOfFolder(folder.location)
    val name = path?.trimEnd('/')?.substringAfterLast('/')?.ifEmpty { null } ?: folderName(folder.location)
    val where = path?.let { displayPath(it, actions.storages, thisDevice) } ?: stringResource(R.string.nag_fr_source_folder)
    Row(
        Modifier.fillMaxWidth().padding(vertical = dimens.gapXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NagIcons.Folder, contentDescription = null, tint = if (folder.exclude) colors.textTertiary else colors.accentText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(dimens.gapS))
        Column(Modifier.weight(1f)) {
            Text(name, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(where, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (folder.exclude) {
            Text(
                stringResource(R.string.nag_lib_excluded),
                style = NagTheme.type.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .border(1.dp, colors.borderStrong, RoundedCornerShape(4.dp))
                    .background(colors.surfaceRaised, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(dimens.gapXs))
        }
        val excludeLabel = stringResource(R.string.nag_lib_exclude)
        val removeLabel = stringResource(R.string.nag_queue_remove_short)
        IconMenuButton(NagIcons.MoreHorizontal, stringResource(R.string.nag_lib_folder_actions, name), {
            listOf(
                MenuEntry.Item(excludeLabel, { actions.toggleExclude(section, folder) }, checked = folder.exclude),
                MenuEntry.Item(removeLabel, { actions.remove(section, folder) }, icon = NagIcons.Close),
            )
        })
    }
}

/** "/storage/1A2B-3C4D/Movies/Yeni" → "SD kart › Movies › Yeni"; the device's own storage by its name. */
private fun displayPath(path: String, storages: List<StorageSource>, thisDevice: String): String {
    val storage = storages.filter { Locations.isUnder(Locations.ofPath(path), Locations.ofPath(it.path)) }.maxByOrNull { it.path.length }
        ?: return path
    val rest = path.trimEnd('/').drop(storage.path.trimEnd('/').length).trim('/')
    val head = storage.label.ifEmpty { thisDevice }
    return if (rest.isEmpty()) head else head + " › " + rest.replace("/", " › ")
}
