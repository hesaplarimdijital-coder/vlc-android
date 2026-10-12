package app.nagmedar.ui.player.snapshot

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * S-606: the card says where the screenshot went and what became of its HDR and subtitles; it
 * opens and shares it; a folder gone is chosen again; the card goes after six seconds.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class SnapshotCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var outcome by mutableStateOf<SnapshotOutcome?>(null)
    private var saving by mutableStateOf(false)

    private val saved = SnapshotOutcome.Saved(
        uri = Uri.parse("content://media/external/images/media/42"),
        fileName = "Kuzey Işığı 01-11-45.png",
        folder = "Pictures/Nağmedar",
        format = SnapshotFormat.Png,
        hdr = HdrOutcome.Kept,
        subtitles = true,
        thumbnail = ImageBitmap(320, 180),
        width = 3840,
        height = 2160,
    )

    private fun card() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                SnapshotCard(
                    outcome = outcome,
                    saving = saving,
                    onOpen = { calls += "open:${it.fileName}" },
                    onShare = { calls += "share:${it.fileName}" },
                    onChooseFolder = { calls += "folder" },
                    onDismiss = {
                        calls += "dismiss"
                        outcome = null
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aSavedScreenshotSaysWhereAndWhat() {
        outcome = saved
        card()
        compose.onNodeWithText("Ekran görüntüsü kaydedildi").assertExists()
        compose.onNodeWithTag("snapshot-file").assertExists()
        compose.onNodeWithText("Kuzey Işığı 01-11-45.png").assertExists()
        compose.onNodeWithText("Pictures/Nağmedar · PNG · 3840×2160").assertExists()
        compose.onNodeWithText("HDR korundu").assertExists()
        compose.onNodeWithText("altyazılı").assertExists()
        compose.onNodeWithTag("snapshot-thumbnail").assertExists()
        compose.onNodeWithText("Aç").performClick()
        compose.onNodeWithText("Paylaş").performClick()
        assertEquals(listOf("open:Kuzey Işığı 01-11-45.png", "share:Kuzey Işığı 01-11-45.png"), calls)
    }

    @Test
    fun aFolderGoneIsChosenAgain() {
        outcome = SnapshotOutcome.Failed(SnapshotFailure.Folder)
        card()
        compose.onNodeWithText("Ekran görüntüsü alınamadı").assertExists()
        compose.onNodeWithText("Seçilen klasöre yazılamıyor; yeniden seçin.").assertExists()
        compose.onNodeWithText("Klasör seç…").performClick()
        assertEquals(listOf("folder"), calls)
    }

    @Test
    fun otherFailuresSayWhy() {
        outcome = SnapshotOutcome.Failed(SnapshotFailure.Write)
        card()
        compose.onNodeWithText("Dosya yazılamadı; sürücü dolu ya da çıkarılmış olabilir.").assertExists()
        compose.onNodeWithText("Klasör seç…").assertDoesNotExist()
    }

    @Test
    fun whileSavingItSaysSo() {
        saving = true
        card()
        compose.onNodeWithText("Ekran görüntüsü kaydediliyor…").assertExists()
        saving = false
        compose.waitForIdle()
        compose.onNodeWithTag("snapshot-card").assertDoesNotExist()
    }

    @Test
    fun theCardGoesAfterSixSeconds() {
        outcome = saved
        card()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(5_500)
        compose.onNodeWithTag("snapshot-card").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(listOf("dismiss"), calls)
        compose.onNodeWithTag("snapshot-card").assertDoesNotExist()
    }

    @Test
    fun closedByHand() {
        outcome = saved
        card()
        compose.onNodeWithTag("snapshot-card").assertExists()
        compose.onNode(hasContentDescription("Kapat")).performClick()
        compose.waitForIdle()
        assertEquals(listOf("dismiss"), calls)
    }
}
