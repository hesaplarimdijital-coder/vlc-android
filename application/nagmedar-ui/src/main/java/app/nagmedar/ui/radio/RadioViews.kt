package app.nagmedar.ui.radio

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.ChipRow
import app.nagmedar.ui.components.IconMenuButton
import app.nagmedar.ui.components.LoadingState
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.music.PlaylistTab
import app.nagmedar.ui.radio.record.RecordedTrack
import app.nagmedar.ui.radio.record.RecordingsController
import app.nagmedar.ui.radio.record.RecordingsTab
import app.nagmedar.ui.radio.record.toRecording
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.first
import org.videolan.tools.Notices

/** The widest the radio screen's content grows. */
private val ContentWidth = 960.dp

/**
 * S-1105, S-1106: the radio screen: tabs for the favourites, the recent, the directory's search
 * (the box for the name's words, the filters for country, language, tag, codec and lowest bitrate,
 * the order) and the imported stations, under the strip that shows the station on the air with the
 * title its stream sends. A station shows its logo, name, place, codec and bitrate, votes and tags.
 * Enter on a station plays it; its menu (right click, the Menu key, the button at its end) plays,
 * favourites, votes, copies the stream's address and takes it out. [searchRequested] (the search
 * shortcut) opens the search and puts the keyboard in the box, and is answered with
 * [onSearchHandled]; what is taken out can be put back through [undo]. With [recordings] (S-1107, S-207)
 * a Recordings tab joins the lists, and each station's menu can record it.
 */
@Composable
fun RadioPage(
    controller: RadioController,
    modifier: Modifier = Modifier,
    searchRequested: Boolean = false,
    onSearchHandled: () -> Unit = {},
    onCopyAddress: (Station) -> Unit = {},
    onImport: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    undo: UndoBarState? = null,
    now: () -> Long = System::currentTimeMillis,
    recordings: RecordingsController? = null,
    recordingActions: RecordingActions = RecordingActions(),
    recordingsRequested: Boolean = false,
    onRecordingsHandled: () -> Unit = {},
    recordingsLive: Boolean = true,
) {
    val context = LocalContext.current
    val res = context.resources
    val compact = currentWidthClass() == WidthClass.Compact
    val tab = controller.tab
    val focus = remember { FocusRequester() }
    LaunchedEffect(searchRequested, tab) {
        if (!searchRequested) return@LaunchedEffect
        if (tab != RadioTab.Search) {
            controller.select(RadioTab.Search)
        } else {
            focus.requestFocus()
            onSearchHandled()
        }
    }
    // S-207: the recordings asked for (Alt+Shift+R)
    LaunchedEffect(recordingsRequested) {
        if (!recordingsRequested) return@LaunchedEffect
        if (recordings != null) controller.select(RadioTab.Recordings)
        onRecordingsHandled()
    }
    val notice = controller.notice
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        val text = noticeText(res, notice)
        if (notice is RadioNotice.Removed && undo != null) {
            undo.show(text, res.getString(R.string.nag_sc_undo), ShortcutRegistry.chordLabel(ShortcutId.Undo, res)) { controller.undo(notice) }
        } else {
            Notices.show(context, text)
        }
        controller.noticed()
    }
    val onRecord: ((Station) -> Unit)? = recordings?.let { rec -> { station -> rec.record(station.toRecording()) } }
    Column(modifier.fillMaxSize()) {
        PageHeader(title = stringResource(R.string.nag_nav_radio), subtitle = stringResource(R.string.nag_radio_source))
        controller.now?.let { NowStrip(it, onTogglePlay) }
        RadioTabs(
            tab,
            mapOf(
                RadioTab.Favourites to controller.favourites?.size,
                RadioTab.Recent to controller.recent?.size,
                RadioTab.Imported to controller.imported?.size,
                RadioTab.Recordings to recordings?.live?.size?.takeIf { it > 0 },
            ),
            controller::select,
            tabs = RadioTab.entries.filter { it != RadioTab.Recordings || recordings != null },
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                RadioTab.Search -> SearchTab(controller, focus, compact, now, onCopyAddress, onRecord)
                RadioTab.Favourites -> SavedTab(tab, controller.favourites, controller, compact, now, onCopyAddress, onImport, onRecord)
                RadioTab.Recent -> SavedTab(tab, controller.recent, controller, compact, now, onCopyAddress, onImport, onRecord)
                RadioTab.Imported -> SavedTab(tab, controller.imported, controller, compact, now, onCopyAddress, onImport, onRecord)
                RadioTab.Recordings -> if (recordings != null) {
                    RecordingsTab(
                        recordings,
                        stations = (controller.favourites.orEmpty() + controller.recent.orEmpty() + controller.imported.orEmpty()).distinctBy { it.key },
                        undo = undo,
                        autoRefresh = recordingsLive,
                        onShare = recordingActions.onShare,
                        onChooseFolder = recordingActions.onChooseFolder,
                        onUseDefaultFolder = recordingActions.onUseDefaultFolder,
                        onAllowExact = recordingActions.onAllowExact,
                        onSearchStations = { controller.select(RadioTab.Search) },
                    )
                }
            }
        }
    }
}

