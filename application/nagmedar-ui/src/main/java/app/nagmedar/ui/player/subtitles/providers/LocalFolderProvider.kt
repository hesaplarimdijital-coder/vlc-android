package app.nagmedar.ui.player.subtitles.providers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import app.nagmedar.ui.player.subtitles.OnlineFailure
import app.nagmedar.ui.player.subtitles.OnlineProblem
import app.nagmedar.ui.player.subtitles.OnlineQuota
import app.nagmedar.ui.player.subtitles.isSubtitleFile
import app.nagmedar.ui.player.subtitles.languageOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

/** A subtitle file in the user's folder: its name and where it is. */
data class LocalSubtitleFile(val name: String, val uri: String)

/** The user's subtitle folder, listed and read; the app's own implementation uses the system's documents. */
interface LocalSubtitleFiles {
    /** Whether a folder is chosen. */
    fun hasFolder(): Boolean

    /** The files of the folder and the folders in it, as far as is reasonable. */
    suspend fun list(): List<LocalSubtitleFile>

    suspend fun read(uri: String): ByteArray?
}

/**
 * A folder of the user's own subtitles (S-707), searched by the film's name: a second provider
 * that needs no network and no key. The words of the film's name before its year, its resolution
 * or its season and episode must all be in the file's name; the more of the rest are, and the
 * fewer words the file's name has besides, the better the file ranks. For a series the season and episode must be the same ("S01E02", "1x02"). A
 * language is read from the name ("Film.tr.srt") when it tells one.
 */
class LocalFolderProvider(
    private val files: LocalSubtitleFiles,
    override val displayName: String = NAME,
) : SubtitleProvider {
    override val id: String = ID

    override val capabilities = ProviderCapabilities(
        searchByName = true,
        searchBySeasonEpisode = true,
        searchByLanguage = true,
        download = true,
    )

    override fun unavailable(): OnlineProblem? = if (files.hasFolder()) null else OnlineProblem.NoFolder

    override suspend fun search(query: SubtitleQuery): List<SubtitleResult> {
        if (!files.hasFolder()) throw OnlineFailure.NoFolder()
        val wanted = Words.of(query.name.orEmpty())
        val season = query.season ?: wanted.season
        val episode = query.episode ?: wanted.episode
        if (wanted.title.isEmpty() && season == null) return emptyList()
        val listed = files.list()
        // many hundreds of names are matched off the caller's (main) thread
        return withContext(Dispatchers.Default) { matches(listed, query, wanted, season, episode) }
    }

    private fun CoroutineScope.matches(listed: List<LocalSubtitleFile>, query: SubtitleQuery, wanted: Words, season: Int?, episode: Int?): List<SubtitleResult> =
        listed.mapNotNull { file ->
            ensureActive()
            if (!isSubtitleFile(file.name) || file.name.substringAfterLast('.').lowercase(Locale.ROOT) in PAIRED) return@mapNotNull null
            val found = Words.of(file.name.substringBeforeLast('.'))
            val language = found.tokens.drop(1).firstNotNullOfOrNull(::languageOf).orEmpty()
            if (query.languages.isNotEmpty() && language.isNotEmpty() && query.languages.none { sameLanguage(it, language) }) return@mapNotNull null
            if (!found.tokens.containsAll(wanted.title)) return@mapNotNull null
            if (season != null && (found.season != season || found.episode != episode)) return@mapNotNull null
            val others = wanted.tokens - wanted.title.toSet()
            val shared = if (others.isEmpty()) 1.0 else others.count { it in found.tokens }.toDouble() / others.size
            // a file named for more than was asked for (a sequel, a cut) ranks below the exact one
            val closeness = wanted.title.size.toDouble() / found.title.size.coerceAtLeast(1)
            SubtitleResult(
                provider = ID,
                id = file.uri,
                providerName = displayName,
                fileName = file.name,
                release = file.name.substringBeforeLast('.'),
                language = language,
                rating = shared * closeness,
            )
        }

    override suspend fun download(result: SubtitleResult, onQuota: (OnlineQuota) -> Unit): SubtitleFile {
        val bytes = files.read(result.id) ?: throw OnlineFailure.Server(0)
        return SubtitleFile(bytes, result.fileName)
    }

    private fun sameLanguage(wanted: String, found: String) =
        wanted.substringBefore('-').equals(found.substringBefore('-'), ignoreCase = true)

    /** A name as words: the title's, then the rest; the season and the episode when it tells them. */
    internal class Words(val tokens: List<String>, val title: List<String>, val season: Int?, val episode: Int?) {
        companion object {
            private val split = Regex("""[^\p{L}\p{Nd}]+""")
            private val seasonEpisode = Regex("""^s(\d{1,2})e(\d{1,3})$|^(\d{1,2})x(\d{2,3})$""")
            private val technical = Regex("""^(19|20)\d\d$|^\d{3,4}p$|^(x|h)26[45]$|^(web|webdl|webrip|bluray|brrip|bdrip|hdtv|dvdrip|hdr|uhd|4k)$""")

            fun of(text: String): Words {
                val tokens = fold(text).split(split).filter { it.isNotEmpty() }
                var season: Int? = null
                var episode: Int? = null
                tokens.forEach { token ->
                    seasonEpisode.matchEntire(token)?.let { match ->
                        val g = match.groupValues
                        season = (g[1].ifEmpty { g[3] }).toInt()
                        episode = (g[2].ifEmpty { g[4] }).toInt()
                    }
                }
                val end = tokens.withIndex().indexOfFirst { (at, token) -> seasonEpisode.matches(token) || technical.matches(token) || at > 0 && languageOf(token) != null }
                val title = (if (end < 0) tokens else tokens.take(end)).filter { it.length > 1 }
                return Words(tokens, title, season, episode)
            }

            /** Lower case without accents, the Turkish dotless i as an i: "Işığı" and "isigi" are alike. */
            fun fold(text: String): String =
                Normalizer.normalize(text.replace('ı', 'i').replace('İ', 'I'), Normalizer.Form.NFD)
                    .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
                    .lowercase(Locale.ROOT)
        }
    }

    companion object {
        /** A VobSub's two files are one subtitle; one of them alone cannot be loaded. */
        private val PAIRED = setOf("idx", "sub")

        const val ID = "local-folder"
        const val NAME = "Local folder"
    }
}

