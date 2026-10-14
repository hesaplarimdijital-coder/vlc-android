package app.nagmedar.audio

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import java.io.File

/**
 * The files beside a media file, their names and where they are: a local file's folder, read
 * directly, or a folder chosen with the system's picker (S-309), read through its document tree.
 * A document another app shared alone, or a stream, has no folder to read. The subtitles, the
 * lyrics, the covers and the CUE sheets (S-808) look beside a file with this.
 */
object FolderFiles {

    /** The name and the uri (as text) of each file in [media]'s folder, [media] itself among them. */
    fun siblings(context: Context, media: Uri): List<Pair<String, String>> = when (media.scheme) {
        null, ContentResolver.SCHEME_FILE -> {
            val folder = media.path?.let { File(it).parentFile }
            folder?.listFiles()?.filter { it.isFile }?.map { it.name to Uri.fromFile(it).toString() }.orEmpty()
        }
        ContentResolver.SCHEME_CONTENT -> treeSiblings(context, media)
        else -> emptyList()
    }

    private fun treeSiblings(context: Context, media: Uri): List<Pair<String, String>> {
        // content://…/tree/<root>/document/<document>: a folder's tree the app may read
        val segments = media.pathSegments
        if (segments.size < 4 || segments[0] != "tree" || segments[2] != "document") return emptyList()
        val resolver = context.contentResolver
        val documentId = runCatching { DocumentsContract.getDocumentId(media) }.getOrNull() ?: return emptyList()
        val parentId = parentDocument(resolver, media, documentId) ?: return emptyList()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(media, parentId)
        val cursor = runCatching { resolver.query(children, SIBLINGS, null, null, null) }.getOrNull() ?: return emptyList()
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    add(name to DocumentsContract.buildDocumentUriUsingTree(media, id).toString())
                }
            }
        }
    }

    /** The document id of [media]'s folder in its tree; null when the tree does not tell. */
    fun parentDocument(resolver: ContentResolver, media: Uri, documentId: String): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val path = runCatching { DocumentsContract.findDocumentPath(resolver, media) }.getOrNull()?.path
            if (path != null && path.size >= 2) return path[path.size - 2]
        }
        // "primary:Movies/Film.mkv" is in "primary:Movies", "primary:Film.mkv" in "primary:"
        return when {
            '/' in documentId -> documentId.substringBeforeLast('/')
            ':' in documentId && !documentId.endsWith(':') -> documentId.substringBefore(':') + ":"
            else -> null
        }
    }

    private val SIBLINGS = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )
}
