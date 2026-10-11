package app.nagmedar.ui.library

import app.nagmedar.lab.DynamicRange
import app.nagmedar.lab.MediaTech
import java.util.Locale

/*
 * S-801: the video library as the screens show it. The medialibrary indexes files; here they
 * become films and series episodes (from the file and folder names), folders, and what to
 * continue. Everything is plain data so it can be tested without the medialibrary.
 */

/** Season and episode of a file, from its name or its folders. */
data class EpisodeInfo(
    val show: String,
    val season: Int,
    val episode: Int,
    /** Last episode of a multi-episode file (S01E01E02, S01E01-E02), else [episode]. */
    val lastEpisode: Int = episode,
    /** The episode's own title when the name carries one ("Pilot"), else empty. */
    val title: String = "",
)

object EpisodeParser {
    private val sxe = Regex("""(?i)(?:^|[ ._\-\[(])s(\d{1,2})[ ._-]?e(\d{1,3})(?:[ ._-]?(?:e|-e?)(\d{1,3}))?(?![0-9])""")
    private val nxn = Regex("""(?i)(?:^|[ ._\-\[(])(\d{1,2})x(\d{2,3})(?![0-9])""")
    private val turkish = Regex("""(?iu)(\d{1,2})\s*\.?\s*sezon[ ._-]*(\d{1,3})\s*\.?\s*b[öo]l[üu]m""")
    private val turkishWords = Regex("""(?iu)sezon[ ._-]*(\d{1,2})[ ._-]*b[öo]l[üu]m[ ._-]*(\d{1,3})""")
    private val seasonFolder = Regex("""(?i)^(?:season|sezon|series|staffel|saison|s)[ ._-]*(\d{1,2})$|^(\d{1,2})\.?\s*sezon$""")

    /** Release tags where an episode title ends: resolution, source, codec, HDR, audio, group. */
    private val tags = Regex(
        """(?i)(?:^|[ ._\-\[(])(?:2160p|1080p|1080i|720p|576p|480p|4k|uhd|web[ ._-]?dl|webrip|web|blu[ ._-]?ray|bdrip|brrip|remux|hdtv|dvdrip|""" +
            """x26[45]|h[ ._]?26[45]|hevc|avc|av1|hdr10\+?|hdr|dv|dovi|dolby[ ._-]?vision|atmos|truehd|dts|ddp?5[ ._]?1|aac|proper|repack|internal)(?:$|[ ._\-\])])""",
    )

    /**
     * The episode a file is, from [path] (its file and folders): "Show.S01E02.Title.mkv",
     * "Show 1x02", "Dizi 1. Sezon 2. Bölüm", or "S01E02.mkv" inside "Show/Season 1/".
     */
    fun parse(path: String): EpisodeInfo? {
        val parts = path.trimEnd('/').split('/').filter { it.isNotEmpty() }
        val file = parts.lastOrNull() ?: return null
        val name = file.substringBeforeLast('.', file)
        val folders = parts.dropLast(1)

        val m = sxe.find(name) ?: nxn.find(name) ?: turkish.find(name) ?: turkishWords.find(name) ?: return null
        val season = m.groupValues[1].toInt()
        val episode = m.groupValues[2].toInt()
        val last = m.groupValues.getOrNull(3)?.toIntOrNull()?.takeIf { it > episode } ?: episode
        val before = name.substring(0, m.range.first)
        val after = name.substring(m.range.last + 1)

        var show = clean(before)
        if (show.isEmpty()) {
            // "Show/Season 1/S01E02.mkv" or "Show/S01E02.mkv"
            val parent = folders.lastOrNull().orEmpty()
            show = if (seasonFolder.matches(parent.trim())) clean(folders.getOrNull(folders.size - 2).orEmpty()) else clean(parent)
        }
        if (show.isEmpty()) return null
        return EpisodeInfo(show, season, episode, last, title(after))
    }

    /** "The.Expanse" → "The Expanse"; a trailing year and separators go. */
    private fun clean(text: String): String {
        val words = text.replace('.', ' ').replace('_', ' ').trim(' ', '-', '[', '(', ']', ')')
        return words.replace(Regex("""\s*[(\[]?(19|20)\d{2}[)\]]?$"""), "").replace(Regex("""\s+"""), " ").trim(' ', '-')
    }

    private fun title(after: String): String {
        val cut = tags.find(after)?.range?.first ?: after.length
        return clean(after.substring(0, cut))
    }

    /** Key that groups the files of one show: case, accents of I/i and separators do not matter. */
    fun showKey(show: String): String = show.lowercase(Locale.ROOT).replace('ı', 'i').replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
}

data class Episode(val item: VideoItem, val info: EpisodeInfo) {
    /** "S01E02", "S01E01–E02". */
    val code: String
        get() = "S%02dE%02d".format(Locale.ROOT, info.season, info.episode) +
            if (info.lastEpisode > info.episode) "–E%02d".format(Locale.ROOT, info.lastEpisode) else ""
}

data class Season(val number: Int, val episodes: List<Episode>)

data class Show(val key: String, val title: String, val seasons: List<Season>) {
    val episodes: List<Episode> get() = seasons.flatMap { it.episodes }

    /** Episodes watched to the end (the medialibrary's play count). */
    val watched: Int get() = episodes.count { it.item.watched }

    /**
     * Where to go on: the episode in progress, else the first unwatched one after the last
     * watched, else the first one; null when every episode has been watched.
     */
    val next: Episode?
        get() {
            val all = episodes
            all.firstOrNull { it.item.progress > 0f }?.let { return it }
            val lastWatched = all.indexOfLast { it.item.watched }
            return all.drop(lastWatched + 1).firstOrNull { !it.item.watched } ?: all.firstOrNull { !it.item.watched }
        }
}

