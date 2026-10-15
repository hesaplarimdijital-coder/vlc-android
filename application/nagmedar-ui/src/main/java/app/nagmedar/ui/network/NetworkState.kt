package app.nagmedar.ui.network

import androidx.compose.runtime.Immutable
import java.text.Collator
import java.util.Locale

/** One folder on the way down from a server: its name and its address. */
@Immutable
data class Crumb(val name: String, val url: String)

/** The server the browser has open: a saved one ([savedId]) or one found on the network. */
@Immutable
data class OpenServer(val name: String, val url: String, val savedId: Long? = null) {
    val protocol: NetworkProtocol? get() = NetworkProtocol.of(url.substringBefore("://", ""))
}

/** Why a folder waits for a sign-in. */
enum class SignInReason {
    /** The server asks for one and none is kept. */
    Needed,

    /** The one given was turned down. */
    Rejected,

    /** One was kept, but can no longer be opened on this device (its key is gone). */
    Forgotten,

    /** One is kept, but the Keystore did not open it now; it stays, to be tried again. */
    Unavailable,
}

/** Where reading a folder stands. */
@Immutable
sealed interface FolderStatus {
    /** libVLC is reading it since [since] (the clock's milliseconds). */
    data class Connecting(val since: Long) : FolderStatus

    data object Listed : FolderStatus

    data object Unreachable : FolderStatus

    data class SignIn(val reason: SignInReason) : FolderStatus

    /** libVLC asks [prompt] while it reads (a server's certificate), reading since [since]. */
    data class Question(val prompt: LibVlcPrompt, val since: Long) : FolderStatus
}

/**
 * S-206, S-1301: a folder of a network server as the browser shows it: where it is ([trail], the
 * server's root first), what was found in it, how reading it went, and the folder it was opened
 * from ([parent]), kept as listed so going up shows it again without reading the network.
 * Every change is a new value (the browser's reducer); the functions below are its steps.
 */
@Immutable
data class FolderView(
    val server: OpenServer,
    val trail: List<Crumb>,
    val entries: List<NetworkEntry> = emptyList(),
    val status: FolderStatus,
    val parent: FolderView? = null,
) {
    val url: String get() = trail.last().url
    val name: String get() = trail.last().name
    val atRoot: Boolean get() = parent == null

    /** The files the playback service plays, in the order shown. */
    val playable: List<NetworkEntry> get() = entries.filter { it.kind.playable }

    /** [entries] found while reading: in [order]'s place (folders first, by name), or last when there is none. */
    fun found(entries: List<NetworkEntry>, order: Comparator<NetworkEntry>?): FolderView {
        if (entries.isEmpty()) return this
        val all = this.entries + entries
        return copy(entries = if (order != null) all.sortedWith(order) else all)
    }

    /** Reading ended with [result]. */
    fun ended(result: BrowseResult): FolderView = copy(
        status = when (result) {
            BrowseResult.Done -> FolderStatus.Listed
            BrowseResult.Unreachable -> FolderStatus.Unreachable
            BrowseResult.SignInNeeded -> FolderStatus.SignIn(SignInReason.Needed)
            BrowseResult.SignInRejected -> FolderStatus.SignIn(SignInReason.Rejected)
        },
        // a folder that asks for a sign-in again shows nothing of what it held
        entries = if (result == BrowseResult.Done) entries else emptyList(),
    )

    /** libVLC asks [prompt] while it reads; null: the question was answered or taken back, the reading goes on. */
    fun asked(prompt: LibVlcPrompt?): FolderView = when (val current = status) {
        is FolderStatus.Connecting -> if (prompt != null) copy(status = FolderStatus.Question(prompt, current.since)) else this
        is FolderStatus.Question -> copy(status = if (prompt != null) FolderStatus.Question(prompt, current.since) else FolderStatus.Connecting(current.since))
        else -> this
    }

    /** [folder] of this one opened, to be read from [now]. */
    fun into(folder: NetworkEntry, now: Long): FolderView =
        FolderView(server, trail + Crumb(folder.name, folder.url), status = FolderStatus.Connecting(now), parent = this)

    /** The same folder read again from [now]. */
    fun reread(now: Long): FolderView = copy(entries = emptyList(), status = FolderStatus.Connecting(now))

    /** Asked for a sign-in for [reason]. */
    fun signIn(reason: SignInReason): FolderView = copy(entries = emptyList(), status = FolderStatus.SignIn(reason))

    /** The folder above, as it was listed; null at the server's root. */
    fun up(): FolderView? = parent

    /** The folder [index] of [trail] ([index] 0: the server's root), as it was listed. */
    fun upTo(index: Int): FolderView {
        var view = this
        while (view.trail.size > index + 1) view = view.parent ?: return view
        return view
    }

    /** Reading given up: the folder above as it was, null at the root (nothing was shown yet). */
    fun cancelled(): FolderView? = parent

    companion object {
        /** [server]'s root, to be read from [now]. */
        fun opening(server: OpenServer, now: Long): FolderView =
            FolderView(server, listOf(Crumb(server.name, server.url)), status = FolderStatus.Connecting(now))

        /**
         * Folders first, then by name as [locale] sorts ("Ağaç" before "Bahçe" in Turkish), numbers
         * in a name as numbers ("Bölüm 2" before "Bölüm 10"). UPnP servers keep their own order (null).
         */
        fun order(server: OpenServer, locale: Locale): Comparator<NetworkEntry>? {
            if (server.protocol == NetworkProtocol.Upnp) return null
            val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
            return compareBy<NetworkEntry> { it.kind != EntryKind.Folder }.then(Comparator { a, b -> naturalCompare(a.name, b.name, collator) })
        }

        /** [a] and [b] compared by [collator], their runs of digits as numbers. */
        internal fun naturalCompare(a: String, b: String, collator: Comparator<Any?>): Int {
            val partsA = parts(a)
            val partsB = parts(b)
            for (i in 0 until minOf(partsA.size, partsB.size)) {
                val x = partsA[i]
                val y = partsB[i]
                val c = if (x[0].isDigit() && y[0].isDigit()) {
                    x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 } ?: x.trimStart('0').compareTo(y.trimStart('0'))
                } else {
                    collator.compare(x, y)
                }
                if (c != 0) return c
            }
            return partsA.size.compareTo(partsB.size)
        }

        private val runs = Regex("""\d+|\D+""")

        private fun parts(text: String): List<String> = runs.findAll(text).map { it.value }.toList().ifEmpty { listOf("") }
    }
}

/** Whether the network can be looked through now. */
enum class LanState {
    Available,

    /** No Wi-Fi, Ethernet or VPN: only a mobile connection, or none. */
    NoLan,

    /** Network shares were turned off (the first run, or VLC's "Browse the network" setting). */
    Disabled,
}

/** S-1301: what the servers' list shows of the network: what was found, and whether it is still looked for. */
@Immutable
data class Discovery(val servers: List<DiscoveredServer> = emptyList(), val searching: Boolean = false) {
    /** [server] found; one already listed under its address is replaced. */
    fun found(server: DiscoveredServer): Discovery = copy(servers = (servers.filter { it.url != server.url } + server).sortedBy { it.name.lowercase(Locale.ROOT) })

    fun lost(url: String): Discovery = copy(servers = servers.filter { it.url != url })

    /** The servers that are not saved already: a saved one shows once, among the saved. */
    fun unsaved(saved: List<SavedServer>): List<DiscoveredServer> = servers.filter { found -> saved.none { it.url.trimEnd('/') == found.url.trimEnd('/') } }
}
