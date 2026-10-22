package app.nagmedar.ui.lyrics.editor

import android.net.Uri
import app.nagmedar.audio.TagWrite
import app.nagmedar.data.LyricsEntry
import app.nagmedar.data.LyricsSource
import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.Lyrics
import app.nagmedar.ui.lyrics.LyricsCache
import app.nagmedar.ui.lyrics.LyricsOrigin
import app.nagmedar.ui.lyrics.LyricsTrack

/** What writing the .lrc beside a track came to. */
sealed interface SidecarResult {
    /** The file [name] was written. */
    data class Written(val name: String) : SidecarResult

    /** The app may not write where the track is: the user picks the folder (and the app keeps the permission). */
    data object NeedsFolder : SidecarResult

    data object Failed : SidecarResult
}

/** S-1005: the .lrc beside a track: the device's files in the app, a map in the tests. */
interface SidecarStore {
    /** The text of the .lrc beside [track]; null when there is none. */
    fun read(track: LyricsTrack): String?

    /** Writes [text] as the .lrc named as [track]'s file, over the one there; into [folder], a tree the user picked, when the track's own folder is not writable. */
    fun write(track: LyricsTrack, text: String, folder: Uri?): SidecarResult
}

/** S-1005: a track's tags: the music engine's in the app, a record in the tests. */
interface TagStore {
    /** The lyrics the tags hold; null for none. */
    fun read(track: LyricsTrack): Lyrics?

    /** Writes [lines] into the tags in the form the file's format keeps; what came of it. */
    fun write(track: LyricsTrack, lines: List<LyricLine>): TagWrite
}

/** Where a save went, or why it did not. */
sealed interface SaveResult {
    /** Saved to the .lrc [name], or into the tags ([name] null). */
    data class Saved(val name: String?) : SaveResult

    /** Saving beside the file needs a folder the user picks. */
    data object NeedsFolder : SaveResult

    /** No room in the app's storage for the copy of the file kept while its tags are written. */
    data object NoRoom : SaveResult

    data object Failed : SaveResult
}

/** The lyrics the editor replaced, kept: [source] says where they were. */
data class OriginalLyrics(val source: String, val text: String)

/**
 * S-1005: saves the edited lyrics: into a .lrc beside the file, or into its tags, as the user
 * chooses. Before it writes over lyrics that are there it keeps them (once: the first ones are
 * the originals) in the cache, and the lyrics saved are kept there as the user's own, so the
 * views show them whatever the files say. Reads and writes files: off the main thread.
 */
class LyricsWriter(
    private val cache: LyricsCache,
    private val sidecar: SidecarStore,
    private val tags: TagStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Saves [lines] as the .lrc of [track]; the [header] tags on top. */
    fun toFile(track: LyricsTrack, lines: List<LyricLine>, header: LrcHeader?, folder: Uri? = null): SaveResult {
        val text = LrcDocument.lrc(lines, header)
        sidecar.read(track)?.takeIf { it.isNotBlank() }?.let { keepOriginal(track, LyricsSource.ORIGINAL_FILE, it) }
        return when (val result = sidecar.write(track, text, folder)) {
            is SidecarResult.Written -> {
                keepAsUser(track, text)
                SaveResult.Saved(result.name)
            }
            SidecarResult.NeedsFolder -> SaveResult.NeedsFolder
            SidecarResult.Failed -> SaveResult.Failed
        }
    }

    /** Saves [lines] into the tags of [track]. */
    fun toTag(track: LyricsTrack, lines: List<LyricLine>): SaveResult {
        tags.read(track)?.let { keepOriginal(track, LyricsSource.ORIGINAL_TAG, it.cacheText) }
        when (tags.write(track, lines)) {
            TagWrite.Written -> Unit
            TagWrite.NoRoom -> return SaveResult.NoRoom
            TagWrite.Failed -> return SaveResult.Failed
        }
        keepAsUser(track, LrcDocument.lrc(lines))
        return SaveResult.Saved(null)
    }

    /** The lyrics the editor replaced for [track], the tags' before the file's. */
    fun originals(track: LyricsTrack): List<OriginalLyrics> {
        val rows = cache.forTrack(track.key).associateBy { it.source }
        return listOf(LyricsSource.ORIGINAL_TAG, LyricsSource.ORIGINAL_FILE).mapNotNull { source ->
            rows[source]?.takeIf { it.text.isNotBlank() }?.let { OriginalLyrics(source, it.text) }
        }
    }

    private fun keepOriginal(track: LyricsTrack, source: String, text: String) {
        if (cache.forTrack(track.key).any { it.source == source }) return
        cache.save(LyricsEntry(track.key, source, synced = Lyrics.of(text, LyricsOrigin.User)?.synced == true, text = text, fetchedAt = clock()))
    }

    private fun keepAsUser(track: LyricsTrack, text: String) {
        cache.save(LyricsEntry(track.key, LyricsSource.USER, synced = true, text = text, fetchedAt = clock()))
    }
}
