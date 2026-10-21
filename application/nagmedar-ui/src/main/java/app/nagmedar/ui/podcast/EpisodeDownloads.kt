package app.nagmedar.ui.podcast

import app.nagmedar.data.DownloadState
import app.nagmedar.data.PodcastDao
import app.nagmedar.data.PodcastEpisode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** S-1103: how far a running download has got; [total] is -1 when the server does not say. */
data class DownloadProgress(val done: Long, val total: Long) {
    /** 0 to 1, null when the length is not known. */
    val fraction: Float? get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
}

/** S-1103: what is downloading in this process: the progress the screens show, the cancel requests, and which episodes are being worked on. */
class DownloadTracker {
    private val running = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    private val cancelled: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /** The progress of every episode being downloaded now, by episode id. */
    val progress: StateFlow<Map<Long, DownloadProgress>> = running

    fun started(episodeId: Long) {
        cancelled.remove(episodeId)
        running.value = running.value + (episodeId to DownloadProgress(0, -1))
    }

    fun report(episodeId: Long, done: Long, total: Long) {
        running.value = running.value + (episodeId to DownloadProgress(done, total))
    }

    fun finished(episodeId: Long) {
        running.value = running.value - episodeId
        cancelled.remove(episodeId)
    }

    /** Asks the download of [episodeId] to stop at its next step. */
    fun cancel(episodeId: Long) {
        if (episodeId in running.value) cancelled += episodeId
    }

    fun isCancelled(episodeId: Long): Boolean = episodeId in cancelled

    fun isRunning(episodeId: Long): Boolean = episodeId in running.value
}

/** S-1103: what a run of the download queue came to. */
data class RunOutcome(
    val done: Int,
    val failed: Int,
    /** An episode was put back in the queue after a failure that may pass (network, server): the work should be tried again. */
    val retry: Boolean,
    /** The device is out of room: the rest of the queue was not tried. */
    val diskFull: Boolean,
    /** The episodes that were downloaded. */
    val completed: List<Long>,
)

/**
 * S-1103: downloads the queued episodes one after the other: each is claimed in Room (queued → running, so that no
 * two workers take it), downloaded into its partial file, put in its place by the [store] and marked done with its
 * file and size. A download the person cancels is dropped; one cut short by the system goes back in the queue and
 * continues where it stopped; a network or server failure goes back in the queue too, until [final] says the work
 * has tried enough; a missing, forbidden or refused file is failed at once.
 */
class DownloadRunner(
    private val dao: PodcastDao,
    private val downloader: FileDownloader,
    private val store: DownloadStore,
    private val tracker: DownloadTracker,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Runs [ids] in order. [onStart] hears the episode a download begins and [onProgress] how far it is, on the worker's
     * thread. Throws only [CancellationException], after the running episode is back in the queue.
     */
    suspend fun run(
        ids: List<Long>,
        final: Boolean,
        onStart: (PodcastEpisode) -> Unit = {},
        onProgress: (PodcastEpisode, DownloadProgress) -> Unit = { _, _ -> },
    ): RunOutcome {
        var done = 0
        var failed = 0
        var retry = false
        val completed = ArrayList<Long>()
        for (id in ids) {
            val episode = withContext(io) {
                dao.episode(id)?.also { known ->
                    // a download a dead process left running is the queue's again
                    if (known.downloadState == DownloadState.RUNNING && !tracker.isRunning(id)) dao.setDownloadState(id, DownloadState.QUEUED)
                }?.takeIf { dao.claimDownload(id) == 1 }
            } ?: continue
            tracker.started(id)
            onStart(episode)
            try {
                val part = store.partFile(episode.feedId, id)
                withContext(io) {
                    downloader.download(episode.audioUrl, part) { bytes, total ->
                        if (tracker.isCancelled(id)) throw DownloadCancelled()
                        tracker.report(id, bytes, total)
                        onProgress(episode, DownloadProgress(bytes, total))
                    }
                    val stored = store.commit(episode.feedId, part, DownloadFiles.fileName(episode), DownloadFiles.mimeOf(episode))
                    dao.setDownload(id, DownloadState.DONE, stored.location, stored.bytes)
                }
                done++
                completed += id
            } catch (e: DownloadCancelled) {
                withContext(NonCancellable + io) {
                    store.partFile(episode.feedId, id).let { part ->
                        part.delete()
                        java.io.File(part.path + ".etag").delete()
                    }
                    dao.setDownload(id, DownloadState.REMOVED, null, 0)
                }
            } catch (e: CancellationException) {
                // the system stopped the work: back in the queue, to go on from the partial file
                withContext(NonCancellable + io) { dao.setDownloadState(id, DownloadState.QUEUED) }
                throw e
            } catch (e: DownloadFailure) {
                val again = when (e) {
                    is DownloadFailure.Network, is DownloadFailure.Incomplete -> true
                    is DownloadFailure.Server -> e.code >= HTTP_SERVER_ERROR || e.code == HTTP_TOO_MANY || e.code == HTTP_TIMEOUT
                    is DownloadFailure.DiskFull, is DownloadFailure.BadAddress -> false
                }
                if (again && !final) {
                    withContext(io) { dao.setDownloadState(id, DownloadState.QUEUED) }
                    retry = true
                } else {
                    withContext(io) { fail(episode) }
                    failed++
                }
                if (e is DownloadFailure.DiskFull) {
                    // the rest wait for room: they go back to the queue they were taken from, as failed ones
                    return RunOutcome(done, failed, retry, diskFull = true, completed = completed)
                }
            } catch (e: IOException) {
                // the file could not be put in its place (the folder is gone, the permission is revoked)
                withContext(io) { fail(episode) }
                failed++
            } finally {
                tracker.finished(id)
            }
        }
        return RunOutcome(done, failed, retry, diskFull = false, completed = completed)
    }

    private fun fail(episode: PodcastEpisode) {
        store.partFile(episode.feedId, episode.id).let { part ->
            part.delete()
            java.io.File(part.path + ".etag").delete()
        }
        dao.setDownload(episode.id, DownloadState.FAILED, null, 0)
    }

    private companion object {
        const val HTTP_SERVER_ERROR = 500
        const val HTTP_TOO_MANY = 429
        const val HTTP_TIMEOUT = 408
    }
}

