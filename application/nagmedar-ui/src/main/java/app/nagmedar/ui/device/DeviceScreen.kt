package app.nagmedar.ui.device

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.R
import app.nagmedar.ui.components.BrowserCommands
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.IconMenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SectionCaption
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.components.TableSort
import app.nagmedar.ui.components.VerticalRule
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.KeyChord
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.library.LibrarySources
import app.nagmedar.ui.library.LocalLibraryAccess
import app.nagmedar.ui.library.SectionSourcesDialog
import app.nagmedar.ui.library.SourceFolder
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.network.EntryKind
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.Settings
import org.videolan.tools.AppScope
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.videolan.tools.Notices

/** The browser's own choices, kept for the next visit: hidden files shown, and the order. */
private const val KEY_HIDDEN = "nagmedar_device_hidden"
private const val KEY_SORT = "nagmedar_device_sort"

/**
 * Sources › This device: the device's own storage, its SD cards and USB disks, and the folders
 * picked with the system picker; each folder a dense table with icons, sizes and dates (Enter or
 * a double click opens, Backspace goes up, F5 reads it again, the Menu key opens a row's menu).
 * A file plays or joins the queue; a folder plays, joins the queue or becomes a section's source folder.
 * On a wide window the places stay beside the folder; on a phone the folder covers them, Back
 * going up. A library menu's "Dosyada göster" ([DeviceRequests]) opens a file's folder with the
 * file selected in sight.
 */
@Composable
fun DeviceScreen(prefs: NagmedarPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    val access = LocalLibraryAccess.current
    val settings = remember(context) { Settings.getInstance(context) }
    val picked by rememberUpdatedState(access?.folders.orEmpty())
    val model = remember(context, locale) {
        DeviceBrowserModel(AndroidDeviceSource(context) { picked }, scope, locale).apply {
            showHidden = settings.getBoolean(KEY_HIDDEN, false)
            sort = readSort(settings.getString(KEY_SORT, null))
        }
    }
    // a drive put in or taken out, a folder picked, an access given: the places again
    LifecycleResumeEffect(model) {
        model.loadRoots()
        onPauseOrDispose {}
    }
    LaunchedEffect(access?.folders, access?.media) { model.refresh() }
    // "Dosyada göster" from a library menu: the file's folder, the file selected in it
    val reveal = DeviceRequests.reveal
    LaunchedEffect(reveal) {
        if (reveal == null) return@LaunchedEffect
        model.reveal(reveal)
        DeviceRequests.taken(reveal)
    }
    val wide = currentWidthClass() >= WidthClass.Expanded
    BrowserCommands(onUp = model::up, onRefresh = {
        model.refresh()
        true
    })
    // a folder given to a section: that section's folders window shows it among the others
    var sourcesFor by remember { mutableStateOf<LibrarySection?>(null) }
    val actions = remember(context, model, scope, prefs) { DeviceActions(context, model, scope, prefs) { sourcesFor = it } }
    sourcesFor?.let { section -> SectionSourcesDialog(section, prefs) { sourcesFor = null } }
    Column(Modifier.fillMaxSize()) {
        PageHeader(title = stringResource(R.string.nag_nav_device), subtitle = model.current?.name) {
            NagIconButton(NagIcons.Repeat, stringResource(R.string.nag_sc_refresh), { model.refresh() }, shortcut = ShortcutId.Refresh)
            IconMenuButton(NagIcons.Sort, stringResource(R.string.nag_device_view_menu), {
                viewMenu(context.resources, model) { hidden, sort ->
                    settings.edit().putBoolean(KEY_HIDDEN, hidden).putString(KEY_SORT, "${sort.key.name}:${sort.ascending}").apply()
                }
            }, modifier = Modifier.testTag("device-view"))
            if (access != null) {
                if (wide) {
                    NagButton(stringResource(R.string.nag_device_pick_folder), access.pickFolder, icon = NagIcons.FolderAdd)
                } else {
                    NagIconButton(NagIcons.FolderAdd, stringResource(R.string.nag_device_pick_folder), access.pickFolder)
                }
            }
        }
        HorizontalRule()
        val places = @Composable { paneModifier: Modifier -> PlacesPane(model, paneModifier) }
        val folder = @Composable { paneModifier: Modifier -> FolderPane(model, actions, paneModifier, showBack = !wide) }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                places(Modifier.width(if (NagTheme.inputMode == InputMode.Pointer) 280.dp else 320.dp).fillMaxHeight())
                VerticalRule()
                folder(Modifier.weight(1f).fillMaxHeight())
            }
        } else if (model.current == null) {
            places(Modifier.fillMaxSize())
        } else {
            BackHandler { if (!model.up()) model.close() }
            folder(Modifier.fillMaxSize())
        }
    }
}

