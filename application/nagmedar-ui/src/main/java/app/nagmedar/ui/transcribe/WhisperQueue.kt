package app.nagmedar.ui.transcribe

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.videolan.tools.Settings

/**
 * The podcast transcripts' Whisper jobs, in the order they run: those asked for by hand first, in the order they were
 * asked, then the automatic ones (new downloads); one job per episode. The first is the one under way or the next to
 * start; [running] is the one a job of this process has taken, which nothing is put before (it is not kept: a job
 * taken up after a restart takes it again). What the last try of an episode came to when it failed is kept for its
 * page ([failures], the latest [MAX_FAILURES]).
 */
@Immutable
data class WhisperQueue(
    val requests: List<WhisperRequest> = emptyList(),
    val running: Long? = null,
    val failures: Map<Long, WhisperFailure> = emptyMap(),
) {
    val head: WhisperRequest? get() = requests.firstOrNull()

    /** The job to start after the one under way: the first that is not [running]. */
    val next: WhisperRequest? get() = requests.firstOrNull { it.episodeId != running }

    fun has(episodeId: Long): Boolean = requests.any { it.episodeId == episodeId }

    /**
     * [request] asked for, its last failure forgotten: by hand, after the others asked by hand; automatic, last. An
     * episode asked for again keeps its place, unless a hand's ask finds it among the automatic ones: it moves up with
     * the hand's (the one under way stays where it is).
     */
    fun add(request: WhisperRequest): WhisperQueue {
        val cleared = failures - request.episodeId
        val at = requests.indexOfFirst { it.episodeId == request.episodeId }
        if (at < 0) return copy(requests = placed(requests, request), failures = cleared)
        val known = requests[at]
        val merged = request.copy(auto = known.auto && request.auto)
        val moves = known.auto && !merged.auto && known.episodeId != running
        if (!moves) return copy(requests = requests.toMutableList().also { it[at] = merged }, failures = cleared)
        return copy(requests = placed(requests.filterNot { it.episodeId == request.episodeId }, merged), failures = cleared)
    }

    private fun placed(list: List<WhisperRequest>, request: WhisperRequest): List<WhisperRequest> {
        if (request.auto) return list + request
        val firstAuto = list.indexOfFirst { it.auto && it.episodeId != running }.takeIf { it >= 0 } ?: list.size
        return list.take(firstAuto) + request + list.drop(firstAuto)
    }

    /** The job of [episodeId] taken out: done, cancelled, or its episode gone. */
    fun remove(episodeId: Long): WhisperQueue =
        copy(requests = requests.filterNot { it.episodeId == episodeId }, running = running.takeIf { it != episodeId })

    /** The job of [episodeId] failed for [reason]: out of the queue, its reason kept. */
    fun failed(episodeId: Long, reason: WhisperFailure): WhisperQueue {
        val kept = (failures - episodeId).entries.toList().takeLast(MAX_FAILURES - 1).associate { it.key to it.value } + (episodeId to reason)
        return remove(episodeId).copy(failures = kept)
    }

    /** A job has taken [episodeId]'s request. */
    fun begin(episodeId: Long): WhisperQueue = copy(running = episodeId)

    /** The job of [episodeId] was stopped by the system: it stays first, to be taken up again. */
    fun paused(episodeId: Long): WhisperQueue = copy(running = running.takeIf { it != episodeId })

    fun toJson(): String = JSONObject()
        .put("requests", JSONArray().apply { requests.forEach { put(JSONObject(it.toJson())) } })
        .put("failures", JSONObject().apply { failures.forEach { (id, reason) -> put(id.toString(), reason.key) } })
        .toString()

    companion object {
        const val MAX_FAILURES = 20

        /** The queue [json] holds; an empty one for nothing or what cannot be read. */
        fun parse(json: String?): WhisperQueue = runCatching {
            val o = JSONObject(json ?: return WhisperQueue())
            val list = o.optJSONArray("requests") ?: JSONArray()
            val requests = (0 until list.length()).mapNotNull { i -> list.optJSONObject(i)?.let { WhisperRequest.parse(it.toString()) } }
            val failed = o.optJSONObject("failures")
            val failures = failed?.keys()?.asSequence()?.mapNotNull { key -> key.toLongOrNull()?.let { it to WhisperFailure.of(failed.optString(key)) } }?.toMap().orEmpty()
            WhisperQueue(requests.distinctBy { it.episodeId }, failures = failures)
        }.getOrDefault(WhisperQueue())
    }
}

/** What a job needs from the network to start: nothing, any network, or an unmetered one. */
enum class NetworkNeed { None, Any, Unmetered }

/**
 * What a job needs to start: a network when the model or the episode's audio must be downloaded first (an unmetered
 * one when Settings › Podcasts says Wi-Fi only, and always for an automatic job); an automatic job also waits for the
 * charger and an idle device.
 */
@Immutable
data class JobNeeds(val network: NetworkNeed = NetworkNeed.None, val charging: Boolean = false, val idle: Boolean = false) {
    /** Whether a job started with these needs met may also run what [other] needs. */
    fun covers(other: JobNeeds): Boolean =
        network.ordinal >= other.network.ordinal && (charging || !other.charging) && (idle || !other.idle)

    companion object {
        fun of(request: WhisperRequest, modelReady: Boolean, downloaded: Boolean, wifiOnly: Boolean): JobNeeds {
            val network = when {
                modelReady && downloaded -> NetworkNeed.None
                wifiOnly || request.auto -> NetworkNeed.Unmetered
                else -> NetworkNeed.Any
            }
            return JobNeeds(network, charging = request.auto, idle = request.auto)
        }
    }
}

/** The queue in the app's preferences, and in this process as a flow that every page and the job follow. */
object WhisperQueueStore {
    private val queue = MutableStateFlow(WhisperQueue())

    @Volatile
    private var loaded = false

    /** The queue as it is now; read from the preferences at the first call. */
    fun state(context: Context): StateFlow<WhisperQueue> {
        load(context)
        return queue
    }

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        queue.value = WhisperQueue.parse(WhisperPrefs(Settings.getInstance(context.applicationContext)).queue)
        loaded = true
    }

    /** The queue changed by [change], kept; the queue as it is after. */
    @Synchronized
    fun update(context: Context, change: (WhisperQueue) -> WhisperQueue): WhisperQueue {
        load(context)
        val before = queue.value
        val after = change(before)
        if (after != before) {
            queue.value = after
            WhisperPrefs(Settings.getInstance(context.applicationContext)).queue = after.toJson()
        }
        return after
    }
}
