package app.nagmedar.ui.radio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.random.Random

/** S-1105: the name service Radio Browser's servers are found with. */
interface ServerDns {
    /** Every address (A and AAAA records) [name] has, as text; none when it cannot be resolved. */
    fun addresses(name: String): List<String>

    /** The host name [address] reverse-resolves to; null when it has none. */
    fun hostName(address: String): String?
}

/** The device's own name service. */
object SystemServerDns : ServerDns {
    override fun addresses(name: String): List<String> = try {
        InetAddress.getAllByName(name).mapNotNull { it.hostAddress }.distinct()
    } catch (e: UnknownHostException) {
        emptyList()
    }

    override fun hostName(address: String): String? = try {
        // an address written as numbers is not looked up by getByName; the canonical name is the reverse lookup's
        InetAddress.getByName(address).canonicalHostName?.takeIf { it != address }
    } catch (e: UnknownHostException) {
        null
    }
}

/**
 * S-1105: the Radio Browser servers, as the directory asks its users to find them: all.api.radio-browser.info
 * has an address for every server, each of which reverse-resolves to the server's own name
 * (de1.api.radio-browser.info …). The names are shuffled once, so users spread over the servers,
 * and used with HTTPS. A server that failed is left for a while ([badForMs]) and only tried again
 * when the others fail too. The lookups run in parallel and outside the caller (a name service
 * that hangs holds a thread of its own, not the request), each step with a time limit
 * ([dnsTimeoutMs]), and what was found by then is used. When the name service gives nothing,
 * [listing] reads the server list from the directory's own address, and when that fails too
 * (it needs the same name service) the built-in [seeds] are the last resort.
 */
class RadioServers(
    private val dns: ServerDns,
    private val listing: suspend () -> List<String>,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    private val badForMs: Long = BAD_FOR_MS,
    private val keepForMs: Long = KEEP_FOR_MS,
    private val dnsTimeoutMs: Long = DNS_TIMEOUT_MS,
    private val seeds: List<String> = SEEDS,
) {
    private val finding = Mutex()
    @Volatile
    private var hosts: List<String> = emptyList()

    @Volatile
    private var foundAt = 0L
    private val refreshing = AtomicBoolean(false)
    private val badUntil = HashMap<String, Long>()

    /** Where the lookups run: a hung one is left behind there and does not hold the request. */
    private val lookups = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The servers to try, in order: those not left aside first. Empty when none can be found. With a
     * list at hand (even an old one) it answers at once; an old list is looked for again in the
     * background. Only the first call, with nothing known, waits for the lookups.
     */
    suspend fun candidates(): List<String> {
        var all = hosts
        if (all.isEmpty()) {
            all = finding.withLock {
                if (hosts.isEmpty()) refresh()
                hosts
            }
        } else if (clock() - foundAt >= keepForMs && refreshing.compareAndSet(false, true)) {
            lookups.launch {
                try {
                    finding.withLock { refresh() }
                } finally {
                    refreshing.set(false)
                }
            }
        }
        val now = clock()
        val (bad, good) = synchronized(badUntil) { all.partition { (badUntil[it] ?: 0L) > now } }
        return good + bad
    }

    /** Looks for the servers; what is found replaces the list, nothing found keeps it and asks again soon. */
    private suspend fun refresh() {
        val found = discover()
        if (found.names.isNotEmpty()) {
            hosts = found.names
            // the built-in list is a stopgap: the real one is looked for again soon
            foundAt = if (found.seeded) clock() - keepForMs + SEED_KEEP_MS else clock()
        } else {
            foundAt = clock() - keepForMs + SEED_KEEP_MS
        }
    }

    /** [host] did not answer (a timeout, a server error): it is left aside for a while. */
    fun markBad(host: String) {
        synchronized(badUntil) { badUntil[host] = clock() + badForMs }
    }

    /** [host] answered. */
    fun markGood(host: String) {
        synchronized(badUntil) { badUntil.remove(host) }
    }

    /** Every server failed: they are looked for again at the next request. */
    suspend fun forget() {
        finding.withLock { hosts = emptyList() }
    }

    private class Found(val names: List<String>, val seeded: Boolean = false)

    private suspend fun discover(): Found {
        val fromDns = fromDns()
        if (fromDns.isNotEmpty()) return Found(fromDns.shuffled(random))
        val listed = try {
            withTimeoutOrNull(dnsTimeoutMs) { listing() }.orEmpty().mapNotNull(::serverName).distinct()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        if (listed.isNotEmpty()) return Found(listed.shuffled(random))
        return Found(seeds.mapNotNull(::serverName).distinct().shuffled(random), seeded = true)
    }

    /** The servers' names by the name service: the forward lookup, then every reverse lookup at once; what came in time. */
    private suspend fun fromDns(): List<String> {
        val forward = lookups.async { quietly { dns.addresses(ALL_HOST) } ?: emptyList() }
        val addresses = withTimeoutOrNull(dnsTimeoutMs) { forward.await() }.orEmpty()
        if (addresses.isEmpty()) return emptyList()
        val names = AtomicReferenceArray<String?>(addresses.size)
        val reverse = addresses.mapIndexed { i, address ->
            lookups.async { names.set(i, quietly { dns.hostName(address) }?.let(::serverName)) }
        }
        withTimeoutOrNull(dnsTimeoutMs) { reverse.forEach { it.await() } }
        return (0 until addresses.size).mapNotNull { names.get(it) }.distinct()
    }

    private inline fun <T> quietly(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** The name that has an address for each server. */
        const val ALL_HOST = "all.api.radio-browser.info"

        /** Where the server list is read when the name service gives none. */
        const val LIST_URL = "https://$ALL_HOST/json/servers"

        const val BAD_FOR_MS = 10 * 60_000L
        const val KEEP_FOR_MS = 60 * 60_000L
        const val DNS_TIMEOUT_MS = 5_000L

        /** How long the built-in servers are used before the name service is asked again. */
        const val SEED_KEEP_MS = 60_000L

        /** Servers of the directory known to exist, for when neither the name service nor the list answers. */
        val SEEDS = listOf("de1", "de2", "fi1", "at1", "nl1").map { "$it.api.radio-browser.info" }

        private const val DOMAIN = ".radio-browser.info"

        /** [name] as a server's host name: lower case, no dot at its end; null for anything outside the directory's domain. */
        fun serverName(name: String): String? {
            val host = name.trim().trimEnd('.').lowercase(Locale.ROOT)
            return host.takeIf { it.length > DOMAIN.length && it.endsWith(DOMAIN) && host.none { c -> c.isWhitespace() || c == '/' } }
        }

        /** The names in the directory's server list (`/json/servers`): `[{"ip":"…","name":"de1.api.radio-browser.info"}, …]`. */
        fun parseListing(text: String): List<String> {
            val array = try {
                JSONArray(text)
            } catch (e: JSONException) {
                return emptyList()
            }
            return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("name")?.let(::serverName) }.distinct()
        }
    }
}