private fun readSort(value: String?): DeviceSort {
    val key = value?.substringBefore(':')?.let { name -> DeviceSortKey.entries.firstOrNull { it.name == name } } ?: return DeviceSort()
    return DeviceSort(key, value.substringAfter(':') != "false")
}

/** The order and the hidden files, in the header's menu (the table's headers order too). */
private fun viewMenu(res: Resources, model: DeviceBrowserModel, save: (Boolean, DeviceSort) -> Unit): List<MenuEntry> {
    val sortBy = { key: DeviceSortKey ->
        val now = model.sort
        model.sort = if (now.key == key) now.copy(ascending = !now.ascending) else DeviceSort(key, ascending = key == DeviceSortKey.Name)
        save(model.showHidden, model.sort)
    }
    return listOf(
        MenuEntry.Item(res.getString(R.string.nag_device_sort_name), { sortBy(DeviceSortKey.Name) }, checked = model.sort.key == DeviceSortKey.Name),
        MenuEntry.Item(res.getString(R.string.nag_device_sort_size), { sortBy(DeviceSortKey.Size) }, checked = model.sort.key == DeviceSortKey.Size),
        MenuEntry.Item(res.getString(R.string.nag_device_sort_date), { sortBy(DeviceSortKey.Modified) }, checked = model.sort.key == DeviceSortKey.Modified),
        MenuEntry.Separator,
        MenuEntry.Item(res.getString(R.string.nag_device_show_hidden), {
            model.showHidden = !model.showHidden
            save(model.showHidden, model.sort)
        }, checked = model.showHidden),
    )
}

/** What the browser's files and folders are made to do: played, queued, put in the library. */
private class DeviceActions(
    private val context: Context,
    private val model: DeviceBrowserModel,
    private val scope: CoroutineScope,
    private val prefs: NagmedarPreferences,
    private val showSources: (LibrarySection) -> Unit,
) {
    private val res get() = context.resources

    fun play(entries: List<DeviceEntry>, index: Int) {
        val media = entries.map(::mediaOf)
        if (media.isNotEmpty()) PlaybackActions.playList(context, media, index.coerceIn(0, media.lastIndex))
    }

    fun queue(entries: List<DeviceEntry>) {
        if (entries.isEmpty()) return
        PlaybackActions.appendList(context, entries.map(::mediaOf))
        Notices.show(context, res.getQuantityString(R.plurals.nag_drop_queued, entries.size, entries.size))
    }

    /** A folder's playable files, played or queued; a word when it has none. */
    fun folder(entry: DeviceEntry, enqueue: Boolean) {
        scope.launch {
            val files = model.playableIn(entry)
            when {
                files.isEmpty() -> Notices.show(context, res.getString(R.string.nag_device_nothing_to_play, entry.name))
                enqueue -> queue(files)
                else -> play(files, 0)
            }
        }
    }

    /** A folder of a drive becomes a section's source folder (a picked folder already is the library's). */
    fun canAddToLibrary(location: DeviceLocation): Boolean = location is DeviceLocation.Path

    /** [location] joins [section]'s folders, scanned when the medialibrary does not index it yet; the section's folders window opens. */
    fun addToLibrary(location: DeviceLocation, section: LibrarySection) {
        val path = (location as? DeviceLocation.Path)?.path ?: return
        prefs.updateLibrarySources(prefs.librarySources.plus(section, SourceFolder(path)))
        AppScope.launch { runCatching { LibrarySources.ensureIndexed(context.applicationContext, path) } }
        showSources(section)
    }

    /** "Kütüphaneye ekle: Filmler" and the others, a section each. */
    fun libraryEntries(location: DeviceLocation): List<MenuEntry> = LibrarySection.entries.map { section ->
        MenuEntry.Item(res.getString(R.string.nag_device_add_to_section, res.getString(sectionName(section))), { addToLibrary(location, section) }, icon = NagIcons.FolderAdd)
    }

    private fun mediaOf(entry: DeviceEntry): MediaWrapper = MLServiceLocator.getAbstractMediaWrapper(Uri.parse(entry.location.uri)).apply {
        title = entry.name.substringBeforeLast('.', entry.name)
        when (entry.kind) {
            EntryKind.Video -> type = MediaWrapper.TYPE_VIDEO
            EntryKind.Audio -> type = MediaWrapper.TYPE_AUDIO
            EntryKind.Playlist -> type = MediaWrapper.TYPE_PLAYLIST
            else -> Unit
        }
    }
}

