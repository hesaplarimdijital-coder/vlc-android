package app.nagmedar.ui.player.subtitles.providers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import app.nagmedar.ui.player.subtitles.OnlineFailure
import app.nagmedar.ui.player.subtitles.OnlineProblem
import app.nagmedar.ui.player.subtitles.isSubtitleFile
import app.nagmedar.ui.player.subtitles.languageOf
import kotlinx.coroutines.Dispatchers
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
        return files.list().mapNotNull { file ->
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
    }

    override suspend fun download(result: SubtitleResult): SubtitleFile {
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

/** The folder's subtitle files through the system's documents: the folder and the ones in it, down two levels. */
class DocumentLocalSubtitleFiles(private val context: Context, private val folder: SubtitleFolderStore) : LocalSubtitleFiles {

    override fun hasFolder(): Boolean = folder.get() != null

    override suspend fun list(): List<LocalSubtitleFile> = withContext(Dispatchers.IO) {
        val root = folder.get()?.let(Uri::parse) ?: return@withContext emptyList()
        val found = ArrayList<LocalSubtitleFile>()
        walk(root, DocumentsContract.getTreeDocumentId(root), 0, found)
        found
    }

    private fun walk(tree: Uri, documentId: String, depth: Int, into: MutableList<LocalSubtitleFile>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        runCatching {
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext() && into.size < MAX_FILES) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < MAX_DEPTH) walk(tree, id, depth + 1, into)
                    } else {
                        into += LocalSubtitleFile(name, DocumentsContract.buildDocumentUriUsingTree(tree, id).toString())
                    }
                }
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
