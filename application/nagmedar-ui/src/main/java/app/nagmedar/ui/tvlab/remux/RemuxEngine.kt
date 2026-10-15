package app.nagmedar.ui.tvlab.remux

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import app.nagmedar.lab.NativeLab
import app.nagmedar.lab.RemuxPlan
import app.nagmedar.lab.RemuxResult
import app.nagmedar.lab.RemuxVerification
import java.io.File

/** A step of a running remux or verification, as the engine reports it. */
data class RemuxProgress(val doneSeconds: Double, val totalSeconds: Double, val bytesOut: Long)

/**
 * What the queue needs from the device (S-1206, S-1209): the native remux and its verification
 * (native/lab/remux.c), and the removal of an output that must not stay. [steer] is asked at
 * every step and answers as [NativeLab.RemuxProgress] does: 0 to go on, a negative number to
 * stop, milliseconds to wait.
 */
interface RemuxEngine {
    /** Whether this build has the native tools. */
    val available: Boolean

    /**
     * Opens [input] for reading only and [output] for writing (truncated) and runs the remux;
     * null when the engine's answer could not be read. Throws [RemuxOpenException] for a file
     * that does not open.
     */
    fun remux(input: Uri, output: Uri, plan: RemuxPlan, steer: (RemuxProgress) -> Int): RemuxResult?

    /** Reads [output] back against [result]'s hash and count; null when it cannot be opened or the answer could not be read. */
    fun verify(output: Uri, result: RemuxResult, dvProfile: Int, steer: (RemuxProgress) -> Int): RemuxVerification?

    /** Stops what runs. */
    fun cancel()

    /** Deletes [output], or empties it when the provider does not delete; whether nothing is left of it. */
    fun removeOutput(output: Uri): Boolean

    /** Truncates [output] to nothing, the document kept for the job's next attempt; whether it could. */
    fun emptyOutput(output: Uri): Boolean

    /** Whether [output] holds nothing yet (a document the picker just made); null when it cannot be read. */
    fun outputEmpty(output: Uri): Boolean?
}

/** The device's engine: file descriptors from the storage framework handed to libnagmedar_lab. */
class NativeRemuxEngine(private val context: Context) : RemuxEngine {
    override val available: Boolean get() = NativeLab.available

    override fun remux(input: Uri, output: Uri, plan: RemuxPlan, steer: (RemuxProgress) -> Int): RemuxResult? {
        // the picker can hand back the source itself ("replace"): never opened for writing
        if (input == output) throw RemuxOpenException(output = true)
        val text = plan.toNative().toByteArray(Charsets.UTF_8)
        // the source is opened to read and nothing else: the engine cannot touch it; the output is not truncated
        // here but by the engine, after it has made sure the two descriptors are not one file and the output is empty
        val json = (open(input, "r") ?: throw RemuxOpenException(output = false)).use { src ->
            (open(output, "rw") ?: throw RemuxOpenException(output = true)).use { dst ->
                NativeLab.remux(src.fd, dst.fd, text) { done, total, bytes -> steer(RemuxProgress(done, total, bytes)) }
            }
        }
        return RemuxResult.parse(json)
    }

    override fun verify(output: Uri, result: RemuxResult, dvProfile: Int, steer: (RemuxProgress) -> Int): RemuxVerification? {
        val json = open(output, "r")?.use { dst ->
            NativeLab.remuxVerify(dst.fd, result.hashScope, result.videoHash, result.videoPackets, dvProfile) { done, total, bytes ->
                steer(RemuxProgress(done, total, bytes))
            }
        } ?: return null
        return RemuxVerification.parse(json)
    }

    override fun cancel() = NativeLab.remuxCancel()

    override fun removeOutput(output: Uri): Boolean {
        val deleted = when (output.scheme) {
            null, "file" -> output.path?.let { File(it).delete() } ?: false
            else -> runCatching { DocumentsContract.deleteDocument(context.contentResolver, output) }.getOrDefault(false)
        }
        if (deleted) return true
        // a provider without delete: at least nothing of the film stays in the file
        return emptyOutput(output)
    }

    override fun emptyOutput(output: Uri): Boolean = runCatching { open(output, "rwt")?.use { true } ?: false }.getOrDefault(false)

    override fun outputEmpty(output: Uri): Boolean? = runCatching { open(output, "r")?.use { it.statSize == 0L } }.getOrNull()

    private fun open(uri: Uri, mode: String): ParcelFileDescriptor? = runCatching {
        when (uri.scheme) {
            null, "file" -> ParcelFileDescriptor.open(File(uri.path ?: return null), ParcelFileDescriptor.parseMode(mode))
            else -> context.contentResolver.openFileDescriptor(uri, mode)
        }
    }.getOrNull()
}