private fun sectionName(section: LibrarySection): Int = when (section) {
    LibrarySection.Movies -> R.string.nag_nav_movies
    LibrarySection.Series -> R.string.nag_nav_series
    LibrarySection.Videos -> R.string.nag_nav_videos
    LibrarySection.Music -> R.string.nag_nav_music
}

/** The device's places: its storage and drives, with their room, then the picked folders. */
@Composable
private fun PlacesPane(model: DeviceBrowserModel, modifier: Modifier) {
    val dimens = NagTheme.dimens
    val access = LocalLibraryAccess.current
    val roots = model.roots
    val drives = roots.orEmpty().filter { it.kind != RootKind.Picked }
    val picked = roots.orEmpty().filter { it.kind == RootKind.Picked }
    val open = model.trail.firstOrNull()?.location
    val internalName = stringResource(R.string.nag_device_internal)
    LazyColumn(modifier.testTag("device-places"), contentPadding = PaddingValues(vertical = dimens.gapS)) {
        if (access != null && !access.media) {
            item(key = "access") {
                Column(Modifier.fillMaxWidth().padding(horizontal = dimens.gapM, vertical = dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                    Text(stringResource(R.string.nag_device_no_access), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
                    NagButton(stringResource(R.string.nag_device_grant), access.requestMedia, primary = true)
                }
            }
        }
        item(key = "drives") { PaneCaption(stringResource(R.string.nag_device_drives)) }
        items(drives, key = { "drive-${it.location}" }) { root ->
            val name = root.name.ifEmpty { internalName }
            PlaceRow(
                icon = if (root.kind == RootKind.Internal) NagIcons.Device else NagIcons.ExternalDrive,
                name = name,
                detail = roomLabel(root),
                selected = open == root.location,
                onClick = { model.openRoot(root, name) },
            )
        }
        item(key = "picked") { PaneCaption(stringResource(R.string.nag_device_picked)) }
        if (picked.isEmpty()) {
            item(key = "picked-none") {
                Text(
                    stringResource(R.string.nag_device_picked_none),
                    style = NagTheme.type.labelSmall,
                    color = NagTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = dimens.gapM, vertical = dimens.gapXs),
                )
            }
        }
        items(picked, key = { "picked-${it.tree}" }) { root ->
            PlaceRow(
                icon = NagIcons.Folder,
                name = root.name,
                detail = stringResource(R.string.nag_device_picked_detail),
                selected = open == root.location,
                onClick = { model.openRoot(root, root.name) },
            )
        }
    }
}

/** "52,1 GB boş · 128 GB": the room left of a drive, when it can be told. */
@Composable
private fun roomLabel(root: DeviceRoot): String {
    val locale = currentLocale()
    if (root.totalBytes <= 0) return stringResource(R.string.nag_device_drive)
    return stringResource(R.string.nag_device_room, sizeLabel(root.freeBytes, locale), sizeLabel(root.totalBytes, locale))
}

@Composable
private fun PaneCaption(text: String) {
    val dimens = NagTheme.dimens
    SectionCaption(text, Modifier.fillMaxWidth().padding(start = dimens.gapM, end = dimens.gapM, top = dimens.gapM, bottom = dimens.gapXs).semantics { heading() })
}

@Composable
private fun PlaceRow(icon: ImageVector, name: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val ink = if (selected) colors.onSelection else colors.text
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gapS)
            .heightIn(min = dimens.minTarget.coerceAtLeast(44.dp))
            .focusRing(state, shape)
            .background(stateBackground(state, selected), shape)
            .semantics { this.selected = selected }
            .nagClickable(interaction, onClick)
            .padding(horizontal = dimens.gapS, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) colors.onSelection else colors.textSecondary, modifier = Modifier.size(dimens.icon))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = NagTheme.type.bodyStrong, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = NagTheme.type.labelSmall, color = if (selected) colors.onSelection else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The open folder: its trail, what is done with all of it, and its files or why they do not show. */
