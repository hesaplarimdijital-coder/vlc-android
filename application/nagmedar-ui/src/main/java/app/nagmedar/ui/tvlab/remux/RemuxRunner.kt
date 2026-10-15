package app.nagmedar.ui.tvlab.remux

import android.net.Uri
import app.nagmedar.data.RemuxJobDao
import app.nagmedar.data.RemuxJobEntry
import app.nagmedar.data.RemuxJobError
import app.nagmedar.data.RemuxJobKind
import app.nagmedar.data.RemuxJobState
import app.nagmedar.lab.DolbyVisionMode
import app.nagmedar.lab.RemuxPlan
import app.nagmedar.lab.RemuxRefusal
import app.nagmedar.ui.convert.ConvertRunner

/** What a line of [RemuxJobEntry.events] records: "<millis>\t<kind>\t<detail>". */
object RemuxEvent {
    const val STARTED = "started"
    const val RESTARTED = "restarted"
    const val PAUSED = "paused"
    const val RESUMED = "resumed"
    const val SLOWED = "slowed"
    const val STOPPED = "stopped"
    const val INTERRUPTED = "interrupted"

    /** Why a job was interrupted (the detail of [INTERRUPTED]): the worker stopped, its foreground time ran out, the process died. */
    const val WORKER_STOPPED = "worker-stopped"
    const val FOREGROUND_TIMEOUT = "foreground-timeout"
    const val PROCESS_DIED = "process-died"

    data class Line(val at: Long, val kind: String, val detail: String)

    fun parse(events: String): List<Line> = events.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        val parts = line.split('\t', limit = 3)
        val at = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        Line(at, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" })
    }.toList()

    fun line(at: Long, kind: String, detail: String = ""): String = "$at\t$kind\t$detail\n"
}

/**
 * The queue's loop (S-1206, S-1209): one job at a time, from the oldest waiting. A job runs
 * through [RemuxJobState]: running (paused while the battery or the heat say so), verifying,
 * then done with the verified hash, or failed with why, or cancelled. A job writes only into an
 * output it found empty, and only such an output ([RemuxJobEntry.ownsOutput]) is ever removed or
 * emptied: the source, or a file that held something, is refused and left alone. Nothing of a
 * job that is not done stays on the disk: a failed or cancelled job's output is removed, an
 * interrupted one's emptied until it starts over. The device is asked at every step of the engine
 * ([RemuxPolicy]); a job whose worker is stopped (the interruption says why) goes back to the
 * queue, and a job left running by a process that died is started again from the beginning
 * ([recoverInterrupted]): a Matroska file written halfway cannot be continued. While a job runs
 * its row is written column by column, so a cancel asked meanwhile is never overwritten.
 * Blocking: runs on the worker's thread.
 *
 * S-1303: the queue is the app's media jobs'; a conversion ([RemuxJobKind.CONVERT]) is run by
 * [convert] in its turn, under the same rules.
 */
