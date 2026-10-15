package app.nagmedar.ui.convert

import app.nagmedar.audio.convert.ConvertFailure
import app.nagmedar.audio.convert.ConvertResult
import app.nagmedar.audio.convert.ConvertSettings

/**
 * S-1010: where the new files go: a document tree ([TreeDestination]), a map in tests. A file
 * that was there before the job is never written into: each conversion writes a new file of a
 * temporary name, which takes the planned name only once it is whole ([commit]).
 */
interface ConvertDestination {
    /** Whether the folder is there and still ours to write in. */
    fun ready(): Boolean

    /** Whether [item]'s file is in its folder already. */
    fun exists(item: ConvertItem): Boolean

    /** Whether the file at [item]'s place is its source itself (or cannot be told apart from it). */
    fun isSource(item: ConvertItem): Boolean

    /** A new, empty file of a temporary name in [item]'s folder, the folders made; its handle (a document's uri), or null. */
    fun createTemporary(item: ConvertItem): String?

    /**
     * The whole file [handle] given [item]'s name; the one there before goes only with [replace],
     * after the new one is whole, and never when it is the source. False, the file there
     * unchanged, when that cannot be done; [handle] is then the caller's to remove.
     */
    fun commit(handle: String, item: ConvertItem, replace: Boolean): Boolean

    /** A temporary file removed: a conversion that failed or was cut short leaves nothing behind. */
    fun delete(handle: String)
}

/** S-1010: the music engine's side: [item] converted into the file [output] ([NativeConvertEngine]). */
fun interface ConvertEngine {
    fun convert(item: ConvertItem, output: String, settings: ConvertSettings, progress: (Double) -> Boolean): ConvertResult
}

/**
 * S-1010: where a job is: file [index] of [total] ([title]), [fraction] of it done; the counts so
 * far; [waiting] when the battery or the heat holds it.
 */
data class ConvertProgress(
    val index: Int,
    val total: Int,
    val title: String,
    val fraction: Double,
    val converted: Int,
    val skipped: Int,
    val failed: Int,
    val waiting: ConvertReason? = null,
) {
    /** The whole job's, 0..1. */
    val overall: Float get() = if (total <= 0) 0f else ((index + fraction.coerceIn(0.0, 1.0)) / total).toFloat().coerceIn(0f, 1f)
}

/**
 * S-1010: a job's files, one after the other: a file that is there already is left alone (when
 * asked), else made and converted; a failed one is removed, its reason kept. Before each file
 * and twice a second during it the device is looked at ([ConvertPolicy]): a hot device slows
 * the work, a low battery or a hotter device pauses it, worse stops it (the files left fail
 * with the reason). [cancelled] stops it the same way, the file under way removed. What was
 * done goes to [onItem] after each file, so that a job taken up again goes on after it.
 */
