package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.LyricsEntry
import app.nagmedar.data.SmartPlaylistEntry
import app.nagmedar.data.TrackStatsEntry
import app.nagmedar.ui.backup.Backup
import app.nagmedar.ui.backup.BackupCover
import app.nagmedar.ui.backup.BackupPart
import app.nagmedar.ui.backup.BackupPlaylist
import app.nagmedar.ui.backup.BackupSection
import app.nagmedar.ui.backup.RestoreReport
import app.nagmedar.ui.backup.RestoreState
import app.nagmedar.ui.backup.RestoreSurface
import app.nagmedar.ui.backup.SettingValue
import app.nagmedar.ui.music.PlaylistEntry
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * S-1502, for approval: Settings › Backup in DeX with a backup made, on a phone in English with
 * none; the restore's window with every part, and what came back with tracks not found.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = TimeZone.getDefault()

    /** The times as Istanbul reads them: 12 October 2026, 23:40. */
    @Before
    fun istanbul() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"))

    @After
    fun back() = TimeZone.setDefault(zone)

    private val madeAt = 1_791_837_600_000L

    private val backup = Backup(
        createdAt = madeAt,
        appVersion = "0.2.0",
        settingsVersion = 17,
        settings = List(214) { SettingValue("nagmedar_sample_$it", it) },
        shortcuts = mapOf("Stop" to "Q", "Mute" to "", "Lyrics" to "Ctrl+L", "Next" to "Alt+N", "Previous" to "Alt+P", "PlayQueue" to "Ctrl+Q"),
        playlists = List(5) { list -> BackupPlaylist("Liste ${list + 1}", List(if (list == 0) 100 else 53) { PlaylistEntry("/m/$list/$it.flac") }) },
        smartLists = List(3) { SmartPlaylistEntry(name = "Akıllı ${it + 1}", rules = "") },
        stats = List(1204) { TrackStatsEntry("file:///m/$it.flac", rating = it % 6) },
        lyrics = List(12) { LyricsEntry("t$it", "user", synced = true, text = "") },
        covers = List(4) { BackupCover("%040x.user".format(it), ByteArray(0)) },
    )

    private fun capture(name: String, theme: ThemeMode, input: InputMode, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
    fun settingsDex() = capture("settings-backup-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
        Box(Modifier.padding(top = 16.dp)) { BackupSection(lastBackup = madeAt, onBackUp = {}, onRestore = {}) }
    }

    @Test @Config(qualifiers = "en-w390dp-h844dp-hdpi")
    fun settingsPhoneEnglish() = capture("settings-backup-phone-light-en", ThemeMode.Light, InputMode.Touch) {
        Box(Modifier.padding(top = 16.dp)) { BackupSection(lastBackup = 0L, onBackUp = {}, onRestore = {}) }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
    fun restoreDex() = capture("restore-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RestoreSurface(RestoreState.Choosing(backup), onRestore = {}, onDismiss = {})
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun restoredPhone() = capture("restore-done-phone-dark", ThemeMode.Dark, InputMode.Touch) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RestoreSurface(
                RestoreState.Done(
                    RestoreReport(
                        mapOf(BackupPart.Settings to 214, BackupPart.Shortcuts to 6, BackupPart.Playlists to 5, BackupPart.Stats to 1204),
                        missingTracks = 7,
                    ),
                ),
                onRestore = {},
                onDismiss = {},
            )
        }
    }
}
