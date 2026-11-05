package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.playback.FakeQueue
import app.nagmedar.ui.playback.QueuePage
import app.nagmedar.ui.playback.QueuePanel
import app.nagmedar.ui.search.CommandPalette
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * 1.0.3 (Desktop Kit 2.3), keyboard and TalkBack users on DeX: the keyboard's focus goes back where it
 * was when a sheet, a page over the whole window or the command palette closes, so Tab goes on from
 * there instead of from the window's first control.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
class FocusReturnTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var inputModes: InputModeManager
    private var open by mutableStateOf(false)

    /** The real shell with two buttons on its screen, [layer] beside them and [overlay] over the screen; the keyboard drives it. */
    private fun window(widthClass: WidthClass, overlay: @Composable BoxScope.() -> Unit = {}, layer: @Composable () -> Unit = {}) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            inputModes = LocalInputModeManager.current
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                TestShell(widthClass, overlay = overlay, screen = { _, _ ->
                    Column(Modifier.fillMaxSize()) {
                        NagButton("Paylaş", {}, Modifier.testTag("share"))
                        NagButton("Vazgeç", {}, Modifier.testTag("cancel"))
                        layer()
                    }
                })
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
        compose.waitForIdle()
        compose.onNodeWithTag("share").requestFocus()
        compose.onNodeWithTag("share").assertIsFocused()
    }

    @Test
    fun aSheetGivesTheFocusBack() {
        window(WidthClass.Large, layer = {
            GlassBottomSheet(visible = open, onDismiss = { open = false }, label = "Paylaş") {
                NagButton("Bağlantıyı kopyala", {})
            }
        })
        open = true
        compose.waitForIdle()
        // the sheet has the keyboard while it is open
        compose.onNodeWithTag("share").assertIsNotFocused()
        open = false
        compose.waitForIdle()
        compose.onNodeWithTag("share").assertIsFocused()
    }

    /** The phone's "Sıradaki", a page over the whole window: closed with Back, the focus is where it was. */
    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-mdpi-keysexposed-qwerty")
    fun aPageOverTheWindowGivesTheFocusBack() {
        val queue = FakeQueue(FakeQueue.sample, current = 0)
        window(WidthClass.Compact, overlay = {
            QueuePage(open, onClose = { open = false }) { pageModifier ->
                QueuePanel(queue.entries, queue.current, queue, { open = false }, pageModifier, background = NagTheme.colors.background, page = true)
            }
        })
        open = true
        compose.waitForIdle()
        compose.onNodeWithTag("queue-page").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("queue-page").assertDoesNotExist()
        compose.onNodeWithTag("share").assertIsFocused()
    }

    /** The palette is a window of its own: the focus it leaves behind is still there when it closes. */
    @Test
    fun thePaletteGivesTheFocusBack() {
        window(WidthClass.Large, layer = {
            if (open) CommandPalette(items = emptyList(), recent = emptyList(), onRun = {}, onDismiss = { open = false })
        })
        open = true
        compose.waitForIdle()
        open = false
        compose.waitForIdle()
        compose.onNodeWithTag("share").assertIsFocused()
    }

    /** The focus goes back to the control that had it when the sheet opened, not to one that had it before. */
    @Test
    fun theFocusGoesBackToTheControlThatHadIt() {
        window(WidthClass.Large, layer = {
            GlassBottomSheet(visible = open, onDismiss = { open = false }, label = "Paylaş") {
                NagButton("Bağlantıyı kopyala", {})
            }
        })
        compose.onNodeWithTag("cancel").requestFocus()
        compose.onNodeWithTag("cancel").assertIsFocused()
        open = true
        compose.waitForIdle()
        open = false
        compose.waitForIdle()
        compose.onNodeWithTag("cancel").assertIsFocused()
        compose.onNodeWithTag("share").assertIsNotFocused()
    }
}
