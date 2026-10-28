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

/** S-408: the mouse's back button leaves a screen the forward button returns to; going elsewhere forgets it. */
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
                listOf(Destination.Music, Destination.Podcasts, Destination.Radio).forEach { destination ->
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
}
