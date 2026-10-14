package app.nagmedar.ui.library

import android.content.Context
import android.net.Uri
import app.nagmedar.audio.FolderFiles
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.media.CueMedia
import java.io.File

/**
 * S-808: the library's music with its CUE sheets read: a file whose folder holds a sheet listing
 * it with two tracks or more is replaced by those tracks, each an item of its own ("…#cue=3").
 * A folder is listed once, and only when it holds a sheet at all.
 */
object CueLibrary {

    fun expand(context: Context, files: List<MediaWrapper>): List<MediaWrapper> {
        if (files.isEmpty()) return files
        // the folders' listings, by folder, read as a file of the folder asks for one
        val listings = HashMap<String, List<Pair<String, String>>?>()
        val out = ArrayList<MediaWrapper>(files.size)
        for (media in files) {
            val uri = media.uri
            val folder = uri?.let(::folderKey)
            val siblings = if (folder == null) {
                null
            } else {
                listings.getOrPut(folder) { listing(context, uri) }
            }
            val tracks = if (siblings == null || siblings.none { (name, _) -> name.endsWith(".cue", ignoreCase = true) }) null else CueMedia.expand(context, media, siblings)
            if (tracks == null) out += media else out += tracks
        }
        return out
    }

    /** The folder of a file's uri, as a key: the path's folder, or the document tree's parent id. */
    private fun folderKey(uri: Uri): String? = when (uri.scheme) {
        null, "file" -> uri.path?.substringBeforeLast('/', "")
        "content" -> uri.toString().substringBeforeLast("%2F", "").takeIf { it.isNotEmpty() } ?: uri.toString().substringBeforeLast('/', "")
        else -> null
    }

    /** The files in [uri]'s folder: a local folder's names and uris, a tree's through its documents. */
    private fun listing(context: Context, uri: Uri): List<Pair<String, String>>? = when (uri.scheme) {
        null, "file" -> {
            val folder = uri.path?.let { File(it).parentFile } ?: return null
            val names = folder.list() ?: return null
            if (names.none { it.endsWith(".cue", ignoreCase = true) }) emptyList() else names.map { it to Uri.fromFile(File(folder, it)).toString() }
        }
        "content" -> FolderFiles.siblings(context, uri)
        else -> null
    }
}
