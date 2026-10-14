package app.nagmedar.ui.radio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.media.MediaUtils

/** A station and the address it plays from, handed to the playback service. */
data class StationStream(val station: Station, val url: String)

/** Where a station's stream is played: the playback service in the app, a recorder in the tests. */
fun interface StationSink {
    fun play(stream: StationStream)
}

/** The playback service (libVLC plays radio, ADR-06): the station is a stream whose title is its name and whose art is its logo. */
class ServiceStationSink(context: Context) : StationSink {
    private val appContext = context.applicationContext

    override fun play(stream: StationStream) {
        val media = MLServiceLocator.getAbstractMediaWrapper(Uri.parse(stream.url))
        media.type = MediaWrapper.TYPE_STREAM
        media.title = stream.station.name
        stream.station.logoUrl?.let { media.setArtworkURL(it) }
        MediaUtils.openMedia(appContext, media)
    }
}

/**
 * S-1105: the station that plays, for the screens and for what comes later (S-1107 records what
 * plays): which station, and the address its stream plays from.
 */
object RadioSession {
    private val current = MutableStateFlow<StationStream?>(null)

    /** The station started from the radio screen and its stream address; null before any. */
    val playing: StateFlow<StationStream?> = current

    internal fun started(stream: StationStream) {
        current.value = stream
    }
}

/**
 * S-1105: starts a station. A station of the directory is reported as a click every time it starts
 * playing, as the directory asks; the same answer gives the address to play, which is used when it
 * comes in time ([clickWaitMs]), else the one kept. Nothing about the directory keeps the station
 * from playing. The click runs in [clickScope], which outlives the caller: waiting for its answer
 * gives up after [clickWaitMs], the click itself is made to the end (a slow first server, or the
 * pace of requests, does not cancel it).
 */
class RadioPlayer(
    private val directory: RadioDirectory,
    private val library: RadioLibrary,
    private val sink: StationSink,
    private val clock: () -> Long = System::currentTimeMillis,
    private val clickWaitMs: Long = CLICK_WAIT_MS,
    private val clickScope: CoroutineScope = CLICKS,
) {
    /** The address [station] plays from now; reports the click of a station of the directory. */
    suspend fun resolve(station: Station): String {
        val uuid = station.uuid ?: return station.streamUrl
        val pending = clickScope.async {
            try {
                directory.click(uuid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // the directory not answering never keeps a station from playing
                null
            }
        }
        // giving up the wait does not cancel the click: it is made to the end
        val click = withTimeoutOrNull(clickWaitMs) { pending.await() }
        return click?.streamUrl?.takeIf { it.isNotBlank() } ?: station.streamUrl
    }

    /** Plays [station]: it is kept, noted as played, its click reported, and its stream handed to the sink. Returns the station as kept. */
    suspend fun start(station: Station): Station {
        val url = resolve(station)
        // the station plays even when the database cannot be written: the failure is raised after it started
        var failure: Exception? = null
        val kept = try {
            library.played(station.copy(streamUrl = url), clock())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
            station.copy(streamUrl = url, lastPlayedAt = clock())
        }
        val stream = StationStream(kept, url)
        sink.play(stream)
        RadioSession.started(stream)
        failure?.let { throw it }
        return kept
    }

    companion object {
        /** How long the click may take before the kept address plays. */
        const val CLICK_WAIT_MS = 3_000L

        /** Where clicks run when no scope is given: outside any screen. */
        private val CLICKS = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
