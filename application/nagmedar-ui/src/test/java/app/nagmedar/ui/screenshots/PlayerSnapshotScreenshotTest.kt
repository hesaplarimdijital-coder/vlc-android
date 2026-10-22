package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuItemRow
import app.nagmedar.ui.player.ChromeVisibility
import app.nagmedar.ui.player.DesktopControls
import app.nagmedar.ui.player.FakePlayerActions
import app.nagmedar.ui.player.FakePreviews
import app.nagmedar.ui.player.samplePlayerState
import app.nagmedar.ui.player.snapshot.HdrOutcome
import app.nagmedar.ui.player.snapshot.SnapshotCard
import app.nagmedar.ui.player.snapshot.SnapshotFailure
import app.nagmedar.ui.player.snapshot.SnapshotFormat
import app.nagmedar.ui.player.snapshot.SnapshotOutcome
import app.nagmedar.ui.player.snapshot.SnapshotSettings
import app.nagmedar.ui.player.snapshotMenu
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-606: the card a screenshot leaves over the video, above the control bar: saved with HDR
 * tone mapped and the subtitles in (DeX, in English, on a phone's width); not saved, the folder
 * gone; and the screenshot button's menu.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerSnapshotScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, overVideo: @Composable BoxScope.() -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        val visibility = ChromeVisibility().apply {
            onMouseMoved()
            overBars = true
        }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    SampleVideo(Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                    DesktopControls(samplePlayerState, FakePlayerActions(), visibility, FakePreviews())
                    overVideo()
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Composable
    private fun BoxScope.Card(outcome: SnapshotOutcome) {
        SnapshotCard(
            outcome = outcome,
            saving = false,
            onOpen = {},
            onShare = {},
            onChooseFolder = {},
            onDismiss = {},
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 120.dp),
            stays = true,
        )
    }

    private fun saved(hdr: HdrOutcome = HdrOutcome.ToneMapped) = SnapshotOutcome.Saved(
        uri = Uri.parse("content://media/external/images/media/42"),
        fileName = "Kuzey Işığı 01-11-45.png",
        folder = "Pictures/Nağmedar",
        format = SnapshotFormat.Png,
        hdr = hdr,
        subtitles = true,
        thumbnail = sampleShot(),
        width = 3840,
        height = 2160,
    )

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dex() = capture("player-snapshot-dex") { Card(saved()) }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun dexEnglish() = capture("player-snapshot-dex-en") { Card(saved(HdrOutcome.Kept)) }

    @Test @Config(qualifiers = "tr-w728dp-h880dp-mdpi")
    fun folderGone() = capture("player-snapshot-failed-half") { Card(SnapshotOutcome.Failed(SnapshotFailure.Folder)) }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("player-snapshot-phone") { Card(saved(HdrOutcome.None)) }

    /** The camera's menu, open above it: the subtitles in, PNG, HDR to SDR, the default folder. */
    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun menu() = capture("player-snapshot-menu-dex") {
        val colors = NagTheme.colors
        val shape = RoundedCornerShape(10.dp)
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 120.dp, bottom = 64.dp)
                .width(280.dp)
                .shadow(12.dp, shape)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                .padding(4.dp),
        ) {
            snapshotMenu(SnapshotSettings(), FakePlayerActions()).forEach { entry ->
                when (entry) {
                    is MenuEntry.Item -> MenuItemRow(entry) {}
                    MenuEntry.Separator -> HorizontalRule(Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

/** A screenshot of the night sky, small: the card's picture. */
private fun sampleShot() = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).also { bitmap ->
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { shader = LinearGradient(0f, 0f, 320f, 180f, 0xFF0E2A3E.toInt(), 0xFF2E8B6F.toInt(), Shader.TileMode.CLAMP) }
    canvas.drawRect(0f, 0f, 320f, 180f, paint)
    // a line of subtitles
    canvas.drawRect(110f, 150f, 210f, 158f, Paint().apply { color = 0xFFEDEDED.toInt() })
}.asImageBitmap()
