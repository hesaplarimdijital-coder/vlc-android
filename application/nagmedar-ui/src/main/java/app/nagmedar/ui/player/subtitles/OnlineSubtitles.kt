package app.nagmedar.ui.player.subtitles

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** What the online search is for (S-706): the playing film, its fingerprint, and where a download goes. */
interface OnlineFilm {
    /** The film's file name: "Kuzey.Isigi.2024.1080p.mkv". */
    val name: String

    /** OpenSubtitles' fingerprint of its file; null for a stream. */
    suspend fun hash(): String?

    /** [bytes] written as [fileName] beside the film, or in the app's folder; where, or null when neither took it. */
    suspend fun save(fileName: String, bytes: ByteArray): SavedSubtitle?

    /** The saved file into the film, showing. */
    suspend fun load(saved: SavedSubtitle): Boolean
}

/** A downloaded subtitle file: where it is, its name, and whether beside the film (else in the app's folder). */
@Immutable
data class SavedSubtitle(val uri: String, val name: String, val beside: Boolean)

/** Why a search or a download did not go through. */
enum class OnlineProblem { NoKey, Offline, QuotaOver, Server, Unsaved }

/** The search window's state (S-706). */
@Immutable
data class OnlineSearchState(
    /** What is searched for by name: the film's name, cleaned. */
    val name: String,
    /** The languages chosen, in the order the results come in. */
    val languages: List<String>,
    /** The languages offered as chips. */
    val offered: List<String>,
    /** The file's fingerprint first: the subtitles timed for this very file. */
    val byHash: Boolean = true,
    val searching: Boolean = false,
    /** Null until the first search ends. */
    val results: List<OnlineSubtitle>? = null,
    /** The file being downloaded. */
    val downloading: Long? = null,
    val problem: OnlineProblem? = null,
)

/** The day's downloads, as OpenSubtitles last told them: how many are left of how many, and when they come back. */
@Immutable
data class OnlineQuota(val remaining: Int, val allowed: Int, val resetAtMs: Long?) {
    /** What is left at [nowMs]: all of them once the day is over. */
    fun remainingAt(nowMs: Long): Int = if (resetAtMs != null && nowMs >= resetAtMs) allowed else remaining.coerceAtLeast(0)
}

interface OnlineQuotaStore {
    fun load(): OnlineQuota?

    fun save(quota: OnlineQuota)
}

/** The quota in the settings: "4,5,1760313600000". */
class PrefsOnlineQuotaStore(private val prefs: SharedPreferences) : OnlineQuotaStore {
    override fun load(): OnlineQuota? {
        val parts = prefs.getString(KEY, null)?.split(',') ?: return null
        val remaining = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val allowed = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return OnlineQuota(remaining, allowed, parts.getOrNull(2)?.toLongOrNull())
    }

    override fun save(quota: OnlineQuota) {
        prefs.edit().putString(KEY, listOfNotNull(quota.remaining, quota.allowed, quota.resetAtMs).joinToString(",")).apply()
    }

    private companion object {
        const val KEY = "nagmedar_opensubtitles_quota"
    }
}

/**
 * The search for a film's subtitles on OpenSubtitles.com (S-706): by the file's fingerprint
 * first, by its name when that finds none; in the languages chosen, the fingerprint's matches
 * first, then by language, then by downloads. A download goes beside the film, or into the app's
 * folder, and shows at once; the day's quota is kept as OpenSubtitles tells it.
 */
