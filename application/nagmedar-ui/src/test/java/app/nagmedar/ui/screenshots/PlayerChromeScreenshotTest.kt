package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.ChromeVisibility
import app.nagmedar.ui.player.DesktopControls
import app.nagmedar.ui.player.FakePlayerActions
import app.nagmedar.ui.player.FakePreviews
import app.nagmedar.ui.player.PlayerChromeState
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
 * S-601: the desktop controls over a video: in a DeX window with the mouse on the timeline (a
 * frame of that moment, its chapter, the A-B loop lit); on a tablet paused at 1,5× with A set and
 * no subtitles; on a phone's width, which makes room; and in English.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerChromeScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, state: PlayerChromeState, hover: Float?) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        // the mouse rests on the bar: they stay
        val visibility = ChromeVisibility(active = true).apply {
            onMouseMoved()
            overBars = true
        }
        val previews = FakePreviews(sampleFrame())
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    SampleVideo(Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                    DesktopControls(state, FakePlayerActions(), visibility, previews, timelineHover = hover)
                }
            }
        }
        compose.waitForIdle()
        // the mouse rests on the timeline a moment: its frame is read
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dex() = capture("player-chrome-dex", samplePlayerState, hover = 0.9f)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tablet() = capture(
        "player-chrome-tablet",
        samplePlayerState.copy(playing = false, rate = 1.5f, abStopMs = -1L, subtitleTrack = null, chapters = emptyList()),
        hover = null,
    )

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("player-chrome-phone", samplePlayerState.copy(canFullscreen = false), hover = 0.3f)

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun dexEnglish() = capture("player-chrome-dex-en", samplePlayerState, hover = 0.9f)
}

/** A night sky with an aurora, for the controls to lie over. */
@Composable
internal fun SampleVideo(modifier: Modifier = Modifier) {
    DrawCanvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF07111C), Color(0xFF0E2A3E), Color(0xFF132F2A))))
        val wave = Path().apply {
            moveTo(0f, size.height * 0.55f)
            cubicTo(size.width * 0.25f, size.height * 0.35f, size.width * 0.55f, size.height * 0.7f, size.width, size.height * 0.4f)
            lineTo(size.width, size.height * 0.55f)
            cubicTo(size.width * 0.6f, size.height * 0.8f, size.width * 0.3f, size.height * 0.5f, 0f, size.height * 0.7f)
            close()
        }
        drawPath(wave, Brush.horizontalGradient(listOf(Color(0x5534D399), Color(0x9934D3B4), Color(0x4422A0C8))))
        for (i in 0 until 40) {
            val x = (i * 97 % 100) / 100f * size.width
            val y = (i * 53 % 37) / 100f * size.height
            drawCircle(Color.White.copy(alpha = 0.7f), 1.2f, Offset(x, y))
        }
    }
}

/** The preview's frame: the same sky, small. */
private fun sampleFrame(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { shader = LinearGradient(0f, 0f, 320f, 180f, 0xFF0E2A3E.toInt(), 0xFF2E8B6F.toInt(), Shader.TileMode.CLAMP) }
    canvas.drawRect(0f, 0f, 320f, 180f, paint)
    return bitmap.asImageBitmap()
}
