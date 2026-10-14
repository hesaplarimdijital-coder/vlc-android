package app.nagmedar.ui.scrobble

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-1008: Settings › Scrobble: the rows, signing in and out, the switch, "send now". */
@RunWith(AndroidJUnit4::class)
class ScrobbleControllerTest {

    private val store = FakeScrobbleStore()
    private val lastFm = FakeScrobbleService(ScrobbleServiceId.LastFm)
    private val noKey = FakeScrobbleService(ScrobbleServiceId.ListenBrainz, available = false)
    private val scheduler = FakeScrobbleScheduler()
    private val settings = ScrobbleSettings(freshPrefs(), FakeSecretBox())
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val controller = ScrobbleController(settings, store, listOf(lastFm, noKey), scheduler, scope, Dispatchers.Unconfined)

    private fun row(id: ScrobbleServiceId) = controller.rows.first { it.id == id }

    @Test
    fun theRowsSayWhatEachServiceIs() {
        assertEquals(listOf(ScrobbleServiceId.LastFm, ScrobbleServiceId.ListenBrainz), controller.rows.map { it.id })
        assertNull(row(ScrobbleServiceId.LastFm).account)
        assertFalse(row(ScrobbleServiceId.LastFm).enabled)
        assertTrue(row(ScrobbleServiceId.LastFm).available)
        assertFalse(row(ScrobbleServiceId.ListenBrainz).available)
    }

    @Test
    fun signingInKeepsTheAccountSwitchesItOnAndSendsTheQueue() {
        controller.openSignIn(ScrobbleServiceId.LastFm)
        assertEquals(SignInState.Open(ScrobbleServiceId.LastFm), controller.form)
        controller.signIn(ScrobbleServiceId.LastFm, SignInInput("ada", "parola"))
        assertEquals(listOf(SignInInput("ada", "parola")), lastFm.signIns)
        assertEquals(SignInState.Closed, controller.form)
        assertEquals("ada", row(ScrobbleServiceId.LastFm).account)
        assertTrue(row(ScrobbleServiceId.LastFm).enabled)
        assertEquals("key-1", settings.secret(ScrobbleServiceId.LastFm))
        assertEquals(listOf(true), scheduler.requests)
    }

    @Test
    fun aRefusedSignInSaysWhyAndKeepsNothing() {
        lastFm.signInResult = Result.failure(SignInFailure.Refused())
        controller.signIn(ScrobbleServiceId.LastFm, SignInInput("ada", "yanlış"))
        assertEquals(SignInState.Open(ScrobbleServiceId.LastFm, SignInProblem.Refused), controller.form)
        assertNull(row(ScrobbleServiceId.LastFm).account)
        lastFm.signInResult = Result.failure(SignInFailure.Offline())
        controller.signIn(ScrobbleServiceId.LastFm, SignInInput("ada", "parola"))
        assertEquals(SignInProblem.Offline, (controller.form as SignInState.Open).problem)
        lastFm.signInResult = Result.failure(SignInFailure.Server(11))
        controller.signIn(ScrobbleServiceId.LastFm, SignInInput("ada", "parola"))
        assertEquals(SignInState.Open(ScrobbleServiceId.LastFm, SignInProblem.Server, 11), controller.form)
        assertTrue(scheduler.requests.isEmpty())
    }

    @Test
    fun aSignInWhoseSecretCannotBeKeptIsSaidSo() {
        val broken = ScrobbleController(
            ScrobbleSettings(freshPrefs("broken"), object : SecretBox {
                override fun seal(secret: String): String = throw java.security.ProviderException("no keystore")

                override fun open(sealed: String): String? = null
            }),
            store, listOf(lastFm), scheduler, scope, Dispatchers.Unconfined,
        )
        broken.signIn(ScrobbleServiceId.LastFm, SignInInput("ada", "parola"))
        assertEquals(SignInProblem.NotKept, (broken.form as SignInState.Open).problem)
        assertNull(broken.rows.single().account)
    }

    @Test
    fun theSwitchTurnsScrobblingOnAndOff() {
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "k"))
        controller.setEnabled(ScrobbleServiceId.LastFm, false)
        assertFalse(row(ScrobbleServiceId.LastFm).enabled)
        assertEquals("the account stays", "ada", row(ScrobbleServiceId.LastFm).account)
        controller.setEnabled(ScrobbleServiceId.LastFm, true)
        assertTrue(row(ScrobbleServiceId.LastFm).enabled)
        // turned on: what waited is sent
        assertEquals(listOf(false), scheduler.requests)
    }

    @Test
    fun forgettingTheAccountDropsItsQueueOnly() {
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "k"))
        store.add(entry(ScrobbleServiceId.LastFm))
        store.add(entry(ScrobbleServiceId.ListenBrainz))
        controller.forget(ScrobbleServiceId.LastFm)
        assertNull(row(ScrobbleServiceId.LastFm).account)
        assertEquals(0, store.count(ScrobbleServiceId.LastFm))
        assertEquals(1, store.count(ScrobbleServiceId.ListenBrainz))
    }

    @Test
    fun theQueueSizeFollowsTheStoreAndSendNowAsksForTheJobAtOnce() {
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "k"))
        val watching = scope.launch { controller.watch() }
        store.add(entry(ScrobbleServiceId.LastFm, "A", 1))
        store.add(entry(ScrobbleServiceId.LastFm, "B", 2))
        assertEquals(2, row(ScrobbleServiceId.LastFm).waiting)
        controller.sendNow()
        assertEquals(listOf(true), scheduler.requests)
        store.remove(store.rows.toList())
        assertEquals(0, row(ScrobbleServiceId.LastFm).waiting)
        watching.cancel()
    }

    @Test
    fun aHoldShowsOnTheRowAndEndsWithANewSignIn() {
        settings.signIn(ScrobbleServiceId.LastFm, ScrobbleAccount("ada", "k"))
        settings.setHold(ScrobbleServiceId.LastFm, ScrobbleHold("code 9"))
        controller.setEnabled(ScrobbleServiceId.LastFm, true)
        assertTrue(row(ScrobbleServiceId.LastFm).held)
        controller.signIn(ScrobbleServiceId.LastFm, SignInInput("ada", "parola"))
        assertFalse(row(ScrobbleServiceId.LastFm).held)
    }
}
