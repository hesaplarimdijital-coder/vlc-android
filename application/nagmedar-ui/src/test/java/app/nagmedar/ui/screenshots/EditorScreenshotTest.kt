package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.lyrics.editor.FakePlayer
import app.nagmedar.ui.lyrics.editor.FakeSidecar
import app.nagmedar.ui.lyrics.editor.FakeTags
import app.nagmedar.ui.lyrics.editor.LyricsEditorController
import app.nagmedar.ui.lyrics.editor.LyricsEditorScreen
import app.nagmedar.ui.lyrics.editor.LyricsWriter
import app.nagmedar.ui.lyrics.editor.MemoryLyricsCache
import app.nagmedar.ui.lyrics.editor.SAMPLE_TRACK
import app.nagmedar.ui.lyrics.editor.SidecarResult
import app.nagmedar.ui.lyrics.editor.StampMode
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-1005, for approval: the lyrics editor in DeX in word mode with the preview beside the lines;
 * on a tablet after saving a .lrc; on a phone asking for a folder to write the .lrc into; on a
 * phone in English with the preview open.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val text = "Sabahın ilk ışığı pencereye düştü\nEski bir türkü gibi usulca\nYolun sonunda bir ev vardı\nKapısı hep açık kaldı"

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

    private fun controller(player: FakePlayer, sidecar: FakeSidecar = FakeSidecar()) = LyricsEditorController(
        SAMPLE_TRACK, null, player, LyricsWriter(MemoryLyricsCache(), sidecar, FakeTags()), null,
        CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, workers = 1,
    ).also { it.import(text) }

    private fun LyricsEditorController.stampLines(count: Int, player: FakePlayer) {
        repeat(count) {
            player.positionMs = 4_000L + it * 5_200L
            stamp()
        }
    }

    @Composable
    private fun Editor(c: LyricsEditorController, player: FakePlayer) =
        LyricsEditorScreen(c, timeMs = { player.positionMs }, playing = player.playing, onClose = {})

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dexWords() {
        val player = FakePlayer()
        val c = controller(player)
        c.stampLines(1, player)
        c.setMode(StampMode.Word)
        player.positionMs = 9_300
        c.stamp()
        player.positionMs = 9_700
        c.stamp()
        player.positionMs = 10_100
        c.stamp()
        c.preview = true
        capture("lyrics-editor-dex-dark", ThemeMode.Dark, InputMode.Pointer) { Editor(c, player) }
    }

    @Test @Config(qualifiers = "tr-w800dp-h1000dp-mdpi")
    fun tabletSaved() {
        val player = FakePlayer()
        val c = controller(player)
        c.stampLines(4, player)
        c.save()
        capture("lyrics-editor-tablet-light", ThemeMode.Light, InputMode.Touch) { Editor(c, player) }
    }

    @Test @Config(qualifiers = "tr-w411dp-h1500dp-xhdpi")
    fun phoneNeedsFolder() {
        val player = FakePlayer()
        val c = controller(player, FakeSidecar(result = { SidecarResult.NeedsFolder }))
        c.stampLines(4, player)
        c.save()
        capture("lyrics-editor-phone-dark", ThemeMode.Dark, InputMode.Touch) { Editor(c, player) }
    }

    @Test @Config(qualifiers = "en-w390dp-h1500dp-hdpi")
    fun phoneEnglishPreview() {
        val player = FakePlayer()
        val c = controller(player)
        c.stampLines(2, player)
        c.preview = true
        player.positionMs = 10_000
        capture("lyrics-editor-phone-light-en", ThemeMode.Light, InputMode.Touch) { Editor(c, player) }
    }
}
