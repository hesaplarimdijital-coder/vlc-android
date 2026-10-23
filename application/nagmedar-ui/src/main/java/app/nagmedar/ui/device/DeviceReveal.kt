package app.nagmedar.ui.device

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.ui.R
import app.nagmedar.ui.lab.findActivity
import app.nagmedar.ui.library.LibrarySources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.AppScope
import org.videolan.tools.NAGMEDAR_SAF_FOLDERS
import org.videolan.tools.Notices
import org.videolan.tools.Settings
import java.io.File

/** "Dosyada göster" in the browser: the way from a place to the file's folder, and the file to point at there. */
@Immutable
data class DeviceReveal(val trail: List<DeviceCrumb>, val file: DeviceLocation)

/** What the "Bu cihaz" browser is asked to show: a file in its folder, until the browser takes it. */
object DeviceRequests {
    // process-wide state: made in the global snapshot by ProcessState.warmUp, before any window composes
    var reveal by mutableStateOf<DeviceReveal?>(null)
        private set

    fun open(request: DeviceReveal) {
        reveal = request
    }

    /** The browser opened [request] (a newer one stays). */
    fun taken(request: DeviceReveal) {
        if (reveal == request) reveal = null
    }
}

/** Where a file can be shown: in the browser, in the system's files app (a folder the browser has not), or nowhere. */
internal sealed interface FileSpot {
    data class Browser(val reveal: DeviceReveal) : FileSpot

    data class FilesApp(val folder: Uri) : FileSpot

    data object Nowhere : FileSpot
}

/**
 * "Dosyada göster" of the library's menus: the file's folder in Sources › This device with the
 * file pointed at; a document of a folder the browser has not (one picked for the podcasts'
 * downloads, say) in the system's files app; a notice when neither can show it.
 */
object FileReveal {
    private const val TAG = "Nagmedar/FileReveal"
    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    fun show(context: Context, uri: Uri) {
        AppScope.launch {
            // the drives, the folders and the providers are read off the main thread
            when (val spot = withContext(Dispatchers.IO) { runCatching { locate(context, CueUri.file(uri)) }.getOrDefault(FileSpot.Nowhere) }) {
                is FileSpot.Browser -> DeviceRequests.open(spot.reveal)
                is FileSpot.FilesApp -> openFolder(context, spot.folder)
                FileSpot.Nowhere -> Notices.show(context, context.getString(R.string.nag_media_reveal_nowhere))
            }
        }
    }