class ConvertRunner(
    private val engine: ConvertEngine,
    private val destination: ConvertDestination,
    private val device: () -> DeviceState = { DeviceState.FINE },
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    /** How often progress is told while a file is converted. */
    private val reportEveryMs = 250L

    /**
     * Works through [job] after what [done] says was done. [interrupted] (the system stopping
     * the worker) ends it at once without a word about the file under way or those after: the
     * job is taken up again later, from there.
     */
    fun run(
        job: ConvertJob,
        done: ConvertSummary? = null,
        cancelled: () -> Boolean = { false },
        interrupted: () -> Boolean = { false },
        onProgress: (ConvertProgress) -> Unit = {},
        onItem: (ConvertSummary) -> Unit = {},
        leftover: String? = null,
        onPartial: (String?) -> Unit = {},
    ): ConvertSummary {
        // the temporary file of a run the process did not live through
        if (leftover != null) {
            runCatching { destination.delete(leftover) }
            onPartial(null)
        }
        var summary = done?.takeIf { it.jobId == job.id }?.copy(finished = false) ?: ConvertSummary(job.id, job.items.size, emptyList())
        val already = summary.outcomes.mapTo(HashSet()) { key(it.item) }
        // a folder gone or no longer ours fails every file at once
        var stop: ConvertReason? = if (destination.ready()) null else ConvertReason.Folder
        val settings = job.options.settings()
        val halted = { cancelled() || interrupted() }
        for ((index, item) in job.items.withIndex()) {
            if (key(item) in already) continue
            if (interrupted()) return summary
            if (stop == null && cancelled()) stop = ConvertReason.Cancelled
            if (stop == null) {
                val steer = waitForDevice(halted) { reason -> onProgress(progress(summary, index, item, 0.0, reason)) }
                if (interrupted()) return summary
                if (steer is ConvertSteer.Stop) stop = steer.reason
            }
            val outcome = stop?.let { ItemOutcome.Failed(item, it) } ?: run {
                onProgress(progress(summary, index, item, 0.0, null))
                convertOne(job, item, settings, halted, onPartial) { fraction, waiting -> onProgress(progress(summary, index, item, fraction, waiting)) }
            }
            if (interrupted()) return summary
            if (outcome is ItemOutcome.Failed && outcome.reason in STOPPING) stop = outcome.reason
            summary = summary.with(outcome)
            onItem(summary)
        }
        summary = summary.copy(finished = true)
        onItem(summary)
        return summary
    }

    private fun convertOne(
        job: ConvertJob,
        item: ConvertItem,
        settings: ConvertSettings,
        cancelled: () -> Boolean,
        onPartial: (String?) -> Unit,
        report: (Double, ConvertReason?) -> Unit,
    ): ItemOutcome {
        val exists = destination.exists(item)
        if (exists && job.options.skipExisting) return ItemOutcome.Skipped(item)
        // the source in the planned place (an M4A library converted to AAC into its own folder): left alone
        if (exists && destination.isSource(item)) return ItemOutcome.Failed(item, ConvertReason.Source)
        val output = destination.createTemporary(item) ?: return ItemOutcome.Failed(item, ConvertReason.Output)
        onPartial(output)
        val outcome = convertInto(output, exists, item, settings, cancelled, report)
        if (outcome !is ItemOutcome.Converted) runCatching { destination.delete(output) }
        onPartial(null)
        return outcome
    }

    private fun convertInto(
        output: String,
        exists: Boolean,
        item: ConvertItem,
        settings: ConvertSettings,
        cancelled: () -> Boolean,
        report: (Double, ConvertReason?) -> Unit,
    ): ItemOutcome {
        var stopped: ConvertReason? = null
        var lastCheck = now()
        var lastReport = lastCheck
        var paused = false
        val listener: (Double) -> Boolean = listener@{ fraction ->
            val time = now()
            if (time - lastReport >= reportEveryMs) {
                lastReport = time
                report(fraction, null)
            }
            if (cancelled()) {
                stopped = ConvertReason.Cancelled
                return@listener false
            }
            if (time - lastCheck >= ConvertPolicy.CHECK_MS) {
                lastCheck = time
                when (val steer = ConvertPolicy.decide(device(), paused)) {
                    ConvertSteer.Go -> paused = false
                    is ConvertSteer.Slow -> {
                        paused = false
                        sleep(steer.waitMs)
                    }
                    is ConvertSteer.Pause -> {
                        report(fraction, steer.reason)
                        val after = waitForDevice(cancelled, paused = true) { reason -> report(fraction, reason) }
                        paused = false
                        when {
                            cancelled() -> stopped = ConvertReason.Cancelled
                            after is ConvertSteer.Stop -> stopped = after.reason
                        }
                        lastCheck = now()
                    }
                    is ConvertSteer.Stop -> stopped = steer.reason
                }
            }
            stopped == null
        }
        // whatever goes wrong in the engine's side is this file's failure, not the job's
        val result = try {
            engine.convert(item, output, settings, listener)
        } catch (e: Exception) {
            ConvertResult.Failed(ConvertFailure.Engine, e.message ?: e.javaClass.simpleName)
        }
        return when (result) {
            is ConvertResult.Done ->
                if (runCatching { destination.commit(output, item, replace = exists) }.getOrDefault(false)) {
                    ItemOutcome.Converted(item, result.bytes)
                } else {
                    ItemOutcome.Failed(item, ConvertReason.Output)
                }
            ConvertResult.Cancelled -> ItemOutcome.Failed(item, stopped ?: ConvertReason.Cancelled)
            is ConvertResult.Failed -> ItemOutcome.Failed(item, reasonOf(result.failure), result.detail)
        }
    }

    /**
     * Waits while the device asks for a pause, telling [waiting] why; Go or Slow when the work
     * may go on, Stop when it must not (or [cancelled]).
     */
    private fun waitForDevice(cancelled: () -> Boolean, paused: Boolean = false, waiting: (ConvertReason) -> Unit): ConvertSteer {
        var held = paused
        while (true) {
            if (cancelled()) return ConvertSteer.Stop(ConvertReason.Cancelled)
            when (val steer = ConvertPolicy.decide(device(), held)) {
                is ConvertSteer.Pause -> {
                    held = true
                    waiting(steer.reason)
                    sleep(steer.waitMs)
                }
                else -> return steer
            }
        }
    }

    private fun progress(summary: ConvertSummary, index: Int, item: ConvertItem, fraction: Double, waiting: ConvertReason?) =
        ConvertProgress(index, summary.total, item.title, fraction, summary.converted, summary.skipped, summary.failed.size, waiting)

    private fun key(item: ConvertItem) = item.uri + "\u0000" + item.path

    private fun reasonOf(failure: ConvertFailure): ConvertReason = when (failure) {
        ConvertFailure.NoEngine -> ConvertReason.NoEngine
        ConvertFailure.Input -> ConvertReason.Input
        ConvertFailure.Output -> ConvertReason.Output
        ConvertFailure.Engine -> ConvertReason.Engine
    }

    private companion object {
        /** Reasons that end the job: the files after fail the same way. */
        val STOPPING = setOf(ConvertReason.Cancelled, ConvertReason.Battery, ConvertReason.Thermal, ConvertReason.NoEngine, ConvertReason.Folder)
    }
}
