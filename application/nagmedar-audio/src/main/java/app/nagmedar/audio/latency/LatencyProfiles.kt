package app.nagmedar.audio.latency

/** S-613: the kinds of output device a latency profile tells apart, in the list's order. */
enum class OutputKind { Bluetooth, Usb, Hdmi, Wired, Speaker, Other }

/**
 * S-613: an output device as a latency profile knows it: AudioDeviceInfo's type, its product
 * name, and its address where the system gives a stable one (a Bluetooth device's; a USB
 * device's names the port it is on, so it is left out).
 */
data class OutputDeviceKey(val type: Int, val name: String, val address: String = "") {
    val kind: OutputKind get() = LatencyProfiles.kindOf(type)

    /** The device whose address is not kept: the same device on another port or pairing. */
    val withoutAddress: OutputDeviceKey get() = if (address.isEmpty()) this else copy(address = "")

    /** For the extension that reads an AudioDeviceInfo, in the module that has Android. */
    companion object
}

/**
 * S-613: how much later than the app's clock a device plays, in milliseconds (positive: the
 * sound is heard that much after the clock says so); set by hand or measured.
 */
data class LatencyProfile(
    val device: OutputDeviceKey,
    val delayMs: Int,
    /** The delay came from a measurement with the microphone, with its spread. */
    val measured: Boolean = false,
    val spreadMs: Int? = null,
    val updatedAt: Long = 0,
    /**
     * The built-in speaker's own round trip as measured, ms: the microphone's and the speaker's
     * path, taken off every other device's measurement. It is not applied as a delay; the
     * speaker's [delayMs] stays what the user sets.
     */
    val calibrationMs: Int? = null,
)

/** S-613: the profile applied to the output in use now, for the notice and the signal path. */
data class AppliedLatency(val device: OutputDeviceKey, val delayMs: Int, val profile: LatencyProfile?)

/**
 * S-613: the rules of the latency profiles: which device is the output now, which profile is
 * its, and how its delay joins the audio delay the user set for a file. Pure, so the playback
 * service and the settings screen share one answer.
 */
object LatencyProfiles {
    const val MIN_MS = -2000
    const val MAX_MS = 2000
    const val STEP_MS = 10

    // AudioDeviceInfo.TYPE_* by their numbers, so that nothing here needs Android or an API level
    const val TYPE_BUILTIN_EARPIECE = 1
    const val TYPE_BUILTIN_SPEAKER = 2
    const val TYPE_WIRED_HEADSET = 3
    const val TYPE_WIRED_HEADPHONES = 4
    const val TYPE_LINE_ANALOG = 5
    const val TYPE_LINE_DIGITAL = 6
    const val TYPE_BLUETOOTH_SCO = 7
    const val TYPE_BLUETOOTH_A2DP = 8
    const val TYPE_HDMI = 9
    const val TYPE_HDMI_ARC = 10
    const val TYPE_USB_DEVICE = 11
    const val TYPE_USB_ACCESSORY = 12
    const val TYPE_DOCK = 13
    const val TYPE_FM = 14
    const val TYPE_TELEPHONY = 18
    const val TYPE_AUX_LINE = 19
    const val TYPE_IP = 20
    const val TYPE_BUS = 21
    const val TYPE_USB_HEADSET = 22
    const val TYPE_HEARING_AID = 23
    const val TYPE_BUILTIN_SPEAKER_SAFE = 24
    const val TYPE_REMOTE_SUBMIX = 25
    const val TYPE_BLE_HEADSET = 26
    const val TYPE_BLE_SPEAKER = 27
    const val TYPE_HDMI_EARC = 29
    const val TYPE_BLE_BROADCAST = 30
    const val TYPE_DOCK_ANALOG = 31

