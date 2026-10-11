package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.MultiSelection
import app.nagmedar.ui.library.MovieOrder
import app.nagmedar.ui.library.MovieSamples
import app.nagmedar.ui.library.MoviesLibrary
import app.nagmedar.ui.library.MoviesView
import app.nagmedar.ui.playback.FakeQueue
import app.nagmedar.ui.playback.QueueSheet
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
 * S-310: "Sıradaki" beside the films on a tablet by touch with a row half swiped, beside them in
 * DeX, over the screen on a phone; films selected by touch on a tablet, marked, with the bar.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TouchScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, then: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
        then()
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Composable
    private fun QueueShell(widthClass: WidthClass, compact: Boolean) {
        val queue = remember { FakeQueue(FakeQueue.sample, current = 0) }
        TestShell(widthClass, startDestination = Destination.Movies, queueOpen = true, overlay = {
            QueueSheet(queue.entries, queue.current, queue, compact = compact, onClose = {}, modifier = Modifier.align(Alignment.TopEnd))
        })
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun queueTablet() = capture(
        "queue-tablet-dark", ThemeMode.Dark, InputMode.Touch,
        then = {
            // a row half swiped, its finger still down: "Kaldır" under it
            // from the middle of the row: its end is the handle, which drags
            compose.onNodeWithTag("queue-Liman Işıkları").performTouchInput {
                down(Offset(width * 0.7f, centerY))
                repeat(6) { moveBy(Offset(-width * 0.05f, 0f)) }
            }
        },
    ) { QueueShell(WidthClass.Expanded, compact = false) }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun queueDex() = capture("queue-dex-dark", ThemeMode.Dark, InputMode.Pointer) { QueueShell(WidthClass.Large, compact = false) }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun queuePhone() = capture("queue-phone-light", ThemeMode.Light, InputMode.Touch) { QueueShell(WidthClass.Compact, compact = true) }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun filmsSelectedByTouch() {
        val selection = MultiSelection().apply {
            startSelecting(MovieSamples.demirVadi.key)
            toggle(MovieSamples.maviKanal.key)
        }
        capture("films-touch-selection-tablet-light", ThemeMode.Light, InputMode.Touch) {
            MoviesLibrary(
                library = MovieSamples.library,
                tech = MovieSamples.tech,
                view = MoviesView.Grid,
                filters = emptySet(),
                onFiltersChange = {},
                order = MovieOrder.Added,
                folder = null,
                onOpenFolder = {},
                sort = null,
                onSortChange = {},
                selected = null,
                onSelect = {},
                onShowDetails = {},
                onPlay = {},
                menu = { _, _, _ -> emptyList() },
                selection = selection,
            )
        }
    }
}
