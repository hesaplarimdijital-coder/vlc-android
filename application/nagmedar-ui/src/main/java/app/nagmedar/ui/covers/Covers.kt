package app.nagmedar.ui.covers

import androidx.compose.runtime.Immutable
import app.nagmedar.ui.music.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

/** S-1006: where a cover came from. */
enum class CoverOrigin(val key: String) {
    /** Chosen by hand: it stays until taken away. */
    User("user"),

    /** The file's tags: APIC, PICTURE, covr. */
    Tags("tags"),

    /** An image beside the file: cover.jpg, folder.jpg… */
    Folder("folder"),

    /** Cover Art Archive, through MusicBrainz. */
    Online("caa"),
}

/** A cover found, kept in the app's own folder: its file, where it came from. */
@Immutable
data class Cover(val file: File, val origin: CoverOrigin) {
    /** Changes when the file does: a new cover of the same album shows. */
    val version: Long get() = file.lastModified()
}

/** S-1006: whose cover is looked for: an album's (by its artist and title), else a single file's. */
@Immutable
data class CoverTrack(val uri: String, val album: String?, val artist: String?) {
    /** An album's tracks share their cover; a track without an album has its own. */
    val key: String
        get() = if (!album.isNullOrBlank()) albumKey(artist, album) else "file|$uri"

    /** Cover Art Archive needs the album and its artist. */
    val searchable: Boolean get() = !album.isNullOrBlank() && !artist.isNullOrBlank()

    companion object {
        private fun fold(text: String?): String = text?.trim()?.lowercase(Locale.ROOT).orEmpty()

        /** The key of the album [title] of [artist], as its tracks' is. */
        fun albumKey(artist: String?, title: String): String = "album|${fold(artist)}|${fold(title)}"

        /** The medialibrary's item: its album under its album artist, else its artist. */
        fun of(media: MediaWrapper): CoverTrack? {
            val uri = media.uri?.toString() ?: return null
            return CoverTrack(uri, media.albumName, media.albumArtistName?.takeIf { it.isNotBlank() } ?: media.artistName)
        }

        fun of(track: Track): CoverTrack = CoverTrack(track.uri, track.album, track.filedArtist)
    }
}

/** Whether [bytes] are an image the app shows: JPEG, PNG, WebP, GIF or BMP, by their first bytes. */
fun looksLikeImage(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    fun at(index: Int, text: String) = text.indices.all { bytes[index + it] == text[it].code.toByte() }
    return (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) ||
        (bytes[0] == 0x89.toByte() && at(1, "PNG")) ||
        (at(0, "RIFF") && at(8, "WEBP")) ||
        at(0, "GIF8") ||
        at(0, "BM")
}

/**
 * S-1006: the covers kept, a file each in a folder: "<key's SHA-1>.<origin>", the user's beside the
 * one found, which it goes before; "<key's SHA-1>.miss" holds when Cover Art Archive had none. The
 * folder is asked of [folder] at the first read or write, not when the store is made: the app's
 * filesDir looks at the disk, which the main thread must not.
 */
class CoverStore(folder: () -> File) {

    constructor(dir: File) : this({ dir })

    private val dir by lazy(folder)