    fun kindOf(type: Int): OutputKind = when (type) {
        TYPE_BLUETOOTH_A2DP, TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER, TYPE_BLE_BROADCAST, TYPE_HEARING_AID -> OutputKind.Bluetooth
        TYPE_USB_DEVICE, TYPE_USB_HEADSET, TYPE_USB_ACCESSORY -> OutputKind.Usb
        TYPE_HDMI, TYPE_HDMI_ARC, TYPE_HDMI_EARC -> OutputKind.Hdmi
        TYPE_WIRED_HEADSET, TYPE_WIRED_HEADPHONES, TYPE_LINE_ANALOG, TYPE_LINE_DIGITAL, TYPE_AUX_LINE, TYPE_DOCK, TYPE_DOCK_ANALOG -> OutputKind.Wired
        TYPE_BUILTIN_SPEAKER, TYPE_BUILTIN_SPEAKER_SAFE -> OutputKind.Speaker
        else -> OutputKind.Other
    }

    /** Whether a device of [type] has an address that stays the same: a Bluetooth device's. */
    fun stableAddress(type: Int): Boolean = kindOf(type) == OutputKind.Bluetooth

    /** Whether a device of [type] plays media at all: the earpiece, the call's Bluetooth, the tuners and the mixes do not. */
    fun playsMedia(type: Int): Boolean = when (type) {
        TYPE_BUILTIN_EARPIECE, TYPE_BLUETOOTH_SCO, TYPE_FM, TYPE_TELEPHONY, TYPE_IP, TYPE_BUS, TYPE_REMOTE_SUBMIX -> false
        else -> kindOf(type) != OutputKind.Other
    }

    /** Whether a device of [type] comes and goes: everything but the built-in speakers. */
    fun removable(type: Int): Boolean = kindOf(type) != OutputKind.Speaker

    /**
     * Android's own order for media: the Bluetooth devices, then the wired ones, USB, the dock,
     * HDMI, and the speaker last. Lower comes first.
     */
    private fun rank(type: Int): Int = when (type) {
        TYPE_BLUETOOTH_A2DP, TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER, TYPE_BLE_BROADCAST, TYPE_HEARING_AID -> 0
        TYPE_WIRED_HEADPHONES -> 1
        TYPE_LINE_ANALOG, TYPE_LINE_DIGITAL, TYPE_AUX_LINE -> 2
        TYPE_WIRED_HEADSET -> 3
        TYPE_USB_HEADSET -> 4
        TYPE_USB_ACCESSORY -> 5
        TYPE_USB_DEVICE -> 6
        TYPE_DOCK, TYPE_DOCK_ANALOG -> 7
        TYPE_HDMI_EARC -> 8
        TYPE_HDMI_ARC -> 9
        TYPE_HDMI -> 10
        TYPE_BUILTIN_SPEAKER -> 11
        TYPE_BUILTIN_SPEAKER_SAFE -> 12
        else -> 13
    }

    /**
     * The device Android plays media through, out of the [connected] outputs: the removable
     * device connected last ([recent], oldest first) as Android 12 and later route, otherwise
     * the first in Android's own order; null when nothing plays media.
     */
    fun active(connected: List<OutputDeviceKey>, recent: List<OutputDeviceKey> = emptyList()): OutputDeviceKey? {
        val candidates = connected.filter { playsMedia(it.type) }
        if (candidates.isEmpty()) return null
        val last = recent.lastOrNull { it in candidates && removable(it.type) }
        return last ?: candidates.minWith(compareBy({ rank(it.type) }, { it.name }))
    }

    /**
     * [device]'s profile among [profiles]: the one with its address, else the one saved without
     * (before the address was known, or from another device's backup); null when it has none.
     */
    fun find(profiles: List<LatencyProfile>, device: OutputDeviceKey?): LatencyProfile? {
        if (device == null) return null
        return profiles.firstOrNull { it.device == device }
            ?: profiles.firstOrNull { it.device.withoutAddress == device.withoutAddress && (it.device.address.isEmpty() || device.address.isEmpty()) }
    }

    /** What the output plays late, by its profile; 0 without one. */
    fun delayMs(profiles: List<LatencyProfile>, device: OutputDeviceKey?): Int = find(profiles, device)?.delayMs ?: 0

