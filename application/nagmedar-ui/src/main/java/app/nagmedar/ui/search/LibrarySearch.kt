package app.nagmedar.ui.search

/*
 * The search page's engine (S-311): every word of the query must appear in a title, an artist,
 * an album or a folder, folded the Turkish way ("isik" finds "Işık"); "tam ifade" must appear as
 * it is, -kelime must not, and tür:müzik keeps one kind.
 * Texts are folded once, so 10 000 tracks are searched in a few milliseconds.
 */

/** What a hit is: the groups of the page. */
enum class SearchKind(vararg val words: String) {
    Music("muzik", "parca", "parcalar", "sarki", "music", "track", "tracks", "song", "songs"),
}

/** A query taken apart; every part is folded with [foldForSearch]. */
data class SearchQuery(
    val words: List<String>,
    val phrases: List<String>,
    val excluded: List<String>,
    val kinds: Set<SearchKind>,
) {
    val isEmpty: Boolean get() = words.isEmpty() && phrases.isEmpty()

    /** The words and phrases to highlight in what was found. */
    val highlights: List<String> get() = phrases + words

    companion object {
        private val token = Regex(""""([^"]*)"?|(\S+)""")

        fun parse(raw: String): SearchQuery {
            val words = mutableListOf<String>()
            val phrases = mutableListOf<String>()
            val excluded = mutableListOf<String>()
            val kinds = mutableSetOf<SearchKind>()
            for (match in token.findAll(raw)) {
                val quoted = match.groups[1]?.value
                if (quoted != null) {
                    foldForSearch(quoted).trim().takeIf { it.isNotEmpty() }?.let(phrases::add)
                    continue
                }
                val word = foldForSearch(match.groupValues[2])
                when {
                    word.startsWith("tur:") || word.startsWith("type:") -> {
                        val value = word.substringAfter(':')
                        SearchKind.entries.firstOrNull { value in it.words }?.let(kinds::add)
                    }
                    word.startsWith("-") && word.length > 1 -> excluded += word.drop(1)
                    else -> words += word
                }
            }
            return SearchQuery(words, phrases, excluded, kinds)
        }
    }
}

/** A searchable item: its [title] first, then other words (artist, album, folder) in [extra]. */
class Searchable<T>(val value: T, val kind: SearchKind, title: String, extra: String = "") {
    internal val title: String = foldForSearch(title)
    internal val text: String = if (extra.isEmpty()) this.title else this.title + " \u0000 " + foldForSearch(extra)
}

class SearchHit<T>(val value: T, val kind: SearchKind, val score: Int)

/** The items matching [query], best first: words at the start of the title, then at word starts, then inside words. */
fun <T> List<Searchable<T>>.search(query: SearchQuery): List<SearchHit<T>> {
    if (query.isEmpty) return emptyList()
    val hits = ArrayList<SearchHit<T>>()
    for (item in this) {
        if (query.kinds.isNotEmpty() && item.kind !in query.kinds) continue
        var score = 0
        var matched = true
        for (word in query.words) {
            val s = wordScore(item, word)
            if (s == 0) {
                matched = false
                break
            }
            score += s
        }
        if (!matched) continue
        for (phrase in query.phrases) {
            if (!item.text.contains(phrase)) {
                matched = false
                break
            }
            score += if (item.title.contains(phrase)) 40 else 20
        }
        if (!matched) continue
        if (query.excluded.any { item.text.contains(it) }) continue
        // a shorter title with the same words first
        hits += SearchHit(item.value, item.kind, score * 100 - item.title.length.coerceAtMost(99))
    }
    hits.sortByDescending { it.score }
    return hits
}

private fun wordScore(item: Searchable<*>, word: String): Int {
    val title = item.title
    if (title.startsWith(word)) return 40
    var at = title.indexOf(word)
    while (at > 0) {
        if (!title[at - 1].isLetterOrDigit()) return 30
        at = title.indexOf(word, at + 1)
    }
    if (title.contains(word)) return 20
    val text = item.text
    at = text.indexOf(word, title.length)
    while (at > 0) {
        if (!text[at - 1].isLetterOrDigit()) return 12
        at = text.indexOf(word, at + 1)
    }
    return if (text.indexOf(word, title.length) >= 0) 6 else 0
}

/** Where the query's words and phrases are in [text] (as shown, not folded), for highlighting: start to end pairs. */
fun highlightRanges(text: String, query: SearchQuery): List<IntRange> {
    val folded = foldForSearch(text)
    // folding keeps the length, so positions carry over
    if (folded.length != text.length) return emptyList()
    val ranges = mutableListOf<IntRange>()
    for (part in query.highlights) {
        if (part.isEmpty()) continue
        var at = folded.indexOf(part)
        while (at >= 0) {
            ranges += at until at + part.length
            at = folded.indexOf(part, at + part.length)
        }
    }
    return ranges.sortedBy { it.first }
}
