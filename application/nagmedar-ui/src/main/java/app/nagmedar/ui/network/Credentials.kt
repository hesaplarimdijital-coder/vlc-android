package app.nagmedar.ui.network

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * S-1301: a user name and a password for a network server. Never written out: [toString] leaves
 * the password out, so neither a log nor a crash report can carry it.
 */
class Credentials(user: String, password: String) {
    /** Without the control characters a text field may let through. */
    val user: String = user.filterNot { it.isISOControl() }.trim()
    val password: String = password.filterNot { it.isISOControl() }

    override fun equals(other: Any?): Boolean = other is Credentials && other.user == user && other.password == password

    override fun hashCode(): Int = 31 * user.hashCode() + password.hashCode()

    override fun toString(): String = "Credentials(user=$user)"
}

/**
 * The sealed sign-in could not be opened. [lost]: never again (the key is gone after a restore or
 * a reset, or invalidated, or the bytes are not its: their tag does not check); else only now (the
 * Keystore did not answer), and it is kept to be tried again.
 */
class UnreadableCredentialsException(val lost: Boolean = true, cause: Throwable? = null) : Exception(cause)

/** Seals and opens bytes; [associated] is bound to them and must be the same to open them. */
interface CredentialCipher {
    fun seal(plain: ByteArray, associated: ByteArray): ByteArray

    /**
     * @throws UnreadableCredentialsException when the bytes cannot be opened; any other exception
     * of the provider or the key store is passed on as one that is not [UnreadableCredentialsException.lost].
     */
    fun open(sealed: ByteArray, associated: ByteArray): ByteArray
}

/**
 * AES-GCM with a fresh 12-byte IV per message (the cipher's own, as the Android Keystore requires)
 * and a 128-bit tag: a version byte, the IV's length, the IV, then the text with its tag. [key]
 * gives the key, [create] true when one may be made for sealing; null when there is none.
 */
open class AesGcmCipher(private val key: (create: Boolean) -> SecretKey?) : CredentialCipher {

    override fun seal(plain: ByteArray, associated: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(true) ?: throw GeneralSecurityException("no key"))
        cipher.updateAAD(associated)
        val iv = cipher.iv
        val text = cipher.doFinal(plain)
        return ByteBuffer.allocate(2 + iv.size + text.size).put(VERSION).put(iv.size.toByte()).put(iv).put(text).array()
    }

    override fun open(sealed: ByteArray, associated: ByteArray): ByteArray {
        if (sealed.size < 2 || sealed[0] != VERSION) throw UnreadableCredentialsException()
        val ivSize = sealed[1].toInt()
        if (ivSize !in 12..16 || sealed.size < 2 + ivSize + TAG_BITS / 8) throw UnreadableCredentialsException()
        val key = try {
            key(false)
        } catch (e: Exception) {
            // the key store did not answer now: kept, to be tried again
            throw UnreadableCredentialsException(lost = false, cause = e)
        } ?: throw UnreadableCredentialsException()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 2, ivSize))
            cipher.updateAAD(associated)
            return cipher.doFinal(sealed, 2 + ivSize, sealed.size - 2 - ivSize)
        } catch (e: AEADBadTagException) {
            throw UnreadableCredentialsException(cause = e)
        } catch (e: Exception) {
            if (invalidated(e)) throw UnreadableCredentialsException(cause = e)
            throw UnreadableCredentialsException(lost = false, cause = e)
        }
    }

    /** Whether [error] says the key can never be used again; the Keystore's cipher then lets go of it. */
    protected open fun invalidated(error: Exception): Boolean = false

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        private const val VERSION: Byte = 1
    }
}

/**
 * The app's cipher: a 256-bit AES key made in the Android Keystore at the first seal; the key
 * never leaves it, is not backed up and is not bound to the screen lock. A key the system has
 * invalidated is deleted, so the next seal makes a new one and the sign-in is asked for again.
 */
class KeystoreCipher : AesGcmCipher(::keystoreKey) {
    override fun invalidated(error: Exception): Boolean {
        if (error !is KeyPermanentlyInvalidatedException) return false
        runCatching { keyStore().deleteEntry(ALIAS) }
        return true
    }

    companion object {
        const val ALIAS = "nagmedar_network_credentials"
    }
}

private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

private fun keystoreKey(create: Boolean): SecretKey? {
    (keyStore().getKey(KeystoreCipher.ALIAS, null) as? SecretKey)?.let { return it }
    if (!create) return null
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(
        KeyGenParameterSpec.Builder(KeystoreCipher.ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build(),
    )
    return generator.generateKey()
}

/**
 * Credentials sealed for one server: the user name and the password as UTF-8 with a NUL between
 * them, sealed by [cipher] with the server's address bound to them, so a sealed sign-in moved to
 * another server's row does not open.
 */
class CredentialSeal(private val cipher: CredentialCipher) {

    fun seal(credentials: Credentials, url: String): ByteArray =
        cipher.seal((credentials.user + SEPARATOR + credentials.password).toByteArray(Charsets.UTF_8), associated(url))

    /** @throws UnreadableCredentialsException when [sealed] cannot be opened for [url]. */
    fun open(sealed: ByteArray, url: String): Credentials {
        val text = String(cipher.open(sealed, associated(url)), Charsets.UTF_8)
        val at = text.indexOf(SEPARATOR)
        if (at < 0) throw UnreadableCredentialsException()
        return Credentials(text.substring(0, at), text.substring(at + 1))
    }

    private fun associated(url: String) = "nagmedar-network:$url".toByteArray(Charsets.UTF_8)

    private companion object {
        const val SEPARATOR = '\u0000'
    }
}
