package app.nagmedar.ui.library

import androidx.compose.runtime.Immutable
import java.util.Calendar
import java.util.Locale

/*
 * The library's sections and the folders each one shows (Settings › Library). Plain data and
 * rules, so they can be tested without Android: where a file is, which section takes it, and
 * which videos are camera clips rather than films.
 */

/** The parts of the library that can be given their own folders. */
enum class LibrarySection(val key: String) {
    Movies("movies"),
    Series("series"),
    Videos("videos"),
    Music("music"),
    ;

    /** The sections that share the videos; music has its own files. */
    val video: Boolean get() = this != Music
}

/**
 * A folder a section shows, or with [exclude] one it leaves out: a file path ("/storage/1A2B-3C4D/Movies")
 * or the tree URI the system's folder picker gave ("content://…/tree/primary%3AMovies").
 */
@Immutable
data class SourceFolder(val location: String, val exclude: Boolean = false) {
    val isTree: Boolean get() = location.startsWith("content://")
}

/** Each section's folders. A section without folders to show shows everything the library has for it. */
@Immutable
data class LibraryScopes(val folders: Map<LibrarySection, List<SourceFolder>> = emptyMap()) {
    fun of(section: LibrarySection): List<SourceFolder> = folders[section].orEmpty()

    /** The section shows only files under its folders. */
    fun constrained(section: LibrarySection): Boolean = of(section).any { !it.exclude }

    val isEmpty: Boolean get() = folders.values.all { it.isEmpty() }

    /** Every tree URI any section holds. */
    val trees: Set<String> get() = folders.values.flatten().filter { it.isTree }.map { it.location }.toSet()

    fun with(section: LibrarySection, list: List<SourceFolder>): LibraryScopes = LibraryScopes(folders + (section to list))

    /** [folder] added to [section] (once; added again it only changes whether it is left out). */
    fun plus(section: LibrarySection, folder: SourceFolder): LibraryScopes =
        with(section, of(section).filter { it.location != folder.location } + folder)

    fun minus(section: LibrarySection, location: String): LibraryScopes = with(section, of(section).filter { it.location != location })

    companion object {
        val Empty = LibraryScopes()

        /** One section's folders as the preferences keep them: a line each, "+" shown, "−" left out. */
        fun encode(list: List<SourceFolder>): String = list.joinToString("\n") { (if (it.exclude) "-" else "+") + it.location }

        fun decode(text: String?): List<SourceFolder> = text.orEmpty().lineSequence()
            .filter { it.length > 1 && (it[0] == '+' || it[0] == '-') }
            .map { SourceFolder(it.substring(1), exclude = it[0] == '-') }
            .distinctBy { it.location }
            .toList()
    }
}

/** Where files and folders are, compared without regard to case (Android's shared storage and SD cards ignore it). */
object Locations {
    /** The provider of the shared storage and SD cards in the system's folder picker. */
    const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    /** The Downloads provider, whose documents are sometimes plain paths ("raw:/storage/…"). */
    const val DOWNLOADS = "com.android.providers.downloads.documents"

    /** "/storage/emulated/0/Movies/" → "/storage/emulated/0/movies". */
    fun ofPath(path: String): String = path.trimEnd('/').lowercase(Locale.ROOT).ifEmpty { "/" }

    /**
     * A document's file path when the provider's id names one: "primary:Movies/Film.mkv" of the
     * shared storage under [primaryRoot], "1A2B-3C4D:Movies" under /storage/1A2B-3C4D, "raw:/…" of
     * Downloads; null for any other document.
     */
    fun documentPath(authority: String, documentId: String, primaryRoot: String): String? {
        if (authority == DOWNLOADS && documentId.startsWith("raw:/")) return documentId.removePrefix("raw:")
        if (authority != EXTERNAL_STORAGE) return null
        val colon = documentId.indexOf(':')
        if (colon <= 0) return null
        val volume = documentId.substring(0, colon)
        val rest = documentId.substring(colon + 1).trim('/')
        val root = if (volume.equals("primary", ignoreCase = true)) primaryRoot.trimEnd('/') else "/storage/$volume"
        return if (rest.isEmpty()) root else "$root/$rest"
    }

    /** A document's location: its path when it has one, else the provider and the id (a child's id starts with its folder's). */
    fun ofDocument(authority: String, documentId: String, primaryRoot: String): String =
        documentPath(authority, documentId, primaryRoot)?.let(::ofPath) ?: "doc:$authority/" + documentId.trimEnd('/').lowercase(Locale.ROOT)

    /** [location] is [folder] or inside it, subfolders included. */
    fun isUnder(location: String, folder: String): Boolean =
        location == folder || location.startsWith(if (folder.endsWith('/') || folder.endsWith(':')) folder else "$folder/")
}

/**
 * The sections' folders made into rules, with [locate] turning a stored folder into its location.
 * A folder whose location is unknown still makes its section show only its folders; it matches nothing.
 */
class ScopeRules(val scopes: LibraryScopes, locate: (String) -> String?) {
    private val shown = LibrarySection.entries.associateWith { section ->
        scopes.of(section).filter { !it.exclude }.map { locate(it.location) ?: UNKNOWN }
    }
    private val left = LibrarySection.entries.associateWith { section ->
        scopes.of(section).filter { it.exclude }.mapNotNull { locate(it.location) }
    }

    fun constrained(section: LibrarySection): Boolean = shown.getValue(section).isNotEmpty()

    private fun excluded(section: LibrarySection, location: String) = left.getValue(section).any { Locations.isUnder(location, it) }

    /** [section] has a folder to show that holds [location], and does not leave it out. */
    fun claims(section: LibrarySection, location: String): Boolean =
        shown.getValue(section).any { Locations.isUnder(location, it) } && !excluded(section, location)