/** What a notice says. */
fun noticeText(res: Resources, notice: RadioNotice): String = when (notice) {
    is RadioNotice.Voted -> res.getString(R.string.nag_radio_voted, notice.station)
    is RadioNotice.AlreadyVoted -> res.getString(R.string.nag_radio_already_voted, notice.station)
    is RadioNotice.VoteRefused -> res.getString(R.string.nag_radio_vote_refused)
    RadioNotice.VoteOffline -> res.getString(R.string.nag_radio_vote_offline)
    is RadioNotice.Removed -> res.getString(R.string.nag_radio_removed, notice.station.name)
    is RadioNotice.Imported -> listOfNotNull(
        notice.count.takeIf { it > 0 || notice.present == 0 }?.let { res.getQuantityString(R.plurals.nag_radio_imported, it, it) },
        notice.present.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.nag_radio_import_present, it, it) },
        notice.skipped.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.nag_radio_import_skipped, it, it) },
    ).joinToString("; ")
    RadioNotice.Failed -> res.getString(R.string.nag_radio_failed)
    is RadioNotice.ImportFailed -> res.getString(
        when (notice.problem) {
            ImportProblem.NotAPlaylist -> R.string.nag_radio_import_unsupported
            ImportProblem.Empty -> R.string.nag_radio_import_empty
            ImportProblem.TooLarge -> R.string.nag_radio_import_large
            ImportProblem.Unreadable -> R.string.nag_radio_import_failed
        },
    )
}

/** The tabs' names. */
private fun tabName(tab: RadioTab): Int = when (tab) {
    RadioTab.Favourites -> R.string.nag_radio_tab_favourites
    RadioTab.Recent -> R.string.nag_radio_tab_recent
    RadioTab.Search -> R.string.nag_radio_tab_search
    RadioTab.Imported -> R.string.nag_radio_tab_imported
    RadioTab.Recordings -> R.string.nag_radio_tab_recordings
}

/** The lists' tabs, scrolled sideways when they do not fit; a count shows where the list has stations. */
@Composable
private fun RadioTabs(selected: RadioTab, counts: Map<RadioTab, Int?>, onSelect: (RadioTab) -> Unit, tabs: List<RadioTab> = RadioTab.entries) {
    val scroll = rememberScrollState()
    val reduced = NagTheme.motion.reduced
    // S-207: the last tab (Kayıtlar) is out of sight on a phone: asked for, the strip scrolls to it
    LaunchedEffect(selected) {
        if (selected == RadioTab.Recordings) {
            snapshotFlow { scroll.maxValue }.first { it > 0 }
            if (reduced) scroll.scrollTo(scroll.maxValue) else scroll.animateScrollTo(scroll.maxValue)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = NagTheme.dimens.pagePadding - 8.dp, vertical = 2.dp)
            .testTag("radio-tabs"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            Box(Modifier.testTag("radio-tab-${tab.name.lowercase()}")) {
                PlaylistTab(stringResource(tabName(tab)), counts[tab]?.takeIf { it > 0 }, selected = tab == selected, onClick = { onSelect(tab) })
            }
        }
    }
}

