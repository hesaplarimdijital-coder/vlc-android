package app.nagmedar.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.ListAppear
import app.nagmedar.ui.components.ListClick
import app.nagmedar.ui.components.LocalClickModifiers
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MoreButton
import app.nagmedar.ui.components.MultiSelection
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SelectionBar
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.listAppear
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.rememberListAppear
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.toActionGroups
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.shell.LocalShellBottomPadding
import app.nagmedar.ui.shell.OnReselect
import app.nagmedar.ui.shell.plus
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.SemiBoldPlus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// 1.1.1 (the design's phone page): a phone's lists. The groups as rows (an album's cover, an artist's initial, a genre's
// or a folder's sign) under their letters; the tracks as rows with their covers; a group's own page (its band, its
// cover and name, Oynat and Karıştır, its tracks by their numbers). The letters' wave runs down their end.

/** What a phone's Music page gives its lists for a group's page: the band's inks, the status bar over it. */
@Immutable
internal class PhoneBand(val inks: EbruInks, val statusTop: Dp)

internal val LocalPhoneBand = staticCompositionLocalOf<PhoneBand?> { null }

/**
 * The groups of [view] in [sort] when it is not the view's own: made from [base] (the view's own order) off the main
 * thread the first time, the last order shown meanwhile; then kept with the library ([MusicLibrary.orderedGroups]).
 */
@Composable
internal fun rememberOrderedGroups(library: MusicLibrary, view: MusicView, filter: TechFilter, sort: GroupSort?, base: List<MusicGroup>): List<MusicGroup> {
    if (sort == null || sort == GroupSort.default(view) || !view.grouped) return base
    val made by produceState<List<MusicGroup>?>(null, library, view, filter, sort, base) {
        value = withContext(Dispatchers.Default) { library.orderedGroups(view, filter, sort) }
    }
    return made ?: base
}

/** A state panel under a phone's header, scrolling with it; alone in the middle where there is no header. */
@Composable
internal fun UnderPhoneHeader(header: (@Composable () -> Unit)?, content: @Composable () -> Unit) {
    if (header == null) {
        Box(Modifier.fillMaxSize().padding(LocalShellBottomPadding.current), contentAlignment = Alignment.Center) { content() }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(LocalShellBottomPadding.current)) {
        header()
        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { content() }
    }
}

/** A lettered list's items: a letter's heading, or the item. */
internal sealed interface Lettered<out T> {
    class Letter(val letter: String, val count: Int, val key: String) : Lettered<Nothing>
    class Item<T>(val item: T, val index: Int) : Lettered<T>
}

/** [items] under their letters' headings ([letterRanges]) when [name] gives them one; else as they are. */
internal fun <T> letteredItems(items: List<T>, name: ((T) -> String?)?, key: (T) -> String, locale: Locale, nameless: String): List<Lettered<T>> {
    if (name == null) return items.mapIndexed { i, t -> Lettered.Item(t, i) }
    val out = ArrayList<Lettered<T>>(items.size + 30)
    letterRanges(items, name, locale, nameless).forEach { (letter, range) ->
        out += Lettered.Letter(letter, range.last - range.first + 1, LETTER_KEY + key(items[range.first]))
        for (k in range) out += Lettered.Item(items[k], k)
    }
    return out
}

/** The wave's letters of [entries], [first] the list's index of their first one. */
internal fun <T> runsOf(entries: List<Lettered<T>>, first: Int): List<LetterRun> =
    entries.mapIndexedNotNull { i, e -> (e as? Lettered.Letter)?.let { LetterRun(it.letter, it.count, i + first) } }

