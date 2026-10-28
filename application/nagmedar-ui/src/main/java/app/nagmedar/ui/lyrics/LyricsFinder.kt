package app.nagmedar.ui.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import app.nagmedar.audio.TagLyrics
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.data.LyricsEntry
import app.nagmedar.data.LyricsKey
import app.nagmedar.data.LyricsSource
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.library.displayName
import app.nagmedar.ui.library.siblingFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.io.File
import java.io.IOException

/** S-1003: the track whose lyrics are looked for: where it is, its tags and length. */
@Immutable
data class LyricsTrack(val uri: String, val title: String?, val artist: String?, val album: String?, val lengthMs: Long) {
    /** What its lyrics are kept by: the same song in two files shares them. */
    val key: String get() = LyricsKey.of(artist, title, album, lengthMs)

    /** LRCLIB needs a title and an artist. */
    val searchable: Boolean get() = !title.isNullOrBlank() && !artist.isNullOrBlank()

    val query: LyricsQuery?
        get() = if (!searchable) null else LyricsQuery(artist!!.trim(), title!!.trim(), album?.trim(), ((lengthMs + 500) / 1_000).toInt().takeIf { it > 0 })

    companion object {
        /** The medialibrary's item; null for one without a place (a stream's has one, and no lyrics file). */
        fun of(media: MediaWrapper): LyricsTrack? {
            val uri = media.uri?.toString() ?: return null
            return LyricsTrack(uri, media.title, media.artistName, media.albumName, media.length)
        }
    }
}

/** S-1003: where the lyrics are kept: Nağmedar's database in the app, a list in the tests. */
interface LyricsCache {
    fun forTrack(key: String): List<LyricsEntry>

    fun save(entry: LyricsEntry)

    /** Forgets what was downloaded; the hand-written and the shifts of local lyrics stay. How many went. */
    fun clearDownloaded(): Int

    /** How many tracks' downloaded lyrics are kept. */
    fun downloadedCount(): Int
}

/** The lyrics_cache table. */
class DatabaseLyricsCache(context: Context) : LyricsCache {
    private val dao by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { NagmedarDatabase.get(context).lyrics() }

    override fun forTrack(key: String): List<LyricsEntry> = dao.forTrack(key)

    override fun save(entry: LyricsEntry) = dao.save(entry)

    override fun clearDownloaded(): Int = dao.pruneDownloaded(Long.MAX_VALUE)

    override fun downloadedCount(): Int = dao.countDownloaded()
}

/** S-1003: the lyrics a track has on the device: its tags', the .lrc beside it; in that order. */
fun interface LocalLyrics {
    fun read(track: LyricsTrack): List<Lyrics>
}

/**
 * S-1003: where lyrics come from, in order: the user's own; the device's timed ones (the file's
 * tags, then the .lrc beside it); LRCLIB's timed ones; the device's plain ones; LRCLIB's plain
 * ones; LRCLIB's word that the track is instrumental. LRCLIB's answers are kept, so they show
 * offline; one that found nothing is asked again after [missRetryMs], plain lyrics after
 * [plainRetryMs] (timed ones may have come). The network is used only when [online] says so, and
 * only for a track with a title and an artist.
 */
