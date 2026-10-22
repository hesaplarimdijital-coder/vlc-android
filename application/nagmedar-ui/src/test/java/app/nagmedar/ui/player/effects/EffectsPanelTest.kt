package app.nagmedar.ui.player.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.panel.PanelDock
import app.nagmedar.ui.player.panel.PanelState
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.player.panel.PlayerPanel
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
 * S-603: the effects panel changes the film's effects at once, each control its own; "Save for
 * this file" and "Make default" reach the controller; what the player cannot do yet is faded
 * and says why; the panel docks beside a wide window's video and under a narrow one's.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class EffectsPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private var effects by mutableStateOf(PlayerEffects())
    private var remembered by mutableStateOf(false)
    private var madeDefault = 0
    private var equalizerWindow = 0

    private fun panel(supported: Set<Effect> = Effect.entries.toSet()) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.width(360.dp).height(885.dp)) {
                    EffectsPanel(
                        effects = effects,
                        supported = supported,
                        remembered = remembered,
                        onChange = { change -> effects = change(effects) },
                        onRemember = { remembered = it },
                        onMakeDefault = { madeDefault++ },
                        onEqualizerWindow = { equalizerWindow++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun eachControlChangesItsEffect() {
        panel()
        compose.onNode(hasText("2,39:1") and hasClickAction()).performClick()
        assertEquals(AspectChoice.Scope, effects.aspect)
        compose.onNodeWithText("Siyah bantları algıla").performClick()
        assertTrue(effects.autoCrop)
        compose.onNodeWithContentDescription("90° sağa döndür").performClick()
        compose.onNodeWithContentDescription("Yatay çevir").performClick()
        assertEquals(VideoTransform(rotation = 90, flipHorizontal = true), effects.transform)
        compose.onNodeWithText("90° döndürüldü").assertExists()
        // a slider set as TalkBack sets it: the middle of saturation is 0, its end +100
        compose.onNode(hasContentDescription("Doygunluk") and hasSetProgress()).performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        assertEquals(100, effects.color.saturation)
        compose.onNodeWithText("+100").assertExists()
        compose.onNodeWithText("Sıfırla").performClick()
        assertTrue(effects.color.isNeutral)
        compose.onNodeWithText("Bant giderme (debanding)").performScrollTo().performClick()
        assertTrue(effects.debanding)
        compose.onNode(hasContentDescription("Yükseltme") and hasSetProgress()).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        assertEquals(150, effects.gainPercent)
        compose.onNodeWithText("%150").assertExists()
    }

    @Test
    fun listsOpenAndChoose() {
        panel()
        compose.onNodeWithContentDescription("Ölçekleyici").performScrollTo().performClick()
        compose.onNode(hasText("EWA Lanczos") and hasClickAction()).performClick()
        assertEquals(Scaler.EwaLanczos, effects.scaler)
        compose.onNodeWithContentDescription("HDR çıkışı").performClick()
        compose.onNode(hasText("Ekrana geçir") and hasClickAction()).performClick()
        assertEquals(HdrOutput.Passthrough, effects.hdrOutput)
        // passed to the screen as HDR, there is nothing to tone-map
        compose.onNodeWithText("Geçişte kullanılmaz").assertExists()
        compose.onNodeWithContentDescription("Ekolayzer").performScrollTo().performClick()
        compose.onNode(hasText("Rock") and hasClickAction()).performClick()
        assertEquals("rock", effects.equalizer)
        compose.onNodeWithContentDescription("Ekolayzer").performClick()
        compose.onNode(hasText("Oynatıcının ayarı") and hasClickAction()).performClick()
        assertEquals(null, effects.equalizer)
        compose.onNodeWithTag("effects-equalizer-window").performScrollTo().performClick()
        assertEquals(1, equalizerWindow)
    }

    @Test
    fun saveForThisFileAndMakeDefault() {
        panel()
        compose.onNodeWithTag("effects-remember").assertIsOff().performClick()
        assertTrue(remembered)
        compose.onNodeWithTag("effects-remember").assertIsOn()
        compose.onNodeWithText("Varsayılan yap").performClick()
        assertEquals(1, madeDefault)
        compose.onNodeWithText("Varsayılan yapıldı").assertExists()
    }

    @Test
    fun whatThePlayerCannotDoYetIsFadedAndSaysWhy() {
        panel(supported = setOf(Effect.Aspect, Effect.Equalizer, Effect.Gain))
        assertTrue(compose.onAllNodesWithTag("effects-pending").fetchSemanticsNodes().size >= 3)
        // faded controls do nothing
        compose.onNodeWithContentDescription("Yatay çevir").performClick()
        compose.onNodeWithText("Siyah bantları algıla").performClick()
        assertFalse(effects.autoCrop)
        assertFalse(effects.transform.flipHorizontal)
        // what works, works
        compose.onNode(hasText("Doldur") and hasClickAction()).performClick()
        assertEquals(AspectChoice.Fill, effects.aspect)
    }

    @Test
    fun theTabsAndTheCloseButton() {
        val state = PanelState(open = true)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.width(360.dp).height(600.dp)) {
                    PlayerPanel(state, listOf(PanelTab.Effects), PanelDock.Side) { tab -> if (tab == PanelTab.Effects) Box(Modifier) }
                }
            }
        }
        compose.onNodeWithText("Efektler").assertExists()
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        assertFalse(state.open)
        state.toggle(PanelTab.Effects)
        assertTrue(state.shown)
        state.suspended = true
        assertFalse("picture in picture puts it away", state.shown)
        state.suspended = false
        state.toggle(PanelTab.Effects)
        assertFalse(state.open)
    }

    @Test
    fun thePanelDocksBesideAWideWindowsVideoAndUnderANarrowOnes() {
        assertEquals(PanelDock.Side, PanelDock.of(1280.dp, 800.dp))
        assertEquals("DeX's half window", PanelDock.Bottom, PanelDock.of(728.dp, 880.dp))
        assertEquals(2, PanelDock.bottomColumns(728.dp))
        assertEquals("a phone", PanelDock.Bottom, PanelDock.of(411.dp, 891.dp))
        assertEquals(1, PanelDock.bottomColumns(411.dp))
        assertEquals("a phone on its side", PanelDock.Side, PanelDock.of(891.dp, 411.dp))
        assertEquals("a tablet upright", PanelDock.Bottom, PanelDock.of(800.dp, 1280.dp))
        assertEquals(400.95f, PanelDock.bottomHeight(891.dp).value, 0.01f)
        assertEquals(260f, PanelDock.bottomHeight(400.dp).value, 0.01f)
    }

    private fun hasSetProgress() = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)
}
