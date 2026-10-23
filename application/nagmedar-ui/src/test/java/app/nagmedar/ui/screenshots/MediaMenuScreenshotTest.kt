package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuItemRow
import app.nagmedar.ui.library.mediaMenu
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * A film's menu in the library, as right click on DeX, a long press or the Menu key opens it:
 * "Paylaş…" and "Dosyada göster" beside the media information, then the film's tools.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaMenuScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { FilmMenu() }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    /** The menu of a film on the device's storage, drawn open where the pop-up would be. */
    @Composable
    private fun FilmMenu() {
        val context = LocalContext.current
        val entries = remember(context) {
            val film = MLServiceLocator.getAbstractMediaWrapper(Uri.parse("file:///storage/emulated/0/Movies/Kuzey%20I%C5%9F%C4%B1%C4%9F%C4%B1.mkv")).apply {
                type = MediaWrapper.TYPE_VIDEO
                title = "Kuzey Işığı"
            }
            mediaMenu(context, film, play = {})
        }
        val colors = NagTheme.colors
        val shape = RoundedCornerShape(10.dp)
        // as wide as the pop-up makes itself: its longest line, between 220 and 420 dp
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(min = 220.dp, max = 420.dp)
                .width(IntrinsicSize.Max)
                .shadow(12.dp, shape)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                .padding(4.dp),
        ) {
            entries.forEach { entry ->
                when (entry) {
                    is MenuEntry.Item -> MenuItemRow(entry) {}
                    MenuEntry.Separator -> HorizontalRule(Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun filmMenuDex() = capture("media-menu-dex-dark", ThemeMode.Dark)

    @Test @Config(qualifiers = "en-w1000dp-h800dp-mdpi")
    fun filmMenuTabletEnglish() = capture("media-menu-tablet-light-en", ThemeMode.Light)
}
