package app.nagmedar.ui.convert

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.nagmedar.data.RemuxJobDao
import app.nagmedar.data.RemuxJobEntry
import app.nagmedar.data.RemuxJobError
import app.nagmedar.data.RemuxJobState
import app.nagmedar.ui.tvlab.remux.DeviceCondition
import app.nagmedar.ui.tvlab.remux.RemuxEvent
import app.nagmedar.ui.tvlab.remux.RemuxPolicy
import app.nagmedar.ui.tvlab.remux.Steer
import java.io.File
import java.io.IOException

/**
 * S-1303: where a conversion's file is made: in the app's storage first (libVLC writes a path),
 * then copied into the document the system's picker named, and removed.
 */
interface ConvertFiles {
    /** The file job [id] is encoded into, with [extension]. */
    fun temp(id: Long, extension: String): File

    /** Copies [from] into [output]; [step] gets the bytes copied and says whether to go on. False when it could not. */
    fun save(from: File, output: Uri, step: (Long) -> Boolean): Boolean

    /** Deletes the document, or empties it when it cannot be deleted. */
    fun removeOutput(output: Uri)

    /** Empties the document, for the job's next attempt (which writes only into an empty one). */
    fun emptyOutput(output: Uri)
}

/** S-1303: [ConvertFiles] on the device: the app's external files (its own, no permission), the storage framework's documents. */
class DeviceConvertFiles(context: Context) : ConvertFiles {
    private val context = context.applicationContext

    override fun temp(id: Long, extension: String): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "convert").apply { mkdirs() }
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

    override fun removeOutput(output: Uri) {
        val deleted = runCatching { DocumentsContract.deleteDocument(context.contentResolver, output) }.getOrDefault(false)
        if (!deleted) emptyOutput(output)
    }

    override fun emptyOutput(output: Uri) {
        runCatching { context.contentResolver.openOutputStream(output, "wt")?.close() }
    }
}

/**
 * S-1303: a conversion job of the media jobs' queue (RemuxJobKind.CONVERT), run by libVLC's
 * stream output as the remux's runner runs its jobs: the battery and the heat watched at each
 * step ([RemuxPolicy]: paused, slowed, stopped), a cancel seen, the worker's stop sending it back
 * to the queue to start over. The encoding is nine tenths of the progress, the copy into the
 * document the last tenth. Nothing of a job that is not done stays: the file in the app's storage
 * is always deleted, the document removed when the job owns it (the queue found it empty and
 * gave it the job, [RemuxJobEntry.ownsOutput]). Blocking: the worker's thread.
 */
