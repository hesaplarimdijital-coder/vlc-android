package app.nagmedar.ui.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.ActionMenuButton
import app.nagmedar.ui.components.GradientTile
import app.nagmedar.ui.components.IconMenuButton
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagCard
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSearchField
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.rememberMenuState
import app.nagmedar.ui.components.moreActionsLabel
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.covers.rememberCover
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.library.formatDuration
import app.nagmedar.ui.shell.LocalShellBottomPadding
import app.nagmedar.ui.shell.plus
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import java.text.DateFormat
import java.util.Date

/** How the overview lays its cards out: tiles with a cover above, or the narrow list beside an open list. */
internal enum class OverviewLayout { Grid, Rows }

/** The size, in pixels, a cover is decoded at: a quarter of a mosaic, and a cover that stands alone. */
private const val TILE_PX = 160
private const val FULL_PX = 320

/**
 * S-1601: every playlist and smart list at once: a search field and the order to the top, then a
 * card for each ([cards], already filtered and in order; [total] is how many there are before the
 * search). A click opens the list; a right click, a long press, the Menu key or the card's own
 * button gives [menu]. [openKey] marks the card of the list open beside the overview.
 */
@Composable
internal fun PlaylistOverview(
    cards: List<PlaylistCardData>,
    total: Int,
    layout: OverviewLayout,
    openKey: String?,
    query: String,
    onQuery: (String) -> Unit,
    sort: PlaylistSort,
    onSort: (PlaylistSort) -> Unit,
    onOpen: (PlaylistCardData) -> Unit,
    menu: (PlaylistCardData) -> List<MenuEntry>,
    modifier: Modifier = Modifier,
) {
    val dimens = NagTheme.dimens
    val locale = LocalContext.current.resources.locale()
    val dates = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
    val sortNames = PlaylistSort.entries.associateWith { sortName(it) }
    val sortEntries = {
        PlaylistSort.entries.map { option -> MenuEntry.Item(sortNames.getValue(option), { onSort(option) }, checked = option == sort) }
    }
    val sortLabel = stringResource(R.string.nag_sort_by, sortNames.getValue(sort))
    Column(modifier.fillMaxSize().testTag("playlists-overview")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
        ) {
            NagSearchField(
                query,
                onQuery,
                placeholder = stringResource(R.string.nag_pl_search),
                clearLabel = stringResource(R.string.nag_search_clear),
                modifier = Modifier.weight(1f).testTag("playlists-search"),
            )
            if (layout == OverviewLayout.Grid) {
                MenuButton(sortLabel, sortEntries, icon = NagIcons.Sort, modifier = Modifier.testTag("playlists-sort"))
            } else {
                IconMenuButton(NagIcons.Sort, sortLabel, sortEntries, modifier = Modifier.testTag("playlists-sort"))
            }
        }
        when {
            cards.isEmpty() && total > 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NoResultsState(
                    title = stringResource(R.string.nag_pl_no_match, query.trim()),
                    suggestions = listOf(StateAction(stringResource(R.string.nag_search_clear)) { onQuery("") }),
                )
            }
            layout == OverviewLayout.Grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapS) + LocalShellBottomPadding.current,
                horizontalArrangement = Arrangement.spacedBy(dimens.gapM),
                verticalArrangement = Arrangement.spacedBy(dimens.gapM),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(cards, key = { it.key }) { card ->
                    PlaylistGridCard(card, card.key == openKey, dates, onOpen, menu)
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = dimens.gapS, vertical = dimens.gapXs) + LocalShellBottomPadding.current,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(cards, key = { it.key }) { card ->
                    PlaylistRowCard(card, card.key == openKey, dates, onOpen, menu)
                }
            }
        }
    }
}

@Composable
private fun sortName(sort: PlaylistSort): String = stringResource(
    when (sort) {
        PlaylistSort.Name -> R.string.nag_pl_sort_name
        PlaylistSort.Recent -> R.string.nag_pl_sort_recent
        PlaylistSort.Created -> R.string.nag_pl_sort_created
    },
)

/** "12 parça · 48 dk": a list's size. */
@Composable
internal fun playlistMeta(count: Int?, durationMs: Long?): String = listOfNotNull(
    count?.let { pluralStringResource(R.plurals.nag_tracks_count, it, it) },
    durationMs?.takeIf { it > 0 }?.let(::formatDuration),
).joinToString(" · ")

/** "Son çalınma: 3 Eki 2026", or that it never was. */
@Composable
internal fun playlistLastPlayed(at: Long, dates: DateFormat): String =
    if (at > 0) stringResource(R.string.nag_pl_last_played, dates.format(Date(at))) else stringResource(R.string.nag_pl_never_played)

