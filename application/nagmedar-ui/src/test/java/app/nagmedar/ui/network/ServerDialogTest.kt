package app.nagmedar.ui.network

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-206: the add-server form: a fault under its field, nothing saved while one stays, a pasted address read. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class ServerDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(draft: ServerDraft, onSave: () -> Unit = {}) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                ServerDialogSurface(draft, onSave = onSave, onCancel = {})
            }
        }
    }

    @Test
    fun nothingIsSavedWhileAFaultStays() {
        val draft = ServerDraft()
        var saved = 0
        show(draft) { saved++ }
        // a missing host shows once saving is tried
        compose.onNodeWithText("Sunucunun adını ya da IP adresini yazın.").assertDoesNotExist()
        compose.onNodeWithTag("server-save").performClick()
        compose.onNodeWithText("Sunucunun adını ya da IP adresini yazın.").assertExists()
        assertEquals(0, saved)
        compose.onNodeWithTag("server-host").performTextInput("nas local")
        compose.onNodeWithText("Bu bir sunucu adı ya da IP adresi değil.").assertExists()
        compose.onNodeWithTag("server-host").performTextReplacement("nas.local")
        compose.onNodeWithTag("server-port").performTextInput("70000")
        compose.onNodeWithText("Bağlantı noktası 1 ile 65535 arasında bir sayıdır.").assertExists()
        compose.onNodeWithTag("server-save").performClick()
        assertEquals(0, saved)
        compose.onNodeWithTag("server-port").performTextReplacement("4455")
        // a password without its user
        compose.onNodeWithTag("server-password").performTextInput("gizli")
        compose.onNodeWithText("Parolayla birlikte kullanıcı adını da yazın.").assertExists()
        compose.onNodeWithTag("server-user").performTextInput("ali")
        compose.onNodeWithTag("server-path").performTextInput("Filmler")
        compose.onNodeWithTag("server-save").performClick()
        assertEquals(1, saved)
        assertEquals("smb://nas.local:4455/Filmler", draft.url)
        assertEquals("Filmler", draft.displayName)
        assertEquals(SignInChange.Use(Credentials("ali", "gizli"), remember = true), draft.signInChange)
        // the address shown never holds the password
        compose.onNodeWithTag("server-url").assertTextContains("smb://nas.local:4455/Filmler", substring = true)
        assertFalse("gizli" in draft.url)
    }

    @Test
    fun aPastedAddressFillsTheForm() {
        val draft = ServerDraft()
        show(draft)
        compose.onNodeWithTag("server-host").performTextInput("ftpes://veli:parola@ftp.example.org:2121/arsiv/Yeni%20klas%C3%B6r")
        assertEquals(NetworkProtocol.Ftpes, draft.protocol)
        assertEquals("ftp.example.org", draft.host)
        assertEquals("2121", draft.port)
        assertEquals("arsiv/Yeni klasör", draft.path)
        assertEquals("veli", draft.user)
        assertTrue("the password is not taken from an address", draft.password.isEmpty())
        // FTP's TLS is chosen under FTP
        compose.onNodeWithText("FTPES").assertIsSelected()
        compose.onNodeWithText("SFTP").performClick()
        assertEquals(NetworkProtocol.Sftp, draft.protocol)
        compose.onNodeWithText("NFS").performClick()
        compose.onNodeWithTag("server-port").assertDoesNotExist()
        compose.onNodeWithTag("server-user").assertDoesNotExist()
        assertEquals(SignInChange.Forget, draft.signInChange)
    }

    @Test
    fun aKeptSignInStaysUnlessChanged() {
        val draft = ServerDraft.of(SavedServer(3, "NAS", "smb://nas.local/Filmler", signedIn = true))
        show(draft)
        compose.onNodeWithText("Bu cihazda şifreli olarak bir kullanıcı adı ve parola kayıtlı.").assertExists()
        compose.onNodeWithTag("server-password").assertDoesNotExist()
        assertEquals(SignInChange.Unchanged, draft.signInChange)
        compose.onNodeWithText("Unut").performClick()
        assertEquals(SignInChange.Forget, draft.signInChange)
        compose.onNodeWithText("Değiştir").performClick()
        compose.onNodeWithTag("server-user").performTextInput("ali")
        assertFalse(draft.signInChange == SignInChange.Forget)
        // a UPnP server keeps libVLC's address: only its name changes
        val upnp = ServerDraft.of(SavedServer(4, "Bravia", "upnp://http://192.168.1.10:8200/rootDesc.xml", signedIn = false))
        assertEquals("upnp://http://192.168.1.10:8200/rootDesc.xml", upnp.url)
        assertTrue(upnp.valid)
        assertFalse(upnp.asksSignIn)
    }
}
