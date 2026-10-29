package app.nagmedar.ui.shell

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * S-408: the mouse's back button leaves a screen the forward button returns to; going elsewhere forgets it.
 * 1.0.1: tabs keep their own pages, a page opened goes on its tab's stack, the tab chosen again goes to its
 * first page, Back from another tab's first page goes to Müzik.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellNavigatorTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navigator: ShellNavigator
    private lateinit var navController: NavHostController

    private fun shell() {
        compose.setContent {
            val controller = rememberNavController()
            navController = controller
            navigator = ShellNavigator(controller)
            NavHost(controller, startDestination = Destination.Music.route) {
                Destination.entries.forEach { destination ->
                    composable(destination.route) { Text(destination.name) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun on(destination: Destination) = assertEquals(destination.route, compose.runOnIdle { navigator.currentRoute })

    @Test
    fun backThenForwardReturns() {
        shell()
        compose.runOnIdle { navigator.navigate(Destination.Podcasts) }
        on(Destination.Podcasts)
        compose.runOnIdle { assertTrue(navigator.back()) }
        on(Destination.Music)
        compose.runOnIdle { assertTrue(navigator.forward()) }
        on(Destination.Podcasts)
        // nowhere further
        compose.runOnIdle { assertFalse(navigator.forward()) }
    }

    @Test
    fun goingElsewhereForgetsForward() {
        shell()
        compose.runOnIdle { navigator.navigate(Destination.Podcasts) }
        compose.runOnIdle { navigator.back() }
        compose.runOnIdle { navigator.navigate(Destination.Radio) }
        compose.runOnIdle { assertFalse(navigator.forward()) }
        on(Destination.Radio)
        // a back the system made is remembered too
        compose.runOnIdle {
            val from = navigator.currentRoute
            navController.popBackStack()
            navigator.wentBack(from)
        }
        on(Destination.Music)
        compose.runOnIdle { assertTrue(navigator.forward()) }
        on(Destination.Radio)
    }

    @Test
    fun aPageOpensOnItsTabsStackAndBackReturnsToTheOneThatOpenedIt() {
        shell()
        compose.runOnIdle { navigator.selectTab(Destination.More) }
        compose.runOnIdle { navigator.open(Destination.Settings) }
        on(Destination.Settings)
        compose.runOnIdle {
            assertEquals(NavMove.Open, navigator.lastMove)
            assertEquals(Destination.More, navigator.tab)
        }
        compose.runOnIdle { navigator.open(Destination.About) }
        compose.runOnIdle { assertTrue(navigator.back()) }
        on(Destination.Settings)
        compose.runOnIdle { assertTrue(navigator.back()) }
        on(Destination.More)
        // the first page of another tab: Back goes to Müzik, and there no further
        compose.runOnIdle { assertTrue(navigator.back()) }
        on(Destination.Music)
        compose.runOnIdle { assertFalse(navigator.back()) }
        on(Destination.Music)
    }

    @Test
    fun eachTabKeepsItsPages() {
        shell()
        compose.runOnIdle { navigator.selectTab(Destination.More) }
        compose.runOnIdle { navigator.open(Destination.Network) }
        compose.runOnIdle { navigator.selectTab(Destination.Podcasts) }
        on(Destination.Podcasts)
        // back on "⋯": the page it had open
        compose.runOnIdle { navigator.selectTab(Destination.More) }
        on(Destination.Network)
        compose.runOnIdle { assertEquals(NavMove.Tab, navigator.lastMove) }
        // chosen again: its first page (Android's reselect)
        compose.runOnIdle { navigator.selectTab(Destination.More) }
        on(Destination.More)
        compose.runOnIdle { assertEquals(NavMove.Reselect, navigator.lastMove) }
    }

    @Test
    fun theEqualizerFromThePlayerStaysInMusic() {
        shell()
        compose.runOnIdle { navigator.open(Destination.Equalizer) }
        on(Destination.Equalizer)
        compose.runOnIdle { assertEquals(Destination.Music, navigator.tab) }
        compose.runOnIdle { navigator.selectTab(Destination.Radio) }
        compose.runOnIdle { navigator.selectTab(Destination.Music) }
        on(Destination.Equalizer)
        compose.runOnIdle { navigator.selectTab(Destination.Music) }
        on(Destination.Music)
    }

    @Test
    fun aPageOpenedAgainIsReturnedTo() {
        shell()
        compose.runOnIdle { navigator.open(Destination.Settings) }
        compose.runOnIdle { navigator.open(Destination.Guide) }
        compose.runOnIdle { navigator.open(Destination.ShortcutCard) }
        // the card leads to the guide, which is under it already: back to it, not a third page
        compose.runOnIdle { navigator.open(Destination.Guide) }
        on(Destination.Guide)
        compose.runOnIdle { assertEquals(NavMove.Back, navigator.lastMove) }
        compose.runOnIdle { assertTrue(navigator.back()) }
        on(Destination.Settings)
    }

    @Test
    fun navigatePicksTabOrPageByTheWindow() {
        shell()
        // a phone: Settings is a page, opened over Müzik
        compose.runOnIdle { navigator.navigate(Destination.Settings) }
        on(Destination.Settings)
        compose.runOnIdle { assertEquals(Destination.Music, navigator.tab) }
        // a desktop window: Settings is a tab of the sidebar
        compose.runOnIdle {
            navigator.navKind = NavKind.Sidebar
            navigator.navigate(Destination.Playlists)
        }
        on(Destination.Playlists)
        compose.runOnIdle {
            assertEquals(Destination.Playlists, navigator.tab)
            assertEquals(NavMove.Tab, navigator.lastMove)
        }
    }
}
