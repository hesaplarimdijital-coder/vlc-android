package app.nagmedar.ui.e2e

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.withKeysDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.theme.InputMode as NagInputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import app.nagmedar.ui.tvlab.BuiltInProfiles
import app.nagmedar.ui.tvlab.Step
import app.nagmedar.ui.tvlab.sampleFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-1403: the main flows of a DeX window with the keyboard alone, through the window's own keys:
 * the TV Lab from its shortcut, a file opened, the target chosen from its menu and a profile
 * edited; the command palette; the menu bar from Alt; Esc going back. Nothing is clicked.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
class KeyboardOnlyFlowsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val window = DexWindow()

    @Before
    fun show() {
        ShortcutRegistry.resume()
        window.picked = Uri.parse(sampleFile.uri)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = NagInputMode.Pointer) { window.Content() }
        }
        compose.waitForIdle()
        // a hardware keyboard's first key takes the window out of touch mode; injected keys do not
        compose.runOnIdle { window.inputModes.requestInputMode(InputMode.Keyboard) }
        compose.waitForIdle()
    }

    /** Keys to the window on top: a menu or a dialog when one is open. */
    private fun keys(block: KeyInjectionScope.() -> Unit) {
        compose.onAllNodes(isRoot()).onLast().performKeyInput(block)
        compose.waitForIdle()
    }

    private fun texts(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText)?.text)

    private fun focused(): List<String> = compose.onAllNodes(isFocused()).fetchSemanticsNodes().flatMap(::texts)

    /** Tab until the focus is on what says [text], as a keyboard user goes through a window. */
    private fun tabTo(text: String) {
        repeat(120) {
            if (focused().any { it == text }) return
            keys { pressKey(Key.Tab) }
        }
        fail("Tab never reached “$text”; the focus is on ${focused()}")
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > until) fail("waited for $what")
            compose.waitForIdle()
            Thread.sleep(10)
        }
    }

    @Test
    fun theTvLabFromItsShortcutToAnEditedProfile() {
        keys { withKeysDown(listOf(Key.CtrlLeft, Key.ShiftLeft)) { pressKey(Key.T) } }
        assertEquals(Destination.TvLab, window.destination)
        compose.onNodeWithText("Analiz edilecek filmi seçin").assertExists()

        // a file, from the system's picker
        tabTo("Dosya seçin")
        keys { pressKey(Key.Enter) }
        assertEquals(listOf("OpenDocument"), window.asked)
        waitFor("the analysis") { window.lab.bitrateStep == Step.Done }
        compose.onNodeWithText("2 kritik · 3 uyarı").assertExists()

        // the target, from its menu: Enter opens it on its first profile, Down and Enter take the next
        tabTo("LG OLED B4 · webOS 24 · USB")
        keys { pressKey(Key.Enter) }
        assertTrue(focused().toString(), focused().contains("LG OLED B4 · webOS 24 · USB"))
        keys { pressKey(Key.DirectionDown) }
        keys { pressKey(Key.Enter) }
        assertEquals(BuiltInProfiles.tabS11Ultra, window.lab.profile)
        compose.onNodeWithText("1 uyarı").assertExists()

        // its editor: a threshold typed, saved with Enter on Kaydet
        tabTo("Profili düzenle")
        keys { pressKey(Key.Enter) }
        compose.onNodeWithTag("tvlab-editor").assertExists()
        tabTo("Bit hızı eşiği (Mbps, 1 sn tepe)")
        compose.onNode(hasSetTextAction() and isFocused()).performTextInput("90")
        tabTo("Kaydet")
        keys { pressKey(Key.Enter) }
        compose.onNodeWithTag("tvlab-editor").assertDoesNotExist()
        assertEquals(90_000, window.lab.profile.peakKbps)
        assertTrue(window.store.saved(BuiltInProfiles.tabS11Ultra.id))
    }

    @Test
    fun theCommandPaletteFindsAScreen() {
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
        compose.onNode(hasSetTextAction()).performTextInput("tv lab")
        compose.waitForIdle()
        keys { pressKey(Key.Enter) }
        assertEquals(Destination.TvLab, window.destination)
    }

    @Test
    fun theMenuBarFromAlt() {
        // Alt alone: the menu bar has the keyboard on its first title
        keys { pressKey(Key.AltLeft) }
        // to Araçlar, the sixth; Down opens it on its first item
        repeat(5) { keys { pressKey(Key.DirectionRight) } }
        keys { pressKey(Key.DirectionDown) }
        assertTrue(focused().toString(), focused().contains("Ayarlar"))
        // Senkron is the player's: Down skips it to TV Lab
        keys { pressKey(Key.DirectionDown) }
        assertTrue(focused().toString(), focused().contains("TV Lab"))
        keys { pressKey(Key.Enter) }
        assertEquals(Destination.TvLab, window.destination)
    }

    @Test
    fun escapeGoesBackNeverOut() {
        keys { withKeysDown(listOf(Key.CtrlLeft, Key.ShiftLeft)) { pressKey(Key.T) } }
        assertEquals(Destination.TvLab, window.destination)
        keys { pressKey(Key.Escape) }
        assertEquals(Destination.Home, window.destination)
        // on the first screen Esc stays
        keys { pressKey(Key.Escape) }
        assertEquals(Destination.Home, window.destination)
    }

    @Test
    fun settingsFromTheirShortcut() {
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.Comma) } }
        assertEquals(Destination.Settings, window.destination)
    }
}