/** S-1103: starts the work that downloads: WorkManager in the app. */
fun interface DownloadScheduler {
    /** Runs the queue for [ids]; [auto] downloads wait for an unmetered network and a battery that is not low. */
    fun start(ids: List<Long>, auto: Boolean)
}

/**
 * S-1103: the manual and automatic ways in and out of the download queue: queueing episodes, cancelling a download,
 * deleting a downloaded file. Everything the person asked for is in Room; the work reads the queue from there.
 */
class DownloadManager(
    private val dao: PodcastDao,
    private val store: DownloadStore,
    private val tracker: DownloadTracker,
    private val scheduler: DownloadScheduler,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Queues the episodes of [ids] that are not downloaded (and those marked downloaded whose file is gone) and starts the
     * work; the ids queued. [auto]: the work waits for an unmetered network and a battery that is not low.
     */
    suspend fun queue(ids: List<Long>, auto: Boolean = false): List<Long> {
        val queued = withContext(io) {
            ids.mapNotNull { id ->
                val episode = dao.episode(id) ?: return@mapNotNull null
                val gone = episode.downloadState == DownloadState.DONE && episode.downloadPath?.let(store::exists) != true
                if (episode.downloadState in WANTED || gone) {
                    dao.setDownload(id, DownloadState.QUEUED, null, 0)
                    id
                } else if (!auto && episode.downloadState == DownloadState.QUEUED) {
                    // waiting for the automatic chain's Wi-Fi: the person asked, so it goes on any network
                    id
                } else {
                    null
                }
            }
        }
        if (queued.isNotEmpty()) scheduler.start(queued, auto)
        return queued
    }

    /** Stops a queued or running download of [episodeId]; it is not downloaded again by itself. */
    suspend fun cancel(episodeId: Long) {
        withContext(io) {
            val episode = dao.episode(episodeId) ?: return@withContext
            when (episode.downloadState) {
                DownloadState.QUEUED -> {
                    dao.setDownload(episodeId, DownloadState.REMOVED, null, 0)
                    store.partFile(episode.feedId, episodeId).delete()
                }
                DownloadState.RUNNING -> tracker.cancel(episodeId)
            }
        }
    }

    /** Deletes the downloaded file of [episodeId]; the episode stays in the list and is not downloaded again by itself. */
    suspend fun delete(episodeId: Long): Boolean = withContext(io) {
        val episode = dao.episode(episodeId) ?: return@withContext false
        val path = episode.downloadPath
        if (path != null && !store.delete(path)) return@withContext false
        dao.setDownload(episodeId, DownloadState.REMOVED, null, 0)
        true
    }

    private companion object {
        val WANTED = setOf(DownloadState.NONE, DownloadState.FAILED, DownloadState.REMOVED)
    }
}

/**
 * S-1103: carries out [StoragePolicy] on the downloads in Room: the files that fall under the rules are deleted and
 * their episodes marked removed. [protected] returns what must stay (an episode playing now).
 */
class StorageCleaner(
    private val dao: PodcastDao,
    private val store: DownloadStore,
    private val settings: () -> PodcastSettings,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** The plan for the downloads as they are, without doing it: Settings › Podcasts shows its warning from this. */
    suspend fun plan(protected: Set<Long> = emptySet()): CleanupPlan = withContext(io) {
        val config = settings()
        val feeds = dao.feedList().associate { it.id to it.keepLatest }
        val stored = dao.episodesDownload(DownloadState.DONE).map { StoredEpisode(it.id, it.feedId, it.downloadBytes, it.publishedAt, it.played, it.playedAt) }
        StoragePolicy.plan(stored, feeds, config.storageLimitBytes, config.deletePlayedAfterDays, now(), protected)
    }

    /** Does the plan; the plan it did (what could not be deleted is not in it). */
    suspend fun clean(protected: Set<Long> = emptySet()): CleanupPlan {
        val plan = plan(protected)
        val removed = withContext(io) {
            plan.removals.filter { removal ->
                val episode = dao.episode(removal.episodeId)
                val path = episode?.downloadPath
                val gone = path == null || store.delete(path)
                if (gone) dao.setDownload(removal.episodeId, DownloadState.REMOVED, null, 0)
                gone
            }
        }
        return plan.copy(removals = removed)
    }
}
