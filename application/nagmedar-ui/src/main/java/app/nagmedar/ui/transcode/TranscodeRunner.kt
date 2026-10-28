package app.nagmedar.ui.transcode

import android.content.Context
import android.net.Uri
import app.nagmedar.data.TranscodeJobDao
import app.nagmedar.data.TranscodeJobEntry
import app.nagmedar.data.TranscodeJobError
import app.nagmedar.data.TranscodeJobState
import app.nagmedar.ui.convert.ConvertPolicy
import app.nagmedar.ui.convert.ConvertReason
import app.nagmedar.ui.convert.ConvertSteer
import app.nagmedar.ui.convert.DeviceState
import app.nagmedar.ui.jobs.JobOutputs
import app.nagmedar.ui.jobs.OutputState
import java.io.File
import java.io.IOException

/**
 * S-1303: where a conversion's file is made: in the app's storage first (libVLC writes a path),
 * then copied into the document the system's picker named, and removed.
 */
interface TranscodeFiles {
    /** The file job [id] is encoded into, with [extension]. */
    fun temp(id: Long, extension: String): File

    /** Copies [from] into [output]; [step] gets the bytes copied and says whether to go on. False when it could not. */
    fun save(from: File, output: Uri, step: (Long) -> Boolean): Boolean

    /** Deletes the document, or empties it when it cannot be deleted. */
    fun removeOutput(output: Uri)

    /** Empties the document, for the job's next attempt (which writes only into an empty one). */
    fun emptyOutput(output: Uri)

    /** Whether the document holds nothing yet (the picker just made it); null when it cannot be read. */
    fun outputEmpty(output: Uri): Boolean?
}

/** S-1303: [TranscodeFiles] on the device: the app's external files (its own, no permission), the storage framework's documents. */
class DeviceTranscodeFiles(context: Context) : TranscodeFiles {
    private val context = context.applicationContext

    override fun temp(id: Long, extension: String): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "transcode").apply { mkdirs() }
        return File(dir, "job-$id.$extension")
    }

    override fun save(from: File, output: Uri, step: (Long) -> Boolean): Boolean = try {
        context.contentResolver.openOutputStream(output, "wt")?.use { out ->
            from.inputStream().use { input ->
                val buffer = ByteArray(1 shl 20)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    copied += read
                    if (!step(copied)) return false
                }
            }
            true
        } ?: false
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    }

    /** The documents' rule ([JobOutputs]). */
    private val outputs = JobOutputs(context)

    override fun removeOutput(output: Uri) {
        outputs.remove(output)
    }

    override fun emptyOutput(output: Uri) {
        outputs.empty(output)
    }

    override fun outputEmpty(output: Uri): Boolean? = when (outputs.state(output)) {
        OutputState.Empty -> true
        OutputState.Unreadable -> null
        OutputState.Holds, OutputState.NotAFile -> false
    }
}

/**
 * S-1303: the video conversions' queue, one job at a time from the oldest waiting, each run by
 * libVLC's stream output: the battery and the heat watched at each step (S-1010's
 * [ConvertPolicy]: paused on a low battery or when hot, stopped when worse), a cancel seen, the
 * worker's stop sending the job back to the queue to start over. The encoding is nine tenths of
 * the progress, the copy into the document the last tenth. A job writes only into an output it
 * found empty (the new document the picker made), and nothing of a job that is not done stays:
 * the file in the app's storage is always deleted, the document removed. Blocking: the worker's
 * thread.
 */
