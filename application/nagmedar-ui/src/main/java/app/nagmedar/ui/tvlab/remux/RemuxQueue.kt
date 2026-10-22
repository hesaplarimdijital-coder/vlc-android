package app.nagmedar.ui.tvlab.remux

import android.content.Context
import android.net.Uri
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.RemuxJobDao
import app.nagmedar.data.RemuxJobEntry
import app.nagmedar.data.RemuxJobError
import app.nagmedar.data.RemuxJobState
import app.nagmedar.lab.NativeLab
import app.nagmedar.lab.RemuxPlan
import app.nagmedar.ui.jobs.OutputState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The remux queue (S-1206): jobs kept in Nağmedar's database, run one at a time by
 * [RemuxWorker] in the foreground. Queued from the TV Lab's report ([enqueue]); the batch of
 * S-1210 and the planner of S-1205 queue the same way. Watched by the screen ([observe]).
 */
class RemuxQueue(
    context: Context,
    private val dao: RemuxJobDao = NagmedarDatabase.get(context).remuxJobs(),
    private val engine: RemuxEngine = NativeRemuxEngine(context.applicationContext),
) {
    private val context = context.applicationContext

    /** Every job, newest first; or those of one film. */
    fun observe(inputUri: String? = null): Flow<List<RemuxJobEntry>> = if (inputUri == null) dao.observe() else dao.observeInput(inputUri)

    /**
     * Queues a job and makes sure the worker runs; the job's id, or [REFUSED_SAME_FILE] when
     * [output] is [input] itself, [REFUSED_NOT_EMPTY] when the output already holds something
     * (both the picker's "replace"), [REFUSED_NOT_FILE] when it is not a regular file, or
     * [REFUSED_IN_USE] when another job writes into it or keeps it: a job writes only into an
     * empty, new file of its own, and the source is never written. The runner and the engine
     * check the same again before writing. The job goes last in the queue's order (S-1210);
     * [start] false leaves the worker to the caller (a batch asks once, at its end).
     */
    suspend fun enqueue(input: Uri, inputName: String, inputSize: Long, output: Uri, outputName: String, plan: RemuxPlan, start: Boolean = true): Long =
        withContext(Dispatchers.IO) {
            if (input == output) return@withContext REFUSED_SAME_FILE
            if (dao.othersUsing(output.toString(), 0) > 0) return@withContext REFUSED_IN_USE
            when (engine.outputState(output)) {
                OutputState.Holds -> return@withContext REFUSED_NOT_EMPTY
                OutputState.NotAFile -> return@withContext REFUSED_NOT_FILE
                // one that does not open now is refused by the runner, untouched
                OutputState.Empty, OutputState.Unreadable -> Unit
            }
            val id = dao.insert(
                RemuxJobEntry(
                    inputUri = input.toString(),
                    inputName = inputName,
                    inputSize = inputSize,
                    outputUri = output.toString(),
                    outputName = outputName,
                    plan = plan.toJson(),
                    createdAt = System.currentTimeMillis(),
                    position = dao.nextPosition(),
                ),
            )
            if (start) RemuxWorker.ensure(context)
            id
        }

    /** S-1210: the worker asked for what was queued (after a batch). */
    fun start() = RemuxWorker.ensure(context)

    /** S-1210: the waiting jobs in the order of [ids], first to run first; the others keep their places after them. */
    suspend fun reorder(ids: List<Long>) = withContext(Dispatchers.IO) {
        val base = ids.mapNotNull { dao.get(it)?.position }.minOrNull() ?: return@withContext
        ids.forEachIndexed { i, id -> dao.savePosition(id, base + i) }
    }

    /**
     * S-1210: the whole queue paused ([on]) or resumed: paused, the running job waits where it
     * is and no other starts; resumed, it goes on and the worker is asked again.
     */
    fun hold(on: Boolean) {
        RemuxQueueControl.get(context).hold(on)
        if (!on) RemuxWorker.ensure(context)
    }

    /**
     * Stops a job: a waiting one is marked cancelled at once and, when the output is its own
     * (what an interrupted attempt left) and still only its own, that output removed (or later,
     * when it cannot be now); a running one is asked to stop and marks itself when the engine
     * returns, its partial output removed.
     */
    suspend fun cancel(id: Long) = withContext(Dispatchers.IO) { cancelNow(id) }

    /** [cancel] on the caller's thread: the notification's receiver. */
    fun cancelNow(id: Long) {
        val job = dao.get(id) ?: return
        when (job.state) {
            RemuxJobState.QUEUED -> {
                // only a job still waiting: the worker may have taken it up since
                if (dao.cancelQueued(id, RemuxJobError.CANCELLED, System.currentTimeMillis()) == 1) {
                    removeOwnedOutput(engine, dao, job.copy(state = RemuxJobState.CANCELLED))
                } else {
                    cancelRunning(id)
                }
            }
            RemuxJobState.RUNNING, RemuxJobState.PAUSED, RemuxJobState.VERIFYING -> cancelRunning(id)
            else -> Unit
        }
    }

    private fun cancelRunning(id: Long) {
        dao.requestCancel(id)
        if (NativeLab.available) NativeLab.remuxCancel()
    }

    /**
     * Forgets a finished job; a done job's output stays. The unfinished output of a failed or
     * cancelled one that is still there is removed first: the row stays while it cannot be.
     */
    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        val job = dao.get(id) ?: return@withContext
        if (job.state !in RemuxJobState.FINAL) return@withContext
        if (job.state != RemuxJobState.DONE && !removeOwnedOutput(engine, dao, job)) return@withContext
        dao.delete(id)
    }

    /**
     * When the app shows: a worker for whatever waits (a job the last process left, one the
     * system let no foreground job start for while the app was in the background, or an
     * unfinished output still to remove).
     */
    suspend fun resume() = withContext(Dispatchers.IO) {
        // S-1210: a night run whose alarm the system did not deliver (the device off at the time) starts now
        RemuxNight.reschedule(context)
        // a worker already running or waiting is kept: no stack of them from every start of the app
        if (dao.next() != null || dao.interrupted().isNotEmpty() || dao.leftovers().isNotEmpty()) RemuxWorker.ensure(context, append = false)
    }

    companion object {
        /** [enqueue]'s answers to an output that is the source, holds something, is no regular file, or is another job's. */
        const val REFUSED_SAME_FILE = -1L
        const val REFUSED_NOT_EMPTY = -2L
        const val REFUSED_NOT_FILE = -3L
        const val REFUSED_IN_USE = -4L

        @Volatile
        private var instance: RemuxQueue? = null

        fun get(context: Context): RemuxQueue = instance ?: synchronized(this) {
            instance ?: RemuxQueue(context.applicationContext).also { instance = it }
        }
    }
}
