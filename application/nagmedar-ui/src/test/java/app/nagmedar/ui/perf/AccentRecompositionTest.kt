package app.nagmedar.ui.perf

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagPalette
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 1.0.3 (Desktop Kit 2.3.0): an accent or theme change cross-fades the colours over 150 ms, and only what reads
 * them follows the fade. The colours were a static composition local, so each of the fade's nine frames
 * recomposed the whole window (the screens, the lists, the player); now what does not read them is recomposed
 * once at most, on the frame the change starts (Material's colour scheme, a static local of Material's, takes the
 * new palette then, at once).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class AccentRecompositionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var accent by mutableStateOf(Accent.Blue)

    @Test
    fun onlyWhatReadsTheColoursFollowsTheCrossFade() {
        val seen = mutableListOf<Color>()
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, accent = accent, inputMode = InputMode.Touch, reduceMotion = false) {
                Column {
                    Plain()
                    Reader { seen += it }
                    Material()
                }
            }
        }
        compose.waitForIdle()
        val plainBefore = Counts.plain
        val readerBefore = Counts.reader
        val materialBefore = Counts.material
        seen.clear()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { accent = Accent.Coral }
        // frame by frame (a run of frames without an idle in between leaves Robolectric's recomposer asleep)
        repeat(15) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val coral = NagPalette.colors(ThemeMode.Dark, systemDark = true, accent = Accent.Coral).accent
        val blue = NagPalette.colors(ThemeMode.Dark, systemDark = true, accent = Accent.Blue).accent
        assertTrue("the reader followed the cross-fade (${Counts.reader - readerBefore} compositions)", Counts.reader - readerBefore >= 3)
        assertTrue("on its way it showed colours between the two: $seen", seen.any { it != blue && it != coral })
        assertEquals("it ends on the new accent", coral, seen.last())
        // once, where the change starts, not on each of the fade's frames (it was nine times)
        assertTrue("what does not read the colours was composed ${Counts.plain - plainBefore} times", Counts.plain - plainBefore <= 1)
        // Material's colours take the new accent at once: one composition, not one per frame
        assertTrue("what reads Material's scheme was composed ${Counts.material - materialBefore} times", Counts.material - materialBefore <= 1)
        assertEquals(coral, Counts.materialPrimary)
    }

    @Test
    fun withReducedMotionTheNewAccentIsThereAtOnce() {
        val seen = mutableListOf<Color>()
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, accent = accent, inputMode = InputMode.Touch, reduceMotion = true) {
                Reader { seen += it }
            }
        }
        compose.waitForIdle()
        seen.clear()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { accent = Accent.Green }
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val blue = NagPalette.colors(ThemeMode.Dark, systemDark = true, accent = Accent.Blue).accent
        val green = NagPalette.colors(ThemeMode.Dark, systemDark = true, accent = Accent.Green).accent
        // the old accent may stay for the frame the change starts in, never a colour between the two
        assertTrue("no colour between the two: $seen", seen.all { it == blue || it == green })
        assertEquals(green, seen.last())
    }
}

private object Counts {
    var plain = 0
    var reader = 0
    var material = 0
    var materialPrimary = Color.Unspecified
}

/** Reads no colour: skipped while the colours change. */
@Composable
private fun Plain() {
    SideEffect { Counts.plain++ }
    Box(Modifier.size(10.dp))
}

/** Reads the colours: follows the cross-fade. */
@Composable
private fun Reader(onAccent: (Color) -> Unit) {
    val accent = NagTheme.colors.accent
    SideEffect {
        Counts.reader++
        onAccent(accent)
    }
    Box(Modifier.size(10.dp))
}

/** Reads Material's colour scheme, as a Material component does. */
@Composable
private fun Material() {
    val primary = MaterialTheme.colorScheme.primary
    SideEffect {
        Counts.material++
        Counts.materialPrimary = primary
    }
    Box(Modifier.size(10.dp))
}
