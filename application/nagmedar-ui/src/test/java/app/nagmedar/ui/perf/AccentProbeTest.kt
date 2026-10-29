package app.nagmedar.ui.perf

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.SamplePlayerBar
import app.nagmedar.ui.samplePlayerInfo
import app.nagmedar.ui.playback.PlayPauseCircle
import app.nagmedar.ui.playback.ProgressTimeline
import app.nagmedar.ui.playback.TimelineStyle
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.theme.AccentTones
import app.nagmedar.ui.theme.ArtworkAccent
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import androidx.compose.ui.unit.dp
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Temporary probe (1.0.2): which part of the phone's bar keeps a new accent from reaching a test's waitUntil. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class AccentProbeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun probe(name: String, content: @Composable () -> Unit) {
        val first = Color(0xFF2E7D32)
        val second = Color(0xFFC0392B)
        var seed by mutableStateOf<Color?>(first)
        var accent = Color.Unspecified
        var outer = 0
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                ArtworkAccent(seed) {
                    val colors = NagTheme.colors
                    SideEffect {
                        outer++
                        accent = colors.accent
                    }
                    content()
                }
            }
        }
        val one = runCatching { compose.waitUntil(3_000) { accent == AccentTones.fromSeed(first).base } }.isSuccess
        val idle = runCatching { compose.waitForIdle() }.isSuccess
        val before = outer
        seed = second
        val two = runCatching { compose.waitUntil(3_000) { accent == AccentTones.fromSeed(second).base } }.isSuccess
        val after = outer
        val idle2 = runCatching { compose.waitForIdle() }.isSuccess
        val three = accent == AccentTones.fromSeed(second).base
        fail("PROBE $name: one=$one idle=$idle two=$two idle2=$idle2 afterIdle=$three outer=$before/$after/$outer")
    }

    @Test fun nothing() = probe("nothing") { Box(Modifier) }

    @Test fun rail() = probe("rail") { SamplePlayerBar(NavKind.Rail) }

    @Test fun sidebar() = probe("sidebar") { SamplePlayerBar(NavKind.Sidebar) }

    @Test fun compact() = probe("compact") { SamplePlayerBar(NavKind.BottomBar) }

    @Test fun compactPaused() = probe("compactPaused") { SamplePlayerBar(NavKind.BottomBar, samplePlayerInfo.copy(playing = false)) }

    @Test fun boxWithConstraints() = probe("bwc") { BoxWithConstraints { Text("x") } }

    @Test fun play() = probe("play") { PlayPauseCircle(true, {}, 48.dp, 22.dp) }

    @Test fun timeline() = probe("timeline") { ProgressTimeline({ 0.42f }, 258_000, {}, playing = true, style = TimelineStyle.Bar) }

    @Test fun timelinePaused() = probe("timelinePaused") { ProgressTimeline({ 0.42f }, 258_000, {}, playing = false, style = TimelineStyle.Bar) }
}
