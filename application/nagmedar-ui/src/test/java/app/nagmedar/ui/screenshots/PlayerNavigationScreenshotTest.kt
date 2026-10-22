package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.ChromeVisibility
import app.nagmedar.ui.player.DesktopControls
import app.nagmedar.ui.player.FakePlayerActions
import app.nagmedar.ui.player.FramePreviews
import app.nagmedar.ui.player.navigate.BookmarksPanel
import app.nagmedar.ui.player.navigate.JumpSurface
import app.nagmedar.ui.player.navigate.sampleBookmarks
import app.nagmedar.ui.player.navigate.sampleChapters
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.player.samplePlayerState
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-608: the bookmarks tab beside the video (its diamonds on the timeline), under it in half a
 * DeX window, on a phone, in English; "Jump to time" over the video, going into a chapter and,
 * on a phone, past the film's end.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerNavigationScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun save(name: String) {
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    private fun bookmarks(name: String, input: InputMode = InputMode.Pointer, controls: Boolean = true) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                PlayerWithPanel(PanelTab.Bookmarks, controls, bookmarks = sampleBookmarks.map { it.timeMs }) {
                    BookmarksPanel(
                        marks = sampleBookmarks,
                        refused = false,
                        previews = TintedPreviews,
                        onAdd = {},
                        onSeek = {},
                        onRename = {},
                        onRemove = {},
                    )
                }
            }
        }
        save(name)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dex() = bookmarks("player-bookmarks-dex")

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun dexEnglish() = bookmarks("player-bookmarks-dex-en")

    @Test @Config(qualifiers = "tr-w728dp-h880dp-mdpi")
    fun halfWindow() = bookmarks("player-bookmarks-half")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = bookmarks("player-bookmarks-phone", input = InputMode.Touch, controls = false)

    private fun jump(name: String, typed: String, input: InputMode) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        val visibility = ChromeVisibility().apply {
            onMouseMoved()
            overBars = true
        }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    SampleVideo(Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                    DesktopControls(samplePlayerState, FakePlayerActions(), visibility, previews = null)
                    // the dialog's dim, drawn here: the window holds only the activity
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                    JumpSurface(samplePlayerState.timeMs, samplePlayerState.lengthMs, sampleChapters, onJump = {}, onDismiss = {}, initial = typed)
                }
            }
        }
        save(name)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun jumpDex() = jump("player-jump-dex", "1:12:33", InputMode.Pointer)

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun jumpDexEnglish() = jump("player-jump-dex-en", "+30", InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun jumpPhone() = jump("player-jump-phone", "3:00:00", InputMode.Touch)
}

/** Frames for the screenshots: the sky's colours shifting with the time, so each moment looks its own. */
internal object TintedPreviews : FramePreviews {
    private val colours = listOf(0xFF0E2A3E, 0xFF2E8B6F, 0xFF3B4A5C, 0xFF6F8799, 0xFF2FB7A2, 0xFF1D2A3A).map { it.toInt() }

    override suspend fun frameAt(ms: Long): ImageBitmap = frame(((ms / 600_000) % colours.size).toInt())

    private val frames = HashMap<Int, ImageBitmap>()

    private fun frame(index: Int): ImageBitmap = frames.getOrPut(index) {
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 180f, colours[index], colours[(index + 1) % colours.size], Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, 320f, 180f, paint)
        bitmap.asImageBitmap()
    }

    override fun close() = Unit
}