/** A tile of the overview: the cover on top, the name and what it holds under it. */
@Composable
private fun PlaylistGridCard(
    card: PlaylistCardData,
    selected: Boolean,
    dates: DateFormat,
    onOpen: (PlaylistCardData) -> Unit,
    menu: (PlaylistCardData) -> List<MenuEntry>,
) {
    val colors = NagTheme.colors
    val moreActions = stringResource(R.string.nag_more_actions)
    val menuState = rememberMenuState()
    var within by remember { mutableStateOf(false) }
    val touch = NagTheme.inputMode == InputMode.Touch
    ContextMenuArea(entries = { menu(card) }, modifier = Modifier.fillMaxWidth(), accessibilityLabel = moreActions, asSheet = true, sheetTitle = card.name) {
        NagCard(
            onClick = { onOpen(card) },
            selected = selected,
            modifier = Modifier.fillMaxWidth().onFocusChanged { within = it.hasFocus }.testTag("playlist-card"),
        ) { state ->
            Column(Modifier.padding(8.dp)) {
                Box {
                    PlaylistCover(card.covers, card.key, card.smart, Modifier.fillMaxWidth().aspectRatio(1f))
                    if (card.smart) {
                        ToneBadge(stringResource(R.string.nag_pl_smart_badge), StateTone.Info, Modifier.align(Alignment.TopStart).padding(6.dp))
                    }
                    // under the pointer or the keyboard, or always where a finger is the pointer
                    if (touch || state.hovered || within || menuState.isOpen) {
                        ActionMenuButton(menuState, NagIcons.MoreHorizontal, moreActionsLabel(card.name), { menu(card) }, Modifier.align(Alignment.TopEnd).padding(2.dp), card.name)
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    card.name,
                    style = NagTheme.type.bodyStrong,
                    color = if (selected) colors.onSelection else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    playlistMeta(card.count, card.durationMs),
                    style = NagTheme.type.labelSmall,
                    color = if (selected) colors.onSelection else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    playlistLastPlayed(card.lastPlayedAt, dates),
                    style = NagTheme.type.labelSmall,
                    color = if (selected) colors.onSelection else colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A line of the narrow list beside an open list: a small cover, the name, what it holds, when it was last played. */
@Composable
private fun PlaylistRowCard(
    card: PlaylistCardData,
    selected: Boolean,
    dates: DateFormat,
    onOpen: (PlaylistCardData) -> Unit,
    menu: (PlaylistCardData) -> List<MenuEntry>,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val moreActions = stringResource(R.string.nag_more_actions)
    val smartLabel = stringResource(R.string.nag_pl_smart_badge)
    val menuState = rememberMenuState()
    var within by remember { mutableStateOf(false) }
    val touch = NagTheme.inputMode == InputMode.Touch
    ContextMenuArea(entries = { menu(card) }, modifier = Modifier.fillMaxWidth(), accessibilityLabel = moreActions, asSheet = true, sheetTitle = card.name) {
        NagCard(
            onClick = { onOpen(card) },
            selected = selected,
            shape = RoundedCornerShape(dimens.radiusControl),
            modifier = Modifier.fillMaxWidth().onFocusChanged { within = it.hasFocus }.testTag("playlist-card"),
        ) { state ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlaylistCover(card.covers, card.key, card.smart, Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        card.name,
                        style = NagTheme.type.bodyStrong,
                        color = if (selected) colors.onSelection else colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        playlistMeta(card.count, card.durationMs),
                        style = NagTheme.type.labelSmall,
                        color = if (selected) colors.onSelection else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        playlistLastPlayed(card.lastPlayedAt, dates),
                        style = NagTheme.type.labelSmall,
                        color = if (selected) colors.onSelection else colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (card.smart) {
                    Icon(
                        NagIcons.SmartList,
                        contentDescription = smartLabel,
                        tint = if (selected) colors.onSelection else colors.textTertiary,
                        modifier = Modifier.size(dimens.icon),
                    )
                }
                if (touch || state.hovered || within || menuState.isOpen) {
                    ActionMenuButton(menuState, NagIcons.MoreHorizontal, moreActionsLabel(card.name), { menu(card) }, sheetTitle = card.name)
                }
            }
        }
    }
}

/**
 * S-1601: a list's cover. The covers of its first tracks as a mosaic (one alone fills it), the
 * places without one a gradient that is the same for the same [seed]; a list with no covers is a
 * gradient with a note (a smart list's mark when [smart]). Decorative: the name beside it says it.
 */
@Composable
internal fun PlaylistCover(covers: List<CoverTrack>, seed: String, smart: Boolean, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Box(modifier.clip(shape)) {
        when {
            covers.isEmpty() -> GradientTile(seed, Modifier.fillMaxSize()) {
                Icon(
                    if (smart) NagIcons.SmartList else NagIcons.Music,
                    contentDescription = null,
                    tint = colors.text,
                    modifier = Modifier.fillMaxSize(0.4f),
                )
            }
            covers.size == 1 -> CoverTile(covers[0], seed, FULL_PX, Modifier.fillMaxSize())
            else -> Column(Modifier.fillMaxSize()) {
                repeat(2) { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        repeat(2) { column ->
                            val index = row * 2 + column
                            CoverTile(covers.getOrNull(index), "$seed/$index", TILE_PX, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

/** One picture of a mosaic: the track's cover once it is found, a gradient before and instead. */
@Composable
private fun CoverTile(track: CoverTrack?, seed: String, px: Int, modifier: Modifier) {
    val image = rememberCover(track, px)
    if (image != null) {
        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        GradientTile(seed, modifier)
    }
}
