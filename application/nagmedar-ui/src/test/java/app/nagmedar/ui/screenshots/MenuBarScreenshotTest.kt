package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.MenuBarState
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuItemRow
import app.nagmedar.ui.components.MenuSpec
import app.nagmedar.ui.components.MenuTitle
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.input.menuBarSpecs
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
 * S-302: the eight menus of the menu bar side by side, items and keys from the shortcut table,
 * those the player handles or a later MVP brings dimmed as in the app; a title highlighted from
 * the keyboard; and a large window by touch, whose bar has no menus.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuBarScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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

    /** As in the app: player shortcuts and those of later MVPs are not available in the shell. */
    private val later = setOf(ShortcutId.MediaInfo)

    @Composable
    private fun Menus() {
        val res = LocalContext.current.resources
        val specs = remember(res) {
            menuBarSpecs(res, {}, { ShortcutRegistry[it].scope == ShortcutScope.Global && it !in later })
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            specs.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    row.forEach { OpenMenu(it) }
                }
            }
        }
    }

    @Composable
    private fun OpenMenu(spec: MenuSpec) {
        val colors = NagTheme.colors
        val shape = RoundedCornerShape(10.dp)
        Column(Modifier.width(440.dp)) {
            MenuTitle(spec.title, open = true, onClick = {})
            Column(
                Modifier
                    .padding(top = 2.dp)
                    .shadow(12.dp, shape)
                    .background(colors.surfaceRaised, shape)
                    .border(1.dp, colors.borderStrong, shape)
                    .padding(4.dp),
            ) {
                spec.entries().forEach { entry ->
                    when (entry) {
                        is MenuEntry.Item -> MenuItemRow(entry) {}
                        MenuEntry.Separator -> HorizontalRule(Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun menusDark() = capture("menubar-menus-dark", ThemeMode.Dark, InputMode.Pointer) { Menus() }

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun menusLight() = capture("menubar-menus-light", ThemeMode.Light, InputMode.Pointer) { Menus() }

    /** Alt and Right: the second title has the keyboard, the window's focus stays where it was. */
    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun altHighlightsATitle() {
        val state = MenuBarState()
        capture("menubar-alt-dark", ThemeMode.Dark, InputMode.Pointer) {
            TestShell(WidthClass.Large, startDestination = Destination.Music, menuBar = state)
            LaunchedEffect(Unit) {
                state.onAlt()
                state.onKey(KeyEvent.KEYCODE_DPAD_RIGHT, down = true)
            }
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun largeByTouch() = capture("menubar-large-touch-dark", ThemeMode.Dark, InputMode.Touch) {
        TestShell(WidthClass.Large, startDestination = Destination.Music)
    }
}
