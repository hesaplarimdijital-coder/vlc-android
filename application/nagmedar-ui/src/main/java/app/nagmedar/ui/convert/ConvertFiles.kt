package app.nagmedar.ui.convert

import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
import android.system.StructStat
import android.util.Log
import app.nagmedar.audio.convert.AudioConverter
import app.nagmedar.audio.convert.ConvertFailure
import app.nagmedar.audio.convert.ConvertPart
import app.nagmedar.audio.convert.ConvertResult
import app.nagmedar.audio.convert.ConvertSettings
import app.nagmedar.audio.cue.CueSheets
import app.nagmedar.audio.cue.CueUri
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * S-1010: the jobs' files, in the app's own storage (convert/): a job's choices and files
 * (<id>.json), what came of them so far (<id>.summary.json), and the user's Stop (<id>.cancel).
 * Nothing else is kept: a job's files go when its summary is put away, or after [KEEP_MS].
 */
class ConvertStore(private val dir: File) {

    constructor(context: Context) : this(File(context.applicationContext.filesDir, "convert"))

    fun save(job: ConvertJob) {
        dir.mkdirs()
        write(File(dir, "${job.id}.json"), job.toJson())
    }

    fun job(id: String): ConvertJob? = read(File(dir, "$id.json"))?.let(ConvertJob::parse)

    /** Whether job [id] is still kept (its summary not put away). */
    fun has(id: String): Boolean = File(dir, "$id.json").isFile

    /** The temporary file job [id] is writing (null: none), kept so that a run the process did not live through removes it. */
    fun savePartial(id: String, handle: String?) {
        val file = File(dir, "$id.partial")
        if (handle == null) {
            file.delete()
        } else {
            dir.mkdirs()
            write(file, handle)
        }
    }

    fun partial(id: String): String? = read(File(dir, "$id.partial"))?.takeIf { it.isNotBlank() }

    /** The folders of the jobs not finished yet: still written in, their permission is kept. */
    fun activeFolders(): Set<String> = dir.listFiles { file -> file.name.endsWith(".json") && !file.name.endsWith(SUMMARY) }
        .orEmpty()
        .mapNotNull { file ->
            val id = file.name.removeSuffix(".json")
            val finished = read(File(dir, id + SUMMARY))?.let { runCatching { JSONObject(it).optBoolean("finished") }.getOrNull() } ?: false
            if (finished) null else read(file)?.let { runCatching { JSONObject(it).optString("folder") }.getOrNull() }
        }
        .filter { it.isNotEmpty() }
        .toSet()

    fun saveSummary(summary: ConvertSummary) {
        dir.mkdirs()
        write(File(dir, summary.jobId + SUMMARY), summary.toJson())
    }

    fun summary(job: ConvertJob): ConvertSummary? = read(File(dir, job.id + SUMMARY))?.let { ConvertSummary.parse(it, job) }

    fun markCancelled(id: String) {
        dir.mkdirs()
        runCatching { File(dir, "$id.cancel").createNewFile() }
    }

    fun isCancelled(id: String): Boolean = File(dir, "$id.cancel").exists()

    /** The job's files, gone: its summary was put away. */
    fun delete(id: String) {
        listOf("$id.json", id + SUMMARY, "$id.cancel", "$id.partial").forEach { File(dir, it).delete() }
    }

    /**
     * Jobs older than [KEEP_MS] at [now] gone, and those finished: a new job's summary takes the
     * place of the last one's.
     */
    fun prune(now: Long) {
        dir.listFiles()?.filter { now - it.lastModified() > KEEP_MS }?.forEach { it.delete() }
        dir.listFiles { file -> file.name.endsWith(SUMMARY) }?.forEach { file ->
            val finished = runCatching { JSONObject(file.readText()).optBoolean("finished") }.getOrDefault(false)
            if (finished) delete(file.name.removeSuffix(SUMMARY))
        }
    }

    private fun write(file: File, text: String) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }

    private fun read(file: File): String? = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()

    companion object {
        /** How long a finished job's summary waits to be seen. */
        const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
        private const val SUMMARY = ".summary.json"
    }
}