@Composable
private fun FolderPane(model: DeviceBrowserModel, actions: DeviceActions, modifier: Modifier, showBack: Boolean) {
    val current = model.current
    val access = LocalLibraryAccess.current
    Column(modifier) {
        if (current == null) {
            Centered {
                StatePanel(
                    tone = StateTone.Neutral,
                    icon = NagIcons.Device,
                    title = stringResource(R.string.nag_device_choose_title),
                    description = stringResource(R.string.nag_device_choose_desc),
                )
            }
            return@Column
        }
        FolderBar(model, actions, showBack)
        HorizontalRule()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val listing = model.listing) {
                FolderListing.Loading -> Unit
                FolderListing.Unreadable -> Centered {
                    StatePanel(
                        tone = StateTone.Warning,
                        icon = NagIcons.DriveRemoved,
                        title = stringResource(R.string.nag_device_unreadable_title),
                        description = stringResource(R.string.nag_device_unreadable_desc, current.name),
                        primary = StateAction(stringResource(R.string.nag_net_try_again)) { model.refresh() },
                        secondary = if (access != null && !access.media) StateAction(stringResource(R.string.nag_device_grant), access.requestMedia) else null,
                    )
                }
                is FolderListing.Listed -> {
                    val shown = remember(listing, model.sort, model.showHidden) { model.visible(listing.entries) }
                    if (shown.isEmpty()) {
                        Centered {
                            StatePanel(
                                tone = StateTone.Neutral,
                                icon = NagIcons.Folder,
                                title = stringResource(R.string.nag_net_empty_title),
                                description = stringResource(
                                    if (listing.entries.isEmpty()) R.string.nag_device_empty_desc else R.string.nag_device_only_hidden_desc,
                                    current.name,
                                ),
                                primary = if (model.trail.size > 1) StateAction(stringResource(R.string.nag_sc_folder_up)) { model.up() } else null,
                            )
                        }
                    } else {
                        EntryTable(shown, model, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { content() }
}

/** Where the folder is (each folder on the way a button back to it) and what is done with all it holds. */
@Composable
private fun FolderBar(model: DeviceBrowserModel, actions: DeviceActions, showBack: Boolean) {
    val dimens = NagTheme.dimens
    val colors = NagTheme.colors
    val current = model.current ?: return
    val listing = model.listing
    val playable = remember(listing, model.showHidden, model.sort) { model.playable() }
    val access = LocalLibraryAccess.current
    Row(Modifier.fillMaxWidth().padding(horizontal = dimens.gapS, vertical = dimens.gapXs), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_sc_folder_up), { if (!model.up()) model.close() }, shortcut = ShortcutId.FolderUp)
        }
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).testTag("device-trail"), verticalAlignment = Alignment.CenterVertically) {
            model.trail.forEachIndexed { index, crumb ->
                if (index > 0) Icon(NagIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                CrumbButton(crumb.name, current = index == model.trail.lastIndex) { model.goTo(index) }
            }
        }
        if (playable.isNotEmpty()) {
            NagIconButton(NagIcons.Play, stringResource(R.string.nag_net_play_all), { actions.play(playable, 0) })
            NagIconButton(NagIcons.Queue, stringResource(R.string.nag_net_queue_all), { actions.queue(playable) })
        }
        if (actions.canAddToLibrary(current.location) && access?.media != false) {
            IconMenuButton(NagIcons.FolderAdd, stringResource(R.string.nag_net_add_to_library), { actions.libraryEntries(current.location) })
        }
    }
}

