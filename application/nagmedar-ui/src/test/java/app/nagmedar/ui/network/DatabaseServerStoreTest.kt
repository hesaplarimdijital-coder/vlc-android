package app.nagmedar.ui.network

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.NagmedarDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.KeyGenerator

/**
 * S-1301: saved servers in Nağmedar's database: the sign-in sealed (a fake of the Keystore's
 * cipher, the same AES-GCM with a key of the test's), never in clear in the row; one that can no
 * longer be opened forgotten and asked for again; the sign-in of what plays found by its server.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseServerStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, NagmedarDatabase::class.java).allowMainThreadQueries().build()
    private var key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = DatabaseServerStore(context, CredentialSeal(AesGcmCipher { key }), db.networkServers())
    private val ali = Credentials("ali", "Gizli parola 1")

    /** The key store answers unless set. */
    private var keyStoreDown = false
    private val flaky = DatabaseServerStore(context, CredentialSeal(AesGcmCipher { if (keyStoreDown) throw java.security.KeyStoreException("busy") else key }), db.networkServers())

    private fun SaveResult.id(): Long = (this as SaveResult.Saved).id

    @After
    fun close() {
        db.close()
        SessionSignIns.clear()
    }

    @Test
    fun aSignInIsSealedInItsRow() = runBlocking {
        val id = store.save(0, "NAS", "smb://nas.local/Filmler", SignInChange.Use(ali, remember = true)).id()
        val row = db.networkServers().get(id)!!
        assertFalse("Gizli" in String(row.credentials!!, Charsets.ISO_8859_1))
        assertFalse("ali" in row.url)
        assertEquals(StoredSignIn.Kept(ali), store.signIn(id))
        assertEquals(listOf(SavedServer(id, "NAS", "smb://nas.local/Filmler", signedIn = true)), store.servers.first())
        // what plays from the server signs in with it
        assertEquals(ali, store.signInFor("smb://nas.local/Filmler/2024/Kuzey%20I%C5%9F%C4%B1%C4%9F%C4%B1.mkv"))
        assertNull(store.signInFor("smb://nas.local/Muzik/a.flac"))
    }

    @Test
    fun aSignInThatCannotBeOpenedIsForgotten() = runBlocking {
        val id = store.save(0, "NAS", "smb://nas.local/", SignInChange.Use(ali, remember = true)).id()
        // the key is gone: another device, a reset
        key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertNull(store.signInFor("smb://nas.local/a.mkv"))
        assertEquals(StoredSignIn.Unreadable, store.signIn(id))
        assertNull(db.networkServers().get(id)!!.credentials)
        assertEquals(StoredSignIn.None, store.signIn(id))
    }

    @Test
    fun changesKeepWhatWasNotChanged() = runBlocking {
        val id = store.save(0, "NAS", "smb://nas.local/Filmler", SignInChange.Use(ali, remember = true)).id()
        // renamed and moved: the sign-in is sealed again for the new address
        assertEquals(SaveResult.Saved(id), store.save(id, "Salon", "smb://nas.local:4455/Filmler", SignInChange.Unchanged))
        assertEquals(StoredSignIn.Kept(ali), store.signIn(id))
        assertEquals("Salon", db.networkServers().get(id)!!.name)
        // another server cannot take its address
        assertEquals(SaveResult.AddressTaken, store.save(0, "Yine", "smb://nas.local:4455/Filmler", SignInChange.Forget))
        // for this session only: nothing sealed, but known until the app goes
        store.save(id, "Salon", "smb://nas.local:4455/Filmler", SignInChange.Use(Credentials("veli", "x"), remember = false))
        assertNull(db.networkServers().get(id)!!.credentials)
        assertEquals(StoredSignIn.Kept(Credentials("veli", "x")), store.signIn(id))
        store.save(id, "Salon", "smb://nas.local:4455/Filmler", SignInChange.Forget)
        assertEquals(StoredSignIn.None, store.signIn(id))
        store.remove(id)
        assertTrue(store.servers.first().isEmpty())
    }

    @Test
    fun aKeyStoreThatDoesNotAnswerLosesNothing() = runBlocking {
        val id = flaky.save(0, "NAS", "smb://nas.local/", SignInChange.Use(ali, remember = true)).id()
        keyStoreDown = true
        assertEquals(StoredSignIn.Unavailable, flaky.signIn(id))
        assertTrue("the password is kept", db.networkServers().get(id)!!.credentials != null)
        keyStoreDown = false
        assertEquals(StoredSignIn.Kept(ali), flaky.signIn(id))
        // it cannot seal now: the row is saved, the sign-in serves this session and says so
        keyStoreDown = true
        val result = flaky.save(0, "Arşiv", "ftp://arsiv/", SignInChange.Use(ali, remember = true)) as SaveResult.Saved
        assertFalse(result.signInKept)
        assertNull(db.networkServers().get(result.id)!!.credentials)
        assertEquals(ali, SessionSignIns.forUrl("ftp://arsiv/a.mkv"))
    }

    @Test
    fun aRequestNamingASavedServerFindsItsSignIn() = runBlocking {
        store.save(0, "NAS", "smb://nas.local/Filmler", SignInChange.Use(ali, remember = true))
        store.save(0, "Arşiv", "ftp://arsiv.example.org/", SignInChange.Use(Credentials("a", "b"), remember = true))
        assertEquals(ali, store.signInNamed("Please enter a valid login name and password for nas.local"))
        assertNull(store.signInNamed("Please enter a valid login name and password for medya-pc"))
        // two servers named: no guess
        assertNull(store.signInNamed("nas.local arsiv.example.org"))
    }
}
