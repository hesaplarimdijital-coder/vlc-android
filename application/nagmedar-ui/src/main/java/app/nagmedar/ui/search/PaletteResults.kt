package app.nagmedar.ui.search

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import app.nagmedar.ui.R

/** What the command palette finds (S-305), in the order its groups are shown. */
enum class PaletteKind(@StringRes val title: Int, val prefix: Char?) {
    Command(R.string.nag_palette_commands, '>'),
    Screen(R.string.nag_palette_screens, '/'),
    Setting(R.string.nag_palette_settings, '#'),
    Media(R.string.nag_palette_media, '@'),
}

/** One thing the palette can run: a shortcut's command, a screen, a setting or a media file. */
@Immutable
class PaletteItem(
    /** Stable across sessions, for the recent list: "command:PlayPause", "screen:movies". */
    val id: String,
    val kind: PaletteKind,
    val label: String,
    /** The line under it: the menu of a command, the kind and length of a film. */
    val detail: String = "",
    /** The keys of a command ("Ctrl+O"). */
    val shortcut: String? = null,
    val icon: ImageVector? = null,
    /** 1.0.7: what a media item is found by besides its label: a track's artist and album, an album's artist. */
    val words: String = "",
    /** 1.0.7: among media that match equally well the lower comes first: an artist, then an album, then a track. */
    val order: Int = 0,
    val run: () -> Unit,
) {
    /** 1.0.7: a media item as the library's search looks at it (its texts folded once); null for the other kinds. */
    internal val searchable: Searchable<PaletteItem>? = if (kind == PaletteKind.Media) Searchable(this, SearchKind.Music, label, words) else null
}

/** A found item with the characters of its label that matched, for highlighting. */
@Immutable
class PaletteHit private constructor(val item: PaletteItem, val score: Int, positions: Lazy<IntArray>) {
    constructor(item: PaletteItem, matched: IntArray, score: Int) : this(item, score, lazyOf(matched))

    /** 1.0.7: [matched] is looked for when a row first shows the hit: the library's search can find thousands. */
    constructor(item: PaletteItem, score: Int, matched: () -> IntArray) : this(item, score, lazy(LazyThreadSafetyMode.NONE, matched))

    val matched: IntArray by positions
}

/** A kind's hits as shown; [total] counts those it has, which are more than [hits] when only its best are shown. */
@Immutable
class PaletteGroup(val kind: PaletteKind?, val hits: List<PaletteHit>, val total: Int)

/**
 * 1.0.7: a line of the palette's list, top to bottom: a group's title, a hit, or the line under a group that shows
 * its best only ("show all"). The arrows stop at hits and at those lines: [stop] is which stop a line is.
 */
sealed class PaletteRow(val group: PaletteGroup) {
    abstract val key: String

    class Header(group: PaletteGroup) : PaletteRow(group) {
        override val key: String get() = "header-${group.kind}"
    }

    class Hit(group: PaletteGroup, val hit: PaletteHit, val stop: Int) : PaletteRow(group) {
        override val key: String get() = "${group.kind}-${hit.item.id}"
    }

    class More(group: PaletteGroup, val stop: Int) : PaletteRow(group) {
        override val key: String get() = "more-${group.kind}"
    }
}

/** What the palette shows for a query: groups of hits, or the recent items when there is no query. */
@Immutable
class PaletteResults(val groups: List<PaletteGroup>, val scope: PaletteKind?, val count: Int) {
    val hits: List<PaletteHit> = groups.flatMap { it.hits }

    /** 1.0.7: the list's lines: each group's title, its hits, and "show all" under one that has more than it shows. */
    val rows: List<PaletteRow>

    /** 1.0.7: the lines the arrows stop at, in order: the hits and the "show all" lines; and which line each is. */
    val stops: List<PaletteRow>
    val rowOfStop: IntArray

    init {
        val lines = ArrayList<PaletteRow>(hits.size + groups.size * 2)
        val stopping = ArrayList<PaletteRow>(hits.size + groups.size)
        val at = ArrayList<Int>(hits.size + groups.size)
        fun stop(row: PaletteRow) {
            at += lines.size
            lines += row
            stopping += row
        }
        for (group in groups) {
            lines += PaletteRow.Header(group)
            for (hit in group.hits) stop(PaletteRow.Hit(group, hit, stopping.size))
            // a group without a kind (what ran lately) is never cut
            if (group.total > group.hits.size && group.kind != null) stop(PaletteRow.More(group, stopping.size))
        }
        rows = lines
        stops = stopping
        rowOfStop = at.toIntArray()
    }
}

