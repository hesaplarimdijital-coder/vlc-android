package app.nagmedar.ui.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
    )

    private val cache = ConcurrentHashMap<String, List<MediaWrapper>>()

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

    /** Media type from the provider's MIME type, or from the extension when the provider does not know it. */
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
                    val media = MLServiceLocator.getAbstractMediaWrapper(DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    media.setType(type)
                    media.setTitle(name.substringBeforeLast('.'))
                    media.setLastModified(c.getLong(3) / 1000)
                    found += media
                }
            }
        }
        return found
    }
}
