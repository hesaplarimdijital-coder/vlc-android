package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.search.PaletteItem
import app.nagmedar.ui.search.PaletteKind
import app.nagmedar.ui.search.PaletteSurface
import app.nagmedar.ui.search.commandItems
import app.nagmedar.ui.search.screenItems
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
 * S-305: the command palette over the shell: a query in a DeX window, with the selected hit
 * beside the list; on a tablet by touch; on a phone with no query, the recent items first.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaletteScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** As in the app: the player's commands and those of later MVPs are not offered in the shell. */
    private val later = emptySet<ShortcutId>()

    @Composable
    private fun items(): List<PaletteItem> {
        val res = LocalContext.current.resources
        return commandItems(res, { ShortcutRegistry[it].scope == ShortcutScope.Global && it !in later }, {}) + screenItems(res) {} + listOf(
            PaletteItem("media:kuzey", PaletteKind.Media, "Kuzeyden Esen", "Parça · Ada Karan", icon = NagIcons.Music) {},
            PaletteItem("media:sabaha", PaletteKind.Media, "Sabaha Kadar", "Parça · Ada Karan", icon = NagIcons.Music) {},
            PaletteItem("media:acik", PaletteKind.Media, "Açık Deniz", "Parça · Deniz Aral", icon = NagIcons.Music) {},
        )
    }

    private fun capture(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode, query: String, recent: List<String>) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    TestShell(widthClass, startDestination = Destination.Music)
                    // the dialog's dim, drawn here: the window holds only the activity
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                    PaletteSurface(items(), recent, onRun = {}, onDismiss = {}, initialQuery = query)
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
    fun dexQuery() = capture("palette-dex-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer, "aç", emptyList())

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletQuery() = capture("palette-tablet-light", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch, "kuzey", emptyList())

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phoneRecent() = capture(
        "palette-phone-light", WidthClass.Compact, ThemeMode.Light, InputMode.Touch, "",
        listOf("media:sabaha", "screen:${Destination.Settings.route}", "command:${ShortcutId.OpenFile.name}"),
    )

    /** S-1501: in English. */
    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi-qwerty")
    fun dexEnglish() = capture("palette-dex-dark-en", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer, "open", emptyList())
}