    private fun name(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { ((it.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

    private fun file(key: String, origin: CoverOrigin) = File(dir, "${name(key)}.${origin.key}")

    private fun missFile(key: String) = File(dir, "${name(key)}.miss")

    /** The cover kept for [key]: the user's, else the one found. */
    fun kept(key: String): Cover? = ORDER.firstNotNullOfOrNull { origin ->
        file(key, origin).takeIf { it.isFile && it.length() > 0 }?.let { Cover(it, origin) }
    }

    /** [bytes] kept as [key]'s cover from [origin]; null when they are not an image or cannot be written. */
    fun keep(key: String, origin: CoverOrigin, bytes: ByteArray): Cover? {
        if (!looksLikeImage(bytes)) return null
        val target = file(key, origin)
        return if (write(target, bytes)) Cover(target, origin) else null
    }

    /** S-1502: the covers chosen by hand, for a backup: their files, whose names their albums' keys give. */
    fun chosen(): List<File> = dir.listFiles()
        ?.filter { it.isFile && CHOSEN_NAME.matches(it.name) && it.length() > 0 }
        ?.sortedBy { it.name }
        .orEmpty()

    /** S-1502: a cover chosen by hand, back from a backup under its file's [name]; false when it is not one. */
    fun restoreChosen(name: String, bytes: ByteArray): Boolean =
        CHOSEN_NAME.matches(name) && looksLikeImage(bytes) && write(File(dir, name), bytes)

    /** [bytes] as [target], whole or not at all. */
    private fun write(target: File, bytes: ByteArray): Boolean = try {
        dir.mkdirs()
        val part = File(dir, "${target.name}.part")
        part.writeBytes(bytes)
        part.renameTo(target).also { moved -> if (!moved) part.delete() }
    } catch (e: IOException) {
        false
    }

    /** The user's cover of [key] taken away. */
    fun forgetUsers(key: String) {
        file(key, CoverOrigin.User).delete()
    }

    /** What was found for [key] forgotten, and that nothing was: it is looked for again. */
    fun forgetFound(key: String) {
        FOUND.forEach { file(key, it).delete() }
        missFile(key).delete()
    }

    /** When Cover Art Archive last had nothing for [key]; null when it was not asked or had one. */
    fun missedAt(key: String): Long? = runCatching { missFile(key).takeIf { it.isFile }?.readText()?.trim()?.toLong() }.getOrNull()

    fun miss(key: String, at: Long) {
        runCatching {
            dir.mkdirs()
            missFile(key).writeText(at.toString())
        }
    }

    /** How many found covers are kept (the users' are not counted). */
    fun foundCount(): Int = dir.listFiles()?.count { file -> FOUND.any { file.name.endsWith(".${it.key}") } } ?: 0

    /** Every found cover and miss forgotten; the users' stay. How many covers went. */
    fun clearFound(): Int {
        var gone = 0
        dir.listFiles()?.forEach { file ->
            when {
                FOUND.any { file.name.endsWith(".${it.key}") } -> if (file.delete()) gone++
                file.name.endsWith(".miss") || file.name.endsWith(".part") -> file.delete()
            }
        }
        return gone
    }

    companion object {
        private val ORDER = listOf(CoverOrigin.User, CoverOrigin.Tags, CoverOrigin.Folder, CoverOrigin.Online)
        private val FOUND = ORDER - CoverOrigin.User

        /** A chosen cover's file: its album key's SHA-1, ".user". */
        val CHOSEN_NAME = Regex("[0-9a-f]{40}\\.user")
    }
}

/** The MusicBrainz release and release group a file's tags name (Picard writes them). */
@Immutable
data class MusicBrainzIds(val release: String? = null, val releaseGroup: String? = null)

/** S-1006: where covers are on the device: the file's tags, its folder; and the ids its tags give. */
interface CoverSources {
    fun tags(track: CoverTrack): ByteArray?

    fun folder(track: CoverTrack): ByteArray?

    fun ids(track: CoverTrack): MusicBrainzIds
}

/** What Cover Art Archive is asked for. */
@Immutable
data class CoverQuery(val artist: String, val album: String, val ids: MusicBrainzIds)

/** S-1006: covers on the network: Cover Art Archive in the app, a fake in the tests. */
fun interface CoverOnline {
    /** The album's front cover; null when there is none. Throws an IOException when it could not ask. */
    suspend fun front(query: CoverQuery): ByteArray?
}

/**
 * S-1006: an album's cover, in order: the one chosen by hand; the one kept; the file's tags'; an
 * image in its folder; Cover Art Archive's (when [online] allows, for an album with an artist).
 * What is found is kept at its size, for every view of it; an album Cover Art Archive had nothing
 * for is asked again after [missRetryMs].
 */
class CoverFinder(
    private val store: CoverStore,
    private val sources: CoverSources,
    private val service: CoverOnline?,
    private val online: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val missRetryMs: Long = MISS_RETRY_MS,
) {

    suspend fun find(track: CoverTrack): Cover? = withContext(io) {
        val key = track.key
        store.kept(key)?.let { return@withContext it }
        runCatching { sources.tags(track) }.getOrNull()?.let { bytes -> store.keep(key, CoverOrigin.Tags, bytes)?.let { return@withContext it } }
        runCatching { sources.folder(track) }.getOrNull()?.let { bytes -> store.keep(key, CoverOrigin.Folder, bytes)?.let { return@withContext it } }
        fetch(track)
    }

    /** [bytes] as [track]'s album's cover, chosen by hand; null when they are not an image. */
    suspend fun choose(track: CoverTrack, bytes: ByteArray): Cover? = withContext(io) { store.keep(track.key, CoverOrigin.User, bytes) }

    /** The cover kept for [key], at once: no file read but whether it is there, no network. */
    fun keptNow(key: String): Cover? = store.kept(key)

    /** S-1502: the covers chosen by hand, for a backup. */
    suspend fun chosenFiles(): List<File> = withContext(io) { store.chosen() }

    /** S-1502: a cover chosen by hand, back from a backup; false when it is not one. */
    suspend fun restoreChosen(name: String, bytes: ByteArray): Boolean = withContext(io) { store.restoreChosen(name, bytes) }

    /** Whether [track]'s album's cover was chosen by hand. */
    suspend fun isChosen(track: CoverTrack): Boolean = withContext(io) { store.kept(track.key)?.origin == CoverOrigin.User }

    /** The cover chosen by hand taken away: the one found shows again. */
    suspend fun unchoose(track: CoverTrack) = withContext(io) { store.forgetUsers(track.key) }

    /** What was found forgotten and looked for again (Cover Art Archive asked at once). */
    suspend fun again(track: CoverTrack): Cover? = withContext(io) {
        store.forgetFound(track.key)
        find(track)
    }

    suspend fun foundCount(): Int = withContext(io) { store.foundCount() }

    suspend fun clearFound(): Int = withContext(io) { store.clearFound() }

    private suspend fun fetch(track: CoverTrack): Cover? {
        val key = track.key
        if (!track.searchable || service == null || !online()) return null
        store.missedAt(key)?.let { if (clock() - it < missRetryMs) return null }
        val ids = runCatching { sources.ids(track) }.getOrDefault(MusicBrainzIds())
        val bytes = try {
            service.front(CoverQuery(track.artist!!.trim(), track.album!!.trim(), ids))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // offline, or the server busy: asked again next time
            return null
        }
        if (bytes == null) {
            store.miss(key, clock())
            return null
        }
        return store.keep(key, CoverOrigin.Online, bytes)
    }

    companion object {
        /** An album Cover Art Archive had nothing for is asked about again after a week. */
        const val MISS_RETRY_MS = 7L * 24 * 60 * 60 * 1000
    }
}
