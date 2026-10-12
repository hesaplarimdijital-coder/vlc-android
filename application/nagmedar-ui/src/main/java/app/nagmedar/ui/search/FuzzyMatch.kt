package app.nagmedar.ui.search

import java.util.Locale

/*
 * Fuzzy search for the command palette (S-305) and the search page (S-311). Text is compared
 * folded the Turkish way: "ışık" is found by "isik", "Şarkı" by "sarki", "İzlenmedi" by
 * "izlenmedi"; a query matches when its characters come in order, and matches at word starts and
 * in runs score higher, a whole word ("aç" in "Dosya aç…") the most.
 */

private val turkish = Locale.forLanguageTag("tr")

/** Lower case as in Turkish (I → ı, İ → i), then ı, ş, ğ, ü, ö, ç and circumflexed vowels to plain Latin letters. */
fun foldForSearch(text: String): String {
    val lower = text.lowercase(turkish)
    val out = StringBuilder(lower.length)
    for (c in lower) {
        out.append(
            when (c) {
                'ı' -> 'i'
                'ş' -> 's'
                'ğ' -> 'g'
                'ü', 'û' -> 'u'
                'ö' -> 'o'
                'ç' -> 'c'
                'â', 'à', 'á', 'ä' -> 'a'
                'î', 'ï', 'í' -> 'i'
                'é', 'è', 'ê', 'ë' -> 'e'
                'ó', 'ô' -> 'o'
                // a dotted capital I lower-cased outside Turkish leaves a combining dot
                '̇' -> continue
                else -> c
            },
        )
    }
    return out.toString()
}

/** How well a query matched a text, and where: [positions] index the matched characters of the text. */
class FuzzyMatch(val score: Int, val positions: IntArray)

object FuzzyMatcher {
    private const val MATCH = 16
    private const val WORD_START = 24
    private const val TEXT_START = 8
    private const val RUN = 20
    private const val GAP = 1
    private const val WORD = 16
    private const val NONE = Int.MIN_VALUE / 2

    /**
     * Matches [query] in [text]; null when the query's characters (spaces ignored) do not all
     * appear in order. Both are folded with [foldForSearch].
     */
    fun match(query: String, text: String): FuzzyMatch? {
        val q = foldForSearch(query).filter { !it.isWhitespace() }
        if (q.isEmpty()) return FuzzyMatch(0, IntArray(0))
        val t = foldForSearch(text)
        val m = q.length
        val n = t.length
        if (m > n) return null
        // quick rejection: the characters must at least appear in order
        var k = 0
        for (c in t) if (k < m && c == q[k]) k++
        if (k < m) return null

        val score = Array(m) { IntArray(n) { NONE } }
        val from = Array(m) { IntArray(n) { -1 } }
        for (i in 0 until m) {
            // best of score[i - 1][k] + k * GAP over k <= j - 2, for a jump over a gap
            var bestGap = NONE
            var bestGapAt = -1
            for (j in i until n) {
                if (i > 0 && j >= 2) {
                    val prev = score[i - 1][j - 2]
                    if (prev > NONE && prev + (j - 2) * GAP > bestGap) {
                        bestGap = prev + (j - 2) * GAP
                        bestGapAt = j - 2
                    }
                }
                if (t[j] != q[i]) continue
                val bonus = MATCH + when {
                    j == 0 -> WORD_START + TEXT_START
                    isWordStart(t, j) -> WORD_START
                    else -> 0
                }
                if (i == 0) {
                    score[i][j] = bonus - j.coerceAtMost(10)
                    continue
                }
                val run = score[i - 1].getOrElse(j - 1) { NONE }.let { if (it > NONE) it + RUN else NONE }
                val jump = if (bestGap > NONE) bestGap - (j - 1) * GAP else NONE
                if (run == NONE && jump == NONE) continue
                if (run >= jump) {
                    score[i][j] = bonus + run
                    from[i][j] = j - 1
                } else {
                    score[i][j] = bonus + jump
                    from[i][j] = bestGapAt
                }
            }
        }
        var end = -1
        var best = NONE
        for (j in 0 until n) if (score[m - 1][j] > best) {
            best = score[m - 1][j]
            end = j
        }
        if (end < 0) return null
        val word = wholeWord(q, t)
        val positions = if (word != null && word.first >= best) {
            best = word.first
            IntArray(m) { word.second + it }
        } else {
            IntArray(m).also { positions ->
                var j = end
                for (i in m - 1 downTo 0) {
                    positions[i] = j
                    j = from[i][j]
                }
            }
        }
        // a shorter text with the same match ranks first
        return FuzzyMatch(best - (n - m) / 4, positions)
    }

    /**
     * The query as a whole word of the text, scored as the run it is and [WORD] more, with where
     * it starts; null for none. Initials alone ("A/B testi: çalanı" for "aç") rank after it.
     */
    private fun wholeWord(q: String, t: String): Pair<Int, Int>? {
        var found: Pair<Int, Int>? = null
        var start = t.indexOf(q)
        while (start >= 0) {
            val end = start + q.length
            if ((start == 0 || isWordStart(t, start)) && (end == t.length || !t[end].isLetterOrDigit())) {
                var score = MATCH + WORD_START + (if (start == 0) TEXT_START else 0) - start.coerceAtMost(10) + WORD
                for (k in start + 1 until end) score += MATCH + RUN + (if (isWordStart(t, k)) WORD_START else 0)
                if (found == null || score > found.first) found = score to start
            }
            start = t.indexOf(q, start + 1)
        }
        return found
    }

    private fun isWordStart(t: String, j: Int): Boolean {
        val before = t[j - 1]
        return !before.isLetterOrDigit() || (before.isDigit() != t[j].isDigit())
    }
}
