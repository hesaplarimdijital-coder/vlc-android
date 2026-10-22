package app.nagmedar.ui.player.snapshot

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.R
import app.nagmedar.ui.onboarding.FolderGrants
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/** A screenshot could not be written, and why. */
class SnapshotException(val reason: SnapshotFailure, cause: Throwable? = null) : Exception(reason.name, cause)

/**
 * Where screenshots are written (S-606): the folder chosen with the system's picker, or
 * Pictures/Nağmedar, where the gallery finds them. A file that fails half way is removed.
 */
internal class SnapshotFiles(private val context: Context) {
    private val appName: String get() = context.getString(R.string.nagmedar_shell_title)

    /** Writes [name] with [body] and returns where it is; throws [SnapshotException]. */
    fun write(folder: String?, name: String, mime: String, body: (OutputStream) -> Unit): Uri =
        if (folder != null) {
            writeToTree(Uri.parse(folder), name, mime, body)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToPictures(name, mime, body)
        } else {
            writeToFile(name, mime, body)
        }

    private fun writeToTree(tree: Uri, name: String, mime: String, body: (OutputStream) -> Unit): Uri {
        val resolver = context.contentResolver
        val parent = try {
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        } catch (e: IllegalArgumentException) {
            throw SnapshotException(SnapshotFailure.Folder, e)
        }
        // the provider makes the name unique ("… (1).png") when the folder has it
        val document = try {
            DocumentsContract.createDocument(resolver, parent, mime, name)
        } catch (e: SecurityException) {
            throw SnapshotException(SnapshotFailure.Folder, e)
        } catch (e: FileNotFoundException) {
            throw SnapshotException(SnapshotFailure.Folder, e)
        } catch (e: IllegalArgumentException) {
            throw SnapshotException(SnapshotFailure.Folder, e)
        } ?: throw SnapshotException(SnapshotFailure.Folder)
        try {
            (resolver.openOutputStream(document, "w") ?: throw IOException("no stream")).use(body)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw SnapshotException(SnapshotFailure.Write, e)
        }
        return document
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToPictures(name: String, mime: String, body: (OutputStream) -> Unit): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, defaultSnapshotFolder(appName))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        } catch (e: Exception) {
            throw SnapshotException(SnapshotFailure.Write, e)
        } ?: throw SnapshotException(SnapshotFailure.Write)
        try {
            (resolver.openOutputStream(uri, "w") ?: throw IOException("no stream")).use(body)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw SnapshotException(SnapshotFailure.Write, e)
        }
        return uri
    }

    /** Before Android 10: the file itself, with the storage permission the player asks for. */
    @Suppress("DEPRECATION")
    private fun writeToFile(name: String, mime: String, body: (OutputStream) -> Unit): Uri {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), appName)
        if (!folder.isDirectory && !folder.mkdirs()) throw SnapshotException(SnapshotFailure.Write)
        val file = unique(folder, name)
        try {
            file.outputStream().use(body)
        } catch (e: Exception) {
            file.delete()
            throw SnapshotException(SnapshotFailure.Write, e)
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(mime), null)
        return FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    }

    private fun unique(folder: File, name: String): File {
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "")
        var file = File(folder, name)
        var n = 1
        while (file.exists()) file = File(folder, "$base (${n++}).$extension")
        return file
    }
}

/**
 * A folder chosen for the screenshots (Settings, or the screenshot button's menu): written in from
 * now on, the permission kept across restarts. The folder before it lets go of its permission as
 * far as nothing else uses it (the recordings, the library…: [FolderGrants]).
 */
fun keepSnapshotFolder(context: Context, prefs: NagmedarPreferences, uri: Uri) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    val before = prefs.snapshot.folder
    prefs.updateSnapshot(prefs.snapshot.copy(folder = uri.toString()))
    if (before != null && before != uri.toString()) FolderGrants.release(context, before)
}

/** Back to Pictures/Nağmedar: the chosen folder lets go of its permission as far as nothing else uses it. */
fun forgetSnapshotFolder(context: Context, prefs: NagmedarPreferences) {
    val before = prefs.snapshot.folder ?: return
    prefs.updateSnapshot(prefs.snapshot.copy(folder = null))
    FolderGrants.release(context, before)
}
