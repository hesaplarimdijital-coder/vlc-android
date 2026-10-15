package app.nagmedar.ui.tvlab.remux

import android.content.Context
import android.net.Uri
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.RemuxJobDao
import app.nagmedar.data.RemuxJobEntry
import app.nagmedar.data.RemuxJobError
import app.nagmedar.data.RemuxJobKind
import app.nagmedar.data.RemuxJobState
import app.nagmedar.lab.NativeLab
import app.nagmedar.lab.RemuxPlan
import app.nagmedar.ui.convert.ConvertProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The remux queue (S-1206): jobs kept in Nağmedar's database, run one at a time by
 * [RemuxWorker] in the foreground. Queued from the TV Lab's report ([enqueue]); the batch of
 * S-1210 and the planner of S-1205 queue the same way. Watched by the screen ([observe]).
 * The conversions of S-1303 wait in it too ([enqueueConvert]): it is the app's one job queue.
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
     * [output] is [input] itself, or [REFUSED_NOT_EMPTY] when the output already holds something
     * (both the picker's "replace"): a job writes only into an empty, new file, and the source is
     * never written. The runner and the engine check the same again before writing.
     */
    suspend fun enqueue(input: Uri, inputName: String, inputSize: Long, output: Uri, outputName: String, plan: RemuxPlan): Long =
        withContext(Dispatchers.IO) {
            if (input == output) return@withContext REFUSED_SAME_FILE
            if (engine.outputEmpty(output) == false) return@withContext REFUSED_NOT_EMPTY
            val id = dao.insert(
                RemuxJobEntry(
                    inputUri = input.toString(),
                    inputName = inputName,
                    inputSize = inputSize,
                    outputUri = output.toString(),
                    outputName = outputName,
                    plan = plan.toJson(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            RemuxWorker.ensure(context)
            id
        }

    /**
     * S-1303: queues a conversion of [input] with [profile] into [output], in the same queue as
     * the remuxes; the job's id, or [REFUSED] when [output] is [input] itself.
     */
    suspend fun enqueueConvert(input: Uri, inputName: String, inputSize: Long, output: Uri, outputName: String, profile: ConvertProfile): Long =
        withContext(Dispatchers.IO) {
            if (input == output) return@withContext REFUSED
            val id = dao.insert(
                RemuxJobEntry(
                    inputUri = input.toString(),
                    inputName = inputName,
                    inputSize = inputSize,
                    outputUri = output.toString(),
                    outputName = outputName,
                    plan = profile.toJson(),
                    createdAt = System.currentTimeMillis(),
                    kind = RemuxJobKind.CONVERT,
                ),
            )
            RemuxWorker.ensure(context)
            id
        }

    /**
     * Stops a job: a waiting one is marked cancelled at once and, when the output is its own
     * (what an interrupted attempt left), that output removed; a running one is asked to stop
     * and marks itself when the engine returns, its partial output removed.
     */
    suspend fun cancel(id: Long) = withContext(Dispatchers.IO) { cancelNow(id) }

    /** [cancel] on the caller's thread: the notification's receiver. */
    fun cancelNow(id: Long) {
        val job = dao.get(id) ?: return
        when (job.state) {
            RemuxJobState.QUEUED -> {
                // only a job still waiting: the worker may have taken it up since
                if (dao.cancelQueued(id, RemuxJobError.CANCELLED, System.currentTimeMillis()) == 1) {
                    if (job.ownsOutput) engine.removeOutput(Uri.parse(job.outputUri))
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

    /** Forgets a finished job; its output, if any, stays. */
    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        val job = dao.get(id) ?: return@withContext
        if (job.state in RemuxJobState.FINAL) dao.delete(id)
    }

    /**
     * When the app shows: a worker for whatever waits (a job the last process left, or one the
     * system let no foreground job start for while the app was in the background).
     */
    suspend fun resume() = withContext(Dispatchers.IO) {
        // a worker already running or waiting is kept: no stack of them from every start of the app
        if (dao.next() != null || dao.interrupted().isNotEmpty()) RemuxWorker.ensure(context, append = false)
    }

    companion object {
        /** [enqueue]'s answers to an output that is the source, or one that holds something. */
        const val REFUSED_SAME_FILE = -1L
        const val REFUSED_NOT_EMPTY = -2L

        @Volatile
        private var instance: RemuxQueue? = null

        fun get(context: Context): RemuxQueue = instance ?: synchronized(this) {
            instance ?: RemuxQueue(context.applicationContext).also { instance = it }
        }
    }
}
