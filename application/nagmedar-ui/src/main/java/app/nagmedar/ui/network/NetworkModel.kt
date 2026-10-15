package app.nagmedar.ui.network

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.CoroutineContext

/** What the network browser tells in passing (a toast in the app). */
sealed interface NetworkNotice {
    /** [count] files put after what plays. */
    data class Queued(val count: Int) : NetworkNotice

    /** The folder [name] holds nothing to play. */
    data class NothingToPlay(val name: String) : NetworkNotice

    /** The folder [name] was handed to the library's scan; without [signInKept], later scans cannot sign in. */
    data class AddedToLibrary(val name: String, val signInKept: Boolean = true) : NetworkNotice

    /** Another saved server has this address already. */
    data object AddressTaken : NetworkNotice

    /** [count] folders were passed over while gathering: their servers asked a question (a certificate). */
    data class FoldersSkipped(val count: Int) : NetworkNotice

    /** The Keystore could not keep the sign-in: it serves until the app closes. */
    data object SignInNotKept : NetworkNotice
}

/**
 * S-1301: what the network browser does with files: play them or queue them with the playback
 * service, and give a folder to the medialibrary's scan.
 */
interface NetworkActions {
    /** [entries] played from [index]. */
    fun play(entries: List<NetworkEntry>, index: Int)

    /** [entries] put after what plays. */
    fun enqueue(entries: List<NetworkEntry>)

    /** Whether the medialibrary can scan the folder at [url] (VLC's list of protocols). */
    fun canAddToLibrary(url: String): Boolean

    /** The folder at [url] added to the library's scanned folders. */
    fun addToLibrary(url: String)
}

/**
 * S-206, S-1301: the network browser's state and what it does: the saved servers, the servers on
 * this network, the open folder ([FolderView], the reducer) read through [source], sign-ins kept
 * in [store]; [now] is the clock the slow network's seconds are counted on.
 */
