package app.nagmedar.ui.library

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * 1.0.3: one document of a picked folder's tree as its provider listed it: a file or a folder
 * ([folder]), under the folder [parentId] (the tree's root for its top level), at [depth].
 */
data class SafEntry(
    val documentId: String,
    val parentId: String,
    val name: String,
    val mime: String?,
    val modified: Long,
    val size: Long?,
    val depth: Int,
) {
    val folder: Boolean get() = mime == MIME_FOLDER

    companion object {
        /** DocumentsContract.Document.MIME_TYPE_DIR, here so that the rules need no Android. */
        const val MIME_FOLDER = "vnd.android.document/directory"
    }
}

/**
 * One walk of a tree: the documents it listed, the folders whose listing it read to the end
 * ([read]; the root's id among them when the root answered).
 */
class SafWalk(val rootId: String, val entries: List<SafEntry>, val read: Set<String>)

/** What a walk changed in a kept listing. */
data class SafChange(val added: Int, val changed: Int, val removed: Int) {
    val any: Boolean get() = added + changed + removed > 0
}

/**
 * 1.0.3: a picked folder's listing kept between starts and brought up to date by a walk, without
 * losing what the walk could not see. A document the walk listed is taken as it is now (a new one
 * is added; one whose modification time or size moved is changed). A kept document the walk did
 * not list is removed only when its folder was read to the end; under a folder the walk could not
 * read (the drive was out, the provider failed, the walk stopped at its limit) it stays, as does
 * everything when the tree's root did not answer: a folder that became unreachable keeps its
 * tracks until the user takes it away. A folder that is gone takes what was under it.
 */
object SafListing {

    fun merge(kept: List<SafEntry>, walk: SafWalk): Pair<List<SafEntry>, SafChange> {
        if (walk.rootId !in walk.read) return kept to SafChange(0, 0, 0)
        val before = kept.associateBy { it.documentId }
        val now = walk.entries.associateBy { it.documentId }
        val result = LinkedHashMap<String, SafEntry>(now)
        var added = 0
        var changed = 0
        for (entry in walk.entries) {
            val old = before[entry.documentId]
            when {
                old == null -> added++
                old.modified != entry.modified || old.size != entry.size || old.name != entry.name -> changed++
            }
        }
        // the folders that stand: the root, and every folder listed now or kept below
        val folders = HashSet<String>().apply {
            add(walk.rootId)
            walk.entries.filter { it.folder }.mapTo(this) { it.documentId }
        }
        var removed = 0
        for (entry in kept.sortedBy { it.depth }) {
            if (entry.documentId in now) continue
            val stays = entry.parentId !in walk.read && entry.parentId in folders
            if (stays) {
                result[entry.documentId] = entry
                if (entry.folder) folders += entry.documentId
            } else {
                removed++
            }
        }
        return result.values.toList() to SafChange(added, changed, removed)
    }
}

/**
 * 1.0.3: the picked folders' listings on the device (the app's no-backup files), a file each, so
 * that a start shows their music at once instead of walking every tree first.
 */
internal class SafListingStore(private val directory: File) {

    private fun file(tree: String) = File(directory, "tree-" + Integer.toHexString(tree.hashCode()) + "-" + tree.length)

    /** The kept listing of [tree]; null when none was kept (or it was kept for another tree, or by another version). */
    fun read(tree: String): List<SafEntry>? = runCatching {
        file(tree).takeIf { it.isFile }?.inputStream()?.use { decode(it, tree) }
    }.onFailure { Log.w(TAG, "a picked folder's listing could not be read: $it") }.getOrNull()

    fun write(tree: String, entries: List<SafEntry>) {
        runCatching {
            directory.mkdirs()
            val target = file(tree)
            val temporary = File(directory, target.name + ".new")
            temporary.outputStream().use { encode(it, tree, entries) }
            if (!temporary.renameTo(target)) temporary.delete()
        }.onFailure { Log.w(TAG, "a picked folder's listing could not be kept: $it") }
    }

    fun delete(tree: String) {
        file(tree).delete()
    }

    companion object {
        private const val TAG = "SafListingStore"
        private const val MAGIC = 0x4e534c31 // "NSL1"
        private const val VERSION = 1

        internal fun encode(output: OutputStream, tree: String, entries: List<SafEntry>) {
            val out = DataOutputStream(BufferedOutputStream(output, 32 * 1024))
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeUTF(tree)
            out.writeInt(entries.size)
            for (entry in entries) {
                out.writeUTF(entry.documentId)
                out.writeUTF(entry.parentId)
                out.writeUTF(entry.name)
                out.writeBoolean(entry.mime != null)
                if (entry.mime != null) out.writeUTF(entry.mime)
                out.writeLong(entry.modified)
                out.writeLong(entry.size ?: -1L)
                out.writeInt(entry.depth)
            }
            out.flush()
        }

        internal fun decode(input: InputStream, tree: String): List<SafEntry>? {
            val data = DataInputStream(BufferedInputStream(input, 32 * 1024))
            if (data.readInt() != MAGIC || data.readInt() != VERSION || data.readUTF() != tree) return null
            val count = data.readInt()
            if (count < 0) return null
            return List(count) {
                val id = data.readUTF()
                val parent = data.readUTF()
                val name = data.readUTF()
                val mime = if (data.readBoolean()) data.readUTF() else null
                val modified = data.readLong()
                val size = data.readLong().takeIf { it >= 0 }
                SafEntry(id, parent, name, mime, modified, size, data.readInt())
            }
        }
    }
}
