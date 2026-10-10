package app.nagmedar.ui.library

import android.content.Context
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.util.getFromMl
import org.videolan.tools.Settings

/**
 * Read access to the existing medialibrary for the new screens. Queries run on the IO
 * dispatcher and wait for the medialibrary to start if needed (getFromMl).
 */
class MediaRepository(private val context: Context) {

    suspend fun videos(): List<MediaWrapper> = context.getFromMl {
        getVideos(Medialibrary.SORT_ALPHA, false, Settings.includeMissing, false)?.toList().orEmpty()
    }

    suspend fun tracks(): List<MediaWrapper> = context.getFromMl {
        getAudio(Medialibrary.SORT_ARTIST, false, Settings.includeMissing, false)?.toList().orEmpty()
    }

    suspend fun recentVideos(): List<MediaWrapper> = context.getFromMl { recentVideos?.toList().orEmpty() }

    suspend fun recentAudio(): List<MediaWrapper> = context.getFromMl { recentAudio?.toList().orEmpty() }

    suspend fun counts(): LibraryCounts = context.getFromMl { LibraryCounts(videoCount, audioCount) }
}

data class LibraryCounts(val videos: Int, val tracks: Int)

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
