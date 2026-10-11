package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
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
 * S-304: the player bars with a pointer over them: the mini player of a medium window shows its
 * hover controls, the transport bar of a large one the time under the pointer.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerBarsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val seekBar = hasContentDescription("Parça konumu") and SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

    private fun capture(name: String, widthClass: WidthClass, theme: ThemeMode, bar: String) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    TestShell(widthClass, startDestination = Destination.Music)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(bar).performMouseInput { moveTo(center) }
        compose.waitForIdle()
        // the pointer rests over the first third of the seek bar
        compose.onNode(seekBar).performMouseInput { moveTo(percentOffset(0.3f, 0.5f)) }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w700dp-h1000dp-mdpi")
    fun miniPlayerHovered() = capture("player-mini-hover-dark", WidthClass.Medium, ThemeMode.Dark, "mini-player")

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun transportBarHovered() = capture("player-transport-hover-light", WidthClass.Large, ThemeMode.Light, "transport-bar")
}
