package app.nagmedar.ui.radio

import android.net.Uri
import androidx.compose.runtime.Immutable
import app.nagmedar.playback.RadioMetadata
import app.nagmedar.playback.StreamDisplay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.PlaybackService

/**
 * What the playback service plays, as far as the radio screen cares: its address, the stream's
 * "now playing" text (ICY), whether it plays, the address it was expanded from when it is an entry
 * of a playlist ([entry]; null when the media is not an entry of the last expansion) and whether
 * it is a stream at all.
 */
data class PlayingSnapshot(
    val url: String,
    val nowPlaying: String?,
    val playing: Boolean,
    val entry: String? = null,
    val isStream: Boolean = false,
)

/** The station on the air, and what its stream says is playing. */
@Immutable
data class RadioNow(val station: Station, val display: StreamDisplay, val playing: Boolean)

/**
 * S-1106: the radio screen's now-playing strip: the station started from the screen, as long as it
 * is what the playback service plays, with the title its stream sends (ICY StreamTitle, which
 * libVLC reports as the media's now playing) split by [RadioMetadata]. [flow] is the current ICY
 * title as a flow, for the strip and for what comes after (S-1107 records what plays).
 */
object RadioNowPlaying {

    /** The strip for [stream] given what the service plays; null when it is not that station (another media took over, or nothing plays). */
    fun of(stream: StationStream?, snapshot: PlayingSnapshot?): RadioNow? {
        if (stream == null || snapshot == null) return null
        if (!isStation(stream, snapshot)) return null
        return RadioNow(stream.station, RadioMetadata.display(stream.station.name, snapshot.nowPlaying), snapshot.playing)
    }

    /**
     * Whether the service plays [stream]'s station: a stream with its address, or the entry of the
     * playlist it points to (libVLC replaces a .pls or .m3u by the addresses in it, which are not
     * the address the station was started with; the service says what the media was expanded
     * from). Another station of the same host is another station, and a media that is no stream
     * (a local file played after) is not the station.
     */
    private fun isStation(stream: StationStream, snapshot: PlayingSnapshot): Boolean {
        val url = Uri.parse(stream.url).toString()
        if (snapshot.entry == url) return true
        return snapshot.isStream && snapshot.url == url
    }

    /** The station on the air and its title, as they change: from the screen's last start and the playback service. */
    fun flow(): Flow<RadioNow?> = combine(RadioSession.playing, snapshots()) { stream, snapshot -> of(stream, snapshot) }.distinctUntilChanged()

    /** What the playback service plays, each time its media, its stream's metadata or its state change; null with no service or no media. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun snapshots(): Flow<PlayingSnapshot?> = PlaybackService.serviceFlow.flatMapLatest<PlaybackService?, PlayingSnapshot?> { service ->
        if (service == null) {
            flowOf<PlayingSnapshot?>(null)
        } else {
            callbackFlow<PlayingSnapshot?> {
                fun snapshot() = service.currentMediaWrapper?.let {
                    PlayingSnapshot(it.uri.toString(), it.nowPlaying, service.isPlaying, service.playlistManager.expandedFrom, it.type == MediaWrapper.TYPE_STREAM)
                }
                val callback = object : PlaybackService.Callback {
                    override fun update() {
                        trySend(snapshot())
                    }

                    override fun onMediaEvent(event: IMedia.Event) {
                        trySend(snapshot())
                    }

                    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
                        when (event.type) {
                            MediaPlayer.Event.Playing, MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped,
                            MediaPlayer.Event.EndReached, MediaPlayer.Event.EncounteredError -> trySend(snapshot())
                        }
                    }
                }
                trySend(snapshot())
                service.addCallback(callback)
                awaitClose { service.removeCallback(callback) }
            }
        }
    }.distinctUntilChanged()
}
