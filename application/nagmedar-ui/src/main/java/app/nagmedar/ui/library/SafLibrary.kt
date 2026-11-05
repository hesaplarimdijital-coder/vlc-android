package app.nagmedar.ui.library

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import app.nagmedar.audio.MusicFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.Extensions
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.AppScope
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Media in folders picked with the system picker (S-309). Without media permission the
 * medialibrary sees nothing, so these folders are how the app keeps working: their videos and
 * songs are listed from the document tree and played through their content URIs.
 *
 * 1.0.3: each tree's listing is kept on the device ([SafListingStore]): a start shows its music
 * at once from it and walks the tree once in the background, bringing the listing up to date
 * ([SafListing.merge]: new, changed and removed files; a tree that cannot be reached keeps its
 * files). What the walk changed reaches the screens through [listen].
 */
object SafLibrary {

    private const val MAX_DEPTH = 6
    private const val MAX_ITEMS = 5_000

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_SIZE,
    )

    /** A tree's listing as this process knows it, and its media made of it. */
    private class Known(val entries: List<SafEntry>, val media: List<MediaWrapper>)

    private val known = ConcurrentHashMap<String, Known>()

    /** The trees walked (or being walked) in this process: once a start, again after [forget] or [rescan]. */
    private val walked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** The trees whose kept listing is not to be trusted ([forget]): walked before they are shown. */
    private val stale: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    private var directory: File? = null

    /** The listed documents' sizes in bytes, by URI: the Videos page sorts by them. */
    private val sizes = ConcurrentHashMap<String, Long>()

    /** A listed document's size in bytes, 0 when unknown. */
    fun sizeOf(uri: String): Long = sizes[uri] ?: 0L

    /** Videos ([MediaWrapper.TYPE_VIDEO]) or songs ([MediaWrapper.TYPE_AUDIO]) of [folders], sorted by title. */
    suspend fun media(context: Context, folders: Set<String>, type: Int): List<MediaWrapper> = withContext(Dispatchers.IO) {
        folders.flatMap { folder -> mediaOf(context.applicationContext, folder) }
            .filter { it.type == type }
            .sortedBy { it.title?.lowercase(Locale.ROOT) }
    }

    /**
     * Drops what is known of [folder] (picked again, or taken away), so the next query walks it
     * again before showing it.
     */
    fun forget(folder: String) {
        known.remove(folder)
        walked.remove(folder)
        stale.add(folder)
        // the menus and the picker call it on the main thread: the kept file goes in the background
        directory?.let { dir -> AppScope.launch(Dispatchers.IO) { SafListingStore(dir).delete(folder) } }
    }

    /** 1.0.3: [onChange] whenever a walk changed a tree's listing; the returned function stops it. */
    fun listen(onChange: () -> Unit): () -> Unit {
        listeners += onChange
        return { listeners -= onChange }
    }

    /** 1.0.3: every tree known in this process walked again in the background ("Kitaplığı yeniden tara"). */
    fun rescan(context: Context) {
        val app = context.applicationContext
        for (tree in known.keys) {
            walked.remove(tree)
            refreshLater(app, tree)
        }
    }

    private fun store(context: Context): SafListingStore {
        val dir = directory ?: File(context.noBackupFilesDir, "nagmedar_picked_folders").also { directory = it }
        return SafListingStore(dir)
    }

    /** [tree]'s media: as this process knows them, else as kept (walked again behind them), else walked now. */
    private fun mediaOf(context: Context, tree: String): List<MediaWrapper> {
        known[tree]?.let { return it.media }
        val kept = if (tree in stale) null else store(context).read(tree)
        if (kept != null) {
            remember(tree, kept)
            refreshLater(context, tree)
            return known[tree]?.media.orEmpty()
        }
        walked.add(tree)
        stale.remove(tree)
        refresh(context, tree, emptyList(), keptBefore = false)
        return known[tree]?.media.orEmpty()
    }

    private fun refreshLater(context: Context, tree: String) {
        if (!walked.add(tree)) return
        AppScope.launch(Dispatchers.IO) {
            val before = known[tree]?.entries.orEmpty()
            if (refresh(context, tree, before, keptBefore = true)) listeners.forEach { it() }
        }
    }

    /** Walks [tree], merges the walk into [before] and keeps the result; whether the listing changed. */
    private fun refresh(context: Context, tree: String, before: List<SafEntry>, keptBefore: Boolean): Boolean {
        val walk = walk(context, Uri.parse(tree)) ?: return false
        val (entries, change) = SafListing.merge(before, walk)
        val rootRead = walk.rootId in walk.read
        if (!change.any && keptBefore) return false
        remember(tree, entries)
        // a tree that did not answer and had nothing kept leaves no empty listing behind
        if (rootRead || entries.isNotEmpty()) store(context).write(tree, entries)
        return change.any
    }

    private fun remember(tree: String, entries: List<SafEntry>) {
        val uri = Uri.parse(tree)
        val media = entries.mapNotNull { entry -> entry.takeUnless { it.folder }?.let { toMedia(uri, it) } }
        known[tree] = Known(entries, media)
    }

    private fun toMedia(tree: Uri, entry: SafEntry): MediaWrapper? {
        val type = typeOf(entry.name, entry.mime)
        if (type != MediaWrapper.TYPE_VIDEO && type != MediaWrapper.TYPE_AUDIO) return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId)
        val media = MLServiceLocator.getAbstractMediaWrapper(uri)
        entry.size?.let { sizes[uri.toString()] = it }
        media.setType(type)
        media.setTitle(entry.name.substringBeforeLast('.'))
        media.setLastModified(entry.modified / 1000)
        return media
    }

    /**
     * Media type from the provider's MIME type, or from the extension when the provider does
     * not know it: libVLC's, and the engine's own (the tracker modules, S-911).
     */
    fun typeOf(name: String, mime: String?): Int {
        val m = mime.orEmpty()
        if (m.startsWith("video/")) return MediaWrapper.TYPE_VIDEO
        if (m.startsWith("audio/")) return MediaWrapper.TYPE_AUDIO
        val dot = name.lastIndexOf('.')
        if (dot < 0) return MediaWrapper.TYPE_ALL
        val extension = name.substring(dot).lowercase(Locale.ROOT)
        return when {
            Extensions.VIDEO.contains(extension) -> MediaWrapper.TYPE_VIDEO
            Extensions.AUDIO.contains(extension) -> MediaWrapper.TYPE_AUDIO
            extension.substring(1) in MusicFormats.extensions -> MediaWrapper.TYPE_AUDIO
            else -> MediaWrapper.TYPE_ALL
        }
    }

    /** One walk of [tree]: its media and folders, and the folders read to the end; null when it is no tree. */
    private fun walk(context: Context, tree: Uri): SafWalk? {
        val resolver = context.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val entries = mutableListOf<SafEntry>()
        val read = HashSet<String>()
        var media = 0
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootId to 0)
        while (queue.isNotEmpty() && media < MAX_ITEMS) {
            val (documentId, depth) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            // a folder that went away (disk removed, permission revoked) is skipped: what was kept under it stays
            val cursor = runCatching { resolver.query(children, PROJECTION, null, null, null) }.getOrNull() ?: continue
            // 1.0.1: the provider can fail while its rows are read too (it died, the disk went): what was read stays
            val before = entries.size
            val complete = runCatching { readFolder(cursor, documentId, depth, queue, entries, MAX_ITEMS - media) }.getOrDefault(false)
            media += entries.subList(before, entries.size).count { !it.folder }
            if (complete) read += documentId
        }
        return SafWalk(rootId, entries, read)
    }

    /** The rows of one folder: its media and folders into [found], its folders into [queue]; whether it was read to the end. */
    private fun readFolder(cursor: Cursor, parentId: String, depth: Int, queue: ArrayDeque<Pair<String, Int>>, found: MutableList<SafEntry>, room: Int): Boolean {
        cursor.use { c ->
            var media = 0
            while (c.moveToNext()) {
                if (media >= room) return false
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                val modified = if (c.isNull(3)) 0L else c.getLong(3)
                val size = if (c.isNull(4)) null else c.getLong(4)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (depth < MAX_DEPTH && !name.startsWith(".")) {
                        queue.add(id to depth + 1)
                        found += SafEntry(id, parentId, name, mime, modified, size, depth + 1)
                    }
                    continue
                }
                val type = typeOf(name, mime)
                if (type != MediaWrapper.TYPE_VIDEO && type != MediaWrapper.TYPE_AUDIO) continue
                found += SafEntry(id, parentId, name, mime, modified, size, depth + 1)
                media++
            }
            return true
        }
    }
}