@Stable
class OnlineSubtitlesController(
    private val api: OpenSubtitlesApi,
    private val quotas: OnlineQuotaStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var state by mutableStateOf<OnlineSearchState?>(null)
        private set

    var quota by mutableStateOf(quotas.load())
        private set

    /** Whether this build has an API key. */
    val available: Boolean get() = api.available

    private var film: OnlineFilm? = null
    private var hash: String? = null
    private var hashed = false
    private var job: Job? = null

    /** The window opens on [film]: its name cleaned, [languages] offered (the first two chosen), the search started. */
    fun open(film: OnlineFilm, languages: List<String>) {
        job?.cancel()
        this.film = film
        hash = null
        hashed = false
        val offered = languages.distinct()
        state = OnlineSearchState(name = searchName(film.name), languages = offered.take(2), offered = offered)
        if (!api.available) {
            state = state?.copy(problem = OnlineProblem.NoKey)
            return
        }
        search()
    }

    fun setName(name: String) {
        state = state?.copy(name = name)
    }

    /** A language chosen or left out; the order offered stays. */
    fun toggle(language: String) {
        val current = state ?: return
        val chosen = if (language in current.languages) current.languages - language else current.languages + language
        state = current.copy(languages = current.offered.filter { it in chosen })
    }

    fun setByHash(on: Boolean) {
        state = state?.copy(byHash = on)
    }

    fun search() {
        val current = state ?: return
        val film = film ?: return
        if (!api.available) return
        job?.cancel()
        state = current.copy(searching = true, problem = null)
        job = scope.launch {
            try {
                if (current.byHash && !hashed) {
                    hash = film.hash()
                    hashed = true
                }
                val fingerprint = hash.takeIf { current.byHash }
                var found = if (fingerprint != null) api.search(OnlineQuery(fingerprint, null, current.languages)) else emptyList()
                if (found.isEmpty() && current.name.isNotBlank()) found = api.search(OnlineQuery(null, current.name, current.languages))
                state = state?.copy(searching = false, results = ranked(found, current.languages))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state?.copy(searching = false, problem = problemOf(e))
            }
        }
    }

    /** [subtitle] downloaded, saved beside the film and shown; the window closes. */
    fun download(subtitle: OnlineSubtitle) {
        val film = film ?: return
        if (state?.downloading != null) return
        state = state?.copy(downloading = subtitle.fileId, problem = null)
        job = scope.launch {
            try {
                val ticket = api.download(subtitle.fileId)
                keep(OnlineQuota(ticket.remaining, ticket.remaining + ticket.requests, resetAt(ticket.resetUtc)))
                val bytes = api.fetch(ticket.link)
                val saved = film.save(fileNameFor(film.name, subtitle, ticket.fileName), bytes)
                if (saved == null || !film.load(saved)) {
                    state = state?.copy(downloading = null, problem = OnlineProblem.Unsaved)
                } else {
                    state = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is OnlineFailure.QuotaOver) keep(OnlineQuota(0, quota?.allowed ?: ANONYMOUS_DOWNLOADS, resetAt(e.resetUtc)))
                state = state?.copy(downloading = null, problem = problemOf(e))
            }
        }
    }

    fun close() {
        job?.cancel()
        state = null
    }

    /** What is left of the day's downloads now; null before OpenSubtitles told it. */
    fun remaining(): Int? = quota?.remainingAt(now())

    private fun keep(next: OnlineQuota) {
        quota = next
        quotas.save(next)
    }

    companion object {
        /** OpenSubtitles' downloads a day without an account. */
        const val ANONYMOUS_DOWNLOADS = 5

        fun problemOf(e: Exception): OnlineProblem = when (e) {
            is OnlineFailure.NoKey -> OnlineProblem.NoKey
            is OnlineFailure.Offline -> OnlineProblem.Offline
            is OnlineFailure.QuotaOver -> OnlineProblem.QuotaOver
            else -> OnlineProblem.Server
        }

        /** The fingerprint's matches first, then the languages in the order chosen, then the most downloaded. */
        fun ranked(found: List<OnlineSubtitle>, languages: List<String>): List<OnlineSubtitle> {
            fun place(language: String) = languages.indexOfFirst { it.equals(language, ignoreCase = true) }.let { if (it < 0) languages.size else it }
            return found.distinctBy { it.fileId }.sortedWith(
                compareByDescending<OnlineSubtitle> { it.hashMatch }.thenBy { place(it.language) }.thenByDescending { it.downloads },
            )
        }

        /** "Kuzey.Isigi.2024.1080p.mkv" → "Kuzey Isigi 2024 1080p": a file's name as words to search for. */
        fun searchName(fileName: String): String =
            fileName.substringBeforeLast('.').ifEmpty { fileName }.replace(Regex("[._]+"), " ").replace(Regex("\\s+"), " ").trim()

        /**
         * "Kuzey Işığı.tr.srt": the film's name, the subtitle's language and its kind, so that the
         * film finds it beside itself again (S-701).
         */
        fun fileNameFor(film: String, subtitle: OnlineSubtitle, served: String): String {
            val base = film.substringBeforeLast('.').ifEmpty { film }
            val extension = served.substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf { it in SUBTITLE_EXTENSIONS } ?: "srt"
            val language = subtitle.language.lowercase(Locale.ROOT).ifEmpty { null }
            return listOfNotNull(base, language, extension).joinToString(".")
        }

        private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub", "smi", "txt")

        /** "2026-10-13T00:00:00.000Z" in milliseconds. */
        fun resetAt(utc: String?): Long? {
            if (utc == null || utc.length < UTC_LENGTH) return null
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
            return try {
                format.parse(utc.substring(0, UTC_LENGTH))?.time
            } catch (e: ParseException) {
                null
            }
        }

        private const val UTC_LENGTH = 19
    }
}
