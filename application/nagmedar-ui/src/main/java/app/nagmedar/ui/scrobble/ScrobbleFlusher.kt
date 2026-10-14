package app.nagmedar.ui.scrobble

import app.nagmedar.data.ScrobbleDao
import app.nagmedar.data.ScrobbleEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** S-1008: where scrobbles wait to be sent: Nağmedar's database in the app, a list in the tests. */
interface ScrobbleStore {
    /** Queues [entry]; false when the same track at the same time already waits for its service. */
    fun add(entry: ScrobbleEntry): Boolean

    fun pending(service: ScrobbleServiceId, limit: Int): List<ScrobbleEntry>

    /** [entries] were sent, or given up on. */
    fun remove(entries: List<ScrobbleEntry>)

    /** Sending [entries] failed for a reason that may pass. */
    fun failed(entries: List<ScrobbleEntry>)

    fun count(service: ScrobbleServiceId): Int

    /** How many wait for each service, as it changes. */
    fun counts(): Flow<Map<ScrobbleServiceId, Int>>

    /** Forgets what waits for [service]; how many. */
    fun clear(service: ScrobbleServiceId): Int

    /** Forgets what was heard before [beforeSec] and waits for [service]; how many. */
    fun dropOlderThan(service: ScrobbleServiceId, beforeSec: Long): Int
}

/** The scrobble_queue table. */
class RoomScrobbleStore(private val dao: ScrobbleDao) : ScrobbleStore {
    override fun add(entry: ScrobbleEntry): Boolean = dao.add(entry) > 0

    override fun pending(service: ScrobbleServiceId, limit: Int): List<ScrobbleEntry> = dao.pending(service.key, limit)

    override fun remove(entries: List<ScrobbleEntry>) = dao.remove(entries.map { it.id })

    override fun failed(entries: List<ScrobbleEntry>) = dao.failed(entries.map { it.id })

    override fun count(service: ScrobbleServiceId): Int = dao.count(service.key)

    override fun counts(): Flow<Map<ScrobbleServiceId, Int>> =
        dao.counts().map { rows -> rows.mapNotNull { row -> ScrobbleServiceId.of(row.service)?.let { it to row.count } }.toMap() }

    override fun clear(service: ScrobbleServiceId): Int = dao.clear(service.key)

    override fun dropOlderThan(service: ScrobbleServiceId, beforeSec: Long): Int = dao.dropOlderThan(service.key, beforeSec)
}

/** What a flush came to, for the job that ran it. */
enum class FlushResult {
    /** Everything that could be sent was; what is left waits for the user (a held service, a switch off). */
    Done,

    /** Some of it could not be sent now: try again later. */
    Retry,
}

/**
 * S-1008: sends the queue: for each service that is on, signed in and not held, the oldest
 * scrobbles in batches of up to its batch size, until none are left or one batch cannot be sent.
 *  - Accepted: the batch leaves the queue.
 *  - Retry (no network, the server busy): the batch stays, its attempts are counted, the service
 *    is left for later ([FlushResult.Retry]); the others go on.
 *  - AuthFailed: the service is held (the user signs in again) and the queue waits.
 *  - Rejected: a batch of one is given up on; a larger one is sent scrobble by scrobble, so that
 *    one bad scrobble does not take the others with it.
 * Scrobbles older than a service takes ([ScrobbleService.maxAgeSec]) are dropped first.
 */
class ScrobbleFlusher(
    private val store: ScrobbleStore,
    private val settings: ScrobbleSettings,
    private val services: List<ScrobbleService>,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Sends what is queued for every service that sends. Cancellation stops it between batches, and the batch in flight is sent again later. */
    suspend fun flush(): FlushResult {
        var result = FlushResult.Done
        for (service in services) {
            val id = service.id
            if (!service.available || !settings.active(id)) continue
            val secret = settings.secret(id)
            if (secret == null) {
                // the account cannot be had: a copy of the preferences on another device
                settings.setHold(id, ScrobbleHold("secret"))
                continue
            }
            service.maxAgeSec?.let { store.dropOlderThan(id, clock() / 1_000 - it) }
            if (!flush(service, secret)) result = FlushResult.Retry
        }
        return result
    }

    /** One service's queue; false when it was left with scrobbles that may still be sent. */
    private suspend fun flush(service: ScrobbleService, secret: String): Boolean {
        while (true) {
            val batch = store.pending(service.id, service.batchSize)
            if (batch.isEmpty()) return true
            when (val outcome = send(service, secret, batch)) {
                SubmitOutcome.Accepted -> store.remove(batch)
                is SubmitOutcome.Retry -> {
                    store.failed(batch)
                    return false
                }
                is SubmitOutcome.AuthFailed -> {
                    settings.setHold(service.id, ScrobbleHold(outcome.reason))
                    return true
                }
                is SubmitOutcome.Rejected -> {
                    if (batch.size == 1) store.remove(batch) else if (!flushOneByOne(service, secret, batch)) return false
                    if (settings.hold(service.id) != null) return true
                }
            }
        }
    }

    /** A batch the service refused, sent scrobble by scrobble; false when one could not be sent for now. */
    private suspend fun flushOneByOne(service: ScrobbleService, secret: String, batch: List<ScrobbleEntry>): Boolean {
        for (entry in batch) {
            val one = listOf(entry)
            when (val outcome = send(service, secret, one)) {
                SubmitOutcome.Accepted, is SubmitOutcome.Rejected -> store.remove(one)
                is SubmitOutcome.Retry -> {
                    store.failed(one)
                    return false
                }
                is SubmitOutcome.AuthFailed -> {
                    settings.setHold(service.id, ScrobbleHold(outcome.reason))
                    return true
                }
            }
        }
        return true
    }

    private suspend fun send(service: ScrobbleService, secret: String, batch: List<ScrobbleEntry>): SubmitOutcome = try {
        service.submit(secret, batch)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SubmitOutcome.Retry(e.message)
    }
}