class TranscodeRunner(
    private val engine: SoutEngine,
    private val files: TranscodeFiles,
    private val dao: TranscodeJobDao,
    private val device: () -> DeviceState,
    private val now: () -> Long = System::currentTimeMillis,
    private val onUpdate: (TranscodeJobEntry) -> Unit = {},
) {
    /** The jobs a process that died left running or paused: their files deleted, queued again to start over. */
    fun recoverInterrupted() {
        dao.interrupted().forEach { job ->
            TranscodeProfile.parse(job.profile)?.let { files.temp(job.id, it.container.extension).delete() }
            if (job.ownsOutput) files.emptyOutput(Uri.parse(job.outputUri))
            dao.update(job.copy(state = TranscodeJobState.QUEUED, progress = 0f, mediaMs = 0, bytesOut = 0, error = null, restarted = true))
        }
    }

    /** Every waiting job in turn; returns when none waits or [interruption] names a reason (null while the worker may go on). */
    fun drain(interruption: () -> String?) {
        while (interruption() == null) {
            val job = dao.next() ?: return
            run(job, interruption)
        }
    }

    /** Runs [job] to its end; [interruption] names why the worker stops (null while it may go on). The row as it was left. */
    fun run(job: TranscodeJobEntry, interruption: () -> String?): TranscodeJobEntry {
        if (job.cancelRequested) return finish(job, null, TranscodeJobState.CANCELLED, TranscodeJobError.CANCELLED)
        if (job.inputUri == job.outputUri) return refuse(job, TranscodeJobError.SAME_FILE)
        val output = Uri.parse(job.outputUri)
        // only into an empty output: the document the picker made, never a file it "replaced" (a second attempt finds it emptied)
        when (files.outputEmpty(output)) {
            false -> return refuse(job, TranscodeJobError.OUTPUT_NOT_EMPTY)
            null -> return refuse(job, TranscodeJobError.OPEN_OUTPUT)
            true -> Unit
        }
        // from here the empty document is the job's: removed with it when it fails
        dao.ownOutput(job.id)
        val owned = job.copy(ownsOutput = true)
        val profile = TranscodeProfile.parse(job.profile) ?: return finish(owned, null, TranscodeJobState.FAILED, TranscodeJobError.ENGINE + ENCODER)
        if (TranscodeProfiles.validate(profile).any { it != ProfileProblem.NameMissing && it != ProfileProblem.NameTaken }) {
            return finish(owned, null, TranscodeJobState.FAILED, TranscodeJobError.ENGINE + ENCODER)
        }
        val temp = files.temp(job.id, profile.container.extension)
        temp.delete()
        val started = owned.copy(
            state = TranscodeJobState.RUNNING,
            progress = 0f,
            attempts = job.attempts + 1,
            startedAt = now(),
            finishedAt = null,
            error = null,
        )
        dao.update(started)
        onUpdate(started)
        val steering = Steering(started, temp, interruption)
        val outcome = engine.run(Uri.parse(job.inputUri), SoutChain.convert(profile, temp.path), steering::step)
        var current = steering.latest()
        when {
            steering.cancelled -> return finish(current, temp, TranscodeJobState.CANCELLED, TranscodeJobError.CANCELLED)
            steering.stopReason != null -> return finish(current, temp, TranscodeJobState.FAILED, steering.stopReason)
            steering.interrupted != null -> return requeue(current, temp, steering.interrupted!!)
            outcome is SoutOutcome.Failed -> return finish(current, temp, TranscodeJobState.FAILED, TranscodeJobError.ENGINE + outcome.reason)
            !temp.isFile || temp.length() == 0L -> return finish(current, temp, TranscodeJobState.FAILED, TranscodeJobError.ENGINE + EMPTY)
        }
        // into the document: the last tenth
        val size = temp.length()
        current = current.copy(progress = ENCODE_SHARE, bytesOut = size)
        dao.saveProgress(current.id, current.progress, current.mediaMs, current.totalMs, size)
        onUpdate(current)
        var lastSave = now()
        var cancelled = false
        var interrupted: String? = null
        val saved = files.save(temp, output) { copied ->
            if (dao.get(job.id)?.cancelRequested != false) {
                cancelled = true
                return@save false
            }
            interrupted = interruption()
            if (interrupted != null) return@save false
            if (now() - lastSave >= SAVE_EVERY_MS) {
                lastSave = now()
                current = current.copy(progress = ENCODE_SHARE + (1f - ENCODE_SHARE) * copied / size.coerceAtLeast(1))
                dao.saveProgress(current.id, current.progress, current.mediaMs, current.totalMs, size)
                onUpdate(current)
            }
            true
        }
        return when {
            cancelled -> finish(current, temp, TranscodeJobState.CANCELLED, TranscodeJobError.CANCELLED)
            interrupted != null -> requeue(current, temp, interrupted!!)
            !saved -> finish(current, temp, TranscodeJobState.FAILED, TranscodeJobError.OPEN_OUTPUT)
            else -> finish(current.copy(progress = 1f, bytesOut = size), temp, TranscodeJobState.DONE, null)
        }
    }

    /** An output the job may not write into (the source, a file that holds something): failed, the output left as it was. */
    private fun refuse(job: TranscodeJobEntry, error: String): TranscodeJobEntry = finish(job.copy(ownsOutput = false), null, TranscodeJobState.FAILED, error)

    /** The job's end: its file in the app's storage deleted; anything but done leaves no document of its own either. */
    private fun finish(job: TranscodeJobEntry, temp: File?, state: String, error: String?): TranscodeJobEntry {
        temp?.delete()
        if (state != TranscodeJobState.DONE && job.ownsOutput) files.removeOutput(Uri.parse(job.outputUri))
        val done = job.copy(state = state, error = error, finishedAt = now(), cancelRequested = false)
        dao.update(done)
        onUpdate(done)
        return done
    }

    /** The worker was stopped under the job: back to the queue, to start over; [why] is kept when it is the system's time limit. */
    private fun requeue(job: TranscodeJobEntry, temp: File, why: String): TranscodeJobEntry {
        temp.delete()
        // a copy cut halfway: emptied, so the next attempt finds the document as the picker made it
        if (job.ownsOutput) files.emptyOutput(Uri.parse(job.outputUri))
        val back = job.copy(
            state = TranscodeJobState.QUEUED,
            progress = 0f,
            mediaMs = 0,
            bytesOut = 0,
            error = why.takeIf { it == TranscodeJobError.TIMEOUT },
            restarted = true,
        )
        dao.update(back)
        onUpdate(back)
        return back
    }

    /** libVLC's steps: the device watched, the row kept up to date, a cancel seen. */
    private inner class Steering(private var job: TranscodeJobEntry, private val temp: File, private val interruption: () -> String?) {
        var cancelled = false
            private set
        var interrupted: String? = null
            private set
        var stopReason: String? = null
            private set
        private var paused = false
        private var lastSave = 0L

        fun latest(): TranscodeJobEntry = job

        fun step(progress: SoutProgress): SoutSteer {
            val fresh = dao.get(job.id)
            if (fresh == null || fresh.cancelRequested) {
                cancelled = true
                return SoutSteer.Stop
            }
            interruption()?.let {
                interrupted = it
                return SoutSteer.Stop
            }
            job = job.copy(
                progress = if (progress.position >= 0f) progress.position * ENCODE_SHARE else 0f,
                mediaMs = progress.timeMs.coerceAtLeast(0),
                totalMs = progress.lengthMs.coerceAtLeast(0),
                bytesOut = temp.length(),
            )
            return when (val steer = ConvertPolicy.decide(device(), paused)) {
                // a warm device: libVLC's encoder cannot be slowed, the job goes on
                ConvertSteer.Go, is ConvertSteer.Slow -> {
                    if (paused) {
                        paused = false
                        transition(TranscodeJobState.RUNNING, null)
                    } else {
                        saveThrottled()
                    }
                    SoutSteer.Go
                }
                is ConvertSteer.Pause -> {
                    if (!paused) {
                        paused = true
                        transition(TranscodeJobState.PAUSED, reasonOf(steer.reason))
                    }
                    SoutSteer.Pause
                }
                is ConvertSteer.Stop -> {
                    stopReason = reasonOf(steer.reason)
                    SoutSteer.Stop
                }
            }
        }

        private fun reasonOf(reason: ConvertReason): String =
            if (reason == ConvertReason.Battery) TranscodeJobError.BATTERY else TranscodeJobError.THERMAL

        private fun transition(state: String, error: String?) {
            job = job.copy(state = state, error = error)
            dao.saveState(job.id, state, error)
            saveProgress()
        }

        private fun saveProgress() {
            lastSave = now()
            dao.saveProgress(job.id, job.progress, job.mediaMs, job.totalMs, job.bytesOut)
            onUpdate(job)
        }

        private fun saveThrottled() {
            if (now() - lastSave >= SAVE_EVERY_MS) saveProgress()
        }
    }

    companion object {
        /** The encoding's share of the progress; the copy into the document takes the rest. */
        const val ENCODE_SHARE = 0.9f

        /** Why a job failed, after "engine:": a profile this build cannot encode, or nothing written. */
        const val ENCODER = "encoder"
        const val EMPTY = "empty"

        private const val SAVE_EVERY_MS = 1_000L
    }
}
