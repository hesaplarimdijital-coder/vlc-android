package app.nagmedar.ui.podcast

import androidx.compose.runtime.Immutable

/** 1.0.3: one of Apple's top-level podcast categories ("Comedy", 1303) as its chart names it. */
@Immutable
data class ChartGenre(val id: Int, val name: String)

/** 1.0.3: a show of Apple's chart: the directory's hit (its feed found by a lookup) and its top-level categories. */
@Immutable
data class ChartShow(val hit: PodcastHit, val genres: List<ChartGenre>)

/** 1.0.3: a line of Apple's chart as it comes, before the lookup gives its feed: Apple's id of the show, its name, cover, categories. */
@Immutable
data class ChartEntry(val id: Long, val name: String, val artist: String?, val artworkUrl: String?, val genres: List<ChartGenre>)

/**
 * 1.0.3: Apple Podcasts' charts as Android Auto's Keşfet lists them: the storefronts it offers, the categories it names in
 * the app's language. A chart lists a storefront's most listened shows ([ITunesDirectory.top]); its categories are those
 * of its shows (the chart cannot be asked for one category).
 */
object ApplePodcasts {
    /** The most shows a chart lists (Apple answers no more). */
    const val CHART_LIMIT = 100

    /** The storefronts Keşfet offers besides the device's own, by their two-letter codes (Apple's are lower case). */
    val COUNTRIES = listOf(
        "ae", "ar", "at", "au", "be", "bg", "br", "ca", "ch", "cl", "co", "cz", "de", "dk", "eg", "es", "fi", "fr", "gb", "gr",
        "hk", "hr", "hu", "id", "ie", "il", "in", "it", "jp", "kr", "kz", "mx", "my", "ng", "nl", "no", "nz", "pe", "ph", "pk",
        "pl", "pt", "ro", "ru", "sa", "se", "sg", "sk", "th", "tr", "tw", "ua", "us", "vn", "za",
    )

    /** The genre every podcast is under ("Podcasts"): no category of its own. */
    const val ROOT_GENRE = 26

    const val ARTS = 1301
    const val BUSINESS = 1321
    const val COMEDY = 1303
    const val EDUCATION = 1304
    const val FICTION = 1483
    const val GOVERNMENT = 1511
    const val HEALTH = 1512
    const val HISTORY = 1487
    const val KIDS = 1305
    const val LEISURE = 1502
    const val MUSIC = 1310
    const val NEWS = 1489
    const val RELIGION = 1314
    const val SCIENCE = 1533
    const val SOCIETY = 1324
    const val SPORTS = 1545
    const val TECHNOLOGY = 1318
    const val TRUE_CRIME = 1488
    const val TV = 1309

    /** [code] as a storefront's code: two letters in lower case; null for anything else. */
    fun storefront(code: String?): String? = code?.trim()?.lowercase(java.util.Locale.ROOT)?.takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' } }

    /**
     * The shows of [entries] (a chart, in its order) with their feeds from [found] (the lookup's hits by Apple's id): a
     * show the lookup did not give a feed for (Apple's own, a paid one) is left out; what a hit lacks comes from the chart.
     */
    fun shows(entries: List<ChartEntry>, found: Map<Long, PodcastHit>): List<ChartShow> {
        val seen = HashSet<String>()
        return entries.mapNotNull { entry ->
            val hit = found[entry.id] ?: return@mapNotNull null
            // a show listed twice (two ids, one feed) is shown once
            if (!seen.add(FeedUrl.key(hit.feedUrl))) return@mapNotNull null
            ChartShow(hit.copy(author = hit.author ?: entry.artist, imageUrl = hit.imageUrl ?: entry.artworkUrl), entry.genres)
        }
    }
}