    /**
     * libVLC's audio delay (µs) for a file whose own delay is [userDelayUs] on an output that
     * plays [deviceDelayMs] late: the device's share is taken off underneath, so the delay the
     * user sees, saves and nudges stays the file's own.
     */
    fun combinedDelayUs(userDelayUs: Long, deviceDelayMs: Int): Long = userDelayUs - deviceDelayMs * 1_000L

    /** The music engine's clock for the interface: the engine's position less what the output plays late, within the track ([lengthMs] 0: unknown). */
    fun clockMs(engineMs: Long, deviceDelayMs: Int, lengthMs: Long = 0L): Long {
        val clock = (engineMs - deviceDelayMs).coerceAtLeast(0L)
        return if (lengthMs > 0L) clock.coerceAtMost(lengthMs) else clock
    }

    /**
     * The output in use: [routed], what Android says it plays media through (API 33's
     * getDevicesForAttributes), when known; else [active] over [connected] and [recent].
     */
    fun choose(routed: OutputDeviceKey?, connected: List<OutputDeviceKey>, recent: List<OutputDeviceKey>): OutputDeviceKey? =
        routed?.takeIf { playsMedia(it.type) } ?: active(connected, recent)

    /** [ms] on the stepper's grid and within its range. */
    fun snap(ms: Int): Int = (Math.round(ms / STEP_MS.toDouble()) * STEP_MS).toInt().coerceIn(MIN_MS, MAX_MS)
}

/**
 * S-613: the order the output devices were connected in while the service lives, for
 * [LatencyProfiles.active]. The system's first report lists every device already there, in no
 * order that means anything, so it is not an order; what comes after it is.
 */
class OutputOrder {
    private val order = ArrayList<OutputDeviceKey>()
    private var reported = false

    /** Oldest first. */
    val recent: List<OutputDeviceKey> get() = order

    /** Devices the system reports as added: the first report is what was there, the rest are connections. */
    fun added(devices: List<OutputDeviceKey>) {
        if (!reported) {
            reported = true
            return
        }
        devices.forEach { device ->
            order.remove(device)
            order.add(device)
        }
    }

    fun removed(devices: List<OutputDeviceKey>) {
        order.removeAll(devices.toSet())
    }

    fun clear() {
        order.clear()
        reported = false
    }
}

/**
 * S-613: libVLC's audio delay as two parts: the user's, for the file (what the sync panel shows
 * and the per-file settings keep), and the output device's, from its profile, underneath it.
 * [casting]: the sound goes to a renderer, not the local output, so the profile does not apply.
 */
class AudioDelayMix {
    var userDelayUs = 0L
    var outputLatencyUs = 0L
    var casting = false

    /**
     * VLC's classic Bluetooth delay (one value for every Bluetooth device, an audio delay as the
     * user sets one), as the video player gives it while one plays: the output's share when no
     * latency profile has one, never on top of a profile's, whichever of the two comes first. Not
     * the user's delay, so the file's own is neither overwritten nor added to twice.
     */
    var legacyBluetoothUs = 0L

    /** What libVLC is given: the user's delay with the output's share (its profile, else the classic Bluetooth delay; none while casting). */
    val combinedUs: Long
        get() = when {
            casting -> userDelayUs
            outputLatencyUs != 0L -> LatencyProfiles.combinedDelayUs(userDelayUs, (outputLatencyUs / 1_000L).toInt())
            else -> userDelayUs + legacyBluetoothUs
        }

    /**
     * A new media: libVLC begins it at no delay, the user's part starts over, the profile's
     * stays; the classic Bluetooth delay is the video player's to give again for it.
     */
    fun mediaStarted() {
        userDelayUs = 0L
        legacyBluetoothUs = 0L
    }

    /**
     * S-1302: a renderer chosen ([casting]) or let go: what libVLC is to be given now, at once,
     * changed or not, as its sound starts again on the other output.
     */
    fun rendererChanged(casting: Boolean): Long {
        this.casting = casting
        return combinedUs
    }
}
