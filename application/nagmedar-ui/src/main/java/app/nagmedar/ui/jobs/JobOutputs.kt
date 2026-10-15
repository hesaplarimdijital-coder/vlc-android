package app.nagmedar.ui.jobs

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException

/** What a long job's output holds before the job writes into it (S-1303, S-1206). */
enum class OutputState {
    /** Nothing: the new document the system's picker made, or one a job emptied. */
    Empty,

    /** Something: a file the picker "replaced". Never written. */
    Holds,

    /** Not a regular file (a pipe, a socket): its size, so whether it is empty, cannot be known. Never written. */
    NotAFile,

    /** It does not open. */
    Unreadable,
}

/**
 * The documents long jobs write (S-1303's video conversions, S-1206's remux), by one rule: a
 * job writes only into an output it found [OutputState.Empty], and removes or empties only an
 * output that is its own (the callers keep that ownership). A file:// output is a plain file.
 * Blocking: a worker's or IO's thread.
 */
class JobOutputs(context: Context) {
    private val context = context.applicationContext

    fun state(output: Uri): OutputState = runCatching {
        open(output, "r")?.use { fd ->
            val size = fd.statSize
            when {
                size == 0L -> OutputState.Empty
                size < 0L -> OutputState.NotAFile
                else -> OutputState.Holds
            }
        } ?: OutputState.Unreadable
    }.getOrDefault(OutputState.Unreadable)

    /** Deletes [output], or empties it when the provider does not delete; whether nothing of it is left. */
    fun remove(output: Uri): Boolean {
        val deleted = when (output.scheme) {
            null, "file" -> output.path?.let { File(it) }?.let { !it.exists() || it.delete() } ?: false
            else -> try {
                DocumentsContract.deleteDocument(context.contentResolver, output)
            } catch (e: FileNotFoundException) {
                // gone already
                true
            } catch (e: Exception) {
                // a provider that does not delete, or the permission gone
                false
            }
        }
        return deleted || empty(output)
    }

    /** Truncates [output] to nothing, the document kept (for a job's next attempt); whether it is empty now. */
    fun empty(output: Uri): Boolean {
        if (output.scheme == null || output.scheme == "file") {
            // never made here: a file that is gone stays gone
            if (output.path?.let { File(it).isFile } != true) return false
        }
        runCatching { open(output, "wt")?.close() }
        return state(output) == OutputState.Empty
    }

    /** The descriptor of [uri] in [mode] ("r", "rw", "wt" …); null when it does not open. */
    fun open(uri: Uri, mode: String): ParcelFileDescriptor? = runCatching {
        when (uri.scheme) {
            null, "file" -> ParcelFileDescriptor.open(File(uri.path ?: return null), ParcelFileDescriptor.parseMode(mode))
            else -> context.contentResolver.openFileDescriptor(uri, mode)
        }
    }.getOrNull()
}
