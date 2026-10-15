package app.nagmedar.ui.player.subtitles.providers

import androidx.compose.runtime.Immutable
import app.nagmedar.ui.player.subtitles.OnlineFailure
import app.nagmedar.ui.player.subtitles.OnlineProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.util.Locale

/** What one provider answered: how many it found, or what went wrong. */
@Immutable
data class ProviderReport(val provider: String, val name: String, val found: Int, val problem: OnlineProblem? = null)

/** The merged results, and a report for each provider asked. */
@Immutable
data class SearchOutcome(val results: List<SubtitleResult>, val reports: List<ProviderReport>) {
    /** Every provider failed and nothing was found: what to tell. */
    val failure: OnlineProblem? get() = if (results.isEmpty() && reports.isNotEmpty() && reports.all { it.problem != null }) reports.first().problem else null
}

/**
 * The search over several providers (S-707): all asked at once, each within [timeoutMs] (a slow
 * one does not hold the others back; a failed one is reported, the others' results are kept);
 * the answers merged into one list without doubles, and ranked.
 */
class SubtitleSearch(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) {

    suspend fun search(providers: List<SubtitleProvider>, query: SubtitleQuery): SearchOutcome {
        val answers = coroutineScope {
            providers.map { provider ->
                async {
                    provider.unavailable()?.let { return@async Answer(provider, emptyList(), it) }
                    try {
                        Answer(provider, withTimeout(timeoutMs) { provider.search(query) }, null)
                    } catch (e: TimeoutCancellationException) {
                        Answer(provider, emptyList(), OnlineProblem.Timeout)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Answer(provider, emptyList(), problemOf(e))
                    }
                }
            }.awaitAll()
        }
        val reports = answers.map { ProviderReport(it.provider.id, it.provider.displayName, it.found.size, it.problem) }
        return SearchOutcome(rank(merge(answers.map { it.found }), query.languages), reports)
    }

    private class Answer(val provider: SubtitleProvider, val found: List<SubtitleResult>, val problem: OnlineProblem?)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 12_000L

        fun problemOf(e: Exception): OnlineProblem = when (e) {
            is OnlineFailure.NoKey -> OnlineProblem.NoKey
            is OnlineFailure.Offline -> OnlineProblem.Offline
            is OnlineFailure.QuotaOver -> OnlineProblem.QuotaOver
            is OnlineFailure.NoFolder -> OnlineProblem.NoFolder
            else -> OnlineProblem.Server
        }

        /** The same file by another provider: its language and its release, written alike. */
        fun sameFile(a: SubtitleResult, b: SubtitleResult): Boolean {
            if (!a.language.equals(b.language, ignoreCase = true)) return false
            val x = fold(a.release.ifBlank { a.fileName.substringBeforeLast('.') })
            val y = fold(b.release.ifBlank { b.fileName.substringBeforeLast('.') })
            return x.isNotEmpty() && x == y
        }

        private fun fold(text: String): String = text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        /**
         * The providers' lists as one, in the order of the providers (the user's order): a result
         * another provider has too is kept once, from the provider that is earlier or has the
         * better one, and says who else has it. One provider's own results are never merged.
         */
        fun merge(lists: List<List<SubtitleResult>>): List<SubtitleResult> {
            val kept = ArrayList<SubtitleResult>()
            for (list in lists) {
                val fresh = ArrayList<SubtitleResult>()
                for (result in list.distinctBy { it.key }) {
                    val twin = kept.indexOfFirst { it.provider != result.provider && sameFile(it, result) }
                    if (twin < 0) {
                        fresh += result
                    } else {
                        val old = kept[twin]
                        val better = if (isBetter(result, old)) result else old
                        val other = if (better === old) result else old
                        kept[twin] = better.copy(
                            hashMatch = better.hashMatch || other.hashMatch,
                            alsoFrom = (better.alsoFrom + other.providerName + other.alsoFrom).distinct().filter { it != better.providerName },
                        )
                    }
                }
                kept += fresh
            }
            return kept
        }

        private fun isBetter(a: SubtitleResult, b: SubtitleResult): Boolean =
            if (a.hashMatch != b.hashMatch) a.hashMatch else a.downloads > b.downloads

        /**
         * The file's own matches first, then the languages in the order wanted, then the most
         * downloaded, then the best rated; the order of the providers decides the rest.
         */
        fun rank(found: List<SubtitleResult>, languages: List<String>): List<SubtitleResult> {
            fun place(language: String) = languages.indexOfFirst { it.equals(language, ignoreCase = true) }.let { if (it < 0) languages.size else it }
            return found.sortedWith(
                compareByDescending<SubtitleResult> { it.hashMatch }
                    .thenBy { place(it.language) }
                    .thenByDescending { it.downloads }
                    .thenByDescending { it.rating ?: 0.0 },
            )
        }
    }
}
