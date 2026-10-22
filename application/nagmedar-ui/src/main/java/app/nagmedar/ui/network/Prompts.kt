package app.nagmedar.ui.network

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicBoolean

/** What libVLC asks for. */
enum class PromptKind { Login, Question, Progress }

/** How a request of libVLC's is answered; the app's answers its dialog on the main thread. */
interface PromptReply {
    fun login(user: String, password: String, store: Boolean)

    /** libVLC's first (1) or second (2) action. */
    fun action(action: Int)

    fun dismiss()
}

/**
 * S-1301: one of libVLC's requests: a sign-in, a question (a server's certificate…) or a long
 * task's progress, in libVLC's own words. It is answered once; libVLC may also take it back.
 */
@Stable
class LibVlcPrompt(
    val kind: PromptKind,
    val title: String,
    text: String,
    val defaultUser: String? = null,
    /** libVLC offers to keep the sign-in in its own keystore (encrypted on Android). */
    val asksStore: Boolean = false,
    val cancelText: String? = null,
    val action1: String? = null,
    val action2: String? = null,
    /** A question that warns: libVLC's warning or error type. */
    val warning: Boolean = false,
    val indeterminate: Boolean = true,
    position: Float = 0f,
    private val reply: PromptReply,
) {
    var text by mutableStateOf(text)
    var position by mutableFloatStateOf(position)

    private val done = AtomicBoolean(false)

    /** Answered here or taken back by libVLC. */
    val settled: Boolean get() = done.get()

    fun login(user: String, password: String, store: Boolean) {
        if (done.compareAndSet(false, true)) reply.login(user, password, store)
    }

    fun action(action: Int) {
        if (done.compareAndSet(false, true)) reply.action(action)
    }

    fun dismiss() {
        if (done.compareAndSet(false, true)) reply.dismiss()
    }

    /** libVLC took it back: nothing is answered any more. */
    internal fun gone() = done.set(true)

    /** The words a request names its server in. */
    internal val words: String get() = "$title\n$text"
}

/**
 * Who answers libVLC's requests for one server while it is read ([browse]: a folder) or starts
 * playing: requests naming [host] are its; [credentials] answer the first sign-in (unless
 * [triedInOptions] already gave them as the access module's options), a sign-in asked again means
 * they were turned down. A folder's question goes to [onQuestion], shown on its page; null when
 * it is gone. State guarded by the router.
 */
class DialogClaim(
    val host: String,
    val protocol: NetworkProtocol?,
    val credentials: Credentials?,
    triedInOptions: Boolean = false,
    val browse: Boolean = true,
    /** When it began (the router's clock): a file's claim lasts [DialogRouter.PLAY_CLAIM_MS]. */
    val since: Long = 0,
    val onQuestion: (LibVlcPrompt?) -> Unit = {},
) {
    internal var answered = triedInOptions
    internal var refused = false
    internal var question: LibVlcPrompt? = null

    /** How the reading ended, libVLC having read the folder when [done]. */
    fun result(done: Boolean): BrowseResult = synchronized(this) {
        when {
            refused && (answered || credentials != null) -> BrowseResult.SignInRejected
            refused -> BrowseResult.SignInNeeded
            done -> BrowseResult.Done
            else -> BrowseResult.Unreachable
        }
    }

    companion object {
        /** The claim of a folder at [url], read with [credentials]. */
        fun browsing(url: String, credentials: Credentials?, triedInOptions: Boolean, since: Long, onQuestion: (LibVlcPrompt?) -> Unit) =
            DialogClaim(ServerAddress.parse(url)?.host.orEmpty(), NetworkProtocol.of(url.substringBefore("://", "")), credentials, triedInOptions, true, since, onQuestion)
    }
}

/** The window that shows libVLC's requests nobody else answers, while it is in front. */
interface PromptHost {
    /** True when [prompt] is shown, to be answered by the person. */
    fun show(prompt: LibVlcPrompt): Boolean

    /** libVLC took [prompt] back; true when it was shown here. */
    fun gone(prompt: LibVlcPrompt): Boolean
}

/**
 * S-1301: where each of libVLC's requests goes. A sign-in or a question naming the host of a
 * folder being read (or of a file starting to play) is that claim's: answered with its sign-in,
 * or, for a folder, turned down to be asked on its page, its question shown there. WebDAV's and
 * HTTP's sign-ins name only a realm: they go to the one WebDAV server being opened, never when
 * two are. A sign-in naming a saved server (the library's scan, a file played after a restart) is
 * answered with its kept sign-in, once: asked again, it was turned down, and the person is asked
 * until it is typed anew ([savedSignIn] finds it off the main thread). Hosts are matched whole. The rest goes to [host]: the app's window, which a notification calls when none is in front.
 */
