package app.nagmedar.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode as ComposeInputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.LocalScreenCommands
import app.nagmedar.ui.components.ScreenCommands
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-206, S-1301: the network browser by mouse and keys: a server opened, a folder opened with
 * Enter, Backspace up and F5 again (the window's shortcuts), a file played, a sign-in turned down
 * and given again.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
class NetworkBrowserTest {

    @get:Rule
    val compose = createComposeRule()

    private val commands = ScreenCommands()
    private lateinit var inputModes: InputModeManager

    private fun show(input: InputMode = InputMode.Pointer, content: @Composable () -> Unit) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                inputModes = LocalInputModeManager.current
                CompositionLocalProvider(LocalScreenCommands provides commands, LocalWidthClass provides WidthClass.Large) {
                    Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
                }
            }
        }
    }

    private fun row(name: String) = compose.onNode(hasText(name) and hasClickAction())

    @Test
    fun foldersOpenWithTheKeysAndFilesPlay() {
        val source = NetworkSamples.source()
        val actions = RecordingActions()
        show { SampleNetworkBrowser(source = source, actions = actions) { it.found(source) } }
        compose.onNodeWithText("MEDYA-PC").assertExists()
        // a saved server is not listed again among those found
        compose.onNodeWithText("Bir sunucu seçin").assertExists()
        compose.onNodeWithText("Salondaki NAS").performClick()
        compose.onNodeWithText("Kuzey Işığı (2023).mkv").assertExists()
        // Enter opens the folder chosen
        compose.onNodeWithText("Diziler").performClick()
        // a hardware key takes the window out of touch mode; injected keys do not
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
        row("Diziler").requestFocus()
        row("Diziler").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        compose.onNodeWithText("Sahil S01E03.mkv").assertExists()
        // Backspace goes up without reading again, F5 reads again
        val reads = source.reads.size
        compose.runOnIdle { assertTrue(commands.up()) }
        compose.onNodeWithText("Bozkırda Bir Gün.mp4").assertExists()
        assertEquals(reads, source.reads.size)
        compose.runOnIdle { assertTrue(commands.refresh()) }
        compose.waitForIdle()
        assertEquals(reads + 1, source.reads.size)
        // a film plays with the folder's others
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
        row("Çay Bahçesi.mkv").requestFocus()
        row("Çay Bahçesi.mkv").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        val (played, index) = actions.played.single()
        assertEquals("Çay Bahçesi.mkv", played[index])
        assertTrue("Ağustos Böceği.tr.srt" !in played)
    }

    @Test
    fun aTurnedDownSignInIsAskedForAgain() {
        val store = NetworkSamples.store()
        show { SampleNetworkBrowser(store = store) }
        compose.onNodeWithText("Arşiv (FTP)").performClick()
        compose.onNodeWithText("Kullanıcı adı ya da parola yanlış").assertExists()
        compose.onNodeWithTag("server-user").performTextInput("arsiv")
        compose.onNodeWithTag("server-password").performTextInput("doğru")
        compose.onNodeWithTag("network-sign-in-go").performClick()
        compose.onNodeWithText("Belgeseller").assertExists()
        assertEquals(Credentials("arsiv", "doğru"), store.signIns[1])
    }

    @Test
    fun aTapOpensByTouch() {
        show(InputMode.Touch) { SampleNetworkBrowser() }
        compose.onNodeWithText("Salondaki NAS").performClick()
        compose.onNodeWithText("Diziler").performClick()
        compose.onNodeWithText("Sahil S01E01.mkv").assertExists()
    }

    @Test
    fun aServersQuestionIsAnsweredOnItsPage() {
        val replies = mutableListOf<String>()
        val source = NetworkSamples.source()
        source.questions[NetworkSamples.DAV] = LibVlcPrompt(
            PromptKind.Question, "Insecure site", "You attempted to reach bulut.example.org.",
            cancelText = "Cancel", action1 = "Accept permanently", action2 = "Accept once", reply = RecordingReply(replies),
        )
        show { SampleNetworkBrowser(source = source) }
        compose.onNodeWithText("Bulut").performClick()
        compose.onNodeWithTag("network-question").assertExists()
        compose.onNodeWithTag("prompt-action-2").performClick()
        assertEquals(listOf("action 2"), replies)
        compose.onNodeWithTag("network-connecting").assertExists()
    }
}
