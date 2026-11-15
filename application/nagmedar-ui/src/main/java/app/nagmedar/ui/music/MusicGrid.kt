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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
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
import app.nagmedar.ui.theme.mixSrgb
import java.util.Calendar

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
 * The quick filters as chips over the groups, scrolling with them; one is always chosen ("Tümü" when none other is).
 * [wrap] as [ChipRow]'s: a narrow pane beside the tracks keeps them on one line that scrolls sideways.
 */
@Composable
internal fun QuickFilterRow(state: MusicBrowserState, modifier: Modifier = Modifier, wrap: Boolean = NagTheme.largeWindow) {
    ChipRow(
        modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("music-quick"),
        contentPadding = PaddingValues(horizontal = NagTheme.dimens.pagePadding),
        wrap = wrap,
    ) {
        QuickFilter.entries.forEach { filter ->
            FilterChip(stringResource(filter.label), selected = state.quick == filter, onToggle = { on ->
                state.quick = if (on || filter == QuickFilter.All) filter else QuickFilter.All
            })
        }
    }
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
 * 1.1.0 (Ebru): a group's listening trace: a tick for each track (at most [MAX_TICKS], spread over the album), those
 * played darker, those never played faint, the one that plays taller in the accent. Decoration: TalkBack hears the
 * group's line instead.
 */
@Composable
internal fun ListeningTrace(tracks: List<Track>, stats: Map<String, TrackStats>, playing: String?, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val heard = colors.textTertiary
    val unheard = mixSrgb(colors.text, colors.background, 0.14f)
    val accent = colors.accent
    val shown = remember(tracks) { if (tracks.size <= MAX_TICKS) tracks else List(MAX_TICKS) { tracks[it * tracks.size / MAX_TICKS] } }
    Canvas(modifier.height(8.dp).clearAndSetSemantics {}) {
        val count = shown.size
        if (count == 0) return@Canvas
        val gap = 3.dp.toPx()
        val width = ((size.width - gap * (count - 1)) / count).coerceIn(1f, 18.dp.toPx())
        val low = 4.dp.toPx()
        val tall = size.height
        shown.forEachIndexed { i, track ->
            val now = track.uri == playing
            val height = if (now) tall else low
            val colour = when {
                now -> accent
                (stats[track.uri]?.plays ?: 0) > 0 -> heard
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
)

/** One item of the grid: a letter over the groups it starts, or a group. */
private sealed interface GridEntry {
    class Letter(val letter: String, val count: Int) : GridEntry
    class Group(val group: MusicGroup) : GridEntry
}

/**
 * [groups] under their first letters, as the reader's alphabet ([locale]) tells letters apart: in Turkish "Ç" apart
 * from "C" and "İ" from "I", in English "Ç" under "C", as the groups' order has them; a group without a name, or one
 * that starts with a digit or a sign, under "#".
 */
private fun lettered(groups: List<MusicGroup>, name: (MusicGroup) -> String?, locale: java.util.Locale): List<GridEntry> {
    val collator = java.text.Collator.getInstance(locale).apply { strength = java.text.Collator.PRIMARY }
    val out = ArrayList<GridEntry>(groups.size + 30)
    var i = 0
    while (i < groups.size) {
        val letter = letterOf(name(groups[i]), locale, collator)
        var j = i
        while (j < groups.size && letterOf(name(groups[j]), locale, collator) == letter) j++
        out += GridEntry.Letter(letter, j - i)
        for (k in i until j) out += GridEntry.Group(groups[k])
        i = j
    }
    return out
}

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
) {
    val dimens = NagTheme.dimens
    val compact = currentWidthClass() == WidthClass.Compact
    val gridState = rememberLazyGridState()
    val reduced = NagTheme.motion.reduced
    OnReselect { if (reduced) gridState.scrollToItem(0) else gridState.animateScrollToItem(0) }
    val side = dimens.pagePadding
    val locale = app.nagmedar.ui.library.currentLocale()
    val entries = remember(groups, words.letters, locale) { if (words.letters) lettered(groups, words.sortName, locale) else groups.map { GridEntry.Group(it) } }
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
            key = { entry -> if (entry is GridEntry.Group) entry.group.key else "\u0000letter-" + (entry as GridEntry.Letter).letter },
            span = { entry -> if (entry is GridEntry.Letter) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { entry -> if (entry is GridEntry.Letter) GRID_LETTER else GRID_GROUP },
        ) { entry ->
            when (entry) {
                is GridEntry.Letter -> LetterHeading(entry.letter, entry.count)
                is GridEntry.Group -> ContextMenuArea(entries = { menu(entry.group) }, accessibilityLabel = stringResource(R.string.nag_more_actions)) {
                    GroupCard(entry.group, words.title(entry.group), words.line(entry.group), stats, playing) { onOpen(entry.group) }
                }
            }
        }
    }
}

/**
 * A letter over the groups it starts: the letter large in Bodoni's italic, a rule, how many there are ("5 albüm" for
 * the albums, else the count alone). A heading for TalkBack.
 */
@Composable
private fun LetterHeading(letter: String, count: Int) {
    val colors = NagTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Text(letter, style = NagTheme.type.display, color = colors.text)
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

/** The grid's kinds of items, for the lazy grid's reuse. */
private const val GRID_LETTER = "letter"
private const val GRID_GROUP = "group"