class ConvertRunner(
    private val engine: SoutEngine,
    private val files: ConvertFiles,
    private val dao: RemuxJobDao,
    private val condition: () -> DeviceCondition,
    private val now: () -> Long = System::currentTimeMillis,
    private val onUpdate: (RemuxJobEntry) -> Unit = {},
) {
    /** A job the last process left: its file in the app's storage deleted (the queue empties the document). */
    fun recover(job: RemuxJobEntry) {
        ConvertProfile.parse(job.plan)?.let { files.temp(job.id, it.container.extension).delete() }
    }

    /** Runs [job] to its end; [interruption] names why the worker stops (null while it may go on). The row as it was left. */
    fun run(job: RemuxJobEntry, interruption: () -> String?): RemuxJobEntry {
        val profile = ConvertProfile.parse(job.plan) ?: return finish(job, null, RemuxJobState.FAILED, RemuxJobError.ENGINE + "bad profile")
        if (ConvertProfiles.validate(profile).any { it != ProfileProblem.NameMissing && it != ProfileProblem.NameTaken }) {
            return finish(job, null, RemuxJobState.FAILED, RemuxJobError.ENGINE + ENCODER)
        }
        if (job.inputUri == job.outputUri) return finish(job.copy(ownsOutput = false), null, RemuxJobState.FAILED, RemuxJobError.SAME_FILE)
        val temp = files.temp(job.id, profile.container.extension)
        temp.delete()
        val startedAt = now()
        val started = job.copy(
            state = RemuxJobState.RUNNING,
            progress = 0f,
            attempts = job.attempts + 1,
            startedAt = startedAt,
            finishedAt = null,
            error = null,
            events = job.events + RemuxEvent.line(startedAt, if (job.attempts == 0) RemuxEvent.STARTED else RemuxEvent.RESTARTED, (job.attempts + 1).toString()),
        )
        dao.saveStart(started.id, started.state, started.attempts, startedAt, started.events)
        onUpdate(started)
        val steering = Steering(started, temp, interruption)
        val outcome = engine.run(Uri.parse(job.inputUri), SoutChain.convert(profile, temp.path), steering::step)
        var current = steering.latest()
        when {
            steering.cancelled -> return finish(current, temp, RemuxJobState.CANCELLED, RemuxJobError.CANCELLED)
            steering.stopReason != null -> return finish(current, temp, RemuxJobState.FAILED, steering.stopReason)
            steering.interrupted != null -> return requeue(current, temp, steering.interrupted!!)
            outcome is SoutOutcome.Failed -> return finish(current, temp, RemuxJobState.FAILED, RemuxJobError.ENGINE + outcome.reason)
            !temp.isFile || temp.length() == 0L -> return finish(current, temp, RemuxJobState.FAILED, RemuxJobError.ENGINE + EMPTY)
        }
        // into the document: the last tenth
        val size = temp.length()
        current = current.copy(progress = ENCODE_SHARE, bytesOut = size)
        dao.saveProgress(current.id, current.progress, current.mediaSeconds, current.totalSeconds, size)
        onUpdate(current)
        var lastSave = now()
        var cancelled = false
        var interrupted: String? = null
        val saved = files.save(temp, Uri.parse(job.outputUri)) { copied ->
            if (dao.get(job.id)?.cancelRequested != false) {
                cancelled = true
                return@save false
            }
            interrupted = interruption()
            if (interrupted != null) return@save false
            if (now() - lastSave >= SAVE_EVERY_MS) {
                lastSave = now()
                current = current.copy(progress = ENCODE_SHARE + (1f - ENCODE_SHARE) * copied / size.coerceAtLeast(1))
                dao.saveProgress(current.id, current.progress, current.mediaSeconds, current.totalSeconds, size)
                onUpdate(current)
            }
            true
        }
        return when {
            cancelled -> finish(current, temp, RemuxJobState.CANCELLED, RemuxJobError.CANCELLED)
            interrupted != null -> requeue(current, temp, interrupted!!)
            !saved -> finish(current, temp, RemuxJobState.FAILED, RemuxJobError.OPEN_OUTPUT)
            else -> finish(current.copy(progress = 1f, bytesOut = size), temp, RemuxJobState.DONE, null)
        }
    }

    /** The job's end: its file in the app's storage deleted; anything but done leaves no document of its own either. */
    private fun finish(job: RemuxJobEntry, temp: File?, state: String, error: String?): RemuxJobEntry {
        temp?.delete()
        if (state != RemuxJobState.DONE && job.ownsOutput) files.removeOutput(Uri.parse(job.outputUri))
        val done = job.copy(state = state, error = error, finishedAt = now(), cancelRequested = false)
        dao.update(done)
        onUpdate(done)
        return done
    }

    /** The worker was stopped under the job: back to the queue, to start over. */
    private fun requeue(job: RemuxJobEntry, temp: File, why: String): RemuxJobEntry {
        temp.delete()
        // a copy cut halfway: emptied, so the next attempt finds the document as the picker made it
        if (job.ownsOutput) files.emptyOutput(Uri.parse(job.outputUri))
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

    /** libVLC's steps: the device watched, the row kept up to date, a cancel seen. */
    private inner class Steering(private var job: RemuxJobEntry, private val temp: File, private val interruption: () -> String?) {
        var cancelled = false
            private set
        var interrupted: String? = null
            private set
        var stopReason: String? = null
            private set
        private var paused = false
        private var slowed = false
        private var lastSave = 0L

        fun latest(): RemuxJobEntry = job

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
            val fraction = if (progress.position >= 0f) progress.position * ENCODE_SHARE else 0f
            job = job.copy(
                progress = fraction,
                mediaSeconds = progress.timeMs / 1000.0,
                totalSeconds = progress.lengthMs.coerceAtLeast(0) / 1000.0,
                bytesOut = temp.length(),
            )
            return when (val steer = RemuxPolicy.decide(condition(), paused)) {
                Steer.Go -> {
                    if (paused) {
                        paused = false
                        transition(RemuxJobState.RUNNING, null, RemuxEvent.RESUMED)
                    } else {
                        saveThrottled()
                    }
                    SoutSteer.Go
                }
                // libVLC's encoder cannot be slowed: a warm device is noted, the job goes on
                is Steer.Slow -> {
                    if (!slowed) {
                        slowed = true
                        transition(RemuxJobState.RUNNING, null, RemuxEvent.SLOWED, RemuxJobError.THERMAL)
                    } else {
                        saveThrottled()
                    }
                    SoutSteer.Go
                }
                is Steer.Pause -> {
                    if (!paused) {
                        paused = true
                        transition(RemuxJobState.PAUSED, steer.reason, RemuxEvent.PAUSED, steer.reason)
                    }
                    SoutSteer.Pause
                }
                is Steer.Stop -> {
                    stopReason = steer.reason
                    job = job.copy(events = job.events + RemuxEvent.line(now(), RemuxEvent.STOPPED, steer.reason))
                    SoutSteer.Stop
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

    companion object {
        /** The encoding's share of the progress; the copy into the document takes the rest. */
        const val ENCODE_SHARE = 0.9f

        /** Why a job failed, after "engine:": a profile this build cannot encode, or nothing written. */
        const val ENCODER = "encoder"
        const val EMPTY = "empty"

        private const val SAVE_EVERY_MS = 1_000L
    }
}
