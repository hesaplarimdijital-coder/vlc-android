package app.nagmedar.ui.library.metadata

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Immutable
import app.nagmedar.data.FilmEpisodeEntry
import app.nagmedar.data.FilmInfoDao
import app.nagmedar.data.FilmInfoEntry
import app.nagmedar.data.FilmKind
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.BuildConfig as UiBuildConfig
import app.nagmedar.ui.podcast.OkHttpFeedSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.videolan.tools.AppScope
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig as VlcBuildConfig
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Film information: the language of the overviews, genres and titles TMDB gives. */
enum class FilmLanguage(val code: String, val tmdb: String) {
    Turkish("tr", "tr-TR"),
    English("en", "en-US"),
}

/** Film information as the screens show it: a found film or series ([FilmInfoEntry] read). */
@Immutable
data class FilmInfo(
    val queryKey: String,
    val kind: String,
    val tmdbId: Long,
    val imdbId: String?,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val overview: String?,
    val genres: List<String>,
    val runtimeMinutes: Int?,
    val tmdbRating: Double?,
    val imdbRating: Double?,
    val imdbVotes: Int?,
    val rottenTomatoes: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val cast: List<CastMember>,
    val seasonCount: Int?,
    val episodeCount: Int?,
    val manual: Boolean,
) {
    companion object {
        fun of(entry: FilmInfoEntry): FilmInfo? = if (entry.tmdbId <= 0) null else FilmInfo(
            queryKey = entry.queryKey,
            kind = entry.kind,
            tmdbId = entry.tmdbId,
            imdbId = entry.imdbId,
            title = entry.title,
            originalTitle = entry.originalTitle?.takeIf { !it.equals(entry.title, ignoreCase = true) },
            year = entry.year,
            overview = entry.overview,
            genres = entry.genres?.split('|')?.filter { it.isNotBlank() }.orEmpty(),
            runtimeMinutes = entry.runtimeMinutes,
            tmdbRating = entry.tmdbRating,
            imdbRating = entry.imdbRating,
            imdbVotes = entry.imdbVotes,
            rottenTomatoes = entry.rottenTomatoes,
            posterPath = entry.posterPath,
            backdropPath = entry.backdropPath,
            cast = entry.cast?.lines()?.mapNotNull { line ->
                line.split('\t').takeIf { it.first().isNotBlank() }?.let { CastMember(it[0], it.getOrElse(1) { "" }) }
            }.orEmpty(),
            seasonCount = entry.seasonCount,
            episodeCount = entry.episodeCount,
            manual = entry.manual,
        )
    }
}

/** Film information: one episode as TMDB names it. */
@Immutable
data class EpisodeMeta(val title: String?, val overview: String?, val stillPath: String?, val airDate: String?, val runtimeMinutes: Int?)

/** What the background lookup is doing, for Settings › Film information. */
sealed interface FilmFetchState {
    data object Idle : FilmFetchState
    data class Working(val remaining: Int) : FilmFetchState
    data object WaitingForWifi : FilmFetchState
    data object Offline : FilmFetchState
    data object KeyRefused : FilmFetchState
    data object Limited : FilmFetchState
}

/** How a lookup the user asked for ended. */
enum class FilmOutcome { Done, NotFound, Off, NoKey, Offline, WaitingForWifi, KeyRefused, Limited, Failed }

/**
 * Film information: the app's one lookup of films and series on TMDB (and of their IMDb and Rotten
 * Tomatoes ratings on OMDb), with Nağmedar's own keys from the build (`NAGMEDAR_TMDB_API_KEY`,
 * `NAGMEDAR_OMDB_API_KEY`; without the first nothing is looked up, without the second the ratings stay
 * out, and Settings says so). Each film and series is looked up once, from its title and year alone,
 * and kept in Room; the library shows what is kept and works without it. Lookups go one at a time in
 * the background with a pause between requests, only while the setting is on and (by default) on Wi-Fi.
 */