@Stable
class NetworkModel(
    private val source: NetworkSource,
    private val store: ServerStore,
    private val scope: CoroutineScope,
    private val locale: Locale,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val onNotice: (NetworkNotice) -> Unit = {},
    /** Where the saved servers' database is read; the tests' own. */
    private val io: CoroutineContext = Dispatchers.IO,
    /** How long the list says it is searching the network. */
    private val searchMs: Long = SEARCH_MS,
) {
    /** Null until read. */
    var saved: List<SavedServer>? by mutableStateOf(null)
        private set

    var discovery by mutableStateOf(Discovery())
        private set

    /** The open folder; null when no server is open. */
    var folder: FolderView? by mutableStateOf(null)
        private set

    /** The folder whose files are being gathered to play, by name; null when none is. */
    var gathering: String? by mutableStateOf(null)
        private set

    /** The open server's sign-in. */
    private var credentials: Credentials? = null
    private var browseJob: Job? = null
    private var discoveryJob: Job? = null
    private var gatherJob: Job? = null

    /** Folders the gathering passed over: they asked something. */
    private var skipped = 0

    /** Follows the saved servers for as long as the caller's coroutine runs. */
    suspend fun followSaved() {
        store.servers.collect { saved = it }
    }

    /** Looks for servers on this network until [stopDiscovery]; the list says "searching" for a few seconds. */
    fun discover() {
        discoveryJob?.cancel()
        discovery = Discovery(searching = true)
        discoveryJob = scope.launch {
            val quiet = launch {
                delay(searchMs)
                discovery = discovery.copy(searching = false)
            }
            source.discover().catch { }.collect { event ->
                discovery = when (event) {
                    is DiscoveryEvent.Found -> discovery.found(event.server)
                    is DiscoveryEvent.Lost -> discovery.lost(event.url)
                }
            }
            quiet.cancel()
            discovery = discovery.copy(searching = false)
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        discovery = Discovery()
    }

    /** [server] opened at its folder, with its kept sign-in. */
    fun open(server: SavedServer) {
        val open = OpenServer(server.name, server.url, server.id)
        val view = FolderView.opening(open, now())
        // another server's files are no longer gathered (nor read with its sign-in)
        stopGathering()
        folder = view
        start {
            val signIn = store.signIn(server.id)
            store.used(server.id)
            credentials = (signIn as? StoredSignIn.Kept)?.credentials
            when (signIn) {
                StoredSignIn.Unreadable -> folder = view.signIn(SignInReason.Forgotten)
                StoredSignIn.Unavailable -> folder = view.signIn(SignInReason.Unavailable)
                else -> read(view)
            }
        }
    }

    /** The open server opened again: its kept sign-in read again (the Keystore may answer now). */
    fun reopen() {
        val server = folder?.server ?: return
        val id = server.savedId
        if (id != null) open(SavedServer(id, server.name, server.url, signedIn = true)) else refresh()
    }

    /** [server] found on the network opened, with a sign-in given for it before. */
    fun open(server: DiscoveredServer) {
        val view = FolderView.opening(OpenServer(server.name, server.url), now())
        stopGathering()
        folder = view
        start {
            credentials = withContext(io) { store.signInFor(server.url) }
            read(view)
        }
    }

    /** A folder of the open one opened; a file played with the folder's others after it. */
    fun open(entry: NetworkEntry, actions: NetworkActions) {
        val view = folder ?: return
        when {
            entry.kind == EntryKind.Folder -> start { read(view.into(entry, now())) }
            entry.kind.playable -> view.playable.let { files -> actions.play(files, files.indexOf(entry).coerceAtLeast(0)) }
            // libVLC may still play what it was not named for
            else -> actions.play(listOf(entry), 0)
        }
    }

    /** The folder above shown again; false at the server's root. */
    fun up(): Boolean {
        val above = folder?.up() ?: return false
        browseJob?.cancel()
        folder = above
        return true
    }

    /** The folder [index] of the trail shown again (0: the server's root). */
    fun goTo(index: Int) {
        val view = folder ?: return
        if (index >= view.trail.size - 1) return
        browseJob?.cancel()
        folder = view.upTo(index)
    }

    /** The open folder read again. */
    fun refresh() {
        val view = folder ?: return
        start { read(view.reread(now())) }
    }

    /** Reading given up: the folder above as it was, or no server open. */
    fun cancel() {
        browseJob?.cancel()
        folder = folder?.cancelled()
    }

    /** No server open. */
    fun close() {
        browseJob?.cancel()
        gatherJob?.cancel()
        gathering = null
        folder = null
        credentials = null
    }

    /**
     * The open folder read again signed in with [credentials]; once the server takes them they are
     * kept sealed with the saved server when [remember] (a server found on the network is saved
     * for it), else for this session.
     */
    fun signIn(credentials: Credentials, remember: Boolean) {
        val view = folder ?: return
        this.credentials = credentials
        start {
            read(view.reread(now()))
            if (folder?.status != FolderStatus.Listed) return@start
            val server = view.server
            // a server found on the network that is saved already: its row takes the sign-in
            val id = server.savedId ?: saved?.firstOrNull { it.url.trimEnd('/') == server.url.trimEnd('/') }?.id
            if (id == null && !remember) {
                SessionSignIns.keep(server.url, credentials)
                return@start
            }
            when (val result = store.save(id ?: 0, server.name, server.url, SignInChange.Use(credentials, remember))) {
                SaveResult.AddressTaken -> onNotice(NetworkNotice.AddressTaken)
                is SaveResult.Saved -> {
                    if (id == null) folder = folder?.copy(server = server.copy(savedId = result.id))
                    if (!result.signInKept) onNotice(NetworkNotice.SignInNotKept)
                }
            }
        }
    }

    /** libVLC's question on the folder's page answered: its [action] (1 or 2), or turned down (null). */
    fun answer(prompt: LibVlcPrompt, action: Int?) {
        if (action == null) prompt.dismiss() else prompt.action(action)
        folder = folder?.asked(null)
    }

    /**
     * A server saved ([id] 0: a new one) and opened; [done] hears whether it was (another server
     * may have its address).
     */
    fun save(id: Long, name: String, url: String, signIn: SignInChange, done: (Boolean) -> Unit = {}) {
        scope.launch {
            val result = store.save(id, name, url, signIn)
            done(result is SaveResult.Saved)
            if (result !is SaveResult.Saved) {
                onNotice(NetworkNotice.AddressTaken)
                return@launch
            }
            if (!result.signInKept) onNotice(NetworkNotice.SignInNotKept)
            val open = folder?.server
            if (id == 0L || open?.savedId == id) open(SavedServer(result.id, name, url, signIn is SignInChange.Use && signIn.remember))
        }
    }

    /** [server] removed; [restored] tells the screen how to bring it back, with its sign-in. */
    fun remove(server: SavedServer, restored: (restore: () -> Unit) -> Unit = {}) {
        if (folder?.server?.savedId == server.id) close()
        saved = saved?.filter { it.id != server.id }
        scope.launch {
            val kept = (store.signIn(server.id) as? StoredSignIn.Kept)?.credentials
            store.remove(server.id)
            restored {
                scope.launch { store.save(0, server.name, server.url, kept?.let { SignInChange.Use(it, remember = true) } ?: SignInChange.Forget) }
            }
        }
    }

    /**
     * The media of [entry]'s folder (the open one when null) and of the folders under it, played or
     * [enqueue]d: up to [MAX_GATHERED] files, [MAX_DEPTH] folders down.
     */
    fun playFolder(entry: NetworkEntry?, enqueue: Boolean, actions: NetworkActions) {
        val view = folder ?: return
        val name = entry?.name ?: view.name
        // this server's sign-in as it is now; another server opened meanwhile stops the gathering
        val signIn = credentials
        gatherJob?.cancel()
        gatherJob = scope.launch {
            gathering = name
            skipped = 0
            val files = ArrayList<NetworkEntry>()
            val order = FolderView.order(view.server, locale)
            if (entry == null && view.status == FolderStatus.Listed) {
                gather(view.entries, 0, files, order, signIn)
            } else {
                gather(entry?.url ?: view.url, 0, files, order, signIn)
            }
            gathering = null
            if (skipped > 0) onNotice(NetworkNotice.FoldersSkipped(skipped))
            when {
                files.isEmpty() -> onNotice(NetworkNotice.NothingToPlay(name))
                enqueue -> {
                    actions.enqueue(files)
                    onNotice(NetworkNotice.Queued(files.size))
                }
                else -> actions.play(files, 0)
            }
        }
    }

    /** Gathering given up. */
    fun stopGathering() {
        gatherJob?.cancel()
        gathering = null
    }

    /** The folder at [url] (the open one when null) given to the library's scan. */
    fun addToLibrary(entry: NetworkEntry?, actions: NetworkActions) {
        val view = folder ?: return
        val url = entry?.url ?: view.url
        if (!actions.canAddToLibrary(url)) return
        actions.addToLibrary(url)
        // the library's scans after a restart sign in with a kept password only
        val kept = view.server.savedId?.let { id -> saved?.firstOrNull { it.id == id }?.signedIn } == true
        onNotice(NetworkNotice.AddedToLibrary(entry?.name ?: view.name, signInKept = credentials == null || kept))
    }

    private suspend fun gather(url: String, depth: Int, into: MutableList<NetworkEntry>, order: Comparator<NetworkEntry>?, signIn: Credentials?) {
        val found = ArrayList<NetworkEntry>()
        var asked = false
        // a folder that asks something (a certificate) is passed over: its question goes with its reading
        source.browse(url, signIn)
            .catch { }
            .takeWhile { event ->
                asked = event is BrowseEvent.Asked && event.prompt != null
                !asked
            }
            .collect { if (it is BrowseEvent.Found) found += it.entry }
        if (asked) {
            skipped++
            return
        }
        gather(if (order != null) found.sortedWith(order) else found, depth, into, order, signIn)
    }

    private suspend fun gather(entries: List<NetworkEntry>, depth: Int, into: MutableList<NetworkEntry>, order: Comparator<NetworkEntry>?, signIn: Credentials?) {
        entries.filter { it.kind.playable }.forEach { if (into.size < MAX_GATHERED) into += it }
        if (depth >= MAX_DEPTH) return
        for (folder in entries.filter { it.kind == EntryKind.Folder }) {
            if (into.size >= MAX_GATHERED) return
            gather(folder.url, depth + 1, into, order, signIn)
        }
    }

    private fun start(block: suspend () -> Unit) {
        browseJob?.cancel()
        browseJob = scope.launch { block() }
    }

    /** [view] read from the network, shown as it fills (every [BATCH_MS]) and when it ends. */
    private suspend fun read(view: FolderView) {
        folder = view
        val order = FolderView.order(view.server, locale)
        val batch = ArrayList<NetworkEntry>()
        var shownAt = now()
        fun show() {
            if (batch.isEmpty()) return
            folder = folder?.takeIf { it.trail == view.trail }?.found(batch.toList(), order)
            batch.clear()
            shownAt = now()
        }
        source.browse(view.url, credentials)
            .catch { emit(BrowseEvent.Ended(BrowseResult.Unreachable)) }
            .collect { event ->
                when (event) {
                    is BrowseEvent.Found -> {
                        batch += event.entry
                        if (now() - shownAt >= BATCH_MS) show()
                    }
                    is BrowseEvent.Asked -> folder = folder?.takeIf { it.trail == view.trail }?.asked(event.prompt)
                    is BrowseEvent.Ended -> {
                        show()
                        folder = folder?.takeIf { it.trail == view.trail }?.ended(event.result)
                    }
                }
            }
        // a reading that ended without saying how
        val current = folder
        val reading = current?.status is FolderStatus.Connecting || current?.status is FolderStatus.Question
        if (current != null && current.trail == view.trail && reading) folder = current.ended(BrowseResult.Unreachable)
    }

    companion object {
        const val SEARCH_MS = 6_000L

        /** After this long, a folder being read shows how long it has taken, and a way to give up. */
        const val SLOW_MS = 3_000L

        private const val BATCH_MS = 150L
        const val MAX_GATHERED = 2_000
        const val MAX_DEPTH = 4
    }
}
