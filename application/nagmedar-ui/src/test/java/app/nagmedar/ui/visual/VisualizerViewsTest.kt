package app.nagmedar.ui.visual

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-611: the pane chooses the kind and opens the full-screen view; it says why it cannot draw,
 * with the way out (the permission, Battery Saver); Settings › Visualiser keeps its choices and
 * shows the battery warning; the settings come back as written.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class VisualizerViewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var kind by mutableStateOf(VisualizerKind.Spectrum)

    private fun pane(state: VisualizerState) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                VisualizerPane(
                    kind = kind,
                    state = state,
                    onKind = { kind = it; calls += "kind:$it" },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    onFullScreen = { calls += "full" },
                    onAllow = { calls += "allow" },
                    onRunOnBatterySaver = { calls += "saver" },
                )
            }
        }
    }

    @Test
    fun thePaneChoosesTheKindAndGoesFullScreen() {
        pane(VisualizerState())
        compose.onNode(hasText("VU metre") and hasClickAction()).performClick()
        compose.onNode(hasText("Osiloskop") and hasClickAction()).performClick()
        compose.onNodeWithTag("visualizer-full-screen").performClick()
        assertEquals(listOf("kind:Vu", "kind:Scope", "full"), calls)
        compose.onNodeWithTag("visualizer-canvas").assertExists()
    }

    @Test
    fun itSaysWhyItCannotDraw() {
        val state = VisualizerState()
        pane(state)
        compose.onNodeWithText("İzin ver").assertDoesNotExist()
        state.problem = ScopeProblem.NeedsPermission
        compose.waitForIdle()
        compose.onNodeWithText("İzin ver").performClick()
        state.problem = null
        state.pause = VisualizerPause.BatterySaver
        compose.waitForIdle()
        compose.onNodeWithText("Pil tasarrufu açık: görselleştirici duraklatıldı.").assertExists()
        compose.onNodeWithText("Yine de çalıştır").performClick()
        state.pause = null
        state.problem = ScopeProblem.Unavailable
        compose.waitForIdle()
        compose.onNodeWithText("Bu cihaz sesinin görselleştirilmesine izin vermiyor.").assertExists()
        assertEquals(listOf("allow", "saver"), calls)
    }

    @Test
    fun theFullScreenViewClosesAndChooses() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Pointer) {
                VisualizerFullScreen(
                    title = "Neva Kâr",
                    subtitle = "Kadri Erdem",
                    kind = kind,
                    state = VisualizerState(),
                    cost = "≈ 30 fps’de saatte pilin %0,6 kadarı",
                    onKind = { kind = it; calls += "kind:$it" },
                    onClose = { calls += "close" },
                )
            }
        }
        compose.onNodeWithText("Neva Kâr").assertExists()
        compose.onNode(hasText("Spektrum") and hasClickAction()).assertExists()
        compose.onNode(hasText("VU metre") and hasClickAction()).performClick()
        compose.onNodeWithTag("visualizer-close").performClick()
        assertEquals(listOf("kind:Vu", "close"), calls)
    }

    @Test
    fun theSettingsKeepTheirChoices() {
        val prefs = NagmedarPreferences(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("visualizer-test", Context.MODE_PRIVATE))
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) { VisualizerSection(prefs, batteryWh = 44.66) }
        }
        compose.onNodeWithTag("visualizer-battery-warning").assertExists()
        compose.onNodeWithText("projectM bu sürümde yok").assertExists()
        compose.onAllNodes(hasText("Açık") and hasClickAction())[0].performClick()
        compose.onNode(hasText("Osiloskop") and hasClickAction()).performClick()
        compose.onNode(hasText("60 fps") and hasClickAction()).performClick()
        compose.onNode(hasText("Çalışsın") and hasClickAction()).performClick()
        compose.waitForIdle()
        val settings = prefs.visualizer
        assertTrue(settings.music)
        assertFalse(settings.video)
        assertEquals(VisualizerKind.Scope, settings.kind)
        assertEquals(VisualizerFps.Fps60, settings.fps)
        assertTrue(settings.onBatterySaver)
        // the estimate follows the choice: 245 mW of 44.66 Wh
        compose.onNodeWithText("≈ 60 fps'de saatte pilin %0,5 kadarı").assertExists()
        // written, they come back
        val again = NagmedarPreferences(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("visualizer-test", Context.MODE_PRIVATE))
        assertEquals(settings, again.visualizer)
    }
}
