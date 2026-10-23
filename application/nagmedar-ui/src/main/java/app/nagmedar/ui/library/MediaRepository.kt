package app.nagmedar.ui.library

import android.content.Context
import app.nagmedar.ui.onboarding.MediaAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.util.getFromMl
import org.videolan.tools.NAGMEDAR_SAF_FOLDERS
import org.videolan.tools.Settings
import java.io.File

/**
 * Read access to the existing medialibrary for the new screens. Queries run on the IO
 * dispatcher and wait for the medialibrary to start if needed (getFromMl). Without media
 * permission the folders picked with the system picker stand in for it (S-309).
 */
class MediaRepository(private val context: Context) {

    /** What the screens last read from here, shown again at once when they come back (see [ScreenMemory]). */
    val lastRead = ScreenMemory()

    suspend fun videos(): List<MediaWrapper> = context.getFromMl {
        getVideos(Medialibrary.SORT_ALPHA, false, Settings.includeMissing, false)?.toList().orEmpty()
    } + picked(MediaWrapper.TYPE_VIDEO)

    /** The sections' folders as the preferences have them now. */
    fun sources(): LibraryScopes = LibrarySources.read(Settings.getInstance(context))

    /**
     * The music: the medialibrary's (or the picked folders'), a file with a CUE sheet beside it
     * (S-808) replaced by the sheet's tracks. Each folder is listed once for its sheets.
     */
    suspend fun tracks(section: LibrarySection? = null): List<MediaWrapper> {
        val files = audio(section)
        return withContext(Dispatchers.IO) { CueLibrary.expand(context, files) }
    }

    /** The audio files, those of [section]'s folders when it is given (the Music page, its smart lists). */
    private suspend fun audio(section: LibrarySection?): List<MediaWrapper> {
        val files = context.getFromMl {
            getAudio(Medialibrary.SORT_ARTIST, false, Settings.includeMissing, false)?.toList().orEmpty()
        } + picked(MediaWrapper.TYPE_AUDIO)
        val scopes = sources()
        if (section == null || scopes.of(section).isEmpty()) return files
        val rules = LibrarySources.rules(scopes)
        return withContext(Dispatchers.Default) { files.filter { rules.inScope(section, LibrarySources.locationOf(it.uri)) } }
    }

    /**
     * The videos as library items (S-801), with the languages of the subtitle files next to
     * them: each folder is listed once.
     */
    suspend fun videoItems(): List<VideoItem> {
        val videos = videos()
        return withContext(Dispatchers.IO) {
            val listings = HashMap<String, List<String>>()
            videos.map { media ->
                val path = media.uri.takeIf { it.scheme == "file" }?.path
                val sidecars = if (path == null) {
                    emptySet()
                } else {
                    val folder = path.substringBeforeLast('/')
                    val files = listings.getOrPut(folder) { File(folder).list()?.toList().orEmpty() }
                    Sidecars.languages(path.substringAfterLast('/'), files)
                }
                media.toVideoItem(sidecars)
            }
        }
    }

    /**
     * The videos [section] shows (Films, Series, Videos), by its folders and the other sections'
     * (Settings › Library); without folders anywhere as before, camera clips aside from the films.
     */
    suspend fun videoItems(section: LibrarySection): List<VideoItem> {
        val items = videoItems()
        val rules = LibrarySources.rules(sources())
        return withContext(Dispatchers.Default) { splitVideos(items, rules) { LibrarySources.locationOf(it.media?.uri) }.of(section) }
    }

    /** The Videos page's videos with when each was taken (from its name, else its date) and its size. */
    suspend fun clips(): List<VideoClip> {
        val items = videoItems(LibrarySection.Videos)
        return withContext(Dispatchers.IO) {
            items.map { item ->
                val name = item.media?.fileName ?: item.path.substringAfterLast('/')
                val modified = (item.media?.lastModified ?: 0L) * 1000
                VideoClip(item, ClipDates.fromName(name) ?: modified.takeIf { it > 0 } ?: item.addedAt * 1000, sizeOf(item))
            }
        }
    }

    private fun sizeOf(item: VideoItem): Long {
        val uri = item.media?.uri ?: return 0
        return when (uri.scheme) {
            "file" -> uri.path?.let { runCatching { File(it).length() }.getOrNull() } ?: 0
            else -> SafLibrary.sizeOf(uri.toString())
        }
    }

