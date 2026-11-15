package app.nagmedar.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.ChipRow
import app.nagmedar.ui.components.FilterChip
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.pressScale
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.covers.rememberCover
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.shell.LocalShellBottomPadding
import app.nagmedar.ui.shell.OnReselect
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.shell.plus
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.theme.NagTheme
import java.util.Calendar
import kotlin.math.min
import kotlinx.coroutines.launch

/** 1.1.0 (Ebru): how the library shows its groups: their covers in a grid, or rows beside their tracks. */
enum class MusicLayout { Grid, List }

/**
 * 1.1.0 (Ebru): the quick filters over a way in's groups: all of them; those listened to in part ("Yarım kalanlar");
 * those never played; those with a track added this year.
 */
enum class QuickFilter(val label: Int) {
    All(R.string.nag_music_quick_all),
    Unfinished(R.string.nag_music_quick_unfinished),
    Unheard(R.string.nag_music_quick_unheard),
    ThisYear(R.string.nag_music_quick_new),
}

/** These groups through [filter], by the play counts of [stats] and the tracks' dates added in [year]. */
internal fun List<MusicGroup>.quick(filter: QuickFilter, stats: Map<String, TrackStats>, year: Int): List<MusicGroup> = when (filter) {
    QuickFilter.All -> this
    QuickFilter.Unfinished -> filter { group ->
        val heard = group.tracks.count { (stats[it.uri]?.plays ?: 0) > 0 }
        heard in 1 until group.tracks.size
    }
    QuickFilter.Unheard -> filter { group -> group.tracks.none { (stats[it.uri]?.plays ?: 0) > 0 } }
    QuickFilter.ThisYear -> filter { group -> group.tracks.any { yearAdded(it.addedAt) == year } }
}

/** The year a track was added in (seconds or milliseconds, [epochMillis]); -1 for a track without the date. */
internal fun yearAdded(addedAt: Long): Int {
    if (addedAt <= 0) return -1
    return Calendar.getInstance().apply { timeInMillis = epochMillis(addedAt) }.get(Calendar.YEAR)
}

internal fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

/**
 * The quick filters as chips over the groups, scrolling with them; one is always chosen ("Tümü" when none other is),
 * so screen readers hear them as radio buttons. [wrap] as [ChipRow]'s: a narrow pane beside the tracks keeps them on
 * one line that scrolls sideways.
 */
@Composable
internal fun QuickFilterRow(state: MusicBrowserState, modifier: Modifier = Modifier, wrap: Boolean = NagTheme.largeWindow) {
    ChipRow(
        modifier.fillMaxWidth().padding(vertical = 4.dp).selectableGroup().testTag("music-quick"),
        contentPadding = PaddingValues(horizontal = NagTheme.dimens.pagePadding),
        wrap = wrap,
    ) {
        QuickFilter.entries.forEach { filter ->
            FilterChip(stringResource(filter.label), selected = state.quick == filter, onToggle = { state.quick = filter }, single = true)
        }
    }
}

/** A narrow window's one button to the other way of showing the groups, named and drawn for it. */
@Composable
internal fun LayoutSwitch(state: MusicBrowserState) {
    val grid = state.layout == MusicLayout.Grid
    NagIconButton(
        if (grid) NagIcons.ListView else NagIcons.Grid,
        stringResource(if (grid) R.string.nag_music_layout_list else R.string.nag_music_layout_grid),
        { state.changeLayout(if (grid) MusicLayout.List else MusicLayout.Grid) },
        modifier = Modifier.testTag("music-layout"),
    )
}

/** The grid and the list as two buttons, the one shown pressed. */
@Composable
internal fun LayoutToggle(state: MusicBrowserState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val grid = state.layout == MusicLayout.Grid
        NagIconButton(
            NagIcons.Grid, stringResource(R.string.nag_music_layout_grid), { state.changeLayout(MusicLayout.Grid) },
            modifier = Modifier.testTag("music-layout-grid").semantics { selected = grid }, selected = grid,
        )
        NagIconButton(
            NagIcons.ListView, stringResource(R.string.nag_music_layout_list), { state.changeLayout(MusicLayout.List) },
            modifier = Modifier.testTag("music-layout-list").semantics { selected = !grid }, selected = !grid,
        )
    }
}

