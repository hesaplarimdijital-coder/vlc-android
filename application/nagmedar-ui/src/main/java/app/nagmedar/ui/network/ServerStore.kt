package app.nagmedar.ui.network

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.NetworkServerDao
import app.nagmedar.data.NetworkServerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.videolan.resources.NetworkMediaHook
import org.videolan.resources.NetworkMediaOptions
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.PlaybackObserver
import java.util.concurrent.ConcurrentHashMap

/** S-1301: a server the user saved, as the network browser lists it; [signedIn] when a sign-in is kept for it. */
@Immutable
data class SavedServer(val id: Long, val name: String, val url: String, val signedIn: Boolean) {
    val protocol: NetworkProtocol? get() = NetworkProtocol.of(url.substringBefore("://", ""))
}

/** A saved server's sign-in as it can be had now. */
sealed interface StoredSignIn {
    data object None : StoredSignIn

    data class Kept(val credentials: Credentials) : StoredSignIn

    /** One was kept but cannot be opened any more (the key is gone); it has been forgotten. */
    data object Unreadable : StoredSignIn

    /** One is kept but the Keystore did not open it now; it stays, to be tried again. */
    data object Unavailable : StoredSignIn
}

/** What saving a server does with its sign-in. */
sealed interface SignInChange {
    data object Unchanged : SignInChange

    data object Forget : SignInChange

    /** [credentials] used; kept sealed when [remember], else for this session only. */
    data class Use(val credentials: Credentials, val remember: Boolean) : SignInChange
}

/**
 * S-1301: sign-ins given for this session only ("remember" left off), by the address they were
 * given for; gone when the app's process is.
 */
object SessionSignIns {
    private val byUrl = ConcurrentHashMap<String, Credentials>()

    fun keep(url: String, credentials: Credentials) {
        byUrl[url] = credentials
    }

    fun forget(url: String) {
        byUrl.remove(url)
    }

    /** The sign-in given for [url] or the nearest folder above it. */
    fun forUrl(url: String): Credentials? =
        byUrl.entries.filter { isUnder(url, it.key) }.maxByOrNull { it.key.length }?.value

    /** Tests: none kept. */
    internal fun clear() = byUrl.clear()
}

/** S-1301: where saved servers are kept: Nağmedar's database in the app, a list in the tests. */
interface ServerStore {
    /** By name, again after each change. */
    val servers: Flow<List<SavedServer>>

    /** A server saved: a new one ([id] 0) or [id] changed, under [name] at [url], its sign-in as [signIn] says. */
    suspend fun save(id: Long, name: String, url: String, signIn: SignInChange): SaveResult

    suspend fun remove(id: Long)

    /** [id]'s sign-in; one that cannot be opened any more is forgotten. */
    suspend fun signIn(id: Long): StoredSignIn

    /** [id] was opened now. */
    suspend fun used(id: Long)

    /** The sign-in for [url]: the saved server's it lies under, else this session's. Blocking. */
    fun signInFor(url: String): Credentials?

    /** The kept sign-in of the one saved server whose host libVLC's request names in [words]. Blocking. */
    fun signInNamed(words: String): Credentials?
}

/** How saving a server went. */
sealed interface SaveResult {
    /** Saved as [id]; its sign-in kept unless the Keystore could not seal it ([signInKept] false: this session only). */
    data class Saved(val id: Long, val signInKept: Boolean = true) : SaveResult

    /** Another saved server has this address. */
    data object AddressTaken : SaveResult
}

