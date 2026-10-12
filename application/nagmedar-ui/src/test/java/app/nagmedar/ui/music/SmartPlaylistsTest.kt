package app.nagmedar.ui.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-805: smart lists in the playlists screen: made from a template, changed, played, deleted and brought back. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
class SmartPlaylistsTest {

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

    private fun show(smartStore: FakeSmartListStore, store: FakePlaylistStore = FakePlaylistStore("Akşam" to listOf(1, 4))) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val state = remember { PlaylistsState(store, scope) }
            val smart = remember { SmartListsState(smartStore, scope) }
            LaunchedEffect(Unit) { state.reload() }
            LaunchedEffect(Unit) { smart.follow() }
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalWidthClass provides WidthClass.Large) {
                    PlaylistsBrowser(
                        state, actions, undo = undo, smart = smart,
                        library = MusicSamples.tracks, stats = MusicSamples.stats, now = { 1_760_000_000_000L },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun madeFromATemplateAndPlayed() {
        val smartStore = FakeSmartListStore()
        show(smartStore)
        compose.onNodeWithTag("smart-new").performClick()
        compose.onNodeWithText("En sevilenler").performClick()
        compose.onNodeWithTag("smart-count").assertTextEquals("3 parça uyuyor")
        compose.onNodeWithTag("smart-save").performClick()
        compose.waitForIdle()
        assertEquals(listOf("En sevilenler"), smartStore.kept.map { it.name })
        // made, it is open: its tracks, the best first
        compose.onNodeWithTag("smart-pane").assertExists()
        compose.onNode(hasText("Oynat") and hasClickAction()).performClick()
        assertEquals(listOf(listOf("Segâh Tekbîr", "Gece Yolcusu", "Neva Kâr") to 0), played)
    }

    @Test
    fun itsRulesChangedThenDeletedAndBroughtBack() {
        val smartStore = FakeSmartListStore(SmartTemplate.MostPlayed.build("En çok çalınanlar"))
        show(smartStore)
        // no playlist chosen: the smart list's tab is open after the playlist's
        compose.onNodeWithText("En çok çalınanlar").performClick()
        compose.onNodeWithTag("smart-pane").assertExists()
        compose.onNodeWithText("Gece Yolcusu").assertExists()
        compose.onNode(hasText("Kuralları düzenle…") and hasClickAction()).performClick()
        // at least 20 plays
        compose.onAllNodesWithTag("smart-value")[0].performTextReplacement("20")
        compose.onNodeWithTag("smart-count").assertTextEquals("2 parça uyuyor")
        compose.onNodeWithTag("smart-save").performClick()
        compose.waitForIdle()
        assertEquals(listOf(SmartRule(SmartField.Plays, SmartOp.AtLeast, "20")), smartStore.kept.single().rules)
        compose.onNodeWithText("Segâh Tekbîr").assertDoesNotExist()
        // deleted from its menu, then back with the undo bar
        compose.onNode(hasContentDescription("Diğer işlemler")).performClick()
        compose.onNodeWithText("Akıllı listeyi sil").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<SmartList>(), smartStore.kept)
        compose.onNodeWithTag("smart-pane").assertDoesNotExist()
        compose.runOnIdle { assertTrue(undo.undoLatest()) }
        compose.waitForIdle()
        assertEquals(listOf("En çok çalınanlar"), smartStore.kept.map { it.name })
        assertEquals(listOf(SmartRule(SmartField.Plays, SmartOp.AtLeast, "20")), smartStore.kept.single().rules)
    }
}