object FilmInfoCenter {
    const val KEY_ENABLED = "nagmedar_film_info"
    const val KEY_WIFI_ONLY = "nagmedar_film_info_wifi_only"
    const val KEY_LANGUAGE = "nagmedar_film_info_language"
    private const val KEY_OMDB_DAY = "nagmedar_film_info_omdb_day"
    private const val KEY_OMDB_COUNT = "nagmedar_film_info_omdb_count"

    /** Between two requests to TMDB: well under its limit. */
    private const val REQUEST_GAP_MS = 300L

    /** OMDb's free key allows 1 000 requests a day; some are left for the user's own refreshes. */
    private const val OMDB_DAILY_BUDGET = 900

    /** A search that found nothing is tried again after this long. */
    private val RETRY_NOT_FOUND_MS = TimeUnit.DAYS.toMillis(30)

    /**
     * Lookups and images go to the network only in the app: its activity turns this on. Tests and
     * previews show what is cached, and never ask the network.
     */
    @Volatile
    var active = false

    val tmdbKey: String get() = UiBuildConfig.TMDB_API_KEY

    val omdbKey: String get() = UiBuildConfig.OMDB_API_KEY

    /** Whether this build has a TMDB key: without one there is no film information. */
    val tmdbAvailable: Boolean get() = tmdbKey.isNotBlank()

    /** Whether this build has an OMDb key: without one the IMDb rating and the Rotten Tomatoes score stay out. */
    val omdbAvailable: Boolean get() = omdbKey.isNotBlank()

    private val stateFlow = MutableStateFlow<FilmFetchState>(FilmFetchState.Idle)

    /** What the background lookup is doing now. */
    val state: StateFlow<FilmFetchState> = stateFlow

    private val settingsVersion = MutableStateFlow(0L)

    /** Grows when a setting changes: the screens ask again, in the new language. */
    val settingsChanges: StateFlow<Long> = settingsVersion

    private val lock = Any()
    private val pending = LinkedHashMap<String, FilmQuery>()
    private var worker: Job? = null

    /** One lookup at a time, the background's and the user's. */
    private val fetching = Mutex()

    fun userAgent(): String = OkHttpFeedSource.userAgent(VlcBuildConfig.VLC_VERSION_NAME)

    fun enabled(context: Context): Boolean = Settings.getInstance(context).getBoolean(KEY_ENABLED, true)

    fun wifiOnly(context: Context): Boolean = Settings.getInstance(context).getBoolean(KEY_WIFI_ONLY, true)

    fun language(context: Context): FilmLanguage {
        val saved = Settings.getInstance(context).getString(KEY_LANGUAGE, null)
        return FilmLanguage.entries.firstOrNull { it.code == saved }
            ?: if (Locale.getDefault().language == FilmLanguage.Turkish.code) FilmLanguage.Turkish else FilmLanguage.English
    }

    fun setEnabled(context: Context, on: Boolean) = save(context) { putBoolean(KEY_ENABLED, on) }

    fun setWifiOnly(context: Context, on: Boolean) = save(context) { putBoolean(KEY_WIFI_ONLY, on) }

    fun setLanguage(context: Context, language: FilmLanguage) = save(context) { putString(KEY_LANGUAGE, language.code) }

    private fun save(context: Context, edit: android.content.SharedPreferences.Editor.() -> Unit) {
        Settings.getInstance(context).edit().apply(edit).apply()
        settingsVersion.value++
    }

    private fun dao(context: Context): FilmInfoDao = NagmedarDatabase.get(context).filmInfo()

    /** [all] and the table it follows; another database (a test's) gets its own. */
    @Volatile
    private var shared: Pair<FilmInfoDao, StateFlow<Map<String, FilmInfo>>>? = null

