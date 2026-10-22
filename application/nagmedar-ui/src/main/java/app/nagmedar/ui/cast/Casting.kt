package app.nagmedar.ui.cast

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.audio.MusicFormats
import app.nagmedar.ui.network.NetworkEntry
import app.nagmedar.ui.network.RemoteRenderer
import app.nagmedar.ui.network.RemoteRendererHook
import app.nagmedar.ui.network.RemoteRenderers
import app.nagmedar.ui.network.ServiceNetworkActions
import app.nagmedar.ui.playback.PlaybackActions
import org.videolan.libvlc.RendererItem
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AppContextProvider
import org.videolan.tools.KEY_ENABLE_CASTING
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.RendererDelegate
import org.videolan.vlc.media.castItemOf
import org.videolan.vlc.util.CastPickerHook

/** S-1302: what a menu chose to play on a device: the picker plays it there, or here. */
@Immutable
class CastPayload(val media: List<MediaWrapper>, val index: Int) {
    val first: MediaWrapper? get() = media.getOrNull(index)
}

/** The device picker asked for: by a cast button or its keys ([payload] null: what plays now), or by a menu's "Play on …". */
@Immutable
class CastRequest(val payload: CastPayload? = null)

/**
 * S-1302: casting in the app. libVLC's renderer discovery (VLC's RendererDelegate) finds the
 * devices, only while a picker is open or something is cast; the device chosen is the playback
 * service's renderer, which libVLC sends to and the service hands the music engine's track over
 * to; the network browser's "Play on …" comes here too.
 */
object Casting : RemoteRenderers {

    /** The device picker open, and what for; null while it is closed. */
    // made in the global snapshot: the object is often first touched inside a window's composition
    var request: CastRequest? by Snapshot.global { mutableStateOf<CastRequest?>(null) }
        private set

    /** The new interface's picker and menus: installed when the app starts. */
    fun install() {
        RemoteRendererHook.renderers = this
        CastPickerHook.picker = { activity -> showCastPicker(activity) }
    }

    fun openPicker(payload: CastPayload? = null) {
        request = CastRequest(payload)
    }

    fun closePicker() {
        request = null
    }

    /** VLC's "Wireless casting" setting, on unless turned off. */
    fun enabled(context: Context): Boolean = Settings.getInstance(context).getBoolean(KEY_ENABLE_CASTING, true)

    /** Changes when the picker turns casting on, for the cast buttons to show. */
    var settingChanges by Snapshot.global { mutableIntStateOf(0) }
        private set

    fun turnOn(context: Context) {
        Settings.getInstance(context).putSingle(KEY_ENABLE_CASTING, true)
        settingChanges++
    }

    fun deviceOf(item: RendererItem): CastDevice = CastDevice.of(item.displayName, item.type)

    /** The devices found now, one each. */
    fun found(): List<CastDevice> = RendererDelegate.renderers.value.map(::deviceOf)

    /** The device cast to, null while this device plays. */
    val active: CastDevice? get() = PlaybackService.renderer.value?.let(::deviceOf)

    /** Sends what plays, and what plays next, to [device]; false when it is no longer found. Main thread. */
    fun cast(device: CastDevice): Boolean {
        val item = RendererDelegate.renderers.value.firstOrNull { deviceOf(it).id == device.id } ?: return false
        if (PlaybackService.renderer.value != item) PlaybackService.renderer.value = item
        return true
    }

    /** Casting stopped: this device plays again. Main thread. */
    fun stop() {
        if (PlaybackService.renderer.value != null) PlaybackService.renderer.value = null
    }

    /** [payload] played on [device], or here for null. */
    fun play(context: Context, device: CastDevice?, payload: CastPayload) {
        if (payload.media.isEmpty()) return
        if (device == null) {
            // something else plays here: the film cast until now is not brought back to the video player
            PlaybackService.instance?.playlistManager?.castEndsForNewPlayback()
            stop()
        } else if (!cast(device)) {
            return
        }
        PlaybackActions.playList(context, payload.media, payload.index.coerceIn(0, payload.media.lastIndex))
    }

    /** What the picker says of [media] (the one playing, or the one chosen to play). */
    fun notices(media: MediaWrapper?): List<CastNotice> {
        val uri = media?.uri ?: return emptyList()
        val engineMusic = media.type == MediaWrapper.TYPE_AUDIO && MusicFormats.canPlay(uri, null)
        return CastPicker.notices(castItemOf(uri), engineMusic)
    }

    /** Whether "Play on …" is offered for [uri]: not for a tracker module, which plays here alone. */
    fun offered(uri: Uri?): Boolean = CastPicker.offered(castItemOf(uri))

    // the network browser (S-1301's hook)

    override fun available(): List<RemoteRenderer> = found().map { RemoteRenderer(it.id, it.name) }

    override fun play(renderer: RemoteRenderer, entries: List<NetworkEntry>, index: Int) {
        val device = found().firstOrNull { it.id == renderer.id } ?: return
        play(AppContextProvider.appContext, device, CastPayload(entries.map(ServiceNetworkActions::mediaOf), index))
    }

    override fun choose(entries: List<NetworkEntry>, index: Int) = openPicker(CastPayload(entries.map(ServiceNetworkActions::mediaOf), index))

    override fun offered(entry: NetworkEntry): Boolean = offered(Uri.parse(entry.url))
}

/** S-1302: the device cast to, as it changes; null while this device plays. */
@Composable
fun rememberCastDevice(): CastDevice? {
    val item by PlaybackService.renderer.observeAsState()
    return item?.let(Casting::deviceOf)
}

/** S-1302: whether VLC's casting setting is on (cast buttons show then). */
@Composable
fun rememberCastingEnabled(): Boolean {
    val context = LocalContext.current
    val changes = Casting.settingChanges
    return remember(context, changes) { Casting.enabled(context) }
}

/** S-1302: the devices found while the caller is shown; the discovery runs for it until then. */
@Composable
fun rememberDiscovery(): Pair<List<CastDevice>, State<Boolean>> {
    val owner = remember { Any() }
    DisposableEffect(owner) {
        RendererDelegate.want(owner, true)
        onDispose { RendererDelegate.want(owner, false) }
    }
    val items by RendererDelegate.renderers.observeAsState()
    val discovering = RendererDelegate.discovering.collectAsState()
    return items.orEmpty().map(Casting::deviceOf) to discovering
}