/** The station on the air: its logo, the title its stream sends (the station's name when it sends none), who sings it, and play or pause. */
@Composable
private fun NowStrip(now: RadioNow, onToggle: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val display = now.display
    val line = if (display.hasTrack) listOf(display.artist, display.album).filter { it.isNotEmpty() }.joinToString(" · ") else stringResource(R.string.nag_radio_live)
    Box(Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs)) {
        Row(
            Modifier
                .widthIn(max = ContentWidth)
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(1.dp, colors.border, shape)
                .padding(8.dp)
                .testTag("radio-now"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StationLogo(now.station, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // a new title is said to a screen reader when it changes
                Text(
                    display.title,
                    style = NagTheme.type.bodyStrong,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("radio-now-title"),
                )
                if (line.isNotEmpty()) {
                    Text(line, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("radio-now-line"))
                }
            }
            NagIconButton(
                if (now.playing) NagIcons.Pause else NagIcons.Play,
                stringResource(if (now.playing) R.string.nag_pause else R.string.nag_play),
                onToggle,
                modifier = Modifier.testTag("radio-now-toggle"),
            )
        }
    }
}

/** The directory's search: the box, the filters and what was found. */
@Composable
private fun SearchTab(controller: RadioController, focus: FocusRequester, compact: Boolean, now: () -> Long, onCopyAddress: (Station) -> Unit, onRecord: ((Station) -> Unit)?) {
    val search = controller.search
    LaunchedEffect(search) {
        search.start()
        search.loadOptions()
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.widthIn(max = ContentWidth).padding(horizontal = NagTheme.dimens.pagePadding)) {
            RadioSearchField(search.text, search::type, search::search, focus)
            Spacer(Modifier.size(NagTheme.dimens.gapS))
            FilterBar(search)
            Spacer(Modifier.size(NagTheme.dimens.gapXs))
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Results(search, controller, compact, now, onCopyAddress, onRecord)
        }
    }
}

/** A list the user keeps (favourites, recent, imported): its stations, or what to do while it is empty. */
@Composable
private fun SavedTab(
    tab: RadioTab,
    stations: List<Station>?,
    controller: RadioController,
    compact: Boolean,
    now: () -> Long,
    onCopyAddress: (Station) -> Unit,
    onImport: () -> Unit,
    onRecord: ((Station) -> Unit)?,
) {
    if (stations == null) return
    Column(Modifier.fillMaxSize()) {
        if (tab == RadioTab.Imported && stations.isNotEmpty()) ImportBar(onImport)
        if (stations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyList(tab, controller::select, onImport) }
        } else {
            StationList(stations, tab, controller, compact, now, onCopyAddress, onRecord, footer = null, modifier = Modifier.weight(1f))
        }
    }
}

/** The import button over the imported stations, with the kinds of file it takes. */
@Composable
private fun ImportBar(onImport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = NagTheme.dimens.pagePadding, vertical = NagTheme.dimens.gapXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NagButton(stringResource(R.string.nag_radio_import), onImport, icon = NagIcons.FolderAdd, modifier = Modifier.testTag("radio-import"))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.nag_radio_import_hint), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary, maxLines = 2)
    }
}

