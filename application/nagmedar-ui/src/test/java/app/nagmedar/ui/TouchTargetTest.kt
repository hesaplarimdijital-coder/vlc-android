package app.nagmedar.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.input.PhysicalKeys
import app.nagmedar.ui.playback.FakeQueue
import app.nagmedar.ui.playback.QueuePanel
import app.nagmedar.ui.screens.AppearanceSection
import app.nagmedar.ui.screens.InputSection
import app.nagmedar.ui.screens.KeyboardMap
import app.nagmedar.ui.screens.ShortcutEditor
import app.nagmedar.ui.screens.tableOf
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-310: by touch every target is at least 48 dp each way, on a phone and a tablet: the shell,
 * "Sıradaki", the settings and the shortcut editor.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TouchTargetTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun reset() {
        PhysicalKeys.reset()
    }

    private fun touch(content: @Composable () -> Unit) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun name(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            ?: node.config.getOrNull(SemanticsProperties.TestTag)
            ?: "?"

    /** Every target of what is shown, enabled, at least 48 dp wide and high; those that are not, by name. */
    private fun assertTargets(what: String) {
        val least = with(compose.density) { 47.5.dp.toPx() }
        val small = compose.onAllNodes(hasClickAction() and isEnabled()).fetchSemanticsNodes()
            .filter { it.size.width < least || it.size.height < least }
            .map { node -> "${name(node)} ${(node.size.width / compose.density.density).toInt()}×${(node.size.height / compose.density.density).toInt()} dp" }
        assertTrue("$what: ${small.joinToString("; ")}", small.isEmpty())
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-mdpi")
    fun phoneShell() {
        touch { TestShell(WidthClass.Compact, startDestination = Destination.Music) }
        assertTargets("phone shell")
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletShell() {
        touch { TestShell(WidthClass.Expanded, startDestination = Destination.Music) }
        assertTargets("tablet shell")
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-mdpi")
    fun upNext() {
        val queue = FakeQueue(FakeQueue.sample, current = 0)
        touch { QueuePanel(queue.entries, queue.current, queue, onClose = {}, modifier = Modifier.fillMaxSize()) }
        assertTargets("up next")
    }

    @Test @Config(qualifiers = "tr-w411dp-h2400dp-mdpi")
    fun settings() {
        val prefs = NagmedarPreferences(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("touch-targets", Context.MODE_PRIVATE))
        touch {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AppearanceSection(prefs)
                InputSection(prefs)
                KeyboardMap(tableOf(emptyMap()))
            }
        }
        assertTargets("settings")
    }

    // every shortcut's row on the screen at once (S-611, S-1303 and S-207 brought five more)
    @Test @Config(qualifiers = "tr-w411dp-h6400dp-mdpi")
    fun shortcutEditor() {
        touch { Column { ShortcutEditor(emptyMap(), {}) } }
        assertTargets("shortcut editor")
    }
}
