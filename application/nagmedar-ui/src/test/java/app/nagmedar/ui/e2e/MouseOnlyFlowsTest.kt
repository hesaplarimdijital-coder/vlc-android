package app.nagmedar.ui.e2e

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import app.nagmedar.ui.tvlab.BuiltInProfiles
import app.nagmedar.ui.tvlab.Step
import app.nagmedar.ui.tvlab.sampleFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * S-1403: the main flows of a DeX window with the mouse alone: the TV Lab from the sidebar, a file
 * opened, the target chosen, the chart's data saved, a profile edited and the report read; the
 * menu bar's menus and the command palette opened by clicking. No key is pressed.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// tall enough for the TV Lab's whole page: what is clicked is on the screen
@Config(qualifiers = "tr-w1280dp-h1400dp-mdpi")
class MouseOnlyFlowsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val window = DexWindow()

    @Before
    fun show() {
        ShortcutRegistry.resume()
        window.picked = Uri.parse(sampleFile.uri)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) { window.Content() }
        }
        compose.waitForIdle()
    }

    /** A left click with the mouse, in the middle of what says [text]. */
    private fun click(text: String, substring: Boolean = false) = click(compose.onNode(hasText(text, substring = substring) and hasClickAction()))

    private fun click(node: SemanticsNodeInteraction) {
        node.performMouseInput { click(center) }
        compose.waitForIdle()
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > until) fail("waited for $what")
            compose.waitForIdle()
            Thread.sleep(10)
        }
    }

    @Test
    fun theTvLabFromTheSidebar() {
        click("TV Lab")
        assertEquals(Destination.TvLab, window.destination)
        click("Dosya seçin")
        waitFor("the analysis") { window.lab.bitrateStep == Step.Done }
        compose.onNodeWithText("2 kritik · 3 uyarı").assertExists()

        // the target from its menu
        click("LG OLED B4 · webOS 24 · USB")
        click("Tab S11 Ultra · One UI 8 · HDMI")
        assertEquals(BuiltInProfiles.tabS11Ultra, window.lab.profile)

        // the chart's data, saved where the picker says
        val csv = ByteArrayOutputStream()
        val place = Uri.parse("content://nagmedar.tests/bitrate.csv")
        shadowOf(compose.activity.contentResolver).registerOutputStream(place, csv)
        window.saved = place
        click("Dışa aktar")
        click("Veriler (CSV)…")
        assertEquals(listOf("OpenDocument", "CreateFile"), window.asked)
        waitFor("the data saved") { csv.size() > 0 }
        val lines = csv.toString(Charsets.UTF_8.name()).trimEnd().lines()
        assertEquals("second,total_kbps,video_kbps,average_5s_kbps", lines.first())
        assertEquals(61, lines.size)

        // the profile edited: DTS taken from what it plays
        click("Profili düzenle")
        compose.onNodeWithTag("tvlab-editor").assertExists()
        compose.onNode(hasText("DTS") and hasClickAction()).performScrollTo()
        click("DTS")
        click("Kaydet")
        assertFalse(window.lab.profile.allowsAudio("dts"))
        assertTrue(window.lab.profile.allowsAudio("truehd"))

        // the report, read and closed
        click("Rapor")
        compose.onNodeWithText("TV Lab raporu").assertExists()
        click("Kapat")
        compose.onNodeWithText("TV Lab raporu").assertDoesNotExist()
    }

    @Test
    fun theMenuBarsMenus() {
        click("Araçlar")
        // the menu's item, with its keys; the sidebar has a TV Lab too
        click(compose.onNode(hasText("TV Lab") and hasText("Ctrl+Shift+T") and hasClickAction()))
        assertEquals(Destination.TvLab, window.destination)
        click("Yardım")
        click("Kısayol kartı")
        assertEquals(Destination.ShortcutCard, window.destination)
    }

    @Test
    fun theCommandPaletteByClicking() {
        click("Ctrl+K", substring = true)
        compose.onNodeWithTag("palette").assertExists()
        // its screens, then the TV Lab among them: the row says its section
        click("Ekranlar")
        click(compose.onNode(hasText("TV Lab") and hasText("Araçlar") and hasClickAction()))
        assertEquals(Destination.TvLab, window.destination)
        compose.onNodeWithTag("palette").assertDoesNotExist()
    }
}
