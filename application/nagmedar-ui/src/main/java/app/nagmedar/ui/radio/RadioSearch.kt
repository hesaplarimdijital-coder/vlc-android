package app.nagmedar.ui.radio

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far the directory's lists for the filters (countries, languages, tags) have got. */
enum class OptionsPhase { Idle, Loading, Ready, Failed }

/** The directory's countries, languages and tags, for the filters' menus. */
@Immutable
data class FilterOptions(
    val phase: OptionsPhase = OptionsPhase.Idle,
    val countries: List<DirectoryCountry> = emptyList(),
    val languages: List<DirectoryLanguage> = emptyList(),
    val tags: List<DirectoryTag> = emptyList(),
)

/**
 * S-1105: the search of the directory as the radio screen has it: the words typed (searched a
 * moment after the last key, or at once on Enter), the filters and the order, the stations found
 * with a "more" page, and why the search failed. Only the latest search counts; a station the
 * user marked (a vote) is shown as marked.
 */
@Stable
class RadioSearch(
    private val directory: RadioDirectory,
    private val library: RadioLibrary,
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEBOUNCE_MS,
) {
    var text by mutableStateOf("")
        private set

    var filters by mutableStateOf(StationFilters())
        private set

    var order by mutableStateOf(StationOrder.Votes)
        private set

    /** The stations found so far, pages together. */
    var stations by mutableStateOf<List<Station>>(emptyList())
        private set

    /** A search or a further page is on its way. */
    var loading by mutableStateOf(false)
        private set

    /** Why the last search failed; null when it did not. */
    var failure by mutableStateOf<RadioFailure?>(null)
        private set

    /** Whether the directory may have more stations than are listed. */
    var more by mutableStateOf(false)
        private set

    /** Whether a search has finished: tells "nothing found" from "not searched yet". */
    var searched by mutableStateOf(false)
        private set

    var options by mutableStateOf(FilterOptions())
        private set

    private var job: Job? = null
    private var optionsJob: Job? = null
    private var generation = 0

    /** The words in the box changed: the search follows when the typing pauses. */
    fun type(value: String) {
        if (value == text) return
        text = value
        job?.cancel()
        job = scope.launch {
            delay(debounceMs)
            run(0, append = false)
        }
    }

    /** The first search of a screen: the directory's best stations, unless one was made. */
    fun start() {
        if (!searched && job?.isActive != true) search()
    }

    /** Searches at once (Enter, a changed filter), from the first page. */
    fun search() {
        job?.cancel()
        job = scope.launch { run(0, append = false) }
    }

    fun filter(value: StationFilters) {
        if (value == filters) return
        filters = value
        search()
    }

    fun sort(value: StationOrder) {
        if (value == order) return
        order = value
        search()
    }

    /** The next page of the same search. */
    fun loadMore() {
        if (loading || !more) return
        job?.cancel()
        job = scope.launch { run(stations.size, append = true) }
    }

    /** Tries again what failed: the first page, or the next. */
    fun retry() {
        if (stations.isEmpty()) search() else loadMore()
    }

    /** [station] changed (a vote, a play): its row shows it. */
    fun replace(station: Station) {
        stations = stations.map { if (it.key == station.key) station else it }
    }

    /** The lists the filters' menus show are fetched, once. */
    fun loadOptions() {
        if (options.phase == OptionsPhase.Loading || options.phase == OptionsPhase.Ready) return
        options = options.copy(phase = OptionsPhase.Loading)
        optionsJob?.cancel()
        optionsJob = scope.launch {
            try {
                coroutineScope {
                    val countries = async { directory.countries() }
                    val languages = async { directory.languages() }
                    val tags = async { directory.tags() }
                    options = FilterOptions(OptionsPhase.Ready, countries.await(), languages.await(), tags.await())
                }
            } catch (e: RadioFailure) {
                options = options.copy(phase = OptionsPhase.Failed)
            }
        }
    }

    private suspend fun run(offset: Int, append: Boolean) {
        val mine = ++generation
        loading = true
        failure = null
        try {
            val query = StationQuery(text, filters, order, offset)
            val found = directory.search(query)
            val marked = mark(found)
            if (mine != generation) return
            stations = if (append) {
                val known = stations.mapTo(HashSet()) { it.key }
                stations + marked.filter { it.key !in known }
            } else {
                marked
            }
            more = found.size >= query.limit
            searched = true
        } catch (e: RadioFailure) {
            if (mine == generation) {
                failure = e
                if (!append) {
                    stations = emptyList()
                    more = false
                }
                searched = true
            }
        } finally {
            if (mine == generation) loading = false
        }
    }

    /** What the user did with the stations listed (a vote, a play) put on them. */
    private suspend fun mark(found: List<Station>): List<Station> {
        // the marks are a nicety: a database that cannot be read does not stop the list
        val kept = try {
            library.marks(found.mapNotNull { it.uuid })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
        return found.map { station ->
            val mark = station.uuid?.let(kept::get) ?: return@map station
            station.copy(id = mark.id, favourite = mark.favourite, lastVotedAt = mark.lastVotedAt, lastPlayedAt = mark.lastPlayedAt)
        }
    }

    companion object {
        /** How long typing may pause before the search starts. */
        const val DEBOUNCE_MS = 400L
    }
}
