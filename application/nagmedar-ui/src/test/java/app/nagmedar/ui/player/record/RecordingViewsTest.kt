package app.nagmedar.ui.player.record

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.player.FakePlayerActions
import app.nagmedar.ui.player.snapshot.SnapshotSettings
import app.nagmedar.ui.player.snapshotMenu
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-607: the mark over the video says the time and size and stops the recording; the card plays
 * the recording and opens its folder, or asks for a folder and tries again; the camera's menu
 * records with Shift+R; the chosen folder is remembered.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class RecordingViewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var status by mutableStateOf<RecordingStatus>(RecordingStatus.Off)
    private var outcome by mutableStateOf<RecordingOutcome?>(null)

    private val saved = RecordingOutcome.Saved(
        uri = "content://media/external/video/media/42",
        fileName = "Kuzey Işığı 2026-10-15 21-04-33.ts",
        folder = "Movies/Nağmedar",
        tree = null,
        mime = "video/mp2t",
        bytes = 1_240_000_000,
        durationMs = 1_865_000,
    )

    private fun show() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                RecordingIndicator(status, onStop = { calls += "stop" })
                RecordingCard(
                    outcome = outcome,
                    onPlay = { calls += "play:${it.fileName}" },
                    onOpenFolder = { calls += "folder:${it.folder}" },
                    onChooseFolder = { calls += "choose" },
                    onRetry = { calls += "retry" },
                    onDismiss = { outcome = null },
                    stays = true,
                )
            }
        }
    }

    @Test
    fun theMarkSaysTheTimeAndSizeAndStops() {
        show()
        compose.onNodeWithTag("recording-indicator").assertDoesNotExist()
        status = RecordingStatus.On(elapsedMs = 754_000, bytes = 248_300_000)
        compose.onNodeWithTag("recording-text").assertTextEquals("12:34 · 248 MB")
        compose.onNodeWithContentDescription("Kayıt sürüyor, 12:34 · 248 MB").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kaydı durdur").performClick()
        assertEquals(listOf("stop"), calls)
    }

    @Test
    fun theCopySaysHowFarWithoutAStopButton() {
        status = RecordingStatus.Saving(copied = 45, total = 100)
        show()
        compose.onNodeWithTag("recording-text").assertTextEquals("Kaydediliyor… %45")
        compose.onNodeWithContentDescription("Kaydı durdur").assertDoesNotExist()
    }

    @Test
    fun theCardPlaysAndOpensTheFolder() {
        outcome = saved
        show()
        compose.onNodeWithText("Kayıt tamamlandı").assertIsDisplayed()
        compose.onNodeWithTag("recording-file").assertTextEquals("Kuzey Işığı 2026-10-15 21-04-33.ts")
        compose.onNodeWithText("Movies/Nağmedar · 31:05 · 1,2 GB").assertIsDisplayed()
        compose.onNodeWithText("Oynat").performClick()
        compose.onNodeWithText("Klasörü aç").performClick()
        assertEquals(listOf("play:Kuzey Işığı 2026-10-15 21-04-33.ts", "folder:Movies/Nağmedar"), calls)
        compose.onNodeWithContentDescription("Kapat").performClick()
        assertNull(outcome)
    }

    @Test
    fun aFolderGoneAsksForAnotherAndTriesAgain() {
        outcome = RecordingOutcome.Failed(RecordingFailure.Folder, canRetry = true)
        show()
        compose.onNodeWithText("Kayıt yapılamadı").assertIsDisplayed()
        compose.onNodeWithText("Kayıt uygulamanın kendi alanında bekliyor.").assertIsDisplayed()
        compose.onNodeWithText("Klasör seç…").performClick()
        compose.onNodeWithText("Yeniden dene").performClick()
        assertEquals(listOf("choose", "retry"), calls)
    }

    @Test
    fun aFullStorageOffersAnotherFolder() {
        outcome = RecordingOutcome.Failed(RecordingFailure.NoSpace, canRetry = true)
        show()
        compose.onNodeWithText("Depolama alanı dolu.").assertIsDisplayed()
        compose.onNodeWithText("Klasör seç…").performClick()
        compose.onNodeWithText("Yeniden dene").performClick()
        assertEquals(listOf("choose", "retry"), calls)
    }

    @Test
    fun whatCannotBeRecordedIsSaid() {
        outcome = RecordingOutcome.Failed(RecordingFailure.NotSupported)
        show()
        compose.onNodeWithTag("recording-reason").assertTextEquals(
            "Bu kayda alınamıyor: müzik motoru oynatıyor ya da VLC bu türden medyayı kaydedemiyor.",
        )
        compose.onNodeWithText("Yeniden dene").assertDoesNotExist()
        compose.onNodeWithText("Klasör seç…").assertDoesNotExist()
    }

    @Test
    fun theCamerasMenuRecordsWithShiftR() {
        var entries: List<MenuEntry> = emptyList()
        var stopping: List<MenuEntry> = emptyList()
        val actions = FakePlayerActions()
        compose.setContent {
            entries = snapshotMenu(SnapshotSettings(), actions)
            stopping = snapshotMenu(SnapshotSettings(), actions, recording = true)
        }
        compose.waitForIdle()
        val record = entries.filterIsInstance<MenuEntry.Item>()[1]
        assertEquals("Kayda al", record.label)
        assertEquals("Shift+R", record.shortcut)
        assertEquals("Kaydı durdur", stopping.filterIsInstance<MenuEntry.Item>()[1].label)
        record.onClick()
        assertEquals(listOf("record"), actions.calls)
    }

    @Test
    fun theChosenFolderIsRemembered() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shared = context.getSharedPreferences("recording-folder-test", Context.MODE_PRIVATE)
        val prefs = NagmedarPreferences(shared)
        assertNull(prefs.recordingFolder)
        val tree = "content://com.android.externalstorage.documents/tree/1234-ABCD%3AKay%C4%B1tlar"
        prefs.updateRecordingFolder(tree)
        assertEquals(tree, shared.getString(KEY_RECORDING_FOLDER, null))
        assertEquals(tree, NagmedarPreferences(shared).recordingFolder)
        // another window changed it
        shared.edit().putString(KEY_RECORDING_FOLDER, null).commit()
        prefs.reload(KEY_RECORDING_FOLDER)
        assertNull(prefs.recordingFolder)
    }
}
