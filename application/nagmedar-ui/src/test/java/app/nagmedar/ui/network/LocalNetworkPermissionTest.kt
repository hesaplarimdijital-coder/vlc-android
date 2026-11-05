package app.nagmedar.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.network.LocalNetworkState
import app.nagmedar.ui.R
import app.nagmedar.ui.components.LocalScreenCommands
import app.nagmedar.ui.components.ScreenCommands
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Android 17: the network browser without the local network permission. The servers' pane says what it is for, with
 * the way to give it; a server on the local network asks for it while Android still asks, and opens once it is given;
 * one on the internet opens; refused for good, a server that cannot be reached says why. Nothing is looked for on the
 * network meanwhile, and the saved servers stay listed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
class LocalNetworkPermissionTest {

    @get:Rule
    val compose = createComposeRule()

    private val commands = ScreenCommands()
    private var asked = 0

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalScreenCommands provides commands, LocalWidthClass provides WidthClass.Large) {
                    Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
                }
            }
        }
    }

    private fun row(name: String) = compose.onNode(hasText(name) and hasClickAction())

    @Test
    fun theServersPaneSaysWhyAndAsks() {
        val source = NetworkSamples.source()
        show {
            SampleNetworkBrowser(source = source, lan = LanState.NoPermission, access = LocalNetworkState.Ask, onAllowLocalNetwork = { asked++ }) {
                it.found(source)
            }
        }
        compose.onNodeWithTag("network-permission").assertExists()
        compose.onNodeWithText("Yerel ağ izni").assertExists()
        // the saved servers stay; nothing found on the network is listed without it
        compose.onNodeWithText("Salondaki NAS").assertExists()
        compose.onNodeWithText("Bu ağda").assertDoesNotExist()
        compose.onNodeWithText("İzin ver").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun aServerOnTheLocalNetworkAsksAndOpensOnceGiven() {
        val source = NetworkSamples.source()
        var access by mutableStateOf(LocalNetworkState.Denied)
        var model: NetworkModel? = null
        show {
            SampleNetworkBrowser(source = source, lan = if (access == LocalNetworkState.Granted) LanState.Available else LanState.NoPermission,
                access = access, onAllowLocalNetwork = { asked++ }) { model = it }
        }
        compose.onNodeWithText("Yerel ağ izni kapalı").assertExists()
        // nas.local: the question, not a reading that would wait for its time-out
        row("Salondaki NAS").performClick()
        compose.waitForIdle()
        assertEquals(1, asked)
        assertNull(model?.folder)
        // given: the server waiting opens
        access = LocalNetworkState.Granted
        compose.waitForIdle()
        assertEquals(NetworkSamples.NAS, model?.folder?.url)
        assertTrue(source.reads.any { it.first == NetworkSamples.NAS })
    }

    @Test
    fun aServerOnTheInternetOpensWithoutIt() {
        val source = NetworkSamples.source()
        var model: NetworkModel? = null
        show {
            SampleNetworkBrowser(source = source, lan = LanState.NoPermission, access = LocalNetworkState.Denied, onAllowLocalNetwork = { asked++ }) {
                model = it
            }
        }
        row("Arşiv (FTP)").performClick()
        compose.waitForIdle()
        assertEquals(0, asked)
        assertEquals(NetworkSamples.FTP, model?.folder?.url)
    }

    @Test
    fun refusedForGoodAServerThatCannotBeReachedSaysWhy() {
        val source = NetworkSamples.source()
        val store = NetworkSamples.store().apply {
            rows.value = rows.value + SavedServer(4, "Müzik kutusu", "smb://192.168.1.20/Muzik", signedIn = false)
        }
        show {
            SampleNetworkBrowser(source = source, store = store, lan = LanState.NoPermission, access = LocalNetworkState.Blocked,
                onAllowLocalNetwork = { asked++ })
        }
        compose.onNodeWithText("Ayarları aç").assertExists()
        // Android no longer asks: the server is tried, and its failure says why
        row("Müzik kutusu").performClick()
        compose.waitForIdle()
        assertEquals(0, asked)
        compose.onNodeWithTag("network-unreachable").assertExists()
        compose.onNodeWithText("192.168.1.20 yerel ağınızda ve Nağmedar yerel ağ izni olmadan oraya ulaşamaz.").assertExists()
        compose.onNode(hasText("Ayarları aç") and hasClickAction() and hasAnyAncestor(hasTestTag("network-unreachable"))).performClick()
        assertEquals(1, asked)
    }

    @Test
    fun withThePermissionNothingIsSaid() {
        val source = NetworkSamples.source()
        show { SampleNetworkBrowser(source = source, access = LocalNetworkState.Granted) { it.found(source) } }
        compose.onNodeWithTag("network-permission").assertDoesNotExist()
        compose.onNodeWithText("Bu ağda").assertExists()
    }

    @Test
    fun eachStateHasItsWords() {
        assertNull(localNetworkWords(LocalNetworkState.Granted))
        assertEquals(R.string.nag_lan_allow, localNetworkWords(LocalNetworkState.Ask)?.action)
        assertEquals(R.string.nag_lan_allow, localNetworkWords(LocalNetworkState.Denied)?.action)
        assertEquals(R.string.nag_lan_open_settings, localNetworkWords(LocalNetworkState.Blocked)?.action)
        assertEquals(R.string.nag_lan_blocked_desc, localNetworkWords(LocalNetworkState.Blocked)?.description)
    }

    @Test
    fun aWindowPutsAQuestionOnceAndOnlyWhileItIsRecent() {
        val at = 1_000_000L
        assertTrue(LocalNetworkAccess.take(at, now = at + 100))
        // the second window, or the same request seen again
        assertFalse(LocalNetworkAccess.take(at, now = at + 200))
        // a window that comes up long after a page asked does not ask by itself
        assertFalse(LocalNetworkAccess.take(at + 1, now = at + 60_000))
        assertTrue(LocalNetworkAccess.take(at + 70_000, now = at + 70_500))
    }
}
