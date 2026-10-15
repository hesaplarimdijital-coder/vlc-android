package app.nagmedar.ui.player.subtitles.tools

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

/**
 * Whether two addresses are the same file (S-708): the tools never write into the original, and
 * the system's "save as" lets the user pick it. The addresses are compared as they are, by
 * document (authority and id: a tree's address and a document's of the same file agree), and by
 * the path they come to where one can be told (a file, or the primary storage's document).
 */
internal object DocumentIdentity {

    fun same(a: Uri, b: Uri): Boolean {
        if (a == b || a.toString() == b.toString()) return true
        val ida = documentOf(a)
        val idb = documentOf(b)
        if (ida != null && idb != null && ida == idb) return true
        val pa = pathOf(a)
        val pb = pathOf(b)
        return pa != null && pb != null && pa == pb
    }

    /** "authority/id" of a document address; null for any other. */
    private fun documentOf(uri: Uri): String? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        return "${uri.authority}/$id"
    }

    /** The canonical path a file address or a primary-storage document comes to; null when it is not told. */
    private fun pathOf(uri: Uri): String? {
        val raw = when (uri.scheme) {
            null, ContentResolver.SCHEME_FILE -> uri.path
            ContentResolver.SCHEME_CONTENT -> {
                if (uri.authority != EXTERNAL_STORAGE) return null
                val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
                if (!id.startsWith("primary:")) return null
                File(Environment.getExternalStorageDirectory(), id.substringAfter(':')).path
            }
            else -> null
        } ?: return null
        return runCatching { File(raw).canonicalPath }.getOrNull()
    }

    /** The size a document says it has; null when it does not say. */
    fun sizeOf(resolver: ContentResolver, uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
}
