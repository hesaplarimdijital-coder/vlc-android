package app.nagmedar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.input.KeyChord
import app.nagmedar.ui.input.PhysicalKeys
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.TestLayouts
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-410: the keyboard map draws the keys as the keyboard prints them and the shortcuts on them,
 * Ctrl shows its layer, two shortcuts on a key are a conflict, and a key pressed on the keyboard
 * (Türkçe Q here) says where it is and what it does without doing it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
class KeyboardMapTest {

    @get:Rule
    val compose = createComposeRule()

    /** Keys the tests press come from the virtual keyboard's id. */
    private val injected = -1

    @After
    fun reset() {
        PhysicalKeys.reset()
        ShortcutRegistry.resume()
    }

    private fun map(overrides: Map<ShortcutId, KeyChord?> = emptyMap()) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Column(Modifier.padding(24.dp)) { KeyboardMap(tableOf(overrides)) }
            }
        }
        compose.waitForIdle()
    }

    private val detail get() = compose.onNodeWithTag("keyboard-map-detail")

    @Test
    fun aTurkishKeyboardsKeysAndTheirShortcuts() {
        TestLayouts.use(injected to TestLayouts.turkishQ)
        map()
        compose.onNodeWithText("Klavye: Türkçe Q · tuşlar üzerlerinde yazdığı gibi").assertExists()
        // "Ğ" where a US keyboard has "[", Yavaşlat on it; "Ö" where it has ",", free alone
        compose.onNodeWithContentDescription("Ğ · Yavaşlat").assertExists()
        compose.onNodeWithContentDescription("- · Normal hız").assertExists()
        compose.onNodeWithContentDescription("Ö · boş").assertExists()
        compose.onNodeWithContentDescription("Boşluk · Oynat / duraklat").assertExists()
        // Ctrl shows its layer: Ctrl+Ö is Ayarlar
        compose.onAllNodesWithContentDescription("Ctrl")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Ctrl+Ö · Ayarlar").assertExists()
        compose.onNodeWithContentDescription("Ctrl+. · Klavye kısayolları").assertExists()
    }

    @Test
    fun aKeyPressedSaysWhereItIsAndWhatItDoes() {
        TestLayouts.use(injected to TestLayouts.turkishQ)
        map()
        detail.assertTextEquals("Bir tuşa tıklayın ya da basın")
        // a click chooses a key, and gives the map the keyboard
        compose.onNodeWithContentDescription("Ğ · Yavaşlat").performClick()
        compose.waitForIdle()
        detail.assertTextEquals("Ğ · Yavaşlat")
        compose.onNodeWithTag("keyboard-map").assertIsFocused()
        // while the map has the keys they are not shortcuts
        assertTrue(ShortcutRegistry.recording)
        // Türkçe Q's "-" is the key at the US "=": Normal hız
        compose.onNodeWithTag("keyboard-map").performKeyInput { pressKey(Key.Minus) }
        compose.waitForIdle()
        detail.assertTextEquals("- · Normal hız")
        // held Ctrl shows its layer; the key giving "=" is at the US ","
        compose.onNodeWithTag("keyboard-map").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Equals) } }
        compose.waitForIdle()
        detail.assertTextEquals("Ctrl+Ö · Ayarlar")
    }

    @Test
    fun twoShortcutsOnAKeyAreAConflict() {
        TestLayouts.use(injected to TestLayouts.turkishQ)
        map(mapOf(ShortcutId.Stop to KeyChord(Key.N)))
        compose.onNodeWithContentDescription("N · çakışma: Durdur, Sonraki").assertExists()
        compose.onNodeWithContentDescription("S · boş").assertExists()
    }

    @Test
    fun byTouchTheChipsChooseTheLayerAndTheKeysAreNoTargets() {
        TestLayouts.use(injected to TestLayouts.turkishQ)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                Column(Modifier.padding(24.dp)) { KeyboardMap(tableOf(emptyMap())) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Ğ · Yavaşlat").assert(hasClickAction().not())
        compose.onNode(hasText("Ctrl") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Ctrl+Ö · Ayarlar").assertExists()
        compose.onNodeWithTag("keyboard-map-detail").assertDoesNotExist()
    }

    @Test
    fun withoutAKeyboardTheKeysAreAUsKeyboards() {
        TestLayouts.use(keyboard = null)
        map()
        compose.onNodeWithText("Bağlı klavye yok · tuşlar bir ABD klavyesindeki gibi").assertExists()
        compose.onNodeWithContentDescription("[ · Yavaşlat").assertExists()
        compose.onNodeWithContentDescription("Tab · Tab, Enter ve Esc pencerede gezinmek içindir; tek başlarına kısayol olamaz.").assertExists()
        assertFalse(ShortcutRegistry.recording)
    }
}
