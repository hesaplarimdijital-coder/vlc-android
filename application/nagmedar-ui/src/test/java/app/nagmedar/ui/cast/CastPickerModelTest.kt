package app.nagmedar.ui.cast

import app.nagmedar.playback.CastItem
import app.nagmedar.playback.RendererKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1302: the devices as the picker names them, what it shows in each state, what it says of an item. */
class CastPickerModelTest {

    private val salon = CastDevice.of("Salon TV", "chromecast")
    private val kitchen = CastDevice.of("Mutfak", "stream_out_dlna")

    @Test
    fun aDeviceIsKnownByItsNameAndKind() {
        assertEquals(salon.id, CastDevice.of("  salon tv ", "chromecast").id)
        assertEquals("Salon TV", CastDevice.of(" Salon TV ", "chromecast").name)
        assertTrue(salon.id != CastDevice.of("Salon TV", "stream_out_dlna").id)
        assertEquals(RendererKind.Chromecast, salon.kind)
        assertEquals(RendererKind.Dlna, kitchen.kind)
    }

    @Test
    fun castingTurnedOffComesFirst() {
        assertEquals(CastPickerView.Off, CastPicker.view(enabled = false, lan = true, found = listOf(salon), active = null, discovering = true, searchedMs = 0L))
    }

    @Test
    fun searchingThenWhatToCheck() {
        assertEquals(CastPickerView.Searching, CastPicker.view(true, lan = true, found = emptyList(), active = null, discovering = true, searchedMs = 3_000L))
        assertEquals(CastPickerView.Empty, CastPicker.view(true, lan = true, found = emptyList(), active = null, discovering = true, searchedMs = CastPicker.EMPTY_AFTER_MS))
    }

    @Test
    fun noLocalNetwork() {
        assertEquals(CastPickerView.NoNetwork, CastPicker.view(true, lan = false, found = emptyList(), active = null, discovering = false, searchedMs = 0L))
    }

    @Test
    fun theDevicesAsTheyComeAndGo() {
        assertEquals(CastPickerView.Devices(listOf(salon), searching = true), CastPicker.view(true, true, listOf(salon), null, discovering = true, searchedMs = 1_000L))
        assertEquals(CastPickerView.Devices(listOf(salon, kitchen), searching = true), CastPicker.view(true, true, listOf(salon, kitchen), null, true, 20_000L))
        // the Chromecast went: the DLNA renderer stays
        assertEquals(CastPickerView.Devices(listOf(kitchen), searching = false), CastPicker.view(true, true, listOf(kitchen), null, false, 20_000L))
    }

    @Test
    fun theDeviceCastToIsListedEvenBeforeItIsFoundAgain() {
        // the picker opened while casting: the discovery has not answered yet
        assertEquals(CastPickerView.Devices(listOf(salon), searching = true), CastPicker.view(true, true, emptyList(), salon, true, 0L))
        assertEquals(CastPickerView.Devices(listOf(salon, kitchen), searching = true), CastPicker.view(true, true, listOf(kitchen), salon, true, 0L))
        assertEquals(CastPickerView.Devices(listOf(kitchen, salon), searching = true), CastPicker.view(true, true, listOf(kitchen, salon), salon, true, 0L))
        // the network went while casting: the device is still the one cast to
        assertEquals(CastPickerView.Devices(listOf(salon), searching = false), CastPicker.view(true, lan = false, found = emptyList(), active = salon, discovering = false, searchedMs = 0L))
    }

    @Test
    fun whatIsSaidOfAnItem() {
        val plain = CastItem(tracker = false, dsd = false)
        assertEquals(emptyList<CastNotice>(), CastPicker.notices(plain, engineMusic = false))
        assertEquals(emptyList<CastNotice>(), CastPicker.notices(null, engineMusic = false))
        assertEquals(listOf(CastNotice.EngineSettings), CastPicker.notices(plain, engineMusic = true))
        assertEquals(listOf(CastNotice.DsdAsPcm, CastNotice.EngineSettings), CastPicker.notices(CastItem(tracker = false, dsd = true), engineMusic = true))
        // a tracker module is not cast at all: its notice alone
        assertEquals(listOf(CastNotice.TrackerHere), CastPicker.notices(CastItem(tracker = true, dsd = false), engineMusic = true))
    }

    @Test
    fun playOnIsNotOfferedForATrackerModule() {
        assertTrue(CastPicker.offered(CastItem(tracker = false, dsd = true)))
        assertTrue(CastPicker.offered(null))
        assertFalse(CastPicker.offered(CastItem(tracker = true, dsd = false)))
    }
}