    /**
     * Whether [section] shows the file at [location]: under one of its folders when it has some,
     * else anywhere but its excluded folders and the folders other video sections claim (a file
     * in the folder chosen for Videos is no film). A file of no known place (null) is shown only
     * by sections without folders.
     */
    fun inScope(section: LibrarySection, location: String?): Boolean {
        if (location == null) return !constrained(section)
        if (excluded(section, location)) return false
        if (constrained(section)) return claims(section, location)
        if (!section.video) return true
        return LibrarySection.entries.none { it != section && it.video && claims(it, location) }
    }

    private companion object {
        const val UNKNOWN = "\u0000"
    }
}

/** The videos as the library's sections show them: films, series episodes and the other videos (camera clips, recordings). */
class VideoSplit(val films: List<VideoItem>, val episodes: List<VideoItem>, val clips: List<VideoItem>) {
    fun of(section: LibrarySection): List<VideoItem> = when (section) {
        LibrarySection.Movies -> films
        LibrarySection.Series -> episodes
        LibrarySection.Videos -> clips
        LibrarySection.Music -> emptyList()
    }

    /** How many shows the episodes make. */
    val showCount: Int get() = episodes.mapNotNull { EpisodeParser.parse(it.path)?.let { info -> EpisodeParser.showKey(info.show) } }.distinct().size
}

/**
 * Which section shows each video. Series shows the episodes (by their names) in its scope; Films
 * the other videos in its scope, and without folders of its own not the camera clips either
 * ([CameraClips]); Videos what its folders hold, or without folders what neither of them shows.
 * A file can be in two sections when both have its folder.
 */
fun splitVideos(items: List<VideoItem>, rules: ScopeRules, locate: (VideoItem) -> String?): VideoSplit {
    val episodes = ArrayList<VideoItem>()
    val films = ArrayList<VideoItem>()
    val clips = ArrayList<VideoItem>()
    val moviesConstrained = rules.constrained(LibrarySection.Movies)
    val videosConstrained = rules.constrained(LibrarySection.Videos)
    for (item in items) {
        val location = locate(item)
        val episode = EpisodeParser.parse(item.path) != null
        val inSeries = episode && rules.inScope(LibrarySection.Series, location)
        val inFilms = !inSeries && rules.inScope(LibrarySection.Movies, location) &&
            (moviesConstrained || (!episode && !CameraClips.isClip(location ?: item.path.lowercase(Locale.ROOT))))
        val inVideos = rules.inScope(LibrarySection.Videos, location) && (videosConstrained || (!inSeries && !inFilms))
        if (inSeries) episodes += item
        if (inFilms) films += item
        if (inVideos) clips += item
    }
    return VideoSplit(films, episodes, clips)
}

/** Videos made with the device rather than collected: the camera's, screen recordings, messengers' (a guess from where they are and their names). */
object CameraClips {
    /** Folders whose videos are recordings wherever they are. */
    private val folders = setOf(
        "dcim", "camera", "screenrecords", "screenrecorder", "screen recordings", "screenrecordings", "screencasts",
        "whatsapp video", "whatsapp animated gifs", "telegram video", "signal", "instagram", "snapchat", "tiktok",
    )

    /** "VID_20240101_123456", "PXL_20240101_…", "VID-20240101-WA0001", "20240101_123456", "Screen_Recording_20240101…". */
    private val names = Regex("""^(?:vid|pxl|mvimg|img|video|mov|screen[ _-]?recording|screenrecord|screenrecorder|record|rec|trim|signal)[ _-]?\d{8}|^\d{8}[ _-]\d{6}""")

    /** [location] (lower case) is a camera clip or a recording. */
    fun isClip(location: String): Boolean {
        val parts = location.split('/').filter { it.isNotEmpty() }
        if (parts.dropLast(1).any { it in folders }) return true
        return names.containsMatchIn(parts.lastOrNull().orEmpty())
    }
}

/** When a clip was taken, from its name ("VID_20240101_123456.mp4") when it carries the date. */
object ClipDates {
    private val stamp = Regex("""(?:^|\D)(20\d{2}|19\d{2})(\d{2})(\d{2})(?:[ _-]?(\d{2})(\d{2})(\d{2}))?(?!\d)""")

    /** Milliseconds since the epoch in this device's time zone, or null when the name has no valid date. */
    fun fromName(name: String): Long? {
        val m = stamp.find(name) ?: return null
        val (y, mo, d) = Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        val h = m.groupValues[4].toIntOrNull() ?: 0
        val mi = m.groupValues[5].toIntOrNull() ?: 0
        val s = m.groupValues[6].toIntOrNull() ?: 0
        if (mo !in 1..12 || d !in 1..31 || h > 23 || mi > 59 || s > 59) return null
        return Calendar.getInstance().apply {
            clear()
            isLenient = false
            set(y, mo - 1, d, h, mi, s)
        }.let { runCatching { it.timeInMillis }.getOrNull() }
    }
}

/** A video of the Videos page: the item, when it was taken (or last changed) and its size. */
@Immutable
data class VideoClip(val item: VideoItem, val takenAt: Long, val sizeBytes: Long)

/** How the Videos page is ordered ("Sırala: …"). */
enum class ClipOrder { Date, Name, Size, Duration }

fun List<VideoClip>.ordered(order: ClipOrder): List<VideoClip> = when (order) {
    ClipOrder.Date -> sortedByDescending { it.takenAt }
    ClipOrder.Name -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.item.title })
    ClipOrder.Size -> sortedByDescending { it.sizeBytes }
    ClipOrder.Duration -> sortedByDescending { it.item.durationMs }
}
