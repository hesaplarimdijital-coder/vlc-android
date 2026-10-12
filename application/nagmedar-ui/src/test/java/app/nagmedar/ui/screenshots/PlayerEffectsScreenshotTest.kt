package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.ChromeVisibility
import app.nagmedar.ui.player.DesktopControls
import app.nagmedar.ui.player.FakePlayerActions
import app.nagmedar.ui.player.effects.AspectChoice
import app.nagmedar.ui.player.effects.Effect
import app.nagmedar.ui.player.effects.EffectsPanel
import app.nagmedar.ui.player.effects.PlayerEffects
import app.nagmedar.ui.player.effects.sampleEffects
import app.nagmedar.ui.player.panel.PanelDock
import app.nagmedar.ui.player.panel.PanelState
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.player.panel.PlayerPanel
import app.nagmedar.ui.player.samplePlayerState
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-603: the player with its effects panel, after the mockups' "Panel · Effects": docked beside
 * the video in a DeX window (and in English); under it in a half-screen DeX window, in two
 * columns, and on a phone, for a finger; and with this build's player, whose native effects
 * are still to come.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerEffectsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val everything = Effect.entries.toSet()

    /** This build: the classic player's aspect ratios and equaliser. */
    private val thisBuild = setOf(Effect.Aspect, Effect.Equalizer, Effect.Gain)

    private fun capture(
        name: String,
        effects: PlayerEffects,
        supported: Set<Effect>,
        input: InputMode = InputMode.Pointer,
        controls: Boolean = true,
    ) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                PlayerWithPanel(PanelTab.Effects, controls) { columns ->
                    EffectsPanel(effects, supported, remembered = true, onChange = {}, onRemember = {}, onMakeDefault = {}, onEqualizerWindow = {}, columns = columns)
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dex() = capture("player-effects-dex", sampleEffects, everything)

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun dexEnglish() = capture("player-effects-dex-en", sampleEffects, everything)

    @Test @Config(qualifiers = "tr-w728dp-h880dp-mdpi")
    fun halfWindow() = capture("player-effects-half", sampleEffects, everything)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("player-effects-phone", sampleEffects, everything, input = InputMode.Touch, controls = false)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun thisBuildsPlayer() = capture("player-effects-pending", PlayerEffects(aspect = AspectChoice.Wide, gainPercent = 130), thisBuild)
}

/**
 * The player as the app lays it out with its panel open on [tab]: the video and its controls beside
 * the panel in a wide window, above it in a narrow one ([PanelDock]); [content] gets the columns.
 */
@Composable
internal fun PlayerWithPanel(
    tab: PanelTab,
    controls: Boolean,
    overVideo: @Composable BoxScope.() -> Unit = {},
    content: @Composable (columns: Int) -> Unit,
) {
    val panel = remember { PanelState(tab, open = true) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // the window's size, read here: the rows and columns below have their own scopes
        val width = maxWidth
        val height = maxHeight
        val dock = PanelDock.of(width, height)
        val video: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier, contentAlignment = Alignment.Center) {
                SampleVideo(Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                if (controls) {
                    val visibility = remember {
                        ChromeVisibility(active = true).apply {
                            onMouseMoved()
                            overBars = true
                        }
                    }
                    DesktopControls(samplePlayerState.copy(effectsOpen = tab == PanelTab.Effects), FakePlayerActions(), visibility, previews = null)
                }
                overVideo()
            }
        }
        val tools: @Composable (Modifier, Int) -> Unit = { modifier, columns ->
            PlayerPanel(panel, listOf(PanelTab.Sync, PanelTab.Effects, PanelTab.Info), dock, modifier) { content(columns) }
        }
        if (dock == PanelDock.Side) {
            Row(Modifier.fillMaxSize()) {
                video(Modifier.weight(1f).fillMaxHeight())
                tools(Modifier.width(PanelDock.SideWidth).fillMaxHeight(), 1)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                video(Modifier.weight(1f).fillMaxWidth())
                tools(Modifier.height(PanelDock.bottomHeight(height)).fillMaxWidth(), PanelDock.bottomColumns(width))
            }
        }
    }
}