    /** Where [uri] is shown. Reads the disk and asks the providers. */
    private suspend fun locate(context: Context, uri: Uri): FileSpot {
        val settings = Settings.getInstance(context)
        val picked = settings.getStringSet(NAGMEDAR_SAF_FOLDERS, null).orEmpty() + LibrarySources.trees(settings)
        val roots = AndroidDeviceSource(context) { picked }.roots()
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> documentSpot(context.contentResolver, uri, roots)
            ContentResolver.SCHEME_FILE, null -> {
                val path = uri.path?.takeIf { it.startsWith("/") } ?: return FileSpot.Nowhere
                val internal = context.getString(R.string.nag_device_internal)
                // a path the drives do not start with may be a link to one ("/sdcard/…")
                val reveal = pathReveal(path, roots, internal)
                    ?: runCatching { File(path).canonicalPath }.getOrNull()?.takeIf { it != path }?.let { pathReveal(it, roots, internal) }
                reveal?.let { FileSpot.Browser(it) } ?: FileSpot.Nowhere
            }
            else -> FileSpot.Nowhere
        }
    }

    /** A document: in the browser when a folder picked for the library holds it, else its folder in the files app. */
    private fun documentSpot(resolver: ContentResolver, uri: Uri, roots: List<DeviceRoot>): FileSpot {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return FileSpot.Nowhere
        for (root in roots) {
            val treeText = root.tree ?: continue
            val tree = Uri.parse(treeText)
            if (root.kind != RootKind.Picked || tree.authority != uri.authority) continue
            val path = documentPath(resolver, tree, documentId) ?: continue
            return FileSpot.Browser(documentReveal(resolver, root, treeText, path))
        }
        val parent = parentOf(resolver, uri, documentId) ?: return FileSpot.Nowhere
        // "content://…/tree/<tree>/document/<id>": a document reached through a tree keeps it
        val throughTree = uri.pathSegments.let { it.size >= 2 && it[0] == "tree" }
        val folder = if (throughTree) {
            DocumentsContract.buildDocumentUriUsingTree(uri, parent)
        } else {
            DocumentsContract.buildDocumentUri(uri.authority, parent)
        }
        return FileSpot.FilesApp(folder)
    }

    /** The documents from [tree]'s top down to [documentId]; null when the tree does not hold it. */
    private fun documentPath(resolver: ContentResolver, tree: Uri, documentId: String): List<String>? {
        val top = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // the provider refuses a document outside the tree
            val path = runCatching { DocumentsContract.findDocumentPath(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, documentId)) }.getOrNull()?.path
            val start = path?.indexOf(top) ?: -1
            if (path != null && start >= 0 && path.last() == documentId) return path.subList(start, path.size)
        }
        // a provider that cannot tell: the external storage's ids say it
        return if (tree.authority == EXTERNAL_STORAGE) storagePath(top, documentId) else null
    }

    /** The folder [documentId] is in, as its provider tells, or as the external storage's id says. */
    private fun parentOf(resolver: ContentResolver, uri: Uri, documentId: String): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val path = runCatching { DocumentsContract.findDocumentPath(resolver, uri) }.getOrNull()?.path
            if (path != null && path.size >= 2 && path.last() == documentId) return path[path.size - 2]
        }
        return if (uri.authority == EXTERNAL_STORAGE) storageParent(documentId) else null
    }

    /** The browser's crumbs from the picked folder [root] down to the document's folder, the names asked of the provider. */
    private fun documentReveal(resolver: ContentResolver, root: DeviceRoot, tree: String, path: List<String>): DeviceReveal {
        val treeUri = Uri.parse(tree)
        val folders = path.drop(1).dropLast(1).map { id ->
            DeviceCrumb(displayName(resolver, treeUri, id) ?: storageName(id), DeviceLocation.Document(tree, id))
        }
        return DeviceReveal(listOf(DeviceCrumb(root.name, root.location)) + folders, DeviceLocation.Document(tree, path.last()))
    }

    private fun displayName(resolver: ContentResolver, tree: Uri, id: String): String? = runCatching {
        resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, id), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** The folder in the files app, with the read permission this app has; a notice when no app opens folders. */
    private fun openFolder(context: Context, folder: Uri) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context.findActivity() == null) view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Log.i(TAG, "nothing opens the folder $folder", e)
            Notices.show(context, context.getString(R.string.nag_media_reveal_no_app))
        } catch (e: SecurityException) {
            Log.i(TAG, "not allowed to open the folder $folder", e)
            Notices.show(context, context.getString(R.string.nag_media_reveal_no_app))
        }
    }
}

/**
 * The browser's way from the drive holding [path] (its longest of [roots]' drives) down to the
 * file's folder, with the file to point at: the locations the browser itself makes as it goes.
 * Null when no drive holds it (the app's own storage).
 */
internal fun pathReveal(path: String, roots: List<DeviceRoot>, internalName: String): DeviceReveal? {
    val (root, top) = roots
        .filter { it.kind != RootKind.Picked }
        .mapNotNull { root -> (root.location as? DeviceLocation.Path)?.let { root to it.path.trimEnd('/') } }
        .filter { (_, top) -> path.startsWith("$top/") }
        .maxByOrNull { (_, top) -> top.length } ?: return null
    val parts = path.substring(top.length + 1).split('/').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    var at = top
    val folders = parts.dropLast(1).map { name ->
        at = "$at/$name"
        DeviceCrumb(name, DeviceLocation.Path(at))
    }
    return DeviceReveal(listOf(DeviceCrumb(root.name.ifEmpty { internalName }, root.location)) + folders, DeviceLocation.Path("$at/${parts.last()}"))
}

/** The external storage's ids from [top] down to [documentId] ("primary:Filmler", "primary:Filmler/Yeni", …); null when not under it. */
internal fun storagePath(top: String, documentId: String): List<String>? {
    if (':' !in top) return null
    val prefix = if (top.endsWith(":")) top else "$top/"
    if (!documentId.startsWith(prefix)) return null
    val parts = documentId.substring(prefix.length).split('/').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    return listOf(top) + parts.indices.map { index -> prefix + parts.subList(0, index + 1).joinToString("/") }
}

/** The folder of an external storage document: "primary:Filmler/Film.mkv" is in "primary:Filmler", "primary:Film.mkv" in "primary:". */
internal fun storageParent(documentId: String): String? {
    val colon = documentId.indexOf(':')
    if (colon < 0 || colon == documentId.length - 1) return null
    val slash = documentId.lastIndexOf('/')
    return if (slash > colon) documentId.substring(0, slash) else documentId.substring(0, colon + 1)
}

/** A document's name from an external storage id, when its provider does not say: "primary:Filmler/Yeni" is "Yeni". */
private fun storageName(id: String): String = id.substringAfterLast('/').substringAfter(':')
