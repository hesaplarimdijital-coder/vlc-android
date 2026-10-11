package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.library.MovieFilter
import app.nagmedar.ui.library.MovieOrder
import app.nagmedar.ui.library.MovieSamples
import app.nagmedar.ui.library.MoviesHeader
import app.nagmedar.ui.library.MoviesLibrary
import app.nagmedar.ui.library.MoviesView
import app.nagmedar.ui.library.SeriesContent
import app.nagmedar.ui.shell.LocalNavKind
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-801: films with chips, the shelf and folders; series and one show; in a DeX window and on a phone. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoLibraryScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                val kind = if (input == InputMode.Pointer) NavKind.Sidebar else NavKind.BottomBar
                CompositionLocalProvider(LocalNavKind provides kind) {
                    Column(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
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
    private fun films(view: MoviesView, filters: Set<MovieFilter> = emptySet(), folder: String? = null, order: MovieOrder = MovieOrder.Added) {
        val library = MovieSamples.library
        MoviesHeader(library, view, {}, order, {}, {})
        MoviesLibrary(
            library = library,
            tech = MovieSamples.tech,
            view = view,
            filters = filters,
            onFiltersChange = {},
            order = order,
            folder = folder,
            onOpenFolder = {},
            sort = null,
            onSortChange = {},
            selected = null,
            onSelect = {},
            detailsRequested = false,
            onShowDetails = {},
            onCloseDetails = {},
            onPlay = {},
            menu = { _, _, _ -> emptyList() },
        )
    }

    @Composable
    private fun series(open: String? = null) {
        SeriesContent(
            shows = MovieSamples.library.shows,
            tech = MovieSamples.tech,
            open = open,
            onOpen = {},
            onClose = {},
            season = null,
            onSeasonChange = {},
            onPlay = {},
            menu = { _, _ -> emptyList() },
        )
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun filmsDark() = capture("films-dex-dark", ThemeMode.Dark, InputMode.Pointer) { films(MoviesView.Grid) }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun filmsFilteredLight() = capture("films-dex-filtered-light", ThemeMode.Light, InputMode.Pointer) {
        films(MoviesView.Grid, setOf(MovieFilter.UHD, MovieFilter.DolbyVision), order = MovieOrder.Resolution)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun foldersDark() = capture("films-dex-folders-dark", ThemeMode.Dark, InputMode.Pointer) { films(MoviesView.Folders) }

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun filmsPhone() = capture("films-phone-dark", ThemeMode.Dark, InputMode.Touch) { films(MoviesView.Grid) }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun seriesDark() = capture("series-dex-dark", ThemeMode.Dark, InputMode.Pointer) { series() }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun showDark() = capture("series-dex-show-dark", ThemeMode.Dark, InputMode.Pointer) {
        series(MovieSamples.library.shows.first { it.title == "Kuzey Rüzgârı" }.key)
    }

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun showPhone() = capture("series-phone-show-dark", ThemeMode.Dark, InputMode.Touch) {
        series(MovieSamples.library.shows.first { it.title == "Kuzey Rüzgârı" }.key)
    }
}
