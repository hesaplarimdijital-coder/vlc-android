package app.nagmedar.audio.latency

import app.nagmedar.audio.latency.LatencyProfiles.TYPE_BLE_HEADSET
import app.nagmedar.audio.latency.LatencyProfiles.TYPE_BLUETOOTH_A2DP
import app.nagmedar.audio.latency.LatencyProfiles.TYPE_BLUETOOTH_SCO
import app.nagmedar.audio.latency.LatencyProfiles.TYPE_BUILTIN_EARPIECE
import app.nagmedar.audio.latency.LatencyProfiles.TYPE_BUILTIN_SPEAKER
import app.nagmedar.audio.latency.LatencyProfiles.TYPE_HDMI
import app.nagmedar.audio.latency.LatencyProfiles.TYPE_HDMI_EARC
import app.nagmedar.audio.latency.LatencyProfiles.TYPE_REMOTE_SUBMIX
import app.nagmedar.audio.latency.LatencyProfiles.TYPE_TELEPHONY
import app.nagmedar.audio.latency.LatencyProfiles.TYPE_USB_DEVICE
import app.nagmedar.audio.latency.LatencyProfiles.TYPE_USB_HEADSET
import app.nagmedar.audio.latency.LatencyProfiles.TYPE_WIRED_HEADPHONES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-613: which output plays, which profile is its, and how its delay joins a file's own. */
class LatencyProfilesTest {

    private val speaker = OutputDeviceKey(TYPE_BUILTIN_SPEAKER, "Speaker")
    private val buds = OutputDeviceKey(TYPE_BLUETOOTH_A2DP, "Galaxy Buds2 Pro", "AA:BB:CC:DD:EE:FF")
    private val receiver = OutputDeviceKey(TYPE_HDMI_EARC, "Denon AVR-X2800H")
    private val dac = OutputDeviceKey(TYPE_USB_DEVICE, "Topping D90")
    private val wired = OutputDeviceKey(TYPE_WIRED_HEADPHONES, "Headphones")

    @Test
    fun devicesAreKnownByTypeNameAndAStableAddress() {
        assertEquals(OutputKind.Bluetooth, buds.kind)
        assertEquals(OutputKind.Hdmi, receiver.kind)
        assertEquals(OutputKind.Usb, dac.kind)
        assertEquals(OutputKind.Wired, wired.kind)
        assertEquals(OutputKind.Speaker, speaker.kind)
        assertEquals(OutputKind.Other, OutputDeviceKey(TYPE_TELEPHONY, "").kind)
        assertTrue(LatencyProfiles.stableAddress(TYPE_BLUETOOTH_A2DP))
        assertTrue(LatencyProfiles.stableAddress(TYPE_BLE_HEADSET))
        assertFalse("a USB address names the port", LatencyProfiles.stableAddress(TYPE_USB_DEVICE))
        assertFalse(LatencyProfiles.stableAddress(TYPE_HDMI))
        assertEquals(buds.copy(address = ""), buds.withoutAddress)
    }

    @Test
    fun theLastRemovableDeviceConnectedIsTheOutput() {
        assertEquals(speaker, LatencyProfiles.active(listOf(speaker)))
        assertEquals(buds, LatencyProfiles.active(listOf(speaker, buds)))
        // Android 12 and later: the one plugged in last
        assertEquals(dac, LatencyProfiles.active(listOf(speaker, buds, dac), recent = listOf(buds, dac)))
        assertEquals(buds, LatencyProfiles.active(listOf(speaker, buds, dac), recent = listOf(dac, buds)))
        // one that went again does not count
        assertEquals(buds, LatencyProfiles.active(listOf(speaker, buds), recent = listOf(buds, dac)))
        // nothing known about the order: Android's own, Bluetooth before wired before USB before HDMI
        assertEquals(buds, LatencyProfiles.active(listOf(receiver, dac, wired, speaker, buds)))
        assertEquals(wired, LatencyProfiles.active(listOf(receiver, dac, wired, speaker)))
        assertEquals(OutputDeviceKey(TYPE_USB_HEADSET, "Buds"), LatencyProfiles.active(listOf(receiver, dac, speaker, OutputDeviceKey(TYPE_USB_HEADSET, "Buds"))))
        assertEquals(receiver, LatencyProfiles.active(listOf(speaker, receiver)))
        // what does not play media is passed over
        assertEquals(speaker, LatencyProfiles.active(listOf(OutputDeviceKey(TYPE_BUILTIN_EARPIECE, ""), OutputDeviceKey(TYPE_BLUETOOTH_SCO, "Car"), speaker, OutputDeviceKey(TYPE_REMOTE_SUBMIX, ""))))
        assertNull(LatencyProfiles.active(listOf(OutputDeviceKey(TYPE_TELEPHONY, ""))))
        assertNull(LatencyProfiles.active(emptyList()))
    }

