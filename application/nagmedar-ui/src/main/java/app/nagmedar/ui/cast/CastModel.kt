package app.nagmedar.ui.cast

import androidx.compose.runtime.Immutable
import app.nagmedar.playback.CastItem
import app.nagmedar.playback.CastSupport
import app.nagmedar.playback.RendererKey
import app.nagmedar.playback.RendererKind

/** S-1302: a device that plays what is sent to it (a Chromecast, a DLNA TV or receiver), as the picker lists it. */
@Immutable
data class CastDevice(val id: String, val name: String, val kind: RendererKind) {
    companion object {
        /** The device libVLC reports as [name] of [type]: one id for each address it answers on. */
        fun of(name: String, type: String?): CastDevice {
            val key = RendererKey.of(name, type)
            return CastDevice("${key.kind.name}:${key.name}", name.trim(), key.kind)
        }
    }
}

/** What the device picker shows under "This device" (S-1302). */
sealed interface CastPickerView {
    /** VLC's casting setting is off: nothing is looked for. */
    data object Off : CastPickerView

    /** No Wi-Fi or Ethernet: there is no local network to look on. */
    data object NoNetwork : CastPickerView

    /** Looking, nothing found yet. */
    data object Searching : CastPickerView

    /** Nothing found after a while: what to check. */
    data object Empty : CastPickerView

    /** The devices found, as they come and go; [searching] while the discovery runs. */
    data class Devices(val devices: List<CastDevice>, val searching: Boolean) : CastPickerView
}

/** Why an item is not cast as it is (S-1302), said in the picker above the devices. */
enum class CastNotice {
    /** A tracker module: it plays on this device alone; the queue after it is cast. */
    TrackerHere,

    /** DSD: libVLC sends it as PCM. */
    DsdAsPcm,

    /** Music of the music engine: libVLC sends the file, without the engine's DSP chain, ReplayGain or crossfade. */
    EngineSettings,
}

object CastPicker {
    /** How long the picker says "searching" before it says what to check. */
    const val EMPTY_AFTER_MS = 10_000L

    /**
     * What the picker shows: casting off, no local network, the devices (the one cast to first
     * when the discovery has not found it again), searching, or nothing found after
     * [EMPTY_AFTER_MS] of looking.
     */
    fun view(enabled: Boolean, lan: Boolean, found: List<CastDevice>, active: CastDevice?, discovering: Boolean, searchedMs: Long): CastPickerView {
        if (!enabled) return CastPickerView.Off
        val devices = if (active != null && found.none { it.id == active.id }) listOf(active) + found else found
        if (devices.isNotEmpty()) return CastPickerView.Devices(devices, searching = discovering)
        if (!lan) return CastPickerView.NoNetwork
        return if (searchedMs < EMPTY_AFTER_MS) CastPickerView.Searching else CastPickerView.Empty
    }

    /** What is said of [item] (the one playing, or the first one chosen to play); [engineMusic] when the music engine would play it here. */
    fun notices(item: CastItem?, engineMusic: Boolean): List<CastNotice> = buildList {
        when (item?.support) {
            CastSupport.LocalOnly -> add(CastNotice.TrackerHere)
            CastSupport.AsPcm -> add(CastNotice.DsdAsPcm)
            else -> Unit
        }
        if (engineMusic && item?.support != CastSupport.LocalOnly) add(CastNotice.EngineSettings)
    }

    /** Whether "Play on …" is offered for [item]: not for a tracker module. */
    fun offered(item: CastItem?): Boolean = item?.support != CastSupport.LocalOnly
}