    /**
     * Every film and series found, by [FilmQuery.key]: read once for every screen that shows them and kept, so a
     * screen that comes back starts with what was read last instead of an empty map, and is told when it changes.
     */
    fun all(context: Context): StateFlow<Map<String, FilmInfo>> {
        val dao = dao(context)
        shared?.let { (table, flow) -> if (table === dao) return flow }
        return synchronized(this) {
            shared?.takeIf { it.first === dao }?.second ?: dao.observeAll()
                .map { rows -> rows.mapNotNull(FilmInfo::of).associateBy { it.queryKey } }
                .flowOn(Dispatchers.Default)
                .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.WhileSubscribed(KEPT_FOLLOWING_MS), emptyMap())
                .also { shared = dao to it }
        }
    }

    /** How long [all] goes on following the table once no screen shows it. */
    private const val KEPT_FOLLOWING_MS = 60_000L

    /** A series' episodes as TMDB names them, by season and episode. */
    fun episodes(context: Context, showId: Long): Flow<Map<Pair<Int, Int>, EpisodeMeta>> = dao(context).observeEpisodes(showId)
        .map { rows ->
            rows.filter { it.episode != FilmEpisodeEntry.NO_EPISODES }
                .associate { (it.season to it.episode) to EpisodeMeta(it.title, it.overview, it.stillPath, it.airDate, it.runtimeMinutes) }
        }
        .flowOn(Dispatchers.Default)

    /** How many films and series are found. */
    fun foundCount(context: Context): Flow<Int> = dao(context).observeFound()

    /** Whether lookups and images may go to the network now: the setting, the key, a network, Wi-Fi when asked. */
    fun mayUseNetwork(context: Context): Boolean = active && gate(context) == null

    /** What stops a lookup now, or null when nothing does. */
    private fun gate(context: Context): FilmOutcome? {
        if (!tmdbAvailable) return FilmOutcome.NoKey
        if (!enabled(context)) return FilmOutcome.Off
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return FilmOutcome.Offline
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return FilmOutcome.Offline
        if (wifiOnly(context) && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return FilmOutcome.WaitingForWifi
        return null
    }

    /** Looks up in the background those of [queries] not found yet, found in another language, or missing seasons. */
    fun request(context: Context, queries: List<FilmQuery>) {
        if (!active || queries.isEmpty() || !tmdbAvailable) return
        val app = context.applicationContext
        if (!enabled(app)) return
        AppScope.launch(Dispatchers.IO) {
            val stale = runCatching {
                val dao = dao(app)
                val known = dao.all().associateBy { it.queryKey }
                val language = language(app).code
                val now = System.currentTimeMillis()
                queries.distinctBy { it.key }.filter { query -> needsLookup(dao, known[query.key], query, language, now) }
            }.getOrDefault(emptyList())
            if (stale.isEmpty()) return@launch
            synchronized(lock) {
                stale.forEach { pending.putIfAbsent(it.key, it) }
                if (worker?.isActive != true) worker = AppScope.launch(Dispatchers.IO) { work(app) }
            }
        }
    }

    private fun needsLookup(dao: FilmInfoDao, entry: FilmInfoEntry?, query: FilmQuery, language: String, now: Long): Boolean = when {
        entry == null -> true
        entry.tmdbId <= 0 -> now - entry.fetchedAt > RETRY_NOT_FOUND_MS
        entry.language != language -> true
        query.kind == FilmKind.TV -> !knownSeasons(dao, entry.tmdbId, now).containsAll(query.seasons)
        else -> false
    }

    /**
     * The seasons of [showId] that need no asking: those with episodes, and those TMDB had none of
     * (S00's specials, a season of another match) when asked within [RETRY_NOT_FOUND_MS].
     */
    private fun knownSeasons(dao: FilmInfoDao, showId: Long, now: Long): Set<Int> =
        dao.seasons(showId).toSet() - dao.emptySeasonsAskedBefore(showId, now - RETRY_NOT_FOUND_MS).toSet()

    private suspend fun work(context: Context) {
        while (true) {
            val next = synchronized(lock) { pending.values.firstOrNull()?.also { pending.remove(it.key) } }
            if (next == null) {
                stateFlow.value = FilmFetchState.Idle
                return
            }
            when (gate(context)) {
                null -> Unit
                FilmOutcome.WaitingForWifi, FilmOutcome.Offline -> {
                    // the rest waits for the next visit of the library on a network that may be used
                    synchronized(lock) { pending[next.key] = next }
                    stateFlow.value = if (gate(context) == FilmOutcome.Offline) FilmFetchState.Offline else FilmFetchState.WaitingForWifi
                    return
                }
                else -> {
                    synchronized(lock) { pending.clear() }
                    stateFlow.value = FilmFetchState.Idle
                    return
                }
            }
            stateFlow.value = FilmFetchState.Working(synchronized(lock) { pending.size } + 1)
            try {
                fetching.withLock { lookUp(context, next, force = false, chosen = null) }
            } catch (e: FilmServiceException.KeyRefused) {
                synchronized(lock) { pending.clear() }
                stateFlow.value = FilmFetchState.KeyRefused
                return
            } catch (e: FilmServiceException.Limited) {
                synchronized(lock) { pending[next.key] = next }
                stateFlow.value = FilmFetchState.Limited
                return
            } catch (e: IOException) {
                // a network that broke: the next visit tries again
            } catch (e: RuntimeException) {
                // an answer that could not be read: tried again in a month
                runCatching { saveNotFound(context, next) }
            }
            delay(REQUEST_GAP_MS)
        }
    }

    /** Looks [query] up again now ("Bilgileri yenile"): the same film or series, its texts and ratings anew. */
    suspend fun refresh(context: Context, query: FilmQuery): FilmOutcome = runAsked(context) { lookUp(context, query, force = true, chosen = null) }

    /** Keeps [hit] as what [query] is ("Yanlış film mi? Ara…"): a refresh keeps it too. */
    suspend fun choose(context: Context, query: FilmQuery, hit: TmdbHit): FilmOutcome = runAsked(context) { lookUp(context, query, force = true, chosen = hit.id) }

    /** TMDB's films or series for [text] (and [year]) as the user typed them. */
    suspend fun search(context: Context, kind: String, text: String, year: Int?): Pair<FilmOutcome, List<TmdbHit>> {
        if (!active) return FilmOutcome.Offline to emptyList()
        gate(context)?.takeIf { it != FilmOutcome.WaitingForWifi }?.let { return it to emptyList() }
        return try {
            val language = language(context).tmdb
            val tmdb = TmdbClient(tmdbKey, userAgent())
            val hits = if (kind == FilmKind.TV) tmdb.searchSeries(text, year, language) else tmdb.searchMovies(text, year, language)
            FilmOutcome.Done to hits
        } catch (e: FilmServiceException.KeyRefused) {
            FilmOutcome.KeyRefused to emptyList()
        } catch (e: FilmServiceException.Limited) {
            FilmOutcome.Limited to emptyList()
        } catch (e: IOException) {
            FilmOutcome.Failed to emptyList()
        }
    }

    /** Everything found and every image: looked up again on the next visit of the library. */
    suspend fun clear(context: Context) {
        withContext(Dispatchers.IO) {
            synchronized(lock) { pending.clear() }
            fetching.withLock { dao(context).clear() }
        }
        FilmImages.clear(context)
    }

    /** A lookup the user asked for: on any network (the user is there), not when off or without a key. */
    private suspend fun runAsked(context: Context, block: suspend () -> Boolean): FilmOutcome {
        if (!active) return FilmOutcome.Offline
        gate(context)?.takeIf { it != FilmOutcome.WaitingForWifi }?.let { return it }
        return try {
            withContext(Dispatchers.IO) { fetching.withLock { if (block()) FilmOutcome.Done else FilmOutcome.NotFound } }
        } catch (e: FilmServiceException.KeyRefused) {
            FilmOutcome.KeyRefused
        } catch (e: FilmServiceException.Limited) {
            FilmOutcome.Limited
        } catch (e: IOException) {
            FilmOutcome.Failed
        }
    }

    /** Looks [query] up and keeps what is found; false when nothing is. [chosen] is a TMDB id the user picked. */
    private suspend fun lookUp(context: Context, query: FilmQuery, force: Boolean, chosen: Long?): Boolean {
        val dao = dao(context)
        val language = language(context)
        val tmdb = TmdbClient(tmdbKey, userAgent())
        val series = query.kind == FilmKind.TV
        val existing = dao.get(query.key)
        // a film found once stays that film: a refresh and another language read the same one again
        val id = chosen ?: existing?.tmdbId?.takeIf { it > 0 } ?: search(tmdb, query, language)?.id
        if (id == null) {
            saveNotFound(context, query)
            return false
        }
        val first = (if (series) tmdb.series(id, language.tmdb) else tmdb.movie(id, language.tmdb))
        if (first == null) {
            saveNotFound(context, query)
            return false
        }
        val details = if (first.overview.isNullOrBlank() && language != FilmLanguage.English) {
            // TMDB has no Turkish overview of many films: the English one, rather than none
            val english = try {
                if (series) tmdb.series(id, FilmLanguage.English.tmdb) else tmdb.movie(id, FilmLanguage.English.tmdb)
            } catch (e: IOException) {
                null
            }
            first.copy(overview = english?.overview)
        } else {
            first
        }
        val sameFilm = existing != null && existing.imdbId == details.imdbId
        val keepRatings = !force && sameFilm && existing?.imdbRating != null
        val ratings = if (keepRatings) null else details.imdbId?.let { ratings(context, it) }
        // OMDb not asked (kept, no key, the day used up): the same film's ratings stay
        val kept = if (ratings == null && sameFilm) existing else null
        dao.save(
            FilmInfoEntry(
                queryKey = query.key,
                kind = query.kind,
                tmdbId = details.id,
                imdbId = details.imdbId,
                title = details.title,
                originalTitle = details.originalTitle,
                year = details.year,
                overview = details.overview,
                language = language.code,
                genres = details.genres.joinToString("|").takeIf { it.isNotEmpty() },
                runtimeMinutes = details.runtimeMinutes,
                tmdbRating = details.rating,
                tmdbVotes = details.votes,
                imdbRating = ratings?.imdbRating ?: kept?.imdbRating,
                imdbVotes = ratings?.imdbVotes ?: kept?.imdbVotes,
                rottenTomatoes = ratings?.rottenTomatoes ?: kept?.rottenTomatoes,
                posterPath = details.posterPath,
                backdropPath = details.backdropPath,
                cast = details.cast.joinToString("\n") { it.name.replace('\t', ' ') + "\t" + it.character.replace('\t', ' ').replace('\n', ' ') }
                    .takeIf { it.isNotEmpty() },
                seasonCount = details.seasonCount,
                episodeCount = details.episodeCount,
                manual = chosen != null || (existing?.manual == true && existing.tmdbId == details.id),
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        if (series) {
            val changed = force || existing == null || existing.language != language.code || existing.tmdbId != details.id
            val have = if (changed) emptySet() else knownSeasons(dao, details.id, System.currentTimeMillis())
            for (season in query.seasons.sorted()) {
                if (season in have) continue
                delay(REQUEST_GAP_MS)
                val episodes = tmdb.season(details.id, season, language.tmdb)
                val now = System.currentTimeMillis()
                val rows = episodes.map { e ->
                    FilmEpisodeEntry(details.id, e.season, e.episode, e.title, e.overview, e.stillPath, e.airDate, e.runtimeMinutes, language.code, now)
                }
                // TMDB has no such season: kept as asked, else every visit of Series would ask for the show and its season again
                val none = if (rows.none { it.season == season }) {
                    listOf(FilmEpisodeEntry(details.id, season, FilmEpisodeEntry.NO_EPISODES, language = language.code, fetchedAt = now))
                } else {
                    emptyList()
                }
                dao.saveEpisodes(rows + none)
            }
        }
        return true
    }

    /** The best of TMDB's answers for [query]: the same title and year first, then TMDB's own order. */
    private suspend fun search(tmdb: TmdbClient, query: FilmQuery, language: FilmLanguage): TmdbHit? {
        val series = query.kind == FilmKind.TV
        var hits = if (series) tmdb.searchSeries(query.title, query.year, language.tmdb) else tmdb.searchMovies(query.title, query.year, language.tmdb)
        if (hits.isEmpty() && query.year != null) {
            // a year one off (a festival, another country's release) finds nothing with it
            delay(REQUEST_GAP_MS)
            hits = if (series) tmdb.searchSeries(query.title, null, language.tmdb) else tmdb.searchMovies(query.title, null, language.tmdb)
        }
        return bestMatch(query, hits)
    }

    /** OMDb's ratings for [imdbId] while the day's budget lasts; null without a key or over it. */
    private suspend fun ratings(context: Context, imdbId: String): OmdbRatings? {
        if (!omdbAvailable) return null
        val settings = Settings.getInstance(context)
        val day = System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1)
        val used = if (settings.getLong(KEY_OMDB_DAY, -1) == day) settings.getInt(KEY_OMDB_COUNT, 0) else 0
        if (used >= OMDB_DAILY_BUDGET) return null
        settings.edit().putLong(KEY_OMDB_DAY, day).putInt(KEY_OMDB_COUNT, used + 1).apply()
        return try {
            OmdbClient(omdbKey, userAgent()).ratings(imdbId)
        } catch (e: FilmServiceException.Limited) {
            // the day is used up elsewhere: no more today
            settings.edit().putInt(KEY_OMDB_COUNT, OMDB_DAILY_BUDGET).apply()
            null
        } catch (e: FilmServiceException.KeyRefused) {
            null
        } catch (e: IOException) {
            null
        }
    }

    private fun saveNotFound(context: Context, query: FilmQuery) {
        val dao = dao(context)
        // a match found once is not thrown away by a failed refresh
        if ((dao.get(query.key)?.tmdbId ?: 0) > 0) return
        dao.save(FilmInfoEntry(query.key, query.kind, 0, title = query.title, year = query.year, language = language(context).code, fetchedAt = System.currentTimeMillis()))
    }
}

/**
 * The hit that is [query]: the most alike in title (folded, the original title too) and year, TMDB's
 * order deciding between equals; null when no title is alike and there is more than one answer.
 */
internal fun bestMatch(query: FilmQuery, hits: List<TmdbHit>): TmdbHit? {
    if (hits.isEmpty()) return null
    val wanted = foldTitle(query.title)
    fun titleScore(hit: TmdbHit): Int = listOfNotNull(hit.title, hit.originalTitle).maxOf { title ->
        val folded = foldTitle(title)
        when {
            folded == wanted -> 3
            folded.startsWith(wanted) || wanted.startsWith(folded) -> 2
            folded.contains(wanted) || wanted.contains(folded) -> 1
            else -> 0
        }
    }
    fun yearScore(hit: TmdbHit): Int {
        val year = query.year ?: return 0
        val found = hit.year ?: return 0
        return when (kotlin.math.abs(found - year)) {
            0 -> 2
            1 -> 1
            else -> -1
        }
    }
    val scored = hits.take(10).map { it to titleScore(it) * 2 + yearScore(it) }
    val best = scored.maxByOrNull { it.second } ?: return null
    if (titleScore(best.first) == 0 && hits.size > 1) return null
    return best.first
}
