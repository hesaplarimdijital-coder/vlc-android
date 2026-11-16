/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.4.0: from Metamatik (core/domain/src/main/kotlin/com/bobbyesp/metadator/core/domain/artwork/LibraryCovers.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags.artwork

import app.nagmedar.tags.LibraryFile
import app.nagmedar.tags.TagField
import app.nagmedar.tags.TagMap
import app.nagmedar.tags.TagReadResult
import app.nagmedar.tags.TagReader
import app.nagmedar.tags.text.looksGarbled
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Whether a song's file holds a cover, as found for one version of the file: its modification date
 * and size when it was read. [hasCover] is null where that could not be told: a file whose format
 * TagLib reads no tags of, or one that could not be opened.
 *
 * Nağmedar 1.4.0: the same read says which of the tags a song is filed by the file lacks ([missing], Metamatik's
 * "needs attention", which it reads from MediaStore) and whether the text it is listed by looks garbled ([garbled]);
 * both null where the tags could not be read.
 */
data class CoverCheck(
    /** The file's URI (Nağmedar's library knows a song by it). */
    val uri: String,
    val dateModifiedEpochSeconds: Long,
    val sizeBytes: Long,
    val hasCover: Boolean?,
    val missing: Set<String>? = null,
    val garbled: Boolean? = null,
)

/** Nağmedar 1.4.0: the tags whose lack makes a song one to put right, Metamatik's essential fields: by their keys. */
val EssentialKeys: List<String> = listOf(TagField.Artist.key, TagField.Album.key, TagField.AlbumArtist.key, TagField.Date.key, TagField.TrackNumber.key)

/** Nağmedar 1.4.0: the tags a song is listed by, whose text is looked at for garbling (Metamatik's). */
val ListedKeys: List<String> = listOf(TagField.Title.key, TagField.Artist.key, TagField.Album.key, TagField.AlbumArtist.key)

/** The essential tags [tags] lack, by key: none of their values holds a letter or a digit. */
fun missingEssentials(tags: TagMap): Set<String> = EssentialKeys.filterTo(LinkedHashSet()) { key -> tags[key].none { value -> value.any(Char::isLetterOrDigit) } }

/** Whether any value of the tags [tags] is listed by looks garbled, repairable or not. */
fun hasGarbledText(tags: TagMap): Boolean = ListedKeys.any { key -> tags[key].any(::looksGarbled) }

/**
 * Where cover checks are kept between runs of the app: a library on an SD card takes minutes to
 * read, and each file only needs it once per version.
 */
interface CoverCheckStore {
    suspend fun load(): List<CoverCheck>

    suspend fun save(checks: List<CoverCheck>)

    suspend fun remove(uris: Collection<String>)
}

/** What the check of the library's covers has found, and how far it has got. */
data class LibraryCoverState(
    /** The songs whose files hold no picture, of those checked, by URI. */
    val withoutCover: Set<String> = emptySet(),
    /** Nağmedar 1.4.0: the songs whose files lack an essential tag, of those checked, by URI. */
    val needsAttention: Set<String> = emptySet(),
    /** Nağmedar 1.4.0: the songs whose listed text looks garbled, of those checked, by URI. */
    val garbled: Set<String> = emptySet(),
    /** Songs checked, of [total]: every song of the library the scanner was last given. */
    val checked: Int = 0,
    val total: Int = 0,
) {
    val isRunning: Boolean
        get() = checked < total

    fun lacksCover(uri: String): Boolean = uri in withoutCover
}

/**
 * Finds the songs whose files hold no cover, in the background, so that the library can list them
 * without a song's row having to fail to show one first. A picture of any kind counts: the app
 * shows the first picture of a file without a front cover as its cover.
 *
 * Each file is read once per version of it, and what was found is kept ([CoverCheckStore]): a cover
 * saved to a song changes its file, which is then read again, before any song not read yet. It
 * works through the library it was last [submit]ted, one file at a time, and starts over from a new
 * one.
 *
 * It is the least urgent work on the files: it waits while [busy] says that work which matters more
 * is reading or writing them (the lossless check of the library, a batch being saved), and yields
 * between two files. The scope it runs in ends it.
 *
 * @param scope where the work runs: the app's, since it outlives any screen
 */
class LibraryCoverScanner(
    private val reader: TagReader,
    private val store: CoverCheckStore,
    private val scope: CoroutineScope,
    private val busy: Flow<Boolean> = flowOf(false),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val default: CoroutineDispatcher = Dispatchers.Default,
) {
    private val library = MutableStateFlow<List<LibraryFile>?>(null)
    private val _state = MutableStateFlow(LibraryCoverState())
    val state: StateFlow<LibraryCoverState> = _state.asStateFlow()

    /** What is known by song: the checks read from the store, then those made since. */
    private val known = ConcurrentHashMap<String, CoverCheck>()
    private var worker: Job? = null

    /** Takes the library as it is now. Starts the work the first time. */
    fun submit(tracks: List<LibraryFile>) {
        library.value = tracks
        synchronized(this) {
            if (worker == null) worker = scope.launch(default) { work() }
        }
    }

    private suspend fun work() {
        val stored = runCatching { withContext(io) { store.load() } }.getOrNull()
        stored?.forEach { known.putIfAbsent(it.uri, it) }
        while (true) {
            val tracks = library.value ?: library.first { it != null }!!
            forget(tracks)
            publish(tracks)
            val pending = pending(tracks)
            if (pending.isEmpty()) {
                // All checked: wait for a library that differs.
                library.first { it !== tracks }
                continue
            }
            check(pending, tracks)
        }
    }

    /** The songs to read, those saved since they were first: a cover just added shows at once. */
    private fun pending(tracks: List<LibraryFile>): List<LibraryFile> {
        val (changed, unseen) =
            tracks.filter { checkOf(it) == null }.partition { known.containsKey(it.ref.uri) }
        return changed + unseen
    }

    private suspend fun check(pending: List<LibraryFile>, tracks: List<LibraryFile>) {
        val unsaved = mutableListOf<CoverCheck>()
        try {
            for (track in pending) {
                // A newer library is worked through from its start instead.
                if (library.value !== tracks) break
                busy.first { !it }
                val (check, lasting) = read(track)
                known[track.ref.uri] = check
                if (lasting) unsaved += check
                if (unsaved.size >= SAVE_EVERY) {
                    save(unsaved.toList())
                    unsaved.clear()
                    publish(tracks)
                }
                yield()
            }
        } finally {
            // What was found before the work was cancelled is not lost with it.
            if (unsaved.isNotEmpty()) withContext(NonCancellable) { save(unsaved) }
        }
        publish(tracks)
    }

    /**
     * What [track]'s file says, and whether that lasts: a file that could not be opened is only
     * remembered until the app starts again.
     */
    private suspend fun read(track: LibraryFile): Pair<CoverCheck, Boolean> {
        val result =
            try {
                reader.read(track.ref, includePictures = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // One file the reader chokes on must not stop the rest from being checked.
                TagReadResult.Failed(error.message.orEmpty())
            }
        val snapshot = (result as? TagReadResult.Success)?.snapshot
        val check =
            CoverCheck(
                uri = track.ref.uri,
                dateModifiedEpochSeconds = track.dateModifiedEpochSeconds,
                sizeBytes = track.sizeBytes,
                hasCover = snapshot?.pictures?.isNotEmpty(),
                missing = snapshot?.let { missingEssentials(it.tags) },
                garbled = snapshot?.let { hasGarbledText(it.tags) },
            )
        return check to (result is TagReadResult.Success || result == TagReadResult.Unsupported)
    }

    private suspend fun save(checks: List<CoverCheck>) {
        runCatching { withContext(io) { store.save(checks) } }
    }

    private suspend fun forget(tracks: List<LibraryFile>) {
        // An empty library is more often a permission not granted yet than every song gone.
        if (tracks.isEmpty()) return
        val gone = known.keys - tracks.mapTo(HashSet()) { it.ref.uri }
        if (gone.isEmpty()) return
        gone.forEach(known::remove)
        runCatching { withContext(io) { store.remove(gone) } }
    }

    /** The check kept for [track], unless its file changed since: null when it needs reading. */
    private fun checkOf(track: LibraryFile): CoverCheck? =
        known[track.ref.uri]?.takeIf {
            it.dateModifiedEpochSeconds == track.dateModifiedEpochSeconds &&
                it.sizeBytes == track.sizeBytes
        }

    private fun publish(tracks: List<LibraryFile>) {
        var checked = 0
        val withoutCover = HashSet<String>()
        val needsAttention = HashSet<String>()
        val garbled = HashSet<String>()
        for (track in tracks) {
            val check = checkOf(track) ?: continue
            checked++
            if (check.hasCover == false) withoutCover += track.ref.uri
            if (!check.missing.isNullOrEmpty()) needsAttention += track.ref.uri
            if (check.garbled == true) garbled += track.ref.uri
        }
        _state.value = LibraryCoverState(withoutCover, needsAttention, garbled, checked = checked, total = tracks.size)
    }

    private companion object {
        /** Checks kept together, and shown together: one write and one new state for each. */
        const val SAVE_EVERY = 25
    }
}
