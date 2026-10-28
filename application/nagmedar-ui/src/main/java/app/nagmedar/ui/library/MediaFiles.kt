package app.nagmedar.ui.library

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import app.nagmedar.audio.FolderFiles
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.device.FileReveal
import app.nagmedar.ui.lab.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.AppScope
import org.videolan.tools.Notices
import java.io.File
import java.util.Locale

/**
 * A media's file as the library's menus share it or show it in its folder: its address (a path's
 * `file:` address or a document's, a CUE sheet's track as its file), whether it is a video, and
 * its name for the share sheet's title.
 */
@Immutable
data class MediaFile(val uri: Uri, val video: Boolean, val name: String) {
    /** A file of this device, a path or a document, rather than a stream: it can be shared and shown. */
    val local: Boolean get() = uri.scheme == ContentResolver.SCHEME_CONTENT || uri.scheme == ContentResolver.SCHEME_FILE || (uri.scheme == null && uri.path?.startsWith("/") == true)

    companion object {
        fun of(media: MediaWrapper): MediaFile =
            MediaFile(CueUri.file(media.uri), media.type == MediaWrapper.TYPE_VIDEO, media.title?.takeIf { it.isNotBlank() } ?: media.fileName.orEmpty())
    }
}

/** "Paylaş…" and "Dosyada göster" for [file] in an item's menu; none for a stream, which is no file of this device. */
fun fileMenuEntries(context: Context, file: MediaFile): List<MenuEntry> {
    if (!file.local) return emptyList()
    val res = context.resources
    return listOf(
        MenuEntry.Item(res.getString(R.string.nag_media_share), { MediaSharing.share(context, listOf(file)) }, icon = NagIcons.Share),
        MenuEntry.Item(res.getString(R.string.nag_media_reveal), { FileReveal.show(context, file.uri) }, icon = NagIcons.Folder),
    )
}

/** A file as another app may read it, with its type and its name. */
@Immutable
data class SharedFile(val uri: Uri, val mime: String, val name: String)

/**
 * Media files sent to another app with the system's share sheet: a document as it is, a file of
 * this device through the app's FileProvider; one with ACTION_SEND, several with
 * ACTION_SEND_MULTIPLE, the app chosen allowed to read them.
 */
object MediaSharing {

    /** Shares [files] (the readable ones) with the sheet, titled with the file's name or how many; a notice when none can be. */
    fun share(context: Context, files: List<MediaFile>) {
        val asked = files.filter { it.local }.distinctBy { it.uri }
        if (asked.isEmpty()) return
        val res = context.resources
        AppScope.launch {
            // the files are looked at, and the providers asked for their types, off the main thread
            val shared = withContext(Dispatchers.IO) {
                asked.mapNotNull { file -> readable(context, file.uri)?.let { SharedFile(it, mimeOf(context, file), file.name) } }
            }
            val send = sendIntent(shared)
            if (send == null) {
                Notices.show(context, res.getString(R.string.nag_media_share_missing))
                return@launch
            }
            val title = shared.singleOrNull()?.let { res.getString(R.string.nag_media_share_title, it.name) }
                ?: res.getQuantityString(R.plurals.nag_media_share_many, shared.size, shared.size)
            val chooser = Intent.createChooser(send, title)
            if (context.findActivity() == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooser)
            } catch (e: ActivityNotFoundException) {
                Notices.show(context, res.getString(R.string.nag_media_share_no_app))
            } catch (e: SecurityException) {
                Log.i(TAG, "the share sheet would not open", e)
                Notices.show(context, res.getString(R.string.nag_media_share_no_app))
            }
        }
    }

    /** [uri] as another app may read it, through this app's FileProvider for a file. Looks at the disk. */
    fun readable(context: Context, uri: Uri): Uri? =
        readable(uri, File::isFile) { file -> FileProvider.getUriForFile(context, context.packageName + ".provider", file) }

    /** A document as it is; a file that is there through [provider]; nothing for a stream or a file that is gone. */
    internal fun readable(uri: Uri, isFile: (File) -> Boolean, provider: (File) -> Uri): Uri? = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> uri
        ContentResolver.SCHEME_FILE, null -> uri.path?.takeIf { it.startsWith("/") }?.let(::File)?.takeIf(isFile)?.let { file -> runCatching { provider(file) }.getOrNull() }
        else -> null
    }

    /**
     * The intent that sends [files]: ACTION_SEND for one, ACTION_SEND_MULTIPLE for several, of their
     * common type; their addresses also in its clip, which carries the permission to read them to
     * the app chosen (and to the sheet's preview); null when there is nothing to send.
     */
    fun sendIntent(files: List<SharedFile>): Intent? {
        val first = files.firstOrNull() ?: return null
        val clip = ClipData(first.name, files.map { it.mime }.distinct().toTypedArray(), ClipData.Item(first.uri))
        files.drop(1).forEach { clip.addItem(ClipData.Item(it.uri)) }
        val send = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, first.uri)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { it.uri }))
        }
        send.setType(commonType(files.map { it.mime }))
        send.clipData = clip
        return send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** One type for them all: theirs when they have the same, any video's or any sound's for one kind, any type for a mix. */
    internal fun commonType(types: List<String>): String = when {
        types.isEmpty() -> ANY
        types.distinct().size == 1 -> types.first()
        types.all { it.startsWith("video/") } -> "video/*"
        types.all { it.startsWith("audio/") } -> "audio/*"
        else -> ANY
    }

    /** [file]'s type: its provider's for a document, else its extension's, else a video's or a sound's. */
    private fun mimeOf(context: Context, file: MediaFile): String {
        val told = if (file.uri.scheme == ContentResolver.SCHEME_CONTENT) runCatching { context.contentResolver.getType(file.uri) }.getOrNull() else null
        val extension = file.uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        val byName = extension.takeIf { it.isNotEmpty() }?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        return media(told) ?: media(byName) ?: if (file.video) "video/*" else "audio/*"
    }

    private fun media(type: String?): String? = type?.lowercase(Locale.ROOT)?.takeIf { it.startsWith("video/") || it.startsWith("audio/") }

    private const val ANY = "*/*"
    private const val TAG = "Nagmedar/MediaSharing"
}

/** A document's or a file's name as the file manager shows it. */
internal fun displayName(resolver: ContentResolver, uri: Uri): String? {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.let { return it }
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

/** The files beside a media file, their names and where they are ([FolderFiles]: a folder, or a picked tree). */
internal fun siblingFiles(context: Context, media: Uri): List<Pair<String, String>> = FolderFiles.siblings(context, media)

/**
 * A folder the system's picker gave, as Settings shows it: [default] when none was chosen, else
 * the chosen folder's path on its drive ("Music/Podcasts", "Recordings"); a drive's root is its
 * own name.
 */
fun pickedFolderLabel(folder: String?, default: String): String {
    folder ?: return default
    val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(folder)) }.getOrNull() ?: return folder
    val path = id.substringAfter(':', "").trim('/')
    return path.ifEmpty { id.substringBefore(':') }
}