/** A list with nothing in it yet: what it is for, and the one thing to do. */
@Composable
private fun EmptyList(tab: RadioTab, onSelect: (RadioTab) -> Unit, onImport: () -> Unit) {
    val text = when (tab) {
        RadioTab.Favourites -> EmptyText(NagIcons.Star, R.string.nag_radio_fav_empty_title, R.string.nag_radio_fav_empty_desc, R.string.nag_radio_search_stations)
        RadioTab.Recent -> EmptyText(NagIcons.Radio, R.string.nag_radio_recent_empty_title, R.string.nag_radio_recent_empty_desc, R.string.nag_radio_search_stations)
        else -> EmptyText(NagIcons.FolderAdd, R.string.nag_radio_imported_empty_title, R.string.nag_radio_imported_empty_desc, R.string.nag_radio_import)
    }
    StatePanel(
        tone = StateTone.Neutral,
        icon = text.icon,
        title = stringResource(text.title),
        description = stringResource(text.description),
        primary = StateAction(stringResource(text.action)) { if (tab == RadioTab.Imported) onImport() else onSelect(RadioTab.Search) },
        modifier = Modifier.testTag("radio-empty-${tab.name.lowercase()}"),
    )
}

private class EmptyText(val icon: ImageVector, val title: Int, val description: Int, val action: Int)

@Composable
private fun RadioSearchField(text: String, onChange: (String) -> Unit, onSearch: () -> Unit, focus: FocusRequester) {
    val focusManager = LocalFocusManager.current
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val placeholder = stringResource(R.string.nag_radio_search_placeholder)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTarget.coerceAtLeast(44.dp))
            .background(colors.surface, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NagIcons.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text(placeholder, style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = text,
                onValueChange = onChange,
                singleLine = true,
                textStyle = NagTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .tabMovesFocus(focusManager)
                    .testTag("radio-search-field")
                    .semantics { contentDescription = placeholder },
            )
        }
        if (text.isNotEmpty()) NagIconButton(NagIcons.Close, stringResource(R.string.nag_search_clear), { onChange("") })
    }
}

@Composable
private fun FilterBar(search: RadioSearch) {
    val res = LocalContext.current.resources
    val locale = currentLocale()
    val filters = search.filters
    val options = search.options
    val any = stringResource(R.string.nag_radio_any)
    fun label(title: Int, value: String?): String =
        if (value == null) res.getString(title) else res.getString(R.string.nag_radio_filter_value, res.getString(title), value)
    val countryName = filters.countryCode?.let { code -> RadioLabels.country(code, options.countries.firstOrNull { it.code == code }?.name, locale) }
    val languageName = filters.language?.let { name -> RadioLabels.language(name, options.languages.firstOrNull { it.name == name }?.code, locale) }
    ChipRow(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 2.dp)) {
        MenuButton(
            label(R.string.nag_radio_filter_country, countryName),
            entries = {
                listEntries(res, options.phase, any, selected = filters.countryCode == null, onAny = { search.filter(filters.copy(countryCode = null)) },
                    items = RadioLabels.countries(options.countries, locale).map { (country, name) ->
                        Triple(name, filters.countryCode == country.code) { search.filter(filters.copy(countryCode = country.code)) }
                    })
            },
            modifier = Modifier.testTag("radio-filter-country"),
        )
        MenuButton(
            label(R.string.nag_radio_filter_language, languageName),
            entries = {
                listEntries(res, options.phase, any, selected = filters.language == null, onAny = { search.filter(filters.copy(language = null)) },
                    items = RadioLabels.languages(options.languages, locale).map { (language, name) ->
                        Triple(name, filters.language == language.name) { search.filter(filters.copy(language = language.name)) }
                    })
            },
            modifier = Modifier.testTag("radio-filter-language"),
        )
        MenuButton(
            label(R.string.nag_radio_filter_tag, filters.tag),
            entries = {
                listEntries(res, options.phase, any, selected = filters.tag == null, onAny = { search.filter(filters.copy(tag = null)) },
                    items = options.tags.map { tag -> Triple(tag.name, filters.tag == tag.name) { search.filter(filters.copy(tag = tag.name)) } })
            },
            modifier = Modifier.testTag("radio-filter-tag"),
        )
        MenuButton(
            label(R.string.nag_radio_filter_codec, filters.codec),
            entries = {
                listOf(MenuEntry.Item(any, { search.filter(filters.copy(codec = null)) }, checked = filters.codec == null), MenuEntry.Separator) +
                    RadioLabels.CODECS.map { codec -> MenuEntry.Item(codec, { search.filter(filters.copy(codec = codec)) }, checked = filters.codec == codec) }
            },
            modifier = Modifier.testTag("radio-filter-codec"),
        )
        MenuButton(
            label(R.string.nag_radio_filter_bitrate, filters.minBitrate.takeIf { it > 0 }?.let { res.getString(R.string.nag_radio_bitrate_at_least, it) }),
            entries = {
                listOf(MenuEntry.Item(any, { search.filter(filters.copy(minBitrate = 0)) }, checked = filters.minBitrate == 0), MenuEntry.Separator) +
                    RadioLabels.BITRATES.map { rate ->
                        MenuEntry.Item(res.getString(R.string.nag_radio_bitrate_at_least, rate), { search.filter(filters.copy(minBitrate = rate)) }, checked = filters.minBitrate == rate)
                    }
            },
            modifier = Modifier.testTag("radio-filter-bitrate"),
        )
        MenuButton(
            res.getString(R.string.nag_radio_sort, orderName(res, search.order)),
            entries = {
                StationOrder.entries.map { order -> MenuEntry.Item(orderName(res, order), { search.sort(order) }, checked = search.order == order) }
            },
            icon = NagIcons.Sort,
            modifier = Modifier.testTag("radio-sort"),
        )
        if (filters.count > 0) {
            NagButton(stringResource(R.string.nag_clear_filters), { search.filter(StationFilters()) }, modifier = Modifier.testTag("radio-filters-clear"))
        }
    }
}

