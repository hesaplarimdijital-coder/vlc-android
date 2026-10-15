package app.nagmedar.ui.scrobble

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What Settings › Scrobble shows of one service. */
@Immutable
data class ScrobbleRow(
    val id: ScrobbleServiceId,
    /** Whether this build can use the service (Last.fm needs the keys it was built with). */
    val available: Boolean,
    /** The account signed in; null without one. */
    val account: String?,
    val enabled: Boolean,
    /** How many scrobbles wait to be sent. */
    val waiting: Int,
    /** The service refused the account's scrobbles: they wait until the user signs in again. */
    val held: Boolean,
)

/** Why signing in did not work, for the form to say. */
enum class SignInProblem {
    /** The name and password, or the token, were not accepted. */
    Refused,
    Offline,
    NoKey,

    /** The service answered with an error of its own. */
    Server,

    /** The sign-in worked, but its secret could not be stored. */
    NotKept,
}

/** The sign-in form: closed, open for one service (with what went wrong the last time), or waiting for the answer. */
@Immutable
sealed interface SignInState {
    data object Closed : SignInState

    data class Open(val service: ScrobbleServiceId, val problem: SignInProblem? = null, val code: Int = 0) : SignInState

    data class Working(val service: ScrobbleServiceId) : SignInState
}

/**
 * S-1008: what Settings › Scrobble does: the rows it shows, the switches, signing in and out, and
 * sending the queue now. A sign-in asks the service on [io]; the secret it returns is sealed into
 * the preferences, and the password the user typed is not kept anywhere. Forgetting an account
 * drops the scrobbles that waited for it (they were that account's).
 */
@Stable
class ScrobbleController(
    private val settings: ScrobbleSettings,
    private val store: ScrobbleStore,
    private val services: List<ScrobbleService>,
    private val scheduler: ScrobbleScheduler,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private var waiting: Map<ScrobbleServiceId, Int> = emptyMap()
    private var signingIn: Job? = null

    var rows: List<ScrobbleRow> by mutableStateOf(read())
        private set

    /** The sign-in form's state. */
    var form: SignInState by mutableStateOf(SignInState.Closed)
        private set

    /** Follows the queue's size until cancelled. */
    suspend fun watch() {
        store.counts().collect {
            waiting = it
            rows = read()
        }
    }

    fun setEnabled(id: ScrobbleServiceId, on: Boolean) {
        settings.setEnabled(id, on)
        rows = read()
        if (on) scheduler.schedule()
    }

    fun openSignIn(id: ScrobbleServiceId) {
        if (form !is SignInState.Working) form = SignInState.Open(id)
    }

    fun closeSignIn() {
        signingIn?.cancel()
        form = SignInState.Closed
    }

    /** Signs in to [id] with what the user typed: on success the form closes, the service is on and its held queue goes on. */
    fun signIn(id: ScrobbleServiceId, input: SignInInput) {
        val service = services.firstOrNull { it.id == id } ?: return
        if (form is SignInState.Working) return
        form = SignInState.Working(id)
        signingIn = scope.launch {
            val account = try {
                withContext(io) { service.signIn(input) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignInFailure) {
                form = SignInState.Open(id, e.problem(), (e as? SignInFailure.Server)?.code ?: 0)
                return@launch
            }
            if (withContext(io) { settings.signIn(id, account) }) {
                form = SignInState.Closed
                rows = read()
                scheduler.schedule(now = true)
            } else {
                form = SignInState.Open(id, SignInProblem.NotKept)
            }
        }
    }

    /** Signs out of [id] and drops what waited to be sent for it. */
    fun forget(id: ScrobbleServiceId) {
        settings.signOut(id)
        rows = read()
        scope.launch(io) { store.clear(id) }
    }

    /** Sends the queue now, not when the network or the wait after a failure would. */
    fun sendNow() {
        scheduler.schedule(now = true)
    }

    private fun read(): List<ScrobbleRow> = services.map { service ->
        ScrobbleRow(
            id = service.id,
            available = service.available,
            account = settings.accountName(service.id),
            enabled = settings.enabled(service.id),
            waiting = waiting[service.id] ?: 0,
            held = settings.hold(service.id) != null,
        )
    }

    private fun SignInFailure.problem(): SignInProblem = when (this) {
        is SignInFailure.Refused -> SignInProblem.Refused
        is SignInFailure.Offline -> SignInProblem.Offline
        is SignInFailure.NoKey -> SignInProblem.NoKey
        is SignInFailure.Server -> SignInProblem.Server
    }
}