/**
 * 1.1.0 (Ebru): a group's listening trace: a tick for each track (at most [MAX_TICKS]: a longer album's tracks share
 * them in turn), those played in the text's colour, those never played in a controls' edge's (each at least 3:1 on the
 * page, apart from each other), the one that plays taller in the accent. TalkBack hears how many of the tracks were
 * played.
 */
@Composable
internal fun ListeningTrace(tracks: List<Track>, stats: Map<String, TrackStats>, playing: String?, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val heard = colors.text
    val unheard = colors.controlBorder
    val accent = colors.accent
    val played = remember(tracks, stats) { tracks.count { (stats[it.uri]?.plays ?: 0) > 0 } }
    val said = stringResource(R.string.nag_music_trace, played, tracks.size)
    Canvas(modifier.height(8.dp).clearAndSetSemantics { contentDescription = said }) {
        val total = tracks.size
        val count = min(total, MAX_TICKS)
        if (count == 0) return@Canvas
        val gap = 3.dp.toPx()
        val width = ((size.width - gap * (count - 1)) / count).coerceIn(1f, 18.dp.toPx())
        val low = 4.dp.toPx()
        val tall = size.height
        for (i in 0 until count) {
            // the tracks this tick stands for: one each up to MAX_TICKS, else a run of them; the one that plays is never
            // left out, and a run counts as played once half of it was
            val from = i * total / count
            val to = (i + 1) * total / count
            var heardHere = 0
            var now = false
            for (k in from until to) {
                if ((stats[tracks[k].uri]?.plays ?: 0) > 0) heardHere++
                if (tracks[k].uri == playing) now = true
            }
            val height = if (now) tall else low
            val colour = when {
                now -> accent
                heardHere * 2 >= to - from -> heard
                else -> unheard
            }
            drawRoundRect(colour, Offset(i * (width + gap), (size.height - height) / 2), Size(width, height), CornerRadius(2.dp.toPx()))
        }
    }
}

/** A group's cover: its first track's album cover, or a sign of an album on the raised surface while there is none. */
@Composable
internal fun GroupCover(group: MusicGroup, modifier: Modifier = Modifier, corner: Dp = 6.dp, read: Dp = 176.dp) {
    val colors = NagTheme.colors
    val track = remember(group.key) { group.tracks.firstOrNull()?.let(CoverTrack::of) }
    val px = with(LocalDensity.current) { read.roundToPx() }
    val image = rememberCover(track, px)
    Box(modifier.clip(RoundedCornerShape(corner)).background(colors.surfaceRaised), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(NagIcons.Albums, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(28.dp))
        }
    }
}

/**
 * What the grid needs of the browser: a group's name and line; [letters], the groups are in the alphabet's order (the
 * albums, the artists, the genres, the composers) and go under their first letters.
 */
@Immutable
internal class GroupGridWords(
    val title: @Composable (MusicGroup) -> String,
    val line: @Composable (MusicGroup) -> String,
    val letters: Boolean = false,
    /** The name the groups are in order by: an album's artist, another group's own name. */
    val sortName: (MusicGroup) -> String? = { it.title },
    /** The heading of the groups without that name, at the end ("Bilinmeyen sanatçı"). */
    val nameless: @Composable () -> String = { "#" },
    /** How many groups a letter has, as TalkBack hears it ("5 albüm"). */
    val count: @Composable (Int) -> String = { it.toString() },
)

/** One item of the grid: a letter over the groups it starts, or a group. */
internal sealed interface GridEntry {
    /** [key]: the grid's key, its first group's: a letter can come twice ("#" first and last, below). */
    class Letter(val letter: String, val count: Int, val key: String) : GridEntry
    class Group(val group: MusicGroup) : GridEntry
}

/**
 * [groups] under their first letters, as the reader's alphabet ([locale]) tells letters apart: in Turkish "Ç" apart
 * from "C" and "İ" from "I", in English "Ç" (and a letter that does not come apart into a letter and its marks, as "Ø"
 * may, when the alphabet files it there) under "C", as the groups' order has them; one that starts with a digit or a
 * sign under "#"; those without the name at all (the order puts them last) under [nameless].
 */
