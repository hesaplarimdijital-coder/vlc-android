package app.nagmedar.ui.covers

import android.content.Context
import android.os.SystemClock
import app.nagmedar.ui.podcast.PodcastCovers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.AppScope
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.AlbumCovers
import org.videolan.vlc.media.CoverHook
import org.videolan.vlc.media.PlaybackObserver
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * S-1009: the album covers (S-1006) where the playback service shows the music outside the app:
 * the notification and the lock screen, the media session (Android Auto), the widgets. A kept
 * cover is told at once; the playing track's album's is looked for when it starts (its tags, its
 * folder, Cover Art Archive when allowed), and the service shows it once found. A cover changed in
 * the app shows there too. 1.0: also the web images shown there (shows' covers, stations' logos:
 * [PodcastCovers.webImages]).
 */
class CoverObserver : PlaybackObserver, AlbumCovers {

    private var job: Job? = null

    @Volatile
    private var context: Context? = null

    /** What was told of each album: its cover, or that there is none (asked again a minute later). */
    private val told = ConcurrentHashMap<String, Told>()

    private class Told(val file: File?, val at: Long)

    override fun coverFile(media: MediaWrapper): File? {
        if (media.type != MediaWrapper.TYPE_AUDIO) return null
        return CoverTrack.of(media)?.let { coverFor(it.key) }
    }

    override fun albumCoverFile(artist: String?, title: String?): File? =
        title?.takeIf { it.isNotBlank() }?.let { coverFor(CoverTrack.albumKey(artist, it)) }

    /** The cover kept for [key]; a miss is believed for a minute (a cover found in the app meanwhile shows after it). */
    internal fun coverFor(key: String, now: Long = SystemClock.elapsedRealtime()): File? {
        val app = context ?: return null
        told[key]?.let { if (it.file != null || now - it.at < MISS_MS) return it.file }
        val file = CoverCenter.finder(app).keptNow(key)?.file
        told[key] = Told(file, now)
        return file
    }

    /** For the tests: the context the covers are read in, without a service. */
    internal fun use(context: Context) {
        this.context = context.applicationContext
    }

    override fun attach(service: PlaybackService) {
        use(service.applicationContext)
        CoverHook.covers = this
        CoverHook.web = PodcastCovers.webImages(service)
        job?.cancel()
        job = AppScope.launch {
            // a cover chosen, looked for again or taken away in the app
            launch {
                CoverCenter.changes.drop(1).collect {
                    told.clear()
                    service.onCoverChanged()
                }
            }
            var last: String? = null
            while (isActive) {
                delay(LOOK_MS)
                val media = runCatching { service.currentMediaWrapper }.getOrNull()?.takeIf { it.type == MediaWrapper.TYPE_AUDIO }
                val track = media?.let(CoverTrack::of) ?: continue
                if (track.key == last) continue
                last = track.key
                val before = coverFor(track.key)
                val found = runCatching { CoverCenter.finder(service.applicationContext).find(track) }.getOrNull()?.file
                if (found != null && found != before) {
                    told[track.key] = Told(found, SystemClock.elapsedRealtime())
                    service.onCoverChanged()
                }
            }
        }
    }

    override fun detach() {
        job?.cancel()
        job = null
        if (CoverHook.covers === this) {
            CoverHook.covers = null
            CoverHook.web = null
        }
    }

    private companion object {
        /** How often the playing track is looked at. */
        const val LOOK_MS = 1_000L

        /** How long "no cover" is believed. */
        const val MISS_MS = 60_000L
    }
}
