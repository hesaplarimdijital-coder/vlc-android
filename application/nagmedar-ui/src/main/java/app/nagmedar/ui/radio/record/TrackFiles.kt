package app.nagmedar.ui.radio.record

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File

/**
 * S-1107: what is done to a recorded track's file, by the address kept for it: a path (the app's
 * own music folder) or a document of the folder chosen. Every call is blocking and may fail:
 * nothing here throws; a refusal is false or null.
 */
interface TrackFiles {
    /** Deletes the file; true when it is gone (it was gone already too). */
    fun delete(uri: String): Boolean

    /** Whether the file is still there. */
    fun exists(uri: String): Boolean

    /** The file named [name] (without a folder) instead; its new address, or null when refused. */
    fun rename(uri: String, name: String): Renamed?

    /** An address another app may read the file at, or null. */
    fun shareable(uri: String): Uri?
}

/** A file renamed: its new address and the name the storage gave it. */
data class Renamed(val uri: String, val name: String)

internal class AndroidTrackFiles(private val context: Context) : TrackFiles {

    private fun file(uri: Uri): File? = if (uri.scheme == "file") uri.path?.let(::File) else null

    override fun delete(uri: String): Boolean {
        val parsed = Uri.parse(uri)
        file(parsed)?.let { return !it.exists() || it.delete() }
        return try {
            if (!exists(uri)) true else DocumentsContract.deleteDocument(context.contentResolver, parsed)
        } catch (e: Exception) {
            false
        }
    }

    override fun exists(uri: String): Boolean {
        val parsed = Uri.parse(uri)
        file(parsed)?.let { return it.isFile }
        return try {
            context.contentResolver.query(parsed, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
        } catch (e: Exception) {
            false
        }
    }

    override fun rename(uri: String, name: String): Renamed? {
        val parsed = Uri.parse(uri)
        val local = file(parsed)
        if (local != null) {
            val target = File(local.parentFile ?: return null, name)
            if (target.exists() || !local.renameTo(target)) return null
            return Renamed(Uri.fromFile(target).toString(), target.name)
        }
        return try {
            val moved = DocumentsContract.renameDocument(context.contentResolver, parsed, name) ?: return null
            val actual = context.contentResolver.query(moved, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            Renamed(moved.toString(), actual ?: name)
        } catch (e: Exception) {
            null
        }
    }

    override fun shareable(uri: String): Uri? {
        val parsed = Uri.parse(uri)
        val local = file(parsed) ?: return parsed
        return runCatching { FileProvider.getUriForFile(context, context.packageName + ".provider", local) }.getOrNull()
    }
}