internal fun lettered(groups: List<MusicGroup>, name: (MusicGroup) -> String?, locale: java.util.Locale, nameless: String): List<GridEntry> {
    val collator = java.text.Collator.getInstance(locale).apply { strength = java.text.Collator.PRIMARY }
    fun letter(group: MusicGroup): String = name(group)?.takeIf { it.isNotBlank() }?.let { letterOf(it, locale, collator) } ?: nameless
    val out = ArrayList<GridEntry>(groups.size + 30)
    var i = 0
    while (i < groups.size) {
        val letter = letter(groups[i])
        var j = i + 1
        while (j < groups.size && sameLetter(letter(groups[j]), letter, collator, nameless)) j++
        out += GridEntry.Letter(letter, j - i, LETTER_KEY + groups[i].key)
        for (k in i until j) out += GridEntry.Group(groups[k])
        i = j
    }
    return out
}

/** Whether [a] goes under the heading [b]: the same, or letters the alphabet does not tell apart. */
private fun sameLetter(a: String, b: String, collator: java.text.Collator, nameless: String): Boolean =
    a == b || (a != "#" && b != "#" && a != nameless && b != nameless && collator.equals(a, b))

/**
 * A name's first letter in capitals; one its alphabet ([collator], at primary strength) does not tell from the letter
 * without its marks goes under that one ("Ç" under "C" in English, apart in Turkish). "#" for a name without a letter
 * first.
 */
internal fun letterOf(title: String?, locale: java.util.Locale, collator: java.text.Collator): String {
    val first = title?.trim()?.firstOrNull() ?: return "#"
    if (!first.isLetter()) return "#"
    val letter = first.toString().uppercase(locale)
    // a single letter left once the marks go: a Hangul syllable falls apart into its jamo, and stays whole
    val bare = java.text.Normalizer.normalize(letter, java.text.Normalizer.Form.NFD).replace(MARKS, "")
    return if (bare.length == 1 && bare != letter && collator.equals(bare, letter)) bare else letter
}

/** The combining marks a decomposed letter carries. */
private val MARKS = Regex("\\p{M}+")

/**
 * 1.1.0 (Ebru): a way in's groups as their covers, two across on a phone and as many as fit on a wider window; under
 * each its name, its line and its [ListeningTrace]. A tap opens it ([onOpen]); a right click or a long press its menu.
 * [leading] (the page's band, a phone's shelf) spans the grid above them, edge to edge, scrolling with it.
 */
@Composable
internal fun GroupGrid(
    groups: List<MusicGroup>,
    words: GroupGridWords,
    stats: Map<String, TrackStats>,
    playing: String?,
    onOpen: (MusicGroup) -> Unit,
    menu: (MusicGroup) -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    /** A group to scroll to once (a player's link), [onRevealed] when it is in sight. */
    reveal: String? = null,
    onRevealed: () -> Unit = {},
) {
    val dimens = NagTheme.dimens
    val compact = currentWidthClass() == WidthClass.Compact
    val gridState = rememberLazyGridState()
    val reduced = NagTheme.motion.reduced
    OnReselect { if (reduced) gridState.scrollToItem(0) else gridState.animateScrollToItem(0) }
    val side = dimens.pagePadding
    val locale = app.nagmedar.ui.library.currentLocale()
    val nameless = words.nameless()
    val entries = remember(groups, words.letters, locale, nameless) {
        if (words.letters) lettered(groups, words.sortName, locale, nameless) else groups.map { GridEntry.Group(it) }
    }
    LaunchedEffect(reveal, entries) {
        val key = reveal ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it is GridEntry.Group && it.group.key == key }
        if (index >= 0) {
            val item = index + if (leading != null) 1 else 0
            if (reduced) gridState.scrollToItem(item) else gridState.animateScrollToItem(item)
            onRevealed()
        }
    }
    // the covers the reader was at stay in sight as the number of columns changes (the inspector sliding in, a window
    // resized, the sidebar folding): each new number starts the first line where it puts the first item shown, a line
    // earlier at a time, and a slide of several steps would carry the grid back towards its top
    val anchor = remember { GridAnchor() }
    val currentEntries by rememberUpdatedState(entries)
    val hasLeading by rememberUpdatedState(leading != null)
    LaunchedEffect(gridState) {
        launch {
            snapshotFlow { gridState.isScrollInProgress }.collect { scrolling ->
                if (scrolling) return@collect
                // the reader's own scroll (a fling, a tap on a letter, the reveal), not the grid's own way back
                if (anchor.restoring) anchor.restoring = false else anchor.key = firstFullyShown(gridState)
            }
        }
        snapshotFlow { gridState.layoutInfo.maxSpan }.collect { span ->
            val key = anchor.key
            if (anchor.span != 0 && span != anchor.span && key != null) {
                val index = if (key == GRID_LEADING) 0 else currentEntries.indexOfFirst { gridKey(it) == key }.let { i ->
                    if (i < 0) -1 else i + if (hasLeading) 1 else 0
                }
                if (index >= 0) {
                    anchor.restoring = true
                    gridState.scrollToItem(index)
                }
            }
            anchor.span = span
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (compact) 150.dp else 172.dp),
        modifier = modifier.testTag("music-grid"),
        state = gridState,
        contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 16.dp) + LocalShellBottomPadding.current,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 22.dp),
    ) {
        if (leading != null) {
            item(key = GRID_LEADING, span = { GridItemSpan(maxLineSpan) }, contentType = GRID_LEADING) {
                // edge to edge: past the grid's side padding
                Box(Modifier.bleed(side)) { leading() }
            }
        }
        items(
            entries,
            key = ::gridKey,
            span = { entry -> if (entry is GridEntry.Letter) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { entry -> if (entry is GridEntry.Letter) GRID_LETTER else GRID_GROUP },
        ) { entry ->
            when (entry) {
                is GridEntry.Letter -> LetterHeading(entry.letter, entry.count, words.count(entry.count))
                is GridEntry.Group -> ContextMenuArea(entries = { menu(entry.group) }, accessibilityLabel = stringResource(R.string.nag_more_actions)) {
                    GroupCard(entry.group, words.title(entry.group), words.line(entry.group), stats, playing) { onOpen(entry.group) }
                }
            }
        }
    }
}