/** The wave over a lettered lazy list: its letters, the one at the top under the chips, a jump, its room. */
@Composable
private fun ListWave(letters: List<WaveLetter>, list: LazyListState, room: Int, header: Boolean, modifier: Modifier) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val bottom = LocalShellBottomPadding.current
    val gap = with(density) { 8.dp.roundToPx() }
    LetterWave(
        letters,
        current = {
            val under = list.layoutInfo.visibleItemsInfo.firstOrNull { it.offset + it.size > room }?.index ?: list.firstVisibleItemIndex
            currentWaveLetter(letters, under)
        },
        onJump = { letter -> scope.launch { list.scrollToItem(letter.index, -room) } },
        top = {
            val head = list.layoutInfo.visibleItemsInfo.firstOrNull()?.takeIf { it.index == 0 && header }
            maxOf(room, (head?.let { it.offset + it.size } ?: 0) + gap)
        },
        bottom = { list.layoutInfo.viewportSize.height - with(density) { bottom.calculateBottomPadding().roundToPx() } - gap },
        modifier = modifier,
    )
}

// ------------------------------------------------------------------- the groups

/**
 * A way in's groups as a phone's rows (the design's list view): under their letters while they are in order by a name
 * ([words]), each a 64 dp row ending in "⋮", their letters' wave down the end. [leading]: the page's header.
 */
@Composable
internal fun PhoneGroupList(
    groups: List<MusicGroup>,
    state: MusicBrowserState,
    actions: MusicActions,
    listState: LazyListState,
    modifier: Modifier,
    leading: (@Composable () -> Unit)?,
    stats: Map<String, TrackStats>,
    playing: String?,
    words: GroupGridWords,
    chipsRoom: Dp,
) {
    val res = LocalContext.current.resources
    val locale = currentLocale()
    val nameless = words.nameless()
    val reduced = NagTheme.motion.reduced
    val room = with(LocalDensity.current) { chipsRoom.roundToPx() }
    val entries = remember(groups, words.letters, words.sortName, locale, nameless) {
        letteredItems(groups, words.sortName.takeIf { words.letters }, { it.key }, locale, nameless)
    }
    val first = if (leading != null) 1 else 0
    val alphabet = rememberAlphabet()
    val letters = remember(entries, alphabet) { if (words.letters) waveLetters(runsOf(entries, first), alphabet) else emptyList() }
    val wave = letters.count { it.count > 0 } >= 2
    // 1.0.8 (Desktop Kit 2.8.0): Müzik's entry tapped again: the groups go to their top
    OnReselect { if (reduced) listState.scrollToItem(0) else listState.animateScrollToItem(0) }
    // 1.0.3: on a phone the first groups arrive in turn, again for another way in, not when a group's tracks close
    val appear = rememberListAppear(state.view)
    LaunchedEffect(state.reveal, entries) {
        val key = state.reveal ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it is Lettered.Item && it.item.key == key }
        if (index >= 0) {
            if (reduced) listState.scrollToItem(index + first, -room) else listState.animateScrollToItem(index + first, -room)
            state.reveal = null
        }
    }
    val end = if (wave) WAVE_ROOM else 8.dp
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize().testTag("music-groups"), state = listState, contentPadding = PaddingValues(bottom = 16.dp) + LocalShellBottomPadding.current) {
            if (leading != null) item(key = LEADING_ITEM, contentType = LEADING_ITEM) { leading() }
            itemsIndexed(entries, key = { _, e -> if (e is Lettered.Letter) e.key else (e as Lettered.Item).item.key }, contentType = { _, e -> if (e is Lettered.Letter) 0 else 1 }) { index, entry ->
                when (entry) {
                    is Lettered.Letter -> LetterHeading(
                        entry.letter, words.count(entry.count),
                        Modifier.padding(start = 16.dp, end = end, top = 6.dp, bottom = 4.dp),
                    )
                    is Lettered.Item -> {
                        val group = entry.item
                        val title = phoneGroupTitle(group, state.view)
                        // the menu plays the group's tracks, as the table's rows do one track
                        val menu = { groupMenu(res, group, actions.playingFrom(groupSource(state.view, group))) }
                        ContextMenuArea(
                            entries = menu,
                            modifier = Modifier.listAppear(appear, index).padding(start = 8.dp, end = end, bottom = 4.dp),
                            accessibilityLabel = stringResource(R.string.nag_more_actions),
                            asSheet = true,
                            sheetTitle = title,
                        ) {
                            PhoneGroupRow(group, state.view, title, stats, playing, menu) { state.group = group.key }
                        }
                    }
                }
            }
        }
        if (wave) ListWave(letters, listState, room, leading != null, Modifier.align(Alignment.TopEnd))
    }
}

