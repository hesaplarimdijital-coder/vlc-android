package app.nagmedar.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.0.1: tabs and pages, which tab is lit, which pages have a back arrow and which hide the phone's bars. */
class NavRulesTest {

    private val phone = NavKind.BottomBar
    private val rail = NavKind.Rail
    private val desktop = NavKind.Sidebar

    /** A back stack (bottom first) as the probe the shell's navigator answers. */
    private fun stack(vararg pages: Destination): (Destination) -> Boolean = { it in pages }

    @Test
    fun thePhonesTabsAreTheBottomBar() {
        assertEquals(listOf(Destination.Music, Destination.Podcasts, Destination.Radio, Destination.More), NavRules.tabs(phone))
        assertFalse(NavRules.isTab(Destination.Settings, phone))
        // the rail has Settings at its foot; the sidebar every listed page
        assertTrue(NavRules.isTab(Destination.Settings, rail))
        assertTrue(NavRules.isTab(Destination.Playlists, desktop))
        assertFalse(NavRules.isTab(Destination.More, desktop))
    }

    @Test
    fun aTabSwitchesAndAPageOpens() {
        val onMusic = stack(Destination.Music)
        assertEquals(NavMove.Tab, NavRules.moveFor(Destination.Podcasts, Destination.Music, onMusic, phone))
        assertEquals(NavMove.Open, NavRules.moveFor(Destination.Settings, Destination.Music, onMusic, phone))
        assertEquals(NavMove.Open, NavRules.moveFor(Destination.Playlists, Destination.Music, onMusic, phone))
        // on the rail Settings is a tab of its own
        assertEquals(NavMove.Tab, NavRules.moveFor(Destination.Settings, Destination.Music, onMusic, rail))
    }

    @Test
    fun theTabOnScreenChosenAgainGoesToItsFirstPage() {
        val networkFromMore = stack(Destination.Music, Destination.More, Destination.Network)
        assertEquals(NavMove.Reselect, NavRules.moveFor(Destination.More, Destination.Network, networkFromMore, phone))
        assertEquals(NavMove.Tab, NavRules.moveFor(Destination.Music, Destination.Network, networkFromMore, phone))
        assertEquals(NavMove.Reselect, NavRules.moveFor(Destination.Music, Destination.Music, stack(Destination.Music), phone))
    }

    @Test
    fun aPageLightsTheTabWhoseStackItIsIn() {
        // the equaliser opened from "⋯": "⋯"; from the player while in Müzik: Müzik
        assertEquals(Destination.More, NavRules.highlighted(Destination.Equalizer, stack(Destination.Music, Destination.More, Destination.Equalizer), phone))
        assertEquals(Destination.Music, NavRules.highlighted(Destination.Equalizer, stack(Destination.Music, Destination.Equalizer), phone))
        // Settings' pages: still "⋯"
        assertEquals(
            Destination.More,
            NavRules.highlighted(Destination.About, stack(Destination.Music, Destination.More, Destination.Settings, Destination.About), phone),
        )
        // on the rail Settings is lit for its pages
        assertEquals(Destination.Settings, NavRules.highlighted(Destination.About, stack(Destination.Music, Destination.Settings, Destination.About), rail))
        // a tab's first page lights itself
        assertEquals(Destination.Podcasts, NavRules.highlighted(Destination.Podcasts, stack(Destination.Music, Destination.Podcasts), phone))
    }

    @Test
    fun theSearchPageLightsNothing() {
        assertNull(NavRules.highlighted(Destination.Search, stack(Destination.Music, Destination.Radio, Destination.Search), phone))
        assertNull(NavRules.highlighted(Destination.Search, stack(Destination.Music, Destination.Search), desktop))
    }

    @Test
    fun pagesThatAreNotATabsFirstHaveTheBackArrow() {
        assertFalse(NavRules.showsBack(Destination.Music, phone))
        assertFalse(NavRules.showsBack(Destination.More, phone))
        assertTrue(NavRules.showsBack(Destination.Settings, phone))
        assertTrue(NavRules.showsBack(Destination.Equalizer, phone))
        assertTrue(NavRules.showsBack(Destination.Search, phone))
        assertTrue(NavRules.showsBack(Destination.Playlists, rail))
        assertFalse(NavRules.showsBack(Destination.Settings, rail))
        // a desktop window's menu bar has no title to put an arrow beside
        assertFalse(NavRules.showsBack(Destination.About, desktop))
    }

    @Test
    fun settingsAndItsPagesHideThePhonesBars() {
        listOf(Destination.Settings, Destination.About, Destination.Guide, Destination.ShortcutCard, Destination.AllSettings).forEach {
            assertTrue("$it", NavRules.hidesBars(it, phone))
            assertFalse("$it on the rail", NavRules.hidesBars(it, rail))
        }
        // the content pages opened from "⋯" keep the bar and the mini player
        listOf(
            Destination.Playlists, Destination.Device, Destination.Network, Destination.Streams, Destination.Downloads,
            Destination.Equalizer, Destination.Search, Destination.More, Destination.Music,
        ).forEach { assertFalse("$it", NavRules.hidesBars(it, phone)) }
    }

    @Test
    fun backOnAnotherTabsFirstPageGoesToMusic() {
        assertTrue(NavRules.backToStart(Destination.Podcasts, phone))
        assertTrue(NavRules.backToStart(Destination.More, phone))
        assertTrue(NavRules.backToStart(Destination.Settings, rail))
        assertFalse("leaves the app", NavRules.backToStart(Destination.Music, phone))
        assertFalse("a page goes to the one under it", NavRules.backToStart(Destination.Settings, phone))
    }
}