/** An entry's key in the grid. */
private fun gridKey(entry: GridEntry): Any = if (entry is GridEntry.Group) entry.group.key else (entry as GridEntry.Letter).key

/** What the grid shows first, all of it: the item a change of columns keeps in sight ([GroupGrid]). */
private class GridAnchor {
    var key: Any? = null
    var span = 0
    var restoring = false
}

/** The key of the first item [state] shows whole (its top in sight), else of the first shown at all. */
private fun firstFullyShown(state: LazyGridState): Any? {
    val items = state.layoutInfo.visibleItemsInfo
    return (items.firstOrNull { it.offset.y >= 0 } ?: items.firstOrNull())?.key
}

/**
 * A letter over the groups it starts: the letter large in Bodoni's italic, a rule, how many there are; a heading for
 * TalkBack, which hears what they are ([said]: "C, 5 albüm").
 */
@Composable
private fun LetterHeading(letter: String, count: Int, said: String) {
    val colors = NagTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics {
            heading()
            contentDescription = "$letter, $said"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // a letter, or the nameless groups' words ("Bilinmeyen sanatçı"): the rule and the count keep their room
        Text(letter, style = NagTheme.type.display, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 280.dp))
        Box(Modifier.padding(horizontal = 12.dp).weight(1f).height(1.dp).background(colors.border))
        Text(count.toString(), style = NagTheme.type.labelSmall, color = colors.textTertiary)
    }
}

/** One group of the grid: its cover, name, line and trace, one target. */
@Composable
private fun GroupCard(group: MusicGroup, title: String, line: String, stats: Map<String, TrackStats>, playing: String?, onOpen: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(10.dp)
    val playsHere = playing != null && group.tracks.any { it.uri == playing }
    Column(
        Modifier
            .fillMaxWidth()
            .pressScale(state.pressed)
            .focusRing(state, shape)
            .nagClickable(interaction, onOpen),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GroupCover(group, Modifier.fillMaxWidth().aspectRatio(1f))
        Column(Modifier.fillMaxWidth()) {
            Text(title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = NagTheme.type.labelSmall, color = if (playsHere) colors.accentText else colors.textTertiary, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        ListeningTrace(group.tracks, stats, playing, Modifier.fillMaxWidth())
    }
}

/** [by] wider on both sides than the room it is given: an item that reaches past its list's padding. */
private fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = (by * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    layout(constraints.constrainWidth(placeable.width - extra), placeable.height) { placeable.place(-by.roundToPx(), 0) }
}

/** A group's ticks at most: a longer album's are spread over it. */
private const val MAX_TICKS = 24

/** The key of the grid's first item that is not a group; no group's key holds a NUL. */
private const val GRID_LEADING = "\u0000grid-leading"

/** Before a letter's first group's key: a letter's key in the grid. */
private const val LETTER_KEY = "\u0000letter-"

/** The grid's kinds of items, for the lazy grid's reuse. */
private const val GRID_LETTER = "letter"
private const val GRID_GROUP = "group"