/** A folder of the video library with the films in it. */
data class VideoFolder(val path: String, val name: String, val items: List<VideoItem>)

/** The video library split for the screens: films, shows, folders of films. */
class VideoLibrary(val movies: List<VideoItem>, val shows: List<Show>, val folders: List<VideoFolder>) {
    /** Films in progress, newest first: the "Devam et" shelf. */
    val continueMovies: List<VideoItem> get() = movies.filter { it.progress > 0f }.sortedByDescending { it.addedAt }

    companion object {
        val Empty = VideoLibrary(emptyList(), emptyList(), emptyList())
    }
}

fun buildVideoLibrary(items: List<VideoItem>): VideoLibrary {
    val episodes = mutableListOf<Episode>()
    val movies = mutableListOf<VideoItem>()
    for (item in items) {
        val info = EpisodeParser.parse(item.path)
        if (info != null) episodes += Episode(item, info) else movies += item
    }
    val shows = episodes.groupBy { EpisodeParser.showKey(it.info.show) }.map { (key, group) ->
        // the most common spelling names the show
        val title = group.groupingBy { it.info.show }.eachCount().maxByOrNull { it.value }!!.key
        val seasons = group.groupBy { it.info.season }.toSortedMap().map { (number, list) ->
            Season(number, list.sortedWith(compareBy<Episode> { it.info.episode }.thenBy { it.item.title }))
        }
        Show(key, title, seasons)
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    val folders = movies.groupBy { it.folder }.map { (path, list) ->
        VideoFolder(path, path.trimEnd('/').substringAfterLast('/').ifEmpty { path }, list)
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    return VideoLibrary(movies, shows, folders)
}

/** The filter chips of the films (S-801); chips combine, each one narrowing the list. */
enum class MovieFilter {
    UHD, HDR, DolbyVision, Atmos, TurkishSubtitles, Unwatched;

    fun matches(item: VideoItem, tech: MediaTech?): Boolean = when (this) {
        UHD -> (tech?.video?.resolution ?: resolutionOf(item.width, item.height))?.isUhd == true
        HDR -> tech?.video?.dynamicRange.let { it != null && it != DynamicRange.SDR }
        DolbyVision -> tech?.video?.dynamicRange == DynamicRange.DolbyVision
        Atmos -> tech?.atmos == true
        TurkishSubtitles -> "tur" in item.sidecarSubtitles || tech?.subtitles?.any { it.language.lowercase(Locale.ROOT) == "tur" } == true
        Unwatched -> !item.watched
    }
}

fun List<VideoItem>.filtered(filters: Set<MovieFilter>, tech: Map<String, MediaTech>): List<VideoItem> =
    if (filters.isEmpty()) this else filter { item -> filters.all { it.matches(item, tech[item.key]) } }

/** How the films are ordered ("Sırala: …"). */
enum class MovieOrder { Added, Title, Duration, Resolution }

fun List<VideoItem>.ordered(order: MovieOrder, tech: Map<String, MediaTech>): List<VideoItem> = when (order) {
    MovieOrder.Added -> sortedByDescending { it.addedAt }
    MovieOrder.Title -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    MovieOrder.Duration -> sortedByDescending { it.durationMs }
    // the sharpest first: 4K Dolby Vision before 4K HDR10 before HD
    MovieOrder.Resolution -> sortedWith(
        compareByDescending<VideoItem> { (tech[it.key]?.video?.resolution ?: resolutionOf(it.width, it.height))?.ordinal ?: -1 }
            .thenByDescending { tech[it.key]?.video?.dynamicRange?.ordinal ?: -1 },
    )
}

/** Subtitle files next to a video and their languages: "Film.tr.srt", "Film.Turkish.ass", "Film.srt". */
object Sidecars {
    val extensions = setOf("srt", "ass", "ssa", "sub", "vtt", "idx", "sup", "smi")

    private val words = mapOf(
        "tr" to "tur", "tur" to "tur", "turkish" to "tur", "türkçe" to "tur", "turkce" to "tur",
        "en" to "eng", "eng" to "eng", "english" to "eng", "ingilizce" to "eng",
        "de" to "ger", "ger" to "ger", "deu" to "ger", "german" to "ger", "fr" to "fre", "fre" to "fre", "fra" to "fre", "french" to "fre",
        "es" to "spa", "spa" to "spa", "spanish" to "spa", "it" to "ita", "ita" to "ita", "italian" to "ita", "ru" to "rus", "rus" to "rus",
        "ar" to "ara", "ara" to "ara", "ja" to "jpn", "jpn" to "jpn", "ko" to "kor", "kor" to "kor", "pt" to "por", "por" to "por",
    )

    /**
     * Languages of the subtitle files among [files] (names in the video's folder) that belong to
     * [video] (its file name): ISO 639-2 codes, "und" for a file without a language.
     */
    fun languages(video: String, files: Collection<String>): Set<String> {
        val base = video.substringBeforeLast('.', video).lowercase(Locale.ROOT)
        return files.mapNotNull { file ->
            val lower = file.lowercase(Locale.ROOT)
            val ext = lower.substringAfterLast('.', "")
            if (ext !in extensions || !lower.startsWith(base)) return@mapNotNull null
            val middle = lower.substring(base.length, lower.length - ext.length - 1)
            // "Film2.srt" and "Film 2.srt" belong to other films
            if (middle.isNotEmpty() && middle[0] !in ".-_") return@mapNotNull null
            middle.split('.', '-', '_', ' ').firstNotNullOfOrNull { words[it] } ?: "und"
        }.toSet()
    }
}
