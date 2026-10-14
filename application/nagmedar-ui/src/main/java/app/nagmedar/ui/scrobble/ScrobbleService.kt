package app.nagmedar.ui.scrobble

import app.nagmedar.data.ScrobbleEntry

/** S-1008: the services that take scrobbles, by the name their queue and settings go by. */
enum class ScrobbleServiceId(val key: String) {
    LastFm("lastfm"),
    ListenBrainz("listenbrainz"),
    ;

    companion object {
        fun of(key: String): ScrobbleServiceId? = entries.firstOrNull { it.key == key }
    }
}

/** What a service answered to scrobbles sent. */
sealed interface SubmitOutcome {
    /** The service has them (or knows them already, or ignored them for good): they leave the queue. */
    data object Accepted : SubmitOutcome

    /** Not now (no network, the server busy or down): they stay, and are sent again later. */
    data class Retry(val reason: String? = null) : SubmitOutcome

    /** The service does not take this account's scrobbles (a bad or revoked key): the queue stops until the user signs in again. */
    data class AuthFailed(val reason: String? = null) : SubmitOutcome

    /** The service refuses these scrobbles themselves (invalid fields): they are given up on; a batch is then tried one by one. */
    data class Rejected(val reason: String? = null) : SubmitOutcome
}

/** Why signing in did not work. */
sealed class SignInFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The name and password, or the token, are not right. */
    class Refused(message: String = "refused") : SignInFailure(message)

    /** No network, or the server could not be reached. */
    class Offline(cause: Throwable? = null) : SignInFailure("offline", cause)

    /** This build has no key for the service. */
    class NoKey : SignInFailure("no key")

    /** Another answer: the service's code or HTTP status. */
    class Server(val code: Int) : SignInFailure("server $code")
}

/** An account signed in: the name the service knows it by, and the secret that scrobbles for it (a session key, a token). */
data class ScrobbleAccount(val name: String, val secret: String)

/** What the user typed to sign in: Last.fm's user name and [secret] (the password, used once); ListenBrainz's token as [secret]. */
data class SignInInput(val name: String = "", val secret: String)

/** S-1008: a scrobbling service: Last.fm in the app, a fake in the tests. */
interface ScrobbleService {
    val id: ScrobbleServiceId

    /** Whether this build can use the service (Last.fm needs the keys it was built with). */
    val available: Boolean

    /** The most scrobbles one request takes. */
    val batchSize: Int

    /** Scrobbles older than this many seconds are refused by the service, or null for no limit. */
    val maxAgeSec: Long?

    /** Signs in with what the user typed; the account to keep. Throws [SignInFailure]. */
    suspend fun signIn(input: SignInInput): ScrobbleAccount

    /** What plays now: told to the service at [secret]'s account; failures are not worth a word. */
    suspend fun nowPlaying(secret: String, track: ScrobbleTrack)

    /** Sends [entries] (at most [batchSize]) for the account at [secret]. */
    suspend fun submit(secret: String, entries: List<ScrobbleEntry>): SubmitOutcome
}
