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
import app.nagmedar.ui.SampleScreen
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.search.SearchClock
import app.nagmedar.ui.search.SearchContent
import app.nagmedar.ui.search.SearchSamples
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-311: the search page in the shell: "ada" in a DeX window finds the artist's tracks; on a
 * tablet and a phone the same.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun realClock() {
        SearchClock.nanoTime = System::nanoTime
    }

    private fun capture(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode, query: String) {
        // a search takes 2 ms in every picture
        var now = 0L
        SearchClock.nanoTime = {
            now += SEARCH_STEP_NS
            now
        }
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    TestShell(widthClass, startDestination = Destination.Search) { destination, _ ->
                        if (destination == Destination.Search) {
                            SearchContent(
                                data = SearchSamples.data,
                                query = query,
                                onQueryChange = {},
                                onPlayTrack = { _, _ -> },
                            )
                        } else {
                            SampleScreen(destination)
                        }
                    }
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
    fun dex() = capture("search-dex-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer, "ada")

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tablet() = capture("search-tablet-dark", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch, "ada")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("search-phone-light", WidthClass.Compact, ThemeMode.Light, InputMode.Touch, "ada")

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun syntax() = capture("search-empty-light", WidthClass.Large, ThemeMode.Light, InputMode.Pointer, "")

    /** S-1501: in English, the results (English capitals: MUSIC) and the syntax. */
    @Test @Config(qualifiers = "en-w1000dp-h800dp-mdpi")
    fun tabletEnglish() = capture("search-tablet-dark-en", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch, "ada")

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun syntaxEnglish() = capture("search-empty-light-en", WidthClass.Large, ThemeMode.Light, InputMode.Pointer, "")
}

private const val SEARCH_STEP_NS = 2_000_000L