/** A filter menu: "Any", then the directory's list, or a word on where the list is. */
private fun listEntries(
    res: Resources,
    phase: OptionsPhase,
    any: String,
    selected: Boolean,
    onAny: () -> Unit,
    items: List<Triple<String, Boolean, () -> Unit>>,
): List<MenuEntry> = buildList {
    add(MenuEntry.Item(any, onAny, checked = selected))
    when {
        items.isNotEmpty() -> {
            add(MenuEntry.Separator)
            items.forEach { (name, checked, pick) -> add(MenuEntry.Item(name, pick, checked = checked)) }
        }
        phase == OptionsPhase.Failed -> add(MenuEntry.Item(res.getString(R.string.nag_radio_options_failed), {}, enabled = false))
        else -> add(MenuEntry.Item(res.getString(R.string.nag_radio_options_loading), {}, enabled = false))
    }
}

private fun orderName(res: Resources, order: StationOrder): String = res.getString(
    when (order) {
        StationOrder.Votes -> R.string.nag_radio_sort_votes
        StationOrder.Popularity -> R.string.nag_radio_sort_popular
        StationOrder.Name -> R.string.nag_radio_sort_name
        StationOrder.Bitrate -> R.string.nag_radio_sort_bitrate
    },
)

@Composable
private fun Results(search: RadioSearch, controller: RadioController, compact: Boolean, now: () -> Long, onCopyAddress: (Station) -> Unit, onRecord: ((Station) -> Unit)?) {
    val stations = search.stations
    val failure = search.failure
    when {
        stations.isEmpty() && search.loading -> LoadingState(
            title = stringResource(R.string.nag_radio_searching),
            posters = 0,
            modifier = Modifier.padding(horizontal = NagTheme.dimens.pagePadding, vertical = NagTheme.dimens.gapL).testTag("radio-loading"),
        )
        stations.isEmpty() && failure != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FailurePanel(failure, search::retry) }
        stations.isEmpty() && search.searched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val filtered = search.filters.count > 0
            NoResultsState(
                title = stringResource(R.string.nag_radio_none_title),
                detail = stringResource(R.string.nag_radio_none_desc),
                suggestions = if (filtered) listOf(StateAction(stringResource(R.string.nag_clear_filters)) { search.filter(StationFilters()) }) else emptyList(),
                modifier = Modifier.testTag("radio-none"),
            )
        }
        stations.isNotEmpty() -> StationList(
            stations, RadioTab.Search, controller, compact, now, onCopyAddress, onRecord,
            footer = {
                when {
                    search.loading -> Text(stringResource(R.string.nag_radio_loading_more), style = NagTheme.type.label, color = NagTheme.colors.textSecondary)
                    search.failure != null -> NagButton(stringResource(R.string.nag_radio_retry), search::retry, modifier = Modifier.testTag("radio-retry"))
                    search.more -> NagButton(stringResource(R.string.nag_radio_more), search::loadMore, modifier = Modifier.testTag("radio-more"))
                }
            },
        )
    }
}