/** The subtitle folder in the settings: a document tree's address; absent when none is chosen. */
class SubtitleFolderStore(private val prefs: SharedPreferences) {
    fun get(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun set(uri: String?) {
        prefs.edit().apply { if (uri == null) remove(KEY) else putString(KEY, uri) }.apply()
    }

    companion object {
        /** This device's, so a backup leaves it out (S-1502). */
        const val KEY = "nagmedar_subtitle_folder"
    }
}

/** A folder's entry: a document id, a name, and whether it is a folder. */
internal class TreeEntry(val id: String, val name: String, val directory: Boolean)

/**
 * The files below [root], down [maxDepth] levels and at most [maxFiles] of them, by [children]
 * (each folder's entries, read lazily). Cancellation is checked before every entry, so a walk of
 * a slow or huge folder ends when its coroutine is cancelled (the search's timeout, the window
 * closed) instead of running on; a folder that cannot be read is skipped.
 */
internal suspend fun walkTree(
    root: String,
    maxDepth: Int,
    maxFiles: Int,
    children: (String) -> Iterator<TreeEntry>,
    file: (TreeEntry) -> LocalSubtitleFile,
    into: MutableList<LocalSubtitleFile>,
) {
    val context = currentCoroutineContext()
    fun walk(documentId: String, depth: Int) {
        val entries = try {
            children(documentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        try {
            while (into.size < maxFiles && entries.hasNext()) {
                context.ensureActive()
                val entry = entries.next()
                if (entry.directory) {
                    if (depth < maxDepth) walk(entry.id, depth + 1)
                } else {
                    into += file(entry)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // the rest of this folder cannot be read; what was found stays
        }
    }
    walk(root, 0)
}

/** The folder's subtitle files through the system's documents: the folder and the ones in it, down two levels. */
class DocumentLocalSubtitleFiles(private val context: Context, private val folder: SubtitleFolderStore) : LocalSubtitleFiles {

    override fun hasFolder(): Boolean = folder.get() != null

    override suspend fun list(): List<LocalSubtitleFile> = withContext(Dispatchers.IO) {
        val root = folder.get()?.let(Uri::parse) ?: return@withContext emptyList()
        val found = ArrayList<LocalSubtitleFile>()
        walkTree(
            root = DocumentsContract.getTreeDocumentId(root),
            maxDepth = MAX_DEPTH,
            maxFiles = MAX_FILES,
            children = { documentId -> children(root, documentId) },
            file = { entry -> LocalSubtitleFile(entry.name, DocumentsContract.buildDocumentUriUsingTree(root, entry.id).toString()) },
            into = found,
        )
        found
    }

    /** The entries of a folder, read one at a time (the walk stops between two of them when it is cancelled). */
    private fun children(tree: Uri, documentId: String): Iterator<TreeEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val cursor = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null,
                null,
                null,
            )
        }.getOrNull() ?: return emptyList<TreeEntry>().iterator()
        return object : Iterator<TreeEntry> {
            private var ready: TreeEntry? = null
            private var done = false

            private fun advance() {
                while (ready == null && !done) {
                    if (!cursor.moveToNext()) {
                        done = true
                        cursor.close()
                        return
                    }
                    val name = cursor.getString(1) ?: continue
                    ready = TreeEntry(cursor.getString(0), name, cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
                }
            }

            override fun hasNext(): Boolean {
                advance()
                return ready != null
            }

            override fun next(): TreeEntry {
                advance()
                return ready.also { ready = null } ?: throw NoSuchElementException()
            }
        }
    }

    override suspend fun read(uri: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }?.takeIf { it.size <= MAX_BYTES } }.getOrNull()
    }

    private companion object {
        const val MAX_DEPTH = 2
        const val MAX_FILES = 20_000
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
