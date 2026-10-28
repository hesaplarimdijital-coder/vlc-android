package app.nagmedar.ui.transcribe

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Immutable
import app.nagmedar.audio.FolderFiles
import app.nagmedar.ui.library.displayName
import java.io.File
import java.io.OutputStream

/** A subtitle file a job wrote: where it is, its name, and whether beside the media (else in the app's folder). */
@Immutable
data class SavedSubtitle(val uri: String, val name: String, val beside: Boolean)

/** A file a "Save as…" copies from: where it is and what it is called. */
@Immutable
data class ToolsFile(val uri: String, val name: String)

/** What came of a write into the document the system's "save as" made. */
enum class WriteOutcome { Written, SameFile, Failed }

/**
 * [bytes] into the document [uri] the system's "save as" made for [source]: never into the
 * original, whatever the picker confirmed, nor into any file that is not new and empty.
 */
internal fun writeNewDocument(context: Context, uri: String, source: ToolsFile, bytes: ByteArray): WriteOutcome {
    val target = Uri.parse(uri)
    val resolver = context.contentResolver
    if (DocumentIdentity.same(target, Uri.parse(source.uri))) return WriteOutcome.SameFile
    val size = DocumentIdentity.sizeOf(resolver, target)
    if (size != null && size > 0L) return WriteOutcome.SameFile
    val written = runCatching { resolver.openOutputStream(target, "wt")?.use { it.write(bytes) } != null }.getOrDefault(false)
    if (written) return WriteOutcome.Written
    // what the picker made for this write (it was empty) does not stay, empty or half written;
    // a document whose size was not told is left alone rather than removed
    if (size == 0L) runCatching { DocumentsContract.deleteDocument(resolver, target) }.recoverCatching { resolver.delete(target, null, null) }
    return WriteOutcome.Failed
}

/** [bytes] as a subtitle file beside [media], or in the app's folder ([saveSubtitle] below). */
internal fun saveSubtitle(context: Context, media: Uri, fileName: String, bytes: ByteArray): SavedSubtitle? =
    saveSubtitle(context, media, fileName) { it.write(bytes) }

/**
 * A subtitle file beside [media], [write] putting the file's content into the new file's stream:
 * in a local file's folder, or in the folder chosen with the system's picker, through its
 * document tree; where the app may not write, in its own folder. Null when neither takes it. It
 * may be called twice: beside the media, then in the app's folder.
 */
internal fun saveSubtitle(context: Context, media: Uri, fileName: String, write: (OutputStream) -> Unit): SavedSubtitle? {
    saveBeside(context, media, fileName, write)?.let { uri ->
        return SavedSubtitle(uri.toString(), displayName(context.contentResolver, uri) ?: fileName, beside = true)
    }
    val folder = File(context.getExternalFilesDir(null) ?: context.filesDir, APP_SUBTITLES)
    return runCatching {
        folder.mkdirs()
        val file = writeNewFile(folder, fileName, source = null) { file -> file.outputStream().use(write) } ?: return@runCatching null
        SavedSubtitle(Uri.fromFile(file).toString(), file.name, beside = false)
    }.getOrNull()
}

/** A new file beside [media] (a local file's folder, or its picked tree), [write] filling its stream; null where the app may not write. Never replaces a file. */
private fun saveBeside(context: Context, media: Uri, fileName: String, write: (OutputStream) -> Unit): Uri? = when (media.scheme) {
    null, ContentResolver.SCHEME_FILE -> runCatching {
        val folder = File(requireNotNull(media.path)).parentFile ?: error("no folder")
        val file = writeNewFile(folder, fileName, source = File(requireNotNull(media.path))) { file -> file.outputStream().use(write) }
            ?: return@runCatching null
        Uri.fromFile(file)
    }.getOrNull()
    ContentResolver.SCHEME_CONTENT -> treeSave(context, media, fileName, write)
    else -> null
}

private fun treeSave(context: Context, media: Uri, fileName: String, write: (OutputStream) -> Unit): Uri? {
    val segments = media.pathSegments
    if (segments.size < 4 || segments[0] != "tree" || segments[2] != "document") return null
    val resolver = context.contentResolver
    val documentId = runCatching { DocumentsContract.getDocumentId(media) }.getOrNull() ?: return null
    val parentId = FolderFiles.parentDocument(resolver, media, documentId) ?: return null
    val folder = DocumentsContract.buildDocumentUriUsingTree(media, parentId)
    // the provider keeps the name as it is given, and numbers it when it is taken
    val created = runCatching { DocumentsContract.createDocument(resolver, folder, ANY_FILE, fileName) }.getOrNull() ?: return null
    val written = runCatching { resolver.openOutputStream(created)?.use(write) != null }.getOrDefault(false)
    if (!written) {
        runCatching { DocumentsContract.deleteDocument(resolver, created) }
        return null
    }
    return created
}

private const val APP_SUBTITLES = "Subtitles"
private const val ANY_FILE = "application/octet-stream"
