package app.nagmedar.ui.input

import app.nagmedar.ui.theme.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.0.3 (Desktop Kit 2.3's M4): the density follows the keyboard cover's touchpad switched on and
 * off and a mouse that comes, goes or wakes up; any other change of a device leaves it alone, and
 * only devices that are switched on count.
 */
class PointerDevicesTest {

    // 3 is the keyboard cover's keyboard, 7 its touchpad, 9 a mouse
    private val switchedOn = mutableSetOf(7)
    private val devices = PointerDevices(intArrayOf(3, 7)) { it in switchedOn }

    @Test
    fun theCoversOtherChangesLeaveTheDensityAlone() {
        // a new layout, a reconfigured key map: the cover reports changes of its keyboard and its touchpad
        assertNull(devices.changed(3))
        assertNull(devices.changed(7))
        assertNull(devices.changed(7))
    }

    @Test
    fun theTouchpadSwitchedOffAndOnAgain() {
        switchedOn -= 7
        assertEquals(InputMode.Touch, devices.changed(7))
        assertNull("still off", devices.changed(7))
        switchedOn += 7
        assertEquals(InputMode.Pointer, devices.changed(7))
        assertNull("still on", devices.changed(7))
    }

    @Test
    fun aMouseComesAndGoes() {
        switchedOn += 9
        assertEquals(InputMode.Pointer, devices.added(9))
        // the touchpad is still on: the mouse leaving changes nothing
        assertNull(devices.removed(9))
        assertEquals(InputMode.Touch, devices.removed(7))
        assertNull("a keyboard leaving says nothing", devices.removed(3))
    }

    @Test
    fun aMouseThatWakesUpCountsFromThen() {
        // connected while asleep (switched off), it counts once it wakes up
        assertNull(devices.added(9))
        switchedOn += 9
        assertEquals(InputMode.Pointer, devices.changed(9))
        // the touchpad switched off: the mouse still drives the pointer
        switchedOn -= 7
        assertNull(devices.changed(7))
    }

    @Test
    fun aDeviceSwitchedOffAtTheStartDoesNotCount() {
        // the cover attached with its touchpad off: no pointer to lose, nothing changes when it leaves
        val off = PointerDevices(intArrayOf(3, 7)) { false }
        assertNull(off.removed(7))
        assertNull(off.changed(7))
        assertNull(off.changed(3))
    }
}
