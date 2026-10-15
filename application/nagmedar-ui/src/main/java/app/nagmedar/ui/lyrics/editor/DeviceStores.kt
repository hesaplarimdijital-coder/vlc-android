package app.nagmedar.ui.lyrics.editor

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import app.nagmedar.audio.FolderFiles
import app.nagmedar.audio.LoudnessScanner
import app.nagmedar.audio.TagLyrics
import app.nagmedar.ui.lyrics.DeviceLyrics
import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.Lyrics
import app.nagmedar.ui.lyrics.LyricsTrack
import java.io.File
import java.io.IOException

/**
 * S-1005: the .lrc beside a track on the device. A local file's folder is written directly; where
 * the app may not, through a folder the user picked before (Android keeps the permission) or picks
 * now ([SidecarResult.NeedsFolder]); a track in a picked library folder (S-309) through its tree.
 */
class DeviceSidecar(context: Context) : SidecarStore {
    private val context = context.applicationContext
    private val reader = DeviceLyrics(this.context)

    override fun read(track: LyricsTrack): String? = runCatching { reader.sidecar(Uri.parse(track.uri)) }.getOrNull()

    override fun write(track: LyricsTrack, text: String, folder: Uri?): SidecarResult {
        val media = Uri.parse(track.uri)
        val name = baseName(media)?.let { "$it.lrc" } ?: return SidecarResult.Failed
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (folder != null) return inTree(folder, DocumentsContract.getTreeDocumentId(folder), name, bytes)
        return when (media.scheme) {
            null, ContentResolver.SCHEME_FILE -> beside(File(media.path ?: return SidecarResult.Failed), name, bytes)
            ContentResolver.SCHEME_CONTENT -> besideDocument(media, name, bytes)
            else -> SidecarResult.Failed
        }
    }

    private fun baseName(media: Uri): String? {
        val file = when (media.scheme) {
            null, ContentResolver.SCHEME_FILE -> media.path?.let { File(it).name }
            else -> LoudnessScanner.displayName(context, media)
        }
        return file?.takeIf { it.isNotBlank() }?.substringBeforeLast('.')
    }

    /** A local file's folder: directly, else through a tree the user picked for it. */
    private fun beside(file: File, name: String, bytes: ByteArray): SidecarResult {
        val folder = file.parentFile ?: return SidecarResult.Failed
        val existing = DeviceLyrics.lrcNames(file.name).map { File(folder, it) }.firstOrNull { it.isFile }
        val target = existing ?: File(folder, name)
        // written beside it first and then moved over it: a failed write never leaves half a file
        val partial = File(folder, ".${target.name}.part")
        try {
            partial.writeBytes(bytes)
            if (partial.renameTo(target)) return SidecarResult.Written(target.name)
            partial.delete()
        } catch (e: IOException) {
            // not writable: a folder the user picked
            partial.delete()
        } catch (e: SecurityException) {
        }
        val tree = pickedTrees().firstNotNullOfOrNull { tree -> SafPaths.documentFor(tree, folder.path, volumeRoots())?.let { tree to it } }
            ?: return SidecarResult.NeedsFolder
        return inTree(tree.first, tree.second, name, bytes)
    }

    /** A document of a picked tree: beside it, in the same folder. */
    private fun besideDocument(media: Uri, name: String, bytes: ByteArray): SidecarResult {
        val segments = media.pathSegments
        if (segments.size < 4 || segments[0] != "tree" || segments[2] != "document") return SidecarResult.NeedsFolder
        val documentId = runCatching { DocumentsContract.getDocumentId(media) }.getOrNull() ?: return SidecarResult.NeedsFolder
        val parentId = FolderFiles.parentDocument(context.contentResolver, media, documentId) ?: return SidecarResult.NeedsFolder
        return inTree(media, parentId, name, bytes)
    }

    /** [name] in the folder [folderId] of [tree]: the .lrc there is written over, else one is created. */
    private fun inTree(tree: Uri, folderId: String, name: String, bytes: ByteArray): SidecarResult {
        val resolver = context.contentResolver
        return try {
            val folder = DocumentsContract.buildDocumentUriUsingTree(tree, folderId)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
            var target: Uri? = null
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (target == null && c.moveToNext()) {
                    if (c.getString(1).equals(name, ignoreCase = true)) target = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                }
            }
            val document = target ?: DocumentsContract.createDocument(resolver, folder, ANY_FILE, name) ?: return SidecarResult.NeedsFolder
            val written = resolver.openOutputStream(document, "wt")?.use { it.write(bytes) } != null
            if (written) SidecarResult.Written(name) else SidecarResult.Failed
        } catch (e: SecurityException) {
            SidecarResult.NeedsFolder
        } catch (e: IOException) {
            SidecarResult.Failed
        } catch (e: IllegalArgumentException) {
            SidecarResult.NeedsFolder
        }
    }

    /** The trees the user gave the app to write in. */
    private fun pickedTrees(): List<Uri> = context.contentResolver.persistedUriPermissions.filter { it.isWritePermission }.map { it.uri }

    private fun volumeRoots(): Map<String, String> = buildMap {
        put("primary", Environment.getExternalStorageDirectory().path)
        File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }?.forEach { put(it.name, it.path) }
    }

    private companion object {
        const val ANY_FILE = "application/octet-stream"
    }
}

/** Document ids and paths of the storage access framework's external storage provider. */
object SafPaths {

    /**
     * The document id of the folder at [path] inside the picked [tree] ("primary:Music/Album"), given the volumes' roots
     * ("primary" to "/storage/emulated/0"); null when the folder is not in the tree.
     */
    fun documentFor(tree: Uri, path: String, roots: Map<String, String>): String? {
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        return documentFor(treeId, path, roots)
    }

    /** As above, for the tree's document id. */
    fun documentFor(treeId: String, path: String, roots: Map<String, String>): String? {
        val (volume, root) = roots.entries.firstOrNull { (_, root) -> path == root || path.startsWith("$root/") }?.toPair() ?: return null
        val id = volume + ":" + path.removePrefix(root).trim('/')
        return id.takeIf { it == treeId || it.startsWith(treeId.trimEnd('/') + "/") || treeId.endsWith(":") && it.startsWith(treeId) }
    }
}

/** S-1005: a track's tags through the music engine, in the form the file's format keeps lyrics. */
class EngineTags(context: Context) : TagStore {
    private val context = context.applicationContext

    override fun read(track: LyricsTrack): Lyrics? = runCatching { TagLyrics.read(context, Uri.parse(track.uri))?.let { Lyrics.of(it) } }.getOrNull()

    override fun write(track: LyricsTrack, lines: List<LyricLine>): Boolean {
        val uri = Uri.parse(track.uri)
        val id3 = LrcDocument.usesId3(LoudnessScanner.displayName(context, uri))
        val (text, synced) = LrcDocument.tagPayload(lines, id3)
        return runCatching { TagLyrics.write(context, uri, text, synced) }.getOrDefault(false)
    }
}
