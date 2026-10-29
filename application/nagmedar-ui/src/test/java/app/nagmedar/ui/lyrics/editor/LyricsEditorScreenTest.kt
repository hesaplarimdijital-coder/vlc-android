package app.nagmedar.ui.lyrics.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.theme.NagmedarTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-1005: the editor on screen: the keys, the import, saving and the confirmation before LRCLIB. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
class LyricsEditorScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val player = FakePlayer()
    private val sidecar = FakeSidecar()
    private val publisher = object : LyricsPublisher {
        var request: PublishRequest? = null
        override suspend fun challenge() = PublishChallenge.of("abcDEF123", "00" + "FF".repeat(31))!!
        override suspend fun publish(request: PublishRequest, challenge: PublishChallenge, nonce: String) {
            this.request = request
        }
    }
    private var closed = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var controller: LyricsEditorController

    @After
    fun tearDown() {
        ShortcutRegistry.resume()
        scope.cancel()
    }

    private fun show(text: String? = SAMPLE_TEXT) {
        controller = LyricsEditorController(
            SAMPLE_TRACK, null, player, LyricsWriter(MemoryLyricsCache(), sidecar, FakeTags()), publisher,
            scope, Dispatchers.Unconfined, workers = 1,
        )
        if (text != null) controller.import(text)
        compose.setContent {
            NagmedarTheme { LyricsEditorScreen(controller, timeMs = { player.positionMs }, playing = player.playing, onClose = { closed++ }) }
        }
        compose.waitForIdle()
    }

    private fun key(key: Key) = compose.onNodeWithTag("lrc-editor").performKeyInput { pressKey(key) }

    @Test
    fun enterStampsTheLineAtTheTracksPositionAndTheCursorGoesOn() {
        show()
        player.positionMs = 1_500
        key(Key.Enter)
        compose.waitForIdle()
        assertEquals(listOf(1_500L, -1L, -1L, -1L), controller.stamps.lines.map { it.startMs })
        assertEquals(1, controller.stamps.cursor.line)
        compose.onNodeWithTag("lrc-line-0").assertExists()
        compose.onNodeWithTag("lrc-progress").assertExists()
    }

    @Test
    fun theOtherKeysMoveUndoAndNudge() {
        show()
        player.positionMs = 1_000
        key(Key.Enter)
        player.positionMs = 2_000
        key(Key.Enter)
        key(Key.Period)
        assertEquals(2_100L, controller.stamps.lines[1].startMs)
        key(Key.Comma)
        key(Key.Backspace)
        assertEquals(-1L, controller.stamps.lines[1].startMs)
        key(Key.DirectionDown)
        assertEquals(2, controller.stamps.cursor.line)
        key(Key.DirectionUp)
        assertEquals(1, controller.stamps.cursor.line)
        key(Key.W)
        assertEquals(StampMode.Word, controller.stamps.mode)
    }

    @Test
    fun spaceStaysPlayAndPause() {
        show()
        key(Key.Spacebar)
        assertEquals(1, player.toggles)
        assertFalse(controller.stamps.changed)
    }

    @Test
    fun aClickOnALineGoesThere() {
        show()
        player.positionMs = 1_000
        key(Key.Enter)
        compose.onNodeWithTag("lrc-line-0").performClick()
        assertEquals(0, controller.stamps.cursor.line)
        assertEquals(listOf(1_000L), player.seeks)
    }

    @Test
    fun theKeysAreNotTheEditorsWhileAnImportBoxHasTheKeyboard() {
        show(null)
        compose.onNodeWithTag("lrc-import-dialog").assertExists()
        compose.onNodeWithTag("lrc-import-text").performTextInput("Bir\nİki")
        compose.onNodeWithTag("lrc-import-do").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Bir", "İki"), controller.stamps.lines.map { it.text })
        compose.onNodeWithTag("lrc-import-dialog").assertDoesNotExist()
    }

    @Test
    fun anEmptyEditorSaysSo() {
        show(null)
        compose.onNodeWithTag("lrc-import-cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("lrc-empty").assertExists()
    }

    @Test
    fun savingSaysWhatIsLeftThenWhereItWent() {
        show()
        compose.onNodeWithTag("lrc-save").performClick()
        compose.onNodeWithText("Kaydetmek için 4 satır daha damgalanmalı.").assertExists()
        for (ms in listOf(1_000L, 2_000L, 3_000L, 4_000L)) {
            player.positionMs = ms
            key(Key.Enter)
        }
        compose.onNodeWithTag("lrc-save").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Parça.lrc olarak kaydedildi. Değiştirdiği sözler saklandı.").assertExists()
        assertTrue(sidecar.written!!.contains("[00:04.00]Dördüncü satır"))
    }

    @Test
    fun aFolderTheAppCannotWriteToAsksForOne() {
        show()
        sidecar.result = { SidecarResult.NeedsFolder }
        for (ms in listOf(1_000L, 2_000L, 3_000L, 4_000L)) {
            player.positionMs = ms
            key(Key.Enter)
        }
        compose.onNodeWithTag("lrc-save").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("lrc-pick-folder").assertExists()
    }

    @Test
    fun lrclibIsAskedAboutBeforeAnythingIsSent() {
        show()
        for (ms in listOf(1_000L, 2_000L, 3_000L, 4_000L)) {
            player.positionMs = ms
            key(Key.Enter)
        }
        compose.onNodeWithTag("lrc-publish").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("lrc-publish-dialog").assertExists()
        compose.onNodeWithTag("lrc-publish-summary").assertExists()
        assertTrue(publisher.request == null)
        compose.onNodeWithTag("lrc-publish-cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("lrc-publish-dialog").assertDoesNotExist()
        assertTrue(publisher.request == null)
    }

    @Test
    fun closingWithUnsavedStampsAsksFirst() {
        show()
        player.positionMs = 1_000
        key(Key.Enter)
        compose.onNodeWithTag("lrc-close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("lrc-discard").assertExists()
        assertEquals(0, closed)
        compose.onNodeWithTag("lrc-discard-close").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun closingACleanEditorClosesAtOnce() {
        show(null)
        compose.onNodeWithTag("lrc-import-cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("lrc-close").performClick()
        assertEquals(1, closed)
    }
}
