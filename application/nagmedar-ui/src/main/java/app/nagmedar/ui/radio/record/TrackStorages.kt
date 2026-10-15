package app.nagmedar.ui.radio.record

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale

/**
 * S-1107: where recordings are written in the app. Without a folder chosen (Settings › Radio ›
 * Recordings folder) the app's own music folder, which needs no permission; with one, the tree the
 * system's picker gave, through the Storage Access Framework, a folder per station in it.
 */
internal class AndroidTrackStorage(
    private val context: Context,
    private val folder: () -> String?,
) : TrackStorage {
    @Volatile
    private var cached: Pair<String?, TrackStorage>? = null

    private fun current(): TrackStorage {
        val tree = folder()
        cached?.let { (key, storage) -> if (key == tree) return storage }
        val made = if (tree == null) LocalTrackStorage(context) else SafTrackStorage(context, Uri.parse(tree))
        cached = tree to made
        return made
    }

    override fun writable(): Boolean = current().writable()

    override fun names(folder: String): Set<String> = current().names(folder)

    override fun create(folder: String, name: String, mime: String): TrackOutput = current().create(folder, name, mime)

    override fun freeBytes(): Long = current().freeBytes()
}

/** The app's own music folder (Android/data/…/files/Music/Radio): no permission, removed with the app. */
internal class LocalTrackStorage(private val context: Context) : TrackStorage {

    private fun root(): File = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "music"), ROOT)

    override fun writable(): Boolean {
        val root = root()
        return (root.isDirectory || root.mkdirs()) && root.canWrite()
    }

    override fun names(folder: String): Set<String> =
        File(root(), folder).list()?.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }.orEmpty()

    override fun create(folder: String, name: String, mime: String): TrackOutput {
        val directory = File(root(), folder)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("no folder $folder")
        val file = File(directory, name)
        if (!file.createNewFile()) throw IOException("$name exists")
        val stream = FileOutputStream(file)
        return StreamOutput(
            uri = Uri.fromFile(file).toString(),
            name = file.name,
            stream = stream,
            free = { freeBytes() },
            remove = { file.delete() },
        )
    }

    override fun freeBytes(): Long {
        var place: File? = root()
        while (place != null && !place.exists()) place = place.parentFile
        return place?.usableSpace ?: -1L
    }

    private companion object {
        const val ROOT = "Radio"
    }
}

/** A tree of the Storage Access Framework: the files are documents made with [DocumentsContract]. */
internal class SafTrackStorage(private val context: Context, private val tree: Uri) : TrackStorage {

    private val resolver = context.contentResolver

    /** The station folders found or made, by name. */
    private val folders = HashMap<String, Uri>()

    @Volatile
    private var lastFree = -1L

    private val rootDocument: Uri? by lazy {
        try {
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun writable(): Boolean {
        val held = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        val root = rootDocument ?: return false
        return held && exists(root)
    }

    private fun exists(document: Uri): Boolean = try {
        resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
    } catch (e: Exception) {
        false
    }

    /** The children of [parent]: name and document id. */
    private fun children(parent: Uri): List<Child> {
        val id = DocumentsContract.getDocumentId(parent)
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        val out = ArrayList<Child>()
        try {
            resolver.query(
                uri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) out += Child(c.getString(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
            }
        } catch (e: Exception) {
            // a provider that fails to list: the folder reads as empty, creating then says what is wrong
        }
        return out
    }

    private class Child(val id: String, val name: String, val mime: String) {
        val isFolder: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    /** The folder [name] in the tree, made if it is not there. */
    private fun stationFolder(name: String, make: Boolean): Uri? {
        folders[name]?.let { known -> if (exists(known)) return known }
        val root = rootDocument ?: throw IOException("no folder")
        val found = children(root).firstOrNull { it.isFolder && it.name.equals(name, ignoreCase = true) }
        val uri = if (found != null) {
            DocumentsContract.buildDocumentUriUsingTree(tree, found.id)
        } else if (make) {
            try {
                DocumentsContract.createDocument(resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, name)
            } catch (e: Exception) {
                throw IOException("folder $name could not be made", e)
            } ?: throw IOException("folder $name could not be made")
        } else {
            return null
        }
        folders[name] = uri
        return uri
    }

    override fun names(folder: String): Set<String> {
        val parent = stationFolder(folder, make = false) ?: return emptySet()
        return children(parent).mapTo(HashSet()) { it.name.lowercase(Locale.ROOT) }
    }

    override fun create(folder: String, name: String, mime: String): TrackOutput {
        val parent = stationFolder(folder, make = true) ?: throw IOException("no folder")
        val document = try {
            DocumentsContract.createDocument(resolver, parent, mime, name)
        } catch (e: Exception) {
            throw IOException("$name could not be made", e)
        } ?: throw IOException("$name could not be made")
        val descriptor = try {
            resolver.openFileDescriptor(document, "w")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw IOException("$name could not be opened", e)
        } ?: run {
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw IOException("$name could not be opened")
        }
        val stream = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
        val actual = displayName(document) ?: name
        return StreamOutput(
            uri = document.toString(),
            name = actual,
            stream = stream,
            free = {
                val free = runCatching { Os.fstatvfs(descriptor.fileDescriptor).let { it.f_bavail * it.f_frsize } }.getOrDefault(-1L)
                if (free >= 0) lastFree = free
                free
            },
            remove = { runCatching { DocumentsContract.deleteDocument(resolver, document) } },
        )
    }

    /** The name the provider gave the document: it may have made it unique or added an extension. */
    private fun displayName(document: Uri): String? = try {
        resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    override fun freeBytes(): Long {
        val fromRoot = rootFree()
        return if (fromRoot >= 0) fromRoot else lastFree
    }

    /** The room of the volume the tree is on, where its provider says (the device's storage and cards do). */
    private fun rootFree(): Long {
        val authority = tree.authority ?: return -1L
        val volume = try {
            DocumentsContract.getTreeDocumentId(tree).substringBefore(':')
        } catch (e: IllegalArgumentException) {
            return -1L
        }
        return try {
            resolver.query(
                DocumentsContract.buildRootsUri(authority),
                arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_AVAILABLE_BYTES),
                null, null, null,
            )?.use { c ->
                var found = -1L
                while (c.moveToNext()) {
                    if (c.getString(0) == volume && !c.isNull(1)) found = c.getLong(1)
                }
                found
            } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }
}

/** A file being written to a stream: buffered, its bytes counted. */
internal class StreamOutput(
    override val uri: String,
    override val name: String,
    stream: OutputStream,
    private val free: () -> Long,
    private val remove: () -> Unit,
) : TrackOutput {
    private val out = BufferedOutputStream(stream, BUFFER)
    private var closed = false

    override var size: Long = 0
        private set

    override fun write(data: ByteArray, offset: Int, length: Int) {
        out.write(data, offset, length)
        size += length
    }

    override fun freeBytes(): Long = free()

    override fun close() {
        if (closed) return
        closed = true
        try {
            out.flush()
        } finally {
            runCatching { out.close() }
        }
    }

    override fun delete() {
        if (!closed) {
            closed = true
            runCatching { out.close() }
        }
        remove()
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