    @Test
    fun aProfileIsFoundByItsDeviceWithOrWithoutTheAddress() {
        val profiles = listOf(
            LatencyProfile(buds, 180, measured = true, spreadMs = 4),
            LatencyProfile(receiver, 90),
            LatencyProfile(speaker, 0),
        )
        assertEquals(180, LatencyProfiles.delayMs(profiles, buds))
        assertEquals(90, LatencyProfiles.delayMs(profiles, receiver))
        assertEquals(0, LatencyProfiles.delayMs(profiles, speaker))
        assertEquals(0, LatencyProfiles.delayMs(profiles, dac))
        assertNull(LatencyProfiles.find(profiles, dac))
        assertNull(LatencyProfiles.find(profiles, null))
        // the same buds paired again under another address, or restored from another device's backup
        assertEquals(180, LatencyProfiles.delayMs(listOf(LatencyProfile(buds.withoutAddress, 180)), buds))
        assertEquals(180, LatencyProfiles.delayMs(profiles, buds.withoutAddress))
        // another pair of the same model with its own address keeps its own
        val other = buds.copy(address = "11:22:33:44:55:66")
        assertEquals(buds, LatencyProfiles.find(profiles + LatencyProfile(other, 220), buds)!!.device)
        assertEquals(220, LatencyProfiles.delayMs(profiles + LatencyProfile(other, 220), other))
        // a different type with the same name is another device
        assertNull(LatencyProfiles.find(profiles, OutputDeviceKey(TYPE_BLE_HEADSET, "Galaxy Buds2 Pro", buds.address)))
    }

    @Test
    fun theDevicesDelayGoesUnderTheFilesOwn() {
        // the file says +50 ms; the buds play 180 ms late: libVLC sends the audio 130 ms early
        assertEquals(-130_000L, LatencyProfiles.combinedDelayUs(50_000L, 180))
        assertEquals(-180_000L, LatencyProfiles.combinedDelayUs(0L, 180))
        assertEquals(50_000L, LatencyProfiles.combinedDelayUs(50_000L, 0))
        assertEquals(70_000L, LatencyProfiles.combinedDelayUs(50_000L, -20))
        // the engine's clock for the lyrics: the sound is where the engine was 180 ms ago, within the track
        assertEquals(1_820L, LatencyProfiles.clockMs(2_000L, 180))
        assertEquals(0L, LatencyProfiles.clockMs(100L, 180))
        assertEquals(2_020L, LatencyProfiles.clockMs(2_000L, -20))
        assertEquals(2_000L, LatencyProfiles.clockMs(2_000L, -20, lengthMs = 2_000L))
        assertEquals(1_820L, LatencyProfiles.clockMs(2_000L, 180, lengthMs = 2_000L))
    }

    @Test
    fun theUsersDelayAndTheOutputsAreKeptApart() {
        val mix = AudioDelayMix()
        mix.userDelayUs = 50_000L
        assertEquals(50_000L, mix.combinedUs)
        // the buds connect: libVLC's delay moves, the user's value does not
        mix.outputLatencyUs = 180_000L
        assertEquals(-130_000L, mix.combinedUs)
        assertEquals(50_000L, mix.userDelayUs)
        // the user nudges: the output's share stays underneath
        mix.userDelayUs += 10_000L
        assertEquals(-120_000L, mix.combinedUs)
        // casting: the local output's profile does not apply
        mix.casting = true
        assertEquals(60_000L, mix.combinedUs)
        mix.casting = false
        // a new media starts at the user's zero, the output's share still there
        mix.mediaStarted()
        assertEquals(0L, mix.userDelayUs)
        assertEquals(-180_000L, mix.combinedUs)
    }

    @Test
    fun theSystemsFirstReportIsNoOrder() {
        val order = OutputOrder()
        // registering: everything connected now, in the system's own order
        order.added(listOf(receiver, buds, speaker))
        assertEquals(emptyList<OutputDeviceKey>(), order.recent)
        // with no order known, Android's own: the buds before the receiver
        assertEquals(buds, LatencyProfiles.choose(null, listOf(receiver, buds, speaker), order.recent))
        // what Android says it routes to wins over the guess
        assertEquals(receiver, LatencyProfiles.choose(receiver, listOf(receiver, buds, speaker), order.recent))
        // the earpiece is never the route for media
        assertEquals(buds, LatencyProfiles.choose(OutputDeviceKey(TYPE_BUILTIN_EARPIECE, ""), listOf(receiver, buds, speaker), order.recent))
        // the DAC plugged in afterwards is the output; unplugged, the buds again
        order.added(listOf(dac))
        assertEquals(listOf(dac), order.recent)
        assertEquals(dac, LatencyProfiles.choose(null, listOf(receiver, buds, speaker, dac), order.recent))
        order.removed(listOf(dac))
        assertEquals(buds, LatencyProfiles.choose(null, listOf(receiver, buds, speaker), order.recent))
        order.clear()
        order.added(listOf(dac, buds))
        assertEquals(emptyList<OutputDeviceKey>(), order.recent)
    }

    @Test
    fun theStepperSnapsToTensWithinTheRange() {
        assertEquals(180, LatencyProfiles.snap(183))
        assertEquals(190, LatencyProfiles.snap(186))
        assertEquals(-50, LatencyProfiles.snap(-52))
        assertEquals(2_000, LatencyProfiles.snap(2_500))
        assertEquals(-2_000, LatencyProfiles.snap(-9_999))
        assertEquals(0, LatencyProfiles.snap(4))
    }
}