/**
 * S-1010: a document tree the system's picker gave (Settings' destination), as the converter's
 * destination: the folders of a file are found or made by name (letter case aside, as the
 * storage treats them), each folder's children read once.
 */
class TreeDestination(private val context: Context, private val tree: Uri) : ConvertDestination {
    private val resolver = context.contentResolver
    private val treeId: String? = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()

    /** A folder's children by their name in lower case: (document id, whether a folder). */
    private val children = HashMap<String, MutableMap<String, Pair<String, Boolean>>>()

    /** The temporary files made, by their uri: their folder. */
    private val temporaries = HashMap<String, String>()

    override fun ready(): Boolean {
        val id = treeId ?: return false
        return runCatching {
            resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, id), arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { c ->
                c.moveToFirst() && (c.getInt(0) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0
            } ?: false
        }.getOrDefault(false)
    }

    override fun exists(item: ConvertItem): Boolean = existing(item) != null

    /** The document at [item]'s place: its folder's id and its own; null when there is none. */
    private fun existing(item: ConvertItem): Pair<String, String>? {
        val parent = folderOf(item, make = false) ?: return null
        val found = list(parent)[item.name.lowercase(Locale.ROOT)] ?: return null
        return if (found.second) null else parent to found.first
    }

    override fun isSource(item: ConvertItem): Boolean {
        val (_, id) = existing(item) ?: return false
        return SameFile.check(context, DocumentsContract.buildDocumentUriUsingTree(tree, id), CueUri.file(Uri.parse(item.uri)))
    }

    override fun createTemporary(item: ConvertItem): String? = runCatching {
        val parent = folderOf(item, make = true) ?: return null
        // hidden, and never the planned name: a file cut short is never taken for a finished one
        val id = make(parent, temporaryName(item), ClipDescription.MIMETYPE_UNKNOWN) ?: return null
        DocumentsContract.buildDocumentUriUsingTree(tree, id).toString().also { temporaries[it] = parent }
    }.onFailure { Log.w(TAG, "cannot make ${item.path}: $it") }.getOrNull()

