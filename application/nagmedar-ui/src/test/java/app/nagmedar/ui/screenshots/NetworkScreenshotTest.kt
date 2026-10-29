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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.network.LanState
import app.nagmedar.ui.network.LibVlcPrompt
import app.nagmedar.ui.network.PromptKind
import app.nagmedar.ui.network.PromptReply
import app.nagmedar.ui.network.NetworkModel
import app.nagmedar.ui.network.NetworkProtocol
import app.nagmedar.ui.network.NetworkSamples
import app.nagmedar.ui.network.SampleNetworkBrowser
import app.nagmedar.ui.network.ServerDialogSurface
import app.nagmedar.ui.network.ServerDraft
import app.nagmedar.ui.network.FakeServerStore
import app.nagmedar.ui.network.found
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
 * S-206, for approval: Sources › Network in every state, on DeX, a tablet and a phone: a folder
 * open beside the servers, a sign-in turned down, adding a server with its faults, a slow
 * network, a server out of reach, no local network, shares turned off, the network being
 * searched with nothing saved, an empty folder; some in English.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NetworkScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode, overlay: (@Composable () -> Unit)? = null, browser: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    TestShell(widthClass, startDestination = Destination.Network) { _, _ -> browser() }
                    if (overlay != null) {
                        Box(Modifier.fillMaxSize().background(NagTheme.colors.scrim), contentAlignment = Alignment.Center) { overlay() }
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

    /** The samples with a server opened by [open] once the network is searched. */
    @Composable
    private fun Opened(lan: LanState = LanState.Available, clock: () -> Long = { 1_000L }, hold: Boolean = false, open: (NetworkModel) -> Unit) {
        val source = remember { NetworkSamples.source().apply { if (hold) held += NetworkSamples.NAS } }
        val store = remember { NetworkSamples.store() }
        SampleNetworkBrowser(lan = lan, source = source, store = store, clock = clock) { model ->
            if (lan == LanState.Available) model.found(source)
            open(model)
        }
    }

    private fun NetworkModel.openFound(index: Int) = open(NetworkSamples.found[index])

    /** The saved servers come from the store's flow: opened once they are read. */
    private fun NetworkModel.openNas() = open(NetworkSamples.store().rows.value.first { it.id == 3L })

    private fun NetworkModel.openFtp() = open(NetworkSamples.store().rows.value.first { it.id == 1L })

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
    fun folderDex() = capture("network-dex-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer) { Opened { it.openNas() } }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
    fun signInDex() = capture("network-signin-dex-light", WidthClass.Large, ThemeMode.Light, InputMode.Pointer) { Opened { it.openFtp() } }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
    fun addServerDex() = capture(
        "network-add-dex-dark",
        WidthClass.Large,
        ThemeMode.Dark,
        InputMode.Pointer,
        overlay = {
            val draft = remember {
                ServerDraft().apply {
                    typeHost("nas local")
                    port = "70000"
                    path = "Filmler"
                    user = ""
                    password = "gizli"
                    attempted = true
                }
            }
            ServerDialogSurface(draft, onSave = {}, onCancel = {})
        },
    ) { Opened { it.openNas() } }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun slowTablet() = capture("network-slow-tablet-light", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch) {
        Opened(clock = { 8_000L }, hold = true) { it.openNas() }
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun unreachableTablet() = capture("network-unreachable-tablet-dark", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch) {
        Opened { it.openFound(0) }
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun searchingTablet() = capture("network-searching-tablet-dark", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch) {
        SampleNetworkBrowser(store = remember { FakeServerStore() }, searching = true) { it.discover() }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
    fun sharesOffDex() = capture("network-off-dex-light", WidthClass.Large, ThemeMode.Light, InputMode.Pointer) {
        SampleNetworkBrowser(lan = LanState.Disabled)
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun noLanPhone() = capture("network-nolan-phone-dark", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch) {
        SampleNetworkBrowser(lan = LanState.NoLan)
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun serversPhone() = capture("network-phone-light", WidthClass.Compact, ThemeMode.Light, InputMode.Touch) {
        Opened { }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun folderPhone() = capture("network-folder-phone-dark", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch) { Opened { it.openNas() } }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun emptyPhone() = capture("network-empty-phone-light", WidthClass.Compact, ThemeMode.Light, InputMode.Touch) { Opened { it.openFound(2) } }

    /** S-1501: in English. */
    @Test @Config(qualifiers = "en-w411dp-h891dp-hdpi")
    fun signInPhoneEnglish() = capture("network-signin-phone-dark-en", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch) { Opened { it.openFtp() } }

    @Test @Config(qualifiers = "en-w411dp-h891dp-hdpi")
    fun addServerPhoneEnglish() = capture(
        "network-add-phone-light-en",
        WidthClass.Compact,
        ThemeMode.Light,
        InputMode.Touch,
        overlay = {
            val draft = remember {
                ServerDraft().apply {
                    protocol = NetworkProtocol.Ftpes
                    typeHost("ftp.example.org")
                    path = "arsiv"
                    name = "Archive"
                    user = "ali"
                    password = "secret"
                }
            }
            ServerDialogSurface(draft, onSave = {}, onCancel = {})
        },
    ) { Opened { } }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi-qwerty")
    fun folderDexEnglish() = capture("network-dex-light-en", WidthClass.Large, ThemeMode.Light, InputMode.Pointer) { Opened { it.openNas() } }

    /**
     * S-1301: libVLC's question about a server's certificate, on the folder's page, as gnutls asks it in English and
     * the window says it in the interface's language (LibVlcWords).
     */
    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun questionTablet() = capture("network-question-tablet-dark", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch) {
        val source = remember {
            NetworkSamples.source().apply {
                questions[NetworkSamples.DAV] = LibVlcPrompt(
                    PromptKind.Question,
                    "Insecure site",
                    "You attempted to reach bulut.example.org. However, the security certificate presented by the server is unknown " +
                        "and could not be authenticated by any trusted Certificate Authority.\nThis problem may be stem from an attempt " +
                        "to breach your security, compromise your privacy, or a configuration error.\n\nIf in doubt, abort now.\n",
                    cancelText = "Abort",
                    action1 = "View certificate",
                    warning = true,
                    reply = object : PromptReply {
                        override fun login(user: String, password: String, store: Boolean) = Unit
                        override fun action(action: Int) = Unit
                        override fun dismiss() = Unit
                    },
                )
            }
        }
        SampleNetworkBrowser(source = source) { model -> model.open(NetworkSamples.store().rows.value.first { it.id == 2L }) }
    }
}