/** A group's name in its row: a folder's last name (its path in the line). */
@Composable
private fun phoneGroupTitle(group: MusicGroup, view: MusicView): String =
    if (view == MusicView.Folders) group.title?.substringAfterLast('/') ?: groupTitle(group, view) else groupTitle(group, view)

/**
 * A group's row (the design's): an album's cover (in the ink's ring while it plays), an artist's initial, a genre's or a
 * folder's sign; its name (in the ink while it plays) and line; an album's listening trace; "⋮" with its menu.
 */
@Composable
private fun PhoneGroupRow(
    group: MusicGroup,
    view: MusicView,
    title: String,
    stats: Map<String, TrackStats>,
    playing: String?,
    menu: () -> List<MenuEntry>,
    onOpen: () -> Unit,
) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(12.dp)
    val playsHere = remember(group, playing) { playing != null && group.tracks.any { it.uri == playing } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .height(64.dp)
                .focusRing(state, shape)
                .clip(shape)
                .background(stateBackground(state))
                .nagClickable(interaction, onOpen)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (view == MusicView.Albums) {
                GroupCover(group, Modifier.size(52.dp).coverRing(playsHere, colors.accent, 5.dp, gap = 2.dp), corner = 5.dp, read = 64.dp)
            } else {
                GroupSign(view, group.title)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = designText(15f, 20f, SemiBoldPlus),
                    color = when {
                        playsHere -> colors.accentText
                        group.title == null -> colors.textSecondary
                        else -> colors.text
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(groupLine(group, view), style = designText(13f, 18f), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (view == MusicView.Albums) ListeningTrace(group.tracks, stats, playing, Modifier.padding(top = 5.dp).fillMaxWidth(), tick = 7.dp, gap = 2.dp)
            }
        }
        MoreButton(groups = { menu().toActionGroups() }, title = title, icon = NagIcons.MoreVertical)
    }
}

// ------------------------------------------------------------------- the tracks

/** The name a track list's letters go by in [sort], if they go under letters at all. */
private fun letterName(sort: MusicSort?): ((Track) -> String?)? = when (sort?.column) {
    MusicColumn.Title -> { t -> t.title }
    MusicColumn.Artist -> { t -> t.artist?.takeIf { it.isNotBlank() } }
    MusicColumn.AlbumArtist -> { t -> t.filedArtist }
    MusicColumn.Album -> { t -> t.album?.takeIf { it.isNotBlank() } }
    else -> null
}

/**
 * A phone's tracks: the whole library's (the Tracks view, the recently played) under [leading] (the page's header), as
 * rows with their covers under their letters while they are in order by words; or a group's own page ([page]): its
 * header with the cover, the name, Oynat and Karıştır, its tracks by their numbers. A tap plays from the row; a long
 * press opens its menu (as a sheet), where "Seç" starts a selection; the rows end with "⋮".
 */
