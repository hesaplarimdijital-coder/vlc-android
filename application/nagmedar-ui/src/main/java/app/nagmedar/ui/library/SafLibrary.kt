package app.nagmedar.ui.library

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import app.nagmedar.audio.MusicFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.Extensions
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Media in folders picked with the system picker (S-309). Without media permission the
 * medialibrary sees nothing, so these folders are how the app keeps working: their videos and
 * songs are listed from the document tree and played through their content URIs.
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

    private val cache = ConcurrentHashMap<String, List<MediaWrapper>>()

    /** The listed documents' sizes in bytes, by URI: the Videos page sorts by them. */
    private val sizes = ConcurrentHashMap<String, Long>()

    /** A listed document's size in bytes, 0 when unknown. */
    fun sizeOf(uri: String): Long = sizes[uri] ?: 0L

    /** Videos ([MediaWrapper.TYPE_VIDEO]) or songs ([MediaWrapper.TYPE_AUDIO]) of [folders], sorted by title. */
    suspend fun media(context: Context, folders: Set<String>, type: Int): List<MediaWrapper> = withContext(Dispatchers.IO) {
        folders.flatMap { folder -> cache.getOrPut(folder) { scan(context, Uri.parse(folder)) } }
            .filter { it.type == type }
            .sortedBy { it.title?.lowercase(Locale.ROOT) }
    }

    /** Drops what is known of [folder], so the next query lists it again. */
    fun forget(folder: String) {
        cache.remove(folder)
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

    private fun scan(context: Context, tree: Uri): List<MediaWrapper> {
        val resolver = context.contentResolver
        val found = mutableListOf<MediaWrapper>()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return found
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootId to 0)
        while (queue.isNotEmpty() && found.size < MAX_ITEMS) {
            val (documentId, depth) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            // a folder that went away (disk removed, permission revoked) is skipped
            val cursor = runCatching { resolver.query(children, PROJECTION, null, null, null) }.getOrNull() ?: continue
            // 1.0.1: the provider can fail while its rows are read too (it died, the disk went): what was read stays
            runCatching { readFolder(cursor, tree, depth, queue, found) }
        }
        return found
    }

    /** The rows of one folder: its media into [found], its folders into [queue]. */
    private fun readFolder(cursor: Cursor, tree: Uri, depth: Int, queue: ArrayDeque<Pair<String, Int>>, found: MutableList<MediaWrapper>) {
        cursor.use { c ->
            while (c.moveToNext() && found.size < MAX_ITEMS) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (depth < MAX_DEPTH && !name.startsWith(".")) queue.add(id to depth + 1)
                    continue
                }
                val type = typeOf(name, mime)
                if (type != MediaWrapper.TYPE_VIDEO && type != MediaWrapper.TYPE_AUDIO) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                val media = MLServiceLocator.getAbstractMediaWrapper(uri)
                if (!c.isNull(4)) sizes[uri.toString()] = c.getLong(4)
                media.setType(type)
                media.setTitle(name.substringBeforeLast('.'))
                media.setLastModified(c.getLong(3) / 1000)
                found += media
            }
        }
    }
}
