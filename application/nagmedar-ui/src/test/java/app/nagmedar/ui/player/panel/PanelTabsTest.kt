package app.nagmedar.ui.player.panel

import org.junit.Assert.assertEquals
import org.junit.Test

/** The player panel's five tabs fit a phone's width: their room narrows before the row has to scroll. */
class PanelTabsTest {

    @Test
    fun fullRoomWhereTheTabsFit() {
        assertEquals(10f, PanelTabs.padding(words = 200f, available = 400f, count = 5, full = 10f, least = 4f), 0.001f)
    }

    @Test
    fun lessRoomSoThatTheyFit() {
        assertEquals(6f, PanelTabs.padding(words = 200f, available = 260f, count = 5, full = 10f, least = 4f), 0.001f)
    }

    @Test
    fun neverUnderTheLeast() {
        assertEquals(4f, PanelTabs.padding(words = 200f, available = 210f, count = 5, full = 10f, least = 4f), 0.001f)
    }
}
