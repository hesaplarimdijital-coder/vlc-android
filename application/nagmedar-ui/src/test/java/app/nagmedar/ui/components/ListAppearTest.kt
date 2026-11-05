package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagmedarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 1.0.3 (Desktop Kit 2.3.0): the first rows of a phone's list arrive in turn; later rows, wider windows and reduced motion: they are there. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class ListAppearTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var rows by mutableIntStateOf(6)

    private fun list(tier: MotionTier) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NagmedarTheme(inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides tier) {
                    val appear = rememberListAppear()
                    Column {
                        repeat(rows) { i ->
                            Box(Modifier.listAppear(appear, i).fillMaxWidth().height(40.dp).background(Color.Red).testTag("row$i"))
                        }
                    }
                }
            }
        }
    }

    private fun top(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().top

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test
    fun theFirstRowsRiseIntoPlaceInTurnAndLaterRowsAreThere() {
        list(MotionTier.Gesture)
        frame()
        // each row starts 12 dp low and rises into place
        assertTrue("row 1 starts low: ${top("row1")}", top("row1") > 40.dp + 6.dp)
        var row0Arrived = -1
        var row3Arrived = -1
        repeat(40) { f ->
            frame()
            if (row0Arrived < 0 && top("row0").value <= 0.5f) row0Arrived = f
            if (row3Arrived < 0 && top("row3").value <= 3 * 40f + 0.5f) row3Arrived = f
        }
        assertTrue("row 0 arrived ($row0Arrived) before row 3 ($row3Arrived)", row0Arrived in 0 until row3Arrived)
        // a row added once the list has settled is simply there (two frames: the recomposer wakes with the first)
        compose.runOnIdle { rows = 7 }
        frame()
        frame()
        assertEquals(6 * 40f, top("row6").value, 0.5f)
    }

    @Test
    fun onlyTheFirstEightRowsArriveInTurn() {
        rows = 10
        list(MotionTier.Gesture)
        frame()
        assertTrue("row 7 starts low: ${top("row7")}", top("row7") > (7 * 40 + 6).dp)
        assertEquals(8 * 40f, top("row8").value, 0.5f)
        assertEquals(9 * 40f, top("row9").value, 0.5f)
    }

    @Test
    fun widerUpTheRowsAreThereAtOnce() {
        list(MotionTier.Timed)
        frame()
        assertEquals(40f, top("row1").value, 0.5f)
        assertEquals(3 * 40f, top("row3").value, 0.5f)
    }

    @Test
    fun withReducedMotionTheRowsAreThereAtOnce() {
        list(MotionTier.Reduced)
        frame()
        assertEquals(40f, top("row1").value, 0.5f)
        assertEquals(5 * 40f, top("row5").value, 0.5f)
    }
}