@Composable
internal fun PhoneTrackList(
    group: MusicGroup,
    shown: List<Track>,
    state: MusicBrowserState,
    actions: MusicActions,
    stats: Map<String, TrackStats>,
    selection: MultiSelection,
    listState: LazyListState,
    leading: (@Composable () -> Unit)?,
    chipsRoom: Dp,
    playing: String?,
    page: Boolean,
    appear: ListAppear,
) {
    val colors = NagTheme.colors
    val res = LocalContext.current.resources
    val locale = currentLocale()
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val modifiers = LocalClickModifiers.current
    val reduced = NagTheme.motion.reduced
    val room = with(LocalDensity.current) { chipsRoom.roundToPx() }
    val keys = remember(shown) { shown.map { it.uri } }
    val sort = effectiveSort(state)
    val name = if (page || state.view == MusicView.Recent) null else letterName(sort)
    val nameless = stringResource(R.string.nag_unknown_artist)
    val entries = remember(shown, name, locale) { letteredItems(shown, name, { it.uri }, locale, nameless) }
    val header: (@Composable () -> Unit)? = if (page) ({ PhoneGroupPageHeader(group, state.view, shown, actions, chipsRoom) }) else leading
    val first = if (header != null) 1 else 0
    val alphabet = rememberAlphabet()
    val letters = remember(entries, alphabet) { if (name != null) waveLetters(runsOf(entries, first), alphabet) else emptyList() }
    val wave = letters.count { it.count > 0 } >= 2
    OnReselect { if (reduced) listState.scrollToItem(0) else listState.animateScrollToItem(0) }
    val end = if (wave) WAVE_ROOM else 8.dp
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("music-tracks"),
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp) + LocalShellBottomPadding.current,
        ) {
            if (header != null) item(key = LEADING_ITEM, contentType = LEADING_ITEM) { header() }
            itemsIndexed(entries, key = { _, e -> if (e is Lettered.Letter) e.key else (e as Lettered.Item).item.uri }, contentType = { _, e -> if (e is Lettered.Letter) 0 else 1 }) { row, entry ->
                when (entry) {
                    is Lettered.Letter -> LetterHeading(
                        entry.letter, pluralStringResource(R.plurals.nag_tracks_count, entry.count, entry.count),
                        Modifier.padding(start = 16.dp, end = end, top = 6.dp, bottom = 4.dp),
                    )
                    is Lettered.Item -> {
                        val track = entry.item
                        val index = entry.index
                        val chosen = track.uri in selection.keys
                        val menu: () -> List<MenuEntry> = {
                            // on one of several selected rows, the menu is the selection's (Desktop Kit 1.0)
                            if (chosen && selection.count > 1) {
                                tracksMenu(res, shown.filter { it.uri in selection }, actions, rate = true)
                            } else {
                                actions.menu(track, play = { actions.play(shown, index) }, select = if (pointer) null else ({ selection.startSelecting(track.uri) }))
                            }
                        }
                        ContextMenuArea(
                            entries = menu,
                            modifier = Modifier.listAppear(appear, row).padding(start = 8.dp, end = end),
                            accessibilityLabel = stringResource(R.string.nag_more_actions),
                            asSheet = true,
                            sheetTitle = track.title,
                        ) {
                            PhoneTrackRow(
                                track, number = if (page) index + 1 else null, playing = track.uri == playing, chosen = chosen,
                                selecting = selection.selecting, menu = menu,
                                modifier = actions.rowModifier(track, atOnce = { track.uri in selection }) {
                                    if (track.uri in selection && selection.count > 1) shown.filter { it.uri in selection } else listOf(track)
                                },
                            ) {
                                when (ListClick.of(selection.selecting, modifiers.takeFinger(), touchLayout = !pointer)) {
                                    ListClick.Toggle -> selection.toggle(track.uri)
                                    ListClick.Select -> {
                                        val (ctrl, shift) = modifiers.take()
                                        selection.click(track.uri, keys, ctrl, shift)
                                    }
                                    ListClick.Open -> actions.play(shown, index)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (wave) ListWave(letters, listState, room, header != null, Modifier.align(Alignment.TopEnd))
        // a selection being made: what it is and what can be done with it, under the chips
        if (selection.count > 1 || selection.selecting) {
            val chosen = shown.filter { it.uri in selection }
            SelectionBar(
                text = pluralStringResource(R.plurals.nag_selected_tracks, chosen.size, chosen.size),
                onClear = selection::clear,
                clearLabel = stringResource(R.string.nag_selection_clear),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = chipsRoom, start = 8.dp, end = 8.dp)
                    .background(colors.surfaceRaised, RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                NagButton(stringResource(R.string.nag_selection_play), { actions.play(chosen, 0) }, primary = true)
                NagButton(stringResource(R.string.nag_play_next), { actions.playNext(chosen) })
                NagButton(stringResource(R.string.nag_add_to_queue), { actions.queue(chosen) })
                actions.addToPlaylist?.let { add -> NagButton(stringResource(R.string.nag_pl_add), { add(chosen) }) }
                actions.askRating?.let { ask -> NagButton(stringResource(R.string.nag_rate), { ask(chosen) }) }
                actions.convert?.let { convert -> NagButton(stringResource(R.string.nag_convert), { convert(chosen) }) }
                actions.share?.let { share -> NagButton(stringResource(R.string.nag_media_share), { share(chosen) }) }
            }
        }
    }
}

/**
 * A track's row: its cover (in the ink's ring while it plays) or, on a group's page, its number; its title (in the ink
 * while it plays) and its artist and album; "⋮" with its menu. Selected, on the selection's colour.
 */
@Composable
private fun PhoneTrackRow(
    track: Track,
    number: Int?,
    playing: Boolean,
    chosen: Boolean,
    selecting: Boolean,
    menu: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(12.dp)
    val line = if (number != null) {
        listOfNotNull(track.artist?.takeIf { it.isNotBlank() }, formatDuration(track.durationMs)).joinToString(" · ")
    } else {
        listOfNotNull(track.artist?.takeIf { it.isNotBlank() }, track.album?.takeIf { it.isNotBlank() }).joinToString(" · ")
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .height(64.dp)
                .focusRing(state, shape)
                .clip(shape)
                .background(stateBackground(state, chosen))
                .semantics { if (selecting || chosen) selected = chosen }
                .nagClickable(interaction, onClick)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (number != null) {
                Text(
                    number.toString(),
                    style = designText(14f, 20f, if (playing) SemiBoldPlus else FontWeight.Normal),
                    color = if (playing) colors.accentText else colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 28.dp),
                )
            } else {
                TrackCover(track, 48.dp, Modifier.coverRing(playing, colors.accent, 5.dp, gap = 2.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = designText(15f, 20f, SemiBoldPlus),
                    color = when {
                        chosen -> colors.onSelection
                        playing -> colors.accentText
                        else -> colors.text
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line.isNotEmpty()) {
                    Text(line, style = designText(13f, 18f), color = if (chosen) colors.onSelection else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        MoreButton(groups = { menu().toActionGroups() }, title = track.title, icon = NagIcons.MoreVertical)
    }
}

/**
 * A group's page's header on a phone: the band under the chips (the page's own, [LocalPhoneBand]), the album's cover
 * (its menu changes it) or the group's sign, its name in Bodoni's italic and its line, then Oynat and Karıştır and the
 * converter.
 */
@Composable
private fun PhoneGroupPageHeader(group: MusicGroup, view: MusicView, shown: List<Track>, actions: MusicActions, chipsRoom: Dp) {
    val colors = NagTheme.colors
    val band = LocalPhoneBand.current
    val title = groupTitle(group, view)
    val ready = shown.isNotEmpty()
    Column(Modifier.fillMaxWidth().testTag("music-group-header")) {
        if (band != null) {
            EbruBand(band.inks, band.statusTop + PHONE_PAGE_BAND, Modifier.reportsBand())
        } else {
            Spacer(Modifier.height(chipsRoom))
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (view == MusicView.Albums) {
                AlbumCover(group, side = 88.dp)
            } else {
                GroupSign(view, group.title, 72.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = NagTheme.type.display, color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                Text(groupLine(group, view), style = designText(14f, 20f), color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayPill(if (ready) ({ actions.play(shown, 0) }) else null)
            ShufflePill(if (ready) ({ actions.play(shown.shuffled(), 0) }) else null)
            Spacer(Modifier.weight(1f))
            // S-1010: an album's, an artist's, a folder's tracks converted
            actions.convert?.let { convert ->
                NagIconButton(NagIcons.Convert, stringResource(R.string.nag_convert), { convert(shown) }, modifier = Modifier.testTag("music-convert"))
            }
        }
    }
}
