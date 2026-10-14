package app.nagmedar.ui.scrobble

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.nagmedar.data.ScrobbleEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A queue in a list, as the database keeps it: oldest first, a track once per service and time. */
class FakeScrobbleStore : ScrobbleStore {
    val rows = mutableListOf<ScrobbleEntry>()
    private val sizes = MutableStateFlow<Map<ScrobbleServiceId, Int>>(emptyMap())
    private var next = 1L

    override fun add(entry: ScrobbleEntry): Boolean {
        if (rows.any { it.service == entry.service && it.artist == entry.artist && it.track == entry.track && it.listenedAt == entry.listenedAt }) return false
        rows += entry.copy(id = next++)
        publish()
        return true
    }

    override fun pending(service: ScrobbleServiceId, limit: Int) =
        rows.filter { it.service == service.key }.sortedWith(compareBy({ it.listenedAt }, { it.id })).take(limit)

    override fun remove(entries: List<ScrobbleEntry>) {
        rows.removeAll { row -> entries.any { it.id == row.id } }
        publish()
    }

    override fun failed(entries: List<ScrobbleEntry>) {
        for (entry in entries) {
            val at = rows.indexOfFirst { it.id == entry.id }
            if (at >= 0) rows[at] = rows[at].copy(attempts = rows[at].attempts + 1)
        }
    }

    override fun count(service: ScrobbleServiceId) = rows.count { it.service == service.key }

    override fun counts(): Flow<Map<ScrobbleServiceId, Int>> = sizes

    override fun clear(service: ScrobbleServiceId): Int {
        val before = rows.size
        rows.removeAll { it.service == service.key }
        publish()
        return before - rows.size
    }

    override fun dropOlderThan(service: ScrobbleServiceId, beforeSec: Long): Int {
        val before = rows.size
        rows.removeAll { it.service == service.key && it.listenedAt < beforeSec }
        publish()
        return before - rows.size
    }

    private fun publish() {
        sizes.value = ScrobbleServiceId.entries.associateWith { count(it) }.filterValues { it > 0 }
    }
}

/** A service that answers as it is told: [outcomes] in turn (the last one stays), and keeps what it was sent. */
class FakeScrobbleService(
    override val id: ScrobbleServiceId = ScrobbleServiceId.LastFm,
    override val available: Boolean = true,
    override val batchSize: Int = 50,
    override val maxAgeSec: Long? = null,
    var outcomes: List<SubmitOutcome> = listOf(SubmitOutcome.Accepted),
    var signInResult: Result<ScrobbleAccount> = Result.success(ScrobbleAccount("ada", "key-1")),
) : ScrobbleService {
    /** The requests it got. */
    val batches = mutableListOf<List<ScrobbleEntry>>()
    val secrets = mutableListOf<String>()
    val playing = mutableListOf<ScrobbleTrack>()
    val signIns = mutableListOf<SignInInput>()
    private var calls = 0

    /** What answers the request [entries] is in: [outcomes] by the number of the request. */
    var rule: ((List<ScrobbleEntry>) -> SubmitOutcome)? = null

    override suspend fun signIn(input: SignInInput): ScrobbleAccount {
        signIns += input
        return signInResult.getOrThrow()
    }

    override suspend fun nowPlaying(secret: String, track: ScrobbleTrack) {
        playing += track
    }

    override suspend fun submit(secret: String, entries: List<ScrobbleEntry>): SubmitOutcome {
        secrets += secret
        batches += entries
        val outcome = rule?.invoke(entries) ?: outcomes[minOf(calls, outcomes.lastIndex)]
        calls++
        return outcome
    }
}

class FakeScrobbleScheduler : ScrobbleScheduler {
    val requests = mutableListOf<Boolean>()

    override fun schedule(now: Boolean) {
        requests += now
    }
}

/** A box that "seals" by turning the text round, and opens only what it sealed. */
class FakeSecretBox : SecretBox {
    override fun seal(secret: String) = "sealed:" + secret.reversed()

    override fun open(sealed: String): String? = sealed.removePrefix("sealed:").takeIf { sealed.startsWith("sealed:") }?.reversed()
}

/** Preferences as the app keeps them (Robolectric's), empty. */
fun freshPrefs(name: String = "scrobble-test"): SharedPreferences {
    val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    return prefs
}

fun entry(service: ScrobbleServiceId = ScrobbleServiceId.LastFm, track: String = "Sabaha Kadar", at: Long = 1_800_000_000L) =
    ScrobbleEntry(service = service.key, artist = "Ada Karan", track = track, album = "Gece Mavisi", durationSec = 241, listenedAt = at, queuedAt = at)