class RemuxRunner(
    private val engine: RemuxEngine,
    private val dao: RemuxJobDao,
    private val condition: () -> DeviceCondition,
    private val now: () -> Long = System::currentTimeMillis,
    private val convert: ConvertRunner? = null,
    private val onUpdate: (RemuxJobEntry) -> Unit = {},
) {
    /** The jobs the last process left running, paused or verifying: their outputs emptied, queued again, to start over. */
    fun recoverInterrupted() {
        dao.interrupted().forEach { job ->
            if (job.ownsOutput) engine.emptyOutput(Uri.parse(job.outputUri))
            if (job.kind == RemuxJobKind.CONVERT) convert?.recover(job)
            dao.update(
                job.copy(
                    state = RemuxJobState.QUEUED,
                    progress = 0f,
                    mediaSeconds = 0.0,
                    bytesOut = 0,
                    error = null,
                    events = job.events + RemuxEvent.line(now(), RemuxEvent.INTERRUPTED, RemuxEvent.PROCESS_DIED),
                ),
            )
        }
    }

    /**
     * Every waiting job in turn; returns when none waits or [interruption] names a reason (null
     * while the worker may go on), which the interrupted job records.
     */
    fun drain(interruption: () -> String? = { null }) {
        while (interruption() == null) {
            val job = dao.next() ?: return
            run(job, interruption)
        }
    }

    /** The system let no foreground job start: [job] waits, marked so, for the app to show. */
    fun waitForApp(job: RemuxJobEntry) {
        if (job.state != RemuxJobState.QUEUED) return
        dao.saveState(job.id, RemuxJobState.QUEUED, RemuxJobError.WAITING_APP, job.events)
        onUpdate(job.copy(error = RemuxJobError.WAITING_APP))
    }

    /** Runs [job] to its end; the row as it was left. */
    fun run(job: RemuxJobEntry, interruption: () -> String? = { null }): RemuxJobEntry {
        if (job.inputUri == job.outputUri) return refuse(job, RemuxJobError.SAME_FILE)
        val output = Uri.parse(job.outputUri)
        // only into an empty output: a document the picker made, never a film it "replaced" (a second attempt finds it emptied)
        when (engine.outputEmpty(output)) {
            false -> return refuse(job, RemuxJobError.OUTPUT_NOT_EMPTY)
            null -> return refuse(job, RemuxJobError.OPEN_OUTPUT)
            true -> Unit
        }
        // from here the empty document is the job's: removed with it when it fails
        dao.saveOwnsOutput(job.id, true)
        val owned = job.copy(ownsOutput = true)
        if (job.kind == RemuxJobKind.CONVERT) return convert?.run(owned, interruption) ?: finish(owned, RemuxJobState.FAILED, RemuxJobError.NO_TOOLS)
        if (!engine.available) return finish(owned, RemuxJobState.FAILED, RemuxJobError.NO_TOOLS)
        val plan = RemuxPlan.parse(job.plan) ?: return finish(owned, RemuxJobState.FAILED, RemuxJobError.ENGINE + "bad plan")
        if (!plan.copiesEverything) return finish(owned, RemuxJobState.FAILED, RemuxJobError.ENGINE + "the engine copies streams only")
        val startedAt = now()
        var current = owned.copy(
            state = RemuxJobState.RUNNING,
            progress = 0f,
            attempts = job.attempts + 1,
            startedAt = startedAt,
            finishedAt = null,
            error = null,
            events = job.events + RemuxEvent.line(startedAt, if (job.attempts == 0) RemuxEvent.STARTED else RemuxEvent.RESTARTED, (job.attempts + 1).toString()),
        )
        dao.saveStart(current.id, current.state, current.attempts, startedAt, current.events)
        onUpdate(current)
        val steering = Steering(current, RemuxJobState.RUNNING, interruption)
        val result = try {
            engine.remux(Uri.parse(job.inputUri), Uri.parse(job.outputUri), plan, steering::step)
        } catch (e: RemuxOpenException) {
            return finish(steering.latest(), RemuxJobState.FAILED, if (e.output) RemuxJobError.OPEN_OUTPUT else RemuxJobError.OPEN_INPUT)
        }
        current = steering.latest()
        val interruptedWhile = steering.interrupted
        when {
            result == null -> return finish(current, RemuxJobState.FAILED, RemuxJobError.ENGINE + "no answer")
            // the engine saw the same file behind other names, or something in the output: nothing was written
            result.refused == RemuxRefusal.SAME_FILE -> return refuse(current, RemuxJobError.SAME_FILE)
            result.refused.isNotEmpty() -> return refuse(current, RemuxJobError.OUTPUT_NOT_EMPTY)
            steering.stopReason != null -> return finish(current, RemuxJobState.FAILED, steering.stopReason)
            steering.cancelled -> return finish(current, RemuxJobState.CANCELLED, RemuxJobError.CANCELLED)
            interruptedWhile != null -> return requeue(current, interruptedWhile)
            result.cancelled -> return finish(current, RemuxJobState.CANCELLED, RemuxJobError.CANCELLED)
            result.error.isNotEmpty() -> return finish(current.copy(result = result.json), RemuxJobState.FAILED, RemuxJobError.ENGINE + result.error)
        }
        current = current.copy(
            state = RemuxJobState.VERIFYING,
            progress = 0f,
            mediaSeconds = result.mediaSeconds,
            totalSeconds = result.sourceSeconds,
            bytesOut = result.bytesOut,
            result = result.json,
        )
        dao.saveResult(current.id, current.state, current.mediaSeconds, current.totalSeconds, current.bytesOut, result.json)
        onUpdate(current)
        val verifying = Steering(current, RemuxJobState.VERIFYING, interruption)
        val dvProfile = if (plan.dolbyVision == DolbyVisionMode.ConvertToProfile81) 8 else 0
        val verification = engine.verify(output, result, dvProfile, verifying::step)
        current = verifying.latest()
        val interruptedVerifying = verifying.interrupted
        return when {
            verification == null -> finish(current, RemuxJobState.FAILED, RemuxJobError.OPEN_OUTPUT)
            verifying.stopReason != null -> finish(current, RemuxJobState.FAILED, verifying.stopReason)
            verifying.cancelled -> finish(current, RemuxJobState.CANCELLED, RemuxJobError.CANCELLED)
            interruptedVerifying != null -> requeue(current, interruptedVerifying)
            verification.cancelled -> finish(current, RemuxJobState.CANCELLED, RemuxJobError.CANCELLED)
            verification.error.isNotEmpty() -> finish(current.copy(verification = verification.json), RemuxJobState.FAILED, RemuxJobError.ENGINE + verification.error)
            !verification.ok || result.videoPackets <= 0 -> finish(current.copy(verification = verification.json), RemuxJobState.FAILED, RemuxJobError.VERIFY)
            else -> finish(current.copy(verification = verification.json, videoHash = verification.hash, progress = 1f), RemuxJobState.DONE, null)
        }
    }

    /** The job's end: anything but done leaves no output behind, when the output is the job's own. */
    private fun finish(job: RemuxJobEntry, state: String, error: String?): RemuxJobEntry {
        if (state != RemuxJobState.DONE && job.ownsOutput) engine.removeOutput(Uri.parse(job.outputUri))
        val done = job.copy(state = state, error = error, finishedAt = now(), cancelRequested = false)
        dao.update(done)
        onUpdate(done)
        return done
    }

    /** Refused before a byte was written: the output is not the job's, and is left exactly as it was. */
    private fun refuse(job: RemuxJobEntry, error: String): RemuxJobEntry = finish(job.copy(ownsOutput = false), RemuxJobState.FAILED, error)

    /** The worker was stopped under the job: back to the queue, to start over; its output emptied meanwhile. */
    private fun requeue(job: RemuxJobEntry, why: String): RemuxJobEntry {
        if (job.ownsOutput) engine.emptyOutput(Uri.parse(job.outputUri))
        val back = job.copy(
            state = RemuxJobState.QUEUED,
            progress = 0f,
            mediaSeconds = 0.0,
            bytesOut = 0,
            error = null,
            events = job.events + RemuxEvent.line(now(), RemuxEvent.INTERRUPTED, why),
        )
        dao.saveState(back.id, back.state, back.error, back.events)
        dao.saveProgress(back.id, 0f, 0.0, back.totalSeconds, 0)
        onUpdate(back)
        return back
    }

    /** The engine's steps of one phase: the device watched, the row kept up to date, a cancel seen. */
    private inner class Steering(private var job: RemuxJobEntry, private val activeState: String, private val interruption: () -> String?) {
        var cancelled = false
            private set
        var interrupted: String? = null
            private set
        var stopReason: String? = null
            private set
        private var paused = false
        private var slowed = false
        /** The engine asks again after a wait: a slowed step waits once, then goes on. */
        private var slowWaited = false
        private var lastSave = 0L

        fun latest(): RemuxJobEntry = job

        fun step(progress: RemuxProgress): Int {
            val fresh = dao.get(job.id)
            if (fresh == null || fresh.cancelRequested) {
                cancelled = true
                return -1
            }
            val why = interruption()
            if (why != null) {
                interrupted = why
                return -1
            }
            val fraction = if (progress.totalSeconds > 0) (progress.doneSeconds / progress.totalSeconds).toFloat().coerceIn(0f, 1f) else 0f
            job = job.copy(progress = fraction, mediaSeconds = progress.doneSeconds, totalSeconds = progress.totalSeconds, bytesOut = progress.bytesOut)
            return when (val steer = RemuxPolicy.decide(condition(), paused)) {
                Steer.Go -> {
                    slowWaited = false
                    if (paused) {
                        paused = false
                        transition(activeState, null, RemuxEvent.RESUMED)
                    } else {
                        saveThrottled()
                    }
                    0
                }
                is Steer.Slow -> {
                    if (!slowed) {
                        slowed = true
                        transition(activeState, null, RemuxEvent.SLOWED, RemuxJobError.THERMAL)
                    } else {
                        saveThrottled()
                    }
                    slowWaited = !slowWaited
                    if (slowWaited) steer.waitMs else 0
                }
                is Steer.Pause -> {
                    if (!paused) {
                        paused = true
                        transition(RemuxJobState.PAUSED, steer.reason, RemuxEvent.PAUSED, steer.reason)
                    }
                    steer.waitMs
                }
                is Steer.Stop -> {
                    stopReason = steer.reason
                    job = job.copy(events = job.events + RemuxEvent.line(now(), RemuxEvent.STOPPED, steer.reason))
                    -1
                }
            }
        }

        private fun transition(state: String, error: String?, event: String, detail: String = "") {
            job = job.copy(state = state, error = error, events = job.events + RemuxEvent.line(now(), event, detail))
            dao.saveState(job.id, state, error, job.events)
            saveProgress()
        }

        private fun saveProgress() {
            lastSave = now()
            dao.saveProgress(job.id, job.progress, job.mediaSeconds, job.totalSeconds, job.bytesOut)
            onUpdate(job)
        }

        private fun saveThrottled() {
            if (now() - lastSave >= SAVE_EVERY_MS) saveProgress()
        }
    }

    private companion object {
        /** The row (and the notification) follow the engine this often. */
        const val SAVE_EVERY_MS = 1_000L
    }
}

/** A file of the job could not be opened: [output] says which. */
class RemuxOpenException(val output: Boolean) : RuntimeException(if (output) "output" else "input")
