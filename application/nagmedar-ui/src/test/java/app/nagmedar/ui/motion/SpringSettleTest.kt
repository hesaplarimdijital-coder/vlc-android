package app.nagmedar.ui.motion

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.ExpandablePlayer
import app.nagmedar.ui.components.ExpandablePlayerState
import app.nagmedar.ui.components.rememberExpandablePlayerState
import app.nagmedar.ui.shell.DetailMotionState
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.rememberDetailMotion
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.3 (Desktop Kit 2.3.0), measured in the phone's shell with the frame clock driven by hand: the player's open
 * and close settle on the gesture spring within about 400 ms (the spring stops half a pixel from its place, no
 * longer a hundredth of a pixel after a long invisible tail), a list's detail within about 250 ms; and with
 * "Remove animations" they jump to their end on the first frame, Back's too (the player's springs did not take
 * the motion tier before).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class SpringSettleTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var player: ExpandablePlayerState
    private var scope: CoroutineScope? = null

    private fun playerShell(reduced: Boolean) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch, reduceMotion = reduced) {
                player = rememberExpandablePlayerState()
                scope = rememberCoroutineScope()
                TestShell(
                    WidthClass.Compact,
                    player = { kind ->
                        if (kind == NavKind.BottomBar) {
                            ExpandablePlayer(
                                player,
                                mini = { Box(Modifier.fillMaxWidth().height(72.dp)) { Text("Sabaha Kadar") } },
                                full = { Box(Modifier.fillMaxSize().testTag("full-player")) { Text("Ada Karan") } },
                                modifier = Modifier.testTag("mini"),
                            )
                        }
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    /** Something of the window waits for the next frame: an animation runs. */
    private fun moving(): Boolean = compose.runOnIdle { Recomposer.runningRecomposers.value.any { it.hasPendingWork } }

    /**
     * Starts [change] with the clock stopped and advances it a frame at a time until nothing waits for a frame any
     * more; returns the milliseconds that took, and [watch]'s value after each frame.
     */
    private fun <T> settle(watch: () -> T, change: () -> Unit): Pair<Long, List<T>> {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { change() }
        val values = mutableListOf<T>()
        var ms = 0L
        do {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            ms += 16
            values += compose.runOnIdle { watch() }
        } while (moving() && ms < 3_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return ms to values
    }

    @Test
    fun thePlayerOpensAndClosesWithoutATail() {
        playerShell(reduced = false)
        val (open, opening) = settle({ player.progress }) { scope?.launch { player.expand() } }
        assertTrue(player.isExpanded)
        assertEquals(1f, opening.last())
        assertTrue("the open took several frames: $opening", opening.count { it in 0.01f..0.99f } >= 5)
        // half a pixel from its place it has arrived: some 400 ms over the phone's height, no longer 600 and more
        assertTrue("the open ran $open ms", open <= 480)
        val (close, closing) = settle({ player.progress }) { scope?.launch { player.collapse() } }
        assertTrue(!player.isExpanded)
        assertEquals(0f, closing.last())
        assertTrue("the close ran $close ms", close <= 480)
    }

    /** With "Remove animations" no frame shows the player between its two places (the frame the change starts in aside). */
    @Test
    fun withReducedMotionThePlayerJumps() {
        playerShell(reduced = true)
        fun between(values: List<Float>) = values.filter { it > 0f && it < 1f }
        val (_, opening) = settle({ player.progress }) { scope?.launch { player.expand() } }
        assertTrue(player.isExpanded)
        assertEquals("the open: $opening", emptyList<Float>(), between(opening))
        val (_, closing) = settle({ player.progress }) { scope?.launch { player.collapse() } }
        assertTrue(!player.isExpanded)
        assertEquals("the close: $closing", emptyList<Float>(), between(closing))
        // Back: the same, with no shrink left to ease back
        compose.runOnIdle { scope?.launch { player.expand() } }
        compose.waitForIdle()
        val (back, closedByBack) = settle({ player.progress }) { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertTrue(!player.isExpanded)
        assertEquals("Back: $closedByBack", emptyList<Float>(), between(closedByBack))
        // no 300 ms ease of the shrink left running behind it (it ran with reduced motion before)
        assertTrue("Back's frames went on for $back ms", back <= 80)
    }

    @Test
    fun aDetailArrivesOnThePhoneAndJumpsWithReducedMotion() {
        lateinit var detail: DetailMotionState
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                detail = rememberDetailMotion(open = false)
                scope = rememberCoroutineScope()
            }
        }
        compose.waitForIdle()
        val (open, opening) = settle({ detail.progress.value }) { scope?.launch { detail.show(open = true, NagMotion.Full, MotionTier.Gesture) } }
        assertEquals(1f, opening.last())
        assertTrue("the detail took several frames: $opening", opening.count { it in 0.01f..0.99f } >= 5)
        // the spring arrives a fiftieth from its end (DetailMotion.ARRIVED): some 220 ms, not 250 at a hundredth or 330 before
        assertTrue("the detail arrived in $open ms", open <= 256)
        val (_, closing) = settle({ detail.progress.value }) { scope?.launch { detail.show(open = false, NagMotion.Reduced, MotionTier.Reduced) } }
        assertTrue("with reduced motion no frame between open and closed: $closing", closing.all { it == 0f || it == 1f })
        assertEquals(0f, closing.last())
    }
}
