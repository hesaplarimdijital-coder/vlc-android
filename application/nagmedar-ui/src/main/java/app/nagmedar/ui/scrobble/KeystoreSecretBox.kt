package app.nagmedar.ui.scrobble

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * S-1008: [SecretBox] with an AES-256-GCM key that lives in the Android Keystore under [alias]
 * and never leaves it. The text is the random 12-byte nonce followed by the cipher text and its
 * tag, in Base64. A key that cannot be had is no secret kept: [open] returns null.
 */
class KeystoreSecretBox(private val alias: String = ALIAS) : SecretBox {

    override fun seal(secret: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    override fun open(sealed: String): String? = try {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val existing = existingKey() ?: return null
        cipher.init(Cipher.DECRYPT_MODE, existing, GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES))
        String(cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES), Charsets.UTF_8)
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: ProviderException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun existingKey(): SecretKey? {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return store.getKey(alias, null) as? SecretKey
    }

    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        generateKey()
    }

    companion object {
        const val ALIAS = "nagmedar_scrobble"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val NONCE_BYTES = 12
    }
}