@Composable
private fun CrumbButton(name: String, current: Boolean, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Box(
        Modifier
            .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onClick, enabled = !current)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = if (current) NagTheme.type.bodyStrong else NagTheme.type.body,
            color = if (current) colors.text else colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 240.dp),
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The folder's entries, folders first: a click selects and a double click or Enter opens (a tap opens by touch). */
@Composable
private fun EntryTable(entries: List<DeviceEntry>, model: DeviceBrowserModel, actions: DeviceActions) {
    val context = LocalContext.current
    val res = context.resources
    val locale = currentLocale()
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val compact = currentWidthClass() == WidthClass.Compact
    var selected by remember(model.current) { mutableStateOf<DeviceLocation?>(null) }
    val listState = rememberLazyListState()
    // the file a library menu asked to show: selected, and scrolled into sight
    LaunchedEffect(model.pointed, entries) {
        val pointed = model.pointed ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it.location == pointed }
        if (index < 0) return@LaunchedEffect
        selected = pointed
        listState.scrollToItem(index)
        model.pointedShown()
    }
    val dates = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    val name = TableColumn<DeviceEntry>(
        title = stringResource(R.string.nag_net_column_name),
        width = ColumnWidth.Weight(1f),
        content = { entry, isSelected -> EntryName(entry, isSelected, detail = if (compact) entryDetail(entry, locale, dates) else null) },
        value = { it.name },
    )
    val size = TableColumn<DeviceEntry>(
        title = stringResource(R.string.nag_device_column_size),
        width = ColumnWidth.Fixed(110.dp),
        alignEnd = true,
        mono = true,
        value = { if (it.folder) "" else sizeLabel(it.sizeBytes, locale) },
    )
    val modified = TableColumn<DeviceEntry>(
        title = stringResource(R.string.nag_device_column_modified),
        width = ColumnWidth.Fixed(180.dp),
        mono = true,
        value = { if (it.modifiedMs > 0) dates.format(Date(it.modifiedMs)) else "" },
    )
    val sortColumn = when (model.sort.key) {
        DeviceSortKey.Name -> 0
        DeviceSortKey.Size -> 1
        DeviceSortKey.Modified -> 2
    }
    val open = { entry: DeviceEntry -> openEntry(entry, entries, model, actions) }
    DenseTable(
        columns = if (compact) listOf(name) else listOf(name, size, modified),
        rows = entries,
        key = { it.location },
        state = listState,
        selectedKey = selected,
        sort = if (compact) null else TableSort(sortColumn, model.sort.ascending),
        onSortChange = if (compact) {
            null
        } else {
            { sort ->
                val key = when (sort.column) {
                    1 -> DeviceSortKey.Size
                    2 -> DeviceSortKey.Modified
                    else -> DeviceSortKey.Name
                }
                model.sort = DeviceSort(key, sort.ascending)
                Settings.getInstance(context).edit().putString(KEY_SORT, "${key.name}:${sort.ascending}").apply()
            }
        },
        onRowClick = { entry, _ -> if (pointer) selected = entry.location else open(entry) },
        onRowDoubleClick = if (pointer) { entry, _ -> open(entry) } else null,
        rowMenu = { entry, _ -> entryMenu(res, entry, entries, model, actions) },
        rowMenuLabel = stringResource(R.string.nag_more_actions),
        modifier = Modifier.testTag("device-table"),
    )
}

