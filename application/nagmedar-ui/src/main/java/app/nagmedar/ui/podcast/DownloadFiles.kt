package app.nagmedar.ui.podcast

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import app.nagmedar.data.PodcastEpisode
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

/** S-1103: a finished download: where it is (a file path, or a document's address) and how big. */
data class StoredFile(val location: String, val bytes: Long)

/** S-1103: where downloaded episodes go. A fake in the tests, [AndroidDownloadStore] in the app. */
interface DownloadStore {
    /** The file an episode is downloaded into until it is whole. */
    fun partFile(feedId: Long, episodeId: Long): File

    /** Puts the whole [part] in its place under [fileName] and takes it away from where it was; throws [IOException]. */
    fun commit(feedId: Long, part: File, fileName: String, mime: String): StoredFile

    /** Deletes the file at [location]; true when it is gone (it was gone already too). */
    fun delete(location: String): Boolean

    fun exists(location: String): Boolean
}

/** S-1103: names and addresses of downloaded files. */
object DownloadFiles {
    /** The address libVLC plays a downloaded file from: a path is a `file:` address, a document's address is as it is. */
    fun uriOf(location: String): Uri = if (location.startsWith("/")) Uri.fromFile(File(location)) else Uri.parse(location)

    /** The text [uriOf] gives libVLC's media, which is what the playback service reports as the media's location. */
    fun locationOf(location: String): String = uriOf(location).toString()

    /** "gece-yarisi-sohbeti-42.mp3": a name no file system refuses, the episode's number making it unique. */
    fun fileName(episode: PodcastEpisode): String {
        val words = episode.title.map { c -> if (c.isLetterOrDigit()) c else ' ' }.joinToString("").trim().split(Regex(" +")).filter { it.isNotEmpty() }
        val slug = words.joinToString("-").lowercase(Locale.ROOT).take(MAX_NAME)
        return (if (slug.isEmpty()) "bolum" else slug) + "-" + episode.id + "." + extension(episode)
    }

    /** The extension of [episode]'s file: by its media type, else by its address, else mp3. */
    fun extension(episode: PodcastEpisode): String {
        val byType = when (episode.mimeType?.lowercase(Locale.ROOT)) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/aac" -> "m4a"
            "audio/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/wav", "audio/x-wav" -> "wav"
            "video/mp4", "video/x-m4v" -> "mp4"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            else -> null
        }
        if (byType != null) return byType
        val last = episode.audioUrl.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val byAddress = last.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return if (byAddress.length in 2..4 && byAddress.all { it.isLetterOrDigit() }) byAddress else "mp3"
    }

    /** The media type the file of [episode] is stored as. */
    fun mimeOf(episode: PodcastEpisode): String = episode.mimeType?.takeIf { it.startsWith("audio/") || it.startsWith("video/") } ?: when (extension(episode)) {
        "mp4", "webm", "mov" -> "video/" + extension(episode)
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        else -> "audio/" + extension(episode)
    }

    private const val MAX_NAME = 60
}

/**
 * S-1103: [DownloadStore] in the app: the app's own storage under `Android/data/…/files/Podcasts` (no permission
 * asked, gone with the app), or the document tree [folder] returns when one was chosen in Settings › Podcasts. The
 * partial files are always in the app's own storage beside the first, so that resuming works the same for both.
 */
class AndroidDownloadStore(private val context: Context, private val folder: () -> String) : DownloadStore {
    private val base: File
        get() = (context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS) ?: File(context.filesDir, "podcasts")).also { it.mkdirs() }

    override fun partFile(feedId: Long, episodeId: Long): File = File(File(base, ".parts"), "$feedId-$episodeId.part")

    override fun commit(feedId: Long, part: File, fileName: String, mime: String): StoredFile {
        val tree = folder().takeIf { it.isNotEmpty() }?.let(Uri::parse)
        return if (tree == null) toAppStorage(feedId, part, fileName) else toTree(tree, part, fileName, mime)
    }

    private fun toAppStorage(feedId: Long, part: File, fileName: String): StoredFile {
        val dir = File(base, feedId.toString()).also { it.mkdirs() }
        val target = File(dir, fileName)
        if (target.exists() && !target.delete()) throw IOException("cannot replace $target")
        if (!part.renameTo(target)) {
            part.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            part.delete()
        }
        return StoredFile(target.absolutePath, target.length())
    }

    private fun toTree(tree: Uri, part: File, fileName: String, mime: String): StoredFile {
        val resolver = context.contentResolver
        val parent = try {
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        } catch (e: IllegalArgumentException) {
            throw IOException("not a folder", e)
        }
        val document = try {
            DocumentsContract.createDocument(resolver, parent, mime, fileName)
        } catch (e: SecurityException) {
            throw IOException("no permission", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("not a folder", e)
        } catch (e: IllegalStateException) {
            throw IOException("provider failed", e)
        } ?: throw IOException("no document")
        try {
            val out = resolver.openOutputStream(document, "w") ?: throw IOException("no stream")
            part.inputStream().use { input -> out.use { input.copyTo(it) } }
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw if (e is IOException) e else IOException("copy failed", e)
        }
        val bytes = part.length()
        part.delete()
        return StoredFile(document.toString(), bytes)
    }

    override fun delete(location: String): Boolean = try {
        if (location.startsWith("/")) {
            val file = File(location)
            !file.exists() || file.delete()
        } else {
            DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(location)) || !exists(location)
        }
    } catch (e: FileNotFoundException) {
        true
    } catch (e: RuntimeException) {
        false
    }

    override fun exists(location: String): Boolean = try {
        if (location.startsWith("/")) {
            File(location).isFile
        } else {
            context.contentResolver.query(Uri.parse(location), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
        }
    } catch (e: RuntimeException) {
        false
    }
}