@Composable
private fun FailurePanel(failure: RadioFailure, onRetry: () -> Unit) {
    val retry = StateAction(stringResource(R.string.nag_radio_retry), onRetry)
    when (failure) {
        is RadioFailure.Offline -> StatePanel(
            tone = StateTone.Warning,
            icon = NagIcons.NetworkOff,
            title = stringResource(R.string.nag_radio_offline_title),
            description = stringResource(R.string.nag_radio_offline_desc),
            primary = retry,
            modifier = Modifier.testTag("radio-offline"),
        )
        is RadioFailure.Busy -> StatePanel(
            tone = StateTone.Warning,
            icon = NagIcons.Warning,
            title = stringResource(R.string.nag_radio_busy_title),
            description = stringResource(R.string.nag_radio_busy_desc),
            primary = retry,
            modifier = Modifier.testTag("radio-busy"),
        )
        is RadioFailure.Server -> StatePanel(
            tone = StateTone.Error,
            icon = NagIcons.ErrorCircle,
            title = stringResource(R.string.nag_radio_error_title),
            description = stringResource(R.string.nag_radio_error_desc, failure.status),
            primary = retry,
            modifier = Modifier.testTag("radio-error"),
        )
    }
}

@Composable
private fun StationList(
    stations: List<Station>,
    tab: RadioTab,
    controller: RadioController,
    compact: Boolean,
    now: () -> Long,
    onCopyAddress: (Station) -> Unit,
    onRecord: ((Station) -> Unit)?,
    footer: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val res = LocalContext.current.resources
    val locale = currentLocale()
    val dimens = NagTheme.dimens
    val moreActions = stringResource(R.string.nag_more_actions)
    LazyColumn(
        modifier.fillMaxSize().testTag("radio-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = dimens.pagePadding - 8.dp, vertical = dimens.gapXs),
    ) {
        items(stations, key = { it.key }) { station ->
            val actions = StationActions(
                onPlay = { controller.play(station) },
                onFavourite = { controller.toggleFavourite(station) },
                onVote = { controller.vote(station) },
                onCopy = { onCopyAddress(station) },
                onRemove = { controller.remove(station) },
                onRecord = onRecord?.let { record -> { record(station) } },
            )
            val menu = { stationMenu(res, station, now(), tab, actions) }
            Box(Modifier.widthIn(max = ContentWidth)) {
                StationRow(station, compact, locale, menu, moreActions, actions)
            }
        }
        if (footer != null) {
            item(key = "radio-footer") {
                Box(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(vertical = dimens.gapM), contentAlignment = Alignment.Center) { footer() }
            }
        }
    }
}

/** S-207: what the Recordings tab needs of the app around it: sharing a file, the folder, the alarm permission. */
class RecordingActions(
    val onShare: (RecordedTrack) -> Unit = {},
    val onChooseFolder: () -> Unit = {},
    val onUseDefaultFolder: () -> Unit = {},
    val onAllowExact: () -> Unit = {},
)

/** What a station's menu and buttons do. */
class StationActions(
    val onPlay: () -> Unit,
    val onFavourite: () -> Unit,
    val onVote: () -> Unit,
    val onCopy: () -> Unit,
    val onRemove: () -> Unit,
    /** S-1107: records the station (null: the screen has no recordings). */
    val onRecord: (() -> Unit)? = null,
)

/**
 * A station's menu: play, favourite, vote (once a day, directory stations only), copy the stream's
 * address, and, in the lists the user keeps, take it out (in favourites that is un-starring).
 */
fun stationMenu(res: Resources, station: Station, now: Long, tab: RadioTab, actions: StationActions): List<MenuEntry> = buildList {
    add(MenuEntry.Item(res.getString(R.string.nag_play), actions.onPlay, shortcut = "Enter", icon = NagIcons.Play))
    add(
        MenuEntry.Item(
            res.getString(if (station.favourite) R.string.nag_radio_unfavourite else R.string.nag_radio_favourite),
            actions.onFavourite,
            icon = if (station.favourite) NagIcons.StarFilled else NagIcons.Star,
        ),
    )
    if (station.uuid != null) {
        val may = VoteRule.canVote(station.lastVotedAt, now)
        add(MenuEntry.Item(res.getString(if (may) R.string.nag_radio_vote else R.string.nag_radio_voted_today), actions.onVote, enabled = may))
    }
    actions.onRecord?.let { record -> add(MenuEntry.Item(res.getString(R.string.nag_radio_rec_record), record, shortcut = ShortcutRegistry.chordLabel(ShortcutId.RadioRecord, res), icon = NagIcons.Record)) }
    add(MenuEntry.Separator)
    add(MenuEntry.Item(res.getString(R.string.nag_radio_copy_address), actions.onCopy, icon = NagIcons.Streams))
    val remove = when (tab) {
        RadioTab.Recent -> R.string.nag_radio_remove_recent
        RadioTab.Imported -> R.string.nag_radio_remove_imported
        else -> null
    }
    if (remove != null) add(MenuEntry.Item(res.getString(remove), actions.onRemove, icon = NagIcons.Delete, destructive = true))
}

@Composable
private fun StationRow(
    station: Station,
    compact: Boolean,
    locale: Locale,
    menu: () -> List<MenuEntry>,
    moreActions: String,
    actions: StationActions,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val play = stringResource(R.string.nag_play)
    val favourite = stringResource(if (station.favourite) R.string.nag_radio_unfavourite else R.string.nag_radio_favourite)
    ContextMenuArea(entries = menu, accessibilityLabel = moreActions, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = dimens.minTarget.coerceAtLeast(60.dp))
                    .focusRing(state, shape)
                    .background(stateBackground(state), shape)
                    .nagClickable(interaction, actions.onPlay, onClickLabel = play)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("station-${station.key}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StationLogo(station, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(station.name, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val place = RadioLabels.place(station, locale)
                    val tech = stationTech(station, LocalContext.current.resources)
                    // on a narrow window the codec goes under the name with the place
                    val line = if (compact) listOf(place, tech).filter { it.isNotEmpty() }.joinToString(" · ") else place
                    if (line.isNotEmpty()) Text(line, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (station.tags.isNotEmpty()) {
                        Text(station.tags.take(MAX_TAGS).joinToString(" · "), style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!compact) {
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        val tech = stationTech(station, LocalContext.current.resources)
                        if (tech.isNotEmpty()) Text(tech, style = NagTheme.type.mono, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (station.votes > 0) {
                            Text(
                                pluralStringResource(R.plurals.nag_radio_votes, station.votes, numbers.format(station.votes)),
                                style = NagTheme.type.labelSmall,
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            NagIconButton(
                if (station.favourite) NagIcons.StarFilled else NagIcons.Star,
                favourite,
                actions.onFavourite,
                tint = if (station.favourite) colors.accent else colors.textSecondary,
                modifier = Modifier.testTag("favourite-${station.key}"),
            )
            IconMenuButton(NagIcons.MoreHorizontal, moreActions, menu)
        }
    }
}

/** "MP3 · 128 kbps": the codec and the bitrate, whichever the directory knows. */
fun stationTech(station: Station, res: Resources): String = listOfNotNull(
    station.codec,
    station.bitrate.takeIf { it > 0 }?.let { res.getString(R.string.nag_sp_kbps, it) },
).joinToString(" · ")

private const val MAX_TAGS = 3
