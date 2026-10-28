package app.nagmedar.ui.audio.latency

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.nagmedar.audio.latency.AppliedLatency
import app.nagmedar.audio.latency.LatencyProfile
import app.nagmedar.audio.latency.LatencyProfiles
import app.nagmedar.audio.latency.OutputDeviceKey
import app.nagmedar.audio.latency.OutputOrder
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.signed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.tools.AppScope
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.PlaybackObserver
import org.videolan.tools.Notices

/**
 * S-613: the output devices as the app sees them: which plays media now, by Android's own order
 * and the order they were connected in ([LatencyObserver] keeps that while the service lives).
 */
object OutputDevices {
    /** The outputs connected, as profiles know them. */
    fun connected(context: Context): List<OutputDeviceKey> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { OutputDeviceKey.of(it) }
    }

    /** What Android says it plays media through (API 33 and later); null before that. */
    fun routed(context: Context): OutputDeviceKey? {
        if (Build.VERSION.SDK_INT < 33) return null
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        return runCatching { audioManager.getAudioDevicesForAttributes(media).firstOrNull()?.let { OutputDeviceKey.of(it) } }.getOrNull()
    }

    /** The output Android plays media through now: what the observer saw last, else Android's route or its own order over what is connected. */
    fun active(context: Context): OutputDeviceKey? = LatencyCenter.active.value ?: LatencyProfiles.choose(routed(context), connected(context), emptyList())
}

/**
 * S-613: what the latency profiles do now, for the screens: the output in use and the profile
 * applied to it, if any. The observer writes them; without the service they are null.
 */
object LatencyCenter {
    private val activeFlow = MutableStateFlow<OutputDeviceKey?>(null)
    private val appliedFlow = MutableStateFlow<AppliedLatency?>(null)

    /** The output device in use, as the observer last saw it. */
    val active: StateFlow<OutputDeviceKey?> = activeFlow.asStateFlow()

    /** The output and the delay applied to it; null without the service. */
    val applied: StateFlow<AppliedLatency?> = appliedFlow.asStateFlow()

    internal fun set(device: OutputDeviceKey?, profile: LatencyProfile?) {
        activeFlow.value = device
        appliedFlow.value = device?.let { AppliedLatency(it, profile?.delayMs ?: 0, profile) }
    }

    internal fun clear() {
        activeFlow.value = null
        appliedFlow.value = null
    }
}

/**
 * S-613: the latency profiles applied to the output in use, for as long as the playback service
 * lives: when a device connects or goes, the output is found again (what Android routes media
 * to on API 33 and later; before that the device connected last, else Android's own order), its
 * profile looked up, and its delay given to libVLC's audio delay (under the file's own) and to
 * the music engine's clock. A device seen without a profile is written to the list at 0 so that
 * the settings can set it; a profile that is not 0 says so in a short notice when its device
 * becomes the output.
 */
class LatencyObserver : PlaybackObserver {

    private var job: Job? = null
    private var service: PlaybackService? = null
    private var store: LatencyProfileStore? = null
    private var audioManager: AudioManager? = null

    /** The order the devices were connected in while the service lives. */
    private val order = OutputOrder()
    private var profiles: List<LatencyProfile> = emptyList()
    private var applied: AppliedLatency? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val outputs = addedDevices.filter { it.isSink && LatencyProfiles.playsMedia(it.type) }.map { OutputDeviceKey.of(it) }
            order.added(outputs)
            // a device that has no profile yet is listed, at 0; one with a row (with or without its address) is left as it is
            val store = store
            val unknown = outputs.filter { LatencyProfiles.find(profiles, it) == null }
            if (store != null && unknown.isNotEmpty()) AppScope.launch(Dispatchers.IO) { unknown.forEach { runCatching { store.seen(it) } } }
            apply()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            order.removed(removedDevices.map { OutputDeviceKey.of(it) })
            apply()
        }
    }

    override fun attach(service: PlaybackService) {
        val context = service.applicationContext
        this.service = service
        val store = RoomLatencyStore(context).also { store = it }
        audioManager = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.also {
            // the callback comes at once with the devices connected now
            it.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        }
        job?.cancel()
        job = AppScope.launch {
            store.observe().collect { list ->
                profiles = list
                apply()
            }
        }
    }

    override fun detach() {
        job?.cancel()
        job = null
        audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        audioManager = null
        order.clear()
        service = null
        store = null
        applied = null
        LatencyCenter.clear()
    }

    /** The output now and its profile, into the players; a notice when a device with a delay becomes the output. */
    private fun apply() {
        val service = service ?: return
        val audioManager = audioManager ?: return
        val context = service.applicationContext
        val connected = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { OutputDeviceKey.of(it) }
        val device = LatencyProfiles.choose(OutputDevices.routed(context), connected, order.recent)
        val profile = LatencyProfiles.find(profiles, device)
        val delayMs = profile?.delayMs ?: 0
        val manager = runCatching { service.playlistManager }.getOrNull() ?: return
        manager.player.outputLatencyUs = delayMs * 1_000L
        manager.musicEngine.outputLatencyMs = delayMs
        val now = device?.let { AppliedLatency(it, delayMs, profile) }
        val before = applied
        applied = now
        LatencyCenter.set(device, profile)
        if (now != null && profile != null && delayMs != 0 && before?.device != now.device) {
            val res = context.resources
            val delay = res.getString(R.string.nag_sync_ms, signed(delayMs, res.locale()))
            Notices.show(context, res.getString(R.string.nag_latency_applied, deviceLabel(res, now.device), delay))
        }
    }
}
