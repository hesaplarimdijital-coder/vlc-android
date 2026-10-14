package app.nagmedar.ui.radio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlin.random.Random

/** S-1105: the servers are found by DNS, spread by a shuffle, and a bad one is left aside for a while. */
@RunWith(AndroidJUnit4::class)
class RadioServersTest {

    /** A name service with the directory's shape: one name, many addresses, each address with its server's name. */
    private class FakeDns(
        private val addresses: List<String>,
        private val names: Map<String, String?>,
    ) : ServerDns {
        @Volatile
        var asked = 0

        override fun addresses(name: String): List<String> {
            assertEquals(RadioServers.ALL_HOST, name)
            asked++
            return addresses
        }

        override fun hostName(address: String): String? = names[address]
    }

    private val dns = FakeDns(
        listOf("10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4"),
        mapOf(
            "10.0.0.1" to "de1.api.radio-browser.info",
            "10.0.0.2" to "fr1.api.radio-browser.info.",
            "10.0.0.3" to "NL1.api.radio-browser.info",
            // not the directory's: dropped
            "10.0.0.4" to "ec2-1-2-3-4.compute.amazonaws.com",
        ),
    )
    private val all = setOf("de1.api.radio-browser.info", "fr1.api.radio-browser.info", "nl1.api.radio-browser.info")

    private var now = 1_000_000L

    private fun servers(dns: ServerDns = this.dns, listing: suspend () -> List<String> = { emptyList() }, seed: Int = 7, seeds: List<String> = emptyList(), dnsTimeoutMs: Long = 5_000L) =
        RadioServers(dns, listing, Random(seed), clock = { now }, badForMs = 10 * 60_000L, keepForMs = 60 * 60_000L, dnsTimeoutMs = dnsTimeoutMs, seeds = seeds)

    @Test
    fun theNamesAreTheReverseOfEachAddress() = runBlocking {
        val found = servers().candidates()
        assertEquals(all, found.toSet())
        assertEquals(3, found.size)
    }

    @Test
    fun theOrderIsShuffledAndKeptUntilTheyAreLookedForAgain() = runBlocking {
        val orders = (1..12).map { servers(seed = it).candidates() }.toSet()
        // users spread over the servers: not one order for everybody
        assertTrue(orders.size > 1)
        val before = dns.asked
        val one = servers()
        assertEquals(one.candidates(), one.candidates())
        // the shuffled list is made once, not at each request
        assertEquals(before + 1, dns.asked)
    }

    @Test
    fun theyAreLookedForAgainAfterAWhile() = runBlocking {
        val servers = servers()
        servers.candidates()
        val asked = dns.asked
        now += 30 * 60_000L
        servers.candidates()
        assertEquals(asked, dns.asked)
        now += 31 * 60_000L
        // the old list answers at once; the lookup is made behind it
        assertEquals(all, servers.candidates().toSet())
        until { dns.asked == asked + 1 }
        assertEquals(asked + 1, dns.asked)
    }

