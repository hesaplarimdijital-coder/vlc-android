package app.nagmedar.ui.library

import android.content.Context
import app.nagmedar.ui.onboarding.MediaAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.util.getFromMl
import org.videolan.tools.NAGMEDAR_SAF_FOLDERS
import org.videolan.tools.Settings

/**
 * Read access to the existing medialibrary for the new screens. Queries run on the IO
 * dispatcher and wait for the medialibrary to start if needed (getFromMl). Without media
 * permission the folders picked with the system picker stand in for it (S-309).
 */
class MediaRepository(private val context: Context) {

    /** What the screens last read from here, shown again at once when they come back (see [ScreenMemory]). */
    val lastRead = ScreenMemory()

    /** The sections' folders as the preferences have them now. */
    fun sources(): LibraryScopes = LibrarySources.read(Settings.getInstance(context))

    /**
     * The music: the medialibrary's (or the picked folders'), a file with a CUE sheet beside it
     * (S-808) replaced by the sheet's tracks. Each folder is listed once for its sheets.
     */
    suspend fun tracks(section: LibrarySection? = null): List<MediaWrapper> = tracksOfFiles(section).first

    /**
     * 1.0: [tracks] of [section] with the medialibrary ids of the files they were read from (a CUE sheet's image is one
     * file): what [recentAudio] keeps the tracks played last to, without listing the library again.
     */
    suspend fun tracksOfFiles(section: LibrarySection?): Pair<List<MediaWrapper>, Set<Long>> {
        val files = audio(section)
        val ids = files.mapNotNullTo(HashSet()) { it.id.takeIf { id -> id > 0 } }
        return withContext(Dispatchers.IO) { CueLibrary.expand(context, files) } to ids
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
     * What each section has by its folders (S-309's counts when none has any): the sidebar's
     * numbers.
     */
    suspend fun sectionCounts(): SectionCounts {
        val scopes = sources()
        val tracks = if (scopes.of(LibrarySection.Music).isEmpty()) counts().tracks else audio(LibrarySection.Music).size
        return SectionCounts(tracks)
    }

    /**
     * The tracks played last, newest first: the medialibrary's history of local files, those of
     * the Music section only (a podcast episode or a radio station is not a track of it). Not its
     * getRecentAudio: that is every track by when it was added, and its native side reads two
     * arguments the Java side never passes, so it came back empty at random.
     */
    suspend fun recentAudio(): List<MediaWrapper> = recentAudio { audio(LibrarySection.Music).mapNotNullTo(HashSet()) { it.id.takeIf { id -> id > 0 } } }

    /** 1.0: [recentAudio], the Music section's files known by their ids ([musicFiles]: [tracksOfFiles]'s, when the caller keeps them). */
    suspend fun recentAudio(musicFiles: suspend () -> Set<Long>): List<MediaWrapper> {
        val played = context.getFromMl { history(Medialibrary.HISTORY_TYPE_LOCAL)?.toList().orEmpty() }
            .filter { it.type == MediaWrapper.TYPE_AUDIO }
        if (played.isEmpty()) return emptyList()
        val tracks = musicFiles()
        // 1.0.1: each track once (the shelf keys its cards by the track's id)
        return played.filter { it.id in tracks }.distinctBy { it.id }.take(RECENT_AUDIO)
    }

    /**
     * 1.0: each change of the medialibrary's playback history (a track played, the history cleared), as it comes,
     * a burst as one; the Music screen reads its recently played again on each while it is open.
     */
    fun historyChanges(): Flow<Unit> = callbackFlow {
        val medialibrary = Medialibrary.getInstance()
        val callback = Medialibrary.HistoryCb { trySend(Unit) }
        medialibrary.addHistoryCb(callback)
        awaitClose { medialibrary.removeHistoryCb(callback) }
    }.conflate()

    suspend fun counts(): LibraryCounts {
        val indexed = context.getFromMl { LibraryCounts(videoCount, audioCount) }
        if (MediaAccess.hasMediaAccess(context)) return indexed
        return LibraryCounts(indexed.videos, indexed.tracks + picked(MediaWrapper.TYPE_AUDIO).size)
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

/** How many tracks the recently played shelf (Music, "Son çalınanlar") shows at most. */
private const val RECENT_AUDIO = 20

data class LibraryCounts(val videos: Int, val tracks: Int)

/** What the sections show by their folders: the tracks. */
data class SectionCounts(val tracks: Int)

/** "1:52:07" / "3:41" */
fun formatDuration(millis: Long): String {
    if (millis <= 0) return "–"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