/** S-1301: the saved servers of Nağmedar's database, their sign-ins sealed by [seal]. */
class DatabaseServerStore(
    context: Context,
    private val seal: CredentialSeal = CredentialSeal(KeystoreCipher()),
    /** The table; the tests' database's. */
    table: NetworkServerDao? = null,
) : ServerStore {

    private val app = context.applicationContext
    private val dao: NetworkServerDao by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { table ?: NagmedarDatabase.get(app).networkServers() }

    override val servers: Flow<List<SavedServer>>
        get() = dao.observe().map { rows -> rows.map { SavedServer(it.id, it.name, it.url, it.credentials != null) } }.flowOn(Dispatchers.IO)

    override suspend fun save(id: Long, name: String, url: String, signIn: SignInChange): SaveResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val old = if (id == 0L) null else dao.get(id)
        // the Keystore may fail to seal now: the row is kept, the sign-in serves this session only
        var sealFailed = false
        fun sealed(credentials: Credentials): ByteArray? = try {
            seal.seal(credentials, url)
        } catch (e: Exception) {
            sealFailed = true
            null
        }
        val kept = when (signIn) {
            SignInChange.Forget -> null
            is SignInChange.Use -> if (signIn.remember) sealed(signIn.credentials) else null
            // moved to another address, a kept sign-in is sealed again for it; one that cannot be opened now stays as it was
            SignInChange.Unchanged -> old?.credentials?.let { bytes ->
                if (old.url == url) bytes else runCatching { seal.open(bytes, old.url) }.getOrNull()?.let(::sealed)
            }
        }
        val entry = NetworkServerEntry(id = old?.id ?: 0, name = name, url = url, credentials = kept, createdAt = old?.createdAt ?: now, lastUsedAt = old?.lastUsedAt ?: 0)
        val saved = try {
            if (old == null) dao.insert(entry) else old.id.also { dao.update(entry) }
        } catch (e: SQLiteConstraintException) {
            return@withContext SaveResult.AddressTaken
        }
        if (old != null && old.url != url) SessionSignIns.forget(old.url)
        when (signIn) {
            is SignInChange.Use -> if (signIn.remember && !sealFailed) SessionSignIns.forget(url) else SessionSignIns.keep(url, signIn.credentials)
            SignInChange.Forget -> SessionSignIns.forget(url)
            SignInChange.Unchanged -> Unit
        }
        // a sign-in typed anew: the library's scan may use it again
        if (signIn is SignInChange.Use) ServerAddress.parse(url)?.host?.let { NetworkDialogs.router.renewed(it) }
        SaveResult.Saved(saved, signInKept = !sealFailed)
    }

    override suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        dao.get(id)?.let { SessionSignIns.forget(it.url) }
        dao.delete(id)
    }

    override suspend fun signIn(id: Long): StoredSignIn = withContext(Dispatchers.IO) {
        val entry = dao.get(id) ?: return@withContext StoredSignIn.None
        val sealed = entry.credentials ?: return@withContext SessionSignIns.forUrl(entry.url)?.let { StoredSignIn.Kept(it) } ?: StoredSignIn.None
        try {
            StoredSignIn.Kept(seal.open(sealed, entry.url))
        } catch (e: UnreadableCredentialsException) {
            if (!e.lost) return@withContext StoredSignIn.Unavailable
            dao.setCredentials(id, null)
            StoredSignIn.Unreadable
        } catch (e: Exception) {
            StoredSignIn.Unavailable
        }
    }

    override suspend fun used(id: Long) = withContext(Dispatchers.IO) { dao.used(id, System.currentTimeMillis()) }

    override fun signInFor(url: String): Credentials? {
        val entry = dao.all().filter { isUnder(url, it.url) }.maxByOrNull { it.url.length }
        val kept = entry?.credentials?.let { sealed -> runCatching { seal.open(sealed, entry.url) }.getOrNull() }
        return kept ?: SessionSignIns.forUrl(url)
    }

    override fun signInNamed(words: String): Credentials? {
        val named = dao.all().filter { row -> ServerAddress.parse(row.url)?.host?.let { hostNamed(words, it) } == true }
        // one server's host, or none: never a guess between two
        if (named.mapNotNull { ServerAddress.parse(it.url)?.host?.lowercase() }.distinct().size != 1) return null
        return named.firstNotNullOfOrNull { row -> row.credentials?.let { sealed -> runCatching { seal.open(sealed, row.url) }.getOrNull() } }
    }
}

/**
 * S-1301: while the playback service lives, what it plays from a saved server signs in with the
 * server's user name and password as libVLC's options ([NetworkMediaHook]), whatever started it.
 */
class NetworkSignInObserver : PlaybackObserver {
    private var options: NetworkMediaOptions? = null

    override fun attach(service: PlaybackService) {
        NetworkDialogs.install(service.applicationContext)
        val store = DatabaseServerStore(service.applicationContext)
        val hook = object : NetworkMediaOptions {
            override fun optionsFor(media: Uri): List<String> {
                val protocol = NetworkProtocol.of(media.scheme) ?: return emptyList()
                val url = media.toString()
                val credentials = runCatching { store.signInFor(url) }.getOrNull()
                val options = credentials?.let { signInOptions(protocol, it) }.orEmpty()
                // libVLC's own request while it opens (WebDAV's, or the options turned down) is answered for this server
                NetworkDialogs.router.begin(
                    DialogClaim(ServerAddress.parse(url)?.host.orEmpty(), protocol, credentials, triedInOptions = options.isNotEmpty(), browse = false, since = SystemClock.elapsedRealtime()),
                )
                return options
            }
        }
        options = hook
        NetworkMediaHook.options = hook
    }

    override fun detach() {
        if (NetworkMediaHook.options === options) NetworkMediaHook.options = null
        options = null
    }
}
