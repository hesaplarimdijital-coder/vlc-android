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
    val run: () -> Unit,
)

/** A found item with the characters of its label that matched, for highlighting. */
@Immutable
class PaletteHit(val item: PaletteItem, val matched: IntArray, val score: Int)

@Immutable
class PaletteGroup(val kind: PaletteKind?, val hits: List<PaletteHit>, val total: Int)

/** What the palette shows for a query: groups of hits, or the recent items when there is no query. */
@Immutable
class PaletteResults(val groups: List<PaletteGroup>, val scope: PaletteKind?, val count: Int) {
    val hits: List<PaletteHit> get() = groups.flatMap { it.hits }
}

/** A query with its scope: "> ses" looks for commands only, "@kuzey" for media, "#tema" for settings, "/fil" for screens. */
fun parsePaletteQuery(raw: String, scope: PaletteKind?): Pair<String, PaletteKind?> {
    val trimmed = raw.trimStart()
    val prefixed = PaletteKind.entries.firstOrNull { it.prefix != null && trimmed.startsWith(it.prefix) }
    return if (prefixed != null) trimmed.drop(1).trim() to prefixed else trimmed.trim() to scope
}

/**
 * Ranks [items] for [raw] (S-305): fuzzy and Turkish-folded ([FuzzyMatcher]), grouped by kind in
 * [PaletteKind] order, each group best first and cut to [perGroup] when every kind is searched.
 * With no query the [recent] items come first, then the commands.
 */
fun paletteResults(
    raw: String,
    scope: PaletteKind?,
    items: List<PaletteItem>,
    recent: List<String> = emptyList(),
    perGroup: Int = 6,
): PaletteResults {
    val (query, effective) = parsePaletteQuery(raw, scope)
    val pool = if (effective == null) items else items.filter { it.kind == effective }
    if (query.isEmpty()) {
        val byId = pool.associateBy { it.id }
        val recents = recent.mapNotNull { byId[it] }.map { PaletteHit(it, IntArray(0), 0) }
        val rest = pool.filter { it.id !in recent && (effective != null || it.kind == PaletteKind.Command) }
            .map { PaletteHit(it, IntArray(0), 0) }
        val groups = listOfNotNull(
            recents.takeIf { it.isNotEmpty() }?.let { PaletteGroup(null, it, it.size) },
            rest.takeIf { it.isNotEmpty() }?.let { PaletteGroup(effective ?: PaletteKind.Command, it.take(if (effective == null) perGroup else 50), it.size) },
        )
        return PaletteResults(groups, effective, recents.size + rest.size)
    }
    val recentRank = recent.withIndex().associate { (index, id) -> id to index }
    val hits = pool.mapNotNull { item ->
        FuzzyMatcher.match(query, item.label)?.let { match ->
            // what was run lately comes up a little
            val boost = recentRank[item.id]?.let { 12 - it } ?: 0
            PaletteHit(item, match.positions, match.score + boost)
        }
    }
    val groups = PaletteKind.entries.mapNotNull { kind ->
        val found = hits.filter { it.item.kind == kind }.sortedByDescending { it.score }
        if (found.isEmpty()) null else PaletteGroup(kind, found.take(if (effective == null) perGroup else 50), found.size)
    }
    return PaletteResults(groups, effective, hits.size)
}
