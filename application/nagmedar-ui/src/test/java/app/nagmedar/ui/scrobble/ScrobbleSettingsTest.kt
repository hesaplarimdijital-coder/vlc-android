package app.nagmedar.ui.scrobble

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.backup.BackupSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-1008: the switches and accounts as the preferences keep them, the secret sealed, none of it in a backup. */
@RunWith(AndroidJUnit4::class)
class ScrobbleSettingsTest {

    private val prefs = freshPrefs()
    private val settings = ScrobbleSettings(prefs, FakeSecretBox())

    @Test
    fun aServiceIsOffUntilAnAccountIsSignedIn() {
        assertFalse(settings.enabled(ScrobbleServiceId.LastFm))
        settings.setEnabled(ScrobbleServiceId.LastFm, true)
        assertFalse("no account, no scrobbling", settings.enabled(ScrobbleServiceId.LastFm))
        assertTrue(settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "session-key")))
        assertTrue(settings.enabled(ScrobbleServiceId.LastFm))
        assertEquals("ada", settings.accountName(ScrobbleServiceId.LastFm))
        assertEquals("session-key", settings.secret(ScrobbleServiceId.LastFm))
        // the other service is its own
        assertFalse(settings.enabled(ScrobbleServiceId.ListenBrainz))
        settings.setEnabled(ScrobbleServiceId.LastFm, false)
        assertFalse(settings.enabled(ScrobbleServiceId.LastFm))
        assertEquals("the account stays", "ada", settings.accountName(ScrobbleServiceId.LastFm))
    }

    @Test
    fun theSecretIsInThePreferencesOnlySealed() {
        settings.signIn(ScrobbleServiceId.ListenBrainz, ScrobbleAccount("ada", "token-123"))
        val stored = prefs.all.values.joinToString("|")
        assertFalse(stored.contains("token-123"))
        assertTrue(stored.contains("sealed:"))
    }

    @Test
    fun signingOutForgetsEverythingOfTheService() {
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "session-key"))
        settings.setHold(ScrobbleServiceId.LastFm, ScrobbleHold("code 9"))
        settings.signOut(ScrobbleServiceId.LastFm)
        assertNull(settings.accountName(ScrobbleServiceId.LastFm))
        assertNull(settings.secret(ScrobbleServiceId.LastFm))
        assertNull(settings.hold(ScrobbleServiceId.LastFm))
        assertFalse(settings.enabled(ScrobbleServiceId.LastFm))
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun aHoldIsKeptWithItsReasonAndEndsWithANewSignIn() {
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "k"))
        assertNull(settings.hold(ScrobbleServiceId.LastFm))
        settings.setHold(ScrobbleServiceId.LastFm, ScrobbleHold(null))
        assertNotNull(settings.hold(ScrobbleServiceId.LastFm))
        assertNull(settings.hold(ScrobbleServiceId.LastFm)?.reason)
        assertFalse(settings.active(ScrobbleServiceId.LastFm))
        settings.setHold(ScrobbleServiceId.LastFm, ScrobbleHold("code 9"))
        assertEquals("code 9", settings.hold(ScrobbleServiceId.LastFm)?.reason)
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "k2"))
        assertNull(settings.hold(ScrobbleServiceId.LastFm))
        assertTrue(settings.active(ScrobbleServiceId.LastFm))
    }

    @Test
    fun aSecretThatCannotBeSealedIsNoSignIn() {
        val broken = ScrobbleSettings(prefs, object : SecretBox {
            override fun seal(secret: String): String = throw java.security.ProviderException("no keystore")

            override fun open(sealed: String): String? = null
        })
        assertFalse(broken.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "k")))
        assertNull(broken.accountName(ScrobbleServiceId.LastFm))
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun aSecretThatCannotBeOpenedIsNone() {
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "k"))
        val elsewhere = ScrobbleSettings(prefs, object : SecretBox {
            override fun seal(secret: String) = secret

            override fun open(sealed: String): String? = null
        })
        assertEquals("ada", elsewhere.accountName(ScrobbleServiceId.LastFm))
        assertNull(elsewhere.secret(ScrobbleServiceId.LastFm))
    }

    @Test
    fun aBackupOfTheSettingsLeavesTheAccountsOut() {
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "session-key"))
        settings.signIn(ScrobbleServiceId.ListenBrainz, ScrobbleAccount("ada", "token"))
        settings.setHold(ScrobbleServiceId.ListenBrainz, ScrobbleHold("status 401"))
        prefs.edit().putBoolean("nagmedar_something_else", true).commit()
        val collected = BackupSettings.collect(prefs.all).map { it.key }
        assertEquals(listOf("nagmedar_something_else"), collected)
        assertEquals(8, ScrobbleSettings.keys.size)
        assertTrue(BackupSettings.leftOut().containsAll(ScrobbleSettings.keys))
    }
}
