package app.nagmedar.ui.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * S-1301: a sign-in sealed with AES-GCM and opened again, on the JVM's own AES (the Android
 * Keystore holds the app's key, which Robolectric does not have): a fresh IV each time, nothing
 * readable in the sealed bytes, and a key gone, bytes changed or another server's address all
 * leaving it unreadable.
 */
class CredentialsTest {

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val cipher = AesGcmCipher { key }
    private val seal = CredentialSeal(cipher)
    private val url = "smb://nas.local/Filmler"

    @Test
    fun aSignInComesBackAsItWasSealed() {
        val credentials = Credentials("EV\\şule", "Çok gizli 123!")
        val sealed = seal.seal(credentials, url)
        assertEquals(credentials, seal.open(sealed, url))
        // nothing of it can be read in the sealed bytes
        val text = String(sealed, Charsets.ISO_8859_1)
        assertFalse("gizli" in text)
        assertFalse(String(sealed, Charsets.UTF_8).contains("şule"))
        // a new IV each time: the same sign-in sealed twice differs
        assertNotEquals(sealed.toList(), seal.seal(credentials, url).toList())
        assertEquals(credentials, Credentials("EV\\şule", "Çok gizli 123!"))
    }

    @Test
    fun theFormatIsVersionIvAndText() {
        val sealed = cipher.seal(byteArrayOf(1, 2, 3), byteArrayOf())
        assertEquals(1.toByte(), sealed[0])
        assertEquals(12, sealed[1].toInt())
        // 3 bytes of text and the 16-byte tag after the IV
        assertEquals(2 + 12 + 3 + 16, sealed.size)
    }

    @Test
    fun whatCannotBeOpenedSaysSo() {
        val sealed = seal.seal(Credentials("ali", "pw"), url)
        fun unreadable(block: () -> Unit) = assertTrue(runCatching(block).exceptionOrNull() is UnreadableCredentialsException)
        // another server's row
        unreadable { seal.open(sealed, "smb://other/Filmler") }
        // a byte changed
        unreadable { seal.open(sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }, url) }
        // the key gone (another device, a reset), or another key
        unreadable { CredentialSeal(AesGcmCipher { null }).open(sealed, url) }
        unreadable { CredentialSeal(AesGcmCipher { newKey() }).open(sealed, url) }
        // not sealed at all
        unreadable { seal.open(byteArrayOf(), url) }
        unreadable { seal.open(byteArrayOf(2, 12, 0), url) }
        unreadable { seal.open(byteArrayOf(1, 40) + ByteArray(60), url) }
    }

    @Test
    fun aKeyStoreThatDoesNotAnswerLosesNothing() {
        val sealed = seal.seal(Credentials("ali", "pw"), url)
        fun lost(cipher: CredentialCipher) = (runCatching { CredentialSeal(cipher).open(sealed, url) }.exceptionOrNull() as UnreadableCredentialsException).lost
        // the key store failed now: kept, to be tried again
        assertFalse(lost(AesGcmCipher { throw java.security.KeyStoreException("busy") }))
        assertFalse(lost(AesGcmCipher { throw java.io.IOException("not loaded") }))
        assertFalse(lost(AesGcmCipher { throw java.security.ProviderException("keystore daemon") }))
        // gone for good: no key, another key (its tag does not check), or bytes that are not a seal
        assertTrue(lost(AesGcmCipher { null }))
        assertTrue(lost(AesGcmCipher { newKey() }))
        assertTrue((runCatching { seal.open(byteArrayOf(9), url) }.exceptionOrNull() as UnreadableCredentialsException).lost)
    }

    @Test
    fun aSignInIsNeverWrittenOut() {
        val credentials = Credentials(" ali\n", "pa\u0000ss")
        assertEquals("ali", credentials.user)
        assertEquals("pass", credentials.password)
        assertFalse("pass" in credentials.toString())
        assertFalse("pass" in listOf(credentials).toString())
    }
}