class DialogRouter(
    /** The kept sign-in of the one saved server whose host is in the words; [reply] on any thread. */
    private val savedSignIn: (words: String, reply: (Credentials?) -> Unit) -> Unit,
    private val now: () -> Long,
    /** A request taken here that nobody here can answer, given to VLC's windows. */
    private val handBack: (LibVlcPrompt) -> Unit = { it.dismiss() },
) {
    private val claims = ArrayList<DialogClaim>()

    /** The saved servers' hosts answered for the library's scan, and those whose kept sign-in was turned down. */
    private val autoAnswered = bounded()
    private val refused = bounded()

    /** The hosts of the saved servers with a kept sign-in. */
    @Volatile
    var savedHosts: Set<String> = emptySet()

    @Volatile
    var host: PromptHost? = null

    fun begin(claim: DialogClaim) = synchronized(this) {
        expire()
        claims += claim
    }

    /** A file's claims older than [PLAY_CLAIM_MS] answer no more. Under the lock. */
    private fun expire() {
        val at = now()
        claims.removeAll { !it.browse && at - it.since > PLAY_CLAIM_MS }
    }

    /** [host]'s kept sign-in was typed again: the library's scan may use it again. */
    fun renewed(host: String) = synchronized(this) {
        refused.remove(host.lowercase())
        autoAnswered.remove(host.lowercase())
    }

    /** [claim] is over: its question, if one waits, is dismissed. */
    fun end(claim: DialogClaim) {
        val pending = synchronized(this) {
            claims.remove(claim)
            claim.question.also { claim.question = null }
        }
        pending?.dismiss()
    }

    /** Whether [claim] still answers (the tests'). */
    internal fun active(claim: DialogClaim): Boolean = synchronized(this) { claim in claims }

    /** True when [prompt] is taken: answered, shown on a folder's page or in the app's window. */
    fun route(prompt: LibVlcPrompt): Boolean = when (prompt.kind) {
        PromptKind.Login -> login(prompt)
        PromptKind.Question -> question(prompt)
        PromptKind.Progress -> show(prompt)
    }

    /** libVLC took [prompt] back; true when it was taken here. */
    fun canceled(prompt: LibVlcPrompt): Boolean {
        prompt.gone()
        val owner = synchronized(this) { claims.firstOrNull { it.question === prompt }?.also { it.question = null } }
        if (owner != null) {
            owner.onQuestion(null)
            return true
        }
        return host?.gone(prompt) == true
    }

    private fun show(prompt: LibVlcPrompt): Boolean = host?.show(prompt) == true

    private fun login(prompt: LibVlcPrompt): Boolean {
        val words = prompt.words
        val owner: DialogClaim?
        val answer: Credentials?
        synchronized(this) {
            expire()
            owner = owner(words)
            answer = owner?.let { claim ->
                synchronized(claim) {
                    if (claim.credentials != null && !claim.answered) {
                        claim.answered = true
                        claim.credentials
                    } else {
                        if (claim.browse) claim.refused = true
                        null
                    }
                }
            }
        }
        if (owner != null) {
            when {
                // libVLC keeps it for this session only; Nağmedar keeps it sealed
                answer != null -> prompt.login(answer.user, answer.password, false)
                // the folder asks on its page
                owner.browse -> prompt.dismiss()
                // a file playing: the person is asked
                else -> return show(prompt)
            }
            return true
        }
        val named = savedHosts.filter { hostNamed(words, it) }.map { it.lowercase() }.distinct().singleOrNull() ?: return show(prompt)
        val turnedDown = synchronized(this) {
            // asked again after its kept sign-in was given: turned down; never tried again (a lockout) until typed anew
            if (autoAnswered.remove(named) != null) refused[named] = true
            named in refused
        }
        if (turnedDown) return show(prompt)
        savedSignIn(words) { credentials ->
            when {
                credentials != null -> {
                    synchronized(this) { autoAnswered[named] = true }
                    prompt.login(credentials.user, credentials.password, false)
                }
                !show(prompt) -> handBack(prompt)
            }
        }
        return true
    }

    private fun question(prompt: LibVlcPrompt): Boolean {
        val words = prompt.words
        val owner = synchronized(this) {
            expire()
            claims.lastOrNull { it.browse && hostNamed(words, it.host) }?.also { it.question = prompt }
        }
        if (owner != null) {
            owner.onQuestion(prompt)
            return true
        }
        return show(prompt)
    }

    /** The claim [words] are for: by the host they name (a folder's first), else the one WebDAV server being opened. */
    private fun owner(words: String): DialogClaim? {
        val named = claims.filter { hostNamed(words, it.host) }
        // a folder being read before a file starting to play
        (named.lastOrNull { it.browse } ?: named.lastOrNull())?.let { return it }
        val web = claims.filter { it.protocol == NetworkProtocol.WebDav || it.protocol == NetworkProtocol.WebDavs }
        val last = web.lastOrNull() ?: return null
        return last.takeIf { web.all { it.host.equals(last.host, ignoreCase = true) } }
    }

    companion object {
        /** How long a file's claim answers after it starts playing. */
        const val PLAY_CLAIM_MS = 60_000L

        /** How many hosts the library's scan's answers are remembered for. */
        private const val REMEMBERED = 64

        private fun bounded() = object : LinkedHashMap<String, Boolean>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > REMEMBERED
        }
    }
}