    override fun commit(handle: String, item: ConvertItem, replace: Boolean): Boolean = runCatching {
        val parent = temporaries[handle] ?: return false
        val temporary = Uri.parse(handle)
        val size = size(temporary)
        if (size <= 0) return false
        val key = item.name.lowercase(Locale.ROOT)
        val before = list(parent)[key]?.takeUnless { it.second }?.first?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) }
        if (before != null && (!replace || SameFile.check(context, before, CueUri.file(Uri.parse(item.uri))))) return false
        // the file there moved aside first, so that a rename that fails leaves it as it was
        val aside = before?.let { rename(it, ".${item.name}.old") ?: return copyOver(parent, temporary, size, item, before) }
        val placed = rename(temporary, item.name)
        if (placed == null) {
            aside?.let { rename(it, item.name) }
            return if (aside == null) copyOver(parent, temporary, size, item, null) else false
        }
        aside?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        list(parent)[key] = DocumentsContract.getDocumentId(placed) to false
        temporaries.remove(handle)
        true
    }.onFailure { Log.w(TAG, "cannot put ${item.path} in place: $it") }.getOrDefault(false)

    /**
     * A provider that renames nothing: the whole new file is copied into a document of the
     * planned name (the one there, [before], removed first: the new file is whole, and it is not
     * the source), then the temporary one removed.
     */
    private fun copyOver(parent: String, temporary: Uri, size: Long, item: ConvertItem, before: Uri?): Boolean {
        if (before != null && !DocumentsContract.deleteDocument(resolver, before)) return false
        val key = item.name.lowercase(Locale.ROOT)
        list(parent).remove(key)
        val id = make(parent, item.name, ClipDescription.MIMETYPE_UNKNOWN) ?: return false
        val target = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        val copied = runCatching {
            resolver.openInputStream(temporary)!!.use { input -> resolver.openOutputStream(target, "w")!!.use { input.copyTo(it) } }
        }.getOrNull()
        if (copied != size || size(target) != size) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            list(parent).remove(key)
            return false
        }
        delete(temporary.toString())
        return true
    }

    override fun delete(handle: String) {
        runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(handle)) }
        temporaries.remove(handle)
    }

    /** [item]'s folder: found, or with [make] made; null when it cannot be. */
    private fun folderOf(item: ConvertItem, make: Boolean): String? {
        var parent = treeId ?: return null
        for (folder in item.folders) {
            val found = list(parent)[folder.lowercase(Locale.ROOT)]
            parent = when {
                found?.second == true -> found.first
                found != null || !make -> return null
                else -> make(parent, folder, DocumentsContract.Document.MIME_TYPE_DIR) ?: return null
            }
        }
        return parent
    }

    /** The document renamed: its new uri, or null when the provider does not rename (or refused). */
    private fun rename(uri: Uri, name: String): Uri? =
        runCatching { DocumentsContract.renameDocument(resolver, uri, name) }.getOrNull()

    private fun size(uri: Uri): Long = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
    }.getOrNull() ?: -1L

    /**
     * A new document [name] in [parent]; its id, or null. A type of no extension keeps the name
     * as given (with audio/ogg the provider would add ".ogg" to ".opus").
     */
    private fun make(parent: String, name: String, mime: String): String? {
        val made = DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, parent), mime, name) ?: return null
        val id = DocumentsContract.getDocumentId(made)
        list(parent)[name.lowercase(Locale.ROOT)] = id to (mime == DocumentsContract.Document.MIME_TYPE_DIR)
        return id
    }

    private fun list(parent: String): MutableMap<String, Pair<String, Boolean>> = children.getOrPut(parent) {
        val names = HashMap<String, Pair<String, Boolean>>()
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        runCatching {
            resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent), projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    names[name.lowercase(Locale.ROOT)] = id to (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
                }
            }
        }
        names
    }

    companion object {
        private const val TAG = "NagmedarConvert"

        /** ".03 Gece.opus.part": hidden, and never a name the planner gives. */
        fun temporaryName(item: ConvertItem): String = ".${item.name}.part"
    }
}

/** S-1010: the music engine converting a library's track; a CUE sheet's track is its part of its file, from the sheet. */
class NativeConvertEngine(context: Context) : ConvertEngine {
    private val context = context.applicationContext

    override fun convert(item: ConvertItem, output: String, settings: ConvertSettings, progress: (Double) -> Boolean): ConvertResult {
        val input = Uri.parse(item.uri)
        val part = if (item.isCuePart) {
            // a CUE track whose sheet is gone is not its whole file
            val track = CueSheets.track(context, input) ?: return ConvertResult.Failed(ConvertFailure.Input, "no CUE sheet")
            ConvertPart(track.from, track.to)
        } else {
            null
        }
        return AudioConverter.convert(context, input, part, Uri.parse(output), item.name, settings, item.tags, progress)
    }
}

/**
 * S-1010: whether two uris are one file: the same uri, or the same device and inode behind
 * their descriptors (a library's file:// path and a folder's document are one file then). A file
 * at [place] that cannot be looked at counts as the same: it is not written over.
 */
object SameFile {
    fun check(context: Context, place: Uri, source: Uri): Boolean {
        if (place == source) return true
        val a = stat(context, place) ?: return true
        val b = stat(context, source) ?: return false
        return a.st_dev == b.st_dev && a.st_ino == b.st_ino
    }

    private fun stat(context: Context, uri: Uri): StructStat? = runCatching {
        val descriptor = when (uri.scheme) {
            null, "file" -> uri.path?.let { ParcelFileDescriptor.open(File(it), ParcelFileDescriptor.MODE_READ_ONLY) }
            "content" -> context.contentResolver.openFileDescriptor(uri, "r")
            else -> null
        }
        descriptor?.use { Os.fstat(it.fileDescriptor) }
    }.getOrNull()
}
