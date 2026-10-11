package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.library.MovieSamples
import app.nagmedar.ui.library.MoviesContent
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-802: technical badges on the cards and in the table, and the details panel, in a DeX window and on a phone. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoviesScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, grid: Boolean, selected: String?) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    MoviesContent(
                        items = MovieSamples.items,
                        tech = MovieSamples.tech,
                        techState = MovieSamples::state,
                        grid = grid,
                        sort = null,
                        onSortChange = {},
                        selected = selected,
                        onSelect = {},
                        detailsRequested = false,
                        onShowDetails = {},
                        onCloseDetails = {},
                        onPlay = {},
                        menu = { _, _, _ -> emptyList() },
                    )
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun gridWithDetailsDark() = capture("movies-dex-details-dark", ThemeMode.Dark, InputMode.Pointer, grid = true, MovieSamples.kuzeyIsigi.key)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun gridWithDetailsLight() = capture("movies-dex-details-light", ThemeMode.Light, InputMode.Pointer, grid = true, MovieSamples.kuzeyIsigi.key)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun tableDark() = capture("movies-dex-table-dark", ThemeMode.Dark, InputMode.Pointer, grid = false, MovieSamples.demirVadi.key)

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun phoneGridDark() = capture("movies-phone-dark", ThemeMode.Dark, InputMode.Touch, grid = true, null)
}
