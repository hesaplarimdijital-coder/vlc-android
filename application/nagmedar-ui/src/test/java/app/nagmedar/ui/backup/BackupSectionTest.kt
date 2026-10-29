package app.nagmedar.ui.backup

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.TrackStatsEntry
import app.nagmedar.ui.music.PlaylistEntry
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-1502: Settings › Backup's buttons, and the restore's window: the parts chosen, what came back. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
class BackupSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val backup = Backup(
        createdAt = 1_791_837_600_000L,
        appVersion = "0.2.0",
        settings = listOf(SettingValue("nagmedar_theme", "Dark")),
        shortcuts = mapOf("Stop" to "Q"),
        playlists = listOf(BackupPlaylist("Yol", listOf(PlaylistEntry("/m/01.flac"), PlaylistEntry("/m/02.flac")))),
        stats = listOf(TrackStatsEntry("file:///m/01.flac", rating = 4)),
    )

    private fun show(state: RestoreState, onRestore: (Set<BackupPart>) -> Unit = {}) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) { RestoreSurface(state, onRestore, onDismiss = {}) }
        }
    }

    @Test
    fun theButtons() {
        var made = 0
        var restored = 0
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                BackupSection(lastBackup = 0L, onBackUp = { made++ }, onRestore = { restored++ })
            }
        }
        compose.onNodeWithText("Henüz yedek alınmadı", substring = true).assertExists()
        compose.onNodeWithTag("backup-make").performClick()
        compose.onNodeWithTag("backup-restore").performClick()
        assertEquals(1, made)
        assertEquals(1, restored)
    }

    @Test
    fun thePartsAreChosen() {
        var chosen: Set<BackupPart>? = null
        show(RestoreState.Choosing(backup)) { chosen = it }
        compose.onNodeWithText("Çalma listeleri").assertExists()
        compose.onNodeWithText("1 liste · 2 parça").assertExists()
        // the shortcuts stay as they are here
        compose.onNodeWithTag("restore-Shortcuts").performClick()
        compose.onNodeWithTag("restore-go").performClick()
        assertEquals(setOf(BackupPart.Settings, BackupPart.Playlists, BackupPart.Stats), chosen)
    }

    @Test
    fun nothingChosenIsNotRestored() {
        show(RestoreState.Choosing(backup))
        backup.parts.forEach { compose.onNodeWithTag("restore-${it.name}").performClick() }
        compose.onNodeWithTag("restore-go").assertIsNotEnabled()
    }

    @Test
    fun whatCameBackIsSaid() {
        show(RestoreState.Done(RestoreReport(mapOf(BackupPart.Settings to 214, BackupPart.Playlists to 3), missingTracks = 7, failed = setOf(BackupPart.Covers))))
        compose.onNodeWithText("214 ayar").assertExists()
        compose.onNodeWithText("3 liste").assertExists()
        compose.onNodeWithTag("restore-missing").assertExists()
        compose.onNodeWithText("Geri yüklenemedi: Seçtiğiniz kapaklar").assertExists()
    }
}
