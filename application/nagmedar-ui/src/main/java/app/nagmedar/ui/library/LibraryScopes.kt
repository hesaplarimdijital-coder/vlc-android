package app.nagmedar.ui.library

import androidx.compose.runtime.Immutable
import java.util.Locale

/*
 * The library's sections and the folders each one shows (Settings › Library). Plain data and
 * rules, so they can be tested without Android: where a file is and which section takes it.
 */

/** The parts of the library that can be given their own folders: since 1.0 the music alone. */
enum class LibrarySection(val key: String) {
    Music("music"),
}

/**
 * A folder a section shows, or with [exclude] one it leaves out: a file path ("/storage/1A2B-3C4D/Movies")
 * or the tree URI the system's folder picker gave ("content://…/tree/primary%3AMovies").
 */
@Immutable
data class SourceFolder(val location: String, val exclude: Boolean = false) {
    val isTree: Boolean get() = location.startsWith("content://")
}

/** Each section's folders. A section without folders to show shows everything the library has for it. */
@Immutable
data class LibraryScopes(val folders: Map<LibrarySection, List<SourceFolder>> = emptyMap()) {
    fun of(section: LibrarySection): List<SourceFolder> = folders[section].orEmpty()

    /** The section shows only files under its folders. */
    fun constrained(section: LibrarySection): Boolean = of(section).any { !it.exclude }

    val isEmpty: Boolean get() = folders.values.all { it.isEmpty() }

    /** Every tree URI any section holds. */
    val trees: Set<String> get() = folders.values.flatten().filter { it.isTree }.map { it.location }.toSet()

    fun with(section: LibrarySection, list: List<SourceFolder>): LibraryScopes = LibraryScopes(folders + (section to list))

    /** [folder] added to [section] (once; added again it only changes whether it is left out). */
    fun plus(section: LibrarySection, folder: SourceFolder): LibraryScopes =
        with(section, of(section).filter { it.location != folder.location } + folder)

    fun minus(section: LibrarySection, location: String): LibraryScopes = with(section, of(section).filter { it.location != location })

    companion object {
        val Empty = LibraryScopes()

        /** One section's folders as the preferences keep them: a line each, "+" shown, "−" left out. */
        fun encode(list: List<SourceFolder>): String = list.joinToString("\n") { (if (it.exclude) "-" else "+") + it.location }

        fun decode(text: String?): List<SourceFolder> = text.orEmpty().lineSequence()
            .filter { it.length > 1 && (it[0] == '+' || it[0] == '-') }
            .map { SourceFolder(it.substring(1), exclude = it[0] == '-') }
            .distinctBy { it.location }
            .toList()
    }
}

/** Where files and folders are, compared without regard to case (Android's shared storage and SD cards ignore it). */
object Locations {
    /** The provider of the shared storage and SD cards in the system's folder picker. */
    const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    /** The Downloads provider, whose documents are sometimes plain paths ("raw:/storage/…"). */
    const val DOWNLOADS = "com.android.providers.downloads.documents"

    /** "/storage/emulated/0/Movies/" → "/storage/emulated/0/movies". */
    fun ofPath(path: String): String = path.trimEnd('/').lowercase(Locale.ROOT).ifEmpty { "/" }

    /**
     * A document's file path when the provider's id names one: "primary:Movies/Film.mkv" of the
     * shared storage under [primaryRoot], "1A2B-3C4D:Movies" under /storage/1A2B-3C4D, "raw:/…" of
     * Downloads; null for any other document.
     */
    fun documentPath(authority: String, documentId: String, primaryRoot: String): String? {
        if (authority == DOWNLOADS && documentId.startsWith("raw:/")) return documentId.removePrefix("raw:")
        if (authority != EXTERNAL_STORAGE) return null
        val colon = documentId.indexOf(':')
        if (colon <= 0) return null
        val volume = documentId.substring(0, colon)
        val rest = documentId.substring(colon + 1).trim('/')
        val root = if (volume.equals("primary", ignoreCase = true)) primaryRoot.trimEnd('/') else "/storage/$volume"
        return if (rest.isEmpty()) root else "$root/$rest"
    }

    /** A document's location: its path when it has one, else the provider and the id (a child's id starts with its folder's). */
    fun ofDocument(authority: String, documentId: String, primaryRoot: String): String =
        documentPath(authority, documentId, primaryRoot)?.let(::ofPath) ?: "doc:$authority/" + documentId.trimEnd('/').lowercase(Locale.ROOT)

    /** [location] is [folder] or inside it, subfolders included. */
    fun isUnder(location: String, folder: String): Boolean =
        location == folder || location.startsWith(if (folder.endsWith('/') || folder.endsWith(':')) folder else "$folder/")
}

/**
 * The sections' folders made into rules, with [locate] turning a stored folder into its location.
 * A folder whose location is unknown still makes its section show only its folders; it matches nothing.
 */
class ScopeRules(val scopes: LibraryScopes, locate: (String) -> String?) {
    private val shown = LibrarySection.entries.associateWith { section ->
        scopes.of(section).filter { !it.exclude }.map { locate(it.location) ?: UNKNOWN }
    }
    private val left = LibrarySection.entries.associateWith { section ->
        scopes.of(section).filter { it.exclude }.mapNotNull { locate(it.location) }
    }

    fun constrained(section: LibrarySection): Boolean = shown.getValue(section).isNotEmpty()

    private fun excluded(section: LibrarySection, location: String) = left.getValue(section).any { Locations.isUnder(location, it) }

    /** [section] has a folder to show that holds [location], and does not leave it out. */
    fun claims(section: LibrarySection, location: String): Boolean =
        shown.getValue(section).any { Locations.isUnder(location, it) } && !excluded(section, location)

    /** [section] leaves the file at [location] out: one of its folders marked so holds it. */
    fun leavesOut(section: LibrarySection, location: String?): Boolean = location != null && excluded(section, location)

    /**
     * Whether [section]'s folders let it show the file at [location]: under one of its folders when
     * it has some, else anywhere but its excluded folders. A file of no known place (null) only by
     * sections without folders.
     */
    fun inScope(section: LibrarySection, location: String?): Boolean {
        if (location == null) return !constrained(section)
        if (excluded(section, location)) return false
        return !constrained(section) || claims(section, location)
    }

    private companion object {
        const val UNKNOWN = "\u0000"
    }
}
