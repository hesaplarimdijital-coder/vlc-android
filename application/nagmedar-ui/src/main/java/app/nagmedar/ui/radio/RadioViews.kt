package app.nagmedar.ui.radio

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat
import java.util.Locale

/** The widest the radio screen's content grows. */
private val ContentWidth = 960.dp

/**
 * S-1105: the radio screen's search page: the box for the name's words, the filters (country,
 * language, tag, codec, minimum bitrate) and the order, then the stations found with their logo,
 * name, place, codec and bitrate, votes and tags. Enter on a station plays it; its menu (right
 * click, the Menu key, the button at its end) votes, copies the stream's address. [searchRequested]
 * (the search shortcut) puts the keyboard in the box and is answered with [onSearchHandled].
 */
@Composable
fun RadioPage(
    controller: RadioController,
    modifier: Modifier = Modifier,
    searchRequested: Boolean = false,
    onSearchHandled: () -> Unit = {},
    onCopyAddress: (Station) -> Unit = {},
    now: () -> Long = System::currentTimeMillis,
) {
    val search = controller.search
    val context = LocalContext.current
    val res = context.resources
    val compact = currentWidthClass() == WidthClass.Compact
    val focus = remember { FocusRequester() }
    LaunchedEffect(search) {
        search.start()
        search.loadOptions()
    }
    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            focus.requestFocus()
            onSearchHandled()
        }
    }
    val notice = controller.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            android.widget.Toast.makeText(context, noticeText(res, notice), android.widget.Toast.LENGTH_SHORT).show()
            controller.noticed()
        }
    }
    Column(modifier.fillMaxSize()) {
        PageHeader(title = stringResource(R.string.nag_nav_radio), subtitle = stringResource(R.string.nag_radio_source))
        Column(Modifier.widthIn(max = ContentWidth).padding(horizontal = NagTheme.dimens.pagePadding)) {
            RadioSearchField(search.text, search::type, search::search, focus)
            Spacer(Modifier.size(NagTheme.dimens.gapS))
            FilterBar(search)
            Spacer(Modifier.size(NagTheme.dimens.gapXs))
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Results(search, controller, compact, now, onCopyAddress)
        }
    }
}

/** What a notice says. */
fun noticeText(res: Resources, notice: RadioNotice): String = when (notice) {
    is RadioNotice.Voted -> res.getString(R.string.nag_radio_voted, notice.station)
    is RadioNotice.AlreadyVoted -> res.getString(R.string.nag_radio_already_voted, notice.station)
    is RadioNotice.VoteRefused -> res.getString(R.string.nag_radio_vote_refused)
    RadioNotice.VoteOffline -> res.getString(R.string.nag_radio_vote_offline)
    RadioNotice.Failed -> res.getString(R.string.nag_radio_failed)
}

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
            if (text.isEmpty()) Text(placeholder, style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1)
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
private fun Results(search: RadioSearch, controller: RadioController, compact: Boolean, now: () -> Long, onCopyAddress: (Station) -> Unit) {
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
        stations.isNotEmpty() -> StationList(search, controller, compact, now, onCopyAddress)
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
private fun StationList(search: RadioSearch, controller: RadioController, compact: Boolean, now: () -> Long, onCopyAddress: (Station) -> Unit) {
    val res = LocalContext.current.resources
    val locale = currentLocale()
    val dimens = NagTheme.dimens
    val moreActions = stringResource(R.string.nag_more_actions)
    LazyColumn(
        Modifier.fillMaxSize().testTag("radio-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = dimens.pagePadding - 8.dp, vertical = dimens.gapXs),
    ) {
        items(search.stations, key = { it.key }) { station ->
            val menu = {
                stationMenu(
                    res, station, now(),
                    onPlay = { controller.play(station) },
                    onVote = { controller.vote(station) },
                    onCopy = { onCopyAddress(station) },
                )
            }
            Box(Modifier.widthIn(max = ContentWidth)) {
                StationRow(station, compact, locale, menu, moreActions, onPlay = { controller.play(station) })
            }
        }
        item(key = "radio-footer") {
            Box(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(vertical = dimens.gapM), contentAlignment = Alignment.Center) {
                when {
                    search.loading -> Text(stringResource(R.string.nag_radio_loading_more), style = NagTheme.type.label, color = NagTheme.colors.textSecondary)
                    search.failure != null -> NagButton(stringResource(R.string.nag_radio_retry), search::retry, modifier = Modifier.testTag("radio-retry"))
                    search.more -> NagButton(stringResource(R.string.nag_radio_more), search::loadMore, modifier = Modifier.testTag("radio-more"))
                }
            }
        }
    }
}

/** A station's menu: play, vote (once a day), copy the stream's address. */
fun stationMenu(
    res: Resources,
    station: Station,
    now: Long,
    onPlay: () -> Unit,
    onVote: () -> Unit,
    onCopy: () -> Unit,
): List<MenuEntry> = buildList {
    add(MenuEntry.Item(res.getString(R.string.nag_play), onPlay, shortcut = "Enter", icon = NagIcons.Play))
    if (station.uuid != null) {
        val may = VoteRule.canVote(station.lastVotedAt, now)
        add(MenuEntry.Item(res.getString(if (may) R.string.nag_radio_vote else R.string.nag_radio_voted_today), onVote, enabled = may))
    }
    add(MenuEntry.Separator)
    add(MenuEntry.Item(res.getString(R.string.nag_radio_copy_address), onCopy, icon = NagIcons.Streams))
}

@Composable
private fun StationRow(
    station: Station,
    compact: Boolean,
    locale: Locale,
    menu: () -> List<MenuEntry>,
    moreActions: String,
    onPlay: () -> Unit,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val play = stringResource(R.string.nag_play)
    ContextMenuArea(entries = menu, accessibilityLabel = moreActions, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = dimens.minTarget.coerceAtLeast(60.dp))
                    .focusRing(state, shape)
                    .background(stateBackground(state), shape)
                    .nagClickable(interaction, onPlay, onClickLabel = play)
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
                        if (tech.isNotEmpty()) Text(tech, style = NagTheme.type.mono, color = colors.textSecondary, maxLines = 1)
                        if (station.votes > 0) {
                            Text(
                                pluralStringResource(R.plurals.nag_radio_votes, station.votes, numbers.format(station.votes)),
                                style = NagTheme.type.labelSmall,
                                color = colors.textTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
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
