package app.nagmedar.ui.tvlab.remux

import android.net.Uri
import app.nagmedar.data.RemuxJobDao
import app.nagmedar.data.RemuxJobEntry
import app.nagmedar.data.RemuxJobError
import app.nagmedar.data.RemuxJobState
import app.nagmedar.lab.DolbyVisionMode
import app.nagmedar.lab.RemuxPlan
import app.nagmedar.lab.RemuxRefusal
import app.nagmedar.ui.convert.ConvertPolicy
import app.nagmedar.ui.convert.ConvertReason
import app.nagmedar.ui.convert.ConvertSteer
import app.nagmedar.ui.convert.DeviceState
import app.nagmedar.ui.jobs.OutputState

/** What a line of [RemuxJobEntry.events] records: "<millis>\t<kind>\t<detail>". */
object RemuxEvent {
    const val STARTED = "started"
    const val RESTARTED = "restarted"
    const val PAUSED = "paused"
    const val RESUMED = "resumed"
    const val SLOWED = "slowed"
    const val STOPPED = "stopped"
    const val INTERRUPTED = "interrupted"

    /** S-1210: the whole queue paused by the user; [RESUMED] when it goes on. */
    const val HELD = "held"

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
 * through [RemuxJobState]: running (paused while the battery or the heat say so, by S-1010's
 * [ConvertPolicy]), verifying, then done with the verified hash, or failed with why, or
 * cancelled. A job is taken up only while it still waits ([RemuxJobDao.claim]): a cancel that
 * came first wins. It writes only into an output it found empty and no other job uses, and only
 * such an output ([RemuxJobEntry.ownsOutput]: the empty document it was queued with, or one it
 * found empty before its first byte) is ever removed or emptied, and one no attempt wrote into
 * only while it is still empty: the source, or a file that held something, is refused and left
 * alone. Nothing of a job that is not done stays on the disk: a failed or cancelled job's output
 * is removed (one that cannot be removed yet stays the job's and is removed later, [cleanUp]),
 * an interrupted one's emptied until it starts over. A job whose worker is stopped (the
 * interruption says why) goes back to the queue, and a job left running by a process that died
 * is started again from the beginning ([recoverInterrupted]): a Matroska file written halfway
 * cannot be continued.
 * While a job runs its row is written column by column, so a cancel asked meanwhile is never
 * overwritten. The queue as a whole (S-1210) is [held] by the user (the running job waits where
 * it is, paused, no other starts) or [waits] (its night run: no job starts before the time); a
 * night run that starts only once the device charges waits for the [charger] before its first
 * job. Blocking: runs on the worker's thread, one runner at a time.
 */
class RemuxRunner(
    private val engine: RemuxEngine,
    private val dao: RemuxJobDao,
    private val device: () -> DeviceState,
    private val now: () -> Long = System::currentTimeMillis,
    private val held: () -> Boolean = { false },
    private val waits: () -> Boolean = { false },
    private val charger: NightCharger = NightCharger.None,
    private val onUpdate: (RemuxJobEntry) -> Unit = {},
) {
    /** The jobs the last process left running, paused or verifying: their outputs emptied, queued again, to start over. */
    fun recoverInterrupted() {
        dao.interrupted().forEach { job ->
            // when emptying fails the output stays the job's: its next attempt empties it first
            if (job.ownsOutput) engine.emptyOutput(Uri.parse(job.outputUri))
            dao.requeueInterrupted(job.id, job.events + RemuxEvent.line(now(), RemuxEvent.INTERRUPTED, RemuxEvent.PROCESS_DIED))
        }
    }

    /** The outputs of failed or cancelled jobs that could not be removed when they ended: removed now, when still only theirs. */
    fun cleanUp() {
        dao.leftovers().forEach { removeOwnedOutput(engine, dao, it) }
    }

    /** Whether the queue starts no job now: paused by the user, or waiting for its night run. */
    fun stopped(): Boolean = held() || waits()

    /**
     * Every waiting job in turn; returns when none waits, the queue is [stopped], or
     * [interruption] names a reason (null while the worker may go on), which the interrupted
     * job records. A night run waiting for the charger is waited for first ([awaitCharger]).
     */
    fun drain(interruption: () -> String? = { null }) {
        while (interruption() == null) {
            if (stopped()) return
            val job = dao.next() ?: return
            if (charger.waiting()) {
                if (!awaitCharger(job, interruption)) return
                // the queue read again: the wait may have been long
                continue
            }
            run(job, interruption)
        }
    }

    /**
     * S-1210: the night run that starts only once the device charges, its time come, waits here
     * for the charger before its first job, the worker in the foreground since the alarm (one
     * started later from the background would be refused it). [job], the first to run, is shown
     * meanwhile. The user's pause, another night run, Start now (the night run taken off) or the
     * worker's stop end the wait: whether the queue may go on.
     */
    private fun awaitCharger(job: RemuxJobEntry, interruption: () -> String?): Boolean {
        var shown = false
        while (charger.waiting()) {
            if (device().charging) {
                charger.begin()
                return true
            }
            if (!shown) {
                shown = true
                onUpdate(job)
            }
            charger.await(CHARGER_CHECK_MS)
            if (interruption() != null || stopped()) return false
        }
        return true
    }

    /** The system let no foreground job start: [job] waits, marked so, for the app to show (when it still waits). */
    fun waitForApp(job: RemuxJobEntry) {
        if (dao.markWaiting(job.id, RemuxJobError.WAITING_APP) == 1) onUpdate(job.copy(error = RemuxJobError.WAITING_APP))
    }

    /** Runs [job] to its end; the row as it was left. A job no longer waiting (cancelled meanwhile) is left as it is. */
    fun run(job: RemuxJobEntry, interruption: () -> String? = { null }): RemuxJobEntry {
        val startedAt = now()
        val events = job.events + RemuxEvent.line(startedAt, if (job.attempts == 0) RemuxEvent.STARTED else RemuxEvent.RESTARTED, (job.attempts + 1).toString())
        if (dao.claim(job.id, job.attempts + 1, startedAt, events) == 0) return dao.get(job.id) ?: job
        var current = job.copy(
            state = RemuxJobState.RUNNING,
            progress = 0f,
            attempts = job.attempts + 1,
            startedAt = startedAt,
            finishedAt = null,
            error = null,
            events = events,
        )
        onUpdate(current)
        // whether an earlier attempt wrote into the output: until one did, it is the empty document the job was queued with
        val written = job.attempts > 0
        // a cancel asked while an earlier attempt ran, seen only after the worker stopped under it
        if (dao.get(job.id)?.cancelRequested != false) return finish(current, RemuxJobState.CANCELLED, RemuxJobError.CANCELLED, written)
        if (job.inputUri == job.outputUri) return refuse(current, RemuxJobError.SAME_FILE)
        if (dao.othersUsing(job.outputUri, job.id) > 0) return refuse(current, RemuxJobError.OUTPUT_IN_USE)
        val output = Uri.parse(job.outputUri)
        // only into an empty output: a document the picker made, never a film it "replaced"
        var found = engine.outputState(output)
        if (found == OutputState.Holds && job.ownsOutput && written) {
            // what an earlier attempt of this job left when emptying it failed then: its own, emptied now
            engine.emptyOutput(output)
            found = engine.outputState(output)
            if (found != OutputState.Empty) return finish(current, RemuxJobState.FAILED, RemuxJobError.OPEN_OUTPUT)
        }
        when (found) {
            OutputState.Empty -> Unit
            // filled by something else since it was queued, or never the job's: left as it is
            OutputState.Holds -> return refuse(current, RemuxJobError.OUTPUT_NOT_EMPTY)
            OutputState.NotAFile -> return refuse(current, RemuxJobError.OUTPUT_NOT_FILE)
            // one an earlier attempt wrote stays the job's, removed when it can be
            OutputState.Unreadable -> return if (job.ownsOutput && written) finish(current, RemuxJobState.FAILED, RemuxJobError.OPEN_OUTPUT) else refuse(current, RemuxJobError.OPEN_OUTPUT)
        }
        // from here the empty document is the job's, before the first byte: removed with it when it fails
        dao.saveOwnsOutput(job.id, true)
        current = current.copy(ownsOutput = true)
        if (!engine.available) return finish(current, RemuxJobState.FAILED, RemuxJobError.NO_TOOLS, written)
        val plan = RemuxPlan.parse(job.plan) ?: return finish(current, RemuxJobState.FAILED, RemuxJobError.ENGINE + "bad plan", written)
        val steering = Steering(current, RemuxJobState.RUNNING, interruption)
        val result = try {
            engine.remux(Uri.parse(job.inputUri), output, plan, steering::step)
        } catch (e: RemuxOpenException) {
            // the engine wrote nothing
            return finish(steering.latest(), RemuxJobState.FAILED, if (e.output) RemuxJobError.OPEN_OUTPUT else RemuxJobError.OPEN_INPUT, written)
        }
        current = steering.latest()
        val interruptedWhile = steering.interrupted
        when {
            result == null -> return finish(current, RemuxJobState.FAILED, RemuxJobError.ENGINE + "no answer")
            // the engine saw the same file behind other names, or something in the output: nothing was written
            result.refused == RemuxRefusal.SAME_FILE -> return refuse(current, RemuxJobError.SAME_FILE)
            result.refused == RemuxRefusal.OUTPUT_NOT_FILE -> return refuse(current, RemuxJobError.OUTPUT_NOT_FILE)
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

    /**
     * The job's end: anything but done leaves no output behind, when the output is the job's
     * own; one that cannot be removed now stays the job's, for [cleanUp] to remove later. One
     * no attempt has [written] into is the empty document the job was queued with: removed while
     * it is still empty, else (or when it cannot be removed now) left as it is, the job's no
     * longer: at worst an empty file stays, never one that holds something goes.
     */
    private fun finish(job: RemuxJobEntry, state: String, error: String?, written: Boolean = true): RemuxJobEntry {
        val output = Uri.parse(job.outputUri)
        val owns = when {
            state == RemuxJobState.DONE -> job.ownsOutput
            !job.ownsOutput -> false
            written -> !engine.removeOutput(output)
            else -> {
                if (engine.outputState(output) == OutputState.Empty) engine.removeOutput(output)
                false
            }
        }
        val done = job.copy(state = state, error = error, finishedAt = now(), cancelRequested = false, ownsOutput = owns)
        dao.update(done)
        onUpdate(done)
        return done
    }

    /** Refused before a byte was written: the output is not the job's, and is left exactly as it was. */
    private fun refuse(job: RemuxJobEntry, error: String): RemuxJobEntry = finish(job.copy(ownsOutput = false), RemuxJobState.FAILED, error)

    /**
     * The worker was stopped under the job: back to the queue, to start over; its output emptied
     * meanwhile (when that fails it stays the job's, and the next attempt empties it first).
     */
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
        /** Paused with the whole queue by the user (S-1210). */
        private var holding = false
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
            // the user's pause comes first: the job waits where it is, the battery and heat looked at again after it
            if (held()) {
                if (!holding) {
                    holding = true
                    transition(RemuxJobState.PAUSED, RemuxJobError.HELD, RemuxEvent.HELD)
                }
                return HOLD_WAIT_MS
            }
            if (holding) {
                holding = false
                paused = false
                transition(activeState, null, RemuxEvent.RESUMED)
            }
            return when (val steer = ConvertPolicy.decide(device(), paused)) {
                ConvertSteer.Go -> {
                    slowWaited = false
                    if (paused) {
                        paused = false
                        transition(activeState, null, RemuxEvent.RESUMED)
                    } else {
                        saveThrottled()
                    }
                    0
                }
                is ConvertSteer.Slow -> {
                    if (!slowed) {
                        slowed = true
                        transition(activeState, null, RemuxEvent.SLOWED, RemuxJobError.THERMAL)
                    } else {
                        saveThrottled()
                    }
                    slowWaited = !slowWaited
                    if (slowWaited) steer.waitMs.toInt() else 0
                }
                is ConvertSteer.Pause -> {
                    if (!paused) {
                        paused = true
                        val reason = reasonOf(steer.reason)
                        transition(RemuxJobState.PAUSED, reason, RemuxEvent.PAUSED, reason)
                    }
                    steer.waitMs.toInt()
                }
                is ConvertSteer.Stop -> {
                    val reason = reasonOf(steer.reason)
                    stopReason = reason
                    job = job.copy(events = job.events + RemuxEvent.line(now(), RemuxEvent.STOPPED, reason))
                    -1
                }
            }
        }

        private fun reasonOf(reason: ConvertReason): String =
            if (reason == ConvertReason.Battery) RemuxJobError.BATTERY else RemuxJobError.THERMAL

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

        /** How often a job the user paused looks whether the queue goes on. */
        const val HOLD_WAIT_MS = 1_000

        /** How often a night run waiting for the charger looks again, when no plug-in wakes it first. */
        const val CHARGER_CHECK_MS = 5_000L
    }
}

/** A file of the job could not be opened: [output] says which. */
class RemuxOpenException(val output: Boolean) : RuntimeException(if (output) "output" else "input")

/**
 * Removes the output a failed or cancelled [job] still owns, when it is only that job's (no
 * other job writes into it or keeps it) and still a file: whether the job owns none now. One
 * shared with another job, or no longer a file, is given up and left alone. A job that never
 * ran owns only the empty document it was queued with: removed while it is still empty; one
 * that holds something now is not the job's and is left alone, one that does not open now is
 * tried again later.
 */
internal fun removeOwnedOutput(engine: RemuxEngine, dao: RemuxJobDao, job: RemuxJobEntry): Boolean {
    if (!job.ownsOutput) return true
    val output = Uri.parse(job.outputUri)
    val found = engine.outputState(output)
    val ran = job.attempts > 0
    if (!ran && found == OutputState.Unreadable) return false
    val ours = dao.othersUsing(job.outputUri, job.id) == 0 && found != OutputState.NotAFile && (ran || found == OutputState.Empty)
    if (ours && !engine.removeOutput(output)) return false
    dao.saveOwnsOutput(job.id, false)
    return true
}
