package app.nagmedar.ui.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.screenshots.ShellScreenshotTest
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-306: in a desktop window's caption bar the menu bar (and on narrower windows the page's top
 * bar) is the bar: it keeps clear of the system's buttons, its menus and search take their clicks
 * (gesture exclusion), the brand and the space between still move the window.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptionBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View

    /** Android 15's caption: 40 dp high, its three buttons in the last 144 dp. */
    private val caption = CaptionBar(height = 40.dp, right = 144.dp)

    private fun shell(widthClass: WidthClass, theme: ThemeMode = ThemeMode.Dark, input: InputMode = InputMode.Pointer) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                view = LocalView.current
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    TestShell(widthClass, startDestination = Destination.Music, caption = caption)
                    // where the system draws its buttons, for the eye
                    SystemButtons(Modifier.align(Alignment.TopEnd))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun excluded(x: Float, y: Float): Boolean = view.systemGestureExclusionRects.any { it.contains(x.toInt(), y.toInt()) }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun theMenuBarIsTheCaptionBar() {
        shell(WidthClass.Large)
        val window = view.width
        val field = compose.onNode(hasText("Sanatçı, albüm, parça ya da komut") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("clear of the buttons: ${field.right}", field.right <= window - 144f)
        assertTrue("the field takes its clicks", excluded(field.center.x, field.center.y))
        val menu = compose.onNodeWithText("Medya").fetchSemanticsNode().boundsInRoot
        assertTrue("a menu takes its clicks", excluded(menu.center.x, menu.center.y))
        // the brand and the space after the menus move the window
        val brand = compose.onNodeWithText("Nağmedar").fetchSemanticsNode().boundsInRoot
        assertFalse(excluded(brand.center.x, brand.center.y))
        assertFalse(excluded(field.left - 40f, field.center.y))
    }

    @Test @Config(qualifiers = "tr-w700dp-h800dp-mdpi")
    fun theTopBarIsTheCaptionBarOfANarrowWindow() {
        shell(WidthClass.Medium, ThemeMode.Light, InputMode.Pointer)
        val window = view.width
        val search = compose.onNode(hasContentDescription("Ara ya da komut çalıştır") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("clear of the buttons: ${search.right}", search.right <= window - 144f)
        assertTrue(excluded(search.center.x, search.center.y))
        // the top bar's title, above the page's own
        val title = compose.onAllNodesWithText("Müzik").fetchSemanticsNodes().minBy { it.boundsInRoot.top }.boundsInRoot
        assertFalse(excluded(title.center.x, title.center.y))
    }

    private fun capture(name: String) {
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dexScreenshot() {
        shell(WidthClass.Large)
        capture("caption-dex-dark")
    }

    @Test @Config(qualifiers = "tr-w900dp-h700dp-mdpi")
    fun narrowWindowScreenshot() {
        shell(WidthClass.Expanded, ThemeMode.Light, InputMode.Pointer)
        capture("caption-window-light")
    }
}

/** The caption's minimise, maximise and close buttons as Android 15 draws them, roughly. */
@Composable
private fun SystemButtons(modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    Row(modifier.width(144.dp).height(40.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf("–", "□", "✕").forEach { Text(it, color = colors.textSecondary) }
    }
}
