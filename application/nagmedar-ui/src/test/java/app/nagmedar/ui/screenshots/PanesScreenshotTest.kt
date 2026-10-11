package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.library.MovieOrder
import app.nagmedar.ui.library.MovieSamples
import app.nagmedar.ui.library.MoviesHeader
import app.nagmedar.ui.library.MoviesLibrary
import app.nagmedar.ui.library.MoviesView
import app.nagmedar.ui.library.VideoDetailsPanes
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
 * S-303: the films with the details of one in the shell: over the list on a phone, beside it with
 * the rail on a tablet, and the sidebar, the list and the details as three panes in a DeX window.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanesScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode, view: MoviesView = MoviesView.Grid) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    TestShell(widthClass, startDestination = Destination.Movies, screen = { destination, _ ->
                        if (destination == Destination.Movies) FilmsWithDetails(view)
                    })
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
    private fun FilmsWithDetails(view: MoviesView) {
        val library = MovieSamples.library
        VideoDetailsPanes(
            selected = MovieSamples.kuzeyIsigi,
            detailsRequested = true,
            tech = MovieSamples.tech,
            techState = MovieSamples::state,
            onPlay = {},
            onCloseDetails = {},
        ) {
            Column(Modifier.fillMaxSize()) {
                MoviesHeader(library, view, {}, MovieOrder.Added, {}, {})
                MoviesLibrary(
                    library = library,
                    tech = MovieSamples.tech,
                    view = view,
                    filters = emptySet(),
                    onFiltersChange = {},
                    order = MovieOrder.Added,
                    folder = null,
                    onOpenFolder = {},
                    sort = null,
                    onSortChange = {},
                    selected = MovieSamples.kuzeyIsigi.key,
                    onSelect = {},
                    onShowDetails = {},
                    onPlay = {},
                    menu = { _, _, _ -> emptyList() },
                )
            }
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("panes-phone-dark", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tablet() = capture("panes-tablet-light", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dex() = capture("panes-dex-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi")
    fun monitor() = capture("panes-xlarge-table-light", WidthClass.ExtraLarge, ThemeMode.Light, InputMode.Pointer, MoviesView.Table)
}