class LyricsFinder(
    private val cache: LyricsCache,
    private val local: LocalLyrics,
    private val service: LyricsOnline?,
    private val online: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val missRetryMs: Long = MISS_RETRY_MS,
    private val plainRetryMs: Long = PLAIN_RETRY_MS,
) {

    /** [track]'s lyrics; null when nothing has them. Reads files and the network: may take a while. */
    suspend fun find(track: LyricsTrack): Lyrics? = withContext(io) {
        val rows = cache.forTrack(track.key).associateBy { it.source }
        rows[LyricsSource.USER]?.let { row -> Lyrics.of(row.text, LyricsOrigin.User, row.offsetMs)?.let { return@withContext it } }
        val device = runCatching { local.read(track) }.getOrDefault(emptyList()).map { found ->
            rows[found.origin.key]?.let { found.copy(offsetMs = it.offsetMs) } ?: found
        }
        device.firstOrNull { it.synced }?.let { return@withContext it }
        val downloaded = downloaded(track, rows[LyricsSource.LRCLIB])
        downloaded?.takeIf { it.synced }
            ?: device.firstOrNull()
            ?: downloaded
    }

    /** Keeps [offsetMs] as [lyrics]' shift for [track]. */
    suspend fun setOffset(track: LyricsTrack, lyrics: Lyrics, offsetMs: Long) = withContext(io) {
        val row = cache.forTrack(track.key).firstOrNull { it.source == lyrics.origin.key }
        cache.save(
            row?.copy(offsetMs = offsetMs)
                ?: LyricsEntry(track.key, lyrics.origin.key, lyrics.synced, lyrics.cacheText, offsetMs = offsetMs, fetchedAt = clock()),
        )
    }

    /** Forgets LRCLIB's answers; how many. */
    suspend fun clearDownloaded(): Int = withContext(io) { cache.clearDownloaded() }

    /** How many tracks' lyrics LRCLIB gave are kept. */
    suspend fun downloadedCount(): Int = withContext(io) { cache.downloadedCount() }

    /** LRCLIB's lyrics: kept, or asked for now when that is allowed and due. */
    private suspend fun downloaded(track: LyricsTrack, kept: LyricsEntry?): Lyrics? {
        val shown = kept?.let(::fromRow)
        val now = clock()
        val due = when {
            kept == null -> true
            shown == null -> now - kept.fetchedAt >= missRetryMs
            !shown.synced && !shown.instrumental -> now - kept.fetchedAt >= plainRetryMs
            else -> false
        }
        val query = track.query
        if (!due || query == null || service == null || !online()) return shown
        val answer = try {
            service.lookup(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // offline, the server busy or garbled: what was kept
            return shown
        }
        if (answer == null && kept != null && shown != null) {
            // gone from LRCLIB: what was found stays, asked about again later
            cache.save(kept.copy(fetchedAt = now))
            return shown
        }
        val entry = LyricsEntry(
            trackKey = track.key,
            source = LyricsSource.LRCLIB,
            synced = answer?.synced != null,
            text = answer?.synced ?: answer?.plain.orEmpty(),
            language = if (answer?.instrumental == true) INSTRUMENTAL else null,
            offsetMs = kept?.offsetMs ?: 0,
            fetchedAt = now,
        )
        cache.save(entry)
        return fromRow(entry)
    }

    companion object {
        /** A track LRCLIB had nothing for is asked about again after a week. */
        const val MISS_RETRY_MS = 7L * 24 * 60 * 60 * 1000

        /** Plain lyrics are asked about again after a month: timed ones may have come. */
        const val PLAIN_RETRY_MS = 30L * 24 * 60 * 60 * 1000

        /** ISO 639's "no linguistic content": the language of an instrumental's lyrics. */
        const val INSTRUMENTAL = "zxx"

        /** A row of LRCLIB's: its lyrics, the instrumental's word, or null for nothing found. */
        internal fun fromRow(row: LyricsEntry): Lyrics? = when {
            row.text.isNotBlank() -> Lyrics.of(row.text, LyricsOrigin.Online, row.offsetMs)
            row.language == INSTRUMENTAL -> Lyrics(emptyList(), LyricsOrigin.Online, row.offsetMs, instrumental = true)
            else -> null
        }
    }
}

/** S-1003: the device's lyrics of a track: the file's tags (through the music engine), then the .lrc beside it. */
class DeviceLyrics(context: Context) : LocalLyrics {
    private val context = context.applicationContext

    override fun read(track: LyricsTrack): List<Lyrics> {
        val uri = Uri.parse(track.uri)
        if (uri.scheme != null && uri.scheme != ContentResolver.SCHEME_FILE && uri.scheme != ContentResolver.SCHEME_CONTENT) return emptyList()
        // S-808: a CUE sheet's track shares its file: the file's lyrics and the .lrc beside it are the album's, not the track's
        if (CueUri.isTrack(uri)) return emptyList()
        return listOfNotNull(
            runCatching { TagLyrics.read(context, uri)?.let { Lyrics.of(it) } }.getOrNull(),
            runCatching { sidecar(uri)?.let { Lyrics.of(it, LyricsOrigin.File) } }.getOrNull(),
        )
    }

    /** The text of the .lrc named as the file is, beside it: a local folder's, or a picked folder's (S-309). */
    internal fun sidecar(media: Uri): String? {
        val found: Uri? = when (media.scheme) {
            null, ContentResolver.SCHEME_FILE -> {
                val file = media.path?.let(::File) ?: return null
                lrcNames(file.name).map { File(file.parentFile, it) }.firstOrNull { it.isFile }?.let(Uri::fromFile)
            }
            else -> {
                val name = displayName(context.contentResolver, media) ?: return null
                val wanted = lrcNames(name).first()
                siblingFiles(context, media).firstOrNull { (sibling, _) -> sibling.equals(wanted, ignoreCase = true) }?.second?.let(Uri::parse)
            }
        }
        return found?.let(::text)
    }

    private fun text(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > MAX_LRC_BYTES) null else decodeLrc(bytes)
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    companion object {
        /** An .lrc is a few kilobytes; a larger file is not one. */
        private const val MAX_LRC_BYTES = 512 * 1024

        /** The names an .lrc for [mediaName] may have: "Parça.flac" → "Parça.lrc", "Parça.LRC". */
        internal fun lrcNames(mediaName: String): List<String> {
            val base = mediaName.substringBeforeLast('.')
            return listOf("$base.lrc", "$base.LRC")
        }

        /** An .lrc's text: by its byte order mark, as UTF-8 when it reads so, else in the code page its letters tell. */
        internal fun decodeLrc(bytes: ByteArray): String = String(bytes, detectEncoding(bytes).charset).removePrefix("﻿")
    }
}