    @Test
    fun anOldListAnswersAtOnceEvenWhenTheNameServiceHangs() = runBlocking {
        val gate = java.util.concurrent.CountDownLatch(1)
        var hang = false
        val flaky = object : ServerDns {
            override fun addresses(name: String): List<String> {
                if (hang) gate.await()
                return dns.addresses(name)
            }

            override fun hostName(address: String): String? = dns.hostName(address)
        }
        val servers = servers(flaky, dnsTimeoutMs = 10_000)
        val first = servers.candidates()
        hang = true
        now += 2 * 60 * 60_000L
        val started = System.nanoTime()
        assertEquals(first, servers.candidates())
        assertEquals(first, servers.candidates())
        gate.countDown()
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000)
    }

    /** Waits (a few seconds at most) for something done in the background. */
    private fun until(condition: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < end) Thread.sleep(10)
    }

    @Test
    fun aBadServerIsLastUntilItsTimeIsUp() = runBlocking {
        val servers = servers()
        val order = servers.candidates()
        servers.markBad(order[0])
        assertEquals(order.drop(1) + order[0], servers.candidates())
        now += 9 * 60_000L
        assertEquals(order[0], servers.candidates().last())
        now += 2 * 60_000L
        assertEquals(order, servers.candidates())
    }

    @Test
    fun aServerThatAnsweredIsForgiven() = runBlocking {
        val servers = servers()
        val order = servers.candidates()
        servers.markBad(order[0])
        servers.markGood(order[0])
        assertEquals(order, servers.candidates())
    }

    @Test
    fun forgettingLooksForThemAgain() = runBlocking {
        val servers = servers()
        servers.candidates()
        val asked = dns.asked
        servers.forget()
        servers.candidates()
        assertEquals(asked + 1, dns.asked)
    }

    @Test
    fun withoutDnsTheListIsRead() = runBlocking {
        val empty = FakeDns(emptyList(), emptyMap())
        var listed = 0
        val servers = servers(empty, listing = {
            listed++
            listOf("at1.api.radio-browser.info", "de2.api.radio-browser.info", "evil.example.com", "at1.api.radio-browser.info")
        })
        assertEquals(setOf("at1.api.radio-browser.info", "de2.api.radio-browser.info"), servers.candidates().toSet())
        servers.candidates()
        assertEquals(1, listed)
    }

    @Test
    fun withNeitherNothingIsFound() = runBlocking {
        val none = FakeDns(emptyList(), emptyMap())
        assertEquals(emptyList<String>(), servers(none, listing = { throw IOException("no route") }).candidates())
        assertEquals(emptyList<String>(), servers(none).candidates())
    }

    @Test
    fun aReverseLookupThatHangsDoesNotHoldTheRequest() = runBlocking {
        val gate = java.util.concurrent.CountDownLatch(1)
        val hanging = object : ServerDns {
            override fun addresses(name: String) = listOf("10.0.0.1", "10.0.0.2", "10.0.0.3")

            override fun hostName(address: String): String? {
                if (address == "10.0.0.2") gate.await()
                return "h${address.last()}.api.radio-browser.info"
            }
        }
        val started = System.nanoTime()
        val found = servers(hanging, dnsTimeoutMs = 150).candidates()
        gate.countDown()
        // what answered in time is used, the hung one is left out, and the wait is the limit, not the hang
        assertEquals(setOf("h1.api.radio-browser.info", "h3.api.radio-browser.info"), found.toSet())
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000)
    }

    @Test
    fun aForwardLookupThatHangsFallsBackToTheList() = runBlocking {
        val gate = java.util.concurrent.CountDownLatch(1)
        val hanging = object : ServerDns {
            override fun addresses(name: String): List<String> {
                gate.await()
                return emptyList()
            }

            override fun hostName(address: String): String? = null
        }
        val found = servers(hanging, listing = { listOf("at1.api.radio-browser.info") }, dnsTimeoutMs = 100).candidates()
        gate.countDown()
        assertEquals(listOf("at1.api.radio-browser.info"), found)
    }

    @Test
    fun theReverseLookupsRunTogether() = runBlocking {
        val inFlight = java.util.concurrent.atomic.AtomicInteger()
        val most = java.util.concurrent.atomic.AtomicInteger()
        val slow = object : ServerDns {
            override fun addresses(name: String) = (1..4).map { "10.0.0.$it" }

            override fun hostName(address: String): String? {
                most.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                Thread.sleep(150)
                inFlight.decrementAndGet()
                return "h${address.last()}.api.radio-browser.info"
            }
        }
        assertEquals(4, servers(slow).candidates().size)
        assertTrue(most.get() > 1)
    }

    @Test
    fun withNeitherDnsNorListTheBuiltInServersAreTheLastResort() = runBlocking {
        val none = FakeDns(emptyList(), emptyMap())
        val seeded = servers(none, listing = { throw IOException("no route") }, seeds = RadioServers.SEEDS)
        val found = seeded.candidates()
        assertEquals(RadioServers.SEEDS.toSet(), found.toSet())
        assertTrue("de1.api.radio-browser.info" in found && "nl1.api.radio-browser.info" in found && "at1.api.radio-browser.info" in found)
        // they stand in only for a short while: DNS is asked again soon
        val asked = none.asked
        now += 2 * 60_000L
        seeded.candidates()
        until { none.asked == asked + 1 }
        assertEquals(asked + 1, none.asked)
    }

    @Test
    fun serverNamesStayInTheDirectorysDomain() {
        assertEquals("de1.api.radio-browser.info", RadioServers.serverName(" DE1.api.radio-browser.info. "))
        assertNull(RadioServers.serverName("radio-browser.info"))
        assertNull(RadioServers.serverName("api.radio-browser.info.evil.example"))
        assertNull(RadioServers.serverName("de1.api.radio-browser.info/path"))
        assertNull(RadioServers.serverName("de1 .api.radio-browser.info"))
        assertNull(RadioServers.serverName(""))
    }

    @Test
    fun theServerListIsRead() {
        val text = """[{"ip":"1.2.3.4","name":"de1.api.radio-browser.info"},{"ip":"5.6.7.8","name":"fr1.api.radio-browser.info"},""" +
            """{"ip":"9.9.9.9","name":"elsewhere.example"},{"ip":"1.2.3.4"}]"""
        assertEquals(listOf("de1.api.radio-browser.info", "fr1.api.radio-browser.info"), RadioServers.parseListing(text))
        assertEquals(emptyList<String>(), RadioServers.parseListing("<html>nope</html>"))
        assertEquals(emptyList<String>(), RadioServers.parseListing("{}"))
    }
}
