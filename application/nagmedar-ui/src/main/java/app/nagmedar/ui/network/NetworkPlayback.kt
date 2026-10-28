package app.nagmedar.ui.network

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import app.nagmedar.ui.playback.PlaybackActions
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.util.isSchemeSupported

/**
 * S-1301: the network browser's files played and queued by the playback service (which signs in
 * to a saved server through [NetworkSignInObserver]), and its folders scanned by the medialibrary.
 */
class ServiceNetworkActions(context: Context) : NetworkActions {
    private val app = context.applicationContext

    override fun play(entries: List<NetworkEntry>, index: Int) {
        val media = entries.map(::mediaOf)
        if (media.isEmpty()) return
        // to the device cast to while casting (S-1302): the playback service's renderer
        PlaybackActions.playList(app, media, index.coerceIn(0, media.lastIndex))
    }

    override fun enqueue(entries: List<NetworkEntry>) {
        if (entries.isNotEmpty()) PlaybackActions.appendList(app, entries.map(::mediaOf))
    }

    override fun canAddToLibrary(url: String): Boolean = isSchemeSupported(Uri.parse(url).scheme)

    override fun addToLibrary(url: String) = MedialibraryUtils.addDir(url, app)

    companion object {
        /** [entry] as the playback service takes it: its address, its name, audio (a video heard) or a playlist by its kind. */
        fun mediaOf(entry: NetworkEntry): MediaWrapper = MLServiceLocator.getAbstractMediaWrapper(Uri.parse(entry.url)).apply {
            title = entry.name
            when (entry.kind) {
                // a video is heard: Nağmedar plays sound alone
            EntryKind.Video -> {
                type = MediaWrapper.TYPE_AUDIO
                addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
            }
                EntryKind.Audio -> type = MediaWrapper.TYPE_AUDIO
                EntryKind.Playlist -> type = MediaWrapper.TYPE_PLAYLIST
                else -> Unit
            }
        }
    }
}

/** A device that plays what is sent to it (a DLNA renderer, a Chromecast). */
@Immutable
data class RemoteRenderer(val id: String, val name: String)

/**
 * Sending to a DLNA renderer or a Chromecast (S-1302): the devices found now (while casting, or
 * a picker looked for them), each offered as "Play on …" in the network browser's file menus,
 * the device picker for the rest, and the sending itself.
 */
interface RemoteRenderers {
    fun available(): List<RemoteRenderer>

    fun play(renderer: RemoteRenderer, entries: List<NetworkEntry>, index: Int)

    /** The device picker, to play [entries] from [index] on the device chosen there. */
    fun choose(entries: List<NetworkEntry>, index: Int)

    /** Whether [entry] can be sent at all: a tracker module plays here alone. */
    fun offered(entry: NetworkEntry): Boolean = true
}

object RemoteRendererHook {
    @Volatile
    var renderers: RemoteRenderers? = null
}
