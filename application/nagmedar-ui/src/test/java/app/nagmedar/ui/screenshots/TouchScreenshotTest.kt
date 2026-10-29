package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.UndoBarHost
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.input.LocalHardwareKeyboard
import app.nagmedar.ui.playback.FakeQueue
import app.nagmedar.ui.playback.QueueEntry
import app.nagmedar.ui.playback.QueuePage
import app.nagmedar.ui.playback.QueuePanel
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
 * S-310: "Sıradaki" beside the music on a tablet by touch with a row half swiped, beside it in
 * DeX, over the screen on a phone; 1.0.1: empty beside them, and on a phone a page of its own
 * over the whole window, empty, and with the undo bar of a row taken off (no keycap by touch).
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
    private fun QueueShell(widthClass: WidthClass, entries: List<QueueEntry> = FakeQueue.sample, current: Int = 0) {
        val queue = remember { FakeQueue(entries, current) }
        TestShell(widthClass, startDestination = Destination.Music, queueOpen = true, overlay = {
            QueueSheet(queue.entries, queue.current, queue, compact = false, onClose = {}, modifier = Modifier.align(Alignment.TopEnd))
        })
    }

    /** 1.0.1: a phone's "Sıradaki", a page over the whole window; [removed] leaves its undo bar over it. */
    @Composable
    private fun QueuePhone(entries: List<QueueEntry> = FakeQueue.sample, current: Int = 0, removed: String? = null) {
        val queue = remember { FakeQueue(entries, current) }
        val res = LocalContext.current.resources
        val undo = remember {
            UndoBarState().apply {
                removed?.let { title -> show(res.getString(R.string.nag_queue_removed, title), res.getString(R.string.nag_sc_undo), "Ctrl+Z") {} }
            }
        }
        // a touch phone: no keyboard at hand
        CompositionLocalProvider(LocalHardwareKeyboard provides false) {
            TestShell(WidthClass.Compact, startDestination = Destination.Music, queueOpen = true, overlay = {
                QueuePage(open = true, onClose = {}, undo = undo) { pageModifier ->
                    QueuePanel(queue.entries, queue.current, queue, {}, pageModifier, undo, NagTheme.colors.background, page = true)
                }
                Column(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { UndoBarHost(undo) }
            })
        }
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
    ) { QueueShell(WidthClass.Expanded) }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
    fun queueDex() = capture("queue-dex-dark", ThemeMode.Dark, InputMode.Pointer) { QueueShell(WidthClass.Large) }

    /** 1.0.1: nothing queued beside a tablet's music, by touch. */
    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun queueEmptyTablet() = capture("queue-empty-tablet-light", ThemeMode.Light, InputMode.Touch) { QueueShell(WidthClass.Expanded, emptyList(), -1) }

    /** 1.0.1: nothing queued beside DeX's music, in English. */
    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi-qwerty")
    fun queueEmptyDexEnglish() = capture("queue-empty-dex-dark-en", ThemeMode.Dark, InputMode.Pointer) { QueueShell(WidthClass.Large, emptyList(), -1) }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun queuePhone() = capture("queue-phone-light", ThemeMode.Light, InputMode.Touch) { QueuePhone() }

    /** S-1501: Sıradaki in English (Up next). */
    @Test @Config(qualifiers = "en-w411dp-h891dp-hdpi")
    fun queuePhoneEnglish() = capture("queue-phone-light-en", ThemeMode.Light, InputMode.Touch) { QueuePhone() }

    /** 1.0.1: nothing plays and nothing is queued: the page is the empty state's, over the whole window. */
    @Test @Config(qualifiers = "tr-w384dp-h832dp-xhdpi")
    fun queueEmptyPhone() = capture("queue-empty-phone-dark", ThemeMode.Dark, InputMode.Touch) { QueuePhone(emptyList(), -1) }

    /** 1.0.1: a row taken off on a touch phone: its undo bar has no keycap. */
    @Test @Config(qualifiers = "tr-w384dp-h832dp-xhdpi")
    fun queueUndoPhone() = capture("queue-undo-phone-dark", ThemeMode.Dark, InputMode.Touch) {
        QueuePhone(FakeQueue.sample.filter { it.title != "Fener" }, 0, removed = "Fener")
    }
}