/** A query with its scope: "> ses" looks for commands only, "@kuzey" for media, "#tema" for settings, "/fil" for screens. */
fun parsePaletteQuery(raw: String, scope: PaletteKind?): Pair<String, PaletteKind?> {
    val trimmed = raw.trimStart()
    val prefixed = PaletteKind.entries.firstOrNull { it.prefix != null && trimmed.startsWith(it.prefix) }
    return if (prefixed != null) trimmed.drop(1).trim() to prefixed else trimmed.trim() to scope
}

/**
 * Ranks [items] for [raw] (S-305), grouped by kind in [PaletteKind] order, each group best first.
 * Commands, screens and settings are found fuzzily and Turkish-folded by their label ([FuzzyMatcher]);
 * when every kind is searched each shows its best [perGroup], with how many it has ([PaletteGroup.total]).
 * With no query the [recent] items come first, then the commands.
 *
 * 1.0.7: media are found as the search page finds them ([search], S-311): every word of the query in the
 * title, the artist or the album, in any order. Until then only a track's title was looked at, letter by
 * letter: an artist's or an album's name found nothing. They are all listed, as is one kind asked for:
 * until then six of them were (fifty of a kind asked for) and nothing led to the rest. Media that match
 * equally well come as artists, albums, then tracks ([PaletteItem.order]), what ran lately first among them.
 */
fun paletteResults(
    raw: String,
    scope: PaletteKind?,
    items: List<PaletteItem>,
    recent: List<String> = emptyList(),
    perGroup: Int = 6,
): PaletteResults {
    val (query, effective) = parsePaletteQuery(raw, scope)
    // 1.0.1: each item once, the first of an id (the list keys its rows by id: a file twice in the library, a recent
    // id kept twice, would end the app)
    val pool = (if (effective == null) items else items.filter { it.kind == effective }).distinctBy { it.id }
    if (query.isEmpty()) {
        val byId = pool.associateBy { it.id }
        val recents = recent.distinct().mapNotNull { byId[it] }.map { PaletteHit(it, IntArray(0), 0) }
        val rest = pool.filter { it.id !in recent && (effective != null || it.kind == PaletteKind.Command) }
            .map { PaletteHit(it, IntArray(0), 0) }
        val groups = listOfNotNull(
            recents.takeIf { it.isNotEmpty() }?.let { PaletteGroup(null, it, it.size) },
            rest.takeIf { it.isNotEmpty() }?.let { PaletteGroup(effective ?: PaletteKind.Command, if (effective == null) it.take(perGroup) else it, it.size) },
        )
        return PaletteResults(groups, effective, recents.size + rest.size)
    }
    val recentRank = recent.withIndex().associate { (index, id) -> id to index }
    val fuzzy = pool.mapNotNull { item ->
        if (item.kind == PaletteKind.Media) return@mapNotNull null
        FuzzyMatcher.match(query, item.label)?.let { match ->
            // what was run lately comes up a little
            val boost = recentRank[item.id]?.let { 12 - it } ?: 0
            PaletteHit(item, match.positions, match.score + boost)
        }
    }
    val words = SearchQuery.parse(query)
    // the search's own order (best first, a shorter title before a longer one) stays among those of one rank;
    // a hit's rank is made once, not at every comparison: a letter can find most of a large library
    val media = pool.mapNotNull { it.searchable }.search(words)
        .map { hit -> hit to mediaRank(hit, recentRank) }
        .sortedBy { it.second }
        .map { (hit, _) -> PaletteHit(hit.value, hit.score) { highlightPositions(hit.value.label, words) } }
    val groups = PaletteKind.entries.mapNotNull { kind ->
        val found = if (kind == PaletteKind.Media) media else fuzzy.filter { it.item.kind == kind }.sortedByDescending { it.score }
        when {
            found.isEmpty() -> null
            kind == PaletteKind.Media || effective != null -> PaletteGroup(kind, found, found.size)
            else -> PaletteGroup(kind, found.take(perGroup), found.size)
        }
    }
    return PaletteResults(groups, effective, fuzzy.size + media.size)
}

/** A media hit's rank, the lower first: how well the words matched, then an artist, an album, a track, then what ran lately. */
private fun mediaRank(hit: SearchHit<PaletteItem>, recentRank: Map<String, Int>): Long {
    val recent = (if (recentRank.isEmpty()) null else recentRank[hit.value.id]) ?: NOT_RECENT
    return ((Int.MAX_VALUE - hit.match).toLong() shl 24) or (hit.value.order.coerceIn(0, 0xFF).toLong() shl 16) or recent.coerceIn(0, NOT_RECENT).toLong()
}

private const val NOT_RECENT = 0xFFFF

/** The characters of [label] that the words and phrases of [query] are found at, in order. */
private fun highlightPositions(label: String, query: SearchQuery): IntArray {
    val marked = java.util.TreeSet<Int>()
    for (range in highlightRanges(label, query)) for (i in range) marked += i
    return marked.toIntArray()
}