/** A folder opens; a media file plays, the files after it in the folder following it in the queue. */
private fun openEntry(entry: DeviceEntry, entries: List<DeviceEntry>, model: DeviceBrowserModel, actions: DeviceActions) {
    when {
        entry.folder -> model.open(entry)
        entry.kind.playable -> {
            val files = entries.filter { it.kind.playable }
            actions.play(files, files.indexOf(entry).coerceAtLeast(0))
        }
    }
}

/** "1,4 GB · 12 Eki 2026 21:40" under a file's name on a phone, where the columns do not fit. */
private fun entryDetail(entry: DeviceEntry, locale: Locale, dates: DateFormat): String = listOfNotNull(
    entry.sizeBytes.takeIf { !entry.folder && it > 0 }?.let { sizeLabel(it, locale) },
    entry.modifiedMs.takeIf { it > 0 }?.let { dates.format(Date(it)) },
).joinToString(" · ")

@Composable
private fun EntryName(entry: DeviceEntry, selected: Boolean, detail: String?) {
    val colors = NagTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when (entry.kind) {
                EntryKind.Folder -> NagIcons.Folder
                EntryKind.Video -> NagIcons.Movies
                EntryKind.Audio -> NagIcons.Music
                EntryKind.Playlist -> NagIcons.PlaylistAdd
                EntryKind.Subtitle -> NagIcons.Subtitle
                EntryKind.Other -> NagIcons.MoreHorizontal
            },
            contentDescription = null,
            tint = if (selected) colors.onSelection else if (entry.folder) colors.accentText else colors.textSecondary,
            modifier = Modifier.size(NagTheme.dimens.icon),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                entry.name,
                style = if (entry.folder) NagTheme.type.bodyStrong else NagTheme.type.body,
                color = if (selected) colors.onSelection else if (entry.hidden) colors.textTertiary else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrEmpty()) {
                Text(detail, style = NagTheme.type.labelSmall, color = if (selected) colors.onSelection else colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A file's menu: play, queue; a folder's: open, play, queue, add to the library. */
private fun entryMenu(res: Resources, entry: DeviceEntry, entries: List<DeviceEntry>, model: DeviceBrowserModel, actions: DeviceActions): List<MenuEntry> = buildList {
    val enter = KeyChord(Key.Enter).label(res)
    if (entry.folder) {
        add(MenuEntry.Item(res.getString(R.string.nag_net_open), { model.open(entry) }, shortcut = enter))
        add(MenuEntry.Item(res.getString(R.string.nag_play), { actions.folder(entry, enqueue = false) }, icon = NagIcons.Play))
        add(MenuEntry.Item(res.getString(R.string.nag_add_to_queue), { actions.folder(entry, enqueue = true) }, icon = NagIcons.Queue))
        if (actions.canAddToLibrary(entry.location)) {
            add(MenuEntry.Separator)
            addAll(actions.libraryEntries(entry.location))
        }
    } else if (entry.kind.playable) {
        add(MenuEntry.Item(res.getString(R.string.nag_play), { openEntry(entry, entries, model, actions) }, icon = NagIcons.Play, shortcut = enter))
        add(MenuEntry.Item(res.getString(R.string.nag_play_this_only), { actions.play(listOf(entry), 0) }))
        add(MenuEntry.Item(res.getString(R.string.nag_add_to_queue), { actions.queue(listOf(entry)) }, icon = NagIcons.Queue))
    } else {
        add(MenuEntry.Item(res.getString(R.string.nag_device_not_media), {}, enabled = false))
    }
}
