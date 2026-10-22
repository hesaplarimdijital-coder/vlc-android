package app.nagmedar.ui.library.metadata

import app.nagmedar.data.FilmKind
import app.nagmedar.ui.library.EpisodeParser
import java.text.Normalizer
import java.util.Locale

/** Film information: a film's title and year as its file name gives them. */
data class FilmName(val title: String, val year: Int?)

/**
 * Film information: a film's title and year from its file name ("The.Matrix.1999.1080p.BluRay.x264-GRP.mkv",
 * "Arrival (2016) [2160p].mkv", "Babam ve Oğlum 2005 WEB-DL"), the release tags cut away. A name that says
 * nothing ("movie.mkv", "VIDEO_TS", a disc's "title_t00") gives way to its folder's. Only this title and year
 * are ever sent to TMDB: never the path.
 */
object FilmNameParser {
    private val year = Regex("""(?<![0-9])(19[0-9]{2}|20[0-9]{2})(?![0-9])""")

    /** Where a title ends: resolution, source, codec, HDR, audio, edition and release words. */
    private val tags = Regex(
        """(?i)(?:^|[ ._\-\[(])(?:2160p|1440p|1080p|1080i|720p|576p|480p|4k|uhd|web[ ._-]?dl|webrip|blu[ ._-]?ray|bdrip|brrip|remux|hdtv|""" +
            """dvdrip|dvd|hdrip|hdcam|x26[45]|h[ ._]?26[45]|hevc|avc|av1|xvid|divx|10[ ._-]?bit|hdr10\+?|hdr|dv|dovi|dolby[ ._-]?vision|""" +
            """atmos|truehd|dts(?:[ ._-]?hd)?|ddp?[ ._]?[257][ ._]?[01]|ac3|eac3|aac|flac|proper|repack|internal|limited|extended|unrated|""" +
            """uncut|remastered|imax|directors?[ ._-]?cut|theatrical|multi|dual|dublaj|turkce|altyazili|subbed|amzn|dsnp|hmax|atvp)(?:$|[ ._\-\])])""",
    )

    /** Names that say nothing of the film: the folder names it. */
    private val generic = Regex("""(?i)^(?:movie|film|video|main|feature|sample|title[ ._-]?t?\d+|video_ts|vts_\d+_\d+|bdmv|stream|index|playlist)$""")

    /** Folders that hold a disc's structure, not the film's name. */
    private val discFolders = setOf("video_ts", "bdmv", "stream", "playlist", "certificate", "audio_ts")

    /** The title and year of the film at [path]; null when nothing like a title is left. */
    fun parse(path: String): FilmName? {
        val parts = path.trimEnd('/').split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val file = parts.last().substringBeforeLast('.', parts.last())
        val folders = parts.dropLast(1).filter { it.lowercase(Locale.ROOT) !in discFolders }
        val bare = file.trim()
        // a number names nothing ("00001.m2ts"), unless it is a year that is a title ("1917")
        val saysNothing = generic.matches(bare) || (bare.all { it.isDigit() } && !year.matches(bare))
        val fromFile = parseName(file).takeUnless { saysNothing }
        if (fromFile != null) return fromFile
        return folders.lastOrNull()?.let(::parseName)
    }

    /** The title and year in one name ("The.Matrix.1999.1080p"). */
    fun parseName(name: String): FilmName? {
        // "[GROUP] Title" and "www.site.org - Title": a leading group goes
        var text = name.trim().replace(Regex("""^\[[^\]]*]\s*"""), "").replace(Regex("""(?i)^www\.[^ ]+\s+-\s+"""), "")
        // dots and underscores separate words when the name has no spaces of its own
        if (' ' !in text || text.count { it == '.' } > 2) text = text.replace('.', ' ')
        text = text.replace('_', ' ')
        val tag = tags.find(text)?.range?.first ?: text.length
        val head = text.substring(0, tag)
        // the last year before the tags; a title that is a year ("1917", "2001: A Space Odyssey") stays a title, and
        // so does a year still to come ("Blade Runner 2049")
        val latest = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1
        val years = year.findAll(head).filter { it.range.first > 0 && it.value.toInt() <= latest }.toList()
        val found = years.lastOrNull()
        val title = clean(if (found != null) head.substring(0, found.range.first) else head)
        if (title.isEmpty() || title.none { it.isLetterOrDigit() }) return null
        return FilmName(title, found?.value?.toInt())
    }

    private fun clean(text: String): String = text
        .replace(Regex("""[(\[{]\s*$"""), "")
        .replace(Regex("""[(\[{][^)\]}]*$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '(', '[', '{', ',', '+')
}

/**
 * Film information: what is looked up for one film or series. [key] names the cached result: the
 * film's normalised title and year, the series' name ([EpisodeParser.showKey]). [seasons] are a
 * series' seasons in the library, whose episodes are read too.
 */
data class FilmQuery(val kind: String, val title: String, val year: Int?, val seasons: Set<Int> = emptySet()) {
    val key: String = keyOf(kind, title, year)

    companion object {
        fun keyOf(kind: String, title: String, year: Int?): String = when (kind) {
            FilmKind.TV -> "tv:" + EpisodeParser.showKey(title)
            else -> "m:" + EpisodeParser.showKey(title) + ":" + (year?.toString() ?: "")
        }

        fun movie(name: FilmName) = FilmQuery(FilmKind.MOVIE, name.title, name.year)

        fun show(title: String, seasons: Set<Int>) = FilmQuery(FilmKind.TV, title, null, seasons)
    }
}

/** A title folded for comparing: lower case, no accents, the dotless i as i, words by single spaces. */
internal fun foldTitle(text: String): String {
    val lower = text.lowercase(Locale.ROOT).replace('\u0131', 'i').replace("&", " and ")
    val bare = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
    return bare.replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
}
