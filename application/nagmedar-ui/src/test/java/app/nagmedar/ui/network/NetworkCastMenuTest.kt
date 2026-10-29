package app.nagmedar.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.LocalScreenCommands
import app.nagmedar.ui.components.ScreenCommands
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-1302: a file's menu in the network browser plays it on a device found, or opens the device picker for it. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
class NetworkCastMenuTest {

    @get:Rule
    val compose = createComposeRule()

    private class FakeRenderers : RemoteRenderers {
        val salon = RemoteRenderer("Chromecast:salon tv", "Salon TV")
        val played = mutableListOf<Triple<RemoteRenderer, List<String>, Int>>()
        val chosen = mutableListOf<Pair<List<String>, Int>>()

        override fun available() = listOf(salon)

        override fun play(renderer: RemoteRenderer, entries: List<NetworkEntry>, index: Int) {
            played += Triple(renderer, entries.map { it.name }, index)
        }

        override fun choose(entries: List<NetworkEntry>, index: Int) {
            chosen += entries.map { it.name } to index
        }
    }

    private val renderers = FakeRenderers()

    @Before
    fun hook() {
        RemoteRendererHook.renderers = renderers
    }

    @After
    fun unhook() {
        RemoteRendererHook.renderers = null
    }

    private fun rightClick(name: String) {
        compose.onNode(hasText(name) and hasClickAction()).performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
        }
        compose.waitForIdle()
    }

    @Test
    fun aFilePlaysOnADeviceOrThePickerOpens() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalScreenCommands provides ScreenCommands(), LocalWidthClass provides WidthClass.Large) {
                    Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { SampleNetworkBrowser() }
                }
            }
        }
        compose.onNodeWithText("Salondaki NAS").performClick()
        compose.onNodeWithText("Çay Bahçesi.mkv").assertExists()
        rightClick("Çay Bahçesi.mkv")
        compose.onNode(hasText("Salon TV cihazında oynat") and hasClickAction()).performClick()
        compose.waitForIdle()
        val (device, files, index) = renderers.played.single()
        assertEquals(renderers.salon, device)
        // with the folder's other films, from the one chosen
        assertEquals("Çay Bahçesi.mkv", files[index])
        rightClick("Çay Bahçesi.mkv")
        compose.onNode(hasText("Bir cihazda oynat…") and hasClickAction()).performClick()
        compose.waitForIdle()
        val (picked, at) = renderers.chosen.single()
        assertEquals("Çay Bahçesi.mkv", picked[at])
    }
}