    /**
     * What each section has by its folders (S-309's counts when none has any): the sidebar's
     * numbers and whether the library is empty.
     */
    suspend fun sectionCounts(): SectionCounts {
        val scopes = sources()
        val rules = LibrarySources.rules(scopes)
        val items = videos().map { it.toVideoItem() }
        val split = withContext(Dispatchers.Default) { splitVideos(items, rules) { LibrarySources.locationOf(it.media?.uri) } }
        val tracks = if (scopes.of(LibrarySection.Music).isEmpty()) counts().tracks else audio(LibrarySection.Music).size
        return SectionCounts(split.films.size, split.showCount, split.clips.size, tracks)
    }

    /** Nothing to show in any section: by their folders when some have any. */
    suspend fun libraryEmpty(): Boolean = if (sources().isEmpty) {
        counts().let { it.videos + it.tracks == 0 }
    } else {
        sectionCounts().empty
    }

    suspend fun recentVideos(): List<MediaWrapper> = context.getFromMl { recentVideos?.toList().orEmpty() }

    /**
     * The tracks played last, newest first: the medialibrary's history of local files, those of
     * the Music section only (a podcast episode or a radio station is not a track of it). Not its
     * getRecentAudio: that is every track by when it was added, and its native side reads two
     * arguments the Java side never passes, so it came back empty at random.
     */
    suspend fun recentAudio(): List<MediaWrapper> {
        val played = context.getFromMl { history(Medialibrary.HISTORY_TYPE_LOCAL)?.toList().orEmpty() }
            .filter { it.type == MediaWrapper.TYPE_AUDIO }
        if (played.isEmpty()) return emptyList()
        val tracks = audio(LibrarySection.Music).mapNotNullTo(HashSet()) { it.id.takeIf { id -> id > 0 } }
        return played.filter { it.id in tracks }.take(RECENT_AUDIO)
    }

    suspend fun counts(): LibraryCounts {
        val indexed = context.getFromMl { LibraryCounts(videoCount, audioCount) }
        if (MediaAccess.hasMediaAccess(context)) return indexed
        return LibraryCounts(indexed.videos + picked(MediaWrapper.TYPE_VIDEO).size, indexed.tracks + picked(MediaWrapper.TYPE_AUDIO).size)
    }

    /**
     * Media of the folders picked with the system picker: without media access all of them (S-309's
     * and the sections'), with it only the sections' trees that have no path the medialibrary could
     * scan (a USB drive or a cloud provider's folder).
     */
    private suspend fun picked(type: Int): List<MediaWrapper> {
        val media = MediaAccess.hasMediaAccess(context)
        val settings = Settings.getInstance(context)
        val folders = buildSet {
            if (!media) addAll(settings.getStringSet(NAGMEDAR_SAF_FOLDERS, null).orEmpty())
            LibrarySources.trees(settings).forEach { tree -> if (!media || LibrarySources.pathOfFolder(tree) == null) add(tree) }
        }
        return if (folders.isEmpty()) emptyList() else SafLibrary.media(context, folders, type).distinctBy { it.uri }
    }
}

/** How many tracks Home's recently played shelf shows at most. */
private const val RECENT_AUDIO = 20

data class LibraryCounts(val videos: Int, val tracks: Int)

/** What the sections show by their folders: films, shows, other videos and tracks. */
data class SectionCounts(val films: Int, val shows: Int, val clips: Int, val tracks: Int) {
    val empty: Boolean get() = films + shows + clips + tracks == 0
}

/** "1:52:07" / "3:41" */
fun formatDuration(millis: Long): String {
    if (millis <= 0) return "–"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** "2160p", "1080p", "720p" … from the video height; null for audio or unknown sizes. */
fun MediaWrapper.resolutionLabel(): String? {
    val h = height
    val w = width
    if (h <= 0 || w <= 0) return null
    val lines = minOf(h, w)
    return when {
        lines >= 2000 || w >= 3800 -> "2160p"
        lines >= 1400 -> "1440p"
        lines >= 1000 || w >= 1900 -> "1080p"
        lines >= 700 -> "720p"
        else -> "${lines}p"
    }
}

/** Playback progress of a partly watched video, 0 when unwatched or finished. */
fun MediaWrapper.watchProgress(): Float {
    val position = displayTime
    val duration = length
    if (duration <= 0 || position <= 0) return 0f
    return (position.toFloat() / duration).coerceIn(0f, 1f)
}
