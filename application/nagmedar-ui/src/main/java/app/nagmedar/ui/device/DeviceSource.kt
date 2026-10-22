package app.nagmedar.ui.device

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.DocumentsContract
import androidx.compose.runtime.Immutable
import app.nagmedar.ui.network.EntryKind
import app.nagmedar.ui.onboarding.folderName
import app.nagmedar.ui.onboarding.storageSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.resources.AndroidDevices
import java.io.File

/** Where a folder or a file of this device is: a path on a mounted drive, or a document of a folder picked with the system picker. */
@Immutable
sealed interface DeviceLocation {
    data class Path(val path: String) : DeviceLocation

    data class Document(val tree: String, val documentId: String) : DeviceLocation

    /** The address the playback service is given. */
    val uri: String
        get() = when (this) {
            is Path -> Uri.fromFile(File(path)).toString()
            is Document -> DocumentsContract.buildDocumentUriUsingTree(Uri.parse(tree), documentId).toString()
        }
}

/** What a place to browse is: this device's own storage, a removable drive (SD card, USB disk), a folder picked with the system picker. */
enum class RootKind { Internal, Removable, Picked }

/** A place to browse from: its name, what it is, and its room ([totalBytes] 0 when it cannot be told). */
@Immutable
data class DeviceRoot(
    val name: String,
    val kind: RootKind,
    val location: DeviceLocation,
    val totalBytes: Long = 0,
    val freeBytes: Long = 0,
    /** The picked folder's tree, for taking it away. */
    val tree: String? = null,
)

/** A folder or a file in a folder. */
@Immutable
data class DeviceEntry(
    val name: String,
    val location: DeviceLocation,
    val kind: EntryKind,
    val sizeBytes: Long,
    val modifiedMs: Long,
) {
    val folder: Boolean get() = kind == EntryKind.Folder

    val hidden: Boolean get() = name.startsWith(".")
}

/** Reads this device's places and folders: the app's, a recording one in the tests. */
interface DeviceSource {
    suspend fun roots(): List<DeviceRoot>

    /** What [location] holds; null when it cannot be read (a drive pulled out, no permission). */
    suspend fun list(location: DeviceLocation): List<DeviceEntry>?
}

/**
 * The places of this device as Android's storage manager tells them (the shared storage, the
 * mounted SD cards and USB disks; before Android 11 the mounted drives the medialibrary finds),
 * then the folders picked with the system picker; their folders read with the file API or the
 * document provider, off the main thread.
 */
class AndroidDeviceSource(context: Context, private val pickedFolders: () -> Set<String>) : DeviceSource {
    private val context = context.applicationContext

    override suspend fun roots(): List<DeviceRoot> = withContext(Dispatchers.IO) {
        val drives = storageSources(context).map { source ->
            val kind = if (source.removable) RootKind.Removable else RootKind.Internal
            val free = runCatching { StatFs(source.path).availableBytes }.getOrDefault(0L)
            DeviceRoot(source.label, kind, DeviceLocation.Path(source.path), source.totalBytes, free)
        }
        // before Android 11 the storage manager gives no paths: the drives the medialibrary would scan
        val older = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            runCatching { AndroidDevices.externalStorageDirectories }.getOrDefault(emptyList())
                .filter { path -> drives.none { it.location == DeviceLocation.Path(path) } }
                .map { path ->
                    val stat = runCatching { StatFs(path) }.getOrNull()
                    DeviceRoot(File(path).name, RootKind.Removable, DeviceLocation.Path(path), stat?.totalBytes ?: 0L, stat?.availableBytes ?: 0L)
                }
        } else {
            emptyList()
        }
        val picked = pickedFolders().sorted().mapNotNull { tree ->
            val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(tree)) }.getOrNull() ?: return@mapNotNull null
            DeviceRoot(folderName(tree), RootKind.Picked, DeviceLocation.Document(tree, id), tree = tree)
        }
        drives + older + picked
    }

    override suspend fun list(location: DeviceLocation): List<DeviceEntry>? = withContext(Dispatchers.IO) {
        when (location) {
            is DeviceLocation.Path -> listPath(location.path)
            is DeviceLocation.Document -> listDocument(location)
        }
    }

    private fun listPath(path: String): List<DeviceEntry>? {
        val files = runCatching { File(path).listFiles() }.getOrNull() ?: return null
        return files.map { file ->
            val folder = file.isDirectory
            DeviceEntry(
                name = file.name,
                location = DeviceLocation.Path(file.path),
                kind = EntryKind.of(file.name, folder),
                sizeBytes = if (folder) 0L else file.length(),
                modifiedMs = file.lastModified(),
            )
        }
    }

    private fun listDocument(location: DeviceLocation.Document): List<DeviceEntry>? {
        val tree = Uri.parse(location.tree)
        val children = runCatching { DocumentsContract.buildChildDocumentsUriUsingTree(tree, location.documentId) }.getOrNull() ?: return null
        val cursor = runCatching { context.contentResolver.query(children, PROJECTION, null, null, null) }.getOrNull() ?: return null
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val folder = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    add(
                        DeviceEntry(
                            name = name,
                            location = DeviceLocation.Document(location.tree, id),
                            kind = EntryKind.of(name, folder),
                            sizeBytes = if (folder || c.isNull(4)) 0L else c.getLong(4),
                            modifiedMs = if (c.isNull(3)) 0L else c.getLong(3),
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}
