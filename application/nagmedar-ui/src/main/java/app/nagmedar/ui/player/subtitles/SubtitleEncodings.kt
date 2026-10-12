package app.nagmedar.ui.player.subtitles

import android.content.Context
import android.net.Uri
import android.util.Log
import app.nagmedar.data.NagmedarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.resources.SubtitleEncodingFinder
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * The character sets of the films' subtitle files (S-702). Before a film starts, VLC is told the
 * one its subtitle files are written in ([find]): the user's choice for the film, or what the
 * subtitle files beside it or kept with it are detected as (a Turkish SRT in Windows-1254 or
 * ISO-8859-9; UTF-8 VLC reads by itself). A file added while the film plays in another one is
 * read again into UTF-8 ([readable]), which VLC takes whatever it was told.
 */
object SubtitleEncodings : SubtitleEncodingFinder {
    private const val TAG = "Nagmedar/SubEncoding"

    /** What is read to tell a file's character set: its first lines are enough. */
    private const val HEAD_BYTES = 64 * 1024

    /** A subtitle file larger than this is not a subtitle file. */
    private const val MAX_BYTES = 16L * 1024 * 1024

    /** The character set VLC was told for each film playing, by its URI; absent: VLC's own. */
    private val told = ConcurrentHashMap<String, SubtitleEncoding>()

    override suspend fun find(context: Context, media: Uri, location: Uri, kept: List<String>): String? = withContext(Dispatchers.IO) {
        val chosen = chosen(context, media)
        val encoding = chosen ?: detectFor(context, location, kept)
        if (encoding == null || encoding.unicode) told.remove(media.toString()) else told[media.toString()] = encoding
        encoding?.takeUnless { it.unicode }?.vlc
    }

    /** The character set VLC reads [media]'s subtitles in, as it was told; null: VLC's own setting. */
    fun toldFor(media: String): SubtitleEncoding? = told[media]

    /** The user's choice for [media], kept with its other settings (S-809); null: detected. */
    suspend fun chosen(context: Context, media: Uri): SubtitleEncoding? = withContext(Dispatchers.IO) {
        runCatching { NagmedarDatabase.get(context).mediaSettings().get(media.toString())?.subtitleEncoding }.getOrNull()?.let(SubtitleEncoding::of)
    }

    /** Keeps [encoding] for [media]; null: detected again. */
    suspend fun choose(context: Context, media: Uri, encoding: SubtitleEncoding?) = withContext(Dispatchers.IO) {
        NagmedarDatabase.get(context).mediaSettings().saveSubtitleEncoding(media.toString(), encoding?.key, System.currentTimeMillis())
    }

    /** The first non-Unicode character set among the film's subtitle files: those beside it, then those kept with it. */
    private fun detectFor(context: Context, location: Uri, kept: List<String>): SubtitleEncoding? {
        val name = displayName(context.contentResolver, location) ?: return null
        val beside = matchSubtitles(name, siblingFiles(context, location)).map { Uri.parse(it.uri) to it.name }
        val others = kept.map { Uri.parse(it) }.map { it to (it.lastPathSegment?.substringAfterLast('/') ?: "") }
        return (beside + others).take(MAX_FILES).firstNotNullOfOrNull { (uri, fileName) ->
            val head = head(context, uri) ?: return@firstNotNullOfOrNull null
            if (!isTextSubtitle(fileName, head)) return@firstNotNullOfOrNull null
            detectEncoding(head).takeUnless { it.unicode }
        }
    }

    private const val MAX_FILES = 6

    /** The first bytes of a file, to tell its character set by. */
    fun head(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(HEAD_BYTES)
            var read = 0
            while (read < HEAD_BYTES) {
                val count = input.read(buffer, read, HEAD_BYTES - read)
                if (count < 0) break
                read += count
            }
            buffer.copyOf(read)
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    /** A whole subtitle file, within reason. */
    fun bytes(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            bytes.takeIf { it.size <= MAX_BYTES }
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /**
     * [uri] (named [name]) as VLC reads it right for [media]: the file itself when it is pictures,
     * Unicode or in the character set VLC was told; else its text in [encoding] (or as detected)
     * written into UTF-8, with the mark VLC goes by, in the app's own folder.
     */
    fun readable(context: Context, media: String, uri: Uri, name: String, encoding: SubtitleEncoding? = null): Uri {
        val bytes = bytes(context, uri) ?: return uri
        if (!isTextSubtitle(name, bytes.copyOf(minOf(bytes.size, HEAD_BYTES)))) return uri
        val read = encoding ?: detectEncoding(bytes.copyOf(minOf(bytes.size, HEAD_BYTES)))
        if (encoding == null && (read == SubtitleEncoding.Utf8 || read == told[media])) return uri
        return try {
            val folder = File(context.filesDir, FOLDER).apply { mkdirs() }
            val extension = name.substringAfterLast('.', "srt")
            val copy = File(folder, digest("$uri ${read.key}") + "." + extension)
            if (!copy.exists()) copy.writeText("﻿" + decodeSubtitle(bytes, read), Charsets.UTF_8)
            Uri.fromFile(copy)
        } catch (e: IOException) {
            Log.w(TAG, "$name stays as it is", e)
            uri
        }
    }

    /** Whether [uri] is one of the copies [readable] wrote. */
    fun isCopy(context: Context, uri: String): Boolean = Uri.parse(uri).path?.startsWith(File(context.filesDir, FOLDER).path) == true

    private const val FOLDER = "subtitles"

    private fun digest(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { ((it.toInt() and 0xFF) + 0x100).toString(16).substring(1) }.take(24)
}
