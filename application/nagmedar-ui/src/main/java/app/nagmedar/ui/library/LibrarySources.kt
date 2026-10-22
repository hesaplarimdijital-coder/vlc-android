package app.nagmedar.ui.library

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import app.nagmedar.ui.onboarding.MediaAccess
import app.nagmedar.ui.onboarding.scanSources
import app.nagmedar.ui.onboarding.storageSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AndroidDevices
import java.io.File
import java.util.Locale

/**
 * The sections' folders in the app's preferences (Settings › Library), a key per section, and
 * what choosing one does: a picked tree keeps its read permission, a folder the medialibrary
 * does not index yet becomes one of its roots so its files are found.
 */
object LibrarySources {
    private const val KEY_PREFIX = "nagmedar_library_folders_"

    fun key(section: LibrarySection): String = KEY_PREFIX + section.key

    val keys: Set<String> = LibrarySection.entries.map(::key).toSet()

    fun read(prefs: SharedPreferences): LibraryScopes =
        LibraryScopes(LibrarySection.entries.associateWith { LibraryScopes.decode(prefs.getString(key(it), null)) }.filterValues { it.isNotEmpty() })

    fun write(editor: SharedPreferences.Editor, scopes: LibraryScopes): SharedPreferences.Editor {
        for (section in LibrarySection.entries) {
            val list = scopes.of(section)
            if (list.isEmpty()) editor.remove(key(section)) else editor.putString(key(section), LibraryScopes.encode(list))
        }
        return editor
    }

    /** The tree URIs the sections hold, for the code that lets go of a folder's permission. */
    fun trees(prefs: SharedPreferences): Set<String> = read(prefs).trees

    /** A file's location for the rules: its path, or for a picked document its path when it has one. */
    fun locationOf(uri: Uri?): String? {
        if (uri == null) return null
        return when (uri.scheme) {
            "file" -> uri.path?.let(Locations::ofPath)
            "content" -> {
                val authority = uri.authority ?: return null
                val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                    ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                    ?: return null
                Locations.ofDocument(authority, id, AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
            }
            null -> uri.path?.takeIf { it.startsWith("/") }?.let(Locations::ofPath)
            else -> null
        }
    }

    /** A stored folder's location: a path, or a picked tree. */
    fun locationOfFolder(stored: String): String? =
        if (stored.startsWith("/")) Locations.ofPath(stored) else locationOf(runCatching { Uri.parse(stored) }.getOrNull())

    /** A stored folder's file path, when it has one (a tree of the shared storage or an SD card). */
    fun pathOfFolder(stored: String): String? {
        if (stored.startsWith("/")) return stored.trimEnd('/')
        val uri = runCatching { Uri.parse(stored) }.getOrNull() ?: return null
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        return Locations.documentPath(uri.authority.orEmpty(), id, AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
    }

    fun rules(scopes: LibraryScopes): ScopeRules = ScopeRules(scopes, ::locationOfFolder)

    /**
     * Keeps the read permission of a picked [tree]. Permissions are never let go when a folder is
     * taken off a section: the same tree may serve the S-309 folders, recordings or conversions.
     */
    fun keepPermission(context: Context, tree: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        SafLibrary.forget(tree.toString())
    }

    /**
     * With media access, a chosen folder that no root of the medialibrary holds is given to it to
     * scan (as the first run and "Klasör ekle" do); a tree with no path is read through the
     * picker instead (SafLibrary). Runs on the IO dispatcher.
     */
    suspend fun ensureIndexed(context: Context, stored: String) = withContext(Dispatchers.IO) {
        if (!MediaAccess.hasMediaAccess(context)) return@withContext
        val path = pathOfFolder(stored) ?: return@withContext
        val ml = Medialibrary.getInstance()
        val roots = if (ml.isInitiated) runCatching { ml.foldersList.toList() }.getOrDefault(emptyList()) else emptyList()
        val location = Locations.ofPath(path)
        val indexed = roots.any { root -> locationOf(Uri.parse(root))?.let { Locations.isUnder(location, it) } == true }
        if (!indexed) scanSources(context, listOf(path))
    }

    /**
     * Folders offered for one tap: each storage (this device's, the SD cards and USB drives) and
     * the folders at its top, the media ones first ("Movies", "DCIM", "Download"…). Read on the IO
     * dispatcher; without media access there are none (the picker is the way then).
     */
    suspend fun suggestions(context: Context): List<SuggestedFolder> = withContext(Dispatchers.IO) {
        if (!MediaAccess.hasMediaAccess(context)) return@withContext emptyList<SuggestedFolder>()
        storageSources(context).flatMap { storage ->
            val children = runCatching { File(storage.path).listFiles()?.toList() }.getOrNull().orEmpty()
                .filter { it.isDirectory && !it.name.startsWith(".") && it.name !in hidden }
                .sortedWith(compareBy<File> { priority(it.name) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .take(MAX_PER_STORAGE)
            listOf(SuggestedFolder(storage.path, storage.label, storage.removable, root = true)) +
                children.map { SuggestedFolder(it.path, storage.label, storage.removable, root = false) }
        }
    }

    private val hidden = setOf("Android", "LOST.DIR", "System Volume Information")

    private val mediaNames = listOf(
        "movies", "films", "filmler", "film", "tv shows", "series", "diziler", "dizi", "video", "videos", "videolar",
        "dcim", "download", "downloads", "music", "muzik", "recordings", "podcasts",
    )

    private fun priority(name: String): Int = mediaNames.indexOf(name.lowercase(Locale.ROOT)).let { if (it < 0) mediaNames.size else it }

    private const val MAX_PER_STORAGE = 12
}

/** A folder offered for one tap: [label] is its storage's name (empty for this device's own). */
data class SuggestedFolder(val path: String, val label: String, val removable: Boolean, val root: Boolean) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/')
}
