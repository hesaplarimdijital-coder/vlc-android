package app.nagmedar.ui.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/** S-804: the playlists' tabs, their lines moved and taken out by keys, a playlist made. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi-qwerty")
class PlaylistsBrowserTest {

    @get:Rule
    val compose = createComposeRule()

    private val played = mutableListOf<Pair<List<String>, Int>>()

    private val actions = object : MusicActions {
        override fun play(tracks: List<Track>, index: Int) {
            played += tracks.map { it.title } to index
        }

        override fun queue(tracks: List<Track>) = Unit

        override fun menu(track: Track, play: () -> Unit, select: (() -> Unit)?): List<MenuEntry> = emptyList()
    }

    private val undo = UndoBarState()
    private lateinit var inputModes: InputModeManager

    private fun show(store: FakePlaylistStore, input: InputMode = InputMode.Pointer): PlaylistsState {
        lateinit var state: PlaylistsState
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { PlaylistsState(store, scope) }
            LaunchedEffect(Unit) { state.reload() }
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                inputModes = LocalInputModeManager.current
                CompositionLocalProvider(LocalWidthClass provides WidthClass.Large) {
                    PlaylistsBrowser(state, actions, undo = undo)
                }
            }
        }
        compose.waitForIdle()
        // a hardware key takes the window out of touch mode; injected keys do not
        if (input == InputMode.Pointer) compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
        return state
    }

    private fun row(title: String) = compose.onNode(hasText(title) and hasClickAction())

    @Test
    fun tabsOpenTheirPlaylists() {
        show(FakePlaylistStore("Akşam" to listOf(0, 1, 2), "Yol" to listOf(3, 4)))
        row("Neva Kâr").assertExists()
        compose.onNode(hasText("Yol") and hasClickAction()).performClick()
        compose.waitForIdle()
        row("Yağmur").assertExists()
        row("Neva Kâr").assertDoesNotExist()
        // Oynat plays the open playlist from its start
        compose.onNode(hasText("Oynat") and hasClickAction()).performClick()
        assertEquals(listOf(listOf("Gece Yolcusu", "Yağmur") to 0), played)
    }

    @Test
    fun keysMoveAndTakeOutWithTheUndoBar() {
        val store = FakePlaylistStore("Akşam" to listOf(0, 1, 2))
        show(store)
        row("Segâh Tekbîr").apply {
            requestFocus()
            performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionDown) } }
        }
        compose.waitForIdle()
        assertEquals(listOf("Neva Kâr", "Segâh Tekbîr", "Nihâvend Saz Semâîsi"), store.titles(1))
        row("Nihâvend Saz Semâîsi").apply {
            requestFocus()
            performKeyInput { pressKey(Key.Delete) }
        }
        compose.waitForIdle()
        assertEquals(listOf("Neva Kâr", "Segâh Tekbîr"), store.titles(1))
        assertEquals("1 parça “Akşam” listesinden kaldırıldı", undo.messages.last().text)
        undo.undoLatest()
        compose.waitForIdle()
        assertEquals(listOf("Neva Kâr", "Segâh Tekbîr", "Nihâvend Saz Semâîsi"), store.titles(1))
    }

    @Test
    fun aPlaylistIsMadeFromTheEmptyScreen() {
        val store = FakePlaylistStore()
        show(store)
        compose.onNodeWithText("Henüz çalma listesi yok").assertExists()
        // the header's button and the empty screen's both make one
        compose.onAllNodes(hasText("Yeni liste") and hasClickAction())[1].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("playlist-name-save").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Yeni liste"), store.kept.values.map { it.first })
        compose.onNodeWithText("Bu liste boş").assertExists()
    }
}
