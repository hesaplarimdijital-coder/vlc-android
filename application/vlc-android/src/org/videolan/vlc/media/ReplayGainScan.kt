package org.videolan.vlc.media

import android.content.Context
import android.util.Log
import app.nagmedar.audio.Loudness
import app.nagmedar.audio.LoudnessScanner
import app.nagmedar.audio.MusicFormats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * S-907: the library's albums measured (EBU R128) and tagged with their ReplayGain, in the
 * background. One album at a time: its tracks scanned into the album's gain, then each track's
 * tags written (track and album gain and peak; R128 for Opus). Tracks that carry a gain already
 * are skipped unless [start] is told to redo them. Progress is a state the settings screen shows.
 */
object ReplayGainScan {

    data class Progress(
        val running: Boolean = false,
        val albums: Int = 0,
        val albumsDone: Int = 0,
        val tracksDone: Int = 0,
        val tracksSkipped: Int = 0,
        val tracksFailed: Int = 0,
        /** The album under way. */
        val current: String? = null,
        val finished: Boolean = false,
        val cancelled: Boolean = false,
    ) {
        val fraction: Float get() = if (albums == 0) 0f else albumsDone.toFloat() / albums
    }

    private const val TAG = "ReplayGainScan"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    @Volatile private var job: Job? = null
    @Volatile private var scanner: LoudnessScanner? = null

    val running: Boolean get() = job?.isActive == true

    /** Starts the scan over the whole library; [redo] measures tagged tracks again. No-op while one runs. */
    fun start(context: Context, redo: Boolean = false) {
        if (running) return
        val app = context.applicationContext
        _progress.value = Progress(running = true)
        job = scope.launch { run(app, redo) }
    }

    fun cancel() {
        scanner?.cancel()
        job?.cancel()
        _progress.update { it.copy(running = false, cancelled = true, finished = true, current = null) }
    }

    private suspend fun run(context: Context, redo: Boolean) {
        val ml = Medialibrary.getInstance()
        val albums = if (ml.isInitiated) ml.getAlbums(false, false) else emptyArray()
        _progress.update { it.copy(albums = albums.size) }
        try {
            for (album in albums) {
                if (job?.isActive != true) return
                _progress.update { it.copy(current = album.title) }
                scanAlbum(context, album, redo)
                _progress.update { it.copy(albumsDone = it.albumsDone + 1) }
            }
            _progress.update { it.copy(running = false, finished = true, current = null) }
        } catch (e: Exception) {
            Log.w(TAG, "scan stopped: $e")
            _progress.update { it.copy(running = false, finished = true, current = null) }
        }
    }

    /** One album: the tracks the engine can read, measured together; the tags of each written. */
    internal fun scanAlbum(context: Context, album: Album, redo: Boolean) {
        val tracks = album.tracks.filter { track -> MusicFormats.canPlay(track.uri, null) }
        if (tracks.isEmpty()) return
        val todo = if (redo) tracks else tracks.filter { track -> LoudnessScanner.probe(context, track.uri)?.hasReplayGain != true }
        if (todo.isEmpty()) {
            _progress.update { it.copy(tracksSkipped = it.tracksSkipped + tracks.size) }
            return
        }
        _progress.update { it.copy(tracksSkipped = it.tracksSkipped + tracks.size - todo.size) }
        LoudnessScanner(context).use { session ->
            scanner = session
            val measured = LinkedHashMap<MediaWrapper, Loudness>()
            for (track in tracks) {
                if (job?.isActive != true) return
                /* every track of the album counts for the album's gain, the tagged ones included */
                val loudness = session.scan(track.uri, forAlbum = true)
                if (loudness == null) {
                    if (track in todo) _progress.update { it.copy(tracksFailed = it.tracksFailed + 1) }
                    continue
                }
                measured[track] = loudness
            }
            val albumLoudness = session.album()
            for ((track, loudness) in measured) {
                if (track !in todo) continue
                if (LoudnessScanner.writeTags(context, track.uri, loudness, albumLoudness)) {
                    _progress.update { it.copy(tracksDone = it.tracksDone + 1) }
                } else {
                    _progress.update { it.copy(tracksFailed = it.tracksFailed + 1) }
                }
            }
            scanner = null
        }
    }
}
